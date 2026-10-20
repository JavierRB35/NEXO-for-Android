package com.jruiz.nexo.connect.vault;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.crypto.tink.KeysetHandle;
import com.jruiz.nexo.connect.NexoConnectAtomicImporter;
import com.jruiz.nexo.connect.NexoConnectPayloadCodec;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NexoConnectInboxCoordinatorTest {
    private static final UUID ID=UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final String SENDER="22222222-2222-4222-8222-222222222222";

    static final class FakeGateway implements NexoConnectInboxCoordinator.Gateway {
        int opened,acks;
        boolean failAck;
        String serverStatus="accepted";
        @Override public JSONObject open(UUID id)throws Exception {
            opened++;
            return new JSONObject().put("deliveryId",id.toString());
        }
        @Override public String accept(UUID id)throws Exception {
            acks++;
            if(failAck)throw new java.io.IOException("simulated offline");
            return serverStatus;
        }
    }
    static SQLiteDatabase db(){
        SQLiteDatabase sql=SQLiteDatabase.create(null);
        sql.execSQL("CREATE TABLE demo(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL)");
        return sql;
    }
    static int rows(SQLiteDatabase db){
        try(Cursor c=db.rawQuery("SELECT count(*) FROM demo",null)){
            c.moveToFirst();return c.getInt(0);
        }
    }
    static final class Fixture{
        final FakeGateway net=new FakeGateway();
        boolean failImport;
        final NexoConnectInboxCoordinator coordinator=
                new NexoConnectInboxCoordinator(net,(db,envelope,receiver,sender,
                        senderId,senderGen,account,gen,now)->{
                    if(failImport)throw new SecurityException("invalid QR pin");
                    return NexoConnectAtomicImporter.importVerified(db,
                            ID.toString(),SENDER,"note",
                            NexoConnectPayloadCodec.encode("note",
                                new JSONObject().put("title","Copia"),null),
                            (sql,kind,payload)->{
                                sql.execSQL("INSERT INTO demo(title) VALUES(?)",
                                        new Object[]{payload.getString("title")});
                                try(Cursor c=sql.rawQuery("SELECT last_insert_rowid()",null)){
                                    c.moveToFirst();return c.getLong(0);
                                }
                            });
                });
        NexoConnectInboxCoordinator.ImportOutcome receive(SQLiteDatabase database,
                boolean consent)throws Exception{
            return coordinator.importApproved(database,ID,consent,null,null,
                    UUID.fromString(SENDER),1,ID,1,1780000000000L);
        }
    }
    @Test public void noConsentMeansNoDownloadOrImportOrAck()throws Exception{
        Fixture f=new Fixture();
        try(SQLiteDatabase db=db()){
            try{f.receive(db,false);fail("Imported without consent");}
            catch(SecurityException expected){}
            assertEquals(0,f.net.opened);
            assertEquals(0,f.net.acks);
            assertEquals(0,rows(db));
        }
    }
    @Test public void importCommitsBeforeAcceptedAndNeverCopiesTwice()throws Exception{
        Fixture f=new Fixture();
        try(SQLiteDatabase db=db()){
            NexoConnectInboxCoordinator.ImportOutcome first=f.receive(db,true);
            assertTrue(first.acknowledged);
            assertEquals("accepted",first.serverStatus);
            assertEquals(1,rows(db));
            assertTrue(NexoConnectAtomicImporter.pendingAcknowledgements(db,10).isEmpty());
            NexoConnectInboxCoordinator.ImportOutcome replay=f.receive(db,true);
            assertTrue(replay.alreadyImported);
            assertEquals(first.localId,replay.localId);
            assertEquals(1,rows(db));
            assertEquals(2,f.net.acks);
        }
    }
    @Test public void networkFailureAfterCommitDoesNotCreateSecondCopy()throws Exception{
        Fixture f=new Fixture();f.net.failAck=true;
        try(SQLiteDatabase db=db()){
            NexoConnectInboxCoordinator.ImportOutcome first=f.receive(db,true);
            assertFalse(first.acknowledged);
            assertEquals("ack-pending",first.serverStatus);
            assertEquals(1,rows(db));
            assertEquals(1,NexoConnectAtomicImporter.pendingAcknowledgements(db,10).size());
            f.net.failAck=false;
            NexoConnectInboxCoordinator.RetryOutcome retried=
                    f.coordinator.retryAcknowledgements(db,10);
            assertEquals(1,retried.acceptedCount);
            assertEquals(0,retried.conflictCount);
            assertFalse(retried.stoppedOffline);
            assertEquals(1,rows(db));
            assertTrue(NexoConnectAtomicImporter.pendingAcknowledgements(db,10).isEmpty());
        }
    }
    @Test public void invalidIdentityFailsBeforeAnyAcknowledgement()throws Exception{
        Fixture f=new Fixture();f.failImport=true;
        try(SQLiteDatabase db=db()){
            try{f.receive(db,true);fail("Invalid signed payload imported");}
            catch(SecurityException expected){}
            assertEquals(1,f.net.opened);
            assertEquals(0,f.net.acks);
            assertEquals(0,rows(db));
        }
    }
    @Test public void expirationAfterCommitRemainsUnacknowledged()throws Exception{
        Fixture f=new Fixture();f.net.serverStatus="expired";
        try(SQLiteDatabase db=db()){
            NexoConnectInboxCoordinator.ImportOutcome imported=f.receive(db,true);
            assertEquals(1,rows(db));
            assertFalse(imported.acknowledged);
            assertEquals("expired",imported.serverStatus);
            NexoConnectInboxCoordinator.RetryOutcome again=
                    f.coordinator.retryAcknowledgements(db,10);
            assertEquals(0,again.acceptedCount);
            assertEquals(1,again.conflictCount);
            assertEquals(1,NexoConnectAtomicImporter.pendingAcknowledgements(db,10).size());
        }
    }
    @Test public void offlineRetryStopsAfterOneAttempt()throws Exception{
        Fixture f=new Fixture();f.net.failAck=true;
        try(SQLiteDatabase db=db()){
            f.receive(db,true);
            assertEquals(1,f.net.acks);
            NexoConnectInboxCoordinator.RetryOutcome again=
                    f.coordinator.retryAcknowledgements(db,10);
            assertTrue(again.stoppedOffline);
            assertEquals(2,f.net.acks);
            assertEquals(1,rows(db));
        }
    }
}

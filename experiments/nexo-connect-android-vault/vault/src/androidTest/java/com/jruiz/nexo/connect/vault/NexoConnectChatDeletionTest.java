package com.jruiz.nexo.connect.vault;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Arrays;
import java.util.UUID;
import static org.junit.Assert.*;

/**
 * These tests use isolated in-memory SQLite, no phone identities and no remote
 * API. They verify the operation cannot unlink or corrupt other conversations.
 */
@RunWith(AndroidJUnit4.class)
public final class NexoConnectChatDeletionTest {
    private static final UUID OWNER=UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OTHER=UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID PEER=UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID SECOND=UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID M1=UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID M2=UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID M3=UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");

    private static SQLiteDatabase memory(){
        SQLiteDatabase db=SQLiteDatabase.create(null);
        NexoConnectChatStore.schema(db);
        NexoConnectChatBackup.schema(db);
        NexoConnectChatDeletion.schema(db);
        db.execSQL("CREATE TABLE nexo_connect_import_receipts("+
            "delivery_id TEXT PRIMARY KEY,sender_id TEXT NOT NULL,entity_type TEXT NOT NULL,"+
            "local_id INTEGER NOT NULL)");
        return db;
    }
    private static void addOutgoing(SQLiteDatabase db,UUID account,UUID peer,UUID delivery){
        db.execSQL("INSERT INTO nexo_chat_outgoing VALUES(?,?,?,?,?,?)",
            new Object[]{delivery.toString(),account.toString(),peer.toString(),
                "encrypted-placeholder","sent",System.currentTimeMillis()});
    }
    private static long count(SQLiteDatabase db,String sql,String... args){
        try(Cursor c=db.rawQuery(sql,args)){assertTrue(c.moveToFirst());return c.getLong(0);}
    }

    @Test public void messageDeletionIsAccountAndPeerScoped(){
        try(SQLiteDatabase db=memory()){
            addOutgoing(db,OWNER,PEER,M1);
            addOutgoing(db,OWNER,SECOND,M2);
            addOutgoing(db,OTHER,PEER,M3);
            NexoConnectChatDeletion.deleteMessages(db,OWNER,PEER,Arrays.asList(M1.toString()));
            assertEquals(0,count(db,"SELECT count(*) FROM nexo_chat_outgoing WHERE delivery=?",
                    M1.toString()));
            assertEquals(1,count(db,"SELECT count(*) FROM nexo_chat_outgoing WHERE delivery=?",
                    M2.toString()));
            assertEquals(1,count(db,"SELECT count(*) FROM nexo_chat_outgoing WHERE delivery=?",
                    M3.toString()));
            assertTrue(NexoConnectChatDeletion.visibility(db,OWNER,PEER)
                    .hidden(M1.toString(),1));
            assertFalse(NexoConnectChatDeletion.visibility(db,OWNER,SECOND)
                    .hidden(M2.toString(),1));
            assertFalse(NexoConnectChatDeletion.visibility(db,OTHER,PEER)
                    .hidden(M1.toString(),1));
        }
    }

    @Test public void clearingConversationPreservesReceiptsAndFutureMessages(){
        try(SQLiteDatabase db=memory()){
            addOutgoing(db,OWNER,PEER,M1);
            addOutgoing(db,OWNER,SECOND,M2);
            db.execSQL("INSERT INTO nexo_connect_import_receipts VALUES(?,?,?,?)",
                new Object[]{M3.toString(),PEER.toString(),"note",51});
            long now=System.currentTimeMillis();
            NexoConnectChatDeletion.deleteConversation(db,OWNER,PEER,now);
            assertEquals(0,count(db,"SELECT count(*) FROM nexo_chat_outgoing "+
                    "WHERE account=? AND peer=?",OWNER.toString(),PEER.toString()));
            assertEquals(1,count(db,"SELECT count(*) FROM nexo_chat_outgoing "+
                    "WHERE account=? AND peer=?",OWNER.toString(),SECOND.toString()));
            assertEquals(1,count(db,"SELECT count(*) FROM nexo_connect_import_receipts "+
                    "WHERE delivery_id=?",M3.toString()));
            NexoConnectChatDeletion.Visibility visible=
                    NexoConnectChatDeletion.visibility(db,OWNER,PEER);
            assertTrue(visible.cleared());
            assertTrue(visible.hidden(M1.toString(),now-500));
            assertTrue(visible.hidden(M3.toString(),now-500));
            assertTrue(visible.hidden(UUID.randomUUID().toString(),now-500));
            assertFalse(visible.hidden(UUID.randomUUID().toString(),now+5000));
            assertFalse(NexoConnectChatDeletion.wasCleared(db,OWNER,SECOND));
        }
    }

    @Test public void repeatedLocalDeletionIsIdempotent(){
        try(SQLiteDatabase db=memory()){
            addOutgoing(db,OWNER,PEER,M1);
            NexoConnectChatDeletion.deleteMessages(db,OWNER,PEER,Arrays.asList(M1.toString()));
            NexoConnectChatDeletion.deleteMessages(db,OWNER,PEER,Arrays.asList(M1.toString()));
            assertEquals(1,count(db,"SELECT count(*) FROM nexo_chat_deleted WHERE delivery=?",
                    M1.toString()));
        }
    }
}

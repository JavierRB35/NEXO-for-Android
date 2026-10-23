package com.jruiz.nexo.connect.vault;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.content.ContentValues;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.Collection;

/**
 * Local-only Chat deletion. Never sends remote delete requests, revokes a peer,
 * or changes the authenticated delivery/ACK receipts. Tombstones ensure that
 * old backup rows or re-imported encrypted messages do not reappear.
 *
 * Incoming ciphertext may be retained in the shared Nexo items table to
 * preserve receipt/ACK referential integrity. It is not displayed or exported
 * as Chat history after deletion. Local backup files already exported and
 * messages on the other person's phone are outside this operation.
 */
public final class NexoConnectChatDeletion {
    private NexoConnectChatDeletion(){}

    public static void schema(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS nexo_chat_deleted("+
            "account TEXT NOT NULL,peer TEXT NOT NULL,delivery TEXT NOT NULL,"+
            "PRIMARY KEY(account,peer,delivery))");
        db.execSQL("CREATE TABLE IF NOT EXISTS nexo_chat_cleared("+
            "account TEXT NOT NULL,peer TEXT NOT NULL,cleared_at INTEGER NOT NULL,"+
            "PRIMARY KEY(account,peer))");
    }

    public static final class Visibility {
        private final Set<String> deleted;
        private final long cutoff;
        private Visibility(Set<String> deleted,long cutoff){this.deleted=deleted;this.cutoff=cutoff;}
        public boolean hidden(String delivery,long sentAt){
            return deleted.contains(delivery)||(cutoff>0&&sentAt<=cutoff);
        }
        public boolean cleared(){return cutoff>0;}
    }

    public static Visibility visibility(SQLiteDatabase db,UUID owner,UUID peer){
        schema(db);
        Set<String> deleted=new HashSet<>();
        try(Cursor c=db.rawQuery("SELECT delivery FROM nexo_chat_deleted WHERE account=? AND peer=?",
                new String[]{owner.toString(),peer.toString()})){
            while(c.moveToNext())deleted.add(c.getString(0));
        }
        long cutoff=0;
        try(Cursor c=db.rawQuery("SELECT cleared_at FROM nexo_chat_cleared WHERE account=? AND peer=?",
                new String[]{owner.toString(),peer.toString()})){
            if(c.moveToFirst())cutoff=c.getLong(0);
        }
        return new Visibility(Collections.unmodifiableSet(deleted),cutoff);
    }

    public static boolean wasCleared(SQLiteDatabase db,UUID owner,UUID peer){
        return visibility(db,owner,peer).cleared();
    }

    private static void tombstone(SQLiteDatabase db,UUID owner,UUID peer,String id){
        UUID delivery=UUID.fromString(id);
        ContentValues row=new ContentValues();
        row.put("account",owner.toString());row.put("peer",peer.toString());
        row.put("delivery",delivery.toString());
        db.insertWithOnConflict("nexo_chat_deleted",null,row,SQLiteDatabase.CONFLICT_IGNORE);
    }

    public static void deleteMessages(SQLiteDatabase db,UUID owner,UUID peer,Collection<String> deliveries){
        if(deliveries==null||deliveries.isEmpty()||deliveries.size()>200)
            throw new IllegalArgumentException("Select 1 to 200 messages");
        schema(db);
        NexoConnectChatStore.schema(db);
        NexoConnectChatBackup.schema(db);
        db.beginTransaction();
        try{
            for(String delivery:deliveries){
                tombstone(db,owner,peer,delivery);
                String[] args={owner.toString(),peer.toString(),delivery};
                db.execSQL("DELETE FROM nexo_chat_outgoing WHERE account=? AND peer=? AND delivery=?",args);
                db.execSQL("DELETE FROM nexo_chat_restored WHERE account=? AND peer=? AND delivery=?",args);
                db.execSQL("DELETE FROM nexo_chat_reads WHERE account=? AND peer=? AND delivery=?",args);
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }

    private static boolean tableExists(SQLiteDatabase db,String name){
        try(Cursor c=db.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",new String[]{name})){
            return c.moveToFirst();
        }
    }

    /** Deletes all local outgoing/restored chat copies, hides previously
     * received chat messages and keeps identity pins, contacts and receipts. */
    public static void deleteConversation(SQLiteDatabase db,UUID owner,UUID peer,long now){
        if(now<=0)throw new IllegalArgumentException("Invalid deletion time");
        schema(db);
        db.beginTransaction();
        try{
            if(tableExists(db,"nexo_chat_outgoing")){
                try(Cursor c=db.rawQuery("SELECT delivery FROM nexo_chat_outgoing WHERE account=? AND peer=?",
                        new String[]{owner.toString(),peer.toString()})){
                    while(c.moveToNext())tombstone(db,owner,peer,c.getString(0));
                }
                db.execSQL("DELETE FROM nexo_chat_outgoing WHERE account=? AND peer=?",
                        new Object[]{owner.toString(),peer.toString()});
            }
            if(tableExists(db,"nexo_chat_restored")){
                try(Cursor c=db.rawQuery("SELECT delivery FROM nexo_chat_restored WHERE account=? AND peer=?",
                        new String[]{owner.toString(),peer.toString()})){
                    while(c.moveToNext())tombstone(db,owner,peer,c.getString(0));
                }
                db.execSQL("DELETE FROM nexo_chat_restored WHERE account=? AND peer=?",
                        new Object[]{owner.toString(),peer.toString()});
            }
            if(tableExists(db,"nexo_connect_import_receipts")){
                try(Cursor c=db.rawQuery("SELECT delivery_id FROM nexo_connect_import_receipts "+
                        "WHERE sender_id=? AND entity_type='note'",new String[]{peer.toString()})){
                    while(c.moveToNext())tombstone(db,owner,peer,c.getString(0));
                }
            }
            if(tableExists(db,"nexo_chat_reads")){
                db.execSQL("DELETE FROM nexo_chat_reads WHERE account=? AND peer=?",
                        new Object[]{owner.toString(),peer.toString()});
            }
            db.execSQL("INSERT OR REPLACE INTO nexo_chat_cleared(account,peer,cleared_at) "+
                    "VALUES(?,?,?)",new Object[]{owner.toString(),peer.toString(),now});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
}

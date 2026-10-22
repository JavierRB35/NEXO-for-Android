package com.jruiz.nexo.connect.host;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;

import com.jruiz.nexo.BuildConfig;
import com.jruiz.nexo.connect.NexoConnectPayloadCodec;
import com.jruiz.nexo.connect.vault.NexoConnectBridgeProtocol;
import com.jruiz.nexo.data.DatabaseHelper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * DEBUG ONLY: ephemeral handoff from a Nexo-native share gesture to the
 * separately installed, already-enrolled pilot. The pilot owns all crypto
 * keys. This service only reads ONE approved Nexo record, then forgets it.
 *
 * DO NOT EXPORT without the matching signature-level permission, the
 * exact pilot package/UID and a one-time unpredictable ticket. This service
 * must NOT be included in release builds or started by any background job.
 */
public final class NexoConnectShareBridgeService extends Service {
    private static final Object LOCK=new Object();
    private static final long MAX_AGE_MS=15*60*1000L;
    private static Pending pending;

    private static final class Pending {
        final String ticket,section;
        final long localId,createdAt;
        Pending(String ticket,String section,long id){
            this.ticket=ticket;this.section=section;this.localId=id;
            this.createdAt=android.os.SystemClock.elapsedRealtime();
        }
    }

    /**
     * Stage a user-selected local row ID in volatile app memory only.
     * No plaintext is cached, put in Intent extras or written to disk.
     */
    static String stage(String section,long localId){
        if(!BuildConfig.DEBUG||localId<=0||!allowedSection(section))
            throw new SecurityException("Invalid host share selection");
        String ticket=UUID.randomUUID().toString();
        synchronized(LOCK){ pending=new Pending(ticket,section,localId); }
        return ticket;
    }

    private static boolean allowedSection(String value){
        return NexoConnectHostShare.ITEMS.equals(value)||
                NexoConnectHostShare.LISTS.equals(value)||
                NexoConnectHostShare.JOURNAL.equals(value)||
                NexoConnectHostShare.BOOKS.equals(value)||
                NexoConnectHostShare.MOVIES.equals(value);
    }

    private void requirePilotCaller(){
        if(!BuildConfig.DEBUG)throw new SecurityException("DEBUG only");
        int caller=Binder.getCallingUid();
        if(caller==Process.myUid())throw new SecurityException("Cross-app pilot required");
        PackageManager pm=getPackageManager();
        if(pm.checkSignatures(caller,Process.myUid())!=
                PackageManager.SIGNATURE_MATCH)
            throw new SecurityException("Application signing mismatch");
        String[] packages=pm.getPackagesForUid(caller);
        boolean pilot=false;
        if(packages!=null)for(String pkg:packages)
            if(NexoConnectBridgeProtocol.PILOT_PACKAGE.equals(pkg))pilot=true;
        if(!pilot)throw new SecurityException("Unauthorized caller package");
    }

    private final IBinder endpoint=new Binder(){
        @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)
                throws android.os.RemoteException {
            if(code!=NexoConnectBridgeProtocol.TRANSACTION_PULL)
                return super.onTransact(code,data,reply,flags);
            try {
                data.enforceInterface(NexoConnectBridgeProtocol.BINDER_DESCRIPTOR);
                requirePilotCaller();
                String ticket=data.readString();
                if(!NexoConnectBridgeProtocol.validTicket(ticket))
                    throw new SecurityException("Invalid handoff ticket");
                if(android.os.Build.VERSION.SDK_INT>=33)data.enforceNoDataAvail();

                final Pending selected;
                synchronized(LOCK){
                    selected=pending;
                    if(selected==null||!selected.ticket.equals(ticket)||
                            android.os.SystemClock.elapsedRealtime()-selected.createdAt>
                                    MAX_AGE_MS)
                        throw new SecurityException("Share expired or previously used");
                    pending=null; // consumed BEFORE opening the database
                }
                try(DatabaseHelper db=new DatabaseHelper(
                        NexoConnectShareBridgeService.this)){
                    NexoConnectHostShare.Draft draft=NexoConnectHostShare.prepare(
                            db,selected.section,selected.localId);
                    byte[] encoded=NexoConnectPayloadCodec.encode(
                            draft.entity,draft.safeData,null);
                    if(encoded.length>NexoConnectBridgeProtocol.MAX_METADATA_BYTES)
                        throw new SecurityException("Item too large for in-memory bridge");
                    reply.writeNoException();
                    reply.writeString(draft.entity);
                    reply.writeString(draft.safeData.toString());
                }
            }catch(Exception failure){
                // Never include private payload or filesystem info in the error.
                reply.writeException(new SecurityException(
                        "Nexo did not authorize or could not prepare this handoff"));
            }
            return true;
        }
    };

    @Override public IBinder onBind(Intent intent){
        if(!BuildConfig.DEBUG)return null;
        // Android also enforces the signature permission from the Manifest.
        // Caller UID is checked again INSIDE onTransact, not on a Handler.
        return endpoint;
    }
}

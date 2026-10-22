package com.jruiz.nexo.connect.vault;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.SystemClock;

import com.jruiz.nexo.connect.NexoConnectPayloadCodec;

import java.util.UUID;

/**
 * DEBUG/LAB only. A user in the verified pilot explicitly selects a received
 * list. A separate, same-signature Nexo app may pull ONE freshly reauthenticated
 * decrypted copy via Binder. Never exports Android Keystore keys, tokens,
 * trust pins, database paths, or plaintext Intent extras.
 */
public final class NexoConnectReceivedBridgeService extends Service {
    private static final Object LOCK = new Object();
    private static final long MAX_AGE_MS = 2 * 60 * 1000L;
    private static Pending pending;

    private static final class Pending {
        final String ticket;
        final UUID account, delivery;
        final long created = SystemClock.elapsedRealtime();
        Pending(String ticket, UUID account, UUID delivery) {
            this.ticket=ticket; this.account=account; this.delivery=delivery;
        }
    }

    /** Called ONLY by a user-approved button in the authenticated pilot UI. */
    static String stage(UUID account, UUID delivery) {
        if(account==null || delivery==null)
            throw new SecurityException("Missing verified list selection");
        String ticket=UUID.randomUUID().toString();
        synchronized(LOCK) { pending=new Pending(ticket,account,delivery); }
        return ticket;
    }

    private void requireNexoCaller() {
        int uid=Binder.getCallingUid();
        if(uid==Process.myUid())
            throw new SecurityException("Cross-application call required");
        PackageManager pm=getPackageManager();
        if(pm.checkSignatures(uid,Process.myUid())!=PackageManager.SIGNATURE_MATCH)
            throw new SecurityException("Signer mismatch");
        String[] packages=pm.getPackagesForUid(uid);
        boolean nexo=false;
        if(packages!=null)for(String pkg:packages)
            if(NexoConnectBridgeProtocol.HOST_PACKAGE.equals(pkg))nexo=true;
        if(!nexo)throw new SecurityException("Unexpected caller");
    }

    private final IBinder endpoint=new Binder() {
        @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)
                throws android.os.RemoteException {
            if(code!=NexoConnectBridgeProtocol.TRANSACTION_PULL_RECEIVED)
                return super.onTransact(code,data,reply,flags);
            try {
                data.enforceInterface(NexoConnectBridgeProtocol.RECEIVE_BINDER_DESCRIPTOR);
                requireNexoCaller();
                String ticket=data.readString();
                if(!NexoConnectBridgeProtocol.validTicket(ticket))
                    throw new SecurityException("Invalid ticket");
                if(android.os.Build.VERSION.SDK_INT>=33)data.enforceNoDataAvail();
                final Pending selection;
                synchronized(LOCK) {
                    selection=pending;
                    if(selection==null || !selection.ticket.equals(ticket) ||
                            SystemClock.elapsedRealtime()-selection.created>MAX_AGE_MS)
                        throw new SecurityException("Expired or consumed ticket");
                    pending=null; // consume BEFORE any disk access, including errors
                }
                try(SQLiteDatabase db=openOrCreateDatabase(
                        "nexo-connect-pilot.db",Context.MODE_PRIVATE,null)) {
                    AndroidPeerTrustStore trust=
                            new AndroidPeerTrustStore(getApplicationContext(),selection.account);
                    NexoConnectLocalListPromotion.VerifiedList verified=
                            NexoConnectLocalListPromotion.read(
                                    db,selection.account,selection.delivery,trust);
                    byte[] bytes=NexoConnectPayloadCodec.encode(
                            "list",verified.dataCopy(),null);
                    if(bytes.length>NexoConnectBridgeProtocol.MAX_METADATA_BYTES)
                        throw new SecurityException("List too large for bridge");
                    reply.writeNoException();
                    reply.writeString(selection.account.toString());
                    reply.writeString(selection.delivery.toString());
                    reply.writeString(verified.source.senderId.toString());
                    reply.writeString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
                    java.util.Arrays.fill(bytes,(byte)0);
                }
            }catch(Exception failure) {
                // Deliberately do not send any private, filesystem or crypto detail.
                reply.writeException(new SecurityException(
                        "This list is no longer available or verified for Nexo"));
            }
            return true;
        }
    };

    @Override public IBinder onBind(Intent intent) {
        if((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE)==0)
            return null;
        return endpoint;
    }
}

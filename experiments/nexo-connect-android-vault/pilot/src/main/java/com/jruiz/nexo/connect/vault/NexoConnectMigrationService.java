package com.jruiz.nexo.connect.vault;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.*;

public final class NexoConnectMigrationService extends Service {
    private final Binder endpoint=new Binder(){
        @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException {
            if(code!=1&&code!=2)return super.onTransact(code,data,reply,flags);
            try{
                data.enforceInterface(NexoConnectMigration.DESCRIPTOR);
                int uid=Binder.getCallingUid();PackageManager pm=getPackageManager();boolean host=false;
                String[] packages=pm.getPackagesForUid(uid);if(packages!=null)for(String p:packages)host|=NexoConnectMigration.HOST.equals(p);
                if(!NexoConnectMigration.PILOT.equals(getPackageName())||uid==android.os.Process.myUid()||!host||pm.checkSignatures(uid,android.os.Process.myUid())!=PackageManager.SIGNATURE_MATCH)throw new SecurityException();
                String ticket=data.readString();if(android.os.Build.VERSION.SDK_INT>=33)data.enforceNoDataAvail();
                if(ticket==null||!ticket.equals(NexoConnectMigration.state(NexoConnectMigrationService.this).getString("ticket","")))throw new SecurityException();
                if(code==1){byte[] bytes=NexoConnectMigration.loadArchive(NexoConnectMigrationService.this);reply.writeNoException();reply.writeByteArray(bytes);java.util.Arrays.fill(bytes,(byte)0);}
                else{NexoConnectMigration.retireSource(NexoConnectMigrationService.this,ticket);reply.writeNoException();}
            }catch(Exception error){reply.writeException(new SecurityException("Transfer unavailable"));}
            return true;
        }
    };
    @Override public IBinder onBind(Intent intent){return endpoint;}
}

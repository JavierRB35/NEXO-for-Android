package com.jruiz.nexo.connectui;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.widget.TextView;
import com.jruiz.nexo.connect.vault.NexoConnectMigration;

public final class NexoConnectMigrationActivity extends Activity {
    private ServiceConnection connection;private boolean bound,started;private String ticket;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);TextView text=new TextView(this);text.setText("Trayendo tu cuenta a Nexo…");text.setTextSize(20);text.setPadding(40,90,40,40);setContentView(text);
        try{
            if(getPackageManager().checkSignatures(getPackageName(),NexoConnectMigration.PILOT)!=PackageManager.SIGNATURE_MATCH)throw new SecurityException();
            if(NexoConnectMigration.state(this).getBoolean("targetPending",false)&&NexoConnectMigration.hasArchive(this)){
                ticket=NexoConnectMigration.state(this).getString("ticket",null);connect();return;
            }
            Intent open=new Intent().setClassName(NexoConnectMigration.PILOT,"com.jruiz.nexo.connect.vault.NexoConnectMigrationExportActivity");
            open.putExtra("request",NexoConnectMigration.request(this)).putExtra("publicKey",NexoConnectMigration.publicKey(this));startActivityForResult(open,61);
        }catch(Exception e){fail();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=61)return;
        if(result!=RESULT_OK||data==null){finish();return;}ticket=data.getStringExtra("ticket");connect();
    }
    private void connect(){
        connection=new ServiceConnection(){
            public void onServiceConnected(ComponentName n,IBinder binder){if(started)return;started=true;new Thread(()->{
                byte[] bytes=null;try{
                    if(NexoConnectMigration.hasArchive(NexoConnectMigrationActivity.this))bytes=NexoConnectMigration.loadArchive(NexoConnectMigrationActivity.this);
                    else{Parcel data=Parcel.obtain(),reply=Parcel.obtain();try{
                        data.writeInterfaceToken(NexoConnectMigration.DESCRIPTOR);data.writeString(ticket);
                        if(!binder.transact(1,data,reply,0))throw new SecurityException();reply.readException();bytes=reply.createByteArray();
                    }finally{data.recycle();reply.recycle();}}
                    NexoConnectMigration.importTarget(NexoConnectMigrationActivity.this,ticket,bytes);
                    Parcel data=Parcel.obtain(),reply=Parcel.obtain();try{
                        data.writeInterfaceToken(NexoConnectMigration.DESCRIPTOR);data.writeString(ticket);
                        if(!binder.transact(2,data,reply,0))throw new SecurityException();reply.readException();
                    }finally{data.recycle();reply.recycle();}
                    NexoConnectMigration.finishTarget(NexoConnectMigrationActivity.this);
                    runOnUiThread(()->{startActivity(new Intent(NexoConnectMigrationActivity.this,NexoConnectActivity.class));finish();});
                }catch(Exception e){runOnUiThread(()->fail());}finally{if(bytes!=null)java.util.Arrays.fill(bytes,(byte)0);}
            },"connect-import-account").start();}
            public void onServiceDisconnected(ComponentName n){}
        };
        try{bound=bindService(new Intent().setClassName(NexoConnectMigration.PILOT,"com.jruiz.nexo.connect.vault.NexoConnectMigrationService"),connection,BIND_AUTO_CREATE);if(!bound)fail();}catch(Exception e){fail();}
    }
    private void fail(){if(isFinishing()||isDestroyed())return;new AlertDialog.Builder(this).setTitle("No se pudo terminar el traslado")
        .setMessage("Tus datos siguen guardados. Mantén instalada Connect y vuelve a pulsar «Traer mi cuenta». Si acabas de actualizar Nexo, actualiza también Connect para realizar este único paso.")
        .setPositiveButton("Entendido",(d,w)->finish()).show();}
    @Override protected void onDestroy(){if(bound)unbindService(connection);super.onDestroy();}
}

package com.jruiz.nexo.connect.vault;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;

import com.jruiz.nexo.connect.NexoConnectPayloadCodec;

import org.json.JSONObject;

/**
 * Pilot-owned screen for a Nexo share. The original pilot account, QR pins and
 * Android Keystore keys remain in this APK. No device registration is made by
 * this activity. There is NO native data in the launching Intent.
 *
 * Only the Nexo app with the SAME signing certificate can expose its DEBUG
 * Binder share service. The service authenticates our actual caller UID and
 * consumes one random token before returning sanitized data in process memory.
 *
 * The regular pilot's receiver selection + explicit one-shot send confirmation
 * are still required after this handoff. Merely opening the activity NEVER
 * uploads or transmits anything.
 */
public final class NexoConnectPilotBridgeActivity extends NexoConnectPilotActivity {
    private String ticket;
    private boolean bound,requested;
    private ServiceConnection connection;

    @Override public void onCreate(Bundle state){
        ticket=getIntent()==null?null:
                getIntent().getStringExtra(NexoConnectBridgeProtocol.EXTRA_TICKET);
        super.onCreate(state);
    }

    @Override protected boolean addHostSetupActions(){
        if(!NexoConnectBridgeProtocol.validTicket(ticket)){
            screen("No hay ficha para compartir",false);
            label("Vuelve a Nexo, abre la ficha y pulsa Compartir.",15);
            button("Volver",this::finish);
            return true;
        }
        if(!hasSavedPilotSession()){
            screen("Conexión segura pendiente",false);
            label("Este piloto todavía no tiene una sesión recuperable en este "+
                    "teléfono. Ábrelo de forma normal para recuperar la "+
                    "misma cuenta y el mismo dispositivo verificado.",15);
            label("No crees otra cuenta ni sustituyas el dispositivo.",14);
            button("Volver a Nexo",this::finish);
            return true;
        }
        return false; // existing protected session -> 'Entrar', no new OTP
    }

    @Override protected void onVerifiedPilotReady(){
        if(requested||!NexoConnectBridgeProtocol.validTicket(ticket))return;
        requested=true;
        screen("Preparando ficha de Nexo",false);
        label("Recuperando la ficha elegida de forma privada. "+
                "Todavía no se ha enviado nada.",15);
        if(getPackageManager().checkSignatures(getPackageName(),
                NexoConnectBridgeProtocol.HOST_PACKAGE)!=PackageManager.SIGNATURE_MATCH){
            failure("La firma de Nexo no coincide con la de este piloto. "+
                    "No se ha compartido ningún dato.");
            return;
        }
        Intent bind=new Intent().setComponent(new ComponentName(
                NexoConnectBridgeProtocol.HOST_PACKAGE,
                NexoConnectBridgeProtocol.HOST_SERVICE));
        connection=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName component,IBinder binder){
                new Thread(()->pullOnce(binder),"nexo-connect-signed-ipc").start();
            }
            @Override public void onServiceDisconnected(ComponentName component){
                // No automatic replay after a disconnected one-time handoff.
            }
        };
        try{
            bound=bindService(bind,connection,Context.BIND_AUTO_CREATE);
            if(!bound)failure("Nexo no ha podido abrir el canal local seguro.");
        }catch(Exception ex){
            failure("El canal privado no está disponible o no tiene permisos.");
        }
    }

    private void pullOnce(IBinder endpoint){
        Parcel request=Parcel.obtain(),reply=Parcel.obtain();
        try {
            request.writeInterfaceToken(NexoConnectBridgeProtocol.BINDER_DESCRIPTOR);
            request.writeString(ticket);
            if(!endpoint.transact(NexoConnectBridgeProtocol.TRANSACTION_PULL,
                    request,reply,0))
                throw new SecurityException("Invalid bridge transaction");
            reply.readException();
            String entity=reply.readString();
            String json=reply.readString();
            if(entity==null||json==null||
                    json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>
                            NexoConnectBridgeProtocol.MAX_METADATA_BYTES)
                throw new SecurityException("Invalid bridge payload");
            // Both sides independently enforce the exact outgoing allowlist.
            JSONObject safe=NexoConnectPayloadCodec.sanitize(
                    entity,new JSONObject(json),null);
            if(NexoConnectPayloadCodec.encode(entity,safe,null).length>
                    NexoConnectBridgeProtocol.MAX_METADATA_BYTES)
                throw new SecurityException("Share too large");
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                ticket=null; // cannot replay on rotation or screen reopening
                offerHostEntity(entity,safe);
            });
        }catch(Exception ex){
            runOnUiThread(()->failure("No se pudo recuperar la ficha. "+
                    "Vuelve a Nexo y comparte de nuevo. Nunca se envió nada."));
        }finally{
            reply.recycle();
            request.recycle();
        }
    }

    private void failure(String explanation){
        if(isFinishing()||isDestroyed())return;
        new AlertDialog.Builder(this)
            .setTitle("No se ha enviado nada")
            .setMessage(explanation)
            .setPositiveButton("Volver a Nexo",(d,w)->finish())
            .show();
    }

    @Override protected void onDestroy(){
        if(bound&&connection!=null){
            try{unbindService(connection);}catch(IllegalArgumentException ignored){}
        }
        bound=false;
        super.onDestroy();
    }
}

package com.jruiz.nexo.connect.host;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.widget.TextView;
import android.database.sqlite.SQLiteDatabase;

import com.jruiz.nexo.BuildConfig;
import com.jruiz.nexo.connect.NexoConnectPayloadCodec;
import com.jruiz.nexo.connect.NexoConnectPilotListImporter;
import com.jruiz.nexo.connect.vault.NexoConnectBridgeProtocol;
import com.jruiz.nexo.data.DatabaseHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * DEBUG ONLY. The pilot must have already authenticated, unsealed and verified
 * a selected received list. This activity fetches it with a one-use opaque
 * ticket from a signature-permission protected Binder service, then asks for
 * a SECOND explicit consent before making an ordinary native Nexo copy.
 */
public final class NexoConnectReceivedListActivity extends Activity {
    private String ticket;
    private boolean bindingAttempted,requested,copying;
    private volatile boolean completed;
    private ServiceConnection connection;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView label=new TextView(this);
        label.setText("Preparando lista recibida desde Nexo Connect…");
        label.setTextSize(18);
        label.setPadding(28,48,28,28);
        setContentView(label);
        ticket=getIntent()==null?null:
                getIntent().getStringExtra(
                        NexoConnectBridgeProtocol.EXTRA_RECEIVED_TICKET);
        if(!BuildConfig.DEBUG ||
                !NexoConnectBridgeProtocol.validTicket(ticket)) {
            fail();
            return;
        }
        if(getPackageManager().checkSignatures(getPackageName(),
                NexoConnectBridgeProtocol.PILOT_PACKAGE)!=
                        PackageManager.SIGNATURE_MATCH) {
            fail();
            return;
        }
        connection=new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name,IBinder binder) {
                if(requested)return;
                requested=true;
                new Thread(()->pullVerifiedList(binder),
                        "nexo-connect-pilot-receive").start();
            }
            @Override public void onServiceDisconnected(ComponentName name) {
                if(!completed)runOnUiThread(()->fail());
            }
        };
        try {
            Intent service=new Intent();
            service.setClassName(NexoConnectBridgeProtocol.PILOT_PACKAGE,
                    NexoConnectBridgeProtocol.PILOT_RECEIVE_SERVICE);
            // Even a failed bind can leave a registered connection on some
            // Android versions; always attempt an explicit unbind.
            bindingAttempted=true;
            if(!bindService(service,connection,BIND_AUTO_CREATE)) {
                releaseBinding();
                fail();
            }
        }catch(Exception failure) {
            releaseBinding();
            fail();
        }
    }

    private static UUID canonical(String value) {
        UUID uuid=UUID.fromString(value);
        if(!uuid.toString().equals(value))
            throw new SecurityException("Invalid identity");
        return uuid;
    }

    private void pullVerifiedList(IBinder binder) {
        Parcel data=Parcel.obtain();
        Parcel reply=Parcel.obtain();
        try {
            data.writeInterfaceToken(
                    NexoConnectBridgeProtocol.RECEIVE_BINDER_DESCRIPTOR);
            data.writeString(ticket);
            if(!binder.transact(NexoConnectBridgeProtocol.TRANSACTION_PULL_RECEIVED,
                    data,reply,0))
                throw new SecurityException("Bridge rejected request");
            reply.readException();
            UUID account=canonical(reply.readString());
            UUID delivery=canonical(reply.readString());
            UUID sender=canonical(reply.readString());
            String json=reply.readString();
            if(json==null || json.getBytes(StandardCharsets.UTF_8).length>
                    NexoConnectBridgeProtocol.MAX_METADATA_BYTES)
                throw new SecurityException("Invalid bridge payload");
            JSONObject envelope=NexoConnectPayloadCodec.decodeDecrypted(
                    json.getBytes(StandardCharsets.UTF_8));
            if(!"list".equals(envelope.optString("entity","")))
                throw new SecurityException("Wrong entity type");
            JSONObject raw=envelope.optJSONObject("data");
            if(raw==null)throw new SecurityException("Empty list");
            JSONObject safe=NexoConnectPayloadCodec.sanitize(
                    "list",raw,raw.optJSONArray("entries"));
            if(safe.optString("title","").trim().isEmpty() ||
                    safe.optJSONArray("entries")==null)
                throw new SecurityException("Incomplete list");
            completed=true;
            runOnUiThread(()->review(account,delivery,sender,safe));
        }catch(Exception failure) {
            runOnUiThread(()->fail());
        }finally {
            reply.recycle();
            data.recycle();
            // The opaque ticket is one-use; the binding is no longer needed.
            runOnUiThread(this::releaseBinding);
        }
    }

    private void review(UUID account,UUID delivery,UUID sender,JSONObject safe) {
        if(isFinishing()||isDestroyed())return;
        JSONArray entries=safe.optJSONArray("entries");
        StringBuilder message=new StringBuilder();
        message.append("Lista: ").append(safe.optString("title"))
                .append("\nRemitente verificado: ")
                .append(sender.toString().substring(0,8))
                .append("\n\nElementos: ").append(entries.length());
        for(int i=0;i<Math.min(20,entries.length());i++){
            JSONObject item=entries.optJSONObject(i);
            if(item!=null)
                message.append("\n")
                        .append(item.optBoolean("checked",false)?"☑ ":"☐ ")
                        .append(item.optString("text",""));
        }
        if(entries.length()>20)
            message.append("\n… y ").append(entries.length()-20).append(" más");
        message.append("\n\nSe creará una nueva lista local en Nexo. ")
               .append("La copia nativa no tendrá el cifrado adicional de ")
               .append("Connect. La ficha cifrada original y las claves ")
               .append("del piloto permanecerán intactas.");
        new AlertDialog.Builder(this)
                .setTitle("¿Añadir a mis listas de Nexo?")
                .setMessage(message.toString())
                .setCancelable(false)
                .setNegativeButton("Cancelar",(d,w)->finish())
                .setPositiveButton("Añadir a Nexo",
                        (d,w)->copyApproved(account,delivery,sender,safe))
                .show();
    }

    private void copyApproved(UUID account,UUID delivery,UUID sender,JSONObject safe) {
        if(copying)return;
        copying=true;
        new Thread(()->{
            NexoConnectPilotListImporter.Result result;
            try(DatabaseHelper helper=new DatabaseHelper(this)) {
                SQLiteDatabase db=helper.getWritableDatabase();
                result=NexoConnectPilotListImporter.copyApproved(
                        db,account,delivery,sender,safe,true);
            }catch(Exception failure){
                runOnUiThread(()->{
                    copying=false;
                    fail();
                });
                return;
            }
            runOnUiThread(()->{
                copying=false;
                if(isFinishing()||isDestroyed())return;
                new AlertDialog.Builder(this)
                    .setTitle(result.alreadyCopied?
                            "La lista ya estaba en Nexo":"Lista añadida a Nexo")
                    .setMessage("Puedes encontrar la copia en Listas. "+
                            "La ficha cifrada original no ha cambiado.")
                    .setCancelable(false)
                    .setPositiveButton("Entendido",(d,w)->finish()).show();
            });
        },"nexo-connect-native-receive-copy").start();
    }

    private void fail() {
        if(isFinishing()||isDestroyed())return;
        new AlertDialog.Builder(this)
            .setTitle("No se pudo incorporar la lista")
            .setMessage("La operación no ha finalizado. Vuelve al piloto, "+
                    "comprueba que la ficha sigue verificada y selecciónala "+
                    "de nuevo. No se han exportado claves privadas.")
            .setCancelable(false)
            .setPositiveButton("Cerrar",(d,w)->finish()).show();
    }

    /** Always run on the main thread, including the failed-bind case. */
    private void releaseBinding() {
        if(bindingAttempted && connection!=null) {
            bindingAttempted=false;
            try {unbindService(connection);}
            catch(IllegalArgumentException ignored) {
                // No service connection was registered (bind failed early).
            }
        }
    }

    @Override protected void onDestroy() {
        releaseBinding();
        super.onDestroy();
    }
}

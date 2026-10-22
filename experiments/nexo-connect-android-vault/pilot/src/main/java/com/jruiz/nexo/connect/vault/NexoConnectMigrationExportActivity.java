package com.jruiz.nexo.connect.vault;
import android.app.*;
import android.os.Bundle;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.widget.TextView;

public final class NexoConnectMigrationExportActivity extends Activity {
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        if(!NexoConnectMigration.PILOT.equals(getPackageName())||!NexoConnectMigration.HOST.equals(getCallingPackage())||
            getPackageManager().checkSignatures(getPackageName(),NexoConnectMigration.HOST)!=PackageManager.SIGNATURE_MATCH){finish();return;}
        TextView text=new TextView(this);text.setText("Tu cuenta se trasladará a Nexo en este teléfono.");text.setTextSize(18);text.setPadding(32,64,32,32);setContentView(text);
        new AlertDialog.Builder(this).setTitle("Continuar en Nexo")
            .setMessage("Llevaremos tu cuenta, personas e historial a Nexo. A partir de entonces usarás Nexo para compartir y conversar.")
            .setNegativeButton("Ahora no",(d,w)->finish()).setPositiveButton("Traer mi cuenta",(d,w)->{
                text.setText("Preparando tu cuenta…");new Thread(()->{
                    try{
                        String ticket=NexoConnectMigration.prepareSource(this,getIntent().getStringExtra("request"),getIntent().getByteArrayExtra("publicKey"));
                        runOnUiThread(()->{setResult(RESULT_OK,new Intent().putExtra("ticket",ticket));finish();});
                    }catch(Exception failure){runOnUiThread(()->new AlertDialog.Builder(this).setTitle("No se pudo completar")
                        .setMessage("Tu cuenta sigue guardada. Vuelve a Nexo e inténtalo de nuevo. Si el historial es muy grande, conserva ambas aplicaciones hasta completar el traslado.")
                        .setPositiveButton("Cerrar",(d2,w2)->finish()).show());}
                },"connect-account-transfer").start();
            }).setCancelable(false).show();
    }
}

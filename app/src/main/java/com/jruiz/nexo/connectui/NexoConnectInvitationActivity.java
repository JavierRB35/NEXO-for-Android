package com.jruiz.nexo.connectui;
import android.app.*;
import android.content.*;
import android.os.Bundle;
import com.jruiz.nexo.connect.vault.NexoConnectInvitationQr;
/** Untrusted external invitations can only open a consent screen. */
public final class NexoConnectInvitationActivity extends Activity{
    @Override public void onCreate(Bundle state){super.onCreate(state);
        new Thread(()->{try{
            android.net.Uri uri=getIntent().getData();if(uri==null)uri=getIntent().getParcelableExtra(Intent.EXTRA_STREAM);if(uri==null||!"content".equals(uri.getScheme()))throw new SecurityException();
            byte[] bytes;try(java.io.InputStream in=getContentResolver().openInputStream(uri);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
                if(in==null)throw new java.io.IOException();byte[] buf=new byte[1024];int n;while((n=in.read(buf))!=-1){if(out.size()+n>4096)throw new SecurityException();out.write(buf,0,n);}bytes=out.toByteArray();}
            String raw=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);NexoConnectInvitationQr.parse(raw);
            runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Te han invitado a Nexo")
                .setMessage("Para aceptar y compartir con esta persona necesitas activar tu cuenta. Si ya tienes una, puedes entrar.")
                .setNegativeButton("Ahora no",(d,w)->finish()).setPositiveButton("Continuar",(d,w)->{
                    getSharedPreferences("nexo-connect-pending-invite",0).edit().putString("payload",raw).apply();startActivity(new Intent(this,NexoConnectActivity.class));finish();}).show());
        }catch(Exception e){runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Invitación no disponible").setMessage("Pide a la otra persona una nueva invitación.").setPositiveButton("Cerrar",(d,w)->finish()).show());}},"nexo-read-invite").start();
    }
}

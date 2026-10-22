package com.jruiz.nexo.connect.vault;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.crypto.tink.KeysetHandle;
import com.jruiz.nexo.connect.NexoConnectPayloadCodec;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.UUID;

/**
 * Standalone, experimental TWO-PHONE pilot. Do not integrate with stable Nexo.
 * Only email-verified, registered phone identities can sign network traffic.
 * All ciphertext is created and authenticated by vault classes.
 * Local notes and receipts never leave app-private SQLite except E2EE network.
 * No retries of ambiguous sends, device enrollments or QR invitations.
 *
 * Session/JWT is memory-only. Re-open app -> request a fresh email OTP.
 */
public class NexoConnectPilotActivity extends Activity {
    private static final String ORIGIN="https://fwvmoqrsqtpvpbyfclld.supabase.co";
    // Supabase PUBLIC publishable key, NEVER a service_role secret.
    private static final String PUBLIC_KEY="sb_publishable_rsDUyUEyuXcjkyKsSxZFUg_RezHhNUk";
    private static final int QR_SCAN=41;
    private static final int EMAIL=0,LOGIN=1,SECOND=2,ENROLL=3,READY=4;
    private int stage=EMAIL,generation;
    private boolean busy,replace;
    /** Explicitly selected LAB account flow; never implied by a host share. */
    private boolean hostAccountUi;
    private String accountEmail="",lastMessage="";
    private NexoConnectEmailOtpClient otp;
    private NexoConnectSessionVault sessionVault;
    private NexoConnectEmailOtpClient.VerifiedSession session;
    private NexoConnectLocalPhoneIdentity identity;
    private AndroidPeerTrustStore trusted;
    private NexoConnectSignedGatewayClient client;
    private LinearLayout column;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        try{
            otp=new NexoConnectEmailOtpClient(ORIGIN,PUBLIC_KEY);
            sessionVault=new NexoConnectSessionVault(this);
            if(sessionVault.exists()){
                try{accountEmail=sessionVault.restore().email;}
                catch(Exception e){lastMessage="No se puede recuperar la sesión anterior; utiliza un nuevo OTP.";}
            }
        }
        catch(Exception ex){new AlertDialog.Builder(this)
                .setMessage("Configuración de laboratorio inválida")
                .setPositiveButton("Cerrar",(d,w)->finish()).show();return;}
        showSetup();
    }

    protected final LinearLayout screen(String name,boolean back){
        ScrollView sc=new ScrollView(this);
        sc.setFillViewport(true);
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad=dp(18);
        body.setPadding(pad,pad,pad,pad);
        sc.addView(body);
        setContentView(sc);
        column=body;
        label("NEXO CONNECT · PRUEBAS",14);
        label(name,22);
        label("Solo laboratorio. No envíes datos personales reales.",13);
        if(back)button("← Volver",()->showHome());
        return body;
    }
    private int dp(int pixels){return (int)(pixels*getResources().getDisplayMetrics().density+0.5f);}
    protected final TextView label(String text,int font){
        TextView v=new TextView(this);
        v.setText(text);v.setTextSize(font);v.setTextIsSelectable(true);
        v.setPadding(0,dp(7),0,dp(7));column.addView(v);return v;
    }
    protected final Button button(String text,Runnable action){
        Button b=new Button(this);b.setText(text);b.setAllCaps(false);
        column.addView(b,new LinearLayout.LayoutParams(-1,-2));
        b.setOnClickListener(v->{if(!busy)action.run();});
        return b;
    }
    private EditText input(String hint,boolean numeric){
        EditText v=new EditText(this);
        v.setHint(hint);v.setSingleLine(!hint.equals("Mensaje"));
        v.setInputType(numeric?(InputType.TYPE_CLASS_NUMBER|
                InputType.TYPE_NUMBER_VARIATION_PASSWORD):
                (hint.equals("Mensaje")?InputType.TYPE_CLASS_TEXT|
                  InputType.TYPE_TEXT_FLAG_MULTI_LINE:InputType.TYPE_CLASS_TEXT));
        if(hint.equals("Mensaje"))v.setMinLines(3);
        column.addView(v,new LinearLayout.LayoutParams(-1,-2));
        return v;
    }
    private interface Work{Object call()throws Exception;}
    private interface Finished{void done(Object value);}
    private void runWork(Work work,Finished callback){
        if(busy)return;
        busy=true;
        lastMessage="Trabajando…";
        new Thread(()->{
            Object result=null;Exception error=null;
            try{result=work.call();}catch(Exception e){error=e;}
            Object value=result;Exception failure=error;
            runOnUiThread(()->{
                busy=false;
                if(failure!=null){
                    // Never display OTP, bearer token, private keys or HTTP body.
                    lastMessage="No se ha completado la operación ("+
                            failure.getClass().getSimpleName()+
                            "). Comprueba permisos, red y datos; NO reintentes "+
                            "automáticamente envíos inciertos.";
                    showError(lastMessage);
                }else{
                    lastMessage="Operación completada.";
                    callback.done(value);
                }
            });
        },"nexo-connect-pilot-worker").start();
    }
    private void showError(String text){
        new AlertDialog.Builder(this).setTitle("Nexo Connect Pruebas")
                .setMessage(text).setPositiveButton("Entendido",(d,w)->{
                    if(stage==READY)showHome();else showSetup();
                }).show();
    }

    /**
     * Host-specific, pre-authentication UI only. By default no action exists.
     * Never use this hook to send content, mint identities or access a network.
     */
    protected boolean addHostSetupActions(){return false;}

    /** Safe for a separate pilot subclass: never exposes session credentials. */
    protected final boolean hasSavedPilotSession(){
        return sessionVault!=null && sessionVault.exists();
    }

    /** Called only after account+own-device verification succeeded. */
    protected void onVerifiedPilotReady(){}

    /** A deliberate, reversible LAB-only action; never silently enroll. */
    protected final void openHostAccountSetup(){
        hostAccountUi=true;
        showSetup();
    }

    private void showSetup(){
        screen("Inicio seguro",false);
        // The integrated DEBUG host can render a share-first landing instead
        // of an OTP form. Standalone pilot keeps its original login flow.
        if(!hostAccountUi && addHostSetupActions())return;
        if(hostAccountUi)
            button("← Volver a la ficha",()->{
                hostAccountUi=false;
                showSetup();
            });
        label("Conecta tu cuenta para compartir fichas con personas autorizadas. "+
              "En esta prueba, cada emulador representa una cuenta distinta.",14);
        if(stage==EMAIL){
            EditText email=input("Correo de esta cuenta",false);
            email.setInputType(InputType.TYPE_CLASS_TEXT|
                    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            email.setText(accountEmail);
            if(sessionVault.exists()){
                label("Este teléfono tiene una sesión protegida. Entrar no envía correos.",14);
                button("Entrar",()->{
                    String address=email.getText().toString().trim();
                    runWork(()->{
                        NexoConnectSessionVault.Saved saved=sessionVault.restore();
                        if(!saved.email.equalsIgnoreCase(address))
                            throw new SecurityException("El correo no coincide con este teléfono");
                        NexoConnectEmailOtpClient.VerifiedSession restored=
                                otp.refreshSession(saved.refreshToken,saved.accountId,saved.email);
                        // Refresh tokens rotate. Save the NEW token before other work.
                        sessionVault.save(restored);
                        NexoConnectLocalPhoneIdentity local=
                                NexoConnectLocalPhoneIdentity.openOrCreate(
                                        getApplicationContext(),restored.accountId);
                        NexoConnectDeviceRecoveryClient.Outcome status=
                                new NexoConnectDeviceRecoveryClient(ORIGIN,PUBLIC_KEY,
                                        restored,restored.accountId,local.deviceId(),
                                        local.signingPrivate()).checkOnce();
                        if(status.state!=NexoConnectDeviceRecoveryClient.State.OWN_DEVICE_CONFIRMED)
                            throw new SecurityException("El teléfono debe volver a verificarse");
                        return new Object[]{restored,local,status};
                    },value->{
                        Object[] out=(Object[])value;
                        session=(NexoConnectEmailOtpClient.VerifiedSession)out[0];
                        identity=(NexoConnectLocalPhoneIdentity)out[1];
                        generation=((NexoConnectDeviceRecoveryClient.Outcome)out[2]).generation;
                        accountEmail=session.email;
                        activate();
                    });
                });
                button("Volver a verificar por correo (solo si falla Entrar)",()->{
                    // Explicit fallback: only wipes the encrypted session, not identity keys.
                    sessionVault.clear();
                    stage=EMAIL;
                    showSetup();
                });
                return;
            }
            CheckBox create=new CheckBox(this);
            create.setText("Crear cuenta nueva con este correo");
            column.addView(create);
            button("1. Solicitar código de acceso",()->{
                String address=email.getText().toString().trim();
                runWork(()->{otp.sendEmailCode(address,create.isChecked());
                    return address;},value->{
                    accountEmail=(String)value;stage=LOGIN;showSetup();
                });
            });
        }else if(stage==LOGIN){
            label("Introduce el PRIMER código recibido en "+accountEmail,15);
            EditText code=input("Primer código OTP",true);
            button("2. Verificar cuenta",()->{
                String entered=code.getText().toString();code.setText("");
                runWork(()->{
                    NexoConnectEmailOtpClient.VerifiedSession verified=
                            otp.verifyEmailCode(accountEmail,entered);
                    // Persist only a rotated refresh credential in Android Keystore.
                    // A later launch must still prove the SAME registered phone.
                    sessionVault.save(verified);
                    NexoConnectLocalPhoneIdentity local=
                            NexoConnectLocalPhoneIdentity.openOrCreate(
                                    getApplicationContext(),verified.accountId);
                    NexoConnectDeviceRecoveryClient.Outcome status=
                            new NexoConnectDeviceRecoveryClient(ORIGIN,PUBLIC_KEY,
                                    verified,verified.accountId,local.deviceId(),
                                    local.signingPrivate()).checkOnce();
                    return new Object[]{verified,local,status};
                },value->{
                    Object[] out=(Object[])value;
                    session=(NexoConnectEmailOtpClient.VerifiedSession)out[0];
                    identity=(NexoConnectLocalPhoneIdentity)out[1];
                    NexoConnectDeviceRecoveryClient.Outcome state=
                            (NexoConnectDeviceRecoveryClient.Outcome)out[2];
                    if(state.state==NexoConnectDeviceRecoveryClient.State.OWN_DEVICE_CONFIRMED){
                        generation=state.generation;activate();
                    }else if(state.state==NexoConnectDeviceRecoveryClient.State.INCONCLUSIVE){
                        showError("El servidor no ha podido comprobar la identidad. "+
                                "No se registrará otro dispositivo.");
                    }else{
                        replace=state.state==NexoConnectDeviceRecoveryClient.State.ANOTHER_DEVICE;
                        stage=SECOND;showSetup();
                    }
                });
            });
        }else if(stage==SECOND){
            label(replace?
                "Ya existe otro teléfono para esta cuenta. Sustituirlo revoca el anterior. "+
                "Continúa solo si es una sustitución autorizada.":
                "Tu cuenta todavía no tiene teléfono registrado. Necesitamos otro OTP.",
                15);
            button("3. Enviar SEGUNDO código nuevo",()->runWork(()->{
                otp.sendEmailCode(accountEmail,false);return Boolean.TRUE;
            },v->{stage=ENROLL;showSetup();}));
        }else if(stage==ENROLL){
            label("Introduce el SEGUNDO código, diferente del utilizado al entrar.",15);
            if(replace)label("ATENCIÓN: esta operación revocará el dispositivo anterior.",15);
            EditText code=input("Segundo código OTP",true);
            CheckBox consent=new CheckBox(this);
            consent.setText(replace?
                    "Autorizo sustituir y revocar el teléfono anterior":
                    "Autorizo registrar este teléfono para Nexo Connect");
            column.addView(consent);
            button("4. Registrar teléfono",()->{
                if(!consent.isChecked()){showError("Necesitamos tu consentimiento.");return;}
                String entered=code.getText().toString();code.setText("");
                runWork(()->new NexoConnectDeviceEnrollmentClient(ORIGIN,PUBLIC_KEY,
                        session,identity.accountId(),identity.deviceId(),
                        identity.signingPrivate(),identity.hpkePrivate())
                        .enroll(entered,replace,true),v->{
                            generation=(Integer)v;activate();
                        });
            });
        }
    }
    private void activate(){
        try{
            trusted=new AndroidPeerTrustStore(this,identity.accountId());
            client=new NexoConnectSignedGatewayClient(ORIGIN,PUBLIC_KEY,
                    session.accessTokenFor(identity.accountId(),
                            System.currentTimeMillis()),
                    identity.accountId(),identity.deviceId(),generation,
                    identity.signingPrivate());
            stage=READY;showHome();
            onVerifiedPilotReady();
        }catch(Exception ex){showError("No se puede verificar la sesión del teléfono");}
    }
    private void showHome(){
        if(stage!=READY){showSetup();return;}
        screen("Compartir con personas",false);
        label("Conectado como "+accountEmail,14);
        if(!lastMessage.isEmpty())label(lastMessage,13);
        button("Mi código para conectar",this::showOwnQr);
        button("Invitar a una persona",this::showInvite);
        button("Escanear invitación",this::openScanner);
        button("Abrir invitación guardada",this::openImageQr);
        button("Personas autorizadas",this::showContacts);
        button("Compartir una nota",this::chooseRecipient);
        // The standalone pilot has no host data access. A DEBUG-only Nexo
        // subclass may add a read-only, explicit user-selected local note.
        addHostActions();
        button("Bandeja de entrada",this::showInbox);
        button("Fichas recibidas",this::showReceivedNotes);
        button("Confirmaciones pendientes",this::retryAcks);
        label("Tus fichas se cifran para el destinatario y las personas "+
              "autorizadas se verifican antes de compartir.",13);
    }
    private void openScanner(){
        try{
            Intent intent=NexoConnectQrScannerActivity.forAuthenticatedAccount(
                    this,identity.accountId());
            if(intent.resolveActivity(getPackageManager())==null){
                showError("El lector QR no está incluido en esta instalación.");
                return;
            }
            startActivityForResult(intent,QR_SCAN);
        }catch(RuntimeException|LinkageError ex){
            Log.e("NexoConnectQr","No se pudo iniciar la actividad del escáner",ex);
            showError("Error abriendo el lector QR ("+
                    ex.getClass().getSimpleName()+"). Consulta el registro del sistema.");
        }
    }

    /**
     * Alternative to scanning with the emulator camera. A selected image is
     * decoded locally; it is NOT proof of identity on its own. The existing
     * independent dual-SHA-256 verification UI is mandatory before pinning.
     */
    private void openImageQr(){
        try{
            Intent intent=NexoConnectQrScannerActivity.forVerifiedQrImage(
                    this,identity.accountId());
            if(intent.resolveActivity(getPackageManager())==null){
                showError("No está disponible el importador QR en esta instalación.");
                return;
            }
            startActivityForResult(intent,QR_SCAN);
        }catch(RuntimeException|LinkageError ex){
            showError("No se pudo abrir la selección de imagen QR.");
        }
    }

    private void showOwnQr(){
        try{
            LinearLayout group=screen("Mi código de identidad",true);
            NexoConnectOwnQrView qr=new NexoConnectOwnQrView(this,
                    identity.accountId(),generation,
                    identity.signingPrivate(),identity.hpkePrivate());
            group.addView(qr,new LinearLayout.LayoutParams(-1,dp(650)));
        }catch(Exception ex){showError("No se pudo generar el código QR");}
    }
    private void showInvite(){
        runWork(()->{
            AndroidPeerTrustStore.PeerPin own=
                NexoConnectOwnQrView.publicIdentity(identity.accountId(),
                        generation,identity.signingPrivate(),identity.hpkePrivate());
            return client.createInvitation(own,3600);
        },value->{
            try{
                NexoConnectSignedGatewayClient.InvitationCreated invite=
                        (NexoConnectSignedGatewayClient.InvitationCreated)value;
                screen("Invitación privada",true);
                label("Muestra esta invitación a la persona con la que "+
                      "quieras conectar. Antes de autorizar, los dos deberéis "+
                      "comprobar los códigos de seguridad por otro medio.",14);
                Bitmap qr=NexoConnectOwnQrView.encodeQrBitmap(
                        invite.qrPayload,Math.max(384,
                                Math.min(getResources().getDisplayMetrics().widthPixels-50,720)));
                ImageView img=new ImageView(this);
                img.setImageBitmap(qr);img.setAdjustViewBounds(true);
                column.addView(img,new LinearLayout.LayoutParams(-1,-2));
                label("Invitación válida durante 60 minutos. No la publiques.",14);
            }catch(Exception e){showError("No se pudo mostrar el QR");}
        });
    }

    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(req!=QR_SCAN||stage!=READY)return;
        if(result!=RESULT_OK||data==null){
            lastMessage="Escaneo cancelado.";showHome();return;
        }
        String peer=data.getStringExtra(NexoConnectQrScannerActivity.RESULT_PEER_ID);
        String invitation=data.getStringExtra(
                NexoConnectQrScannerActivity.RESULT_INVITATION_QR);
        if(peer==null){showError("QR incompleto");return;}
        final UUID id;
        try{id=UUID.fromString(peer);}catch(Exception ex){showError("QR inválido");return;}
        if(invitation!=null){
            new AlertDialog.Builder(this).setTitle("Aceptar invitación")
                .setMessage("Has comprobado la identidad QR de tu contacto. "+
                    "¿Quieres enlazar y autorizar recibir mensajes de esta cuenta?")
                .setNegativeButton("Cancelar",(d,w)->showHome())
                .setPositiveButton("Sí, aceptar",(d,w)->runWork(()->{
                    client.acceptVerifiedInvitation(invitation,trusted,true);
                    client.verifyStoredPeer(trusted,id);
                    client.setReceiving(trusted,id,true);
                    return id;
                },v->{lastMessage="Vínculo solicitado y verificación de "+
                      "contacto completada. Debe verificarse también en el otro móvil.";
                      showHome();})).show();
        }else{
            lastMessage="Identidad QR guardada en este móvil. Si ya existe "+
                    "invitación aceptada, verifica el contacto en el servidor.";
            showHome();
        }
    }
    private void showContacts(){
        try{
            List<AndroidPeerTrustStore.PeerPin> peers=trusted.listActive();
            screen("Personas autorizadas",true);
            if(peers.isEmpty()){
                label("Todavía no tienes contactos. Puedes enviarles una invitación.",15);
                return;
            }
            for(AndroidPeerTrustStore.PeerPin p:peers){
                label("Contacto verificado · "+p.peerId.toString().substring(0,8),16);
                button("Detalles de seguridad",()->{
                    try{
                        new AlertDialog.Builder(this)
                            .setTitle("Verificación del contacto")
                            .setMessage("Identificador: "+p.peerId+
                                    "\n\nFirma SHA-256:\n"+p.signingFingerprint()+
                                    "\n\nCifrado SHA-256:\n"+p.encryptionFingerprint()+
                                    "\n\nEstos códigos permiten confirmar "+
                                    "que estás hablando con la persona correcta.")
                            .setPositiveButton("Cerrar",null).show();
                    }catch(Exception error){
                        showError("No se pudieron mostrar los detalles de seguridad");
                    }
                });
                button("Confirmar vínculo",()->runWork(()->{
                    client.verifyStoredPeer(trusted,p.peerId);return p.peerId;
                },v->{lastMessage="Verificación bilateral solicitada.";showHome();}));
                button("Permitir recepción",()->runWork(()->{
                    client.setReceiving(trusted,p.peerId,true);return p.peerId;
                },v->{lastMessage="Recepción autorizada para ese contacto.";showHome();}));
                button("Compartir una nota con este contacto",()->showCompose(p));
            }
        }catch(Exception ex){showError("No se pudo leer el almacén QR");}
    }
    /**
     * Hook for optional host-app actions. The standalone pilot leaves it empty.
     * Opening the screen never reads Nexo data or sends network requests.
     */
    protected void addHostActions(){}

    /**
     * An authenticated account identifier (not a bearer credential).
     * Never return the JWT or private keysets to host UI or WebView.
     */
    protected final UUID authenticatedAccountId(){
        if(stage!=READY || identity==null || session==null ||
                !session.accountId.equals(identity.accountId()))
            throw new SecurityException("Nexo Connect device is not verified");
        return identity.accountId();
    }

    /**
     * Host notes are staged ONLY IN MEMORY. This method is invoked after a
     * user manually selects an already saved note. The recipient must still
     * be explicitly chosen, the composed text reviewed, and the single send
     * confirmed; neither host content nor private keys are placed in Intents.
     */
    protected final void offerHostNote(String title,String body){
        if(stage!=READY){showSetup();return;}
        String safeTitle=title==null?"":title.trim();
        String safeBody=body==null?"":body;
        if(safeTitle.isEmpty()||safeTitle.length()>250||safeBody.length()>20000){
            showError("La nota supera el limite de texto del piloto (250 caracteres de titulo, 20000 de texto). No se ha enviado nada.");
            return;
        }
        chooseRecipient(safeTitle,safeBody);
    }

    /**
     * One generic signed-E2EE composer shared by notes, simple appointments,
     * lists, journal/quotes, books and movies. Host supplies only a previously
     * sanitized snapshot. Opening the composer NEVER sends automatically.
     * Standalone pilot's existing note composer remains unchanged.
     */
    protected final void offerHostEntity(String entity,JSONObject candidate) {
        if(stage!=READY){showSetup();return;}
        try {
            final byte[] serialized=NexoConnectPayloadCodec.encode(entity,candidate,null);
            final JSONObject clean=NexoConnectPayloadCodec
                    .decodeDecrypted(serialized).getJSONObject("data");
            final List<AndroidPeerTrustStore.PeerPin> peers=trusted.listActive();
            if(peers.isEmpty()){
                new AlertDialog.Builder(this).setTitle("Sin contactos autorizados")
                    .setMessage("Primero añade y verifica una persona desde Contactos. "+
                            "No se ha enviado nada.")
                    .setPositiveButton("Entendido",null).show();
                return;
            }
            String[] names=new String[peers.size()];
            for(int i=0;i<peers.size();i++)
                names[i]="Contacto verificado · "+peers.get(i).peerId.toString().substring(0,8);
            new AlertDialog.Builder(this)
                .setTitle("¿Con quién quieres compartir?")
                .setItems(names,(d,which)->composeHostEntity(entity,clean,peers.get(which)))
                .setNegativeButton("Cancelar",null).show();
        } catch(Exception rejected) {
            new AlertDialog.Builder(this).setTitle("No se puede compartir")
                .setMessage("La ficha no cumple todavía las condiciones "+
                        "de intercambio seguro. No se ha enviado nada.")
                .setPositiveButton("Entendido",null).show();
        }
    }

    private void composeHostEntity(String entity,JSONObject safe,
            AndroidPeerTrustStore.PeerPin peer) {
        screen("Revisar envío",true);
        String title=safe.optString("title","");
        label("Se compartirá una copia de: "+title,18);
        label("Tipo: "+friendlyEntity(entity),14);
        label("Para: contacto verificado "+peer.peerId.toString().substring(0,8),14);
        String text=safe.optString("body",
                safe.optString("summary",""));
        if(text.length()>500)text=text.substring(0,500)+"…";
        if(!text.isEmpty())label(text,14);
        JSONArray entries=safe.optJSONArray("entries");
        if(entries!=null)label("Elementos de la lista: "+entries.length(),14);
        label("Tus datos originales no se modificarán. No se adjuntarán "+
                "ficheros, fotografías ni rutas privadas.",13);
        button("Enviar copia cifrada",()->new AlertDialog.Builder(this)
            .setTitle("Confirmar envío")
            .setMessage("¿Enviar «"+title+"» a este contacto? "+
                    "El envío solo se intentará una vez.")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Enviar una vez",(dialog,which)->runWork(()->{
                AndroidPeerTrustStore.PeerPin latest=trusted.findActive(peer.peerId);
                if(latest==null||latest.generation!=peer.generation||
                        !java.security.MessageDigest.isEqual(
                             latest.signingPublicRaw(),peer.signingPublicRaw())||
                        !java.security.MessageDigest.isEqual(
                             latest.encryptionPublicRaw(),peer.encryptionPublicRaw()))
                    throw new SecurityException("Verificación del contacto revocada");
                KeysetHandle recipient=NexoConnectPinnedKeysets.recipientEncryption(latest);
                return client.sendApproved(identity,trusted,peer.peerId,recipient,
                        UUID.randomUUID(),UUID.randomUUID(),entity,safe,null,
                        System.currentTimeMillis()+60*60*1000,true);
            },result->{
                lastMessage="Copia cifrada enviada. Pide al destinatario "+
                        "que consulte su bandeja de entrada.";
                showHome();
            })).show());
    }

    private static String friendlyEntity(String entity) {
        switch(entity){
            case "appointment":return "Cita o evento";
            case "note":return "Nota";
            case "list":return "Lista";
            case "journal":return "Cuaderno o frase";
            case "book":return "Libro";
            case "movie":return "Película o serie";
            default:return "Ficha de Nexo";
        }
    }

    private void chooseRecipient(){chooseRecipient("","");}
    private void chooseRecipient(String proposedTitle,String proposedBody){
        try{
            List<AndroidPeerTrustStore.PeerPin> peers=trusted.listActive();
            if(peers.isEmpty()){showError("Escanea y verifica antes el QR del contacto");return;}
            String[] names=new String[peers.size()];
            for(int i=0;i<peers.size();i++)
                names[i]="Contacto verificado · "+
                        peers.get(i).peerId.toString().substring(0,8);
            new AlertDialog.Builder(this).setTitle("Destinatario verificado")
                .setItems(names,(d,which)->showCompose(peers.get(which),
                        proposedTitle,proposedBody))
                .setNegativeButton("Cancelar",null).show();
        }catch(Exception ex){showError("No se pudieron cargar los contactos");}
    }
    private void showCompose(AndroidPeerTrustStore.PeerPin peer){
        showCompose(peer,"","");
    }
    private void showCompose(AndroidPeerTrustStore.PeerPin peer,
            String proposedTitle,String proposedBody){
        screen("Enviar NOTA cifrada",true);
        label("Para: contacto verificado · "+
                peer.peerId.toString().substring(0,8),14);
        EditText title=input("Título",false),body=input("Mensaje",false);
        title.setText(proposedTitle);
        body.setText(proposedBody);
        if(!proposedTitle.isEmpty()||!proposedBody.isEmpty())
            label("Nota guardada en Nexo: revisa el titulo y el texto antes de confirmar. No se incluiran adjuntos.",13);
        button("Confirmar y enviar UNA vez",()->{
            String t=title.getText().toString().trim(),b=body.getText().toString();
            if(t.isEmpty()||t.length()>250||b.length()>20000){
                showError("Escribe un título y un mensaje breve");return;
            }
            new AlertDialog.Builder(this).setTitle("Confirmar envío cifrado")
                .setMessage("¿Compartir «"+t+"» con el contacto "+
                   peer.peerId.toString().substring(0,8)+"? "+
                   "No se reintentará automáticamente si falla Internet.")
                .setNegativeButton("No",null)
                .setPositiveButton("Enviar una vez",(d,w)->runWork(()->{
                    AndroidPeerTrustStore.PeerPin latest=
                            trusted.findActive(peer.peerId);
                    if(latest==null)throw new SecurityException("Contacto revocado");
                    KeysetHandle recipient=
                            NexoConnectPinnedKeysets.recipientEncryption(latest);
                    return client.sendApproved(identity,trusted,peer.peerId,
                            recipient,UUID.randomUUID(),UUID.randomUUID(),
                            "note",new JSONObject().put("title",t).put("body",b),
                            null,System.currentTimeMillis()+60*60*1000,true);
                },value->{
                    lastMessage="Solicitud de envío cifrada registrada: "+
                            value+". Pide al otro móvil que abra su bandeja.";
                    showHome();
                })).show();
        });
    }
    private void showInbox(){
        runWork(()->client.inbox(20,null),value->{
            JSONArray items=(JSONArray)value;
            screen("Bandeja de recepción",true);
            if(items.length()==0){
                label("Sin entregas pendientes. Comprueba el vínculo bilateral.",15);
                return;
            }
            for(int i=0;i<items.length();i++){
                JSONObject info=items.optJSONObject(i);
                if(info==null)continue;
                String id=info.optString("deliveryId","");
                String sender=info.optString("senderId","");
                label("De: contacto "+sender.substring(0,Math.min(8,sender.length())),14);
                button("Recibir ficha",()->acceptInboxItem(id,sender));
            }
        });
    }
    private void acceptInboxItem(String delivery,String sender){
        new AlertDialog.Builder(this).setTitle("Descifrar e importar")
            .setMessage("¿Recibir esta ficha del contacto "+
                sender.substring(0,Math.min(8,sender.length()))+
                "? Se comprobará su identidad antes de guardarla.")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Sí, importar",(d,w)->runWork(()->{
                UUID peer=UUID.fromString(sender);
                AndroidPeerTrustStore.PeerPin pin=trusted.findActive(peer);
                if(pin==null)throw new SecurityException("QR del remitente no verificado");
                try(SQLiteDatabase db=openPrivateDatabase()){
                    NexoConnectInboxCoordinator.ImportOutcome imported=
                            new NexoConnectInboxCoordinator(client)
                            .importApprovedFromTrust(db,UUID.fromString(delivery),
                                true,identity.hpkePrivate(),
                                NexoConnectPinnedKeysets.senderVerification(pin),
                                trusted,peer,identity.accountId(),generation,
                                System.currentTimeMillis());
                    return imported.acknowledged?
                            "Ficha recibida y confirmada":
                            "Ficha guardada. Confirmación pendiente; "+
                            "puedes reintentar SOLO confirmaciones";
                }
            },value->{lastMessage=(String)value;showHome();})).show();
    }
    private SQLiteDatabase openPrivateDatabase(){
        SQLiteDatabase db=openOrCreateDatabase("nexo-connect-pilot.db",
                Context.MODE_PRIVATE,null);
        db.execSQL("CREATE TABLE IF NOT EXISTS items("+
              "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
              "type TEXT NOT NULL DEFAULT 'note',"+
              "title TEXT NOT NULL,body TEXT NOT NULL DEFAULT '',"+
              "original_text TEXT NOT NULL DEFAULT '',"+
              "category TEXT NOT NULL DEFAULT 'Otros',tags TEXT NOT NULL DEFAULT '',"+
              "favorite INTEGER NOT NULL DEFAULT 0,"+
              "source TEXT NOT NULL DEFAULT 'manual',"+
              "note_title_manual INTEGER NOT NULL DEFAULT 0,"+
              "created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        return db;
    }
    private void showReceivedNotes(){
        try(SQLiteDatabase db=openPrivateDatabase();
            Cursor c=db.rawQuery("SELECT id,title,body FROM items ORDER BY id DESC LIMIT 100",null)){
            screen("Fichas recibidas (copia privada)",true);
            if(c.getCount()==0)label("No se han recibido fichas.",14);
            while(c.moveToNext()){
                long localId=c.getLong(0);
                String storedTitle=c.getString(1),storedBody=c.getString(2);
                if(NexoConnectSealedNoteStore.isSealed(storedBody)){
                    try(Cursor receipt=db.rawQuery(
                        "SELECT delivery_id,entity_type FROM nexo_connect_import_receipts "+
                        "WHERE local_id=?",
                        new String[]{String.valueOf(localId)})){
                        if(!receipt.moveToFirst())
                            throw new SecurityException("Missing signed import receipt");
                        UUID id=UUID.fromString(receipt.getString(0));
                        String receiptType=receipt.getString(1);
                        JSONObject inner=new NexoConnectSealedNoteStore(
                            identity.accountId(),id).read(storedBody);
                        JSONObject data=inner;
                        if(inner.has(NexoConnectSealedNoteStore.PACKED_ENTITY)) {
                            if(!receiptType.equals(inner.optString(
                                    NexoConnectSealedNoteStore.PACKED_ENTITY)))
                                throw new SecurityException("Receipt entity mismatch");
                            data=inner.getJSONObject(NexoConnectSealedNoteStore.PACKED_DATA);
                        }else if(!"note".equals(receiptType)){
                            throw new SecurityException("Legacy receipt entity mismatch");
                        }
                        label("■ "+friendlyEntity(receiptType)+" · "+
                                data.getString("title"),17);
                        String preview=data.optString("body",
                                data.optString("summary",""));
                        if(!preview.isEmpty())label(preview,15);
                        JSONArray elements=data.optJSONArray("entries");
                        if(elements!=null){
                            label("Elementos: "+elements.length(),14);
                            for(int j=0;j<elements.length();j++){
                                JSONObject entry=elements.optJSONObject(j);
                                if(entry!=null)label("• "+entry.optString("text",""),14);
                            }
                        }
                        if("list".equals(receiptType)) {
                            final UUID verifiedDelivery=id;
                            button("Añadir esta lista a Nexo",
                                    ()->handoffReceivedList(verifiedDelivery));
                        }
                    }catch(Exception invalidNote){
                        label("■ Ficha protegida inaccesible (clave o datos no válidos)",14);
                        // Never fall back to displaying unverified ciphertext.
                    }
                }else{
                    // Historical copies from earlier LAB APKs were already
                    // committed in plaintext; do not delete, hide or migrate
                    // their data without an explicit recovery/migration plan.
                    label("■ "+storedTitle,17);
                    label(storedBody,15);
                }
            }
        }catch(Exception ex){showError("No se pudo consultar el almacenamiento");}
    }

    /** User-initiated reverse bridge. Pilot owns all keys and reauthenticates
     * the saved encrypted list before issuing a one-use ticket. */
    private void handoffReceivedList(UUID delivery) {
        if(stage!=READY || identity==null || trusted==null || delivery==null) {
            showError("Debes recuperar tu cuenta verificada del piloto.");
            return;
        }
        if(getPackageManager().checkSignatures(getPackageName(),
                NexoConnectBridgeProtocol.HOST_PACKAGE)!=
                android.content.pm.PackageManager.SIGNATURE_MATCH) {
            showError("Nexo no está instalado con una firma compatible.");
            return;
        }
        try(SQLiteDatabase db=openPrivateDatabase()) {
            // Refuse an old, revoked or tampered source BEFORE showing consent.
            NexoConnectLocalListPromotion.read(
                    db,identity.accountId(),delivery,trusted);
        }catch(Exception invalid) {
            showError("La lista o el remitente ya no están verificados.");
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Continuar en Nexo")
            .setMessage("Se abrirá Nexo para revisar esta lista recibida. "+
                "Solo se transferirá una copia verificada entre las dos aplicaciones "+
                "protegidas por la misma firma, sin claves privadas, archivos ni "+
                "datos de la ficha en el enlace. Nexo solicitará una segunda "+
                "confirmación antes de crear una lista normal sin el cifrado "+
                "adicional de Connect.")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Continuar",(d,w)->{
                try {
                    String ticket=NexoConnectReceivedBridgeService.stage(
                            identity.accountId(),delivery);
                    Intent open=new Intent();
                    open.setClassName(NexoConnectBridgeProtocol.HOST_PACKAGE,
                            NexoConnectBridgeProtocol.HOST_RECEIVE_ACTIVITY);
                    open.putExtra(NexoConnectBridgeProtocol.EXTRA_RECEIVED_TICKET,
                            ticket);
                    startActivity(open);
                }catch(Exception failure) {
                    showError("No se pudo abrir Nexo de forma segura.");
                }
            }).show();
    }

    private void retryAcks(){
        new AlertDialog.Builder(this).setTitle("Confirmaciones pendientes")
            .setMessage("Se reintentará únicamente la confirmación de fichas "+
                    "ya guardadas. NO se descargará ni importará otra copia.")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Reconciliar",(d,w)->runWork(()->{
                try(SQLiteDatabase db=openPrivateDatabase()){
                    NexoConnectInboxCoordinator.RetryOutcome r=
                            new NexoConnectInboxCoordinator(client)
                                    .retryAcknowledgements(db,20);
                    return "Confirmadas: "+r.acceptedCount+
                        ", conflictos: "+r.conflictCount+
                        ", sin red: "+r.stoppedOffline;
                }
            },v->{lastMessage=(String)v;showHome();})).show();
    }
}

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

    private LinearLayout screen(String name,boolean back){
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
    private TextView label(String text,int font){
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
    protected void addHostSetupActions(){}

    private void showSetup(){
        screen("Inicio seguro",false);
        label("Necesitas dos CORREOS DISTINTOS, uno en cada móvil. "+
              "El primer código inicia sesión; otro diferente registra el teléfono.",14);
        // Only the integrated Nexo DEBUG subclass offers read-only previews.
        // Standalone pilot deliberately does not expose Nexo's SQLite.
        addHostSetupActions();
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
        }catch(Exception ex){showError("No se puede verificar la sesión del teléfono");}
    }
    private void showHome(){
        if(stage!=READY){showSetup();return;}
        screen("Dos móviles · intercambio cifrado",false);
        label("Cuenta: "+accountEmail,14);
        label("Dispositivo registrado: generación "+generation,14);
        if(!lastMessage.isEmpty())label(lastMessage,13);
        button("1. Mostrar mi QR público",this::showOwnQr);
        button("2. Crear invitación QR (caduca en 1 hora)",this::showInvite);
        button("3. Escanear QR de otro teléfono",this::openScanner);
        button("3a. Importar QR de imagen (sin cámara)",this::openImageQr);
        button("4. Contactos verificados",this::showContacts);
        button("5. Enviar nota cifrada",this::chooseRecipient);
        // The standalone pilot has no host data access. A DEBUG-only Nexo
        // subclass may add a read-only, explicit user-selected local note.
        addHostActions();
        button("6. Bandeja de entrada",this::showInbox);
        button("7. Notas recibidas",this::showReceivedNotes);
        button("8. Reintentar SOLO confirmaciones pendientes",this::retryAcks);
        label("El acceso JWT se mantiene en memoria. La sesión renovable se protege "+
              "con Android Keystore y al entrar se comprueba de nuevo este dispositivo.",13);
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
                label("Muestra este código QR al móvil B. B deberá comparar "+
                      "las dos huellas SHA-256 completas y aceptar. "+
                      "Una invitación NO sustituye la verificación de identidad.",14);
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
            screen("Contactos QR verificados",true);
            if(peers.isEmpty()){label("Sin contactos. Empieza con invitación QR.",15);return;}
            for(AndroidPeerTrustStore.PeerPin p:peers){
                label(p.peerId.toString(),15);
                label("Firma SHA256: "+p.signingFingerprint(),12);
                label("HPKE SHA256: "+p.encryptionFingerprint(),12);
                button("Verificar relación con servidor",()->runWork(()->{
                    client.verifyStoredPeer(trusted,p.peerId);return p.peerId;
                },v->{lastMessage="Verificación bilateral solicitada.";showHome();}));
                button("Autorizar recibir de este contacto",()->runWork(()->{
                    client.setReceiving(trusted,p.peerId,true);return p.peerId;
                },v->{lastMessage="Recepción autorizada para ese contacto.";showHome();}));
                button("Enviar nota a este contacto",()->showCompose(p));
            }
        }catch(Exception ex){showError("No se pudo leer el almacén QR");}
    }
    /**
     * Hook for optional host-app actions. The standalone pilot leaves it empty.
     * Opening the screen never reads Nexo data or sends network requests.
     */
    protected void addHostActions(){}

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

    private void chooseRecipient(){chooseRecipient("","");}
    private void chooseRecipient(String proposedTitle,String proposedBody){
        try{
            List<AndroidPeerTrustStore.PeerPin> peers=trusted.listActive();
            if(peers.isEmpty()){showError("Escanea y verifica antes el QR del contacto");return;}
            String[] names=new String[peers.size()];
            for(int i=0;i<peers.size();i++)names[i]=peers.get(i).peerId.toString();
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
        label("Destinatario fijado por QR: "+peer.peerId,14);
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
                .setMessage("Enviar «"+t+"» a "+peer.peerId+"? "+
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
                label("Remitente: "+sender,14);
                button("Recibir nota "+id,()->acceptInboxItem(id,sender));
            }
        });
    }
    private void acceptInboxItem(String delivery,String sender){
        new AlertDialog.Builder(this).setTitle("Descifrar e importar")
            .setMessage("¿Importar esta ficha de "+sender+
                "? Se verificará la firma QR del remitente antes de guardarla.")
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
                            "Nota importada; servidor confirmado":
                            "Nota guardada. Confirmación pendiente; "+
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
            screen("Notas recibidas (copia privada)",true);
            if(c.getCount()==0)label("No se han importado notas.",14);
            while(c.moveToNext()){
                long localId=c.getLong(0);
                String storedTitle=c.getString(1),storedBody=c.getString(2);
                if(NexoConnectSealedNoteStore.isSealed(storedBody)){
                    try(Cursor receipt=db.rawQuery(
                        "SELECT delivery_id FROM nexo_connect_import_receipts "+
                        "WHERE local_id=? AND entity_type='note'",
                        new String[]{String.valueOf(localId)})){
                        if(!receipt.moveToFirst())
                            throw new SecurityException("Missing signed import receipt");
                        UUID id=UUID.fromString(receipt.getString(0));
                        JSONObject note=new NexoConnectSealedNoteStore(
                            identity.accountId(),id).read(storedBody);
                        label("■ "+note.getString("title"),17);
                        label(note.optString("body",""),15);
                    }catch(Exception invalidNote){
                        label("■ Nota protegida inaccesible (clave o datos no válidos)",14);
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

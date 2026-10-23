package com.jruiz.nexo.connect.vault;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Build;
import android.Manifest;
import android.content.pm.PackageManager;
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
import java.util.ArrayList;
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
    static final String ORIGIN="https://fwvmoqrsqtpvpbyfclld.supabase.co";
    // Supabase PUBLIC publishable key, NEVER a service_role secret.
    static final String PUBLIC_KEY="sb_publishable_rsDUyUEyuXcjkyKsSxZFUg_RezHhNUk";
    static final String EXTRA_OPEN_INBOX="com.jruiz.nexo.connect.pilot.OPEN_INBOX";
    private static final int NOTIFICATION_CONSENT=73;
    private static final int QR_SCAN=41;
    protected static final int EMAIL=0,LOGIN=1,SECOND=2,ENROLL=3,READY=4;
    protected int stage=EMAIL,generation;
    protected boolean busy,replace;
    /** Explicitly selected LAB account flow; never implied by a host share. */
    private boolean hostAccountUi;
    private boolean retryEmailCodeWithSavedSession;
    protected String accountEmail="",lastMessage="";
    protected NexoConnectEmailOtpClient otp;
    protected NexoConnectSessionVault sessionVault;
    protected NexoConnectEmailOtpClient.VerifiedSession session;
    protected NexoConnectLocalPhoneIdentity identity;
    protected AndroidPeerTrustStore trusted;
    protected NexoConnectSignedGatewayClient client;
    protected LinearLayout column;

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
        if(NexoConnectMigration.blocked(this)&&NexoConnectMigration.PILOT.equals(getPackageName())){
            screen("Continúa en Nexo",false);
            label("Tu cuenta se está trasladando a Nexo. Abre Nexo para continuar.",16);
            button("Cerrar",this::finish);return;
        }
        showSetup();
    }

    protected final LinearLayout screen(String name,boolean back){
        ScrollView sc=new ScrollView(this);
        sc.setFillViewport(true);
        sc.setBackgroundColor(0xfff7f9fc);
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad=dp(18);
        body.setPadding(pad,pad,pad,pad);
        sc.addView(body);
        setContentView(sc);
        column=body;
        TextView brand=label("NEXO",13);
        brand.setTextColor(0xff0f766e);
        brand.setTypeface(null,android.graphics.Typeface.BOLD);
        TextView heading=label(name,24);
        heading.setTextColor(0xff142337);
        heading.setTypeface(null,android.graphics.Typeface.BOLD);

        if(back)button("← Volver",()->showHome());
        return body;
    }
    private int dp(int pixels){return (int)(pixels*getResources().getDisplayMetrics().density+0.5f);}
    protected final TextView label(String text,int font){
        TextView v=new TextView(this);
        v.setText(text);v.setTextSize(font);v.setTextIsSelectable(true);
        v.setTextColor(0xff142337);
        v.setPadding(0,dp(7),0,dp(9));column.addView(v);return v;
    }
    protected final Button button(String text,Runnable action){
        Button b=new Button(this);
        b.setText(text);b.setTextSize(16);b.setAllCaps(false);
        boolean secondary=text.startsWith("←")||text.startsWith("Volver")||
                text.startsWith("Seguir usando")||text.startsWith("Cancelar")||
                text.startsWith("Mi código")||text.startsWith("Detalles");
        android.graphics.drawable.GradientDrawable shape=
                new android.graphics.drawable.GradientDrawable();
        shape.setColor(secondary?android.graphics.Color.WHITE:0xff0f766e);
        shape.setCornerRadius(dp(13));
        if(secondary)shape.setStroke(dp(1),0xffcbd5e1);
        b.setBackground(shape);
        b.setTextColor(secondary?0xff0f766e:android.graphics.Color.WHITE);
        b.setMinHeight(dp(50));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(50));
        params.bottomMargin=dp(10);
        column.addView(b,params);
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
    protected final Object sessionRefreshLock(){return NexoConnectSessionVault.REFRESH_LOCK;}
    protected interface Work{Object call()throws Exception;}
    protected interface Finished{void done(Object value);}
    protected void runWork(Work work,Finished callback){
        if(busy)return;
        busy=true;
        lastMessage="Trabajando…";
        new Thread(()->{
            Object result=null;Exception error=null;
            try{synchronized(NexoConnectSessionVault.REFRESH_LOCK){if(NexoConnectMigration.blocked(this))throw new SecurityException();if(stage==READY)refreshSessionIfNeeded();result=work.call();}}catch(Exception e){error=e;}
            Object value=result;Exception failure=error;
            runOnUiThread(()->{
                busy=false;
                if(isFinishing()||isDestroyed())return;
                if(failure!=null){
                    // Never display OTP, bearer token, private keys or HTTP body.
                    lastMessage="No se ha completado la operación ("+
                            failure.getClass().getSimpleName()+
                            "). Comprueba permisos, red y datos; NO reintentes "+
                            "automáticamente envíos inciertos.";
                    showError(userFacingAuthError(failure));
                }else{
                    lastMessage="Operación completada.";
                    callback.done(value);
                }
            });
        },"nexo-connect-pilot-worker").start();
    }
    private static String userFacingAuthError(Exception failure){
        if(failure instanceof NexoConnectEmailOtpClient.AuthException){
            int status=((NexoConnectEmailOtpClient.AuthException)failure).httpStatus;
            if(status==429)
                return "Has solicitado códigos demasiado seguido. El servicio ha limitado los envíos. Espera unos minutos antes de volver a solicitar uno; tu cuenta y los datos de Nexo se conservan.";
            if(status==400||status==422)
                return "El servicio no ha aceptado el código o la solicitud. Comprueba el correo y el código recibido; si ha caducado, solicita uno nuevo cuando se permita.";
            if(status>=500)
                return "El servicio de verificación no está disponible ahora. No se han borrado tu sesión ni tus datos.";
        }
        if(failure instanceof NexoConnectSignedGatewayClient.TransportException){
            int code=((NexoConnectSignedGatewayClient.TransportException)failure).httpStatus;
            if(code==503)
                return "Nexo Connect no ha podido confirmar esta operación. Si estabas aceptando una invitación, puede haber caducado o el contacto ya estar vinculado. Abre Personas para comprobarlo o solicita una invitación nueva.";
            if(code==403)
                return "No se ha podido validar este teléfono o contacto. Comprueba que los dos estáis usando vuestra cuenta y la versión actual de Nexo.";
        }
        return "No hemos podido terminar la operación. Tus datos siguen guardados. Comprueba tu conexión y vuelve a intentarlo desde Personas.";
    }

    protected void showError(String text){
        new AlertDialog.Builder(this).setTitle("Nexo")
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
    /** Non-destructive fallback when a saved session cannot resume. */
    protected final void openHostEmailReverification(){
        retryEmailCodeWithSavedSession=true;
        stage=EMAIL;
        openHostAccountSetup();
    }

    protected void showSetup(){
        screen("Chat y compartir",false);
        // The integrated DEBUG host can render a share-first landing instead
        // of an OTP form. Standalone pilot keeps its original login flow.
        if(!hostAccountUi && addHostSetupActions())return;
        button("Seguir usando Nexo sin cuenta",this::finish);
        if(hostAccountUi)
            button("← Volver a la ficha",()->{
                hostAccountUi=false;
                showSetup();
            });
        label("Conecta tu cuenta para compartir fichas con personas autorizadas. "+
              "Las demás funciones de Nexo no necesitan cuenta.",14);
        if(stage==EMAIL){
            EditText email=input("Correo de esta cuenta",false);
            email.setInputType(InputType.TYPE_CLASS_TEXT|
                    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            email.setText(accountEmail);
            if(sessionVault.exists()&&!retryEmailCodeWithSavedSession){
                label("Hay una sesión protegida en este teléfono. Si el alta quedó incompleta, Entrar continuará la confirmación del teléfono.",14);
                button("Entrar / Continuar alta",this::resumeSavedSession);
                button("Volver a verificar mi correo",this::openHostEmailReverification);
                return;
            }
            if(sessionVault.exists())
                label("Puedes solicitar otro código sin borrar la sesión protegida ni la identidad de este teléfono.",14);
            CheckBox create=new CheckBox(this);
            create.setText("Crear cuenta nueva con este correo");
            column.addView(create);
            button("Recibir código",()->{
                String address=email.getText().toString().trim();
                runWork(()->{otp.sendEmailCode(address,create.isChecked());
                    return address;},value->{
                    accountEmail=(String)value;stage=LOGIN;showSetup();
                });
            });
        }else if(stage==LOGIN){
            label("Introduce el código recibido en "+accountEmail,15);
            EditText code=input("Código de acceso",true);
            button("Continuar",()->{
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
                "Tu cuenta todavía no tiene teléfono registrado. Necesitamos confirmar este teléfono.",
                15);
            button("Enviar código de confirmación",()->runWork(()->{
                otp.sendEmailCode(accountEmail,false);return Boolean.TRUE;
            },v->{stage=ENROLL;showSetup();}));
        }else if(stage==ENROLL){
            label("Introduce el SEGUNDO código, diferente del utilizado al entrar.",15);
            if(replace)label("ATENCIÓN: esta operación revocará el dispositivo anterior.",15);
            EditText code=input("Código de confirmación",true);
            CheckBox consent=new CheckBox(this);
            consent.setText(replace?
                    "Autorizo sustituir y revocar el teléfono anterior":
                    "Autorizo registrar este teléfono para Nexo Connect");
            column.addView(consent);
            button("Confirmar teléfono",()->{
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
    protected final void refreshSessionIfNeeded()throws Exception{
        if(session!=null&&session.expiresAtMillis-System.currentTimeMillis()>60000)return;
        var restored=NexoConnectSessionVault.refreshSavedSession(this,otp);
        if(identity==null||!identity.accountId().equals(restored.accountId))throw new SecurityException();
        var status=new NexoConnectDeviceRecoveryClient(ORIGIN,PUBLIC_KEY,restored,restored.accountId,identity.deviceId(),identity.signingPrivate()).checkOnce();
        if(status.state!=NexoConnectDeviceRecoveryClient.State.OWN_DEVICE_CONFIRMED||status.generation!=generation)throw new SecurityException();
        session=restored;client=new NexoConnectSignedGatewayClient(ORIGIN,PUBLIC_KEY,session.accessTokenFor(identity.accountId(),System.currentTimeMillis()),identity.accountId(),identity.deviceId(),generation,identity.signingPrivate());
    }
    protected final void resumeSavedSession(){
        runWork(()->{
            NexoConnectEmailOtpClient.VerifiedSession restored=NexoConnectSessionVault.refreshSavedSession(this,otp);
            NexoConnectLocalPhoneIdentity local=NexoConnectLocalPhoneIdentity.openOrCreate(this,restored.accountId);
            NexoConnectDeviceRecoveryClient.Outcome status=new NexoConnectDeviceRecoveryClient(ORIGIN,PUBLIC_KEY,restored,restored.accountId,local.deviceId(),local.signingPrivate()).checkOnce();
            if(status.state==NexoConnectDeviceRecoveryClient.State.INCONCLUSIVE)
                throw new SecurityException("No se pudo comprobar la identidad del teléfono");
            return new Object[]{restored,local,status};
        },value->{
            Object[] out=(Object[])value;
            session=(NexoConnectEmailOtpClient.VerifiedSession)out[0];
            identity=(NexoConnectLocalPhoneIdentity)out[1];
            NexoConnectDeviceRecoveryClient.Outcome status=(NexoConnectDeviceRecoveryClient.Outcome)out[2];
            accountEmail=session.email;
            if(status.state==NexoConnectDeviceRecoveryClient.State.OWN_DEVICE_CONFIRMED){
                generation=status.generation;
                activate();
            }else if(status.state==NexoConnectDeviceRecoveryClient.State.NO_REGISTERED_DEVICE||
                    status.state==NexoConnectDeviceRecoveryClient.State.ANOTHER_DEVICE){
                // Resuming an incomplete enrollment never sends an OTP or replaces a device.
                // The user must explicitly request a fresh second OTP and consent.
                replace=status.state==NexoConnectDeviceRecoveryClient.State.ANOTHER_DEVICE;
                stage=SECOND;
                openHostAccountSetup();
            }else showError("No se ha podido comprobar la identidad del teléfono.");
        });
    }
    private void activate(){
        try{
            trusted=new AndroidPeerTrustStore(this,identity.accountId());
            client=new NexoConnectSignedGatewayClient(ORIGIN,PUBLIC_KEY,
                    session.accessTokenFor(identity.accountId(),
                            System.currentTimeMillis()),
                    identity.accountId(),identity.deviceId(),generation,
                    identity.signingPrivate());
            stage=READY;
            // A scheduled job may run while the pilot is closed. Device
            // verification still precedes every background inbox query.
            try { NexoConnectInboxNoticeJob.schedule(this); }
            catch(RuntimeException unavailable){
                Log.w("NexoConnectNotice","Alerts could not be scheduled");
            }
            showHome();
            onVerifiedPilotReady();
            if(getIntent()!=null &&
                    getIntent().getBooleanExtra(EXTRA_OPEN_INBOX,false)){
                getIntent().removeExtra(EXTRA_OPEN_INBOX);
                showInbox();
            }
        }catch(Exception ex){showError("No se puede verificar la sesión del teléfono");}
    }
    protected void showHome(){
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
        if(Build.VERSION.SDK_INT>=33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=
                        PackageManager.PERMISSION_GRANTED){
            button("Activar avisos de fichas",()->
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_CONSENT));
        }else if(!NexoConnectInboxNoticeJob.permitted(this)){
            button("Revisar ajustes de avisos",()->
                startActivity(new Intent(
                    android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,
                            getPackageName())));
        }else if(!NexoConnectInboxNoticeJob.isScheduled(this)){
            label("Avisos permitidos, pero revisión automática NO programada.",12);
            button("Reactivar revisión automática",()->{
                try{
                    NexoConnectInboxNoticeJob.schedule(this);
                    lastMessage=NexoConnectInboxNoticeJob.isScheduled(this)?
                            "Revisión automática programada.":
                            "Android no ha programado la revisión.";
                    showHome();
                }catch(RuntimeException unavailable){
                    // Don't expose private environment data on screen.
                    lastMessage="Android no ha podido programar la revisión ("+
                            unavailable.getClass().getSimpleName()+").";
                    showHome();
                }
            });
        }else{
            label("Avisos permitidos y revisión programada. Android puede retrasar "+
                    "la comprobación para ahorrar batería.",12);
        }
        button("Fichas recibidas",this::showReceivedNotes);
        button("Confirmaciones pendientes",this::retryAcks);
        label("Tus fichas se cifran para el destinatario y las personas "+
              "autorizadas se verifican antes de compartir.",13);
    }

    @Override public void onRequestPermissionsResult(int code,String[] permissions,
            int[] results) {
        super.onRequestPermissionsResult(code,permissions,results);
        if(code==NOTIFICATION_CONSENT && stage==READY){
            // Denial is honored. The background job will not post without it.
            showHome();
        }
    }

    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        setIntent(intent);
        if(stage==READY && intent!=null &&
                intent.getBooleanExtra(EXTRA_OPEN_INBOX,false)){
            intent.removeExtra(EXTRA_OPEN_INBOX);
            showInbox();
        }
    }

    protected final void acceptPendingInvitation(String raw){
        startActivityForResult(NexoConnectQrScannerActivity.forInvitation(this,identity.accountId(),raw),QR_SCAN);
    }
    protected void onInvitationAccepted(){}
    protected void openScanner(){
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
     * independent safety-code check, full-key pinning and consent remain mandatory.
     */
    protected void openImageQr(){
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

    protected void showOwnQr(){
        try{
            LinearLayout group=screen("Mi código de identidad",true);
            NexoConnectOwnQrView qr=new NexoConnectOwnQrView(this,
                    identity.accountId(),generation,
                    identity.signingPrivate(),identity.hpkePrivate());
            group.addView(qr,new LinearLayout.LayoutParams(-1,dp(650)));
        }catch(Exception ex){showError("No se pudo generar el código QR");}
    }
    protected void showInvite(){
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
                label("Comparte este QR o envía la invitación por WhatsApp. Confirmad el siguiente código por llamada o en persona.",15);
                AndroidPeerTrustStore.PeerPin own=NexoConnectOwnQrView.publicIdentity(
                        identity.accountId(),generation,identity.signingPrivate(),identity.hpkePrivate());
                TextView safety=label("Mi código: "+
                        NexoConnectSafetyCode.grouped(NexoConnectSafetyCode.forPeer(own)),20);
                safety.setTextColor(0xff0f766e);
                safety.setTypeface(null,android.graphics.Typeface.BOLD);
                Bitmap qr=NexoConnectOwnQrView.encodeQrBitmap(
                        invite.qrPayload,Math.max(384,
                                Math.min(getResources().getDisplayMetrics().widthPixels-50,720)));
                ImageView img=new ImageView(this);
                img.setImageBitmap(qr);img.setAdjustViewBounds(true);
                column.addView(img,new LinearLayout.LayoutParams(-1,-2));
                label("Invitación válida durante 60 minutos. No la publiques.",14);
                onInvitationCreated(invite.qrPayload,qr);
            }catch(Exception e){showError("No se pudo mostrar el QR");}
        });
    }

    protected void onInvitationCreated(String payload,Bitmap qr){}
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
            new AlertDialog.Builder(this).setTitle("Añadir esta persona")
                .setMessage("Código de seguridad comprobado. ¿Quieres permitir conversar y compartir fichas con esta persona?")
                .setNegativeButton("Ahora no",(d,w)->showHome())
                .setPositiveButton("Sí, conectar",(d,w)->runWork(()->{
                    boolean newlyLinked=true;
                    try{
                        client.acceptVerifiedInvitation(invitation,trusted,true);
                    }catch(NexoConnectSignedGatewayClient.TransportException unexpected){
                        if(unexpected.httpStatus!=503)throw unexpected;
                        // Recovery is safe only if the signed server endpoint
                        // independently confirms a PREEXISTING active relationship
                        // to the locally fingerprint-pinned peer. Never bypass trust.
                        try{client.verifyStoredPeer(trusted,id);newlyLinked=false;}
                        catch(Exception unlinked){throw unexpected;}
                    }
                    if(newlyLinked)client.verifyStoredPeer(trusted,id);
                    client.setReceiving(trusted,id,true);
                    return id;
                },v->{onInvitationAccepted();
                    lastMessage="Identidad comprobada. Contacto conectado. La otra persona debe confirmar también tu código.";
                    showHome();})).show();
        }else{
            // The verified identity was pinned locally first. A second signed,
            // server-side check is required before allowing this contact to
            // send anything; mere possession of a QR never grants access.
            runWork(()->{
                try{
                    client.verifyStoredPeer(trusted,id);
                    client.setReceiving(trusted,id,true);
                    return Boolean.TRUE;
                }catch(NexoConnectSignedGatewayClient.TransportException status){
                    if(status.httpStatus==403||status.httpStatus==503)
                        return Boolean.FALSE;
                    throw status;
                }
            },out->{
                boolean connected=Boolean.TRUE.equals(out);
                new AlertDialog.Builder(this)
                    .setTitle(connected?"Contacto conectado":"Identidad comprobada")
                    .setMessage(connected?
                        "La otra persona ya está autorizada y podéis compartir mensajes y fichas.":
                        "El código es correcto y está guardado, pero el servidor todavía no ha confirmado la conexión. La otra persona debe aceptar tu invitación; luego podrás comprobarla desde Personas.")
                    .setPositiveButton("Entendido",(d,w)->showHome()).show();
            });
        }
    }
    /** Friendly local names: no cloud lookup, no identity or QR key change. */
    /** Avatar hook for the integrated Nexo host; no unsigned remote image fetches. */
    protected View contactAvatar(AndroidPeerTrustStore.PeerPin peer){
        String name=contactName(peer);
        TextView initial=new TextView(this);
        initial.setText(name.isEmpty()?"?":name.substring(0,1).toUpperCase(java.util.Locale.ROOT));
        initial.setTextSize(19);initial.setGravity(android.view.Gravity.CENTER);
        initial.setTextColor(0xffffffff);initial.setBackgroundColor(0xff0f766e);
        return initial;
    }
    private void chooseVerifiedPeers(List<AndroidPeerTrustStore.PeerPin> peers,
            java.util.function.Consumer<List<AndroidPeerTrustStore.PeerPin>> chosen){
        ScrollView scroll=new ScrollView(this);
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12),dp(8),dp(12),dp(8));scroll.addView(list);
        final boolean[] checked=new boolean[peers.size()];
        for(int i=0;i<peers.size();i++){
            final int index=i;
            LinearLayout row=new LinearLayout(this);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8),dp(7),dp(8),dp(7));
            View photo=contactAvatar(peers.get(i));
            row.addView(photo,new LinearLayout.LayoutParams(dp(42),dp(42)));
            CheckBox check=new CheckBox(this);
            check.setText(contactName(peers.get(i)));
            check.setTextColor(0xff142337);check.setTextSize(16);
            check.setButtonTintList(android.content.res.ColorStateList.valueOf(0xff0f766e));
            check.setPadding(dp(10),0,0,0);
            check.setOnCheckedChangeListener((button,value)->checked[index]=value);
            row.addView(check,new LinearLayout.LayoutParams(0,dp(52),1));
            row.setOnClickListener(v->check.setChecked(!check.isChecked()));
            list.addView(row);
        }
        new AlertDialog.Builder(this).setTitle("¿A quién se la enviamos?")
            .setView(scroll).setNegativeButton("Cancelar",null)
            .setPositiveButton("Continuar",(dialog,which)->{
                List<AndroidPeerTrustStore.PeerPin> selected=new ArrayList<>();
                for(int i=0;i<peers.size();i++)if(checked[i])selected.add(peers.get(i));
                if(selected.isEmpty()||selected.size()>NexoConnectSendBatch.MAX_RECIPIENTS){
                    showError("Selecciona de 1 a "+NexoConnectSendBatch.MAX_RECIPIENTS+" personas.");
                }else chosen.accept(selected);
            }).show();
    }
    protected String contactName(AndroidPeerTrustStore.PeerPin peer) {
        if(peer==null)return "Contacto";
        String fallback="Contacto "+peer.peerId.toString().substring(0,8);
        if(identity==null)return fallback;
        String name=getSharedPreferences("nexo-connect-local-names",MODE_PRIVATE)
            .getString(identity.accountId()+"_"+peer.peerId,"").trim();
        return name.isEmpty()?fallback:name;
    }

    private void renameContact(AndroidPeerTrustStore.PeerPin peer) {
        try {
            if(identity==null || trusted==null ||
                    trusted.findActive(peer.peerId)==null) {
                showError("Este contacto ya no está disponible.");
                return;
            }
        }catch(Exception invalid) {
            showError("No se ha podido verificar el contacto.");
            return;
        }
        EditText input=new EditText(this);
        input.setSingleLine(true);
        input.setHint("Nombre, por ejemplo Pepito");
        input.setText(contactName(peer).startsWith("Contacto ")?"":
                contactName(peer));
        new AlertDialog.Builder(this)
            .setTitle("Nombre de este contacto")
            .setMessage("El nombre solo se guarda en tu móvil. No cambia su identidad verificada.")
            .setView(input)
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Guardar",(d,w)->{
                String name=input.getText().toString().trim();
                if(name.length()>50 || name.contains("\n") || name.contains("\r")) {
                    showError("Usa un nombre de hasta 50 caracteres.");
                    return;
                }
                getSharedPreferences("nexo-connect-local-names",MODE_PRIVATE)
                    .edit().putString(identity.accountId()+"_"+peer.peerId,name).apply();
                showContacts();
            }).show();
    }

    protected void showContacts(){
        try{
            List<AndroidPeerTrustStore.PeerPin> peers=trusted.listActive();
            screen("Personas autorizadas",true);
            if(peers.isEmpty()){
                label("Todavía no tienes contactos. Puedes enviarles una invitación.",15);
                return;
            }
            for(AndroidPeerTrustStore.PeerPin p:peers){
                label(contactName(p),16);
                button("Cambiar nombre",()->renameContact(p));
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
                new AlertDialog.Builder(this).setTitle("Aún no tienes contactos")
                    .setMessage("Añade antes una persona de confianza. No se ha enviado nada.")
                    .setPositiveButton("Entendido",null).show();
                return;
            }
            chooseVerifiedPeers(peers,selected->composeHostEntity(entity,clean,selected));
        }catch(Exception rejected){
            new AlertDialog.Builder(this).setTitle("No se puede compartir")
                .setMessage("Esta ficha no se puede enviar todavía. No se ha enviado nada.")
                .setPositiveButton("Entendido",null).show();
        }
    }

    /** One explicit send action, independently encrypted for each recipient.
     * Confirmed deliveries stay confirmed if a subsequent request is uncertain.
     * A failed/ambiguous request stops the batch; never auto-retry. */
    private void composeHostEntity(String entity,JSONObject safe,
            List<AndroidPeerTrustStore.PeerPin> recipients) {
        screen("Revisar envío",true);
        String title=safe.optString("title","");
        label("Compartir: "+title,18);
        label("Tipo: "+friendlyEntity(entity),14);
        label("Personas: "+recipients.size(),14);
        for(AndroidPeerTrustStore.PeerPin person:recipients)
            label("• "+contactName(person),14);
        JSONArray entries=safe.optJSONArray("entries");
        if(entries!=null)label("Elementos: "+entries.length(),14);
        label("Cada persona recibirá su propia copia cifrada. "+
                "Tus datos originales no se modificarán.",13);
        button("Enviar",()->runWork(()->{
            List<UUID> ids=new ArrayList<>();
            for(AndroidPeerTrustStore.PeerPin peer:recipients)
                ids.add(peer.peerId);
            return NexoConnectSendBatch.sendOnce(
                ids,identity.accountId(),true,recipientId->{
                    AndroidPeerTrustStore.PeerPin original=null;
                    for(AndroidPeerTrustStore.PeerPin p:recipients)
                        if(p.peerId.equals(recipientId)){original=p;break;}
                    if(original==null)throw new SecurityException("Recipient not selected");
                    AndroidPeerTrustStore.PeerPin latest=trusted.findActive(recipientId);
                    if(latest==null || latest.generation!=original.generation ||
                            !java.security.MessageDigest.isEqual(
                                    latest.signingPublicRaw(),original.signingPublicRaw()) ||
                            !java.security.MessageDigest.isEqual(
                                    latest.encryptionPublicRaw(),original.encryptionPublicRaw()))
                        throw new SecurityException("Recipient changed or revoked");
                    KeysetHandle key=NexoConnectPinnedKeysets.recipientEncryption(latest);
                    // Fresh delivery + request IDs for EVERY recipient; each has
                    // its own HPKE ciphertext. Never reuse another person's copy.
                    client.sendApproved(identity,trusted,recipientId,key,
                            UUID.randomUUID(),UUID.randomUUID(),entity,safe,null,
                            System.currentTimeMillis()+60*60*1000,true);
                });
        },result->{
            NexoConnectSendBatch.Result outcome=(NexoConnectSendBatch.Result)result;
            if(!outcome.interrupted){
                lastMessage=outcome.confirmed==1?
                    "Enviado correctamente a 1 persona.":
                    "Enviado correctamente a "+outcome.confirmed+" personas.";
                showHome();
            }else{
                showHome();
                new AlertDialog.Builder(this)
                    .setTitle("Envío parcialmente confirmado")
                    .setMessage("Envíos confirmados: "+outcome.confirmed+
                        " de "+outcome.requested+".\n"+
                        "No pudimos confirmar el siguiente envío; podría "+
                        "haberse entregado. Los restantes no se intentaron. "+
                        "Para evitar copias repetidas no reenvíes esta ficha "+
                        "hasta comprobarlo con los destinatarios.")
                    .setPositiveButton("Entendido",null).show();
            }
        }));
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
        try {
            List<AndroidPeerTrustStore.PeerPin> peers=trusted.listActive();
            if(peers.isEmpty()){
                showError("Añade antes una persona de confianza.");
                return;
            }
            chooseVerifiedPeers(peers,selection->
                showCompose(selection,proposedTitle,proposedBody));
        }catch(Exception ex){showError("No se pudieron cargar los contactos");}
    }
    private void showCompose(AndroidPeerTrustStore.PeerPin peer) {
        List<AndroidPeerTrustStore.PeerPin> one=new ArrayList<>();
        one.add(peer);
        showCompose(one,"","");
    }
    private void showCompose(List<AndroidPeerTrustStore.PeerPin> peers,
            String proposedTitle,String proposedBody) {
        screen("Enviar nota",true);
        label("Para "+peers.size()+(peers.size()==1?" persona:":" personas:"),14);
        for(AndroidPeerTrustStore.PeerPin p:peers)label("• "+contactName(p),14);
        EditText title=input("Título",false),body=input("Mensaje",false);
        title.setText(proposedTitle);
        body.setText(proposedBody);
        button("Enviar",()->{
            String t=title.getText().toString().trim(),b=body.getText().toString();
            if(t.isEmpty()||t.length()>250||b.length()>20000){
                showError("Escribe un título y un mensaje breve.");return;
            }
            runWork(()->{
                JSONObject safe=new JSONObject().put("title",t).put("body",b);
                List<UUID> ids=new ArrayList<>();
                for(AndroidPeerTrustStore.PeerPin peer:peers)ids.add(peer.peerId);
                return NexoConnectSendBatch.sendOnce(ids,identity.accountId(),true,id->{
                    AndroidPeerTrustStore.PeerPin old=null;
                    for(AndroidPeerTrustStore.PeerPin p:peers)
                        if(p.peerId.equals(id)){old=p;break;}
                    AndroidPeerTrustStore.PeerPin latest=trusted.findActive(id);
                    if(old==null || latest==null || latest.generation!=old.generation ||
                            !java.security.MessageDigest.isEqual(
                                    latest.signingPublicRaw(),old.signingPublicRaw()) ||
                            !java.security.MessageDigest.isEqual(
                                    latest.encryptionPublicRaw(),old.encryptionPublicRaw()))
                        throw new SecurityException("Contacto modificado");
                    client.sendApproved(identity,trusted,id,
                            NexoConnectPinnedKeysets.recipientEncryption(latest),
                            UUID.randomUUID(),UUID.randomUUID(),"note",safe,
                            null,System.currentTimeMillis()+60*60*1000,true);
                });
            },value->{
                NexoConnectSendBatch.Result r=(NexoConnectSendBatch.Result)value;
                showHome();
                if(!r.interrupted){
                    lastMessage="Nota enviada a "+r.confirmed+
                            (r.confirmed==1?" persona.":" personas.");
                    showHome();
                }else{
                    new AlertDialog.Builder(this)
                        .setTitle("Envío parcialmente confirmado")
                        .setMessage("Envíos confirmados: "+r.confirmed+
                                " de "+r.requested+". El siguiente podría "+
                                "haberse entregado, y el resto no se intentó. "+
                                "No reenvíes sin consultar antes.")
                        .setPositiveButton("Entendido",null).show();
                }
            });
        });
    }
    protected void showInbox(){
        runWork(()->{NexoConnectChatTransport.sync(this,client,identity,trusted,generation);return client.inbox(20,null);},value->{
            JSONArray items=(JSONArray)value;
            screen("Bandeja de recepción",true);
            if(items.length()==0){
                label("No hay fichas pendientes.",15);
                return;
            }
            for(int i=0;i<items.length();i++){
                JSONObject info=items.optJSONObject(i);
                if(info==null)continue;
                String id=info.optString("deliveryId","");
                String sender=info.optString("senderId","");
                String senderName="Una persona";
                try {
                    UUID from=UUID.fromString(sender);
                    AndroidPeerTrustStore.PeerPin peer=trusted.findActive(from);
                    if(peer!=null)senderName=contactName(peer);
                }catch(Exception malformed){}
                // The delivery remains encrypted; type is only a hint until
                // signed-envelope verification during explicit receipt.
                String type=info.optString("entityType","");
                String kind;
                switch(type) {
                    case "list":kind="una lista";break;
                    case "appointment":kind="una cita";break;
                    case "book":kind="un libro";break;
                    case "note":kind="una nota";break;
                    case "movie":kind="una película o serie";break;
                    case "journal":kind="una ficha";break;
                    default:kind="una ficha";
                }
                label(senderName+" te ha enviado "+kind+".",14);
                button("Ver y recibir",()->acceptInboxItem(id,sender));
            }
        });
    }
    private void acceptInboxItem(String delivery,String sender){
        new AlertDialog.Builder(this).setTitle("¿Recibir esta ficha?")
            .setMessage("Comprobaremos que procede de una persona autorizada antes de guardarla.")
            .setNegativeButton("No",null)
            .setPositiveButton("Sí",(d,w)->runWork(()->{
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
    protected void showReceivedNotes(){
        try(SQLiteDatabase db=openPrivateDatabase();
            Cursor c=db.rawQuery("SELECT id,title,body FROM items ORDER BY id DESC LIMIT 100",null)){
            screen("Fichas recibidas",true);
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
                        if(NexoConnectChatStore.CATEGORY.equals(data.optString("category")))continue;
                        label("■ "+friendlyEntity(receiptType)+" · "+
                                data.getString("title"),17);
                        String preview=data.optString("body",
                                data.optString("summary",""));
                        if(!preview.isEmpty())label(preview,15);
                        JSONArray elements=data.optJSONArray("entries");
                        if(elements!=null){
                            label("Elementos: "+elements.length(),14);
                            List<JSONObject> sorted=new ArrayList<>();
                            for(int j=0;j<elements.length();j++){
                                JSONObject entry=elements.optJSONObject(j);
                                if(entry!=null)sorted.add(entry);
                            }
                            // The pilot preview must use the SAME order as
                            // native Nexo (sort_order, then original position).
                            java.util.Collections.sort(sorted,
                                (a,b)->Integer.compare(
                                    a.optInt("sortOrder",Integer.MAX_VALUE),
                                    b.optInt("sortOrder",Integer.MAX_VALUE)));
                            for(JSONObject entry:sorted)
                                label("• "+entry.optString("text",""),14);
                        }
                        if(!receiptType.isEmpty()) {
                            final UUID verifiedDelivery=id;
                            button("Añadir esta ficha a Nexo",
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
    protected void handoffReceivedList(UUID delivery) {
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
            NexoConnectReceivedEntity.read(
                    db,identity.accountId(),delivery,trusted);
        }catch(Exception invalid) {
            showError("La ficha o el remitente ya no están verificados.");
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Continuar en Nexo")
            .setMessage("Se abrirá Nexo para revisar esta ficha recibida. "+
                "Solo se transferirá una copia verificada entre las dos aplicaciones "+
                "protegidas por la misma firma, sin claves privadas, archivos ni "+
                "datos de la ficha en el enlace. Nexo solicitará una segunda "+
                "confirmación antes de crear una copia en su sección sin el cifrado "+
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

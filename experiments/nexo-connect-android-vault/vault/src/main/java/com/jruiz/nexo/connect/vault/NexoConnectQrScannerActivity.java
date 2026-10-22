package com.jruiz.nexo.connect.vault;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.security.GeneralSecurityException;
import java.util.UUID;

/**
 * LAB: internal camera QR scanner -> independent SHA-256 verification UI.
 *
 * No contact becomes trusted from camera input alone. A trusted session owner
 * is supplied ONLY by an internal authenticated caller, never from a web link.
 * The Activity is android:exported=false in the isolated module manifest.
 *
 * ZXing embedded manages the CAMERA runtime permission within CaptureActivity.
 * This scanner never sends QR bytes to Supabase or other servers.
 */
public final class NexoConnectQrScannerActivity extends Activity {
    public static final String RESULT_INVITATION_QR = "nexo_connect_verified_invitation";
    public static final String RESULT_PEER_ID = "nexo_connect_verified_peer_id";
    public static final String RESULT_PEER_GENERATION = "nexo_connect_verified_generation";
    private static final String EXTRA_ACCOUNT = "nexo_connect_local_session_account";
    private static final String EXTRA_IMAGE_FIRST = "nexo_connect_import_image_first";
    private static final int IMAGE_PICKER = 72;
    private static final int MAX_QR_UTF16 = 1024;
    private UUID localAccount;
    private AndroidPeerTrustStore store;
    private LinearLayout screen;
    private boolean scannerLaunched;
    private boolean imagePickerLaunched;

    /** Called by a trusted, INTERNAL Nexo account flow after JWT+device check. */
    public static Intent forAuthenticatedAccount(Context from,UUID verifiedOwner){
        if(from==null||verifiedOwner==null)
            throw new IllegalArgumentException("Verified Nexo session required");
        return new Intent(from,NexoConnectQrScannerActivity.class)
                .putExtra(EXTRA_ACCOUNT,verifiedOwner.toString());
    }

    /** Camera-free route with the same human fingerprint check as ZXing camera. */
    public static Intent forVerifiedQrImage(Context from,UUID verifiedOwner){
        return forAuthenticatedAccount(from,verifiedOwner)
                .putExtra(EXTRA_IMAGE_FIRST,true);
    }

    public static Intent forInvitation(Context from,UUID owner,String raw){return forAuthenticatedAccount(from,owner).putExtra("pendingInvitation",raw);}
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            String owner=getIntent()==null?null:getIntent().getStringExtra(EXTRA_ACCOUNT);
            if(owner==null||!owner.matches(
                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
                throw new GeneralSecurityException("Missing verified account");
            localAccount=UUID.fromString(owner);
            store=new AndroidPeerTrustStore(this,localAccount);
        }catch(Exception e){
            setResult(RESULT_CANCELED);
            finish();
            return;
        }
        screen=new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        int dp=(int)(18*getResources().getDisplayMetrics().density);
        screen.setPadding(dp,dp,dp,dp);
        setContentView(screen);
        title("Nexo Connect · Verificar QR");
        title("Escanea el QR del contacto. Después tendrás que cotejar ambas "+
                "huellas SHA-256 por un canal de confianza distinto del QR.");
        Button scan=new Button(this);
        scan.setText("Abrir cámara y escanear QR");
        screen.addView(scan);
        scan.setOnClickListener(v->startScan());
        Button file=new Button(this);
        file.setText("Importar una imagen QR (sin cámara)");
        screen.addView(file);
        file.setOnClickListener(v->startImagePicker());
        if(state==null){
            if(getIntent().hasExtra("pendingInvitation"))showUntrustedQr(getIntent().getStringExtra("pendingInvitation"));
            else if(getIntent().getBooleanExtra(EXTRA_IMAGE_FIRST,false))
                startImagePicker();
            else startScan();
        }
    }

    private void startScan() {
        if(scannerLaunched||isFinishing())return;
        scannerLaunched=true;
        // This is an embedded Activity and works without a separate QR app.
        // LinkageErrors (missing packaged scanner runtime) are not Exceptions.
        try {
            IntentIntegrator integrator=new IntentIntegrator(this);
            integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
            integrator.setPrompt("Coloca el código QR Nexo dentro del recuadro");
            integrator.setBeepEnabled(false);
            integrator.setOrientationLocked(false);
            integrator.initiateScan();
        } catch(RuntimeException|LinkageError failure) {
            scannerLaunched=false;
            Log.e("NexoConnectQr","Error iniciando ZXing Camera Activity",failure);
            title("No se pudo abrir la cámara ("+
                    failure.getClass().getSimpleName()+
                    "). Puedes intentarlo otra vez.");
        }
    }

    private void startImagePicker(){
        if(imagePickerLaunched||scannerLaunched||isFinishing())return;
        imagePickerLaunched=true;
        try {
            Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            picker.setType("image/*");
            picker.addCategory(Intent.CATEGORY_OPENABLE);
            picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(picker,IMAGE_PICKER);
        }catch(RuntimeException problem){
            imagePickerLaunched=false;
            title("No se puede abrir el selector de imágenes.");
        }
    }

    private void receiveSelectedImage(int resultCode,Intent data){
        imagePickerLaunched=false;
        if(resultCode!=RESULT_OK||data==null||data.getData()==null){
            title("Importación cancelada. No se ha guardado ningún contacto.");
            return;
        }
        final Uri selected=data.getData();
        // Untrusted image reading and QR decoding stay strictly off the UI thread.
        new Thread(()->{
            String decoded=null;
            try {
                decoded=NexoConnectQrImageDecoder.readSelectedImage(
                        getApplicationContext(),selected);
            }catch(Exception ignored){
                // Never reveal file details or raw invitation tokens in logs/UI.
            }
            final String qr=decoded;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                if(qr==null){
                    title("La imagen no contiene un QR Nexo válido o es demasiado grande.");
                    return;
                }
                showUntrustedQr(qr);
            });
        },"nexo-qr-image-decode").start();
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        if(requestCode==IMAGE_PICKER){
            receiveSelectedImage(resultCode,data);
            return;
        }

        IntentResult scanned=IntentIntegrator.parseActivityResult(requestCode,resultCode,data);
        if(scanned==null){super.onActivityResult(requestCode,resultCode,data);return;}
        scannerLaunched=false;
        if(scanned.getContents()==null){
            title("Escaneo cancelado. Puedes intentarlo de nuevo.");
            return;
        }
        showUntrustedQr(scanned.getContents());
    }

    /** Shared path: both file and camera require independent full fingerprints. */
    private void showUntrustedQr(String raw){
        try {
            final NexoConnectInvitationQr invitation;
            final String peerQr;
            if(raw.contains("\"nexo-connect-invite\"")){
                invitation=NexoConnectInvitationQr.parse(raw);
                peerQr=invitation.publicPeerQr();
            }else{
                invitation=null;
                peerQr=raw;
            }
            requireScannedPeerQr(peerQr,localAccount);
            final QrTrustConfirmationView verification=new QrTrustConfirmationView(
                    this,peerQr,store,new QrTrustConfirmationView.Listener(){
                @Override public void onVerified(AndroidPeerTrustStore.PeerPin pin){
                    // This means LOCAL pin only; NOT proof of a server relationship.
                    Intent result=new Intent().putExtra(RESULT_PEER_ID,pin.peerId.toString())
                            .putExtra(RESULT_PEER_GENERATION,pin.generation);
                    // Invitation secret is returned only to the internal
                    // authenticated flow AFTER local QR trust is saved.
                    // This does not automatically call contact/accept.
                    if(invitation!=null){
                        try{result.putExtra(RESULT_INVITATION_QR,invitation.encode());}
                        catch(Exception ex){setResult(RESULT_CANCELED);finish();return;}
                    }
                    setResult(RESULT_OK,result);
                    finish();
                }
                @Override public void onFailure(String reason){
                    // QrTrustConfirmationView reports the error visibly and
                    // keeps user on verification without silently trusting.
                }
            });
            // UI must continue to require independent full hashes and consent.
            setContentView(verification);
        }catch(Exception invalid){
            title("QR inválido o no pertenece al protocolo Nexo Connect.");
        }
    }

    /** Only strictly parsed PUBLIC peer QR values reach the confirmation UI. */
    public static AndroidPeerTrustStore.PeerPin requireScannedPeerQr(
            String raw,UUID loggedInAccount)throws Exception {
        if(raw==null||raw.length()>MAX_QR_UTF16||loggedInAccount==null)
            throw new GeneralSecurityException("Invalid camera payload");
        AndroidPeerTrustStore.PeerPin pin=AndroidPeerTrustStore.parseQr(raw);
        if(pin.peerId.equals(loggedInAccount))
            throw new GeneralSecurityException("Cannot pair with yourself");
        return pin;
    }

    private void title(String value){
        TextView label=new TextView(this);
        label.setText(value);
        label.setTextSize(17);
        label.setPadding(0,0,0,20);
        screen.addView(label);
    }
}

package com.jruiz.nexo.connect.vault;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.Editable;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/**
 * VISUAL LAB COMPONENT, not wired to stable Nexo:
 * Displays an untrusted scanned QR and requires TWO full SHA-256 fingerprints
 * independently obtained from the OTHER phone or a trusted human channel.
 *
 * QR alone is NOT identity proof. Never pre-fill independent fingerprint
 * inputs using the scanned QR or any response from Supabase.
 *
 * Backend account linkage / enrollment / camera scanner / user identity
 * verification are separate operations still required before production.
 */
public final class QrTrustConfirmationView extends ScrollView {
    public interface Listener {
        void onVerified(AndroidPeerTrustStore.PeerPin pin);
        void onFailure(String reason);
    }

    private final AndroidPeerTrustStore store;
    private final AndroidPeerTrustStore.PeerPin candidate;
    private final String scannedQr;
    private final EditText independentSigning;
    private final EditText independentEncryption;
    private final CheckBox checkedChannel;
    private final CheckBox checkedConsent;
    private final Button verify;
    private final TextView status;
    private final Listener listener;
    private boolean inFlight;
    private boolean finished;

    public QrTrustConfirmationView(Context context, String scannedQr,
            AndroidPeerTrustStore store, Listener listener) throws Exception {
        super(context);
        if(store==null||listener==null)throw new IllegalArgumentException("Missing QR workflow");
        this.candidate=AndroidPeerTrustStore.parseQr(scannedQr);
        if(candidate.peerId.equals(store.ownerAccountId()))
            throw new GeneralSecurityException("Cannot verify own identity");
        this.scannedQr=scannedQr;
        this.store=store;
        this.listener=listener;
        setFillViewport(true);

        LinearLayout column=new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        int margin=dp(18);
        column.setPadding(margin,margin,margin,margin);
        addView(column,new ScrollView.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView title=label("Verificar contacto Nexo",20,true);
        column.addView(title);
        column.addView(label(
                "Confirma que es la persona con la que quieres conectar. Pídele sus dos códigos de seguridad " +
                "en persona o por un medio de confianza. Los encontrará en Mi código QR → Ver códigos de seguridad.",14,false));


        column.addView(label("Código de seguridad 1:",14,true));
        column.addView(fingerprint(candidate.signingFingerprint()));
        column.addView(label("Código de seguridad 2:",14,true));
        column.addView(fingerprint(candidate.encryptionFingerprint()));
        column.addView(label(
                "Introduce los dos códigos que has obtenido " +
                "directamente de la otra persona. No las copies de los valores mostrados arriba.",
                14,false));
        independentSigning=input("Código 1 de la otra persona");
        independentEncryption=input("Código 2 de la otra persona");
        column.addView(independentSigning);
        column.addView(independentEncryption);
        checkedChannel=new CheckBox(context);
        checkedChannel.setText("He comprobado los dos códigos con esta persona");
        column.addView(checkedChannel);
        checkedConsent=new CheckBox(context);
        checkedConsent.setText("Quiero conectar con esta persona");
        column.addView(checkedConsent);
        verify=new Button(context);
        verify.setText("Guardar persona");
        verify.setEnabled(false);
        column.addView(verify);
        status=label("",14,false);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        column.addView(status);

        TextWatcher validate=new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                refreshButton();
            }
            @Override public void afterTextChanged(Editable s){}
        };
        independentSigning.addTextChangedListener(validate);
        independentEncryption.addTextChangedListener(validate);
        checkedChannel.setOnCheckedChangeListener((button,isChecked)->refreshButton());
        checkedConsent.setOnCheckedChangeListener((button,isChecked)->refreshButton());
        verify.setOnClickListener(v->performVerification());
        refreshButton();
    }

    private void performVerification() {
        if(inFlight||finished)return;
        final String sign,encrypt;
        try{
            sign=normalizeFingerprint(independentSigning.getText().toString());
            encrypt=normalizeFingerprint(independentEncryption.getText().toString());
            requireHumanVerification(candidate,sign,encrypt,
                    checkedChannel.isChecked(),checkedConsent.isChecked());
        }catch(GeneralSecurityException ex){
            status.setText("Revisa que los dos códigos coincidan y confirma que quieres conectar.");
            listener.onFailure("fingerprint-mismatch");
            return;
        }
        // UI does not block on Keystore AES-GCM or AtomicFile fsync.
        inFlight=true;
        refreshButton();
        status.setText("Guardando el contacto verificado…");
        new Thread(()->{
            AndroidPeerTrustStore.PeerPin result=null;
            Exception problem=null;
            try {
                result=store.saveQrConfirmed(scannedQr,sign,encrypt,true);
            }catch(Exception ex){problem=ex;}
            final AndroidPeerTrustStore.PeerPin saved=result;
            final Exception failure=problem;
            new Handler(Looper.getMainLooper()).post(()->{
                inFlight=false;
                if(failure==null){
                    finished=true;
                    independentSigning.setEnabled(false);
                    independentEncryption.setEnabled(false);
                    checkedChannel.setEnabled(false);
                    checkedConsent.setEnabled(false);
                    status.setText("Contacto verificado y guardado en este teléfono.");
                    listener.onVerified(saved);
                }else{
                    status.setText(
                        "No se ha guardado. Puede haber cambiado la clave o fallado Keystore.");
                    listener.onFailure("trust-store-rejected");
                }
                refreshButton();
            });
        },"nexo-peer-pin-write").start();
    }

    private void refreshButton(){
        verify.setEnabled(!inFlight&&!finished&&checkedChannel.isChecked()&&
                checkedConsent.isChecked()&&
                plausible(independentSigning.getText().toString())&&
                plausible(independentEncryption.getText().toString()));
    }

    /**
     * Input is from INDEPENDENT verification, never from the scanned QR.
     * Group separators and upper case are presentation-only conveniences.
     */
    public static String normalizeFingerprint(String raw)
            throws GeneralSecurityException {
        if(raw==null||raw.length()>96)
            throw new GeneralSecurityException("Invalid fingerprint length");
        String cleaned=raw.toLowerCase(Locale.ROOT)
                .replace(" ","").replace("\n","")
                .replace(":","").replace("-","");
        if(!cleaned.matches("[0-9a-f]{64}"))
            throw new GeneralSecurityException("Invalid SHA-256 fingerprint");
        return cleaned;
    }

    public static void requireHumanVerification(AndroidPeerTrustStore.PeerPin pin,
            String independentSigning,String independentEncryption,
            boolean checkedIndependentChannel,boolean consent)
            throws GeneralSecurityException {
        if(pin==null||!pin.active||!checkedIndependentChannel||!consent)
            throw new GeneralSecurityException("Out-of-band verification required");
        if(!equal(pin.signingFingerprint(),independentSigning)||
                !equal(pin.encryptionFingerprint(),independentEncryption))
            throw new GeneralSecurityException("QR fingerprint mismatch");
    }

    private static boolean equal(String expected,String supplied){
        if(supplied==null||!supplied.matches("[0-9a-f]{64}"))return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                supplied.getBytes(StandardCharsets.US_ASCII));
    }
    private static boolean plausible(String text){
        try{return !TextUtils.isEmpty(normalizeFingerprint(text));}
        catch(GeneralSecurityException error){return false;}
    }

    private TextView label(String s,int sp,boolean bold){
        TextView view=new TextView(getContext());
        view.setText(s);
        view.setTextSize(sp);
        if(bold)view.setTypeface(null,android.graphics.Typeface.BOLD);
        view.setPadding(0,dp(7),0,dp(7));
        return view;
    }
    private TextView fingerprint(String value){
        TextView view=label(groups(value),14,false);
        view.setTextIsSelectable(true);
        view.setContentDescription("Huella de clave pública: "+value);
        return view;
    }
    private static String groups(String s){
        StringBuilder out=new StringBuilder();
        for(int i=0;i<s.length();i+=8){
            if(i>0)out.append(' ');
            out.append(s.substring(i,Math.min(i+8,s.length())));
        }
        return out.toString();
    }
    private EditText input(String hint){
        EditText e=new EditText(getContext());
        e.setHint(hint);
        e.setSingleLine(false);
        e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(96)});
        e.setMinLines(2);
        e.setMaxLines(3);
        e.setTextSize(14);
        return e;
    }
    private int dp(int pixels){
        return (int)(pixels*getResources().getDisplayMetrics().density+0.5f);
    }

    // Package-private UI harness: no production screens currently create it.
    Button buttonForTest(){return verify;}
    EditText signingInputForTest(){return independentSigning;}
    EditText encryptionInputForTest(){return independentEncryption;}
    CheckBox channelCheckForTest(){return checkedChannel;}
    CheckBox consentCheckForTest(){return checkedConsent;}
}

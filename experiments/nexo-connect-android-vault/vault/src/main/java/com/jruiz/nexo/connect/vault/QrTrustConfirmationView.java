package com.jruiz.nexo.connect.vault;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.security.GeneralSecurityException;

/**
 * Nexo onboarding: one independently communicated 64-bit human check code.
 * Full SHA-256 fingerprints are retained for local trust and server checks.
 * The code MUST come from the other person's own Nexo phone, never from
 * this scanned invitation or a server response.
 */
public final class QrTrustConfirmationView extends ScrollView {
    public interface Listener {
        void onVerified(AndroidPeerTrustStore.PeerPin pin);
        void onFailure(String reason);
    }
    private static final int GREEN=0xff0f766e,INK=0xff142337,MUTED=0xff526177,PAPER=0xfff7f9fc;
    private final AndroidPeerTrustStore store;
    private final AndroidPeerTrustStore.PeerPin candidate;
    private final String scannedQr;
    private final EditText independentCode;
    private final CheckBox checkedConsent;
    private final Button verify;
    private final TextView status;
    private final Listener listener;
    private boolean inFlight,finished;

    public QrTrustConfirmationView(Context context,String scannedQr,
            AndroidPeerTrustStore store,Listener listener)throws Exception {
        super(context);
        if(store==null||listener==null)throw new IllegalArgumentException("Missing QR workflow");
        this.candidate=AndroidPeerTrustStore.parseQr(scannedQr);
        if(candidate.peerId.equals(store.ownerAccountId()))
            throw new GeneralSecurityException("Cannot verify own identity");
        this.scannedQr=scannedQr;
        this.store=store;
        this.listener=listener;
        setFillViewport(true);
        setBackgroundColor(PAPER);

        LinearLayout column=new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(20),dp(24),dp(20),dp(24));
        addView(column,new ScrollView.LayoutParams(-1,-2));

        TextView brand=label("NEXO  ·  PERSONAS",13,true,GREEN);
        column.addView(brand);
        column.addView(label("Confirma tu contacto",25,true,INK));
        column.addView(label("Solo falta comprobar que estás hablando con la persona correcta.",16,false,MUTED));
        column.addView(label("1. Llámala o compruébalo en persona.\n2. Pídele el código que aparece en Personas → Mi código QR.\n3. Escríbelo aquí.",16,false,INK));

        column.addView(label("Código de seguridad",16,true,INK));
        independentCode=new EditText(context);
        independentCode.setHint("XXXX  XXXX  XXXX  XXXX");
        independentCode.setSingleLine(true);
        independentCode.setInputType(InputType.TYPE_CLASS_TEXT|
                InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS|
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        independentCode.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
        independentCode.setTextColor(INK);
        independentCode.setHintTextColor(MUTED);
        independentCode.setTextSize(19);
        independentCode.setPadding(dp(16),dp(12),dp(16),dp(12));
        independentCode.setBackground(surface(Color.WHITE,12,0xffcbd5e1));
        column.addView(independentCode,new LinearLayout.LayoutParams(-1,dp(56)));
        column.addView(label("Son 16 letras y números, en cuatro grupos. No necesitas copiar las firmas largas.",14,false,MUTED));

        checkedConsent=new CheckBox(context);
        checkedConsent.setText("La persona me ha facilitado el código por otro medio y quiero conectar con ella.");
        checkedConsent.setTextSize(15);
        checkedConsent.setTextColor(INK);
        checkedConsent.setButtonTintList(android.content.res.ColorStateList.valueOf(GREEN));
        LinearLayout.LayoutParams consentParams=new LinearLayout.LayoutParams(-1,-2);
        consentParams.topMargin=dp(12);
        column.addView(checkedConsent,consentParams);

        verify=new Button(context);
        verify.setText("Confirmar contacto");
        verify.setTextSize(16);
        verify.setAllCaps(false);
        verify.setTextColor(Color.WHITE);
        verify.setBackground(surface(GREEN,14,0));
        verify.setMinHeight(dp(52));
        LinearLayout.LayoutParams buttonParams=new LinearLayout.LayoutParams(-1,dp(52));
        buttonParams.topMargin=dp(14);
        column.addView(verify,buttonParams);
        status=label("",14,false,MUTED);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        column.addView(status);

        TextWatcher watcher=new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){refreshButton();}
            public void afterTextChanged(Editable s){}
        };
        independentCode.addTextChangedListener(watcher);
        checkedConsent.setOnCheckedChangeListener((b,checked)->refreshButton());
        verify.setOnClickListener(v->performVerification());
        refreshButton();
    }

    private void performVerification(){
        if(inFlight||finished)return;
        try {
            NexoConnectSafetyCode.verify(candidate,independentCode.getText().toString(),
                    checkedConsent.isChecked(),checkedConsent.isChecked());
        }catch(GeneralSecurityException ex){
            status.setText("Los códigos no coinciden. Pide que te lean el código desde su propio Nexo.");
            listener.onFailure("safety-code-mismatch");
            return;
        }
        inFlight=true;
        refreshButton();
        status.setText("Guardando el contacto de forma segura…");
        new Thread(()->{
            AndroidPeerTrustStore.PeerPin saved=null;
            Exception failure=null;
            try{
                // Full 256-bit public-key hashes remain the trust-store API contract.
                saved=store.saveQrConfirmed(scannedQr,candidate.signingFingerprint(),
                        candidate.encryptionFingerprint(),true);
            }catch(Exception ex){failure=ex;}
            final AndroidPeerTrustStore.PeerPin result=saved;
            final Exception problem=failure;
            new Handler(Looper.getMainLooper()).post(()->{
                inFlight=false;
                if(problem==null){
                    finished=true;
                    independentCode.setEnabled(false);
                    checkedConsent.setEnabled(false);
                    status.setText("Identidad comprobada. Ya puedes completar la conexión.");
                    listener.onVerified(result);
                }else{
                    status.setText("No se ha guardado el contacto. Vuelve a abrir su invitación.");
                    listener.onFailure("trust-store-rejected");
                }
                refreshButton();
            });
        },"nexo-peer-pin-write").start();
    }

    private void refreshButton(){
        boolean valid;
        try{NexoConnectSafetyCode.normalize(independentCode.getText().toString());valid=true;}
        catch(GeneralSecurityException ex){valid=false;}
        verify.setEnabled(!inFlight&&!finished&&checkedConsent.isChecked()&&valid);
        verify.setAlpha(verify.isEnabled()?1f:0.55f);
    }
    private TextView label(String value,int size,boolean bold,int color){
        TextView view=new TextView(getContext());
        view.setText(value);view.setTextSize(size);view.setTextColor(color);
        if(bold)view.setTypeface(null,Typeface.BOLD);
        view.setPadding(0,dp(6),0,dp(9));
        return view;
    }
    private GradientDrawable surface(int color,int radius,int stroke){
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(color);bg.setCornerRadius(dp(radius));
        if(stroke!=0)bg.setStroke(dp(1),stroke);
        return bg;
    }
    private int dp(int size){return Math.round(size*getResources().getDisplayMetrics().density);}

    Button buttonForTest(){return verify;}
    EditText safetyCodeInputForTest(){return independentCode;}
    CheckBox consentCheckForTest(){return checkedConsent;}
}

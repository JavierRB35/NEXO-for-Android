package com.jruiz.nexo.connect.vault;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Native, isolated 2-OTP account setup UI. Does not run in stable Nexo.
 *
 * The first code logs into Auth; the second, separate code proves recent
 * email ownership to the server before a device is registered/replaced.
 * No passwords, codes, JWT or private keys are displayed or logged.
 */
public final class NexoConnectOnboardingView extends ScrollView {
    public interface Listener {
        void onDeviceEnrolled(java.util.UUID accountId,int generation);
        void onRecoveryRequired();
    }
    private final NexoConnectOnboardingFlow flow;
    private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final LinearLayout content;
    private final TextView title;
    private final TextView instructions;
    private final TextView status;
    private final EditText email,code;
    private final CheckBox replace,consent,createAccount;
    private final Button next;
    private boolean busy;

    public NexoConnectOnboardingView(Context context,
            NexoConnectOnboardingFlow flow, Listener listener) {
        super(context);
        if(flow==null||listener==null)throw new IllegalArgumentException("Missing setup");
        this.flow=flow;
        this.listener=listener;
        setFillViewport(true);
        content=new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int space=dp(16);
        content.setPadding(space,space,space,space);
        addView(content,new ScrollView.LayoutParams(
                LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
        title=label("Nexo Connect · Configurar cuenta",20);
        instructions=label("",15);
        status=label("",14);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        email=new EditText(context);
        email.setSingleLine(true);
        email.setInputType(InputType.TYPE_CLASS_TEXT|
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        email.setHint("Correo de tu cuenta");
        code=new EditText(context);
        code.setSingleLine(true);
        code.setInputType(InputType.TYPE_CLASS_NUMBER|
                InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        code.setHint("Código recibido por correo");
        createAccount=new CheckBox(context);
        createAccount.setText("Crear cuenta nueva si todavía no existe");
        replace=new CheckBox(context);
        replace.setText("Sustituir el móvil autorizado (revoca el anterior)");
        consent=new CheckBox(context);
        consent.setText("Autorizo registrar o sustituir este teléfono");
        next=new Button(context);
        next.setOnClickListener(v->advance());
        content.addView(title);content.addView(instructions);
        content.addView(email);content.addView(code);
        content.addView(createAccount);content.addView(replace);
        content.addView(consent);content.addView(next);
        content.addView(status);
        render();
    }

    private void advance(){
        if(busy)return;
        final NexoConnectOnboardingFlow.Stage s=flow.stage();
        if(s==NexoConnectOnboardingFlow.Stage.COMPLETE||
                s==NexoConnectOnboardingFlow.Stage.RECOVER_FROM_UNKNOWN_RESULT)return;
        final String address=email.getText().toString();
        final String submitted=code.getText().toString();
        final boolean newAccount=createAccount.isChecked();
        final boolean replacement=replace.isChecked();
        final boolean approved=consent.isChecked();
        // No copying OTP into saved instance state, intents or logs.
        code.setText("");
        busy=true;
        render();
        new Thread(()->{
            Exception error=null;
            try {
                switch(s){
                    case REQUEST_LOGIN_CODE:flow.requestLogin(address,newAccount);break;
                    case VERIFY_LOGIN_CODE:flow.verifyLogin(submitted);break;
                    case REQUEST_DEVICE_CODE:flow.requestDeviceCode(replacement);break;
                    case ENROLL_DEVICE:flow.confirmDevice(submitted,approved);break;
                    default:throw new IllegalStateException("Invalid enrollment step");
                }
            }catch(Exception ex){error=ex;}
            final Exception result=error;
            main.post(()->{
                busy=false;
                if(result!=null) {
                    // Use generic language; do not leak OTP, tokens, SQL names
                    // or whether an email account exists.
                    status.setText(flow.stage()==
                            NexoConnectOnboardingFlow.Stage.RECOVER_FROM_UNKNOWN_RESULT
                        ?"Resultado incierto. No reintentes el código. Comprueba el "+
                          "estado del móvil y solicita otro código."
                        :"No se ha completado este paso. Comprueba los datos.");
                } else {
                    status.setText("Paso completado.");
                    if(flow.stage()==NexoConnectOnboardingFlow.Stage.COMPLETE)
                        listener.onDeviceEnrolled(flow.accountId(),flow.generation());
                }
                if(flow.stage()==
                        NexoConnectOnboardingFlow.Stage.RECOVER_FROM_UNKNOWN_RESULT)
                    listener.onRecoveryRequired();
                render();
            });
        },"nexo-connect-setup-worker").start();
    }

    private void render(){
        final NexoConnectOnboardingFlow.Stage s=flow.stage();
        boolean login=s==NexoConnectOnboardingFlow.Stage.REQUEST_LOGIN_CODE;
        boolean verify=s==NexoConnectOnboardingFlow.Stage.VERIFY_LOGIN_CODE;
        boolean ask=s==NexoConnectOnboardingFlow.Stage.REQUEST_DEVICE_CODE;
        boolean enroll=s==NexoConnectOnboardingFlow.Stage.ENROLL_DEVICE;
        email.setVisibility(login?View.VISIBLE:View.GONE);
        createAccount.setVisibility(login?View.VISIBLE:View.GONE);
        code.setVisibility(verify||enroll?View.VISIBLE:View.GONE);
        replace.setVisibility(ask?View.VISIBLE:View.GONE);
        consent.setVisibility(enroll?View.VISIBLE:View.GONE);
        next.setEnabled(!busy&&(login||verify||ask||(enroll&&consent.isChecked())));
        if(enroll)consent.setOnCheckedChangeListener((b,checked)->render());
        switch(s){
            case REQUEST_LOGIN_CODE:
                instructions.setText("Paso 1/4 · Solicitar código de acceso por correo.");
                next.setText("Enviar código de acceso");break;
            case VERIFY_LOGIN_CODE:
                instructions.setText("Paso 2/4 · Verificar el primer código para "+
                        "iniciar sesión.");
                next.setText("Verificar acceso");break;
            case REQUEST_DEVICE_CODE:
                instructions.setText("Paso 3/4 · Solicitar un SEGUNDO código "+
                        "nuevo para autorizar el teléfono.");
                next.setText("Enviar segundo código");break;
            case ENROLL_DEVICE:
                instructions.setText("Paso 4/4 · Introduce el segundo código y "+
                        "autoriza el registro. Sustituir el móvil revoca el anterior.");
                next.setText("Autorizar teléfono");break;
            case COMPLETE:
                instructions.setText("Dispositivo registrado. Generación "+
                        flow.generation()+".");
                next.setText("Finalizado");break;
            default:
                instructions.setText("Resultado de red incierto. No se volverá "+
                        "a enviar este código. Es necesaria una conciliación.");
                next.setText("Revisar estado");break;
        }
    }
    private TextView label(String text,int sp){
        TextView t=new TextView(getContext());
        t.setText(text);
        t.setTextSize(sp);
        t.setPadding(0,dp(8),0,dp(8));
        return t;
    }
    private int dp(int px){
        return (int)(px*getResources().getDisplayMetrics().density+0.5f);
    }
    // Test harness only; no launch destination in production.
    boolean actionEnabledForTest(){return next.isEnabled();}
    NexoConnectOnboardingFlow.Stage stageForTest(){return flow.stage();}
}

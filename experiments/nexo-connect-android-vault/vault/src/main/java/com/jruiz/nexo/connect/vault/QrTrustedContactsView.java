package com.jruiz.nexo.connect.vault;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.UUID;

/**
 * LAB-ONLY management of LOCAL QR trust. Does NOT unlink in Supabase.
 *
 * Security: two-step consent. A revoked pin cannot be used by
 * NexoConnectInboxCoordinator.importApprovedFromTrust on subsequent lookups.
 * The backend remains independently authoritative for remote contact status.
 */
public final class QrTrustedContactsView extends ScrollView {
    public interface Listener {
        void onLocalRevoked(UUID peerId);
        void onFailure(String code);
    }
    private final AndroidPeerTrustStore store;
    private final Listener listener;
    private final LinearLayout content;
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean busy;
    private final TextView notice;

    public QrTrustedContactsView(Context context,AndroidPeerTrustStore store,
            Listener listener) {
        super(context);
        if(store==null||listener==null)
            throw new IllegalArgumentException("Trust store and listener required");
        this.store=store;
        this.listener=listener;
        setFillViewport(true);
        content=new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int margin=dp(16);
        content.setPadding(margin,margin,margin,margin);
        addView(content,new ScrollView.LayoutParams(
                LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
        notice=text("Solo confianza guardada en este teléfono. "+
                "La revocación local NO elimina el vínculo remoto en Supabase.",14);
        content.addView(notice);
        reload();
    }

    public void reload(){
        if(busy)return;
        busy=true;
        runWorker(()->{
            final List<AndroidPeerTrustStore.PeerPin> peers=store.listActive();
            main.post(()->{
                busy=false;
                render(peers);
            });
        },"load-failed");
    }

    private void render(List<AndroidPeerTrustStore.PeerPin> peers){
        content.removeViews(1,content.getChildCount()-1);
        if(peers.isEmpty()){
            content.addView(text("No hay contactos verificados en este dispositivo.",16));
            return;
        }
        for(AndroidPeerTrustStore.PeerPin pin:peers){
            LinearLayout group=new LinearLayout(getContext());
            group.setOrientation(LinearLayout.VERTICAL);
            group.setPadding(0,dp(14),0,dp(14));
            group.addView(text("Contacto: "+pin.peerId,16));
            group.addView(text("Móvil generación "+pin.generation,14));
            try {
                group.addView(text("Firma SHA-256: "+pin.signingFingerprint(),12));
                group.addView(text("Cifrado SHA-256: "+pin.encryptionFingerprint(),12));
            }catch(Exception e){
                notice.setText("No es posible comprobar las huellas del contacto.");
                listener.onFailure("fingerprint-unavailable");
                continue;
            }
            Button revoke=new Button(getContext());
            revoke.setText("Revocar confianza local");
            group.addView(revoke);
            content.addView(group);
            revoke.setOnClickListener(v->showConfirmation(group,pin.peerId,revoke));
        }
    }

    private void showConfirmation(LinearLayout group,UUID peerId,Button initiator){
        if(busy)return;
        initiator.setEnabled(false);
        group.addView(text("Vas a dejar de confiar en "+peerId+
                " SOLO en este móvil. También debes desvincular en "+
                "el servidor cuando esté disponible la función de contactos.",14));
        CheckBox understood=new CheckBox(getContext());
        understood.setText("Entiendo que esto no revoca la relación en Supabase");
        group.addView(understood);
        Button confirm=new Button(getContext());
        confirm.setText("Confirmar revocación local");
        confirm.setEnabled(false);
        group.addView(confirm);
        understood.setOnCheckedChangeListener((button,checked)->
                confirm.setEnabled(checked&&!busy));
        confirm.setOnClickListener(v->{
            if(busy||!understood.isChecked())return;
            busy=true;
            confirm.setEnabled(false);
            runWorker(()->{
                boolean revoked=store.revoke(peerId);
                List<AndroidPeerTrustStore.PeerPin> peers=store.listActive();
                main.post(()->{
                    busy=false;
                    render(peers);
                    if(revoked)listener.onLocalRevoked(peerId);
                    else listener.onFailure("already-revoked");
                });
            },"local-revocation-failed");
        });
    }

    private interface Work {void run() throws Exception;}
    private void runWorker(Work work,String code){
        new Thread(()->{
            try{work.run();}
            catch(Exception ex){
                main.post(()->{
                    busy=false;
                    notice.setText("No se ha realizado ninguna modificación. "+
                            "Comprueba el almacén local.");
                    listener.onFailure(code);
                });
            }
        },"nexo-qr-trust-worker").start();
    }

    private TextView text(String message,int sp){
        TextView t=new TextView(getContext());
        t.setText(message);
        t.setTextSize(sp);
        t.setTextIsSelectable(true);
        t.setPadding(0,dp(6),0,dp(6));
        return t;
    }
    private int dp(int pixels){
        return (int)(pixels*getResources().getDisplayMetrics().density+0.5f);
    }
}

package com.jruiz.nexo.connect.vault;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.hybrid.HpkePublicKey;
import com.google.crypto.tink.signature.Ed25519PublicKey;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.security.GeneralSecurityException;
import java.util.UUID;

/**
 * LAB ONLY: show the registered phone's PUBLIC identity as a QR plus
 * independently readable complete fingerprints. QR has no invitation token,
 * and scanning it alone does NOT establish a server-side contact link.
 *
 * Never pass private keysets to a QR encoder. Only the two 32-byte public keys
 * are extracted from Android Keystore restored identity by a trusted caller.
 */
public final class NexoConnectOwnQrView extends ScrollView {
    private final AndroidPeerTrustStore.PeerPin self;

    public NexoConnectOwnQrView(Context context,UUID verifiedAccount,
            int registeredGeneration,KeysetHandle localSigningPrivate,
            KeysetHandle localHpkePrivate)throws Exception{
        super(context);
        self=publicIdentity(verifiedAccount,registeredGeneration,
                localSigningPrivate,localHpkePrivate);
        String qr=AndroidPeerTrustStore.encodeQr(self);
        int size=Math.max(384,Math.min(getResources().getDisplayMetrics().widthPixels-48,768));
        Bitmap bitmap=encodeQrBitmap(qr,size);
        LinearLayout column=new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        int padding=dp(16);
        column.setPadding(padding,padding,padding,padding);
        addView(column,new ScrollView.LayoutParams(
                LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
        TextView title=text("Mi identidad pública · Nexo Connect",20);
        column.addView(title);
        column.addView(text("Muestra este QR a la otra persona EN PERSONA. "+
                "Debe comprobar también las huellas completas en esta pantalla. "+
                "Este QR no contiene contraseñas ni claves privadas.",14));
        ImageView image=new ImageView(context);
        image.setImageBitmap(bitmap);
        image.setAdjustViewBounds(true);
        image.setContentDescription("Código QR público de mi identidad Nexo Connect");
        column.addView(image,new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
        column.addView(text("Cuenta: "+self.peerId,14));
        column.addView(text("Generación: "+self.generation,14));
        column.addView(text("Firma Ed25519 · SHA-256: "+groups(self.signingFingerprint()),14));
        column.addView(text("Cifrado HPKE · SHA-256: "+groups(self.encryptionFingerprint()),14));
        column.addView(text("No significa que haya un contacto autorizado en "+
                "el servidor: todavía debe completarse el vínculo bilateral.",14));
    }

    /** Construct QR ONLY from the public halves of the verified local identity. */
    public static AndroidPeerTrustStore.PeerPin publicIdentity(UUID account,
            int generation,KeysetHandle signingPrivate,KeysetHandle hpkePrivate)
            throws Exception{
        if(account==null||generation<=0||signingPrivate==null||hpkePrivate==null||
                signingPrivate.size()!=1||hpkePrivate.size()!=1)
            throw new GeneralSecurityException("Missing active phone identity");
        KeysetHandle signaturePublic=signingPrivate.getPublicKeysetHandle();
        KeysetHandle encryptionPublic=hpkePrivate.getPublicKeysetHandle();
        if(signaturePublic.size()!=1||encryptionPublic.size()!=1||
                !(signaturePublic.getAt(0).getKey() instanceof Ed25519PublicKey)||
                !(encryptionPublic.getAt(0).getKey() instanceof HpkePublicKey))
            throw new GeneralSecurityException("Unexpected device public key types");
        byte[] signing=((Ed25519PublicKey)signaturePublic.getAt(0).getKey())
                .getPublicKeyBytes().toByteArray();
        byte[] encryption=((HpkePublicKey)encryptionPublic.getAt(0).getKey())
                .getPublicKeyBytes().toByteArray();
        if(signing.length!=32||encryption.length!=32)
            throw new GeneralSecurityException("Invalid public key length");
        return new AndroidPeerTrustStore.PeerPin(
                account,generation,signing,encryption,true);
    }

    /** ZXing library QR generation does not require a camera or Internet. */
    public static Bitmap encodeQrBitmap(String text,int size)throws Exception{
        if(text==null||text.length()>1024||size<256||size>2048)
            throw new GeneralSecurityException("Invalid QR request");
        return new BarcodeEncoder().encodeBitmap(text,BarcodeFormat.QR_CODE,size,size);
    }
    private TextView text(String value,int sp){
        TextView v=new TextView(getContext());
        v.setText(value);
        v.setTextSize(sp);
        v.setTextIsSelectable(true);
        v.setPadding(0,dp(6),0,dp(6));
        return v;
    }
    private static String groups(String hex){
        StringBuilder result=new StringBuilder(72);
        for(int i=0;i<hex.length();i+=8){
            if(i>0)result.append(' ');
            result.append(hex, i,Math.min(i+8,hex.length()));
        }
        return result.toString();
    }
    private int dp(int px){return (int)(px*getResources().getDisplayMetrics().density+0.5f);}
}

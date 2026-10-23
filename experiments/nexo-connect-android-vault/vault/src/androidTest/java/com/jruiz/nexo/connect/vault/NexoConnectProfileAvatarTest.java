package com.jruiz.nexo.connect.vault;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Base64;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.ByteArrayOutputStream;
import java.security.GeneralSecurityException;
import java.util.UUID;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NexoConnectProfileAvatarTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test public void encryptedChatProfileCarriesSmallAvatarAndNeverChangesLocalAlias()
            throws Exception{
        UUID own=UUID.randomUUID(),viewer=UUID.randomUUID();
        NexoConnectChatStore.setOwnName(context,own,"Mar");
        Bitmap picture=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);
        picture.eraseColor(0xff116b60);
        ByteArrayOutputStream data=new ByteArrayOutputStream();
        assertTrue(picture.compress(Bitmap.CompressFormat.JPEG,55,data));
        picture.recycle();
        String encoded=NexoConnectProfileAvatar.validate(
                Base64.encodeToString(data.toByteArray(),Base64.NO_WRAP));
        NexoConnectChatStore.setOwnAvatar(context,own,encoded);
        assertEquals(encoded,NexoConnectChatStore.ownAvatar(context,own));
        assertEquals(64,NexoConnectProfileAvatar.decode(encoded).getWidth());
        JSONObject body=NexoConnectChatStore.parse(
                NexoConnectChatStore.profilePayload(context,own));
        assertEquals("profile",body.getString("kind"));
        assertEquals("Mar",body.getString("name"));
        assertEquals("avatar:"+encoded,body.getString("text"));
        NexoConnectChatStore.rememberProfile(context,viewer,own,body);
        assertEquals("Mar",NexoConnectChatStore.peerName(context,viewer,own));
        assertEquals(encoded,NexoConnectChatStore.peerAvatar(context,viewer,own));
        NexoConnectChatStore.setLocalAlias(context,viewer,own,"Mamà");
        assertEquals("Mamà",NexoConnectChatStore.peerName(context,viewer,own));
        NexoConnectChatStore.setOwnAvatar(context,own,"");
        assertEquals("avatar:",NexoConnectChatStore.parse(
                NexoConnectChatStore.profilePayload(context,own)).getString("text"));
    }

    @Test public void maliciousAvatarIsRejected()throws Exception{
        for(String invalid:new String[]{"x".repeat(3401),"AA==","not base64","/9j/"}) {
            try{NexoConnectProfileAvatar.validate(invalid);fail("Accepted malformed avatar");}
            catch(GeneralSecurityException expected){}
        }
    }
}

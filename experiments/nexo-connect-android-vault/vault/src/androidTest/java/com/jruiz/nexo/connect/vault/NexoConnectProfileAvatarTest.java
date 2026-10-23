package com.jruiz.nexo.connect.vault;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.ExifInterface;
import android.util.Base64;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
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

    private void assertOrientation(int orientation,int[][] expected){
        final int[] pixels={Color.RED,Color.GREEN,Color.BLUE,Color.YELLOW,Color.CYAN,Color.MAGENTA};
        Bitmap portrait=Bitmap.createBitmap(2,3,Bitmap.Config.ARGB_8888);
        for(int y=0;y<3;y++)for(int x=0;x<2;x++)portrait.setPixel(x,y,pixels[y*2+x]);
        Bitmap adjusted=NexoConnectProfileAvatar.applyExifOrientation(portrait,orientation);
        try{
            assertEquals(expected.length,adjusted.getHeight());
            assertEquals(expected[0].length,adjusted.getWidth());
            for(int y=0;y<expected.length;y++)for(int x=0;x<expected[y].length;x++)
                assertEquals("EXIF "+orientation+" en "+x+","+y,pixels[expected[y][x]-1],adjusted.getPixel(x,y));
        }finally{
            if(adjusted!=portrait)adjusted.recycle();
            portrait.recycle();
        }
    }

    @Test public void allEightExifOrientationsPreservePixelPositions(){
        assertOrientation(ExifInterface.ORIENTATION_NORMAL,new int[][]{{1,2},{3,4},{5,6}});
        assertOrientation(ExifInterface.ORIENTATION_FLIP_HORIZONTAL,new int[][]{{2,1},{4,3},{6,5}});
        assertOrientation(ExifInterface.ORIENTATION_ROTATE_180,new int[][]{{6,5},{4,3},{2,1}});
        assertOrientation(ExifInterface.ORIENTATION_FLIP_VERTICAL,new int[][]{{5,6},{3,4},{1,2}});
        assertOrientation(ExifInterface.ORIENTATION_TRANSPOSE,new int[][]{{1,3,5},{2,4,6}});
        assertOrientation(ExifInterface.ORIENTATION_ROTATE_90,new int[][]{{5,3,1},{6,4,2}});
        assertOrientation(ExifInterface.ORIENTATION_TRANSVERSE,new int[][]{{6,4,2},{5,3,1}});
        assertOrientation(ExifInterface.ORIENTATION_ROTATE_270,new int[][]{{2,4,6},{1,3,5}});
    }

    @Test public void importedPortraitIsRotatedBeforeJpegAvatarIsSaved()throws Exception{
        Bitmap picture=Bitmap.createBitmap(120,200,Bitmap.Config.ARGB_8888);
        for(int y=0;y<200;y++)for(int x=0;x<120;x++)
            picture.setPixel(x,y,y<100?Color.RED:Color.BLUE);
        File jpeg=File.createTempFile("nexo-profile-orientation-",".jpg",context.getCacheDir());
        try{
            try(FileOutputStream out=new FileOutputStream(jpeg)){
                assertTrue(picture.compress(Bitmap.CompressFormat.JPEG,95,out));
            }finally{picture.recycle();}
            ExifInterface metadata=new ExifInterface(jpeg.getAbsolutePath());
            metadata.setAttribute(ExifInterface.TAG_ORIENTATION,
                    String.valueOf(ExifInterface.ORIENTATION_ROTATE_90));
            metadata.saveAttributes();
            byte[] bytes=Files.readAllBytes(jpeg.toPath());
            assertEquals(ExifInterface.ORIENTATION_ROTATE_90,
                    NexoConnectProfileAvatar.readExifOrientation(bytes));
            String encoded=NexoConnectProfileAvatar.encodeNormalized(bytes);
            Bitmap preview=NexoConnectProfileAvatar.decode(encoded);
            try{
                assertEquals(72,preview.getWidth());
                assertEquals(72,preview.getHeight());
                assertTrue("La izquierda debe ser azul",Color.blue(preview.getPixel(10,36))>
                        Color.red(preview.getPixel(10,36))+60);
                assertTrue("La derecha debe ser roja",Color.red(preview.getPixel(62,36))>
                        Color.blue(preview.getPixel(62,36))+60);
            }finally{preview.recycle();}
        }finally{jpeg.delete();}
    }
}


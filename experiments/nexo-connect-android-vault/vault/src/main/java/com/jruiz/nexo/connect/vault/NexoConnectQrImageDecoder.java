package com.jruiz.nexo.connect.vault;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * LAB: camera-free QR acquisition from an explicitly selected image.
 *
 * Only content:// images chosen by Android's ACTION_OPEN_DOCUMENT are read.
 * No URI, source image or QR text is logged, uploaded or stored in files.
 * A decoded QR is still UNTRUSTED: caller must use the existing full
 * out-of-band SHA-256 fingerprint + explicit-consent verification screen.
 */
public final class NexoConnectQrImageDecoder {
    private static final int MAX_FILE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_DIMENSION = 8192;
    private static final int MAX_DECODED_SIDE = 2048;
    private static final int MAX_QR_CHARS = 1024;

    private NexoConnectQrImageDecoder(){}

    /** Must be invoked from a worker thread, never Android's UI thread. */
    public static String readSelectedImage(Context context, Uri uri) throws Exception {
        if(context==null||uri==null||
                !"content".equals(uri.getScheme()))
            throw new GeneralSecurityException("Expected selected content URI");
        String mime=context.getContentResolver().getType(uri);
        if(mime==null||!mime.startsWith("image/"))
            throw new GeneralSecurityException("Selected item is not an image");

        byte[] imageBytes;
        try(InputStream input=context.getContentResolver().openInputStream(uri)){
            if(input==null)throw new GeneralSecurityException("Selected image unavailable");
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            byte[] chunk=new byte[8192];
            int count;
            while((count=input.read(chunk))!=-1){
                if(count==0)continue;
                if(output.size()>MAX_FILE_BYTES-count)
                    throw new GeneralSecurityException("Selected image is too large");
                output.write(chunk,0,count);
            }
            imageBytes=output.toByteArray();
        }
        try {
            return decodeImageBytes(imageBytes);
        }finally {
            java.util.Arrays.fill(imageBytes,(byte)0);
        }
    }

    /**
     * Pure decoder entry point used by Android instrumentation: generates
     * actual ZXing image pixels, without camera/emulator 3D scene or network.
     * No image may skip the independent identity-verification UI.
     */
    static String decodeImageBytes(byte[] data) throws Exception {
        if(data==null||data.length==0||data.length>MAX_FILE_BYTES)
            throw new GeneralSecurityException("Invalid QR image size");
        BitmapFactory.Options bounds=new BitmapFactory.Options();
        bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(data,0,data.length,bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0||
                bounds.outWidth>MAX_DIMENSION||bounds.outHeight>MAX_DIMENSION)
            throw new GeneralSecurityException("QR image dimensions rejected");
        BitmapFactory.Options scaled=new BitmapFactory.Options();
        int sample=1;
        while(bounds.outWidth/sample>MAX_DECODED_SIDE||
                bounds.outHeight/sample>MAX_DECODED_SIDE)sample*=2;
        scaled.inSampleSize=sample;
        scaled.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap bitmap=BitmapFactory.decodeByteArray(data,0,data.length,scaled);
        if(bitmap==null)throw new GeneralSecurityException("Invalid QR bitmap");
        try {
            int width=bitmap.getWidth(),height=bitmap.getHeight();
            if(width<32||height<32||width>MAX_DECODED_SIDE||
                    height>MAX_DECODED_SIDE)
                throw new GeneralSecurityException("Invalid decoded QR bounds");
            int[] pixels=new int[width*height];
            bitmap.getPixels(pixels,0,width,0,0,width,height);
            BinaryBitmap source=new BinaryBitmap(new HybridBinarizer(
                    new RGBLuminanceSource(width,height,pixels)));
            Map<DecodeHintType,Object> hints=new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.POSSIBLE_FORMATS,
                    Collections.singletonList(BarcodeFormat.QR_CODE));
            hints.put(DecodeHintType.TRY_HARDER,Boolean.TRUE);
            String text=new MultiFormatReader().decode(source,hints).getText();
            if(text==null||text.length()==0||text.length()>MAX_QR_CHARS)
                throw new GeneralSecurityException("Invalid QR text length");
            return text;
        }finally {
            bitmap.recycle();
        }
    }
}

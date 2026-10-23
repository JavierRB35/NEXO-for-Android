package com.jruiz.nexo.connect.vault;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.GeneralSecurityException;

/** Tiny private avatar for the existing signed and encrypted profile envelope. */
public final class NexoConnectProfileAvatar {
    private NexoConnectProfileAvatar(){}
    private static final int MAX_SOURCE=8*1024*1024,MAX_JPEG=2400;
    public static String importFrom(ContentResolver resolver,Uri uri)throws Exception{
        if(resolver==null||uri==null||!"content".equals(uri.getScheme()))
            throw new GeneralSecurityException("Selecciona una imagen de la galería");
        byte[] data;
        try(InputStream in=resolver.openInputStream(uri)){
            if(in==null)throw new GeneralSecurityException("No se pudo abrir la fotografía");
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            byte[] buf=new byte[8192];int count;
            while((count=in.read(buf))!=-1){
                if(out.size()+count>MAX_SOURCE)throw new GeneralSecurityException("Imagen demasiado grande");
                out.write(buf,0,count);
            }
            data=out.toByteArray();
        }
        BitmapFactory.Options bounds=new BitmapFactory.Options();
        bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(data,0,data.length,bounds);
        if(bounds.outWidth<1||bounds.outHeight<1||bounds.outWidth>16384||bounds.outHeight>16384)
            throw new GeneralSecurityException("Imagen no válida");
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inSampleSize=1;
        while(bounds.outWidth/options.inSampleSize>256||bounds.outHeight/options.inSampleSize>256)
            options.inSampleSize*=2;
        Bitmap source=BitmapFactory.decodeByteArray(data,0,data.length,options);
        if(source==null)throw new GeneralSecurityException("Formato de imagen no compatible");
        try{
            int edge=Math.min(source.getWidth(),source.getHeight());
            Bitmap crop=Bitmap.createBitmap(source,(source.getWidth()-edge)/2,(source.getHeight()-edge)/2,edge,edge);
            try{
                Bitmap scaled=Bitmap.createScaledBitmap(crop,72,72,true);
                try{
                    for(int quality=76;quality>=20;quality-=8){
                        ByteArrayOutputStream out=new ByteArrayOutputStream();
                        if(!scaled.compress(Bitmap.CompressFormat.JPEG,quality,out))
                            throw new GeneralSecurityException("No se pudo preparar la foto");
                        if(out.size()<=MAX_JPEG)
                            return validate(Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP));
                    }
                    throw new GeneralSecurityException("Prueba con una foto más sencilla");
                }finally{if(scaled!=crop)scaled.recycle();}
            }finally{if(crop!=source)crop.recycle();}
        }finally{source.recycle();}
    }
    public static String validate(String encoded)throws GeneralSecurityException{
        if(encoded==null||encoded.isEmpty())return "";
        if(encoded.length()>3400||!encoded.matches("[A-Za-z0-9+/=]+"))
            throw new GeneralSecurityException("Foto de perfil no válida");
        byte[] bytes;
        try{bytes=Base64.decode(encoded,Base64.NO_WRAP);}
        catch(IllegalArgumentException ex){throw new GeneralSecurityException("Imagen corrupta",ex);}
        if(bytes.length<16||bytes.length>MAX_JPEG||(bytes[0]&255)!=255||
                (bytes[1]&255)!=216||(bytes[bytes.length-2]&255)!=255||
                (bytes[bytes.length-1]&255)!=217)
            throw new GeneralSecurityException("La imagen debe ser JPEG");
        BitmapFactory.Options b=new BitmapFactory.Options();b.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(bytes,0,bytes.length,b);
        if(b.outWidth<1||b.outHeight<1||b.outWidth>128||b.outHeight>128)
            throw new GeneralSecurityException("Dimensiones no válidas");
        return encoded;
    }
    public static Bitmap decode(String encoded)throws GeneralSecurityException {
        if(validate(encoded).isEmpty())return null;
        byte[] bytes=Base64.decode(encoded,Base64.NO_WRAP);
        Bitmap bmp=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
        if(bmp==null)throw new GeneralSecurityException("Imagen no válida");
        return bmp;
    }
}

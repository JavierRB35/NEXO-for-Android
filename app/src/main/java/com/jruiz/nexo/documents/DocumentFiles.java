package com.jruiz.nexo.documents;

import java.io.*;

/** File copies and access stay inside the attachment directory already included in backups. */
public final class DocumentFiles {
    private DocumentFiles() {}

    public static File requireStored(File directory, String path) throws IOException {
        if (path==null || path.isBlank()) throw new FileNotFoundException("Archivo no disponible. Vuelve a seleccionarlo.");
        File root=directory.getCanonicalFile(),file=new File(path).getCanonicalFile();
        if (!root.equals(file.getParentFile()) || !file.isFile())
            throw new FileNotFoundException("Archivo no disponible. Vuelve a seleccionarlo.");
        return file;
    }

    public static String safeName(String name) {
        String clean=(name==null?"":name).replaceAll("[\\p{Cntrl}/\\\\]","_").trim();
        if (clean.isEmpty() || clean.equals(".") || clean.equals("..")) clean="archivo";
        // Leave room for a unique prefix within the usual 255-byte filename limit.
        while (clean.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>180)
            clean=shortenName(clean);
        return clean;
    }

    private static String shortenName(String name) {
        int dot=name.lastIndexOf('.');
        int cut=(dot>0 && name.length()-dot<=12)?dot:name.length();
        int previous=name.offsetByCodePoints(cut,-1);
        return name.substring(0,previous)+name.substring(cut);
    }

    public static File copy(File directory,String name,InputStream input) throws IOException {
        if (input==null) throw new IOException("El archivo seleccionado no está disponible.");
        File out=null;
        try (InputStream in=input) {
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("No se pudo crear la carpeta de documentos.");
            out=File.createTempFile("nexo_","_"+safeName(name),directory);
            try (OutputStream stream=new FileOutputStream(out)) {
                byte[] buffer=new byte[16384];int size;
                while ((size=in.read(buffer))!=-1) { if(size>0)stream.write(buffer,0,size); }
            }
            return out;
        } catch (IOException | RuntimeException e) {
            if (out!=null) out.delete();
            throw e;
        }
    }
}

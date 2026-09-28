package android.content;
/** QA only. No Android APIs are executed by the service tests. */
public class Context {
    private java.io.File files=new java.io.File(System.getProperty("java.io.tmpdir"),"nexo-document-qa");
    public java.io.File getFilesDir() { return files; }
    public Context withFilesDir(java.io.File directory) { files=directory;return this; }
    public Context getApplicationContext() { return this; }
}

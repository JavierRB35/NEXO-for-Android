package com.jruiz.nexo.connect.host;

import android.app.AlertDialog;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.jruiz.nexo.connect.vault.AndroidPeerTrustStore;
import com.jruiz.nexo.connect.vault.NexoConnectLocalNotePromotion;

import java.util.UUID;

import com.jruiz.nexo.BuildConfig;
import com.jruiz.nexo.data.DatabaseHelper;
import com.jruiz.nexo.connect.vault.NexoConnectPilotActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The host integration lives exclusively in app/src/debug: the release build
 * neither packages LAB transport nor reads Nexo notes for it.
 *
 * Only a user-initiated note picker reads native SQLite. The selected note is
 * copied to the existing E2EE compose form in memory; no implicit send, no
 * files, no note mutations, and no plaintext Intent extras.
 */
public final class NexoConnectEmbeddedActivity extends NexoConnectPilotActivity {
    /**
     * Debug-only local preview available even without an account or verified
     * contacts. Does not authenticate, encrypt, upload, enroll or send.
     */
    @Override protected void addHostSetupActions() {
        if (BuildConfig.DEBUG) {
            button("Explorar fichas de Nexo · sin cuenta y sin envío",
                    () -> chooseShareSection(true));
            if(hasSelectedShare())
                button("Ver ficha seleccionada · sin envío",()->readSelectedShare(true));
            button("Vista previa de notas de Nexo · sin cuenta y sin envío",
                    () -> pickSavedNote(true));
        }
    }

    @Override protected void addHostActions() {
        if (BuildConfig.DEBUG) {
            if(hasSelectedShare())
                button("Compartir ficha seleccionada con un contacto",
                        () -> readSelectedShare(false));
            button("Compartir ficha guardada en Nexo",
                    () -> chooseShareSection(false));
            button("5a. Enviar nota guardada en Nexo",
                    () -> pickSavedNote(false));
            button("6a. Copiar nota cifrada recibida a Notas de Nexo",
                    this::chooseReceivedNote);
        }
    }

    // A protected internal Intent can carry a *local ID and fixed section
    // name*, never a plaintext title, body, bearer, key or file path.
    private boolean hasSelectedShare() {
        android.content.Intent intent=getIntent();
        if(intent==null)return false;
        long id=intent.getLongExtra("nexoShareId",0L);
        String kind=intent.getStringExtra("nexoShareSection");
        if(id<=0||kind==null)return false;
        for(String allowed:NexoConnectHostShare.sectionKinds())
            if(allowed.equals(kind))return true;
        return false;
    }

    private void readSelectedShare(boolean localOnly) {
        if(!BuildConfig.DEBUG||!hasSelectedShare())return;
        reviewShare(getIntent().getStringExtra("nexoShareSection"),
                    getIntent().getLongExtra("nexoShareId",0L),localOnly);
    }

    private void chooseShareSection(boolean localOnly) {
        if(!BuildConfig.DEBUG)return;
        String[] sections=NexoConnectHostShare.sections();
        String[] kinds=NexoConnectHostShare.sectionKinds();
        new AlertDialog.Builder(this)
            .setTitle(localOnly?"Explorar fichas · solo en este móvil":
                    "¿Qué quieres compartir?")
            .setItems(sections,(dialog,which)->chooseShareEntry(kinds[which],localOnly))
            .setNegativeButton("Cancelar",null).show();
    }

    private void chooseShareEntry(String kind,boolean localOnly){
        if(!BuildConfig.DEBUG)return;
        try(DatabaseHelper db=new DatabaseHelper(this)){
            List<NexoConnectHostShare.Entry> all=NexoConnectHostShare.list(db,kind);
            if(all.isEmpty()){
                showPickerError("No hay fichas guardadas en esta sección.");
                return;
            }
            String[] labels=new String[all.size()];
            for(int i=0;i<all.size();i++){
                NexoConnectHostShare.Entry entry=all.get(i);
                labels[i]=entry.title+"  ·  "+friendlyKind(entry.kind);
            }
            new AlertDialog.Builder(this)
                .setTitle("Selecciona una ficha")
                .setItems(labels,(d,which)->reviewShare(kind,all.get(which).id,localOnly))
                .setNegativeButton("Cancelar",null).show();
        }catch(Exception failure){
            showPickerError("No se pudieron consultar las fichas de Nexo.");
        }
    }

    private static String friendlyKind(String kind){
        switch(kind) {
            case "event":return "Cita";
            case "task":return "Tarea";
            case "reminder":return "Recordatorio";
            case "expiry":return "Vencimiento";
            case "note":return "Nota";
            case "place":return "Lugar";
            case "restaurant":return "Restaurante";
            case "document":return "Documento";
            case "thought":return "Cuaderno";
            case "quote":return "Frase";
            case "list":return "Lista";
            case "book":return "Libro";
            case "series":return "Serie";
            case "movie":return "Película";
            default:return "Ficha";
        }
    }

    private void reviewShare(String section,long id,boolean localOnly){
        if(!BuildConfig.DEBUG||id<=0)return;
        final NexoConnectHostShare.Draft draft;
        try(DatabaseHelper db=new DatabaseHelper(this)){
            draft=NexoConnectHostShare.prepare(db,section,id);
        }catch(UnsupportedOperationException pending){
            new AlertDialog.Builder(this)
                .setTitle("Tipo de ficha en preparación")
                .setMessage(pending.getMessage()+"\n\n"+
                    "Mientras tanto puedes utilizar el menú Compartir de Android. "+
                    "No se ha enviado nada mediante Connect.")
                .setPositiveButton("Entendido",null).show();
            return;
        }catch(Exception invalid){
            showPickerError("La ficha no está disponible o no se puede "+
                    "preparar de forma segura.");
            return;
        }

        StringBuilder summary=new StringBuilder();
        summary.append("Tipo: ").append(draft.displayKind)
                .append("\n\n").append(draft.title);
        String body=draft.safeData.optString("body",
                draft.safeData.optString("summary",""));
        if(!body.isEmpty()){
            if(body.length()>650)body=body.substring(0,650)+"…";
            summary.append("\n\n").append(body);
        }
        org.json.JSONArray rows=draft.safeData.optJSONArray("entries");
        if(rows!=null) {
            summary.append("\n\nElementos incluidos: ").append(rows.length());
            for(int i=0;i<Math.min(rows.length(),4);i++){
                JSONObject item=rows.optJSONObject(i);
                if(item!=null)summary.append("\n• ")
                    .append(item.optString("text",""));
            }
        }
        summary.append("\n\nSolo se incluirán los campos compatibles. "+
                "No se enviarán adjuntos, rutas privadas ni contraseñas.");
        new AlertDialog.Builder(this)
            .setTitle(localOnly?"Vista previa local · sin envío":"Revisar ficha")
            .setMessage(summary.toString())
            .setNegativeButton("Cerrar",null)
            .setPositiveButton(localOnly?"Entendido":"Elegir destinatario",
                    (dialog,which)->{
                        if(!localOnly)
                            offerHostEntity(draft.entity,draft.safeData);
                    })
            .show();
    }

    private boolean copyingReceived;

    private SQLiteDatabase privateConnectDatabase(){
        return openOrCreateDatabase("nexo-connect-pilot.db", Context.MODE_PRIVATE,null);
    }

    private void chooseReceivedNote(){
        if(!BuildConfig.DEBUG)return;
        UUID owner;
        try{owner=authenticatedAccountId();}
        catch(SecurityException invalid){
            showPickerError("Verifica tu cuenta de Connect para consultar la bandeja cifrada.");
            return;
        }
        try(SQLiteDatabase privateDb=privateConnectDatabase()){
            List<NexoConnectLocalNotePromotion.Received> received=
                    NexoConnectLocalNotePromotion.list(privateDb,100);
            if(received.isEmpty()){
                showPickerError("Todavia no hay notas cifradas verificadas en este dispositivo. "+
                        "Recibe una nota desde la bandeja de Connect antes de copiarla.");
                return;
            }
            String[] labels=new String[received.size()];
            for(int i=0;i<received.size();i++){
                NexoConnectLocalNotePromotion.Received entry=received.get(i);
                labels[i]="Recibida · "+entry.deliveryId.toString().substring(0,8)+
                        " · remitente "+entry.senderId.toString().substring(0,8);
            }
            new AlertDialog.Builder(this)
                .setTitle("Notas cifradas recibidas")
                .setItems(labels,(dialog,which)->reviewReceivedNote(owner,
                        received.get(which).deliveryId))
                .setNegativeButton("Cancelar",null).show();
        }catch(Exception failure){
            showPickerError("No se pudo consultar la bandeja cifrada local.");
        }
    }

    private void reviewReceivedNote(UUID owner,UUID delivery){
        if(!BuildConfig.DEBUG)return;
        try(SQLiteDatabase privateDb=privateConnectDatabase()){
            AndroidPeerTrustStore trust=new AndroidPeerTrustStore(this,owner);
            NexoConnectLocalNotePromotion.VerifiedNote received=
                    NexoConnectLocalNotePromotion.read(privateDb,owner,delivery,trust);
            String text=received.body;
            if(text.length()>1600)text=text.substring(0,1600)+
                    "\n\n[Vista previa limitada a 1600 caracteres]";
            new AlertDialog.Builder(this)
                .setTitle("Nota cifrada autenticada")
                .setMessage("Remitente QR verificado: "+received.source.senderId+
                        "\n\nTitulo: "+received.title+
                        "\n\n"+text+
                        "\n\nIMPORTANTE: si copias esta nota al bloc normal de Nexo, "+
                        "la copia se guardara como las demas notas locales "+
                        "(sin el cifrado adicional de Connect). "+
                        "El original cifrado permanecera intacto.")
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Copiar a Notas de Nexo",
                        (dialog,which)->copyReceivedNote(owner,delivery))
                .show();
        }catch(Exception failure){
            showPickerError("No se puede autenticar la nota recibida o el contacto esta revocado.");
        }
    }

    private void copyReceivedNote(UUID owner,UUID delivery){
        if(!BuildConfig.DEBUG||copyingReceived)return;
        try{
            if(!owner.equals(authenticatedAccountId()))
                throw new SecurityException("La cuenta verificada ha cambiado");
        }catch(Exception invalid){
            showPickerError("Vuelve a verificar la cuenta de Connect.");
            return;
        }
        copyingReceived=true;
        // Do the native SQLite transaction outside the UI thread. The
        // receiving server is never contacted or acknowledged by this action.
        new Thread(()->{
            NexoConnectLocalNotePromotion.Result result=null;
            try(SQLiteDatabase source=privateConnectDatabase();
                DatabaseHelper nexo=new DatabaseHelper(this)){
                AndroidPeerTrustStore trust=new AndroidPeerTrustStore(this,owner);
                result=NexoConnectLocalNotePromotion.copyApproved(source,
                        nexo.getWritableDatabase(),owner,delivery,trust,true);
            }catch(Exception failure){
                runOnUiThread(()->{
                    copyingReceived=false;
                    if(!isFinishing()&&!isDestroyed())
                        showPickerError("No se pudo copiar: comprueba la identidad "+
                                "verificada, el almacenamiento o si ya eliminaste la copia.");
                });
                return;
            }
            final NexoConnectLocalNotePromotion.Result completed=result;
            runOnUiThread(()->{
                copyingReceived=false;
                if(isFinishing()||isDestroyed())return;
                new AlertDialog.Builder(this)
                    .setTitle(completed.alreadyCopied
                            ? "La nota ya estaba copiada"
                            : "Nota añadida a Nexo")
                    .setMessage("Puedes encontrarla en Notas de Nexo. "+
                            "El original cifrado y las confirmaciones "+
                            "del servidor no se han modificado.")
                    .setPositiveButton("Entendido",null).show();
            });
        },"nexo-connect-native-note-copy").start();
    }

    private void pickSavedNote(boolean previewOnly) {
        if (!BuildConfig.DEBUG) return;
        final List<Long> ids = new ArrayList<>();
        final List<String> titles = new ArrayList<>();

        // DatabaseHelper is the existing Nexo SQLite helper. This never
        // writes to it, exports an attachment, or opens the Nexo WebView.
        try (DatabaseHelper db = new DatabaseHelper(this)) {
            JSONArray notes = db.getNotes();
            for (int i = 0; i < notes.length() && ids.size() < 100; i++) {
                JSONObject note = notes.optJSONObject(i);
                if (note == null || !"note".equals(note.optString("type"))
                        || !"active".equals(note.optString("status"))) continue;
                long id = note.optLong("id", 0);
                if (id <= 0) continue;
                String name = note.optString("title", "Nota guardada").trim();
                if (name.isEmpty()) name = "Nota guardada";
                if (name.length() > 90) name = name.substring(0, 90) + "…";
                ids.add(id);
                titles.add(name);
            }
        } catch (Exception failure) {
            showPickerError("No se pudieron leer las notas guardadas de Nexo.");
            return;
        }

        if (ids.isEmpty()) {
            new AlertDialog.Builder(this)
                .setTitle("Notas de Nexo")
                .setMessage("No hay notas guardadas. Crea una nota en Nexo antes de intentar compartirla.")
                .setPositiveButton("Entendido", null).show();
            return;
        }
        // Android's AlertDialog list and message share the dialog's content
        // area. Use a LIST ONLY here; a simultaneous setMessage can hide it.
        new AlertDialog.Builder(this)
            .setTitle(previewOnly
                    ? "Elegir nota · vista local, sin envío"
                    : "Elegir nota · revisar antes de enviar")
            .setItems(titles.toArray(new String[0]), (dialog, index) ->
                    reviewSavedNote(ids.get(index), previewOnly))
            .setNegativeButton("Cancelar", null).show();
    }

    private void reviewSavedNote(long noteId, boolean previewOnly) {
        if (!BuildConfig.DEBUG || noteId <= 0) return;
        try (DatabaseHelper db = new DatabaseHelper(this)) {
            // Re-read at selection time so no deleted/edited row is used from
            // the earlier picker snapshot.
            JSONObject note = db.getItem(noteId);
            if (!"note".equals(note.optString("type"))
                    || !"active".equals(note.optString("status"))) {
                showPickerError("Esta nota ya no esta disponible.");
                return;
            }
            String title = note.optString("title", "").trim();
            String body = note.optString("body", "");

            if (previewOnly) {
                // No network client is instantiated, no encryption/session/OTP
                // is needed, and there is no button to send from this dialog.
                final int maxPreview = 2000;
                String excerpt = body.length() > maxPreview
                        ? body.substring(0, maxPreview) +
                            "\n\n[Vista previa limitada a 2000 caracteres]"
                        : body;
                new AlertDialog.Builder(this)
                    .setTitle("Vista previa local · no se envía")
                    .setMessage("Título:\n" + title + "\n\nTexto:\n" +
                            excerpt + "\n\nCaracteres del texto: " +
                            body.length() +
                            "\n\nEsto solo comprueba la lectura de notas. " +
                            "No verifica el cifrado ni realiza ninguna conexión.")
                    .setPositiveButton("Cerrar", null).show();
                return;
            }
            // The base activity checks size, asks for a QR-pinned peer,
            // shows editable content, and demands final explicit confirmation.
            offerHostNote(title, body);
        } catch (Exception failure) {
            showPickerError("No se pudo abrir la nota elegida.");
        }
    }

    private void showPickerError(String message) {
        new AlertDialog.Builder(this).setTitle("Nexo Connect")
            .setMessage(message).setPositiveButton("Entendido", null).show();
    }
}

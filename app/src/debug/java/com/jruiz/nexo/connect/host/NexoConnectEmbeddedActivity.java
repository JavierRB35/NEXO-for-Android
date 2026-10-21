package com.jruiz.nexo.connect.host;

import android.app.AlertDialog;

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
    @Override protected void addHostActions() {
        if (BuildConfig.DEBUG) {
            button("5a. Enviar nota guardada en Nexo", this::pickSavedNote);
        }
    }

    private void pickSavedNote() {
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
        new AlertDialog.Builder(this)
            .setTitle("Elegir nota guardada")
            .setMessage("Solo se prepararan el titulo y el texto. No se incluyen archivos, adjuntos ni metadatos privados.")
            .setItems(titles.toArray(new String[0]), (dialog, index) ->
                    reviewSavedNote(ids.get(index)))
            .setNegativeButton("Cancelar", null).show();
    }

    private void reviewSavedNote(long noteId) {
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
            // The base activity checks size, then asks for a QR-pinned peer,
            // previews the copy, and requires one final explicit confirmation.
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

from pathlib import Path

root=Path(__file__).resolve().parents[1]
js=(root/'app/src/main/assets/app.js').read_text(encoding='utf-8')
css=(root/'app/src/main/assets/styles.css').read_text(encoding='utf-8')

start=js.index('function noteCard(n){')
end=js.index('\nfunction bindNoteButtons',start)
source=js[start:end]
card=source.split('function openNoteReader(id){',1)[0]
reader=source.split('function openNoteReader(id){',1)[1]
binder=js.split('function bindNoteButtons(',1)[1].split('function setNoteSaveStatus(',1)[0]

# Cada tarjeta es un botón completo de lectura, separado de lápiz/compartir/eliminar.
assert 'data-note-read=' in card and 'class="note-card-open note-card-read"' in card
assert 'noteSnippet(n.body)' in card, 'La lista debe mostrar solo un extracto para ser compacta'
assert 'class="note-card-body"' not in card, 'La lectura completa ya no se hace dentro de la tarjeta'
assert "recordIconAction('edit','Editar nota'" in card and 'data-open-note=' in card
assert "recordIconAction('share','Compartir nota'" in card and 'data-note-share=' in card
assert "recordIconAction('trash','Eliminar nota'" in card and 'data-note-delete=' in card
assert "root.querySelectorAll('[data-note-read]')" in binder
assert "openNoteReader(Number(b.dataset.noteRead))" in binder

# Visor completo, sin controles de entrada, conservando saltos de línea y texto.
assert "NexoNative.getItem(Number(id))" in reader
assert "note.type!=='note'" in reader
assert "esc(String(note.body||''))" in reader
assert "note-reader-text" in reader and "note-reader-toolbar" in reader
assert 'id="noteReaderEdit"' in reader and 'id="noteReaderShare"' in reader
assert 'id="noteReaderFontDown"' in reader and 'id="noteReaderFontUp"' in reader
assert 'id="noteReaderDelete"' in reader
assert 'textarea' not in reader and '<input' not in reader
assert "if(i.type==='note'){openNoteReader(Number(id));return;}" in js
assert "else if(kind==='note')openNoteReader(Number(id));" in js
assert "document.body.classList.remove('note-reading-open')" in js

for token in ('.note-card-preview', '-webkit-line-clamp:3', '.note-reading-open #modalBody',
              'overflow-y:auto', 'overscroll-behavior:contain',
              '-webkit-overflow-scrolling:touch', '.note-reader-text',
              'white-space:pre-wrap', 'user-select:text'):
    assert token in css, f'Falta UX de lectura: {token}'

print('OK: Notas compactas, apertura tocando tarjeta y visor completo desplazable de solo lectura')

from pathlib import Path

root=Path(__file__).resolve().parents[1]
js=(root/'app/src/main/assets/app.js').read_text(encoding='utf-8')
css=(root/'app/src/main/assets/styles.css').read_text(encoding='utf-8')
main=(root/'app/src/main/java/com/jruiz/nexo/MainActivity.java').read_text(encoding='utf-8')
entry=(root/'app/src/main/java/com/jruiz/nexo/core/DocumentEntry.java').read_text(encoding='utf-8')
db=(root/'app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java').read_text(encoding='utf-8')

card=js[js.index('function documentCard'):js.index('function openDocumentDetail')]
for token in ['document-card__pill','document-card__title','document-card__actions','recordIconAction("edit"','recordIconAction("eye"','recordIconAction("download"','recordIconAction("trash"']:
    assert token in card, token
assert 'data-document-share' not in card
for token in ['id="documentTitle"','id="documentLabel"','id="documentExpiry"','id="documentDescription"','expiryAt']:
    assert token in js, token
assert 'function openDocumentDetail' in js
assert 'getDocumentFileSize' in main
assert 'grid-template-columns:repeat(4,minmax(0,1fr))' in css
assert '.put("dueAt",expiryAt)' in entry
assert """return queryItems("type='document'", null, "updated_at DESC", 3000);""" in db

print('Documentos: gestión alineada con Nexo Familia: OK')

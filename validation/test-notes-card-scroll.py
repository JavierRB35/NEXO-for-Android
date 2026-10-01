from pathlib import Path

root=Path(__file__).resolve().parents[1]
js=(root/'app/src/main/assets/app.js').read_text(encoding='utf-8')
css=(root/'app/src/main/assets/styles.css').read_text(encoding='utf-8')

start=js.index('function noteCard(n){')
end=js.index('\nfunction bindNoteButtons',start)
card=js[start:end]

assert 'note-card-read' in card
assert 'aria-label="Lectura de nota"' in card
assert 'class="note-card-body"' in card
assert "esc(String(n.body||''))" in card, 'La ficha debe mostrar el cuerpo completo, no un snippet'
assert 'noteSnippet(n.body)' not in card, 'No debe recortarse la nota a 180 caracteres'
assert 'note-card-open" data-open-note=' not in card, 'La zona de lectura no debe abrir edición'
assert "recordIconAction('edit','Editar nota'" in card and 'data-open-note=' in card, 'Editar debe quedar en el lápiz'

for token in (
    'max-height:220px',
    'overflow-y:auto',
    'overscroll-behavior:contain',
    'touch-action:pan-y',
    '-webkit-overflow-scrolling:touch',
):
    assert token in css, f'Falta comportamiento de scroll de nota: {token}'

print('OK: notas completas y desplazables en la ficha; edición solo desde el lápiz')

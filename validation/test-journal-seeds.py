from pathlib import Path
import re
p=Path('app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java')
s=p.read_text(encoding='utf-8')
# Scope only the new library block.
a=s.index('private static void seedJournalBiblicalLibrary')
b=s.index('public long insertItem', a)
block=s[a:b]
entries=re.findall(r'^\s*\{"([^"]+)",\s*"([^"]+)",\s*"(Inspirada en:[^"]+)",\s*"([^"]+)"\},?\s*$', block, re.M)
if len(entries)!=40:
    raise SystemExit(f'Se esperaban 40 semillas y hay {len(entries)}')
if len({body for _,body,_,_ in entries})!=40:
    raise SystemExit('Hay frases duplicadas en las 40 semillas')
if not all(src.startswith('Inspirada en:') for _,_,src,_ in entries):
    raise SystemExit('Alguna frase carece de procedencia')
for legacy in ('seedJournalLibrary(', 'seedJournalPragmaticLibrary('):
    if legacy in s:
        raise SystemExit('Queda una llamada/método de biblioteca antigua: '+legacy)
if 'DB_VERSION = 34' not in s:
    raise SystemExit('DB_VERSION no es 26')
if 'source LIKE ? OR source LIKE ? OR source=? OR source LIKE ?' not in s:
    raise SystemExit('No se localiza la migración selectiva de semillas antiguas')
print('40 frases nuevas, fuentes y migración selectiva OK')

# La colección Radical Face debe sumarse, no sustituir las 40 frases existentes.
radical=Path('app/src/main/java/com/jruiz/nexo/data/RadicalFaceQuoteLibrary.java').read_text(encoding='utf-8')
radical_entries=re.findall(r'^\s*\{"([^"]+)",\s*"([^"]+)",\s*"([^"]+)",\s*"([^"]+)"\},?\s*$',radical,re.M)
if len(radical_entries)!=40:
    raise SystemExit(f'Se esperaban 40 frases de Radical Face y hay {len(radical_entries)}')
if len({body for _,body,_,_ in radical_entries})!=40:
    raise SystemExit('Hay duplicados en las frases de Radical Face')
if radical_entries[0][0]!='Welcome Home' or radical_entries[-1][0]!='Aftermath':
    raise SystemExit('No se reconoce el catálogo completo de Radical Face')
if 'seed-journal-radical-face-v26-' not in radical:
    raise SystemExit('Falta identificador estable de semillas Radical Face')
if 'Paráfrasis original; no es una cita literal.' not in radical:
    raise SystemExit('Falta identificar las reflexiones como paráfrasis')
if 'RadicalFaceQuoteLibrary.seed(db);' not in s or 'oldVersion < 26' not in s:
    raise SystemExit('Falta la carga de Radical Face en creación/migración de BD')
if "SELECT id FROM journal_entries WHERE source=? OR (kind='quote' AND body=?) LIMIT 1" not in radical:
    raise SystemExit('Falta protección contra duplicados en la colección Radical Face')
js=Path('app/src/main/assets/app.js').read_text(encoding='utf-8')
if "getJournalEntries('','quote',false)" not in js or 'NexoNative.getJournalDailyPick(' not in js:
    raise SystemExit('Las selecciones aleatorias deben continuar leyendo de la BD local')
print('40 frases previas + 40 de Radical Face, instalación, migración y selección aleatoria: OK')

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
if 'DB_VERSION = 24' not in s:
    raise SystemExit('DB_VERSION no es 14')
if 'source LIKE ? OR source LIKE ? OR source=? OR source LIKE ?' not in s:
    raise SystemExit('No se localiza la migración selectiva de semillas antiguas')
print('40 frases nuevas, fuentes y migración selectiva OK')

from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
js=(root/'app/src/main/assets/app.js').read_text(encoding='utf-8')
java=(root/'app/src/main/java/com/jruiz/nexo/MainActivity.java').read_text(encoding='utf-8')
db=(root/'app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java').read_text(encoding='utf-8')

checks={
 'acceso compra abre lista elegida': 'NexoNative.getPreferredShoppingList()' in js and 'if(id&&lists.some(l=>Number(l.id)===id)){openList(id);return;}' in js,
 'compra determinista por título': "normVoiceText(x.title)==='lista de compra'" in js,
 'UI renombrar': 'openRenameListModal' in js and 'NexoNative.renameList' in js,
 'UI duplicar': 'NexoNative.duplicateList' in js,
 'UI reiniciar': 'NexoNative.resetList' in js,
 'UI vaciar': 'NexoNative.clearList' in js,
 'UI eliminar': 'NexoNative.deleteList' in js,
 'confirmación eliminación': 'Esta acción no se puede deshacer.' in js,
 'puente renombrar': '@JavascriptInterface public boolean renameList' in java,
 'puente duplicar': '@JavascriptInterface public long duplicateList' in java,
 'puente reiniciar': '@JavascriptInterface public int resetList' in java,
 'puente vaciar': '@JavascriptInterface public int clearList' in java,
 'puente eliminar': '@JavascriptInterface public boolean deleteList' in java,
 'BD renombrar': 'public boolean renameList(long listId, String title)' in db,
 'BD duplicar': 'public long duplicateList(long listId)' in db,
 'BD reiniciar': 'public int resetList(long listId)' in db,
 'BD vaciar': 'public int clearList(long listId)' in db,
 'BD eliminar': 'public boolean deleteList(long listId)' in db,
 'borrado de elementos al borrar lista': 'db.delete("list_entries", "list_id=?"' in db,
 'transacción de borrado': re.search(r'public boolean deleteList\(long listId\).*?beginTransaction\(\).*?setTransactionSuccessful\(\)',db,re.S) is not None,
 'duplicado no reutiliza ID': 'newId = db.insertOrThrow("lists", null, list)' in db,
}
missing=[name for name,ok in checks.items() if not ok]
if missing:
    raise SystemExit('Fallos de gestión de listas: '+', '.join(missing))
print(f'Gestión de listas OK: {len(checks)} comprobaciones')

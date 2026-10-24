from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
read = lambda p: (root / p).read_text(encoding='utf-8')

db = read('app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java')
repo = read('app/src/main/java/com/jruiz/nexo/data/VehicleRepository.java')
main = read('app/src/main/java/com/jruiz/nexo/MainActivity.java')
html = read('app/src/main/assets/index.html')
ui = read('app/src/main/assets/vehicles.js')
app = read('app/src/main/assets/app.js')
backup = read('app/src/main/java/com/jruiz/nexo/backup/NexoBackupManager.java')

assert 'DB_VERSION = 34' in db
assert db.count('VehicleRepository.createTables(db);') == 2, 'Debe crearse tanto en instalación nueva como migración'
assert 'oldVersion < 34' in db
for table in ('vehicles', 'vehicle_services', 'vehicle_custom_operations', 'vehicle_due_rules'):
    assert 'CREATE TABLE IF NOT EXISTS ' + table in repo, table
assert 'sql.beginTransaction()' in repo and 'sql.setTransactionSuccessful()' in repo
assert 'operations_json' in repo and 'new JSONArray(operations)' in repo
assert 'SELECT id,attachment_path,attachment_name FROM vehicle_services' in db
assert 'SELECT 1 FROM vehicle_services WHERE attachment_path=?' in db
assert 'collectFiles(attachments, attachments, "attachments/", sources)' in backup

bridge_methods = [
    'getVehicles','saveVehicle','deleteVehicle','getVehicleServices',
    'saveVehicleService','deleteVehicleService','getVehicleOperations',
    'saveVehicleOperation','hideVehicleOperation',
    'getVehicleDueRules','saveVehicleDueRule','deleteVehicleDueRule'
]
exposed = set(re.findall(r'@JavascriptInterface\s+public\s+\w+\s+(\w+)\s*\(', main))
assert set(bridge_methods).issubset(exposed)
assert 'DocumentFiles.requireStored' in main and 'VehicleRepository.saveService(db,input)' in main
assert 'data-panel="vehicles"' in html
assert 'src="vehicles.js"' in html and 'href="vehicles.css"' in html
assert "else if(panel==='vehicles') window.NexoVehicles.render();" in app
assert "window.NexoVehicles.openService()" in app
assert "startsWith('vehicle-')" in app

for label in ('Filtro de aceite','Filtro de aire del motor','Filtro de combustible',
              'Filtro de habitáculo / polen','Pastillas de freno','Neumáticos'):
    assert label in ui
for feature in ('data-v-operation','saveVehicleService','saveVehicleOperation',
                'deleteVehicleService','getVehicleDueRules','getVehicleServices',
                'repeat','attachmentPath'):
    assert feature in ui.lower() if feature == 'repeat' else feature in ui
assert "new Set([...document.querySelectorAll('[data-v-operation]:checked')]" in ui
assert "selection:new Set(Array.isArray(chosen?.operations)" in ui
assert 'html(op.label)' in ui
assert 'window.NexoVehicles=' in ui
assert 'window.state.' not in ui

print('Mis vehículos: esquema SQLite, migración, bridge, navegación, catálogo y adjuntos OK')

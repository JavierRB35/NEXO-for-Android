from pathlib import Path
import json

root = Path(__file__).resolve().parents[1]
java = (root / "app/src/main/java/com/jruiz/nexo/updates/NexoUpdateManager.java").read_text()
job = (root / "app/src/main/java/com/jruiz/nexo/updates/NexoUpdateJob.java").read_text()
publisher = (root / ".github/workflows/publish-public-updates.yml").read_text()
policy = json.loads((root / ".github/nexo-update-policy.json").read_text())
gradle = (root / "app/build.gradle").read_text()

assert policy == {"critical": False, "reason": ""}, "3.7.50 debe publicarse como opcional"
assert "versionCode 143" in gradle and "versionName '3.7.50'" in gradle
assert 'result.put("critical", release.critical)' in java
assert 'result.put("updateType", release.critical ? "critical" : "optional")' in java
assert 'result.put("criticalReason", release.criticalReason)' in java
assert 'boolean critical = json.optBoolean("critical", false)' in java, "Los manifiestos anteriores no deben ser obligatorios"
assert 'json.opt("critical") instanceof Boolean' in java, "Tipo booleano estricto"
assert 'criticalReason.length() < 10 || criticalReason.length() > 300' in java
assert 'if (remote.critical)' in java
assert '.setTitle("Actualización CRÍTICA de Nexo")' in java
assert '.setCancelable(false)' in java
assert 'setCanceledOnTouchOutside(false)' in java
assert '.setNegativeButton("Salir de Nexo"' in java
assert '.setPositiveButton("Actualizar ahora"' in java
assert '.setTitle("Actualización opcional de Nexo")' in java
assert '.setNegativeButton("Más tarde"' in java
assert 'dialog.getButton(AlertDialog.BUTTON_POSITIVE)' in java, "No cerrar el diálogo crítico al descargar"
assert 'download(remote)' in java
assert 'hasOfficialSigner(pkg)' in java
assert 'release.sha256.equals(hex(digest.digest()))' in java
assert 'CRITICAL_CHANNEL' in job and 'NotificationManager.IMPORTANCE_HIGH' in job
assert 'release.critical ? "Actualización CRÍTICA de Nexo" : "Actualización opcional de Nexo"' in job
assert 'putBoolean("last_notified_critical", release.critical)' in job
for token in ("'.github/nexo-update-policy.json'", "'critical':critical", "'criticalReason':reason",
              "Una actualización crítica requiere un motivo", "No se puede modificar la criticidad"):
    assert token in publisher, token
print("OK: criticidad explícita, compatibilidad anterior, avisos y distribución verificada")

from pathlib import Path
import json

root = Path(__file__).resolve().parents[1]
java = (root / "app/src/main/java/com/jruiz/nexo/updates/NexoUpdateManager.java").read_text()
job = (root / "app/src/main/java/com/jruiz/nexo/updates/NexoUpdateJob.java").read_text()
publisher = (root / ".github/workflows/publish-public-updates.yml").read_text()
policy = json.loads((root / ".github/nexo-update-policy.json").read_text())
gradle = (root / "app/build.gradle").read_text()
summary = json.loads((root / ".github/nexo-update-summary.json").read_text(encoding="utf-8"))
notes = summary.get("notes")
assert summary.get("versionName") == "3.7.89", "El resumen debe coincidir con la versión Release"
assert isinstance(notes, str) and 40 <= len(notes.strip()) <= 360, (
    "El resumen de cambios debe contener entre 40 y 360 caracteres"
)
assert not any(mark in notes for mark in ("#", "**", "`", "\n", "\r")), (
    "El aviso público debe ser texto sencillo, sin Markdown ni saltos de línea"
)
assert "github.ref_type == 'tag' && inputs.tag || github.sha" in publisher, (
    "En publicaciones desde main usar metadatos del mismo commit firmado"
)

assert policy == {"critical": False, "reason": ""}, "3.7.89 debe publicarse como opcional"
assert "versionCode 182" in gradle and "versionName '3.7.89'" in gradle
assert 'result.put("critical", release.critical)' in java
assert 'result.put("updateType", release.critical ? "critical" : "optional")' in java
assert 'result.put("criticalReason", release.criticalReason)' in java
assert 'boolean critical = json.optBoolean("critical", false)' in java, "Los manifiestos anteriores no deben ser obligatorios"
assert 'json.opt("critical") instanceof Boolean' in java, "Tipo booleano estricto"
assert 'criticalReason.length() < 10 || criticalReason.length() > 300' in java
assert 'if (remote.critical)' in java
assert '.setTitle("Actualización necesaria de Nexo")' in java
assert '.setCancelable(false)' in java
assert 'setCanceledOnTouchOutside(false)' in java
assert '.setNegativeButton("Salir de Nexo"' in java
assert '.setPositiveButton("Actualizar ahora"' in java
assert '.setTitle("Nueva versión de Nexo")' in java
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

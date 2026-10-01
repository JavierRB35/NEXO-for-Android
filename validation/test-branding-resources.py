from pathlib import Path

root = Path(__file__).resolve().parents[1]
manifest = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
index = (root / "app/src/main/assets/index.html").read_text(encoding="utf-8")
colors = (root / "app/src/main/res/values/colors.xml").read_text(encoding="utf-8")

assert 'android:icon="@mipmap/ic_launcher"' in manifest
assert 'android:roundIcon="@mipmap/ic_launcher_round"' in manifest
assert 'src="nexo-toolbar-logo.png"' in index
assert (root / "app/src/main/assets/nexo-toolbar-logo.png").exists()

for density in ("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"):
    assert (root / f"app/src/main/res/mipmap-{density}/ic_launcher.png").exists()
    assert (root / f"app/src/main/res/mipmap-{density}/ic_launcher_round.png").exists()
    assert (root / f"app/src/main/res/drawable-{density}/ic_launcher_foreground.png").exists()
    assert (root / f"app/src/main/res/drawable-{density}/ic_notification_nexo.png").exists()

v26 = (root / "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml").read_text(encoding="utf-8")
v33 = (root / "app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml").read_text(encoding="utf-8")
assert "@color/nexo_launcher_background" in v26
assert "@drawable/ic_launcher_foreground" in v26
assert "@drawable/ic_launcher_monochrome" in v33
assert 'name="nexo_launcher_background"' in colors

for rel in (
    "app/src/main/java/com/jruiz/nexo/reminders/AlarmReceiver.java",
    "app/src/main/java/com/jruiz/nexo/medications/MedicationAlarmReceiver.java",
    "app/src/main/java/com/jruiz/nexo/recordings/RecordingService.java",
    "app/src/main/java/com/jruiz/nexo/transcription/TranscriptionService.java",
):
    code = (root / rel).read_text(encoding="utf-8")
    assert "R.drawable.ic_notification_nexo" in code, rel

print("OK: new branding resources wired to launcher, toolbar and notifications")

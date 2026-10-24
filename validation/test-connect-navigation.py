#!/usr/bin/env python3
"""Guard the direct, non-duplicated Connect navigation and preserve share paths."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
html = (root / "app/src/main/assets/index.html").read_text()
js = (root / "app/src/main/assets/app.js").read_text()
css = (root / "app/src/main/assets/styles.css").read_text()
main = (root / "app/src/main/java/com/jruiz/nexo/MainActivity.java").read_text()
activity = (root / "app/src/main/java/com/jruiz/nexo/connectui/NexoConnectActivity.java").read_text()
settings = html.split('id="nexoConnectExperimentSection"', 1)[1].split(
    'data-settings-accordion="weather"', 1)[0]
contacts = activity.split('private void people(){', 1)[1].split(
    'private void configureChatNotices()', 1)[0]
home = activity.split('@Override protected void showHome(){', 1)[1].split(
    '@Override protected void onVerifiedPilotReady()', 1)[0]

ids = (
    "nexoConnectProfileOpen", "nexoConnectCodeOpen",
    "nexoConnectGenerateOpen", "nexoConnectPairOpen",
    "nexoConnectInvitationOpen", "nexoConnectPeopleOpen"
)
for entry in ids:
    assert settings.count('id="' + entry + '"') == 1, entry
    assert "'#" + entry + "'" in js, entry
for title in ("Mi identidad", "Vinculación", "Gestión de contactos"):
    assert title in settings, title
assert settings.index("Mi identidad") < settings.index("Vinculación") < settings.index("Gestión de contactos")
assert 'nexoConnectEmbeddedOpen' not in settings
assert 'nexoConnectPilotOpen' not in settings
assert 'Compartir una ficha cifrada' not in settings
assert 'nexoConnectEmbeddedOpen' not in js and 'nexoConnectPilotOpen' not in js
assert ".settings-connect-group" in css
assert 'openNexoConnectSettings(String action)' in main
for action in ("profile", "code", "generate", "pair", "invitation", "people"):
    assert '"' + action + '".equals(action)' in main
    assert '"' + action + '".equals(action)' in home
assert 'screen("Chat y compartir"' not in activity
assert 'if(launchedFromSettings()){finish();return;}' in home
assert 'showChats();' in home
assert 'screen("Mis personas",true)' in contacts
for action in ('Editar nombre', 'Comprobar', 'Desvincular'):
    assert action in contacts, action
for obsolete in ('Generar vinculación', 'Vincular dispositivo',
                 'Abrir invitación recibida', 'Mi código de seguridad'):
    assert obsolete not in contacts, obsolete
assert 'confirmUnlink(p,this::people)' in contacts
assert 'client.verifyStoredPeer(trusted,p.peerId)' in contacts
assert 'client.setReceiving(trusted,p.peerId,true)' in contacts
assert 'button("Añadir persona",this::people)' not in activity
assert "NexoNative.openNexoConnectShare(section,Number(id))" in js
assert 'NexoNative.openNexoConnectShare(\'items\',Number(id))' in js
assert 'putExtra("nexoShareId",localId)' in main
assert 'offerHostEntity(draft.entity,draft.safeData)' in activity
print("Nexo Connect: direct settings, compact people, Chat/Inbox and ficha sharing OK")

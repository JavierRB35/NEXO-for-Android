#!/usr/bin/env python3
"""Static guards for Nexo Connect pairing UX and safe existing-link recovery.
Android instrumented tests cover the actual safety code and Keystore behavior.
"""
from pathlib import Path

root=Path(__file__).resolve().parents[1]
vault=root/"experiments/nexo-connect-android-vault/vault/src/main/java/com/jruiz/nexo/connect/vault"
pilot=(root/"experiments/nexo-connect-android-vault/pilot/src/main/java/com/jruiz/nexo/connect/vault/NexoConnectPilotActivity.java").read_text()
ui=(vault/"QrTrustConfirmationView.java").read_text()
own=(vault/"NexoConnectOwnQrView.java").read_text()
safety=(vault/"NexoConnectSafetyCode.java").read_text()
host=(root/"app/src/main/java/com/jruiz/nexo/connectui/NexoConnectActivity.java").read_text()

assert "NEXO-CONNECT-IDENTITY-SAFETY-CODE-V1" in safety
assert "peer.signingPublicRaw()" in safety and "peer.encryptionPublicRaw()" in safety
assert "peer.peerId.toString()" in safety and "peer.generation" in safety
assert "MessageDigest.isEqual" in safety and "0-9A-F]{16}" in safety
assert "NexoConnectSafetyCode.verify(candidate,independentCode.getText()" in ui
assert "saveQrConfirmed(scannedQr,candidate.signingFingerprint()" in ui
assert "candidate.encryptionFingerprint(),true)" in ui
assert "new EditText(context)" in ui and ui.count("new EditText(context)") == 1
assert "La persona me ha facilitado el código por otro medio" in ui
assert "NexoConnectSafetyCode.forPeer(self)" in own
assert "independentCode.setText(" not in ui, "Never prefill from QR or server"
assert "client.acceptVerifiedInvitation(invitation,trusted,true)" in pilot
assert "if(unexpected.httpStatus!=503)throw unexpected" in pilot
assert "client.verifyStoredPeer(trusted,id);newlyLinked=false;" in pilot
assert "if(newlyLinked)client.verifyStoredPeer(trusted,id);" in pilot
assert "client.setReceiving(trusted,id,true);" in pilot
assert 'check.setContentDescription("Comprobar conexión con "+name)' in host
assert "0xff0f766e" in pilot and "0xff0f766e" in ui
print("Nexo Connect pairing structural guards OK")

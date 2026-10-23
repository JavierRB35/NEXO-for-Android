#!/usr/bin/env python3
"""Nexo Connect profile/pairing invariants: supplement device instrumentation."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
vault=r/"experiments/nexo-connect-android-vault/vault/src/main/java/com/jruiz/nexo/connect/vault"
pilot=r/"experiments/nexo-connect-android-vault/pilot/src/main/java/com/jruiz/nexo/connect/vault"
host=(r/"app/src/main/java/com/jruiz/nexo/connectui/NexoConnectActivity.java").read_text()
store=(vault/"NexoConnectChatStore.java").read_text()
photo=(vault/"NexoConnectProfileAvatar.java").read_text()
transport=(pilot/"NexoConnectChatTransport.java").read_text()
link=(pilot/"NexoConnectPilotActivity.java").read_text()
bridge=(r/"app/src/main/java/com/jruiz/nexo/MainActivity.java").read_text()
html=(r/"app/src/main/assets/index.html").read_text()
js=(r/"app/src/main/assets/app.js").read_text()

for key in ("nexoConnectProfileOpen","nexoConnectGenerateOpen","nexoConnectPairOpen"):
    assert 'id="'+key+'"' in html, key
    assert "'#"+key+"'" in js, key
assert 'openNexoConnectSettings(String action)' in bridge
assert '"profile".equals(action)' in bridge and '"generate".equals(action)' in bridge
assert '"pair".equals(action)' in bridge
assert 'NexoConnectSafetyCode.grouped(NexoConnectSafetyCode.forPeer(own))' in link
assert 'if(hasSavedPilotSession()&&!resumed)' in host
assert 'chatShell("Chat",null,this::finish)' in host
assert 'NexoConnectChatStore.setOwnAvatar' in host
assert 'NexoConnectChatStore.setLocalAlias' in host
assert 'protected android.view.View contactAvatar' in host
assert 'chooseVerifiedPeers(peers' in link
assert 'MAX_JPEG=2400' in photo and 'MAX_SOURCE=8*1024*1024' in photo
assert 'getScheme()' in photo and '"content".equals(uri.getScheme())' in photo
assert 'readExifOrientation(data)' in photo and 'applyExifOrientation(crop,' in photo
assert 'ExifInterface.TAG_ORIENTATION' in photo
assert all(token in photo for token in (
    'ORIENTATION_ROTATE_90','ORIENTATION_ROTATE_180','ORIENTATION_ROTATE_270',
    'ORIENTATION_FLIP_HORIZONTAL','ORIENTATION_FLIP_VERTICAL',
    'ORIENTATION_TRANSPOSE','ORIENTATION_TRANSVERSE'))
assert 'NexoConnectProfileAvatar.validate' in store
assert 'profilePayload(Context c,UUID account)' in store
assert 'payload(ownName(c,account),"avatar:"+ownAvatar(c,account),true)' in store
assert '"profile".equals(body.optString("kind"))' in store
assert 'NexoConnectChatStore.profilePayload(c,identity.accountId())' in transport
assert 'client.sendApproved(' in transport
assert 'NexoConnectChatTransport.syncProfiles(this,client,identity,trusted)' in host
assert 'if(!alias.isEmpty())return alias;' in store
assert 'avatar:'+'' in store
assert 'NEXO_CONNECT_PROFILE' not in bridge
print('Nexo Connect profile UX and encrypted avatar structural regressions OK')


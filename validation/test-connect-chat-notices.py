#!/usr/bin/env python3
"""Static regressions for Nexo Connect unread badges and local Android alerts."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
vault=r/'experiments/nexo-connect-android-vault/vault/src/main/java/com/jruiz/nexo/connect/vault'
pilot=r/'experiments/nexo-connect-android-vault/pilot/src/main/java/com/jruiz/nexo/connect/vault'
job=(pilot/'NexoConnectInboxNoticeJob.java').read_text()
store=(vault/'NexoConnectChatStore.java').read_text()
activity=(r/'app/src/main/java/com/jruiz/nexo/connectui/NexoConnectActivity.java').read_text()
main=(r/'app/src/main/java/com/jruiz/nexo/MainActivity.java').read_text()
js=(r/'app/src/main/assets/app.js').read_text()
html=(r/'app/src/main/assets/index.html').read_text()
css=(r/'app/src/main/assets/styles.css').read_text()
assert 'unreadIds(SQLiteDatabase db,UUID account' in store
assert 'nexo_chat_reads WHERE account=? AND peer=?' in store
assert 'if(!item.outgoing&&!read.contains(item.delivery))' in store
assert 'checkSoon(Context context)' in job
assert 'if(!stopped&&CHECKING.compareAndSet(false,true))' in job
assert 'candidates.addAll(unread)' in job
assert 'if(candidates.isEmpty()||stopped||!permitted(this))return;' in job
assert '.putExtra("openChat",true)' in job
assert 'Mensajes y fichas de Nexo Connect' in job
assert 'CHAT_NOTIFICATION_PERMISSION' in activity
assert 'askFirstChatNotificationPermission()' in activity
assert 'Activar avisos de mensajes' in activity
assert 'NexoConnectChatStore.unreadIds(db,identity.accountId,trusted)' in activity
assert '@JavascriptInterface public int getUnreadChatCount()' in main
assert '@JavascriptInterface public void requestNexoChatRefresh()' in main
assert 'id="chatNavBadge"' in html
assert '.chat-nav-badge' in css
assert 'function refreshChatUnreadBadge()' in js
assert 'NexoNative.requestNexoChatRefresh()' in js
print('OK: Connect unread, notification permission, background delivery and badges')

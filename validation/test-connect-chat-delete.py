#!/usr/bin/env python3
"""Static guards for safe local-only Nexo Connect chat removal UX."""
from pathlib import Path

root=Path(__file__).resolve().parents[1]
activity=(root/"app/src/main/java/com/jruiz/nexo/connectui/NexoConnectActivity.java").read_text()
row=(root/"app/src/main/java/com/jruiz/nexo/connectui/NexoConnectChatRowActions.java").read_text()
selection=(root/"app/src/main/java/com/jruiz/nexo/connectui/NexoConnectMessageSelection.java").read_text()
dialogs=(root/"app/src/main/java/com/jruiz/nexo/connectui/NexoConnectChatDeleteDialogs.java").read_text()
vault=root/"experiments/nexo-connect-android-vault/vault/src/main/java/com/jruiz/nexo/connect/vault"
deletion=(vault/"NexoConnectChatDeletion.java").read_text()
store=(vault/"NexoConnectChatStore.java").read_text()
backup=(vault/"NexoConnectChatBackup.java").read_text()

assert "NexoConnectChatRowActions.wrap(this,row," in activity
assert "NexoConnectChatDeletion.wasCleared(db,identity.accountId(),p.peerId)" in activity
assert "messageSelection.bindBubble(bubble,m.delivery)" in activity
assert "messageSelection!=null&&messageSelection.active()" in activity
assert "1000" in row and "postDelayed(hold,1000)" in row
assert "translationX" in row and 'setText("Eliminar")' in row
assert "postDelayed(longHold,1000)" in selection
assert "chosen.add(id)" in selection and "chosen.remove(id)" in selection
assert "header.setVisibility" in selection and "Cancelar selección" in selection
assert "0xffbc2840" in dialogs and "0xffbc2840" in selection
assert "Eliminar para mí" in dialogs and "Cancelar" in dialogs
assert "NexoConnectChatDeletion.deleteMessages" in dialogs
assert "NexoConnectChatDeletion.deleteConversation" in dialogs
assert "nexo_chat_deleted" in deletion and "nexo_chat_cleared" in deletion
assert "DELETE FROM nexo_chat_outgoing" in deletion
assert "DELETE FROM nexo_chat_restored" in deletion
assert "NexoConnectChatDeletion.visibility(db,account,peer)" in store
assert "NexoConnectChatDeletion.visibility(db,owner,UUID.fromString(peer))" in backup
assert "if(view.hidden(id,body.getLong(" in backup
assert "if(NexoConnectChatDeletion.visibility(db,account,peer).hidden" in backup
assert "client." not in deletion and "supabase" not in deletion.lower()
assert "new AlertDialog.Builder(activity)" in dialogs
print("Nexo Connect local-chat deletion UX structural guards OK")

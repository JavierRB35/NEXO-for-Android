from pathlib import Path

root=Path(__file__).resolve().parents[1]
db=(root/'app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java').read_text(encoding='utf-8')
service=(root/'app/src/main/java/com/jruiz/nexo/core/NexoService.java').read_text(encoding='utf-8')
html=(root/'app/src/main/assets/index.html').read_text(encoding='utf-8')
js=(root/'app/src/main/assets/app.js').read_text(encoding='utf-8')
css=(root/'app/src/main/assets/styles.css').read_text(encoding='utf-8')

# Relación real evento-persona.
assert 'DB_VERSION = 23' in db
assert 'CREATE TABLE IF NOT EXISTS item_people (' in db
assert 'PRIMARY KEY(item_id,person_id)' in db
assert 'public boolean replaceItemPeople(long itemId, JSONArray ids)' in db
assert 'public JSONArray getItemPeople(long itemId)' in db
assert 'if(in.has("participantIds"))replaceItemPeople(id,in.optJSONArray("participantIds"))' in db
assert 'if(in.has("participantIds"))replaceItemPeople(id,in.optJSONArray("participantIds"))' in db
assert 'item.put("participantIds",ids)' in db
assert 'item.put("participants",participants)' in db
assert 'db.delete("item_people", "item_id=?"' in db

# Recurrentes: single, following y all conservan/actualizan personas.
assert '"participantsText","participantIds","durationMinutes"' in service
assert service.count('"participantsText","participantIds","durationMinutes"') >= 3
assert '"participantIds"' in db[db.index('private JSONObject buildOccurrence'):db.index('public JSONArray getFutureReminders')]

# Crear evento: mismo selector visual de Nexo Familia.
assert 'id="capturePeopleAssignment"' in html
assert 'id="capturePeopleChips" class="member-chips"' in html
assert '<legend>Personas incluidas</legend>' in html
assert 'function renderPersonAssignment(hostId,selectedIds,onChange)' in js
assert 'data-people-all' in js
assert 'data-person-assignment' in js
assert 'member-color-dot' in js
assert 'participantIds:normalizeParticipantIds(state.captureParticipantIds)' in js

# Editar evento y aparición recurrente.
item=js[js.index('function openItem(id)'):js.index('function openOccurrence',js.index('function openItem(id)'))]
occ=js[js.index('function openOccurrence'):js.index('function chooseRecurrenceScope',js.index('function openOccurrence'))]
assert "eventPeopleAssignmentHtml('editPeopleChips')" in item
assert "renderPersonAssignment('editPeopleChips'" in item
assert 'participantIds:normalizeParticipantIds(editParticipantIds)' in item
assert "eventPeopleAssignmentHtml('occPeopleChips')" in occ
assert "renderPersonAssignment('occPeopleChips'" in occ
assert 'participantIds:normalizeParticipantIds(occParticipantIds)' in occ

# Apariencia copiada de Nexo Familia y representación uniforme.
for token in ['.member-chips{','.member-chip{','.member-chip--active{','.member-color-dot{','.member-assignment legend{']:
    assert token in css, token
assert 'Array.isArray(item?.participants)&&item.participants.length' in js
assert "color:String(p.color||'#3158D4')" in js
assert 'function eventParticipantAvatarsHtml(item,extraClass=' in js
assert 'participants.slice(0,4)' in js and 'participants.length>4' in js
agenda=js[js.index('function agendaCard'):js.index('function prepareHomeCapture')]
generic=js[js.index('function itemCard'):js.index('function bindItemButtons')]
search=js[js.index('function familySearchCard'):js.index('function openFamilySearchResult')]
assert "eventParticipantAvatarsHtml(i,'event-participant-avatars--agenda')" in agenda
assert "eventParticipantAvatarsHtml(i,'event-participant-avatars--item')" in generic
assert "eventParticipantAvatarsHtml(item.raw,'event-participant-avatars--search')" in search
assert item.count("eventParticipantAvatarsHtml(i,'event-participant-avatars--detail')") == 1
assert occ.count("eventParticipantAvatarsHtml(i,'event-participant-avatars--detail')") == 1
assert 'Asignado a' not in js and "'asignado'" not in js
assert "const involved=item.participantsText?'Personas: '+item.participantsText:'';" in js
assert "kind:'person'" in js and "toggleSettingsAccordion('people')" in js
for token in ['.event-participant-avatars--agenda{','.event-participant-avatars--item{','.event-participant-avatars--detail{','.event-participant-avatars--search{']:
    assert token in css, token

print('Asignación evento-persona: formularios, recurrentes, Agenda, búsqueda y fichas OK')

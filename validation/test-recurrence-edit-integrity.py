from pathlib import Path

root=Path(__file__).resolve().parents[1]
js=(root/'app/src/main/assets/app.js').read_text(encoding='utf-8')
service=(root/'app/src/main/java/com/jruiz/nexo/core/NexoService.java').read_text(encoding='utf-8')

# Editar participantes de una aparición futura no puede copiar su endAt al evento base.
whole_series='for (String key:new String[]{"title","body","category","type","allDay","timeZone","mapsUrl","recurrenceRule","participantsText","participantIds","durationMinutes","locationText","priority","favorite","needsReview"})'
assert whole_series in service
assert '"type","endAt","allDay"' not in service[service.index('// Toda la serie.'):service.index('String allRecurrence=',service.index('// Toda la serie.'))]

# Si cambia cualquier parte temporal, el backend recalcula due/end manteniendo duración coherente.
assert "(endTime||'')!==(originalEndTime||'')" in js
assert "allDay!==originalAllDay" in js
assert "payload.put(\"endAt\", shiftedBase + Math.max(0L,occurrenceEnd-occurrenceDue))" in service

# Cambiar solo participantes no debe generar una segunda actualización de historial por voiceEnabled.
assert "originalVoiceEnabled=!!i.voiceEnabled" in js
assert "$('#occVoiceEnabled').checked!==originalVoiceEnabled" in js

print('Recurrencias 3.7.7: límites temporales e historial de edición protegidos')

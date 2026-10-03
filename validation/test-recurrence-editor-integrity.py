from pathlib import Path

root=Path(__file__).resolve().parents[1]
service=(root/'app/src/main/java/com/jruiz/nexo/core/NexoService.java').read_text(encoding='utf-8')
app=(root/'app/src/main/assets/app.js').read_text(encoding='utf-8')

# Al editar "Toda la serie", endAt de la aparición visible NO se copia a la
# fila base salvo que la fecha/hora realmente haya cambiado.
all_marker='// Toda la serie.'
start=service.index(all_marker)
end=service.index('String allRecurrence=',start)
block=service[start:end]
copy_line=next(line for line in block.splitlines() if 'for (String key:new String[]{' in line)
assert '"endAt"' not in copy_line, 'endAt no puede copiarse sin desplazar la serie'
assert 'if(in.optBoolean("dateChanged",false) && ov.has("dueAt"))' in block
assert 'payload.put("endAt", shiftedBase +' in block

# El editor detecta también cambios de hora final y Todo el día.
assert "originalEndTime=!allDay?endTime:''" in app
assert 'originalAllDay=allDay' in app
assert "(endTime||'')!==(originalEndTime||'')" in app
assert 'allDay!==originalAllDay' in app

# Guardar participantes no debe provocar una segunda updateItem solo por existir
# el checkbox de voz.
assert 'originalVoiceEnabled=!!i.voiceEnabled' in app
assert "$('#occVoiceEnabled')&&$('#occVoiceEnabled').checked!==originalVoiceEnabled" in app

print('Editor de recurrencias: duración base y doble actualización protegidas')

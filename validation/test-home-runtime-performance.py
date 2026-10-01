from pathlib import Path

root=Path(__file__).resolve().parents[1]
js=(root/'app/src/main/assets/app.js').read_text(encoding='utf-8')
main=(root/'app/src/main/java/com/jruiz/nexo/MainActivity.java').read_text(encoding='utf-8')
db=(root/'app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java').read_text(encoding='utf-8')

render=js[js.index('function renderToday('):js.index('function homeParticipantData')]
search=js[js.index('function runHomeSearch(query)'):js.index('function openHomeToday')]
dashboard=db[db.index('public JSONObject getHomeDashboardData()'):db.index('public JSONObject getTodayData()')]

assert 'NexoNative.getHomeDashboard()' in render
assert 'dashboardOverride' in render
assert 'state.startupHomeDashboard' in render
assert 'NexoNative.getCalendar(' not in render
assert '740*86400000' not in render
assert 'buildFamilyStyleSearchIndex();' not in render
assert 'if(!state.homeSearchIndexReady)buildFamilyStyleSearchIndex();' in search
assert '@JavascriptInterface public String getHomeDashboard()' in main
assert 'long end7 = today.plusDays(7)' in dashboard
assert 'JSONArray near = getCalendar(start, end7);' in dashboard
assert 'findNextOccurrence(id,end7)' in dashboard
assert 'getFamilyStyleSearchIndex' not in dashboard

print('OK: Inicio usa resumen ligero, recurrencias acotadas y búsqueda bajo demanda')

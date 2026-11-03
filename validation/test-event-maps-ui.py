from pathlib import Path

root=Path(__file__).resolve().parents[1]
app=(root/'app/src/main/assets/app.js').read_text(encoding='utf-8')
native=(root/'app/src/main/java/com/jruiz/nexo/MainActivity.java').read_text(encoding='utf-8')
css=(root/'app/src/main/assets/styles.css').read_text(encoding='utf-8')
place=(root/'app/src/main/java/com/jruiz/nexo/core/PlaceEntry.java').read_text(encoding='utf-8')

for token in (
    "function eventLocationShortcutHtml(item,buttonId)",
    "function eventDetailOverviewHtml(item,alerts,locationButtonId,statusOverride='')",
    "function bindEventMapActions(item,...ids)",
    "eventDetailOverviewHtml(i,originalAlerts,'eventDetailLocationBtn')",
    "eventDetailOverviewHtml(i,occAlerts,'occDetailLocationBtn',currentState)",
    "eventHasLocation(i)?'eventDetailMapsBtn':''",
    "eventHasLocation(i)?'occDetailMapsBtn':''",
    "bindEventMapActions(i,'eventDetailMapsBtn','eventDetailLocationBtn')",
    "bindEventMapActions(i,'occDetailMapsBtn','occDetailLocationBtn')",
    "bar.insertBefore(mapButton,bar.querySelector('.record-action-overflow'))",
    "NexoNative.openLocationDirections(destination)",
    "if(maps||place){NexoNative.openLocationDirections(maps||place);return;}",
    "openEventLocation(i);",
):
    assert token in app, f'Falta Maps en ficha: {token}'
assert "NexoNative.openExternal(String(i.mapsUrl))" not in app
assert "NexoNative.openPlaceMap(place)" not in app
assert ".event-location-shortcut__button" in css and ".event-maps-footer-action" in css
assert "destination = PlaceEntry.mapsUrl(locationText)" in native
assert 'maps.setPackage("com.google.android.apps.maps")' in native
assert "startActivity(new Intent(Intent.ACTION_VIEW, uri))" in native
assert "MainActivity.this.openLocationDirections(location)" in native
assert 'https://www.google.com/maps/search/?api=1&query=' in place
assert 'uri.getUserInfo()!=null' in place
print('Maps en fichas: acceso sin editar, pie fijo, enlace seguro y respaldo verificados')

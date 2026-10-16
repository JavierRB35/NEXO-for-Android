from pathlib import Path
root = Path(__file__).resolve().parents[1]
java = (root/'app/src/main/java/com/jruiz/nexo/weather/HeaderWeather.java').read_text()
main = (root/'app/src/main/java/com/jruiz/nexo/MainActivity.java').read_text()
app = (root/'app/src/main/assets/app.js').read_text()
html = (root/'app/src/main/assets/index.html').read_text()
css = (root/'app/src/main/assets/styles.css').read_text()
for token in ('LocationManager.GPS_PROVIDER','LocationManager.NETWORK_PROVIDER','getCurrentLocation(','MAX_LOCATION_AGE_MS','Geocoder','getLocality()','precise_prompted','manual_lat','manual_lon','geocoding-api.open-meteo.com/v1/search','name=" +','weatherCallback.accept(data)','searchGeneration','accuracyMeters'):
    assert token in java, f'Falta control de ubicación: {token}'
assert java.index('if (manual())') < java.index('boolean fine ='), 'El modo manual no debe necesitar permisos'
assert java.index('GPS_PROVIDER',java.index('gpsRequested = false')) < java.index('NETWORK_PROVIDER',java.index('gpsRequested = false'))
assert '%.5f&longitude=%.5f' in java, 'Previsión con coordenadas precisas'
assert '%.2f, %.2f' not in java, 'Búsqueda de Google no debe redondear coordenadas'
assert 'Uri.encode("el tiempo en " + place)' in java
for method in ('getHeaderWeatherSettings','setHeaderWeatherAutomatic','setHeaderWeatherManualPlace','searchHeaderWeatherPlaces'):
    assert '@JavascriptInterface public ' in main and f'{method}(' in main
for name in ('headerWeatherPlace','weatherLocationMode','weatherManualFields','weatherPlaceQuery','weatherPlaceResults','weatherPlaceSearch'):
    assert f'id="{name}"' in html
for token in ('function renderWeatherSettings()','function lookupWeatherPlaces()','function renderWeatherPlaceResults(response)','onHeaderWeatherPlaces(raw)','NexoNative.setHeaderWeatherManualPlace','header-weather-place'):
    assert token in app or token in css
assert 'margin-left:14px' in css
assert 'font-size:1.45rem' in css
print('OK: cabecera meteorológica y modos GPS/manual cubiertos')

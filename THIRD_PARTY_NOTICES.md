# Third-party notices

Nexo 3.4.9 integra o descarga componentes de terceros según sus respectivas licencias.

## sherpa-onnx

- Proyecto: `k2-fsa/sherpa-onnx`
- Versión fijada para el runtime Android: `1.13.7`
- El AAR se descarga en la primera compilación mediante `prepare-transcription-runtime.sh` y se verifica por SHA-256.
- Consultar el repositorio upstream para licencia y avisos completos.

## Whisper

- Modelo utilizado: Whisper Base multilingüe, convertido a ONNX y cuantizado INT8 para sherpa-onnx.
- Archivos descargados bajo demanda desde `csukuangfj/sherpa-onnx-whisper-base`.
- Los archivos del modelo no se incluyen en este repositorio ni en el APK.

## Kotlin standard library

- `org.jetbrains.kotlin:kotlin-stdlib:1.9.24`.
- Necesaria porque el AAR Android de sherpa-onnx 1.13.7 expone su API Kotlin/JVM.


Open-Meteo weather data (https://open-meteo.com/)
Used to display current weather and temperature. Data licensed under Creative Commons Attribution 4.0 International (CC BY 4.0): https://creativecommons.org/licenses/by/4.0/ . Values are displayed rounded in the interface and geographic coordinates are rounded before API queries. Open-Meteo free API is for non-commercial use.

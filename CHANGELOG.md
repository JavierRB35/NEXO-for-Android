# Changelog de Nexo

## 3.4.9 · versionCode 55

- Conciliación automática de citas/tareas con fechas pasadas.
- Reprogramación a hoy/futuro restablece el estado a pendiente.
- Cumpleaños completados según la aparición del año actual y reinicio del ciclo anual.
- Se conserva la lógica de transcripción, reproductor y medicación de 3.4.8.
- Fuente reconstruido de forma controlada a partir de 3.3.2 y del historial validado de 3.4.x.

## 3.4.8 · versionCode 54

- Frases de avisos de medicación editables desde Ajustes.
- Variables `{medicacion}` y `{hora}`.
- Restauración de frases por defecto.

## 3.4.7 · versionCode 53

- Avisos de medicación fijos en -5, 0, +5, +10, +15 y +20 min.
- Fin definitivo de avisos después de +20 min.
- Eliminación de “Posponer 10 min” para no crear avisos fuera de la secuencia.
- Nombre y dosis presentes en los mensajes.
- Humor progresivo en los avisos tardíos.

## 3.4.6 · versionCode 52

- Dependencia explícita `kotlin-stdlib 1.9.24` para el AAR Android de sherpa-onnx.
- Corrige `NoClassDefFoundError: kotlin/jvm/internal/Intrinsics`.
- Primera versión de la serie 3.4.x confirmada funcional para transcripción local en dispositivo.

## 3.4.5 · versionCode 51

- Motor de transcripción aislado en proceso Android `:transcription`.
- Diagnóstico interno persistente por fases.
- Reproductor con posición y velocidades 1×/1,5×/2×.

## 3.4.4 · versionCode 50

- Reproductor mejorado con barra arrastrable y velocidad variable.
- Transcripción desactivada temporalmente durante diagnóstico de fallos nativos.

## 3.4.3 · versionCode 49

- Adaptación a la API Kotlin/JVM incluida realmente en el AAR Android de sherpa-onnx.

## 3.4.2 · versionCode 48

- Sustitución de reflexión por API Java directa de sherpa-onnx; descartada posteriormente por discrepancia con el AAR Android.

## 3.4.1 · versionCode 47

- Estado visual persistente de transcripción: preparando, transcribiendo, terminada o error.
- Acceso directo a ver/guardar TXT.
- Corrección del detalle de errores de transcripción.

## 3.4.0 · versionCode 46

- Transcripción 100 % local con Whisper Base multilingüe INT8 + sherpa-onnx.
- Descarga del modelo fuera del APK.
- Procesamiento por bloques y exportación de transcripciones.

## 3.3.2 · versionCode 45

- Base estable conservada: grabadora local, documentos, copias, agenda, medicación, notas, listas, lugares, calendario y resto de módulos previos.

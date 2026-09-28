# Nexo 3.4.9 Release · AndroidIDE

Nexo es una aplicación Android local-first para agenda, tareas, recordatorios, medicamentos, notas, documentos, lugares, listas, cuaderno, aparcamiento y grabaciones.

Esta rama corresponde a **Nexo 3.4.9**, `versionCode 55`, `applicationId com.jruiz.nexo`.

> Reconstrucción controlada: el fuente 3.4.9 se ha reconstruido desde la copia íntegra 3.3.2 conservada y los cambios documentados/validados de las versiones 3.4.x. No se pretende afirmar identidad binaria con el ZIP 3.4.9 temporal original; sí reproducir la funcionalidad acordada y conservar identidad de aplicación, BD y firma Release.

## Entorno AndroidIDE validado

- AndroidIDE-Rv2 / ARM64
- OpenJDK 17
- Android SDK 34
- Build Tools 34.0.4
- Gradle 8.9
- AGP 8.7.3
- `minSdk 26`, `targetSdk 34`, `compileSdk 34`

No actualices estas versiones salvo necesidad.

## Compilar en el teléfono

Descomprime el proyecto directamente como:

```text
/storage/emulated/0/Download/Nexo_v3_4_9_RELEASE_AndroidIDE/
```

Después:

```sh
cd "/storage/emulated/0/Download/Nexo_v3_4_9_RELEASE_AndroidIDE"
sh build-on-phone.sh
```

El script:

1. descarga y verifica `sherpa-onnx 1.13.7` si todavía no existe en `app/libs/`;
2. prepara/restaura la firma Release permanente;
3. compila con `JAVA_OPTS=-Xmx768m`, `--no-daemon`, `--max-workers=1` y AAPT2 de AndroidIDE;
4. verifica la firma;
5. copia el APK a:

```text
/storage/emulated/0/Download/Nexo_v3_4_9_RELEASE.apk
```

La primera compilación necesita Internet para descargar el AAR de sherpa-onnx. No se guarda el AAR de 49 MB en Git.

## Cambios principales 3.4.x

### Grabaciones y transcripción

- Grabación local `.m4a` mediante servicio en primer plano.
- Reproductor con barra de posición arrastrable.
- Velocidades de reproducción `1×`, `1,5×` y `2×`.
- Transcripción local con **Whisper Base multilingüe INT8** mediante `sherpa-onnx 1.13.7`.
- Modelo descargable fuera del APK: encoder INT8 + decoder INT8 + tokens (~161 MB).
- Motor de transcripción en proceso separado `:transcription` para aislar fallos nativos.
- Audio procesado en bloques de ~28 s, sin crear un PCM temporal gigante.
- Progreso, cancelación, diagnóstico persistente y marcas `[HH:MM:SS]`.
- Texto privado en `files/transcriptions/` con copiar, compartir, exportar `.txt` y borrar.
- Al borrar una grabación también se elimina su transcripción.

### Medicación

Serie fija de avisos para cada toma:

```text
-5, 0, +5, +10, +15, +20 minutos
```

Después de `+20` no se vuelve a avisar. Si la toma se marca como tomada u omitida, se cancelan los avisos restantes.

Las seis frases son editables desde **Ajustes > Mensajes de medicación**, con:

- `{medicacion}` → nombre + dosis.
- `{hora}` → hora prevista.

Valores por defecto:

1. `En 5 minutos te toca tomar {medicacion}.`
2. `Es la hora de tomar {medicacion}.`
3. `Pequeño recordatorio: {medicacion} sigue esperando.`
4. `Eh… {medicacion} no se va a tomar solo 😄`
5. `Vamos, que ya llevamos un rato negociando con {medicacion}.`
6. `Último aviso: {medicacion} empieza a pensar que lo estás evitando.`

Existe un botón para restaurar estas frases por defecto.

### Estados por fecha

- Cita/tarea no recurrente con fecha **anterior a hoy** y todavía abierta → `Finalizada`.
- Elemento con fecha **hoy** → no se cambia automáticamente por el mero paso del tiempo.
- Si una cita/tarea se reprograma a **hoy o futuro** → `Pendiente`, aunque antes estuviera finalizada.
- Si se reprograma a una fecha pasada → `Finalizada`.
- Estados manuales distintos de abierta/pendiente/pospuesta no se cierran por la conciliación automática.
- Cumpleaños: la aparición del año actual pasa a completada cuando su fecha ya ha pasado y el ciclo siguiente vuelve a pendiente.

## Privacidad y seguridad

- WebView local con cargas de red bloqueadas.
- `android:usesCleartextTraffic="false"`.
- Documentos y grabaciones guardados en almacenamiento privado de la app.
- Providers internos no exportados.
- La transcripción se realiza localmente; el audio no se envía a un servicio de transcripción.
- Internet se utiliza para descargar el runtime/modelo cuando el usuario lo solicita.
- La clave de firma **no debe subirse al repositorio**.

## Validación

Ejecutar:

```sh
sh validation/validate-project.sh
```

Resultado de la reconstrucción 3.4.9:

- Regresión principal: **410/410**.
- Blind: **126/128 (98,44 %)**.
- Los dos fallos blind conocidos son históricos y no están relacionados con 3.4.x.
- Política/mensajes de medicación: OK.
- Integración estática de transcripción: OK.
- Estados temporales: OK.
- XML y puente JS↔Java: OK.

La validación local no sustituye una compilación e instalación real en AndroidIDE, especialmente para JNI/sherpa-onnx.

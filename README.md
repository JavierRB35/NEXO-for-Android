# Nexo 3.7.7

Nexo es una aplicación Android **local-first** para organizar agenda, tareas, recordatorios, medicación, notas, documentos, listas, escritos, grabaciones, lugares y capturas de Inbox desde una única interfaz.

Esta rama de estabilización corresponde a **Nexo 3.7.7**, `versionCode 100`, `applicationId com.jruiz.nexo` y base de datos local `nexo.db` esquema **25**.

## Base estable

Nexo 3.7.7 mantiene la base funcional de 3.7.6 y corrige la edición de series recurrentes para que cambiar participantes u otros metadatos no pueda alterar la duración de la serie ni generar actualizaciones duplicadas.

Entre los comportamientos ya integrados:

- Inicio ligero con **Hoy**, **7 días**, **Inbox**, **Agenda**, accesos directos y **Próximamente**.
- Splash de arranque con identidad Nexo, animación CSS de tres puntos, mínimo de 3,5 s y salida de seguridad a los 5 s.
- Durante el splash: escaneo resiliente de Inbox y precarga del resumen de Inicio en paralelo.
- Agenda y Calendario con edición de eventos, recurrencias, participantes, categorías, ubicaciones y festivos.
- Voz contextual según pantalla y captura universal **Habla con Nexo**.
- Notas con guardado explícito, aviso al salir con cambios y dictado contextual para continuar una nota abierta.
- Listas, Documentos, Cuaderno, Frases, Medicación, Grabaciones, Lugares y Aparcamiento.
- Input Hub para Calendar, SMS y Gmail, además de captura compatible mediante notificaciones.
- Integración con Nexo Familia mediante Supabase Auth y sincronización/importación controlada.
- Grabación local y transcripción 100 % local mediante sherpa-onnx 1.13.7, con perfiles Whisper Base/Small/Medium multilingües INT8 descargados bajo demanda según los recursos del dispositivo.

## Entorno Android validado

- AndroidIDE-Rv2 / ARM64
- OpenJDK 17
- Android SDK 34
- Build Tools 34.0.4
- Gradle 8.9
- AGP 8.7.3
- `minSdk 26`, `targetSdk 34`, `compileSdk 34`

No actualizar estas versiones salvo necesidad justificada.

## Flujo oficial de compilación

El flujo normal es **GitHub Actions**:

- cada push a una rama que contenga el workflow oficial ejecuta validaciones y genera una APK Release firmada como artefacto;
- una ejecución manual desde Actions hace lo mismo;
- un tag `vX.Y.Z` que coincida con `versionName` puede crear la GitHub Release correspondiente;
- la firma Release permanente se mantiene fuera del repositorio mediante GitHub Secrets.

El procedimiento se documenta en [docs/RELEASE_PROCESS.md](docs/RELEASE_PROCESS.md).

## AndroidIDE como fallback

Desde la raíz del proyecto en el teléfono:

```sh
cd "/storage/emulated/0/Download/Nexo_3_7_7"
sh build-on-phone.sh
```

No ejecutar `./gradlew` directamente desde `Download`.

El script conserva la firma Release permanente, usa el baseline de AndroidIDE y copia el APK final a:

```text
/storage/emulated/0/Download/Nexo_3_7_7.apk
```

## Validación

Validación base del proyecto:

```sh
sh validation/validate-project.sh
```

El workflow oficial añade además las regresiones de runtime WebView, rendimiento de Inicio, splash, navegación contextual y fichas de Notas antes de compilar la Release.

## Privacidad y seguridad

- WebView local con tráfico cleartext deshabilitado.
- Grabaciones, documentos y transcripciones se gestionan localmente.
- La transcripción se realiza de forma local.
- Las fuentes de Inbox requieren permisos/autorizaciones explícitas.
- La clave privada de firma nunca se almacena en el repositorio.

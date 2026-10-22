# Nexo · actualizaciones integradas

El cliente a partir de Nexo 3.7.46 tiene un actualizador nativo; el repositorio de código fuente permanece privado. El canal público debe configurarse **antes** de ponerlo en producción.

## Activación inicial, una sola vez

1. Crear un repositorio **público** GitHub con el nombre exacto JavierRB35/nexo-android-updates y rama principal main, inicializando un README. **No publicar nunca el código fuente, secretos ni la clave de firma.**
2. Crear un fine-grained personal access token limitado a ese repositorio público, con permiso Contents: Read and write y ningún otro repositorio.
3. Añadir el token al repositorio privado JavierRB35/NEXO-for-Android en Settings → Secrets and variables → Actions, con el nombre NEXO_UPDATE_PUBLISH_TOKEN. Nunca escribirlo en el APK ni en los fuentes.
4. Integrar la rama del actualizador en main tras pasar CI y publicar Nexo v3.7.46 en el flujo habitual, con la **firma Release permanente**.
5. El job publish-public-update invoca el workflow de distribución tras acabar el build y la Release privados: verifica certificado oficial, calcula SHA-256 y tamaño real, publica la APK Release firmada en el repositorio público y por último actualiza latest.json.
6. Comprobar sin iniciar sesión la URL: https://raw.githubusercontent.com/JavierRB35/nexo-android-updates/main/latest.json
7. Para probar un aviso real, publicar una segunda versión firmada superior (por ejemplo 3.7.47, versionCode 140) y comprobar desde Ajustes → Actualizaciones → Buscar actualizaciones.

Para una publicación aislada, ejecutar manualmente Actions → Publish Nexo public update channel e indicar el tag oficial existente.

## Seguridad y funcionamiento

- Manifiesto JSON (máximo 32 KiB) servido por HTTPS. El archivo incluye schemaVersion=1, packageName=com.jruiz.nexo, versionName, versionCode, apkUrl, apkSha256, apkSize y notes.
- Solo acepta APK de la distribución pública HTTPS fijada en el código. Descarga en caché interna de Nexo (máximo 250 MiB).
- Valida versión superior, paquete, tamaño, SHA-256 de los bytes, versión de la APK y firma del certificado oficial del proyecto.
- Rechaza si la instalación actual tiene una firma diferente; no desinstala ni cambia datos. IMPORTANTE: una APK local creada mediante setup-release-key.sh puede usar una clave distinta de la clave de CI.
- Abre el instalador estándar de Android mediante un ContentProvider privado de solo lectura y exige las confirmaciones/permisos del sistema.
- Consulta al abrir (máximo una vez al día tras chequeo correcto) y crea un JobScheduler diario sujeto a las restricciones de Android. Solo muestra notificaciones de segundo plano si el usuario ha concedido el permiso correspondiente.
- El segundo plano nunca descarga ni instala APK; únicamente avisa de una versión superior.
- GitHub Actions se niega a rebajar versionCode o sustituir una publicación previa por una APK de hash distinto.
- Los datos principales siguen en la base de datos local. Para cada actualización, validar compatibilidad de migraciones de BD y hacer una copia de seguridad prudente antes de grandes cambios.
- El mecanismo no necesita actualizaciones de AndroidIDE ni del baseline JDK 17 + SDK 34 + AGP 8.7.3 + Gradle 8.9.

## Ejemplo de valores del manifiesto

schemaVersion: 1
packageName: com.jruiz.nexo
versionName: 3.7.46
versionCode: 139
apkUrl: https://github.com/JavierRB35/nexo-android-updates/releases/download/v3.7.46/Nexo-v3.7.46-release.apk
apkSha256: huella hexadecimal real de 64 caracteres
apkSize: número real de bytes
notes: resumen breve de la versión

Estos valores los calcula el workflow a partir de la APK oficial, no son metadatos manuales.


## Actualizaciones voluntarias y críticas (a partir de Nexo 3.7.51)

La política de publicación es una decisión explícita para **cada versión**. En el repositorio privado `JavierRB35/NEXO-for-Android`, editar `.github/nexo-update-policy.json` **antes** de compilar y crear la Release firmada:

- **Opcional**: `{"critical": false, "reason": ""}`. El aviso dice «Actualización opcional» y ofrece «Actualizar» y «Más tarde».
- **Crítica/obligatoria**: `{"critical": true, "reason": "Motivo concreto de seguridad o compatibilidad (10 a 300 caracteres)."}`. El mensaje explica el motivo y muestra «Actualizar ahora» o «Salir de Nexo». No aparece «Más tarde». Android conserva las confirmaciones de instalación; nunca hay instalación automática sin permiso.

El flujo `publish-public-updates.yml` obtiene esos valores de la revisión **inmutable de la Release**, verifica APK, firma y hash, y publica `critical` (booleano) y `criticalReason` (texto) en `latest.json`. No se permite cambiar la criticidad de una versión publicada sin incrementar su `versionCode`. Si faltan campos en un manifiesto antiguo, se considera opcional. Los metadatos inválidos se rechazan.

**Importante:** la obligatoriedad se hace cumplir por el cliente Nexo 3.7.51 o superior cuando detecta el aviso crítico. Las instalaciones anteriores no conocen esta política y podrán seguir mostrando el aviso voluntario. Si la conexión no permite consultar el canal, no se impone un bloqueo remoto; esto evita dejar sin funciones locales a un usuario por una caída de red. En ningún caso se debe modificar `latest.json` manualmente para forzar una actualización ni publicar una APK sin comprobar firma y hash.

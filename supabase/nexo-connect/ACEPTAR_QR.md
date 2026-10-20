# Nexo Connect — aceptación bilateral con comprobación QR

La invitación A→B lleva un secreto aleatorio (32 bytes mínimos) y **las dos
huellas SHA-256 de claves públicas de A junto a su generación**. B debe
escanear o comprobar ese QR mediante un canal fiable; la mera lectura de
un enlace remoto no garantiza autenticidad frente a un enlace sustituido.

Una vez que B decide aceptar, su aplicación firma `contact/accept` con la
clave del dispositivo registrado, usando desafío de un solo uso. La Edge
Function debe comparar las huellas del QR con las claves vigentes de A.
`nexo_connect_accept_invite` bloquea la invitación y ambas filas de
dispositivo durante la transacción. Sólo tras comprobar su identidad
vincula el contacto y fija las claves de A para B.

**Importante:** el enlace no proporciona por sí solo confianza mutua en
ambas direcciones. A no debe poder enviar contenido a B hasta verificar
las claves de B mediante otro QR o autenticación fuera de banda.
La tabla `peer_key_approvals` refleja esa decisión, no la demuestra.

No activo aún aceptación real en Internet antes de que la UI Android
implemente el QR fiable y las pruebas end-to-end entre dos móviles
automáticos. El contrato SQL está preparado para una futura función Edge.

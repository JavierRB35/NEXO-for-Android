# Validación · Nexo 3.4.9 reconstruida

Ejecutado sobre el fuente reconstruido:

```sh
sh validation/validate-project.sh
```

## Resultado

- Parser/regresión principal: **410/410 = 100 %**.
- Blind: **126/128 = 98,44 %**.
- Localización: 18 pruebas específicas OK.
- Citas/servicio: 11 escenarios OK.
- Lugares: 13 escenarios OK.
- Documentos: 16 escenarios OK.
- NoteTitleEngine: 60/60.
- Grabadora: integración, permisos, privacidad y puente OK.
- `.nexoshare`: 10 comprobaciones OK.
- Listas: 21 comprobaciones OK.
- Swipe calendario: 5 pruebas runtime OK.
- Medicación 3.4.7/3.4.8: 14 comprobaciones de política e interfaz OK.
- Transcripción 3.4.x: 14 comprobaciones estáticas de integración OK.
- Estados temporales 3.4.9: 10 comprobaciones OK.
- XML y puente JS↔Java: OK.
- Configuración Release, `applicationId`, versión y esquema BD: OK.

## Dos fallos blind históricos

1. `Pienso en la comida de mañana` puede interpretar `La comida de` como ubicación.
2. `Renovar la póliza del coche` puede clasificar `Otros` en lugar de `Coche`.

No están relacionados con los cambios 3.4.x.

## Limitación de esta validación

Este entorno no dispone del SDK/AndroidIDE del teléfono para ejecutar aquí la compilación Release completa con el AAR binario y la firma real. La prueba definitiva de JNI/sherpa-onnx es:

```sh
sh build-on-phone.sh
```

en el entorno AndroidIDE-Rv2 validado del dispositivo.

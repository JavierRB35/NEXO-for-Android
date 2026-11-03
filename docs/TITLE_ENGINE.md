# Títulos de citas · integración V8.1 (3.7.82)

`AppointmentParser` aplica `core.title.AppointmentTitleEngine` después del generador
original. Las reglas V2–V7 se conservan como clases internas de paquete para reproducir
el candidato validado, sin reescribir sus decisiones durante la integración. V8.1
protege acciones principales explícitas y negaciones. No hay servicios externos,
dependencias nuevas ni cambios en notas, fechas, avisos, recurrencia o almacenamiento.

Los títulos se proponen al capturar una cita; no hay migración ni reescritura de títulos
guardados. Las correcciones manuales siguen prevaleciendo en NexoService. Una instrucción
larga se conserva completa y añade una advertencia de revisión. La ausencia de fecha no
puede borrar esta advertencia. Las alertas temporales preexistentes se conservan.

## Evidencia

- `sh validation/test-appointment-titles.sh`: 568 títulos congelados del laboratorio
  (520 históricos y 48 diagnósticos) y comparación de todos los campos no relativos
  al título contra el contrato anterior; siete casos límite y entradas vacías.
- `sh validation/test-appointments.sh`: servicio real con dobles de BD/Android,
  incluido título manual y revisión de negación larga sin fecha.
- El laboratorio encontró 46/48 casos semánticos satisfactorios frente a 37/48 de V7.
  Son casos dirigidos conocidos durante V8.1, no una estimación de generalización.
- Persisten pérdidas de contexto en títulos con «Mañana» como nombre de libro y
  «por la noche» como asunto. No se afirman correcciones de interpretación horaria.

## Calendario móvil incluido en la entrega

La regla de 48 px mínimos por columna en design-system.css desbordaba el contenedor
que recorta el movimiento del mes. Se sustituyó por siete fracciones con mínimo cero,
coherentes con las cabeceras. Los contadores pueden pasar a otra línea con letra grande.

`node validation/test-calendar-mobile-layout.cjs` usa una instancia aislada Chromium
con CDP en localhost:9333 (configurable con NEXO_CDP_URL), Node con WebSocket y las
cinco hojas CSS de producción. Reproduce el defecto anterior y comprueba 45 combinaciones
(320/360/393/412/600 px, letra 16/20/24 px, tres temas), 42 celdas, alineación de cabeceras
y ausencia de recortes internos. NEXO_LAYOUT_OUTPUT permite guardar medidas y captura.
Es una prueba de navegador con contenido sintético, no una instalación en el móvil.

# Nexo UI/UX Design System · 3.6

## Objetivo

Nexo debe transmitir **limpieza + organización + sencillez + rapidez + modernidad** sin modificar la lógica funcional existente.

La aplicación mantiene su arquitectura Android actual y su interfaz HTML/CSS/JS dentro de WebView. El sistema visual se implementa con tokens CSS reutilizables y recursos Android nativos para conservar ligereza, estabilidad y compatibilidad con AndroidIDE.

## Inventario de superficies

Pantallas base:

1. Inicio
2. Crear / editar cita de Agenda
3. Buscar
4. Calendario
5. Ajustes
6. Contenedor de paneles

Paneles funcionales:

- Agenda
- Listas
- Inbox
- Historial
- Copia de seguridad
- Medicación
- Cuaderno
- Frases
- Notas
- Grabaciones
- Lugares
- Documentos
- Aparcamiento

Superficies transversales:

- diálogos
- confirmaciones
- toast/snackbar
- dictado por voz
- filtros y chips
- formularios
- estados vacíos
- tarjetas y filas
- navegación inferior
- cabecera
- estados de error, aviso y éxito

## Principios

- Blanco como superficie dominante.
- Fondo neutro muy claro para separar superficies sin llenar la pantalla de tarjetas.
- Verde como color primario para acciones y estados activos.
- Amarillo para atención moderada, referencias y contenido destacado.
- Rojo solo para error, destrucción o alertas reales.
- Colores de categorías de calendario se conservan por utilidad funcional y diferenciación.
- Una sola acción primaria visualmente dominante por contexto.
- Acciones secundarias outlined o text.
- Controles táctiles de al menos 48 px equivalentes en la WebView.
- Menor número de sombras; jerarquía mediante espaciado, borde y tipografía.
- Radios coherentes: 12 / 14 / 16 / 22 px.
- Tipografía del sistema Android.
- Animaciones discretas y rápidas; respetar prefers-reduced-motion.

## Tokens principales

### Color

- Primary: #176B50
- Primary strong: #10543E
- Primary soft: #E9F5EF
- Yellow: #D79A00
- Yellow soft: #FFF4C7
- Danger: #B42318
- Danger soft: #FDECEA
- Background: #F7F8F6
- Surface: #FFFFFF
- Surface muted: #F0F3EF
- Text: #1C211E
- Muted text: #68736C
- Outline: #DDE3DD

### Espaciado

4 / 8 / 12 / 16 / 20 / 24 / 32 px.

### Formas

- pequeños controles: 12 px
- botones: 14 px
- tarjetas: 16 px
- bottom sheets: 22–24 px

## Jerarquía tipográfica

- Pantalla: 1.55 rem, semibold/bold.
- Sección: 1 rem, bold.
- Elemento principal: ~0.96–1 rem, semibold.
- Cuerpo: 0.84–0.94 rem.
- Metadato / label: 0.72–0.80 rem.

No se usan tamaños decorativos grandes en superficies de uso frecuente.

## Navegación

La navegación inferior se mantiene como navegación primaria:

- Inicio
- Calendario
- Buscar

Ajustes permanece como acción global en cabecera.

Los módulos de Inicio son accesos funcionales de segundo nivel. Agenda y Frases son conceptos distintos y visibles de forma independiente.

## Componentes

### Botón primario

Verde sólido, texto blanco, altura mínima 48 px.

### Botón secundario

Blanco, borde neutro, texto verde.

### Destructivo

Rojo exclusivamente para eliminar, error o acciones irreversibles.

### Chips

Superficie neutra; seleccionado usa contenedor verde suave. Amarillo se reserva para atención o referencia, no para selección general.

### Cards

Borde fino y fondo blanco. Sin sombra por defecto. Las tarjetas con estado pueden usar una pequeña línea lateral de color.

### Inputs

Fondo casi blanco, borde neutro, foco verde con halo suave. Etiqueta siempre visible.

### Diálogos

Bottom sheet con radio superior 24 px, tirador visual y acciones claramente jerarquizadas.

### Feedback breve

Toast oscuro neutro sobre contenido, con acción amarilla si existe.

## Criterios por superficie

### Inicio

- Acción principal visible sin dominar toda la pantalla.
- Módulos compactos en dos columnas.
- Iconos con contenedor suave.
- Accesos secundarios como chips.
- Frase del día usa acento amarillo discreto.

### Agenda

- Filas compactas con título como información principal.
- Fecha/estado/categoría como metadatos.
- Acciones secundarias en menú o botones compactos.

### Calendario

- Mantiene colores de categoría diferenciados.
- Selección y estado activo de navegación usan verde Nexo.
- Evitar rellenos de color grandes.

### Formularios

- Separación por grupos.
- Más opciones plegadas cuando no son esenciales.
- Validación clara sin depender solo de color.

### Cuaderno / Frases

- Cuaderno = pensamientos y reflexiones.
- Frases = citas textuales y referencias.
- Misma familia visual, navegación separada.

### Medicación

- Verde para tomado/correcto.
- Amarillo para aviso.
- Rojo solo para error o eliminación.

### Grabaciones

- Estado activo claro, pero sin grandes superficies rojas.
- Rojo limitado al indicador de grabación y acciones destructivas.

### Ajustes

- Agrupación en secciones.
- Filas con divisores en lugar de tarjetas independientes para cada opción.
- Estados de conexión con color + texto.

## Accesibilidad

- Objetivo táctil mínimo aproximado: 48 px.
- Contraste mínimo objetivo: 4.5:1 en texto pequeño.
- No depender exclusivamente del color.
- Mantener aria-label existentes y texto junto a iconos ambiguos.
- Respetar tamaño de visualización configurable.
- Respetar prefers-reduced-motion.

## Compatibilidad

No cambia:

- JDK 17
- SDK 34
- Build Tools 34.0.4
- Gradle 8.9
- AGP 8.7.3
- permisos
- servicios
- alarmas
- notificaciones
- almacenamiento
- integraciones
- lógica de negocio
- flujo de compilación AndroidIDE

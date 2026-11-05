# Nexo UI/UX Design System · 4.0

## Objetivo

Nexo debe transmitir **limpieza + organización + sencillez + rapidez + modernidad** sin modificar la lógica funcional existente.

La aplicación mantiene su arquitectura Android actual y su interfaz HTML/CSS/JS dentro de WebView. El sistema visual se implementa con **tokens semánticos únicos** y recursos Android nativos para conservar ligereza, estabilidad y compatibilidad con AndroidIDE.

## Arquitectura de capas CSS

El orden de carga es parte del contrato (`index.html`):

1. `nexo-themes.js` — aplica el tema antes del primer pintado (sin destello).
2. `styles.css` — base heredada: geometría y componentes (se conserva como está).
3. `design-system.css` — **capa compartida**: tokens `--nx-*`, escala tipográfica y repaso premium de todos los componentes. El color proviene siempre de tokens.
4. `time-grid.css`, `vehicles.css` — módulos con geometría propia.
5. `nexo-themes.css` — **capa de temas**: las tres identidades (tokens literales), la agenda horaria y los vehículos por tema, y la cobertura de superficies heredadas.

Reglas de convivencia:

- Los tokens viven en `design-system.css` (respaldo neutro) y cada tema los redefinen en `nexo-themes.css` con **valores literales** (requisito de la auditoría WCAG de `test-visual-themes.cjs`).
- Nada de `touch-action:` en la capa de temas; nunca tocar `.tgv-event--dragging`.
- Los colores semánticos de las 21 categorías del calendario **no** se redefinen por tema.

## Los tres temas

| Tema | Personalidad | Primario | Fondo |
|---|---|---|---|
| **Vivo** (predeterminado) | Fresco y sereno | `#2563EB` | `#F4F7FD` |
| **Mediterráneo** | Cálido y luminoso | `#12707A` | `#FAF6ED` |
| **Noche** | Oscuro y reposado | `#8FB4FF` (texto `#0E1930` sobre acento) | `#121C2E` |

Cada identidad define el mismo conjunto de tokens: superficies (`--nx-bg/surface/surface-2/elevated/header/grid`), texto (`--nx-text/-2/-3`), líneas (`--nx-line/-strong`), acento (`--nx-primary/-press/-soft/-on-primary`), semánticos (`--nx-danger/success/warn` + `soft`), acentos secundarios de iconos (`--nx-accent-two..five`) y tonos suaves de tarjetas (`--nx-card-*`).

La barra de estado y de navegación del sistema se alinean con el tema activo mediante `NexoNative.applyChromeTheme` (invocado desde `app.js` al arrancar y en el evento `nexothemechange`; `nexo-themes.js` nunca accede al puente).

## Principios

- Superficie dominante clara (o oscura coherente en Noche); jerarquía mediante espacio, tipografía y una línea, no mediante cajas dentro de cajas.
- **Un solo acento por contexto**: el color primario se reserva para interacción (activo, primario, foco) y cifras protagonistas.
- Rojo solo para error, destrucción o alertas reales; verde para éxito/salud; ámbar para avisos.
- Las categorías se identifican con icono + etiqueta, nunca solo con color.
- Una acción primaria por vista; secundarias en superficie suave; destructivas diferenciadas.
- Objetivos táctiles de al menos 44-48 px.
- Sombra única y discreta (`--nx-shadow`); las capas flotantes (FAB, toast, hojas) usan `--nx-shadow-float`.
- Radios coherentes: 12 (controles) / 16 (tarjetas) / 20 (superficies grandes) / 24 (hojas).
- Animaciones de 120-170 ms; `prefers-reduced-motion` siempre respetado.

## Jerarquía tipográfica

Fuente: Roboto / sistema. Pesos: 400 · 500 · 600 · 700 (nada por encima).

- Pantalla (h1): 1,5 rem · 700 · tracking −0,02 em.
- Sección (h2): 1,0625 rem · 700 · color de texto.
- Microtítulos de sección (kick): 0,75 rem · 700 · mayúsculas · `--nx-text-3`.
- Cuerpo: 0,875-0,9375 rem · 1,5 de interlineado.
- Metadato/etiqueta: 0,75-0,8125 rem.

## Componentes

- **Cabecera**: marca + fecha a la izquierda, clima y acciones a la derecha; fondo de superficie, sin líneas decorativas.
- **Navegación inferior**: seis destinos (Inicio, Calendario, Notas, Chat, Inbox, Herramientas) con píldora suave sobre el icono activo (Material 3) y etiqueta en español.
- **Botones**: primario relleno con estado pulsado; secundario en superficie suave; ghost; danger (contorno y sólido). Radio 12, altura mínima 48 px.
- **Campos**: fondo blanco, borde fino, foco con anillo `--nx-primary-soft`; etiqueta siempre visible; errores junto al campo.
- **Tarjetas**: superficie + borde fino, sin sombra; las tarjetas de eventos llevan una barra lateral de 3 px con el color de categoría (`--event-category-color`).
- **Tarjetas de pulso de Inicio**: icono con contenedor suave rotatorio, cifra protagonista (1,5 rem), etiqueta y pista contextuales.
- **Chips y píldoras**: superficie neutra; selección con contenedor suave del acento.
- **Hojas modales**: fondo elevado, radio superior 24, tirador, título 1,19 rem; entrada animada de 155 ms (silenciable).
- **Toast**: elevado sobre el contenido, acción en acento; nunca sustituye a la confirmación de una operación.
- **Estados vacíos**: icono en círculo suave, título y una línea de ayuda; jamás transmiten error.
- **FAB**: 56 px de radio 18 para la acción principal contextual; micrófono secundario de 48 px.
- **Calendario**: rejilla sin bordes internos; hoy con círculo del acento; seleccionado con contenedor y anillo; fuera de mes atenuado.
- **Agenda horaria**: banda de horas en `--nx-grid`, citas con mezcla del acento de categoría sobre superficie; geometría y gestos intactos.

## Accesibilidad

- Contraste AA verificado por `validation/test-visual-themes.cjs` (texto, texto secundario, acento sobre su fondo).
- Foco visible consistente (`outline` 3 px del acento).
- Estados nunca solo por color (texto + icono + forma).
- `prefers-reduced-motion` respetado en toda la capa de temas.
- `textZoom` del sistema respetado (90-170 %) y modo de visualización cómodo.

## Compatibilidad

No cambia: JDK 17, SDK 34, Build Tools 34.0.4, Gradle 8.9, AGP 8.7.3, permisos, servicios, alarmas, notificaciones, almacenamiento, integraciones, lógica de negocio ni flujo de compilación AndroidIDE/GitHub Actions.

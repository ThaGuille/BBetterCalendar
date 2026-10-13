# Diseños de Stitch — índice

Proyecto Stitch: «BBetter — Calm Focus redesign» (`projects/9271857544033674332`).
Plan que los consume: [`migracion-visual-stitch.md`](../../.claude/plans/migracion-visual-stitch.md).
Dirección artística y tokens: [`../DESIGN.md`](../DESIGN.md). Fuentes: `app/src/main/res/font/` (licencia en [`../assets/fonts/OFL.txt`](../assets/fonts/OFL.txt)).

**Reglas de uso:** el HTML manda para hex, dp y pesos; el PNG manda para la composición.
Los PNG están a 390 px de ancho (1x); Stitch guarda las capturas a 780 px (2x).

## Canónicos

| Pantalla | PNG | HTML | Screen id (Stitch) | Unidad |
|---|---|---|---|---|
| Home | `04-home.png` | `../html/04-home.html` | `11ee09a38fee48f69f911b6ddd3ca08e` | u13 |
| Focus (en curso) · Sanctuary | `01b-focus-running-sanctuary.png` | `../html/01b-focus-running-sanctuary.html` | `4da7df95d6cd4a9c98e42fa829dc696e` | pendiente |
| Focus (descanso) · Sanctuary | `01c-focus-rest-sanctuary.png` | `../html/01c-focus-rest-sanctuary.html` | `0c14430753f64deba2e4da98d8c623a8` | pendiente |
| Focus (pausa) · Sanctuary | `01d-focus-paused-sanctuary.png` | `../html/01d-focus-paused-sanctuary.html` | `9baeae523d0f40d6884f50cf3a9f0548` | pendiente |
| Cycle complete | `02-celebration.png` | `../html/02-celebration.html` | `a4cb1e522e27412395f761c4272158c3` | u15 |
| Projects | `03-projects.png` | `../html/03-projects.html` | `6fb09811f34d438eb3ddc2110d3351d1` | u16 |
| Progress | `13-progress-final.png` | `../html/13-progress-final.html` | `ae55b48e84e946c4a8254e7dfacbfcc0` | u17 |
| Screen time & blocking | `14-screen-time-blocking-final.png` | `../html/14-screen-time-blocking-final.html` | `af611393071b4fbe99866ad75c9dc31e` | u19 |
| Detalle por app | `10-app-detail-instagram.png` | `../html/10-app-detail-instagram.html` | `c37803bb60284e8a9d6bf60fb8882999` | u20 |

## Superados (no implementar)

| PNG | Qué era |
|---|---|
| `01-focus-running.png` | Focus con la paleta vieja de `DESIGN.md`; sustituido por `01b`–`01d` (sistema «Calm Focus Sanctuary», 2026-10-14). Screen `21b74101…` |
| — | Primer borrador Sanctuary sin refinar (solo en Stitch, screen `4622957a…`) |
| `05-progress.png` | Primera versión de Progress |
| `06-progress-alt-a-screen-time.png` | Alternativa A (con screen time) |
| `07-progress-alt-b-desktop.png` | Alternativa B (formato escritorio) |
| `08-progress-v2-study.png` | Progress v2 |
| `09-screen-time.png` | Primera versión de Screen time |
| `11-progress-v3.png` | Progress v3; sustituido por `13` |
| `12-screen-time-blocking-v2.png` | Screen time v2; sustituido por `14` |

## Notas

- **Focus y Cycle complete usan el sistema «Calm Focus Sanctuary»** (`assets/f05b8e6776b14744ba52356033e93ad4`):
  primario ámbar `#E5A93C`/`#F5BE58`, fondo `#091610`, tarjetas `#13221C` con borde fino y radio 32px, resplandor
  radial ámbar. El descanso pasa a salvia (el ámbar queda para concentración y logros); la pausa atenúa el anillo al 45 %.
  El resto de pantallas usa los `namedColors` de «BBetter Calm Focus» (decisión del 2026-10-14).

- El mapeo de `10`, `13` y `14` a su screen id se hizo por altura: `10` = 1113 px, `13` = 1293 px,
  `14` = 1278 px. `11` y `13` miden lo mismo (1293 px); `13` se enlazó con «Progress - Weekly Overview
  (Reordered)», que es la visible en el lienzo. Si Home o Progress no cuadran con el PNG, reexportar con
  `get_screen` sobre el screen id de esta tabla.
- `get_screen` devuelve URLs de descarga firmadas (`htmlCode.downloadUrl`, `screenshot.downloadUrl`);
  para la captura, añadir `=w390` a la URL.

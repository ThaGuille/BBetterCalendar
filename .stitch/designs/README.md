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
| Focus (en curso) | `01-focus-running.png` | `../html/01-focus-running.html` | `21b74101e3704f37887a7b8218af7293` | u14 |
| Cycle complete | `02-celebration.png` | `../html/02-celebration.html` | `a4cb1e522e27412395f761c4272158c3` | u15 |
| Projects | `03-projects.png` | `../html/03-projects.html` | `6fb09811f34d438eb3ddc2110d3351d1` | u16 |
| Progress | `13-progress-final.png` | `../html/13-progress-final.html` | `ae55b48e84e946c4a8254e7dfacbfcc0` | u17 |
| Screen time & blocking | `14-screen-time-blocking-final.png` | `../html/14-screen-time-blocking-final.html` | `af611393071b4fbe99866ad75c9dc31e` | u19 |
| Detalle por app | `10-app-detail-instagram.png` | `../html/10-app-detail-instagram.html` | `c37803bb60284e8a9d6bf60fb8882999` | u20 |

## Superados (no implementar)

| PNG | Qué era |
|---|---|
| `05-progress.png` | Primera versión de Progress |
| `06-progress-alt-a-screen-time.png` | Alternativa A (con screen time) |
| `07-progress-alt-b-desktop.png` | Alternativa B (formato escritorio) |
| `08-progress-v2-study.png` | Progress v2 |
| `09-screen-time.png` | Primera versión de Screen time |
| `11-progress-v3.png` | Progress v3; sustituido por `13` |
| `12-screen-time-blocking-v2.png` | Screen time v2; sustituido por `14` |

## Notas

- El mapeo de `10`, `13` y `14` a su screen id se hizo por altura: `10` = 1113 px, `13` = 1293 px,
  `14` = 1278 px. `11` y `13` miden lo mismo (1293 px); `13` se enlazó con «Progress - Weekly Overview
  (Reordered)», que es la visible en el lienzo. Si Home o Progress no cuadran con el PNG, reexportar con
  `get_screen` sobre el screen id de esta tabla.
- `get_screen` devuelve URLs de descarga firmadas (`htmlCode.downloadUrl`, `screenshot.downloadUrl`);
  para la captura, añadir `=w390` a la URL.

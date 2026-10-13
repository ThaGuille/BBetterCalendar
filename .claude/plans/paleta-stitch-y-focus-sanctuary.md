# Paleta de Stitch + Focus «Sanctuary»

Status: implemented (uncommitted, verified on emulator 2026-10-14)
Created: 2026-10-14
Last updated: 2026-10-14

Decisión del usuario (2026-10-14, ver `migracion-visual-stitch-questions.md`): la app usa los
`namedColors` que Stitch pinta de verdad (sistema «BBetter Calm Focus»), y Focus + Cycle complete usan
el sistema «Calm Focus Sanctuary». Diseños de Focus: `.stitch/designs/01b` (en curso), `01c`
(descanso), `01d` (pausa).

## 1. Paleta global (solo `palette.xml` + `colors.xml` + usos de texto)

Medido en los PNG/HTML canónicos. Los rellenos salvia (play, FAB, checks, barras, segmento
seleccionado) ya eran `#7FB69A` (`primary-container`) y no cambian.

| Primitivo | Antes | Ahora | Rol Stitch |
|---|---|---|---|
| `pine_950` | `#12201C` | `#081612` | background / surface |
| `pine_925` (nuevo) | — | `#101E1A` | surface-container-low (barra inferior, stepper) |
| `pine_900` | `#1B2D27` | `#14221E` | surface-container (tarjetas) |
| `pine_800` | `#24392F` | `#1F2D28` | surface-container-high |
| `pine_750` (nuevo) | — | `#293833` | surface-container-highest |
| `pine_700` | `#2E453B` | igual | gridlines (el HTML lo usa tal cual) |
| `forest_600` | `#2F4F4A` | `#2C4C47` | secondary-container (píldora nav, chips) |
| `forest_900` (nuevo) | — | `#0E4832` | on-primary-container (icono/texto sobre salvia) |
| `sage_200` | `#9BD3B5` | igual | primary: **texto e iconos de acento** |
| `honey_300` | `#F2B544` | `#FABC4A` | tertiary |
| `mist_50` | `#EAF2EC` | `#D6E6DF` | on-surface |
| `lichen_300` | `#9DB3A8` | `#C0C9C2` | on-surface-variant |
| `grey_400` (nuevo) | — | `#8A938C` | outline (texto terciario) |
| `outline_v_a30` (nuevo) | — | `#4D404943` | divisores (`outline-variant/30`) |

Tokens nuevos: `bb_surface_low`, `bb_surface_highest`, `bb_primary_bright`, `bb_on_surface_faint`.
`textColor`/`tint` = `bb_primary` en layouts y el tinte activo de la barra pasan a `bb_primary_bright`;
los rellenos se quedan en `bb_primary`. `dusk_300` y `ember_300` no cambian (el HTML los usa tal cual).

## 2. Tema Sanctuary para Focus y Cycle complete

`Theme.BBetter.Sanctuary` (overlay sobre la raíz de `fragment_focus.xml` y
`fragment_cycle_complete.xml`) + tokens `bb_sanctuary_*`: fondo `#091610`, tarjeta `#13221C`, borde
`rgba(235,243,238,.06)`, ámbar `#E5A93C`/`#F5BE58`, texto `#EBF3EE`/`#A3B8AD`, resplandor ámbar 12 %.

## 3. Focus a 01b/01c/01d

- Anillo ~70 % del ancho, trazo fino, arco degradado ámbar→salvia (descanso: solo salvia; pausa: 45 %).
- Resplandor radial detrás del anillo (ámbar; salvia en descanso; 1/3 en pausa).
- Botón principal ámbar con resplandor (descanso: fantasma salvia). Stop/Skip fantasma con borde salvia.
- Tarjeta Today/Streak radio 32dp con borde fino; rejilla de amigos sin caja; todo en una pantalla.
- Descanso: línea «Stretch, drink some water. Next: …». Pausa: «Paused · the timer is waiting».

## Verificación

Build + capturas de todas las pantallas con los datos sembrados, comparadas con los PNG (rule #7:
`ui-tester` para crash/flujo).

## Resultado (2026-10-14)

- Paleta: `palette.xml` + `colors.xml` (tokens nuevos `bb_surface_low`, `bb_surface_highest`,
  `bb_primary_bright`, `bb_on_surface_faint`); 53 `textColor`/`tint` de layouts + 8 usos Java pasan a
  `bb_primary_bright`; barra inferior y barra de navegación del sistema en `bb_surface_low`.
- Sanctuary: primitivos `sanct_*`/`amber_*`/`birch_*` y tokens `bb_sanct_*`; `FocusRingView` (anillo con
  degradado y resplandor); `fragment_focus.xml` reescrito; drawables `bg_sanct_*`; Cycle complete
  (`fragment_cycle_complete.xml`, `styles_cycle.xml`, `bg_cycle_*`) pasado a `bb_sanct_*`.
- Verificación: build + `testDebugUnitTest` OK, `token_audit.py` sin regresiones, capturas frente a
  01b/01c/01d/02/03/04/13, `ui-tester` PASS en todos los flujos (sin crash ni ANR).

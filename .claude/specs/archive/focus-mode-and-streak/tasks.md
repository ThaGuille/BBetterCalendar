# Tasks — focus-mode-and-streak

- [x] `FocusEventDAO`: queries de días activos (LiveData 30d, lista de días, contadores de hoy/mes)
- [x] `HomeViewModel`: ventana de 30 días + `activeDaysLast30`, evento `firstFocusOfDay`, retirar `currentStreakText`
- [x] `fragment_home.xml`: fila fecha + tiempo estudiado, quitar stats card, ids de focus mode, botón cancelar
- [x] `HomeFragment`: focus mode (expandir + ocultar chrome), cancelar sesión, block mode mid-run
- [x] Toolbar: item de racha con `actionLayout`, `ToolbarHelper.setStreakCount`, `onToolbarStreakClick`
- [x] `FocusStreakPopup` + `cell_streak_day.xml` + drawables de marca
- [x] `FirstFocusCelebrationPopup` + animación de recompensa + strings
- [x] Verify: `/check` (assembleDebug + lint) y verificación en emulador vía `ui-tester`

## Hallazgos de `code-reviewer` (resueltos en este mismo apply)

- [x] **Alta** — `HomeFragment`: la guarda `!isResumed()` del observador de `firstFocusOfDay` no se
  recuperaba nunca. Un observador de LiveData se activa en STARTED y el valor retenido se entrega
  ANTES de `onResume`, y LiveData sólo reentrega al pasar de inactivo a activo: el premio se
  descartaba de forma permanente (rotación con evento pendiente, diálogo del sistema encima).
  Ahora la guarda es `isStateSaved()` + red de seguridad en `onResume()`.
- [x] **Media** — comentario obsoleto en `handleBlockModeToggle()` que seguía afirmando que el
  botón está deshabilitado mid-run.
- [x] **Baja** — `FocusStreakPopup.POPUP_TAG` en vez de string suelta.
- [x] **Baja** — `home_streak_placeholder` propia en vez de reutilizar `home_stat_fails_placeholder`.
- [x] **Baja** — `DAY_MILLIS` movida junto al resto de campos de `HomeViewModel`.

## Follow-ups (fuera del alcance de este spec)

- [ ] `startOfTodayMillis()` está ahora duplicada en `HomeViewModel`, `FocusStreakPopup`,
  `SplashActivity` e `InitialConfiguration`. Candidata a `FormatHelper`; no se extrae aquí para no
  tocar el arranque en un cambio de UI.

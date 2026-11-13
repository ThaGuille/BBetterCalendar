# Que una tarea grande tarde horas, no días

**Status:** proposed
**Created:** 2026-11-13
**Last updated:** 2026-11-13 (parte BBetterCalendar hecha en la rama bt/wall-time-bbetter)

## Summary

`mejora-visual` tardó 45,5 h de reloj para unas 4 h de trabajo del modelo. La medición (ledger, telemetría, registros
de los 87 agentes, registro de energía de Windows) reparte el tiempo así: 21,7 h con el portátil suspendido o
esperando un chat nuevo, 10,8 h en fallos del sistema de tareas, y dentro de las 19,9 h de agente, 11,2 h esperando
al emulador y 2,8 h en una orden que se cuelga. Este plan ataca cada causa con el arreglo que usa la industria para
ese problema, ordenado por horas recuperadas por hora de trabajo. La mayor parte toca AI-kit (`big-task/`); el
resto, este repo (`bbadb.py`, perfil, scripts de emulador).

Objetivo medible: una tarea del tamaño de `mejora-visual` (21 partes, 16 de pantalla) termina en **6 h de reloj o
menos sin intervención**, con la revisión de rama ejecutada.

## Lo que se probó antes de escribir el plan

| Prueba | Resultado |
|---|---|
| `bt-run.sh` con una ruta `C:\...` sin comillas (como la reciben los agentes) | Reproducido: Git Bash borra las barras, la carpeta no existe, espera todo `BT_RUN_LOCK_WAIT` y responde `busy` (exit 75). Con el valor por defecto son 240 s. |
| Copia de `bt-run.sh` con comprobación «¿es un directorio?» antes del candado | Falla en 0,09 s con un mensaje que dice qué escribir. Con comillas (barras intactas) normaliza y funciona. |
| Coste de cada operación de un recorrido en el emulador (`emulator-5554`, 5 repeticiones) | `uiautomator dump` 2,16 s; captura 0,28 s; `adb shell` 0,07 s; logcat de crashes 0,08 s; tap 0,09 s. Juntar dump y lectura en una llamada no ahorra nada: el coste es el dump. |
| Modelo de tiempo de los 16 recorridos `mv-*` a partir de sus pasos | 2 332 s en total hoy. La mitad (1 159 s) es el «ritmo»: `replay` duerme entre pasos el tiempo que tardó quien lo grabó, entre 0,4 y 4 s. Sin ritmo: 1 173 s. Esperando la condición en vez de un `WAIT` fijo: 954 s. Reusando el volcado entre `ASSERT` seguidos: 891 s (62 % menos). |
| Dos recorridos reales en `emulator-5554`, dos pasadas cada modo, reiniciando la app y sembrando datos antes | `mv-u7-progress`: 113,5 s con el ritmo grabado, 61,4 s sin él. `mv-u12-calendar`: 129,0 s y 61,0 s. Los ocho pasan, y el modelo acierta al segundo (predecía 114→61 y 128→58). Basta con `--min-settle 0 --max-settle 0`, que ya existe. |
| Repetir un recorrido sin reiniciar la app | Falla (13–15 pasos): el recorrido depende de que `installDebug` haya matado la app. `replay` tiene que hacer `am force-stop` antes de `START`; sin eso no se puede saltar la instalación (punto 3.2). |
| Tests de vigilancia de tandas y de `bt-run` en AI-kit (`liveness.test.mjs`, `bt-run.test.mjs`) | 46 de 46 pasan: la falsa alarma de «tanda colgada» está corregida (`41108d8`, `ed51737`). |
| Energía (`powercfg`) | Con cargador: suspensión por inactividad desactivada, pero **cerrar la tapa = suspender**. `SetThreadExecutionState`, que es lo que usa `bt watch`, no puede impedir la suspensión por tapa. |
| Memoria durante la tarea (telemetría de tandas) | Al final de las tandas largas quedaban 743–935 MB libres de 31 GB con **un** emulador. Subir a tres emuladores sin medir antes es arriesgado. |
| Emuladores | Hoy hay dos instancias conectadas y el perfil ya declara `resources.emulator: 2` (plan `emuladores-paralelos`, fusionado). |

Corrección al informe anterior: la tercera comprobación final no empezó de cero. Reutilizó las comprobaciones ya
pasadas sobre el mismo commit y repitió 17. Su hora y media se fue en esas 17 (el recorrido completo en tres
configuraciones) y en esperas activas.

## Plan

Cada punto dice: qué se cambia, por qué esa solución, y cómo se comprueba.

### Fase 0 — Arreglos que no admiten discusión (medio día)

**0.1 Rutas de Windows (2,8 h por tarea; además desbloquea la revisión de rama).** AI-kit.
- `bt.mjs` imprime toda ruta con barras normales: un único `toPosix()` en la salida JSON (`task`, `worktree`, `path`).
- `workflows/big-task.js` normaliza `A.task`, `A.repo`, `A.worktree` al recibir los argumentos (defensa: el chat
  puede seguir pasando barras invertidas).
- `bt-run.sh`: normaliza `\` → `/` en sus dos rutas y sale con código 2 si la carpeta no existe, antes de tocar el
  candado. Probado (tabla de arriba).
- Por qué: es la regla de portabilidad estándar (una sola forma canónica de ruta en la frontera del proceso) más
  «fallar pronto»: un error imposible de resolver no debe esperar un tiempo límite.
- Comprobar: test nuevo en `bt-run.test.mjs` con ruta de barras invertidas; `route.mjs --spec` recibe una ruta
  válida en `e2e-orchestrator.test.mjs`; en telemetría, la etapa «espera de un candado que no llega» queda en 0.

**0.2 La revisión de rama no se puede saltar en silencio.** AI-kit.
- Si el paso que prepara la revisión falla, el cierre no fusiona: para como escalada «revisión no ejecutada» y solo
  continúa con la decisión explícita del usuario (`skip-review` ya existe como acción).
- Por qué: una puerta de calidad que al fallar deja pasar no es una puerta (principio «fail closed»).
- Comprobar: test de política en `orch-next.test.mjs`: `review.stage = failed` ⇒ acción `escalate`, nunca `close`.
- Deuda inmediata: pasar `review-branch` sobre `ae569f1..main` en este repo (22 commits sin revisar).

**0.3 Entorno caído no es un fallo de la tarea (5,3 h).** AI-kit + perfil de este repo.
- El perfil declara, por recurso, cómo comprobarlo y cómo levantarlo:
  `resources: { emulator: { count: 2, probe: "<orden que sale 0 si hay N emuladores>", ready: "avd-up.ps1 -Count 2" } }`.
- `bt` ejecuta `probe` antes de una tanda con partes `@emulator` y antes de la comprobación final; si falla, ejecuta
  `ready` una vez y vuelve a comprobar. Si sigue caído: escalada «entorno» con aviso al móvil. Nunca replanifica.
- Una comprobación que falla con firma de entorno (`no devices/emulators found`, `device offline`) se clasifica como
  fallo externo y se repite tras `ready`, igual que hoy hace el verificador de parte con `FAIL_EXTERNAL`.
- Por qué: es la distinción estándar de CI entre fallo de infraestructura (se reintenta) y fallo de test (se
  investiga). Replanificar por un emulador apagado creó tres partes que solo repetían comprobaciones.
- Comprobar: test con un `probe` falso que falla una vez; ensayo real apagando los emuladores antes de la final.

**0.4 Recoger la telemetría de la última tanda de un chat cerrado.** AI-kit. La parte 17 (2 h) no tiene datos
porque su chat se cerró antes de recogerla. `bt arm`/`resume` recoge las tandas terminadas de cualquier sesión del
mismo task. Comprobar: `telemetry-backfill` lista `w6-r1k5`.

### Fase 1 — Seguir sin el usuario (9,2 h de la primera noche)

**1.1 Tapa cerrada: descartado (decisión del usuario, 2026-11-13).** Ocurrió una vez y a propósito; el equipo no se
cierra mientras carga. No se cambia nada de energía.

**1.2 Encadenar etapas sin pedir un chat nuevo.** AI-kit.
- El chat orquestador no guarda estado (todo está en el ledger), así que no necesita ser «nuevo»: se elimina la
  parada «abre otro chat» en la frontera entre tandas y se deja compactar. Un hook `SessionStart` con
  `matcher: compact` reinyecta el protocolo del orquestador y el `slug` armado.
- Se reduce el crecimiento del chat: `bt watch` deja de emitir «sigue en marcha» cada 5 min cuando no hay novedad
  (una línea cada 30 min basta) y el orquestador no escribe un mensaje por evento benigno.
- Por qué: es el patrón de bucle de control sin estado; el estado duradero vive fuera del proceso que puede morir.
- Comprobar: ensayo con una tarea ligera de 3 partes forzando la compactación (`/compact`) entre tandas: la
  siguiente tanda arranca sola.
- Exploración aparte (no bloquea): un conductor sin chat (`claude -p "/big-task resume <slug>"` en bucle desde un
  proceso separado). Hay que verificar antes que la herramienta de flujos funciona en modo no interactivo.

**1.3 Segunda señal antes de matar una tanda.** AI-kit. La falsa alarma ya está corregida, pero costó 7 tandas.
`orch-next` solo responde `abandon-run` si **dos** señales independientes coinciden: sin actividad en los registros
de los agentes y sin un proceso vivo dueño de un candado de build de ese task. Comprobar: test con candado vivo ⇒
`wait`, no `abandon-run`.

### Fase 2 — No repetir trabajo ya hecho (≈ 7,5 h)

**2.1 El verificador reutiliza las comprobaciones caras ya grabadas.** AI-kit.
- Quien implementa ya puede ejecutar sus comprobaciones con `bt run-check --record`: las ejecuta `bt`, no el agente,
  y guarda resultado y commit. El verificador pide `run-check --list --unit` y repite solo las que no tienen un
  resultado grabado del mismo commit con el árbol limpio, más todas las baratas (menos de 30 s).
- Por qué: es la caché de resultados por contenido de Bazel, Gradle y Nx: mismo árbol, mismo resultado, no se
  vuelve a ejecutar. La independencia del verificador se conserva porque el resultado lo produce `bt`.
- Lo que el verificador sigue haciendo siempre: leer el diff y auditar el código (32 min en toda la tarea, y es lo
  único suyo que encontró algo).
- Comprobar: test: una comprobación grabada sobre el commit actual no aparece en `todo`; tras un commit nuevo, sí.

**2.2 La comprobación final la ejecuta `bt`, no un agente esperando.** AI-kit.
- `bt run-check --all --jobs N --record`: ejecuta lo pendiente en paralelo respetando los recursos (dos emuladores
  ⇒ dos comprobaciones de dispositivo a la vez; las de solo texto, todas juntas). El agente final lee el resultado.
- Por qué: en la final, 59 de 104 min fueron bucles `sleep`/`until` de un agente vigilando procesos en segundo
  plano. Un ejecutor determinista con trabajos en paralelo es lo que hace cualquier CI.
- Comprobar: test con 6 comprobaciones falsas de 2 s y `--jobs 3`: termina en ~4 s y graba las 6.

**2.3 Grabar recorridos en una sola orden.** Este repo.
- `bbadb.py run-steps <fichero>`: ejecuta una lista de pasos, devuelve el resultado de cada uno y una hoja de
  capturas; el implementador escribe el recorrido entero y lo ajusta, en vez de una orden por paso.
- Por qué: 3,3 h se fueron en órdenes sueltas de 20–120 s. Es el flujo «escribir el test, ejecutarlo entero».
- Comprobar: test en `.claude/scripts/test/` con `adb` falso.

### Fase 3 — Emulador más rápido (≈ 3 h más)

**3.1 `replay` espera condiciones, no tiempo.** Este repo, `bbadb.py`.
- Quitar el ritmo grabado (`--min-settle 0 --max-settle 0` por defecto; hoy es la mitad del tiempo). Medido en
  dos recorridos reales: 113,5 → 61,4 s y 129,0 → 61,0 s, pasando igual.
- `START` hace `am force-stop` antes de lanzar, para que un recorrido no dependa de la instalación previa.
- `WAIT n` antes de un `ASSERT`/`TAP` pasa a ser un tope: se sondea hasta encontrar el nodo (ya existe `_retry`).
- Un volcado sirve para todos los `ASSERT` y `SHOT` seguidos sobre la misma pantalla.
- Por qué: es la regla de las pruebas de UI (Espresso con recursos de espera, UiAutomator `Until`, Maestro): nunca
  dormir, esperar la condición. Las esperas fijas son a la vez lentas y la causa de los recorridos inestables.
- Esperado por el modelo: de 2 332 s a ~890 s para pasar los 16 recorridos una vez.
- Comprobar: los 16 recorridos pasan 3 veces seguidas con el nuevo modo; se anota el tiempo de cada uno.
- Exploración aparte: un servidor de UiAutomator en el dispositivo (o Maestro) baja cada consulta de 2,2 s a
  décimas. Solo merece la pena si tras 3.1 el volcado sigue dominando.

**3.2 Instalar una vez por commit y emulador.** `with-emulator.py` recuerda qué commit instaló en cada emulador y
salta `installDebug` si coincide. Ahorro pequeño por comprobación (10–40 s), grande en bucles de 5 comprobaciones.

**3.3 Paralelismo real de las partes de pantalla.**
- Ya hay dos emuladores. Antes de subir a tres: medir memoria con dos (la telemetría ya guarda `memAvailMB`); si
  baja de 3 GB, fijar la RAM de cada instancia y el tope de los daemons de Gradle en vez de añadir otra.
- Regla para el planificador (perfil, `planNotes`): una parte de pantalla depende solo de la base que necesita, y
  el recorrido completo es una parte final, no una dependencia de todas.
- Comprobar: en la siguiente tarea, `parallel.peak` ≥ 2 en las tandas de pantalla.

### Fase 4 — Medir que funcionó

- Telemetría (hecho hoy): `stageMs` por agente y tabla «Tiempo por etapa».
- Añadir a la tarjeta: horas de reloj con el equipo suspendido (el ledger ya anota «la máquina estuvo dormida») y
  relación reloj/trabajo por tarea.
- Tarea canario: una tarea real de 4–6 partes con dos de pantalla, lanzada y dejada sola.

## Orden y coste

| Fase | Trabajo | Recupera en una tarea como esta |
|---|---|---|
| 0 | medio día | 8 h y la revisión de rama |
| 1 | medio día | 9,2 h de reloj (la noche esperando un chat nuevo) |
| 2 | 1–2 días | ≈ 7,5 h |
| 3 | 1 día | ≈ 3 h, y menos recorridos inestables |
| 4 | 2 h | la prueba de que lo anterior funciona |

Las fases 0 y 1 son independientes entre sí y de este repo; la 2 depende de 0.1; la 3 es solo este repo.

## Ejecución

Dos tareas grandes, una por repositorio, porque `big-task` trabaja sobre un repo. Cada una en un chat nuevo abierto en
ese repo, con Opus para el plan y su aprobación (después basta Sonnet para reanudar).

1. **AI-kit** (puntos 0.1, 0.2, 0.3 parte de `bt`, 0.4, 1.2, 1.3, 2.1, 2.2 y la tarjeta de la fase 4). Antes hay que
   confirmar (commit) en AI-kit los cambios de telemetría de hoy, o la tarea partirá de una base sin ellos.
2. **BBetterCalendar** (puntos 0.3 parte del perfil, 2.3, 3.1, 3.2, 3.3), después de que la primera esté fusionada:
   el perfil usa `probe`/`ready`, que nacen en la primera.

### Estado: parte BBetterCalendar hecha (rama bt/wall-time-bbetter, 2026-11-13)

- **0.3 (perfil):** `.agents/profile.yml` declara `resources.emulator` con `probe` (`emulator-probe.py --count 2`), `ready`
  (`avd-up.ps1 -Count 2`) y la firma `with-emulator: no emulator attached`; `with-emulator.py` falla a los 60 s sin emulador.
- **2.3:** `bbadb.py run-steps <fichero>` con resultado por paso, hoja de capturas, `--keep-going` y `--save`; test con adb falso.
- **3.1:** `replay` sin ritmo grabado, `WAIT` como tope, `START` con `am force-stop`, un volcado para los `ASSERT` seguidos,
  tiempo en la última línea; `replay.ps1` es un envoltorio. Medido en mv-tour: ~257 s antes, 188,4 / 187,0 / 189,8 s tres
  pasadas seguidas; progress-smoke 15,1 s, mv-u11-projects 32,6 s.
- **3.2:** `with-emulator.py --install` instala una vez por commit y emulador (registro `<serial>.installed`); medido: 2 s con
  la instalación saltada, 8 s forzada.
- **3.3:** memoria con dos emuladores ~1,8 GB libres (989 MB con otros builds): daemons de Gradle ociosos salen a los 15 min
  (`org.gradle.daemon.idletimeout`), RAM del emulador sin tocar (el snapshot `bt_quick` está atado a 3072 MB). Regla del
  planificador en `planNotes`: parte de pantalla depende solo de su base; `mv-tour` es una parte final propia.
  Pendiente de medir en la siguiente tarea: `parallel.peak` >= 2 en las tandas de pantalla.

## Open questions

- Verificador: ¿se acepta que reutilice comprobaciones de dispositivo grabadas por `bt` sobre el mismo commit?
  Alternativa conservadora: reutilizar solo en la final y en las partes de recuperación.

## Verify

- `lockWait` = 0 min y ninguna tanda viva abandonada en la telemetría de la tarea canario.
- La revisión de rama aparece ejecutada en el cierre.
- Espera a emulador por debajo del 35 % del tiempo de agente (hoy 56 %).
- Reloj total ≤ 1,5 × el tiempo con algún agente trabajando (hoy 45,5 h frente a 19,9 h).

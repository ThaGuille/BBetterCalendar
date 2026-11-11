# Emuladores en paralelo para big-task

Status: in progress (preparado en la rama `emus-paralelos`; se activa cuando termine la tarea `mejora-visual`)
Created: 2026-11-11
Last updated: 2026-11-11

## Problema

En `mejora-visual` todas las unidades de UI declaran `@emulator`, y el perfil decía `resources.emulator: 1`:
big-task las ejecuta una detrás de otra aunque `bigTask.parallel` ya sea 3. Cada unidad tarda ~28-36 min, y
~10 de ellos son emulador: build, instalación, sembrado y recorridos.

Subir `parallel` no ayuda: el límite es el único AVD. Hacen falta dos cosas: más instancias del emulador, y que
cada unidad maneje **solo la suya**. Hoy `bbadb.py` y `_device.ps1` eligen «el primer emulator-*», y los checks
usan `adb` a secas. Con dos emuladores conectados, dos unidades sembrarían y tocarían el mismo.

## Qué se cambia

| Pieza | Cambio |
|---|---|
| `.claude/scripts/avd-up.ps1` | `-Count N`: reutiliza las instancias de `api36` que ya corren y arranca las que faltan; las de más, con `-read-only` (mismo AVD, sin disco extra; se pierde su estado al apagarlas). Test: `.claude/scripts/test/test-avd-up.ps1` (adb y emulador falsos). |
| `.claude/scripts/with-emulator.py` (nuevo) | Ejecuta un comando en un emulador libre: toma el lease (los mismos ficheros que `emulator-lease.*`), fija `ANDROID_SERIAL` y lo suelta al acabar. Espera si están todos ocupados. Si el proceso muere, otro toma su lease enseguida (lleva `pid=`). Test: `.claude/scripts/test/test_with_emulator.py`. |
| `bbadb.py`, `_device.ps1` | El serial por defecto pasa a ser `$ANDROID_SERIAL` antes que «el primer emulador». |
| `.agents/profile.yml` | `resources.emulator: 2` y planNotes nuevas: checks de dispositivo dentro de `with-emulator.py` con `timeout: 1500`, ningún serial fijo, y `avd-up.ps1 -Count 2` antes. |
| Docs | `CLAUDE.md` (tabla de entorno), `windows_commands.md` y `adb-ui-test/SKILL.md`. |

`bt` (AI-kit) no cambia: ya reparte `@recurso: N` entre unidades y relee `main:.agents/profile.yml` al empezar
cada tanda. Sus checks corren en `bash -c` heredando el entorno, y `ANDROID_SERIAL` lo respetan `adb`,
`gradlew installDebug` y, con este cambio, los scripts del repo.

## Por qué no se activa ya

`bt ready` lee el perfil de `main` en cada tanda, pero las unidades de `mejora-visual` usan los scripts de **su**
rama, que aún eligen el primer emulador. Sus checks, ya aprobados, llevan `adb` a secas. Si `emulator: 2` llegara
a `main` mientras corre, la siguiente tanda pondría dos unidades sobre el mismo emulador. Tampoco se arranca un
segundo emulador mientras corre: con dos conectados, su `adb shell …` a secas falla («more than one device»).

## Activación (cuando `mejora-visual` esté en `main`)

1. Rebase de `emus-paralelos` sobre `main`. Posibles roces: `bbadb.py` y `adb-ui-test/SKILL.md`, que la tarea toca.
2. `seed_progress_db.py`: `pick_serial` debe mirar `ANDROID_SERIAL` antes de buscar un emulador. El script de la
   tarea solo lo hace con `--serial` y se niega si hay dos emuladores.
3. Prueba real: `avd-up.ps1 -Count 2`. Hay que comprobar que la segunda instancia arranca `-read-only` desde
   `bt_quick`; si no, cae a frío. Después, dos `with-emulator.py -- "./gradlew.bat installDebug && python .claude/scripts/seed_progress_db.py --device && python .claude/skills/adb-ui-test/scripts/bbadb.py replay mv-tour"`
   a la vez, cada uno en un serial distinto y los dos en PASS.
4. Correr los dos tests de `.claude/scripts/test/`, merge a `main` y push.

## Riesgos

- `-read-only` con el snapshot `bt_quick` no está probado en esta máquina: `avd-up` cae a un arranque en frío
  read-only (~60 s) si falla.
- RAM: cada instancia usa 3 GB. Con 3 unidades y 2 daemons de Gradle cabe en 31 GB.
- Dos unidades que lancen `avd-up -Count 2` a la vez pueden arrancar una tercera instancia. Sobra, pero no rompe
  nada: el lease reparte igual.
- Lease abandonado con varias unidades a la vez (corregido el 2026-11-11): dos unidades que veían el mismo lease
  muerto podían quedarse las dos con el emulador, y en Windows el borrado del lease fallaba con WinError 32 si otra
  lo estaba leyendo. `with-emulator.py` ahora confirma el lease tras escribirlo y reintenta; el test lanza 6 a la vez.
  `emulator-lease.ps1` / `.py` (lease a mano) no se han revisado para esa misma carrera.
- Un `adb` a mano sin `ANDROID_SERIAL` falla con dos emuladores. Es intencionado: mejor un fallo ruidoso que tocar
  el emulador de otra unidad.

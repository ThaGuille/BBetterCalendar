// L-u4 (D-036): opciones comunes de todas las callables (región UE, D-013, y App Check).
// En producción enforceAppCheck es true: una llamada sin token de App Check válido responde 'unauthenticated'.
// En el emulador (FUNCTIONS_EMULATOR === 'true') solo se fuerza con BB_ENFORCE_APP_CHECK=1, que fija
// functions/.env.demo-bbetter (el proyecto de la suite de tests); el emulador de la app (bbetter-dbcda: app debug y
// actor.js) no lo fuerza. Toda callable nueva se declara en UNA línea como onCall(callableOptions, ...): el
// check de la unidad y deploy-preflight lo buscan así. Los triggers de eventos no usan estas opciones.
export const enforceAppCheck: boolean =
  process.env.FUNCTIONS_EMULATOR !== "true" || process.env.BB_ENFORCE_APP_CHECK === "1";

export const callableOptions = { region: "europe-west1", enforceAppCheck };

#!/usr/bin/env node
// L-u5 (L.7): añade la huella SHA-256 de la clave de firma de Play a assetlinks.json.
//   node firebase/scripts/set-release-fingerprint.mjs [--file <ruta>] <SHA-256>
// La huella sale de Play Console > Integridad de la app > Clave de firma de la app (32 pares hex separados por «:»).
// Se normaliza a mayúsculas, se conserva la de debug y es idempotente (repetirla no duplica nada).
// Con una entrada mala sale con 2 sin tocar el fichero. No contacta con ningún proyecto ni red.
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const PACKAGE = "io.github.thaguille.bbettercalendar";
const FORMAT = /^[0-9A-F]{2}(:[0-9A-F]{2}){31}$/;

function fail(msg) {
  console.error(`set-release-fingerprint: ${msg}`);
  console.error("uso: node firebase/scripts/set-release-fingerprint.mjs [--file <ruta>] <SHA-256 con 32 pares hex separados por ':'>");
  process.exit(2);
}

const here = dirname(fileURLToPath(import.meta.url));
let file = resolve(here, "../hosting/public/.well-known/assetlinks.json");
const rest = [];
const argv = process.argv.slice(2);
for (let i = 0; i < argv.length; i++) {
  if (argv[i] === "--file") {
    if (i + 1 >= argv.length) fail("--file pide una ruta");
    file = resolve(argv[++i]);
  } else {
    rest.push(argv[i]);
  }
}
if (rest.length !== 1) fail("hace falta exactamente una huella SHA-256");

const fp = rest[0].trim().toUpperCase();
if (!FORMAT.test(fp)) fail("la huella no son 32 pares hexadecimales separados por ':'");

let links;
try {
  links = JSON.parse(readFileSync(file, "utf8"));
} catch (e) {
  fail(`no se puede leer ${file} como JSON: ${e.message}`);
}
const entry = Array.isArray(links)
  ? links.find((l) => l && l.target && l.target.package_name === PACKAGE)
  : undefined;
if (!entry) fail(`${file} no tiene una entrada para ${PACKAGE}`);

const list = Array.isArray(entry.target.sha256_cert_fingerprints) ? entry.target.sha256_cert_fingerprints : [];
if (list.map((x) => String(x).toUpperCase()).includes(fp)) {
  console.log(`set-release-fingerprint: ya estaba (${fp}); sin cambios`);
  process.exit(0);
}
entry.target.sha256_cert_fingerprints = [...list, fp];
writeFileSync(file, JSON.stringify(links, null, 2) + "\n");
console.log(`set-release-fingerprint: añadida ${fp} a ${file}`);

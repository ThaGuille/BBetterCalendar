# 04 · Cumplimiento

> **Última revisión:** 2026-10-20 · Guía: [README](README.md)
>
> Límites legales y de Google Play. No es asesoría legal: antes del lanzamiento (gate L), revisar los
> textos oficiales vigentes. Las secciones se citan como `04 §N`.

## §1 Edad (D-010)

**Lo que dice la ley hoy:** en España, para tratar datos con consentimiento por debajo de **14 años**
hace falta el de madre, padre o tutor (LOPDGDD art. 7, que desarrolla el art. 8 del RGPD).

**Lo que puede cambiar:** el *Proyecto de Ley Orgánica para la protección de las personas menores de
edad en los entornos digitales* (expediente 121/000052) **sube esa edad a 16** y veta las redes sociales
a menores de 16 sin autorización parental. A finales de septiembre de 2026 seguía en tramitación
(ponencia en la Comisión de Justicia; faltaban las enmiendas, el Senado y el BOE). No está claro si un
círculo de amigos de estudio cuenta como «red social» a efectos de esa ley.

**Diseño (por tramos, con poca complejidad):**

1. **Puerta de edad neutral** la primera vez que se entra en lo social, antes de crear la cuenta. Pide
   el año de nacimiento, sin valor preseleccionado y sin pistas de qué edad desbloquea qué (es el
   patrón de «neutral age screen» de Play). No se pide al abrir la app: la app local no trata datos en
   ningún servidor.
2. **Umbral configurable** `SOCIAL_MIN_AGE = 14`. Es una constante en el código, ampliable a Remote
   Config, para poder subirlo a 16 si se aprueba la ley sin tener que rediseñar.
3. **Por encima del umbral:** todo lo social. El perfil guarda solo el tramo (`14-17` o `18+`) y no
   la fecha de nacimiento (minimización).
4. **Por debajo del umbral, en v1:** lo social **no está disponible**. Se muestra una pantalla amable
   («Para usar Amigos necesitas el permiso de tu madre, padre o tutor. Pronto podrás pedirlo desde
   aquí.») y **el resto de la app funciona completa**: la app se sigue ofreciendo a menores de 14.
   El año queda guardado en local para no volver a preguntarlo.
5. **Consentimiento parental**: diferido (**DIF-02**). Se revisa cuando se resuelva la ley o haya
   demanda real. Diseño provisional: email del tutor → enlace de verificación (Function + página en
   Hosting) → cuenta marcada como menor con un modo más restringido.
6. **Play Console:** público objetivo **13+** (**DIF-03**). Si el público incluye menores de 13, se
   aplica la *Families Policy* a **toda** la app: puerta de edad neutral, SDKs aprobados para
   servicios dirigidos a niños, revisión ampliada y posible incompatibilidad con algunos servicios de
   Google. Ningún menor de 13 llega a lo social con el umbral de 14, pero la declaración debe ser
   veraz.

## §2 Google Play

| Requisito | Qué exige | Cómo lo cumplimos | Entregable |
|---|---|---|---|
| **Borrado de cuenta** (desde 2023–24) | Si la app permite crear cuenta: borrado **dentro de la app y en la web**, y borrar también los datos asociados. Desactivar o congelar la cuenta no cuenta como borrado | `deleteAccount` (03 §7) desde Mi perfil. Página web en `docs/legal/` con instrucciones y un contacto para pedirlo. Enlace en Data safety | F1.6, L.2 |
| **Contenido generado por usuarios** | Los perfiles son UGC: aceptar términos antes de crearlo, **reportar y bloquear** usuarios (controles separados y con etiqueta clara), actuar a tiempo sobre los reportes | Términos en Crear perfil; reportar desde la lista de amigos y las solicitudes; **sin bloquear** (D-027): se revisa en L.3 si Play lo exige (DIF-11); proceso de moderación (§6) | F1.4, F2.6, L.4 |
| **Data safety** | Declarar qué datos se recogen y se comparten | Pasa de «nada sale del dispositivo» a: nombre, email, IDs de usuario, actividad en la app (minutos de foco agregados). Sin ubicación, contactos ni anuncios | L.2 |
| **Target API** | 36 para apps nuevas y actualizaciones desde el 31-ago-2026 | F0.1 | F0.1 |
| **Público objetivo** | Declaración veraz; Families Policy si incluye menores de 13 | 13+ (§1.6) | L.3 |
| **Política de privacidad** | URL pública coherente con lo que hace la app | Versión 2 (§4) | L.2 |

## §3 RGPD

- **Responsable:** el desarrollador (los datos de contacto ya están en la política actual).
- **Encargado:** Google (Firebase), con sus *Data Processing Terms*. Datos en la UE (D-013).
- **Base jurídica:** la ejecución del servicio que el usuario pide al crear la cuenta (art. 6.1.b). Los
  menores por debajo del umbral no llegan a crearla (§1).
- **Minimización:** solo agregados (02 §5), solo el tramo de edad y ningún campo para datos sensibles
  (03 §5).
- **Derechos:** supresión = borrar cuenta. Rectificación = editar el perfil. Acceso y portabilidad:
  bajo petición por email en v1 (la exportación automática se valora en el gate L).
- **Conservación:** mientras la cuenta exista. El borrado es inmediato en Firestore y RTDB; las copias
  de seguridad de Google caducan según sus plazos, y eso se indica en la política.

## §4 Política de privacidad y textos

La política actual (`docs/legal/privacy-policy.md`, versión 1, publicada en GitHub Pages) promete
literalmente **«no backend server, no cloud sync, no user account»** y que los datos **nunca salen del
dispositivo** ni se usan para analítica. Lo social **rompe esas promesas**, así que hace falta:

1. **Versión 2** de la política: una sección «Amigos (opcional)» que diga qué se sube, a quién, dónde
   (UE) y cómo borrarlo. Debe dejar claro que el **uso de apps y el tiempo de pantalla siguen sin salir
   del dispositivo**.
2. La versión 2 **vuelve a disparar el aviso y el consentimiento en la app** (lo dice la propia
   política: «bump the version when the substance changes»). Revisar cómo encaja con
   `ConsentRecord` en F0 o F1.
3. **Términos de uso** nuevos (`docs/legal/terms.md`): conducta, motivos de suspensión, edad mínima.
4. **Página de borrado** (`docs/legal/delete-account.md`).
5. **Analítica (Q-02):** la promesa de «never used for analytics» pesa en contra de añadir Firebase
   Analytics. La recomendación es medir solo con contadores agregados del backend (02 §11).

Los borradores se escriben en F0 y se **publican** al pasar el gate L. GitHub Pages publica todo
`docs/` desde `main` salvo lo excluido en `docs/_config.yml`. `social/` ya está excluido, y cada
borrador legal se añade a `exclude` hasta L.2. La política v2 va en un fichero aparte hasta entonces,
para no pisar la v1 publicada.

## §5 Lo que vigila la ley en trámite (121/000052)

| Si se aprueba… | Impacto | Acción |
|---|---|---|
| Edad de consentimiento a 16 | El umbral sube de 14 a 16 | Cambiar `SOCIAL_MIN_AGE`; los usuarios de 14–15 ya registrados necesitarían consentimiento parental o salir de lo social (DIF-02) |
| Veto a redes sociales para menores de 16 sin autorización | Depende de si BBetter Amigos se considera red social | Evaluar con el texto final; el diseño sin feed, chat ni contenido público ayuda |
| Plataformas autorizadas por la CNMC | Ídem | Ídem |

Al cerrar cada fase, revisar en qué punto está la ley y anotarlo en el diario del tablero.

## §6 Moderación

- **Qué contenido existe:** solo nombre visible y `@usuario` (sin fotos ni texto libre, P4).
- **Prevención:** una lista básica de palabras prohibidas en `@usuario` y en el nombre (`HandleValidator`,
  F1). Los `@usuario` reservados (admin, bbetter, soporte…) están bloqueados.
- **Reportes:** van a `reports/` (03 §5). Se revisan desde la consola de Firebase con una periodicidad
  fija, que se acuerda en L.4.
- **Acción:** renombrar a la fuerza un nombre ofensivo y, si se repite, deshabilitar al usuario en Auth
  (las reglas lo dejan fuera) y borrar sus datos sociales.
- **Menores:** hay usuarios de 14–17. Los reportes de acoso se atienden con prioridad.

# 01 · Investigación

> **Última revisión:** 2026-10-19 · Guía: [README](README.md)
>
> En qué se apoya el diseño. Cada hallazgo tiene un ID (`E…` estudios, `A…` apps) y una **calidad**:
> **RCT** (ensayo aleatorizado), **exp** (experimento), **obs** (observacional), **corr** (correlación
> publicada por la propia empresa), **op** (opinión, reseña o crónica). Un principio apoyado solo en
> `corr` u `op` es una apuesta, no un hecho.

## Resumen

1. **La comparación motiva más que el apoyo**, pero solo en **grupos pequeños** (E1, E2, E3).
2. **Comparar volumen sin límite acaba siendo tóxico**: burnout, trampas y miedo al último puesto
   (E6, E7). La salida es tener un tope y comparar constancia.
3. **El efecto medio de «lo social» es modesto y las preferencias varían mucho** (E9). Por eso todo es
   opcional y la app tiene que estar completa sin ello.
4. **La co-presencia pasiva funciona sin interacción directa** (E4, E10, A1). «Estudiando ahora» es la
   apuesta central y es barata de construir.
5. **El cuello de botella es la primera invitación** (E4). Invitar tiene que costar un toque.
6. **La privacidad por defecto importa**. Lo más criticado de Strava es que todo sea público por
   defecto (E11).

## Evidencia

### E1 · La comparación supera al apoyo (y el apoyo puede restar)
- **Qué:** 790 universitarios repartidos en redes de 6 personas. En las condiciones con comparación, la
  asistencia a clases de ejercicio fue ~90 % mayor que en las condiciones sin comparación. El grupo de
  «apoyo» (mensajes de ánimo) rindió **menos que el control**. La explicación de los autores es que el
  apoyo llama la atención sobre quien no participa y arrastra al grupo a la baja. En los grupos
  competitivos, los más activos fijan la referencia y el grupo se «trinquetea» al alza.
- **Fuente:** Zhang & Centola 2016, *Preventive Medicine Reports*. **Calidad:** RCT, una sola
  universidad, 2014.
- **Implica:** tiene que haber comparación (ranking), no solo ánimos → P2. Los ánimos (F5) son un
  complemento, nunca el mecanismo principal.

### E2 · La competición es lo único que se mantiene al retirar la intervención
- **Qué:** 602 adultos con sobrepeso. Brazos de apoyo, colaboración (equipos de 3) y competición
  (equipos de 3 con ranking semanal por email). Resultados frente al control: competición
  **+920 pasos/día**, apoyo +689 y colaboración +637. Al terminar la intervención, solo la competición
  seguía siendo mejor que el control.
- **Fuente:** Patel et al. 2019, STEP UP, *JAMA Internal Medicine*. **Calidad:** RCT (empleados de
  Deloitte).
- **Implica:** el ranking es **semanal y se reinicia** cada lunes, lo que encaja con la racha semanal
  ya existente → P2, D-005.

### E3 · N-effect: más competidores, menos motivación
- **Qué:** quien cree competir contra 10 rivales se esfuerza más que quien cree competir contra 100,
  aunque la probabilidad de ganar sea la misma. La comparación se diluye cuando N crece. Con pocos
  rivales la motivación sube; con muchos, cae.
- **Fuente:** Garcia & Tor 2009, *Psychological Science*. **Calidad:** exp (laboratorio) + obs.
- **Implica:** grupos pequeños, ranking solo entre amigos y nunca global → P1, D-015.

### E4 · Friend Streak de Duolingo
- **Qué:** los usuarios con al menos un Friend Streak tienen un **22 % más** de probabilidad de hacer la
  lección diaria, y la probabilidad sube con cada racha adicional. El 57 % tiene al menos un amigo, y
  en la carta a accionistas del Q2 2024 más de la mitad de los DAU sigue a algún amigo. El lanzamiento
  tenía un **tope de 5 amigos**: los usuarios con grupos grandes ya retenían bien y subir el tope no
  compensaba la complejidad. Su embudo tenía seis pasos y **el mayor cuello de botella era enviar la
  primera invitación**. La función no exige interacción directa: se trata de «aprender al lado de un
  amigo».
- **Fuente:** blog de producto de Duolingo; cartas a accionistas del Q2 y Q3 2024. **Calidad:** corr
  (con sesgo de selección).
- **Implica:** invitar en un toque (enlace + QR) → P1, F0.4, F2.3. La presencia pasiva aporta sin chat
  → P3, P4.

### E5 · Kudos: feedback de coste cero que funciona, y que también presiona
- **Qué:** en ~329 corredores de clubes de Strava, recibir más kudos predice correr más. Los menos
  activos imitan a los más activos. En un experimento pequeño (Bekhuis & Tolsma), quien recibió kudos
  tuvo más probabilidad de inscribirse en la siguiente carrera. Un estudio cualitativo de 2026 encontró
  que el feedback motiva **y** crea presión por rendir.
- **Fuente:** Universidad Radboud (Strava); Hallgren, Rønne & Wagner 2026. **Calidad:** obs + exp
  pequeño + cualitativo.
- **Implica:** un «ánimo» de un toque, **sin marcador público** de ánimos recibidos → P4, F5.1.

### E6 · Los rankings desmotivan a quien queda abajo (evidencia mixta)
- **Qué:** las posiciones altas generan complacencia y las bajas pueden motivar o desenganchar según la
  persona. En un experimento de campo con más de 1000 usuarios, la contribución dependía de cómo
  estuvieran repartidas las puntuaciones de los pares y del tamaño del grupo. Varios trabajos describen
  desmotivación en las últimas posiciones.
- **Fuente:** Na & Han 2023, *Internet Research*; experimento de campo con leaderboards relativos (NSF
  PAR 10510520). **Calidad:** exp.
- **Implica:** mostrar **tu posición y tus vecinos**, nunca «último», y ofrecer varias categorías →
  P2, 02 §6.

### E7 · YPT: lo que sale mal cuando se compara volumen
- **Qué:** reportaje estudiantil sobre YPT. Describe miedo al último puesto, temporizadores en marcha
  sin estudiar para subir en el ranking, avatares con «sudor» o «cabeza en llamas» según las horas,
  campeones de 321 h en un mes, obsesión con el ranking («YPTunnel vision») y burnout. Recomienda salir
  de los grupos tóxicos o bloquear a los compañeros demasiado intensos.
- **Fuente:** Raffles Press, febrero de 2025. **Calidad:** op (testimonio).
- **Implica:** **tope diario** de minutos que puntúan, no premiar maratones y no estigmatizar → P2,
  D-005.

### E8 · Perder una racha compartida provoca malestar
- **Qué:** las Snapstreaks se asocian con un uso problemático pequeño pero real y con tristeza al
  perderlas, aunque también dan sensación de conexión. La evidencia es escasa y de muestras pequeñas.
- **Fuente:** van Essen et al. 2023 (vía UBC); estudio de 2025 con adolescentes. **Calidad:** obs.
- **Implica:** si algún día hay racha compartida, que sea **semanal y con descanso**, como la racha
  actual (plan `racha-semanal-y-constancia`) → 05 §3.

### E9 · El efecto medio de las funciones sociales es modesto
- **Qué:** un meta-análisis de 19 estudios sobre funciones sociales en apps de actividad física no
  encontró efecto significativo, y las preferencias de los usuarios eran dispares. Otra revisión
  comprobó que las apps de comparación ignoran la literatura sobre diferencias individuales en cómo
  responde cada persona a compararse.
- **Fuente:** Tong & Laranjo 2018, *npj Digital Medicine*; Arigo et al. 2020, *JMIR*. **Calidad:**
  meta-análisis y meta-revisión.
- **Implica:** todo es **opcional** y la app no depende de lo social → P7, D-008.

### E10 · Body doubling y facilitación social
- **Qué:** la mera presencia de otros mejora el rendimiento en tareas ya aprendidas (Zajonc 1965,
  revisado por Guerin). Para el body doubling virtual (Focusmate) hay poca evidencia directa.
- **Fuente:** CHADD; FAQ de Focusmate. **Calidad:** teoría sólida, aplicación op.
- **Implica:** la presencia es una apuesta barata y razonable, **no la promesa del producto** → P3.

### E11 · Privacidad: por defecto, reciprocidad y control por persona
- **Qué:** Strava publica perfil y actividades a **todo el mundo por defecto**, es su decisión más
  criticada, y cambiar la configuración **no es retroactivo**. WhatsApp hace recíproca la «última
  vez»: si ocultas la tuya, no ves la de los demás. Un estudio sobre WhatsApp concluye que la
  reciprocidad pesa mucho en las decisiones de privacidad. Apple Fitness permite ocultar tu actividad y
  silenciar avisos **por amigo**.
- **Fuente:** guías de privacidad de Strava (Tom's Guide); Macworld (WhatsApp); *Online Information
  Review* (reciprocidad); soporte de Apple. **Calidad:** op + documentación oficial.
- **Implica:** solo amigos por defecto (P6). La reciprocidad de la presencia (Q-01) y el control por
  amigo (DIF-01) quedan para más adelante.

## Apps de referencia

| ID | App | Amistad | Qué se ve de otros | Interacción | Lección |
|---|---|---|---|---|---|
| A1 | **YPT** (Yeolpumta) | Grupos | Estado en vivo (activo/inactivo), horas de hoy, ranking por categoría | Ranking por horas | La presencia en vivo engancha; el ranking por horas brutas se vuelve tóxico (E7) |
| A2 | **Forest** | Amigos y salas por código | Ranking de amigos | *Plant Together*: si alguien usa el móvil, mueren todos los árboles | Compromiso compartido para sesiones conjuntas → 05 §2 |
| A3 | **Flora** | Vincularse con un amigo (vía Facebook) | Jardín compartido | Chat; apuestas con dinero | Las reseñas critican el registro con Facebook; evitar chat y dinero |
| A4 | **Duolingo** | Seguir + Friend Streak (máx. 5) | Racha conjunta; XP en ligas de 30 | Friends Quests (cooperativas), avisos para animar al amigo | Combinar un objetivo cooperativo con una liga pequeña (E4) |
| A5 | **Strava** | Seguir (aprobación opcional) | Actividades | Kudos, comentarios | Visibilidad en 3 niveles, ajustable por actividad; el default público es lo más criticado (E11) |
| A6 | **Apple Fitness** | Compartir mutuo | % de anillos | Competición de 7 días con **tope de 600 pts/día** | Tope diario contra el exceso; ocultar y silenciar por amigo |
| A7 | **Finch** | Amigos | Casi nada | «Good vibes», abrazos | Interacción positiva sin exponer datos; la crítica es que resulta superficial |
| A8 | **WhatsApp** | — | «Últ. vez» | — | Reciprocidad de la presencia (E11) |
| A9 | **Opal** | Amigos | Leaderboard de tiempo «ahorrado» | Retos entre amigos | Comparar el tiempo de pantalla existe, pero es un dato sensible (P5); afirmaciones de la propia empresa |

## Límites de esta investigación

- Las pruebas más sólidas (E1, E2) son de **ejercicio físico**, no de estudio, y de poblaciones
  concretas: universitarios y empleados.
- Las cifras de Duolingo son **correlacionales** y las publica la propia empresa.
- No hay estudios controlados sobre la presencia en vivo en apps de estudio. A1 y E10 justifican la
  apuesta, pero no la demuestran.
- Varias fuentes son secundarias (prensa o blogs que resumen papers). Antes de citarlas fuera de este
  repo hay que leer el original.

## Fuentes

- E1 · [Zhang & Centola 2016 — Support or competition?](https://chibe.upenn.edu/publications/support-or-competition-how-online-social-networks-increase-physical-activity-a-randomized-controlled-trial/)
- E2 · [Penn Medicine — STEP UP](https://pennmedicine.org/news/news-releases/2019/september/using-a-wearable-device-to-exercise-more-add-competition-to-improve-your-results) · [ACC quick take](https://acc.org/latest-in-cardiology/articles/2019/11/26/08/23/key-takeaways-regarding-gamification-to-increase-physical-activity)
- E3 · [Garcia & Tor 2009 — The N-effect (PDF)](https://public.websites.umich.edu/~smgarcia/pubs/n-effect.pdf)
- E4 · [Duolingo — Product lessons from Friend Streak](https://blog.duolingo.com/product-lessons-friend-streak/) · [Carta Q2 2024](https://www.sec.gov/Archives/edgar/data/1562088/000156208824000203/q2fy24duolingo06-30x24shar.htm) · [Carta Q3 2024](https://www.sec.gov/Archives/edgar/data/1562088/000156208824000248/q3fy24duolingo09-30x24shar.htm)
- E5 · [Running Magazine — Kudos (Radboud)](https://runningmagazine.ca/?p=91520) · [Hallgren et al. 2026](https://idrottsforum.org/hallgrenetal260512/)
- E6 · [Na & Han 2023](https://www.emerald.com/insight/content/doi/10.1108/intr-12-2021-0897/full/html) · [Relative leaderboards (NSF PAR)](https://par.nsf.gov/servlets/purl/10510520)
- E7 · [Raffles Press — Yawns, Pains, and Tears: On YPT](https://rafflespress.com/2025/02/13/yawns-pains-and-tears-on-ypt/)
- E8 · [UBC — Snapchat streaks](https://blogs.ubc.ca/etec523/2024/06/02/snapchat-streaks/)
- E9 · [Tong & Laranjo 2018](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC6550193/) · [Arigo et al. 2020](https://www.jmir.org/2020/3/e15642)
- E10 · [CHADD — Body doubling](https://chadd.org/?p=33269) · [Focusmate FAQ](https://www.focusmate.com/faq)
- E11 · [Tom's Guide — Strava privacy](https://tomsguide.com/wellness/fitness/how-to-adjust-your-strava-privacy-settings) · [Macworld — WhatsApp](https://www.macworld.com/article/677658/whatsapp-plans-selective-privacy-feature.html) · [Reciprocidad (OIR)](https://emerald.com/insight/content/doi/10.1108/OIR-05-2018-0171/full/html) · [Apple — Compartir actividad](https://support.apple.com/en-al/guide/watch/apd68a69f5c7)
- A1 · [YPT (App Store)](https://apps.apple.com/us/app/-/id1441909643) · A2 · [Forest](https://apps.apple.com/app/id866450515) · A3 · [Flora (Common Sense)](https://www.commonsensemedia.org/app-reviews/flora-focus-habit-tracker) · A7 · [Finch (Internet Matters)](https://www.internetmatters.org/advice/apps-and-platforms/wellbeing/finch/) · A9 · [Opal](https://apps.apple.com/us/app/opal-screen-time-for-focus/id1497465230)

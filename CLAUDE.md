# Rem's Glyph Toys

Glyph Toys (fluid, gallery, pulse) para la Glyph Matrix 25x25 del Nothing Phone (3). Kotlin + Jetpack Compose, Glyph Matrix SDK 2.0. La app ya funciona; el trabajo actual es **publicarla en Nothing Playground** (ver la ruta abajo).

## Reglas del proyecto

- Respuestas y docs internas en español MX, sin guión largo (usar ·). README y textos para Playground en inglés.
- Nunca hacer `git commit`; al cerrar cada sesión, dar un mensaje de conventional commit en español de una línea.
- Compilar con `./gradlew --no-daemon --no-watch-fs :app:assembleDebug` (los daemons viejos traban Gradle; matarlos si se cuelga).
- Las páginas de `docs/` son autocontenidas y siguen sherry_theme (`../37.iroFactory/sherry/`): tokens copiados en línea, Bunny Fonts, sin #000/#fff puros. Lo que se traiga de sherry se porta en línea, no se importa.
- Diagrama de arquitectura: `ariadne generate` desde la raíz si cambia la estructura del código.

## Ruta a Nothing Playground

Una sesión por bloque, en orden. Al terminar una, marcar sus casillas aquí y anotar decisiones o pendientes debajo del bloque. Al arrancar, leer solo este archivo y los archivos que el bloque nombra.

### S1 · Licencias y limpieza del repo
- [x] Dejar de rastrear `app/libs/glyph-matrix-sdk-2.0.aar` (`git rm --cached` + `.gitignore`); la licencia del SDK §2.1(c) prohíbe redistribuirlo. Decidir con la persona usuaria si además se limpia el historial (reescribir historia de un repo público).
- [x] Dejar de rastrear `tools/__pycache__/` y agregarlo al `.gitignore`.
- [x] Crédito de React Bits en `docs/index.html`: el fondo Dither es un port (MIT + Commons Clause, exige conservar el aviso). Comentario de licencia en el encabezado, como en `../37.iroFactory/sherry/index.html`, y mención en el footer.
- [x] Redactar el correo a GDKsupport@nothing.tech: ¿un toy publicado necesita un `NothingKey` real en vez de `test` (`app/src/main/AndroidManifest.xml`)? La persona usuaria lo envía. **S4 depende de la respuesta.**
- Decisiones S1 (2026-10-20): `.gitignore` ignora `app/libs/*.aar` y `__pycache__/`. Historial reescrito con `git filter-repo` en un clon espejo (sin el `.aar` ni el `.pyc`); la persona usuaria hace el force push y luego `git reset --soft origin/main` en local. El header de `docs/index.html` acredita sherry (MIT) y React Bits (aviso completo). Correo a GDK support redactado; **pendiente la respuesta**.
- Pendiente para S6: el Roadmap del README todavía dice que el `.aar` está en el historial.

### S2 · Documentación con lo nuevo de sherry
- [x] Tabla de gestos por toy en `docs/index.html`, al estilo de `doc-keys` de sherry (inclinar, mantener el botón Glyph, presión larga, doble toque en la vista previa de pulse). Sacar los gestos reales del código, no del README.
- [x] `:focus-visible` con el acento en botones y links de `docs/index.html` y `docs/pulse.html`.
- [x] Revisar `--rose` y otros pasteles usados como texto en modo claro; si no pasan WCAG AA, usar las tintas `-ink` de sherry.
- [x] Opcional: lema "build anything" en el footer.
- Fuera de alcance a propósito: terminal, paleta de comandos, Tamauijetto, taller, BIOS, glitch, protector y sonido de sherry.
- Decisiones S2 (2026-10-20): sección `#gestos` nueva tras Toys, con el aspecto de `doc-keys` portado en línea (grupos, chips `kbd`, filtro todo · matriz · app) pero sin escuchar teclas. Gestos tomados de los servicios y vistas: `EVENT_ACTION_DOWN` = mantener Glyph, `EVENT_CHANGE` = presión larga; pulse no usa el botón. Contraste: todo color usado como texto pasa AA en ambos modos (mínimo 4.6:1); `--rose` claro da 2.9:1 pero solo pinta destellos del lienzo, así que queda un comentario en vez de `--rose-ink`. El lema va en el footer de las dos páginas.

### S3 · App: botón al administrador de Glyph Toys
- [x] Botón en la app que abre el administrador de Glyph Toys del sistema para agregar los toys (recomendado en el README del GlyphMatrix Developer Kit; requiere sistema 20250829 o posterior). Tomar el intent exacto del README del kit; si no existe en la versión del teléfono, ocultar el botón.
- [x] Textos en `values/strings.xml` y `values-es/strings.xml`.
- Decisiones S3 (2026-10-20): intent del README del kit, `ComponentName("com.nothing.thirdparty", "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity")`. Botón `> add toys to the carousel` / `> agregar toys al carrusel` bajo el encabezado del inicio, en violeta (ningún toy usa ese neón). Se oculta si `resolveActivity` no la encuentra; por eso el manifest declara `<queries>` con el paquete `com.nothing.thirdparty`. `startActivity` atrapa `ActivityNotFoundException` y `SecurityException`. Compila; **falta probarlo en el teléfono** (que aparezca el botón y abra el administrador).

### S4 · Release v1.0
- `NothingKey` resuelto sin esperar el correo (2026-10-20): el README oficial del Glyph Developer Kit dice que la restricción de API key se quitó desde Android B (Android 16) y que ya no hay que pedir key si la app apunta a esa versión; recomienda conservar el meta-data. La app tiene `targetSdk = 36` (Android 16), así que se queda `NothingKey = test`. Toys ya publicados como GlyphMarquee (v1.5.2) usan `test` también. Si GDK support contesta otra cosa, prevalece su respuesta.
- [ ] Firma de release con keystore **fuera del repo** (rutas y contraseñas en `local.properties` o variables de entorno, nunca en git).
- [ ] Revisar `versionCode`/`versionName` y si conviene minificar (probar que los toys sigan enlazando con R8).
- [ ] Compilar el APK firmado y probarlo instalado limpio en el teléfono (sin la build de debug).
- [ ] Changelog v1.0 en `docs/` al estilo de `doc-changelog`; su texto sirve de notas de la release.
- [ ] GitHub Release v1.0 con el APK. La persona usuaria lo publica.
- [ ] Links de escaneo de VirusTotal y Koodous del APK (las reglas de nothing.community los piden al compartir un APK).
- [ ] README: ruta "descargar e instalar el APK" antes de la de compilar; ajustar el bloque `diff` (key, sideload).

### S5 · Capturas
- [x] La persona usuaria toma capturas y videos cortos; guardarlos como `docs/screenshots/rgt_ss1.png` … `rgt_ss6.png` (ya referenciados en README y `docs/index.html`).
- [x] Optimizar peso, revisar los `alt` y quitar los placeholders de "captura pendiente" si ya no aplican.
- Decisiones S5 (2026-10-21): capturas tomadas por adb (`screencap`) con la app en inglés (`cmd locale set-app-locales`) y la barra de estado en modo demo; ambos restaurados después. No hay forma de fotografiar los LEDs, así que fluid, gallery y pulse son las vistas previas de la app (misma simulación que el toy) recortadas a 9:16 a lo ancho completo; inicio, editor y personalizar son pantalla completa. Todas a 720 px de ancho, 160 a 290 KB. Captions y `alt` dicen "vista previa", no "matriz física". Se quitaron los huecos de "captura pendiente" (HTML, CSS y JS) y el `.gitkeep`. Gallery muestra el glifo dibujado, no la foto personal (que solo asoma abajo en `rgt_ss1`). Pulse se capturó con Spotify sonando. La app se reinstaló con la build de debug y el botón del administrador aparece (S3 sigue pendiente de confirmar que lo abre).
- Pendiente: sin video. Si algún día hay foto o video real de la matriz, sustituir `rgt_ss2/3/5`. El Roadmap del README sigue diciendo "placeholders today" (se limpia en S6).

### S6 · Envío
- [ ] Texto para el formulario de Playground (inglés): descripción corta, los tres toys, requisito Phone (3), aviso de que pulse pide el micrófono solo porque `Visualizer` lo exige y no graba nada.
- [ ] Post para nothing.community con repo, release y los dos escaneos.
- [ ] Actualizar el Roadmap del README (quitar lo publicado).

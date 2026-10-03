![Kotlin](https://img.shields.io/badge/-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Android](https://img.shields.io/badge/-Android%2016-3DDC84?style=for-the-badge&logo=android&logoColor=white)

```ascii
██████╗ ███████╗███╗   ███╗███████╗
██╔══██╗██╔════╝████╗ ████║██╔════╝
██████╔╝█████╗  ██╔████╔██║███████╗
██╔══██╗██╔══╝  ██║╚██╔╝██║╚════██║
██║  ██║███████╗██║ ╚═╝ ██║███████║
╚═╝  ╚═╝╚══════╝╚═╝     ╚═╝╚══════╝
 ██████╗ ██╗  ██╗   ██╗██████╗ ██╗  ██╗    ████████╗ ██████╗ ██╗   ██╗███████╗
██╔════╝ ██║  ╚██╗ ██╔╝██╔══██╗██║  ██║    ╚══██╔══╝██╔═══██╗╚██╗ ██╔╝██╔════╝
██║  ███╗██║   ╚████╔╝ ██████╔╝███████║       ██║   ██║   ██║ ╚████╔╝ ███████╗
██║   ██║██║    ╚██╔╝  ██╔═══╝ ██╔══██║       ██║   ██║   ██║  ╚██╔╝  ╚════██║
╚██████╔╝███████╗██║   ██║     ██║  ██║       ██║   ╚██████╔╝   ██║   ███████║
 ╚═════╝ ╚══════╝╚═╝   ╚═╝     ╚═╝  ╚═╝       ╚═╝    ╚═════╝    ╚═╝   ╚══════╝
       by Hex (@RemiH06)          version 1.0
```

![AGPL-3.0](https://img.shields.io/badge/License-AGPL--3.0-blue.svg?style=for-the-badge)

## Overview

### General Description

**Rem's Glyph Toys** is a collection of custom Glyph Toys for the 25x25 Glyph Matrix on the back of the Nothing Phone (3). Each toy is selected from the Glyph Button carousel and draws directly on the physical matrix; the companion app only shows live previews and requests the permissions the toys need, since they run in the background and cannot prompt on their own.

Three toys ship today: **fluid**, water simulated with FLIP like [mitxela's fluid pendant](https://mitxela.com/projects/fluid-pendant), driven by the accelerometer; **gallery**, photos you pick in the app plus glyphs you draw yourself on the real 489-LED layout, reduced to the matrix by average pooling; and **pulse**, three shapes that breathe with whatever audio is playing (by default a hexagon for bass, a diamond for vocals, a triangle for highs, since the matrix has brightness but no color, each swappable for an alternate shape), plus a fourth ring for the kick drum. The loudest group ends up outside, quiet groups rest still at a chosen idle pose (the app's logo, a cat's slit pupil, the current time, melting into water, falling solid pieces and more), and shapes only cross when two groups are equally loud, whether the sound goes through the speaker, wired headphones or Bluetooth. The app UI follows [sherry_theme](https://github.com/RemiH06/iroFactory) and ships in English and Spanish; full documentation with an interactive architecture diagram lives in [`docs/`](docs/index.html). How pulse works, with every formula and an interactive lab, is in [`docs/pulse.html`](docs/pulse.html).

| App | fluid | gallery | my glyphs | pulse | pulse settings |
|:---:|:---:|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/rgt_ss1.png" width="150" alt="App home with the three toy previews"/> | <img src="docs/screenshots/rgt_ss2.png" width="150" alt="fluid on the Glyph Matrix"/> | <img src="docs/screenshots/rgt_ss3.png" width="150" alt="gallery on the Glyph Matrix"/> | <img src="docs/screenshots/rgt_ss4.png" width="150" alt="drawing a glyph on the real LED layout"/> | <img src="docs/screenshots/rgt_ss5.png" width="150" alt="pulse on the Glyph Matrix"/> | <img src="docs/screenshots/rgt_ss6.png" width="150" alt="picking a rest pose and shapes for pulse"/> |

```diff
- Requires a Nothing Phone (3) (DEVICE_23112) running Nothing OS 4.0 / Android 16
- Uses the development NothingKey "test"; distribution requires a key from Nothing
- Not available on the Play Store, sideload only
```

## Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/RemiH06/RemsGlyphToys.git
   cd RemsGlyphToys
   ```

2. Download the Glyph Matrix SDK (v2.0) from the [GlyphMatrix Developer Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit) and place it at:
   ```
   app/libs/glyph-matrix-sdk-2.0.aar
   ```

3. Connect the phone with **Developer Options → USB Debugging** enabled and install the debug build:
   ```bash
   ./gradlew :app:installDebug
   ```

4. Open **Rem's Glyph Toys** and set up the toys that need it:
   - **gallery**: tap **pick photos** to choose what to show (system Photo Picker, no storage permission), or **draw** to create your own glyphs; both share the same rotation
   - **pulse**: tap the preview to grant the microphone permission, required by Android's `Visualizer` to read the output mix; nothing is recorded. Tap **customize** to pick the idle pose and the shape of each band, in a dedicated screen

5. Open the Glyph Toy settings (long-press the Glyph Button, or **Settings → Glyph Interface → Glyph Toys**), enable **Fluid**, **Gallery** and **Pulse**, and cycle to them with a short press.

6. Optional: toys go dark after 30 s by default. Raise **Timeout duration** in the Glyph Toys settings (up to 30 minutes), or go beyond with adb; the SDK's `setGlyphMatrixTimeout` only works for Nothing's own apps:
   ```bash
   adb shell settings put system glyph_toy_timeout 3600000
   ```

To regenerate the architecture diagram:
```bash
ariadne generate
```

## Features

- **fluid**: FLIP water (particles plus a pressure grid, after Matthias Muller's [Ten Minute Physics](https://matthias-research.github.io/pages/tenMinutePhysics/) tutorial, the same method as [mitxela's fluid pendant](https://mitxela.com/projects/fluid-pendant)) in a circular bowl the size of the matrix; each LED lights by how many particles fall in it; tilt to pour, hold the Glyph Button to shake, long-press to reset. The preview and the physical matrix are mirrored from each other (screen faces you, matrix faces away), so each reads the accelerometer's X axis with the opposite sign
- Dot-matrix logos for each toy and the project, generated from `tools/generate_glyph_icons.py` and exported to GlyphFactory (`design/glyphfactory_rgt.json`)
- All toys declare Always-On (AOD) support
- **gallery**: photos chosen with the system Photo Picker, center-cropped and average-pooled to the matrix, processing tuned for LEDs (auto-levels, 3x3 unsharp mask and 2.2 gamma); long-press for another one, never the same twice in a row
- **My glyphs**: a GlyphFactory-style canvas on the real 489-LED layout with a one-cell brush, five brightness levels and an eraser; saved glyphs join the gallery rotation with the same LED gamma as photos, and tapping a thumbnail edits it
- **pulse**: FFT of the system output mix split into six bands (sub, bass, low-mids, vocals, presence, air), each with its own gain control, grouped into three hollow blobs that breathe at their edges; layer sizes follow a log scale of each group relative to the loudest, so the loudest is always outside and the quietest inside, overlapping only when two groups are nearly equal; each shape rounds off when its group sounds harmonious and grows stretched spikes when it sounds harsh or abrupt (spectral flatness, spectral flux and Plomp-Levelt roughness, not audio quality); works regardless of audio route
  - **shapes, per band**, chosen in a dedicated settings screen: highs as a triangle or a circle (which grows short spikes instead when harsh), vocals as a diamond or an almond that only opens and closes (always the same width, reads as an eye with the center idle pose), bass as a hexagon or a pentagon
  - **kick drum ring**: a fourth shape detects kick hits (by how much the 35–180 Hz band jumps above the rest of the spectrum, resistant to the Visualizer's automatic gain steps) and can show them as a blob entering from outside the matrix, a dot launched from the center, or a ring that opens for the whole beat
  - **melody gate**: the vocal band only grows with an actual melodic line (a voice, a whistle, a wind instrument — one fundamental and its harmonics), so a chord or a plucked guitar doesn't inflate the diamond
  - **idle poses**, chosen in the same screen: shapes resting at the center (default; pair it with the almond vocal shape for an eye), a cat's slit pupil the size of the matrix that opens with volume, the three shapes nested like the app's logo, the kick ring parked at its maximum with the shapes camouflaged against it, a plumb-bob triangle that always points down, the shapes melting into FLIP water that spreads along the rim before reforming, the shapes falling as solid polygons that collide with each other and the walls, a 12-hour clock with an "AM"/"PM" label where each shape's outline becomes the strokes of the current time (HH:MM) and back, plain darkness, and three scenes of their own that the shapes fade into: a realistic jellyfish swimming in pulses with trailing tentacles, a realistic fish swimming and turning around, and bubbles rising nonstop
  - double-tap the preview to restart it (fresh shapes, audio reopened)
- Previews match the physical matrix: the real 489-LED layout of the Nothing Phone (3), square LEDs, faint 25x25 grid
- Toys without content or permission show a dim ring instead of going dark, to tell "nothing to show" apart from "not running"
- In-app live previews that reuse the exact same engines as the toys
- English and Spanish, following the phone's per-app language (Settings → Apps → Rem's Glyph Toys → Language)
- sherry_theme UI: JetBrains Mono and VT323, neon accents with glow in dark mode, CRT scanlines
- Interactive architecture diagram generated with [Ariadne](https://github.com/RemiH06/Ariadne)

## Roadmap

- **Publish fluid, gallery and pulse on [Nothing Playground](https://playground.nothing.tech/)** (sign in, *Upload your Glyph Toys*, public repo URL, description and a capture; moderators review it). Before submitting:
  - Stop tracking `app/libs/glyph-matrix-sdk-2.0.aar`: the [Glyph SDK license](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit/blob/main/LICENSE.md) forbids redistributing it (section 2.1(c)), and it is in this public repo's history
  - Ask GDKsupport@nothing.tech whether a published toy needs a real `NothingKey` instead of `test`
  - Signed release APK on GitHub Releases
  - Real captures and short videos for `docs/screenshots/` (placeholders today)
  - Explain in the listing that pulse asks for the microphone only because Android's `Visualizer` requires it; nothing is recorded
- **Glyph Matrix aware of Android modes**: a toy (for example a digital clock) that changes with the active mode, especially calendar-event modes. Apps can only see the Do Not Disturb level, not which mode is on, and cannot choose the active toy; mirroring each mode's calendar rule inside the app would work but duplicates the configuration. Idea to propose to Nothing: expose the active mode to Glyph Toys, or let each mode pick a toy.
- **Home screen widgets**: being explored, including whether fluid can run live in a widget.

## Autoría

por Hex ([@RemiH06](https://github.com/RemiH06))

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

Three toys ship today: **fluid**, water simulated with FLIP like [mitxela's fluid pendant](https://mitxela.com/projects/fluid-pendant), driven by the accelerometer; **gallery**, photos you pick in the app, reduced to the matrix by average pooling; and **pulse**, three nested hollow shapes that breathe with whatever audio is playing (a hexagon for bass, a diamond for vocals, a triangle for highs, since the matrix has brightness but no color): the loudest group ends up outside, quiet groups rest still at the center, and shapes only cross when two groups are equally loud, whether the sound goes through the speaker, wired headphones or Bluetooth. The app UI follows [sherry_theme](https://github.com/RemiH06/iroFactory), and full documentation with an interactive architecture diagram lives in [`docs/`](docs/index.html).

| App | fluid | gallery | pulse |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/rgt_ss1.png" width="180" alt="App previews"/> | <img src="docs/screenshots/rgt_ss2.png" width="180" alt="fluid on the Glyph Matrix"/> | <img src="docs/screenshots/rgt_ss3.png" width="180" alt="gallery on the Glyph Matrix"/> | <img src="docs/screenshots/rgt_ss4.png" width="180" alt="pulse on the Glyph Matrix"/> |

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
   - **gallery**: tap **elegir fotos** and pick the photos to show (system Photo Picker, no storage permission)
   - **pulse**: tap the preview to grant the microphone permission, required by Android's `Visualizer` to read the output mix; nothing is recorded

5. Open the Glyph Toy settings (long-press the Glyph Button, or **Settings → Glyph Interface → Glyph Toys**), enable **Fluid**, **Gallery** and **Pulse**, and cycle to them with a short press.

To regenerate the architecture diagram:
```bash
ariadne generate
```

## Features

- **fluid**: FLIP water (particles plus a pressure grid, after Matthias Muller's [Ten Minute Physics](https://matthias-research.github.io/pages/tenMinutePhysics/) tutorial, the same method as [mitxela's fluid pendant](https://mitxela.com/projects/fluid-pendant)) in a circular bowl the size of the matrix; each LED lights by how many particles fall in it; tilt to pour, hold the Glyph Button to shake, long-press to reset
- Dot-matrix logos for each toy and the project, generated from `tools/generate_glyph_icons.py` and exported to GlyphFactory (`design/glyphfactory_rgt.json`)
- All toys declare Always-On (AOD) support, so they can stay lit instead of falling back to the default toy
- **gallery**: photos chosen with the system Photo Picker, center-cropped and average-pooled to the matrix; long-press for another one, never the same twice in a row
- **pulse**: FFT of the system output mix split into six bands (sub, bass, low-mids, vocals, presence, air), each with its own gain control, grouped into three hollow blobs that lean toward rounded polygons (hexagon, diamond, triangle), rotate slowly and breathe at their edges; layer sizes follow a log scale of each group relative to the loudest, so the loudest is always outside and the quietest inside, overlapping only when two groups are nearly equal; works regardless of audio route
- **gallery** processing tuned for LEDs: auto-levels, 3x3 unsharp mask and 2.2 gamma on output
- Previews match the physical matrix: the real 489-LED layout of the Nothing Phone (3), square LEDs, faint 25x25 grid
- Animated toys disable the Glyph Matrix timeout so the system does not fall back to the default toy mid-animation
- Toys without content or permission show a dim ring instead of going dark, to tell "nothing to show" apart from "not running"
- In-app live previews that reuse the exact same engines as the toys
- sherry_theme UI: JetBrains Mono and VT323, neon accents with glow in dark mode, CRT scanlines
- Interactive architecture diagram generated with [Ariadne](https://github.com/RemiH06/Ariadne)

## Autoría

por Hex ([@RemiH06](https://github.com/RemiH06))

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

Three toys ship today: **fluid**, an SPH fluid simulation driven by the accelerometer; **gallery**, a random photo from the device reduced to the matrix by average pooling; and **pulse**, an NCS-style sphere that breathes with whatever audio is playing, whether through the speaker, wired headphones or Bluetooth. The app UI follows [sherry_theme](https://github.com/RemiH06/iroFactory), and full documentation with an interactive architecture diagram lives in [`docs/`](docs/index.html).

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

4. Open **Rem's Glyph Toys** and tap each preview to grant its permissions:
   - **gallery**: photos (full access or "select photos")
   - **pulse**: microphone, required by Android's `Visualizer` to read the output mix; nothing is recorded

5. Open the Glyph Toy settings (long-press the Glyph Button, or **Settings → Glyph Interface → Glyph Toys**), enable **Fluid**, **Gallery** and **Pulse**, and cycle to them with a short press.

To regenerate the architecture diagram:
```bash
ariadne generate
```

## Features

- **fluid**: real-time SPH simulation at the matrix's native 25x25 resolution; tilt to pour, hold the Glyph Button to splash, long-press to reset
- **gallery**: random photo from the device, center-cropped and average-pooled to the circular matrix; long-press for another one
- **pulse**: sphere scaled by the RMS level of the system output mix, with expanding rings on detected peaks; works regardless of audio route
- Animated toys disable the Glyph Matrix timeout so the system does not fall back to the default toy mid-animation
- Toys without content or permission show a dim ring instead of going dark, to tell "nothing to show" apart from "not running"
- In-app live previews that reuse the exact same engines as the toys
- sherry_theme UI: JetBrains Mono and VT323, neon accents with glow in dark mode, CRT scanlines in light mode
- Interactive architecture diagram generated with [Ariadne](https://github.com/RemiH06/Ariadne)

## Autoría

por Hex ([@RemiH06](https://github.com/RemiH06))

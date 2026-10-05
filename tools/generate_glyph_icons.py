"""
Genera los logos de Rem's Glyph Toys como matrices de la Glyph Matrix real
(489 LEDs, filas de 7 a 25) y los exporta a:

  app/src/main/res/drawable/ic_toy_*.xml         iconos del carrusel del boton Glyph
  app/src/main/res/drawable/ic_launcher_*.xml    icono de la app (logo del proyecto)
  app/src/main/res/drawable/ic_app_logo.xml      logo para el header del menu de seleccion (fondo transparente)
  docs/logo.svg                                  logo para la documentacion
  design/glyphfactory_rgt.json                   los mismos disenos, editables en GlyphFactory

Uso, desde la raiz del repo:  python tools/generate_glyph_icons.py [dir_previews]
Con dir_previews tambien deja PNG de revision (requiere Pillow).
"""

import json
import math
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
N = 25
C = N / 2
LED_R = 12.5
ROW_SPANS = [7, 11, 15, 17, 19, 21, 21, 23, 23, 25, 25, 25, 25, 25, 25, 25, 23, 23, 21, 21, 19, 17, 15, 11, 7]
MASK = [[(N - w) // 2 <= c < (N - w) // 2 + w for c in range(N)] for w in ROW_SPANS]

ON_WHITE = "#F0F0F0"     # sherry --text, sin blanco puro
OFF_DIM = "#262626"
MAGENTA = "#FF1466"      # sherry --magenta
BG_DARK = "#080808"      # sherry --bg


def empty():
    return [[False] * N for _ in range(N)]


def rounded_polygon(sides, size, amp, rot):
    return lambda th: size * (1 + amp * math.cos(sides * (th - rot)))


def outline(grid, radius_fn, thickness=0.6):
    """Enciende las celdas cuyo centro cae sobre el contorno polar radius_fn(theta)."""
    for r in range(N):
        for c in range(N):
            x, y = c + 0.5 - C, r + 0.5 - C
            if abs(math.hypot(x, y) - radius_fn(math.atan2(y, x))) < thickness:
                grid[r][c] = True


def design_pulse():
    """Las tres capas del toy pulse: hexagono (graves), diamante (voces), triangulo (agudos)."""
    # Hexagono y diamante: tamanos buscados por fuerza bruta para dejar al menos
    # una celda vacia entre contornos. El triangulo va en pixeles exactos: a
    # ese tamano una curva se lee como mancha.
    g = empty()
    outline(g, rounded_polygon(6, 10.9, 0.11, 0.0), 0.5)
    outline(g, rounded_polygon(4, 7.2, 0.14, 0.0), 0.5)
    for r, c in pixel_triangle(apex_row=9, center_col=12, height=6, half_base=3):
        g[r][c] = True
    return g


def pixel_triangle(apex_row, center_col, height, half_base):
    """Triangulo hueco casi equilatero: vertice arriba, lados escalonados y base completa."""
    cells = []
    for i in range(height):
        r = apex_row + i
        offset = round(half_base * i / (height - 1))
        if i == height - 1:
            cells += [(r, c) for c in range(center_col - offset, center_col + offset + 1)]
        else:
            cells += [(r, center_col - offset), (r, center_col + offset)]
    return cells


def design_fluid():
    """Agua en el recipiente con la superficie en oleaje, como la fluid pendant de mitxela."""
    g = empty()
    for c in range(N):
        x = c + 0.5
        surface = 13.6 - 0.22 * (x - C) + 1.2 * math.sin(x * 0.52 + 0.4)
        for r in range(N):
            y = r + 0.5
            if y > surface + 0.5:
                g[r][c] = True
    for r, c in [(9, 18), (7, 15), (10, 21), (8, 20)]:   # gotas sueltas del oleaje
        g[r][c] = True
    return g


def design_gallery():
    """Una foto: marco, sol y montanas."""
    g = empty()
    top, bottom, left, right = 6, 18, 4, 20
    for c in range(left, right + 1):
        g[top][c] = g[bottom][c] = True
    for r in range(top, bottom + 1):
        g[r][left] = g[r][right] = True
    for r in range(7, 12):                               # sol, arriba a la derecha y sin tocar montanas
        for c in range(15, 20):
            if math.hypot(c - 17, r - 9) <= 1.5:
                g[r][c] = True
    peaks = [(5, 17), (8, 12), (11, 15), (13, 13), (16, 17), (19, 17)]   # montanas: (col, fila) de la cresta
    for c in range(left + 1, right):
        for (c0, r0), (c1, r1) in zip(peaks, peaks[1:]):
            if c0 <= c <= c1:
                ridge = r0 + (r1 - r0) * (c - c0) / (c1 - c0)
                for r in range(math.ceil(ridge), bottom):
                    g[r][c] = True
                break
    return g


def clip(grid):
    return [[grid[r][c] and MASK[r][c] for c in range(N)] for r in range(N)]


# ── Salidas ──────────────────────────────────────────────────────────────────

def cells_path(cells, pitch, cell, origin):
    parts = []
    for r, c in cells:
        x = origin + c * pitch + (pitch - cell) / 2
        y = origin + r * pitch + (pitch - cell) / 2
        parts.append(f"M{x:.2f},{y:.2f}h{cell:.2f}v{cell:.2f}h{-cell:.2f}z")
    return "".join(parts)


def vector_drawable(grid, on, off, pitch, origin, header, include_off=True):
    on_cells = [(r, c) for r in range(N) for c in range(N) if MASK[r][c] and grid[r][c]]
    off_cells = [(r, c) for r in range(N) for c in range(N) if MASK[r][c] and not grid[r][c]]
    cell = pitch * 0.82
    paths = []
    if include_off:
        paths.append(f'    <path android:fillColor="{off}" android:pathData="{cells_path(off_cells, pitch, cell, origin)}"/>')
    paths.append(f'    <path android:fillColor="{on}" android:pathData="{cells_path(on_cells, pitch, cell, origin)}"/>')
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f"<!-- {header} Generado por tools/generate_glyph_icons.py, no editar a mano. -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="108dp" android:height="108dp"\n'
        '    android:viewportWidth="108" android:viewportHeight="108">\n'
        + "\n".join(paths) + "\n</vector>\n"
    )


def svg(grid, on, off, bg):
    pitch, cell, pad = 10, 8.2, 12
    size = N * pitch + 2 * pad
    rects = []
    for r in range(N):
        for c in range(N):
            if not MASK[r][c]:
                continue
            x = pad + c * pitch + (pitch - cell) / 2
            y = pad + r * pitch + (pitch - cell) / 2
            rects.append(f'<rect x="{x:.1f}" y="{y:.1f}" width="{cell}" height="{cell}" fill="{on if grid[r][c] else off}"/>')
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {size} {size}" width="{size}" height="{size}">'
        f'<title>Rem\'s Glyph Toys</title><rect width="{size}" height="{size}" rx="{size * 0.22:.0f}" fill="{bg}"/>'
        + "".join(rects) + "</svg>\n"
    )


def glyphfactory(designs):
    palette = ["#f0f0f0"]
    glyphs = {}
    for category, name, grid in designs:
        gid = f"{category}::{name}"
        glyphs[gid] = {
            "id": gid, "name": name, "category": category, "cols": N, "rows": N, "palette": palette,
            "data": [[0 if (MASK[r][c] and grid[r][c]) else None for c in range(N)] for r in range(N)],
        }
    return {
        "meta": {
            "version": "1.0", "app": "GlyphFactory",
            "exportedAt": datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z"),
            "palette": [{"name": "blanco", "hex": "#f0f0f0"}],
        },
        "glyphs": glyphs,
    }


def preview(grids, path):
    from PIL import Image, ImageDraw
    pitch, cell, gap = 12, 10, 24
    w = len(grids) * (N * pitch + gap) + gap
    img = Image.new("RGB", (w, N * pitch + 2 * gap), (8, 8, 8))
    draw = ImageDraw.Draw(img)
    for i, (grid, on) in enumerate(grids):
        ox = gap + i * (N * pitch + gap)
        rgb = tuple(int(on[k:k + 2], 16) for k in (1, 3, 5))
        for r in range(N):
            for c in range(N):
                if MASK[r][c]:
                    x, y = ox + c * pitch, gap + r * pitch
                    draw.rectangle([x, y, x + cell - 1, y + cell - 1], fill=rgb if grid[r][c] else (38, 38, 38))
    img.save(path)


def main():
    pulse, fluid, gallery = clip(design_pulse()), clip(design_fluid()), clip(design_gallery())
    drawable = ROOT / "app/src/main/res/drawable"

    # Carrusel del boton Glyph: estilo de la matriz real, blanco sobre celdas tenues
    for name, grid in [("fluid", fluid), ("gallery", gallery), ("pulse", pulse)]:
        (drawable / f"ic_toy_{name}.xml").write_text(
            vector_drawable(grid, ON_WHITE, OFF_DIM, pitch=4.2, origin=1.5, header=f"Icono del toy {name}."),
            encoding="utf-8")

    # Icono de la app: el logo de pulse en magenta sherry, dentro de la zona segura (66dp)
    (drawable / "ic_launcher_foreground.xml").write_text(
        vector_drawable(pulse, MAGENTA, "#2A2A2A", pitch=2.7, origin=20.25, header="Logo del proyecto."),
        encoding="utf-8")
    (drawable / "ic_launcher_monochrome.xml").write_text(
        vector_drawable(pulse, ON_WHITE, OFF_DIM, pitch=2.7, origin=20.25,
                        header="Logo del proyecto, capa monocromatica.", include_off=False),
        encoding="utf-8")

    # Logo para el header del menu de seleccion: mismo diseno, sin fondo, para flotar sobre sherry_theme
    (drawable / "ic_app_logo.xml").write_text(
        vector_drawable(pulse, MAGENTA, "#2A2A2A", pitch=2.7, origin=20.25,
                        header="Logo del proyecto para el header del menu de seleccion.", include_off=False),
        encoding="utf-8")
    (drawable / "ic_launcher_background.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!-- Fondo del icono: el bg del sherry_theme. Generado por tools/generate_glyph_icons.py. -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="108dp" android:height="108dp"\n'
        '    android:viewportWidth="108" android:viewportHeight="108">\n'
        f'    <path android:fillColor="{BG_DARK}" android:pathData="M0,0h108v108h-108z"/>\n'
        "</vector>\n",
        encoding="utf-8")

    (ROOT / "docs/logo.svg").write_text(svg(pulse, MAGENTA, "#2A2A2A", BG_DARK), encoding="utf-8")

    design = ROOT / "design"
    design.mkdir(exist_ok=True)
    (design / "glyphfactory_rgt.json").write_text(json.dumps(glyphfactory([
        ("toy", "fluid", fluid), ("toy", "gallery", gallery), ("toy", "pulse", pulse), ("logo", "rems-glyph-toys", pulse),
    ]), ensure_ascii=False, indent=2), encoding="utf-8")

    if len(sys.argv) > 1:
        preview([(fluid, ON_WHITE), (gallery, ON_WHITE), (pulse, ON_WHITE), (pulse, MAGENTA)],
                Path(sys.argv[1]) / "glyph_icons.png")
    print("ok")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""
tools/generate_pebble_assets.py

Generates all asset files for cci_radar pebble blocks:
  - textures/block/<resource>_pebbles.png          (16x16 pixel-art, stdlib PNG)
  - models/block/<resource>_pebbles.json           (multi-cuboid 3D model)
  - models/item/<resource>_pebbles.json            (inherits block model)
  - blockstates/<resource>_pebbles.json            (single variant)
  - data/cci_radar/loot_table/blocks/<resource>_pebbles.json
  - lang/en_us.json                               (updated with block names)

Run from project root:
  python3 tools/generate_pebble_assets.py

No external dependencies — uses only Python stdlib (zlib, struct, json, pathlib).
Requires Python 3.8+.
"""

import json
import struct
import zlib
from pathlib import Path

ROOT   = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/cci_radar"
DATA   = ROOT / "src/main/resources/data/cci_radar"

# ── Resource definitions ──────────────────────────────────────────────────────
#   bg:        (r, g, b) base fill colour
#   fragments: list of (r, g, b) shade colours painted as rectangles on top
#   frag_rects: list of (x0, y0, x1, y1) for each fragment rectangle (0..15 coords)
#   label:     creative/lang name
#   elements:  list of [x0,y0,z0, x1,y1,z1] block model cuboids (0..16 mc units)

RESOURCES = {
    "coal": {
        "label": "Coal Pebbles",
        "bg": (28, 28, 28),
        "frag_rects": [(2,3,7,6), (8,2,12,6), (11,9,15,13), (3,9,8,13), (13,1,16,4)],
        "fragments":  [(68,68,68), (50,50,50), (78,78,78), (44,44,44), (60,60,60)],
        "elements": [
            [2, 0, 2,   6, 2,  5],
            [8, 0, 3,  12, 3,  7],
            [11,0,10,  14, 2, 13],
            [3, 0,10,   7, 1, 13],
            [13,0, 1,  15, 2,  4],
        ],
    },
    "iron": {
        "label": "Iron Pebbles",
        "bg": (95, 88, 80),
        "frag_rects": [(1,2,7,5), (5,7,12,10), (10,1,14,4), (2,11,6,14), (9,11,14,15), (7,13,10,16)],
        "fragments":  [(158,150,140), (128,65,38), (143,135,125), (112,52,32), (145,138,128), (120,58,36)],
        "elements": [
            [1, 0, 2,   7, 1,  5],
            [5, 0, 7,  12, 2, 10],
            [10,0, 1,  14, 1,  4],
            [2, 0,11,   6, 2, 14],
            [9, 0,11,  14, 1, 15],
            [7, 0,13,  10, 2, 15],
        ],
    },
    "copper": {
        "label": "Copper Pebbles",
        "bg": (138, 80, 36),
        "frag_rects": [(1,1,5,6), (6,4,10,7), (11,2,14,5), (2,9,5,12), (8,10,14,14)],
        "fragments":  [(182,112,48), (62,120,80), (168,96,44), (56,106,70), (175,104,46)],
        "elements": [
            [1, 0, 1,   5, 3,  6],
            [6, 0, 4,  10, 2,  7],
            [11,0, 2,  14, 3,  5],
            [2, 0, 9,   5, 2, 12],
            [8, 0,10,  14, 3, 14],
        ],
    },
    "zinc": {
        "label": "Zinc Pebbles",
        "bg": (88, 98, 110),
        "frag_rects": [(1,2,8,6), (6,8,14,12), (10,1,15,5), (2,12,9,15), (11,13,14,16)],
        "fragments":  [(142,155,168), (70,80,92), (157,170,182), (64,72,86), (130,143,156)],
        "elements": [
            [1, 0, 2,   8, 1,  6],
            [6, 0, 8,  14, 2, 12],
            [10,0, 1,  15, 1,  5],
            [2, 0,12,   9, 2, 15],
            [11,0,13,  14, 1, 15],
        ],
    },
    "redstone": {
        "label": "Redstone Pebbles",
        "bg": (58, 8, 8),
        "frag_rects": [(2,2,4,4), (6,5,9,8), (11,3,14,6), (3,10,6,13), (9,11,12,14), (13,10,15,12)],
        "fragments":  [(205,30,22), (162,14,10), (225,46,35), (142,18,14), (210,35,26), (150,22,16)],
        "elements": [
            [2, 0, 2,   4, 4,  4],
            [6, 0, 5,   9, 3,  8],
            [11,0, 3,  14, 4,  6],
            [3, 0,10,   6, 3, 13],
            [9, 0,11,  12, 4, 14],
            [13,0,10,  15, 2, 12],
        ],
    },
    "gold": {
        "label": "Gold Pebbles",
        "bg": (138, 105, 22),
        "frag_rects": [(2,3,6,7), (8,2,12,5), (10,8,14,12), (3,9,7,13), (5,14,9,16)],
        "fragments":  [(238,198,42), (204,162,30), (250,218,55), (182,144,24), (220,175,36)],
        "elements": [
            [2, 0, 3,   6, 3,  7],
            [8, 0, 2,  12, 2,  5],
            [10,0, 8,  14, 3, 12],
            [3, 0, 9,   7, 2, 13],
            [5, 0,14,   9, 1, 15],
        ],
    },
}


# ── Minimal stdlib PNG writer ─────────────────────────────────────────────────

def _make_png(pixels_rgba: list[list[tuple[int, int, int, int]]]) -> bytes:
    """Return PNG bytes for a 2D list of (r,g,b,a) tuples (row-major)."""
    h = len(pixels_rgba)
    w = len(pixels_rgba[0])

    def chunk(tag: bytes, data: bytes) -> bytes:
        c = tag + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)

    ihdr = chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))  # RGBA, 8-bit

    raw = bytearray()
    for row in pixels_rgba:
        raw.append(0)           # filter byte: None
        for r, g, b, a in row:
            raw += bytes([r, g, b, a])

    idat = chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    iend = chunk(b"IEND", b"")

    return b"\x89PNG\r\n\x1a\n" + ihdr + idat + iend


def make_pebble_texture(bg: tuple, frag_rects: list, fragments: list, size: int = 16) -> bytes:
    """
    Generate a pixel-art pebble texture.

    bg:         (r,g,b) background colour
    frag_rects: list of (x0,y0,x1,y1) in 0..size  — rectangular fragment regions
    fragments:  list of (r,g,b) colours, one per fragment rect
    """
    pixels = [[(bg[0], bg[1], bg[2], 255)] * size for _ in range(size)]

    for i, (x0, y0, x1, y1) in enumerate(frag_rects):
        col = fragments[i % len(fragments)]
        for py in range(max(0, y0), min(size, y1)):
            for px in range(max(0, x0), min(size, x1)):
                # Slight inner highlight: top-left corner one shade lighter
                shade = tuple(min(255, c + 15) for c in col) if (px == x0 or py == y0) else col
                pixels[py][px] = (shade[0], shade[1], shade[2], 255)

    return _make_png(pixels)


# ── Model JSON ────────────────────────────────────────────────────────────────

def make_block_model(resource: str, elements: list) -> dict:
    tex = f"cci_radar:block/{resource}_pebbles"
    els = []
    for x0, y0, z0, x1, y1, z1 in elements:
        els.append({
            "from": [x0, y0, z0],
            "to":   [x1, y1, z1],
            "faces": {
                "down":  {"texture": "#all", "cullface": "down"},
                "up":    {"texture": "#all"},
                "north": {"texture": "#all"},
                "south": {"texture": "#all"},
                "west":  {"texture": "#all"},
                "east":  {"texture": "#all"},
            },
        })
    return {
        "parent": "block/block",
        "textures": {"all": tex, "particle": tex},
        "elements": els,
    }


def make_item_model(resource: str) -> dict:
    return {"parent": f"cci_radar:block/{resource}_pebbles"}


def make_blockstate(resource: str) -> dict:
    return {"variants": {"": {"model": f"cci_radar:block/{resource}_pebbles"}}}


def make_loot_table(resource: str) -> dict:
    return {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "entries": [{"type": "minecraft:item", "name": f"cci_radar:{resource}_pebbles"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    }


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    tex_dir    = ASSETS / "textures/block"
    model_b    = ASSETS / "models/block"
    model_i    = ASSETS / "models/item"
    bstate_dir = ASSETS / "blockstates"
    loot_dir   = DATA   / "loot_table/blocks"
    lang_file  = ASSETS / "lang/en_us.json"

    for d in (tex_dir, model_b, model_i, bstate_dir, loot_dir):
        d.mkdir(parents=True, exist_ok=True)

    for res, cfg in RESOURCES.items():
        name = f"{res}_pebbles"

        # ── Texture ──
        png = make_pebble_texture(cfg["bg"], cfg["frag_rects"], cfg["fragments"])
        (tex_dir / f"{name}.png").write_bytes(png)
        print(f"  texture  {name}.png  ({len(png)} bytes)")

        # ── Block model ──
        (model_b / f"{name}.json").write_text(
            json.dumps(make_block_model(res, cfg["elements"]), indent=2), encoding="utf-8")
        print(f"  model    block/{name}.json  ({len(cfg['elements'])} elements)")

        # ── Item model ──
        (model_i / f"{name}.json").write_text(
            json.dumps(make_item_model(res), indent=2), encoding="utf-8")

        # ── Blockstate ──
        (bstate_dir / f"{name}.json").write_text(
            json.dumps(make_blockstate(res), indent=2), encoding="utf-8")

        # ── Loot table ──
        (loot_dir / f"{name}.json").write_text(
            json.dumps(make_loot_table(res), indent=2), encoding="utf-8")

    # ── Lang ──
    try:
        lang = json.loads(lang_file.read_text(encoding="utf-8"))
    except FileNotFoundError:
        lang = {}

    for res, cfg in RESOURCES.items():
        lang[f"block.cci_radar.{res}_pebbles"] = cfg["label"]

    # Write with stable key ordering: block.* first, then the rest
    ordered = {k: v for k, v in lang.items() if k.startswith("block.")}
    ordered.update({k: v for k, v in lang.items() if not k.startswith("block.")})
    lang_file.write_text(json.dumps(ordered, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"  lang     en_us.json  ({len(RESOURCES)} block entries)")

    print(f"\nGenerated assets for {len(RESOURCES)} resource(s): {', '.join(RESOURCES)}")


if __name__ == "__main__":
    main()

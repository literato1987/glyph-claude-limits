#!/usr/bin/env python3
"""Render Claude icon + progress ring preview (matches MatrixProgressRing.kt)."""

from __future__ import annotations

import math
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SIMULATOR = ROOT.parent / "glyph-simulator"
sys.path.insert(0, str(SIMULATOR))

from led_mask import SIZE, is_led  # noqa: E402
from preview import render_pearls  # noqa: E402

CENTER = 12.0
RING_INNER = 9.2
RING_OUTER = 11.3
ICON_SIZE = 11

ICON_PATH = ROOT / "app/src/main/res/drawable/claude_icon.png"
DEFAULT_OUTPUT = ROOT / "docs/preview-ring.png"


def ring_leds() -> list[tuple[int, int]]:
    leds: list[tuple[float, int, int]] = []
    for y in range(SIZE):
        for x in range(SIZE):
            if not is_led(x, y):
                continue
            dx = x - CENTER
            dy = y - CENTER
            dist = math.hypot(dx, dy)
            if not (RING_INNER <= dist <= RING_OUTER):
                continue
            angle = (math.atan2(dy, dx) + math.pi / 2 + 2 * math.pi) % (2 * math.pi)
            leds.append((angle, x, y))
    leds.sort(key=lambda item: item[0])
    return [(x, y) for _, x, y in leds]


def stamp_icon(grid: list[list[bool]], icon_path: Path) -> None:
    icon = Image.open(icon_path).convert("L").resize((ICON_SIZE, ICON_SIZE), Image.NEAREST)
    left = (SIZE - ICON_SIZE) // 2
    top = (SIZE - ICON_SIZE) // 2
    for iy in range(ICON_SIZE):
        for ix in range(ICON_SIZE):
            if icon.getpixel((ix, iy)) <= 128:
                continue
            x = left + ix
            y = top + iy
            if is_led(x, y):
                grid[y][x] = True


def build_progress_grid(used_percentage: int) -> list[list[bool]]:
    grid = [[False] * SIZE for _ in range(SIZE)]
    ring = ring_leds()
    progress = max(0, min(used_percentage, 100)) / 100.0
    lit_count = int(len(ring) * progress)
    for x, y in ring[:lit_count]:
        grid[y][x] = True
    stamp_icon(grid, ICON_PATH)
    return grid


def main() -> None:
    import argparse

    parser = argparse.ArgumentParser(description="Claude Glyph Limits ring preview")
    parser.add_argument("-o", "--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--percent", type=int, default=50, help="Ring fill 0-100")
    parser.add_argument("--scale", type=int, default=24)
    args = parser.parse_args()

    image = render_pearls(
        build_progress_grid(args.percent),
        scale=args.scale,
        show_all_leds=False,
        crop_to_leds=True,
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    image.save(args.output)
    print(f"Wrote {args.output.resolve()} ({args.percent}% ring)")


if __name__ == "__main__":
    main()
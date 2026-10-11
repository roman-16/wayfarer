import json

import png
from blocks import noise

GREAT_WIDTH = 256
GREAT_HEIGHT = 128
GREAT = [
    {"size": (24, 16, 24), "uv": (0, 0)},
    {"size": (20, 16, 20), "uv": (96, 0)},
    {"size": (18, 16, 18), "uv": (176, 0)},
    {"size": (16, 16, 16), "uv": (0, 40)},
    {"size": (16, 20, 16), "uv": (64, 40)},
]
GREAT_SAND = [(196, 170, 118), (182, 154, 104), (166, 140, 92), (150, 124, 80), (130, 106, 68)]
GREAT_RING = (98, 78, 50)
GREAT_EYE = [(107, 82, 168), (185, 163, 240), (230, 220, 255)]
GREAT_PUPIL = (24, 18, 34)
GREAT_MAW = (52, 30, 24)
GREAT_PERIOD = 3
GREAT_SWAY = 6


def great_bones():
    result = [{"name": "root", "pivot": [0, 0, 0]}]
    y = 0
    for index, segment in enumerate(GREAT):
        w, h, d = segment["size"]
        name = "head" if index == len(GREAT) - 1 else f"segment{index}"
        parent = "root" if index == 0 else ("segment" + str(index - 1))
        result.append(
            {
                "name": name,
                "parent": parent,
                "pivot": [0, y, 0],
                "cubes": [{"origin": [-w / 2, y, -d / 2], "size": [w, h, d], "uv": list(segment["uv"])}],
            }
        )
        y += h
    return result


def great_geometry():
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": "geometry.great_worm",
                    "texture_width": GREAT_WIDTH,
                    "texture_height": GREAT_HEIGHT,
                    "visible_bounds_width": 4,
                    "visible_bounds_height": 6,
                    "visible_bounds_offset": [0, 2.5, 0],
                },
                "bones": great_bones(),
            }
        ],
    }


def great_animation():
    speed = 360 / GREAT_PERIOD
    sway = {}
    for index in range(1, len(GREAT)):
        name = "head" if index == len(GREAT) - 1 else f"segment{index}"
        phase = index * 40
        sway[name] = {
            "rotation": [
                f"math.sin(query.anim_time * {speed:g} - {phase}) * {GREAT_SWAY}",
                "0",
                f"math.cos(query.anim_time * {speed:g} - {phase}) * {GREAT_SWAY / 2:g}",
            ]
        }
    return {
        "format_version": "1.8.0",
        "animations": {"animation.great_worm.sway": {"loop": True, "animation_length": GREAT_PERIOD, "bones": sway}},
    }


def great_texture():
    pixels = [[(0, 0, 0, 0) for _ in range(GREAT_WIDTH)] for _ in range(GREAT_HEIGHT)]
    for index, segment in enumerate(GREAT):
        w, h, d = segment["size"]
        u, v = segment["uv"]
        for y in range(v, v + d + h):
            for x in range(u, u + 2 * (d + w)):
                shade = GREAT_SAND[int(noise("great_worm", index, x, y) * len(GREAT_SAND))]
                if y >= v + d and (y - v - d) in (0, 1, h - 1):
                    shade = GREAT_RING
                pixels[y][x] = (*shade, 255)
        if index == len(GREAT) - 1:
            for y in range(v, v + d):
                for x in range(u + d, u + d + w):
                    edge = min(x - u - d, u + d + w - 1 - x, y - v, v + d - 1 - y)
                    pixels[y][x] = (*(GREAT_MAW if edge >= 3 else GREAT_RING), 255)
            cx = u + d + w / 2 - 0.5
            cy = v + d + h / 2 - 2
            for y in range(v + d, v + d + h):
                for x in range(u + d, u + d + w):
                    dx = (x - cx) / 5.5
                    dy = (y - cy) / 4
                    inside = dx * dx + dy * dy
                    if inside <= 1:
                        ring = int(min(2, inside * 3))
                        pixels[y][x] = (*GREAT_EYE[2 - ring], 255)
                        if abs(x - cx) <= 1 and abs(y - cy) <= 2.5:
                            pixels[y][x] = (*GREAT_PUPIL, 255)
    return pixels


DEEP = {
    "bone": ["#3e3a33", "#6e675a", "#9c9483"],
    "deep": ["#05070d", "#0d1320", "#182235", "#2b3a55"],
    "eye": ["#6b52a8", "#b9a3f0", "#e6dcff"],
    "eye_box": [8, 0, 14, 3],
    "spike_hue": [15, 50],
    "spike_saturation": 0.35,
}


def deep(root):
    path = root / "wayfarer/deep.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(DEEP, indent=2, sort_keys=True) + "\n")


def great_worm(root):
    assets = root / "assets/wayfarer"
    for path, content in (
        (assets / "geo/entity/great_worm.geo.json", great_geometry()),
        (assets / "animations/entity/great_worm.animation.json", great_animation()),
    ):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(content, indent=2) + "\n")
    png.write(assets / "textures/entity/great_worm.png", great_texture())

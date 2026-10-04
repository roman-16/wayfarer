import json

import png
from blocks import noise

WIDTH = 64
HEIGHT = 32
SEGMENTS = [
    {"size": (8, 8, 6), "uv": (0, 0)},
    {"size": (7, 7, 6), "uv": (28, 0)},
    {"size": (6, 6, 5), "uv": (0, 14)},
    {"size": (5, 5, 5), "uv": (22, 14)},
    {"size": (4, 4, 4), "uv": (42, 14)},
]
SAND = [(219, 207, 163), (205, 190, 140), (190, 172, 120), (168, 150, 100)]
RING = (140, 120, 80)
MOUTH = (60, 40, 30)
PERIOD = 2
SWAY = 14
RISE = 0.6


def bones():
    result = [{"name": "root", "pivot": [0, 0, 0]}]
    z = -sum(segment["size"][2] for segment in SEGMENTS) / 2
    for index, segment in enumerate(SEGMENTS):
        w, h, d = segment["size"]
        result.append(
            {
                "name": f"segment{index}",
                "parent": "root" if index == 0 else f"segment{index - 1}",
                "pivot": [0, h / 2, z],
                "cubes": [{"origin": [-w / 2, 0, z], "size": [w, h, d], "uv": list(segment["uv"])}],
            }
        )
        z += d
    return result


def geometry():
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": "geometry.worm",
                    "texture_width": WIDTH,
                    "texture_height": HEIGHT,
                    "visible_bounds_width": 3,
                    "visible_bounds_height": 2,
                    "visible_bounds_offset": [0, 0.5, 0],
                },
                "bones": bones(),
            }
        ],
    }


def animation():
    speed = 360 / PERIOD
    crawl = {}
    for index in range(1, len(SEGMENTS)):
        phase = index * 45
        crawl[f"segment{index}"] = {
            "rotation": ["0", f"math.sin(query.anim_time * {speed:g} - {phase}) * {SWAY}", "0"],
            "position": ["0", f"math.sin(query.anim_time * {speed:g} * 2 - {phase}) * {RISE}", "0"],
        }
    return {
        "format_version": "1.8.0",
        "animations": {"animation.worm.crawl": {"loop": True, "animation_length": PERIOD, "bones": crawl}},
    }


def texture():
    pixels = [[(0, 0, 0, 0) for _ in range(WIDTH)] for _ in range(HEIGHT)]
    for index, segment in enumerate(SEGMENTS):
        w, h, d = segment["size"]
        u, v = segment["uv"]
        for y in range(v, v + d + h):
            for x in range(u, u + 2 * (d + w)):
                shade = SAND[int(noise("worm", index, x, y) * len(SAND))]
                if y >= v + d and (y - v - d) in (0, h - 1):
                    shade = RING
                pixels[y][x] = (*shade, 255)
        if index == 0:
            for y in range(v + d + 2, v + d + h - 2):
                for x in range(u + d + 2, u + d + w - 2):
                    pixels[y][x] = (*MOUTH, 255)
    return pixels


def creatures(root):
    assets = root / "assets/wayfarer"
    for path, content in (
        (assets / "geo/entity/worm.geo.json", geometry()),
        (assets / "animations/entity/worm.animation.json", animation()),
    ):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(content, indent=2) + "\n")
    png.write(assets / "textures/entity/worm.png", texture())

from blocks import coherent

SIZE = 64
DARK = 0.24
LIGHT = 0.78


def boxes(arm):
    return {
        "head": (0, 0, 8, 8, 8),
        "hat": (32, 0, 8, 8, 8),
        "body": (16, 16, 8, 12, 4),
        "jacket": (16, 32, 8, 12, 4),
        "right_arm": (40, 16, arm, 12, 4),
        "right_sleeve": (40, 32, arm, 12, 4),
        "left_arm": (32, 48, arm, 12, 4),
        "left_sleeve": (48, 48, arm, 12, 4),
        "right_leg": (0, 16, 4, 12, 4),
        "right_pants": (0, 32, 4, 12, 4),
        "left_leg": (16, 48, 4, 12, 4),
        "left_pants": (0, 48, 4, 12, 4),
    }


GRIDS = {
    "head": "head",
    "hat": "hat",
    "body": "body",
    "jacket": "jacket",
    "right_arm": "arm",
    "left_arm": "arm",
    "right_sleeve": "sleeve",
    "left_sleeve": "sleeve",
    "right_leg": "leg",
    "left_leg": "leg",
    "right_pants": "pants",
    "left_pants": "pants",
}


def faces(u, v, w, h, d):
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


def rgba(color):
    return tuple(int(color[i : i + 2], 16) for i in (1, 3, 5)) + (255,)


def grid(spec, name, face):
    side = "side" if face in ("left", "right") else face
    keys = (f"{name}_{face}",) if face in ("top", "bottom") else (f"{name}_{face}", f"{name}_{side}", name)
    for key in keys:
        if key in spec:
            rows = spec[key]
            return [row[::-1] for row in rows] if face == "left" and key == f"{name}_side" else rows
    return None


def edge(spec, name, face, w, d):
    front = grid(spec, name, "front")
    back = grid(spec, name, "back")
    if front is None or back is None:
        return None
    row = 0 if face == "top" else -1
    return [(front if r >= d // 2 else back)[row][:w] for r in range(d)]


def shade(name, letter, value, x, y):
    if isinstance(value, str):
        return rgba(value)
    dark, mid, light = value
    noise = coherent(f"skin/{name}/{letter}", (x, y, 0))
    return rgba(dark if noise < DARK else light if noise > LIGHT else mid)


def skin(name, spec):
    arm = 3 if spec["model"] == "humanoid_slim" else 4
    palette = spec["palette"]
    pixels = [[(0, 0, 0, 0)] * SIZE for _ in range(SIZE)]
    for part, (u, v, w, h, d) in boxes(arm).items():
        for face, (x0, y0, fw, fh) in faces(u, v, w, h, d).items():
            kind = GRIDS[part]
            rows = grid(spec, kind, face)
            if rows is None and face in ("top", "bottom"):
                rows = edge(spec, kind, face, fw, fh)
            if rows is None:
                continue
            if len(rows) != fh or any(len(row) != fw for row in rows):
                raise ValueError(f"skin {name}: {kind} {face} must be {fw} by {fh}, is {len(rows[0])} by {len(rows)}")
            for y, row in enumerate(rows):
                for x, letter in enumerate(row):
                    if letter == ".":
                        continue
                    if letter not in palette:
                        raise ValueError(f"skin {name}: {letter!r} in {kind} {face} has no colour")
                    pixels[y0 + y][x0 + x] = shade(name, letter, palette[letter], x0 + x, y0 + y)
    return pixels

import math

import architecture as arch
import detail
from blocks import AIR, Material, Room, Template, bears, coherent, hanging, lantern, noise
from halls import calling_stone

SIZE = [48, 40, 120]
TOP = 36
LEVEL_B = 11
HALL_FLOOR = 4
SET_EVERY = 4
ORES = ["minecraft:coal_ore", "minecraft:copper_ore", "minecraft:deepslate_iron_ore", "minecraft:iron_ore"]
WORKED_FROM = 89
CHASM = (76, 84)
SIGN = "wayfarer:wayfarer_sign"
WALLS = {"east": (-1, 0), "north": (0, 1), "south": (0, -1), "west": (1, 0)}

ROCK = Material(
    "diggers/rock",
    [
        ("minecraft:stone", 30),
        ("minecraft:andesite", 15),
        ("minecraft:tuff", 15),
        ("minecraft:deepslate", 15),
        ("minecraft:cobblestone", 10),
        ("minecraft:cobbled_deepslate", 10),
        ("minecraft:mossy_cobblestone", 5),
    ],
    "minecraft:cobblestone_stairs",
    "minecraft:cobblestone_slab",
    fade=5,
)
FLOOR = Material(
    "diggers/floor",
    [
        ("minecraft:cobbled_deepslate", 30),
        ("minecraft:gravel", 15),
        ("minecraft:tuff", 15),
        ("minecraft:cobblestone", 15),
        ("minecraft:coarse_dirt", 15),
        ("minecraft:andesite", 10),
    ],
    "minecraft:cobbled_deepslate_stairs",
    "minecraft:cobbled_deepslate_slab",
)
WORKED = Material(
    "diggers/worked",
    [
        ("minecraft:deepslate_bricks", 40),
        ("minecraft:deepslate_tiles", 20),
        ("minecraft:cracked_deepslate_bricks", 15),
        ("minecraft:polished_deepslate", 15),
        ("minecraft:cobbled_deepslate", 10),
    ],
    "minecraft:deepslate_brick_stairs",
    "minecraft:deepslate_brick_slab",
    fade=4,
)
PAVING = Material(
    "diggers/paving",
    [
        ("minecraft:polished_deepslate", 50),
        ("minecraft:deepslate_tiles", 30),
        ("minecraft:cracked_deepslate_tiles", 20),
    ],
)
POSTS = ["minecraft:spruce_log", "minecraft:stripped_dark_oak_log", "minecraft:stripped_spruce_log"]


def shell_at(pos):
    if pos[2] >= WORKED_FROM:
        return WORKED
    if noise("diggers/ore", *pos) < 0.035:
        return ORES[int(noise("diggers/ore-kind", *pos) * len(ORES))]
    return ROCK


def across(x, z, axis):
    return [(x + d, z) for d in (-1, 0, 1)] if axis == "z" else [(x, z + d) for d in (-1, 0, 1)]


def post(room, pos):
    room.set(pos, POSTS[int(noise("diggers/post", *pos) * len(POSTS))], axis="y")


def timber(room, x, floor, z, axis, seed, height=3):
    left, middle, right = across(x, z, axis)
    broken = noise(seed, x, z) < 0.25
    light = noise(seed + "/kind", x, z) < 0.4
    beam_axis = "x" if axis == "z" else "z"
    fence = {"east": False, "north": False, "south": False, "west": False}
    for side in (right,) if broken else (left, right):
        for y in range(floor + 1, floor + height):
            if light:
                room.set((side[0], y, side[1]), "minecraft:spruce_fence", waterlogged=False, **fence)
            else:
                post(room, (side[0], y, side[1]))
    if broken:
        room.set((left[0], floor + 1, left[1]), "minecraft:spruce_log", axis=axis)
        return
    for cell in (left, middle, right):
        if light:
            room.set((cell[0], floor + height, cell[1]), "minecraft:spruce_planks")
        else:
            room.set((cell[0], floor + height, cell[1]), "minecraft:spruce_log", axis=beam_axis)


def drift(room, cells, walk, rails=False, seed="drift"):
    for index, (x, floor, z, axis) in enumerate(cells):
        for cx, cz in across(x, z, axis):
            room.set((cx, floor, cz), FLOOR)
            room.carve((cx, floor + 1, cz), (cx, floor + 3, cz))
        walk[(x, z)] = floor
        if index % SET_EVERY == SET_EVERY // 2:
            timber(room, x, floor, z, axis, seed)
        elif rails and noise(seed + "/rail", x, z) > 0.2:
            shape = "north_south" if axis == "z" else "east_west"
            room.set((x, floor + 1, z), "minecraft:rail", shape=shape, waterlogged=False)


def line(x0, z0, x1, z1, floor):
    if x0 == x1:
        step = 1 if z1 >= z0 else -1
        return [(x0, floor, z, "z") for z in range(z0, z1 + step, step)]
    step = 1 if x1 >= x0 else -1
    return [(x, floor, z0, "x") for x in range(x0, x1 + step, step)]


def decline(room, x, z0, top, drop_every, cells, walk, worked=False):
    floor = top
    for index in range(cells):
        z = z0 + index
        lower = top - (index + 1) // drop_every if drop_every > 1 else top - index
        stepped = index > 0 and lower < floor
        for cx, _ in across(x, z, "z"):
            room.set((cx, lower, z), WORKED if worked else FLOOR)
            room.carve((cx, lower + 1, z), (cx, lower + 4, z))
            if stepped:
                stair = "minecraft:polished_deepslate_stairs" if worked else "minecraft:cobblestone_stairs"
                room.set((cx, lower + 1, z), stair, facing="north", half="bottom", shape="straight", waterlogged=False)
        walk[(x, z)] = lower
        if not worked and not stepped and index % SET_EVERY == SET_EVERY // 2:
            timber(room, x, lower, z, "z", "diggers/decline", height=4)
        floor = lower
    return floor


def winze(room, x0, z0, top, bottom):
    for x in range(x0, x0 + 3):
        for z in range(z0, z0 + 3):
            room.set((x, bottom, z), FLOOR)
            room.carve((x, bottom + 1, z), (x, top + 3, z))
    rotted = {top - 4, top - 3, bottom + 4, bottom + 5}
    for y in range(bottom + 1, top + 3):
        if y not in rotted and noise("diggers/rung", y) > 0.12:
            room.set((x0, y, z0), "minecraft:ladder", facing="south", waterlogged=False)
    landing = (top + bottom) // 2
    for x in range(x0 + 1, x0 + 3):
        for z in range(z0, z0 + 3):
            if (x, z) != (x0 + 1, z0 + 1) and noise("diggers/landing", x, z) > 0.25:
                room.set((x, landing, z), "minecraft:spruce_slab", type="top", waterlogged=False)
    for corner in ((x0 + 2, z0), (x0 + 2, z0 + 2)):
        for y in range(landing - 2, landing):
            post(room, (corner[0], y, corner[1]))


def sump(room, x, z0, walk):
    profile = [10, 8, 6, 6, 6, 6, 6, 8, 10]
    for index, floor in enumerate(profile):
        z = z0 + index
        drowned = floor == min(profile)
        for cx, cz in across(x, z, "z"):
            room.set((cx, floor, cz), FLOOR)
            for y in range(floor + 1, LEVEL_B + 4):
                if drowned and y > LEVEL_B - 1:
                    room.set((cx, y, cz), ROCK)
                elif y <= LEVEL_B:
                    room.water((cx, y, cz))
                else:
                    room.air((cx, y, cz))
        walk[(x, z)] = floor


def collapse(room, x, z0, length):
    for index in range(length):
        z = z0 + index
        height = 3 if 0 < index < length - 1 else 2
        for cx, cz in across(x, z, "z"):
            for y in range(LEVEL_B + 1, LEVEL_B + 1 + height):
                roll = noise("diggers/collapse", cx, y, cz)
                block = "minecraft:gravel" if roll < 0.45 else "minecraft:cobblestone" if roll < 0.75 else ROCK
                room.set((cx, y, cz), block)
    for z in (z0 + 1, z0 + 3):
        room.set((x, LEVEL_B + 3, z), "minecraft:spruce_log", axis="x")


def crawl(room, cells, walk):
    for x, z in cells:
        room.set((x, LEVEL_B, z), FLOOR)
        room.carve((x, LEVEL_B + 1, z), (x, LEVEL_B + 2, z))
        walk[(x, z)] = LEVEL_B


def chasm(room):
    low, high = CHASM
    for x in range(2, SIZE[0] - 2):
        centre = (low + high) / 2 + 1.6 * math.sin(x / 6.5)
        for y in range(1, 31):
            half = 1.2 + 3.2 * math.sin(math.pi * y / 31) + (coherent("diggers/chasm", (x, y, 0)) - 0.5) * 1.6
            for z in range(low - 2, high + 3):
                if abs(z - centre) <= half:
                    room.air((x, y, z))
        for z in range(low - 2, high + 3):
            if abs(z - centre) <= 1.6:
                room.set((x, 0, z), "minecraft:gravel" if noise("diggers/chasm-floor", x, z) < 0.6 else ROCK)
                if noise("diggers/pool", x // 4, z) < 0.35:
                    room.water((x, 1, z))


def bridge(room, x, walk):
    low, high = CHASM
    for z in range(low - 3, high + 4):
        if room.get((x, LEVEL_B, z)) != AIR:
            continue
        for side in (x - 1, x + 1):
            room.set((side, LEVEL_B, z), "minecraft:spruce_log", axis="z")
        if noise("diggers/plank", z) > 0.22:
            room.set((x, LEVEL_B, z), "minecraft:spruce_slab", type="top", waterlogged=False)
        if noise("diggers/bridge-rail", z) > 0.45:
            fence = {"east": False, "north": False, "south": False, "west": False}
            room.set((x - 1, LEVEL_B + 1, z), "minecraft:spruce_fence", waterlogged=False, **fence)
        walk[(x, z)] = LEVEL_B


def mark(room, pos):
    if pos not in MARKS:
        MARKS.append(pos)
    if room.get(pos) != AIR:
        raise ValueError(f"diggers: the sign at {pos} has no free spot")
    for facing, (dx, dz) in WALLS.items():
        wall = (pos[0] + dx, pos[1], pos[2] + dz)
        placed = room.t.blocks.get(wall)
        shell = placed is None and wall not in room.open
        if room.inside(wall) and (shell or placed is not None and bears(placed[0], dict(placed[1]))):
            room.set(pos, SIGN, facing=facing)
            return
    raise ValueError(f"diggers: the sign at {pos} has no wall")


def hall(room, spec, walk):
    cx, cz = 10, 105
    arch.cave(room, cx, cz, 8.5, 8.5, HALL_FLOOR + 1, 4, 4, jitter=1.0, seed="diggers/hall")
    for x in range(room.size[0]):
        for z in range(96, room.size[2]):
            if (x, HALL_FLOOR + 1, z) in room.open:
                room.set((x, HALL_FLOOR, z), PAVING)
    for x in (8, 12):
        room.fill((x, HALL_FLOOR + 1, 96), (x, HALL_FLOOR + 4, 96), "minecraft:chiseled_deepslate")
    room.fill((8, HALL_FLOOR + 5, 96), (12, HALL_FLOOR + 5, 96), "minecraft:polished_deepslate")
    wall = {"east": "none", "north": "none", "south": "none", "west": "none"}
    for z in range(99, 112, 4):
        for x in (4, 16):
            if (x, HALL_FLOOR + 1, z) in room.open:
                room.fill(
                    (x, HALL_FLOOR + 1, z),
                    (x, HALL_FLOOR + 4, z),
                    "minecraft:deepslate_brick_wall",
                    up=True,
                    waterlogged=False,
                    **wall,
                )
        for x in range(4, 17):
            if (x, HALL_FLOOR + 5, z) in room.open:
                room.set((x, HALL_FLOOR + 5, z), "minecraft:stripped_spruce_log", axis="x")
        for x in (7, 13):
            if room.get((x, HALL_FLOOR + 5, z)) == "minecraft:stripped_spruce_log":
                lantern(room, (x, HALL_FLOOR + 4, z), hanging=True)
    for x in range(7, 14):
        room.set(
            (x, HALL_FLOOR + 1, 109),
            "minecraft:polished_deepslate_stairs",
            facing="south",
            half="bottom",
            shape="straight",
            waterlogged=False,
        )
        for z in range(110, 114):
            room.set((x, HALL_FLOOR + 1, z), "minecraft:polished_deepslate")
    for x in range(6, 15):
        for y in range(HALL_FLOOR + 1, HALL_FLOOR + 8):
            room.set((x, y, 114), "minecraft:chiseled_deepslate" if (x + y) % 4 == 0 else WORKED)
    calling_stone(room, "lost", spec, (cx, HALL_FLOOR + 2, 112), (cx - 2, HALL_FLOOR + 2, 111), "east")
    for side in (cx - 3, cx + 3):
        room.fill((side, HALL_FLOOR + 2, 112), (side, HALL_FLOOR + 3, 112), "minecraft:stripped_spruce_log", axis="y")
        lantern(room, (side, HALL_FLOOR + 4, 112), hanging=False)
    mark(room, (cx + 2, HALL_FLOOR + 3, 113))
    for z in range(97, 109):
        if noise("diggers/hall-rail", z) > 0.15:
            room.set((cx, HALL_FLOOR + 1, z), "minecraft:rail", shape="north_south", waterlogged=False)
        walk[(cx, z)] = HALL_FLOOR
    for x, z in ((3, 101), (17, 103), (3, 107)):
        if (x, HALL_FLOOR + 1, z) in room.open:
            room.set((x, HALL_FLOOR + 1, z), "minecraft:barrel", facing="up", open=False)
    hanging(room, cx, 106, room.top(cx, 106, HALL_FLOOR + 6), 2)


MARKS = []


def layout():
    width, depth = ENTRANCE
    return {
        "entrance": {
            "centre": [width // 2, GROUND, depth // 2],
            "join": [width // 2, 0, depth],
            "mouth": [width // 2, GROUND + 1, MOUTH],
        },
        "tunnels": {
            "hall": {"from": [0, HALL_FLOOR - 1, 94], "to": [SIZE[0] // 2 - 4, HALL_FLOOR + 9, SIZE[2] - 1]},
            "join": [24, TOP, 0],
            "marks": [list(pos) for pos in MARKS],
            "stone": [10, HALL_FLOOR + 2, 112],
        },
    }


def tunnels(spec):
    room = Room(SIZE, ROCK, FLOOR, shell_at=shell_at, decay=0.1)
    walk = {}
    x = 24
    level_a = decline(room, x, 0, TOP, 2, 20, walk)
    drift(room, line(x, 20, x, 46, level_a), walk, rails=True, seed="diggers/haulage")
    drift(room, line(x - 2, 28, 6, 28, level_a), walk, seed="diggers/dead-end")
    for dz in (-1, 0, 1):
        for y in range(level_a + 1, level_a + 3):
            room.set(
                (6, y, 28 + dz), "minecraft:gravel" if noise("diggers/face", y, dz) < 0.5 else "minecraft:iron_ore"
            )
    room.set((8, level_a + 1, 27), "minecraft:bone_block", axis="z")
    room.set((8, level_a + 1, 28), "minecraft:skeleton_skull", rotation=4)
    drift(room, line(x + 2, 44, 39, 44, level_a), walk, seed="diggers/east")
    winze(room, 40, 43, level_a, LEVEL_B)
    drift(room, line(x - 2, 44, 10, 44, level_a), walk, seed="diggers/west")
    decline(room, 8, 45, level_a, 1, level_a - LEVEL_B + 1, walk)
    drift(room, line(8, 45 + level_a - LEVEL_B, 8, 62, LEVEL_B), walk, seed="diggers/stair-foot")
    drift(room, line(41, 46, 41, 49, LEVEL_B), walk, seed="diggers/b1")
    sump(room, 41, 50, walk)
    drift(room, line(41, 59, 41, 62, LEVEL_B), walk, seed="diggers/b1-after")
    drift(room, line(39, 62, 10, 62, LEVEL_B), walk, rails=True, seed="diggers/b2")
    drift(room, line(10, 63, 10, 80, LEVEL_B), walk, seed="diggers/b3")
    collapse(room, 10, 65, 5)
    crawl(room, [(12, 64), (13, 64), (14, 64)] + [(14, z) for z in range(65, 71)] + [(13, 71), (12, 71)], walk)
    drift(room, line(10, 82, 10, 88, LEVEL_B), walk, seed="diggers/far")
    chasm(room)
    bridge(room, 10, walk)
    decline(room, 10, 89, LEVEL_B, 1, LEVEL_B - HALL_FLOOR + 1, walk, worked=True)
    hall(room, spec, walk)
    for cx, cz in walk:
        room.reserve((cx, cz), (cx, cz))
    for pos in (
        (x - 1, level_a + 2, 21),
        (x + 1, level_a + 2, 31),
        (x + 3, level_a + 2, 45),
        (39, level_a + 2, 43),
        (13, LEVEL_B + 2, 64),
        (9, LEVEL_B + 2, 75),
        (11, LEVEL_B + 2, 87),
    ):
        mark(room, pos)
    detail.rubble(room, ROCK, 0.18, seed="diggers/rubble")
    detail.webs(room, 0.35, seed="diggers/webs")
    detail.roots(room, 0.05, seed="diggers/roots")
    detail.moss(room, 0.12, seed="diggers/moss")
    headroom(room, walk)
    return room


PASSABLE = {
    AIR,
    "minecraft:cobweb",
    "minecraft:hanging_roots",
    "minecraft:ladder",
    "minecraft:moss_carpet",
    "minecraft:rail",
    "minecraft:water",
    "wayfarer:wayfarer_sign",
}
STANDING_HEIGHT = 1.8


def standing(room, cell, floor):
    x, z = cell
    on = room.get((x, floor + 1, z)) or ""
    surface = floor + 1.5 if on.endswith("_stairs") or on.endswith("_slab") else floor + 1
    y = floor + 2 if surface > floor + 1 else floor + 1
    while y < SIZE[1] and room.get((x, y, z)) in PASSABLE:
        y += 1
    return surface, y


def headroom(room, walk):
    for (x, z), floor in walk.items():
        if room.get((x, floor, z)) in PASSABLE or room.get((x, floor + 1, z)) == "minecraft:water":
            continue
        here, over = standing(room, (x, z), floor)
        for nx, nz in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
            if (nx, nz) not in walk or room.get((nx, walk[(nx, nz)], nz)) in PASSABLE:
                continue
            there, above = standing(room, (nx, nz), walk[(nx, nz)])
            room_left = min(over, above) - max(here, there)
            if 0 < room_left < STANDING_HEIGHT:
                raise ValueError(f"diggers: the way from {(x, z)} to {(nx, nz)} has {room_left} blocks of headroom")


SOIL = Material(
    "diggers/soil",
    [("minecraft:dirt", 70), ("minecraft:coarse_dirt", 15), ("minecraft:rooted_dirt", 15)],
)
TURF = Material(
    "diggers/turf",
    [("minecraft:grass_block", 60), ("minecraft:podzol", 25), ("minecraft:coarse_dirt", 15)],
)
CRAG = Material(
    "diggers/crag",
    [("minecraft:mossy_cobblestone", 40), ("minecraft:cobblestone", 30), ("minecraft:stone", 30)],
)
SPOIL = ["minecraft:cobblestone", "minecraft:gravel", "minecraft:tuff", "minecraft:andesite"]
GROWTH = ["minecraft:fern", "minecraft:short_grass", "minecraft:short_grass", "minecraft:large_fern"]
ENTRANCE = (13, 14)
MOUTH = 4
GROUND = 4


def entrance():
    t = Template()
    width, depth = ENTRANCE
    c = width // 2
    cut = {}
    for z in range(MOUTH + 1, depth):
        cut[z] = GROUND - (z - MOUTH) // 2
    heights = {}
    for x in range(width):
        for z in range(depth):
            reach = math.hypot((x - c) / 6.2, (z - 9.5) / 5.2) + (coherent("diggers/mound", (x, 0, z)) - 0.5) * 0.4
            heights[(x, z)] = GROUND + max(0, round(6.5 * (1 - reach**1.6)))
    for x in range(width):
        for z in range(depth):
            top = heights[(x, z)]
            for y in range(top + 1):
                if y < GROUND - 1:
                    block = ROCK
                elif y == top:
                    block = CRAG if top > GROUND and coherent("diggers/crag", (x, y, z)) > 0.72 else TURF
                else:
                    block = CRAG if coherent("diggers/crag", (x, y, z)) > 0.8 else SOIL
                t.set((x, y, z), block)
    for z, floor in cut.items():
        stepped = floor < cut.get(z - 1, GROUND)
        for x in range(c - 1, c + 2):
            t.set((x, floor, z), FLOOR)
            for y in range(floor + 1, floor + 4):
                t.set((x, y, z), AIR)
            if stepped:
                stair = {"facing": "north", "half": "bottom", "shape": "straight", "waterlogged": False}
                t.set((x, floor + 1, z), "minecraft:cobblestone_stairs", **stair)
    for x in range(c - 1, c + 2):
        for y in range(GROUND + 1, GROUND + 4):
            t.set((x, y, MOUTH), AIR)
    for x in (c - 2, c + 2):
        for y in range(GROUND + 1, GROUND + 4):
            t.set((x, y, MOUTH), "minecraft:stripped_spruce_log", axis="y")
    for x in range(c - 3, c + 4):
        t.set((x, GROUND + 4, MOUTH), "minecraft:spruce_log", axis="x")
    for x in (c - 2, c + 2):
        t.set((x, GROUND + 5, MOUTH), "minecraft:spruce_log", axis="y")
    for x in range(c - 1, c + 2):
        t.set((x, GROUND + 5, MOUTH), SOIL)
    for z in range(0, MOUTH + 2):
        if noise("diggers/out-rail", z) > 0.25:
            t.set((c, GROUND + 1, z), "minecraft:rail", shape="north_south", waterlogged=False)
    t.set((c, GROUND + 1, 0), "minecraft:spruce_log", axis="x")
    for x in range(c + 3, width):
        for z in range(0, MOUTH + 1):
            rise = round(2.2 - math.hypot(x - (c + 4.5), z - 1.5) * 0.9 + (noise("diggers/heap", x, z) - 0.5))
            for y in range(GROUND + 1, GROUND + 1 + max(0, rise)):
                t.set((x, y, z), SPOIL[int(noise("diggers/spoil", x, y, z) * len(SPOIL))])
    birch(t, (c + 2, 10), heights[(c + 2, 10)] + 1)
    growth(t, width, depth)
    return t


def birch(t, trunk, base):
    for y in range(base, base + 5):
        t.set((trunk[0], y, trunk[1]), "minecraft:birch_log", axis="y")
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            for y in range(base + 3, base + 7):
                spread = 2 if y < base + 5 else 1
                beside = abs(dx) <= spread and abs(dz) <= spread and (dx, dz) != (0, 0)
                cap = y >= base + 5 and (dx, dz) == (0, 0)
                kept = abs(dx) + abs(dz) < 4 and noise("diggers/leaf", dx, y, dz) > 0.15
                if (beside or cap) and kept:
                    leaves = {"distance": 1, "persistent": True, "waterlogged": False}
                    t.set((trunk[0] + dx, y, trunk[1] + dz), "minecraft:birch_leaves", **leaves)


def growth(t, width, depth):
    for x in range(width):
        for z in range(depth):
            top = max(y for (bx, y, bz) in t.blocks if (bx, bz) == (x, z) and t.get((bx, y, bz)) != AIR)
            turf = t.get((x, top, z)) in ("minecraft:grass_block", "minecraft:podzol")
            if not turf or t.get((x, top + 1, z)) is not None or noise("diggers/growth", x, z) >= 0.45:
                continue
            plant = GROWTH[int(noise("diggers/growth-kind", x, z) * len(GROWTH))]
            if plant == "minecraft:large_fern":
                t.set((x, top + 1, z), plant, half="lower")
                t.set((x, top + 2, z), plant, half="upper")
            else:
                t.set((x, top + 1, z), plant)

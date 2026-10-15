import math

from blocks import AIR, Material, Template, coherent, lectern

RUIN = (13, 8, 13)
RUIN_WIDTH, RUIN_HEIGHT, RUIN_LENGTH = RUIN
LEFT, RIGHT = 2, RUIN_WIDTH - 3
FRONT, BACK = 3, RUIN_LENGTH - 2
MIDDLE = RUIN_WIDTH // 2
WALL = 5
SEAL = [(MIDDLE + dx, y, FRONT) for dx in (-1, 0, 1) for y in (1, 2, 3)]
MARKS = [(LEFT, 2, FRONT + 2), (RIGHT, 3, FRONT + 4), (MIDDLE + 2, 1, FRONT)]
NOTE = (MIDDLE, 1, BACK - 2)
STONES = Material(
    "errands/seal/walls",
    [
        ("minecraft:stone_bricks", 40),
        ("minecraft:cracked_stone_bricks", 25),
        ("minecraft:mossy_stone_bricks", 20),
        ("minecraft:andesite", 15),
    ],
)
FLOOR = Material(
    "errands/seal/floor",
    [("minecraft:polished_andesite", 40), ("minecraft:stone_bricks", 35), ("minecraft:gravel", 25)],
)
CAIRN = Material(
    "errands/cairns/stone",
    [
        ("minecraft:cobblestone", 40),
        ("minecraft:mossy_cobblestone", 20),
        ("minecraft:andesite", 25),
        ("minecraft:stone", 15),
    ],
)
NEST = 9
NEST_CENTRE = NEST // 2
NEST_FLOOR = Material(
    "errands/nest/floor",
    [("minecraft:coarse_dirt", 45), ("minecraft:gravel", 35), ("minecraft:bone_block", 20)],
)
CAIRNS = 7
RING = 6
FIELD = RING * 2 + 3
CENTRE = FIELD // 2


def standing(x, z):
    return 0.35 + 0.5 * coherent("errands/seal/height", (x, 0, z))


def ruin(note):
    t = Template()
    for x in range(RUIN_WIDTH):
        for z in range(RUIN_LENGTH):
            inside = LEFT <= x <= RIGHT and FRONT <= z <= BACK
            if inside:
                t.set((x, 0, z), FLOOR)
            edge = inside and (x in (LEFT, RIGHT) or z in (FRONT, BACK))
            if not edge:
                if inside:
                    for y in range(1, WALL + 1):
                        t.set((x, y, z), AIR)
                continue
            top = max(2, round(WALL * standing(x, z)))
            for y in range(1, top + 1):
                t.set((x, y, z), STONES)
            for y in range(top + 1, WALL + 1):
                t.set((x, y, z), AIR)
    for x, y, z in SEAL:
        t.set(
            (x, y, z), "minecraft:chiseled_stone_bricks" if y == 2 and x == MIDDLE else "minecraft:polished_deepslate"
        )
    t.set((MIDDLE, 4, FRONT), "minecraft:deepslate_tile_slab", type="bottom", waterlogged=False)
    for pos in MARKS:
        t.set(pos, "minecraft:chiseled_stone_bricks")
    lectern(t, NOTE, "south", note["id"], note["title"], note["author"], note["pages"])
    for x in range(LEFT + 1, RIGHT):
        for z in range(FRONT + 1, BACK):
            if coherent("errands/seal/rubble", (x, 1, z)) > 0.62 and (x, z) != (NOTE[0], NOTE[2]):
                t.set((x, 1, z), "minecraft:cobblestone_slab", type="bottom", waterlogged=False)
    return t


def cairns():
    t = Template()
    for x in range(FIELD):
        for z in range(FIELD):
            t.set(
                (x, 0, z),
                "minecraft:snow_block" if coherent("errands/cairns/ground", (x, 0, z)) > 0.4 else "minecraft:stone",
            )
    for index in range(CAIRNS):
        angle = 2 * math.pi * index / CAIRNS
        x = CENTRE + round(math.cos(angle) * RING)
        z = CENTRE + round(math.sin(angle) * RING)
        height = 2 + (index % 2)
        for y in range(1, height + 1):
            t.set((x, y, z), CAIRN)
        t.set(
            (x, height + 1, z),
            "minecraft:cobblestone_wall",
            up=True,
            east="none",
            north="none",
            south="none",
            west="none",
            waterlogged=False,
        )
    t.set((CENTRE, 1, CENTRE), "minecraft:chiseled_stone_bricks")
    return t


def nest():
    t = Template()
    for x in range(NEST):
        for z in range(NEST):
            distance = math.hypot(x - NEST_CENTRE, z - NEST_CENTRE)
            if distance > NEST_CENTRE + 0.5:
                continue
            t.set((x, 0, z), NEST_FLOOR)
            rim = distance > NEST_CENTRE - 1
            if rim and coherent("errands/nest/ribs", (x, 1, z)) > 0.45:
                t.set(
                    (x, 1, z), "minecraft:bone_block", axis="x" if abs(x - NEST_CENTRE) < abs(z - NEST_CENTRE) else "z"
                )
                if coherent("errands/nest/web", (x, 2, z)) > 0.8:
                    t.set((x, 2, z), "minecraft:cobweb")
            elif coherent("errands/nest/brush", (x, 1, z)) > 0.62 and distance > 1:
                t.set((x, 1, z), "minecraft:dead_bush")
            else:
                t.set((x, 1, z), AIR)
            for y in (2, 3):
                if (x, 2, z) not in t.blocks:
                    t.set((x, y, z), AIR)
    return t


def positions():
    return {
        "nest": {"centre": [NEST_CENTRE, 1, NEST_CENTRE], "size": [NEST, 4, NEST]},
        "cairns": {"centre": [CENTRE, 1, CENTRE], "drifa": [CENTRE, 1, CENTRE - 2], "size": [FIELD, 5, FIELD]},
        "seal": {
            "door": [list(pos) for pos in SEAL],
            "front": [MIDDLE, 1, FRONT - 2],
            "marks": [list(pos) for pos in MARKS],
            "note": list(NOTE),
            "size": list(RUIN),
        },
    }

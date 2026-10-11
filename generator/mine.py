import math

from blocks import AIR, Material, Template, coherent

STAGE = 8
LINING = 5
INSIDE = range(1, LINING - 1)
LADDER = 2
HEAD = 11
HEAD_SHAFT = (HEAD - LINING) // 2
GROUND = STAGE
FRAME = 5
HEAP = (1, 9)
HEAP_REACH = 4
HEAP_HIGH = 4
DEEP_WIDTH = 17
DEEP_HEIGHT = 11
SHAFT_X = DEEP_WIDTH // 2
SHAFT_Z = 44
ROOM = (SHAFT_Z - 9, SHAFT_Z + 1)
ROOM_HIGH = 4
DRIFT = (19, ROOM[0])
DRIFT_HIGH = 3
CHAMBER = (SHAFT_X, 11)
CHAMBER_REACH = 6.5
CHAMBER_HIGH = 7
WALL_Z = 4
BREACH = 2
SHELL = 2

CRIB = Material(
    "mine/crib",
    [
        ("minecraft:spruce_planks", 40),
        ("minecraft:stripped_spruce_wood", 15),
        ("minecraft:cobblestone", 25),
        ("minecraft:andesite", 20),
    ],
    ground=-64,
)
COLLAR = Material(
    "mine/collar",
    [
        ("minecraft:cobblestone", 45),
        ("minecraft:mossy_cobblestone", 15),
        ("minecraft:andesite", 25),
        ("minecraft:stone", 15),
    ],
    ground=-64,
)
LANDING = Material(
    "mine/landing",
    [("minecraft:spruce_planks", 70), ("minecraft:stripped_spruce_wood", 30)],
    ground=-64,
)
FLOOR = Material(
    "mine/floor",
    [("minecraft:gravel", 35), ("minecraft:cobbled_deepslate", 35), ("minecraft:deepslate", 30)],
    ground=-64,
)
ROCK = Material(
    "mine/rock",
    [("minecraft:deepslate", 70), ("minecraft:tuff", 20), ("minecraft:cobbled_deepslate", 10)],
    ground=-64,
)
CITY = Material(
    "mine/city",
    [
        ("minecraft:deepslate_bricks", 45),
        ("minecraft:cracked_deepslate_bricks", 25),
        ("minecraft:deepslate_tiles", 20),
        ("minecraft:cracked_deepslate_tiles", 10),
    ],
    ground=-64,
)
CITY_FLOOR = Material(
    "mine/city_floor",
    [
        ("minecraft:deepslate_tiles", 55),
        ("minecraft:cracked_deepslate_tiles", 25),
        ("minecraft:polished_deepslate", 20),
    ],
    ground=-64,
)
SPOIL = Material(
    "mine/spoil",
    [
        ("minecraft:cobbled_deepslate", 40),
        ("minecraft:tuff", 30),
        ("minecraft:gravel", 20),
        ("minecraft:deepslate", 10),
    ],
    ground=-64,
)
RUBBLE = Material(
    "mine/rubble",
    [("minecraft:cobbled_deepslate", 50), ("minecraft:cracked_deepslate_bricks", 30), ("minecraft:deepslate", 20)],
    ground=-64,
)


def lining(t, origin, top, bottom, ladder):
    ox, oz = origin
    for y in range(bottom, top):
        for x in range(LINING):
            for z in range(LINING):
                pos = (ox + x, y, oz + z)
                corner = x in (0, LINING - 1) and z in (0, LINING - 1)
                if corner:
                    t.set(pos, "minecraft:stripped_spruce_log", axis="y")
                elif x in (0, LINING - 1) or z in (0, LINING - 1):
                    t.set(pos, CRIB)
                elif ladder and (x, z) == (LADDER, 1):
                    t.set(pos, "minecraft:ladder", facing="south", waterlogged=False)
                else:
                    t.set(pos, AIR)


def landing(t, origin, y):
    ox, oz = origin
    for x in INSIDE:
        for z in INSIDE:
            if (x, z) == (LADDER, LINING - 2):
                t.set((ox + x, y, oz + z), "minecraft:ladder", facing="north", waterlogged=False)
            else:
                t.set((ox + x, y, oz + z), LANDING)


def stage():
    t = Template()
    lining(t, (0, 0), STAGE, 1, True)
    lining(t, (0, 0), 1, 0, False)
    landing(t, (0, 0), 0)
    return t


def head():
    t = Template()
    shaft = (HEAD_SHAFT, HEAD_SHAFT)
    lining(t, shaft, GROUND, 1, True)
    lining(t, shaft, 1, 0, False)
    landing(t, shaft, 0)
    low, high = HEAD_SHAFT, HEAD_SHAFT + LINING - 1
    middle = HEAD // 2
    for x in range(HEAD):
        for z in range(HEAD):
            inside = low < x < high and low < z < high
            ring = low <= x <= high and low <= z <= high
            if inside:
                t.set((x, GROUND, z), AIR)
                if (x - low, z - low) == (LADDER, 1):
                    t.set((x, GROUND, z), "minecraft:ladder", facing="south", waterlogged=False)
            elif ring:
                t.set((x, GROUND, z), COLLAR)
            elif math.hypot(x - middle, z - middle) + (coherent("mine/yard", (x, 0, z)) - 0.5) * 2 < HEAD / 2:
                t.set(
                    (x, GROUND, z),
                    "minecraft:gravel" if coherent("mine/gravel", (x, 0, z)) > 0.55 else "minecraft:coarse_dirt",
                )
    for x in range(low, high + 1):
        for z in range(low, high + 1):
            edge = x in (low, high) or z in (low, high)
            corner = x in (low, high) and z in (low, high)
            if edge and not corner and z != low:
                wall = (
                    "minecraft:cobblestone_wall"
                    if coherent("mine/wall", (x, 0, z)) > 0.4
                    else "minecraft:mossy_cobblestone_wall"
                )
                t.set((x, GROUND + 1, z), wall)
    top = GROUND + FRAME + 1
    for x in (low, high):
        for z in (low, high):
            for y in range(GROUND + 1, top):
                t.set((x, y, z), "minecraft:spruce_log", axis="y")
    for x in range(low, high + 1):
        for z in (low, high):
            t.set((x, top, z), "minecraft:spruce_log", axis="x")
    for z in range(low + 1, high):
        for x in (low, high):
            t.set((x, top, z), "minecraft:spruce_log", axis="z")
        t.set((middle, top, z), "minecraft:stripped_spruce_log", axis="z")
    for x, facing in ((low + 1, "west"), (high - 1, "east")):
        for z in (low, high):
            t.set(
                (x, top - 1, z),
                "minecraft:spruce_stairs",
                facing=facing,
                half="top",
                shape="straight",
                waterlogged=False,
            )
    for x in range(low + 1, high):
        for z in range(low + 1, high):
            for y in range(GROUND + 1, top):
                t.set((x, y, z), AIR)
    for x in range(low + 1, high):
        t.set((x, GROUND + 2, middle), "minecraft:stripped_spruce_log", axis="x")
    for x in (low, high):
        t.set((x, GROUND + 2, middle), "minecraft:spruce_fence")
    t.set((low - 1, GROUND + 1, middle), "minecraft:spruce_fence")
    t.set((low - 1, GROUND + 2, middle), "minecraft:spruce_fence")
    for y in range(GROUND + 3, top):
        t.set((middle, y, middle), "minecraft:chain", axis="y")
    for x in range(low, high + 1):
        for z in range(low, high + 1):
            if t.get((x, top, z)) is not None:
                t.set((x, top + 1, z), "minecraft:snow", layers=1 + int(coherent("mine/roof_snow", (x, 0, z)) * 3))
    bin_z = high + 2
    for x in range(middle - 1, middle + 2):
        for z in (bin_z, bin_z + 1):
            t.set((x, GROUND, z), "minecraft:spruce_planks")
            t.set((x, GROUND + 1, z), "minecraft:gravel" if (x + z) % 3 else "minecraft:tuff")
        t.set((x, GROUND + 1, bin_z + 2), "minecraft:spruce_slab", type="bottom", waterlogged=False)
    for z in (bin_z, bin_z + 1):
        t.set((middle - 2, GROUND + 1, z), "minecraft:spruce_slab", type="bottom", waterlogged=False)
        t.set((middle + 2, GROUND + 1, z), "minecraft:spruce_slab", type="bottom", waterlogged=False)
    for x in range(middle - 1, middle + 2):
        t.set((x, GROUND + 2, bin_z), "minecraft:snow", layers=2)
    heap(t)
    for z in range(0, low):
        if coherent("mine/rail", (middle, 0, z)) > 0.3:
            t.set((middle, GROUND, z), "minecraft:gravel")
            t.set((middle, GROUND + 1, z), "minecraft:rail", shape="north_south", waterlogged=False)
    for x in range(HEAD):
        for z in range(HEAD + 3):
            weather = (HEAD - x) / HEAD
            if (
                t.get((x, GROUND, z)) is not None
                and t.get((x, GROUND + 1, z)) is None
                and coherent("mine/drift", (x, 0, z)) * weather > 0.3
            ):
                t.set((x, GROUND + 1, z), "minecraft:snow", layers=1 + int(coherent("mine/drift_depth", (x, 0, z)) * 4))
    return t


def heap(t):
    cx, cz = HEAP
    for x in range(cx - HEAP_REACH - 1, cx + HEAP_REACH + 2):
        for z in range(cz - HEAP_REACH - 1, cz + HEAP_REACH + 2):
            if x < 0 or z < 0:
                continue
            reach = math.hypot(x - cx, (z - cz) * 1.3) + (coherent("mine/heap_edge", (x, 0, z)) - 0.5) * 1.5
            height = round(HEAP_HIGH * (1 - reach / HEAP_REACH))
            if height <= 0:
                continue
            for y in range(GROUND, GROUND + height):
                t.set((x, y, z), SPOIL)
            if coherent("mine/heap_snow", (x, 0, z)) > 0.45:
                t.set(
                    (x, GROUND + height, z),
                    "minecraft:snow",
                    layers=1 + int(coherent("mine/heap_depth", (x, 0, z)) * 3),
                )


def chamber(x, y, z):
    if y < 1 or y > CHAMBER_HIGH:
        return False
    cx, cz = CHAMBER
    rise = (y - 1) / CHAMBER_HIGH
    reach = CHAMBER_REACH * (1 - rise * rise * 0.55) + (coherent("mine/chamber", (x, y, z)) - 0.5) * 2.5
    return z > WALL_Z and math.hypot(x - cx, (z - cz) * 0.85) < reach


def hollow(x, y, z):
    room = SHAFT_X - 2 <= x <= SHAFT_X + 2 and ROOM[0] <= z <= ROOM[1] and 1 <= y <= ROOM_HIGH
    drift = SHAFT_X - 1 <= x <= SHAFT_X + 1 and DRIFT[0] <= z < DRIFT[1] and 1 <= y <= DRIFT_HIGH
    beyond = SHAFT_X - BREACH <= x <= SHAFT_X + BREACH and 1 <= z <= WALL_Z and 1 <= y <= 4
    return room or drift or beyond or chamber(x, y, z)


def deep(vein):
    t = Template()
    open_cells = {
        (x, y, z) for x in range(DEEP_WIDTH) for y in range(DEEP_HEIGHT) for z in range(SHAFT_Z + 3) if hollow(x, y, z)
    }
    shell = set()
    for x, y, z in open_cells:
        for dx in range(-SHELL, SHELL + 1):
            for dy in range(-SHELL, SHELL + 1):
                for dz in range(-SHELL, SHELL + 1):
                    cell = (x + dx, y + dy, z + dz)
                    if (
                        cell not in open_cells
                        and 0 <= cell[0] < DEEP_WIDTH
                        and 0 <= cell[1] < DEEP_HEIGHT
                        and 0 <= cell[2] < SHAFT_Z + 3
                    ):
                        shell.add(cell)
    for cell in shell:
        t.set(cell, ROCK)
    for cell in open_cells:
        t.set(cell, AIR)
    shaft = (SHAFT_X - 2, SHAFT_Z - 2)
    lining(t, shaft, DEEP_HEIGHT, ROOM_HIGH + 2, False)
    for x in range(SHAFT_X - 1, SHAFT_X + 2):
        for z in range(SHAFT_Z - 1, SHAFT_Z + 2):
            t.set((x, ROOM_HIGH + 1, z), AIR)
    for y in range(1, DEEP_HEIGHT):
        t.set((SHAFT_X, y, SHAFT_Z + 1), "minecraft:ladder", facing="north", waterlogged=False)
        if y <= ROOM_HIGH + 1:
            t.set((SHAFT_X, y, SHAFT_Z + 2), CRIB)
    room(t)
    drift(t)
    city(t, open_cells)
    ore(t, open_cells, shell, vein)
    return t


def room(t):
    for x in range(SHAFT_X - 2, SHAFT_X + 3):
        for z in range(ROOM[0], ROOM[1] + 1):
            t.set((x, 0, z), LANDING if z > SHAFT_Z - 3 else FLOOR)
    for z in (ROOM[0] + 1, ROOM[0] + 5):
        for y in range(1, ROOM_HIGH + 1):
            t.set((SHAFT_X - 2, y, z), "minecraft:spruce_log", axis="y")
            t.set((SHAFT_X + 2, y, z), "minecraft:spruce_log", axis="y")
        for x in range(SHAFT_X - 2, SHAFT_X + 3):
            t.set((x, ROOM_HIGH + 1, z), "minecraft:spruce_log", axis="x")
    t.set((SHAFT_X + 1, 1, ROOM[0] + 3), "minecraft:cobbled_deepslate_slab", type="bottom", waterlogged=False)
    t.set((SHAFT_X - 1, 1, ROOM[0] + 2), "minecraft:cobweb")


def drift(t):
    for z in range(DRIFT[0], DRIFT[1]):
        for x in range(SHAFT_X - 1, SHAFT_X + 2):
            t.set((x, 0, z), FLOOR)
        if coherent("mine/drift_rail", (SHAFT_X, 0, z)) > 0.28:
            t.set((SHAFT_X, 0, z), "minecraft:gravel")
            t.set((SHAFT_X, 1, z), "minecraft:rail", shape="north_south", waterlogged=False)
        if (z - DRIFT[0]) % 4 == 0:
            for y in range(1, DRIFT_HIGH + 1):
                t.set((SHAFT_X - 1, y, z), "minecraft:spruce_fence")
                t.set((SHAFT_X + 1, y, z), "minecraft:spruce_fence")
            for x in range(SHAFT_X - 1, SHAFT_X + 2):
                t.set((x, DRIFT_HIGH + 1, z), "minecraft:spruce_planks")
        elif coherent("mine/web", (SHAFT_X, 2, z)) > 0.78:
            t.set((SHAFT_X + (1 if z % 2 else -1), DRIFT_HIGH, z), "minecraft:cobweb")


def city_block(x, y):
    if (x - SHAFT_X) % 4 == 0 and abs(x - SHAFT_X) > BREACH:
        return "minecraft:chiseled_deepslate" if y == 5 else "minecraft:polished_deepslate"
    if y == 0:
        return "minecraft:deepslate_tiles"
    if y == 5:
        return "minecraft:deepslate_tiles"
    return CITY


def city(t, open_cells):
    for x in range(DEEP_WIDTH):
        for y in range(0, CHAMBER_HIGH + 2):
            for z in range(WALL_Z - 1, WALL_Z + 1):
                broken = abs(x - SHAFT_X) <= BREACH and 1 <= y <= 4
                ragged = abs(x - SHAFT_X) == BREACH + 1 and 1 <= y <= 3 and coherent("mine/ragged", (x, y, z)) > 0.45
                if broken or ragged:
                    t.set((x, y, z), AIR)
                elif y == 0 or (x, y, z) not in open_cells:
                    t.set((x, y, z), city_block(x, y))
    for z in range(0, WALL_Z - 1):
        for y in range(0, 6):
            for x in (SHAFT_X - BREACH - 1, SHAFT_X + BREACH + 1):
                t.set((x, y, z), CITY)
        for x in range(SHAFT_X - BREACH, SHAFT_X + BREACH + 1):
            t.set((x, 5, z), CITY)
    for x in range(SHAFT_X - BREACH, SHAFT_X + BREACH + 1):
        for z in range(0, WALL_Z + 1):
            t.set((x, 0, z), CITY_FLOOR)
        for z in range(0, 2):
            for y in range(1, 5):
                if y <= 1 + (2 - abs(x - SHAFT_X)) // 2 + z:
                    t.set((x, y, z), RUBBLE)
    for x, z, kind in (
        (SHAFT_X - 1, WALL_Z + 1, "chiseled"),
        (SHAFT_X + 2, WALL_Z + 2, "slab"),
        (SHAFT_X - 3, WALL_Z + 2, "stairs"),
        (SHAFT_X + 1, WALL_Z + 3, "rubble"),
    ):
        if kind == "chiseled":
            t.set((x, 1, z), "minecraft:chiseled_deepslate")
        elif kind == "slab":
            t.set((x, 1, z), "minecraft:deepslate_brick_slab", type="bottom", waterlogged=False)
        elif kind == "stairs":
            t.set(
                (x, 1, z),
                "minecraft:deepslate_tile_stairs",
                facing="east",
                half="bottom",
                shape="straight",
                waterlogged=False,
            )
        else:
            t.set((x, 1, z), "minecraft:cobbled_deepslate")
    sculk = []
    for x in range(DEEP_WIDTH):
        for z in range(0, WALL_Z + 6):
            for y in range(0, CHAMBER_HIGH + 2):
                cell = (x, y, z)
                name = t.get(cell)
                if name is None or name == AIR or name.endswith(("_slab", "_stairs", "rail")):
                    continue
                exposed = any(
                    (x + dx, y + dy, z + dz) in open_cells and t.get((x + dx, y + dy, z + dz)) == AIR
                    for dx, dy, dz in ((0, 1, 0), (1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1))
                )
                near = (WALL_Z + 6 - z) / 6 - abs(x - SHAFT_X) / DEEP_WIDTH
                if exposed and coherent("mine/sculk", cell) * near > 0.5:
                    sculk.append(cell)
    for cell in sculk:
        t.set(cell, "minecraft:sculk")
    for x, z in ((SHAFT_X - 2, WALL_Z + 2), (SHAFT_X + 3, WALL_Z + 4)):
        t.set((x, 0, z), "minecraft:sculk")
        t.set((x, 1, z), "minecraft:sculk_sensor", power=0, sculk_sensor_phase="inactive", waterlogged=False)
    t.set((SHAFT_X, 0, WALL_Z - 2), "minecraft:sculk")
    t.set((SHAFT_X, 1, WALL_Z - 2), "minecraft:sculk_shrieker", can_summon=False, shrieking=False, waterlogged=False)
    t.set((SHAFT_X + 1, 0, WALL_Z + 1), "minecraft:sculk")
    t.set(
        (SHAFT_X + 1, 1, WALL_Z + 1), "minecraft:sculk_shrieker", can_summon=False, shrieking=False, waterlogged=False
    )
    for x, z, count in ((SHAFT_X - 2, 2, 3), (SHAFT_X + 2, 2, 2)):
        if t.get((x, 1, z)) == AIR:
            t.set((x, 1, z), "minecraft:candle", candles=count, lit=False, waterlogged=False)


def ore(t, open_cells, shell, vein):
    def exposed(cell):
        x, y, z = cell
        return any(
            (x + dx, y + dy, z + dz) in open_cells
            for dx, dy, dz in ((0, 1, 0), (0, -1, 0), (1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1))
        )

    candidates = []
    for cell in shell:
        x, y, z = cell
        name = t.get(cell)
        if name is None or not name.startswith(
            ("minecraft:deepslate", "minecraft:tuff", "minecraft:cobbled_deepslate")
        ):
            continue
        if z <= WALL_Z or z > CHAMBER[1] + CHAMBER_REACH + 1 or y < 1:
            continue
        near_wall = 1 - (z - WALL_Z) / (CHAMBER_REACH * 2)
        low = 1 - max(0, y - 3) / CHAMBER_HIGH
        score = coherent("mine/vein", cell) * 0.5 + near_wall * 0.3 + low * 0.3 + (0.15 if exposed(cell) else 0)
        candidates.append((score, cell))
    candidates.sort(reverse=True)
    for _, cell in candidates[:vein]:
        t.set(cell, "wayfarer:frost_iron_ore")


def positions():
    return {
        "deep": {
            "chamber": [CHAMBER[0], 1, CHAMBER[1]],
            "low": [0, 0, 0],
            "high": [DEEP_WIDTH - 1, DEEP_HEIGHT - 1, SHAFT_Z + 2],
            "shaft": [SHAFT_X, SHAFT_Z],
        },
        "head": {"ground": GROUND, "shaft": [HEAD // 2, HEAD // 2], "top": [HEAD // 2, GROUND + 1, HEAD_SHAFT + 1]},
        "stage": {"height": STAGE, "shaft": [LINING // 2, LINING // 2]},
    }

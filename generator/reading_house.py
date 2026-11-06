import math

from blocks import AIR, Material, Template, candle, coherent

MARGIN = 3
HALL = (9, 15)
WALL = 5
GABLE = 3
TOWER = 5
TOWER_HIGH = 10
VAULT_DEPTH = 6
SIZE = (MARGIN * 2 + HALL[0] + TOWER - 1, MARGIN * 2 + HALL[1])
LEFT = MARGIN
RIGHT = MARGIN + HALL[0] - 1
FRONT = MARGIN
BACK = MARGIN + HALL[1] - 1
MIDDLE = (LEFT + RIGHT) // 2
TOWER_X = (RIGHT, RIGHT + TOWER - 1)
TOWER_Z = (BACK - TOWER + 1, BACK)
STAIR_X = (LEFT + 1, LEFT + 2)
STAIR_Z = (BACK - 6, BACK - 2)
VAULT_X = (LEFT, LEFT + 7)
VAULT_Z = (BACK - 7, BACK)
LECTERN = (MIDDLE + 1, 1, BACK - 3)
FIRES = [(LEFT + 2, 1, FRONT + 4), (RIGHT - 2, 1, FRONT + 8), (MIDDLE + 1, 1, BACK - 9)]
RAFTERS = [FRONT + 3, FRONT + 7, FRONT + 11]

WALLS = Material(
    "reading_house/walls",
    [
        ("minecraft:stone_bricks", 45),
        ("minecraft:cracked_stone_bricks", 25),
        ("minecraft:mossy_stone_bricks", 15),
        ("minecraft:cobblestone", 15),
    ],
)
SOOT = Material(
    "reading_house/soot",
    [
        ("minecraft:cracked_stone_bricks", 30),
        ("minecraft:cobbled_deepslate", 25),
        ("minecraft:tuff", 20),
        ("minecraft:deepslate_bricks", 15),
        ("minecraft:stone_bricks", 10),
    ],
)
FLOOR = Material(
    "reading_house/floor",
    [("minecraft:stone_bricks", 50), ("minecraft:cracked_stone_bricks", 25), ("minecraft:polished_andesite", 25)],
)
ASH = Material(
    "reading_house/ash",
    [
        ("minecraft:light_gray_carpet", 35),
        ("minecraft:gray_carpet", 30),
        (AIR, 35),
    ],
)
YARD = Material(
    "reading_house/yard",
    [
        ("minecraft:coarse_dirt", 35),
        ("minecraft:gravel", 25),
        ("minecraft:dirt_path", 20),
        ("minecraft:cobblestone", 20),
    ],
)
VAULT = Material(
    "reading_house/vault",
    [("minecraft:stone_bricks", 55), ("minecraft:mossy_stone_bricks", 25), ("minecraft:cracked_stone_bricks", 20)],
)


def burnt(x, y, z):
    return y >= WALL - 1 - round(coherent("reading_house/soot_line", (x, 0, z)) * 2)


def wall_block(x, y, z):
    return SOOT if burnt(x, y, z) else WALLS


def window(x, z, y):
    along = z if x in (LEFT, RIGHT) else x
    long_side = x in (LEFT, RIGHT)
    if y not in (2, 3):
        return False
    if long_side:
        return (along - FRONT) % 4 == 2 and FRONT + 2 <= along <= BACK - 2
    return False


def gable(x):
    return round((HALL[0] // 2 - abs(x - MIDDLE)) * GABLE / (HALL[0] // 2))


def house():
    t = Template()
    for x in range(SIZE[0]):
        for z in range(SIZE[1]):
            reach = math.hypot((x - MIDDLE) / (HALL[0] / 2 + MARGIN), (z - (FRONT + BACK) / 2) / (HALL[1] / 2 + MARGIN))
            if reach + (coherent("reading_house/yard_edge", (x, 0, z)) - 0.5) * 0.4 < 1.05 or inside_tower(x, z):
                t.set((x, 0, z), YARD)
    for x in range(LEFT, RIGHT + 1):
        for z in range(FRONT, BACK + 1):
            t.set((x, 0, z), FLOOR)
    walls(t)
    tower(t)
    gables(t)
    rafters(t)
    shelves(t)
    stair(t)
    ash(t)
    fires(t)
    rubble(t)
    t.set(LECTERN, "minecraft:lectern", facing="north", has_book=False, powered=False)
    t.entrance((MIDDLE, 0, FRONT), "north")
    return t


def inside_tower(x, z):
    return TOWER_X[0] <= x <= TOWER_X[1] and TOWER_Z[0] <= z <= TOWER_Z[1]


def walls(t):
    for y in range(1, WALL + 1):
        for x in range(LEFT, RIGHT + 1):
            for z in (FRONT, BACK):
                if z == FRONT and x == MIDDLE and y <= 3:
                    continue
                broken = (
                    z == FRONT
                    and y >= WALL - 1
                    and abs(x - MIDDLE) <= 2
                    and coherent("reading_house/front", (x, y, z)) > 0.35
                )
                if not broken:
                    t.set((x, y, z), wall_block(x, y, z))
        for z in range(FRONT + 1, BACK):
            for x in (LEFT, RIGHT):
                if window(x, z, y):
                    t.set((x, y, z), AIR if coherent("reading_house/bars", (x, y, z)) > 0.4 else "minecraft:iron_bars")
                    continue
                if x == RIGHT and TOWER_Z[0] < z < TOWER_Z[1] and y <= 2 and z == (TOWER_Z[0] + TOWER_Z[1]) // 2:
                    continue
                t.set((x, y, z), wall_block(x, y, z))
    for z in range(FRONT, BACK + 1, 4):
        for x, out in ((LEFT, -1), (RIGHT, 1)):
            if x == RIGHT and z >= TOWER_Z[0]:
                continue
            for y in range(1, WALL - 1):
                t.set((x + out, y, z), "minecraft:stone_bricks")
            t.set(
                (x + out, WALL - 1, z),
                "minecraft:stone_brick_stairs",
                facing="east" if out < 0 else "west",
                half="bottom",
                shape="straight",
                waterlogged=False,
            )
    t.set(
        (MIDDLE, 3, FRONT),
        "minecraft:stone_brick_stairs",
        facing="south",
        half="top",
        shape="straight",
        waterlogged=False,
    )


def tower(t):
    x0, x1 = TOWER_X
    z0, z1 = TOWER_Z
    for y in range(1, TOWER_HIGH + 1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                edge = x in (x0, x1) or z in (z0, z1)
                if not edge:
                    t.set((x, y, z), AIR)
                    continue
                door = x == x0 and z == (z0 + z1) // 2 and y <= 2
                slit = (x == x1 or z == z1) and y in (4, 7) and (x - x0 == 2 or z - z0 == 2)
                if door or slit:
                    t.set((x, y, z), AIR)
                elif y == TOWER_HIGH and (x + z) % 2 == 1 and coherent("reading_house/merlon", (x, y, z)) > 0.25:
                    continue
                else:
                    t.set((x, y, z), wall_block(x, y - TOWER_HIGH + WALL, z) if y > WALL else WALLS)
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            t.set((x, 0, z), FLOOR)
    t.set((x1 - 1, 1, z1 - 1), "minecraft:dark_oak_planks")
    t.set((x1 - 1, 2, z1 - 1), "minecraft:candle", candles=2, lit=False, waterlogged=False)
    t.set(
        (x1 - 2, 1, z1 - 1),
        "minecraft:dark_oak_stairs",
        facing="south",
        half="bottom",
        shape="straight",
        waterlogged=False,
    )
    t.set((x0 + 1, 1, z0 + 1), "minecraft:chiseled_bookshelf", facing="east", **shelf(0b000101))
    t.set((x0 + 1, 2, z0 + 1), "minecraft:chiseled_bookshelf", facing="east", **shelf(0))
    t.set((x0 + 2, 1, z1 - 1), "minecraft:light_gray_carpet")


def gables(t):
    for z in (FRONT, BACK):
        for x in range(LEFT + 1, RIGHT):
            for y in range(WALL + 1, WALL + 1 + gable(x)):
                broken = z == FRONT and coherent("reading_house/gable", (x, y, z)) > 0.45
                if not broken:
                    t.set((x, y, z), SOOT)


def rafters(t):
    for index, z in enumerate(RAFTERS):
        broken = index == 1
        for x in range(LEFT, RIGHT + 1):
            if broken and x > MIDDLE:
                continue
            t.set(
                (x, WALL + 1, z),
                "minecraft:dark_oak_wood"
                if coherent("reading_house/rafter", (x, 0, z)) > 0.5
                else "minecraft:stripped_dark_oak_wood",
            )
    for z in range(RAFTERS[1] - 1, RAFTERS[1] + 3):
        t.set((MIDDLE + 2, 1, z), "minecraft:dark_oak_log", axis="z")
    t.set((MIDDLE + 3, 1, RAFTERS[1] + 3), "minecraft:dark_oak_slab", type="bottom", waterlogged=False)


def shelf(books):
    return {f"slot_{slot}_occupied": bool(books >> slot & 1) for slot in range(6)}


def shelves(t):
    for z in range(FRONT + 2, BACK - 1):
        if (z - FRONT) % 4 == 2:
            continue
        for x, facing in ((LEFT + 1, "east"), (RIGHT - 1, "west")):
            if x == LEFT + 1 and STAIR_Z[0] - 1 <= z <= STAIR_Z[1]:
                continue
            if x == RIGHT - 1 and z >= TOWER_Z[0]:
                continue
            for y in range(1, 4):
                height = 1 + round(coherent("reading_house/shelf_height", (x, 0, z)) * 2)
                if y > height:
                    continue
                roll = coherent("reading_house/shelf", (x, y, z)) + (y - 1) * 0.12
                if roll > 0.58:
                    t.set((x, y, z), "minecraft:dark_oak_planks" if roll < 0.75 else "minecraft:stripped_dark_oak_wood")
                elif roll > 0.2:
                    books = 1 << int(coherent("reading_house/books", (x, y, z)) * 6) if roll < 0.32 else 0
                    t.set((x, y, z), "minecraft:chiseled_bookshelf", facing=facing, **shelf(books))
                else:
                    t.set((x, y, z), "minecraft:bookshelf")


def stair(t):
    x0, x1 = STAIR_X
    for z in range(STAIR_Z[0], STAIR_Z[1] + 1):
        for x in range(x0, x1 + 1):
            if z == STAIR_Z[0]:
                t.set(
                    (x, 0, z),
                    "minecraft:stone_brick_stairs",
                    facing="north",
                    half="bottom",
                    shape="straight",
                    waterlogged=False,
                )
            else:
                t.set((x, 0, z), AIR)
    for z in range(STAIR_Z[0], STAIR_Z[1] + 2):
        t.set((x1 + 1, 1, z), "minecraft:stone_brick_wall" if z % 2 else "minecraft:cobblestone_wall")


def ash(t):
    for x in range(LEFT + 1, RIGHT):
        for z in range(FRONT + 1, BACK):
            if (
                t.get((x, 1, z)) is None
                and t.get((x, 0, z)) not in (None, AIR)
                and not t.get((x, 0, z)).endswith("_stairs")
            ):
                t.set((x, 1, z), ASH)
    for x, z in ((MIDDLE - 1, FRONT + 6), (MIDDLE + 1, FRONT + 10), (LEFT + 3, BACK - 2)):
        t.set((x, 1, z), "minecraft:gray_concrete_powder")


def fires(t):
    for x, y, z in FIRES:
        t.set((x, y, z), "minecraft:campfire", facing="north", lit=True, signal_fire=True, waterlogged=False)
        for dx, dz in ((1, 0), (-1, 0), (0, 1)):
            if t.get((x + dx, y, z + dz)) in (None, AIR, "minecraft:light_gray_carpet", "minecraft:gray_carpet"):
                t.set((x + dx, y, z + dz), "minecraft:dark_oak_slab", type="bottom", waterlogged=False)


def rubble(t):
    for x in range(SIZE[0]):
        for z in range(SIZE[1]):
            if t.get((x, 0, z)) is None:
                continue
            if t.get((x, 1, z)) is not None or LEFT <= x <= RIGHT and FRONT <= z <= BACK or inside_tower(x, z):
                continue
            near = min(abs(x - LEFT), abs(x - RIGHT), abs(z - FRONT), abs(z - BACK))
            roll = coherent("reading_house/rubble", (x, 1, z))
            if near <= 2 and roll > 0.85:
                t.set((x, 1, z), "minecraft:cobblestone")
            elif near <= 2 and roll > 0.72:
                t.set((x, 1, z), "minecraft:stone_brick_slab", type="bottom", waterlogged=False)


def vault():
    t = Template()
    x0, x1 = VAULT_X
    z0, z1 = VAULT_Z
    top = VAULT_DEPTH
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.set((x, 0, z), VAULT)
            t.set((x, 1, z), VAULT)
            for y in range(2, top):
                edge = x in (x0, x1) or z in (z0, z1)
                t.set((x, y, z), VAULT if edge else AIR)
    sx0, sx1 = STAIR_X
    for step, z in enumerate(range(STAIR_Z[0] + 1, STAIR_Z[1] + 1)):
        y = top - 1 - step
        for x in range(sx0, sx1 + 1):
            for below in range(1, y):
                t.set((x, below, z), VAULT)
            t.set(
                (x, y, z),
                "minecraft:stone_brick_stairs",
                facing="north",
                half="bottom",
                shape="straight",
                waterlogged=False,
            )
            for above in range(y + 1, top):
                t.set((x, above, z), AIR)
    floor = 2
    for z in range(z0 + 1, z1):
        t.set(
            (x1 - 1, floor, z),
            "minecraft:bookshelf" if z % 3 else "minecraft:chiseled_bookshelf",
            **({} if z % 3 else {"facing": "west", **shelf(0b111111)}),
        )
        t.set(
            (x1 - 1, floor + 1, z),
            "minecraft:chiseled_bookshelf",
            facing="west",
            **shelf(0b111011 if z % 2 else 0b011111),
        )
        t.set((x1 - 1, floor + 2, z), "minecraft:bookshelf")
    for x in range(sx1 + 2, x1 - 1):
        t.set((x, floor, z1 - 1), "minecraft:chiseled_bookshelf", facing="north", **shelf(0b101111))
        t.set((x, floor + 1, z1 - 1), "minecraft:bookshelf")
    bed = (sx1 + 2, floor, z0 + 2)
    t.set(bed, "minecraft:white_carpet")
    t.set((bed[0] + 1, floor, bed[2]), "minecraft:white_carpet")
    t.set((bed[0], floor, bed[2] + 1), "minecraft:brown_carpet")
    candle(t, (x1 - 2, floor, z0 + 1), count=3)
    candle(t, (sx1 + 2, floor, z1 - 2), count=2)
    t.set((x1 - 2, floor, z1 - 2), "minecraft:lectern", facing="west", has_book=False, powered=False)
    t.set((bed[0] + 2, floor, bed[2] + 1), "minecraft:lantern", hanging=False, waterlogged=False)
    return t


def positions():
    stair_top = [STAIR_X[1] + 2, 1, STAIR_Z[0] - 1]
    return {
        "ansel": [MIDDLE, 1, FRONT + 1],
        "door": [MIDDLE, 1, FRONT],
        "fires": [list(fire) for fire in FIRES],
        "grave": [LEFT - 2, 1, FRONT + 4],
        "lectern": list(LECTERN),
        "prints": [list(LECTERN), [STAIR_X[1], 1, STAIR_Z[0]]],
        "size": [SIZE[0], max(TOWER_HIGH, WALL + GABLE) + 2, SIZE[1]],
        "stair": stair_top,
        "vault": [VAULT_X[1] - 3, 2, VAULT_Z[0] + 3],
        "vault_depth": VAULT_DEPTH,
    }

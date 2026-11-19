from blocks import AIR, Material, Template, coherent, lectern

HOUSE = (15, 10, 11)
CELLAR = 4
FLOOR = CELLAR
DESK = (3, FLOOR + 1, 3)
CHAIR = (3, FLOOR + 1, 4)
CELLAR_SPOT = (11, 1, 5)
CAGE = (2, 1, 6, 9)
HATCH = (7, 8)
WALLS = Material(
    "mansion/walls",
    [("minecraft:dark_oak_planks", 55), ("minecraft:stripped_dark_oak_log", 25), ("minecraft:dark_oak_log", 20)],
)
CELLAR_STONE = Material(
    "mansion/cellar",
    [("minecraft:cobbled_deepslate", 50), ("minecraft:deepslate_bricks", 30), ("minecraft:mossy_cobblestone", 20)],
)


def house(orders):
    width, height, length = HOUSE
    t = Template()
    for x in range(width):
        for z in range(length):
            edge = x in (0, width - 1) or z in (0, length - 1)
            corner = x in (0, width - 1) and z in (0, length - 1)
            t.set((x, 0, z), CELLAR_STONE)
            for y in range(1, FLOOR):
                t.set((x, y, z), CELLAR_STONE if edge else AIR)
            t.set((x, FLOOR, z), "minecraft:dark_oak_planks")
            for y in range(FLOOR + 1, FLOOR + 5):
                if corner:
                    t.set((x, y, z), "minecraft:dark_oak_log", axis="y")
                else:
                    t.set((x, y, z), WALLS if edge else AIR)
            t.set((x, FLOOR + 5, z), "minecraft:dark_oak_slab", type="bottom", waterlogged=False)
            if edge and not corner and coherent("mansion/window", (x, FLOOR + 2, z)) > 0.6:
                t.set(
                    (x, FLOOR + 2, z),
                    "minecraft:gray_stained_glass_pane",
                    east=False,
                    north=False,
                    south=False,
                    west=False,
                    waterlogged=False,
                )
    for y in (FLOOR + 1, FLOOR + 2):
        t.set((width // 2, y, 0), AIR)
    t.entrance((width // 2, FLOOR, 0), "north")
    t.set(DESK, "minecraft:dark_oak_slab", type="top", waterlogged=False)
    t.set((DESK[0] + 1, DESK[1], DESK[2]), "minecraft:dark_oak_slab", type="top", waterlogged=False)
    t.set((DESK[0], DESK[1] + 1, DESK[2]), "minecraft:candle", candles=3, lit=False, waterlogged=False)
    t.set(CHAIR, "minecraft:dark_oak_stairs", facing="north", half="bottom", shape="straight", waterlogged=False)
    t.set((DESK[0] + 3, DESK[1], DESK[2]), "minecraft:bookshelf")
    lectern(
        t, (DESK[0] + 2, DESK[1], DESK[2]), "south", orders["id"], orders["title"], orders["author"], orders["pages"]
    )
    t.set((HATCH[0], FLOOR, HATCH[1]), AIR)
    for y in range(1, FLOOR + 1):
        t.set((HATCH[0], y, HATCH[1]), "minecraft:ladder", facing="north", waterlogged=False)
    for y in range(1, FLOOR):
        t.set((HATCH[0], y, HATCH[1] + 1), CELLAR_STONE)
    x0, z0, x1, z1 = CAGE
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                for y in range(1, FLOOR):
                    t.set(
                        (x, y, z),
                        "minecraft:iron_bars",
                        east=True,
                        north=True,
                        south=True,
                        west=True,
                        waterlogged=False,
                    )
    t.set(
        (CELLAR_SPOT[0], CELLAR_SPOT[1] + 2, CELLAR_SPOT[2]), "minecraft:soul_lantern", hanging=True, waterlogged=False
    )
    return t


def positions():
    return {
        "bars": [
            [x, y, z]
            for x in range(CAGE[0], CAGE[2] + 1)
            for z in range(CAGE[1], CAGE[3] + 1)
            for y in range(1, FLOOR)
            if x in (CAGE[0], CAGE[2]) or z in (CAGE[1], CAGE[3])
        ],
        "cage": [[CAGE[0] + 1, 1, CAGE[1] + 1], [CAGE[0] + 2, 1, CAGE[1] + 2], [CAGE[0] + 3, 1, CAGE[1] + 1]],
        "cellar": list(CELLAR_SPOT),
        "desk": list(DESK),
        "door": [CAGE[2], 1, (CAGE[1] + CAGE[3]) // 2],
        "floor": FLOOR,
        "front": [HOUSE[0] // 2, FLOOR + 1, -2],
        "size": list(HOUSE),
    }

from blocks import AIR, Material, Template, coherent, lectern

PAD = 17
MIDDLE = PAD // 2
MAST = (MIDDLE, MIDDLE + 4)
HUT = (1, 1, 5, 5)
RACK = (3, 1, 7)
AIRSHIP = (MIDDLE + 2, 1, MIDDLE - 2)
DECK = Material(
    "act3/airfield/deck",
    [("minecraft:spruce_planks", 55), ("minecraft:stripped_spruce_log", 20), ("minecraft:dark_oak_planks", 25)],
)
GROUND = Material(
    "act3/airfield/ground",
    [("minecraft:coarse_dirt", 45), ("minecraft:gravel", 25), ("minecraft:dirt_path", 30)],
)
BANNER = {"rotation": 0}


def airfield():
    t = Template()
    for x in range(PAD):
        for z in range(PAD):
            inside = 2 <= x <= PAD - 3 and 2 <= z <= PAD - 3
            t.set((x, 0, z), DECK if inside else GROUND)
    for y in range(1, 12):
        t.set((MAST[0], y, MAST[1]), "minecraft:stripped_spruce_log", axis="y")
    for dx, side in ((-1, "west"), (1, "east")):
        t.set(
            (MAST[0] + dx, 11, MAST[1]),
            "minecraft:spruce_fence",
            **{key: key == ("east" if side == "west" else "west") for key in ("east", "north", "south", "west")},
            waterlogged=False,
        )
    t.set((MAST[0], 12, MAST[1]), "minecraft:lantern", hanging=False, waterlogged=False)
    t.set((MAST[0], 10, MAST[1] - 1), "minecraft:gray_wall_banner", facing="north")
    for x in range(HUT[0], HUT[2] + 1):
        for z in range(HUT[1], HUT[3] + 1):
            edge = x in (HUT[0], HUT[2]) or z in (HUT[1], HUT[3])
            corner = x in (HUT[0], HUT[2]) and z in (HUT[1], HUT[3])
            for y in range(1, 4):
                if corner:
                    t.set((x, y, z), "minecraft:dark_oak_log", axis="y")
                else:
                    t.set((x, y, z), "minecraft:dark_oak_planks" if edge else AIR)
            t.set((x, 4, z), "minecraft:dark_oak_slab", type="bottom", waterlogged=False)
    for y in (1, 2):
        t.set((HUT[2], y, 3), AIR)
    t.set((HUT[0] + 1, 1, HUT[1] + 1), "minecraft:barrel", facing="up", open=False)
    t.set((HUT[0] + 2, 1, HUT[1] + 1), "minecraft:lantern", hanging=False, waterlogged=False)
    t.set(RACK, "minecraft:gray_wool")
    for dx, open_side in ((1, "west"), (-1, "east")):
        t.set(
            (RACK[0] + dx, 1, RACK[2]),
            "minecraft:spruce_fence",
            **{key: key == open_side for key in ("east", "north", "south", "west")},
            waterlogged=False,
        )
        t.set((RACK[0] + dx, 2, RACK[2]), "minecraft:gray_carpet")
    for x in range(PAD):
        for z in (0, PAD - 1):
            if coherent("act3/airfield/rope", (x, 1, z)) > 0.55:
                t.set(
                    (x, 1, z),
                    "minecraft:spruce_fence",
                    east=False,
                    north=False,
                    south=False,
                    west=False,
                    waterlogged=False,
                )
    return t


HOUSE = (15, 10, 11)
CELLAR = 4
FLOOR = CELLAR
DESK = (3, FLOOR + 1, 3)
CHAIR = (3, FLOOR + 1, 4)
STAND = (11, 1, 5)
CAGE = (2, 1, 6, 9)
HATCH = (7, 8)
WALLS = Material(
    "act3/house/walls",
    [("minecraft:dark_oak_planks", 55), ("minecraft:stripped_dark_oak_log", 25), ("minecraft:dark_oak_log", 20)],
)
CELLAR_STONE = Material(
    "act3/house/cellar",
    [("minecraft:cobbled_deepslate", 50), ("minecraft:deepslate_bricks", 30), ("minecraft:mossy_cobblestone", 20)],
)
GRAVES = (21, 6, 17)
EMPTY_GRAVE = (10, 13)
GRAVE_ROWS = (3, 6, 9)
GRAVE_COLUMNS = (3, 6, 9, 12, 15, 18)
GATE = (23, 9, 5)


def sign(text):
    lines = [f'{{"text":"{line}"}}' for line in text] + ['{"text":""}'] * (4 - len(text))
    return {
        "back_text": {"color": "black", "has_glowing_text": 0, "messages": ['{"text":""}'] * 4},
        "front_text": {"color": "black", "has_glowing_text": 0, "messages": lines},
        "id": "minecraft:sign",
        "is_waxed": 1,
    }


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
            if edge and not corner and coherent("act3/house/window", (x, FLOOR + 2, z)) > 0.6:
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
    t.set((STAND[0], STAND[1] + 2, STAND[2]), "minecraft:soul_lantern", hanging=True, waterlogged=False)
    return t


def graves():
    width, _, length = GRAVES
    t = Template()
    for x in range(width):
        for z in range(length):
            t.set(
                (x, 0, z),
                "minecraft:podzol" if coherent("act3/graves/ground", (x, 0, z)) > 0.45 else "minecraft:coarse_dirt",
            )
    for row in GRAVE_ROWS:
        for column in GRAVE_COLUMNS:
            t.set((column, 0, row), "minecraft:rooted_dirt")
            t.set(
                (column, 1, row - 1),
                "minecraft:mossy_cobblestone_wall",
                east="none",
                north="none",
                south="none",
                up=True,
                waterlogged=False,
                west="none",
            )
    x, z = EMPTY_GRAVE
    for dz in (0, 1):
        t.set((x, 0, z + dz), AIR)
    t.set((x, 1, z - 1), "minecraft:cobblestone")
    t.set(
        (x, 1, z - 2), "minecraft:spruce_wall_sign", entity=sign(["WEND", "", "M."]), facing="north", waterlogged=False
    )
    return t


def gatehouse():
    width, height, depth = GATE
    t = Template()
    middle = width // 2
    for x in range(width):
        tower = x < 4 or x >= width - 4
        for z in range(depth):
            t.set((x, 0, z), "minecraft:smooth_sandstone")
            if z not in (1, 2, 3):
                continue
            for y in range(1, height if tower else height - 2):
                t.set(
                    (x, y, z),
                    "minecraft:stripped_acacia_log" if (x + y) % 5 == 0 else "minecraft:cut_sandstone",
                    **({"axis": "y"} if (x + y) % 5 == 0 else {}),
                )
            if not tower and x % 2 == 0:
                t.set(
                    (x, height - 2, z),
                    "minecraft:sandstone_wall",
                    east="none",
                    north="none",
                    south="none",
                    up=True,
                    waterlogged=False,
                    west="none",
                )
    for x in range(middle - 1, middle + 2):
        for y in range(1, 5):
            for z in (1, 3):
                t.set((x, y, z), AIR)
            t.set(
                (x, y, 2),
                "minecraft:iron_bars",
                east=x < middle + 1,
                north=False,
                south=False,
                waterlogged=False,
                west=x > middle - 1,
            )
    return t


def positions():
    return {
        "airfield": {"airship": list(AIRSHIP), "rack": list(RACK), "size": [PAD, 13, PAD]},
        "gatehouse": {
            "champion": [GATE[0] // 2, 1, -4],
            "gate": [[x, y, 2] for x in range(GATE[0] // 2 - 1, GATE[0] // 2 + 2) for y in range(1, 5)],
            "size": list(GATE),
        },
        "graves": {"empty": [EMPTY_GRAVE[0], 1, EMPTY_GRAVE[1] - 2], "size": [GRAVES[0], GRAVES[1], GRAVES[2]]},
        "house": {
            "cage": [[CAGE[0] + 1, 1, CAGE[1] + 1], [CAGE[0] + 2, 1, CAGE[1] + 2], [CAGE[0] + 3, 1, CAGE[1] + 1]],
            "chair": list(CHAIR),
            "desk": list(DESK),
            "front": [HOUSE[0] // 2, FLOOR + 1, -2],
            "size": list(HOUSE),
            "stand": list(STAND),
        },
    }

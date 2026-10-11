from blocks import Material, Template, coherent

YARD = (21, 9, 19)
WRECK = (12, 5, 8)
HULL_X = 11
KEEL = (5, 16)
PROFILE = {5: 0, 6: 1, 7: 2, 8: 2, 9: 2, 10: 2, 11: 2, 12: 2, 13: 2, 14: 1, 15: 1, 16: 0}
PLANKED = 10
RIBS = (11, 13, 15)
GUNWALE = 4
RAILS = (10, 12)
SLIP = (8, 14)
QUAY = 14
COTTAGE = ((1, 2), (6, 8))
DOOR = (6, 5)
JETTY = (16, 3, 16)
GROUND = Material(
    "shipyard/ground",
    [
        ("minecraft:coarse_dirt", 40),
        ("minecraft:dirt", 20),
        ("minecraft:rooted_dirt", 10),
        ("minecraft:packed_mud", 10),
        ("minecraft:gravel", 20),
    ],
)
QUAY_STONE = Material(
    "shipyard/quay",
    [
        ("minecraft:cobblestone", 35),
        ("minecraft:stone", 25),
        ("minecraft:mossy_cobblestone", 20),
        ("minecraft:andesite", 20),
    ],
)
SLIP_STONE = Material(
    "shipyard/slip",
    [
        ("minecraft:cobblestone", 40),
        ("minecraft:stone_bricks", 25),
        ("minecraft:mossy_stone_bricks", 15),
        ("minecraft:cracked_stone_bricks", 20),
    ],
)
WALL = Material("shipyard/wall", [("minecraft:spruce_planks", 70), ("minecraft:oak_planks", 30)])
TIMBER = Material("shipyard/timber", [("minecraft:spruce_log", 60), ("minecraft:oak_log", 40)])
WRECK_SAND = Material(
    "shipyard/wreck_sand", [("minecraft:sand", 60), ("minecraft:gravel", 25), ("minecraft:coarse_dirt", 15)]
)
FENCE = {"east": False, "north": False, "south": False, "west": False, "waterlogged": False}


def fence(t, pos, **connected):
    t.set(pos, "minecraft:spruce_fence", **(FENCE | connected))


def hull(t):
    left = []
    for z, width in PROFILE.items():
        cells = []
        if width == 0:
            top = 6 if z == KEEL[1] else 5
            cells += [((HULL_X, y, z), "minecraft:spruce_log", {"axis": "y"}) for y in range(1, top + 1)]
        else:
            cells.append(((HULL_X, 1, z), "minecraft:spruce_log", {"axis": "z"}))
            for x in range(HULL_X - width + 1, HULL_X + width):
                cells.append(((x, 2, z), "minecraft:dark_oak_planks", {}))
            for x in (HULL_X - width, HULL_X + width):
                cells.append(((x, 2, z), "minecraft:dark_oak_planks", {}))
                cells.append(((x, 3, z), "minecraft:spruce_planks", {}))
                cells.append(((x, GUNWALE, z), "minecraft:stripped_spruce_log", {"axis": "z"}))
        if z in (8, 12):
            cells += [((x, 3, z), "minecraft:spruce_slab", {"type": "top", "waterlogged": False}) for x in (10, 11, 12)]
        for pos, name, properties in cells:
            x, y, _ = pos
            if z <= PLANKED or width == 0 or y == 1 or (x, y) == (HULL_X, 2):
                t.set(pos, name, **properties)
                continue
            if z in RIBS and name != "minecraft:spruce_slab":
                bottom = y == 2 and abs(x - HULL_X) < width
                t.set(pos, "minecraft:stripped_spruce_log", axis="x" if bottom else "y")
            left.append([*pos, name, {key: str(value).lower() for key, value in properties.items()}])
    return left


def cottage(t):
    (x0, z0), (x1, z1) = COTTAGE
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            t.set((x, 0, z), QUAY_STONE if edge else "minecraft:spruce_planks")
            if not edge:
                continue
            corner = x in (x0, x1) and z in (z0, z1)
            for y in range(1, 4):
                t.set((x, y, z), "minecraft:spruce_log" if corner else WALL, **({"axis": "y"} if corner else {}))
    door_x, door_z = DOOR
    for half, y in (("lower", 1), ("upper", 2)):
        t.set(
            (door_x, y, door_z),
            "minecraft:spruce_door",
            facing="east",
            half=half,
            hinge="left",
            open=False,
            powered=False,
        )
    for pos, sides in (
        ((x0, 2, 3), ("north", "south")),
        ((x0, 2, 7), ("north", "south")),
        ((3, 2, z1), ("east", "west")),
        ((4, 2, z1), ("east", "west")),
        ((x1, 2, 3), ("north", "south")),
    ):
        t.set(pos, "minecraft:glass_pane", **(FENCE | {side: True for side in sides}))
    t.set((x0 + 1, 1, 5), "minecraft:campfire", facing="east", lit=False, signal_fire=False, waterlogged=False)
    for step, (west, east) in enumerate(((x0 - 1, x1 + 1), (x0, x1), (x0 + 1, x1 - 1))):
        y = 4 + step
        for z in range(z0 - 1, z1 + 2):
            t.set(
                (west, y, z),
                "minecraft:spruce_stairs",
                facing="east",
                half="bottom",
                shape="straight",
                waterlogged=False,
            )
            t.set(
                (east, y, z),
                "minecraft:spruce_stairs",
                facing="west",
                half="bottom",
                shape="straight",
                waterlogged=False,
            )
        for z in (z0, z1):
            for x in range(west + 1, east):
                t.set((x, y, z), WALL)
    for x in (3, 4):
        for z in range(z0 - 1, z1 + 2):
            t.set((x, 7, z), "minecraft:spruce_slab", type="bottom", waterlogged=False)
    for y in range(1, 8):
        t.set((x0, y, 5), QUAY_STONE)
    t.set(
        (x0, 8, 5),
        "minecraft:cobblestone_wall",
        east="none",
        north="none",
        south="none",
        up=True,
        waterlogged=False,
        west="none",
    )
    for x, z, color in ((2, 7, "white"), (3, 7, "brown"), (2, 6, "white"), (3, 6, "brown")):
        t.set((x, 1, z), f"minecraft:{color}_carpet")
    t.set((5, 1, 7), "minecraft:barrel", facing="up", open=False)
    t.set((5, 1, 3), "minecraft:crafting_table")
    t.set((2, 1, 3), "minecraft:chest", facing="east", type="single", waterlogged=False)
    t.set((3, 6, 5), "minecraft:lantern", hanging=True, waterlogged=False)


def ground_cells():
    width, _, length = YARD
    (x0, z0), (x1, z1) = COTTAGE
    for x in range(width):
        for z in range(length):
            corner = (x < 2 or x > width - 3) and z < 2
            if corner and coherent("shipyard/edge", (x, 0, z)) > 0.45:
                continue
            if SLIP[0] <= x <= SLIP[1] and z >= 3:
                yield (x, z), SLIP_STONE
            elif z >= QUAY:
                yield (x, z), QUAY_STONE
            elif x0 <= x <= x1 and z0 <= z <= z1:
                yield (x, z), None
            else:
                yield (x, z), GROUND


def yard():
    t = Template()
    width, _, length = YARD
    for (x, z), material in ground_cells():
        t.set((x, 0, z), material or GROUND)
    for x in RAILS:
        for z in range(3, length):
            t.set((x, 1, z), "minecraft:spruce_log", axis="z")
    left = hull(t)
    for z in (8, 13):
        fence(t, (SLIP[0], 1, z))
        fence(t, (SLIP[0], 2, z), east=True)
    for x in (14, 15):
        for z in range(10, 16):
            t.set((x, 1, z), "minecraft:spruce_slab", type="top", waterlogged=False)
    t.set((15, 1, 8), "minecraft:grindstone", face="floor", facing="east")
    cottage(t)
    for x in (16, 20):
        fence(t, (x, 1, 3))
    for x in range(16, 21):
        t.set((x, 2, 3), "minecraft:spruce_log", axis="x")
    for x, z in ((17, 3), (18, 3), (18, 4), (19, 2)):
        t.set((x, 1, z), "minecraft:brown_carpet")
    for x in (19, 20):
        for z in (5, 6):
            t.set((x, 1, z), "minecraft:spruce_planks")
            t.set((x, 2, z), "minecraft:spruce_slab", type="bottom", waterlogged=False)
    for x in range(16, 21):
        for y, rows in ((1, (9, 10, 11)), (2, (9, 10)), (3, (10,))):
            for z in rows:
                t.set((x, y, z), TIMBER, axis="x")
    t.set((16, 1, 15), "minecraft:cauldron")
    t.set((17, 1, 15), "minecraft:campfire", facing="north", lit=False, signal_fire=False, waterlogged=False)
    t.set((16, 1, 16), "minecraft:barrel", facing="up", open=False)
    for x in (1, 5):
        fence(t, (x, 1, 12))
        fence(t, (x, 2, 12))
    for x in range(1, 6):
        fence(t, (x, 3, 12), east=x < 5, west=x > 1)
    for x in range(2, 5):
        t.set((x, 2, 12), "minecraft:cobweb")
    for pos in ((7, 1, 15), (7, 1, 16), (7, 2, 16), (4, 1, 16), (5, 1, 16)):
        t.set(pos, "minecraft:barrel", facing="up", open=False)
    for x in (SLIP[0], SLIP[1]):
        fence(t, (x, 1, length - 1))
        fence(t, (x, 2, length - 1))
        t.set((x, 3, length - 1), "minecraft:lantern", hanging=False, waterlogged=False)
    return t, left


def wreck():
    t = Template()
    width, _, length = WRECK
    middle = length // 2
    for x in range(width):
        for z in range(length):
            if abs(z - middle) <= 2 or coherent("shipyard/wreck_edge", (x, 0, z)) > 0.4:
                t.set((x, 0, z), WRECK_SAND)
    for x in range(1, 10):
        if x not in (5, 6):
            t.set((x, 1, middle), "minecraft:spruce_log", axis="x")
    for y in range(1, 4):
        t.set((0, y, middle), "minecraft:spruce_log", axis="y")
    t.set((10, 1, middle), "minecraft:spruce_slab", type="bottom", waterlogged=False)
    for x, high, low in ((2, 3, 2), (4, 2, 1), (7, 3, 1), (9, 2, 2)):
        for y in range(1, high + 1):
            t.set((x, y, middle - 2), "minecraft:stripped_spruce_log", axis="y")
        for y in range(1, low + 1):
            t.set((x, y, middle + 2), "minecraft:stripped_spruce_log", axis="y")
    for x in range(1, 5):
        t.set((x, 1, middle - 1), "minecraft:dark_oak_planks")
    for x in (7, 8):
        t.set((x, 1, middle + 1), "minecraft:dark_oak_planks")
    for x in range(2, 9):
        t.set((x, 1, length - 1), "minecraft:spruce_log", axis="x")
    for x in (9, 10):
        t.set((x, 1, length - 1), "minecraft:white_carpet")
    t.set((3, 1, middle + 1), "minecraft:chest", facing="north", type="single", waterlogged=False)
    return t


def rest(left):
    t = Template()
    for x, y, z, name, properties in left:
        t.set((x, y, z), name, **properties)
    return t


def positions():
    width, _, length = YARD
    return {
        "wreck": {
            "keel": [4, 1, WRECK[2] // 2],
            "log": [8, 1, WRECK[2] // 2 - 1],
            "size": list(WRECK),
            "tools": [3, 1, WRECK[2] // 2 + 1],
        },
        "yard": {
            "edda": [SLIP[0] - 1, 1, 10, 1, 0],
            "ground": [width, length],
            "hull": [HULL_X, 1, 10],
            "jetty": [JETTY[0], length, JETTY[1], JETTY[2]],
            "launch": [JETTY[0] + 1, 1, length - 1, 0, 1],
            "worn": [[x, z] for (x, z), material in ground_cells() if material is GROUND],
            "quay": QUAY,
            "rails": list(RAILS),
            "tamsin": [SLIP[1] - 1, 1, length - 2, 0, 1],
            "size": list(YARD),
        },
    }

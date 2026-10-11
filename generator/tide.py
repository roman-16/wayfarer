from blocks import Material, Template, coherent

VILLAGE = (25, 10, 17)
HUTS = [(2, 2), (10, 1), (18, 3)]
HUT = (5, 5)
STILTS = 2
BOATS = [(4, 13), (12, 14), (19, 12)]
RACKS = [(8, 9), (15, 8)]
FENCE = {"east": False, "north": False, "south": False, "west": False, "waterlogged": False}
SAND = Material("tide/sand", [("minecraft:sand", 70), ("minecraft:gravel", 15), ("minecraft:sandstone", 15)])
DRIFT = Material(
    "tide/drift",
    [("minecraft:stripped_spruce_log", 40), ("minecraft:stripped_oak_log", 35), ("minecraft:stripped_birch_log", 25)],
)
PLANK = Material(
    "tide/plank", [("minecraft:spruce_planks", 45), ("minecraft:dark_oak_planks", 35), ("minecraft:oak_planks", 20)]
)
THATCH = Material("tide/thatch", [("minecraft:dark_oak_planks", 55), ("minecraft:spruce_planks", 45)])


def fence(t, pos, **connected):
    t.set(pos, "minecraft:spruce_fence", **(FENCE | connected))


def hut(t, x0, z0):
    width, depth = HUT
    for dx in (0, width - 1):
        for dz in (0, depth - 1):
            for y in range(1, STILTS + 1):
                t.set((x0 + dx, y, z0 + dz), DRIFT, axis="y")
    floor = STILTS + 1
    for dx in range(width):
        for dz in range(depth):
            t.set((x0 + dx, floor, z0 + dz), PLANK)
            edge = dx in (0, width - 1) or dz in (0, depth - 1)
            if edge:
                corner = dx in (0, width - 1) and dz in (0, depth - 1)
                for y in range(floor + 1, floor + 4):
                    t.set((x0 + dx, y, z0 + dz), DRIFT if corner else PLANK, **({"axis": "y"} if corner else {}))
    door = (x0 + width // 2, z0 + depth - 1)
    for half, y in (("lower", floor + 1), ("upper", floor + 2)):
        t.set(
            (door[0], y, door[1]),
            "minecraft:spruce_door",
            facing="south",
            half=half,
            hinge="left",
            open=False,
            powered=False,
        )
    t.set((x0, floor + 2, z0 + depth // 2), "minecraft:glass_pane", **(FENCE | {"north": True, "south": True}))
    t.set(
        (x0 + width - 1, floor + 2, z0 + depth // 2), "minecraft:glass_pane", **(FENCE | {"north": True, "south": True})
    )
    for dz in range(-1, depth + 1):
        for dx in range(-1, width + 1):
            rise = min(dx + 1, width - dx)
            y = floor + 3 + min(rise, 2)
            if rise >= 3:
                t.set((x0 + dx, y, z0 + dz), THATCH)
            else:
                t.set(
                    (x0 + dx, y, z0 + dz),
                    "minecraft:dark_oak_stairs",
                    facing="east" if dx < width // 2 else "west",
                    half="bottom",
                    shape="straight",
                    waterlogged=False,
                )
    for dz in (0, depth - 1):
        for dx in range(1, width - 1):
            t.set((x0 + dx, floor + 4, z0 + dz), PLANK)
    t.set(
        (door[0], STILTS, door[1] + 1),
        "minecraft:spruce_stairs",
        facing="north",
        half="bottom",
        shape="straight",
        waterlogged=False,
    )
    t.set(
        (door[0], 1, door[1] + 2),
        "minecraft:spruce_stairs",
        facing="north",
        half="bottom",
        shape="straight",
        waterlogged=False,
    )
    t.set((x0 + 1, floor + 1, z0 + 1), "minecraft:barrel", facing="up", open=False)
    t.set((x0 + width - 2, floor + 1, z0 + 1), "minecraft:light_blue_carpet")
    t.set((x0 + width - 2, floor + 1, z0 + 2), "minecraft:light_blue_carpet")
    t.set((x0 + 2, floor + 4, z0 + 2), "minecraft:lantern", hanging=True, waterlogged=False)
    return door


def boat(t, x0, z0):
    for dx in range(4):
        t.set((x0 + dx, 1, z0), "minecraft:dark_oak_slab", type="top", waterlogged=False)
        t.set((x0 + dx, 1, z0 + 1), "minecraft:dark_oak_slab", type="top", waterlogged=False)
    t.set((x0 - 1, 1, z0), "minecraft:dark_oak_stairs", facing="east", half="top", shape="straight", waterlogged=False)
    t.set(
        (x0 - 1, 1, z0 + 1), "minecraft:dark_oak_stairs", facing="east", half="top", shape="straight", waterlogged=False
    )
    t.set((x0 + 4, 1, z0), "minecraft:dark_oak_stairs", facing="west", half="top", shape="straight", waterlogged=False)
    t.set(
        (x0 + 4, 1, z0 + 1), "minecraft:dark_oak_stairs", facing="west", half="top", shape="straight", waterlogged=False
    )
    t.set(
        (x0 + 1, 2, z0),
        "minecraft:oak_trapdoor",
        facing="south",
        half="bottom",
        open=False,
        powered=False,
        waterlogged=False,
    )


def rack(t, x0, z0):
    for dx in (0, 3):
        fence(t, (x0 + dx, 1, z0))
        fence(t, (x0 + dx, 2, z0))
    for dx in range(4):
        fence(t, (x0 + dx, 3, z0), east=dx < 3, west=dx > 0)
    t.set((x0 + 1, 2, z0), "minecraft:cobweb")
    t.set((x0 + 2, 2, z0), "minecraft:cobweb")


def village():
    t = Template()
    width, _, length = VILLAGE
    for x in range(width):
        for z in range(length):
            if (x < 2 or x > width - 3 or z < 1 or z > length - 2) and coherent("tide/edge", (x, 0, z)) > 0.5:
                continue
            t.set((x, 0, z), SAND)
    doors = [hut(t, x, z) for x, z in HUTS]
    for x, z in BOATS:
        boat(t, x, z)
    for x, z in RACKS:
        rack(t, x, z)
    for pos in ((7, 1, 4), (8, 1, 4), (16, 1, 6)):
        t.set(pos, "minecraft:barrel", facing="up", open=False)
    t.set((14, 1, 11), "minecraft:cauldron")
    return t, doors


def positions(doors):
    width, _, length = VILLAGE
    return {
        "morwen": [doors[1][0], 1, doors[1][1] + 3, 0, 1],
        "size": list(VILLAGE),
        "wardens": [[width // 2 - 2, 1, length - 2, 0, 1], [width // 2 + 2, 1, length - 2, 0, 1]],
    }

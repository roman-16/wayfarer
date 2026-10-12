import math

from blocks import AIR, Material, Room, Template, coherent, noise
from halls import FALSE_WALL

WATER = 13
GROUND = WATER
FLOOR = GROUND + 2
LANDING = (13, 14)
WELL = (15, 16)
TUNNEL_FLOOR = -4
TUNNEL = (17, 40)
POCKET = (27, 28)
POOL = (6, 41)
POOL_HALF = 3
SHAFT_HALF = 1
BASIN = 3
STAIR_X = -2
STAIR_FROM = 6
STAIR_TO = 28
CELLAR = ((3, 5), (29, 31))
CELLAR_FLOOR = 9
HATCH = (4, 29)
HUT = ((3, 9), (28, 34))
PORCH = (35, 38)
CHIMNEY = (10, 30)
RIM = 7
MARKS = (20, 25, 32, 37)
LOW = (-4, -6, 0)
HIGH = (14, 30, 49)

MUD = Material(
    "marsh/mud",
    [
        ("minecraft:mud", 35),
        ("minecraft:packed_mud", 20),
        ("minecraft:muddy_mangrove_roots", 20),
        ("minecraft:mud_bricks", 10),
        ("minecraft:rooted_dirt", 15),
    ],
)
BED = Material(
    "marsh/bed",
    [("minecraft:mud", 60), ("minecraft:muddy_mangrove_roots", 25), ("minecraft:packed_mud", 15)],
)
STEPS = Material(
    "marsh/steps",
    [("minecraft:mud_bricks", 50), ("minecraft:packed_mud", 30), ("minecraft:mossy_cobblestone", 20)],
    "minecraft:mud_brick_stairs",
    "minecraft:mud_brick_slab",
)
BANK = Material(
    "marsh/bank",
    [
        ("minecraft:grass_block", 40),
        ("minecraft:mud", 25),
        ("minecraft:moss_block", 15),
        ("minecraft:muddy_mangrove_roots", 10),
        ("minecraft:coarse_dirt", 10),
    ],
    ground=GROUND,
)
TRODDEN = Material(
    "marsh/trodden",
    [("minecraft:packed_mud", 40), ("minecraft:coarse_dirt", 35), ("minecraft:mud", 25)],
    ground=GROUND,
)
WALLS = Material(
    "marsh/walls",
    [("minecraft:spruce_planks", 60), ("minecraft:stripped_spruce_log", 15), ("minecraft:dark_oak_planks", 25)],
)
FOOTING = Material(
    "marsh/footing",
    [("minecraft:mossy_cobblestone", 45), ("minecraft:mud_bricks", 35), ("minecraft:packed_mud", 20)],
)
WALL = {"east": "none", "north": "none", "south": "none", "up": True, "waterlogged": False, "west": "none"}
SHELF = {f"slot_{index}_occupied": index in (0, 2, 3) for index in range(6)}
GROWTH = ["minecraft:short_grass", "minecraft:fern", "minecraft:short_grass", "minecraft:short_grass"]


def way():
    room = Room(size(), MUD, BED, decay=0.3)
    landing(room)
    well(room)
    tunnel(room)
    pocket(room)
    pool(room)
    stair(room)
    cellar(room)
    return room


def size():
    return [HIGH[axis] - LOW[axis] + 1 for axis in range(3)]


def at(pos):
    return (pos[0] - LOW[0], pos[1] - LOW[1], pos[2] - LOW[2])


def put(room, pos, name, **properties):
    room.set(at(pos), name, **properties)


def open_air(room, pos):
    room.air(at(pos))


def flood(room, pos):
    room.water(at(pos))


def landing(room):
    for z in range(LANDING[0], LANDING[1] + 1):
        for x in (5, 6, 7):
            put(room, (x, 1, z), STEPS)
            for y in (2, 3, 4):
                open_air(room, (x, y, z))


def well(room):
    for z in range(WELL[0], WELL[1] + 1):
        for x in (5, 6, 7):
            for y in range(TUNNEL_FLOOR + 1, 2):
                flood(room, (x, y, z))
            for y in (2, 3, 4):
                open_air(room, (x, y, z))


def bend(z):
    return round(math.sin((z - TUNNEL[0]) / 4.0) * 1.2)


def tunnel(room):
    for z in range(TUNNEL[0], TUNNEL[1] + 1):
        shift = bend(z)
        for x in range(4 + shift, 9 + shift):
            edge = x in (4 + shift, 8 + shift)
            if edge and coherent("marsh/tunnel-wide", (x, 0, z)) < 0.55:
                continue
            put(room, (x, TUNNEL_FLOOR, z), BED)
            for y in range(TUNNEL_FLOOR + 1, TUNNEL_FLOOR + 4):
                flood(room, (x, y, z))
        for x in range(4 + shift, 9 + shift):
            for y in range(TUNNEL_FLOOR + 1, TUNNEL_FLOOR + 4):
                pos = (x, y, z)
                if at(pos) not in room.open or abs(x - 6 - shift) == 0 and y < TUNNEL_FLOOR + 3:
                    continue
                hang = y == TUNNEL_FLOOR + 3 and coherent("marsh/roots", pos) < 0.5
                side = abs(x - 6 - shift) >= 2 and coherent("marsh/roots-side", pos) < 0.45
                if hang or side:
                    put(room, pos, "minecraft:mangrove_roots", waterlogged=True)


def pocket(room):
    for z in range(POCKET[0], POCKET[1] + 1):
        for x in (5, 6):
            shift = bend(z)
            for y in range(TUNNEL_FLOOR + 4, TUNNEL_FLOOR + 8):
                if y <= TUNNEL_FLOOR + 5:
                    flood(room, (x + shift, y, z))
                else:
                    open_air(room, (x + shift, y, z))


def pool(room):
    cx, cz = POOL
    for x in range(cx - POOL_HALF, cx + POOL_HALF + 1):
        for z in range(cz - POOL_HALF, cz + POOL_HALF + 1):
            reach = math.hypot(x - cx, z - cz) + (coherent("marsh/pool", (x, 0, z)) - 0.5) * 1.2
            if reach > POOL_HALF + 0.4:
                continue
            depth = BASIN if reach > SHAFT_HALF + 1 else WATER - TUNNEL_FLOOR
            for y in range(WATER - depth + 1, WATER + 1):
                flood(room, (x, y, z))
            put(room, (x, WATER - depth, z), BED)
    for x in range(cx - SHAFT_HALF, cx + SHAFT_HALF + 1):
        for z in range(TUNNEL[1], cz + SHAFT_HALF + 1):
            for y in range(TUNNEL_FLOOR + 1, WATER + 1):
                flood(room, (x, y, z))
    for x in range(cx - SHAFT_HALF - 1, cx + SHAFT_HALF + 2):
        for z in range(cz - SHAFT_HALF - 1, cz + SHAFT_HALF + 2):
            for y in range(WATER - BASIN - 4, WATER - BASIN + 1):
                if at((x, y, z)) in room.open and coherent("marsh/shaft-roots", (x, y, z)) < 0.3:
                    put(room, (x, y, z), "minecraft:mangrove_roots", waterlogged=True)
    for x in range(cx - POOL_HALF - 1, cx + POOL_HALF + 2):
        for z in range(cz - POOL_HALF - 1, cz + POOL_HALF + 2):
            put(room, (x, WATER + 1, z), AIR)


def rise(z):
    return 2 + math.floor((z - STAIR_FROM) * (CELLAR_FLOOR + 1 - 2) / (STAIR_TO - STAIR_FROM))


def stair(room):
    for x in (-1, STAIR_X):
        put(room, (x, 1, STAIR_FROM), STEPS)
        open_air(room, (x, 2, STAIR_FROM))
        open_air(room, (x, 3, STAIR_FROM))
    for z in range(STAIR_FROM + 1, STAIR_TO + 1):
        here = rise(z)
        before = rise(z - 1)
        if here > before:
            put(
                room,
                (STAIR_X, here - 1, z),
                "minecraft:mud_brick_stairs",
                facing="south",
                half="bottom",
                shape="straight",
                waterlogged=False,
            )
            put(room, (STAIR_X, here - 2, z), STEPS)
            open_air(room, (STAIR_X, here, z))
            open_air(room, (STAIR_X, here + 1, z))
        else:
            put(room, (STAIR_X, here - 1, z), STEPS)
            open_air(room, (STAIR_X, here, z))
            open_air(room, (STAIR_X, here + 1, z))
        open_air(room, (STAIR_X, here + 2, z))
    top = rise(STAIR_TO)
    for z in (STAIR_TO + 1, STAIR_TO + 2):
        put(room, (STAIR_X, top - 1, z), STEPS)
        for y in (top, top + 1):
            open_air(room, (STAIR_X, y, z))
    for x in range(STAIR_X + 1, CELLAR[0][0] - 1):
        put(room, (x, top - 1, CELLAR[1][0] + 1), STEPS)
        for y in (top, top + 1):
            open_air(room, (x, y, CELLAR[1][0] + 1))


def cellar(room):
    (x0, x1), (z0, z1) = CELLAR
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            put(room, (x, CELLAR_FLOOR, z), STEPS)
            for y in (CELLAR_FLOOR + 1, CELLAR_FLOOR + 2):
                open_air(room, (x, y, z))
    for y in (CELLAR_FLOOR + 1, CELLAR_FLOOR + 2):
        put(room, (x0 - 1, y, z0 + 1), FALSE_WALL)
    hx, hz = HATCH
    for y in range(CELLAR_FLOOR + 1, GROUND + 1):
        put(room, (hx, y, hz), "minecraft:ladder", facing="south", waterlogged=False)
        for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            if at((hx + dx, y, hz + dz)) not in room.t.blocks:
                put(room, (hx + dx, y, hz + dz), MUD)
    put(
        room,
        (hx, GROUND + 1, hz),
        "minecraft:mangrove_trapdoor",
        facing="south",
        half="top",
        open=False,
        powered=False,
        waterlogged=False,
    )
    put(room, (x1, CELLAR_FLOOR + 1, z1), "minecraft:barrel", facing="up", open=False)
    put(room, (x1, CELLAR_FLOOR + 1, z0), "minecraft:decorated_pot", facing="west", cracked=False, waterlogged=False)
    put(room, (x0, CELLAR_FLOOR + 1, z1), "minecraft:decorated_pot", facing="north", cracked=False, waterlogged=False)
    for x in range(x0, x1 + 1):
        if noise("marsh/cellar-roots", x) < 0.6:
            put(room, (x, CELLAR_FLOOR + 3, z0), "minecraft:rooted_dirt")
            put(room, (x, CELLAR_FLOOR + 2, z0), "minecraft:hanging_roots", waterlogged=False)


def hut(carved):
    t = Template()
    bank(t, carved)
    footing(t)
    house(t)
    porch(t)
    annex(t)
    roof(t)
    chimney(t)
    inside(t)
    shore(t)
    clues(t)
    growth(t)
    return shifted(t)


def carved(room):
    return {(x + LOW[0], y + LOW[1], z + LOW[2]) for x, y, z in room.open}


def shifted(t):
    moved = Template()
    for pos, block in t.blocks.items():
        moved.blocks[at(pos)] = block
    return moved


def in_pool(x, z):
    cx, cz = POOL
    return math.hypot(x - cx, z - cz) + (coherent("marsh/pool", (x, 0, z)) - 0.5) * 1.2 <= POOL_HALF + 0.4


def bank(t, carved):
    cx, cz = POOL
    (x0, x1), (z0, z1) = HUT
    middle = ((x0 + x1) / 2, (z0 + cz) / 2)
    for x in range(LOW[0], HIGH[0] + 1):
        for z in range(LOW[2] + 20, HIGH[2] + 1):
            if in_pool(x, z) or (x, z) == HATCH:
                continue
            reach = math.hypot((x - middle[0]) / (RIM + 2), (z - middle[1]) / (RIM + 8))
            if reach + (coherent("marsh/bank-edge", (x, 0, z)) - 0.5) * 0.6 > 1:
                continue
            path = abs(x - (x1 + 2)) <= 1 and z >= PORCH[0] - 1 or abs(z - (cz + POOL_HALF + 2)) <= 1 and x >= cx
            t.set((x, GROUND, z), TRODDEN if path else BANK)
            for y in range(GROUND - 4, GROUND):
                if (x, y, z) not in carved:
                    t.set((x, y, z), MUD)


def footing(t):
    (x0, x1), (z0, z1) = HUT
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if (x, z) == HATCH:
                continue
            edge = x in (x0, x1) or z in (z0, z1)
            t.set((x, GROUND + 1, z), FOOTING if edge else MUD)


def house(t):
    (x0, x1), (z0, z1) = HUT
    door = (x0 + x1) // 2
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if (x, z) != HATCH and (x * 3 + z) % 5:
                t.set((x, FLOOR, z), "minecraft:spruce_planks")
            elif (x, z) != HATCH:
                t.set((x, FLOOR, z), "minecraft:stripped_spruce_log", axis="x")
            corner = x in (x0, x1) and z in (z0, z1)
            post = x in (x0, x1) and z == (z0 + z1) // 2 or z in (z0, z1) and x in (door - 2, door + 2)
            wall = x in (x0, x1) or z in (z0, z1)
            for y in range(FLOOR + 1, FLOOR + 5):
                if corner or post and wall:
                    t.set((x, y, z), "minecraft:stripped_dark_oak_log", axis="y")
                elif wall:
                    t.set((x, y, z), WALLS if y < FLOOR + 4 else "minecraft:dark_oak_planks")
                else:
                    t.set((x, y, z), AIR)
    t.set(
        (door, FLOOR + 1, z1),
        "minecraft:spruce_door",
        facing="south",
        half="lower",
        hinge="left",
        open=False,
        powered=False,
    )
    t.set(
        (door, FLOOR + 2, z1),
        "minecraft:spruce_door",
        facing="south",
        half="upper",
        hinge="left",
        open=False,
        powered=False,
    )
    windows = (
        (x0, z0 + 2, "west"),
        (x0, z1 - 2, "west"),
        (x1, z0 + 2, "east"),
        (door - 1, z1, "south"),
        (door + 1, z0, "north"),
    )
    for x, z, facing in windows:
        t.set((x, FLOOR + 2, z), AIR)
        dx, dz = {"west": (-1, 0), "east": (1, 0), "south": (0, 1), "north": (0, -1)}[facing]
        t.set(
            (x + dx, FLOOR + 3, z + dz),
            "minecraft:spruce_trapdoor",
            facing=facing,
            half="top",
            open=True,
            powered=False,
            waterlogged=False,
        )
        t.set(
            (x + dx, FLOOR + 1, z + dz),
            "minecraft:spruce_trapdoor",
            facing=facing,
            half="top",
            open=False,
            powered=False,
            waterlogged=False,
        )


def porch(t):
    (x0, x1), _ = HUT
    door = (x0 + x1) // 2
    for x in range(x0, x1 + 1):
        for z in range(PORCH[0], PORCH[1] + 1):
            t.set(
                (x, FLOOR, z),
                "minecraft:spruce_slab" if (x + z) % 4 == 0 else "minecraft:spruce_planks",
                type="top",
                waterlogged=False,
            )
    for x in (x0, door, x1):
        for y in range(GROUND - 3, FLOOR):
            t.set((x, y, PORCH[1]), "minecraft:mangrove_log", axis="y")
        for y in range(FLOOR + 1, FLOOR + 4):
            t.set(
                (x, y, PORCH[1]),
                "minecraft:spruce_fence",
                east=False,
                north=False,
                south=False,
                west=False,
                waterlogged=False,
            )
    rail = {"north": False, "south": False, "waterlogged": False}
    for x in range(x0 + 1, x1):
        if x != door:
            t.set((x, FLOOR + 1, PORCH[1]), "minecraft:spruce_fence", east=True, west=True, **rail)
    for z in range(PORCH[0], PORCH[1]):
        t.set(
            (x0, FLOOR + 1, z),
            "minecraft:spruce_fence",
            east=False,
            north=True,
            south=True,
            west=False,
            waterlogged=False,
        )
    for x in range(x0 - 1, x1 + 2):
        for z in range(PORCH[0], PORCH[1] + 1):
            t.set((x, FLOOR + 4, z), "minecraft:dark_oak_slab", type="bottom", waterlogged=False)
    t.set(
        (x1 + 1, FLOOR, PORCH[0] + 1),
        "minecraft:spruce_stairs",
        facing="west",
        half="bottom",
        shape="straight",
        waterlogged=False,
    )
    t.set((x1 + 1, FLOOR - 1, PORCH[0] + 1), "minecraft:mud_bricks")
    t.set(
        (x1 + 2, FLOOR - 1, PORCH[0] + 1),
        "minecraft:mud_brick_stairs",
        facing="west",
        half="bottom",
        shape="straight",
        waterlogged=False,
    )
    t.set(
        (x1 + 1, FLOOR, PORCH[0]),
        "minecraft:spruce_fence",
        east=False,
        north=False,
        south=False,
        west=False,
        waterlogged=False,
    )
    t.set((x0 + 1, FLOOR + 1, PORCH[0]), "minecraft:cauldron")
    t.set((x0 + 2, FLOOR + 1, PORCH[0]), "minecraft:barrel", facing="up", open=False)
    t.set((x0 + 2, FLOOR + 2, PORCH[0]), "minecraft:flower_pot")
    t.set((x1 - 1, FLOOR + 3, PORCH[0]), "minecraft:lantern", hanging=True, waterlogged=False)


def annex(t):
    (x0, _), (z0, z1) = HUT
    for z in range(z0 + 1, z1):
        for x in range(x0 - 3, x0):
            t.set((x, GROUND + 1, z), TRODDEN)
            t.set((x, GROUND + 2, z), AIR)
    for z in (z0 + 1, z1 - 1):
        for y in range(GROUND + 2, GROUND + 5):
            t.set(
                (x0 - 3, y, z),
                "minecraft:spruce_fence",
                east=False,
                north=False,
                south=False,
                west=False,
                waterlogged=False,
            )
    for z in range(z0 + 1, z1):
        t.set((x0 - 3, GROUND + 5, z), "minecraft:stripped_spruce_log", axis="z")
        for x in range(x0 - 3, x0):
            t.set(
                (x, GROUND + 6 if x == x0 - 1 else GROUND + 5 + (x - x0 + 3) // 2, z),
                "minecraft:spruce_slab",
                type="bottom",
                waterlogged=False,
            )
    for z in range(z0 + 2, z1 - 1):
        t.set((x0 - 2, GROUND + 4, z), "minecraft:hanging_roots", waterlogged=False)
        t.set((x0 - 2, GROUND + 5, z), "minecraft:stripped_spruce_log", axis="z")
    t.set((x0 - 1, GROUND + 2, z1 - 1), "minecraft:barrel", facing="up", open=False)
    t.set((x0 - 1, GROUND + 2, z0 + 1), "minecraft:composter", level=3)
    t.set((x0 - 2, GROUND + 2, z0 + 1), "minecraft:hay_block", axis="z")


def roof(t):
    (x0, x1), (z0, z1) = HUT
    middle = (z0 + z1) / 2
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            distance = abs(z - middle)
            y = FLOOR + 5 + round(min(4, (middle - z0 + 1 - distance)))
            facing = "south" if z > middle else "north"
            mossy = coherent("marsh/roof-moss", (x, y, z)) < 0.3
            if distance < 0.6:
                t.set(
                    (x, y, z),
                    "minecraft:mossy_cobblestone_slab" if mossy else "minecraft:dark_oak_slab",
                    type="bottom",
                    waterlogged=False,
                )
            else:
                name = "minecraft:mossy_cobblestone_stairs" if mossy else "minecraft:dark_oak_stairs"
                t.set((x, y, z), name, facing=facing, half="bottom", shape="straight", waterlogged=False)
            if x0 <= x <= x1 and z0 <= z <= z1:
                for fill in range(FLOOR + 5, y):
                    t.set((x, fill, z), "minecraft:dark_oak_planks" if x in (x0, x1) else AIR)
    for x in (x0, x1):
        t.set(
            (x, FLOOR + 6, round(middle)),
            "minecraft:spruce_trapdoor",
            facing="west" if x == x0 else "east",
            half="top",
            open=True,
            powered=False,
            waterlogged=False,
        )
        t.set((x, FLOOR + 6, round(middle)), AIR)


def chimney(t):
    x, z = CHIMNEY
    for y in range(GROUND + 1, FLOOR + 10):
        t.set(
            (x, y, z), "minecraft:mud_bricks" if coherent("marsh/chimney", (x, y, z)) < 0.7 else "minecraft:packed_mud"
        )
        t.set(
            (x, y, z + 1),
            "minecraft:mud_bricks" if y < FLOOR + 8 else "minecraft:mud_brick_wall",
            **({} if y < FLOOR + 8 else WALL),
        )
    t.set((x, FLOOR + 10, z), "minecraft:campfire", facing="south", lit=True, signal_fire=False, waterlogged=False)


def inside(t):
    (x0, x1), (z0, z1) = HUT
    t.set((x1 - 1, FLOOR + 1, z0 + 2), "minecraft:cauldron")
    t.set((x1 - 1, FLOOR + 1, z0 + 3), "minecraft:smoker", facing="west", lit=False)
    t.set((x1 - 1, FLOOR + 1, z0 + 1), "minecraft:barrel", facing="up", open=False)
    t.set((x1 - 1, FLOOR + 2, z0 + 1), "minecraft:flower_pot")
    t.set((x0 + 1, FLOOR + 1, z1 - 1), "minecraft:barrel", facing="east", open=False)
    t.set((x0 + 1, FLOOR + 1, z1 - 2), "minecraft:chiseled_bookshelf", facing="east", **SHELF)
    for x in range(x0 + 2, x0 + 4):
        t.set((x, FLOOR + 1, z0 + 1), "minecraft:brown_wool")
    t.set((x0 + 2, FLOOR + 2, z0 + 1), "minecraft:white_carpet")
    t.set((x0 + 3, FLOOR + 2, z0 + 1), "minecraft:green_carpet")
    middle = (z0 + z1) // 2
    for z in (middle - 2, middle, middle + 2):
        for x in range(x0 + 1, x1):
            t.set((x, FLOOR + 5, z), "minecraft:stripped_spruce_log", axis="x")
    for x in range(x0 + 2, x1 - 1, 2):
        t.set((x, FLOOR + 4, middle), "minecraft:hanging_roots", waterlogged=False)
    t.set((x1 - 2, FLOOR + 4, middle + 2), "minecraft:lantern", hanging=True, waterlogged=False)


def shore(t):
    cx, cz = POOL
    for x in range(cx - POOL_HALF - 1, cx + POOL_HALF + 2):
        for z in range(cz - POOL_HALF - 1, cz + POOL_HALF + 2):
            if in_pool(x, z) or (x, GROUND, z) not in t.blocks or (x, GROUND + 1, z) in t.blocks:
                continue
            if noise("marsh/shore", x, z) < 0.5:
                t.set((x, GROUND, z), "minecraft:mud")
            else:
                t.set((x, GROUND, z), "minecraft:muddy_mangrove_roots", axis="y")
            reed = noise("marsh/reeds", x, z)
            if reed < 0.3:
                t.set((x, GROUND + 1, z), "minecraft:tall_grass", half="lower")
                t.set((x, GROUND + 2, z), "minecraft:tall_grass", half="upper")
            elif reed < 0.45:
                t.set((x, GROUND + 1, z), "minecraft:fern")
    for x, z in ((cx - 2, cz + 2), (cx + 1, cz - 2), (cx - 1, cz + 3)):
        if in_pool(x, z):
            t.set((x, WATER + 1, z), "minecraft:lily_pad")


def clues(t):
    spots = layout_clues()
    t.set(spots["orchid"], "minecraft:blue_orchid")
    t.set(spots["spawn"], "minecraft:frogspawn")
    t.set(spots["dip"], "minecraft:mud_brick_slab", type="bottom", waterlogged=False)
    t.set(tuple(places()["fisher"]), "minecraft:white_carpet")


def growth(t):
    for (x, y, z), (name, _, _) in list(t.blocks.items()):
        if y != GROUND or name not in ("minecraft:grass_block", "minecraft:moss_block") or (x, y + 1, z) in t.blocks:
            continue
        if noise("marsh/growth", x, z) < 0.35:
            t.set((x, y + 1, z), GROWTH[int(noise("marsh/growth-kind", x, z) * len(GROWTH))])


def places():
    (x0, x1), _ = HUT
    middle = (x0 + x1) // 2
    return {
        "cellar": [HATCH[0], CELLAR_FLOOR + 1, CELLAR[1][0] + 1],
        "fisher": [x1 - 1, FLOOR + 1, PORCH[0] + 1],
        "pool": [POOL[0], WATER, POOL[1]],
        "rue": [middle, FLOOR + 1, PORCH[1] - 1],
        "sedge": [middle - 2, FLOOR + 1, PORCH[1] - 1],
        "tansy": [middle + 2, FLOOR + 1, PORCH[1] - 1],
    }


def layout_clues():
    return {
        "dip": (POOL[0] + POOL_HALF + 1, GROUND, POOL[1]),
        "orchid": (POOL[0] - POOL_HALF - 2, GROUND + 1, POOL[1] + 2),
        "spawn": (POOL[0] + 2, WATER + 1, POOL[1] + 2),
    }


def ground(carved):
    t = Template()
    bank(t, carved)
    return sorted([x, z] for x, y, z in t.blocks if y == GROUND)


def layout(hall, carved):
    (x0, _), (z0, _) = CELLAR
    return {
        "ground": ground(carved),
        "rise": FLOOR + 8 - GROUND,
        "clues": {name: list(pos) for name, pos in layout_clues().items()},
        "false_walls": [hall["hidden"], [hall["hidden"][0], hall["hidden"][1] + 1, hall["hidden"][2]]]
        + [[x0 - 1, y, z0 + 1] for y in (CELLAR_FLOOR + 1, CELLAR_FLOOR + 2)],
        "marks": [[6 + bend(z), TUNNEL_FLOOR, z] for z in MARKS],
        "origin": list(LOW),
        "stone": hall["stone"],
        "places": places(),
        "under": WATER - 1,
        "ward": {"from": list(LOW), "to": [HIGH[0], GROUND, HIGH[2]]},
    }

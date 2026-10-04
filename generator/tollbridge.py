from blocks import AIR, Material, Template, candle, coherent, noise

STONE = Material(
    "tollbridge/stone",
    [
        ("minecraft:cobblestone", 40),
        ("minecraft:stone_bricks", 22),
        ("minecraft:cracked_stone_bricks", 14),
        ("minecraft:andesite", 12),
        ("minecraft:tuff", 12),
    ],
    ground=0,
    fade=3,
)
SOOT = Material(
    "tollbridge/soot",
    [
        ("minecraft:cobbled_deepslate", 30),
        ("minecraft:tuff", 25),
        ("minecraft:cracked_stone_bricks", 20),
        ("minecraft:cobblestone", 15),
        ("minecraft:deepslate_bricks", 10),
    ],
)
QUOIN = Material("tollbridge/quoin", [("minecraft:stone_bricks", 70), ("minecraft:cracked_stone_bricks", 30)])
PLASTER = Material(
    "tollbridge/plaster",
    [
        ("minecraft:light_gray_terracotta", 35),
        ("minecraft:white_terracotta", 15),
        ("minecraft:gray_terracotta", 30),
        ("minecraft:black_terracotta", 20),
    ],
    ground=4,
    fade=4,
)
ASH = Material(
    "tollbridge/ash",
    [
        ("minecraft:coarse_dirt", 30),
        ("minecraft:gravel", 22),
        ("minecraft:gray_concrete_powder", 18),
        ("minecraft:tuff", 12),
        ("minecraft:dirt", 18),
    ],
    ground=0,
    fade=1,
)
STREET = Material(
    "tollbridge/street",
    [
        ("minecraft:dirt_path", 35),
        ("minecraft:gravel", 20),
        ("minecraft:coarse_dirt", 25),
        ("minecraft:cobblestone", 20),
    ],
    ground=0,
    fade=1,
)
SQUARE = Material(
    "tollbridge/square",
    [
        ("minecraft:cobblestone", 45),
        ("minecraft:stone_bricks", 15),
        ("minecraft:gravel", 15),
        ("minecraft:andesite", 15),
        ("minecraft:coarse_dirt", 10),
    ],
    ground=0,
    fade=1,
)
CHAR = ["minecraft:dark_oak_log", "minecraft:stripped_dark_oak_log"]
CHAR_PLANKS = Material(
    "tollbridge/planks",
    [("minecraft:dark_oak_planks", 55), ("minecraft:spruce_planks", 20), ("minecraft:coarse_dirt", 25)],
)
FIRE = {"age": 0, "east": False, "north": False, "south": False, "up": False, "west": False}
TOWER = 7
TOWER_HEIGHT = 19
CELLAR = 4
ROTATE_FACING = {"north": "east", "east": "south", "south": "west", "west": "north"}


def char(t, pos, axis="y"):
    t.set(pos, CHAR[int(noise("tollbridge/char", *pos) * 2)], axis=axis)


def burning(t, pos):
    t.set(pos, "minecraft:fire", **FIRE)


def smoulder(t, pos):
    t.set(pos, "minecraft:campfire", facing="south", lit=True, signal_fire=False, waterlogged=False)


MARGIN = 2


def apron(t, width, depth, seed):
    for x in range(width + 2 * MARGIN):
        for z in range(depth + 2 * MARGIN):
            inside = MARGIN <= x < width + MARGIN and MARGIN <= z < depth + MARGIN
            if inside:
                continue
            reach = min(x, z, width + 2 * MARGIN - 1 - x, depth + 2 * MARGIN - 1 - z)
            if coherent(f"tollbridge/{seed}/apron", (x, 0, z)) + reach * 0.22 > 0.62:
                t.set((x, 0, z), ASH)


def fence(t, pos):
    t.set(pos, "minecraft:dark_oak_fence", east=False, north=False, south=False, waterlogged=False, west=False)


def burnt_floor(t, width, depth, y=0):
    for x in range(MARGIN, width + MARGIN):
        for z in range(MARGIN, depth + MARGIN):
            roll = coherent("tollbridge/floor", (x, y, z))
            t.set((x, y, z), CHAR_PLANKS if roll > 0.62 else ASH)


def fallen_beam(t, start, length, axis, y):
    dx, dz = (1, 0) if axis == "x" else (0, 1)
    for i in range(length):
        char(t, (start[0] + dx * i, y, start[1] + dz * i), axis)


def outward(facing):
    return {"north": "south", "south": "north", "west": "east", "east": "west"}[facing]


def house(seed, width=7, depth=9, chimney_west=True):
    t = Template()
    m = MARGIN
    apron(t, width, depth, seed)
    burnt_floor(t, width, depth)
    x0, z0, x1, z1 = m, m, m + width - 1, m + depth - 1
    door = m + width // 2
    gable = x0 if chimney_west else x1
    stone_gable = noise(f"tollbridge/{seed}/gable-kind") > 0.4
    chimney_top = 5 + int(noise(f"tollbridge/{seed}/chimney") * 5)
    chimney_wide = noise(f"tollbridge/{seed}/chimney-width") > 0.55
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if not (x in (x0, x1) or z in (z0, z1)) or (z == z1 and x == door):
                continue
            if x == gable and stone_gable:
                peak = 5 - abs(z - (z0 + z1) / 2) * 0.6
                high = int(peak - coherent(f"tollbridge/{seed}/gable", (x, 0, z)) * 2)
                for y in range(1, max(2, high) + 1):
                    t.set((x, y, z), STONE if y <= 3 else SOOT)
                continue
            for y in range(1, 2 + (coherent(f"tollbridge/{seed}/wall", (x, 0, z)) > 0.6)):
                t.set((x, y, z), STONE)
    other = x1 if chimney_west else x0
    for x, z in ((other, z0), (other, z1)):
        for y in range(1, 3 + int(coherent(f"tollbridge/{seed}/post", (x, 0, z)) * 3)):
            if t.get((x, y, z)) is None:
                char(t, (x, y, z))
    studs = [(x, z) for x in range(x0 + 1, x1) for z in (z0, z1) if abs(x - door) > 1 or z == z0]
    studs += [(other, z) for z in range(z0 + 1, z1)]
    for x, z in studs:
        roll = coherent(f"tollbridge/{seed}/stud", (x, 0, z))
        top = 1 + int(roll * 4)
        for y in range(1, top + 1):
            if t.get((x, y, z)) is None:
                if (x + z) % 2 and y <= 3 and roll > 0.35:
                    t.set((x, y, z), PLASTER)
                else:
                    fence(t, (x, y, z))
    for y in (1, 2):
        fence(t, (door - 1, y, z1))
        fence(t, (door + 1, y, z1))
    cz = (z0 + z1) // 2
    for y in range(1, chimney_top + 1):
        t.set((gable, y, cz), SOOT if y > 4 else STONE)
        if chimney_wide:
            t.set((gable, y, cz + 1), SOOT if y > 4 else STONE)
    hearth = gable + 1 if chimney_west else gable - 1
    t.set((hearth, 0, cz), "minecraft:cobblestone")
    t.set((hearth, 0, cz + 1), "minecraft:cobblestone")
    smoulder(t, (hearth, 1, cz))
    if chimney_wide:
        t.set((hearth, 1, cz + 1), "minecraft:cauldron")
    beam = x0 + 3 if chimney_west else x0 + 1
    fallen_beam(t, (beam, z0 + 2), width - 4, "x", 1)
    burning(t, (beam + 1, 2, z0 + 2))
    fallen_beam(t, (door, z0 + 4), max(2, depth - 6), "z", 1)
    burning(t, (door, 2, z0 + 5))
    inner = other - 1 if chimney_west else other + 1
    t.set((door - 1, 1, z1 - 2), "minecraft:dark_oak_slab", type="bottom", waterlogged=False)
    t.set(
        (door + 1, 1, z0 + 1),
        "minecraft:dark_oak_stairs",
        facing="west",
        half="bottom",
        shape="straight",
        waterlogged=False,
    )
    t.set((inner, 1, z1 - 1), "minecraft:magma_block")
    t.set((inner, 1, z0 + 1), "minecraft:barrel", facing="up", open=True)
    return t


def tower():
    t = Template()
    g = CELLAR
    o = 1
    lo, hi = o, o + TOWER - 1
    for x in range(lo, hi + 1):
        for z in range(lo, hi + 1):
            t.set((x, 0, z), "minecraft:cobblestone")
            edge = x in (lo, hi) or z in (lo, hi)
            for y in range(1, g):
                t.set((x, y, z), STONE if edge else AIR)
            t.set((x, g, z), "minecraft:stone_bricks" if edge else "minecraft:cobblestone")
    for x in range(lo + 1, lo + 5):
        t.set((x, g, lo + 1), AIR)
    for i, x in enumerate(range(lo + 4, lo, -1)):
        y = g - i
        t.set(
            (x, y, lo + 1),
            "minecraft:stone_brick_stairs",
            facing="east",
            half="bottom",
            shape="straight",
            waterlogged=False,
        )
        for below in range(1, y):
            t.set((x, below, lo + 1), "minecraft:stone_bricks")
    smoulder(t, (hi - 1, 1, hi - 1))
    t.set((lo + 1, 1, hi - 1), "minecraft:barrel", facing="up", open=False)
    t.set((lo + 1, 1, hi - 2), "minecraft:barrel", facing="east", open=False)
    t.set((lo + 1, 2, hi - 1), "minecraft:hay_block", axis="y")
    for z in (lo + 2, lo + 3):
        t.set(
            (lo + 3, 1, z), "minecraft:spruce_stairs", facing="west", half="bottom", shape="straight", waterlogged=False
        )
    candle(t, (lo + 1, 3, hi - 1), "minecraft:candle", 2)
    top = g + TOWER_HEIGHT
    for y in range(g + 1, top):
        for x in range(lo, hi + 1):
            for z in range(lo, hi + 1):
                if not (x in (lo, hi) or z in (lo, hi)):
                    continue
                corner = x in (lo, hi) and z in (lo, hi)
                sooted = y - g > TOWER_HEIGHT - 7 or coherent("tollbridge/tower/soot", (x, y, z)) > 0.72
                t.set((x, y, z), QUOIN if corner else SOOT if sooted else STONE)
    for x in range(lo - o, hi + o + 1):
        for z in range(lo - o, hi + o + 1):
            if lo <= x <= hi and lo <= z <= hi:
                continue
            facing = "north" if z < lo else "south" if z > hi else "west" if x < lo else "east"
            if (x < lo or x > hi) and (z < lo or z > hi):
                t.set((x, g + 1, z), "minecraft:stone_bricks")
                t.set((x, g + 2, z), "minecraft:stone_brick_slab", type="bottom", waterlogged=False)
                continue
            t.set(
                (x, g + 1, z),
                "minecraft:stone_brick_stairs",
                facing=outward(facing),
                half="bottom",
                shape="straight",
                waterlogged=False,
            )
            t.set((x, g + 9, z), "minecraft:stone_brick_slab", type="top", waterlogged=False)
    mid = lo + TOWER // 2
    for y in (g + 1, g + 2):
        t.set((mid, y, hi), AIR)
    t.set((mid, g + 1, hi + 1), AIR)
    t.set((mid, g + 9, hi + 1), AIR)
    t.set(
        (mid, g + 1, hi),
        "minecraft:dark_oak_door",
        facing="north",
        half="lower",
        hinge="left",
        open=False,
        powered=False,
    )
    t.set(
        (mid, g + 2, hi),
        "minecraft:dark_oak_door",
        facing="north",
        half="upper",
        hinge="left",
        open=False,
        powered=False,
    )
    t.set((mid, g + 3, hi), "minecraft:chiseled_stone_bricks")
    for y in range(g + 4, g + 8):
        t.set((mid, y, hi), SOOT)
    for x, z in ((mid, lo), (lo, mid), (hi, mid)):
        for y in (g + 6, g + 7):
            t.set(
                (x, y, z),
                "minecraft:iron_bars",
                east=z != mid,
                north=z == mid,
                south=z == mid,
                waterlogged=False,
                west=z != mid,
            )
        for y in range(g + 8, g + 11):
            t.set((x, y, z), SOOT)
    belfry = top - 5
    for x, z in ((mid, lo), (mid, hi), (lo, mid), (hi, mid)):
        along_x = z in (lo, hi)
        for y in (belfry, belfry + 1, belfry + 2):
            for d in (-1, 0, 1):
                t.set((x + d, y, z) if along_x else (x, y, z + d), AIR)
        for d, face in ((-1, "east"), (1, "west")) if along_x else ((-1, "south"), (1, "north")):
            pos = (x + d, belfry + 3, z) if along_x else (x, belfry + 3, z + d)
            t.set(pos, "minecraft:stone_brick_stairs", facing=face, half="top", shape="straight", waterlogged=False)
        t.set(
            (x, belfry, z),
            "minecraft:dark_oak_trapdoor",
            facing="north" if along_x else "west",
            half="top",
            open=False,
            powered=False,
            waterlogged=False,
        )
    for x in range(lo, hi + 1):
        for z in range(lo, hi + 1):
            if (x in (lo, hi) or z in (lo, hi)) and coherent("tollbridge/tower/crown", (x, top - 1, z)) < 0.4:
                t.set((x, top - 1, z), AIR)
    for x, z in ((lo, lo), (hi, hi), (lo, hi)):
        if t.get((x, top - 1, z)) is not None:
            fence(t, (x, top, z))
            fence(t, (x, top + 1, z))
    for x in range(lo + 1, hi):
        char(t, (x, belfry - 1, mid), "x")
    char(t, (mid, belfry - 1, lo + 1), "z")
    burning(t, (lo + 2, belfry, mid))
    for x in range(lo + 1, hi):
        for z in range(lo + 1, hi):
            if t.get((x, g, z)) == "minecraft:cobblestone" and coherent("tollbridge/tower/ash", (x, g + 1, z)) > 0.6:
                t.set((x, g + 1, z), "minecraft:gray_concrete_powder")
    char(t, (lo + 1, g + 1, hi - 2), "z")
    char(t, (lo + 1, g + 1, hi - 1), "z")
    return t


def well():
    t = Template()
    for x in range(5):
        for z in range(5):
            t.set((x, 0, z), SQUARE)
    for x in range(1, 4):
        for z in range(1, 4):
            if x == 2 and z == 2:
                t.set((x, 0, z), "minecraft:water", level=0)
                continue
            t.set((x, 0, z), "minecraft:stone_bricks")
            t.set(
                (x, 1, z),
                "minecraft:cobblestone_wall",
                east="none",
                north="none",
                south="none",
                up=True,
                waterlogged=False,
                west="none",
            )
    for x, z in ((1, 1), (3, 3)):
        fence(t, (x, 2, z))
        fence(t, (x, 3, z))
    fence(t, (1, 4, 1))
    t.set((3, 4, 3), "minecraft:dark_oak_slab", type="bottom", waterlogged=False)
    t.set((0, 1, 4), "minecraft:dark_oak_slab", type="bottom", waterlogged=False)
    t.set(
        (4, 1, 0),
        "minecraft:dark_oak_trapdoor",
        facing="north",
        half="bottom",
        open=False,
        powered=False,
        waterlogged=False,
    )
    return t


def rotate(t, turns):
    for _ in range(turns % 4):
        depth = max(pos[2] for pos in t.blocks) + 1
        turned = Template()
        for (x, y, z), (name, properties, entity) in t.blocks.items():
            props = dict(properties)
            if "facing" in props and props["facing"] in ROTATE_FACING:
                props["facing"] = ROTATE_FACING[props["facing"]]
            if "axis" in props and props["axis"] != "y":
                props["axis"] = "z" if props["axis"] == "x" else "x"
            if "rotation" in props:
                props["rotation"] = (int(props["rotation"]) + 4) % 16
            sides = {key: props.pop(key) for key in ("north", "east", "south", "west") if key in props}
            for key, value in sides.items():
                props[ROTATE_FACING[key]] = value
            turned.blocks[(depth - 1 - z, y, x)] = (name, tuple(sorted(props.items())), entity)
        t = turned
    return t


def hall(seed, width, depth, strongbox=False):
    t = Template()
    m = MARGIN
    apron(t, width, depth, seed)
    burnt_floor(t, width, depth)
    x0, z0, x1, z1 = m, m, m + width - 1, m + depth - 1
    door = m + width // 2
    openings = {(x0 + 2, z1), (x1 - 2, z1), (x0 + 2, z0), (x1 - 2, z0)}
    openings |= {(x, z) for x in (x0, x1) for z in range(z0 + 3, z1 - 1, 4)}
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if not (x in (x0, x1) or z in (z0, z1)) or (z == z1 and x == door):
                continue
            corner = x in (x0, x1) and z in (z0, z1)
            high = 4 + (coherent(f"tollbridge/{seed}/wall", (x, 0, z)) > 0.45)
            for y in range(1, high + 1):
                t.set((x, y, z), QUOIN if corner else STONE)
            if (x, z) in openings:
                t.set((x, 2, z), AIR)
                t.set((x, 3, z), AIR)
                t.set((x, 1, z), "minecraft:stone_brick_slab", type="top", waterlogged=False)
                for y in range(4, high + 1):
                    t.set((x, y, z), SOOT)
    for y in (1, 2):
        t.set((door, y, z1), AIR)
    t.set((door, 3, z1), "minecraft:stripped_dark_oak_log", axis="x")
    for y in (4, 5):
        if t.get((door, y, z1)) is not None:
            t.set((door, y, z1), SOOT)
    for z in range(z0 + 2, z1 - 1, 3):
        if coherent(f"tollbridge/{seed}/joist", (0, 4, z)) > 0.3:
            for x in range(x0 + 1, x1):
                if coherent(f"tollbridge/{seed}/break", (x, 4, z)) > 0.25:
                    char(t, (x, 4, z), "x")
            char(t, (x0 - 1, 4, z), "x")
            char(t, (x1 + 1, 4, z), "x")
    for z in (z0, (z0 + z1) // 2, z1):
        for x in (x0, x1):
            for y in range(5, 6 + int(coherent(f"tollbridge/{seed}/post", (x, 0, z)) * 3)):
                if t.get((x, y, z)) is None:
                    char(t, (x, y, z))
    fallen_beam(t, (x0 + 2, z0 + 1), depth - 5, "z", 1)
    burning(t, (x0 + 2, 2, z0 + 2))
    fallen_beam(t, (x1 - 2, z0 + 3), depth - 6, "z", 1)
    burning(t, (x1 - 2, 2, z0 + 4))
    t.set((door, 1, z0 + depth // 2), "minecraft:magma_block")
    if strongbox:
        t.set((x0 + 1, 1, z0 + 1), "minecraft:barrel", facing="up", open=True)
        t.set((x0 + 1, 1, z0 + 2), "minecraft:barrel", facing="east", open=False)
        t.set((x0 + 2, 1, z0 + 1), "minecraft:barrel", facing="up", open=False)
        for x, z in ((door, z0 + 4), (door, z1 - 3)):
            fence(t, (x, 1, z))
            t.set((x, 2, z), "minecraft:dark_oak_pressure_plate", powered=False)
            t.set(
                (x + 1, 1, z),
                "minecraft:dark_oak_stairs",
                facing="west",
                half="bottom",
                shape="straight",
                waterlogged=False,
            )
    else:
        t.set((x1 - 1, 1, z0 + 1), "minecraft:barrel", facing="up", open=True)
        t.set((x0 + 1, 1, z1 - 1), "minecraft:cauldron")
    return t


def smithy():
    width, depth = 7, 7
    t = Template()
    m = MARGIN
    apron(t, width, depth, "smithy")
    x0, z0, x1, z1 = m, m, m + width - 1, m + depth - 1
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.set((x, 0, z), ASH if coherent("tollbridge/smithy/floor", (x, 0, z)) > 0.4 else "minecraft:cobblestone")
    for x in range(x0, x1 + 1):
        for y in range(1, 4 + (x in (x0, x1))):
            t.set((x, y, z0), STONE if y < 3 else SOOT)
    for z in (z0 + 1, z0 + 2):
        for y in range(1, 3):
            t.set((x0, y, z), STONE)
    for x, z in ((x0, z1), (x1, z1), (x1, z0 + 3)):
        for y in range(1, 3 + int(coherent("tollbridge/smithy/post", (x, 0, z)) * 3)):
            char(t, (x, y, z))
    t.set((x0 + 2, 1, z0 + 1), "minecraft:blast_furnace", facing="south", lit=False)
    t.set((x0 + 3, 1, z0 + 1), "minecraft:cobblestone")
    smoulder(t, (x0 + 3, 2, z0 + 1))
    t.set((x0 + 4, 1, z0 + 1), "minecraft:blast_furnace", facing="south", lit=False)
    for y in range(4, 8):
        t.set((x0 + 3, y, z0), SOOT)
    t.set((x0 + 3, 1, z0 + 3), "minecraft:anvil", facing="east")
    t.set((x0 + 5, 1, z0 + 2), "minecraft:water_cauldron", level=1)
    t.set((x0 + 1, 1, z0 + 4), "minecraft:barrel", facing="up", open=True)
    fallen_beam(t, (x0 + 1, z0 + 5), 4, "x", 1)
    burning(t, (x0 + 2, 2, z0 + 5))
    return t


def barn(seed):
    width, depth = 9, 13
    t = Template()
    m = MARGIN
    apron(t, width, depth, seed)
    burnt_floor(t, width, depth)
    x0, z0, x1, z1 = m, m, m + width - 1, m + depth - 1
    door = m + width // 2
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            if not edge or (z == z1 and abs(x - door) <= 1):
                continue
            t.set((x, 1, z), STONE)
            post = (x in (x0, x1) and (z - z0) % 3 == 0) or (z in (z0, z1) and (x - x0) % 4 == 0)
            if post:
                for y in range(2, 3 + int(coherent(f"tollbridge/{seed}/post", (x, 0, z)) * 5)):
                    char(t, (x, y, z))
            elif coherent(f"tollbridge/{seed}/board", (x, 0, z)) > 0.5:
                for y in range(2, 2 + int(coherent(f"tollbridge/{seed}/board", (x, 1, z)) * 3)):
                    fence(t, (x, y, z))
    for z in range(z0 + 2, z1 - 1, 4):
        fallen_beam(t, (x0 + 1, z), width - 2, "x", 1)
        burning(t, (x0 + 3, 2, z))
    for x, z in ((x0 + 2, z0 + 4), (x1 - 2, z0 + 7), (x0 + 3, z1 - 3)):
        t.set((x, 1, z), "minecraft:hay_block", axis="y")
        t.set((x + 1, 1, z), "minecraft:coarse_dirt")
    t.set((x1 - 1, 1, z0 + 1), "minecraft:magma_block")
    return t


def stall(seed):
    t = Template()
    for x in range(3):
        for z in range(3):
            t.set((x, 0, z), "minecraft:cobblestone")
    for x, z in ((0, 0), (2, 0), (0, 2), (2, 2)):
        for y in range(1, 2 + int(coherent(f"tollbridge/{seed}/post", (x, 0, z)) * 3)):
            fence(t, (x, y, z))
    t.set((1, 1, 0), "minecraft:barrel", facing="up", open=True)
    t.set((1, 1, 2), "minecraft:dark_oak_slab", type="bottom", waterlogged=False)
    if t.get((0, 3, 0)) is not None and t.get((2, 3, 0)) is not None:
        t.set((1, 3, 0), "minecraft:dark_oak_slab", type="top", waterlogged=False)
        t.set(
            (1, 4, 0),
            "minecraft:red_carpet"
            if coherent(f"tollbridge/{seed}/cloth", (0, 0, 0)) > 0.5
            else "minecraft:yellow_carpet",
        )
    return t


def toll_house():
    t = Template()
    for x in range(5):
        for z in range(5):
            edge = x in (0, 4) or z in (0, 4)
            t.set((x, 0, z), "minecraft:cobblestone" if edge else ASH)
            if edge and not (z == 4 and x == 2):
                for y in range(1, 4 + (x in (0, 4) and z in (0, 4))):
                    t.set((x, y, z), QUOIN if x in (0, 4) and z in (0, 4) else STONE if y < 3 else SOOT)
    t.set((2, 2, 0), AIR)
    t.set((2, 1, 0), "minecraft:stone_brick_slab", type="top", waterlogged=False)
    char(t, (1, 4, 2), "x")
    char(t, (2, 4, 2), "x")
    t.set((2, 1, 2), "minecraft:barrel", facing="up", open=True)
    return t


def bridge():
    width, length = 5, 15
    middle = length // 2
    t = Template()
    for z in range(length):
        deck = min(3, 1 + min(z, length - 1 - z))
        bottom = deck - 1 if abs(z - middle) < 3 else 0
        for y in range(bottom, deck):
            for x in range(width):
                t.set((x, y, z), STONE)
        for x in range(width):
            t.set((x, deck, z), "minecraft:cobblestone" if x in (0, width - 1) else STREET)
        if 0 < z < length - 1:
            for x in (0, width - 1):
                t.set(
                    (x, deck + 1, z),
                    "minecraft:cobblestone_wall",
                    east="none",
                    north="low",
                    south="low",
                    up=coherent("tollbridge/bridge/cap", (x, 0, z)) > 0.6,
                    waterlogged=False,
                    west="none",
                )
    for x in (0, width - 1):
        for y in range(2, 5):
            char(t, (x, y, 0))
    char(t, (0, 5, 0), "x")
    char(t, (1, 5, 0), "x")
    return t


KINDS = {
    "barn": ((9, 13), lambda seed: barn(seed)),
    "house": ((7, 9), lambda seed: house(seed, 7, 9, noise(f"{seed}/side") > 0.5)),
    "house_long": ((7, 11), lambda seed: house(seed, 7, 11, noise(f"{seed}/side") > 0.5)),
    "house_small": ((5, 7), lambda seed: house(seed, 5, 7, noise(f"{seed}/side") > 0.5)),
    "house_wide": ((9, 7), lambda seed: house(seed, 9, 7, noise(f"{seed}/side") > 0.5)),
    "townhouse": ((7, 9), lambda seed: hall(seed, 7, 9)),
}
VARIANTS = 3
PIECES = {
    "bridge": bridge,
    "inn": lambda: hall("inn", 9, 13, strongbox=True),
    "smithy": smithy,
    "stall": lambda: stall("stall"),
    "toll_house": toll_house,
    "well": well,
} | {
    f"{kind}_{variant}": (lambda kind=kind, variant=variant: KINDS[kind][1](f"{kind}/{variant}"))
    for kind in KINDS
    for variant in range(1, VARIANTS + 1)
}
LOT_KINDS = [
    "house",
    "house",
    "house_small",
    "house_small",
    "house_wide",
    "house_long",
    "townhouse",
    "townhouse",
    "barn",
]
SIZE = 104
MAIN_STREET = (50, 54)
CROSS_STREET = (50, 54)
LANES_X = [(24, 26), (78, 80)]
YARDS = 0.4
LANES_Z = [(24, 26), (78, 80)]
SQUARE_AREA = ((38, 66), (40, 64))
RIVER = (93, 99)
FIXED = [
    ("bell_tower", (55, 31), 0, CELLAR),
    ("well", (50, 48), 0, 0),
    ("inn", (35, 14), 3, 0),
    ("smithy", (56, 17), 1, 0),
    ("stall", (42, 56), 0, 0),
    ("stall", (46, 58), 0, 0),
    ("stall", (57, 58), 0, 0),
    ("stall", (61, 56), 0, 0),
    ("toll_house", (55, 86), 0, 0),
]
BRIDGE = ("bridge", (50, 89), 0, 0)
DEAD = 32
FIXED_SIZES = {
    "bell_tower": (TOWER + 2, TOWER + 2),
    "inn": (17, 13),
    "smithy": (11, 11),
    "stall": (3, 3),
    "toll_house": (5, 5),
    "well": (5, 5),
}


def kind_of(name):
    return name.rsplit("_", 1)[0] if name.rsplit("_", 1)[-1].isdigit() else name


def footprint(name, turns):
    width, depth = KINDS[kind_of(name)][0]
    return (depth, width) if turns % 2 else (width, depth)


def free(taken, x0, z0, width, depth):
    if x0 < 1 or z0 < 1 or x0 + width >= SIZE - 1 or z0 + depth >= RIVER[0] - 1:
        return False
    return all((x, z) not in taken for x in range(x0 - 1, x0 + width + 1) for z in range(z0 - 1, z0 + depth + 1))


def claim(taken, x0, z0, width, depth):
    taken.update((x, z) for x in range(x0, x0 + width) for z in range(z0, z0 + depth))


def streets():
    cells = set()
    for x in range(MAIN_STREET[0], MAIN_STREET[1] + 1):
        cells.update((x, z) for z in range(SIZE))
    for z in range(CROSS_STREET[0], CROSS_STREET[1] + 1):
        cells.update((x, z) for x in range(SIZE))
    for low, high in LANES_X:
        cells.update((x, z) for x in range(low, high + 1) for z in range(SIZE) if z < RIVER[0])
    for low, high in LANES_Z:
        cells.update((x, z) for z in range(low, high + 1) for x in range(SIZE))
    (sx0, sx1), (sz0, sz1) = SQUARE_AREA
    square = {(x, z) for x in range(sx0, sx1 + 1) for z in range(sz0, sz1 + 1)}
    return cells, square


def lots(taken):
    placed = []
    runs = []
    for low, high in [MAIN_STREET, *LANES_X]:
        runs.append(("x", low, high))
    for low, high in [CROSS_STREET, *LANES_Z]:
        runs.append(("z", low, high))
    for axis, low, high in runs:
        for side in (-1, 1):
            along = 2
            while along < SIZE - 2:
                if noise(f"tollbridge/yard/{axis}/{low}/{side}/{along}") < YARDS:
                    along += 5 + int(noise(f"tollbridge/yard-size/{axis}/{low}/{side}/{along}") * 4)
                    continue
                kind = pick_kind(f"{axis}/{low}/{side}/{along}")
                if axis == "x":
                    turns = 3 if side < 0 else 1
                    width, depth = footprint(kind, turns)
                    x0 = low - 2 - width if side < 0 else high + 3
                    z0 = along
                    span = depth
                else:
                    turns = 0 if side < 0 else 2
                    width, depth = footprint(kind, turns)
                    x0 = along
                    z0 = low - 2 - depth if side < 0 else high + 3
                    span = width
                if free(taken, x0, z0, width, depth):
                    claim(taken, x0, z0, width, depth)
                    variant = 1 + int(noise(f"tollbridge/variant/{axis}/{low}/{side}/{along}") * VARIANTS) % VARIANTS
                    placed.append((f"{kind}_{variant}", (x0 - MARGIN, z0 - MARGIN), turns, 0))
                    along += span + 3 + int(noise(f"tollbridge/gap/{axis}/{low}/{side}/{along}") * 4)
                else:
                    along += 1
    return placed


def pick_kind(seed):
    return LOT_KINDS[int(noise(f"tollbridge/kind/{seed}") * len(LOT_KINDS)) % len(LOT_KINDS)]


def layout():
    cells, square = streets()
    taken = set(cells) | square
    taken.update((x, z) for x in range(SIZE) for z in range(RIVER[0] - 1, SIZE))
    for name, (ox, oz), _, _ in FIXED:
        claim(taken, ox, oz, *FIXED_SIZES[name])
    return FIXED + lots(taken), cells, square


def dead(pieces, cells):
    spots = []
    for index, (name, (ox, oz), turns, _) in enumerate(pieces):
        if kind_of(name) not in KINDS:
            continue
        width, depth = footprint(name, turns)
        cx, cz = ox + MARGIN + width // 2, oz + MARGIN + depth // 2
        door = {
            0: (cx, oz + MARGIN + depth + 1),
            2: (cx, oz + MARGIN - 2),
            1: (ox + MARGIN - 2, cz),
            3: (ox + MARGIN + width + 1, cz),
        }[turns]
        if noise(f"tollbridge/dead/{index}") > 0.35:
            spots.append(door)
    extra = sorted(cells, key=lambda cell: noise("tollbridge/street-dead", *cell))
    for cell in extra:
        if len(spots) >= DEAD:
            break
        if all(abs(cell[0] - x) + abs(cell[1] - z) > 6 for x, z in spots):
            spots.append(cell)
    return [
        {"skin": f"townsfolk_{i + 1:02d}", "x": x, "yaw": int(noise("tollbridge/yaw", x, z) * 360), "z": z}
        for i, (x, z) in enumerate(spots[:DEAD])
    ]


def scorched(t):
    for (x, y, z), (name, _, _) in list(t.blocks.items()):
        if y == 0 and name == "minecraft:grass_block":
            roll = coherent("tollbridge/scorch", (x, 0, z))
            if roll > 0.62:
                t.set((x, 0, z), "minecraft:coarse_dirt" if roll > 0.76 else "minecraft:podzol", snowy=False)
    for x, z in ((44, 45), (60, 47), (52, 70), (30, 52), (76, 52), (52, 20)):
        smoulder(t, (x, 1, z))


def town():
    pieces, cells, square = layout()
    t = Template()
    for x in range(SIZE):
        for z in range(SIZE):
            if RIVER[0] <= z <= RIVER[1]:
                t.set((x, 0, z), "minecraft:water", level=0)
            elif (x, z) in square:
                t.set((x, 0, z), SQUARE)
            elif (x, z) in cells:
                t.set((x, 0, z), STREET)
            else:
                t.set((x, 0, z), "minecraft:grass_block", snowy=False)
    built = {"bell_tower": tower_above} | PIECES
    for name, (ox, oz), turns, _ in [*pieces, BRIDGE]:
        piece = rotate(built[name](), turns)
        lift = -1 if name == "bridge" else 0
        for (x, y, z), block in piece.blocks.items():
            if y + lift >= 0:
                t.blocks[(ox + x, y + lift, oz + z)] = block
    scorched(t)
    t.set(BELL, "wayfarer:tollbridge_bell", facing="east", fallen=True)
    return t


def plan():
    pieces, cells, _ = layout()
    return {
        "bell": {"facing": "east", "x": BELL[0], "z": BELL[2]},
        "bridge": {"piece": BRIDGE[0], "x": BRIDGE[1][0], "z": BRIDGE[1][1]},
        "dead": dead(pieces, cells),
        "pieces": [
            {"ground": ground, "piece": name, "turns": turns, "x": ox, "z": oz}
            for name, (ox, oz), turns, ground in pieces
        ],
        "size": SIZE,
    }


BELL = (64, 1, 43)


def tower_above():
    full = tower()
    t = Template()
    for (x, y, z), block in full.blocks.items():
        if y >= CELLAR:
            t.blocks[(x, y - CELLAR, z)] = block
    return t

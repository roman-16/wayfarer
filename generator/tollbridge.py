from blocks import AIR, Material, Template, candle, coherent, noise
from poses import DEAD as POSES
from townsfolk import townsfolk

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
    [("minecraft:dark_oak_planks", 55), ("minecraft:spruce_planks", 20), ("minecraft:dark_oak_wood", 25)],
)
FIRE = {"age": 0, "east": False, "north": False, "south": False, "up": False, "west": False}
TOWER = 7
TOWER_HEIGHT = 19
CELLAR = 4
ROTATE_FACING = {"north": "east", "east": "south", "south": "west", "west": "north"}


def stores(kind):
    return {"LootTable": f"wayfarer:chests/tollbridge/{kind}"}


def char(t, pos, axis="y"):
    t.set(pos, CHAR[int(noise("tollbridge/char", *pos) * 2)], axis=axis)


def burning(t, pos):
    t.set(pos, "minecraft:fire", **FIRE)


def smoulder(t, pos, signal=False):
    if signal:
        t.set((pos[0], pos[1] - 1, pos[2]), "minecraft:hay_block", axis="y")
    t.set(pos, "minecraft:campfire", facing="south", lit=True, signal_fire=signal, waterlogged=False)


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


STOREY = 4
LEAN = 3
PORCH = 2
STYLES = ["planks", "plaster", "plaster", "stone", "stone", "stone", "timber"]
RUINS = ["broken", "broken", "fallen", "roofed", "shell"]
TRADES = ["baker", "carpenter", "cooper", "home", "home", "weaver"]
LIME = Material(
    "tollbridge/lime",
    [
        ("minecraft:calcite", 30),
        ("minecraft:light_gray_terracotta", 25),
        ("minecraft:gray_terracotta", 20),
        ("minecraft:tuff", 10),
        ("minecraft:black_terracotta", 15),
    ],
    ground=4,
    fade=4,
)
PLANKS = Material(
    "tollbridge/boards",
    [
        ("minecraft:spruce_planks", 30),
        ("minecraft:dark_oak_planks", 40),
        ("minecraft:stripped_dark_oak_log", 10),
        ("minecraft:dark_oak_wood", 20),
    ],
)
BRICK = Material(
    "tollbridge/brick", [("minecraft:bricks", 70), ("minecraft:mud_bricks", 15), ("minecraft:cobblestone", 15)]
)


def choose(seed, options):
    return options[int(noise(seed) * len(options)) % len(options)]


def stairs(t, pos, facing, half="bottom", block="minecraft:dark_oak_stairs"):
    t.set(pos, block, facing=facing, half=half, shape="straight", waterlogged=False)


def wall_block(style, seed, pos, corner, post, plinth):
    x, y, z = pos
    if style == "stone":
        return QUOIN if corner else SOOT if y >= STOREY and coherent(f"{seed}/soot", pos) > 0.5 else STONE
    if corner or post:
        return "post"
    if style == "plaster":
        return STONE if plinth else LIME
    if style == "planks":
        return STONE if plinth else PLANKS
    return CHAR_PLANKS


def home(seed, storeys=None, style=None, width=None, depth=None, leans=("back", "east", "west")):
    t = Template()
    m = MARGIN
    width = width or choose(f"{seed}/width", [5, 7, 7, 9])
    depth = depth or choose(f"{seed}/depth", [7, 9, 9, 11])
    storeys = storeys or (2 if width >= 7 and noise(f"{seed}/storeys") < 0.45 else 1)
    style = style or choose(f"{seed}/style", STYLES)
    ruin = choose(f"{seed}/ruin", RUINS)
    lean = choose(f"{seed}/lean", [None, None, *leans])
    porch = noise(f"{seed}/porch") < 0.35
    trade = choose(f"{seed}/trade", TRADES)
    x0 = m + (LEAN if lean == "west" else 0)
    z0 = m + (LEAN if lean == "back" else 0)
    x1, z1 = x0 + width - 1, z0 + depth - 1
    outer_w = width + (LEAN if lean in ("east", "west") else 0)
    outer_d = depth + (LEAN if lean == "back" else 0) + (PORCH if porch else 0)
    apron(t, outer_w, outer_d, seed)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.set((x, 0, z), CHAR_PLANKS if coherent(f"{seed}/floor", (x, 0, z)) > 0.55 else ASH)
    full = STOREY * storeys
    door = (
        x0
        + width // 2
        + (
            choose(f"{seed}/door", [-2, 0, 0, 2])
            if width >= 9
            else choose(f"{seed}/door", [-1, 0, 0, 1])
            if width >= 7
            else 0
        )
    )
    chimney = choose(f"{seed}/chimney", ["back", "east", "west"])
    cx, cz = {
        "back": ((x0 + x1) // 2 + (1 if door == (x0 + x1) // 2 else 0), z0),
        "east": (x1, (z0 + z1) // 2),
        "west": (x0, (z0 + z1) // 2),
    }[chimney]
    windows = set()
    for x in range(x0 + 2, x1 - 1, 3):
        windows |= {(x, z0)}
    for z in range(z0 + 2, z1 - 1, 3):
        windows |= {(x0, z), (x1, z)}
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if not (x in (x0, x1) or z in (z0, z1)) or (x, z) == (cx, cz):
                continue
            corner = x in (x0, x1) and z in (z0, z1)
            post = (x - x0) % 3 == 0 if z in (z0, z1) else (z - z0) % 3 == 0
            roll = coherent(f"{seed}/ruin", (x, 0, z))
            high = {
                "broken": max(1, round(full * (0.25 + roll * 0.85))),
                "fallen": (2 + int(roll * 3)) if corner else int(roll * 2),
                "roofed": full,
                "shell": full - (1 if roll > 0.7 else 0),
            }[ruin]
            if corner and ruin != "fallen":
                high = max(high, full - 1)
            for y in range(1, high + 1):
                if z == z1 and abs(x - door) == 0 and y <= 2:
                    continue
                if (x, z) in windows and (y % STOREY) in (2, 3) and not corner:
                    if (y % STOREY) == 2 and noise(f"{seed}/sill", x, z, y) > 0.5:
                        t.set((x, y - 1, z), "minecraft:stone_brick_slab", type="top", waterlogged=False)
                    continue
                kind = wall_block(style, seed, (x, y, z), corner, post, y == 1)
                burnt_down = 1 + int(coherent(f"{seed}/infill", (x, 0, z)) * (4 if style == "plaster" else 3))
                if kind == "post":
                    char(t, (x, y, z))
                elif style != "stone" and (y > burnt_down or coherent(f"{seed}/burnt", (x, y, z)) > 0.62):
                    continue
                else:
                    t.set((x, y, z), kind)
            if storeys == 2 and high >= STOREY + 1:
                axis = "x" if z in (z0, z1) else "z"
                char(t, (x, STOREY + 1, z), axis)
    t.set((door, 0, z1), "minecraft:cobblestone")
    if noise(f"{seed}/door-left") > 0.5:
        t.set(
            (door, 1, z1),
            "minecraft:dark_oak_door",
            facing="north",
            half="lower",
            hinge="left",
            open=True,
            powered=False,
        )
        t.set(
            (door, 2, z1),
            "minecraft:dark_oak_door",
            facing="north",
            half="upper",
            hinge="left",
            open=True,
            powered=False,
        )
    else:
        t.set(
            (door, 0, z1 + 1),
            "minecraft:dark_oak_trapdoor",
            facing="north",
            half="top",
            open=False,
            powered=False,
            waterlogged=False,
        )
    top = full + (2 + int(noise(f"{seed}/chimney-top") * 3))
    for y in range(0, top + 1):
        t.set((cx, y, cz), (BRICK if style == "plaster" else STONE) if y <= STOREY else SOOT)
    inward = {"back": (0, 1), "east": (-1, 0), "west": (1, 0)}[chimney]
    hx, hz = cx + inward[0], cz + inward[1]
    t.set((hx, 0, hz), "minecraft:cobblestone")
    smoulder(t, (hx, 1, hz))
    if storeys == 2:
        for z in range(z0 + 1, z1):
            if (z - z0) % 2 == 0 and coherent(f"{seed}/joist", (0, STOREY, z)) > 0.35:
                for x in range(x0 + 1, x1):
                    if coherent(f"{seed}/joist-break", (x, STOREY, z)) > 0.3:
                        char(t, (x, STOREY + 1, z), "x")
        for i in range(STOREY):
            sx = x1 - 1 - i if chimney != "east" else x0 + 1 + i
            if coherent(f"{seed}/stair", (i, 0, 0)) > 0.25:
                stairs(t, (sx, 1 + i, z0 + 1), "west" if chimney != "east" else "east")
    if ruin == "roofed":
        ridge = (x0 + x1) / 2
        span = int(noise(f"{seed}/roof-span") * depth / 2) + depth // 2
        side = choose(f"{seed}/roof-side", ["east", "west"])
        for z in range(z0 - 1, min(z1 + 1, z0 - 1 + span) + 1):
            for k in range(width // 2 + 1):
                x = x0 - 1 + k if side == "west" else x1 + 1 - k
                if coherent(f"{seed}/roof-hole", (x, k, z)) > 0.72:
                    continue
                if abs(x - ridge) < 0.6:
                    t.set((x, full + 1 + k, z), "minecraft:dark_oak_slab", type="bottom", waterlogged=False)
                else:
                    stairs(t, (x, full + 1 + k, z), "east" if side == "west" else "west")
        if style == "stone":
            for k in range(width // 2):
                for x in range(x0 + k, x1 - k + 1):
                    if coherent(f"{seed}/gable", (x, k, z0)) > 0.25:
                        t.set((x, full + 1 + k, z0), SOOT)
    if ruin == "fallen":
        for i in range(2 + int(noise(f"{seed}/rubble") * 4)):
            rx = x0 + 1 + int(noise(f"{seed}/rubble", i, "x") * max(1, width - 2))
            rz = z0 + 1 + int(noise(f"{seed}/rubble", i, "z") * max(1, depth - 2))
            if (rx, rz) != (hx, hz):
                t.set(
                    (rx, 1, rz),
                    choose(
                        f"{seed}/rubble/{i}",
                        ["minecraft:cobblestone", "minecraft:gravel", "minecraft:cobblestone_slab"],
                    ),
                )
    beam = (x0 + 1, z0 + 2) if width > 5 else (x0 + 1, z0 + 1)
    fallen_beam(t, beam, max(2, width - 3), "x", 1)
    burning(t, (beam[0] + 1, 2, beam[1]))
    if depth > 7:
        fallen_beam(t, (door, z0 + 3), max(2, depth - 6), "z", 1)
        burning(t, (door, 2, z0 + 4))
    furnish(t, seed, trade, x0, z0, x1, z1, door, {(hx, hz), (cx, cz)})
    if lean:
        lean_to(t, seed, lean, x0, z0, x1, z1)
    if porch:
        for x in (door - 1, door + 1):
            for y in range(1, 3 + (noise(f"{seed}/porch-post", x) > 0.4)):
                char(t, (x, y, z1 + PORCH))
        for x in range(door - 1, door + 2):
            if noise(f"{seed}/porch-roof", x) > 0.3:
                t.set((x, 3, z1 + PORCH), "minecraft:dark_oak_slab", type="bottom", waterlogged=False)
    yard(t, seed, x0, z0, x1, z1, outer_w, outer_d, door)
    t.door = (door, z1)
    t.extent = (outer_w + 2 * m, outer_d + 2 * m)
    return t


def furnish(t, seed, trade, x0, z0, x1, z1, door, taken):
    spots = [(x, z) for x in range(x0 + 1, x1) for z in (z0 + 1, z1 - 1)] + [
        (x, z) for z in range(z0 + 2, z1 - 1) for x in (x0 + 1, x1 - 1)
    ]
    spots = [s for s in spots if s not in taken and abs(s[0] - door) > 0 and t.get((s[0], 1, s[1])) is None]
    spots.sort(key=lambda s: noise(f"{seed}/spot", *s))
    things = {
        "baker": [
            ("minecraft:smoker", {"facing": "south", "lit": False}),
            ("minecraft:hay_block", {"axis": "y"}),
            ("minecraft:barrel", {"facing": "up", "open": True}),
        ],
        "carpenter": [
            ("minecraft:crafting_table", {}),
            ("minecraft:stripped_oak_log", {"axis": "x"}),
            ("minecraft:stripped_oak_log", {"axis": "x"}),
        ],
        "cooper": [
            ("minecraft:barrel", {"facing": "up", "open": False}),
            ("minecraft:barrel", {"facing": "east", "open": False}),
            ("minecraft:barrel", {"facing": "up", "open": True}),
        ],
        "home": [
            ("bed", {}),
            ("table", {}),
            ("minecraft:flower_pot", {}),
            ("minecraft:barrel", {"facing": "up", "open": True}),
        ],
        "weaver": [
            ("minecraft:loom", {"facing": "south"}),
            ("minecraft:white_carpet", {}),
            ("minecraft:barrel", {"facing": "up", "open": True}),
        ],
    }[trade]
    for (name, properties), (x, z) in zip(things, spots, strict=False):
        if name == "bed":
            t.set((x, 1, z), "minecraft:brown_carpet")
        elif name == "table":
            fence(t, (x, 1, z))
            t.set((x, 2, z), "minecraft:dark_oak_pressure_plate", powered=False)
        else:
            t.set((x, 1, z), name, entity=stores(trade) if name == "minecraft:barrel" else None, **properties)


def lean_to(t, seed, side, x0, z0, x1, z1):
    if side == "back":
        xs, zs = range(x0, x1 + 1), range(z0 - LEAN, z0)
    elif side == "east":
        xs, zs = range(x1 + 1, x1 + 1 + LEAN), range(z0 + 1, z1)
    else:
        xs, zs = range(x0 - LEAN, x0), range(z0 + 1, z1)
    xs, zs = list(xs), list(zs)
    for x in xs:
        for z in zs:
            t.set((x, 0, z), "minecraft:coarse_dirt" if coherent(f"{seed}/lean-floor", (x, 0, z)) > 0.4 else ASH)
            if side == "back":
                walled = z == zs[0] or x in (xs[0], xs[-1])
            else:
                walled = x == (xs[-1] if side == "east" else xs[0]) or z in (zs[0], zs[-1])
            if not walled:
                continue
            for y in range(1, 2 + int(coherent(f"{seed}/lean-wall", (x, 0, z)) * 2)):
                if coherent(f"{seed}/lean-gap", (x, y, z)) >= 0.8:
                    continue
                if (x + z) % 3:
                    fence(t, (x, y, z))
                else:
                    char(t, (x, y, z))
    name, properties = choose(
        f"{seed}/lean-store",
        [
            ("minecraft:hay_block", {"axis": "y"}),
            ("minecraft:stripped_spruce_log", {"axis": "y"}),
            ("minecraft:composter", {"level": 3}),
        ],
    )
    t.set((xs[len(xs) // 2], 1, zs[len(zs) // 2]), name, **properties)


def yard(t, seed, x0, z0, x1, z1, outer_w, outer_d, door):
    m = MARGIN
    edge = [(x, m - 2) for x in range(0, outer_w + 2 * m)] + [(m - 2, z) for z in range(0, outer_d + 2 * m)]
    if noise(f"{seed}/fence") > 0.45:
        for x, z in edge:
            if coherent(f"{seed}/fence-run", (x, 0, z)) > 0.45 and t.get((x, 1, z)) is None:
                fence(t, (x, 1, z))
    props = [
        ("woodpile", None),
        ("minecraft:hay_block", {"axis": "y"}),
        ("minecraft:cauldron", {}),
        ("minecraft:barrel", {"facing": "up", "open": True}),
        ("minecraft:composter", {"level": 2}),
    ]
    for i in range(int(noise(f"{seed}/props") * 3)):
        name, properties = choose(f"{seed}/prop/{i}", props)
        entity = stores("yard") if name == "minecraft:barrel" else None
        spot = choose(
            f"{seed}/prop-at/{i}", [(x1 + 2, z0 + 1 + i), (x0 - 2, z0 + 2 + i), (x0 + 1 + i, z0 - 2), (x1 + 1, z1 - i)]
        )
        if t.get((spot[0], 1, spot[1])) is not None or spot[0] < 0 or spot[1] < 0:
            continue
        if name == "woodpile":
            for dz in range(2):
                for y in (1, 2) if dz == 0 else (1,):
                    t.set((spot[0], y, spot[1] + dz), "minecraft:stripped_spruce_log", axis="x")
        else:
            t.set((spot[0], 1, spot[1]), name, entity=entity, **properties)


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
    t.set((lo + 1, 1, hi - 1), "minecraft:barrel", entity=stores("cellar"), facing="up", open=False)
    t.set((lo + 1, 1, hi - 2), "minecraft:barrel", entity=stores("cellar"), facing="east", open=False)
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
        t.set((x0 + 1, 1, z0 + 1), "minecraft:barrel", entity=stores("strongbox"), facing="up", open=True)
        t.set((x0 + 1, 1, z0 + 2), "minecraft:barrel", entity=stores("inn"), facing="east", open=False)
        t.set((x0 + 2, 1, z0 + 1), "minecraft:barrel", entity=stores("inn"), facing="up", open=False)
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
        t.set((x1 - 1, 1, z0 + 1), "minecraft:barrel", entity=stores("hall"), facing="up", open=True)
        t.set((x0 + 1, 1, z1 - 1), "minecraft:cauldron")
    t.door = (door, z1)
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
    t.set((x0 + 1, 1, z0 + 4), "minecraft:barrel", entity=stores("smithy"), facing="up", open=True)
    fallen_beam(t, (x0 + 1, z0 + 5), 4, "x", 1)
    burning(t, (x0 + 2, 2, z0 + 5))
    t.door = (x0 + 3, z1)
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
        t.set((x, 2, z), "minecraft:hay_block", axis="y")
        t.set((x + 1, 1, z), "minecraft:coarse_dirt")
    t.set((x1 - 1, 1, z0 + 1), "minecraft:magma_block")
    t.door = (door, z1)
    return t


def shed(seed):
    t = Template()
    m = MARGIN
    closed = noise(f"{seed}/closed") < 0.5
    width = 5
    depth = choose(f"{seed}/depth", [4, 5]) if closed else 3
    apron(t, width, depth, seed)
    x0, z0, x1, z1 = m, m, m + width - 1, m + depth - 1
    door = x0 + width // 2
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.set((x, 0, z), "minecraft:coarse_dirt" if coherent(f"{seed}/floor", (x, 0, z)) > 0.5 else ASH)
            if not (x in (x0, x1) or z in (z0, z1)):
                continue
            if x in (x0, x1) and z in (z0, z1):
                for y in range(1, 3 + (coherent(f"{seed}/post", (x, 0, z)) > 0.5)):
                    char(t, (x, y, z))
                continue
            if z == z1 and (not closed or x == door):
                continue
            for y in range(1, 2 + int(coherent(f"{seed}/infill", (x, 0, z)) * 3)):
                if coherent(f"{seed}/gap", (x, y, z)) > 0.7:
                    continue
                if closed:
                    t.set((x, y, z), PLANKS)
                else:
                    fence(t, (x, y, z))
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if coherent(f"{seed}/roof", (x, 0, z)) > 0.45:
                t.set((x, 3, z), "minecraft:dark_oak_slab", type="bottom", waterlogged=False)
    if closed:
        fallen_beam(t, (x0 + 1, z0 + 1), width - 2, "x", 1)
        burning(t, (x0 + 2, 2, z0 + 1))
        t.set((x1 - 1, 1, z1 - 1), "minecraft:barrel", entity=stores("shed"), facing="up", open=True)
        if depth > 4:
            t.set((x0 + 1, 1, z1 - 1), "minecraft:hay_block", axis="y")
    else:
        for x in range(x0 + 1, x1):
            for y in range(1, 2 + (coherent(f"{seed}/stack", (x, 0, z0)) > 0.4)):
                t.set((x, y, z0 + 1), "minecraft:stripped_spruce_log", axis="x")
        t.set((x1 + 1, 1, z1), "minecraft:stripped_oak_log", axis="y")
    t.door = (door, z1)
    t.extent = (width + 2 * m, depth + 2 * m)
    return t


def stall(seed):
    t = Template()
    for x in range(3):
        for z in range(3):
            t.set((x, 0, z), "minecraft:cobblestone")
    for x, z in ((0, 0), (2, 0), (0, 2), (2, 2)):
        for y in range(1, 2 + int(coherent(f"tollbridge/{seed}/post", (x, 0, z)) * 3)):
            fence(t, (x, y, z))
    t.set((1, 1, 0), "minecraft:barrel", entity=stores("stall"), facing="up", open=True)
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
    t.set((2, 1, 2), "minecraft:barrel", entity=stores("toll"), facing="up", open=True)
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
    "barn": ("outer", 3, barn),
    "cottage": ("any", 6, lambda seed: home(seed, 1, None, 5, choose(f"{seed}/depth", [5, 7]), leans=())),
    "home": ("any", 12, home),
    "shed": ("outer", 4, shed),
    "townhouse": (
        "inner",
        4,
        lambda seed: home(seed, 2, choose(f"{seed}/rich", ["plaster", "stone"]), choose(f"{seed}/rich-width", [7, 9])),
    ),
}
PIECES = {
    "bridge": bridge,
    "inn": lambda: hall("inn", 9, 13, strongbox=True),
    "smithy": smithy,
    "stall": lambda: stall("stall"),
    "toll_house": toll_house,
    "well": well,
} | {
    f"{kind}_{variant}": (lambda kind=kind, variant=variant: KINDS[kind][2](f"{kind}/{variant}"))
    for kind, (_, variants, _) in KINDS.items()
    for variant in range(1, variants + 1)
}
LOTS = [
    "barn",
    "cottage",
    "cottage",
    "cottage",
    "home",
    "home",
    "home",
    "home",
    "home",
    "home",
    "shed",
    "shed",
    "townhouse",
    "townhouse",
]
DEAD = 32
SMOKE = 8
STRONGBOX = (MARGIN + 1, 1, MARGIN + 1)
BELL = (9, 12)
BELL_SEAT = (8, 12)


def size(t):
    measured = [max(pos[axis] for pos in t.blocks) + 1 for axis in range(3)]
    extent = getattr(t, "extent", None)
    if extent:
        measured[0], measured[2] = max(measured[0], extent[0]), max(measured[2], extent[1])
    return measured


def hearth(t):
    found = [list(pos) for pos, (name, _, _) in t.blocks.items() if name == "minecraft:campfire" and pos[1] == 1]
    return found[0] if found else None


OPEN = {"bridge": ([2, 0], 0), "stall": ([1, 2], 0), "toll_house": ([2, 4], 0), "well": ([2, 4], 0)}


def entrance(name, t):
    if name in OPEN:
        return OPEN[name]
    return [*t.door], MARGIN


def catalog():
    pieces = {}
    for name, build in PIECES.items():
        t = build()
        doorway, margin = entrance(name, t)
        pieces[name] = {"door": doorway, "margin": margin, "size": size(t)} | (
            {"hearth": hearth(t)} if name.rsplit("_", 1)[0] in KINDS and hearth(t) else {}
        )
    return pieces


def dead():
    folk = townsfolk()
    well = next(name for name, look in folk.items() if any("p" in row for row in look["body_front"]))
    door = next(name for name, look in folk.items() if "arm_front" in look)
    rest = [name for name in folk if name not in (well, door)][: DEAD - 2]
    return {"doorway": door, "others": rest, "well": well}


def palette(material):
    return [[block, weight] for block, weight in material.entries]


def plan():
    lo, hi = 1, TOWER
    mid = lo + TOWER // 2
    return {
        "dead": dead(),
        "dead_poses": POSES,
        "found_pose": "fallen_back",
        "kinds": {
            kind: {"pieces": [f"{kind}_{variant}" for variant in range(1, variants + 1)], "ring": ring}
            for kind, (ring, variants, _) in sorted(KINDS.items())
        },
        "lots": LOTS,
        "materials": {"ash": palette(ASH), "square": palette(SQUARE), "street": palette(STREET)},
        "pieces": catalog(),
        "smoke": SMOKE,
        "strongbox": list(STRONGBOX),
        "tower": {
            "bell": list(BELL),
            "bell_facing": "east",
            "bell_seat": list(BELL_SEAT),
            "clue": [mid, CELLAR + 1, hi],
            "door": [mid, hi],
            "fire": [hi - 1, 1, hi - 1],
            "ground": CELLAR,
            "margin": 1,
            "seats": {
                "agna": [hi - 2, 1, hi - 1, "sitting_crossed"],
                "hob": [lo + 4, 1, lo + 2, "standing_arms_crossed"],
                "pip": [hi - 1, 1, hi - 2, "sitting_hunched"],
                "tamsin": [lo + 3, 1, lo + 2, "sitting_grieving"],
            },
            "size": size(tower()),
        },
    }

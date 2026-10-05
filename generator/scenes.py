import math

from blocks import Material, Template, coherent, noise

TRODDEN = Material(
    "scenes/trodden",
    [
        ("minecraft:grass_block", 45),
        ("minecraft:coarse_dirt", 30),
        ("minecraft:dirt_path", 15),
        ("minecraft:podzol", 10),
    ],
)
STONES = Material(
    "scenes/stones",
    [("minecraft:mossy_cobblestone", 40), ("minecraft:cobblestone", 35), ("minecraft:andesite", 25)],
)
FENCE = {"east": False, "north": False, "south": False, "waterlogged": False, "west": False}
STAIR = {"half": "bottom", "shape": "straight", "waterlogged": False}


def patch(t, width, depth, seed, radius=None):
    cx, cz = (width - 1) / 2, (depth - 1) / 2
    radius = radius or min(width, depth) / 2
    for x in range(width):
        for z in range(depth):
            reach = math.hypot((x - cx) / (width / 2), (z - cz) / (depth / 2)) + (coherent(seed, (x, 0, z)) - 0.5) * 0.5
            if reach <= 1:
                t.set((x, 0, z), TRODDEN)


def ring(t, cx, cz, lit, seed):
    for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1), (-1, -1), (1, 1), (-1, 1), (1, -1)):
        if noise(seed, dx, dz) > 0.2:
            t.set((cx + dx, 1, cz + dz), "minecraft:cobblestone_slab", type="bottom", waterlogged=False)
    t.set((cx, 0, cz), "minecraft:coarse_dirt")
    t.set((cx, 1, cz), "minecraft:campfire", facing="south", lit=lit, signal_fire=False, waterlogged=False)


def sack(t, pos):
    t.set(pos, "minecraft:brown_wool")
    t.set((pos[0], pos[1] + 1, pos[2]), "minecraft:brown_carpet")


def cache():
    t = Template()
    patch(t, 5, 5, "scenes/cache")
    for x in range(5):
        for z in range(5):
            if t.get((x, 0, z)) is None:
                t.set((x, 0, z), "minecraft:podzol")
    pit = (2, 2)
    t.set((pit[0], 0, pit[1]), "minecraft:coarse_dirt")
    t.set((pit[0], 1, pit[1]), "minecraft:brown_carpet")
    wall = {"east": "none", "north": "none", "south": "none", "up": True, "waterlogged": False, "west": "none"}
    stones = [
        ((-1, 0), "minecraft:mossy_cobblestone_wall", wall),
        ((1, 0), "minecraft:cobblestone_stairs", {"facing": "west"} | STAIR),
        ((0, 1), "minecraft:mossy_cobblestone_slab", {"type": "bottom", "waterlogged": False}),
        ((-1, 1), "minecraft:andesite_wall", wall),
        ((1, 1), "minecraft:mossy_cobblestone_stairs", {"facing": "north"} | STAIR),
        ((-1, -1), "minecraft:cobblestone_slab", {"type": "bottom", "waterlogged": False}),
    ]
    for (dx, dz), block, properties in stones:
        t.set((pit[0] + dx, 1, pit[1] + dz), block, **properties)
    t.set((pit[0] + 2, 1, pit[1] - 1), "minecraft:smooth_stone_slab", type="bottom", waterlogged=False)
    t.set((pit[0] - 1, 2, pit[1]), "minecraft:mossy_cobblestone_slab", type="bottom", waterlogged=False)
    t.set((pit[0], 1, pit[1] - 2), "minecraft:fern")
    return t


def herder():
    t = Template()
    patch(t, 9, 9, "scenes/herder")
    ring(t, 4, 4, True, "scenes/herder-ring")
    t.set((6, 1, 3), "minecraft:hay_block", axis="y")
    t.set((6, 2, 3), "minecraft:hay_block", axis="x")
    sack(t, (2, 1, 5))
    t.set((2, 1, 3), "minecraft:spruce_log", axis="x")
    for z in range(0, 9):
        if noise("scenes/fold", z) > 0.3:
            t.set((8, 1, z), "minecraft:spruce_fence", **FENCE)
    for x in range(5, 8):
        if noise("scenes/fold-side", x) > 0.35:
            t.set((x, 1, 0), "minecraft:spruce_fence", **FENCE)
    return t


def pedlar():
    t = Template()
    patch(t, 7, 7, "scenes/pedlar")
    colours = ["red", "yellow", "brown", "red", "orange", "brown"]
    for index, (x, z) in enumerate((x, z) for x in range(2, 5) for z in range(2, 4)):
        t.set((x, 1, z), f"minecraft:{colours[index]}_carpet")
    for x, z, open_ in ((1, 2, True), (5, 3, False)):
        t.set((x, 1, z), "minecraft:barrel", facing="up", open=open_)
    t.set((5, 1, 1), "minecraft:spruce_fence", **FENCE)
    t.set((5, 2, 1), "minecraft:lantern", hanging=False, waterlogged=False)
    t.set((1, 1, 4), "minecraft:decorated_pot", cracked=False, facing="south", waterlogged=False)
    return t


def traveller():
    t = Template()
    patch(t, 5, 5, "scenes/traveller")
    sack(t, (3, 1, 1))
    t.set(
        (1, 1, 3),
        "minecraft:spruce_trapdoor",
        facing="east",
        half="bottom",
        open=False,
        powered=False,
        waterlogged=False,
    )
    t.set((4, 1, 3), "minecraft:white_carpet")
    return t


def cart():
    t = Template()
    patch(t, 9, 9, "scenes/cart")
    x0 = 2
    for z in range(2, 7):
        for x in range(x0 + 1, x0 + 4):
            low = x == x0 + 1
            t.set((x, 1, z), "minecraft:spruce_slab", type="bottom" if low else "top", waterlogged=False)
    for z in (3, 5):
        t.set(
            (x0 + 4, 1, z),
            "minecraft:spruce_trapdoor",
            facing="east",
            half="bottom",
            open=True,
            powered=False,
            waterlogged=False,
        )
    t.set(
        (x0 - 1, 1, 6),
        "minecraft:spruce_trapdoor",
        facing="north",
        half="bottom",
        open=False,
        powered=False,
        waterlogged=False,
    )
    t.set((x0 + 2, 1, 1), "minecraft:spruce_fence", **FENCE)
    t.set((x0 + 3, 1, 0), "minecraft:stripped_spruce_log", axis="z")
    t.set((x0 + 2, 2, 4), "minecraft:barrel", facing="east", open=False)
    t.set((x0 + 5, 1, 2), "minecraft:barrel", facing="up", open=True)
    t.set((x0 + 5, 1, 6), "minecraft:hay_block", axis="z")
    sack(t, (x0 + 1, 1, 7))
    t.set((x0 + 4, 1, 8), "minecraft:brown_carpet")
    return t


def trunk():
    t = Template()
    length = 16
    for z in range(1, length + 1):
        t.set(
            (2, 1, z),
            "minecraft:spruce_log" if noise("scenes/bark", z) > 0.25 else "minecraft:stripped_spruce_log",
            axis="z",
        )
        if noise("scenes/trunk-moss", z) > 0.55:
            t.set((2, 2, z), "minecraft:moss_carpet")
    for x in range(0, 5):
        for y in range(0, 4):
            reach = math.hypot(x - 2, y - 1.5)
            if reach <= 2.3:
                block = "minecraft:rooted_dirt" if coherent("scenes/roots", (x, y, 0)) > 0.5 else "minecraft:dirt"
                t.set((x, y, 0), block)
    for z, side in ((6, 1), (11, 3), (14, 1)):
        t.set((side, 1, z), "minecraft:spruce_fence", **FENCE)
    t.set(
        (2, 1, length + 1),
        "minecraft:spruce_trapdoor",
        facing="north",
        half="bottom",
        open=True,
        powered=False,
        waterlogged=False,
    )
    return t


SCENES = {
    "cache": cache,
    "cart": cart,
    "herder": herder,
    "pedlar": pedlar,
    "traveller": traveller,
    "trunk": trunk,
}

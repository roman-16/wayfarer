import math

from blocks import AIR, Material, Template, bears, coherent, falls, noise

FUNGUS = {"minecraft:brown_mushroom", "minecraft:red_mushroom"}
SAND = {"minecraft:red_sand", "minecraft:sand", "minecraft:terracotta"}
SOIL = {
    "minecraft:coarse_dirt",
    "minecraft:dirt",
    "minecraft:grass_block",
    "minecraft:moss_block",
    "minecraft:mud",
    "minecraft:muddy_mangrove_roots",
    "minecraft:podzol",
    "minecraft:rooted_dirt",
}
INSETS = {"ladder": 2, "spiral": 3, "switchback": 3}
SIZES = {"ladder": 5, "spiral": 11, "switchback": 11}
DOOR = 2
TOUCHING = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))


def m(seed, entries):
    return Material(seed, [(f"minecraft:{block}", weight) for block, weight in entries])


class Cover:
    def __init__(self, style, seed):
        self.seed = seed
        self.size = SIZES[style]
        self.inset = INSETS[style]
        self.width = self.size + 2 * self.inset
        self.middle = self.width // 2
        self.low = self.inset
        self.high = self.inset + self.size - 1
        self.t = Template()
        self.clear = set()

    def cells(self):
        return [(x, z) for x in range(self.width) for z in range(self.width)]

    def shaft(self, x, z):
        return self.low <= x <= self.high and self.low <= z <= self.high

    def interior(self, x, z):
        return self.low < x < self.high and self.low < z < self.high

    def noise(self, name, pos):
        return coherent(f"{self.seed}/{name}", pos)

    def set(self, pos, name, **properties):
        pos = tuple(pos)
        if pos in self.clear or not (0 <= pos[0] < self.width and 0 <= pos[2] < self.width):
            return
        if isinstance(name, Material):
            name = name.at(pos, 0.35)
        self.t.set(pos, name, **properties)

    def get(self, pos):
        return self.t.get(tuple(pos))

    def solid(self, pos):
        return self.get(pos) not in (None, AIR)

    def carve(self, positions):
        for pos in positions:
            self.clear.add(tuple(pos))
            self.t.set(tuple(pos), AIR)

    def surface(self, x, z):
        return max(
            (y for (bx, y, bz), block in self.t.blocks.items() if (bx, bz) == (x, z) and block[0] != AIR), default=None
        )

    def plant(self, pos, name, **properties):
        pos = tuple(pos)
        if pos not in self.clear and self.get(pos) is None and rooted(name, self.get((pos[0], pos[1] - 1, pos[2]))):
            self.set(pos, name, **properties)

    def floating(self):
        solid = {pos for pos, block in self.t.blocks.items() if block[0] != AIR}
        grounded = {pos for pos in solid if pos[1] == 0 or pos[1] == 1 and self.shaft(pos[0], pos[2])}
        frontier = list(grounded)
        while frontier:
            x, y, z = frontier.pop()
            for dx, dy, dz in TOUCHING:
                near = (x + dx, y + dy, z + dz)
                if near in solid and near not in grounded:
                    grounded.add(near)
                    frontier.append(near)
        return sorted((self.get(pos), pos) for pos in solid - grounded)

    def sliding(self):
        for (x, y, z), (name, _, _) in self.t.blocks.items():
            if not falls(name) or y == 0 or y == 1 and self.shaft(x, z):
                continue
            below = self.t.blocks.get((x, y - 1, z))
            if below is None or not bears(below[0], dict(below[1])):
                yield (name, (x, y, z))

    def save(self, path):
        loose = self.floating()
        if loose:
            raise ValueError(f"{path.parent.name}/{path.name}: floating blocks: {loose[:6]}")
        sliding = sorted(self.sliding())
        if sliding:
            raise ValueError(f"{path.parent.name}/{path.name}: falling blocks over open space: {sliding[:6]}")
        height = max(pos[1] for pos in self.t.blocks) + 1
        self.t.save(path, [self.width, height, self.width])


def rooted(plant, below):
    if below in (None, AIR):
        return False
    if plant in FUNGUS:
        return below == "minecraft:podzol"
    if plant == "minecraft:dead_bush":
        return below in SOIL or below in SAND or below.endswith("_terracotta")
    if plant.startswith("minecraft:") and plant.split(":")[1] in FLORA:
        return below in SOIL
    return True


FLORA = {
    "azure_bluet",
    "blue_orchid",
    "fern",
    "lily_of_the_valley",
    "oxeye_daisy",
    "short_grass",
    "tall_grass",
}


def resolve(cover, block, pos):
    return block.at(pos, 0.35) if isinstance(block, Material) else block


def leaves(cover, pos, name="minecraft:oak_leaves"):
    cover.plant(pos, name, distance=1, persistent=True, waterlogged=False)


def scatter(cover, pool, density, name="growth"):
    for x, z in cover.cells():
        top = cover.surface(x, z)
        if top is None or (x, top + 1, z) in cover.clear:
            continue
        if cover.noise(name, (x, top, z)) > 1 - density:
            choice = pool[min(len(pool) - 1, int(noise(cover.seed, name, x, z) * len(pool)))]
            if choice.endswith("_leaves"):
                leaves(cover, (x, top + 1, z), choice)
            elif choice == "minecraft:snow":
                cover.plant((x, top + 1, z), choice, layers=1)
            else:
                cover.plant((x, top + 1, z), choice)


def tree(cover, x, z, log, leaf, height=4, radius=2):
    top = cover.surface(x, z)
    if top is None:
        return
    for y in range(top + 1, top + 1 + height):
        cover.set((x, y, z), log, axis="y")
    crown = top + height
    for dx in range(-radius, radius + 1):
        for dz in range(-radius, radius + 1):
            for dy in (-1, 0, 1):
                reach = math.hypot(dx, dz) + max(0, dy) * 1.2
                if (
                    reach <= radius + 0.3 - (noise(cover.seed, "crown", dx, dy, dz) * 0.8)
                    and (dx, dz) != (0, 0)
                    or dy == 1
                    and (dx, dz) == (0, 0)
                ):
                    pos = (x + dx, crown + dy, z + dz)
                    if cover.get(pos) is None and pos not in cover.clear:
                        cover.set(pos, leaf, distance=1, persistent=True, waterlogged=False)


def knoll(cover, turf, soil, crag=None, support=None, peak=4.5, slope=0.8, rough=0.9, craggy=0.2, floor=None):
    support = support or soil
    crag = crag or soil
    c, w = cover.middle, cover.width
    heights = {}
    for x, z in cover.cells():
        reach = math.hypot(x - c, z - c) + (cover.noise("warp", (x, 0, z)) - 0.5) * 3.5
        height = peak - slope * max(0.0, reach - 2.5) + (cover.noise("rise", (x, 0, z)) - 0.5) * 2 * rough
        height = round(height)
        if cover.shaft(x, z):
            height = max(height, DOOR + 1)
        heights[(x, z)] = height
    mouth = cover.high
    while (
        mouth + 1 < w
        and heights[(c, mouth + 1)] > DOOR
        and min(heights[(c - 1, mouth + 1)], heights[(c + 1, mouth + 1)]) >= DOOR
    ):
        mouth += 1
    inner = range(cover.low + 1, cover.high)
    cover.carve([(x, y, z) for x in inner for z in inner for y in range(1, DOOR + 1)])
    cover.carve([(c, y, z) for z in range(cover.high, mouth + 1) for y in range(1, DOOR + 1)])
    cover.carve([(c, y, z) for z in range(mouth + 1, w) for y in range(1, heights[(c, z)] + 1)])
    for (x, z), height in heights.items():
        for y in range(height + 1):
            pos = (x, y, z)
            if pos in cover.clear or y == 0 and cover.shaft(x, z):
                continue
            exposed = any(heights.get((x + dx, z + dz), -1) < y for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if y == height:
                block = turf
            elif exposed and cover.noise("crag", pos) > 1 - craggy * 2:
                block = crag
            else:
                block = soil
            name = resolve(cover, block, pos)
            if falls(name) and (x, y - 1, z) in cover.clear:
                name = resolve(cover, support, pos)
            cover.set(pos, name)
    for z in range(cover.high + 1, w):
        cover.set((c, 0, z), floor or soil)
    for z in range(cover.high + 1, mouth + 1):
        cover.set((c, DOOR + 1, z), resolve(cover, support, (c, DOOR + 1, z)))
    return heights, mouth


def roots(cover, density=0.25):
    earth = {"minecraft:coarse_dirt", "minecraft:dirt", "minecraft:rooted_dirt"}
    for x in range(cover.low + 1, cover.high):
        for z in range(cover.low + 1, cover.high):
            if cover.get((x, DOOR + 1, z)) in earth and cover.noise("roots", (x, DOOR, z)) > 1 - density:
                cover.t.set((x, DOOR, z), "minecraft:hanging_roots", waterlogged=False)


def ground(cover, natural, accent, spread=1.0, support=None):
    for x, z in cover.cells():
        reach = math.hypot(x - cover.middle, z - cover.middle) + (cover.noise("edge", (x, 0, z)) - 0.5) * 3
        if not cover.shaft(x, z) and reach > cover.width / 2 - 1:
            continue
        near = 1 - math.hypot(x - cover.middle, z - cover.middle) / (cover.width / 2 * spread)
        block = accent if cover.noise("accent", (x, 0, z)) + near * 0.6 > 0.75 else natural
        name = resolve(cover, block, (x, 0, z))
        if falls(name) and cover.interior(x, z):
            name = resolve(cover, support, (x, 0, z))
        cover.set((x, 0, z), name)


def hatch(cover, trapdoor):
    c = cover.middle
    cover.set((c, 0, c + 1), trapdoor, facing="north", half="bottom", open=True, powered=False, waterlogged=False)
    cover.carve([(c, y, z) for y in (1, 2) for z in (c, c + 1)])


def black(cover, wall):
    ground(
        cover,
        m("black/sand", [("sand", 80), ("gravel", 12), ("sandstone", 8)]),
        m("black/drift", [("sand", 40), ("sandstone", 30), ("gravel", 20), ("coarse_dirt", 10)]),
        support=m("black/bed", [("sandstone", 70), ("smooth_sandstone", 30)]),
    )
    hatch(cover, "minecraft:spruce_trapdoor")
    c = cover.middle
    fire = (c - 2, c - 2)
    cover.set((fire[0], 0, fire[1]), "minecraft:coarse_dirt")
    cover.set(
        (fire[0], 1, fire[1]), "minecraft:campfire", facing="south", lit=False, signal_fire=False, waterlogged=False
    )
    for dx, dz in ((1, 0), (-1, 0), (0, -1), (0, 1), (1, 1)):
        stone = (
            "minecraft:cobblestone_slab" if noise("black", "ring", dx, dz) < 0.6 else "minecraft:mossy_cobblestone_slab"
        )
        cover.plant((fire[0] + dx, 1, fire[1] + dz), stone, type="bottom", waterlogged=False)
    for z in (c - 1, c):
        cover.set((c + 2, 1, z), "minecraft:stripped_oak_log", axis="z")
    cover.set((c - 3, 1, c + 2), "minecraft:stripped_birch_log", axis="x")
    cover.set((c - 2, 1, c + 2), "minecraft:stripped_birch_log", axis="x")
    cover.plant((c + 3, 1, c + 3), "minecraft:bone_block", axis="x")
    scatter(cover, ["minecraft:dead_bush"], 0.15, "black")


def cold(cover, wall):
    snow = m("cold/snow", [("snow_block", 75), ("packed_ice", 25)])
    heights, _ = knoll(
        cover,
        "minecraft:snow_block",
        snow,
        crag=m("cold/ice", [("packed_ice", 60), ("blue_ice", 25), ("snow_block", 15)]),
        peak=4.2,
        slope=0.7,
        rough=0.7,
        craggy=0.25,
        floor="minecraft:packed_ice",
    )
    c = cover.middle
    for index in range(5):
        x = int(noise("cold", "shard", index, "x") * cover.width)
        z = int(noise("cold", "shard", index, "z") * cover.width)
        top = cover.surface(x, z)
        if top is not None and heights[(x, z)] >= 1 and abs(x - c) > 1:
            cover.set((x, top + 1, z), "minecraft:blue_ice" if index % 2 else "minecraft:packed_ice")
    tree(cover, c - 3, c - 2, "minecraft:spruce_log", "minecraft:spruce_leaves", height=5, radius=1)
    scatter(cover, ["minecraft:snow"], 0.8, "cold")


def corrupted(cover, wall):
    knoll(
        cover,
        "minecraft:grass_block",
        m("corrupted/soil", [("dirt", 70), ("coarse_dirt", 30)]),
        crag=m("corrupted/stone", [("cobblestone", 40), ("andesite", 30), ("mossy_cobblestone", 30)]),
        peak=4.0,
        slope=0.75,
        floor="minecraft:coarse_dirt",
    )
    c, last = cover.middle, cover.width - 1
    for x, height in ((c - 2, 2), (c + 2, 1)):
        for y in range(height + 1):
            cover.set((x, y, last), wall)
        cover.set((x, height + 1, last), "minecraft:moss_carpet")
    for z in (last - 1, last - 2):
        cover.set((c + 4, (cover.surface(c + 4, z) or 0) + 1, z), "minecraft:stripped_acacia_log", axis="z")
    tree(cover, c + 2, c - 2, "minecraft:acacia_log", "minecraft:acacia_leaves", height=4, radius=2)
    scatter(
        cover,
        ["minecraft:short_grass", "minecraft:short_grass", "minecraft:tall_grass", "minecraft:dead_bush"],
        0.45,
        "corrupted",
    )


def cryptic(cover, wall):
    rock = m("cryptic/rock", [("stone", 45), ("andesite", 25), ("cobblestone", 20), ("mossy_cobblestone", 10)])
    knoll(
        cover,
        rock,
        m("cryptic/core", [("stone", 60), ("andesite", 25), ("tuff", 15)]),
        crag=m("cryptic/crag", [("cobblestone", 50), ("mossy_cobblestone", 30), ("stone", 20)]),
        peak=5.0,
        slope=0.9,
        rough=1.3,
        craggy=0.35,
        floor="minecraft:gravel",
    )
    c = cover.middle
    top = cover.surface(c + 2, c - 1)
    cover.set((c + 2, top + 1, c - 1), "minecraft:cobblestone")
    cover.set((c + 2, top + 2, c - 1), "minecraft:cobblestone_wall")
    scatter(cover, ["minecraft:moss_carpet", "minecraft:moss_carpet", "minecraft:snow"], 0.3, "cryptic")


def evil(cover, wall):
    ground(
        cover,
        "minecraft:grass_block",
        m("evil/yard", [("coarse_dirt", 40), ("dirt_path", 25), ("grass_block", 25), ("mossy_cobblestone", 10)]),
        spread=0.8,
    )
    c, low, high = cover.middle, cover.low, cover.high
    for x in range(low + 1, high):
        for z in range(low + 1, high):
            cover.set(
                (x, 0, z),
                "minecraft:spruce_trapdoor",
                facing="north",
                half="top",
                open=False,
                powered=False,
                waterlogged=False,
            )
    cover.set((c, 0, c + 1), "minecraft:ladder", facing="north", waterlogged=False)
    cover.carve([(c, y, z) for y in (1, 2) for z in (c, c + 1) if (z, y) != (c + 1, 1)])
    for x, z in cover.cells():
        if cover.shaft(x, z) and not cover.interior(x, z) and (x, z) != (c, high):
            corner = x in (low, high) and z in (low, high)
            if noise("evil", "well", x, z) > (0.35 if corner else 0.12):
                cover.set(
                    (x, 1, z), m("evil/well", [("mossy_cobblestone", 50), ("cobblestone", 40), ("stone_bricks", 10)])
                )
    for x, z in ((c, high), (low, c), (high, c)):
        cover.set((x, 1, z), "minecraft:mossy_cobblestone")
    cover.set((c, 1, c + 1), "minecraft:ladder", facing="north", waterlogged=False)
    for y in (2, 3, 4):
        cover.set((low, y, c), "minecraft:oak_fence")
    cover.set((high, 2, c), "minecraft:oak_fence")
    cover.set((low, 5, c), "minecraft:oak_slab", type="bottom", waterlogged=False)
    cover.set((low + 1, 5, c), "minecraft:oak_slab", type="bottom", waterlogged=False)
    cover.set((low + 1, 4, c), "minecraft:chain", axis="y", waterlogged=False)
    for x in range(high + 1, high + 3):
        cover.plant((x, 1, c - 1), "minecraft:stripped_oak_log", axis="x")
    scatter(
        cover,
        [
            "minecraft:azure_bluet",
            "minecraft:short_grass",
            "minecraft:oxeye_daisy",
            "minecraft:tall_grass",
            "minecraft:short_grass",
        ],
        0.55,
        "evil",
    )


def exotic(cover, wall):
    bands = [
        "orange_terracotta",
        "terracotta",
        "red_terracotta",
        "white_terracotta",
        "orange_terracotta",
        "brown_terracotta",
    ]

    class Banded(Material):
        def at(self, pos, age=None):
            return (
                f"minecraft:{bands[pos[1] % len(bands)]}"
                if coherent("exotic/band", pos) > 0.3
                else "minecraft:red_sandstone"
            )

    banded = Banded("exotic/banded", [("minecraft:terracotta", 1)])
    _, mouth = knoll(
        cover,
        m("exotic/sand", [("red_sand", 85), ("red_sandstone", 15)]),
        banded,
        crag=banded,
        support=m("exotic/bed", [("red_sandstone", 70), ("cut_red_sandstone", 30)]),
        peak=4.8,
        slope=0.85,
        rough=1.1,
        craggy=0.4,
        floor="minecraft:red_sand",
    )
    c = cover.middle
    for x in (c - 1, c + 1):
        cover.set((x, 1, mouth), "minecraft:chiseled_red_sandstone")
        cover.set((x, 2, mouth), "minecraft:cut_red_sandstone")
    cover.set((c, 3, mouth), "minecraft:cut_red_sandstone")
    cover.set((c - 1, 3, mouth), "minecraft:cut_red_sandstone_slab", type="bottom", waterlogged=False)
    scatter(cover, ["minecraft:dead_bush"], 0.2, "exotic")


def lost(cover, wall):
    _, mouth = knoll(
        cover,
        "minecraft:grass_block",
        m("lost/soil", [("dirt", 75), ("coarse_dirt", 15), ("rooted_dirt", 10)]),
        crag=m("lost/stone", [("cobblestone", 40), ("stone", 35), ("mossy_cobblestone", 25)]),
        peak=4.3,
        slope=0.7,
        floor="minecraft:coarse_dirt",
    )
    roots(cover)
    c = cover.middle
    for x in (c - 1, c + 1):
        for y in (1, 2):
            cover.set((x, y, mouth), "minecraft:stripped_spruce_log", axis="y")
    for x in (c - 1, c, c + 1):
        cover.set((x, 3, mouth), "minecraft:spruce_log", axis="x")
    tree(cover, c - 2, c - 1, "minecraft:birch_log", "minecraft:birch_leaves", height=5, radius=2)
    scatter(
        cover, ["minecraft:short_grass", "minecraft:fern", "minecraft:short_grass", "minecraft:oak_leaves"], 0.5, "lost"
    )


def magical(cover, wall):
    ground(
        cover,
        "minecraft:grass_block",
        m("magical/floor", [("podzol", 45), ("rooted_dirt", 25), ("coarse_dirt", 15), ("moss_block", 15)]),
        spread=0.9,
    )
    hatch(cover, "minecraft:dark_oak_trapdoor")
    c, low, high = cover.middle, cover.low, cover.high
    for x, z in cover.cells():
        if not cover.shaft(x, z) or cover.interior(x, z) or x in (low, high) and z in (low, high) or (x, z) == (c, low):
            continue
        height = 1 + int(noise("magical", "stump", x, z) * 4)
        for y in range(1, height + 1):
            cover.set((x, y, z), "minecraft:dark_oak_log", axis="y")
    for x, z, axis in (
        (low - 1, c + 1, "x"),
        (low - 2, c + 1, "x"),
        (high + 1, c - 1, "x"),
        (c - 1, high + 1, "z"),
        (c + 1, low - 1, "z"),
    ):
        cover.set((x, 1, z), "minecraft:dark_oak_log", axis=axis)
    for x, z in ((c - 1, c - 1), (c + 1, c), (c - 1, c + 1)):
        cover.set((x, 0, z), "minecraft:podzol")
        cover.plant(
            (x, 1, z),
            "minecraft:red_mushroom" if noise("magical", "fungus", x, z) < 0.5 else "minecraft:brown_mushroom",
        )
    cover.set((c + 1, 3, c + 1), "minecraft:cobweb")
    scatter(
        cover,
        ["minecraft:fern", "minecraft:short_grass", "minecraft:brown_mushroom", "minecraft:dark_oak_leaves"],
        0.4,
        "magical",
    )


def old(cover, wall):
    _, mouth = knoll(
        cover,
        "minecraft:sand",
        m("old/sand", [("sand", 70), ("sandstone", 30)]),
        crag=m("old/stone", [("sandstone", 60), ("smooth_sandstone", 25), ("cut_sandstone", 15)]),
        support=m("old/bed", [("sandstone", 70), ("smooth_sandstone", 30)]),
        peak=4.2,
        slope=0.6,
        rough=0.5,
        craggy=0.15,
        floor="minecraft:sand",
    )
    c = cover.middle
    for x in (c - 1, c + 1):
        cover.set((x, 1, mouth), "minecraft:cut_sandstone")
        cover.set((x, 2, mouth), "minecraft:chiseled_sandstone")
    cover.set((c, 3, mouth), "minecraft:cut_sandstone")
    for index, (x, z) in enumerate(((1, c - 2), (1, c), (1, c + 2))):
        top = cover.surface(x, z) or 0
        cover.set((x, top + 1, z), "minecraft:bone_block", axis="x")
        if index == 1:
            cover.set((x, top + 2, z), "minecraft:bone_block", axis="y")
    scatter(cover, ["minecraft:dead_bush"], 0.2, "old")


def rogue(cover, wall):
    knoll(
        cover,
        m("rogue/moss", [("moss_block", 65), ("mossy_cobblestone", 25), ("grass_block", 10)]),
        m("rogue/heap", [("mossy_cobblestone", 45), ("cobblestone", 30), ("dirt", 25)]),
        crag=m("rogue/boulder", [("mossy_cobblestone", 50), ("mossy_stone_bricks", 20), ("cobblestone", 30)]),
        peak=4.8,
        slope=0.8,
        rough=1.2,
        craggy=0.35,
        floor="minecraft:moss_block",
    )
    c = cover.middle
    tree(cover, c + 2, c - 2, "minecraft:jungle_log", "minecraft:jungle_leaves", height=2, radius=2)
    scatter(
        cover,
        ["minecraft:fern", "minecraft:moss_carpet", "minecraft:jungle_leaves", "minecraft:jungle_leaves"],
        0.6,
        "rogue",
    )


def undead(cover, wall):
    ground(
        cover,
        m("undead/forest", [("podzol", 45), ("grass_block", 35), ("coarse_dirt", 20)]),
        m("undead/dug", [("coarse_dirt", 45), ("rooted_dirt", 35), ("podzol", 20)]),
        spread=0.7,
    )
    hatch(cover, "minecraft:spruce_trapdoor")
    c = cover.middle
    cover.carve([(c, 0, c + 1)])
    cover.t.set((c, 0, c + 1), "minecraft:ladder", facing="north", waterlogged=False)
    for x in (c - 1, c + 1):
        for z in (c, c + 1):
            cover.set((x, 1, z), m("undead/mound", [("coarse_dirt", 50), ("rooted_dirt", 50)]))
    cover.set((c, 1, c - 1), "minecraft:smooth_stone_slab", type="bottom", waterlogged=False)
    cover.set((c, 1, c + 2), "minecraft:mossy_stone_brick_wall")
    cover.set((c, 2, c + 2), "minecraft:mossy_stone_brick_wall")
    for x, z in ((c - 3, c - 3), (c + 3, c - 2)):
        cover.plant((x, 1, z), "minecraft:mossy_cobblestone_wall")
    cover.plant((c + 3, 1, c + 3), "minecraft:mossy_cobblestone")
    for z in range(c - 3, c):
        cover.plant((c - 3, 1, z + 4), "minecraft:spruce_log", axis="z")
    scatter(
        cover,
        ["minecraft:fern", "minecraft:lily_of_the_valley", "minecraft:short_grass", "minecraft:fern"],
        0.45,
        "undead",
    )


def witch(cover, wall):
    ground(
        cover,
        m("witch/marsh", [("grass_block", 70), ("mud", 20), ("moss_block", 10)]),
        m("witch/bog", [("mud", 45), ("muddy_mangrove_roots", 25), ("moss_block", 20), ("grass_block", 10)]),
        spread=0.8,
    )
    hatch(cover, "minecraft:mangrove_trapdoor")
    c = cover.middle
    for x, y, z in ((c - 1, 1, c + 2), (c - 1, 2, c + 2), (c, 1, c + 2), (c + 1, 1, c + 2), (c + 2, 1, c + 1)):
        cover.set((x, y, z), "minecraft:mangrove_roots", waterlogged=False)
    for x in range(c - 3, c):
        cover.set((x, 1, c - 2), "minecraft:mangrove_log", axis="x")
        cover.plant((x, 2, c - 2), "minecraft:moss_carpet")
    cover.set((c - 2, 0, c - 2), "minecraft:cauldron")
    if cover.solid((c + 3, 0, c - 3)):
        cover.set((c + 3, 1, c - 3), "minecraft:oak_fence")
        cover.set((c + 3, 2, c - 3), "minecraft:oak_fence")
    scatter(
        cover,
        ["minecraft:blue_orchid", "minecraft:moss_carpet", "minecraft:short_grass", "minecraft:short_grass"],
        0.4,
        "witch",
    )


ENTRANCES = {
    "black": black,
    "cold": cold,
    "corrupted": corrupted,
    "cryptic": cryptic,
    "evil": evil,
    "exotic": exotic,
    "lost": lost,
    "magical": magical,
    "old": old,
    "rogue": rogue,
    "undead": undead,
    "witch": witch,
}

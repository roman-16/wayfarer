import math

from blocks import Material, Template, coherent

WALLS = (11, 17)
MARGIN = 2
HOUSE = (WALLS[0] + MARGIN * 2, 9, WALLS[1] + MARGIN * 2)
DRIFTS = {"back": (12, 6, 2), "front": (4, 2, 0), "lee": (6, 3, 1), "weather": (20, 12, 5)}
FLOOR = 1
WALL_TOP = 3
RIDGE = 6
DOOR = (5, 11)
HEARTH = (5, 8)
SMOKE = (5, 8)
LAKE = 48
LAKE_DEPTH = 5
ICE = 2
SHORE = 2
STONE = Material(
    "frost/stone",
    [
        ("minecraft:cobblestone", 40),
        ("minecraft:mossy_cobblestone", 15),
        ("minecraft:andesite", 30),
        ("minecraft:stone", 15),
    ],
)
HIDE = Material("frost/hide", [("minecraft:brown_wool", 70), ("minecraft:brown_terracotta", 30)])
BED = Material(
    "frost/bed", [("minecraft:white_carpet", 50), ("minecraft:light_gray_carpet", 30), ("minecraft:brown_carpet", 20)]
)
BED_FLOOR = Material(
    "frost/floor",
    [("minecraft:spruce_planks", 60), ("minecraft:stripped_spruce_wood", 25), ("minecraft:coarse_dirt", 15)],
)
LAKE_BED = Material("frost/lake_bed", [("minecraft:gravel", 50), ("minecraft:clay", 25), ("minecraft:stone", 25)])
SHORE_STONE = Material(
    "frost/shore", [("minecraft:cobblestone", 30), ("minecraft:stone", 50), ("minecraft:andesite", 20)]
)


class Shifted:
    def __init__(self, template, shift):
        self.template = template
        self.shift = shift

    def set(self, pos, name, entity=None, **properties):
        self.template.set((pos[0] + self.shift, pos[1], pos[2] + self.shift), name, entity, **properties)


def roof_height(x):
    middle = (WALLS[0] - 1) / 2
    return WALL_TOP + round((middle - abs(x - middle)) * (RIDGE - WALL_TOP) / middle)


def drift(x, z):
    first, last = MARGIN, MARGIN + WALLS[0] - 1
    front, back = MARGIN + WALLS[1] - 1, MARGIN
    sides = []
    if x <= first:
        sides.append(("weather", first - x))
    if x >= last:
        sides.append(("lee", x - last))
    if z <= back:
        sides.append(("back", back - z))
    if z >= front:
        sides.append(("front", z - front))
    door = z >= front and abs(x - (MARGIN + DOOR[0])) <= 1
    eighths = max((DRIFTS[side][min(away, 2)] for side, away in sides), default=0)
    if door:
        return 0
    return max(0, eighths + round((coherent("frost/drift", (x, 0, z)) - 0.5) * 6))


def house():
    t = Template()
    width, _, length = HOUSE
    for x in range(width):
        for z in range(length):
            t.set((x, 0, z), "minecraft:dirt")
            depth = drift(x, z)
            if depth == 0:
                continue
            for y in range(FLOOR, FLOOR + depth // 8):
                t.set((x, y, z), "minecraft:snow_block")
            if depth % 8:
                t.set((x, FLOOR + depth // 8, z), "minecraft:snow", layers=depth % 8)
    build(Shifted(t, MARGIN))
    return t


def build(t):
    width, length = WALLS
    for x in range(1, width - 1):
        for z in range(1, length - 1):
            if x in (1, width - 2) or z in (1, length - 2):
                for y in range(FLOOR, WALL_TOP + 1):
                    t.set((x, y, z), STONE)
            else:
                t.set((x, 0, z), BED_FLOOR)
    for x in range(1, width - 1):
        top = roof_height(x)
        for z in range(1, length - 1):
            if x in (1, width - 2):
                continue
            if (x, z) == SMOKE or x == (width - 1) // 2 and z == SMOKE[1]:
                continue
            t.set((x, top, z), HIDE)
            if top < RIDGE - 1 or coherent("frost/roof_snow", (x, top + 1, z)) > 0.55:
                t.set(
                    (x, top + 1, z),
                    "minecraft:snow",
                    layers=2 + int(coherent("frost/roof_snow_depth", (x, top + 2, z)) * 3),
                )
    middle = (width - 1) // 2
    for z in range(0, length):
        if (middle, z) != SMOKE:
            t.set((middle, RIDGE, z), "minecraft:spruce_log", axis="z")
    for z in (1, length - 2):
        for x in range(2, width - 2):
            for y in range(WALL_TOP + 1, roof_height(x)):
                t.set((x, y, z), HIDE)
        for x in range(2, width - 2):
            t.set((x, roof_height(x), z), "minecraft:stripped_spruce_log", axis="x")
    door_x, door_z = DOOR[0], length - 2
    for y in (FLOOR, FLOOR + 1):
        t.set((door_x, y, door_z), "minecraft:air")
    t.set(
        (door_x, FLOOR, door_z),
        "minecraft:spruce_door",
        facing="south",
        half="lower",
        hinge="left",
        open=False,
        powered=False,
    )
    t.set(
        (door_x, FLOOR + 1, door_z),
        "minecraft:spruce_door",
        facing="south",
        half="upper",
        hinge="left",
        open=False,
        powered=False,
    )
    t.set((door_x - 1, FLOOR + 2, door_z + 1), "minecraft:brown_wool")
    t.set((door_x + 1, FLOOR + 2, door_z + 1), "minecraft:brown_wool")
    t.set((door_x, FLOOR + 2, door_z + 1), "minecraft:spruce_slab", type="top", waterlogged=False)
    hx, hz = HEARTH
    for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        t.set((hx + dx, FLOOR, hz + dz), "minecraft:cobblestone_slab", type="bottom", waterlogged=False)
    t.set((hx, FLOOR, hz), "minecraft:campfire", facing="north", lit=True, signal_fire=False, waterlogged=False)
    for x in (2, 3):
        for z in (2, 3, 4):
            t.set((x, FLOOR, z), BED)
    for x in (width - 4, width - 3):
        for z in (2, 3):
            t.set((x, FLOOR, z), BED)
    t.set((width - 3, FLOOR, 13), "minecraft:barrel", facing="up", open=False)
    t.set((width - 3, FLOOR + 1, 13), "minecraft:barrel", facing="up", open=False)
    t.set((2, FLOOR, 13), "minecraft:chest", facing="east", type="single", waterlogged=False)
    for z in (6, 10):
        t.set(
            (2, FLOOR, z), "minecraft:spruce_fence", east=False, north=False, south=False, west=False, waterlogged=False
        )
        t.set(
            (2, FLOOR + 1, z),
            "minecraft:spruce_fence",
            east=False,
            north=False,
            south=False,
            west=False,
            waterlogged=False,
        )
    t.set((2, FLOOR + 2, 8), "minecraft:brown_wall_banner", facing="east")
    t.set((width - 3, FLOOR + 2, 8), "minecraft:white_wall_banner", facing="west")
    t.set((middle, RIDGE - 1, 12), "minecraft:lantern", hanging=True, waterlogged=False)
    return t


def lake():
    t = Template()
    centre = LAKE // 2
    radius = centre - SHORE
    for x in range(LAKE):
        for z in range(LAKE):
            reach = math.hypot(x - centre, z - centre) + (coherent("frost/lake_edge", (x, 0, z)) - 0.5) * 2.5
            if reach > radius + SHORE:
                continue
            if reach > radius:
                t.set((x, LAKE_DEPTH, z), "minecraft:snow_block")
                continue
            t.set((x, 0, z), LAKE_BED)
            depth = 1 + max(0, round((radius - reach) / radius * (LAKE_DEPTH - 2)))
            for y in range(1, LAKE_DEPTH - ICE + 1):
                if y <= LAKE_DEPTH - ICE - depth:
                    t.set((x, y, z), LAKE_BED)
                else:
                    t.set((x, y, z), "minecraft:water", level=0)
            for y in range(LAKE_DEPTH - ICE + 1, LAKE_DEPTH + 1):
                t.set((x, y, z), "minecraft:ice")
    stone = LAKE_DEPTH - ICE
    for y in range(stone):
        t.set((centre, y, centre), "minecraft:stone")
    t.set(
        (centre, stone, centre),
        "wayfarer:calling_stone",
        entity={"id": "wayfarer:calling_stone", "hall": "wayfarer:hall/cold"},
    )
    rock = (centre, LAKE_DEPTH, 0)
    for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1)):
        t.set((rock[0] + dx, rock[1] + dy, rock[2]), SHORE_STONE)
    t.set((rock[0], rock[1], rock[2] + 1), "wayfarer:wayfarer_sign", facing="south")
    return t


def positions():
    width, length = WALLS
    centre = LAKE // 2
    return {
        "house": {
            "door": [MARGIN + DOOR[0], FLOOR, MARGIN + length],
            "hearth": [MARGIN + HEARTH[0], FLOOR, MARGIN + HEARTH[1]],
            "hearth_seat": [MARGIN + HEARTH[0] + 2, FLOOR, MARGIN + HEARTH[1]],
            "kill": [MARGIN + DOOR[0] + 2, FLOOR, MARGIN + length],
            "roof": [MARGIN + (width - 1) // 2, RIDGE + 1, MARGIN + length - 4],
            "size": list(HOUSE),
        },
        "lake": {
            "stone": [centre, LAKE_DEPTH - ICE, centre],
            "rim": [centre + 2, LAKE_DEPTH + 1, centre],
            "shore": [centre + 2, LAKE_DEPTH + 1, 1],
            "sign": [centre, LAKE_DEPTH, 1],
            "size": [LAKE, LAKE_DEPTH + 2, LAKE],
            "surface": LAKE_DEPTH,
        },
    }

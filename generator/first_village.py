import math

from blocks import Material, Template, coherent

HOUSES = [(7, 5), (8, 6), (6, 6), (9, 6), (7, 6), (6, 5)]
CHIMNEY = 4
RING = 15
RING_JITTER = 3
PIT = 5
PIT_OFFSET = (0, 8)
DOOR_SIDE = 0

WALLS = Material(
    "first_village/walls",
    [
        ("minecraft:mossy_cobblestone", 45),
        ("minecraft:cobblestone", 30),
        ("minecraft:stone", 15),
        ("minecraft:mossy_stone_bricks", 10),
    ],
)
FLOOR = Material(
    "first_village/floor",
    [
        ("minecraft:grass_block", 50),
        ("minecraft:coarse_dirt", 20),
        ("minecraft:moss_block", 15),
        ("minecraft:cobblestone", 15),
    ],
    spread=2.2,
)
SLAG = Material(
    "first_village/slag",
    [
        ("minecraft:blackstone", 40),
        ("minecraft:basalt", 20),
        ("minecraft:coal_block", 15),
        ("minecraft:tuff", 15),
        ("minecraft:cobbled_deepslate", 10),
    ],
    spread=2.4,
)
GROWTH = [
    "minecraft:short_grass",
    "minecraft:short_grass",
    "minecraft:short_grass",
    "minecraft:fern",
    "minecraft:short_grass",
    "minecraft:dandelion",
    "minecraft:short_grass",
    "minecraft:oxeye_daisy",
]
GREEN = ("minecraft:grass_block", "minecraft:moss_block")


def wall_height(seed, x, z, corner):
    rise = round(coherent(f"{seed}/height", (x, 0, z)) * 4)
    return max(2, rise + 1) if corner else max(0, rise - 1)


def house(index):
    width, depth = HOUSES[index]
    seed = f"first_village/house_{index}"
    t = Template()
    door = width // 2
    chimney = (1, depth - 1) if index % 2 else (width - 2, depth - 1)
    for x in range(width):
        for z in range(depth):
            t.set((x, 0, z), FLOOR)
            edge = x in (0, width - 1) or z in (0, depth - 1)
            if not edge or (z == DOOR_SIDE and x == door):
                continue
            corner = x in (0, width - 1) and z in (0, depth - 1)
            height = wall_height(seed, x, z, corner)
            for y in range(1, height + 1):
                t.set((x, y, z), WALLS)
            if height and not corner and coherent(f"{seed}/cap", (x, height, z)) > 0.62:
                t.set((x, height, z), "minecraft:mossy_cobblestone_slab", type="bottom", waterlogged=False)
    for y in range(1, CHIMNEY + 1):
        t.set((chimney[0], y, chimney[1]), "minecraft:cobblestone" if y < CHIMNEY else "minecraft:mossy_cobblestone")
    t.set(
        (chimney[0], CHIMNEY + 1, chimney[1]),
        "minecraft:cobblestone_wall",
        east="none",
        north="none",
        south="none",
        up=True,
        waterlogged=False,
        west="none",
    )
    t.set((chimney[0], 0, chimney[1] - 1), "minecraft:cobblestone")
    t.set(
        (chimney[0], 1, chimney[1] - 1),
        "minecraft:campfire",
        facing="north",
        lit=False,
        signal_fire=False,
        waterlogged=False,
    )
    beam_z = 1 + int(coherent(f"{seed}/beam", (0, 0, 0)) * (depth - 3))
    for x in range(1, min(width - 1, 5)):
        if (x, 1, beam_z) not in t.blocks:
            t.set((x, 1, beam_z), "minecraft:dark_oak_log", axis="x")
    post = (width - 2, 1) if index % 2 else (1, 1)
    t.set((post[0], 1, post[1]), "minecraft:dark_oak_log", axis="y")
    t.set((post[0], 2, post[1]), "minecraft:dark_oak_log", axis="y")
    t.set((post[0], 3, post[1]), "minecraft:dark_oak_slab", type="bottom", waterlogged=False)
    for x in range(1, width - 1):
        for z in range(1, depth - 1):
            if (x, 1, z) in t.blocks or t.get((x, 0, z)) not in GREEN:
                continue
            growth = coherent(f"{seed}/growth", (x, 1, z))
            if growth > 0.62:
                t.set((x, 1, z), GROWTH[(x * 7 + z * 3 + index) % len(GROWTH)])
            elif growth < 0.3:
                t.set((x, 1, z), "minecraft:moss_carpet")
    t.entrance((door, 0, DOOR_SIDE), "north")
    return t


def pit():
    t = Template()
    middle = PIT // 2
    for x in range(PIT):
        for z in range(PIT):
            corner = x in (0, PIT - 1) and z in (0, PIT - 1)
            if corner:
                t.set((x, 0, z), "minecraft:grass_block")
            elif (x, z) == (middle, middle):
                t.set((x, 0, z), "minecraft:cauldron")
            else:
                t.set((x, 0, z), SLAG)
    t.set((0, 1, middle), "minecraft:blackstone_slab", type="bottom", waterlogged=False)
    t.set((PIT - 1, 1, middle - 1), "minecraft:blackstone_slab", type="bottom", waterlogged=False)
    t.set((middle + 1, 1, PIT - 1), "minecraft:blackstone_slab", type="bottom", waterlogged=False)
    return t


def houses():
    return {f"first_village/house_{index}": house(index) for index in range(len(HOUSES))}


def positions():
    ring = []
    for index in range(len(HOUSES)):
        angle = math.tau * (index + 0.15 + coherent("first_village/angle", (index, 0, 0)) * 0.5) / len(HOUSES)
        radius = RING + round((coherent("first_village/ring", (index, 0, 0)) - 0.5) * 2 * RING_JITTER)
        dx = round(math.sin(angle) * radius)
        dz = round(math.cos(angle) * radius)
        ring.append(
            {
                "offset": [dx, dz],
                "template": f"first_village/house_{index}",
                "turn": -round(math.degrees(angle) / 90) % 4,
            }
        )
    return {
        "houses": ring,
        "melt": [PIT // 2, 0, PIT // 2],
        "pit": {"offset": list(PIT_OFFSET), "template": "first_village/pit"},
    }

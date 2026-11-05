import math

from blocks import AIR, Material, Template, candle, coherent, noise, text
from nbt import Byte

SIZE = 15
CENTRE = SIZE // 2
FIRE = (CENTRE, CENTRE)
GRAVE = (2, 9)
HEADSTONE = (1, 1)
STRANGER = (5.4, 1, 7.5, 270)
WAKE = (9.5, 1, 10.5, 126)
GROUND = Material(
    "camp/ground",
    [
        ("minecraft:coarse_dirt", 30),
        ("minecraft:dirt_path", 20),
        ("minecraft:rooted_dirt", 15),
        ("minecraft:podzol", 15),
        ("minecraft:gravel", 10),
        ("minecraft:coarse_dirt", 10),
    ],
    ground=0,
    fade=1,
)
CANVAS = Material(
    "camp/canvas",
    [("minecraft:white_wool", 65), ("minecraft:light_gray_wool", 25), ("minecraft:brown_wool", 10)],
    ground=4,
)
STONES = Material(
    "camp/stones", [("minecraft:cobblestone", 40), ("minecraft:mossy_cobblestone", 35), ("minecraft:andesite", 25)]
)
GROWTH = [
    "minecraft:short_grass",
    "minecraft:fern",
    "minecraft:short_grass",
    "minecraft:tall_grass",
    "minecraft:short_grass",
]
SLABS = ["minecraft:andesite_slab", "minecraft:cobblestone_slab", "minecraft:mossy_cobblestone_slab"]
FLOWERS = ["minecraft:lily_of_the_valley", "minecraft:oxeye_daisy", "minecraft:cornflower"]


def ground(t):
    for x in range(SIZE):
        for z in range(SIZE):
            d = math.hypot(x - FIRE[0], z - FIRE[1])
            wild = d + (coherent("camp/edge", (x, 0, z)) - 0.5) * 4
            if wild > 6.2:
                t.set((x, 0, z), "minecraft:grass_block", snowy=False)
                roll = coherent("camp/growth", (x, 1, z))
                plant = GROWTH[int(roll * 997) % len(GROWTH)]
                if roll > 0.55 and plant == "minecraft:tall_grass":
                    t.set((x, 1, z), plant, half="lower")
                    t.set((x, 2, z), plant, half="upper")
                elif roll > 0.55:
                    t.set((x, 1, z), plant)
            else:
                t.set((x, 0, z), GROUND)


def fire(t):
    x, z = FIRE
    t.set((x, 0, z), "minecraft:coarse_dirt")
    t.set((x, 1, z), "minecraft:campfire", facing="south", lit=True, signal_fire=False, waterlogged=False)
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, 1), (1, -1), (-1, -1)):
        t.set((x + dx, 0, z + dz), STONES)
        if dx and dz:
            t.set((x + dx, 1, z + dz), SLABS[int(noise("ring", dx, dz) * len(SLABS))], type="bottom", waterlogged=False)
    for pos, axis in (
        ((x - 3, 1, z - 1), "z"),
        ((x - 3, 1, z), "z"),
        ((x - 1, 1, z - 3), "x"),
        ((x, 1, z - 3), "x"),
        ((x + 1, 1, z - 3), "x"),
        ((x + 3, 1, z + 1), "z"),
    ):
        t.set(pos, "minecraft:stripped_spruce_log", axis=axis)


def tent(t):
    front, back = 9, 13
    for x in range(front, back + 1):
        for y, zs in ((1, (1, 5)), (2, (2, 4)), (3, (3,))):
            for z in zs:
                t.set((x, y, z), CANVAS)
        if x == back:
            for y, zs in ((1, (2, 3, 4)), (2, (3,))):
                for z in zs:
                    t.set((x, y, z), CANVAS)
        else:
            for y, zs in ((1, (2, 3, 4)), (2, (3,))):
                for z in zs:
                    t.set((x, y, z), AIR)
        t.set((x, 0, 2), "minecraft:spruce_planks")
        t.set((x, 0, 3), "minecraft:spruce_planks")
        t.set((x, 0, 4), "minecraft:spruce_planks")
    for x in (front - 1, back + 1):
        for y in range(1, 5):
            t.set((x, y, 3), "minecraft:spruce_fence")
    t.set((11, 1, 3), "minecraft:brown_carpet")
    t.set((12, 1, 3), "minecraft:brown_carpet")
    t.set((back, 1, 2), "minecraft:barrel", facing="up", open=False)
    for x, z in ((front - 1, 0), (front - 1, 6), (back + 1, 0), (back + 1, 6)):
        t.set((x, 1, z), "minecraft:spruce_fence")
    t.set((front - 1, 1, 6), "minecraft:spruce_fence")
    t.set((front - 1, 2, 6), "minecraft:lantern", hanging=False, waterlogged=False)


def grave():
    t = Template()
    for x in range(3):
        for z in range(1, 4):
            t.set((x, 0, z), "minecraft:podzol", snowy=False)
    for x, z in ((1, 2), (1, 3)):
        t.set((x, 1, z), "minecraft:coarse_dirt" if noise("mound", x + 2, z + 9) < 0.5 else "minecraft:rooted_dirt")
    x, z = HEADSTONE
    for y in (1, 2):
        t.set(
            (x, y, z),
            "minecraft:mossy_cobblestone_wall",
            east="none",
            north="none",
            south="none",
            up=True,
            west="none",
            waterlogged=False,
        )
    blank = {"color": "black", "has_glowing_text": Byte(0), "messages": [text("")] * 4}
    sign = {"back_text": blank, "front_text": blank, "id": "minecraft:sign", "is_waxed": Byte(0)}
    t.set((x, 1, z - 1), "minecraft:spruce_sign", entity=sign, rotation=8, waterlogged=False)
    for x, z in ((0, 2), (2, 3), (0, 3), (2, 1)):
        t.set((x, 1, z), FLOWERS[int(noise("flower", x + 2, z + 9) * len(FLOWERS))])
    candle(t, (1, 2, 2), "minecraft:candle", 1)
    t.set((3, 1, 3), "minecraft:spruce_fence")
    t.set((3, 2, 3), "minecraft:lantern", hanging=False, waterlogged=False)
    return t


def supplies(t):
    for z in range(8, 11):
        t.set((13, 1, z), "minecraft:spruce_log", axis="z")
    for z in (8, 9):
        t.set((13, 2, z), "minecraft:spruce_log", axis="z")
    t.set((12, 1, 12), "minecraft:barrel", facing="north", open=False)
    t.set((13, 1, 12), "minecraft:decorated_pot", facing="south", cracked=True, waterlogged=False)
    t.set((12, 2, 12), "minecraft:potted_fern")


def camp():
    t = Template()
    ground(t)
    fire(t)
    tent(t)
    supplies(t)
    return t


OLD_SIZE = 9
OLD_CENTRE = OLD_SIZE // 2
MOSS = Material(
    "old_camp/stones", [("minecraft:mossy_cobblestone", 55), ("minecraft:cobblestone", 25), ("minecraft:andesite", 20)]
)


def old_camp():
    t = Template()
    for x in range(OLD_SIZE):
        for z in range(OLD_SIZE):
            d = math.hypot(x - OLD_CENTRE, z - OLD_CENTRE)
            if d + (coherent("last_camp/edge", (x, 0, z)) - 0.5) * 3 > 3.6:
                t.set((x, 0, z), "minecraft:grass_block", snowy=False)
                if coherent("last_camp/growth", (x, 1, z)) > 0.6:
                    t.set((x, 1, z), "minecraft:short_grass")
            else:
                t.set((x, 0, z), "minecraft:podzol", snowy=False)
    t.set((OLD_CENTRE, 0, OLD_CENTRE), "minecraft:coarse_dirt")
    t.set(
        (OLD_CENTRE, 1, OLD_CENTRE),
        "minecraft:campfire",
        facing="south",
        lit=False,
        signal_fire=False,
        waterlogged=False,
    )
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        t.set((OLD_CENTRE + dx, 0, OLD_CENTRE + dz), MOSS)
    for index, (dx, dz) in enumerate(((1, 1), (-1, 1), (1, -1), (-1, -1))):
        if index % 2:
            t.set((OLD_CENTRE + dx, 1, OLD_CENTRE + dz), SLABS[index % len(SLABS)], type="bottom", waterlogged=False)
    for x, z in ((1, 2), (6, 1), (7, 5)):
        t.set((x, 1, z), "minecraft:mossy_cobblestone")
    t.set((2, 1, 6), "minecraft:stripped_spruce_log", axis="x")
    t.set((3, 1, 6), "minecraft:stripped_spruce_log", axis="x")
    t.set((6, 1, 6), "minecraft:brown_mushroom")
    return t

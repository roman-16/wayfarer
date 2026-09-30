import math

from blocks import AIR, Material, Template, candle, coherent, lectern, noise, text
from nbt import Byte

SIZE = 15
CENTRE = SIZE // 2
FIRE = (CENTRE, CENTRE)
HEADSTONE = (3, 10)
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
    [("minecraft:white_wool", 62), ("minecraft:light_gray_wool", 30), ("minecraft:white_terracotta", 8)],
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
GRAVE_LINES = ["Here rests", "the last", "Wayfarer.", "Carry the ember."]


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
    t.set((x, 1, z), "minecraft:campfire", facing="south", lit=False, signal_fire=False, waterlogged=False)
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
    t.set((11, 1, 3), "comforts:sleeping_bag_brown", facing="east", occupied=False, part="foot")
    t.set((12, 1, 3), "comforts:sleeping_bag_brown", facing="east", occupied=False, part="head")
    t.set((back, 1, 2), "minecraft:barrel", facing="up", open=False)
    for x, z in ((front - 1, 0), (front - 1, 6), (back + 1, 0), (back + 1, 6)):
        t.set((x, 1, z), "minecraft:spruce_fence")
    t.set((front - 1, 1, 6), "minecraft:spruce_fence")
    t.set((front - 1, 2, 6), "minecraft:lantern", hanging=False, waterlogged=False)


def grave(t):
    for x in range(2, 5):
        for z in range(10, 13):
            t.set((x, 0, z), "minecraft:podzol", snowy=False)
    for x, z in ((3, 11), (3, 12)):
        t.set((x, 1, z), "minecraft:coarse_dirt" if noise("mound", x, z) < 0.5 else "minecraft:rooted_dirt")
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
    sign = {
        "back_text": {"color": "black", "has_glowing_text": Byte(0), "messages": [text("")] * 4},
        "front_text": {"color": "black", "has_glowing_text": Byte(0), "messages": [text(line) for line in GRAVE_LINES]},
        "id": "minecraft:sign",
        "is_waxed": Byte(1),
    }
    t.set((x, 1, z - 1), "minecraft:spruce_sign", entity=sign, rotation=8, waterlogged=False)
    for x, z in ((2, 11), (4, 12), (2, 12), (4, 10)):
        t.set((x, 1, z), FLOWERS[int(noise("flower", x, z) * len(FLOWERS))])
    candle(t, (3, 2, 11), "minecraft:candle", 1)
    t.set((5, 1, 12), "minecraft:spruce_fence")
    t.set((5, 2, 12), "minecraft:lantern", hanging=False, waterlogged=False)


def journal_stand(t, journal):
    x, z = 11, 10
    for dx in (-1, 1):
        for y in (1, 2):
            t.set((x + dx, y, z + 1), "minecraft:spruce_fence")
    for dx in (-1, 0, 1):
        t.set((x + dx, 3, z + 1), "minecraft:spruce_slab", type="bottom", waterlogged=False)
        t.set(
            (x + dx, 3, z),
            "minecraft:spruce_trapdoor",
            facing="north",
            half="top",
            open=False,
            powered=False,
            waterlogged=False,
        )
    lectern(t, (x, 1, z), "north", journal["id"], journal["title"], journal["author"], journal["pages"])


def supplies(t):
    for z in range(8, 11):
        t.set((13, 1, z), "minecraft:spruce_log", axis="z")
    for z in (8, 9):
        t.set((13, 2, z), "minecraft:spruce_log", axis="z")
    t.set((12, 1, 12), "minecraft:barrel", facing="north", open=False)
    t.set((13, 1, 12), "minecraft:decorated_pot", facing="south", cracked=True, waterlogged=False)
    t.set((12, 2, 12), "minecraft:potted_fern")


def camp(journal):
    t = Template()
    ground(t)
    fire(t)
    tent(t)
    grave(t)
    journal_stand(t, journal)
    supplies(t)
    return t

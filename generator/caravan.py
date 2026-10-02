import math

from blocks import AIR, Material, Template, coherent
from camp import CANVAS, GROUND, SLABS, STONES

FIRE_SIZE = 7
FIRE_CENTRE = FIRE_SIZE // 2
HIDE = Material(
    "caravan/hide",
    [("minecraft:brown_wool", 55), ("minecraft:brown_terracotta", 25), ("minecraft:light_gray_wool", 20)],
    ground=4,
)


def trodden(t, low, high, seed):
    for x in range(low[0], high[0] + 1):
        for z in range(low[1], high[1] + 1):
            if coherent(f"caravan/{seed}/trodden", (x, 0, z)) > 0.35:
                t.set((x, 0, z), GROUND)


def fire():
    t = Template()
    c = FIRE_CENTRE
    for x in range(FIRE_SIZE):
        for z in range(FIRE_SIZE):
            if math.hypot(x - c, z - c) + (coherent("caravan/fire/edge", (x, 0, z)) - 0.5) * 2 < 3.4:
                t.set((x, 0, z), GROUND)
    t.set((c, 0, c), "minecraft:coarse_dirt")
    t.set((c, 1, c), "minecraft:campfire", facing="south", lit=True, signal_fire=False, waterlogged=False)
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, 1), (1, -1), (-1, -1)):
        t.set((c + dx, 0, c + dz), STONES)
        if dx and dz:
            t.set((c + dx, 1, c + dz), SLABS[(dx + 2 * dz) % len(SLABS)], type="bottom", waterlogged=False)
    for pos, axis in (
        ((0, 1, 3), "z"),
        ((0, 1, 4), "z"),
        ((2, 1, 0), "x"),
        ((3, 1, 0), "x"),
        ((6, 1, 2), "z"),
        ((6, 1, 3), "z"),
        ((3, 1, 6), "x"),
        ((4, 1, 6), "x"),
    ):
        t.set(pos, "minecraft:stripped_spruce_log", axis=axis)
    t.set((5, 1, 5), "minecraft:barrel", facing="up", open=False)
    t.set((1, 1, 1), "minecraft:spruce_log", axis="x")
    return t


def ridge_tent(t, half, depth, canvas):
    ridge = half + 1
    for z in range(1, depth + 1):
        back = z == depth
        for level in range(half + 1):
            for x in (ridge - half + level, ridge + half - level):
                t.set((x, level + 1, z), canvas)
            for x in range(ridge - half + level + 1, ridge + half - level):
                t.set((x, level + 1, z), canvas if back else AIR)
        for x in range(ridge - half + 1, ridge + half):
            t.set((x, 0, z), "minecraft:spruce_planks")
    for z in (0, depth + 1):
        for y in range(1, half + 3):
            t.set((ridge, y, z), "minecraft:spruce_fence")
    for x in (0, 2 * ridge):
        for z in (0, depth + 1):
            t.set((x, 1, z), "minecraft:spruce_fence")
    return ridge


def tent(contents):
    t = Template()
    ridge = ridge_tent(t, 2, 5, CANVAS)
    t.set((ridge - 1, 1, 3), "minecraft:brown_carpet")
    t.set((ridge - 1, 1, 4), "minecraft:brown_carpet")
    if contents == "books":
        t.set((ridge, 1, 4), "minecraft:bookshelf")
        t.set((ridge + 1, 1, 4), "minecraft:chiseled_bookshelf", facing="west", **shelf(0b101011))
        t.set((ridge + 1, 1, 2), "minecraft:lectern", facing="west", has_book=False, powered=False)
    elif contents == "healer":
        t.set((ridge + 1, 1, 4), "minecraft:brewing_stand", has_bottle_0=True, has_bottle_1=False, has_bottle_2=True)
        t.set((ridge + 1, 1, 3), "minecraft:barrel", facing="up", open=False)
        t.set((0, 1, 2), "minecraft:decorated_pot", cracked=False, facing="south", waterlogged=False)
    else:
        t.set((ridge + 1, 1, 4), "minecraft:barrel", facing="up", open=False)
    t.set((0, 2, 0), "minecraft:lantern", hanging=False, waterlogged=False)
    trodden(t, (1, 0), (5, 0), contents)
    return t


def shelf(books):
    return {f"slot_{slot}_occupied": bool(books >> slot & 1) for slot in range(6)}


def large_tent():
    t = Template()
    ridge = ridge_tent(t, 3, 7, CANVAS)
    for x, colour in ((ridge - 2, "brown"), (ridge + 2, "light_gray")):
        for z in (3, 4):
            t.set((x, 1, z), f"minecraft:{colour}_carpet")
    t.set((ridge - 1, 1, 6), "minecraft:barrel", facing="up", open=False)
    t.set((ridge + 1, 1, 6), "minecraft:barrel", facing="up", open=False)
    t.set((ridge, 1, 5), "minecraft:white_carpet")
    t.set((0, 2, 0), "minecraft:lantern", hanging=False, waterlogged=False)
    trodden(t, (1, 0), (7, 0), "large_tent")
    return t


def cart(t, x0, length, cover):
    for z in range(1, length + 1):
        for x in range(x0 + 1, x0 + 4):
            t.set((x, 1, z), "minecraft:spruce_slab", type="top", waterlogged=False)
    for z in (2, length - 1):
        t.set(
            (x0, 1, z),
            "minecraft:spruce_trapdoor",
            facing="west",
            half="bottom",
            open=True,
            powered=False,
            waterlogged=False,
        )
        t.set(
            (x0 + 4, 1, z),
            "minecraft:spruce_trapdoor",
            facing="east",
            half="bottom",
            open=True,
            powered=False,
            waterlogged=False,
        )
    t.set((x0 + 2, 1, 0), "minecraft:spruce_fence")
    if cover:
        for z in range(2, length):
            t.set((x0 + 1, 2, z), CANVAS)
            t.set((x0 + 3, 2, z), CANVAS)
            for x in range(x0 + 1, x0 + 4):
                t.set((x, 3, z), CANVAS)


def forge_wagon():
    t = Template()
    cart(t, 2, 5, cover=True)
    t.set((4, 2, 4), "minecraft:barrel", facing="up", open=False)
    for x in (0, 1):
        for z in range(1, 6):
            t.set(
                (x, 0, z), "minecraft:gravel" if coherent("caravan/forge", (x, 0, z)) > 0.5 else "minecraft:coarse_dirt"
            )
    t.set((0, 1, 2), "minecraft:smithing_table")
    t.set((0, 1, 4), "minecraft:water_cauldron", level=2)
    t.set((1, 1, 5), "minecraft:blast_furnace", facing="west", lit=False)
    return t


def bell_cart():
    t = Template()
    cart(t, 0, 4, cover=False)
    t.set((2, 2, 2), "minecraft:bell", attachment="floor", facing="east", powered=False)
    t.set((1, 2, 3), "minecraft:white_carpet")
    t.set((3, 2, 3), "minecraft:white_carpet")
    return t


def lean_to(roof, wood):
    t = Template()
    for x in (0, 4):
        for y in (1, 2):
            t.set((x, y, 1), f"minecraft:{wood}_fence")
    for x in range(5):
        if roof is None:
            t.set((x, 3, 1), f"minecraft:{wood}_slab", type="bottom", waterlogged=False)
            t.set(
                (x, 2, 2),
                f"minecraft:{wood}_stairs",
                facing="north",
                half="bottom",
                shape="straight",
                waterlogged=False,
            )
            t.set(
                (x, 1, 3),
                f"minecraft:{wood}_stairs",
                facing="north",
                half="bottom",
                shape="straight",
                waterlogged=False,
            )
        else:
            t.set((x, 3, 1), roof)
            t.set((x, 2, 2), roof)
            t.set((x, 1, 3), roof)
    trodden(t, (0, 0), (4, 2), f"lean_to/{wood}")
    if roof is None:
        t.set((1, 1, 2), "minecraft:hay_block", axis="x")
        t.set((3, 1, 2), "minecraft:hay_block", axis="y")
        t.set((2, 1, 2), "minecraft:barrel", facing="north", open=False)
    else:
        t.set((1, 1, 2), "minecraft:white_carpet")
        t.set((2, 1, 2), "minecraft:white_carpet")
        t.set((3, 1, 2), "minecraft:spruce_log", axis="x")
    for x in range(5):
        for z in (1, 2):
            if (x, 1, z) not in t.blocks and (x, 0, z) not in t.blocks:
                t.set((x, 0, z), "minecraft:coarse_dirt")
    return t


PIECES = {
    "bell_cart": bell_cart,
    "book_tent": lambda: tent("books"),
    "fire": fire,
    "forge_wagon": forge_wagon,
    "healer_tent": lambda: tent("healer"),
    "hide_shelter": lambda: lean_to(HIDE, "dark_oak"),
    "large_tent": large_tent,
    "lean_to": lambda: lean_to(None, "spruce"),
    "tent": lambda: tent("plain"),
}


def caravan(root):
    for name, build in PIECES.items():
        build().save(root / f"data/wayfarer/structure/caravan/{name}.nbt")

import math

import architecture as arch
import detail
from blocks import (
    AIR,
    LAMP,
    Material,
    Room,
    candle,
    coherent,
    distance,
    facing_towards,
    hanging,
    lantern,
    lectern,
    noise,
    ring_points,
)

FLOOR = 2
HIDDEN_ROW = 6
FALSE_WALL = "wayfarer:false_wall"
DOOR_HEIGHT = 3
DOOR_HALF = 1
CORALS = ["minecraft:dead_brain_coral_block", "minecraft:dead_fire_coral_block", "minecraft:dead_tube_coral_block"]
ORES = ["minecraft:coal_ore", "minecraft:copper_ore", "minecraft:deepslate_coal_ore", "minecraft:iron_ore"]
SHAFTS = {
    "black": (
        "ladder",
        "dark_prismarine_stairs",
        "minecraft:sea_lantern",
        [("polished_deepslate", 60), ("deepslate_bricks", 40)],
    ),
    "cold": ("switchback", "polished_diorite_stairs", LAMP, [("calcite", 60), ("packed_ice", 40)]),
    "corrupted": ("switchback", "acacia_stairs", LAMP, [("stripped_acacia_log", 70), ("acacia_log", 30)]),
    "cryptic": ("spiral", "stone_stairs", LAMP, [("bookshelf", 70), ("chiseled_bookshelf", 30)]),
    "evil": ("ladder", "stone_brick_stairs", LAMP, [("chiseled_stone_bricks", 50), ("stone_bricks", 50)]),
    "exotic": (
        "switchback",
        "red_sandstone_stairs",
        "minecraft:sea_lantern",
        [("chiseled_red_sandstone", 50), ("cut_red_sandstone", 50)],
    ),
    "lost": (
        "switchback",
        "cobbled_deepslate_stairs",
        LAMP,
        [("stripped_spruce_log", 70), ("spruce_log", 30)],
    ),
    "magical": (
        "ladder",
        "dark_oak_stairs",
        LAMP,
        [("dark_oak_log", 70), ("stripped_dark_oak_log", 30)],
    ),
    "old": ("switchback", "sandstone_stairs", LAMP, [("chiseled_sandstone", 40), ("cut_sandstone", 60)]),
    "rogue": (
        "spiral",
        "mossy_stone_brick_stairs",
        LAMP,
        [("chiseled_stone_bricks", 50), ("mossy_stone_bricks", 50)],
    ),
    "undead": ("ladder", "tuff_brick_stairs", LAMP, [("chiseled_tuff", 50), ("polished_tuff", 50)]),
    "witch": ("ladder", "mud_brick_stairs", LAMP, [("mangrove_log", 70), ("stripped_mangrove_log", 30)]),
}


def m(seed, entries, stairs=None, slab=None, fade=6):
    return Material(
        seed,
        [(f"minecraft:{block}", weight) for block, weight in entries],
        f"minecraft:{stairs}" if stairs else None,
        f"minecraft:{slab}" if slab else None,
        fade=fade,
    )


def shaft(eye, room):
    style, stairs, light, pillar = SHAFTS[eye]
    return {
        "landing": room.floor.expanded(),
        "light": light,
        "pillar": m(f"{eye}/pillar", pillar).expanded(),
        "stairs": f"minecraft:{stairs}",
        "style": style,
        "wall": room.shell.expanded(),
    }


def done(eye, room, door, **extra):
    return room, {"door": door, "shaft": shaft(eye, room)} | extra


def shrine(room, eye, spec, pedestal, reading, facing):
    room.set(
        pedestal,
        "wayfarer:shrine_pedestal",
        entity={"id": "wayfarer:shrine_pedestal"} | ({} if spec.get("empty") else {"shrine": f"wayfarer:shrine/{eye}"}),
    )
    for y in range(pedestal[1] + 1, pedestal[1] + 4):
        room.air((pedestal[0], y, pedestal[2]))
    lectern(room, reading, facing, f"people/{eye}", spec["people"], spec["people"], spec["lore"])
    for x, _, z in (pedestal, reading):
        room.reserve((x - 1, z - 1), (x + 1, z + 1))


def doorway(room, x, length):
    last = room.size[2] - 1
    z = last
    while z > last - length or not opened(room, x, z):
        for dx in range(-DOOR_HALF, DOOR_HALF + 1):
            room.set((x + dx, FLOOR - 1, z), room.floor)
        room.carve((x - DOOR_HALF, FLOOR, z), (x + DOOR_HALF, FLOOR + DOOR_HEIGHT - 1, z))
        z -= 1
    room.reserve((x - DOOR_HALF - 1, z - 2), (x + DOOR_HALF + 1, last))
    return (x, FLOOR, last)


def opened(room, x, z):
    return all(
        (x + dx, y, z) in room.open
        for dx in range(-DOOR_HALF, DOOR_HALF + 1)
        for y in range(FLOOR, FLOOR + DOOR_HEIGHT)
    )


def aquarium(room, x, z, facing, low):
    dx, dz = {"east": (1, 0), "west": (-1, 0), "south": (0, 1), "north": (0, -1)}[facing]
    for y in (low, low + 1):
        room.set((x + dx, y, z + dz), "minecraft:glass")
        room.water((x + 2 * dx, y, z + 2 * dz))
    room.set((x + 2 * dx, low, z + 2 * dz), "minecraft:sea_pickle", pickles=4, waterlogged=True)
    room.open.add((x + 2 * dx, low, z + 2 * dz))
    room.set((x + 2 * dx, low - 1, z + 2 * dz), "minecraft:sand")


def wall_cell(room, x, z, dx, dz, y):
    while (x + dx, y, z + dz) in room.open:
        x, z = x + dx, z + dz
    return x, z


def black(eye, spec):
    wall = m(
        "black/wall",
        [
            ("deepslate_tiles", 35),
            ("deepslate_bricks", 22),
            ("polished_deepslate", 10),
            ("cracked_deepslate_tiles", 13),
            ("cracked_deepslate_bricks", 10),
            ("cobbled_deepslate", 10),
        ],
        "deepslate_tile_stairs",
        "deepslate_tile_slab",
    )
    floor = m(
        "black/floor",
        [("dark_prismarine", 40), ("prismarine_bricks", 28), ("prismarine", 20), ("cobbled_deepslate", 12)],
        "dark_prismarine_stairs",
        "dark_prismarine_slab",
    )
    trim = m(
        "black/trim",
        [("prismarine_bricks", 55), ("prismarine", 35), ("dark_prismarine", 10)],
        "prismarine_brick_stairs",
    )
    room = Room([23, 14, 25], wall, floor, lambda pos: trim if pos[1] in (5, 6) else wall, decay=0.15)
    cx, cz = 11, 11
    arch.dome(room, cx, cz, 10, FLOOR, 5, 6)
    for x in range(23):
        for z in range(23):
            d = distance(x, z, cx, cz)
            if 3.5 <= d < 5.5 and not (x == cx and z > cz):
                room.set((x, 0, z), "minecraft:sea_lantern" if noise("glow", x, z) < 0.15 else floor)
                roll = noise("moat", x, z)
                if roll < 0.1:
                    room.set((x, 1, z), "minecraft:sea_pickle", pickles=3, waterlogged=True)
                    room.open.add((x, 1, z))
                elif roll < 0.4:
                    room.set((x, 1, z), "minecraft:seagrass")
                    room.open.add((x, 1, z))
                else:
                    room.water((x, 1, z))
            elif 2.5 <= d < 3.5:
                arch.stair(room, (x, 2, z), "minecraft:prismarine_brick_stairs", facing_towards(x, z, cx, cz))
            elif d < 2.5:
                room.set((x, 2, z), floor)
            elif 5.5 <= d < 6.5:
                room.set((x, 1, z), trim)
    for z in (15, 16):
        room.set((cx, 1, z), trim)
    for x, z in ring_points(cx, cz, 8.2, 8, math.pi / 8):
        arch.column(
            room,
            x,
            z,
            2,
            room.top(x, z, 2) - 1,
            trim,
            "minecraft:dark_prismarine",
            "minecraft:sea_lantern",
            "minecraft:prismarine_brick_stairs",
        )
    for index, (x, z) in enumerate(ring_points(cx, cz, 9.0, 8)):
        facing = {"east": "west", "west": "east", "north": "south", "south": "north"}[facing_towards(x, z, cx, cz)]
        if index != 2:
            aquarium(
                room,
                *wall_cell(
                    room, x, z, *{"east": (1, 0), "west": (-1, 0), "south": (0, 1), "north": (0, -1)}[facing], 3
                ),
                facing,
                3,
            )
    room.set((cx, room.top(cx, cz, 3), cz), "minecraft:sea_lantern")
    room.set((cx + 2, 3, cz + 1), "minecraft:campfire", facing="north", lit=False, signal_fire=False, waterlogged=False)
    shrine(room, eye, spec, (cx, 3, cz - 1), (cx - 2, 3, cz + 1), "south")
    door = doorway(room, cx, 4)
    detail.skirting(room, "minecraft:deepslate_tile_stairs")
    detail.rubble(room, wall, 0.3)
    detail.webs(room, 0.35)
    return done(eye, room, door)


def cold(eye, spec):
    wall = m(
        "cold/wall",
        [("packed_ice", 45), ("blue_ice", 15), ("snow_block", 15), ("calcite", 10), ("diorite", 10), ("andesite", 5)],
        fade=5,
    )
    floor = m(
        "cold/floor",
        [("polished_diorite", 30), ("calcite", 25), ("snow_block", 20), ("packed_ice", 15), ("diorite", 10)],
        "polished_diorite_stairs",
        "polished_diorite_slab",
    )
    ribs = arch.ribs(5, "minecraft:blue_ice", wall, 12, LAMP, (8, 0))
    room = Room([27, 22, 35], wall, floor, ribs, decay=-0.2)
    arch.vault(room, 8, 18, 1, 33, FLOOR, 8, 11, pointed=True)
    arch.vault(room, 2, 7, 1, 33, FLOOR, 5, 3)
    arch.vault(room, 19, 24, 1, 33, FLOOR, 5, 3)
    for z in range(5, 32, 5):
        for x in (7, 19):
            arch.column(
                room,
                x,
                z,
                2,
                9,
                "minecraft:packed_ice",
                "minecraft:calcite",
                LAMP,
                "minecraft:polished_diorite_stairs",
                wide=True,
            )
        for x in (4, 22):
            room.set((x, room.top(x, z, 3), z), LAMP)
    for z in range(8, 34):
        for x in range(11, 16):
            room.set((x, 1, z), "minecraft:blue_ice" if (x + z) % 5 == 0 else "minecraft:calcite")
        if z % 2 == 0:
            room.set((12 if z % 4 == 0 else 14, 1, z), "minecraft:polished_diorite")
    top = arch.dais(room, 9, 17, 1, 8, FLOOR, 3, "minecraft:polished_diorite_stairs", "minecraft:calcite")
    for index in range(40):
        x = 8 + int(noise("icicle", index, 0) * 11)
        z = 1 + int(noise("icicle", index, 1) * 32)
        ceiling = room.top(x, z, 3)
        if ceiling < 12 or 12 <= x <= 14:
            continue
        length = 1 + int(noise("icicle", index, 2) * 4)
        room.fill((x, ceiling - length, z), (x, ceiling - 1, z), "minecraft:blue_ice")
    for x in (11, 15):
        room.set((x, top - 1, 1), "minecraft:moss_block")
        room.set((x, top, 1), "minecraft:lily_of_the_valley")
    for x in (12, 14):
        candle(room, (x, top, 1), "minecraft:light_blue_candle", 3)
    room.reserve((11, 8), (15, 34))
    shrine(room, eye, spec, (13, top, 3), (11, top, 3), "south")
    door = doorway(room, 13, 1)
    detail.moss(room, 0.5, "drift", "minecraft:snow")
    detail.rubble(room, floor, 0.12)
    return done(eye, room, door)


def corrupted(eye, spec):
    wall = m(
        "corrupted/wall",
        [
            ("terracotta", 30),
            ("orange_terracotta", 18),
            ("red_terracotta", 14),
            ("brown_terracotta", 14),
            ("packed_mud", 12),
            ("coarse_dirt", 12),
        ],
        "mud_brick_stairs",
        "mud_brick_slab",
    )
    floor = m(
        "corrupted/floor",
        [("acacia_planks", 38), ("red_terracotta", 20), ("terracotta", 15), ("coarse_dirt", 15), ("rooted_dirt", 12)],
        "acacia_stairs",
        "acacia_slab",
    )
    ribs = arch.ribs(6, "minecraft:stripped_acacia_wood", wall, 13, LAMP, (6, 1))
    room = Room([23, 18, 37], wall, floor, ribs, decay=0.25)
    arch.vault(room, 6, 16, 1, 35, FLOOR, 8, 7)
    arch.vault(room, 1, 5, 1, 35, FLOOR, 6, 1)
    arch.vault(room, 17, 21, 1, 35, FLOOR, 6, 1)
    for x in range(10, 13):
        for z in range(10, 36):
            if coherent("carpet", (x, 2, z)) > 0.3:
                room.set((x, 2, z), "minecraft:red_carpet")
    for z in range(7, 34, 6):
        for x in (5, 17):
            if (x, z) == (17, 19):
                arch.stump(room, x, z, 2, 3, "minecraft:stripped_acacia_log", wall, wide=True)
                continue
            arch.column(
                room,
                x,
                z,
                2,
                8,
                "minecraft:stripped_acacia_log",
                "minecraft:red_terracotta",
                "minecraft:stripped_acacia_wood",
                "minecraft:acacia_stairs",
                wide=True,
            )
            inward = x + (2 if x == 5 else -2)
            room.set((inward, 6, z), "minecraft:red_wall_banner", facing="east" if x == 5 else "west")
        for x, facing in ((6, "west"), (16, "east")):
            if z + 3 < 35:
                arch.window(room, x, z + 3, facing, 11, 13, "minecraft:orange_stained_glass_pane", LAMP)
    for z in (10, 16, 22, 28):
        arch.chandelier(room, 11, z, room.top(11, z, 3), 4)
    top = arch.dais(room, 6, 16, 1, 8, FLOOR, 3, "minecraft:acacia_stairs", "minecraft:red_terracotta")
    arch.stair(room, (11, top, 2), "minecraft:acacia_stairs", "north")
    room.fill((11, top, 1), (11, top + 3, 1), "minecraft:stripped_acacia_log", axis="y")
    room.set((10, top, 2), "minecraft:acacia_fence")
    for x, z in ((12, 2), (12, 1), (9, 3), (13, 3)):
        room.set((x, top - 1, z), "minecraft:coarse_dirt")
        if noise("dust", x, z) < 0.5:
            room.set((x, top, z), "minecraft:dead_bush")
    for x in (9, 13):
        room.set((x, top + 1, 1), "minecraft:red_wall_banner", facing="south")
    arch.collapse(room, 3, 28, 2, FLOOR, wall)
    room.reserve((9, 9), (13, 36))
    shrine(room, eye, spec, (11, top, 4), (9, top, 4), "south")
    door = doorway(room, 11, 1)
    detail.cornice(room, "minecraft:acacia_stairs")
    detail.skirting(room, "minecraft:acacia_stairs")
    detail.rubble(room, wall, 0.3)
    detail.roots(room, 0.08)
    detail.webs(room, 0.3)
    return done(eye, room, door)


def cryptic(eye, spec):
    wall = m(
        "cryptic/wall",
        [("calcite", 40), ("diorite", 20), ("polished_diorite", 10), ("stone", 10), ("andesite", 10), ("tuff", 10)],
        "polished_diorite_stairs",
        "polished_diorite_slab",
    )
    floor = m(
        "cryptic/floor",
        [
            ("smooth_stone", 30),
            ("stone", 20),
            ("polished_andesite", 20),
            ("andesite", 15),
            ("cobblestone", 10),
            ("gravel", 5),
        ],
        "stone_stairs",
        "stone_slab",
    )
    ribs = arch.ribs(5, "minecraft:polished_diorite", wall, 20, LAMP, (1, 2))
    room = Room([23, 24, 29], wall, floor, ribs, decay=-0.1)
    arch.vault(room, 1, 21, 1, 27, FLOOR, 18, 3)
    galleries = (8, 15)
    for x in range(1, 22):
        for z in range(1, 28):
            edge = min(x - 1, 21 - x, z - 1, 27 - z)
            if edge == 0:
                inward = "east" if x == 1 else "west" if x == 21 else "south" if z == 1 else "north"
                for y in list(range(2, 8)) + list(range(9, 15)) + list(range(16, 20)):
                    if z == 27 and abs(x - 11) <= 1 and y < 5:
                        continue
                    burned = x >= 16 and z <= 7 and coherent("burn", (x, y, z)) > 0.45
                    if burned:
                        room.set(
                            (x, y, z),
                            "minecraft:coal_block" if noise("char", x, y, z) < 0.5 else "minecraft:black_terracotta",
                        )
                    elif (x + z) % 6 == 0:
                        room.set((x, y, z), wall)
                    elif noise("shelf", x, y, z) < 0.3:
                        room.set((x, y, z), "minecraft:chiseled_bookshelf", facing=inward)
                    else:
                        room.set((x, y, z), "minecraft:bookshelf")
            for gallery in galleries:
                if 1 <= edge <= 2:
                    room.set((x, gallery, z), "minecraft:polished_diorite_slab", type="double", waterlogged=False)
                elif edge == 3:
                    room.set((x, gallery, z), "minecraft:polished_diorite_slab", type="top", waterlogged=False)
                    room.set((x, gallery + 1, z), "minecraft:spruce_fence")
                elif edge == 0:
                    room.set((x, gallery, z), "minecraft:polished_diorite")
    for gallery, (x, z) in ((8, (2, 14)), (15, (20, 14))):
        facing = "east" if x == 2 else "west"
        for y in range(gallery - 6, gallery + 1):
            room.set((x, y, z), "minecraft:ladder", facing=facing, waterlogged=False)
            room.set((x - 1 if x == 2 else x + 1, y, z), wall)
        room.set((x + (1 if x == 2 else -1), gallery + 1, z), AIR)
    for z in range(4, 26, 5):
        for x in (1, 21):
            room.set((x, 7, z), LAMP)
            room.set((x, 14, z), LAMP)
    for z in (18, 22):
        for x in (6, 16):
            room.fill((x - 1, 2, z), (x + 1, 2, z), "minecraft:spruce_slab", type="top", waterlogged=False)
            room.set((x, 3, z), "minecraft:potted_cornflower")
            candle(room, (x + 1, 3, z), "minecraft:white_candle", 2)
    for x in range(10, 13):
        for z in range(14, 27):
            room.set((x, 2, z), "minecraft:blue_carpet")
    for z in (8, 20):
        arch.chandelier(room, 11, z, room.top(11, z, 3), 5, "minecraft:lantern")
    top = arch.dais(room, 8, 14, 9, 14, FLOOR, 2, "minecraft:stone_brick_stairs", "minecraft:polished_andesite")
    room.air((11, room.top(11, 11, top + 1), 11))
    room.reserve((10, 9), (12, 28))
    shrine(room, eye, spec, (11, top, 11), (9, top, 12), "south")
    door = doorway(room, 11, 1)
    detail.cornice(room, "minecraft:polished_diorite_stairs")
    detail.rubble(room, floor, 0.12)
    detail.webs(room, 0.5)
    cap = ["minecraft:cobblestone_wall", "minecraft:mossy_cobblestone_wall"]
    return done(eye, room, door, oculus={"at": (11, room.size[1], 11), "cap": cap})


def evil(eye, spec):
    wall = m(
        "evil/wall",
        [
            ("stone_bricks", 35),
            ("andesite", 10),
            ("cracked_stone_bricks", 18),
            ("mossy_stone_bricks", 17),
            ("cobblestone", 10),
            ("mossy_cobblestone", 10),
        ],
        "stone_brick_stairs",
        "stone_brick_slab",
    )
    floor = m(
        "evil/floor",
        [
            ("polished_andesite", 25),
            ("stone_bricks", 25),
            ("andesite", 15),
            ("cracked_stone_bricks", 12),
            ("cobblestone", 10),
            ("mossy_cobblestone", 8),
            ("gravel", 5),
        ],
        "stone_brick_stairs",
        "stone_brick_slab",
    )
    room = Room([13, 11, 21], wall, floor, decay=0.4)
    arch.vault(room, 1, 11, 1, 19, FLOOR, 4, 5, pointed=True)
    for z in range(9, 17, 2):
        for x in list(range(2, 5)) + list(range(8, 11)):
            if coherent("pew", (x, 2, z)) > 0.7:
                continue
            if coherent("pew", (x, 2, z)) > 0.55:
                room.set((x, 2, z), "minecraft:oak_slab", type="bottom", waterlogged=False)
            else:
                arch.stair(room, (x, 2, z), "minecraft:oak_stairs", "south")
    for z in (6, 10, 14):
        for x, facing in ((2, "west"), (10, "east")):
            arch.window(room, x, z, facing, 4, 6, "minecraft:yellow_stained_glass_pane", LAMP)
    top = arch.dais(room, 2, 10, 1, 4, FLOOR, 1, "minecraft:stone_brick_stairs", floor)
    room.fill((5, top, 2), (7, top, 2), "minecraft:chiseled_stone_bricks")
    for x in (5, 7):
        candle(room, (x, top + 1, 2), "minecraft:candle", 3)
    room.set((9, top, 2), "minecraft:bell", attachment="floor", facing="south", powered=False)
    arch.collapse(room, 9, 13, 2, FLOOR, wall)
    room.reserve((5, 5), (7, 20))
    shrine(room, eye, spec, (6, top + 1, 2), (3, top, 3), "south")
    door = doorway(room, 6, 2)
    detail.cornice(room, "minecraft:stone_brick_stairs")
    detail.roots(room, 0.2)
    detail.moss(room, 0.5)
    detail.vines(room, 0.2)
    detail.rubble(room, wall, 0.35)
    detail.webs(room, 0.5)
    return done(eye, room, door)


def exotic(eye, spec):
    wall = m(
        "exotic/wall",
        [
            ("cut_red_sandstone", 35),
            ("smooth_red_sandstone", 25),
            ("red_sandstone", 20),
            ("orange_terracotta", 12),
            ("red_terracotta", 8),
        ],
        "red_sandstone_stairs",
        "red_sandstone_slab",
        fade=4,
    )
    floor = m(
        "exotic/floor",
        [("smooth_red_sandstone", 40), ("cut_red_sandstone", 30), ("red_sandstone", 20), ("orange_terracotta", 10)],
        "smooth_red_sandstone_stairs",
        "red_sandstone_slab",
    )
    coffers = arch.ribs(
        4,
        "minecraft:cut_red_sandstone",
        "minecraft:smooth_red_sandstone",
        11,
        "minecraft:sea_lantern",
        wall=wall,
    )

    def shell(pos):
        return "minecraft:prismarine_bricks" if pos[1] == 9 else coffers(pos)

    room = Room([29, 20, 29], wall, floor, shell, decay=-0.35)
    cx, cz = 14, 14
    room.fill((1, 1, 1), (27, 1, 27), floor)
    room.carve((1, 2, 1), (27, 10, 27))
    arch.dome(room, cx, cz, 12, FLOOR, 9, 10)
    for x in range(29):
        for z in range(29):
            d = distance(x, z, cx, cz)
            if d > 12.5:
                continue
            if 4 <= d < 7:
                room.set(
                    (x, 0, z), "minecraft:sea_lantern" if noise("pool", x, z) < 0.3 else "minecraft:prismarine_bricks"
                )
                room.water((x, 1, z))
            elif d < 4:
                room.set((x, 2, z), "minecraft:chiseled_red_sandstone" if d < 1.5 else "minecraft:cut_red_sandstone")
            elif 7 <= d < 8:
                room.set((x, 1, z), "minecraft:prismarine_bricks")
            elif 8 <= d < 9:
                room.set((x, 1, z), "minecraft:cut_red_sandstone")
            elif (x + z) % 2 == 0:
                room.set((x, 1, z), "minecraft:orange_terracotta" if d < 11 else floor)
    for z in range(cz + 3, cz + 8):
        room.set((cx, 1, z), "minecraft:cut_red_sandstone")
        room.set((cx - 1, 1, z), "minecraft:red_sandstone_slab", type="double", waterlogged=False)
        room.set((cx + 1, 1, z), "minecraft:red_sandstone_slab", type="double", waterlogged=False)
    arch.stair(room, (cx, 2, cz + 4), "minecraft:smooth_red_sandstone_stairs", "north")
    for x, z in ring_points(cx, cz, 5.5, 4, math.pi / 4):
        room.set((x, 1, z), "minecraft:prismarine_bricks")
        room.set((x, 2, z), "minecraft:prismarine_wall")
        room.water((x, 3, z))
    for x, z in ring_points(cx, cz, 10.3, 16):
        arch.column(
            room,
            x,
            z,
            2,
            9,
            "minecraft:cut_red_sandstone",
            "minecraft:chiseled_red_sandstone",
            "minecraft:chiseled_red_sandstone",
            "minecraft:red_sandstone_stairs",
        )
    for z in range(4, 26, 5):
        for x, facing in ((2, "west"), (26, "east")):
            arch.window(room, x, z, facing, 3, 7, "minecraft:cyan_stained_glass_pane", "minecraft:sea_lantern")
    for x in range(4, 26, 5):
        arch.window(room, x, 2, "north", 3, 7, "minecraft:cyan_stained_glass_pane", "minecraft:sea_lantern")
    for x, z in ((3, 3), (25, 3), (3, 25), (25, 25)):
        room.set((x, 2, z), "minecraft:potted_torchflower")
        candle(room, (x + (1 if x < cx else -1), 2, z), "minecraft:orange_candle", 3)
    room.set((cx, room.top(cx, cz, 3), cz), "minecraft:sea_lantern")
    for x, z in ring_points(cx, cz, 2.8, 4, math.pi / 4):
        room.set((x, 2, z), "minecraft:sea_lantern")
    room.reserve((cx - 1, cz + 2), (cx + 1, 28))
    shrine(room, eye, spec, (cx, 3, cz), (cx - 2, 3, cz + 1), "south")
    door = doorway(room, cx, 1)
    detail.cornice(room, "minecraft:red_sandstone_stairs")
    detail.skirting(room, "minecraft:smooth_red_sandstone_stairs")
    return done(eye, room, door)


def lost(eye, spec):
    rock = m(
        "lost/wall",
        [
            ("stone", 30),
            ("andesite", 15),
            ("cobblestone", 15),
            ("tuff", 15),
            ("cobbled_deepslate", 10),
            ("deepslate", 10),
            ("mossy_cobblestone", 5),
        ],
        "cobblestone_stairs",
        "cobblestone_slab",
        fade=5,
    )

    def vein(pos):
        return ORES[int(noise("which", *pos) * len(ORES))] if noise("ore", *pos) < 0.05 else rock

    floor = m(
        "lost/floor",
        [
            ("cobbled_deepslate", 35),
            ("gravel", 15),
            ("tuff", 15),
            ("cobblestone", 15),
            ("andesite", 10),
            ("coarse_dirt", 10),
        ],
        "cobbled_deepslate_stairs",
        "cobbled_deepslate_slab",
    )
    room = Room([25, 12, 29], rock, floor, vein, decay=0.15)
    arch.cave(room, 12, 9, 10.5, 8, FLOOR, 3, 6)
    for z in range(15, 29):
        for x in range(10, 15):
            room.set((x, 1, z), floor)
            room.carve((x, 2, z), (x, 6 if 11 <= x <= 13 else 5, z))
    for low, high, x0, x1 in ((20, 22, 2, 9), (15, 17, 15, 22)):
        for z in range(low, high + 1):
            for x in range(x0, x1 + 1):
                room.set((x, 1, z), floor)
                room.carve((x, 2, z), (x, 4, z))
    arch.collapse(room, 3, 21, 2, FLOOR, rock, "minecraft:gravel")
    for z in range(16, 28, 3):
        for x in (10, 14):
            room.fill((x, 2, z), (x, 5, z), "minecraft:stripped_spruce_log", axis="y")
        room.fill((10, 6, z), (14, 6, z), "minecraft:spruce_planks")
        lantern(room, (12, 5, z))
    for z in range(7, 28):
        room.set((12, 2, z), "minecraft:rail", shape="north_south", waterlogged=False)
    room.fill((11, 2, 6), (13, 2, 6), "minecraft:stripped_spruce_log", axis="x")
    for x, z in ((4, 6), (20, 6), (3, 11), (21, 11), (7, 14), (17, 14)):
        room.fill((x, 2, z), (x, 4, z), "minecraft:stripped_spruce_log", axis="y")
        lantern(room, (x, 5, z), hanging=False)
    room.fill((8, 2, 1), (16, 2, 5), "minecraft:polished_deepslate")
    for x in (9, 15):
        room.fill((x, 3, 1), (x, 4, 1), "minecraft:stripped_spruce_log", axis="y")
        lantern(room, (x, 5, 1), hanging=False)
    room.reserve((11, 6), (13, 28))
    shrine(room, eye, spec, (12, 3, 3), (10, 3, 4), "south")
    door = doorway(room, 12, 1)
    detail.rubble(room, rock, 0.35)
    detail.webs(room, 0.5)
    return done(eye, room, door)


def magical(eye, spec):
    wall = m(
        "magical/wall",
        [
            ("deepslate_bricks", 40),
            ("deepslate_tiles", 18),
            ("polished_deepslate", 10),
            ("cracked_deepslate_bricks", 14),
            ("cracked_deepslate_tiles", 8),
            ("tuff", 10),
        ],
        "deepslate_brick_stairs",
        "deepslate_brick_slab",
    )
    panel = m("magical/panel", [("dark_oak_planks", 60), ("stripped_dark_oak_wood", 25), ("dark_oak_wood", 15)])
    floor = m(
        "magical/floor",
        [
            ("dark_oak_planks", 55),
            ("stripped_dark_oak_wood", 20),
            ("deepslate_bricks", 15),
            ("cracked_deepslate_bricks", 10),
        ],
        "dark_oak_stairs",
        "dark_oak_slab",
    )
    room = Room(
        [25, 16, 27],
        wall,
        floor,
        lambda pos: panel if 2 <= pos[1] <= 5 else "minecraft:stripped_dark_oak_log" if pos[1] == 6 else wall,
        decay=0.05,
    )
    cx, cz = 12, 12
    arch.dome(room, cx, cz, 11, FLOOR, 6, 9)
    for x in range(25):
        for z in range(25):
            d = distance(x, z, cx, cz)
            if d > 11:
                continue
            if d < 1.5 or 3.5 <= d < 4.5:
                room.set((x, 1, z), "minecraft:amethyst_block")
            elif d < 3.5:
                room.set((x, 1, z), "minecraft:polished_deepslate")
            elif d < 6:
                room.set((x, 1, z), wall)
            if 3.5 <= d < 4.5 and (x + z) % 2 == 0:
                candle(room, (x, 2, z), "minecraft:purple_candle", 1 + int(noise("candle", x, z) * 4))
            elif d < 1.5:
                room.set((x, 2, z), "minecraft:amethyst_block")
    for index, (x, z) in enumerate(ring_points(cx, cz, 9.0, 12)):
        if z > cz + 5 and abs(x - cx) <= 2:
            continue
        if index == 4:
            arch.stump(room, x, z, 2, 3, "minecraft:dark_oak_log", wall)
            continue
        arch.column(
            room,
            x,
            z,
            2,
            9,
            "minecraft:dark_oak_log",
            "minecraft:polished_deepslate",
            "minecraft:amethyst_block",
            "minecraft:deepslate_brick_stairs",
        )
        room.set((x, 10, z), LAMP)
    for x, z in ring_points(cx, cz, 3.0, 4, math.pi / 4):
        room.set((x, room.top(x, z, 3), z), LAMP)
    room.set((cx, room.top(cx, cz, 4), cz), "minecraft:amethyst_block")
    room.set((cx, room.top(cx, cz, 4) - 1, cz), "minecraft:amethyst_cluster", facing="down", waterlogged=False)
    for x, z in ring_points(cx, cz, 5.4, 8, math.pi / 8):
        room.set((x, 2, z), "minecraft:potted_allium")
    room.reserve((cx - 1, cz + 3), (cx + 1, 26))
    shrine(room, eye, spec, (cx, 3, cz), (cx - 2, 2, cz + 2), "south")
    door = doorway(room, cx, 3)
    detail.cornice(room, "minecraft:dark_oak_stairs")
    detail.rubble(room, wall, 0.25)
    detail.webs(room, 0.45)
    detail.floor_lights(room, LAMP, 4, "minecraft:purple_carpet")
    return done(eye, room, door)


def old(eye, spec):
    wall = m(
        "old/wall",
        [
            ("sandstone", 38),
            ("cut_sandstone", 25),
            ("smooth_sandstone", 22),
            ("chiseled_sandstone", 5),
            ("sandstone", 10),
        ],
        "sandstone_stairs",
        "sandstone_slab",
    )
    floor = m(
        "old/floor",
        [("smooth_sandstone", 35), ("cut_sandstone", 25), ("sandstone", 25), ("sand", 15)],
        "smooth_sandstone_stairs",
        "smooth_sandstone_slab",
    )
    room = Room([21, 16, 41], wall, floor, decay=0.1)
    arch.vault(room, 1, 19, 1, 39, FLOOR, 6, 9)
    for z in range(1, 40):
        for x in (1, 19):
            if coherent("dune", (x, 2, z)) > 0.35:
                room.set((x, 2, z), "minecraft:sand")
                if noise("bush", x, z) < 0.25:
                    room.set((x, 3, z), "minecraft:dead_bush")
        for x in (2, 18):
            if coherent("dune", (x, 2, z)) > 0.55:
                room.set((x, 2, z), "minecraft:sandstone_slab", type="bottom", waterlogged=False)
    for z in range(10, 38, 3):
        broken = z == 25
        for x in range(1, 20):
            ceiling = room.top(x, z, 3)
            if broken and x > 12:
                continue
            room.set((x, ceiling - 1, z), "minecraft:bone_block", axis="y")
            if x in (1, 19) or x in (2, 18):
                for y in range(2, ceiling - 1):
                    if room.get((x, y, z)) == AIR:
                        room.set((x, y, z), "minecraft:bone_block", axis="y")
    for z in range(9, 38):
        room.set((10, room.top(10, z, 3) - 1, z), "minecraft:bone_block", axis="z")
    for x in range(5, 16):
        for z in range(1, 9):
            side = abs(x - 10) == 5
            if side or z == 1:
                room.set((x, 2, z), "minecraft:bone_block", axis="y")
            if side and z >= 3 and z % 2 == 0:
                room.fill((x, 3, z), (x, 4, z), "minecraft:bone_block", axis="y")
            if z == 8 and not side:
                room.set((x, 9, z), "minecraft:bone_block", axis="x")
            if z == 8 and side:
                room.fill((x, 5, z), (x, 9, z), "minecraft:bone_block", axis="y")
    for x in (7, 13):
        room.set((x, 8, 8), LAMP)
        room.set((x, 5, 0), LAMP)
    room.set((10, 8, 0), LAMP)
    for z in (5, 11, 17, 23, 29, 35):
        for x in (0, 20):
            room.set((x, 4, z), LAMP)
    for z in (14, 20, 26, 32):
        hanging(room, 10, z, room.top(10, z, 3) - 1, 3)
    room.air((10, room.top(10, 4, 3), 4))
    room.reserve((9, 9), (11, 40))
    shrine(room, eye, spec, (10, 2, 4), (8, 2, 10), "south")
    door = doorway(room, 10, 1)
    detail.cornice(room, "minecraft:sandstone_stairs")
    detail.rubble(room, wall, 0.3)
    detail.webs(room, 0.3)
    cap = ["minecraft:sandstone_wall", "minecraft:sandstone_wall"]
    return done(eye, room, door, oculus={"at": (10, room.size[1], 4), "cap": cap})


def rogue(eye, spec):
    wall = m(
        "rogue/wall",
        [
            ("stone_bricks", 20),
            ("mossy_stone_bricks", 30),
            ("cracked_stone_bricks", 15),
            ("mossy_cobblestone", 20),
            ("cobblestone", 10),
            ("moss_block", 5),
        ],
        "mossy_stone_brick_stairs",
        "mossy_stone_brick_slab",
    )
    floor = m(
        "rogue/floor",
        [
            ("mossy_stone_bricks", 35),
            ("stone_bricks", 20),
            ("cracked_stone_bricks", 15),
            ("mossy_cobblestone", 15),
            ("moss_block", 10),
            ("cobblestone", 5),
        ],
        "mossy_stone_brick_stairs",
        "mossy_stone_brick_slab",
    )
    room = Room([25, 18, 33], wall, floor, decay=0.3)
    tiers = [(25, 31, 2), (18, 24, 3), (11, 17, 4), (5, 10, 5), (1, 4, 6)]

    def floor_at(z):
        return next(top for low, high, top in tiers if low <= z <= high)

    for low, high, top in tiers:
        for z in range(low, high + 1):
            for x in range(1, 24):
                room.fill(
                    (x, 1, z), (x, top - 1, z), "minecraft:chiseled_stone_bricks" if z == low and x % 4 == 0 else floor
                )
                room.carve((x, top, z), (x, top + 10, z))
    for low, _, top in tiers[:-1]:
        for x in range(9, 16):
            arch.stair(room, (x, top, low), "minecraft:mossy_stone_brick_stairs", "north")
    for low, high, top in tiers:
        for z in range(low, high + 1, 3):
            for wall_x, inner in ((0, 1), (24, 23)):
                room.set((wall_x, top + 1, z), "minecraft:redstone_block")
                room.set((inner, top + 1, z), "minecraft:redstone_block")
                room.set((inner, top + 2, z), "minecraft:redstone_lamp", lit=True)
        for z in range(low + 1, high, 3):
            for wall_x, facing in ((0, "east"), (24, "west")):
                room.set((wall_x, top + 4, z), "minecraft:dispenser", facing=facing, triggered=False)
        for x in (6, 18):
            for z in range(low, high + 1):
                if z != low or top == 6:
                    room.set(
                        (x, top, z),
                        "minecraft:redstone_wire",
                        power=15,
                        east="none",
                        west="none",
                        north="side",
                        south="side",
                    )
    for x, z in ((8, 21), (16, 21), (8, 14), (16, 14), (8, 7), (16, 7)):
        top = floor_at(z)
        arch.column(
            room,
            x,
            z,
            top,
            top + 9,
            "minecraft:mossy_stone_bricks",
            "minecraft:chiseled_stone_bricks",
            "minecraft:chiseled_stone_bricks",
            "minecraft:mossy_stone_brick_stairs",
            wide=True,
        )
    for z in (3, 7, 14, 21, 28):
        room.fill((1, floor_at(z) + 10, z), (23, floor_at(z) + 10, z), "minecraft:jungle_log", axis="x")
    for x in (10, 14):
        hanging(room, x, 3, floor_at(3) + 10, 3)
    for x in (9, 15):
        room.set((x, 9, 0), LAMP)
    for index in range(40):
        x = 1 + int(noise("vine", index, 0) * 23)
        z = 1 + int(noise("vine", index, 1) * 31)
        if abs(x - 12) <= 3:
            continue
        ceiling = room.top(x, z, floor_at(z) + 1)
        length = 1 + int(noise("vine", index, 2) * 4)
        if room.get((x, ceiling - length - 1, z)) != AIR:
            continue
        for y in range(ceiling - length, ceiling):
            room.set((x, y, z), "minecraft:cave_vines_plant", berries=True)
        room.set((x, ceiling - length - 1, z), "minecraft:cave_vines", age=25, berries=True)
    for x in (9, 15):
        room.set((x, 6, 2), "minecraft:moss_block")
        room.set((x, 7, 2), "minecraft:fern")
    arch.collapse(room, 21, 10, 2, 5, wall)
    room.reserve((9, 1), (15, 32))
    shrine(room, eye, spec, (12, 6, 2), (10, 6, 3), "south")
    door = doorway(room, 12, 1)
    detail.cornice(room, "minecraft:mossy_stone_brick_stairs")
    detail.moss(room, 0.5)
    detail.vines(room, 0.35)
    detail.rubble(room, wall, 0.25)
    detail.webs(room, 0.3)
    return done(eye, room, door)


def undead(eye, spec):
    wall = m(
        "undead/wall",
        [
            ("tuff_bricks", 25),
            ("tuff", 30),
            ("polished_tuff", 8),
            ("cobblestone", 12),
            ("andesite", 10),
            ("mossy_cobblestone", 10),
            ("cobbled_deepslate", 5),
        ],
        "tuff_brick_stairs",
        "tuff_brick_slab",
    )
    floor = m(
        "undead/floor",
        [
            ("polished_tuff", 25),
            ("tuff", 25),
            ("tuff_bricks", 15),
            ("cobblestone", 10),
            ("coarse_dirt", 15),
            ("rooted_dirt", 10),
        ],
        "tuff_stairs",
        "tuff_slab",
    )
    room = Room([9, 7, 11], wall, floor, decay=0.5)
    arch.vault(room, 1, 7, 1, 9, FLOOR, 2, 3)
    room.set((4, 1, 2), "minecraft:bone_block", axis="y")
    for z in (4, 7):
        for x, back, rotation in ((1, 0, 12), (7, 8, 4)):
            room.air((back, 3, z))
            room.set((back, 2, z), "minecraft:tuff_bricks")
            if noise("skull", x, z) < 0.5:
                room.set((back, 3, z), "minecraft:skeleton_skull", rotation=rotation)
            else:
                candle(room, (back, 3, z), "minecraft:white_candle", 1)
    arch.collapse(room, 6, 7, 1, FLOOR, wall)
    shrine(room, eye, spec, (4, 2, 2), (2, 2, 3), "south")
    door = doorway(room, 4, 1)
    detail.roots(room, 0.3)
    detail.moss(room, 0.4)
    detail.rubble(room, wall, 0.35)
    detail.webs(room, 0.8)
    return done(eye, room, door)


def witch(eye, spec):
    wall = m(
        "witch/wall",
        [
            ("mud_bricks", 35),
            ("packed_mud", 20),
            ("muddy_mangrove_roots", 15),
            ("mud", 12),
            ("mossy_cobblestone", 10),
            ("dirt", 8),
        ],
        "mud_brick_stairs",
        "mud_brick_slab",
    )
    floor = m(
        "witch/floor",
        [
            ("packed_mud", 30),
            ("mud_bricks", 15),
            ("muddy_mangrove_roots", 15),
            ("mud", 15),
            ("rooted_dirt", 12),
            ("moss_block", 13),
        ],
        "mud_brick_stairs",
        "mud_brick_slab",
    )
    room = Room([13, 8, 13], wall, floor, decay=0.45)
    cx, cz = 6, 5
    arch.cave(room, 6, 6, 5.5, 5.5, FLOOR, 3, 2, 1.0, "witch")
    for x in range(13):
        for z in range(13):
            d = distance(x, z, cx, cz)
            if d < 1.6:
                room.set((x, 2, z), "minecraft:packed_mud")
            elif d < 2.4 and room.get((x, 2, z)) == AIR:
                arch.stair(room, (x, 2, z), "minecraft:mud_brick_stairs", facing_towards(x, z, cx, cz))
    for index in range(16):
        x = 1 + int(noise("hang", index, 0) * 11)
        z = 1 + int(noise("hang", index, 1) * 11)
        if room.get((x, 3, z)) != AIR or distance(x, z, cx, cz) < 2.5:
            continue
        top = room.top(x, z, 3)
        room.fill(
            (x, top - 1 - int(noise("hang", index, 2) * 2), z),
            (x, top - 1, z),
            "minecraft:mangrove_roots",
            waterlogged=False,
        )
    for x, z in ((2, 5), (10, 5)):
        room.set((x, 2, z), "minecraft:cauldron")
    room.set((3, 2, 9), "minecraft:barrel", facing="up", open=False)
    room.set((9, 2, 9), "minecraft:brewing_stand", has_bottle_0=False, has_bottle_1=False, has_bottle_2=False)
    candle(room, (3, 3, 9), "minecraft:green_candle", 2)
    shrine(room, eye, spec, (cx, 3, cz), (cx - 2, 2, cz + 3), "south")
    door = doorway(room, 6, 2)
    hidden = stairway(room, HIDDEN_ROW)
    detail.moss(room, 0.45)
    detail.roots(room, 0.15, soil="minecraft:muddy_mangrove_roots")
    detail.rubble(room, wall, 0.35)
    detail.webs(room, 0.4)
    detail.floor_lights(room, LAMP, 4)
    return done(eye, room, door, hidden=hidden)


def stairway(room, z):
    x = 0
    while (x, FLOOR, z) not in room.open or (x, FLOOR + 1, z) not in room.open:
        room.set((x, FLOOR - 1, z), room.floor)
        room.carve((x, FLOOR, z), (x, FLOOR + 1, z))
        x += 1
    room.reserve((0, z - 1), (x, z + 1))
    for y in (FLOOR, FLOOR + 1):
        room.set((x - 1, y, z), FALSE_WALL)
    return [x - 1, FLOOR, z]


HALLS = {
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

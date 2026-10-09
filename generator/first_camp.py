import math

from blocks import Material, Template, coherent

SIZE = 17
HEARTH = (8, 0, 10)
PINES = {"west": (4, 6), "middle": (8, 4), "east": (12, 6)}
NOTCHES = (2, 4, 6)
STAKE = (9, 1, 15)
POND = [(x, z) for x in range(10, 14) for z in range(14, 17) if (x, z) != (10, 14)]
SACK = (9, 1, 6)
FALLEN = [(x, 1, 13) for x in range(2, 8)]
WORN = Material(
    "first_camp/worn",
    [("minecraft:coarse_dirt", 45), ("minecraft:dirt_path", 30), ("minecraft:rooted_dirt", 25)],
    ground=0,
)
OVERGROWN = Material(
    "first_camp/overgrown",
    [
        ("minecraft:moss_block", 30),
        ("minecraft:coarse_dirt", 30),
        ("minecraft:podzol", 25),
        ("minecraft:rooted_dirt", 15),
    ],
    ground=0,
)
RING_THEN = Material("first_camp/ring_then", [("minecraft:cobblestone", 60), ("minecraft:andesite", 40)])
RING_NOW = Material(
    "first_camp/ring_now",
    [("minecraft:mossy_cobblestone", 70), ("minecraft:cobblestone", 15), ("minecraft:andesite", 15)],
)
GROWTH_NOW = [
    "minecraft:fern",
    "minecraft:short_grass",
    "minecraft:fern",
    "minecraft:large_fern",
    "minecraft:short_grass",
]
HEIGHT_NOW = {"west": 12, "middle": 15, "east": 13}
HEIGHT_THEN = {"west": 6, "middle": 7, "east": 6}
CONE = [1, 1, 2, 1, 2, 3, 2, 3, 3, 2, 3, 3]
OVERGROWTH = 0.62


def first_camp(now):
    t = Template()
    ground(t, now)
    hearth(t, now)
    for name, (x, z) in PINES.items():
        pine(t, x, z, (HEIGHT_NOW if now else HEIGHT_THEN)[name], notched=now and name == "middle")
    lean_to(t, now)
    fallen(t, now)
    pond(t, now)
    if now:
        t.set(STAKE, "minecraft:spruce_fence", waterlogged=False)
        t.set((STAKE[0], STAKE[1] + 1, STAKE[2]), "minecraft:cobweb")
        t.set(SACK, "supplementaries:sack", waterlogged=False)
    footing(t)
    return t


def ground(t, now):
    for x in range(SIZE):
        for z in range(SIZE):
            d = math.hypot(x - HEARTH[0], z - HEARTH[2])
            path = abs(x - HEARTH[0]) <= 1 and z > HEARTH[2]
            worn = d + (coherent("first_camp/edge", (x, 0, z)) - 0.5) * 3 < (3.2 if now else 4.2) or (path and not now)
            if worn:
                t.set((x, 0, z), OVERGROWN if now else WORN)
                if now and coherent("first_camp/growth", (x, 1, z)) > OVERGROWTH:
                    plant = GROWTH_NOW[int(coherent("first_camp/plant", (x, 1, z)) * 997) % len(GROWTH_NOW)]
                    if plant == "minecraft:large_fern":
                        t.set((x, 1, z), plant, half="lower")
                        t.set((x, 2, z), plant, half="upper")
                    else:
                        t.set((x, 1, z), plant)


def footing(t):
    for x, y, z in list(t.blocks):
        if y == 1 and (x, 0, z) not in t.blocks:
            t.set((x, 0, z), "minecraft:grass_block", snowy=False)


def hearth(t, now):
    hx, _, hz = HEARTH
    t.set(HEARTH, "wayfarer:hearthstone", marked=now)
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if dx or dz:
                t.set((hx + dx, 0, hz + dz), RING_NOW if now else RING_THEN)
                t.blocks.pop((hx + dx, 1, hz + dz), None)
    slabs = (
        ["minecraft:mossy_cobblestone_slab", "minecraft:cobblestone_slab"] if now else ["minecraft:cobblestone_slab"]
    )
    for index, (dx, dz) in enumerate(((-2, 0), (2, 0), (0, -2), (-1, 2), (1, 2))):
        if now and index == 3:
            continue
        t.set((hx + dx, 1, hz + dz), slabs[index % len(slabs)], type="bottom", waterlogged=False)


def pine(t, x, z, height, notched):
    for y in range(1, height + 1):
        if notched and y in NOTCHES:
            t.set((x, y, z), "wayfarer:notched_spruce_log", facing="south", tally=True)
        else:
            t.set((x, y, z), "minecraft:spruce_log", axis="y")
    leaves(t, (x, height + 1, z))
    for layer, radius in enumerate(CONE):
        y = height - layer
        if y < 3:
            break
        for dx in range(-radius, radius + 1):
            for dz in range(-radius, radius + 1):
                if (dx or dz) and abs(dx) + abs(dz) <= radius + (radius > 1):
                    leaves(t, (x + dx, y, z + dz))


def leaves(t, pos):
    if pos not in t.blocks:
        t.set(pos, "minecraft:spruce_leaves", distance=1, persistent=True, waterlogged=False)


def lean_to(t, now):
    px, pz = PINES["middle"]
    posts = [(px - 2, pz + 2), (px + 2, pz + 2)]
    if now:
        t.set((posts[0][0], 1, posts[0][1]), "minecraft:spruce_fence", waterlogged=False)
        for x in range(px - 2, px + 2):
            t.set((x, 1, pz + 3), "minecraft:stripped_spruce_log", axis="x")
        t.set((px - 1, 2, pz + 3), "minecraft:moss_carpet")
        t.set(
            (px + 1, 1, pz + 1),
            "minecraft:spruce_trapdoor",
            facing="south",
            half="bottom",
            open=False,
            powered=False,
            waterlogged=False,
        )
        t.set((px - 1, 1, pz + 1), "minecraft:brown_mushroom")
        return
    for x, z in posts:
        t.set((x, 1, z), "minecraft:spruce_fence", waterlogged=False)
        t.set((x, 2, z), "minecraft:spruce_fence", waterlogged=False)
    for x in range(px - 2, px + 3):
        t.set((x, 3, pz + 2), "minecraft:spruce_slab", type="bottom", waterlogged=False)
        t.set((x, 3, pz + 1), "minecraft:spruce_slab", type="top", waterlogged=False)
        t.set((x, 4, pz + 1), "minecraft:spruce_leaves", distance=1, persistent=True, waterlogged=False)
    for x in range(px - 1, px + 2):
        t.set((x, 1, pz + 1), "minecraft:brown_carpet")


def pond(t, now):
    for x, z in POND:
        t.set((x, 0, z), "minecraft:water", level=0)
        t.blocks.pop((x, 1, z), None)
    if now:
        t.set((POND[0][0], 1, POND[0][1]), "minecraft:lily_pad")


def fallen(t, now):
    for index, pos in enumerate(FALLEN):
        t.set(pos, "minecraft:spruce_log", axis="x")
        if now and index % 2 == 0:
            t.set((pos[0], pos[1] + 1, pos[2]), "minecraft:moss_carpet")
        if now and index == 3:
            t.set((pos[0], pos[1], pos[2] + 1), "minecraft:red_mushroom")
    if not now:
        tip = FALLEN[-1]
        for dz in (-1, 0, 1):
            t.set(
                (tip[0] + 1, 1, tip[2] + dz), "minecraft:spruce_leaves", distance=1, persistent=True, waterlogged=False
            )


def positions():
    return {
        "fallen": [list(pos) for pos in FALLEN],
        "hearth": list(HEARTH),
        "pine": [[PINES["middle"][0], y, PINES["middle"][1]] for y in range(1, HEIGHT_THEN["middle"] + 1)],
        "sack": list(SACK),
        "size": SIZE,
        "stake": list(STAKE),
    }

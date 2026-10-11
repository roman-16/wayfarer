from blocks import Material, Template, coherent

SIZE = 17
HEARTH = (8, 0, 9)
HUT = ((3, 3), (8, 7))
CHAIR = (9, 1, 9)
GARDEN = ((11, 4), (15, 13))
FENCE = {"east": False, "north": False, "south": False, "west": False, "waterlogged": False}
WORN = Material(
    "wend_camp/worn",
    [("minecraft:coarse_dirt", 40), ("minecraft:dirt_path", 25), ("minecraft:dirt", 20), ("minecraft:rooted_dirt", 15)],
    ground=0,
)
RING = Material(
    "wend_camp/ring", [("minecraft:cobblestone", 60), ("minecraft:andesite", 25), ("minecraft:mossy_cobblestone", 15)]
)
LOG = Material("wend_camp/log", [("minecraft:spruce_log", 60), ("minecraft:stripped_spruce_log", 40)])
SAG = Material("wend_camp/sag", [("minecraft:spruce_planks", 75), ("minecraft:mossy_cobblestone", 25)])


def hearth(t, now):
    t.set(HEARTH, "wayfarer:hearthstone", marked=now)
    hx, _, hz = HEARTH
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
        t.set((hx + dx, 0, hz + dz), RING)
    if not now:
        t.set((hx, 1, hz), "minecraft:campfire", facing="south", lit=True, signal_fire=False, waterlogged=False)


def hut(t, now):
    (x0, z0), (x1, z1) = HUT
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            back = z == z0
            if now:
                if edge and not (z == z1 and x0 < x < x1):
                    for y in range(1, 4):
                        t.set((x, y, z), LOG, axis="y")
                t.set((x, 4, z), SAG if coherent("wend_camp/roof", (x, 4, z)) > 0.25 else "minecraft:air")
            elif back or x == x0:
                for y in range(1, 3):
                    t.set((x, y, z), "minecraft:spruce_fence", **FENCE)
                t.set((x, 3, z), "minecraft:spruce_slab", type="bottom", waterlogged=False)
    if now:
        t.set((x0 + 1, 1, z0 + 1), "minecraft:brown_carpet")
        t.set((x0 + 2, 1, z0 + 1), "minecraft:brown_carpet")
        t.set((x1 - 1, 1, z0 + 1), "minecraft:barrel", facing="up", open=False)
        t.set((x0 + 2, 3, z0 + 2), "minecraft:cobweb")
    else:
        t.set((x0 + 1, 1, z0 + 1), "minecraft:gray_carpet")
        t.set((x0 + 2, 1, z0 + 1), "minecraft:gray_carpet")


def garden(t, now):
    (x0, z0), (x1, z1) = GARDEN
    if not now:
        return
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            row = (z - z0) % 2 == 0
            t.set((x, 0, z), "minecraft:coarse_dirt" if row else "minecraft:rooted_dirt")
            if row and coherent("wend_camp/garden", (x, 1, z)) > 0.45:
                t.set((x, 1, z), "minecraft:dead_bush")
    for x in (x0 - 1, x1 + 1):
        for z in range(z0 - 1, z1 + 2, 2):
            if coherent("wend_camp/fence", (x, 1, z)) > 0.3:
                t.set((x, 1, z), "minecraft:spruce_fence", **FENCE)


def chair(t, now):
    t.set(CHAIR, "minecraft:spruce_stairs", facing="west", half="bottom", shape="straight", waterlogged=False)
    if now:
        t.set(
            (CHAIR[0] + 1, CHAIR[1], CHAIR[2]),
            "minecraft:spruce_trapdoor",
            facing="east",
            half="bottom",
            open=True,
            powered=False,
            waterlogged=False,
        )


def wend_camp(now):
    t = Template()
    for x in range(SIZE):
        for z in range(SIZE):
            middle = abs(x - SIZE // 2) + abs(z - SIZE // 2)
            if middle > SIZE - 4 and coherent("wend_camp/edge", (x, 0, z)) > 0.4:
                continue
            t.set((x, 0, z), WORN if now or middle < 7 else "minecraft:grass_block")
    hearth(t, now)
    hut(t, now)
    garden(t, now)
    chair(t, now)
    return t


def positions():
    return {"chair": list(CHAIR), "hearth": list(HEARTH), "size": SIZE}

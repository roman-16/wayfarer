from blocks import Material, Template, coherent

CAMP = (23, 8, 17)
TENTS = [(1, 2, 7), (9, 1, 7), (16, 3, 6)]
TENT_DEPTH = 5
STRIPES = ["minecraft:white_wool", "minecraft:orange_wool", "minecraft:brown_wool"]
FIRE = (11, 11)
FENCE = {"east": False, "north": False, "south": False, "west": False, "waterlogged": False}
GROUND = Material(
    "sand/ground",
    [
        ("minecraft:sand", 60),
        ("minecraft:smooth_sandstone", 15),
        ("minecraft:coarse_dirt", 15),
        ("minecraft:sandstone", 10),
    ],
)
RUG = ["minecraft:orange_carpet", "minecraft:red_carpet", "minecraft:brown_carpet", "minecraft:yellow_carpet"]


def fence(t, pos, **connected):
    t.set(pos, "minecraft:acacia_fence", **(FENCE | connected))


def tent(t, x0, z0, width):
    for x in (x0, x0 + width - 1):
        for z in (z0, z0 + TENT_DEPTH - 1):
            for y in (1, 2):
                fence(t, (x, y, z))
    middle = x0 + width // 2
    for z in (z0, z0 + TENT_DEPTH - 1):
        for y in (1, 2, 3):
            fence(t, (middle, y, z))
    for x in range(x0, x0 + width):
        rise = min(x - x0, x0 + width - 1 - x)
        y = 3 + min(rise, 1)
        for z in range(z0, z0 + TENT_DEPTH):
            stripe = STRIPES[(x - x0) % len(STRIPES)]
            t.set((x, y, z), stripe)
    for x in range(x0 + 1, x0 + width - 1):
        for z in range(z0 + 1, z0 + TENT_DEPTH - 1):
            t.set((x, 1, z), RUG[int(coherent("sand/rug", (x, 1, z)) * len(RUG)) % len(RUG)])
    t.set((x0 + 1, 1, z0 + 1), "minecraft:decorated_pot", facing="south", cracked=False, waterlogged=False)


def camp():
    t = Template()
    width, _, length = CAMP
    for x in range(width):
        for z in range(length):
            if (x < 2 or x > width - 3 or z < 1 or z > length - 2) and coherent("sand/edge", (x, 0, z)) > 0.5:
                continue
            t.set((x, 0, z), GROUND)
    for x, z, w in TENTS:
        tent(t, x, z, w)
    fx, fz = FIRE
    t.set((fx, 1, fz), "minecraft:campfire", facing="south", lit=True, signal_fire=False, waterlogged=False)
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        t.set((fx + dx, 1, fz + dz), "minecraft:sandstone_slab", type="bottom", waterlogged=False)
    for x, z in ((4, 13), (18, 12)):
        t.set((x, 1, z), "minecraft:hay_block", axis="y")
    for z in range(11, 15):
        fence(t, (20, 1, z), north=z > 11, south=z < 14)
    return t


def positions():
    return {"fire": [FIRE[0], 1, FIRE[1]], "sefa": [FIRE[0], 1, FIRE[1] - 2, 0, 1], "size": list(CAMP)}

import math

from blocks import AIR, coherent, noise

TOWARD = {"east": "west", "west": "east", "south": "north", "north": "south"}
SIDES = (("east", 1, 0), ("west", -1, 0), ("south", 0, 1), ("north", 0, -1))


def stair(room, pos, name, facing, half="bottom"):
    room.set(pos, name, facing=facing, half=half, shape="straight", waterlogged=False)


def vault(room, x0, x1, z0, z1, floor, spring, rise, pointed=False, along="z"):
    span = (x1 - x0) / 2 if along == "z" else (z1 - z0) / 2
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            offset = abs(x - (x0 + x1) / 2) if along == "z" else abs(z - (z0 + z1) / 2)
            ratio = min(1.0, offset / (span + 0.5))
            curve = (1 - ratio) if pointed else math.sqrt(max(0.0, 1 - ratio * ratio))
            top = min(room.size[1] - 1, floor + spring + round(curve * rise))
            room.set((x, floor - 1, z), room.floor)
            room.carve((x, floor, z), (x, top - 1, z))


def dome(room, cx, cz, radius, floor, spring, rise):
    for x in range(room.size[0]):
        for z in range(room.size[2]):
            d = math.hypot(x - cx, z - cz)
            if d >= radius:
                continue
            curve = math.sqrt(max(0.0, radius * radius - d * d)) / radius
            top = min(room.size[1] - 1, floor + spring + round(curve * rise))
            room.set((x, floor - 1, z), room.floor)
            room.carve((x, floor, z), (x, top - 1, z))


def cave(room, cx, cz, rx, rz, floor, spring, rise, jitter=1.5, seed="cave"):
    for x in range(room.size[0]):
        for z in range(room.size[2]):
            e = ((x - cx) / rx) ** 2 + ((z - cz) / rz) ** 2 + (coherent(seed, (x, 0, z)) - 0.5) * 0.35
            if e > 1:
                continue
            bump = round((coherent(seed + "/roof", (x, 0, z)) - 0.5) * 2 * jitter)
            top = min(room.size[1] - 1, floor + spring + round(math.sqrt(max(0.0, 1 - e)) * rise) + bump)
            room.set((x, floor - 1, z), room.floor)
            room.carve((x, floor, z), (x, top - 1, z))


def ribs(bay, rib, web, low, keystone=None, origin=(0, 0), wall=None):
    def shell(pos):
        x, y, z = pos
        if y < low:
            return wall or web
        u = (x - origin[0]) % bay
        v = (z - origin[1]) % bay
        centre = bay // 2
        if keystone and u == centre and v == centre:
            return keystone
        if u == 0 or v == 0 or u == v or u + v == bay:
            return rib
        return web

    return shell


def column(room, x, z, low, high, shaft, base, capital, trim=None, wide=False):
    reach = 1 if wide else 0
    for dx in range(-reach, reach + 1):
        for dz in range(-reach, reach + 1):
            corner = abs(dx) == reach and abs(dz) == reach and wide
            room.set((x + dx, low, z + dz), base)
            for y in range(low + 1, high):
                room.set((x + dx, y, z + dz), base if corner and y == low + 1 else shaft)
            room.set((x + dx, high, z + dz), capital)
    if not trim:
        return
    for facing, dx, dz in SIDES:
        for y, half in ((low, "bottom"), (high, "top")):
            for spread in range(-reach, reach + 1):
                pos = (x + dx * (reach + 1) + (spread if dz else 0), y, z + dz * (reach + 1) + (spread if dx else 0))
                if room.get(pos) == AIR and not room.reserved(pos):
                    stair(room, pos, trim, TOWARD[facing], half)


def stump(room, x, z, low, height, shaft, rubble, wide=False):
    reach = 1 if wide else 0
    for dx in range(-reach, reach + 1):
        for dz in range(-reach, reach + 1):
            for y in range(
                low, low + height - (1 if abs(dx) + abs(dz) and noise("stump", x + dx, z + dz) < 0.5 else 0)
            ):
                room.set((x + dx, y, z + dz), shaft)
    for dx, dz in ((2, 0), (-2, 1), (0, -2), (3, 1), (-1, -3), (1, 3), (2, 2), (-3, -1)):
        pos = (x + dx, low, z + dz)
        if room.get(pos) == AIR and not room.reserved(pos):
            if noise("fallen", x, z, dx, dz) < 0.45 and rubble.slab:
                room.set(pos, rubble.slab, type="bottom", waterlogged=False)
            else:
                room.set(pos, rubble.at(pos, 1.0))


def arch(room, x0, x1, y, z, stairs):
    stair(room, (x0, y, z), stairs, "west", "top")
    stair(room, (x1, y, z), stairs, "east", "top")


def dais(room, x0, x1, z0, z1, floor, steps, stairs, material, facing="south"):
    for step in range(steps):
        a, b = x0 + step, x1 - step
        c, d = z0, z1 - step * 2 if facing == "south" else z1
        for x in range(a, b + 1):
            for z in range(c, d + 1):
                room.set((x, floor + step, z), material)
        edge = d + 1 if facing == "south" else c - 1
        for x in range(a, b + 1):
            if room.get((x, floor + step, edge)) == AIR or room.get((x, floor + step, edge)).endswith("_carpet"):
                stair(room, (x, floor + step, edge), stairs, "north" if facing == "south" else "south")
    return floor + steps


def alcove(room, x, z, facing, floor, height, depth=1, back=None):
    dx, dz = {"east": (1, 0), "west": (-1, 0), "south": (0, 1), "north": (0, -1)}[facing]
    for step in range(1, depth + 1):
        for y in range(floor, floor + height):
            room.air((x + dx * step, y, z + dz * step))
    if back:
        room.set((x + dx * (depth + 1), floor + height // 2, z + dz * (depth + 1)), back)
    return (x + dx * depth, floor, z + dz * depth)


def window(room, x, z, facing, low, high, glass, light):
    dx, dz = {"east": (1, 0), "west": (-1, 0), "south": (0, 1), "north": (0, -1)}[facing]
    for y in range(low, high + 1):
        room.set((x + dx, y, z + dz), glass)
        room.set((x + 2 * dx, y, z + 2 * dz), light)


def chandelier(room, x, z, ceiling, drop, light="minecraft:lantern"):
    for y in range(ceiling - drop, ceiling):
        room.set((x, y, z), "minecraft:chain", axis="y", waterlogged=False)
    bottom = ceiling - drop - 1
    room.set((x, bottom, z), "minecraft:sea_lantern" if light == "minecraft:sea_lantern" else "minecraft:spruce_planks")
    for facing, dx, dz in SIDES:
        room.set(
            (x + dx, bottom, z + dz),
            "minecraft:spruce_trapdoor",
            facing=facing,
            half="top",
            open=False,
            powered=False,
            waterlogged=False,
        )
        room.set((x + dx, bottom - 1, z + dz), "minecraft:chain", axis="y", waterlogged=False)
        room.set((x + dx, bottom - 2, z + dz), light, hanging=True, waterlogged=False)


def collapse(room, x, z, radius, floor, rubble, soil="minecraft:coarse_dirt", seed="collapse"):
    for dx in range(-radius, radius + 1):
        for dz in range(-radius, radius + 1):
            d = math.hypot(dx, dz)
            if d > radius + 0.3:
                continue
            height = max(0, round((radius - d) * 0.9 + (coherent(seed, (x + dx, 0, z + dz)) - 0.5) * 2))
            for y in range(floor, floor + height):
                pos = (x + dx, y, z + dz)
                if room.get(pos) == AIR and not room.reserved(pos):
                    room.set(pos, soil if noise(seed, *pos) < 0.35 else rubble)
            top = room.top(x + dx, z + dz, floor + height)
            if d < radius * 0.6 and room.inside((x + dx, top, z + dz)):
                room.set((x + dx, top, z + dz), soil)
                below = (x + dx, top - 1, z + dz)
                if room.get(below) == AIR and noise(seed + "/root", *below) < 0.6:
                    room.set(below, "minecraft:hanging_roots", waterlogged=False)

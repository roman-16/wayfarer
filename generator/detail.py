from blocks import AIR, bears, coherent

SIDES = ((1, 0, "east"), (-1, 0, "west"), (0, 1, "south"), (0, -1, "north"))


def walls(room, pos):
    x, y, z = pos
    return [
        facing
        for dx, dz, facing in SIDES
        if room.inside((x + dx, y, z + dz)) and (x + dx, y, z + dz) not in room.t.blocks
    ]


def ceiling(room, pos):
    above = (pos[0], pos[1] + 1, pos[2])
    return room.inside(above) and above not in room.t.blocks


def standing(room, pos):
    below = room.t.blocks.get((pos[0], pos[1] - 1, pos[2]))
    return below is not None and bears(below[0], dict(below[1]))


def empty(room):
    return sorted(pos for pos in room.open if room.get(pos) == AIR and not room.reserved(pos))


def chance(seed, pos, density):
    return coherent(seed, pos) > 1 - density


def skirting(room, stairs):
    for pos in empty(room):
        sides = walls(room, pos)
        if len(sides) == 1 and standing(room, pos) and not ceiling(room, pos):
            room.set(pos, stairs, facing=sides[0], half="bottom", shape="straight", waterlogged=False)


def cornice(room, stairs):
    for pos in empty(room):
        sides = walls(room, pos)
        if len(sides) == 1 and ceiling(room, pos) and not standing(room, pos):
            room.set(pos, stairs, facing=sides[0], half="top", shape="straight", waterlogged=False)


def rubble(room, material, density=0.25, seed="rubble"):
    for pos in empty(room):
        if not standing(room, pos) or not walls(room, pos) and not near_wall(room, pos):
            continue
        if not chance(seed, pos, density):
            continue
        roll = coherent(seed + "/kind", pos)
        if roll < 0.45 and material.slab:
            room.set(pos, material.slab, type="bottom", waterlogged=False)
        elif roll < 0.8 and material.stairs:
            facing = SIDES[int(roll * 997) % 4][2]
            room.set(pos, material.stairs, facing=facing, half="bottom", shape="straight", waterlogged=False)
        else:
            room.set(pos, material.at(pos, 1.0))


def near_wall(room, pos, reach=2):
    x, y, z = pos
    for dx, dz, _ in SIDES:
        for step in range(1, reach + 1):
            near = (x + dx * step, y, z + dz * step)
            if room.inside(near) and near not in room.t.blocks:
                return True
    return False


def moss(room, density=0.3, seed="moss", block="minecraft:moss_carpet"):
    for pos in empty(room):
        if standing(room, pos) and near_wall(room, pos, 3) and chance(seed, pos, density):
            room.set(pos, block)


def webs(room, density=0.5, seed="webs"):
    for pos in empty(room):
        if len(walls(room, pos)) >= 2 and ceiling(room, pos) and chance(seed, pos, density):
            room.set(pos, "minecraft:cobweb")


def roots(room, density=0.12, seed="roots", soil="minecraft:rooted_dirt"):
    for pos in empty(room):
        above = (pos[0], pos[1] + 1, pos[2])
        if ceiling(room, pos) and not standing(room, pos) and chance(seed, pos, density):
            room.set(above, soil)
            room.set(pos, "minecraft:hanging_roots", waterlogged=False)


def vines(room, density=0.2, seed="vines", longest=4):
    for pos in empty(room):
        sides = walls(room, pos)
        if len(sides) != 1 or not ceiling(room, pos) or not chance(seed, pos, density):
            continue
        faces = {side: side == sides[0] for side in ("east", "north", "south", "west")}
        length = 1 + int(coherent(seed + "/length", pos) * longest)
        for step in range(length):
            below = (pos[0], pos[1] - step, pos[2])
            if room.get(below) != AIR or room.reserved(below) or standing(room, below):
                break
            room.set(below, "minecraft:vine", up=False, **faces)


def floor_lights(room, light, spacing=5, carpet="minecraft:moss_carpet", seed="glow"):
    for pos in empty(room):
        x, y, z = pos
        if not standing(room, pos) or not walls(room, pos) or (x + z) % spacing:
            continue
        if coherent(seed, pos) < 0.4:
            continue
        room.set((x, y - 1, z), light)
        room.set(pos, carpet)

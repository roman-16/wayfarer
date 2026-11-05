import hashlib
import json
import math
from functools import cache

import nbt
from nbt import Byte

AIR = "minecraft:air"
LAMP = "minecraft:waxed_exposed_copper_bulb"
DATA_VERSION = 3955
NEIGHBOURS = [(dx, dy, dz) for dx in (-1, 0, 1) for dy in (-1, 0, 1) for dz in (-1, 0, 1) if (dx, dy, dz) != (0, 0, 0)]
FACING = {"east": (1, 0), "north": (0, -1), "south": (0, 1), "west": (-1, 0)}
OPPOSITE = {"east": "west", "north": "south", "south": "north", "west": "east"}
LIQUIDS = {AIR, "minecraft:water"}
FLIMSY = (
    "_banner",
    "_button",
    "_carpet",
    "_plate",
    "_sign",
    "bush",
    "candle",
    "cobweb",
    "fern",
    "grass",
    "hanging_roots",
    "ladder",
    "rail",
    "torch",
    "vine",
    "vines",
    "vines_plant",
    "wire",
)
PARTIAL = (
    "bell",
    "brewing_stand",
    "calling_stone",
    "campfire",
    "cauldron",
    "chain",
    "chest",
    "decorated_pot",
    "flower_pot",
    "lectern",
    "sea_pickle",
    "skull",
    "snow",
)
RESTING = ("_carpet", "candle", "flower_pot", "rail")


@cache
def lattice(seed, x, y, z):
    return noise(seed, x, y, z)


def smooth(seed, x, y, z, scale):
    fx, fy, fz = x / scale, y / scale, z / scale
    x0, y0, z0 = math.floor(fx), math.floor(fy), math.floor(fz)
    tx, ty, tz = (ease(fx - x0), ease(fy - y0), ease(fz - z0))
    total = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                weight = (tx if dx else 1 - tx) * (ty if dy else 1 - ty) * (tz if dz else 1 - tz)
                total += weight * lattice(seed, x0 + dx, y0 + dy, z0 + dz)
    return total


def ease(t):
    return t * t * (3 - 2 * t)


def coherent(seed, pos):
    x, y, z = pos
    return 0.65 * smooth(seed, x, y, z, 4.0) + 0.35 * smooth(seed + "/fine", x, y, z, 1.6)


class Material:
    def __init__(self, seed, entries, stairs=None, slab=None, wall=None, ground=1, fade=6, spread=1.6):
        self.seed = seed
        self.entries = entries
        self.stairs = stairs
        self.slab = slab
        self.wall = wall
        self.ground = ground
        self.fade = fade
        self.spread = spread
        total = sum(weight for _, weight in entries)
        self.thresholds = []
        running = 0.0
        for block, weight in entries:
            running += weight / total
            self.thresholds.append((running, block))

    def age(self, pos):
        return max(0.0, min(1.0, 1 - (pos[1] - self.ground) / self.fade))

    def at(self, pos, age=None):
        age = self.age(pos) if age is None else age
        value = 0.5 + (coherent(self.seed, pos) - 0.5) * self.spread + (age - 0.35) * 0.45
        value = min(0.999, max(0.0, value))
        for threshold, block in self.thresholds:
            if value < threshold:
                return block
        return self.thresholds[-1][1]

    def expanded(self, slots=20):
        total = sum(weight for _, weight in self.entries)
        blocks = []
        for block, weight in self.entries:
            blocks += [block] * max(1, round(weight / total * slots))
        return blocks


WALKTHROUGH = ("_carpet", "rail", "torch", "short_grass", "fern", "_button", "_pressure_plate")


class Template:
    def __init__(self):
        self.blocks = {}

    def set(self, pos, name, entity=None, **properties):
        if isinstance(name, Material):
            name = name.at(tuple(pos))
        if name == LAMP:
            properties.setdefault("lit", True)
        self.blocks[tuple(pos)] = (
            name,
            tuple(sorted((key, str(value).lower()) for key, value in properties.items())),
            entity,
        )

    def get(self, pos):
        found = self.blocks.get(tuple(pos))
        return found[0] if found else None

    def unsupported(self):
        for (x, y, z), (name, properties, _) in self.blocks.items():
            if hangs(name, dict(properties)):
                above = self.blocks.get((x, y + 1, z))
                if above is None or not holds(above[0], dict(above[1])):
                    yield (name, (x, y, z))
            elif rests(name, dict(properties)):
                below = self.blocks.get((x, y - 1, z))
                if below is None or not bears(below[0], dict(below[1])):
                    yield (name, (x, y, z))

    def walkable(self, pos):
        found = self.blocks.get(tuple(pos))
        return found is None or found[0] == AIR or found[0].split(":", 1)[1].endswith(WALKTHROUGH)

    def shut_in(self):
        beds = [pos for pos, (name, _, _) in self.blocks.items() if name.endswith("_bed")]
        if not beds:
            return []
        low = [min(pos[axis] for pos in self.blocks) for axis in range(3)]
        high = [max(pos[axis] for pos in self.blocks) for axis in range(3)]

        def standable(x, y, z):
            if not (self.walkable((x, y, z)) and self.walkable((x, y + 1, z))):
                return False
            below = self.blocks.get((x, y - 1, z))
            return y - 1 == low[1] and below is None or below is not None and not self.walkable((x, y - 1, z))

        land = low[1] + 1
        reached = {
            (x, land, z)
            for x in range(low[0] - 1, high[0] + 2)
            for z in range(low[2] - 1, high[2] + 2)
            if not (low[0] <= x <= high[0] and low[2] <= z <= high[2]) and standable(x, land, z)
        }
        frontier = list(reached)
        while frontier:
            x, y, z = frontier.pop()
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, nz = x + dx, z + dz
                if not (low[0] - 1 <= nx <= high[0] + 1 and low[2] - 1 <= nz <= high[2] + 1):
                    continue
                for ny in (y + 1, y, y - 1, y - 2, y - 3):
                    if (nx, ny, nz) in reached or not standable(nx, ny, nz):
                        continue
                    if ny > y and not self.walkable((x, y + 2, z)):
                        continue
                    if ny < y and not all(self.walkable((nx, k, nz)) for k in range(ny, y + 2)):
                        continue
                    reached.add((nx, ny, nz))
                    frontier.append((nx, ny, nz))
                    break
        return [
            bed
            for bed in beds
            if not any(
                (bed[0] + dx, y, bed[2] + dz) in reached
                for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))
                for y in (bed[1], bed[1] + 1)
            )
        ]

    def save(self, path, size=None):
        loose = sorted(self.unsupported())
        if loose:
            raise ValueError(f"{path.parent.name}/{path.name}: blocks without support: {loose[:6]}")
        shut = sorted(self.shut_in())
        if shut:
            raise ValueError(f"{path.parent.name}/{path.name}: no two-high way from outside to its bed at {shut[0]}")
        if size is None:
            size = [max(pos[axis] for pos in self.blocks) + 1 for axis in range(3)]
        palette = []
        indices = {}
        blocks = []
        for pos in sorted(self.blocks, key=lambda p: (p[1], p[2], p[0])):
            name, properties, entity = self.blocks[pos]
            state = (name, properties)
            if state not in indices:
                indices[state] = len(palette)
                entry = {"Name": name}
                if properties:
                    entry["Properties"] = dict(properties)
                palette.append(entry)
            block = {"pos": list(pos), "state": indices[state]}
            if entity is not None:
                block["nbt"] = entity
            blocks.append(block)
        nbt.write(
            path,
            {"DataVersion": DATA_VERSION, "blocks": blocks, "entities": [], "palette": palette, "size": list(size)},
        )


class Room:
    def __init__(self, size, shell, floor, shell_at=None, decay=0.0):
        self.size = size
        self.shell = shell
        self.floor = floor
        self.decay = decay
        self.shell_at = shell_at or (lambda pos: shell)
        self.t = Template()
        self.open = set()
        self.keep = set()

    def reserve(self, low, high):
        self.keep.update(cuboid((low[0], 0, low[1]), (high[0], self.size[1] - 1, high[1])))

    def reserved(self, pos):
        return tuple(pos) in self.keep

    def inside(self, pos):
        return all(0 <= pos[axis] < self.size[axis] for axis in range(3))

    def set(self, pos, name, entity=None, **properties):
        if not self.inside(pos):
            return
        if isinstance(name, Material):
            name = name.at(tuple(pos), max(0.0, min(1.0, name.age(pos) + self.decay)))
        self.t.set(pos, name, entity, **properties)

    def get(self, pos):
        return self.t.get(pos)

    def air(self, pos):
        if self.inside(pos):
            self.t.set(pos, AIR)
            self.open.add(tuple(pos))

    def water(self, pos):
        if self.inside(pos):
            self.t.set(pos, "minecraft:water", level=0)
            self.open.add(tuple(pos))

    def fill(self, low, high, name, **properties):
        for pos in cuboid(low, high):
            self.set(pos, name, **properties)

    def carve(self, low, high):
        for pos in cuboid(low, high):
            self.air(pos)

    def top(self, x, z, start):
        y = start
        while self.get((x, y, z)) == AIR:
            y += 1
        return y

    def save(self, path):
        for pos in sorted(self.open):
            for dx, dy, dz in NEIGHBOURS:
                near = (pos[0] + dx, pos[1] + dy, pos[2] + dz)
                if self.inside(near) and near not in self.t.blocks:
                    self.set(near, self.shell_at(near))
        self.support()
        sliding = sorted(self.sliding())
        if sliding:
            raise ValueError(f"{path.parent.name}/{path.name}: falling blocks over open space: {sliding[:6]}")
        self.t.save(path, self.size)

    def support(self):
        loose = [pos for pos, (name, _, _) in self.t.blocks.items() if falls(name)]
        while loose:
            x, y, z = loose.pop()
            below = (x, y - 1, z)
            if y > 0 and below not in self.t.blocks:
                self.set(below, self.shell_at(below))
                if falls(self.t.blocks[below][0]):
                    loose.append(below)

    def sliding(self):
        for (x, y, z), (name, _, _) in self.t.blocks.items():
            below = self.t.blocks.get((x, y - 1, z))
            if falls(name) and y > 0 and (below is None or not bears(below[0], dict(below[1]))):
                yield (name, (x, y, z))


def falls(name):
    short = name.split(":", 1)[1]
    return short != "soul_sand" and (short == "sand" or short.endswith(("_sand", "gravel", "concrete_powder", "anvil")))


def hangs(name, properties):
    return properties.get("hanging") == "true"


def rests(name, properties):
    short = name.split(":", 1)[1]
    return properties.get("hanging") == "false" or short.endswith(RESTING) or short.startswith("potted_")


def flimsy(name, properties):
    short = name.split(":", 1)[1]
    return name in LIQUIDS or "hanging" in properties or short.endswith(FLIMSY) or short.startswith("potted_")


def holds(name, properties):
    if flimsy(name, properties):
        return False
    if name.endswith("trapdoor"):
        return properties.get("half") == "bottom" and properties.get("open") == "false"
    if name.endswith("_slab"):
        return properties.get("type") != "top"
    if name.endswith("_stairs"):
        return properties.get("half") == "bottom"
    return True


def bears(name, properties):
    if flimsy(name, properties) or name.split(":", 1)[1].endswith(PARTIAL):
        return False
    if name.endswith("trapdoor"):
        return properties.get("half") == "top" and properties.get("open") == "false"
    if name.endswith("_slab"):
        return properties.get("type") != "bottom"
    if name.endswith("_stairs"):
        return properties.get("half") == "top"
    return True


def cuboid(low, high):
    for x in range(min(low[0], high[0]), max(low[0], high[0]) + 1):
        for y in range(min(low[1], high[1]), max(low[1], high[1]) + 1):
            for z in range(min(low[2], high[2]), max(low[2], high[2]) + 1):
                yield (x, y, z)


def box(template, low, high, name, **properties):
    for pos in cuboid(low, high):
        template.set(pos, name, **properties)


def noise(*values):
    digest = hashlib.sha256(":".join(str(value) for value in values).encode()).digest()
    return int.from_bytes(digest[:4], "big") / 0xFFFFFFFF


def distance(x, z, cx, cz):
    return math.hypot(x - cx, z - cz)


def ring_points(cx, cz, radius, count, phase=0.0):
    for index in range(count):
        angle = phase + index * math.tau / count
        yield round(cx + radius * math.cos(angle)), round(cz + radius * math.sin(angle))


def facing_towards(x, z, cx, cz):
    dx, dz = cx - x, cz - z
    if abs(dx) >= abs(dz):
        return "east" if dx > 0 else "west"
    return "south" if dz > 0 else "north"


def stairs(room, pos, name, facing, half="bottom"):
    room.set(pos, name, facing=facing, half=half, shape="straight", waterlogged=False)


def slab(room, pos, name, kind="bottom"):
    room.set(pos, name, type=kind, waterlogged=False)


def candle(room, pos, name="minecraft:candle", count=3):
    room.set(pos, name, candles=count, lit=True, waterlogged=False)


def lantern(room, pos, hanging=True, name="minecraft:lantern"):
    room.set(pos, name, hanging=hanging, waterlogged=False)


def chain(room, pos):
    room.set(pos, "minecraft:chain", axis="y", waterlogged=False)


def hanging(room, x, z, below, chains, name="minecraft:lantern"):
    for y in range(below - chains, below):
        chain(room, (x, y, z))
    lantern(room, (x, below - chains - 1, z), name=name)


def text(line):
    return json.dumps(line)


def book(identifier, title, author, pages):
    return {
        "components": {
            "minecraft:custom_data": {"wayfarer_book": identifier},
            "minecraft:written_book_content": {
                "author": author,
                "pages": [{"raw": text(page)} for page in pages],
                "resolved": Byte(1),
                "title": {"raw": title},
            },
        },
        "count": 1,
        "id": "minecraft:written_book",
    }


def lectern(target, pos, facing, identifier, title, author, pages):
    target.set(
        pos,
        "minecraft:lectern",
        entity={"Book": book(identifier, title, author, pages), "Page": 0, "id": "minecraft:lectern"},
        facing=facing,
        has_book=True,
        powered=False,
    )

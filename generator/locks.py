from blocks import Material, Template, coherent, lectern

WIDTH, HEIGHT, LENGTH = 9, 8, 19
HOUSE = (WIDTH + 2, HEIGHT, LENGTH)
MIDDLE = WIDTH // 2
ROOF = 5
DOORS = (5, 10, 14)
LEVERS = (1, 3, 5, 7)
DOWN = (3, 7)
PLATES = ((1, 12), (7, 12))
LANTERNS = (2, 7, 12, 16)
LECTERN = (1, 1, 7)
SIGN = (MIDDLE + 1, 2, DOORS[1] - 1)
WORD = "wake"
STONE = Material(
    "locks/stone",
    [
        ("minecraft:mossy_cobblestone", 40),
        ("minecraft:cobblestone", 20),
        ("minecraft:mossy_stone_bricks", 25),
        ("minecraft:cracked_stone_bricks", 15),
    ],
)
FLOOR = Material(
    "locks/floor",
    [("minecraft:mossy_stone_bricks", 50), ("minecraft:stone_bricks", 30), ("minecraft:cobblestone", 20)],
)
BARS = {"east": True, "north": False, "south": False, "waterlogged": False, "west": True}
SIDES = ("east", "north", "south", "up", "west")


def corner(x, z):
    return x in (0, WIDTH - 1) and z in (0, LENGTH - 1)


class Shifted(Template):
    def set(self, pos, name, entity=None, **properties):
        super().set((pos[0] + 1, pos[1], pos[2]), name, entity, **properties)


def house(prayer):
    t = Shifted()
    for x in range(WIDTH):
        for z in range(LENGTH):
            edge = x in (0, WIDTH - 1) or z in (0, LENGTH - 1)
            t.set((x, 0, z), FLOOR)
            for y in range(1, ROOF):
                if corner(x, z):
                    t.set((x, y, z), "minecraft:jungle_log", axis="y")
                elif edge:
                    t.set((x, y, z), STONE)
                else:
                    t.set((x, y, z), "minecraft:air")
            t.set((x, ROOF, z), "minecraft:jungle_planks" if not edge else "minecraft:mossy_cobblestone")
            if edge:
                t.set((x, ROOF + 1, z), "minecraft:mossy_cobblestone_slab", type="bottom", waterlogged=False)
            elif coherent("locks/roof", (x, ROOF + 1, z)) > 0.35:
                t.set((x, ROOF + 1, z), "minecraft:moss_carpet")
    for z in (0, LENGTH - 1):
        for y in (1, 2):
            t.set((MIDDLE, y, z), "minecraft:air")
        t.set((MIDDLE, 3, z), "minecraft:chiseled_stone_bricks")
    for door in DOORS:
        for x in range(1, WIDTH - 1):
            for y in range(1, ROOF):
                if x == MIDDLE and y <= 2:
                    t.set((x, y, door), "minecraft:iron_bars", **BARS)
                else:
                    t.set((x, y, door), STONE)
    for x in LEVERS:
        t.set((x, 2, DOORS[0] - 1), "minecraft:lever", face="wall", facing="north", powered=False)
        t.set((x, 3, DOORS[0]), "minecraft:chiseled_stone_bricks" if x in DOWN else "minecraft:mossy_stone_bricks")
    lectern(t, LECTERN, "east", prayer["id"], prayer["title"], prayer["author"], prayer["pages"])
    t.set(SIGN, "minecraft:jungle_wall_sign", facing="north", waterlogged=False)
    for x, z in PLATES:
        t.set((x, 1, z), "minecraft:jungle_pressure_plate", powered=False)
    for z in LANTERNS:
        t.set((MIDDLE, ROOF - 1, z), "minecraft:lantern", hanging=True, waterlogged=False)
    for z in range(1, LENGTH - 1):
        for x, side in ((-1, "east"), (WIDTH, "west")):
            for y in range(2, ROOF + 1):
                if coherent("locks/vine", (x, y, z)) > 0.5 - 0.04 * y:
                    t.set((x, y, z), "minecraft:vine", **{key: key == side for key in SIDES})
    return t


def shifted(x, y, z):
    return [x + 1, y, z]


def positions():
    return {
        "back": shifted(MIDDLE, 1, LENGTH),
        "doors": [shifted(MIDDLE, 1, door) for door in DOORS],
        "down": [shifted(x, 2, DOORS[0] - 1) for x in DOWN],
        "front": shifted(MIDDLE, 1, -1),
        "levers": [shifted(x, 2, DOORS[0] - 1) for x in LEVERS],
        "marks": [shifted(x, 3, DOORS[0]) for x in DOWN],
        "plates": [shifted(x, 1, z) for x, z in PLATES],
        "sign": shifted(*SIGN),
        "word": WORD,
    }

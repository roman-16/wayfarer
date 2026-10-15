import hashlib
import json
import math

import png

CLEAR = (0, 0, 0, 0)
SPRITES = "assets/wayfarer/textures/gui/sprites/stamp"
VANILLA = "assets/minecraft/textures/gui/sprites"
JEI = "assets/jei/textures/gui/sprites"
ROLL = [
    "##....",
    "###...",
    ".###..",
    "..###.",
    "...###",
    "..###.",
    ".###..",
    "###...",
    "##....",
]
MARK = ["..#..", ".###.", "#.#.#", "#.#.#", "#.#.#", ".###.", "..#.."]
RING = [".###.", "#...#", "#...#", "#...#", ".###."]
DISC = [".###.", "#####", "#####", "#####", ".###."]
CHECK = [".......#", "......##", "#....##.", "##..##..", ".####...", "..##...."]
CROSS = ["#.....#", ".#...#.", "..#.#..", "...#...", "..#.#..", ".#...#.", "#.....#"]
ARROW = [
    "....#....",
    "...##....",
    "..###....",
    ".########",
    "#########",
    ".########",
    "..###....",
    "...##....",
    "....#....",
]
LOCK = ["..###..", ".#...#.", ".#...#.", "#######", "##...##", "###.###", "###.###", "#######"]
OPEN_LOCK = ["..###..", ".#...#.", ".#.....", "#######", "##...##", "###.###", "###.###", "#######"]
MARKS = {
    "fire": ["..#..", "..##.", ".###.", ".####", "#####", "##.##", ".###."],
    "grave": [".###.", "#####", "##.##", "#...#", "##.##", "#####"],
    "hall": ["..#..", ".###.", "#####", "#...#", "#.#.#", "#.#.#", "#####"],
    "left": ["...#", "..##", ".###", "####", ".###", "..##", "...#"],
    "pointer": ["#####", ".###.", "..#.."],
    "right": ["#...", "##..", "###.", "####", "###.", "##..", "#..."],
    "tent": ["...#...", "..###..", ".##.##.", "##...##", "#######"],
}
WHITE = (255, 254, 253, 255)
ICONS = {
    "afterwards": [
        "#...#...#",
        ".#.....#.",
        "...###...",
        "..#####..",
        ".#######.",
        "#########",
        ".........",
        ".#######.",
        "...###...",
    ],
    "conversations": [
        "....#....",
        "....##...",
        "...###...",
        "..####.#.",
        "..#####..",
        ".###.###.",
        ".##...##.",
        "..#####..",
        "#########",
    ],
    "deeds": [
        "....#....",
        "...###...",
        "...###...",
        "#########",
        ".#######.",
        "..#####..",
        "..#####..",
        ".###.###.",
        ".##...##.",
    ],
    "gear": [
        ".#######.",
        "#.......#",
        "#########",
        "#...#...#",
        "#..###..#",
        "#...#...#",
        "#.......#",
        "#########",
        ".........",
    ],
    "map": [
        "##..##..#",
        "#.##..##.",
        "#.#.#.#.#",
        "#.#.#.#.#",
        "#.#.#.#.#",
        "#.#.#.#.#",
        "#.#.#.#.#",
        ".##..##.#",
        "#..##..##",
    ],
    "pages": [
        ".######..",
        ".#....##.",
        ".#.....#.",
        ".#.###.#.",
        ".#.....#.",
        ".#.###.#.",
        ".#.....#.",
        ".#######.",
        ".........",
    ],
    "people": [
        "..##.....",
        ".####.##.",
        ".########",
        "..##.####",
        ".####.##.",
        "######...",
        "######.##",
        "######.##",
        ".........",
    ],
    "quests": [
        ".#######.",
        "#.......#",
        "#.#####.#",
        "#.......#",
        "#.####..#",
        "#.......#",
        "#.#####.#",
        "#.......#",
        ".#######.",
    ],
    "stones": [
        "..#####..",
        ".#.....#.",
        "#...#...#",
        "#..###..#",
        "#...#...#",
        "#...#...#",
        "#.......#",
        "#########",
        ".........",
    ],
    "story": [
        "##.....##",
        "#.##.##.#",
        "#...#...#",
        "#.#.#.#.#",
        "#...#...#",
        "#.#.#.#.#",
        "#...#...#",
        "#########",
        ".........",
    ],
    "words": [
        ".#######.",
        "#.......#",
        "#.#.#.#.#",
        "#.......#",
        ".######.#",
        "......#.#",
        ".......##",
        "........#",
        ".........",
    ],
}


def rgba(value, alpha=255):
    return tuple(int(value[index : index + 2], 16) for index in (1, 3, 5)) + (alpha,)


class Tokens:
    def __init__(self, spec):
        self.ramps = {name: [rgba(value) for value in ramp] for name, ramp in spec["ramps"].items()}
        self.text = {name: rgba(value) for name, value in spec["text"].items()}
        self.alpha = spec["alpha"]

    def __call__(self, ramp, shade, alpha=255):
        return self.ramps[ramp][shade][:3] + (alpha,)


def chance(*parts):
    digest = hashlib.sha256("/".join(map(str, parts)).encode()).digest()
    return int.from_bytes(digest[:4], "big") / 2**32


class Canvas:
    def __init__(self, width, height):
        self.width = width
        self.height = height
        self.pixels = [[CLEAR] * width for _ in range(height)]

    def set(self, x, y, color):
        if 0 <= x < self.width and 0 <= y < self.height:
            self.pixels[y][x] = color

    def get(self, x, y):
        return self.pixels[y][x]

    def fill(self, left, top, right, bottom, color):
        for y in range(top, bottom):
            for x in range(left, right):
                self.set(x, y, color)

    def grain(self, name, left, top, right, bottom, tokens, ramp, shade, alpha=255, share=0.07):
        for y in range(top, bottom):
            for x in range(left, right):
                roll = chance(name, x, y)
                step = -1 if roll < share else 1 if roll > 1 - share else 0
                self.set(x, y, tokens(ramp, min(len(tokens.ramps[ramp]) - 1, max(0, shade + step)), alpha))

    def shape(self, rows, left, top, color):
        for y, row in enumerate(rows):
            for x, cell in enumerate(row):
                if cell == "#":
                    self.set(left + x, top + y, color)

    def ring(self, inset, top_left, bottom_right, rounded=False):
        left, top = inset, inset
        right, bottom = self.width - 1 - inset, self.height - 1 - inset
        for x in range(left, right + 1):
            if rounded and x in (left, right):
                continue
            self.set(x, top, top_left)
            self.set(x, bottom, bottom_right)
        for y in range(top, bottom + 1):
            if rounded and y in (top, bottom):
                continue
            self.set(left, y, top_left)
            self.set(right, y, bottom_right)
        if not rounded:
            self.set(right, top, bottom_right)
            self.set(left, bottom, bottom_right)


def rivet(canvas, tokens, x, y):
    canvas.set(x, y, tokens("iron", 4))
    canvas.set(x + 1, y, tokens("iron", 3))
    canvas.set(x, y + 1, tokens("iron", 3))
    canvas.set(x + 1, y + 1, tokens("iron", 1))


def plate(canvas, tokens, left, top, width, height, rivets=()):
    for y in range(top, top + height):
        for x in range(left, left + width):
            edge_x = x in (left, left + width - 1)
            edge_y = y in (top, top + height - 1)
            if edge_x and edge_y:
                continue
            if edge_x or edge_y:
                canvas.set(x, y, tokens("iron", 0))
            elif x == left + 1 or y == top + 1:
                canvas.set(x, y, tokens("iron", 3))
            elif x == left + width - 2 or y == top + height - 2:
                canvas.set(x, y, tokens("iron", 1))
            else:
                canvas.set(x, y, tokens("iron", 2))
    for x, y in rivets:
        rivet(canvas, tokens, left + x, top + y)


def cracks(canvas, tokens, name, count):
    edges = (
        [(x, 1) for x in range(8, canvas.width - 8)]
        + [(x, canvas.height - 2) for x in range(8, canvas.width - 8)]
        + [(1, y) for y in range(8, canvas.height - 8)]
        + [(canvas.width - 2, y) for y in range(8, canvas.height - 8)]
    )
    if not edges:
        return
    for index in range(count):
        x, y = edges[int(chance(name, "crack", index) * len(edges))]
        canvas.set(x, y, tokens("iron", 0))
        inward_x = 1 if x == 1 else -1 if x == canvas.width - 2 else 0
        inward_y = 1 if y == 1 else -1 if y == canvas.height - 2 else 0
        canvas.set(x + inward_x, y + inward_y, tokens("iron", 1))


def panel(tokens, name, width=64, height=64, alpha=255, material="ash"):
    canvas = Canvas(width, height)
    canvas.grain(name, 4, 4, width - 4, height - 4, tokens, material, 2, alpha)
    canvas.ring(0, tokens("iron", 0), tokens("iron", 0), rounded=True)
    canvas.ring(1, tokens("iron", 3), tokens("iron", 1))
    canvas.ring(2, tokens("iron", 2), tokens("iron", 2))
    canvas.ring(3, tokens(material, 0, alpha), tokens(material, 3, alpha))
    cracks(canvas, tokens, name, max(1, (width + height) // 32))
    for left, top in ((0, 0), (width - 6, 0), (0, height - 6), (width - 6, height - 6)):
        plate(canvas, tokens, left, top, 6, 6, [(2, 2)])
    return canvas, {"border": 6, "height": height, "type": "nine_slice", "width": width}


def strip(tokens, name, width=64, height=26, cap=9):
    canvas = Canvas(width, height)
    canvas.grain(name, 0, 2, width, height - 2, tokens, "leather", 2)
    for x in range(width):
        canvas.set(x, 0, tokens("iron", 0))
        canvas.set(x, 1, tokens("leather", 4))
        canvas.set(x, height - 2, tokens("leather", 0))
        canvas.set(x, height - 1, tokens("iron", 0))
        if x % 2 == 0:
            canvas.set(x, 3, tokens("paper", 0))
            canvas.set(x, height - 4, tokens("paper", 0))
    middle = height // 2 - 1
    for left in (0, width - cap):
        plate(canvas, tokens, left, 0, cap, height, [(3, 4), (3, height - 6)])
        canvas.set(left + 3, middle, tokens("iron", 1))
        canvas.set(left + 4, middle, tokens("iron", 1))
    return canvas, {
        "border": {"bottom": 4, "left": cap, "right": cap, "top": 4},
        "height": height,
        "type": "nine_slice",
        "width": width,
    }


def band(tokens, name, width=64, height=15, cap=8):
    canvas = Canvas(width, height)
    canvas.grain(name, 0, 2, width, height - 2, tokens, "leather", 1, tokens.alpha["clear"])
    for x in range(width):
        canvas.set(x, 0, tokens("iron", 0))
        canvas.set(x, 1, tokens("leather", 3))
        canvas.set(x, height - 2, tokens("leather", 0))
        canvas.set(x, height - 1, tokens("iron", 0))
    for left in (0, width - cap):
        plate(canvas, tokens, left, 0, cap, height, [(3, height // 2 - 1)])
    return canvas, {
        "border": {"bottom": 3, "left": cap, "right": cap, "top": 3},
        "height": height,
        "type": "nine_slice",
        "width": width,
    }


def button(tokens, name, state, width=200, height=20):
    rim = {"normal": ("iron", 3, 2, 1), "highlighted": ("ember", 4, 3, 1), "disabled": ("iron", 2, 1, 1)}[state]
    fill = {"normal": ("leather", 2), "highlighted": ("leather", 3), "disabled": ("ash", 1)}[state]
    canvas = Canvas(width, height)
    canvas.grain(name, 2, 2, width - 2, height - 2, tokens, fill[0], fill[1])
    canvas.ring(0, tokens("iron", 0), tokens("iron", 0), rounded=True)
    canvas.ring(1, tokens(rim[0], rim[1]), tokens(rim[0], rim[3]))
    for x in range(2, width - 2):
        canvas.set(x, height - 3, tokens(fill[0], max(0, fill[1] - 1)))
    if state != "disabled":
        for x, y in ((2, 2), (width - 3, 2), (2, height - 4), (width - 3, height - 4)):
            canvas.set(x, y, tokens("iron", 4 if state == "normal" else 3))
    return canvas, {"border": 3, "height": height, "type": "nine_slice", "width": width}


def inset(tokens, name, width, height, fill=("ash", 1), light=("iron", 3), dark=("iron", 0), alpha=255):
    canvas = Canvas(width, height)
    canvas.grain(name, 1, 1, width - 1, height - 1, tokens, fill[0], fill[1], alpha)
    for x in range(width):
        canvas.set(x, 0, tokens(*dark))
        canvas.set(x, height - 1, tokens(*light))
    for y in range(height):
        canvas.set(0, y, tokens(*dark))
        canvas.set(width - 1, y, tokens(*light))
    canvas.set(width - 1, 0, tokens("iron", 2))
    canvas.set(0, height - 1, tokens("iron", 2))
    return canvas


def keycap(tokens, name):
    canvas = Canvas(12, 12)
    canvas.grain(name, 1, 1, 11, 9, tokens, "ash", 3)
    canvas.ring(0, tokens("iron", 0), tokens("iron", 0), rounded=True)
    for x in range(1, 11):
        canvas.set(x, 1, tokens("iron", 4))
        canvas.set(x, 9, tokens("iron", 1))
        canvas.set(x, 10, tokens("iron", 0))
    for y in range(2, 9):
        canvas.set(1, y, tokens("iron", 3))
        canvas.set(10, y, tokens("iron", 2))
    return canvas, {"border": 3, "height": 12, "type": "nine_slice", "width": 12}


def tooltip(tokens, name):
    canvas = Canvas(32, 32)
    canvas.grain(name, 2, 2, 30, 30, tokens, "ash", 1, 240)
    canvas.ring(0, tokens("iron", 0), tokens("iron", 0), rounded=True)
    canvas.ring(1, tokens("iron", 3), tokens("iron", 1))
    for x, y in ((2, 2), (29, 2), (2, 29), (29, 29)):
        canvas.set(x, y, tokens("iron", 4))
    return canvas, {"border": 3, "height": 32, "type": "nine_slice", "width": 32}


def tooltip_story(tokens, name):
    canvas, scaling = tooltip(tokens, name)
    canvas.ring(1, tokens("ember", 4), tokens("ember", 1))
    for x, y in ((2, 2), (29, 2), (2, 29), (29, 29)):
        canvas.set(x, y, tokens("ember", 3))
    return canvas, scaling


def book(tokens, name, width=128, height=96):
    canvas = Canvas(width, height)
    canvas.grain(name, 1, 1, width - 1, height - 1, tokens, "leather", 2, share=0.1)
    canvas.ring(0, tokens("leather", 0), tokens("leather", 0), rounded=True)
    canvas.ring(1, tokens("leather", 4), tokens("leather", 1))
    for x in range(4, width - 4):
        if x % 3:
            canvas.set(x, 3, tokens("paper", 0))
            canvas.set(x, height - 4, tokens("paper", 0))
    for y in range(4, height - 4):
        if y % 3:
            canvas.set(3, y, tokens("paper", 0))
            canvas.set(width - 4, y, tokens("paper", 0))
    for index in range(width // 8):
        x = 4 + int(chance(name, "scuff", index) * (width - 8))
        y = 4 + int(chance(name, "scuff", index, "y") * (height - 8))
        canvas.set(x, y, tokens("leather", 1))
    for left, top in ((0, 0), (width - 10, 0), (0, height - 10), (width - 10, height - 10)):
        plate(canvas, tokens, left, top, 10, 10, [(4, 4)])
    return canvas, {"border": 10, "height": height, "type": "nine_slice", "width": width}


def spine(tokens, name, width=12, height=32):
    canvas = Canvas(width, height)
    canvas.grain(name, 0, 0, width, height, tokens, "leather", 1, share=0.1)
    for y in range(height):
        canvas.set(0, y, tokens("leather", 0))
        canvas.set(1, y, tokens("leather", 2))
        canvas.set(width - 2, y, tokens("leather", 2))
        canvas.set(width - 1, y, tokens("leather", 0))
        if y % 4 == 1:
            canvas.set(width // 2 - 1, y, tokens("leather", 3))
    return canvas, {
        "border": {"bottom": 0, "left": 2, "right": 2, "top": 0},
        "height": height,
        "type": "nine_slice",
        "width": width,
    }


def page(tokens, name, side, width=64, height=96):
    canvas = Canvas(width, height)
    canvas.grain(name, 0, 0, width, height, tokens, "paper", 3, share=0.025)
    gutter = width - 1 if side == "left" else 0
    outer = 0 if side == "left" else width - 1
    for y in range(height):
        for step, shade in ((0, 1), (1, 2), (2, 2)):
            x = gutter - step if side == "left" else gutter + step
            canvas.set(x, y, tokens("paper", shade))
        canvas.set(outer, y, tokens("paper", 1))
        canvas.set(outer + (1 if side == "left" else -1), y, tokens("paper", 2))
    for x in range(width):
        canvas.set(x, 0, tokens("paper", 1))
        canvas.set(x, height - 1, tokens("paper", 0))
        canvas.set(x, height - 2, tokens("paper", 2))
    for index in range(4):
        x = 4 + int(chance(name, "soot", index) * (width - 8))
        y = 4 + int(chance(name, "soot", index, "y") * (height - 8))
        canvas.set(x, y, tokens("paper", 2))
    border = {"bottom": 3, "left": 3, "right": 3, "top": 2}
    return canvas, {"border": border, "height": height, "type": "nine_slice", "width": width}


def ribbon(tokens, name, active):
    width, height = 48, 13
    fill = ("ember", 2) if active else ("leather", 3)
    canvas = Canvas(width, height)
    for y in range(height):
        notch = abs(y - height // 2)
        end = width - 1 - (height // 2 - notch)
        for x in range(end):
            roll = chance(name, x, y)
            shade = fill[1] + (-1 if roll < 0.08 else 1 if roll > 0.92 and fill[1] < 4 else 0)
            canvas.set(x, y, tokens(fill[0], shade))
        canvas.set(end, y, tokens("leather", 0))
    for x in range(width - 1):
        canvas.set(x, 0, tokens("leather", 0))
        canvas.set(x, height - 1, tokens("leather", 0))
    for x in range(width - 2):
        canvas.set(x, 1, tokens(fill[0], min(4, fill[1] + 1)))
        canvas.set(x, height - 2, tokens(fill[0], fill[1] - 1))
    return canvas, {
        "border": {"bottom": 3, "left": 4, "right": 8, "top": 3},
        "height": height,
        "type": "nine_slice",
        "width": width,
    }


def divider(tokens, name, ink):
    canvas = Canvas(16, 5)
    color = tokens.text["ink_quiet"] if ink else tokens("iron", 3)
    for x in range(16):
        canvas.set(x, 2, color)
    for left in (0, 11):
        canvas.shape(["..#..", ".#.#.", "#...#", ".#.#.", "..#.."], left, 0, color)
    return canvas, {
        "border": {"bottom": 0, "left": 5, "right": 5, "top": 0},
        "height": 5,
        "type": "nine_slice",
        "width": 16,
    }


def plate_sprite(tokens, name):
    canvas = Canvas(16, 12)
    canvas.grain(name, 2, 2, 14, 10, tokens, "ash", 1, tokens.alpha["clear"])
    canvas.ring(0, tokens("iron", 0), tokens("iron", 0), rounded=True)
    canvas.ring(1, tokens("iron", 3), tokens("iron", 1))
    return canvas, {"border": 2, "height": 12, "type": "nine_slice", "width": 16}


def glyph(rows, color, shadow=None):
    canvas = Canvas(len(rows[0]) + (1 if shadow else 0), len(rows) + (1 if shadow else 0))
    if shadow:
        canvas.shape(rows, 1, 1, shadow)
    canvas.shape(rows, 0, 0, color)
    return canvas, None


def hotbar(tokens, name):
    canvas = Canvas(182, 22)
    canvas.grain(name, 1, 1, 181, 21, tokens, "leather", 1)
    canvas.ring(0, tokens("iron", 0), tokens("iron", 0), rounded=True)
    for x in range(1, 181):
        canvas.set(x, 1, tokens("leather", 3))
        canvas.set(x, 20, tokens("leather", 0))
    for index in range(9):
        well = inset(tokens, f"{name}-{index}", 18, 18)
        for y in range(18):
            for x in range(18):
                canvas.set(2 + 20 * index + x, 2 + y, well.get(x, y))
    for left in (0, 180):
        for y in range(3, 19):
            canvas.set(left + (1 if left == 0 else 0), y, tokens("iron", 2))
    return canvas, None


def selection(tokens):
    canvas = Canvas(24, 23)
    for inset_by, color in ((0, tokens("ember", 0)), (1, tokens("ember", 3)), (2, tokens("ember", 1))):
        for x in range(inset_by, 24 - inset_by):
            canvas.set(x, inset_by, color)
            canvas.set(x, 22 - inset_by, color)
        for y in range(inset_by, 23 - inset_by):
            canvas.set(inset_by, y, color)
            canvas.set(23 - inset_by, y, color)
    for x, y in ((1, 1), (22, 1), (1, 21), (22, 21)):
        canvas.set(x, y, tokens("ember", 4))
    return canvas, None


def offhand(tokens, name, side):
    canvas = Canvas(29, 24)
    left = 0 if side == "left" else 7
    box = Canvas(22, 24)
    box.grain(name, 1, 1, 21, 23, tokens, "leather", 1)
    box.ring(0, tokens("iron", 0), tokens("iron", 0), rounded=True)
    well = inset(tokens, f"{name}-well", 18, 18)
    for y in range(24):
        for x in range(22):
            canvas.set(left + x, y, box.get(x, y))
    for y in range(18):
        for x in range(18):
            canvas.set(left + 2 + x, 3 + y, well.get(x, y))
    return canvas, None


def tab(tokens, name, selected, highlighted):
    width, height = 130, 24
    shade = 3 if selected else 2
    shade += 1 if highlighted else 0
    canvas = Canvas(width, height)
    canvas.grain(name, 2, 2, width - 2, height, tokens, "leather", min(4, shade))
    for x in range(1, width - 1):
        canvas.set(x, 0, tokens("iron", 0))
        canvas.set(x, 1, tokens("ember", 3) if selected else tokens("leather", 4))
    for y in range(1, height):
        canvas.set(0, y, tokens("iron", 0))
        canvas.set(width - 1, y, tokens("iron", 0))
        canvas.set(1, y, tokens("leather", 4))
        canvas.set(width - 2, y, tokens("leather", 1))
    if not selected:
        for x in range(width):
            canvas.set(x, height - 1, tokens("iron", 0))
    border = {"bottom": 0, "left": 2, "right": 2, "top": 2}
    return canvas, {"border": border, "height": height, "type": "nine_slice", "width": width}


def checkbox(tokens, name, selected, highlighted):
    canvas = inset(tokens, name, 20, 20, fill=("ash", 0))
    if highlighted:
        canvas.ring(0, tokens("ember", 3), tokens("ember", 1))
    if selected:
        canvas.shape(CHECK, 6, 7, tokens("ember", 0))
        canvas.shape(CHECK, 5, 6, tokens("ember", 3))
    return canvas, None


def small_button(tokens, name, rows, highlighted, disabled=False):
    size = 14 if rows is CROSS else 20
    canvas, _ = button(tokens, name, "highlighted" if highlighted else "disabled" if disabled else "normal", size, size)
    color = tokens.text["dim"] if disabled else tokens("ember", 3) if highlighted else tokens.text["bone"]
    left = (size - len(rows[0])) // 2
    top = (size - len(rows)) // 2
    canvas.shape(rows, left + 1, top + 1, tokens.text["shadow"])
    canvas.shape(rows, left, top, color)
    return canvas, None


def page_arrow(tokens, forward, highlighted):
    canvas = Canvas(23, 13)
    rows = [row[::-1] for row in ARROW] if forward else ARROW
    color = tokens.text["ink_red"] if highlighted else tokens.text["ink"]
    canvas.shape(rows, 7, 2, color)
    return canvas, None


def slider(tokens, name, highlighted):
    canvas = inset(tokens, name, 200, 20, fill=("ash", 0))
    if highlighted:
        canvas.ring(0, tokens("ember", 3), tokens("ember", 1))
    return canvas, {"border": 2, "height": 20, "type": "nine_slice", "width": 200}


def handle(tokens, name, highlighted):
    canvas = Canvas(8, 20)
    plate(canvas, tokens, 0, 0, 8, 20, [(3, 9)])
    if highlighted:
        canvas.ring(0, tokens("ember", 3), tokens("ember", 1))
    return canvas, {
        "border": {"bottom": 2, "left": 2, "right": 2, "top": 2},
        "height": 20,
        "type": "nine_slice",
        "width": 8,
    }


def text_field(tokens, name, highlighted):
    canvas = inset(tokens, name, 200, 20, fill=("ash", 0))
    if highlighted:
        canvas.ring(0, tokens("ember", 3), tokens("ember", 2))
    return canvas, {"border": 1, "height": 20, "type": "nine_slice", "width": 200}


def scroller(tokens, name, background):
    if background:
        return inset(tokens, name, 6, 32, fill=("ash", 0)), {
            "border": 1,
            "height": 32,
            "type": "nine_slice",
            "width": 6,
        }
    canvas = Canvas(6, 32)
    plate(canvas, tokens, 0, 0, 6, 32)
    return canvas, {"border": 2, "height": 32, "type": "nine_slice", "width": 6}


def page_scroller(tokens, name, background):
    width, height = 4, 32
    canvas = Canvas(width, height)
    if background:
        for y in range(height):
            canvas.set(1, y, tokens("paper", 1))
            canvas.set(2, y, tokens("paper", 2))
        return canvas, {"border": 1, "height": height, "type": "nine_slice", "width": width}
    canvas.grain(name, 1, 1, width - 1, height - 1, tokens, "leather", 3)
    for y in range(height):
        canvas.set(0, y, tokens("leather", 1))
        canvas.set(width - 1, y, tokens("leather", 1))
    for x in range(width):
        canvas.set(x, 0, tokens("leather", 1))
        canvas.set(x, height - 1, tokens("leather", 0))
    for y in range(1, height - 1):
        canvas.set(1, y, tokens("leather", 4))
    return canvas, {
        "border": {"bottom": 2, "left": 1, "right": 1, "top": 2},
        "height": height,
        "type": "nine_slice",
        "width": width,
    }


def toast(tokens, name):
    canvas, _ = panel(tokens, name, 160, 32)
    return canvas, None


def book_screen(tokens):
    canvas = Canvas(256, 256)
    left, top, width, height = 20, 1, 146, 180
    cover, _ = book(tokens, "book-screen", width, height)
    sheet, _ = page(tokens, "book-screen-page", "right", width - 22, height - 16)
    binding, _ = spine(tokens, "book-screen-spine", 8, height - 16)
    for y in range(height):
        for x in range(width):
            canvas.set(left + x, top + y, cover.get(x, y))
    for y in range(sheet.height):
        for x in range(sheet.width):
            canvas.set(left + 14 + x, top + 8 + y, sheet.get(x, y))
    for y in range(binding.height):
        for x in range(binding.width):
            canvas.set(left + 6 + x, top + 8 + y, binding.get(x, y))
    return canvas


def word(glyphs, text, scale, spacing):
    shapes = []
    for letter in text:
        rows = glyphs[ord(letter)]
        columns = [x for x in range(len(rows[0])) if any(row[x] for row in rows)]
        shapes.append([row[columns[0] : columns[-1] + 1] for row in rows])
    used = [y for y in range(len(shapes[0])) if any(any(shape[y]) for shape in shapes)]
    width = sum(len(shape[0]) * scale for shape in shapes) + spacing * (len(shapes) - 1)
    mask = [[False] * width for _ in range((used[-1] - used[0] + 1) * scale)]
    left = 0
    for shape in shapes:
        for y in range(used[0], used[-1] + 1):
            for x, on in enumerate(shape[y]):
                for dy in range(scale * on):
                    for dx in range(scale):
                        mask[(y - used[0]) * scale + dy][left + x * scale + dx] = True
        left += len(shape[0]) * scale + spacing
    return mask


def emblem(size, thickness, reach):
    mask = [[False] * size for _ in range(size + reach * 2)]
    centre = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            if size / 2 - thickness <= math.hypot(x - centre, y - centre) + 0.15 <= size / 2 + 0.1:
                mask[y + reach][x] = True
    for row in mask:
        row[size // 2 - 1] = row[size // 2] = True
    return mask


def lit(mask, x, y):
    return 0 <= y < len(mask) and 0 <= x < len(mask[0]) and mask[y][x]


def gilt(tokens, name):
    def face(mask, x, y):
        if not lit(mask, x, y - 1):
            return tokens("ember", 4)
        if not lit(mask, x, y + 1):
            return tokens("ember", 0)
        if not lit(mask, x + 1, y):
            return tokens("ember", 1)
        fall = y / max(1, len(mask) - 1)
        shade = 3 if fall < 0.35 else 2 if fall < 0.75 else 1
        shade += 0 if lit(mask, x - 1, y) else 1
        roll = chance(name, x, y)
        shade += 1 if roll < 0.05 else -1 if roll > 0.95 else 0
        return tokens("ember", max(0, min(4, shade)))

    return face


def emboss(canvas, mask, left, top, face, side, outline, shadow, depth):
    height, width = len(mask), len(mask[0])

    def filled(x, y):
        return lit(mask, x, y)

    def solid(x, y):
        return any(filled(x, y - lift) for lift in range(depth + 1))

    def rim(x, y):
        return not solid(x, y) and any(solid(x + dx, y + dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1))

    for y in range(-2, height + depth + 2):
        for x in range(-2, width + 2):
            if filled(x, y):
                canvas.set(left + x, top + y, face(mask, x, y))
            elif solid(x, y):
                canvas.set(left + x, top + y, side[1] if filled(x, y - 1) else side[0])
            elif rim(x, y):
                canvas.set(left + x, top + y, outline)
            elif rim(x - 1, y - 1) or solid(x - 1, y - 1):
                canvas.set(left + x, top + y, shadow)


def rule(canvas, left, right, y, light, dark):
    for x in range(left, right + 1):
        canvas.set(x, y, light)
        canvas.set(x, y + 1, dark)
    for end in (left, right):
        for dx, dy in ((0, -2), (-1, -1), (1, -1), (-2, 0), (2, 0), (-1, 1), (1, 1), (0, 2)):
            canvas.set(end + dx, y + dy, light)
        canvas.set(end, y, dark)


def logo(tokens, glyphs):
    canvas = Canvas(256, 64)
    letters = word(glyphs, "WAYFARER", 3, 3)
    mark = emblem(14, 3, 3)
    shadow = tokens.text["shadow"][:3] + (tokens.alpha["dim"],)
    left = (canvas.width - len(letters[0])) // 2
    mark_left = (canvas.width - len(mark[0])) // 2
    middle = 1 + len(mark) // 2
    rule(canvas, left + 6, mark_left - 5, middle, tokens("iron", 3), tokens("iron", 0))
    rule(canvas, mark_left + len(mark[0]) + 4, left + len(letters[0]) - 7, middle, tokens("iron", 3), tokens("iron", 0))
    emboss(
        canvas,
        mark,
        mark_left,
        1,
        gilt(tokens, "logo-mark"),
        (tokens("ember", 0), tokens("ember", 1)),
        tokens("ember", 0),
        shadow,
        1,
    )
    emboss(
        canvas,
        letters,
        left,
        len(mark) + 4,
        gilt(tokens, "logo"),
        (tokens("leather", 1), tokens("leather", 3)),
        tokens("ash", 0),
        shadow,
        3,
    )
    return canvas


def jei(tokens):
    nine = {"border": 2, "height": 18, "type": "nine_slice", "width": 18}
    sprites = {
        name: panel(tokens, f"jei-{name}")
        for name in (
            "bookmark_list_background_v2",
            "gui_background_v2",
            "ingredient_list_background_v2",
            "interactive_ingredient_tooltip_background",
            "recipe_preview_background_v2",
            "single_recipe_background_v2",
        )
    }
    sprites |= {name: panel(tokens, f"jei-{name}", 28, 28) for name in ("catalyst_tab_v2", "recipe_options_tab_v2")}
    sprites |= {
        name: (inset(tokens, f"jei-{name}", 18, 18), nine)
        for name in (
            "bookmark_list_slot_background_v2",
            "ingredient_list_slot_background_v2",
            "recipe_catalyst_slot_background_v2",
        )
    }
    sprites["slot"] = (inset(tokens, "jei-slot", 18, 18), None)
    sprites["output_slot"] = (inset(tokens, "jei-output-slot", 26, 26), None)
    sprites["button_enabled_v2"] = button(tokens, "jei-button", "normal")
    sprites["button_disabled_v2"] = button(tokens, "jei-button-disabled", "disabled")
    for name in ("button_highlight_v2", "button_pressed_highlight_v2", "button_pressed_v2"):
        sprites[name] = button(tokens, f"jei-{name}", "highlighted")
    sprites["search_background_v2"] = text_field(tokens, "jei-search", False)
    sprites["scrollbar_background_v2"] = scroller(tokens, "jei-scrollbar", True)
    sprites["scrollbar_marker_v2"] = scroller(tokens, "jei-scrollbar-marker", False)
    sprites["tab_selected"] = tab(tokens, "jei-tab-selected", True, False)
    sprites["tab_unselected"] = tab(tokens, "jei-tab", False, False)
    return sprites


PING_ARROW = (
    "................",
    "##..............",
    "#B##............",
    "#BBB##..........",
    ".#BBBB##........",
    ".#BBBBBB##......",
    "..#BBBBBBB##....",
    "..#BBBBBBBBB##..",
    "..#BBBBBBBBB##..",
    "..#BBBBBBB##....",
    ".#BBBBBB##......",
    ".#BBBB##........",
    "#BBB##..........",
    "#B##............",
    "##..............",
    "................",
)


def ping_arrow(tokens):
    scale = 4
    canvas = Canvas(len(PING_ARROW[0]) * scale, len(PING_ARROW) * scale)
    colors = {"#": tokens("ash", 0), "B": tokens.text["bone"]}
    for y, row in enumerate(PING_ARROW):
        for x, cell in enumerate(row):
            if cell in colors:
                canvas.fill(x * scale, y * scale, (x + 1) * scale, (y + 1) * scale, colors[cell])
    return canvas


def jade(main, spec):
    text = spec["text"]
    palette = {
        "danger": text["danger"],
        "failure": text["danger"],
        "info": text["quiet"],
        "normal": text["bone"],
        "success": spec["ramps"]["moss"][3],
        "title": text["bone"],
        "warning": text["gold"],
    }
    theme = {
        "text": {"colors": palette, "itemAmountColor": text["bone"], "shadow": True},
        "tooltipStyle": {"padding": [5, 5, 5, 5], "sprite": "wayfarer:stamp/tooltip"},
        "version": 100,
    }
    target = main / "assets/wayfarer/jade_themes/stamp.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(theme, indent=2, sort_keys=True) + "\n")


def write(main, spec, glyphs):
    tokens = Tokens(spec)
    own = {
        "band": band(tokens, "band"),
        "book": book(tokens, "book"),
        "divider": divider(tokens, "divider", False),
        "divider_ink": divider(tokens, "divider_ink", True),
        "disc": glyph(DISC, tokens("ember", 3)),
        "keycap": keycap(tokens, "keycap"),
        "mark": glyph(MARK, tokens("ember", 3), tokens("ember", 0)),
        "mark_ink": glyph(MARK, tokens.text["ink_red"]),
        "page_left": page(tokens, "page_left", "left"),
        "page_right": page(tokens, "page_right", "right"),
        "page_scroller": page_scroller(tokens, "page_scroller", False),
        "page_scroller_track": page_scroller(tokens, "page_scroller_track", True),
        "panel": panel(tokens, "panel"),
        "panel_clear": panel(tokens, "panel_clear", alpha=tokens.alpha["clear"]),
        "ribbon": ribbon(tokens, "ribbon", False),
        "ribbon_active": ribbon(tokens, "ribbon_active", True),
        "ring": glyph(RING, WHITE),
        "ring_done": glyph(DISC, WHITE),
        "roll": glyph(ROLL, WHITE),
        "slot": (inset(tokens, "slot", 18, 18), None),
        "spine": spine(tokens, "spine"),
        "strip": strip(tokens, "strip"),
        "tooltip": tooltip(tokens, "tooltip"),
        "tooltip_story": tooltip_story(tokens, "tooltip_story"),
    }
    own |= {f"icon/{name}": glyph(rows, tokens.text["bone"]) for name, rows in ICONS.items()}
    own |= {f"compass/{name}": glyph(rows, WHITE) for name, rows in MARKS.items()}
    own["plate"] = plate_sprite(tokens, "plate")
    own |= {f"icon/{name}_ink": glyph(rows, tokens.text["ink"]) for name, rows in ICONS.items()}
    vanilla = {
        "container/slot": (inset(tokens, "container-slot", 18, 18), None),
        "container/inventory/effect_background_large": (panel(tokens, "effect-large", 120, 32)[0], None),
        "container/inventory/effect_background_small": (panel(tokens, "effect-small", 32, 32)[0], None),
        "hud/hotbar": hotbar(tokens, "hotbar"),
        "hud/hotbar_offhand_left": offhand(tokens, "offhand-left", "left"),
        "hud/hotbar_offhand_right": offhand(tokens, "offhand-right", "right"),
        "hud/hotbar_selection": selection(tokens),
        "widget/button": button(tokens, "button", "normal"),
        "widget/button_disabled": button(tokens, "button-disabled", "disabled"),
        "widget/button_highlighted": button(tokens, "button-highlighted", "highlighted"),
        "widget/cross_button": small_button(tokens, "cross", CROSS, False),
        "widget/cross_button_highlighted": small_button(tokens, "cross-lit", CROSS, True),
        "widget/locked_button": small_button(tokens, "lock", LOCK, False),
        "widget/locked_button_disabled": small_button(tokens, "lock-off", LOCK, False, True),
        "widget/locked_button_highlighted": small_button(tokens, "lock-lit", LOCK, True),
        "widget/page_backward": page_arrow(tokens, False, False),
        "widget/page_backward_highlighted": page_arrow(tokens, False, True),
        "widget/page_forward": page_arrow(tokens, True, False),
        "widget/page_forward_highlighted": page_arrow(tokens, True, True),
        "widget/scroller": scroller(tokens, "scroller", False),
        "widget/scroller_background": scroller(tokens, "scroller-background", True),
        "widget/slider": slider(tokens, "slider", False),
        "widget/slider_highlighted": slider(tokens, "slider-lit", True),
        "widget/slider_handle": handle(tokens, "handle", False),
        "widget/slider_handle_highlighted": handle(tokens, "handle-lit", True),
        "widget/text_field": text_field(tokens, "text-field", False),
        "widget/text_field_highlighted": text_field(tokens, "text-field-lit", True),
        "widget/unlocked_button": small_button(tokens, "unlock", OPEN_LOCK, False),
        "widget/unlocked_button_disabled": small_button(tokens, "unlock-off", OPEN_LOCK, False, True),
        "widget/unlocked_button_highlighted": small_button(tokens, "unlock-lit", OPEN_LOCK, True),
    }
    for selected in (False, True):
        for highlighted in (False, True):
            suffix = ("_selected" if selected else "") + ("_highlighted" if highlighted else "")
            vanilla[f"widget/tab{suffix}"] = tab(tokens, f"tab{suffix}", selected, highlighted)
            vanilla[f"widget/checkbox{suffix}"] = checkbox(tokens, f"checkbox{suffix}", selected, highlighted)
    for kind in ("advancement", "recipe", "system", "tutorial"):
        vanilla[f"toast/{kind}"] = toast(tokens, f"toast-{kind}")
    jade(main, spec)
    png.write(main / "assets/pingwheel/textures/arrow.png", ping_arrow(tokens).pixels)
    png.write(main / "assets/minecraft/textures/gui/book.png", book_screen(tokens).pixels)
    png.write(main / "assets/wayfarer/textures/gui/title/logo.png", logo(tokens, glyphs).pixels)
    for folder, sprites in ((SPRITES, own), (VANILLA, vanilla), (JEI, jei(tokens))):
        for name, (canvas, scaling) in sprites.items():
            target = main / folder / f"{name}.png"
            png.write(target, canvas.pixels)
            meta = target.with_name(f"{target.name}.mcmeta")
            if scaling:
                meta.write_text(json.dumps({"gui": {"scaling": scaling}}, indent=2, sort_keys=True) + "\n")
            else:
                meta.unlink(missing_ok=True)

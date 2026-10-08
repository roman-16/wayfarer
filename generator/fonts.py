import struct

import png

COLUMNS = 16
WHITE = (255, 255, 255, 255)
CLEAR = (0, 0, 0, 0)
ON_CURVE = 0x01
X_SHORT = 0x02
Y_SHORT = 0x04
REPEAT = 0x08
X_SAME = 0x10
Y_SAME = 0x20
ARGS_ARE_WORDS = 0x0001
MORE_COMPONENTS = 0x0020
HAS_SCALE = 0x0008
HAS_XY_SCALE = 0x0040
HAS_TWO_BY_TWO = 0x0080


class TrueType:
    def __init__(self, data):
        self.data = data
        count = struct.unpack(">H", data[4:6])[0]
        self.tables = {}
        for index in range(count):
            entry = data[12 + 16 * index : 28 + 16 * index]
            self.tables[entry[:4].decode("latin-1")] = struct.unpack(">II", entry[8:16])
        head = self.offset("head")
        self.units = struct.unpack(">H", data[head + 18 : head + 20])[0]
        self.long_offsets = struct.unpack(">h", data[head + 50 : head + 52])[0] == 1
        self.glyphs = struct.unpack(">H", data[self.offset("maxp") + 4 : self.offset("maxp") + 6])[0]
        self.metrics = struct.unpack(">H", data[self.offset("hhea") + 34 : self.offset("hhea") + 36])[0]
        self.characters = self.cmap()

    def offset(self, table):
        return self.tables[table][0]

    def advance(self, glyph):
        hmtx = self.offset("hmtx")
        index = min(glyph, self.metrics - 1)
        return struct.unpack(">H", self.data[hmtx + 4 * index : hmtx + 4 * index + 2])[0]

    def cmap(self):
        data, base = self.data, self.offset("cmap")
        count = struct.unpack(">H", data[base + 2 : base + 4])[0]
        found = {}
        for index in range(count):
            platform, encoding, start = struct.unpack(">HHI", data[base + 4 + 8 * index : base + 12 + 8 * index])
            if (platform, encoding) not in ((3, 1), (0, 3), (3, 10), (0, 4)):
                continue
            table = base + start
            kind = struct.unpack(">H", data[table : table + 2])[0]
            if kind == 4:
                found |= self.format4(table)
            elif kind == 12:
                found |= self.format12(table)
        return found

    def format4(self, table):
        data = self.data
        segments = struct.unpack(">H", data[table + 6 : table + 8])[0] // 2
        ends = table + 14
        starts = ends + 2 * segments + 2
        deltas = starts + 2 * segments
        ranges = deltas + 2 * segments
        found = {}
        for segment in range(segments):
            end = struct.unpack(">H", data[ends + 2 * segment : ends + 2 * segment + 2])[0]
            start = struct.unpack(">H", data[starts + 2 * segment : starts + 2 * segment + 2])[0]
            delta = struct.unpack(">h", data[deltas + 2 * segment : deltas + 2 * segment + 2])[0]
            shift = struct.unpack(">H", data[ranges + 2 * segment : ranges + 2 * segment + 2])[0]
            for code in range(start, end + 1):
                if code == 0xFFFF:
                    continue
                if shift == 0:
                    glyph = (code + delta) & 0xFFFF
                else:
                    at = ranges + 2 * segment + shift + 2 * (code - start)
                    glyph = struct.unpack(">H", data[at : at + 2])[0]
                    glyph = (glyph + delta) & 0xFFFF if glyph else 0
                if glyph:
                    found[code] = glyph
        return found

    def format12(self, table):
        data = self.data
        groups = struct.unpack(">I", data[table + 12 : table + 16])[0]
        found = {}
        for group in range(groups):
            start, end, glyph = struct.unpack(">III", data[table + 16 + 12 * group : table + 28 + 12 * group])
            for code in range(start, end + 1):
                found[code] = glyph + code - start
        return found

    def location(self, glyph):
        loca = self.offset("loca")
        if self.long_offsets:
            first, last = struct.unpack(">II", self.data[loca + 4 * glyph : loca + 4 * glyph + 8])
        else:
            first, last = (
                2 * value for value in struct.unpack(">HH", self.data[loca + 2 * glyph : loca + 2 * glyph + 4])
            )
        return self.offset("glyf") + first, last - first

    def contours(self, glyph, dx=0, dy=0):
        start, length = self.location(glyph)
        if length == 0:
            return []
        data = self.data
        count = struct.unpack(">h", data[start : start + 2])[0]
        if count < 0:
            return self.composite(start + 10, dx, dy)
        ends = struct.unpack(f">{count}H", data[start + 10 : start + 10 + 2 * count])
        points = ends[-1] + 1
        cursor = start + 10 + 2 * count
        cursor += 2 + struct.unpack(">H", data[cursor : cursor + 2])[0]
        flags = []
        while len(flags) < points:
            flag = data[cursor]
            cursor += 1
            flags.append(flag)
            if flag & REPEAT:
                flags.extend([flag] * data[cursor])
                cursor += 1
        xs, cursor = self.coordinates(flags, cursor, X_SHORT, X_SAME)
        ys, cursor = self.coordinates(flags, cursor, Y_SHORT, Y_SAME)
        found, first = [], 0
        for end in ends:
            found.append([(xs[i] + dx, ys[i] + dy) for i in range(first, end + 1)])
            first = end + 1
        return found

    def coordinates(self, flags, cursor, short, same):
        values, value = [], 0
        for flag in flags:
            if flag & short:
                step = self.data[cursor]
                cursor += 1
                value += step if flag & same else -step
            elif not flag & same:
                value += struct.unpack(">h", self.data[cursor : cursor + 2])[0]
                cursor += 2
            values.append(value)
        return values, cursor

    def composite(self, cursor, dx, dy):
        found = []
        while True:
            flags, glyph = struct.unpack(">HH", self.data[cursor : cursor + 4])
            cursor += 4
            if flags & ARGS_ARE_WORDS:
                x, y = struct.unpack(">hh", self.data[cursor : cursor + 4])
                cursor += 4
            else:
                x, y = struct.unpack(">bb", self.data[cursor : cursor + 2])
                cursor += 2
            cursor += 2 if flags & HAS_SCALE else 4 if flags & HAS_XY_SCALE else 8 if flags & HAS_TWO_BY_TWO else 0
            found += self.contours(glyph, dx + x, dy + y)
            if not flags & MORE_COMPONENTS:
                return found


def inside(contours, x, y):
    crossings = 0
    for contour in contours:
        for (x1, y1), (x2, y2) in zip(contour, contour[1:] + contour[:1], strict=True):
            if (y1 > y) != (y2 > y) and x < x1 + (y - y1) * (x2 - x1) / (y2 - y1):
                crossings += 1
    return crossings % 2 == 1


def glyph_pixels(font, glyph, unit, top, bottom):
    contours = [[(x / unit, y / unit) for x, y in contour] for contour in font.contours(glyph)]
    if not contours:
        return []
    right = max(x for contour in contours for x, _ in contour)
    width = max(1, round(right))
    return [[inside(contours, x + 0.5, y - 0.5) for x in range(width)] for y in range(top, bottom, -1)]


def glyphs(spec, font):
    unit = font.units // spec["size"]
    codes = (
        code
        for code in font.characters
        if any(first <= code <= last for first, last in spec["ranges"]) and code != ord(" ")
    )
    shapes = {code: glyph_pixels(font, font.characters[code], unit, spec["ascent"], -spec["descent"]) for code in codes}
    return {code: shape for code, shape in shapes.items() if any(any(row) for row in shape)}


def bitmap(spec, data):
    font = TrueType(data)
    unit = font.units // spec["size"]
    height = spec["ascent"] + spec["descent"]
    shapes = glyphs(spec, font)
    codes = sorted(shapes)
    cell = max(len(shape[0]) for shape in shapes.values())
    rows = [codes[index : index + COLUMNS] for index in range(0, len(codes), COLUMNS)]
    pixels = [[CLEAR] * (cell * COLUMNS) for _ in range(height * len(rows))]
    for row, line in enumerate(rows):
        for column, code in enumerate(line):
            for y, filled in enumerate(shapes[code]):
                for x, on in enumerate(filled):
                    if on:
                        pixels[row * height + y][column * cell + x] = WHITE
    chars = ["".join(chr(code) for code in line).ljust(COLUMNS, "\u0000") for line in rows]
    space = font.advance(font.characters.get(ord(" "), 0)) // unit
    return pixels, chars, height, space


def write(main, spec, root, write_json):
    for name, face in spec.items():
        pixels, chars, height, space = bitmap(face, (root / face["file"]).read_bytes())
        png.write(main / f"assets/wayfarer/textures/font/{name}.png", pixels)
        providers = [
            {"advances": {" ": space}, "type": "space"},
            {
                "ascent": face["ascent"],
                "chars": chars,
                "file": f"wayfarer:font/{name}.png",
                "height": height,
                "type": "bitmap",
            },
        ]
        target = main / (
            "assets/minecraft/font/default.json" if face.get("default") else f"assets/wayfarer/font/{name}.json"
        )
        fallback = (
            [
                {"filter": {"uniform": False}, "id": "minecraft:include/default", "type": "reference"},
                {"id": "minecraft:include/unifont", "type": "reference"},
            ]
            if face.get("default")
            else [{"id": "minecraft:default", "type": "reference"}]
        )
        write_json(target, {"providers": providers + fallback})

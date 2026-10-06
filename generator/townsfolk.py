from blocks import noise

TONES = [
    (["#ae9584", "#bea493", "#c8b09e"], "#9a8474"),
    (["#a88a74", "#b89a84", "#c4a690"], "#94786a"),
    (["#9c7a62", "#ac8a70", "#b8967c"], "#886a54"),
    (["#8a6a52", "#9a7860", "#a6846c"], "#76584a"),
    (["#74563f", "#82624a", "#8e6e56"], "#62483a"),
    (["#5e4434", "#6a4e3c", "#765846"], "#503a2c"),
]
HAIRS = [
    ["#1e1a18", "#28221e", "#322a26"],
    ["#3a281c", "#4a3424", "#5a402e"],
    ["#5a4030", "#6c503a", "#7e6046"],
    ["#6a3422", "#7e4230", "#90503a"],
    ["#8a4420", "#ac5e2a", "#c0743a"],
    ["#a6863c", "#c4a456", "#d6b86c"],
]
GREYS = [
    ["#7a7a76", "#94948e", "#acaca6"],
    ["#b0aca4", "#c8c4bc", "#dcd8d0"],
]
CLOTHS = [
    ["#4a5a70", "#586a84", "#667894"],
    ["#5a4632", "#6a543e", "#7a624a"],
    ["#3e5232", "#4a643c", "#587448"],
    ["#8a6a2e", "#a07e3a", "#b08e48"],
    ["#62301f", "#743a2c", "#844838"],
    ["#4a4a4a", "#585858", "#666666"],
    ["#a89e88", "#bcb29c", "#ccc2ac"],
    ["#2e3450", "#3a4264", "#465076"],
    ["#545a34", "#646a40", "#72784c"],
    ["#7a4a2a", "#8c5832", "#9c663c"],
    ["#3e4652", "#4b5563", "#5a6474"],
    ["#2a4a4a", "#345858", "#3e6666"],
]
TROUSERS = [
    ["#3a3026", "#4a3e32", "#584a3c"],
    ["#4a3a2a", "#584634", "#66543e"],
    ["#3a3a3a", "#464646", "#525252"],
    ["#4a4430", "#58523a", "#666044"],
]
BOOTS = ["#211a14", "#2a2119", "#34291f"]
WOUNDS = [
    ["clOc", "Xxlc", "lxXc", "cxlc"],
    ["clcc", "lxXc", "Xxlc", "cXcc"],
    ["ccOc", "clxl", "cXxc", "clXc"],
]
SOOT = ["#2a2420", "#342c26", "#3e352d"]
DUST = ["#6e6252", "#7c705e", "#8a7e6a"]
MUD = ["#4a3c2c", "#56463a", "#625040"]
CHAR = "#1a1410"
STAINS = [
    ["zz.", ".zz"],
    ["z.z", "zzz", ".z."],
    ["zz", "zz", ".z"],
    ["z..", "zz.", ".zz"],
    [".z", "zz", "z."],
]
RAISED = {"arm": "ODRqr", "body": "ADORaklpry", "head": "Hgh", "leg": "BbDq"}
KINDS = ["man"] * 10 + ["woman"] * 10 + ["old_man"] * 4 + ["old_woman"] * 4 + ["boy"] * 2 + ["girl"] * 2
ROAD = ["man"] * 5 + ["woman"] * 4 + ["old_man"] * 2 + ["old_woman"]
ROAD_DEAD = ["man", "woman"]
CHILD = 0.75


def darker(color, amount=0.82):
    return "#" + "".join(f"{int(int(color[i : i + 2], 16) * amount):02x}" for i in (1, 3, 5))


def pick(seed, options):
    return options[int(noise(seed) * len(options)) % len(options)]


def head(style, eyes, beard):
    rows = {
        "short": ["hhhhhhhh", "hhhhhhhh", "hssssssh", "hHHssHHh", f"s{eyes}s", "ssssssss", "sssmmsss", "ssssssss"],
        "long": ["hhhhhhhh", "hhhhhhhh", "hssssssh", "hHHssHHh", f"h{eyes}h", "hssssssh", "hssmmssh", "hssssssh"],
        "bald": ["ssssssss", "ssssssss", "ssssssss", "sHHssHHs", f"s{eyes}s", "ssssssss", "sssmmsss", "ssssssss"],
        "messy": ["hhhhhhhh", "hhhhhhhh", "hssshssh", "sHHssHHs", f"s{eyes}s", "ssssssss", "sssmmsss", "ssssssss"],
    }[style]
    side = {
        "short": ["hhhhhhhh", "hhhhhhhh", "hhhhhhss", "hhhhssss", "hhhSSsss", "hhhSssss", "hhssssss", "hsssssss"],
        "long": ["hhhhhhhh", "hhhhhhhh", "hhhhhhss", "hhhhhhss", "hhhhhhss", "hhhhhhss", "hhhhhhss", "hhhhhhss"],
        "bald": ["ssssssss", "ssssssss", "ssssssss", "hhhhssss", "hhhSSsss", "hhhSssss", "sshsssss", "ssssssss"],
        "messy": ["hhhhhhhh", "hhhhhhhh", "hhhhhhhs", "hhhhhsss", "hhhSSsss", "hhhSssss", "hhhsssss", "hhhsssss"],
    }[style]
    back = ["ssssssss"] * 3 + ["shhhhhhs"] + ["hhhhhhhh"] * 4 if style == "bald" else ["hhhhhhhh"] * 8
    top = ["ssssssss"] * 8 if style == "bald" else ["hhhhhhhh"] * 8
    if beard:
        rows = rows[:5] + ["gssSSssg", "gggmmggg", "gggggggg"]
        side = side[:5] + ["hhhssggg", "hhhsgggg", "hhgggggg"]
    spec = {"head_back": back, "head_front": rows, "head_side": side, "head_top": top}
    if beard:
        spec["head_bottom"] = ["gggggggg"] * 4 + ["ssssssss"] * 4
    return spec


def shirt(wound, purse):
    w = ["oooo"] * 4 if wound is None else [row.replace("c", "o") for row in WOUNDS[wound]]
    hip = "ttttttpt" if purse else "tttttttt"
    return {
        "arm": ["oooo"] * 10 + ["OOOO", "ssss"],
        "body_back": ["oooooooo"] * 7 + ["kkkkkkkk"] + ["tttttttt"] * 4,
        "body_front": ["ooossooo", "oooooooo"]
        + [f"oooo{row}" for row in w]
        + ["oooooooo", "kkkkykkk", hip, hip, "tttttttt", "tttttttt"],
        "body_side": ["oooo"] * 7 + ["kkkk"] + ["tttt"] * 4,
        "leg": ["tttt"] * 9 + ["bbbb", "BBBB", "BBBB"],
    }


def dress(wound, apron, shawl, ring):
    w = ["dddd"] * 4 if wound is None else [row.replace("c", "d").replace("O", "D") for row in WOUNDS[wound]]
    front = (
        ["dddssddd", "dddddddd"]
        + [f"dddd{row}" for row in w]
        + ["dddddddd", "dDDDDDDd", "dddddddd", "dddDdddd", "dddddDdd", "dddddddd"]
    )
    back = ["dddddddd"] * 7 + ["dDDDDDDd"] + ["dddddddd"] * 4
    side = ["dddd"] * 7 + ["DDDD"] + ["dddd"] * 4
    arm = ["ddd"] * 10 + ["DDD", "sss"]
    arm_side = ["dddd"] * 10 + ["DDDD", "ssss"]
    if apron:
        front = [row[:2] + "aaaa" + row[6:] if 8 <= i <= 11 else row for i, row in enumerate(front)]
        front[7] = "AAAAAAAA"
    if shawl:
        draped = [f"rrrr{row.replace('d', 'r').replace('D', 'R')}" for row in w[:2]]
        front = ["rrrssrrr", "rrrrrrrr"] + draped + front[4:]
        back = ["rrrrrrrr"] * 5 + ["RrrrrrrR"] + back[6:]
        side = ["rrrr"] * 5 + side[5:]
        arm = ["rrr"] * 5 + ["RRR"] + arm[6:]
        arm_side = ["rrrr"] * 5 + ["RRRR"] + arm_side[6:]
    spec = {
        "arm": arm,
        "arm_side": arm_side,
        "body_back": back,
        "body_front": front,
        "body_side": side,
        "leg": ["dddd"] * 6 + ["ddDd"] + ["dddd"] * 4 + ["BBBB"],
    }
    if ring:
        spec["arm_front"] = arm[:11] + ["ssy"]
    return spec


def chance(seed, share):
    return noise(seed) < share


def paint(rows, x, y, letter, cloth):
    if 0 <= y < len(rows) and 0 <= x < len(rows[y]) and rows[y][x] in cloth:
        rows[y] = rows[y][:x] + letter + rows[y][x + 1 :]


def ragged(rows, bottom, deepest, letter, cloth, seed):
    for x in range(len(rows[0])):
        for y in range(bottom, bottom - 1 - int(noise(f"{seed}/{x}") * deepest), -1):
            paint(rows, x, y, letter, cloth)


def side(spec, part, limb):
    for key in [key for key in spec if key == limb or key.startswith(limb + "_")]:
        spec[part + key.removeprefix(limb)] = list(spec[key])
    return [key for key in spec if key == part or key.startswith(part + "_")]


def wear(spec, seed, cloth, dress, opened):
    hand = pick(f"{seed}/hand", ["right", "left"])
    other = "left" if hand == "right" else "right"
    hem = 10 if dress else 8
    if chance(f"{seed}/hem", 0.5):
        for key in [key for key in spec if key == "leg" or key.startswith("leg_")]:
            spec[key] = list(spec[key])
            ragged(spec[key], hem, 3, "q" if dress else "z", cloth, f"{seed}/hem/{key}")
    if chance(f"{seed}/mud", 0.5):
        for key in [key for key in spec if key == "leg" or key.startswith("leg_")]:
            spec[key] = list(spec[key])
            ragged(spec[key], hem - 1, 2, "w", cloth, f"{seed}/mud/{key}")
    if chance(f"{seed}/sleeve", 0.4):
        for key in side(spec, f"{hand}_arm", "arm"):
            ragged(spec[key], 10, 4, "s", cloth, f"{seed}/sleeve/{key}")
    elif chance(f"{seed}/cuff", 0.5):
        for key in side(spec, f"{hand}_arm", "arm"):
            ragged(spec[key], 10, 2, "q", cloth, f"{seed}/cuff/{key}")
    if chance(f"{seed}/rip", 0.35):
        for key in side(spec, f"{other}_leg", "leg"):
            column = 1 + int(noise(f"{seed}/rip/x") * 2)
            for y in range(2, 5):
                paint(spec[key], column, y, "s", cloth)
    if chance(f"{seed}/barefoot", 0.15):
        for key in side(spec, f"{other}_leg", "leg"):
            spec[key] = [row.replace("B", "s").replace("b", "s") for row in spec[key]]
    targets = ["body_back", "body_front", f"{hand}_leg", f"{other}_arm", "body_side"]
    for stain in range(1 + int(noise(f"{seed}/stains") * 3)):
        target = pick(f"{seed}/stain/{stain}/at", targets)
        base = target.split("_", 1)[1] if target.split("_", 1)[0] in ("left", "right") else None
        keys = side(spec, target, base) if base and target not in spec else [target] if target in spec else []
        for key in keys[:1]:
            rows = list(spec[key])
            shape = pick(f"{seed}/stain/{stain}/shape", STAINS)
            x0 = int(noise(f"{seed}/stain/{stain}/x") * max(1, len(rows[0]) - len(shape[0]) + 1))
            y0 = int(noise(f"{seed}/stain/{stain}/y") * max(1, len(rows) - len(shape) - 1))
            for dy, line in enumerate(shape):
                for dx, mark in enumerate(line):
                    wound = key == "body_front" and 4 <= x0 + dx <= 7 and 2 <= y0 + dy <= 5
                    if mark == "z" and not wound:
                        paint(rows, x0 + dx, y0 + dy, "z", cloth)
            spec[key] = rows
    if opened and chance(f"{seed}/bleed", 0.4):
        rows = list(spec["body_front"])
        column = 5 + int(noise(f"{seed}/bleed/x") * 2)
        for y in range(6, 8 + int(noise(f"{seed}/bleed/y") * 2)):
            paint(rows, column, y, "x", cloth | {"k", "y"})
        spec["body_front"] = rows


def person(seed, kind, fate):
    tone, shadow = pick(f"{seed}/tone", TONES)
    old = kind.startswith("old")
    hair = pick(f"{seed}/hair", GREYS if old else HAIRS)
    cloth = pick(f"{seed}/cloth", CLOTHS)
    trousers = pick(f"{seed}/trousers", TROUSERS)
    wound = int(noise(f"{seed}/wound") * len(WOUNDS)) % len(WOUNDS) if fate != "living" else None
    woman = kind in ("woman", "old_woman", "girl")
    if kind in ("man", "old_man"):
        style = pick(f"{seed}/style", ["bald", "short", "short"] if old else ["short", "short", "messy", "long"])
        beard = noise(f"{seed}/beard") > (0.3 if old else 0.55)
    elif kind == "boy":
        style, beard = "messy", False
    else:
        style, beard = pick(f"{seed}/style", ["long", "long", "short"] if kind != "girl" else ["long", "messy"]), False
    spec = head(style, "EEssEE", beard)
    if woman:
        spec |= dress(
            wound, noise(f"{seed}/apron") > 0.6 and kind != "girl", kind == "old_woman", noise(f"{seed}/ring") > 0.5
        )
    else:
        spec |= shirt(wound, noise(f"{seed}/purse") > 0.4)
    light = cloth[2]
    palette = {
        "B": BOOTS,
        "E": darker(shadow, 0.9),
        "H": darker(hair[0]),
        "S": shadow,
        "X": "#5c2620",
        "b": "#4a3a2c",
        "h": hair,
        "k": "#3a2a1c",
        "l": darker(light, 1.15) if int(light[1:3], 16) < 0xD0 else light,
        "m": darker(shadow, 0.8),
        "p": ["#5e4024", "#70502e", "#80603a"],
        "s": tone,
        "x": "#3a1612",
        "y": "#c8a848" if woman else "#8c8a80",
    }
    if beard:
        palette["g"] = hair
    if woman:
        palette |= {"D": darker(cloth[0]), "a": ["#bcb29c", "#d4ccb8", "#e2dbc8"], "A": "#a69c86", "d": cloth}
        if kind == "old_woman":
            shawl = pick(f"{seed}/shawl", CLOTHS)
            palette |= {"R": darker(shawl[0]), "r": shawl}
    else:
        palette |= {"O": darker(cloth[0]), "o": cloth, "t": trousers}
    wear(spec, seed, {"d", "D", "a", "A", "r", "R"} if woman else {"o", "O", "t"}, woman, fate != "living")
    burned = fate == "burned"
    palette |= {"q": CHAR if burned else MUD[0], "w": MUD, "z": SOOT if burned else DUST}
    used = {letter for key, rows in spec.items() for row in rows for letter in row} - {"."}
    child = kind in ("boy", "girl")
    return spec | {
        "model": "humanoid_slim" if woman else "humanoid",
        "raised": RAISED,
        "palette": {letter: palette[letter] for letter in sorted(used)},
        "scale": CHILD if child else 1.0,
    }


def folk(prefix, kinds, fate):
    people = {}
    seen = set()
    for index, kind in enumerate(kinds, start=1):
        attempt = 0
        while True:
            look = person(f"{prefix}/{index}/{attempt}", kind, fate)
            key = repr(sorted((k, repr(v)) for k, v in look.items()))
            if key not in seen:
                break
            attempt += 1
        seen.add(key)
        people[f"{prefix}_{index:02d}"] = look
    return people


def townsfolk():
    return folk("townsfolk", KINDS, "burned")


def roadfolk():
    return folk("road", ROAD, "living") | folk("road_dead", ROAD_DEAD, "opened")

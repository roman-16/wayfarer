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
FURS = [
    ["#8a7a66", "#a29280", "#b8a896"],
    ["#5e4a38", "#6e5844", "#7e6650"],
    ["#a8aca8", "#c0c4c0", "#d4d8d4"],
    ["#6a6a64", "#7c7c76", "#8e8e88"],
    ["#4e3e30", "#5c4a3a", "#6a5644"],
    ["#9a8a70", "#b0a088", "#c4b49c"],
]
HIDES = [
    ["#4a3424", "#5a402e", "#6a4c38"],
    ["#3e3226", "#4c3e30", "#5a4a3a"],
]
RIME = ["#b8c8d0", "#d0dee6", "#e8f0f4"]
IRISES = ["#3e5a78", "#4a3020", "#4a6a3a", "#5a4632", "#5a6268"]
WHITE_OF_EYE = "#e4dcd2"
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
FOUNDERS = ["man", "man", "woman", "old_man", "girl"]
CHILD = 0.75


def darker(color, amount=0.82):
    return "#" + "".join(f"{int(int(color[i : i + 2], 16) * amount):02x}" for i in (1, 3, 5))


def pick(seed, options):
    return options[int(noise(seed) * len(options)) % len(options)]


def eyes(fate):
    return "WEssEW" if fate == "living" else "EEssEE"


def iris(seed, fate, shadow):
    return pick(f"{seed}/eyes", IRISES) if fate == "living" else darker(shadow, 0.9)


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


def person(seed, kind, fate, cloths=CLOTHS, legs=TROUSERS):
    tone, shadow = pick(f"{seed}/tone", TONES)
    old = kind.startswith("old")
    hair = pick(f"{seed}/hair", GREYS if old else HAIRS)
    cloth = pick(f"{seed}/cloth", cloths)
    trousers = pick(f"{seed}/trousers", legs)
    wound = int(noise(f"{seed}/wound") * len(WOUNDS)) % len(WOUNDS) if fate != "living" else None
    woman = kind in ("woman", "old_woman", "girl")
    if kind in ("man", "old_man"):
        style = pick(f"{seed}/style", ["bald", "short", "short"] if old else ["short", "short", "messy", "long"])
        beard = noise(f"{seed}/beard") > (0.3 if old else 0.55)
    elif kind == "boy":
        style, beard = "messy", False
    else:
        style, beard = pick(f"{seed}/style", ["long", "long", "short"] if kind != "girl" else ["long", "messy"]), False
    spec = head(style, eyes(fate), beard)
    if woman:
        spec |= dress(
            wound, noise(f"{seed}/apron") > 0.6 and kind != "girl", kind == "old_woman", noise(f"{seed}/ring") > 0.5
        )
    else:
        spec |= shirt(wound, noise(f"{seed}/purse") > 0.4)
    light = cloth[2]
    palette = {
        "B": BOOTS,
        "E": iris(seed, fate, shadow),
        "H": darker(hair[0]),
        "S": shadow,
        "W": WHITE_OF_EYE,
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
            shawl = pick(f"{seed}/shawl", cloths)
            palette |= {"R": darker(shawl[0]), "r": shawl}
    else:
        palette |= {"O": darker(cloth[0]), "o": cloth, "t": trousers}
    wear(spec, seed, {"d", "D", "a", "A", "r", "R"} if woman else {"o", "O", "t"}, woman, fate != "living")
    burned = fate == "burned"
    stain = SOOT if burned else RIME if fate == "frozen" else DUST
    palette |= {"q": CHAR if burned else MUD[0], "w": MUD, "z": stain}
    used = {letter for key, rows in spec.items() for row in rows for letter in row} - {"."}
    child = kind in ("boy", "girl")
    return spec | {
        "model": "humanoid_slim" if woman else "humanoid",
        "raised": RAISED,
        "palette": {letter: palette[letter] for letter in sorted(used)},
        "scale": CHILD if child else 1.0,
    }


def folk(prefix, kinds, fate, cloths=CLOTHS, legs=TROUSERS):
    people = {}
    seen = set()
    for index, kind in enumerate(kinds, start=1):
        attempt = 0
        while True:
            look = person(f"{prefix}/{index}/{attempt}", kind, fate, cloths, legs)
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


def founders():
    return folk("founder", FOUNDERS, "living")


TRIMS = [
    ["#c8c2b4", "#dcd6ca", "#ece8de"],
    ["#b4ac9c", "#c8c0b0", "#dad4c6"],
    ["#d8d8d4", "#e8e8e4", "#f4f4f2"],
]
WALKER_RAISED = {"arm": "fFuM", "body": "fFuky", "head": "Hghfu", "leg": "fFuvB"}
FROST = ["man", "woman", "old_man", "woman"]
FROST_DEAD = ["man"] * 4 + ["woman"] * 4 + ["old_man"] * 2 + ["old_woman"] * 2


def hood(eyes, beard):
    face = ["uuuuuuuu", "uhhhhhhu", "ussssssu", "uHHssHHu", f"u{eyes}u", "ussssssu", "ussmmssu", "uussssuu"]
    if beard:
        face = face[:5] + ["ugsSSsgu", "uggmmggu", "uuggggu" + "u"]
    return {
        "head_back": ["ffffffff", "ffffffff", "fffFffff", "ffffffff", "ffffffff", "ffffFfff", "ffffffff", "ffffffff"],
        "head_front": face,
        "head_side": ["fffffffu", "fffffffu", "ffFffffu", "fffffffu", "ffffffuu", "fffffffu", "fffFfffu", "ffffffuu"],
        "head_top": ["ffffffff", "ffffffff", "fffFffff", "ffffffff", "ffffffff", "ffffffff", "ffffFfff", "ffffffff"],
        "head_bottom": ["uuuuuuuu", "ussssssu", "ssssssss", "ssssssss", "ssssssss", "ssssssss", "ussssssu", "uuuuuuuu"],
    }


def parka(wound, slim, quiver):
    w = ["ffff"] * 4 if wound is None else [row.replace("c", "f").replace("O", "F") for row in WOUNDS[wound]]
    front = (
        ["uuussuuu", "ufffFffu"]
        + [f"fff{row}f"[:3] + row + "f" for row in w]
        + ["fffFffff", "kkkkykkk", "fffFffff", "fffFffff", "uuuuuuuu", "UUUUUUUU"]
    )
    back = ["uuuuuuuu"] + ["ffffffff"] * 6 + ["kkkkkkkk", "ffffffff", "ffffffff", "uuuuuuuu", "UUUUUUUU"]
    if quiver:
        front = [row[: 7 - i] + "k" + row[8 - i :] if 1 <= i <= 7 else row for i, row in enumerate(front)]
        back = [row[:1] + "pp" + row[3:] if 0 <= i <= 6 else row for i, row in enumerate(back)]
        back[0] = "uPPuuuuu"
    width = 3 if slim else 4
    arm = ["f" * width] * 8 + ["u" * width] * 2 + ["M" * width] * 2
    spec = {
        "arm": arm,
        "body_back": back,
        "body_front": front,
        "body_side": ["uuuu"] + ["ffff"] * 6 + ["kkkk", "ffff", "ffff", "uuuu", "UUUU"],
        "leg": ["ffff", "ffff", "uuuu", "tttt", "tttt", "tttt", "tttt", "tttt", "vvvv", "BBBB", "BBBB", "BBBB"],
    }
    if slim:
        spec["arm_side"] = ["ffff"] * 8 + ["uuuu"] * 2 + ["MMMM"] * 2
    return spec


def walker(seed, kind, fate, hooded=True, quiver=False, look=None):
    old = kind.startswith("old")
    look = look or {}
    tone, shadow = look.get("tone") or pick(f"{seed}/tone", TONES)
    hair = look.get("hair") or pick(f"{seed}/hair", GREYS if old else HAIRS)
    fur = look.get("fur") or pick(f"{seed}/fur", FURS)
    trim = look.get("trim") or pick(f"{seed}/trim", TRIMS)
    hide = look.get("hide") or pick(f"{seed}/hide", HIDES)
    woman = kind in ("woman", "old_woman", "girl")
    beard = not woman and noise(f"{seed}/beard") > (0.3 if old else 0.5)
    wound = int(noise(f"{seed}/wound") * len(WOUNDS)) % len(WOUNDS) if fate != "living" else None
    if hooded:
        spec = hood(eyes(fate), beard)
    else:
        spec = head("long" if woman else pick(f"{seed}/style", ["short", "messy"]), eyes(fate), beard)
    spec |= parka(wound, woman, quiver)
    palette = {
        "B": BOOTS,
        "E": iris(seed, fate, shadow),
        "F": darker(fur[0]),
        "H": darker(hair[0]),
        "M": darker(hide[0], 0.9),
        "P": "#c8b89c",
        "S": shadow,
        "W": WHITE_OF_EYE,
        "U": darker(trim[0], 0.86),
        "X": "#5c2620",
        "f": fur,
        "h": hair,
        "k": "#3a2a1c",
        "l": fur[2],
        "m": darker(shadow, 0.8),
        "p": ["#5e4024", "#70502e", "#80603a"],
        "s": tone,
        "t": hide,
        "u": trim,
        "v": trim,
        "x": "#3a1612",
        "y": "#d8d0bc",
    }
    if beard:
        palette["g"] = hair
    wear(spec, seed, {"f", "F", "u", "U"}, False, fate != "living")
    palette |= {"q": MUD[0], "w": MUD, "z": RIME if fate == "frozen" else DUST}
    used = {letter for rows in spec.values() for row in rows for letter in row} - {"."}
    return spec | {
        "model": "humanoid_slim" if woman else "humanoid",
        "raised": WALKER_RAISED,
        "palette": {letter: palette[letter] for letter in sorted(used)},
        "scale": 1.0,
    }


def walkers(prefix, kinds, fate):
    return {
        f"{prefix}_{index:02d}": walker(f"{prefix}/{index}", kind, fate, noise(f"{prefix}/{index}/hood") > 0.3)
        for index, kind in enumerate(kinds, start=1)
    }


def frostfolk():
    return (
        walkers("frost", FROST, "living")
        | walkers("frost_dead", FROST_DEAD, "frozen")
        | {
            "drifa": walker(
                "frost/drifa",
                "woman",
                "living",
                hooded=False,
                quiver=True,
                look={"fur": FURS[1], "hair": HAIRS[0], "hide": HIDES[0], "tone": TONES[1], "trim": TRIMS[0]},
            ),
            "frost_elder": walker("frost/elder", "old_woman", "living"),
        }
    )


WOOL = ["#6a665c", "#7a766a", "#8a867a"]
KEEPER_HOOD = ["#5e5a52", "#6c685e", "#7a766c"]
APPRENTICE_HOOD = ["#3e4a5a", "#4a586a", "#566678"]
ROPE = "#8a7650"
KNOT = "#a08a60"
INK = "#232838"
KEEPER_RAISED = {"arm": "D", "body": "RkyPp", "head": "Hgh", "leg": "D"}


def robe(wound, slim, satchel, inked):
    w = ["dddd"] * 4 if wound is None else [row.replace("c", "d").replace("O", "D") for row in WOUNDS[wound]]
    front = (
        ["RRRssRRR", "dRddddRd"]
        + [f"dddd{row}" for row in w]
        + ["dddddddd", "kkkkykkk", "dddDdddd", "ddDddddd", "dddddDdd", "dDdddddd"]
    )
    back = (
        ["RRRRRRRR", "RRRRRRRR", "dRRRRRRd"]
        + ["dddddddd"] * 4
        + ["kkkkkkkk"]
        + ["dddDdddd", "ddddDddd", "dDdddddd", "ddddddDd"]
    )
    side = ["RRRR", "dddd"] + ["dddd"] * 5 + ["kkkk"] + ["dddd", "dDdd", "dddd", "ddDd"]
    if satchel:
        for y in range(9):
            x = min(7, 1 + y * 6 // 8)
            front[y] = front[y][:x] + "p" + front[y][x + 1 :]
        side = side[:7] + ["kPPk"[:4], "pPPp", "pppp", "pppp", "dddd"]
    width = 3 if slim else 4
    hand = "s" * width
    inked_hand = ("s" + "i" * (width - 2) + "s") if inked else hand
    spec = {
        "body_back": back,
        "body_front": front,
        "body_side": side,
        "left_arm": ["d" * width] * 10 + ["D" * width, hand],
        "leg": ["dddd"] * 7 + ["DDDD"] + ["tttt"] * 2 + ["BBBB"] * 2,
        "right_arm": ["d" * width] * 10 + ["D" * width, inked_hand],
    }
    if slim:
        spec["left_arm_side"] = ["dddd"] * 10 + ["DDDD", "ssss"]
        spec["right_arm_side"] = ["dddd"] * 10 + ["DDDD", "ssss"]
    return spec


def keeper(seed, kind, fate, look):
    old = kind.startswith("old")
    woman = kind in ("woman", "old_woman")
    tone, shadow = look["tone"]
    hair = look["hair"]
    beard = look.get("beard", False)
    wound = int(noise(f"{seed}/wound") * len(WOUNDS)) % len(WOUNDS) if fate != "living" else None
    spec = head(look["style"], eyes(fate), beard)
    spec |= robe(wound, woman, look.get("satchel", False), True)
    palette = {
        "B": BOOTS,
        "D": darker(WOOL[0], 0.86),
        "E": iris(seed, fate, shadow),
        "H": darker(hair[0]),
        "P": darker("#70502e", 0.8),
        "R": look["hood"],
        "S": shadow,
        "W": WHITE_OF_EYE,
        "X": "#5c2620",
        "d": WOOL,
        "h": hair,
        "i": INK,
        "k": ROPE,
        "l": WOOL[2],
        "m": darker(shadow, 0.8),
        "p": ["#5e4024", "#70502e", "#80603a"],
        "s": tone,
        "t": TROUSERS[2],
        "x": "#3a1612",
        "y": KNOT,
    }
    if beard:
        palette["g"] = hair
    wear(spec, seed, {"d", "D", "R"}, False, fate != "living")
    palette |= {"q": CHAR, "w": SOOT, "z": SOOT}
    used = {letter for rows in spec.values() for row in rows for letter in row} - {"."}
    return spec | {
        "model": "humanoid_slim" if woman else "humanoid",
        "raised": KEEPER_RAISED,
        "palette": {letter: palette[letter] for letter in sorted(used)},
        "scale": 0.95 if old else 1.0,
    }


def keeperfolk():
    return {
        "alruna": keeper(
            "keepers/alruna",
            "woman",
            "living",
            {"hair": HAIRS[3], "hood": APPRENTICE_HOOD, "satchel": True, "style": "long", "tone": TONES[0]},
        ),
        "keeper_ansel": keeper(
            "keepers/ansel",
            "old_man",
            "opened",
            {"beard": True, "hair": GREYS[1], "hood": KEEPER_HOOD, "style": "bald", "tone": TONES[2]},
        ),
    }


LEATHER = ["#4e3626", "#5e4230", "#6e4e3a"]
TAR = ["#1c1a18", "#24211e", "#2c2924"]
ROLLED = 5


def shipwright(look):
    tone, shadow = look["tone"]
    cloth = look["cloth"]
    spec = head(look["style"], eyes("living"), False) | dress(None, True, False, False)
    for key in ("arm", "arm_side"):
        spec[key] = spec[key][:ROLLED] + ["s" * len(spec[key][0])] * (len(spec[key]) - ROLLED)
    front = list(spec["body_front"])
    for x, y in ((3, 9), (4, 10), (2, 11), (5, 8)):
        paint(front, x, y, "z", {"a"})
    spec["body_front"] = front
    palette = {
        "A": darker(LEATHER[0]),
        "B": BOOTS,
        "D": darker(cloth[0]),
        "E": IRISES[1],
        "H": darker(look["hair"][0]),
        "S": shadow,
        "W": WHITE_OF_EYE,
        "a": LEATHER,
        "d": cloth,
        "h": look["hair"],
        "m": darker(shadow, 0.8),
        "s": tone,
        "z": TAR,
    }
    used = {letter for rows in spec.values() for row in rows for letter in row} - {"."}
    return spec | {
        "model": "humanoid_slim",
        "raised": RAISED,
        "palette": {letter: palette[letter] for letter in sorted(used)},
        "scale": 1.0,
    }


def coastfolk():
    return {"edda": shipwright({"cloth": CLOTHS[10], "hair": HAIRS[2], "style": "long", "tone": TONES[2]})}


TIDE_CLOTHS = [CLOTHS[0], CLOTHS[7], CLOTHS[10], CLOTHS[11]]


def tidefolk():
    return {
        "murdo": person("tide/murdo", "old_man", "living", cloths=[CLOTHS[11]]),
        "tide_warden_1": person("tide/warden/1", "man", "living", cloths=[CLOTHS[7]]),
        "tide_warden_2": person("tide/warden/2", "woman", "living", cloths=[CLOTHS[10]]),
    }


SAND_CLOTHS = [["#c89a5a", "#d8aa6a", "#e4b87a"], ["#a8643a", "#b8744a", "#c4825a"], ["#e0d6c0", "#ece2cc", "#f4ecd8"]]


def sandfolk():
    return {"sefa": person("sand/sefa", "woman", "living", cloths=[SAND_CLOTHS[1]])} | folk(
        "sand", ["man", "woman", "old_man"], "living", SAND_CLOTHS
    )


HEIR = {
    "B": ["#8a6a22", "#a07e2e", "#b4923a"],
    "G": ["#2e3033", "#383a3e", "#43464a"],
    "R": ["#2e3033", "#383a3e", "#43464a"],
    "S": ["#b8902e", "#d0a63c", "#e2bc52"],
    "W": ["#a07e2e", "#b4923a", "#c8a648"],
}


def heir_of(cadoc):
    return cadoc | {"palette": cadoc["palette"] | HEIR}


BROTHER = {
    "B": ["#4a4d4f", "#55595b", "#62666a"],
    "R": ["#4a4d4f", "#55595b", "#62666a"],
    "h": ["#5a4630", "#6a5438", "#7a6242"],
}


def brother_of(cadoc):
    return cadoc | {"palette": cadoc["palette"] | BROTHER}


def errandfolk():
    return {
        "bram": person("errands/bram", "man", "living", cloths=[CLOTHS[1]]),
        "bram_son": person("errands/bram_son", "boy", "living", cloths=[CLOTHS[5]]),
        "cecily": person("errands/cecily", "girl", "living"),
        "novice": person("errands/novice", "boy", "living", cloths=[CLOTHS[5]]),
        "wil": person("errands/wil", "old_man", "living"),
    }


def act3folk():
    return {
        "captive_1": person("act3/captive/1", "girl", "living"),
        "captive_2": person("act3/captive/2", "boy", "living"),
        "captive_3": person("act3/captive/3", "girl", "living"),
        "oldest_keeper": keeper(
            "act3/oldest_keeper",
            "old_man",
            "living",
            {"beard": True, "hair": GREYS[0], "hood": KEEPER_HOOD, "style": "long", "tone": TONES[1]},
        ),
        "pilgrim": person("act3/pilgrim", "old_woman", "living"),
        "settled_wayfarer": person("act2/settled_wayfarer", "old_man", "living"),
    }

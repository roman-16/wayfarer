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
KINDS = ["man"] * 10 + ["woman"] * 10 + ["old_man"] * 4 + ["old_woman"] * 4 + ["boy"] * 2 + ["girl"] * 2
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
    w = [row.replace("c", "o").replace("O", "O") for row in WOUNDS[wound]]
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
    w = [row.replace("c", "d").replace("O", "D") for row in WOUNDS[wound]]
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


def person(index, kind):
    seed = f"townsfolk/{index}"
    tone, shadow = pick(f"{seed}/tone", TONES)
    old = kind.startswith("old")
    hair = pick(f"{seed}/hair", GREYS if old else HAIRS)
    cloth = pick(f"{seed}/cloth", CLOTHS)
    trousers = pick(f"{seed}/trousers", TROUSERS)
    wound = int(noise(f"{seed}/wound") * len(WOUNDS)) % len(WOUNDS)
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
    used = {letter for key, rows in spec.items() for row in rows for letter in row} - {"."}
    child = kind in ("boy", "girl")
    return spec | {
        "model": "humanoid_slim" if woman else "humanoid",
        "palette": {letter: palette[letter] for letter in sorted(used)},
        "scale": CHILD if child else 1.0,
    }


def townsfolk():
    people = {}
    seen = set()
    for index, kind in enumerate(KINDS, start=1):
        attempt = 0
        while True:
            look = person(f"{index}/{attempt}", kind)
            key = repr(sorted((k, repr(v)) for k, v in look.items()))
            if key not in seen:
                break
            attempt += 1
        seen.add(key)
        people[f"townsfolk_{index:02d}"] = look
    return people

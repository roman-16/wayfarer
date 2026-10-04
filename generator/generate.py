import hashlib
import json
import math
import shutil
from pathlib import Path

import png
import tomllib
from blocks import Template, box, cuboid, text
from camp import FIRE, HEADSTONE, OLD_SIZE, camp, old_camp
from camp import SIZE as CAMP
from caravan import caravan
from creatures import creatures
from entrances import ENTRANCES, Cover
from halls import HALLS

ROOT = Path(__file__).resolve().parent.parent
MAIN = ROOT / "mod/src/generated/resources"
DEV = ROOT / "mod/src/generated/dev"
ENDREM_EYES = ROOT / "pack/config/EndRemastered-NeoForge/Eyes"
QUESTS = ROOT / "pack/config/ftbquests/quests"
IRIS = ROOT / "pack/config/iris.properties"
QUEST_FILE_VERSION = 13
QUEST_SPACING = 1.5
PAGES_PER_ROW = 6
PAGEBREAK = "{@pagebreak}"
LANG = ROOT / "mod/src/main/resources/assets/wayfarer/lang/en_us.json"
SPARSE_STRUCTURES = ROOT / "pack/config/sparsestructures.json5"
CAMPFIRE_SPAWNS = ROOT / "pack/config/nohostilesaroundcampfire.json5"
LEAD_JOURNAL = "where your compass can mark it"
CHUNK = 16
TIER_NAMES = {1: "tier_one", 2: "tier_two"}

COVER = 3
SHOWROOM_LAYERS = [
    ("minecraft:bedrock", 1),
    ("minecraft:stone", 60),
    ("minecraft:dirt", 3),
    ("minecraft:grass_block", 1),
]
WORLD_BOTTOM = -64
WORKSHOP_LAYERS = [("minecraft:bedrock", 1), ("minecraft:stone", 63), ("minecraft:smooth_stone", 1)]
SHOWROOM_ROW_Z = 64
SHOWROOM_SPACING = 40
EYE_HEIGHT = 1.62
SEE_THROUGH = ("_carpet", "candle", "chain", "cobweb", "lantern", "_pane", "rail", "vine", "vines", "_bars", "torch")
SHOWROOM_WALL_Z = -10
SHOWROOM_SIGN_OFFSET = 17
SHOWROOM_OLD_CAMPS = {"first_camp": ("His First Camp", -16), "last_camp": ("His Last Camp", 8)}
SHOWROOM_OLD_CAMP_Z = 24
DEMO_DISTANCE = 750
DEMO_COMPANION = {"name": "Companion", "radius": 24, "seconds": 60}


def passable(room, pos):
    block = room.get(pos)
    return block is not None and (block in ("minecraft:air", "minecraft:water") or block.endswith(SEE_THROUGH))


def visible(room, at, look):
    eye = (at[0] + 0.5, at[1] + EYE_HEIGHT, at[2] + 0.5)
    steps = max(1, int(math.dist(eye, look) * 4))
    for step in range(1, steps):
        point = [eye[axis] + (look[axis] - eye[axis]) * step / steps for axis in range(3)]
        cell = tuple(math.floor(value) for value in point)
        if math.dist(point, look) > 1.2 and not passable(room, cell):
            return False
    return True


def opening(room, pos, look=None):
    x, y, z = pos
    for reach in range(9):
        for dx, dy, dz in sorted(
            cuboid((-reach, -reach, -reach), (reach, reach, reach)), key=lambda d: abs(d[0]) + abs(d[1]) + abs(d[2])
        ):
            near = (x + dx, y + dy, z + dz)
            if (
                near in room.open
                and room.get(near) == "minecraft:air"
                and room.get((near[0], near[1] + 1, near[2])) == "minecraft:air"
                and (look is None or visible(room, near, look))
            ):
                return near
    return pos


def cameras(room, layout):
    width, height, length = room.size
    door_x, floor, door_z = layout["door"]
    pedestal = next(pos for pos, block in room.t.blocks.items() if block[0] == "wayfarer:shrine_pedestal")
    target = (pedestal[0] + 0.5, pedestal[1] + 1.2, pedestal[2] + 0.5)
    middle = length // 2
    wall = next((x for x in range(width // 2, -1, -1) if (x, floor + 1, middle) not in room.open), 0)
    looks = {
        "hall": ((door_x, floor, door_z - 1), target),
        "high": ((door_x, floor + height - 7, length - 4), target),
        "pedestal": ((pedestal[0], pedestal[1], pedestal[2] + 3), target),
        "wall": ((wall + 4, floor, middle), (wall + 0.5, floor + 2, middle + 0.5)),
        "corner": ((width // 2, floor + 1, middle), (width - 1.5, floor + height - 4, 1.5)),
        "ceiling": ((width // 2, floor, middle), (width // 2 + 0.5, height, middle - 3.5)),
    }
    views = {name: (opening(room, at, look), look) for name, (at, look) in looks.items()}
    return [
        {"at": [at[0] + 0.5, at[1], at[2] + 0.5], "look": list(look), "name": name}
        for name, (at, look) in views.items()
    ]


def shrines(eyes):
    for eye, spec in eyes.items():
        room, layout = HALLS[eye](eye, spec)
        pedestal = next(pos for pos, block in room.t.blocks.items() if block[0] == "wayfarer:shrine_pedestal")
        layout = layout | {"cameras": cameras(room, layout), "pedestal": list(pedestal), "size": list(room.size)}
        room.save(MAIN / f"data/wayfarer/structure/shrine/{eye}/hall.nbt")
        cover = Cover(layout["shaft"]["style"], eye)
        ENTRANCES[eye](cover, room.shell)
        cover.save(MAIN / f"data/wayfarer/structure/shrine/{eye}/entrance.nbt")
        if "oculus" in layout:
            seal = Template()
            seal.set((0, 0, 0), room.shell)
            seal.save(MAIN / f"data/wayfarer/structure/shrine/{eye}/skylight_seal.nbt")
            cap = Template()
            for y, block in enumerate(layout["oculus"]["cap"]):
                cap.set(
                    (0, y, 0), block, east="none", north="none", south="none", up=True, waterlogged=False, west="none"
                )
            cap.save(MAIN / f"data/wayfarer/structure/shrine/{eye}/skylight_cap.nbt")
        yield eye, layout
    column = Template()
    column.set((0, 0, 0), "minecraft:air")
    column.save(MAIN / "data/wayfarer/structure/shrine/skylight.nbt")


def empty():
    t = Template()
    box(t, (0, 0, 0), (7, 7, 7), "minecraft:air")
    t.save(DEV / "data/wayfarer/structure/empty.nbt")


def showroom(eyes):
    lang = json.loads(LANG.read_text())
    items = [f"endrem:{eye}_eye" for eye in sorted(eyes)] + sorted(
        key.split(".", 1)[1].replace(".", ":", 1)
        for key in lang
        if key.startswith(("block.wayfarer.", "item.wayfarer."))
    )
    y = WORLD_BOTTOM + sum(height for _, height in SHOWROOM_LAYERS)
    row = SHOWROOM_ROW_Z + CHUNK // 2
    half = len(items) // 2 + 1
    lines = [
        "settle",
        f"/execute in wayfarer:showroom run tp @s 0.5 {y + 20} 0.5",
        "settle",
        "/gamerule doDaylightCycle false",
        "/gamerule doMobSpawning false",
        "/gamerule doWeatherCycle false",
        "/time set noon",
        "/weather clear",
        f"/place template wayfarer:last_camp {-(CAMP // 2)} {y - 1} {-(CAMP // 2)}",
        f"/fill {-half} {y} {SHOWROOM_WALL_Z} {half} {y + 3} {SHOWROOM_WALL_Z} minecraft:polished_andesite",
    ]
    for index, item in enumerate(items):
        lines.append(
            f"/summon minecraft:item_frame {index - len(items) // 2} {y + 1} {SHOWROOM_WALL_Z + 1} "
            + f'{{Facing:3b,Fixed:1b,Item:{{id:"{item}",count:1}}}}'
        )
    blank = json.dumps(json.dumps(""))
    for kind, (title, x) in SHOWROOM_OLD_CAMPS.items():
        name = json.dumps(json.dumps(title))
        lines += [
            f"/place template wayfarer:lead/{kind} {x} {y - 1} {SHOWROOM_OLD_CAMP_Z}",
            f"/setblock {x + OLD_SIZE // 2} {y} {SHOWROOM_OLD_CAMP_Z - 2} minecraft:oak_sign[rotation=8]"
            + f"{{front_text:{{messages:[{blank},{name},{blank},{blank}]}}}}",
        ]
    for index, eye in enumerate(sorted(eyes)):
        x = SHOWROOM_SPACING * (index + 1)
        center = x + CHUNK // 2
        name = json.dumps(json.dumps(f"{eye.title()} Eye"))
        lines += [
            f"/tp @s {center} {y + 20} {row}",
            "settle",
            f"/place structure wayfarer:shrine/{eye} {x} {y} {SHOWROOM_ROW_Z}",
            f"/setblock {center} {y} {row - SHOWROOM_SIGN_OFFSET} minecraft:oak_sign[rotation=8]"
            + f"{{front_text:{{messages:[{blank},{name},{blank},{blank}]}}}}",
        ]
    target = (-DEMO_DISTANCE, 0)
    lines += [
        f"/tp @s {target[0] + 0.5} {y + 20} {target[1] + 0.5}",
        "settle",
        f"/fill {target[0] - 1} {y} {target[1] - 1} {target[0] + 1} {y} {target[1] + 1} minecraft:mossy_cobblestone",
        f"/setblock {target[0]} {y + 1} {target[1]} minecraft:cobblestone_wall",
        f"/setblock {target[0]} {y + 2} {target[1]} minecraft:lantern",
        f"/tp @s 0.5 {y} 8.5 180 0",
    ]
    demo = {
        "companion": DEMO_COMPANION | {"centre": [FIRE[0] - CAMP // 2, FIRE[1] - CAMP // 2]},
        "grave": [HEADSTONE[0] - CAMP // 2, HEADSTONE[1] - CAMP // 2],
        "target": {"at": [target[0], y, target[1]], "shrine": f"wayfarer:shrine/{sorted(eyes)[0]}"},
    }
    write_json(DEV / "wayfarer/showroom.json", {"demo": demo, "steps": lines})
    write_json(
        DEV / "assets/showroom/lang/en_us.json",
        {"travelerstitles.wayfarer.showroom": "Showroom", "travelerstitles.wayfarer.workshop": "Workshop"},
    )
    for world, layers in (("showroom", SHOWROOM_LAYERS), ("workshop", WORKSHOP_LAYERS)):
        write_json(DEV / f"data/wayfarer/dimension/{world}.json", flat_world(layers))


def flat_world(layers):
    return {
        "generator": {
            "settings": {
                "biome": "minecraft:plains",
                "features": False,
                "lakes": False,
                "layers": [{"block": block, "height": height} for block, height in layers],
                "structure_overrides": [],
            },
            "type": "minecraft:flat",
        },
        "type": "minecraft:overworld",
    }


def tour(eyes, layouts):
    surface = WORLD_BOTTOM + sum(height for _, height in SHOWROOM_LAYERS)
    camp_origin = [-(CAMP // 2), surface - 1, -(CAMP // 2)]
    fire = CAMP // 2 + 0.5
    camp_views = {
        "north-west": ((-2, 6, -2), (fire, 1, fire)),
        "north-east": ((CAMP + 2, 6, -2), (fire, 1, fire)),
        "south-east": ((CAMP + 2, 6, CAMP + 2), (fire, 1, fire)),
        "south-west": ((-2, 6, CAMP + 2), (fire, 1, fire)),
        "tent": ((11.5, 1, 8.5), (11.5, 1.5, 3.5)),
        "grave": ((6.5, 1, 14.5), (HEADSTONE[0] + 0.5, 1.5, HEADSTONE[1] + 0.5)),
        "letter": ((11.5, 1, 6.5), (11.5, 1.2, 10.5)),
        "above": ((fire, 22, fire + 8), (fire, 0, fire)),
    }
    write_json(
        DEV / "wayfarer/tour.json",
        {
            "camp": {
                "cameras": [
                    {"at": list(at), "look": list(look), "name": name} for name, (at, look) in camp_views.items()
                ],
                "origin": camp_origin,
            },
            "shrines": [
                {
                    "cameras": layouts[eye]["cameras"],
                    "centre": [SHOWROOM_SPACING * (index + 1) + CHUNK // 2, SHOWROOM_ROW_Z + CHUNK // 2],
                    "door": list(layouts[eye]["door"]),
                    "eye": eye,
                    "pedestal": layouts[eye]["pedestal"],
                    "shaft": layouts[eye]["shaft"]["style"],
                    "size": layouts[eye]["size"],
                }
                for index, eye in enumerate(sorted(eyes))
            ],
            "surface": surface,
        },
    )


def rgba(color):
    return tuple(int(color[i : i + 2], 16) for i in (1, 3, 5)) + (255,)


def textures(spec):
    for texture in spec.values():
        for variant, colors in texture["variants"].items():
            palette = {key: rgba(value) for key, value in {**texture["colors"], **colors}.items()}
            pixels = [[palette.get(cell, (0, 0, 0, 0)) for cell in row] for row in texture["rows"]]
            png.write(MAIN / f"assets/wayfarer/textures/{texture.get('folder', 'item')}/{variant}.png", pixels)


def snbt(value, indent=0):
    pad = "\t" * (indent + 1)
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, int):
        return str(value)
    if isinstance(value, float):
        return f"{value}d"
    if isinstance(value, str):
        return json.dumps(value)
    if isinstance(value, list):
        if not value:
            return "[ ]"
        items = [snbt(item, indent + 1) for item in value]
        return "[\n" + "".join(f"{pad}{item}\n" for item in items) + "\t" * indent + "]"
    keys = sorted(value)
    lines = []
    for key in keys:
        name = key if key.replace("_", "").isalnum() else json.dumps(key)
        lines.append(f"{pad}{name}: {snbt(value[key], indent + 1)}\n")
    return "{\n" + "".join(lines) + "\t" * indent + "}"


def quest_id(kind, name):
    number = int.from_bytes(hashlib.sha256(f"wayfarer/{kind}/{name}".encode()).digest()[:8], "big") & 0x7FFFFFFFFFFFFFFF
    return f"{max(number, 2):016X}"


def task(spec, owner, index):
    entry = {"id": quest_id("task", f"{owner}/{index}")}
    if "checkmark" in spec:
        return entry | {"type": "checkmark"}
    if "item" in spec:
        return entry | {"item": {"count": 1, "id": spec["item"]}, "type": "item"}
    if "advancement" in spec:
        return entry | {"advancement": spec["advancement"], "criterion": "", "type": "advancement"}
    if "stat" in spec:
        return entry | {"stat": spec["stat"], "type": "stat", "value": spec["value"]}
    if "structure" in spec:
        return entry | {"structure": spec["structure"], "type": "structure"}
    if "dimension" in spec:
        return entry | {"dimension": spec["dimension"], "type": "dimension"}
    if "kill" in spec:
        return entry | {"entity": spec["kill"], "type": "kill", "value": 1}
    raise ValueError(f"unknown task {spec}")


def reward(spec, owner, index):
    entry = {"id": quest_id("reward", f"{owner}/{index}")}
    if "auto" in spec:
        entry["auto"] = spec["auto"]
    if "item" in spec:
        item = {"count": spec.get("count", 1), "id": spec["item"]}
        if "components" in spec:
            item["components"] = spec["components"]
        return entry | {"item": item, "type": "item"}
    if "xp_levels" in spec:
        return entry | {"type": "xp_levels", "xp_levels": spec["xp_levels"]}
    if "command" in spec:
        return entry | {"command": spec["command"], "permission_level": 2, "silent": True, "type": "command"}
    raise ValueError(f"unknown reward {spec}")


def paragraphs(description):
    lines = []
    for paragraph in description:
        if lines:
            lines.append("")
        lines.append(paragraph)
    return lines


def eye_quests(eyes, tier, spec):
    names = [eye for eye in sorted(eyes) if eyes[eye]["tier"] == tier]
    for index, eye in enumerate(names):
        yield {
            "after": spec["after"],
            "description": [eyes[eye]["hint"]],
            "icon": f"endrem:{eye}_eye",
            "id": f"eye_{eye}",
            "rewards": spec.get("rewards", []),
            "subtitle": f"The {eye.title()} Eye",
            "tasks": [{"structure": f"wayfarer:shrine/{eye}"}, {"advancement": f"wayfarer:shards/{eye}"}],
            "title": eyes[eye]["people"],
            "x": (index - (len(names) - 1) / 2) * QUEST_SPACING,
            "y": spec["y"],
        }


def grid(index, row=0):
    return {"x": (index % PAGES_PER_ROW) * QUEST_SPACING, "y": (index // PAGES_PER_ROW + row) * QUEST_SPACING}


def page_quests(library):
    for index, (identifier, entry) in enumerate(library.items()):
        pages = []
        for page in entry["pages"]:
            if pages:
                pages.append(PAGEBREAK)
            pages.append(page)
        yield {
            "description": pages,
            "icon": "minecraft:written_book",
            "id": f"page/{identifier}",
            "invisible": True,
            "subtitle": entry["author"],
            "tasks": [{"checkmark": True}],
            "title": entry["title"],
        } | grid(index)


def speaker_name(fire, speaker, heard):
    names = [entry["name"] for entry in fire["speakers"][speaker]["names"] if entry.get("after") in heard | {None}]
    return names[-1]


def journal_line(line):
    return line.replace("{lead}", LEAD_JOURNAL)


def note_quests(notes, kind):
    entries = notes[kind]
    if kind == "words":
        entries = sorted(entries, key=lambda entry: entry["title"].lower().removeprefix("the ").removeprefix("a "))
    for index, entry in enumerate(entries):
        yield {
            "description": [entry["text"]],
            "icon": "minecraft:paper" if kind == "summary" else "minecraft:name_tag",
            "id": f"note/{kind}/{entry['id']}",
            "invisible": True,
            "subtitle": "The Road So Far" if kind == "summary" else "Words of the Road",
            "tasks": [{"checkmark": True}],
            "title": entry["title"],
        } | grid(index)


def fire_quests(fire, leads):
    heard = set()
    steps = fire["steps"]
    for index, step in enumerate(steps):
        heard.add(step["id"])
        name = speaker_name(fire, step["speaker"], heard)
        yield {
            "description": paragraphs([f"{name}: {journal_line(line)}" for line in step["lines"]]),
            "icon": "minecraft:campfire" if step["speaker"] == "voice" else "minecraft:dark_oak_sapling",
            "id": f"step/{step['id']}",
            "invisible": True,
            "subtitle": name,
            "tasks": [{"checkmark": True}],
            "title": step["title"],
        } | grid(index)
    row = (len(steps) + PAGES_PER_ROW - 1) // PAGES_PER_ROW + 1
    for index, (identifier, lead) in enumerate(leads.items()):
        yield {
            "description": paragraphs([journal_line(lead["journal"]), mark_links(identifier)]),
            "icon": "minecraft:compass",
            "id": f"lead/{identifier}",
            "invisible": True,
            "invisible_until_tasks": 1,
            "subtitle": "A place you were told the way to",
            "tasks": [{"checkmark": True}, {"checkmark": True}],
            "title": lead["title"],
        } | grid(index, row)


def mark_links(lead):
    def link(action):
        return {
            "clickEvent": {"action": "run_command", "value": f"/wayfarer lead {action} {lead}"},
            "color": "gold",
            "hoverEvent": {"action": "show_text", "contents": {"translate": f"wayfarer.lead.{action}.hover"}},
            "translate": f"wayfarer.lead.{action}.link",
        }

    return json.dumps(["", link("mark"), "  ", link("unmark")], sort_keys=True)


def quests(book, eyes, library, notes, fire, leads):
    lang = {f"file.{1:016X}.title": book["title"]}
    tiers = {tier: [f"eye_{eye}" for eye in sorted(eyes) if eyes[eye]["tier"] == tier] for tier in TIER_NAMES}
    for order, chapter in enumerate(book["chapters"]):
        chapter_id = quest_id("chapter", chapter["id"])
        lang[f"chapter.{chapter_id}.title"] = chapter["title"]
        lang[f"chapter.{chapter_id}.chapter_subtitle"] = chapter["subtitle"]
        entries = chapter.get("quests", []) + list(eye_quests(eyes, chapter.get("eyes"), chapter.get("eye_quests", {})))
        if chapter.get("pages"):
            entries += list(page_quests(library))
        if chapter.get("notes"):
            entries += list(note_quests(notes, chapter["notes"]))
        if chapter.get("fire"):
            entries += list(fire_quests(fire, leads))
        written = []
        for spec in entries:
            identifier = quest_id("quest", spec["id"])
            after = spec.get("after", []) + tiers.get(spec.get("after_eyes"), [])
            entry = {
                "dependencies": [quest_id("quest", name) for name in after],
                "id": identifier,
                "tasks": [task(item, spec["id"], index) for index, item in enumerate(spec["tasks"])],
                "x": float(spec["x"]),
                "y": float(spec["y"]),
            }
            if "icon" in spec:
                entry["icon"] = {"count": 1, "id": spec["icon"]}
            if "min_after" in spec:
                entry["min_required_dependencies"] = spec["min_after"]
            if spec.get("invisible"):
                entry["invisible"] = True
            if spec.get("invisible_until_tasks"):
                entry["invisible_until_tasks"] = spec["invisible_until_tasks"]
            if spec.get("rewards"):
                entry["rewards"] = [reward(item, spec["id"], index) for index, item in enumerate(spec["rewards"])]
            written.append(entry)
            lang[f"quest.{identifier}.title"] = spec["title"]
            lang[f"quest.{identifier}.quest_subtitle"] = spec["subtitle"]
            description = spec["description"] if spec.get("invisible") else paragraphs(spec["description"])
            lang[f"quest.{identifier}.quest_desc"] = description
        data = {
            "default_hide_dependency_lines": False,
            "default_quest_shape": "",
            "filename": chapter["id"],
            "group": "",
            "hide_quest_until_deps_complete": True,
            "icon": {"count": 1, "id": chapter["icon"]},
            "id": chapter_id,
            "images": [],
            "order_index": order,
            "quest_links": [],
            "quests": written,
        }
        path = QUESTS / f"chapters/{chapter['id']}.snbt"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(snbt(data) + "\n")
    (QUESTS / "lang").mkdir(parents=True, exist_ok=True)
    (QUESTS / "lang/en_us.snbt").write_text(snbt(lang) + "\n")
    (QUESTS / "chapter_groups.snbt").write_text(snbt({"chapter_groups": []}) + "\n")
    (QUESTS / "data.snbt").write_text(snbt({"fallback_locale": "en_us", "version": QUEST_FILE_VERSION}) + "\n")


def library(spec, eyes):
    entries = {spec["letter"]["id"]: spec["letter"]}
    for memory in spec["memories"]:
        entries[memory["id"]] = memory | {"author": "The Eye"}
    for entry in spec["found"]:
        entries[entry["id"]] = entry
    for eye, eye_spec in sorted(eyes.items()):
        entries[f"people/{eye}"] = {
            "author": eye_spec["people"],
            "pages": eye_spec["lore"],
            "title": eye_spec["people"],
        }
    for collection in spec["collections"]:
        for entry in collection["books"]:
            entries[entry["id"]] = entry | {"author": collection["author"]}
    return entries


def leads(fire, eyes):
    found = {}
    for step in fire["steps"]:
        if "lead" in step:
            line = next(line for line in step["lines"] if "{lead}" in line)
            found[step["lead"]] = line
    result = {}
    for lead in fire["leads"]:
        result[lead["id"]] = lead | {"journal": found[lead["id"]]}
    for eye, spec in sorted(eyes.items()):
        place = spec["place"]
        result[f"people/{eye}"] = {
            "find": [place["find"]],
            "items": [{"count": 1, "item": place["gift"]["item"], "name": place["gift"]["name"]}],
            "journal": place["line"],
            "line": place["line"],
            "mark": place["mark"],
            "structures": place["structures"],
            "title": place["title"],
        }
    return result


def lead_data(identifier, lead):
    data = {
        "find": lead["find"],
        "found_task": quest_id("task", f"lead/{identifier}/1"),
        "given_task": quest_id("task", f"lead/{identifier}/0"),
        "items": [
            item | {"count": item.get("count", 1), "name": item.get("name", "")} for item in lead.get("items", [])
        ],
        "line": lead.get("line", ""),
        "mark": lead["mark"],
        "page": lead.get("page", ""),
    }
    if "camp" in lead:
        return data | {"camp": lead["camp"]}
    write_json(
        MAIN / f"data/wayfarer/tags/worldgen/structure/lead/{identifier}.json",
        {"values": [{"id": structure, "required": False} for structure in lead["structures"]]},
    )
    return data | {"structures": f"wayfarer:lead/{identifier}"}


def story(fire, notes, leads, eyes):
    triggers = {}
    for kind in ("summary", "words"):
        for entry in notes[kind]:
            triggers.setdefault(entry["when"], []).append(quest_id("quest", f"note/{kind}/{entry['id']}"))
    for kind in ("first_camp", "last_camp"):
        old_camp(kind).save(MAIN / f"data/wayfarer/structure/lead/{kind}.nbt")
    write_json(
        MAIN / "data/wayfarer/advancement/gate.json", {"criteria": {"cleared": {"trigger": "minecraft:impossible"}}}
    )
    return {
        "leads": {identifier: lead_data(identifier, lead) for identifier, lead in leads.items()},
        "notes": {trigger: sorted(quests) for trigger, quests in sorted(triggers.items())},
        "places": {f"wayfarer:shrine/{eye}": f"people/{eye}" for eye in sorted(eyes)},
        "scenes": {
            scene["id"]: {"book": scene.get("book", ""), "lines": scene["lines"], "speaker": scene["speaker"]}
            for scene in fire["scenes"]
        },
        "speakers": {
            speaker: {
                "color": spec["color"],
                "names": [entry | {"after": entry.get("after", "")} for entry in spec["names"]],
            }
            for speaker, spec in sorted(fire["speakers"].items())
        },
        "steps": [
            {
                "after": step.get("after", []),
                "found": step.get("found", []),
                "id": step["id"],
                "lead": step.get("lead", ""),
                "lines": step["lines"],
                "quest": quest_id("quest", f"step/{step['id']}"),
                "shards": step.get("shards", 0),
                "slain": step.get("slain", False),
                "speaker": step["speaker"],
                "visit": [item | {"count": item.get("count", 1), "name": ""} for item in step["visit"]]
                if "visit" in step
                else None,
            }
            for step in fire["steps"]
        ],
    }


def books(spec, eyes, entries, told):
    modifiers = sorted(
        f"wayfarer:{path.stem}"
        for path in (ROOT / "mod/src/main/resources/data/wayfarer/loot_modifiers").glob("*.json")
    )
    for collection in spec["collections"]:
        pool = [
            {
                "functions": [
                    {
                        "components": {
                            "minecraft:written_book_content": {
                                "author": collection["author"],
                                "pages": [text(page) for page in entry["pages"]],
                                "title": entry["title"],
                            }
                        },
                        "function": "minecraft:set_components",
                    },
                    {"function": "minecraft:set_custom_data", "tag": snbt_tag({"wayfarer_book": entry["id"]})},
                ],
                "name": "minecraft:written_book",
                "type": "minecraft:item",
            }
            for entry in collection["books"]
        ]
        write_json(
            MAIN / f"data/wayfarer/loot_table/books/{collection['id']}.json",
            {
                "pools": [
                    {
                        "conditions": [{"chance": collection["chance"], "condition": "minecraft:random_chance"}],
                        "entries": pool,
                        "rolls": 1,
                    }
                ],
                "type": "minecraft:chest",
            },
        )
        write_json(
            MAIN / f"data/wayfarer/loot_modifiers/books/{collection['id']}.json",
            {
                "conditions": [
                    {
                        "condition": "minecraft:any_of",
                        "terms": [
                            {"condition": "neoforge:loot_table_id", "loot_table_id": target}
                            for target in collection["targets"]
                        ],
                    }
                ],
                "table": f"wayfarer:books/{collection['id']}",
                "type": "neoforge:add_table",
            },
        )
        modifiers.append(f"wayfarer:books/{collection['id']}")
    write_json(
        MAIN / "data/neoforge/loot_modifiers/global_loot_modifiers.json",
        {"entries": sorted(modifiers), "replace": False},
    )
    write_json(
        MAIN / "wayfarer/library.json",
        told
        | {
            "books": {
                identifier: {
                    "author": entry["author"],
                    "pages": entry["pages"],
                    "quest": quest_id("quest", f"page/{identifier}"),
                    "title": entry["title"],
                }
                for identifier, entry in entries.items()
            },
            "memories": [memory["id"] for memory in spec["memories"]],
            "people": {f"wayfarer:shrine/{eye}": f"people/{eye}" for eye in sorted(eyes)},
        },
    )


def snbt_tag(value):
    return "{" + ",".join(f"{key}:{json.dumps(item)}" for key, item in sorted(value.items())) + "}"


def shaders():
    shader = tomllib.loads((ROOT / "pack/shaderpacks/complementary-reimagined.pw.toml").read_text())["filename"]
    patcher = tomllib.loads((ROOT / "pack/mods/euphoria-patches.pw.toml").read_text())["filename"]
    patches = patcher.split("-")[1]
    pack = f"{shader.removesuffix('.zip')} + EuphoriaPatches_{patches}"
    IRIS.write_text(f"disableUpdateMessage=true\nenableShaders=true\nshaderPack={pack}\n")


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n")


def salt(name):
    return int.from_bytes(hashlib.sha256(name.encode()).digest()[:4], "big") & 0x7FFFFFFF


def states(value):
    return [{"Name": name} for name in value] if isinstance(value, list) else {"Name": value}


def worldgen(eyes, layouts, tiers, balance):
    for eye, spec in eyes.items():
        layout = {"cover": COVER, "door": list(layouts[eye]["door"])}
        if "oculus" in layouts[eye]:
            layout["oculus"] = {
                "at": list(layouts[eye]["oculus"]["at"]),
                "cap": f"wayfarer:shrine/{eye}/skylight_cap",
                "column": "wayfarer:shrine/skylight",
                "seal": f"wayfarer:shrine/{eye}/skylight_seal",
            }
        write_json(
            MAIN / f"data/wayfarer/tags/worldgen/biome/has_structure/shrine/{eye}.json",
            {"values": [{"id": biome, "required": False} for biome in spec["biomes"]]},
        )
        write_json(
            MAIN / f"data/wayfarer/worldgen/structure/shrine/{eye}.json",
            {
                "biomes": f"#wayfarer:has_structure/shrine/{eye}",
                "layout": layout,
                "pieces": {"entrance": f"wayfarer:shrine/{eye}/entrance", "hall": f"wayfarer:shrine/{eye}/hall"},
                "shaft": {
                    key: value if key == "style" else states(value) for key, value in layouts[eye]["shaft"].items()
                },
                "spawn_overrides": {},
                "step": "underground_structures",
                "terrain_adaptation": "none",
                "type": "wayfarer:shrine",
            },
        )
        write_json(
            ENDREM_EYES / f"{eye}_eye.json",
            {
                "id": f"{eye}_eye",
                "loot_tables_id": [],
                "loot_to_inject_id": "minecraft:empty",
                "rarity": "rare" if spec["tier"] == 1 else "epic",
            },
        )
        write_json(
            MAIN / f"data/wayfarer/advancement/shards/{eye}.json",
            {"criteria": {"taken": {"trigger": "minecraft:impossible"}}},
        )
    names = sorted(eyes)
    write_json(
        MAIN / "data/wayfarer/tags/worldgen/structure/shrines.json",
        {"values": [f"wayfarer:shrine/{eye}" for eye in names]},
    )
    for tier, name in TIER_NAMES.items():
        members = [f"wayfarer:shrine/{eye}" for eye in names if eyes[eye]["tier"] == tier]
        write_json(MAIN / f"data/wayfarer/tags/worldgen/structure/shrines/{name}.json", {"values": members})
        spacing = tiers[str(tier)]["spacing"]
        write_json(
            MAIN / f"data/wayfarer/worldgen/structure_set/shrines/{name}.json",
            {
                "placement": {
                    "salt": salt(f"wayfarer:shrines/{name}"),
                    "separation": spacing * 3 // 4,
                    "spacing": spacing,
                    "type": "minecraft:random_spread",
                },
                "structures": [{"structure": member, "weight": 1} for member in members],
            },
        )
    write_json(
        MAIN / "data/curios/tags/item/shard.json",
        {"values": [{"id": f"endrem:{eye}_eye", "required": False} for eye in names]},
    )
    strongholds = balance["strongholds"]
    write_json(
        MAIN / "data/betterstrongholds/worldgen/structure_set/stronghold.json",
        {
            "neoforge:conditions": [{"modid": "betterstrongholds", "type": "neoforge:mod_loaded"}],
            "placement": {
                "count": strongholds["count"],
                "jitter_blocks": strongholds["jitter_blocks"],
                "preferred_biomes": "#minecraft:stronghold_biased_to",
                "radius_blocks": strongholds["radius_blocks"],
                "salt": 0,
                "type": "wayfarer:exact_ring",
            },
            "structures": [{"structure": "betterstrongholds:stronghold", "weight": 1}],
        },
    )
    write_json(
        MAIN / "data/betterstrongholds/tags/worldgen/biome/has_structure/better_stronghold.json",
        {"replace": False, "values": ["#minecraft:is_overworld"]},
    )


def spread(balance):
    structures = balance["structures"]
    unspread = sorted(structures["unspread"] + [f"wayfarer:shrines/{name}" for name in TIER_NAMES.values()])
    write_json(
        SPARSE_STRUCTURES,
        {
            "customSpreadFactors": [{"factor": 1, "structure": name} for name in unspread],
            "idBasedSalt": True,
            "spreadFactor": structures["spread_factor"],
        },
    )
    write_json(
        MAIN / "data/wayfarer/tags/worldgen/structure_set/unspread.json",
        {"values": [{"id": name, "required": False} for name in unspread]},
    )


def campfire_spawns(balance):
    write_json(
        CAMPFIRE_SPAWNS,
        {
            "burnHostilesAroundWhenPlaced": True,
            "burnHostilesRadiusModifier": 0.5,
            "campfireMustBeLit": True,
            "campfireMustBeSignalling": False,
            "enableEffectForNormalCampfires": True,
            "enableEffectForSoulCampfires": True,
            "preventHostilesRadius": balance["fire"]["circle_blocks"],
            "preventMobSpawnerSpawns": False,
        },
    )


def removals(spec):
    for recipe in spec["recipes"]:
        namespace, path = recipe.split(":")
        write_json(
            MAIN / f"data/{namespace}/recipe/{path}.json",
            {"neoforge:conditions": [{"type": "neoforge:false"}]},
        )
    for tag in spec["structure_biomes"]:
        namespace, path = tag.split(":")
        write_json(
            MAIN / f"data/{namespace}/tags/worldgen/biome/{path}.json",
            {"replace": True, "values": []},
        )
    for structure_set in spec["structure_sets"]:
        namespace, path = structure_set.split(":")
        write_json(
            MAIN / f"data/{namespace}/worldgen/structure_set/{path}.json",
            {
                "placement": {"salt": 0, "spacing": 1, "separation": 0, "type": "minecraft:random_spread"},
                "structures": [],
            },
        )
    for structure in spec["structures"]:
        namespace, path = structure.split(":")
        write_json(
            MAIN / f"data/{namespace}/worldgen/structure/{path}.json",
            {
                "type": "minecraft:buried_treasure",
                "biomes": [],
                "spawn_overrides": {},
                "step": "underground_structures",
            },
        )


def main():
    source = tomllib.loads((ROOT / "generator/eyes.toml").read_text())
    eyes = source["eyes"]
    balance = tomllib.loads((ROOT / "balance.toml").read_text())
    written = tomllib.loads((ROOT / "story/books.toml").read_text())
    fire = tomllib.loads((ROOT / "story/fire.toml").read_text())
    notes = tomllib.loads((ROOT / "story/notes.toml").read_text())
    for directory in (DEV, ENDREM_EYES, MAIN, QUESTS):
        shutil.rmtree(directory, ignore_errors=True)
    layouts = dict(shrines(eyes))
    worldgen(eyes, layouts, source["tiers"], balance)
    tour(eyes, layouts)
    spread(balance)
    campfire_spawns(balance)
    removals(tomllib.loads((ROOT / "generator/removals.toml").read_text()))
    camp(written["letter"]).save(MAIN / "data/wayfarer/structure/last_camp.nbt")
    empty()
    caravan(DEV)
    creatures(DEV)
    showroom(eyes)
    textures(tomllib.loads((ROOT / "generator/textures.toml").read_text()))
    entries = library(written, eyes)
    told = leads(fire, eyes)
    quests(tomllib.loads((ROOT / "story/quests.toml").read_text()), eyes, entries, notes, fire, told)
    books(written, eyes, entries, story(fire, notes, told, eyes))
    shaders()


main()

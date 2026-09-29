import hashlib
import json
import shutil
from pathlib import Path

import nbt
import png
import tomllib
from nbt import Byte, Double

ROOT = Path(__file__).resolve().parent.parent
MAIN = ROOT / "mod/src/generated/resources"
DEV = ROOT / "mod/src/generated/dev"
ENDREM_EYES = ROOT / "pack/config/EndRemastered-NeoForge/Eyes"
QUESTS = ROOT / "pack/config/ftbquests/quests"
IRIS = ROOT / "pack/config/iris.properties"
QUEST_FILE_VERSION = 13
QUEST_SPACING = 1.5
LANG = ROOT / "mod/src/main/resources/assets/wayfarer/lang/en_us.json"
SPARSE_STRUCTURES = ROOT / "pack/config/sparsestructures.json5"
DATA_VERSION = 3955
CHUNK = 16

HALL_WIDTH = 15
HALL_LENGTH = 21
HALL_CEILING = 10
AISLE = HALL_WIDTH // 2
PEDESTAL_Z = 3
COVER = 3
HANGING_LIGHTS = {"minecraft:lantern", "minecraft:soul_lantern"}
HEADROOM = 3
MAX_STEPS = 48
STAIR = (1, 1, 0)
STEP_CEILING = STAIR[1] + HEADROOM + 1
STAIR_STATE = {"facing": "south", "half": "bottom", "shape": "straight", "waterlogged": False}
MOUTH = (2, 1, 1)
MARKER_SIZE = [5, 4, 6]
CAMP = 9
SHOWROOM_LAYERS = [
    ("minecraft:bedrock", 1),
    ("minecraft:stone", 60),
    ("minecraft:dirt", 3),
    ("minecraft:grass_block", 1),
]
WORLD_BOTTOM = -64
SHOWROOM_ROW_Z = 64
SHOWROOM_SPACING = 32
SHOWROOM_WALL_Z = -10


class Template:
    def __init__(self):
        self.blocks = {}
        self.entities = []

    def set(self, pos, name, entity=None, **properties):
        self.blocks[pos] = (
            name,
            tuple(sorted((key, str(value).lower()) for key, value in properties.items())),
            entity,
        )

    def entity(self, pos, data):
        self.entities.append((pos, data))

    def save(self, path, size=None):
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
        entities = [
            {
                "blockPos": [int(x), int(y), int(z)],
                "nbt": data,
                "pos": [Double(x), Double(y), Double(z)],
            }
            for (x, y, z), data in self.entities
        ]
        nbt.write(
            path,
            {
                "DataVersion": DATA_VERSION,
                "blocks": blocks,
                "entities": entities,
                "palette": palette,
                "size": size,
            },
        )


def text(line):
    return json.dumps(line)


def book(title, author, pages):
    return {
        "components": {
            "minecraft:written_book_content": {
                "author": author,
                "pages": [{"raw": text(page)} for page in pages],
                "resolved": Byte(1),
                "title": {"raw": title},
            }
        },
        "count": 1,
        "id": "minecraft:written_book",
    }


def lectern(template, pos, facing, title, author, pages):
    template.set(
        pos,
        "minecraft:lectern",
        entity={
            "Book": book(title, author, pages),
            "Page": 0,
            "id": "minecraft:lectern",
        },
        facing=facing,
        has_book=True,
        powered=False,
    )


def box(template, low, high, name, **properties):
    for x in range(low[0], high[0] + 1):
        for y in range(low[1], high[1] + 1):
            for z in range(low[2], high[2] + 1):
                template.set((x, y, z), name, **properties)


def hall(eye, spec):
    palette = spec["palette"]
    t = Template()
    box(t, (0, 0, 0), (HALL_WIDTH - 1, HALL_CEILING, HALL_LENGTH - 1), palette["shell"])
    box(t, (1, 1, 1), (HALL_WIDTH - 2, 1, HALL_LENGTH - 2), palette["floor"])
    box(
        t,
        (1, 2, 1),
        (HALL_WIDTH - 2, HALL_CEILING - 1, HALL_LENGTH - 2),
        "minecraft:air",
    )
    for z in range(1, HALL_LENGTH - 1):
        t.set((0, 6, z), palette["trim"])
        t.set((HALL_WIDTH - 1, 6, z), palette["trim"])
    for x in range(1, HALL_WIDTH - 1):
        t.set((x, 6, 0), palette["trim"])
    for z in range(PEDESTAL_Z + 6, HALL_LENGTH - 1):
        t.set((AISLE, 1, z), palette["trim"])
    for z in (4, 8, 12, 16):
        for x in (2, HALL_WIDTH - 3):
            box(t, (x, 2, z), (x, HALL_CEILING - 1, z), palette["pillar"])
        for x in (4, HALL_WIDTH - 5):
            if palette["light"] in HANGING_LIGHTS:
                t.set((x, HALL_CEILING - 1, z), palette["light"], hanging=True, waterlogged=False)
            else:
                t.set((x, HALL_CEILING, z), palette["light"])
    for x in range(AISLE - 1, AISLE + 2):
        for z in range(PEDESTAL_Z + 3, PEDESTAL_Z + 6):
            t.set((x, 1, z), "minecraft:water", level=0)
    for x0, z0 in (
        (1, 1),
        (HALL_WIDTH - 4, 1),
        (1, HALL_LENGTH - 4),
        (HALL_WIDTH - 4, HALL_LENGTH - 4),
    ):
        for x in range(x0, x0 + 3):
            for z in range(z0, z0 + 3):
                t.set((x, 1, z), "minecraft:moss_block")
                t.set(
                    (x, 2, z),
                    palette["plant"] if (x + z) % 2 == 0 else "minecraft:moss_carpet",
                )
    t.set((AISLE, 2, PEDESTAL_Z), "wayfarer:shrine_pedestal")
    t.set((AISLE, 1, PEDESTAL_Z), palette["trim"])
    t.entity(
        (AISLE + 0.5, 3.4, PEDESTAL_Z + 0.5),
        {
            "billboard": "vertical",
            "id": "minecraft:item_display",
            "item": {"count": 1, "id": f"endrem:{eye}_eye"},
        },
    )
    lectern(
        t,
        (AISLE - 2, 2, PEDESTAL_Z + 1),
        "south",
        spec["people"],
        "The Keepers",
        spec["lore"],
    )
    t.set((AISLE, HALL_CEILING, PEDESTAL_Z), "minecraft:air")
    box(t, (AISLE, 2, HALL_LENGTH - 1), (AISLE, 1 + HEADROOM, HALL_LENGTH - 1), "minecraft:air")
    t.save(MAIN / f"data/wayfarer/structure/shrine/{eye}/hall.nbt")


def steps(eye, spec):
    palette = spec["palette"]
    closed = Template()
    for side in (0, 2):
        box(closed, (side, 0, 0), (side, STEP_CEILING, 0), palette["shell"])
    closed.set((1, 0, 0), palette["shell"])
    closed.set((1, STAIR[1], 0), palette["stairs"], **STAIR_STATE)
    box(closed, (1, STAIR[1] + 1, 0), (1, STAIR[1] + HEADROOM, 0), "minecraft:air")
    closed.set((1, STEP_CEILING, 0), palette["shell"])
    closed.save(MAIN / f"data/wayfarer/structure/shrine/{eye}/step.nbt")
    opened = Template()
    opened.set((1, 0, 0), palette["shell"])
    opened.set((1, STAIR[1], 0), palette["stairs"], **STAIR_STATE)
    box(opened, (1, STAIR[1] + 1, 0), (1, STAIR[1] + HEADROOM, 0), "minecraft:air")
    opened.save(MAIN / f"data/wayfarer/structure/shrine/{eye}/open_step.nbt", size=[3, STEP_CEILING + 1, 1])
    seal = Template()
    seal.set((0, 0, 0), palette["shell"])
    seal.save(MAIN / f"data/wayfarer/structure/shrine/{eye}/seal.nbt")


def shaft():
    column = Template()
    column.set((0, 0, 0), "minecraft:air")
    column.save(MAIN / "data/wayfarer/structure/shrine/shaft.nbt")
    cap = Template()
    cap.set((0, 0, 0), "minecraft:azalea_leaves", distance=1, persistent=True, waterlogged=False)
    cap.save(MAIN / "data/wayfarer/structure/shrine/cap.nbt")


def markers():
    wall = {"east": "none", "north": "none", "south": "none", "up": True, "waterlogged": False, "west": "none"}
    mx, my, mz = MOUTH
    cairn = Template()
    cairn.set((mx + 1, my, mz), "minecraft:mossy_cobblestone")
    cairn.set((mx + 2, my, mz + 1), "minecraft:cobblestone")
    cairn.set((mx + 1, my + 1, mz), "minecraft:mossy_cobblestone_wall", **wall)
    cairn.save(MAIN / "data/wayfarer/structure/shrine/marker/cairn.nbt", size=MARKER_SIZE)
    stone = Template()
    box(stone, (mx + 2, my, mz - 1), (mx + 2, my + 1, mz - 1), "minecraft:mossy_stone_bricks")
    stone.set((mx + 2, my + 2, mz - 1), "minecraft:mossy_stone_brick_wall", **wall)
    stone.set((mx + 2, my, mz), "minecraft:vine", east=False, north=True, south=False, up=False, west=False)
    stone.save(MAIN / "data/wayfarer/structure/shrine/marker/standing_stone.nbt", size=MARKER_SIZE)
    ring = Template()
    center = (mx, my, mz + 3)
    ring.set(center, "minecraft:campfire", facing="north", lit=False, signal_fire=False, waterlogged=False)
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
        ring.set((center[0] + dx, my - 1, center[2] + dz), "minecraft:cobblestone")
    ring.save(MAIN / "data/wayfarer/structure/shrine/marker/fire_ring.nbt", size=MARKER_SIZE)


def last_camp():
    t = Template()
    box(t, (0, 0, 0), (8, 0, 8), "minecraft:coarse_dirt")
    for x, z in ((0, 0), (8, 0), (0, 8), (8, 8), (1, 0), (0, 1)):
        t.set((x, 0, z), "minecraft:grass_block", snowy=False)
    t.set(
        (4, 1, 4),
        "minecraft:campfire",
        facing="north",
        lit=False,
        signal_fire=False,
        waterlogged=False,
    )
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        t.set((4 + dx, 0, 4 + dz), "minecraft:cobblestone")
    t.set(
        (6, 1, 4),
        "comforts:sleeping_bag_brown",
        facing="east",
        occupied=False,
        part="foot",
    )
    t.set(
        (7, 1, 4),
        "comforts:sleeping_bag_brown",
        facing="east",
        occupied=False,
        part="head",
    )
    t.set((2, 0, 6), "minecraft:rooted_dirt")
    t.set((2, 0, 7), "minecraft:rooted_dirt")
    t.set(
        (2, 1, 5),
        "minecraft:mossy_cobblestone_wall",
        east="none",
        north="none",
        south="none",
        up=True,
        waterlogged=False,
        west="none",
    )
    lines = ["Here rests", "the last", "Wayfarer.", "Carry the ember."]
    sign = {
        "back_text": {
            "color": "black",
            "has_glowing_text": Byte(0),
            "messages": [text("")] * 4,
        },
        "front_text": {
            "color": "black",
            "has_glowing_text": Byte(0),
            "messages": [text(line) for line in lines],
        },
        "id": "minecraft:sign",
        "is_waxed": Byte(1),
    }
    t.set((2, 1, 4), "minecraft:spruce_sign", entity=sign, rotation=8, waterlogged=False)
    journal = [
        "I do not know your name, stranger, and you will not learn mine. It does not matter. Wayfarers have no names, only roads.",
        "The ember I gave you is the last light of the Eye. It pulls towards the twelve shards. Follow it.",
        "Never stay. Never build. The gaze finds what stays still. Sleep in your tent, keep your fire lit, and move on.",
        "Gather the twelve before the Gazebound do. Then find the true gate, and finish what we could not.",
    ]
    lectern(
        t,
        (6, 1, 6),
        "west",
        "The Last Wayfarer's Journal",
        "The Last Wayfarer",
        journal,
    )
    t.save(MAIN / "data/wayfarer/structure/last_camp.nbt", size=[CAMP, 3, CAMP])


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
    for index, eye in enumerate(sorted(eyes)):
        x = SHOWROOM_SPACING * (index + 1)
        center = x + CHUNK // 2
        name = json.dumps(json.dumps(f"{eye.title()} Eye"))
        blank = json.dumps(json.dumps(""))
        lines += [
            f"/tp @s {center} {y + 20} {row}",
            "settle",
            f"/place structure wayfarer:shrine/{eye} {x} {y} {SHOWROOM_ROW_Z}",
            f"/setblock {center} {y} {row - HALL_LENGTH // 2 - 2} minecraft:oak_sign[rotation=8]"
            + f"{{front_text:{{messages:[{blank},{name},{blank},{blank}]}}}}",
        ]
    lines += ["wait 20", f"/tp @s 0.5 {y} 8.5 180 0"]
    write_json(DEV / "wayfarer/showroom.json", {"steps": lines})
    write_json(DEV / "assets/showroom/lang/en_us.json", {"travelerstitles.wayfarer.showroom": "Showroom"})
    write_json(
        DEV / "data/wayfarer/dimension/showroom.json",
        {
            "generator": {
                "settings": {
                    "biome": "minecraft:plains",
                    "features": False,
                    "lakes": False,
                    "layers": [{"block": block, "height": height} for block, height in SHOWROOM_LAYERS],
                    "structure_overrides": [],
                },
                "type": "minecraft:flat",
            },
            "type": "minecraft:overworld",
        },
    )


def rgba(color):
    return tuple(int(color[i : i + 2], 16) for i in (1, 3, 5)) + (255,)


def textures(spec):
    for texture in spec.values():
        for variant, colors in texture["variants"].items():
            palette = {key: rgba(value) for key, value in {**texture["colors"], **colors}.items()}
            pixels = [[palette.get(cell, (0, 0, 0, 0)) for cell in row] for row in texture["rows"]]
            png.write(MAIN / f"assets/wayfarer/textures/item/{variant}.png", pixels)


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
        entry["type"] = "checkmark"
    elif "item" in spec:
        entry |= {"item": {"count": 1, "id": spec["item"]}, "type": "item"}
    elif "structure" in spec:
        entry |= {"structure": spec["structure"], "type": "structure"}
    elif "dimension" in spec:
        entry |= {"dimension": spec["dimension"], "type": "dimension"}
    elif "kill" in spec:
        entry |= {"entity": spec["kill"], "type": "kill", "value": 1}
    else:
        raise ValueError(f"unknown task {spec}")
    return entry


def reward(spec, owner, index):
    return {"id": quest_id("reward", f"{owner}/{index}"), "item": {"count": 1, "id": spec["item"]}, "type": "item"}


def eye_quests(eyes, tier, spec):
    names = [eye for eye in sorted(eyes) if eyes[eye]["tier"] == tier]
    for index, eye in enumerate(names):
        yield {
            "after": spec["after"],
            "description": [eyes[eye]["hint"]],
            "icon": f"endrem:{eye}_eye",
            "id": f"eye_{eye}",
            "subtitle": f"The {eye.title()} Eye",
            "tasks": [{"structure": f"wayfarer:shrine/{eye}"}, {"item": f"endrem:{eye}_eye"}],
            "title": eyes[eye]["people"],
            "x": (index - (len(names) - 1) / 2) * QUEST_SPACING,
            "y": spec["y"],
        }


def quests(book, eyes):
    lang = {f"file.{1:016X}.title": book["title"]}
    tiers = {tier: [f"eye_{eye}" for eye in sorted(eyes) if eyes[eye]["tier"] == tier] for tier in (1, 2)}
    for order, chapter in enumerate(book["chapters"]):
        chapter_id = quest_id("chapter", chapter["id"])
        lang[f"chapter.{chapter_id}.title"] = chapter["title"]
        lang[f"chapter.{chapter_id}.chapter_subtitle"] = chapter["subtitle"]
        entries = chapter.get("quests", []) + list(eye_quests(eyes, chapter.get("eyes"), chapter.get("eye_quests", {})))
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
            if spec.get("rewards"):
                entry["rewards"] = [reward(item, spec["id"], index) for index, item in enumerate(spec["rewards"])]
            written.append(entry)
            lang[f"quest.{identifier}.title"] = spec["title"]
            lang[f"quest.{identifier}.quest_subtitle"] = spec["subtitle"]
            lang[f"quest.{identifier}.quest_desc"] = spec["description"]
        data = {
            "default_hide_dependency_lines": False,
            "default_quest_shape": "",
            "filename": chapter["id"],
            "group": "",
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


def books(spec):
    modifiers = sorted(
        f"wayfarer:{path.stem}"
        for path in (ROOT / "mod/src/main/resources/data/wayfarer/loot_modifiers").glob("*.json")
    )
    for collection in spec["collections"]:
        entries = [
            {
                "functions": [
                    {
                        "components": {
                            "minecraft:written_book_content": {
                                "author": collection["author"],
                                "pages": [text(page) for page in book["pages"]],
                                "title": book["title"],
                            }
                        },
                        "function": "minecraft:set_components",
                    }
                ],
                "name": "minecraft:written_book",
                "type": "minecraft:item",
            }
            for book in collection["books"]
        ]
        write_json(
            MAIN / f"data/wayfarer/loot_table/books/{collection['id']}.json",
            {
                "pools": [
                    {
                        "conditions": [{"chance": collection["chance"], "condition": "minecraft:random_chance"}],
                        "entries": entries,
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


def worldgen(eyes, balance):
    for eye, spec in eyes.items():
        key = f"wayfarer:shrine/{eye}"
        write_json(
            MAIN / f"data/wayfarer/tags/worldgen/biome/has_structure/shrine/{eye}.json",
            {"values": [{"id": biome, "required": False} for biome in spec["biomes"]]},
        )
        write_json(
            MAIN / f"data/wayfarer/worldgen/structure/shrine/{eye}.json",
            {
                "biomes": f"#wayfarer:has_structure/shrine/{eye}",
                "layout": {
                    "cover": COVER,
                    "entrance": [AISLE, 2, HALL_LENGTH],
                    "max_steps": MAX_STEPS,
                    "mouth": list(MOUTH),
                    "shaft": [AISLE, HALL_CEILING + 1, PEDESTAL_Z],
                    "stair": list(STAIR),
                },
                "pieces": {
                    "cap": "wayfarer:shrine/cap",
                    "hall": f"wayfarer:shrine/{eye}/hall",
                    "marker": f"wayfarer:shrine/marker/{spec['marker']}",
                    "open_step": f"wayfarer:shrine/{eye}/open_step",
                    "seal": f"wayfarer:shrine/{eye}/seal",
                    "shaft": "wayfarer:shrine/shaft",
                    "step": f"wayfarer:shrine/{eye}/step",
                },
                "spawn_overrides": {},
                "step": "underground_structures",
                "terrain_adaptation": "none",
                "type": "wayfarer:shrine",
            },
        )
        spacing = spec["spacing"]
        write_json(
            MAIN / f"data/wayfarer/worldgen/structure_set/shrine/{eye}.json",
            {
                "placement": {
                    "salt": salt(key),
                    "separation": spacing // 2,
                    "spacing": spacing,
                    "type": "minecraft:random_spread",
                },
                "structures": [{"structure": key, "weight": 1}],
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
    names = sorted(eyes)
    write_json(
        MAIN / "data/wayfarer/tags/worldgen/structure/shrines.json",
        {"values": [f"wayfarer:shrine/{eye}" for eye in names]},
    )
    for tier, name in ((1, "tier_one"), (2, "tier_two")):
        write_json(
            MAIN / f"data/wayfarer/tags/worldgen/structure/shrines/{name}.json",
            {"values": [f"wayfarer:shrine/{eye}" for eye in names if eyes[eye]["tier"] == tier]},
        )
    write_json(
        MAIN / "data/wayfarer/tags/item/eye_shards.json",
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


def spread(eyes, balance):
    structures = balance["structures"]
    unspread = sorted(structures["unspread"] + [f"wayfarer:shrine/{eye}" for eye in eyes])
    write_json(
        SPARSE_STRUCTURES,
        {
            "customSpreadFactors": [{"factor": 1, "structure": name} for name in unspread],
            "idBasedSalt": True,
            "spreadFactor": structures["spread_factor"],
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


def main():
    eyes = tomllib.loads((ROOT / "generator/eyes.toml").read_text())
    balance = tomllib.loads((ROOT / "balance.toml").read_text())
    for directory in (DEV, ENDREM_EYES, MAIN, QUESTS):
        shutil.rmtree(directory, ignore_errors=True)
    worldgen(eyes, balance)
    spread(eyes, balance)
    removals(tomllib.loads((ROOT / "generator/removals.toml").read_text()))
    for eye, spec in eyes.items():
        hall(eye, spec)
        steps(eye, spec)
    shaft()
    markers()
    last_camp()
    empty()
    showroom(eyes)
    textures(tomllib.loads((ROOT / "generator/textures.toml").read_text()))
    quests(tomllib.loads((ROOT / "story/quests.toml").read_text()), eyes)
    books(tomllib.loads((ROOT / "story/books.toml").read_text()))
    shaders()


main()

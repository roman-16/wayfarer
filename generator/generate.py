import hashlib
import json
import math
import shutil
from pathlib import Path

import enchantments
import fonts
import png
import poses
import spoken
import stamp
import tomllib
import tweaks
from act3 import HOUSE as ACT3_HOUSE
from act3 import airfield as act3_airfield
from act3 import gatehouse as act3_gatehouse
from act3 import graves as act3_graves
from act3 import house as act3_house
from act3 import positions as act3_positions
from blocks import LAMP, Template, box, cuboid, text
from camp import FIRE, GRAVE, HEADSTONE, OLD_SIZE, STRANGER, WAKE, camp, grave, old_camp
from camp import SIZE as CAMP
from caravan import caravan
from creatures import deep, great_worm
from diggers import entrance, layout, tunnels
from entrances import ENTRANCES, Cover
from errands import RUIN as ERRANDS_RUIN
from errands import cairns as errands_cairns
from errands import nest as errands_nest
from errands import positions as errands_positions
from errands import ruin as errands_ruin
from first_camp import SIZE as FIRST_CAMP_SIZE
from first_camp import first_camp
from first_camp import positions as first_camp_positions
from first_village import houses as first_village_houses
from first_village import pit as first_village_pit
from first_village import positions as first_village_positions
from frost import HOUSE as FROST_HOUSE
from frost import house as frost_house
from frost import lake as frost_lake
from frost import positions as frost_positions
from halls import HALLS
from locks import HOUSE as LOCKS_HOUSE
from locks import house as locks_house
from locks import positions as locks_positions
from marsh import carved, hut, way
from marsh import layout as marsh_layout
from marsh import size as marsh_size
from mine import deep as mine_deep
from mine import head as mine_head
from mine import positions as mine_positions
from mine import stage as mine_stage
from reading_house import house as reading_house
from reading_house import positions as reading_house_positions
from reading_house import vault as reading_house_vault
from sand import CAMP as SAND_CAMP
from sand import camp as sand_camp
from sand import positions as sand_positions
from scenes import SCENES
from shipyard import WRECK as SHIPYARD_WRECK
from shipyard import YARD as SHIPYARD
from shipyard import positions as shipyard_positions
from shipyard import rest as shipyard_rest
from shipyard import wreck as shipyard_wreck
from shipyard import yard as shipyard_yard
from skins import skin
from tide import VILLAGE as TIDE_VILLAGE
from tide import positions as tide_positions
from tide import village as tide_village
from tollbridge import PIECES, plan, size, tower
from townsfolk import (
    act3folk,
    brother_of,
    coastfolk,
    drownedfolk,
    errandfolk,
    founders,
    frostfolk,
    girl_in_grey,
    heir_of,
    keeperfolk,
    riverfolk,
    roadfolk,
    sandfolk,
    tidefolk,
    townsfolk,
)
from wend_camp import SIZE as WEND_CAMP_SIZE
from wend_camp import positions as wend_camp_positions
from wend_camp import wend_camp

ROOT = Path(__file__).resolve().parent.parent
TICKS_PER_SECOND = 20
MAIN = ROOT / "mod/src/generated/resources"
DEV = ROOT / "mod/src/generated/dev"
ENDREM_EYES = ROOT / "pack/config/EndRemastered-NeoForge/Eyes"
IRIS = ROOT / "pack/config/iris.properties"
SHADERPACKS = ROOT / "pack/shaderpacks"
LANG = ROOT / "mod/src/main/resources/assets/wayfarer/lang/en_us.json"
SPARSE_STRUCTURES = ROOT / "pack/config/sparsestructures.json5"
CAMPFIRE_SPAWNS = ROOT / "pack/config/nohostilesaroundcampfire.json5"
CHUNK = 16
CLOUD_LAYERS_APART = 64
CONTAINER_LOOT = "container_loot"
CUT_LOOT = "cut"
ACT_NAMES = {2: "act_two", 3: "act_three"}

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
SHOWROOM_OLD_CAMPS = {
    "first_camp/now": ("His First Camp", -16, FIRST_CAMP_SIZE),
    "old_camp/last_camp": ("His Last Camp", 8, OLD_SIZE),
}
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
    stone = next(pos for pos, block in room.t.blocks.items() if block[0] == "wayfarer:calling_stone")
    target = (stone[0] + 0.5, stone[1] + 1.2, stone[2] + 0.5)
    middle = length // 2
    wall = next((x for x in range(width // 2, -1, -1) if (x, floor + 1, middle) not in room.open), 0)
    looks = {
        "hall": ((door_x, floor, door_z - 1), target),
        "high": ((door_x, floor + height - 7, length - 4), target),
        "stone": ((stone[0], stone[1], stone[2] + 3), target),
        "wall": ((wall + 4, floor, middle), (wall + 0.5, floor + 2, middle + 0.5)),
        "corner": ((width // 2, floor + 1, middle), (width - 1.5, floor + height - 4, 1.5)),
        "ceiling": ((width // 2, floor, middle), (width // 2 + 0.5, height, middle - 3.5)),
    }
    views = {name: (opening(room, at, look), look) for name, (at, look) in looks.items()}
    return [
        {"at": [at[0] + 0.5, at[1], at[2] + 0.5], "look": list(look), "name": name}
        for name, (at, look) in views.items()
    ]


def halls(eyes):
    for eye, spec in eyes.items():
        room, layout = HALLS[eye](eye, spec)
        stone = next(pos for pos, block in room.t.blocks.items() if block[0] == "wayfarer:calling_stone")
        layout = layout | {"cameras": cameras(room, layout), "stone": list(stone), "size": list(room.size)}
        room.save(MAIN / f"data/wayfarer/structure/hall/{eye}/hall.nbt")
        cover = Cover(layout["shaft"]["style"], eye)
        ENTRANCES[eye](cover, room.shell)
        cover.save(MAIN / f"data/wayfarer/structure/hall/{eye}/entrance.nbt")
        if "oculus" in layout:
            seal = Template()
            seal.set((0, 0, 0), room.shell)
            seal.save(MAIN / f"data/wayfarer/structure/hall/{eye}/skylight_seal.nbt")
            cap = Template()
            for y, block in enumerate(layout["oculus"]["cap"]):
                cap.set(
                    (0, y, 0), block, east="none", north="none", south="none", up=True, waterlogged=False, west="none"
                )
            cap.save(MAIN / f"data/wayfarer/structure/hall/{eye}/skylight_cap.nbt")
        yield eye, layout
    column = Template()
    column.set((0, 0, 0), "minecraft:air")
    column.save(MAIN / "data/wayfarer/structure/hall/skylight.nbt")


def empty():
    t = Template()
    box(t, (0, 0, 0), (7, 7, 7), "minecraft:air")
    t.save(DEV / "data/wayfarer/structure/empty.nbt")


def showroom(eyes):
    lang = json.loads(LANG.read_text())
    models = ROOT / "mod/src/main/resources/assets/wayfarer/models/item"
    items = [f"endrem:{eye}_eye" for eye in sorted(eyes)] + sorted(
        f"wayfarer:{key.split('.', 2)[2]}"
        for key in lang
        if key.startswith(("block.wayfarer.", "item.wayfarer.")) and (models / f"{key.split('.', 2)[2]}.json").exists()
    )
    y = WORLD_BOTTOM + sum(height for _, height in SHOWROOM_LAYERS)
    row = SHOWROOM_ROW_Z + CHUNK // 2
    half = len(items) // 2 + 1
    lines = [
        "settle",
        "/gamemode creative",
        f"/execute in wayfarer:showroom run tp @s 0.5 {y + 20} 0.5",
        "settle",
        "/gamerule doDaylightCycle false",
        "/gamerule wayfarerStory false",
        "/gamerule doMobSpawning false",
        "/gamerule doWeatherCycle false",
        "/time set noon",
        "/weather clear",
        f"/place template wayfarer:last_camp {-(CAMP // 2)} {y - 1} {-(CAMP // 2)}",
        f"/place template wayfarer:last_camp/grave {GRAVE[0] - CAMP // 2} {y - 1} {GRAVE[1] - CAMP // 2}",
        f"/fill {-half} {y} {SHOWROOM_WALL_Z} {half} {y + 3} {SHOWROOM_WALL_Z} minecraft:polished_andesite",
    ]
    for index, item in enumerate(items):
        lines.append(
            f"/summon minecraft:item_frame {index - len(items) // 2} {y + 1} {SHOWROOM_WALL_Z + 1} "
            + f'{{Facing:3b,Fixed:1b,Item:{{id:"{item}",count:1}}}}'
        )
    blank = json.dumps(json.dumps(""))
    for template, (title, x, width) in SHOWROOM_OLD_CAMPS.items():
        name = json.dumps(json.dumps(title))
        lines += [
            f"/place template wayfarer:{template} {x} {y - 1} {SHOWROOM_OLD_CAMP_Z}",
            f"/setblock {x + width // 2} {y} {SHOWROOM_OLD_CAMP_Z - 2} minecraft:oak_sign[rotation=8]"
            + f"{{front_text:{{messages:[{blank},{name},{blank},{blank}]}}}}",
        ]
    for index, eye in enumerate(sorted(eyes)):
        x = SHOWROOM_SPACING * (index + 1)
        center = x + CHUNK // 2
        name = json.dumps(json.dumps(f"{eye.title()} Eye"))
        lines += [
            f"/tp @s {center} {y + 20} {row}",
            "settle",
            f"/place structure wayfarer:hall/{eye} {x} {y} {SHOWROOM_ROW_Z}",
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
        "grave": [GRAVE[0] + HEADSTONE[0] - CAMP // 2, GRAVE[1] + HEADSTONE[1] - CAMP // 2],
        "target": {"at": [target[0], y, target[1]]},
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
        "grave": ((6.5, 1, 14.5), (GRAVE[0] + HEADSTONE[0] + 0.5, 1.5, GRAVE[1] + HEADSTONE[1] + 0.5)),
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
            "halls": [
                {
                    "cameras": layouts[eye]["cameras"],
                    "centre": [SHOWROOM_SPACING * (index + 1) + CHUNK // 2, SHOWROOM_ROW_Z + CHUNK // 2],
                    "door": list(layouts[eye]["door"]),
                    "eye": eye,
                    "stone": layouts[eye]["stone"],
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


def marked(texture):
    width, height = texture["size"]
    rows = [["."] * width for _ in range(height)]
    for letter, points in texture["marks"].items():
        for x, y in points:
            rows[y][x] = letter
    return rows


def textures(spec):
    for texture in spec.values():
        for variant, colors in texture["variants"].items():
            palette = {key: rgba(value) for key, value in {**texture["colors"], **colors}.items()}
            pixels = [
                [palette.get(cell, (0, 0, 0, 0)) for cell in row] for row in texture.get("rows") or marked(texture)
            ]
            target = MAIN / f"assets/wayfarer/textures/{texture.get('folder', 'item')}/{variant}.png"
            png.write(target, pixels)
            if "scaling" in texture:
                write_json(target.with_name(f"{variant}.png.mcmeta"), {"gui": {"scaling": texture["scaling"]}})


def story(quests, cast, skins, caravan_households):
    for person, spec in cast.items():
        if person not in skins and not spec.get("road"):
            raise ValueError(f"story/people.toml: {person} has no skin in generator/skins.toml")
    write_json(
        MAIN / "wayfarer/story.json",
        {
            "people": {
                person: spec | ({} if spec.get("road") else {"skin": person}) for person, spec in sorted(cast.items())
            },
            "caravan": caravan_households["households"],
            "epilogue": quests["epilogue"],
            "memories": quests["memories"],
            "quests": quests["quests"],
        },
    )


def shard_names(eyes):
    write_json(
        MAIN / "assets/endrem/lang/en_us.json",
        {
            f"item.endrem.{eye}_eye": spec["people"] + ("' Shard" if spec["people"].endswith("s") else "'s Shard")
            for eye, spec in eyes.items()
        },
    )


def sounds(spec):
    effects = {name: effect for name, effect in spec["effects"].items() if "replaces" not in effect}
    taken = {}
    for name, effect in spec["effects"].items():
        if "replaces" in effect:
            namespace, event = effect["replaces"].split(":")
            taken.setdefault(namespace, {})[event] = {
                "replace": True,
                "sounds": [{"name": f"wayfarer:effects/{name}"}],
                "subtitle": f"subtitles.wayfarer.{name}",
            }
    for namespace, events in taken.items():
        write_json(MAIN / f"assets/{namespace}/sounds.json", events)
    write_json(
        MAIN / "assets/wayfarer/sounds.json",
        {
            **{
                f"story_music.{piece}": {"sounds": [{"name": f"wayfarer:music/{piece}", "stream": True}]}
                for piece in spec["music"]
            },
            **{
                f"{creature}.{kind}": {
                    "sounds": [{"name": event, "type": "event"}],
                    "subtitle": f"subtitles.wayfarer.{creature}.{kind}",
                }
                for creature, voices in spec["creatures"].items()
                for kind, event in voices.items()
            },
            **{
                name: {
                    "sounds": [
                        {"name": f"wayfarer:effects/{name}"}
                        | ({"attenuation_distance": effect["reach"]} if "reach" in effect else {})
                    ],
                    "subtitle": f"subtitles.wayfarer.{name}",
                }
                for name, effect in effects.items()
            },
        },
    )
    write_json(
        MAIN / "wayfarer/sounds.json",
        {
            name: {"slider": effect["slider"]}
            | ({"reach": effect["reach"]} if "reach" in effect else {})
            | ({"loop_ticks": effect_ticks(name)} if effect["kind"] == "loop" else {})
            for name, effect in effects.items()
        },
    )


def credits(spec, faces, tweaked):
    def line(entry):
        return f"- {entry['credit']}. {entry['licence']}. {entry['page']}"

    sections = {
        "Music": [line(piece) for _, piece in sorted(spec["music"].items())],
        "Sounds": [line(effect) for _, effect in sorted(spec["effects"].items())],
        "Fonts": [
            f"- {face['name']}, by {face['author']}. {face['licence']}. {face['url']}"
            for _, face in sorted(faces.items())
        ],
        "Textures and recipes": [f"- {tweaked['credit']}"],
    }
    (ROOT / "pack/CREDITS.md").write_text(
        "# Credits\n"
        + "".join(f"\n{name} in the Wayfarer mod:\n\n" + "\n".join(lines) + "\n" for name, lines in sections.items())
    )


def effect_ticks(name):
    data = (MAIN.parent.parent / "main/resources/assets/wayfarer/sounds/effects" / f"{name}.ogg").read_bytes()
    header = data.index(b"\x01vorbis")
    rate = int.from_bytes(data[header + 12 : header + 16], "little")
    last = data.rindex(b"OggS")
    samples = int.from_bytes(data[last + 6 : last + 14], "little")
    return round(samples / rate * TICKS_PER_SECOND)


def people(spec):
    for name, look in spec.items():
        png.write(MAIN / f"assets/wayfarer/textures/entity/people/{name}.png", skin(name, look))
    write_json(
        MAIN / "wayfarer/skins.json",
        {name: {"model": f"easy_npc:{look['model']}", "scale": look["scale"]} for name, look in sorted(spec.items())},
    )


def library(spec, eyes):
    entries = {}
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


def old_camps(balance, written):
    old_camp().save(MAIN / "data/wayfarer/structure/old_camp/last_camp.nbt")
    for now, name in ((True, "now"), (False, "then")):
        camp = first_camp(now=now)
        camp.save(
            MAIN / f"data/wayfarer/structure/first_camp/{name}.nbt",
            [FIRST_CAMP_SIZE, max(y for _, y, _ in camp.blocks) + 1, FIRST_CAMP_SIZE],
        )
    write_json(MAIN / "wayfarer/first_camp.json", first_camp_positions())
    frost_house().save(MAIN / "data/wayfarer/structure/frost/house.nbt", list(FROST_HOUSE))
    frost_lake().save(MAIN / "data/wayfarer/structure/frost/lake.nbt")
    mine_head().save(MAIN / "data/wayfarer/structure/frost/mine_head.nbt")
    mine_stage().save(MAIN / "data/wayfarer/structure/frost/mine_stage.nbt")
    mine_deep(balance["forge"]["vein_lumps"]).save(MAIN / "data/wayfarer/structure/frost/mine_deep.nbt")
    write_json(MAIN / "wayfarer/frost.json", frost_positions() | {"mine": mine_positions()})
    reading_house().save(MAIN / "data/wayfarer/structure/reading_house/house.nbt")
    reading_house_vault().save(MAIN / "data/wayfarer/structure/reading_house/vault.nbt")
    write_json(MAIN / "wayfarer/reading_house.json", reading_house_positions())
    for name, template in first_village_houses().items():
        template.save(MAIN / f"data/wayfarer/structure/{name}.nbt")
    first_village_pit().save(MAIN / "data/wayfarer/structure/first_village/pit.nbt")
    write_json(MAIN / "wayfarer/first_village.json", first_village_positions())
    shipyard, unfinished = shipyard_yard()
    shipyard.save(MAIN / "data/wayfarer/structure/shipyard/yard.nbt", list(SHIPYARD))
    shipyard_rest(unfinished).save(MAIN / "data/wayfarer/structure/shipyard/hull.nbt", list(SHIPYARD))
    shipyard_wreck().save(MAIN / "data/wayfarer/structure/shipyard/wreck.nbt", list(SHIPYARD_WRECK))
    write_json(MAIN / "wayfarer/shipyard.json", shipyard_positions())
    for now, name in ((True, "now"), (False, "then")):
        camp = wend_camp(now=now)
        camp.save(MAIN / f"data/wayfarer/structure/wend_camp/{name}.nbt", [WEND_CAMP_SIZE, 6, WEND_CAMP_SIZE])
    write_json(MAIN / "wayfarer/wend_camp.json", wend_camp_positions())
    errands_ruin(next(book for book in written["found"] if book["id"] == "keepers/wend")).save(
        MAIN / "data/wayfarer/structure/errands/seal.nbt", list(ERRANDS_RUIN)
    )
    errands_cairns().save(MAIN / "data/wayfarer/structure/errands/cairns.nbt")
    errands_nest().save(MAIN / "data/wayfarer/structure/errands/nest.nbt")
    act3_airfield().save(MAIN / "data/wayfarer/structure/act3/airfield.nbt")
    act3_gatehouse().save(MAIN / "data/wayfarer/structure/act3/gatehouse.nbt")
    act3_graves().save(MAIN / "data/wayfarer/structure/act3/graves.nbt")
    act3_house(next(book for book in written["found"] if book["id"] == "wend/orders")).save(
        MAIN / "data/wayfarer/structure/act3/house.nbt", list(ACT3_HOUSE)
    )
    write_json(MAIN / "wayfarer/act3.json", act3_positions())
    write_json(MAIN / "wayfarer/errands.json", errands_positions())
    locks_house(next(book for book in written["found"] if book["id"] == "jungle/prayer")).save(
        MAIN / "data/wayfarer/structure/locks/house.nbt", list(LOCKS_HOUSE)
    )
    write_json(MAIN / "wayfarer/locks.json", locks_positions())
    sand_camp().save(MAIN / "data/wayfarer/structure/sand/camp.nbt", list(SAND_CAMP))
    write_json(MAIN / "wayfarer/sand.json", sand_positions())
    tide, doors = tide_village()
    tide.save(MAIN / "data/wayfarer/structure/tide/village.nbt", list(TIDE_VILLAGE))
    write_json(MAIN / "wayfarer/tide.json", tide_positions(doors))


def books(spec, eyes, entries):
    modifiers = sorted(
        f"wayfarer:{path.stem}"
        for path in (ROOT / "mod/src/main/resources/data/wayfarer/loot_modifiers").glob("*.json")
        if path.stem not in (CONTAINER_LOOT, CUT_LOOT)
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
        {"entries": [f"wayfarer:{CONTAINER_LOOT}", *sorted(modifiers), f"wayfarer:{CUT_LOOT}"], "replace": False},
    )
    write_json(
        MAIN / "wayfarer/library.json",
        {
            "books": {
                identifier: {"author": entry["author"], "pages": entry["pages"], "title": entry["title"]}
                for identifier, entry in entries.items()
            },
            "people": {f"wayfarer:hall/{eye}": f"people/{eye}" for eye in sorted(eyes)},
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
    for stale in SHADERPACKS.glob("*.txt"):
        stale.unlink()
    options = tomllib.loads((ROOT / "generator/shader.toml").read_text())
    sky = tomllib.loads((ROOT / "balance.toml").read_text())["sky"]
    options |= {
        "CLOUD_ALT1": str(sky["cloud_height"]),
        "CLOUD_ALT2": str(sky["cloud_height"] + CLOUD_LAYERS_APART),
        "MOON_PHASE_DARK": f"{sky['moon_new']:.2f}",
        "MOON_PHASE_FULL": f"{sky['moon_full']:.2f}",
        "MOON_PHASE_PARTIAL": f"{sky['moon_partial']:.2f}",
    }
    (SHADERPACKS / f"{pack}.txt").write_text("".join(f"{key}={value}\n" for key, value in sorted(options.items())))


def labelled_faces(spec):
    for group in spec.values():
        for name, face in group.items():
            sources = {"default", "person", "texture"} & face.keys()
            if len(sources) != 1:
                raise SystemExit(
                    f"generator/faces.toml: {name} names {sorted(sources)}; it needs one of default, person, texture"
                )
            if len(face["eyes"]) != 8 or any(len(row) != 8 or set(row) - set(".iwIW?") for row in face["eyes"]):
                raise SystemExit(f"generator/faces.toml: {name}'s eyes are not 8 rows of 8 of . i w I W ?")
    write_json(DEV / "wayfarer/faces.json", spec)


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n")


def salt(name):
    return int.from_bytes(hashlib.sha256(name.encode()).digest()[:4], "big") & 0x7FFFFFFF


def states(value):
    if isinstance(value, list):
        return [{"Name": name} for name in value]
    return {"Name": value, "Properties": {"lit": "true"}} if value == LAMP else {"Name": value}


def acts_bands(balance):
    world = balance["world"]
    far = balance["halls"]["act_three_from_blocks"]
    coast = balance["strongholds"]["radius_blocks"] + world["last_sea_past_ring_blocks"]
    return {2: (world["home_blocks"] + world["sea_blocks"], far), 3: (far, coast)}


def worldgen(eyes, layouts, acts, balance):
    for eye, spec in eyes.items():
        layout = {"cover": COVER, "door": list(layouts[eye]["door"])}
        if "oculus" in layouts[eye]:
            layout["oculus"] = {
                "at": list(layouts[eye]["oculus"]["at"]),
                "cap": f"wayfarer:hall/{eye}/skylight_cap",
                "column": "wayfarer:hall/skylight",
                "seal": f"wayfarer:hall/{eye}/skylight_seal",
            }
        write_json(
            MAIN / f"data/wayfarer/tags/worldgen/biome/has_structure/hall/{eye}.json",
            {"values": [{"id": biome, "required": False} for biome in spec["biomes"]]},
        )
        write_json(
            MAIN / f"data/wayfarer/worldgen/structure/hall/{eye}.json",
            {
                "biomes": f"#wayfarer:has_structure/hall/{eye}",
                "climate": spec["climate"],
                "layout": layout,
                "pieces": {"entrance": f"wayfarer:hall/{eye}/entrance", "hall": f"wayfarer:hall/{eye}/hall"},
                "shaft": {
                    key: value if key == "style" else states(value) for key, value in layouts[eye]["shaft"].items()
                },
                "spawn_overrides": {},
                "step": "underground_structures",
                "terrain_adaptation": "none",
                "type": "wayfarer:hall",
            },
        )
        write_json(
            ENDREM_EYES / f"{eye}_eye.json",
            {
                "id": f"{eye}_eye",
                "loot_tables_id": [],
                "loot_to_inject_id": "minecraft:empty",
                "rarity": "epic" if spec["act"] == 3 else "rare",
            },
        )
        write_json(
            MAIN / f"data/wayfarer/advancement/shards/{eye}.json",
            {"criteria": {"taken": {"trigger": "minecraft:impossible"}}},
        )
    names = sorted(eyes)
    write_json(
        MAIN / "data/wayfarer/tags/worldgen/structure/halls.json",
        {"values": [f"wayfarer:hall/{eye}" for eye in names]},
    )
    bands = acts_bands(balance)
    for act, name in ACT_NAMES.items():
        members = [f"wayfarer:hall/{eye}" for eye in names if eyes[eye]["act"] == act]
        write_json(MAIN / f"data/wayfarer/tags/worldgen/structure/halls/{name}.json", {"values": members})
        members = [member for member in members if not eyes[member.removeprefix("wayfarer:hall/")].get("story", False)]
        spacing = acts[str(act)]["spacing"]
        write_json(
            MAIN / f"data/wayfarer/worldgen/structure_set/halls/{name}.json",
            {
                "placement": {
                    "from_blocks": bands[act][0],
                    "salt": salt(f"wayfarer:halls/{name}"),
                    "separation": spacing * 3 // 4,
                    "spacing": spacing,
                    "to_blocks": bands[act][1],
                    "type": "wayfarer:band",
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
    unspread = sorted(structures["unspread"] + [f"wayfarer:halls/{name}" for name in ACT_NAMES.values()])
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


def owners(spec, balance):
    every = balance["structures"]["every_blocks"]
    kinds = {}
    for kind, blocks in sorted(every.items()):
        spacing = blocks // CHUNK
        kinds[kind] = {
            "salt": salt(f"wayfarer:owners/{kind}"),
            "separation": spacing // 2,
            "spacing": spacing,
            "structures": spec[kind],
        }
    write_json(MAIN / "wayfarer/owners.json", {"cut": spec["cut"], "kinds": kinds})
    for kind in every:
        write_json(
            MAIN / f"data/wayfarer/tags/worldgen/structure/owners/{kind}.json",
            {"values": [{"id": structure, "required": False} for structure in spec[kind]]},
        )
    write_json(
        MAIN / "data/wayfarer/tags/worldgen/structure/taverns.json",
        {"values": [{"id": structure, "required": False} for structure in spec["tavern"]]},
    )


def removals(spec):
    write_json(
        MAIN / "wayfarer/cuts.json",
        {
            "creatures": spec["creatures"],
            "enchantments": spec["enchantments"],
            "features": spec["features"],
            "items": spec["items"],
        },
    )
    write_json(
        MAIN / "data/c/tags/item/hidden_from_recipe_viewers.json",
        {"replace": False, "values": [{"id": item, "required": False} for item in spec["items"]]},
    )
    for name in ("rootless", "storage"):
        write_json(
            MAIN / f"data/wayfarer/tags/block/{name}.json",
            {"values": [{"id": block, "required": False} for block in spec[name]]},
        )
    for kind, folder in (("loot_tables", "loot_table"), ("recipes", "recipe")):
        for cut in spec[kind]:
            namespace, path = cut.split(":")
            write_json(
                MAIN / f"data/{namespace}/{folder}/{path}.json", {"neoforge:conditions": [{"type": "neoforge:false"}]}
            )


def stores(loot):
    def uniform(span):
        return {"max": span[1], "min": span[0], "type": "minecraft:uniform"}

    entries = [
        {
            "functions": [{"count": uniform(span), "function": "minecraft:set_count"}],
            "name": item,
            "type": "minecraft:item",
        }
        for item, span in sorted(loot["items"].items())
    ]
    return {"pools": [{"entries": entries, "rolls": uniform(loot["rolls"])}], "type": "minecraft:chest"}


def main():
    source = spoken.shown_data(tomllib.loads((ROOT / "generator/eyes.toml").read_text()))
    eyes = source["eyes"]
    balance = tomllib.loads((ROOT / "balance.toml").read_text())
    written = spoken.shown_data(tomllib.loads((ROOT / "story/books.toml").read_text()))
    for directory in (DEV, ENDREM_EYES, MAIN):
        shutil.rmtree(directory, ignore_errors=True)
    layouts = dict(halls(eyes))
    worldgen(eyes, layouts, source["acts"], balance)
    tour(eyes, layouts)
    spread(balance)
    campfire_spawns(balance)
    removals(tomllib.loads((ROOT / "generator/removals.toml").read_text()))
    write_json(MAIN / "wayfarer/foes.json", tomllib.loads((ROOT / "generator/foes.toml").read_text())["foes"])
    write_json(DEV / "wayfarer/noise.json", tomllib.loads((ROOT / "generator/noise.toml").read_text()))
    labelled_faces(tomllib.loads((ROOT / "generator/faces.toml").read_text()))
    owners(tomllib.loads((ROOT / "generator/owners.toml").read_text()), balance)
    nether = tomllib.loads((ROOT / "generator/nether.toml").read_text())
    write_json(MAIN / "wayfarer/creature_swaps.json", nether["creatures"])
    write_json(MAIN / "wayfarer/swaps.json", nether["swaps"])
    camp().save(MAIN / "data/wayfarer/structure/last_camp.nbt")
    grave().save(MAIN / "data/wayfarer/structure/last_camp/grave.nbt")
    write_json(
        MAIN / "wayfarer/last_camp.json",
        {
            "fire": [FIRE[0], 1, FIRE[1]],
            "grave": [GRAVE[0], 0, GRAVE[1]],
            "sign": [HEADSTONE[0], 1, HEADSTONE[1] - 1],
            "stranger": list(STRANGER),
            "wake": list(WAKE),
        },
    )
    tower().save(MAIN / "data/wayfarer/structure/tollbridge/bell_tower.nbt")
    for name, piece in PIECES.items():
        built = piece()
        built.save(MAIN / f"data/wayfarer/structure/tollbridge/{name}.nbt", size(built))
    write_json(MAIN / "wayfarer/tollbridge.json", plan())
    for kind, loot in balance["tollbridge"]["loot"].items():
        write_json(MAIN / f"data/wayfarer/loot_table/chests/tollbridge/{kind}.json", stores(loot))
    entrance().save(MAIN / "data/wayfarer/structure/diggers/entrance.nbt")
    for name, scene in SCENES.items():
        scene().save(MAIN / f"data/wayfarer/structure/scene/{name}.nbt")
    tunnels(eyes["lost"]).save(MAIN / "data/wayfarer/structure/diggers/tunnels.nbt")
    write_json(MAIN / "wayfarer/diggers.json", layout())
    passage = way()
    passage.save(MAIN / "data/wayfarer/structure/marsh/way.nbt")
    hut(carved(passage)).save(MAIN / "data/wayfarer/structure/marsh/hut.nbt", marsh_size())
    write_json(MAIN / "wayfarer/marsh.json", marsh_layout(layouts["witch"], carved(passage)))
    empty()
    caravan(MAIN)
    deep(MAIN)
    great_worm(MAIN)
    showroom(eyes)
    textures(tomllib.loads((ROOT / "generator/textures.toml").read_text()))
    style = tomllib.loads((ROOT / "style.toml").read_text())
    faces = tomllib.loads((ROOT / "generator/fonts.toml").read_text())
    display = faces["display"]
    stamp.write(MAIN, style, fonts.glyphs(display, fonts.TrueType((ROOT / "generator" / display["file"]).read_bytes())))
    write_json(MAIN / "wayfarer/style.json", style)
    fonts.write(MAIN, faces, ROOT / "generator", write_json)
    skins = (
        tomllib.loads((ROOT / "generator/skins.toml").read_text())
        | townsfolk()
        | founders()
        | roadfolk()
        | riverfolk()
        | frostfolk()
        | keeperfolk()
        | coastfolk()
        | tidefolk()
        | drownedfolk()
        | sandfolk()
        | errandfolk()
        | act3folk()
    )
    skins["malgven_child"] = girl_in_grey(skins["malgven"])
    skins["heir"] = heir_of(skins["cadoc"])
    skins["brannoc"] = brother_of(skins["cadoc"])
    skins["wend_reading"] = skins["stranger"]
    skins["newcomer"] = skins["road_07"]

    people(skins)
    poses.write(MAIN)
    story(
        spoken.shown_data(tomllib.loads((ROOT / "story/quests.toml").read_text())),
        spoken.shown_data(tomllib.loads((ROOT / "story/people.toml").read_text())),
        skins,
        tomllib.loads((ROOT / "story/caravan.toml").read_text()),
    )
    write_json(DEV / "wayfarer/states.json", tomllib.loads((ROOT / "story/states.toml").read_text()))
    write_json(MAIN / "wayfarer/gear.json", spoken.shown_data(tomllib.loads((ROOT / "story/gear.toml").read_text())))
    write_json(
        MAIN / "wayfarer/scenes.json",
        spoken.shown_data(tomllib.loads((ROOT / "story/scenes.toml").read_text())["scenes"]),
    )
    write_json(MAIN / "wayfarer/dice.json", spoken.shown_data(tomllib.loads((ROOT / "story/dice.toml").read_text())))
    music = tomllib.loads((ROOT / "story/sounds.toml").read_text())
    tweaked = tomllib.loads((ROOT / "generator/tweaks.toml").read_text())
    sounds(music)
    credits(music, faces, tweaked)
    shard_names(eyes)
    old_camps(balance, written)
    books(written, eyes, library(written, eyes))
    shaders()
    tweaks.write(MAIN, tomllib.loads((ROOT / "generator/nether.toml").read_text()), tweaked)
    enchantments.write(MAIN, tomllib.loads((ROOT / "generator/enchantments.toml").read_text()))
    spoken.write_index()


main()

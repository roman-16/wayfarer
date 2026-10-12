import json
import re
import sys
from pathlib import Path

import tomllib

import gallery

ROOT = Path(__file__).resolve().parent.parent
PACK = ROOT / "pack"
MOD = ROOT / "mod/src"
LANG = MOD / "main/resources/assets/wayfarer/lang/en_us.json"
BOOK_TITLE_LIMIT = 32
PAGE_LIMIT = 256
TITLE_LIMIT = 17
SUBTITLE_LIMIT = 35
SIDES = {"both", "client"}
SIGN_LINES = 4
SIGN_WIDTH = 15
SLIDERS = {"ambient", "blocks", "hostile", "master", "music", "neutral", "players", "record", "voice", "weather"}
CHASE_OUTCOMES = {"caught"}
DUEL_OUTCOMES = {"won"}
ESCORT_OUTCOMES = {"arrived", "late"}
SEA_OUTCOMES = {"held", "sunk"}
WORM_OUTCOMES = {"killed", "spared"}
SOUND_KINDS = {"frequent", "loop", "rare"}
TRANSLATION = re.compile(r'"(wayfarer\.[a-z_.]+[a-z_])"')
INTERFACE = MOD / "main/java/io/github/roman16/wayfarer/client"
STAMP = INTERFACE / "stamp"
COLOR = re.compile(r"\b0x[0-9A-Fa-f]{6,8}\b")


def sides():
    for meta in sorted(PACK.glob("*/*.pw.toml")):
        side = tomllib.loads(meta.read_text()).get("side")
        if side not in SIDES:
            yield f"{meta.relative_to(ROOT)}: side is {side!r}; singleplayer runs the server inside the client, so a mod is either client or both"


def hall_eyes():
    return tomllib.loads((ROOT / "generator/eyes.toml").read_text())["eyes"]


def eyes():
    for eye, spec in sorted(hall_eyes().items()):
        if not spec.get("lore") or not spec.get("people"):
            yield f"the {eye} eye has no people or lore in generator/eyes.toml"
        if not (MOD / f"generated/resources/data/wayfarer/structure/hall/{eye}/hall.nbt").exists():
            yield f"the {eye} eye has no hall; run just generate"


def translations():
    known = set(json.loads(LANG.read_text()))
    for source in sorted(MOD.glob("*/java/**/*.java")):
        for key in TRANSLATION.findall(source.read_text()):
            if key not in known and not key.endswith("."):
                yield f"{source.relative_to(ROOT)} uses {key}, which {LANG.relative_to(ROOT)} does not name"


def titles():
    lang = json.loads(LANG.read_text())
    shown = {key: text for key, text in lang.items() if key.startswith("wayfarer.night.")}
    shown |= {f"the people of the {eye} eye": spec["people"] for eye, spec in hall_eyes().items()}
    for key, text in sorted(shown.items()):
        limit = (
            TITLE_LIMIT
            if key.endswith(".title")
            else SUBTITLE_LIMIT
            if key.endswith(".subtitle") or " " in key
            else None
        )
        if limit and len(text) > limit:
            yield f"{key} ({text!r}) is {len(text)} characters; a screen title fits {limit}"


def recipes():
    nether = tomllib.loads((ROOT / "generator/nether.toml").read_text())
    unsourced = set(nether["unsourced_items"]) | {"#" + tag for tag in nether["unsourced_tags"]}
    for recipe in sorted(MOD.glob("main/resources/data/*/recipe/**/*.json")):
        text = recipe.read_text()
        for item in sorted(unsourced):
            name = item.lstrip("#")
            if f'"{name}"' in text:
                yield f"{recipe.relative_to(ROOT)} needs {item}, which has no Overworld source"


def story_books():
    story = tomllib.loads((ROOT / "story/books.toml").read_text())
    return [*story["found"]] + [book for collection in story["collections"] for book in collection["books"]]


def books():
    entries = story_books()
    titles = [entry["title"] for entry in entries] + [spec["people"] for spec in hall_eyes().values()]
    for title in titles:
        if len(title) > BOOK_TITLE_LIMIT:
            yield f"the book title {title!r} is {len(title)} characters; Minecraft allows {BOOK_TITLE_LIMIT}"
    identifiers = [entry["id"] for entry in entries]
    for identifier in sorted({name for name in identifiers if identifiers.count(name) > 1}):
        yield f"story/books.toml: two books share the id {identifier!r}"
    for entry in entries:
        for page in entry["pages"]:
            if len(page) > PAGE_LIMIT:
                yield f"story/books.toml: a page of {entry['title']!r} is {len(page)} characters; a book page holds about {PAGE_LIMIT}"


FATES = {"away", "lives", "lost"}
KINDS = {
    "board",
    "bury",
    "camp",
    "caravan",
    "chase",
    "clues",
    "dawn",
    "duel",
    "dusk",
    "escort",
    "gather",
    "give",
    "hold",
    "home",
    "horse",
    "locks",
    "memory",
    "reach",
    "read",
    "rest",
    "sail",
    "say",
    "sea",
    "shard",
    "slay",
    "sleep",
    "talk",
    "vision",
    "visit",
    "wait",
    "walk",
    "wins",
    "worm",
}
MEMORY_KINDS = {"away", "cast", "climb", "cook", "gather", "lay", "light", "notch", "say", "take", "talk"}
PLACES = {
    "airfield",
    "bell",
    "bell_agna",
    "bell_hob",
    "bell_pip",
    "bram_outpost",
    "bram_son",
    "brannoc",
    "brother",
    "bundle",
    "burning",
    "cache",
    "cairns",
    "cairns_nell",
    "caravan",
    "cellar",
    "den",
    "dice",
    "diggers",
    "diggers_hall",
    "dry",
    "dry_stone",
    "errand_outpost",
    "far_shore",
    "fire_1",
    "fire_2",
    "first_camp",
    "first_gate",
    "first_village",
    "first_village_melt",
    "first_village_stone",
    "ford",
    "ford_far",
    "frost",
    "frost_hall",
    "frost_inside",
    "frost_lake",
    "frost_lake_stone",
    "frost_mine",
    "frost_mine_cart",
    "frost_mine_deep",
    "frost_rim",
    "frost_roof",
    "frost_shore",
    "frost_stair",
    "frost_stone",
    "frost_tracks",
    "graveyard",
    "graveyard_stone",
    "hollow",
    "home",
    "keepers",
    "keepers_oldest",
    "last_bundle",
    "last_camp",
    "locks",
    "locks_heir",
    "locks_stone",
    "mansion",
    "mansion_captive_1",
    "mansion_captive_2",
    "mansion_captive_3",
    "mansion_chair",
    "mansion_hall",
    "mansion_stand",
    "maren",
    "marsh",
    "marsh_cellar",
    "marsh_fisher",
    "marsh_hall",
    "missing",
    "nest",
    "never_sleeps",
    "novice",
    "reading_house",
    "reading_house_stair",
    "reading_house_vault",
    "rite",
    "sand",
    "sand_lair",
    "sand_sefa",
    "scribes",
    "scribes_stone",
    "seal",
    "settled",
    "settled_wayfarer",
    "shipyard",
    "shipyard_hull",
    "shipyard_jetty",
    "shipyard_launch",
    "shipyard_quay",
    "shipyard_wreck",
    "tamsins_bell",
    "tide",
    "tide_hall",
    "tide_inside",
    "tide_morwen",
    "tide_warden_1",
    "tide_warden_2",
    "tollbridge",
    "ward",
    "ward_champion",
    "ward_lines",
    "ward_stone",
    "ward_warden",
    "wend_camp",
    "wend_camp_chair",
    "wil",
}
KNOWN = {"feeling"}
TASKS = {
    "bed",
    "bury",
    "ember",
    "enter",
    "fire",
    "horse",
    "hull",
    "look",
    "map",
    "marks",
    "pack",
    "rest",
    "sail",
    "search",
    "stone",
    "sweep",
    "tent",
    "tracks",
}


def story():
    spec = tomllib.loads((ROOT / "story/quests.toml").read_text())
    quests = spec["quests"]
    memories = {memory["id"]: memory for memory in spec["memories"]}
    for memory in memories.values():
        if memory["place"] not in PLACES:
            yield f"story/quests.toml: the memory {memory['id']} happens at {memory['place']}; the story builds {', '.join(sorted(PLACES))}"
    people = set(tomllib.loads((ROOT / "story/people.toml").read_text()))
    for memory in memories.values():
        for step in memory["steps"]:
            if step["kind"] not in MEMORY_KINDS:
                yield f"story/quests.toml: the memory {memory['id']} has a {step['kind']} step; a memory knows {', '.join(sorted(MEMORY_KINDS))}"
            said = step.get("lines", []) + step.get("done", [])
            for person in [line["by"] for line in said if "by" in line] + ([step["meets"]] if "meets" in step else []):
                if person not in people:
                    yield f"story/quests.toml: the memory {memory['id']}/{step['id']} names {person}, who is not in story/people.toml"
    lang = json.loads((MOD / "main/resources/assets/wayfarer/lang/en_us.json").read_text())
    sounds = tomllib.loads((ROOT / "story/sounds.toml").read_text())
    for piece in sounds["music"]:
        if not (MOD / f"main/resources/assets/wayfarer/sounds/music/{piece}.ogg").exists():
            yield f"story/sounds.toml has the piece {piece}, but sounds/music/{piece}.ogg is missing"
    for effect, spec in sounds["effects"].items():
        if not (MOD / f"main/resources/assets/wayfarer/sounds/effects/{effect}.ogg").exists():
            yield f"story/sounds.toml has the sound {effect}, but sounds/effects/{effect}.ogg is missing; run just sounds {effect}"
        if f"subtitles.wayfarer.{effect}" not in lang:
            yield f"story/sounds.toml has the sound {effect}; add subtitles.wayfarer.{effect} to the language file"
        if spec["kind"] not in SOUND_KINDS:
            yield f"story/sounds.toml gives {effect} the kind {spec['kind']}, not one of {', '.join(sorted(SOUND_KINDS))}"
        if spec["slider"] not in SLIDERS:
            yield f"story/sounds.toml puts {effect} under {spec['slider']}, not one of {', '.join(sorted(SLIDERS))}"
    ids = [quest["id"] for quest in quests]
    books = {entry["id"] for entry in story_books()}
    for scene, spec in tomllib.loads((ROOT / "story/scenes.toml").read_text())["scenes"].items():
        for line in [line for key in ("ask", "hid", "won") for line in spec.get(key, [])]:
            if "by" in line and line["by"] not in people:
                yield f"story/scenes.toml: the {scene} scene has {line['by']} speak, who is not in story/people.toml"
    outcomes = {
        f"{quest['id']}/{step['id']}": {choice["id"] for choice in step.get("choices", [])}
        | (CHASE_OUTCOMES if step["kind"] == "chase" else set())
        | (DUEL_OUTCOMES if step["kind"] == "duel" else set())
        | (ESCORT_OUTCOMES if step["kind"] == "escort" else set())
        | (SEA_OUTCOMES if step["kind"] == "sea" else set())
        | (WORM_OUTCOMES if step["kind"] == "worm" else set())
        for quest in quests
        for step in quest["steps"]
    } | {
        f"{quest['id']}/{person}": FATES
        for quest in quests
        for step in quest["steps"]
        for person in step.get("fates", {})
    }
    for quest in quests:
        if "found" in quest and quest["found"] not in PLACES:
            yield f"story/quests.toml: {quest['id']} opens when {quest['found']} is found; the story builds {', '.join(sorted(PLACES))}"
        rewards = [quest.get("reward", {})] + [step.get("reward", {}) for step in quest["steps"]]
        for item in [item for reward in rewards for item in reward.get("items", [])]:
            if "book" in item and item["book"] not in books:
                yield f"story/quests.toml: {quest['id']} gives the book {item['book']}, which story/books.toml does not have"
        for after in quest.get("after", []):
            if after not in ids:
                yield f"story/quests.toml: {quest['id']} comes after {after}, which is not a quest"
        for condition in quest.get("when", []):
            asked, _, answer = condition.lstrip("!").partition("=")
            if answer not in outcomes.get(asked, set()):
                yield f"story/quests.toml: {quest['id']} opens when {asked} chose {answer}, which that step never offers"
        for word in quest.get("renamed", {}):
            if f"wayfarer.word.{word}" not in lang and word not in people:
                yield f"story/quests.toml: {quest['id']} is renamed once {word} is named, which is neither a person nor a word in the language file"
        for line in quest.get("road", []):
            if "by" in line and line["by"] not in people:
                yield f"story/quests.toml: {quest['id']} has {line['by']} speak on the road, who is not in story/people.toml"
        if "road" in quest and "halls" not in quest:
            yield f"story/quests.toml: {quest['id']} has lines for the road back from a hall but opens after no number of halls"
        for step in quest["steps"]:
            where = f"story/quests.toml: {quest['id']}/{step['id']}"
            if step["kind"] not in KINDS:
                yield f"{where} is a {step['kind']} step; the engine knows {', '.join(sorted(KINDS))}"
            if step["kind"] == "memory" and step["memory"] not in memories:
                yield f"{where} dreams the memory {step['memory']}, which story/quests.toml does not hold"
            if "finds" in step and step["finds"] not in PLACES:
                yield f"{where} finds {step['finds']}; the story builds {', '.join(sorted(PLACES))}"
            said = step.get("lines", []) + step.get("done", []) + step.get("burial", [])
            named = [line["by"] for line in said if "by" in line]
            named += [step[key] for key in ("whom", "with") if key in step]
            named += [line["vanishes"] for line in said if "vanishes" in line]
            named += step.get("faces", []) + step.get("figures", [])
            named += (
                list(step.get("people", {}))
                + list(step.get("poses", {}))
                + list(step.get("moves", {}))
                + list(step.get("remarks", {}))
                + step.get("keep", [])
                + step.get("leaves", [])
                + ([step["joins"]] if "joins" in step else [])
                + ([step["shoots"]] if "shoots" in step else [])
                + [choice["leaves"] for choice in step.get("choices", []) if "leaves" in choice]
            )
            for person in named:
                if person not in people:
                    yield f"{where} names {person}, who is not in story/people.toml"
            for name in [name for line in said for name in line.get("names", [])]:
                if name not in people and f"wayfarer.word.{name}" not in lang:
                    yield f"{where} names {name}, who is not in story/people.toml and not a word in the language file"
            for choice in step.get("choices", []):
                if "stone" in choice:
                    yield from stone(where, choice["stone"])
                if "needs" in choice and choice["needs"] not in ids:
                    yield f"{where} offers {choice['id']} once {choice['needs']} has begun, which is not a quest"
            for line in said:
                if "when" in line and not line["when"].lstrip("!").startswith(("gift:", "trusted:")):
                    asked, _, answer = line["when"].lstrip("!").partition("=")
                    if answer not in outcomes.get(asked, set()):
                        yield f"{where} says a line when {asked} chose {answer}, which that step never offers"
                if "sound" in line and line["sound"] not in sounds["effects"]:
                    yield f"{where} plays the sound {line['sound']}, which story/sounds.toml does not have"
                if "music" in line and line["music"] not in sounds["music"]:
                    yield f"{where} plays the piece {line['music']}, which story/sounds.toml does not have"
            if "music" in step and not sounds["music"].get(step["music"], {}).get("loop"):
                yield f"{where} loops the piece {step['music']}, which story/sounds.toml does not mark loop = true"
            for gift in step.get("gifts", {}).values():
                if f"wayfarer.gift.{gift}" not in lang:
                    yield f"{where} gives the gift {gift}; add wayfarer.gift.{gift} to the language file"
            for word in step.get("words", {}):
                if f"wayfarer.word.{word}" not in lang:
                    yield f"{where} teaches the word {word}; add wayfarer.word.{word} to the language file"
            places = [step[key] for key in ("at", "builds", "from", "place", "toward") if key in step]
            places += [move["place"] for move in step.get("moves", {}).values()]
            for place in places:
                if place not in PLACES:
                    yield f"{where} names the place {place}; the story builds {', '.join(sorted(PLACES))}"
            if step.get("known", "told") not in KNOWN | {"told"}:
                yield f"{where} is known by {step['known']}; the compass knows {', '.join(sorted(KNOWN))}"
            for task in step.get("tasks", []):
                if task not in TASKS:
                    yield f"{where} names the task {task}; the panel knows {', '.join(sorted(TASKS))}"
                elif f"wayfarer.task.{task}" not in lang:
                    yield f"{where} names the task {task}; add wayfarer.task.{task} to the language file"
            if step["kind"] == "clues" and step["needed"] > len(step.get("clues", [])):
                yield f"{where} needs {step['needed']} clues but has {len(step.get('clues', []))}"


def gear():
    spec = tomllib.loads((ROOT / "story/gear.toml").read_text())
    quests = tomllib.loads((ROOT / "story/quests.toml").read_text())["quests"]
    people = tomllib.loads((ROOT / "story/people.toml").read_text())
    rewards = {quest["id"] for quest in quests if "reward" in quest} | {
        f"{quest['id']}/{step['id']}" for quest in quests for step in quest["steps"] if "reward" in step
    }
    things = {f"{row['id']}/{thing['id']}" for row in spec["rows"] for thing in row["things"]}
    if spec["opens"]["claimed"] not in rewards:
        yield f"story/gear.toml: the page opens with the reward {spec['opens']['claimed']}, which story/quests.toml does not give"
    lines = {line["id"] for line in spec["lines"]}
    for line in spec["lines"]:
        if line["by"] not in people:
            yield f"story/gear.toml: the line {line['id']} is said by {line['by']}, who is not in story/people.toml"
        for key in line["when"]:
            if key not in things:
                yield f"story/gear.toml: the line {line['id']} waits on {key}, which no row has"
    for row in spec["rows"]:
        if row["who"] not in people:
            yield f"story/gear.toml: {row['id']} is known by {row['who']}, who is not in story/people.toml"
        claimed = row["appears"].get("claimed")
        if claimed is not None and claimed not in rewards:
            yield f"story/gear.toml: {row['id']} appears with the reward {claimed}, which story/quests.toml does not give"
        for key in row["appears"].get("has", []):
            if key not in things:
                yield f"story/gear.toml: {row['id']} appears with {key}, which no row has"
        for thing in row["things"]:
            named = thing.get("named_by")
            if named is not None and named != "had" and named not in lines:
                yield f"story/gear.toml: {row['id']}/{thing['id']} is named by the line {named}, which does not exist"
            if not thing.get("hidden") and "name" not in thing:
                yield f"story/gear.toml: {row['id']}/{thing['id']} has no name"


def stone(where, words):
    lines = []
    for word in words.split():
        if lines and len(lines[-1]) + 1 + len(word) <= SIGN_WIDTH:
            lines[-1] += " " + word
        else:
            lines.append(word)
    if len(lines) > SIGN_LINES or any(len(line) > SIGN_WIDTH for line in lines):
        yield f"{where}: the stone's words {words!r} take {len(lines)} lines; a sign holds {SIGN_LINES} of {SIGN_WIDTH} letters"


def interface():
    found = []
    for path in sorted(INTERFACE.rglob("*.java")):
        if path.parent == STAMP:
            continue
        for number, line in enumerate(path.read_text().splitlines(), start=1):
            if COLOR.search(line):
                found.append(
                    f"{path.relative_to(ROOT)}:{number}: a colour in the interface comes from style.toml through Stamp"
                )
    return found


def main():
    findings = [
        *sides(),
        *eyes(),
        *translations(),
        *titles(),
        *recipes(),
        *books(),
        *story(),
        *gear(),
        *interface(),
        *gallery.problems(),
    ]
    for finding in findings:
        print(finding)
    if findings:
        sys.exit(f"{len(findings)} rule findings")


main()

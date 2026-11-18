import fnmatch
import json
import re
import sys
import zipfile
from pathlib import Path

import instance
import spoken
import tomllib

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
PREVIEW_TEXT = (100, 1000)
SILENT_NAMED = 8
SHOWN_JSON = MOD / "generated/resources/wayfarer"
SHOWN_BOOKS = MOD / "generated/resources/data/wayfarer/loot_table/books"
SHOWN_TAG = re.compile(r'"[^"\n]*(\[[A-Za-z][^\]"\n]*\])[^"\n]*"')
WORLD_CONDITIONS = ("carries:", "gift:", "hour:", "joined:", "land:", "trusted:")
CLAIMS = (
    (re.compile(r"(?i)\b(burnt through|sunburnt|scorched through)\b"), "land:hot"),
    (re.compile(r"(?i)\b(frozen through|half frozen|chilled through)\b"), "land:cold"),
    (re.compile(r"(?i)\byour ships?\b"), "carries:"),
)
DEADLINE = re.compile(
    r"(?i)\b(tonight, or near it|before nightfall|before dark|before dawn|before the (?:first|second|third|next) dawn)\b"
)
DISTANCES = (
    (re.compile(r"(?i)\b(near here|nearby|close by|not far)\b"), 512),
    (re.compile(r"(?i)\b(up the road|a little way along|further on)\b"), 1024),
    (re.compile(r"(?i)\ba few days\b"), 4096),
)
WALKS = (
    (re.compile(r"(?i)\ban hour's walk\b"), 64, 512),
    (re.compile(r"(?i)\ba morning's walk\b"), 512, 2048),
    (re.compile(r"(?i)\ba day's walk\b"), 1024, 4096),
)
TIME_WORDS = re.compile(r"(?i)\b(tonight|today|this evening|this morning|tomorrow)\b")
WEARS = re.compile(r"(?i)\b(over your head|you put (?:it|them) on|you wear)\b")
HANDS = re.compile(r"((?:^|[.!?\]]\s+)Here\.|\b[Tt]ake this\b|\b[Tt]his is yours\b)")
BAND = re.compile(r'Talk\.(?:notice|tell)\((?:[a-z]+, )?Component\.translatable\("(wayfarer\.[a-z_.]+[a-z_])"\s*[,)]')
SPREADS = ("lake_blocks", "search_blocks", "spread_blocks")
QUESTION = re.compile(r"[^.!?]*\?")
ADDRESSED = re.compile(r"(?i)\b(you|your)\b")
FIRE_WORDS = re.compile(r"(?i)\b(at the fire|by the fire|followed you|I watched)\b")
LAND_ANSWER = re.compile(r"(?i)\b(trees|woods|forest|water|river|rocks)\b")
TIMED_KINDS = {"dawn", "dusk", "hold"}
LOADED_HEIGHT = re.compile(r"Heightmap\.Types\.(MOTION_BLOCKING|MOTION_BLOCKING_NO_LEAVES|OCEAN_FLOOR|WORLD_SURFACE)\b")


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
    "cairns_drifa",
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
    "frost_door",
    "frost_hearth",
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
    "malgven",
    "mansion",
    "mansion_captive_1",
    "mansion_captive_2",
    "mansion_captive_3",
    "mansion_chair",
    "mansion_hall",
    "mansion_stand",
    "marsh",
    "marsh_cellar",
    "marsh_fisher",
    "marsh_hall",
    "mauds_bell",
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
    "tide",
    "tide_hall",
    "tide_inside",
    "tide_murdo",
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
    "campfire",
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


def unoffered(outcomes, condition):
    condition = condition.lstrip("!")
    if not condition or condition.startswith(WORLD_CONDITIONS):
        return []
    asked, _, answers = condition.partition("=")
    return [(asked, answer) for answer in answers.split("|") if answer not in outcomes.get(asked, set())]


def epilogue(spec, outcomes, people):
    for number, leaf in enumerate(spec.get("epilogue", []), start=1):
        where = f"story/quests.toml: the epilogue's leaf {number}"
        if "by" in leaf and leaf["by"] not in people:
            yield f"{where} is said by {leaf['by']}, who is not in story/people.toml"
        if "by" in leaf and leaf.get("told"):
            yield f"{where} has both by and told; a told leaf is said by Pip, or by the narrator when Pip was lost"
        if ("{lost}" in leaf["text"]) != bool(leaf.get("lost")):
            yield f"{where} names {{lost}} only when it is marked lost = true, and a lost leaf names them"
        for condition in leaf.get("when", []):
            if condition.lstrip("!") == "alone":
                continue
            for asked, answer in unoffered(outcomes, condition):
                yield f"{where} shows when {asked} chose {answer}, which that step never offers"


NUMBERS = {
    2: "two",
    3: "three",
    4: "four",
    5: "five",
    6: "six",
    7: "seven",
    8: "eight",
    9: "nine",
    10: "ten",
    11: "eleven",
    12: "twelve",
    16: "sixteen",
    24: "twenty-four",
    32: "thirty-two",
    48: "forty-eight",
    64: "sixty-four",
}
COUNTED = {"gather": "{carried}", "give": "{carried}", "wins": "{won}"}


def counted(where, step):
    count = step.get("count", 1)
    if step["kind"] not in COUNTED or count < 2:
        return
    objective = step.get("objective", "")
    if (
        f"{COUNTED[step['kind']]}/{{count}}" not in objective
        and str(count) not in objective
        and NUMBERS.get(count, "-") not in objective.lower()
    ):
        yield f"{where} counts {count} but its objective shows no {COUNTED[step['kind']]}/{{count}}"


def outcome(where, step):
    for ended in ("failed", "lost", "sunk"):
        if ended in step and ended not in step.get("objectives", {}):
            yield f"{where} can end {ended}, but has no objective for it under objectives.{ended}"


def ready(where, step, lang):
    for task in step.get("tasks", []):
        text = lang.get(f"wayfarer.task.{task}", "")
        if ("in your hand" in text or "in your pack" in text) and f"wayfarer.task.{task}.ready" not in lang:
            yield f"{where} needs what wayfarer.task.{task} names; add wayfarer.task.{task}.ready to say so before the quest gets there"


def evening(quests, quest):
    steps = quest["steps"]
    if quest["kind"] != "main" or steps[-1]["kind"] != "caravan":
        return
    closing = len(steps)
    while closing > 0 and steps[closing - 1]["kind"] == "caravan":
        closing -= 1
    road = [line for after in quests if quest["id"] in after.get("after", []) for line in after.get("road", [])]
    for step in steps[closing:]:
        said = [line for key in ("lines", "done") for line in step.get(key, [])] + road
        if not any(line.get("by", "stranger") != "stranger" for line in said):
            yield f"story/quests.toml: {quest['id']}/{step['id']}: its closing evening has no line from anyone at the fire"


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
            yield from counted(f"story/quests.toml: the memory {memory['id']}/{step['id']}", step)
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
    yield from epilogue(spec, outcomes, people)
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
            for asked, answer in unoffered(outcomes, condition):
                yield f"story/quests.toml: {quest['id']} opens when {asked} chose {answer}, which that step never offers"
        for word in quest.get("renamed", {}):
            if f"wayfarer.word.{word}" not in lang and word not in people:
                yield f"story/quests.toml: {quest['id']} is renamed once {word} is named, which is neither a person nor a word in the language file"
        yield from evening(quests, quest)
        for line in quest.get("road", []):
            if "by" in line and line["by"] not in people:
                yield f"story/quests.toml: {quest['id']} has {line['by']} speak on the road, who is not in story/people.toml"
        for step in quest["steps"]:
            where = f"story/quests.toml: {quest['id']}/{step['id']}"
            if step["kind"] not in KINDS:
                yield f"{where} is a {step['kind']} step; the engine knows {', '.join(sorted(KINDS))}"
            yield from counted(where, step)
            if step["kind"] == "memory" and step["memory"] not in memories:
                yield f"{where} dreams the memory {step['memory']}, which story/quests.toml does not hold"
            if "finds" in step and step["finds"] not in PLACES:
                yield f"{where} finds {step['finds']}; the story builds {', '.join(sorted(PLACES))}"
            said = step.get("lines", []) + step.get("done", []) + step.get("burial", [])
            named = [line["by"] for line in said if "by" in line]
            named += [step[key] for key in ("whom", "with") if key in step]
            named += [line["vanishes"] for line in said if "vanishes" in line]
            named += step.get("faces", []) + step.get("figures", [])
            named += [
                person
                for line in said
                for person in line.get("faces", [])
                + line.get("leaves", [])
                + list(line.get("poses", {}))
                + list(line.get("moves", {}))
            ]
            named += (
                list(step.get("people", {}))
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
            yield from outcome(where, step)
            yield from ready(where, step, lang)
            if step["kind"] == "caravan" and "camps" in step.get("objective", "") and "near" not in step:
                yield f"{where} says where the camp stands tonight, but has no near to put it there"
            if "objective" not in step and step["kind"] != "caravan":
                yield f"{where} has no objective; only a caravan step takes the shared one"
            for choice in step.get("choices", []):
                if "stone" in choice:
                    yield from stone(where, choice["stone"])
                if "needs" in choice and choice["needs"] not in ids:
                    yield f"{where} offers {choice['id']} once {choice['needs']} has begun, which is not a quest"
            for line in said:
                for asked, answer in unoffered(outcomes, line.get("when", "")):
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
            places += [move["place"] for line in said for move in line.get("moves", {}).values()]
            if "poses" in step:
                yield f"{where} poses people for the whole step; a pose goes on the line that tells it"
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
        if not line.get("when") and "carries" not in line:
            yield f"story/gear.toml: the line {line['id']} waits on nothing; give it when or carries"
        for key in line.get("when", []):
            if key not in things:
                yield f"story/gear.toml: the line {line['id']} waits on {key}, which no row has"
        for bound in ("before", "after"):
            if bound in line and line[bound] not in {quest["id"] for quest in quests}:
                yield f"story/gear.toml: the line {line['id']} is said {bound} {line[bound]}, which is no quest"
    for row in spec["rows"]:
        if row["who"] not in people:
            yield f"story/gear.toml: {row['id']} is known by {row['who']}, who is not in story/people.toml"
        claimed = row["appears"].get("claimed")
        if claimed is not None and claimed not in rewards:
            yield f"story/gear.toml: {row['id']} appears with the reward {claimed}, which story/quests.toml does not give"
        for key in row["appears"].get("has", []):
            if key not in things:
                yield f"story/gear.toml: {row['id']} appears with {key}, which no row has"
        opened = row["appears"].get("opened")
        if opened is not None and opened not in {quest["id"] for quest in quests}:
            yield f"story/gear.toml: {row['id']} appears when {opened} opens, which is no quest"
        for thing in row["things"]:
            named = thing.get("named_by")
            if named is not None and named != "had" and named not in lines:
                yield f"story/gear.toml: {row['id']}/{thing['id']} is named by the line {named}, which does not exist"
            if not thing.get("hidden") and "name" not in thing:
                yield f"story/gear.toml: {row['id']}/{thing['id']} has no name"
            made = thing.get("made", "").lower().rstrip(".")
            for need in thing.get("needs", []):
                if made and need.get("where", "").lower().rstrip(".") == made:
                    yield f'story/gear.toml: {row["id"]}/{thing["id"]}: the {need["name"]} need repeats "{need["where"]}"'


def stone(where, words):
    lines = []
    for word in words.split():
        if lines and len(lines[-1]) + 1 + len(word) <= SIGN_WIDTH:
            lines[-1] += " " + word
        else:
            lines.append(word)
    if len(lines) > SIGN_LINES or any(len(line) > SIGN_WIDTH for line in lines):
        yield f"{where}: the stone's words {words!r} take {len(lines)} lines; a sign holds {SIGN_LINES} of {SIGN_WIDTH} letters"


def voices():
    config = spoken.voices()
    lines = spoken.lines(config)
    people = tomllib.loads((ROOT / "story/people.toml").read_text())
    speakers = {line.by for line in lines} | set(people)
    groups = {line.group for line in lines}
    chosen = set(spoken.chosen(config).values())
    readers = {}
    for voice, spec in sorted(config["voices"].items()):
        if voice in groups:
            yield f"story/voices.toml: the voice {voice} has the name of a group of lines, so just voice record {voice} is unclear"
        for speaker in spec["reads"]:
            if speaker not in speakers:
                yield f"story/voices.toml: {voice} reads {speaker}, who never speaks"
            elif speaker in readers and voice not in chosen:
                yield f"story/voices.toml: {readers[speaker]} and {voice} both read {speaker}"
            if voice not in chosen:
                readers.setdefault(speaker, voice)
        if ("text" in spec) != ("description" in spec):
            yield f"story/voices.toml: {voice} needs both a description and a preview text, or neither"
        if "text" in spec and not PREVIEW_TEXT[0] <= len(spec["text"]) <= PREVIEW_TEXT[1]:
            yield f"story/voices.toml: {voice}'s preview text is {len(spec['text'])} characters; ElevenLabs needs 100 to 1,000"
    lang = json.loads(LANG.read_text())
    patterns = [pattern for keys in config["lang"].values() for pattern in keys]
    for source in sorted((MOD / "main/java").rglob("*.java")):
        for key in BAND.findall(source.read_text()):
            if not any(fnmatch.fnmatchcase(key, pattern) for pattern in patterns):
                yield f"{source.relative_to(ROOT)}: {key} is said on the band, but no voice reads it in story/voices.toml [lang]"
            elif "%s" in lang.get(key, "") and key not in config.get("fills", {}):
                yield f"{source.relative_to(ROOT)}: {key} fills in a name on the band, but story/voices.toml [fills] records no form of it"
    together = {}
    for line in lines:
        if line.by in readers:
            together.setdefault((readers[line.by], line.step), set()).add(line.by)
    for (voice, step), sharing in sorted(together.items()):
        if len(sharing) > 1:
            yield f"story/voices.toml: {voice} reads {' and '.join(sorted(sharing))}, who both speak in {step}"
    voiced = spoken.readers(config)
    for line in lines:
        if line.by in voiced and not line.recordable():
            words = ", ".join(sorted(set(spoken.PLACEHOLDER.findall(line.text))))
            yield f"{line.group}:{line.number}: {line.by} speaks {words}, a word only the game knows; write it so nothing varying is spoken"
    takes = spoken.takes(config, lines)
    worded = {}
    for take in takes:
        worded.setdefault((take.voice, take.line.shown), set()).add(take.text)
    for (voice, shown), texts in sorted(worded.items()):
        if len(texts) > 1:
            yield f'story/: {voice} says "{shown[:48]}" with different tags: {" | ".join(sorted(texts))}'
    lang = json.loads(LANG.read_text())
    for key, text in sorted(lang.items()):
        if spoken.TAG.search(text):
            yield f"en_us.json: {key} carries an audio tag, which the game would show"
    for by, patterns in sorted(config["lang"].items()):
        for pattern in patterns:
            if not any(fnmatch.fnmatchcase(key, pattern) for key in lang):
                yield f"story/voices.toml: [lang] {by} reads {pattern}, which names no key in en_us.json"
    for path in sorted(SHOWN_JSON.glob("*.json")) + sorted(SHOWN_BOOKS.glob("*.json")):
        found = SHOWN_TAG.search(path.read_text())
        if found:
            yield f"{path.relative_to(ROOT)}: the game would show the audio tag {found.group(1)}; the generator strips them"
    for path in sorted((ROOT / "story/voices").glob("*.json")):
        if path.name != "recordings.json" and path.stem not in config["voices"]:
            yield f"story/voices/{path.name} keeps a design for {path.stem}, which story/voices.toml does not have"


def listed(items):
    shown = ", ".join(items[:SILENT_NAMED])
    return f"{shown} and {len(items) - SILENT_NAMED:,} more" if len(items) > SILENT_NAMED else shown


def amount(number, one, many):
    return f"{number:,} {one if number == 1 else many}"


def waiting():
    config = spoken.voices()
    lines = spoken.lines(config)
    voiced = spoken.readers(config)
    unvoiced = {}
    for line in lines:
        if line.by not in voiced:
            unvoiced.setdefault(line.by, line.step)
    undesigned = [voice for voice in sorted(config["voices"]) if not (ROOT / f"story/voices/{voice}.json").exists()]
    done = spoken.recordings()
    silent = [
        f"{take.line.group}:{take.line.number} ({take.voice})"
        for take in spoken.takes(config, lines)
        if (ROOT / f"story/voices/{take.voice}.json").exists() and not spoken.recorded(done, take)
    ]
    speakers = [f"{speaker} ({step})" for speaker, step in sorted(unvoiced.items())]
    notes = []
    if silent:
        notes.append(
            f"{amount(len(silent), 'line is', 'lines are')} silent, waiting for the recording: {listed(silent)}"
        )
    if speakers:
        notes.append(
            f"{amount(len(speakers), 'speaker has', 'speakers have')} no voice yet, waiting for the recording: {listed(speakers)}"
        )
    if undesigned:
        notes.append(
            f"{amount(len(undesigned), 'voice is', 'voices are')} not designed yet, waiting for the recording: {listed(undesigned)}"
        )
    return notes


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


def spoken_lines(spec):
    for quest in spec["quests"]:
        place = next((step["place"] for step in quest["steps"] if "place" in step), None)
        for line in quest.get("road", []):
            yield quest, None, place, "road", line
        yield quest, None, place, "summary", {"text": quest.get("summary", "")}
        for index, step in enumerate(quest["steps"]):
            ahead = next((later["place"] for later in quest["steps"][index:] if "place" in later), None)
            after = next((later["place"] for later in quest["steps"][index + 1 :] if "place" in later), None)
            yield quest, step, ahead, "objective", {"text": step.get("objective", "")}
            for key in ("lines", "each", "late", "arrived"):
                for line in step.get(key, []) if isinstance(step.get(key), list) else []:
                    yield quest, step, ahead, key, line
            for line in step.get("done", []):
                yield quest, step, after, "done", line
            for person, remarks in step.get("remarks", {}).items():
                for remark in remarks:
                    yield quest, step, ahead, f"remarks.{person}", {"by": person, "text": remark}


def place_distances(balance):
    distances = {
        table: spec["ahead_blocks"]
        for table, spec in balance.items()
        if isinstance(spec, dict) and "ahead_blocks" in spec
    }
    lair = re.search(
        r'static final String LAIR = "([a-z_]+)";',
        (MOD / "main/java/io/github/roman16/wayfarer/story/SandSingers.java").read_text(),
    )
    if lair:
        distances[lair.group(1)] = balance["worm"]["lair_blocks"]
    errands = (MOD / "main/java/io/github/roman16/wayfarer/story/Errands.java").read_text()
    names = dict(re.findall(r'static final String ([A-Z_]+) = "([a-z_]+)";', errands))
    finder = errands[errands.index("static void find(") : errands.index("default -> throw")]
    for label in re.findall(r"case ([A-Z_, ]+) ->", finder):
        for constant in label.split(", "):
            if constant in names:
                distances.setdefault(names[constant], balance["errands"]["ahead_blocks"])
    return distances


def words():
    spec = tomllib.loads((ROOT / "story/quests.toml").read_text())
    balance = tomllib.loads((ROOT / "balance.toml").read_text())
    distances = place_distances(balance)
    for quest, step, place, field, line in spoken_lines(spec):
        where = f"story/quests.toml: {quest['id']}" + (f"/{step['id']}" if step else "") + f" {field}"
        text = line.get("text", "")
        when = line.get("when", "") or ""
        for pattern, condition in CLAIMS:
            found = pattern.search(text)
            if found and condition not in when:
                yield f"{where} says {found.group(0)!r} but holds no {condition} condition"
        found = (
            DEADLINE.search(text) if field in ("objective", "road", "summary") or step is quest["steps"][0] else None
        )
        if found and not any(
            "dawns" in later or "until" in later or later["kind"] in TIMED_KINDS for later in quest["steps"]
        ):
            yield f"{where} says {found.group(0)!r}, but no step of {quest['id']} keeps a deadline"
        for pattern, most in DISTANCES:
            found = pattern.search(text)
            if found and place in distances and distances[place] > most:
                yield f"{where} says {found.group(0)!r}, but {place} lies {distances[place]} blocks ahead"
        for pattern, least, most in WALKS:
            found = pattern.search(text)
            if found and place in distances and not least <= distances[place] <= most:
                yield f"{where} says {found.group(0)!r}, but {place} lies {distances[place]} blocks off"
        found = TIME_WORDS.search(text) if field in ("objective", "summary") else None
        timed = step is not None and ("dawns" in step or "until" in step or step["kind"] in TIMED_KINDS)
        if found and (field == "summary" or not timed):
            yield f"{where} says {found.group(0)!r}, which outlasts the moment an objective or summary is shown"
        if field.startswith("remarks.") or field == "road":
            yield from asked(where, text)
    for who, person in tomllib.loads((ROOT / "story/people.toml").read_text()).items():
        for kind in spoken.FIRE_LINES:
            for text in person.get(kind, []):
                yield from asked(f"story/people.toml: {who} {kind}", text)
                found = FIRE_WORDS.search(text) if kind in ("remarks", "shouts", "cold") else None
                if found:
                    yield f"story/people.toml: {who} {kind} says {found.group(0)!r}, but a {kind[:-1]} is said anywhere"
    for quest in spec["quests"]:
        for step in quest["steps"]:
            reward = step.get("reward")
            if not reward:
                continue
            where = f"story/quests.toml: {quest['id']}/{step['id']}"
            if WEARS.search(reward["claimed"]) and not any(item.get("worn") for item in reward["items"]):
                yield f"{where}'s reward says {WEARS.search(reward['claimed']).group(0)!r} but puts nothing on"
            for line in step.get("lines", []) + step.get("done", []):
                found = HANDS.search(line.get("text", "")) if line.get("by") else None
                if found:
                    yield f"{where} has {line['by']} say {found.group(0)!r} while the reward waits in the journal; say it as the reward's given line"
    reach = balance["story"]["reach_days"]
    offered = reach * balance["caravan"]["day_blocks"]
    for earlier in spec["quests"]:
        for step in earlier["steps"]:
            announced = step.get("place") if step["kind"] in ("talk", "say") else None
            later = [
                quest["id"]
                for quest in spec["quests"]
                if quest is not earlier and any(other.get("place") == announced for other in quest["steps"])
            ]
            table = balance.get(announced, {})
            if later and isinstance(table, dict) and "ahead_blocks" in table:
                most = table["ahead_blocks"] + max((table[key] for key in SPREADS if key in table), default=0) * 1.42
                if most > offered:
                    yield f"story/quests.toml: {earlier['id']}/{step['id']} sets up {announced} up to {int(most)} blocks ahead, but {later[0]} is offered only within {offered}"
    for quest in spec["quests"]:
        for step in quest["steps"]:
            if "dawns" in step and step["dawns"] < reach:
                yield f"story/quests.toml: {quest['id']}/{step['id']} gives {step['dawns']} dawns, but its place may lie {reach} wagon days off"
    for scene, text in tomllib.loads((ROOT / "story/scenes.toml").read_text())["scenes"].items():
        for voice in text.get("voice", []):
            for pattern, condition in CLAIMS:
                found = pattern.search(voice)
                if found:
                    yield f"story/scenes.toml: {scene} voice says {found.group(0)!r}, which a voice line can hold no {condition} condition for"
        for choice in text.get("choices", []):
            found = LAND_ANSWER.search(choice["text"])
            if found and "near" not in choice:
                yield f"story/scenes.toml: {scene}'s answer {choice['text']!r} names {found.group(0)!r} but has no near condition"


def asked(where, text):
    spoken_text = re.sub(r"\[[^\]]*\]", "", text).strip()
    for question in QUESTION.findall(spoken_text):
        if ADDRESSED.search(question):
            yield f"{where} asks {question.strip()!r}, but nothing lets the player answer"


def heights():
    for path in sorted((MOD / "main/java/io/github/roman16/wayfarer").rglob("*.java")):
        if path.name == "Ground.java" or INTERFACE in path.parents:
            continue
        for number, line in enumerate(path.read_text().splitlines(), start=1):
            if LOADED_HEIGHT.search(line):
                yield f"{path.relative_to(ROOT)}:{number}: the loaded land's height is read through Ground (land, crown, sky, stand)"


def packed(meta):
    spec = tomllib.loads(meta.read_text())
    download = spec["download"]
    return zipfile.ZipFile(
        instance.download(instance.source(spec), download["hash-format"], download["hash"], spec["name"])
    )


def enchantments():
    spec = tomllib.loads((ROOT / "generator/enchantments.toml").read_text())
    with packed(PACK / "resourcepacks/enchant-icons-countxd.pw.toml") as icons:
        drawn = {
            char
            for provider in json.loads(icons.read("assets/minecraft/font/default.json"))["providers"]
            for char in provider["chars"]
        }
    for icon, char in sorted(spec["icons"].items()):
        if char not in drawn:
            yield f"generator/enchantments.toml: the {icon} icon is no character of Enchant Icons' font"
    found = {}
    lines = {}
    for meta in sorted(PACK.glob("mods/*.pw.toml")):
        if tomllib.loads(meta.read_text()).get("side") not in SIDES:
            continue
        with packed(meta) as archive:
            for name in archive.namelist():
                parts = name.split("/")
                if (
                    parts[0] == "data"
                    and len(parts) > 3
                    and parts[2] == "enchantment"
                    and name.endswith(".json")
                    and parts[1] != "minecraft"
                ):
                    found[f"{parts[1]}:{'/'.join(parts[3:])[:-5]}"] = json.loads(archive.read(name)).get("description")
                elif parts[0] == "assets" and name.endswith("lang/en_us.json"):
                    lines.update(json.loads(archive.read(name).decode("utf-8-sig"), strict=False))
    for enchantment, description in sorted(found.items()):
        if enchantment in spec["hidden"]:
            continue
        entry = spec["enchantments"].get(enchantment)
        if entry is None:
            yield f"{enchantment} has no icon: add it to generator/enchantments.toml"
            continue
        namespace, path = enchantment.split(":")
        translated = description.get("translate") if isinstance(description, dict) else None
        keys = [f"enchantment.{namespace}.{path}.{kind}" for kind in ("desc", "description")]
        keys += [f"{translated}.{kind}" for kind in ("desc", "description")] if translated else []
        if "description" not in entry and not any(key in lines for key in keys):
            yield f"{enchantment} has no description: give it one in generator/enchantments.toml"
        if entry["key"] != translated:
            yield f"generator/enchantments.toml names {enchantment} by {entry['key']}, but the game asks for {translated}"
    for enchantment in sorted(set(spec["enchantments"]) - set(found)):
        yield f"generator/enchantments.toml lists {enchantment}, which no mod in the pack has"


def foes():
    listed = tomllib.loads((ROOT / "generator/foes.toml").read_text())["foes"]
    named = set()
    for meta in sorted(PACK.glob("mods/*.pw.toml")):
        with packed(meta) as archive:
            for name in archive.namelist():
                if name.startswith("assets/") and name.endswith("lang/en_us.json"):
                    named.update(json.loads(archive.read(name).decode("utf-8-sig"), strict=False))
    vanilla = sorted(ROOT.glob("build/sources/neoforge-*/assets/minecraft/lang/en_us.json"))
    if vanilla:
        named.update(json.loads(vanilla[-1].read_text()))
    for creature, act in sorted(listed.items()):
        namespace, path = creature.split(":")
        if (namespace != "minecraft" or vanilla) and f"entity.{namespace}.{path}" not in named:
            yield f"generator/foes.toml lists {creature}, which no mod in the pack has"
        if act not in (2, 3):
            yield f"generator/foes.toml gives {creature} act {act}, but foes wait only for Act 2 or 3"


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
        *heights(),
        *words(),
        *voices(),
        *enchantments(),
        *foes(),
    ]
    for finding in findings:
        print(finding)
    for note in waiting():
        print(note)
    if findings:
        sys.exit(f"{len(findings)} rule findings")


main()

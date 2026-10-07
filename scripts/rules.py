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
SOUND_KINDS = {"frequent", "loop", "rare"}
TRANSLATION = re.compile(r'"(wayfarer\.[a-z_.]+[a-z_])"')


def sides():
    for meta in sorted(PACK.glob("*/*.pw.toml")):
        side = tomllib.loads(meta.read_text()).get("side")
        if side not in SIDES:
            yield f"{meta.relative_to(ROOT)}: side is {side!r}; singleplayer runs the server inside the client, so a mod is either client or both"


def shrine_eyes():
    return tomllib.loads((ROOT / "generator/eyes.toml").read_text())["eyes"]


def eyes():
    for eye, spec in sorted(shrine_eyes().items()):
        if not spec.get("lore") or not spec.get("people"):
            yield f"the {eye} eye has no people or lore in generator/eyes.toml"
        if not (MOD / f"generated/resources/data/wayfarer/structure/shrine/{eye}/hall.nbt").exists():
            yield f"the {eye} eye has no shrine; run just generate"


def translations():
    known = set(json.loads(LANG.read_text()))
    for source in sorted(MOD.glob("*/java/**/*.java")):
        for key in TRANSLATION.findall(source.read_text()):
            if key not in known and not key.endswith("."):
                yield f"{source.relative_to(ROOT)} uses {key}, which {LANG.relative_to(ROOT)} does not name"


def titles():
    lang = json.loads(LANG.read_text())
    shown = {key: text for key, text in lang.items() if key.startswith("wayfarer.night.")}
    shown |= {f"the people of the {eye} eye": spec["people"] for eye, spec in shrine_eyes().items()}
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
    return [*story["memories"], *story["found"]] + [
        book for collection in story["collections"] for book in collection["books"]
    ]


def books():
    story = tomllib.loads((ROOT / "story/books.toml").read_text())
    entries = story_books()
    titles = [entry["title"] for entry in entries] + [spec["people"] for spec in shrine_eyes().values()]
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
    eyes = tomllib.loads((ROOT / "generator/eyes.toml").read_text())
    if len(story["memories"]) != len(eyes["eyes"]):
        yield f"story/books.toml has {len(story['memories'])} memories for {len(eyes['eyes'])} shards"


KINDS = {
    "bury",
    "camp",
    "caravan",
    "clues",
    "dawn",
    "dusk",
    "hold",
    "horse",
    "reach",
    "rest",
    "shard",
    "sleep",
    "talk",
    "walk",
}
PLACES = {"bell", "cache", "caravan", "cellar", "diggers", "diggers_hall", "first_camp", "last_camp", "tollbridge"}
KNOWN = {"feeling"}
TASKS = {
    "bed",
    "bury",
    "ember",
    "enter",
    "fire",
    "horse",
    "map",
    "marks",
    "pack",
    "rest",
    "search",
    "stone",
    "sweep",
    "tent",
}


def story():
    quests = tomllib.loads((ROOT / "story/quests.toml").read_text())["quests"]
    people = set(tomllib.loads((ROOT / "story/people.toml").read_text()))
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
        for step in quest["steps"]:
            where = f"story/quests.toml: {quest['id']}/{step['id']}"
            if step["kind"] not in KINDS:
                yield f"{where} is a {step['kind']} step; the engine knows {', '.join(sorted(KINDS))}"
            said = step.get("lines", []) + step.get("done", []) + step.get("burial", [])
            named = [line["by"] for line in said if "by" in line]
            named += [step[key] for key in ("whom", "with") if key in step]
            named += (
                list(step.get("people", {}))
                + list(step.get("poses", {}))
                + list(step.get("moves", {}))
                + list(step.get("remarks", {}))
                + step.get("keep", [])
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
            for line in said:
                if "sound" in line and line["sound"] not in sounds["effects"]:
                    yield f"{where} plays the sound {line['sound']}, which story/sounds.toml does not have"
                if "music" in line and line["music"] not in sounds["music"]:
                    yield f"{where} plays the piece {line['music']}, which story/sounds.toml does not have"
            if "music" in step and not sounds["music"].get(step["music"], {}).get("loop"):
                yield f"{where} loops the piece {step['music']}, which story/sounds.toml does not mark loop = true"
            if step["kind"] == "shard" and "gift" in step and f"wayfarer.gift.{step['gift']}" not in lang:
                yield f"{where} gives the gift {step['gift']}; add wayfarer.gift.{step['gift']} to the language file"
            for word in step.get("words", {}):
                if f"wayfarer.word.{word}" not in lang:
                    yield f"{where} teaches the word {word}; add wayfarer.word.{word} to the language file"
            places = [step[key] for key in ("from", "place", "at") if key in step]
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


def stone(where, words):
    lines = []
    for word in words.split():
        if lines and len(lines[-1]) + 1 + len(word) <= SIGN_WIDTH:
            lines[-1] += " " + word
        else:
            lines.append(word)
    if len(lines) > SIGN_LINES or any(len(line) > SIGN_WIDTH for line in lines):
        yield f"{where}: the stone's words {words!r} take {len(lines)} lines; a sign holds {SIGN_LINES} of {SIGN_WIDTH} letters"


def main():
    findings = [*sides(), *eyes(), *translations(), *titles(), *recipes(), *books(), *story(), *gallery.problems()]
    for finding in findings:
        print(finding)
    if findings:
        sys.exit(f"{len(findings)} rule findings")


main()

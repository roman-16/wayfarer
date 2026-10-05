import json
import re
import sys
from pathlib import Path

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


KINDS = {"bury", "camp", "caravan", "clues", "dawn", "dusk", "hold", "reach", "rest", "sleep", "talk", "walk"}
PLACES = {"bell", "caravan", "cellar", "last_camp", "tollbridge"}
TASKS = {"bed", "bury", "ember", "enter", "fire", "map", "pack", "rest", "stone", "tent"}


def story():
    quests = tomllib.loads((ROOT / "story/quests.toml").read_text())["quests"]
    people = set(tomllib.loads((ROOT / "story/people.toml").read_text()))
    lang = json.loads((MOD / "main/resources/assets/wayfarer/lang/en_us.json").read_text())
    ids = [quest["id"] for quest in quests]
    for quest in quests:
        for after in quest.get("after", []):
            if after not in ids:
                yield f"story/quests.toml: {quest['id']} comes after {after}, which is not a quest"
        for step in quest["steps"]:
            where = f"story/quests.toml: {quest['id']}/{step['id']}"
            if step["kind"] not in KINDS:
                yield f"{where} is a {step['kind']} step; the engine knows {', '.join(sorted(KINDS))}"
            named = [line["by"] for line in step.get("lines", []) + step.get("done", []) if "by" in line]
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
            for word in step.get("words", {}):
                if f"wayfarer.word.{word}" not in lang:
                    yield f"{where} teaches the word {word}; add wayfarer.word.{word} to the language file"
            places = [step[key] for key in ("from", "place", "at") if key in step]
            places += [move["place"] for move in step.get("moves", {}).values()]
            for place in places:
                if place not in PLACES:
                    yield f"{where} names the place {place}; the story builds {', '.join(sorted(PLACES))}"
            for task in step.get("tasks", []):
                if task not in TASKS:
                    yield f"{where} names the task {task}; the panel knows {', '.join(sorted(TASKS))}"
                elif f"wayfarer.task.{task}" not in lang:
                    yield f"{where} names the task {task}; add wayfarer.task.{task} to the language file"
            if step["kind"] == "clues" and step["needed"] > len(step.get("clues", [])):
                yield f"{where} needs {step['needed']} clues but has {len(step.get('clues', []))}"


def states():
    recipe = tomllib.loads((ROOT / "story/states.toml").read_text())
    lines = []
    for word in recipe["stone"].split():
        if lines and len(lines[-1]) + 1 + len(word) <= SIGN_WIDTH:
            lines[-1] += " " + word
        else:
            lines.append(word)
    if len(lines) > SIGN_LINES or any(len(line) > SIGN_WIDTH for line in lines):
        yield f"story/states.toml: the stone's words take {len(lines)} lines; a sign holds {SIGN_LINES} of {SIGN_WIDTH} letters"


def main():
    findings = [*sides(), *eyes(), *translations(), *titles(), *recipes(), *books(), *story(), *states()]
    for finding in findings:
        print(finding)
    if findings:
        sys.exit(f"{len(findings)} rule findings")


main()

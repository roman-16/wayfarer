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
TRANSLATION = re.compile(r'"(wayfarer\.[a-z_.]+[a-z_])"')
FIRST = re.compile(r'\bfirst\([^,()]+, "([a-z_]+)"\)')
BARE_TRIGGERS = {"awake", "slain", "start"}


def sides():
    for meta in sorted(PACK.glob("*/*.pw.toml")):
        side = tomllib.loads(meta.read_text()).get("side")
        if side not in SIDES:
            yield f"{meta.relative_to(ROOT)}: side is {side!r}; singleplayer runs the server inside the client, so a mod is either client or both"


def shrine_eyes():
    return tomllib.loads((ROOT / "generator/eyes.toml").read_text())["eyes"]


def eyes():
    for eye, spec in sorted(shrine_eyes().items()):
        if not spec.get("lore") or not spec.get("people") or not spec.get("hint"):
            yield f"the {eye} eye has no people, hint or lore in generator/eyes.toml"
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


def quests():
    book = tomllib.loads((ROOT / "story/quests.toml").read_text())
    eye_tiers = {spec["tier"] for spec in shrine_eyes().values()}
    known = {quest["id"] for chapter in book["chapters"] for quest in chapter.get("quests", [])}
    for chapter in book["chapters"]:
        if "eyes" in chapter and chapter["eyes"] not in eye_tiers:
            yield f"story/quests.toml: chapter {chapter['id']} lists eyes of tier {chapter['eyes']}, which no eye has"
        needs = [(chapter["id"], name) for name in chapter.get("eye_quests", {}).get("after", [])]
        needs += [(quest["id"], name) for quest in chapter.get("quests", []) for name in quest.get("after", [])]
        for owner, name in needs:
            if name not in known:
                yield f"story/quests.toml: {owner} comes after {name}, which is no quest"
        rewarded = chapter.get("eye_quests", {}).get("rewards") if "eyes" in chapter else True
        if not rewarded:
            yield f"story/quests.toml: the eye quests of chapter {chapter['id']} have no reward"
        for quest in chapter.get("quests", []):
            if not quest.get("rewards"):
                yield f"story/quests.toml: {quest['id']} has no reward"


def story_books():
    story = tomllib.loads((ROOT / "story/books.toml").read_text())
    return [story["letter"], *story["memories"], *story["found"]] + [
        book for collection in story["collections"] for book in collection["books"]
    ]


def fire():
    told = tomllib.loads((ROOT / "story/fire.toml").read_text())
    steps = {step["id"] for step in told["steps"]}
    leads = {lead["id"] for lead in told["leads"]}
    books = {book["id"] for book in story_books()}
    for identifier in sorted(
        {step["id"] for step in told["steps"] if [s["id"] for s in told["steps"]].count(step["id"]) > 1}
    ):
        yield f"story/fire.toml: two steps share the id {identifier!r}"
    for step in told["steps"]:
        owner = f"story/fire.toml: step {step['id']}"
        if step["speaker"] not in told["speakers"]:
            yield f"{owner} is spoken by {step['speaker']!r}, which is no speaker"
        for name in step.get("after", []):
            if name not in steps:
                yield f"{owner} comes after {name}, which is no step"
        for name in step.get("found", []):
            if name not in leads:
                yield f"{owner} waits for the lead {name}, which is no lead"
        if "lead" in step and step["lead"] not in leads:
            yield f"{owner} gives the lead {step['lead']}, which is no lead"
        if ("lead" in step) != any("{lead}" in line for line in step["lines"]):
            yield f"{owner} must name {{lead}} in a line exactly when it gives a lead"
    given = {step["lead"] for step in told["steps"] if "lead" in step}
    for lead in told["leads"]:
        if lead["id"] not in given:
            yield f"story/fire.toml: no step gives the lead {lead['id']}"
        if lead.get("page") and lead["page"] not in books:
            yield f"story/fire.toml: the lead {lead['id']} copies {lead['page']}, which is no book in story/books.toml"
        if ("camp" in lead) == ("structures" in lead):
            yield f"story/fire.toml: the lead {lead['id']} needs either a camp or structures"
    for scene in told["scenes"]:
        if scene["speaker"] not in told["speakers"]:
            yield f"story/fire.toml: scene {scene['id']} is spoken by {scene['speaker']!r}, which is no speaker"
        if scene.get("book") and scene["book"] not in books:
            yield f"story/fire.toml: scene {scene['id']} copies {scene['book']}, which is no book in story/books.toml"
    for speaker, spec in sorted(told["speakers"].items()):
        for name in spec["names"]:
            if name.get("after") and name["after"] not in steps:
                yield f"story/fire.toml: {speaker} is renamed after {name['after']}, which is no step"
    for eye, spec in sorted(shrine_eyes().items()):
        if "{lead}" not in spec.get("place", {}).get("line", ""):
            yield f"generator/eyes.toml: the {eye} eye's place line must name {{lead}}"


def notes():
    told = tomllib.loads((ROOT / "story/fire.toml").read_text())
    known = {
        "book": {book["id"] for book in story_books()},
        "first": {name for source in MOD.glob("main/java/**/*.java") for name in FIRST.findall(source.read_text())},
        "lead": {lead["id"] for lead in told["leads"]},
        "scene": {scene["id"] for scene in told["scenes"]},
        "shards": {str(count) for count in range(1, len(shrine_eyes()) + 1)},
        "step": {step["id"] for step in told["steps"]},
    }
    written = tomllib.loads((ROOT / "story/notes.toml").read_text())
    for kind in ("summary", "words"):
        identifiers = [entry["id"] for entry in written[kind]]
        for identifier in sorted({name for name in identifiers if identifiers.count(name) > 1}):
            yield f"story/notes.toml: two {kind} entries share the id {identifier!r}"
        for entry in written[kind]:
            trigger = entry["when"]
            kind_of, _, value = trigger.partition(":")
            if trigger in BARE_TRIGGERS or value in known.get(kind_of, set()):
                continue
            yield f"story/notes.toml: {kind} {entry['id']} waits for {trigger!r}, which never happens"


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


def main():
    findings = [*sides(), *eyes(), *translations(), *titles(), *recipes(), *quests(), *books(), *fire(), *notes()]
    for finding in findings:
        print(finding)
    if findings:
        sys.exit(f"{len(findings)} rule findings")


main()

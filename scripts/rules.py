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
SIDES = {"both", "client"}
TRANSLATION = re.compile(r'"(wayfarer\.[a-z_.]+[a-z_])"')


def sides():
    for meta in sorted(PACK.glob("*/*.pw.toml")):
        side = tomllib.loads(meta.read_text()).get("side")
        if side not in SIDES:
            yield f"{meta.relative_to(ROOT)}: side is {side!r}; singleplayer runs the server inside the client, so a mod is either client or both"


def eyes():
    for eye, spec in sorted(tomllib.loads((ROOT / "generator/eyes.toml").read_text()).items()):
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
    eye_tiers = {spec["tier"] for spec in tomllib.loads((ROOT / "generator/eyes.toml").read_text()).values()}
    known = {quest["id"] for chapter in book["chapters"] for quest in chapter.get("quests", [])}
    for chapter in book["chapters"]:
        if "eyes" in chapter and chapter["eyes"] not in eye_tiers:
            yield f"story/quests.toml: chapter {chapter['id']} lists eyes of tier {chapter['eyes']}, which no eye has"
        needs = [(chapter["id"], name) for name in chapter.get("eye_quests", {}).get("after", [])]
        needs += [(quest["id"], name) for quest in chapter.get("quests", []) for name in quest.get("after", [])]
        for owner, name in needs:
            if name not in known:
                yield f"story/quests.toml: {owner} comes after {name}, which is no quest"


def books():
    titles = [
        book["title"]
        for collection in tomllib.loads((ROOT / "story/books.toml").read_text())["collections"]
        for book in collection["books"]
    ]
    titles += [spec["people"] for spec in tomllib.loads((ROOT / "generator/eyes.toml").read_text()).values()]
    for title in titles:
        if len(title) > BOOK_TITLE_LIMIT:
            yield f"the book title {title!r} is {len(title)} characters; Minecraft allows {BOOK_TITLE_LIMIT}"


def main():
    findings = [*sides(), *eyes(), *translations(), *recipes(), *quests(), *books()]
    for finding in findings:
        print(finding)
    if findings:
        sys.exit(f"{len(findings)} rule findings")


main()

import hashlib
import json
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / "build/states"
STORY = ROOT / "mod/src/generated/resources/wayfarer/story.json"
RESOURCES = ("mod/src/dev/resources", "mod/src/generated/dev", "mod/src/generated/resources", "mod/src/main/resources")
ASSETS = "assets"
STARTUP = ("style.json",)
GROUPS = {
    "pack": "the pack's mods or configs",
    "build": "the mod's build",
    "startup": "a file the game reads while it starts",
    "data": "the mod's data or the story",
    "assets": "the textures, models, fonts, sounds or lang",
}
LAUNCH = ("pack", "build", "startup")
WORLD = ("pack", "build", "startup", "data")


def check(state):
    if state not in steps():
        sys.exit(f"there is no state {state}; a state is a step of a main quest: {', '.join(steps())}")


def steps():
    quests = json.loads(STORY.read_text())["quests"]
    return [f"{quest['id']}/{step['id']}" for quest in quests if quest["kind"] == "main" for step in quest["steps"]]


def group(file):
    relative = file.relative_to(ROOT)
    parts = relative.parts
    if parts[0] == "pack":
        return "pack"
    if parts[0] in ("balance.toml", "story"):
        return "data"
    resources = next((top for top in RESOURCES if relative.is_relative_to(top)), None)
    if resources is None:
        return "build"
    inner = relative.relative_to(resources).parts
    if inner[0] == ASSETS:
        return "assets"
    if inner[-1].endswith(".mixins.json") or (inner[0] == "wayfarer" and inner[-1] in STARTUP):
        return "startup"
    return "data"


def files():
    tops = (
        "balance.toml",
        "mod/build.gradle",
        "mod/gradle.properties",
        "mod/settings.gradle",
        "mod/src/main/templates",
        "pack",
        "story",
        *RESOURCES,
    )
    for top in tops:
        path = ROOT / top
        yield from (
            sorted(file for file in path.rglob("*") if file.is_file()) if path.is_dir() else [path] * path.exists()
        )


def digests():
    found = {name: hashlib.sha256() for name in GROUPS}
    for file in files():
        found[group(file)].update(str(file.relative_to(ROOT)).encode() + b"\0" + file.read_bytes())
    return {name: digest.hexdigest() for name, digest in found.items()}


def combined(found, names):
    return hashlib.sha256("".join(found[name] for name in names).encode()).hexdigest()


def changed(before, after, names):
    return [f"{GROUPS[name]} changed" for name in names if before.get(name) != after.get(name)]


def folder(driver, mode, state):
    return CACHE / f"{driver}-{mode}" / combined(digests(), WORLD)[:16] / state


def prepare(cache, world, rebuild):
    shutil.rmtree(world, ignore_errors=True)
    kept = cache.parent.parent
    if rebuild:
        shutil.rmtree(kept, ignore_errors=True)
    for stale in cache.parent.parent.parent.glob("*"):
        if stale != kept:
            shutil.rmtree(stale, ignore_errors=True)
    if (cache / "level.dat").exists():
        shutil.copytree(cache, world, dirs_exist_ok=True)
        return cache.relative_to(kept).as_posix()
    order = steps()
    target = order.index(cache.relative_to(kept).as_posix())
    for earlier in reversed(order[:target]):
        if (kept / earlier / "level.dat").exists():
            shutil.copytree(kept / earlier, world, dirs_exist_ok=True)
            return earlier
    return None

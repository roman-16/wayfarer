import hashlib
import json
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / "build/states"
STORY = ROOT / "mod/src/generated/resources/wayfarer/story.json"
INPUTS = (
    "balance.toml",
    "mod/build.gradle",
    "mod/gradle.properties",
    "mod/src/dev/resources",
    "mod/src/generated",
    "mod/src/main/resources",
    "mod/src/main/templates",
    "pack",
    "story",
)


def check(state):
    quests = json.loads(STORY.read_text())["quests"]
    steps = [f"{quest['id']}/{step['id']}" for quest in quests if quest["kind"] == "main" for step in quest["steps"]]
    if state not in steps:
        sys.exit(f"there is no state {state}; a state is a step of a main quest: {', '.join(steps)}")


def inputs():
    digest = hashlib.sha256()
    for top in INPUTS:
        path = ROOT / top
        for file in sorted(path.rglob("*")) if path.is_dir() else [path]:
            if file.is_file():
                digest.update(str(file.relative_to(ROOT)).encode() + b"\0" + file.read_bytes())
    return digest.hexdigest()


def folder(driver, mode, state):
    digest = hashlib.sha256(f"{driver} {mode} {state} {inputs()}\n".encode())
    return CACHE / f"{driver}-{mode}" / state / digest.hexdigest()[:16]


def prepare(cache, world, rebuild):
    shutil.rmtree(world, ignore_errors=True)
    if rebuild:
        shutil.rmtree(cache, ignore_errors=True)
    if (cache / "level.dat").exists():
        shutil.copytree(cache, world)
        return True
    shutil.rmtree(cache.parent, ignore_errors=True)
    cache.parent.mkdir(parents=True)
    return False

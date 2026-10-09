import argparse
import math
import random
import re
import shutil
import statistics
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import tomllib
from drive import Failure, Server

TIMEOUT_SECONDS = 900
LOCATE_SECONDS = 300
STOP_SECONDS = 300
ROOT = Path(__file__).resolve().parent.parent
FOUND = re.compile(r"The nearest (\S+)(?: \([^)]*\))? is at \[(-?\d+), [^,]+, (-?\d+)\] \((\d+) blocks away\)")
MISSING = re.compile(r"Could not find a structure of type \"?(\S+?)\"? nearby")
BIOME_SHIFT_BLOCKS = 128
MAX_STEP = 2.0
SURVEY_RADIUS_BLOCKS = 10000
SURVEY_SEEDS = (1, 2, 3, 4, 5)
SURVEY_PORT = 25600
VOICE_PORT = 24600
SHARED = ("libraries", "mods")
WALK_BLOCKS_PER_SECOND = 4.317
ASYNC = re.compile(r"(?m)^\s*locateCommandEnabled = false$")
CONCURRENT = re.compile(r"(?m)^maxConcurrentLocates = \d+$")
CONCURRENT_SEARCHES = 8
REJECTED = re.compile(r"Tried to load invalid item|Couldn't parse element .*\]:wayfarer:")


def question(structure, at):
    prefix = f"execute positioned {at[0]} 0 {at[1]} run " if at else ""

    def answer(said):
        found = FOUND.search(said)
        if found and found.group(1) == structure:
            return int(found.group(4)), int(found.group(2)), int(found.group(3))
        if MISSING.search(said) and structure in said:
            return ()
        return None

    return f"{prefix}locate structure {structure}", answer


def locate(server, structures, at=None):
    answers = server.asks([question(structure, at) for structure in structures], LOCATE_SECONDS)
    return {structure: found or None for structure, found in zip(structures, answers, strict=True)}


def check(server):
    balance = tomllib.loads((ROOT / "balance.toml").read_text())
    strongholds = balance["strongholds"]
    eyes = sorted(
        eye
        for eye, spec in tomllib.loads((ROOT / "generator/eyes.toml").read_text())["eyes"].items()
        if not spec.get("story", False)
    )
    kinds = balance["structures"]["every_blocks"]
    cut = tomllib.loads((ROOT / "generator/owners.toml").read_text())["cut"]
    shrines = {eye: f"wayfarer:shrine/{eye}" for eye in eyes}
    owners = {kind: f"#wayfarer:owners/{kind}" for kind in kinds}
    found = locate(server, ["betterstrongholds:stronghold", *shrines.values(), *owners.values(), *cut])
    failures = []
    ring = found["betterstrongholds:stronghold"][0] if found["betterstrongholds:stronghold"] else None
    low = strongholds["radius_blocks"] - strongholds["jitter_blocks"] - BIOME_SHIFT_BLOCKS * 4
    high = strongholds["radius_blocks"] + strongholds["jitter_blocks"] + BIOME_SHIFT_BLOCKS * 4
    print(f"nearest stronghold: {ring} blocks")
    if ring is None or not low <= ring <= high:
        failures.append(f"the nearest stronghold is {ring} blocks from spawn, not {low}-{high}")
    for eye, structure in shrines.items():
        print(f"nearest {eye} shrine: {found[structure][0] if found[structure] else None} blocks")
        if found[structure] is None:
            failures.append(f"no {eye} shrine was found")
    for kind, structure in owners.items():
        print(f"nearest {kind}: {found[structure][0] if found[structure] else None} blocks")
        if found[structure] is None:
            failures.append(f"no {kind} was found")
    for structure in cut:
        if found[structure] is not None:
            failures.append(
                f"{structure} is cut in generator/owners.toml but still generates {found[structure][0]} blocks from spawn"
            )
    failures += [f"the game rejects our data: {line.strip()[:300]}" for line in server.lines() if REJECTED.search(line)]
    return failures


def points(count):
    rng = random.Random(0)
    for _ in range(count):
        radius = SURVEY_RADIUS_BLOCKS * math.sqrt(rng.random())
        angle = rng.random() * math.tau
        yield round(radius * math.cos(angle)), round(radius * math.sin(angle))


def world(server, seed):
    target = server.parent / "survey" / str(seed)
    shutil.rmtree(target, ignore_errors=True)
    target.mkdir(parents=True)
    for name in SHARED:
        (target / name).symlink_to(server / name)
    for path in server.iterdir():
        if path.is_file() and path.suffix != ".log":
            shutil.copy2(path, target / path.name)
    shutil.copytree(server / "config", target / "config")
    configure(target, {"level-seed": seed, "server-port": SURVEY_PORT + seed})
    voice = target / "config/voicechat/voicechat-server.properties"
    voice.parent.mkdir(parents=True, exist_ok=True)
    voice.write_text(f"port={VOICE_PORT + seed}\n")
    return target


def journey(server, eyes, start, legs):
    pos = start
    for tier in legs:
        remaining = [eye for eye in sorted(eyes) if eyes[eye]["tier"] == tier and not eyes[eye].get("story", False)]
        while remaining:
            hits = locate(server, [f"wayfarer:shrine/{eye}" for eye in remaining], pos)
            found = [(hit, eye) for eye in remaining if (hit := hits[f"wayfarer:shrine/{eye}"])]
            if not found:
                break
            (distance, x, z), eye = min(found)
            legs[tier].append(distance)
            pos = (x, z)
            remaining.remove(eye)


def minutes(blocks):
    return blocks / WALK_BLOCKS_PER_SECOND / 60


def survey(server, count, apply):
    balance = tomllib.loads((ROOT / "balance.toml").read_text())["shrines"]
    targets = {1: balance["tier_one_leg_blocks"], 2: balance["tier_two_leg_blocks"]}
    eyes_path = ROOT / "generator/eyes.toml"
    shrines = tomllib.loads(eyes_path.read_text())
    starts = list(points(count))

    def walk(seed):
        legs = {tier: [] for tier in targets}

        def measure(server):
            for start in starts:
                journey(server, shrines["eyes"], start, legs)
            return []

        boot(world(server, seed), measure)
        return legs

    legs = {tier: [] for tier in targets}
    with ThreadPoolExecutor(max_workers=len(SURVEY_SEEDS)) as pool:
        for found in pool.map(walk, SURVEY_SEEDS):
            for tier, distances in found.items():
                legs[tier] += distances
    text = eyes_path.read_text()
    print(
        f"{'tier':<4} {'legs':>5} {'spacing':>7} {'median':>7} {'walk':>7} {'shortest':>8} {'target':>7} {'walk':>7} {'tuned':>6}"
    )
    for tier, target in targets.items():
        spacing = shrines["tiers"][str(tier)]["spacing"]
        found = legs[tier]
        median = statistics.median(found) if found else 0
        ratio = min(MAX_STEP, max(1 / MAX_STEP, math.sqrt(target / median))) if median else 1
        tuned = max(2, round(spacing * ratio))
        shortest = min(found) if found else 0
        print(
            f"{tier:<4} {len(found):>5} {spacing:>7} {median:>7.0f} {minutes(median):>5.0f} m {shortest:>8}"
            + f" {target:>7} {minutes(target):>5.0f} m {tuned:>6}"
        )
        text = re.sub(rf"(\[tiers\.{tier}\]\n)spacing = \d+", rf"\g<1>spacing = {tuned}", text, count=1)
    if apply:
        eyes_path.write_text(text)
        print(
            f"wrote the tuned spacings to {eyes_path.relative_to(ROOT)}; run just generate, then survey again to confirm"
        )


def configure(directory, overrides):
    properties = directory / "server.properties"
    overrides = {"max-tick-time": -1} | overrides
    lines = [line for line in properties.read_text().splitlines() if line.split("=", 1)[0] not in overrides]
    properties.write_text("\n".join([*lines, *(f"{key}={value}" for key, value in overrides.items())]) + "\n")


def boot(directory, action):
    (directory / "eula.txt").write_text("eula=true\n")
    configure(directory, {})
    searches = directory / "config/asynclocator.toml"
    text = ASYNC.sub("locateCommandEnabled = true", searches.read_text())
    concurrent = f"maxConcurrentLocates = {CONCURRENT_SEARCHES}"
    searches.write_text(CONCURRENT.sub(concurrent, text) if CONCURRENT.search(text) else f"{concurrent}\n{text}")
    server = Server(directory, ["bash", "run.sh", "--nogui"])
    started = time.monotonic()
    server.start(TIMEOUT_SECONDS)
    try:
        failures = action(server)
    finally:
        server.stop(STOP_SECONDS)
    if failures:
        raise Failure("\n".join(failures))
    return time.monotonic() - started, server.log


def main():
    parser = argparse.ArgumentParser(
        description="Boot the pack's dedicated server headless and check the world, or survey shrine distances"
    )
    parser.add_argument("server", type=Path)
    parser.add_argument(
        "--survey",
        type=int,
        metavar="JOURNEYS",
        help="in each survey world at once, walk this many journeys to the nearest shrine not yet taken, instead of checking",
    )
    parser.add_argument("--apply", action="store_true", help="write the survey's tuned spacings to generator/eyes.toml")
    arguments = parser.parse_args()
    server = arguments.server.resolve()
    try:
        if arguments.survey:
            survey(server, arguments.survey, arguments.apply)
            return
        seconds, log = boot(server, check)
    except Failure as failure:
        sys.exit(str(failure))
    seed = tomllib.loads((ROOT / "story/states.toml").read_text())["seed"]
    print(f"the whole pack booted in {seconds:.0f} s on seed {seed}; log in {log}")


main()

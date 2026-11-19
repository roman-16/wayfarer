import argparse
import math
import re
import sys
import time
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
ASYNC = re.compile(r"(?m)^\s*locateCommandEnabled = false$")
CONCURRENT = re.compile(r"(?m)^maxConcurrentLocates = \d+$")
CONCURRENT_SEARCHES = 8
REJECTED = re.compile(r"Tried to load invalid item|Couldn't parse element .*\]:wayfarer:")
STRONGHOLD = "betterstrongholds:stronghold"
SEA_LEVEL = 63
NOT_LOADED = "That position is not loaded"


def question(structure, at):
    prefix = f"execute positioned {at[0]} 0 {at[1]} run " if at else ""

    def answer(said):
        found = FOUND.search(said)
        if found and found.group(1) == structure:
            return int(found.group(4)), int(found.group(2)), int(found.group(3))
        missing = MISSING.search(said)
        if missing and missing.group(1) == structure:
            return ()
        return None

    return f"{prefix}locate structure {structure}", answer


def locate(server, structures, at=None):
    answers = server.asks([question(structure, at) for structure in structures], LOCATE_SECONDS)
    return {structure: found or None for structure, found in zip(structures, answers, strict=True)}


def answered(expected):
    return lambda said: said if any(word in said for word in expected) else None


def ring(server, strongholds):
    found = set()
    for index in range(strongholds["count"] * 2):
        angle = math.tau * index / (strongholds["count"] * 2)
        at = (
            round(strongholds["radius_blocks"] * math.cos(angle)),
            round(strongholds["radius_blocks"] * math.sin(angle)),
        )
        hit = locate(server, [STRONGHOLD], at)[STRONGHOLD]
        if hit:
            found.add((hit[1], hit[2]))
    return sorted(found)


def at_sea(server, x, z):
    server.asks([(f"forceload add {x} {z}", answered(["force loaded", "already"]))], LOCATE_SECONDS)
    deadline = time.monotonic() + LOCATE_SECONDS
    try:
        while True:
            [said] = server.asks(
                [(f"execute if biome {x} {SEA_LEVEL} {z} #minecraft:is_ocean", answered(["Test ", NOT_LOADED]))],
                LOCATE_SECONDS,
            )
            if NOT_LOADED not in said:
                return said.startswith("Test passed")
            if time.monotonic() > deadline:
                raise Failure(f"the chunk at {x}, {z} did not load within {LOCATE_SECONDS} s")
            time.sleep(1)
    finally:
        server.asks([(f"forceload remove {x} {z}", answered(["no longer", "not marked", "Unmarked"]))], LOCATE_SECONDS)


def check(server):
    balance = tomllib.loads((ROOT / "balance.toml").read_text())
    strongholds = balance["strongholds"]
    eyes = sorted(
        eye for eye, spec in tomllib.loads((ROOT / "generator/eyes.toml").read_text())["eyes"].items() if "lore" in spec
    )
    kinds = balance["structures"]["every_blocks"]
    cut = tomllib.loads((ROOT / "generator/owners.toml").read_text())["cut"]
    halls = {eye: f"wayfarer:hall/{eye}" for eye in eyes}
    owners = {kind: f"#wayfarer:owners/{kind}" for kind in kinds}
    found = locate(server, [STRONGHOLD, *halls.values(), *owners.values(), *cut])
    failures = []
    nearest = found[STRONGHOLD][0] if found[STRONGHOLD] else None
    low = strongholds["radius_blocks"] - strongholds["jitter_blocks"] - BIOME_SHIFT_BLOCKS * 4
    high = strongholds["radius_blocks"] + strongholds["jitter_blocks"] + BIOME_SHIFT_BLOCKS * 4
    print(f"nearest stronghold: {nearest} blocks")
    if nearest is None or not low <= nearest <= high:
        failures.append(f"the nearest stronghold is {nearest} blocks from spawn, not {low}-{high}")
    placed = ring(server, strongholds)
    print(f"strongholds on the ring: {len(placed)} of {strongholds['count']}")
    if len(placed) != strongholds["count"]:
        failures.append(f"the ring has {len(placed)} strongholds, not {strongholds['count']}")
    failures += [f"the stronghold at {x}, {z} lies under the sea" for x, z in placed if at_sea(server, x, z)]
    grown = [structure for structure in halls.values() if found[structure] is not None]
    print(f"halls the world grows: {', '.join(grown) or 'none'}")
    failures += [
        f"{structure} grows in the world {found[structure][0]} blocks from spawn; every hall is put down by its quest"
        for structure in grown
    ]
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
    parser = argparse.ArgumentParser(description="Boot the pack's dedicated server headless and check the world")
    parser.add_argument("server", type=Path)
    arguments = parser.parse_args()
    server = arguments.server.resolve()
    seed = tomllib.loads((ROOT / "story/states.toml").read_text())["seed"]
    try:
        configure(server, {"level-seed": seed})
        seconds, log = boot(server, check)
    except Failure as failure:
        sys.exit(str(failure))
    print(f"the whole pack booted in {seconds:.0f} s on seed {seed}; log in {log}")


main()

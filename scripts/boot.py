import argparse
import math
import random
import re
import shutil
import statistics
import subprocess
import sys
import threading
import time
from pathlib import Path

import tomllib

TIMEOUT_SECONDS = 900
READY = "Done ("
ROOT = Path(__file__).resolve().parent.parent
FOUND = re.compile(r"The nearest (\S+) is at \[(-?\d+), [^,]+, (-?\d+)\] \((\d+) blocks away\)")
MISSING = re.compile(r"Could not find a structure of type \"?(\S+?)\"? nearby")
BIOME_SHIFT_BLOCKS = 128
CHECK_SECONDS = 0.2
MAX_STEP = 2.0
SURVEY_RADIUS_BLOCKS = 10000
SURVEY_SEEDS = (1, 2, 3, 4, 5)
REJECTED = re.compile(r"Tried to load invalid item|\[FTB Quests/\]: failed to|Couldn't parse element .*\]:wayfarer:")


def locate(process, lines, structure, at=None):
    start = len(lines)
    prefix = f"execute positioned {at[0]} 0 {at[1]} run " if at else ""
    process.stdin.write(f"{prefix}locate structure {structure}\n")
    process.stdin.flush()
    deadline = time.monotonic() + 300
    while time.monotonic() < deadline:
        for line in lines[start:]:
            found = FOUND.search(line)
            if found and found.group(1) == structure:
                return int(found.group(4))
            if MISSING.search(line) and structure in line:
                return None
        time.sleep(0.5)
    return None


def check(process, lines):
    balance = tomllib.loads((ROOT / "balance.toml").read_text())["strongholds"]
    eyes = sorted(tomllib.loads((ROOT / "generator/eyes.toml").read_text()))
    failures = [f"the game rejects our data: {line.strip()[:300]}" for line in lines if REJECTED.search(line)]
    ring = locate(process, lines, "betterstrongholds:stronghold")
    low = balance["radius_blocks"] - balance["jitter_blocks"] - BIOME_SHIFT_BLOCKS * 4
    high = balance["radius_blocks"] + balance["jitter_blocks"] + BIOME_SHIFT_BLOCKS * 4
    print(f"nearest stronghold: {ring} blocks")
    if ring is None or not low <= ring <= high:
        failures.append(f"the nearest stronghold is {ring} blocks from spawn, not {low}-{high}")
    for eye in eyes:
        distance = locate(process, lines, f"wayfarer:shrine/{eye}")
        print(f"nearest {eye} shrine: {distance} blocks")
        if distance is None:
            failures.append(f"no {eye} shrine was found")
    return failures


def points(count):
    rng = random.Random(0)
    for _ in range(count):
        radius = SURVEY_RADIUS_BLOCKS * math.sqrt(rng.random())
        angle = rng.random() * math.tau
        yield round(radius * math.cos(angle)), round(radius * math.sin(angle))


def seeded(server, seed):
    properties = server / "server.properties"
    lines = [line for line in properties.read_text().splitlines() if not line.startswith("level-seed=")]
    properties.write_text("\n".join([*lines, f"level-seed={seed}"]) + "\n")
    shutil.rmtree(server / "world", ignore_errors=True)


def survey(server, count, apply):
    balance = tomllib.loads((ROOT / "balance.toml").read_text())["shrines"]
    targets = {1: balance["tier_one_trip_blocks"], 2: balance["tier_two_trip_blocks"]}
    eyes_path = ROOT / "generator/eyes.toml"
    eyes = tomllib.loads(eyes_path.read_text())
    samples = list(points(count))
    distances = {eye: [] for eye in eyes}

    def measure(process, lines):
        for eye in sorted(eyes):
            for at in samples:
                distance = locate(process, lines, f"wayfarer:shrine/{eye}", at)
                if distance is not None:
                    distances[eye].append(distance)
        return []

    for seed in SURVEY_SEEDS:
        seeded(server, seed)
        boot(server, measure)
    text = eyes_path.read_text()
    print(f"{'eye':<10} {'tier':>4} {'spacing':>7} {'median':>7} {'target':>7} {'tuned':>6}")
    for eye, spec in sorted(eyes.items()):
        found = distances[eye]
        median = statistics.median(found) if found else None
        target = targets[spec["tier"]]
        ratio = min(MAX_STEP, max(1 / MAX_STEP, math.sqrt(target / median))) if median else 1
        tuned = max(2, round(spec["spacing"] * ratio))
        print(f"{eye:<10} {spec['tier']:>4} {spec['spacing']:>7} {median or 0:>7.0f} {target:>7} {tuned:>6}")
        text = re.sub(rf"(\[{eye}\]\n(?:[^\[].*\n|\n)*?)spacing = \d+", rf"\g<1>spacing = {tuned}", text, count=1)
    if apply:
        eyes_path.write_text(text)
        print(
            f"wrote the tuned spacings to {eyes_path.relative_to(ROOT)}; run just generate, then survey again to confirm"
        )


def boot(server, action):
    (server / "eula.txt").write_text("eula=true\n")
    log = server / "boot.log"
    process = subprocess.Popen(
        ["bash", "run.sh", "--nogui"],
        cwd=server,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    crashes = server / "crash-reports"
    before = set(crashes.glob("*")) if crashes.exists() else set()
    ready = threading.Event()
    lines = []

    def read():
        with log.open("w") as out:
            for line in process.stdout:
                out.write(line)
                lines.append(line)
                if READY in line:
                    ready.set()

    reader = threading.Thread(target=read, daemon=True)
    reader.start()
    started = time.monotonic()
    crashed = []
    while not ready.wait(CHECK_SECONDS) and process.poll() is None and time.monotonic() - started < TIMEOUT_SECONDS:
        crashed = sorted(set(crashes.glob("*")) - before) if crashes.exists() else []
        if crashed:
            break
    failures = action(process, lines) if ready.is_set() else []
    if ready.is_set():
        process.stdin.write("stop\n")
        process.stdin.flush()
    else:
        process.kill()
    process.wait(timeout=300)
    reader.join(timeout=10)
    if crashed:
        print(crashed[0].read_text()[:6000], file=sys.stderr)
        sys.exit(f"the server crashed while starting; report in {crashed[0]}")
    if not ready.is_set():
        print("".join(lines[-80:]), file=sys.stderr)
        sys.exit(f"the server did not finish starting; full log in {log}")
    if failures:
        sys.exit("\n".join(failures))
    return time.monotonic() - started, log


def main():
    parser = argparse.ArgumentParser(
        description="Boot the pack's dedicated server headless and check the world, or survey shrine distances"
    )
    parser.add_argument("server", type=Path)
    parser.add_argument(
        "--survey",
        type=int,
        metavar="POINTS",
        help="locate every shrine from this many points in each survey world instead of checking",
    )
    parser.add_argument("--apply", action="store_true", help="write the survey's tuned spacings to generator/eyes.toml")
    arguments = parser.parse_args()
    server = arguments.server.resolve()
    if arguments.survey:
        survey(server, arguments.survey, arguments.apply)
        return
    seconds, log = boot(server, check)
    print(f"the whole pack booted in {seconds:.0f} s; log in {log}")


main()

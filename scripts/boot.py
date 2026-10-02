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
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import tomllib

TIMEOUT_SECONDS = 900
LOCATE_SECONDS = 300
READY = "Done ("
ROOT = Path(__file__).resolve().parent.parent
FOUND = re.compile(r"The nearest (\S+) is at \[(-?\d+), [^,]+, (-?\d+)\] \((\d+) blocks away\)")
MISSING = re.compile(r"Could not find a structure of type \"?(\S+?)\"? nearby")
BIOME_SHIFT_BLOCKS = 128
CHECK_SECONDS = 0.2
MAX_STEP = 2.0
SURVEY_RADIUS_BLOCKS = 10000
SURVEY_SEEDS = (1, 2, 3, 4, 5)
SURVEY_PORT = 25600
VOICE_PORT = 24600
SHARED = ("libraries", "mods")
WALK_BLOCKS_PER_SECOND = 4.317
REJECTED = re.compile(r"Tried to load invalid item|\[FTB Quests/\]: failed to|Couldn't parse element .*\]:wayfarer:")


class Console:
    def __init__(self, process):
        self.process = process
        self.lines = []
        self.changed = threading.Condition()

    def append(self, line):
        with self.changed:
            self.lines.append(line)
            self.changed.notify_all()

    def ask(self, command, answer):
        with self.changed:
            seen = len(self.lines)
        self.process.stdin.write(command + "\n")
        self.process.stdin.flush()
        deadline = time.monotonic() + LOCATE_SECONDS
        with self.changed:
            while True:
                for line in self.lines[seen:]:
                    reply = answer(line)
                    if reply is not None:
                        return reply
                seen = len(self.lines)
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    return None
                self.changed.wait(remaining)


def locate(console, structure, at=None):
    prefix = f"execute positioned {at[0]} 0 {at[1]} run " if at else ""

    def answer(line):
        found = FOUND.search(line)
        if found and found.group(1) == structure:
            return int(found.group(4)), int(found.group(2)), int(found.group(3))
        if MISSING.search(line) and structure in line:
            return ()
        return None

    return console.ask(f"{prefix}locate structure {structure}", answer) or None


def check(console):
    balance = tomllib.loads((ROOT / "balance.toml").read_text())["strongholds"]
    eyes = sorted(tomllib.loads((ROOT / "generator/eyes.toml").read_text())["eyes"])
    failures = [f"the game rejects our data: {line.strip()[:300]}" for line in console.lines if REJECTED.search(line)]
    found = locate(console, "betterstrongholds:stronghold")
    ring = found[0] if found else None
    low = balance["radius_blocks"] - balance["jitter_blocks"] - BIOME_SHIFT_BLOCKS * 4
    high = balance["radius_blocks"] + balance["jitter_blocks"] + BIOME_SHIFT_BLOCKS * 4
    print(f"nearest stronghold: {ring} blocks")
    if ring is None or not low <= ring <= high:
        failures.append(f"the nearest stronghold is {ring} blocks from spawn, not {low}-{high}")
    for eye in eyes:
        found = locate(console, f"wayfarer:shrine/{eye}")
        print(f"nearest {eye} shrine: {found[0] if found else None} blocks")
        if found is None:
            failures.append(f"no {eye} shrine was found")
    for structure in tomllib.loads((ROOT / "generator/removals.toml").read_text())["structures"]:
        found = locate(console, structure)
        if found is not None:
            failures.append(
                f"{structure} is cut in generator/removals.toml but still generates {found[0]} blocks from spawn"
            )
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
    properties = target / "server.properties"
    overrides = {"level-seed": seed, "max-tick-time": -1, "server-port": SURVEY_PORT + seed}
    lines = [line for line in properties.read_text().splitlines() if line.split("=", 1)[0] not in overrides]
    properties.write_text("\n".join([*lines, *(f"{key}={value}" for key, value in overrides.items())]) + "\n")
    voice = target / "config/voicechat/voicechat-server.properties"
    voice.parent.mkdir(parents=True, exist_ok=True)
    voice.write_text(f"port={VOICE_PORT + seed}\n")
    return target


def journey(console, eyes, start, legs):
    pos = start
    for tier in legs:
        remaining = [eye for eye in sorted(eyes) if eyes[eye]["tier"] == tier]
        while remaining:
            found = [(hit, eye) for eye in remaining if (hit := locate(console, f"wayfarer:shrine/{eye}", pos))]
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

        def measure(console):
            for start in starts:
                journey(console, shrines["eyes"], start, legs)
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
    console = Console(process)
    crashes = server / "crash-reports"
    before = set(crashes.glob("*")) if crashes.exists() else set()
    ready = threading.Event()

    def read():
        with log.open("w") as out:
            for line in process.stdout:
                out.write(line)
                console.append(line)
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
    failures = action(console) if ready.is_set() else []
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
        print("".join(console.lines[-80:]), file=sys.stderr)
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
        metavar="JOURNEYS",
        help="in each survey world at once, walk this many journeys to the nearest shrine not yet taken, instead of checking",
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

import re
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


def locate(process, lines, structure):
    start = len(lines)
    process.stdin.write(f"locate structure {structure}\n")
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
    failures = []
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


def main():
    server = Path(sys.argv[1]).resolve()
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
    while not ready.is_set() and process.poll() is None and time.monotonic() - started < TIMEOUT_SECONDS:
        crashed = sorted(set(crashes.glob("*")) - before) if crashes.exists() else []
        if crashed:
            break
        time.sleep(1)
    failures = check(process, lines) if ready.is_set() else []
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
    print(f"the whole pack booted in {time.monotonic() - started:.0f} s; log in {log}")


main()

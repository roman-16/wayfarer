import argparse
import datetime
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path

import changelog

ROOT = Path(__file__).resolve().parent.parent
RECORD = ROOT / "build/passed.json"
CLIENT_ONLY = re.compile(r"^(assets/|io/github/roman16/wayfarer/client/)")
INPUTS = (
    "balance.toml",
    "devbox.json",
    "devbox.lock",
    "generator/eyes.toml",
    "generator/nether.toml",
    "generator/owners.toml",
    "justfile",
    "mod/src/dev",
    "mod/src/generated/dev",
    "mod/src/generated/resources/assets/wayfarer/textures/entity/people",
    "mod/src/main/templates",
    "pack/config",
    "pack/index.toml",
    "pack/journeymap",
    "pack/mods",
    "scripts/audit.py",
    "scripts/boot.py",
    "scripts/drive.py",
    "scripts/instance.py",
    "scripts/passed.py",
    "story/states.toml",
)


def jar():
    return ROOT / f"mod/build/libs/wayfarer-{changelog.version()}.jar"


def digest():
    found = hashlib.sha256()
    with zipfile.ZipFile(jar()) as archive:
        for entry in sorted(archive.infolist(), key=lambda entry: entry.filename):
            if not CLIENT_ONLY.match(entry.filename) and not entry.is_dir():
                found.update(f"{entry.filename}\0{entry.CRC}\0".encode())
    for top in INPUTS:
        path = ROOT / top
        for file in sorted(path.rglob("*")) if path.is_dir() else [path] * path.exists():
            if file.is_file():
                found.update(str(file.relative_to(ROOT)).encode() + b"\0" + file.read_bytes())
    return found.hexdigest()


def when(stamp):
    passed = datetime.datetime.fromisoformat(stamp)
    return passed.strftime("%H:%M" if passed.date() == datetime.date.today() else "%d.%m. %H:%M")


def main():
    parser = argparse.ArgumentParser(
        description="Remember what the last passing just test ran on, and say whether anything it loads has changed since"
    )
    parser.add_argument("action", choices=["check", "record"])
    arguments = parser.parse_args()
    current = digest()
    if arguments.action == "record":
        RECORD.write_text(json.dumps({"at": datetime.datetime.now().isoformat(timespec="seconds"), "digest": current}))
        return
    record = json.loads(RECORD.read_text()) if RECORD.exists() else {}
    if record.get("digest") != current:
        sys.exit(1)
    print(
        f"nothing the server or the game tests load changed since the pass at {when(record['at'])} "
        "(game tests and boot test)"
    )


main()

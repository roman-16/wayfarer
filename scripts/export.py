import argparse
import json
import zipfile
from pathlib import Path

import tomllib

ROOT = Path(__file__).resolve().parent.parent
MANIFEST = "manifest.json"
MEGABYTES_PER_GIGABYTE = 1024


def finish(archive, icon):
    gigabytes = tomllib.loads((ROOT / "balance.toml").read_text())["heap"]["gigabytes"]
    with zipfile.ZipFile(archive) as source:
        entries = [(info, source.read(info)) for info in source.infolist()]
    partial = archive.with_name(archive.name + ".part")
    with zipfile.ZipFile(partial, "w", zipfile.ZIP_DEFLATED) as target:
        for info, data in entries:
            if info.filename == MANIFEST:
                manifest = json.loads(data)
                manifest["minecraft"]["recommendedRam"] = gigabytes * MEGABYTES_PER_GIGABYTE
                data = json.dumps(manifest, indent=2).encode()
            target.writestr(info, data)
        target.write(icon, "icon.png")
    partial.replace(archive)


def main():
    parser = argparse.ArgumentParser(
        description="Finish the CurseForge-format zip: its icon, and the memory it asks the launcher to give the game"
    )
    parser.add_argument("archive", type=Path)
    parser.add_argument("icon", type=Path)
    arguments = parser.parse_args()
    finish(arguments.archive, arguments.icon)


main()

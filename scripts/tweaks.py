import json
import sys
import urllib.parse
import urllib.request
from pathlib import Path

import tomllib

ROOT = Path(__file__).resolve().parent.parent
SITE = "https://vanillatweaks.net"
FOLDER = ROOT / "generator/tweaks"
AGENT = {"User-Agent": "roman-16/wayfarer"}
KINDS = {"crafting": "zipcraftingtweaks", "resources": "zipresourcepacks"}


def fetch(kind, packs, version):
    form = urllib.parse.urlencode({"packs": json.dumps(packs), "version": version}).encode()
    request = urllib.request.Request(f"{SITE}/assets/server/{KINDS[kind]}.php", data=form, headers=AGENT)
    answer = json.loads(urllib.request.urlopen(request, timeout=120).read())
    if answer.get("status") != "success":
        sys.exit(f"tweaks failed: Vanilla Tweaks refused the {kind}: {answer}")
    archive = urllib.request.urlopen(urllib.request.Request(SITE + answer["link"], headers=AGENT), timeout=120).read()
    FOLDER.mkdir(exist_ok=True)
    (FOLDER / f"{kind}.zip").write_bytes(archive)
    print(f"tweaks: {kind}.zip, {len(archive)} bytes, {sum(len(names) for names in packs.values())} tweaks")


def main():
    spec = tomllib.loads((ROOT / "generator/tweaks.toml").read_text())
    for kind in KINDS:
        fetch(kind, spec[kind], spec["version"])


if __name__ == "__main__":
    main()

import argparse
import hashlib
import json
import shutil
import subprocess
import urllib.request
import zipfile
from pathlib import Path

import tomllib

import changelog

ROOT = Path(__file__).resolve().parent.parent
PACK = ROOT / "pack"
HOSTED = ROOT / "build/hosted"
DIST = ROOT / "dist"
CACHE = Path.home() / ".cache/wayfarer"
SITE = "https://roman-16.github.io/wayfarer"
RELEASES = "https://github.com/roman-16/wayfarer/releases/download"
BOOTSTRAP = (
    "https://github.com/packwiz/packwiz-installer-bootstrap/releases/download/v0.0.3/packwiz-installer-bootstrap.jar"
)
MEGABYTES_PER_GIGABYTE = 1024
LEAST_MEGABYTES = 512
ICON = "wayfarer"


def hosted(jar, version):
    shutil.rmtree(HOSTED, ignore_errors=True)
    shutil.copytree(PACK, HOSTED)
    name = f"wayfarer-{version}.jar"
    (HOSTED / "mods/wayfarer.pw.toml").write_text(
        f'name = "Wayfarer"\nfilename = "{name}"\nside = "both"\n\n[download]\n'
        f'url = "{RELEASES}/v{version}/{name}"\nhash-format = "sha256"\n'
        f'hash = "{hashlib.sha256(jar.read_bytes()).hexdigest()}"\n'
    )
    subprocess.run(["packwiz", "refresh"], cwd=HOSTED, check=True, stdout=subprocess.DEVNULL)
    shutil.copy2(jar, DIST / name)
    with zipfile.ZipFile(DIST / f"Wayfarer-{version}-pack.zip", "w", zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(HOSTED.rglob("*")):
            if path.is_file():
                archive.write(path, path.relative_to(HOSTED).as_posix())


def bootstrap():
    cached = CACHE / "packwiz-installer-bootstrap" / BOOTSTRAP.split("/")[-2] / "packwiz-installer-bootstrap.jar"
    if not cached.exists():
        cached.parent.mkdir(parents=True, exist_ok=True)
        with urllib.request.urlopen(BOOTSTRAP) as answer:
            cached.write_bytes(answer.read())
    return cached


def quoted(value):
    return '"' + str(value).replace("\\", "\\\\").replace('"', '\\"') + '"'


def instance(icon, version, versions, gigabytes):
    major = version.split(".")[0]
    settings = {
        "ConfigVersion": "1.2",
        "InstanceType": "OneSix",
        "MaxMemAlloc": gigabytes * MEGABYTES_PER_GIGABYTE,
        "MinMemAlloc": LEAST_MEGABYTES,
        "OverrideCommands": "true",
        "OverrideMemory": "true",
        "PreLaunchCommand": f'"$INST_JAVA" -jar packwiz-installer-bootstrap.jar {SITE}/{major}/pack.toml',
        "iconKey": ICON,
        "name": f"Wayfarer {major}",
    }
    components = {
        "components": [
            {"important": True, "uid": "net.minecraft", "version": versions["minecraft"]},
            {"uid": "net.neoforged", "version": versions["neoforge"]},
        ],
        "formatVersion": 1,
    }
    with zipfile.ZipFile(DIST / f"Wayfarer-{major}.zip", "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr(
            "instance.cfg", "[General]\n" + "".join(f"{key}={quoted(value)}\n" for key, value in settings.items())
        )
        archive.writestr("mmc-pack.json", json.dumps(components, indent=2) + "\n")
        archive.write(icon, f"{ICON}.png")
        archive.write(bootstrap(), "minecraft/packwiz-installer-bootstrap.jar")


def main():
    parser = argparse.ArgumentParser(
        description="Export into dist/ the Prism instance that updates itself from the hosted address, the pack it hosts and the Wayfarer jar"
    )
    parser.add_argument("jar", type=Path)
    parser.add_argument("icon", type=Path)
    arguments = parser.parse_args()
    pack = tomllib.loads((PACK / "pack.toml").read_text())
    version = changelog.version()
    DIST.mkdir(parents=True, exist_ok=True)
    hosted(arguments.jar, version)
    gigabytes = tomllib.loads((ROOT / "balance.toml").read_text())["heap"]["gigabytes"]
    instance(arguments.icon, version, pack["versions"], gigabytes)


main()

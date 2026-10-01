import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import urllib.request
from pathlib import Path

import tomllib

ROOT = Path(__file__).resolve().parent.parent
PACK = ROOT / "pack"
CACHE = Path(os.environ.get("XDG_CACHE_HOME", Path.home() / ".cache")) / "wayfarer"
CLIENT_CONFIGS = {"dynamic_fps.json": {"enabled": False}}
CLIENT_OPTIONS = {"pauseOnLostFocus": "false"}
SIDES = {"client": {"both", "client"}, "server": {"both", "server"}}
SERVER_PROPERTIES = {
    "allow-flight": "true",
    "difficulty": "hard",
    "enable-command-block": "false",
    "level-type": "minecraft\\:normal",
    "motd": "Wayfarer",
    "spawn-protection": "0",
    "view-distance": "12",
}


def download(url, digest_format, digest, name):
    target = CACHE / "files" / digest
    if not target.exists():
        target.parent.mkdir(parents=True, exist_ok=True)
        request = urllib.request.Request(url, headers={"User-Agent": "roman-16/wayfarer"})
        with urllib.request.urlopen(request, timeout=120) as response:
            data = response.read()
        actual = hashlib.new(digest_format, data).hexdigest()
        if actual != digest:
            sys.exit(f"{name}: expected {digest_format} {digest}, got {actual}")
        target.write_bytes(data)
    return target


def source(meta):
    download_spec = meta["download"]
    if download_spec.get("mode") == "metadata:curseforge":
        file_id = meta["update"]["curseforge"]["file-id"]
        name = urllib.request.quote(meta["filename"])
        return f"https://mediafilez.forgecdn.net/files/{file_id // 1000}/{file_id % 1000}/{name}"
    return download_spec["url"]


def files(target, side, folders):
    index = tomllib.loads((PACK / "index.toml").read_text())
    for entry in index["files"]:
        folder = entry["file"].split("/")[0]
        if not entry.get("metafile") or folder not in folders:
            continue
        meta = tomllib.loads((PACK / entry["file"]).read_text())
        if meta["side"] not in SIDES[side]:
            continue
        spec = meta["download"]
        cached = download(source(meta), spec["hash-format"], spec["hash"], meta["name"])
        (target / folder).mkdir(parents=True, exist_ok=True)
        shutil.copyfile(cached, target / folder / meta["filename"])


def neoforge(target):
    version = tomllib.loads((PACK / "pack.toml").read_text())["versions"]["neoforge"]
    base = f"https://maven.neoforged.net/releases/net/neoforged/neoforge/{version}/neoforge-{version}-installer.jar"
    with urllib.request.urlopen(base + ".sha1", timeout=60) as response:
        sha1 = response.read().decode().split()[0]
    installer = download(base, "sha1", sha1, f"NeoForge {version} installer")
    subprocess.run(
        ["java", "-jar", str(installer), "--install-server", "."],
        cwd=target,
        check=True,
        stdout=subprocess.DEVNULL,
    )


def options(target):
    path = target / "options.txt"
    lines = path.read_text().splitlines() if path.exists() else []
    kept = [line for line in lines if line.split(":", 1)[0] not in CLIENT_OPTIONS]
    forced = [f"{key}:{value}" for key, value in sorted(CLIENT_OPTIONS.items())]
    path.write_text("\n".join(kept + forced) + "\n")


def client(target):
    shutil.rmtree(target / "mods", ignore_errors=True)
    (target / "mods").mkdir(parents=True)
    files(target, "client", {"mods", "resourcepacks", "shaderpacks"})
    shutil.copytree(PACK / "config", target / "config", dirs_exist_ok=True)
    for name, config in CLIENT_CONFIGS.items():
        (target / "config" / name).write_text(json.dumps(config, indent=2) + "\n")
    options(target)


def server(target, jar):
    shutil.rmtree(target, ignore_errors=True)
    (target / "mods").mkdir(parents=True)
    neoforge(target)
    files(target, "server", {"mods"})
    shutil.copyfile(jar, target / "mods" / jar.name)
    shutil.copytree(PACK / "config", target / "config")
    lines = [f"{key}={value}" for key, value in sorted(SERVER_PROPERTIES.items())]
    (target / "server.properties").write_text("\n".join(lines) + "\n")
    (target / "user_jvm_args.txt").write_text("-Xms4G\n-Xmx8G\n")


def main():
    parser = argparse.ArgumentParser(description="Assemble the pack as a dev client instance or a dedicated server")
    sides = parser.add_subparsers(dest="side", required=True)
    sides.add_parser("client", help="mods, packs and configs for the dev client; keeps saves and options").add_argument(
        "target", type=Path
    )
    server_parser = sides.add_parser("server", help="a complete dedicated server, rebuilt from nothing")
    server_parser.add_argument("target", type=Path)
    server_parser.add_argument("jar", type=Path)
    arguments = parser.parse_args()
    if arguments.side == "client":
        client(arguments.target.resolve())
    else:
        server(arguments.target.resolve(), arguments.jar.resolve())


main()

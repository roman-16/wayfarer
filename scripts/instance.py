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
CLIENT_CONFIGS = {
    "DistantHorizons.toml": {
        "client": {"advanced": {"debugging": {"rendererMode": "DISABLED"}}},
        "common": {"worldGenerator": {"generatorPlan": "DISABLED"}},
    },
    "dynamic_fps.json": {"enabled": False},
    "iris.properties": {"enableShaders": "false"},
}
CLIENT_OPTIONS = {"pauseOnLostFocus": "false", "renderDistance": "8", "simulationDistance": "8"}
SIDES = {"client": {"both", "client"}, "server": {"both", "server"}}
DEV_SERVER_PORTS = {"server": 25566, "voice": 24455}
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


def merged(base, overrides):
    result = dict(base)
    for key, value in overrides.items():
        result[key] = merged(result.get(key, {}), value) if isinstance(value, dict) else value
    return result


def toml_value(value):
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, int | float):
        return repr(value)
    return json.dumps(value)


def toml_text(table, prefix=()):
    scalars = {key: value for key, value in table.items() if not isinstance(value, dict)}
    lines = []
    if scalars:
        lines += [f"[{'.'.join(prefix)}]"] if prefix else []
        lines += [f"{key} = {toml_value(value)}" for key, value in sorted(scalars.items())]
        lines.append("")
    for key, value in sorted(table.items()):
        if isinstance(value, dict):
            lines += toml_text(value, (*prefix, key))
    return lines


def configure(path, overrides):
    text = path.read_text() if path.exists() else ""
    if path.suffix == ".json":
        path.write_text(json.dumps(merged(json.loads(text or "{}"), overrides), indent=2) + "\n")
    elif path.suffix == ".toml":
        path.write_text("\n".join(toml_text(merged(tomllib.loads(text), overrides))))
    else:
        pairs = dict(line.split("=", 1) for line in text.splitlines() if "=" in line and not line.startswith("#"))
        path.write_text("".join(f"{key}={value}\n" for key, value in sorted((pairs | overrides).items())))


def client(target, lean):
    shutil.rmtree(target / "mods", ignore_errors=True)
    (target / "mods").mkdir(parents=True)
    files(target, "client", {"resourcepacks", "shaderpacks"} if lean else {"mods", "resourcepacks", "shaderpacks"})
    shutil.copytree(PACK / "config", target / "config", dirs_exist_ok=True)
    for name, overrides in CLIENT_CONFIGS.items():
        configure(target / "config" / name, overrides)
    options(target)


def properties(target, overrides):
    lines = [f"{key}={value}" for key, value in sorted((SERVER_PROPERTIES | overrides).items())]
    (target / "server.properties").write_text("\n".join(lines) + "\n")


def server(target, jar):
    shutil.rmtree(target, ignore_errors=True)
    (target / "mods").mkdir(parents=True)
    neoforge(target)
    files(target, "server", {"mods"})
    shutil.copyfile(jar, target / "mods" / jar.name)
    shutil.copytree(PACK / "config", target / "config")
    properties(target, {})
    (target / "user_jvm_args.txt").write_text("-Xms4G\n-Xmx8G\n")


def dev_server(target, lean):
    shutil.rmtree(target / "mods", ignore_errors=True)
    (target / "mods").mkdir(parents=True)
    if not lean:
        files(target, "server", {"mods"})
    shutil.copytree(PACK / "config", target / "config", dirs_exist_ok=True)
    voice = target / "config/voicechat/voicechat-server.properties"
    voice.parent.mkdir(parents=True, exist_ok=True)
    voice.write_text(f"port={DEV_SERVER_PORTS['voice']}\n")
    world = {"level-name": "world-lean"} if lean else {}
    properties(target, {"online-mode": "false", "server-port": DEV_SERVER_PORTS["server"]} | world)
    (target / "eula.txt").write_text("eula=true\n")


def main():
    parser = argparse.ArgumentParser(description="Assemble the pack as a dev client instance or a dedicated server")
    sides = parser.add_subparsers(dest="side", required=True)
    for side, text in (
        ("client", "mods, packs and configs for the dev client; keeps saves and options"),
        (
            "devserver",
            "mods, configs and properties for the dev server, which brings the Wayfarer mod itself; keeps the world",
        ),
    ):
        side_parser = sides.add_parser(side, help=text)
        side_parser.add_argument("target", type=Path)
        side_parser.add_argument(
            "--mode",
            choices=["full", "lean"],
            default="full",
            help="lean leaves the mods folder empty: only the Wayfarer mod and what it needs, from the dev classpath",
        )
    server_parser = sides.add_parser("server", help="a complete dedicated server, rebuilt from nothing")
    server_parser.add_argument("target", type=Path)
    server_parser.add_argument("jar", type=Path)
    arguments = parser.parse_args()
    if arguments.side == "client":
        client(arguments.target.resolve(), arguments.mode == "lean")
    elif arguments.side == "devserver":
        dev_server(arguments.target.resolve(), arguments.mode == "lean")
    else:
        server(arguments.target.resolve(), arguments.jar.resolve())


main()

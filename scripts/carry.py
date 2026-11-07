import argparse
import hashlib
import json
import os
import random
import re
import shutil
import subprocess
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
from pathlib import Path

import tomllib

from exaroton import (
    CARRY,
    CARRY_SUFFIX,
    OFFLINE,
    RECORDINGS,
    ROOT,
    TRANSFER_SECONDS,
    Api,
    ApiError,
    carries,
    describe,
    find_server,
    megabytes,
    opened,
    options,
    unopened,
)

DEV_SERVER = ROOT / "build/devserver"
SERVER = ROOT / "build/server"
SERVER_PACK = SERVER / "mods"
MIRRORED = ("config", "journeymap", "mods")
NEOFORGE = "libraries/net/neoforged/neoforge"
READ_BACK = 1 << 20
SERVER_BACKUPS = ".bak"
DEV_DATA = ROOT / "mod/src/generated/dev/data"
DRIVER = ROOT / "scripts/server.py"
OLD = "carry-old"
NEW = "carry-new"
WALKER = "Wayfarer"
STAMP = "%Y-%m-%d_%H-%M"
WORLD = "world_%Y_%m_%d_%H_%M"
QUESTS = ROOT / "story/quests.toml"
MAIN = "main"
SEEDS = 4
WORKERS = 4
SHOWN = 8
STEP_SECONDS = 60
BUILD_SECONDS = 1800
PARTS = " | "
PACKED = "road: packed"
CARRIED = "road: carried to "
REFUSED = "road failed: "
UUID = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
FETCHED = (
    "advancements",
    "data",
    "dimensions/nomadictents",
    "entities",
    "level.dat",
    "playerdata",
    "poi",
    "region",
    "serverconfig",
    "stats",
    "streamsreflowing",
)
LEFT_OUT = re.compile(r"^data/lootr(/|$)|(^|/)DistantHorizons[^/]*$")
CARRIED_DATA = (
    "horseman_horse_calling.dat",
    "nomadictents.dat",
    "sophisticatedbackpacks.dat",
    "wayfarer_deeds.dat",
    "wayfarer_journey.dat",
)
CARRIED_FOLDERS = ("advancements", "dimensions/nomadictents", "playerdata", "stats")


class Failure(Exception):
    pass


def say(text):
    print(text, flush=True)


def fail(why):
    sys.exit(f"carry failed: {why}")


def took(started):
    seconds = time.monotonic() - started
    return f"{seconds / 60:.0f} min" if seconds >= 90 else f"{seconds:.0f} s"


def digest(data):
    return hashlib.sha256(data).hexdigest()


def offline(api, server):
    state = api.get(f"servers/{server['id']}")
    if state["status"] != OFFLINE:
        players = (state.get("players") or {}).get("list") or []
        online = f" with {', '.join(players)} online" if players else ""
        fail(
            f"{server['name']} is {describe(state['status'])}{online}; a world is carried only while the server is offline"
        )


def main_step(step):
    quest, _, name = step.partition("/")
    quests = {entry["id"]: entry for entry in tomllib.loads(QUESTS.read_text())["quests"]}
    if quest not in quests or quests[quest]["kind"] != MAIN:
        fail(f"{quest} is no main quest of the story")
    if name not in (entry["id"] for entry in quests[quest].get("steps", [])):
        fail(f"{quest} has no step {name}")


def fetched(relative, folder):
    if LEFT_OUT.search(relative):
        return False
    return any(
        relative == root or relative.startswith(root + "/") or folder and root.startswith(relative + "/")
        for root in FETCHED
    )


def listing(api, server, level, relative=""):
    found = []
    for child in api.get(f"servers/{server['id']}/files/info/{level}/{relative}").get("children") or []:
        inner = relative + child["name"]
        if not fetched(inner, child["isDirectory"]):
            continue
        if child["isDirectory"]:
            found += listing(api, server, level, inner + "/")
        else:
            found.append({"path": inner, "size": child["size"]})
    return found


def download(api, server, level, target):
    started = time.monotonic()
    files = listing(api, server, level)

    def fetch(entry):
        copy = target / entry["path"]
        copy.parent.mkdir(parents=True, exist_ok=True)
        copy.write_bytes(api.data(f"servers/{server['id']}/files/data/{level}/{entry['path']}", TRANSFER_SECONDS))

    with ThreadPoolExecutor(WORKERS) as pool:
        list(pool.map(fetch, files))
    say(
        f"{server['name']} is offline; downloaded what the carry uses of {level}: {len(files)} files, "
        f"{megabytes(sum(entry['size'] for entry in files))} in {took(started)}"
    )


def names(api, server):
    try:
        cache = json.loads(api.data(f"servers/{server['id']}/files/data/usercache.json"))
    except (ApiError, ValueError):
        return {}
    return {entry["uuid"]: entry["name"] for entry in cache}


def players(world, known):
    found = sorted(path.stem for path in (world / "playerdata").glob("*.dat") if UUID.match(path.stem))
    if not found:
        fail("nobody has played on this world, so there is no road to carry")
    return [(uuid, known.get(uuid) or "p" + uuid.replace("-", "")[:15]) for uuid in found]


def driver(*arguments):
    result = subprocess.run([sys.executable, str(DRIVER), *arguments], cwd=ROOT, capture_output=True, text=True)
    if result.returncode:
        raise Failure((result.stderr or result.stdout).strip())
    return result.stdout


def batch(steps, timeout):
    result = subprocess.run(
        [sys.executable, str(DRIVER), "run", "--timeout", str(timeout), *steps],
        cwd=ROOT,
        capture_output=True,
        text=True,
    )
    lines = [json.loads(line) for line in result.stdout.splitlines() if line.startswith("{")]
    errors = [line["error"] for line in lines if "error" in line]
    if result.returncode or errors:
        raise Failure(errors[0] if errors else (result.stderr or result.stdout).strip())
    return [reply for line in lines for reply in line.get("replies") or []]


def reply(replies, start):
    return next(said for said in replies if said.startswith(start))


def parts(said, start):
    return said.removeprefix(start).split(PARTS)


def pack(folder, team):
    shutil.rmtree(DEV_SERVER / OLD, ignore_errors=True)
    shutil.copytree(folder / "old", DEV_SERVER / OLD)
    driver("start", "--world", OLD)
    try:
        replies = batch(
            [
                *(f"wayfarer pretend join {name} {uuid}" for uuid, name in team),
                "wayfarer road pack",
                f"await {PACKED}",
                *(f"wayfarer pretend leave {name}" for _, name in team),
            ],
            STEP_SECONDS,
        )
    finally:
        driver("stop")
    say("packed in the old world:")
    for part in parts(reply(replies, PACKED), PACKED + PARTS):
        say(f"  {part}")


def grow(step):
    tried = []
    for _ in range(SEEDS):
        seed = random.getrandbits(63)
        started = time.monotonic()
        try:
            driver("start", "--world", NEW, "--seed", str(seed))
            replies = batch(
                [
                    f"wayfarer pretend join {WALKER}",
                    f'wayfarer road follow "{DEV_SERVER / OLD}"' + (f" {step}" if step else ""),
                    f"await {CARRIED}",
                    f"wayfarer pretend leave {WALKER}",
                ],
                BUILD_SECONDS,
            )
        except Failure as failure:
            if REFUSED in str(failure):
                fail(str(failure)[str(failure).index(REFUSED) + len(REFUSED) :])
            tried.append((seed, str(failure)))
            continue
        finally:
            driver("stop")
        step, *rest = parts(reply(replies, CARRIED), CARRIED)
        say(f"grew seed {seed} and played it to {step} in {took(started)}: {', '.join(rest)}")
        return seed, step
    fail(f"no seed could hold the road; tried {', '.join(str(seed) for seed, _ in tried)}, the last: {tried[-1][1]}")


def lay(old, new):
    for name in CARRIED_FOLDERS:
        shutil.rmtree(new / name, ignore_errors=True)
        if (old / name).exists():
            shutil.copytree(old / name, new / name)
    for name in CARRIED_DATA:
        (new / "data" / name).unlink(missing_ok=True)
        if (old / "data" / name).exists():
            shutil.copy2(old / "data" / name, new / "data" / name)
    shutil.rmtree(new / "wayfarer" / "record", ignore_errors=True)
    for dimension in DEV_DATA.glob("*/dimension/*.json"):
        shutil.rmtree(new / "dimensions" / dimension.parent.parent.name / dimension.stem, ignore_errors=True)


def size(folder):
    return sum(path.stat().st_size for path in folder.rglob("*") if path.is_file())


def carry(api, server, back):
    if back:
        main_step(back)
    offline(api, server)
    level = opened(options(api, server))
    moment = datetime.now()
    folder = RECORDINGS / (moment.strftime(STAMP) + CARRY_SUFFIX)
    shutil.rmtree(folder, ignore_errors=True)
    download(api, server, level, folder / "old")
    team = players(folder / "old", names(api, server))
    try:
        pack(folder, team)
        seed, step = grow(back)
    except Failure as failure:
        fail(str(failure))
    lay(DEV_SERVER / OLD, DEV_SERVER / NEW)
    shutil.copytree(DEV_SERVER / NEW, folder / "new", ignore=shutil.ignore_patterns("session.lock"))
    (folder / CARRY).write_text(
        json.dumps(
            {
                "from": level,
                "level_dat": digest((folder / "old" / "level.dat").read_bytes()),
                "name": server["name"],
                "seed": seed,
                "server": server["id"],
                "step": step,
                "taken": datetime.now().astimezone().isoformat(timespec="seconds"),
                "to": moment.strftime(WORLD),
                "uploaded": None,
            },
            indent=2,
        )
    )
    say(f"carried into {(folder / 'new').relative_to(ROOT)} ({megabytes(size(folder / 'new'))})")
    say(f"next: just carry {server['name']} upload")


def compare_mods(api, server):
    if not SERVER_PACK.exists():
        fail(f"build/server holds no server pack; run just pack, then just carry {server['name']} pack")
    local = {path.name: path.stat().st_size for path in SERVER_PACK.glob("*.jar")}
    remote = {
        entry["name"]: entry["size"]
        for entry in api.get(f"servers/{server['id']}/files/info/mods/").get("children") or []
        if not entry["isDirectory"]
    }
    differ = []
    for name in sorted(local.keys() | remote.keys()):
        if name not in remote:
            differ.append(f"{name} (missing on the server)")
        elif name not in local:
            differ.append(f"{name} (not in the pack)")
        elif local[name] != remote[name]:
            differ.append(f"{name} (another build)")
    if differ:
        more = f" and {len(differ) - SHOWN} more" if len(differ) > SHOWN else ""
        fail(
            f"{server['name']}'s mods differ from build/server: {', '.join(differ[:SHOWN])}{more}; "
            f"run just carry {server['name']} pack first"
        )


def tree(api, server, folder):
    try:
        children = api.get(f"servers/{server['id']}/files/info/{folder}/").get("children") or []
    except ApiError:
        return {}
    found = {}
    for child in children:
        path = child["path"].strip("/")
        if child["isDirectory"]:
            found.update(tree(api, server, path))
        else:
            found[path] = child["size"]
    return found


def stale(api, server, path, size):
    local = SERVER / path
    if size != local.stat().st_size:
        return True
    return size <= READ_BACK and api.data(f"servers/{server['id']}/files/data/{path}") != local.read_bytes()


def put_pack(api, server):
    if not SERVER_PACK.exists():
        fail("build/server holds no server pack; run just pack first")
    offline(api, server)
    started = time.monotonic()
    wanted = next((SERVER / NEOFORGE).iterdir()).name
    there = sorted(
        child["name"] for child in api.get(f"servers/{server['id']}/files/info/{NEOFORGE}/").get("children") or []
    )
    if wanted not in there:
        fail(
            f"{server['name']} runs NeoForge {', '.join(there) or 'unknown'}, the pack {wanted}; choose {wanted} in its software first"
        )
    remote = {}
    for folder in MIRRORED:
        remote.update(tree(api, server, folder))
    local = [
        path.relative_to(SERVER).as_posix()
        for folder in MIRRORED
        for path in (SERVER / folder).rglob("*")
        if path.is_file()
    ]
    sent = [path for path in local if path not in remote or stale(api, server, path, remote[path])]
    kept = set(local)
    dropped = sorted(path for path in remote if path not in kept and not path.endswith(SERVER_BACKUPS))
    folders = {parent.as_posix() for path in sent for parent in Path(path).parents if parent != Path(".")}
    for folder in sorted(folders, key=lambda name: len(Path(name).parts)):
        if not any(path.startswith(folder + "/") for path in remote):
            api.put(f"servers/{server['id']}/files/data/{folder}", b"", "inode/directory")

    def send(path):
        api.put(f"servers/{server['id']}/files/data/{path}", (SERVER / path).read_bytes())

    def tried(path):
        try:
            send(path)
        except (ApiError, OSError):
            return path
        return None

    with ThreadPoolExecutor(WORKERS) as pool:
        refused = [path for path in pool.map(tried, sent) if path is not None]
    for path in refused:
        send(path)
    for path in dropped:
        api.delete(f"servers/{server['id']}/files/data/{path}")
    compare_mods(api, server)
    shown = ", ".join(sent[:SHOWN]) + (f" and {len(sent) - SHOWN} more" if len(sent) > SHOWN else "")
    say(
        f"put the server pack on {server['name']}: {len(sent)} files up ({megabytes(sum((SERVER / path).stat().st_size for path in sent))})"
        + (f": {shown}" if sent else "")
        + f", {len(dropped)} removed"
        + (f": {', '.join(dropped[:SHOWN])}" if dropped else "")
        + f", in {took(started)}"
    )


def remove(api, server, path):
    for child in api.get(f"servers/{server['id']}/files/info/{path}/").get("children") or []:
        if child["isDirectory"]:
            remove(api, server, child["path"].strip("/"))
        else:
            api.delete(f"servers/{server['id']}/files/data/{child['path'].strip('/')}")
    api.delete(f"servers/{server['id']}/files/data/{path}")


def put_world(api, server, source, name):
    started = time.monotonic()
    folders = sorted(
        {path.relative_to(source) for path in source.rglob("*") if path.is_dir()}, key=lambda path: len(path.parts)
    )
    api.put(f"servers/{server['id']}/files/data/{name}", b"", "inode/directory")
    for relative in folders:
        api.put(f"servers/{server['id']}/files/data/{name}/{relative.as_posix()}", b"", "inode/directory")
    files = [path for path in source.rglob("*") if path.is_file()]

    def send(path):
        api.put(f"servers/{server['id']}/files/data/{name}/{path.relative_to(source).as_posix()}", path.read_bytes())

    def tried(path):
        try:
            send(path)
        except (ApiError, OSError):
            return path
        return None

    with ThreadPoolExecutor(WORKERS) as pool:
        refused = [path for path in pool.map(tried, files) if path is not None]
    for path in refused:
        send(path)
    say(f"uploaded {name}: {megabytes(sum(path.stat().st_size for path in files))} in {took(started)}")


def switch(api, server, level):
    api.post(f"servers/{server['id']}/files/config/server.properties/", {"level-name": level})
    kept = opened(options(api, server))
    if kept != level:
        fail(
            f"exaroton kept level-name {kept} after it was set to {level}; set World name to {level} in "
            f"{server['name']}'s options, then run just carry {server['name']} upload again"
        )


def sweep(api, server, keep):
    there = {child["name"] for child in api.get(f"servers/{server['id']}/files/info/").get("children") or []}
    for level in sorted({carried["to"] for _, carried in carries(server) if carried["uploaded"]} - set(keep)):
        if level not in there:
            continue
        why = "carried but never opened" if unopened(api, server, level) else "two worlds back"
        try:
            remove(api, server, level)
            say(f"removed {level} from {server['name']}, {why}")
        except ApiError as error:
            say(f"{level} stays on {server['name']}: {error}")


def upload(api, server):
    waiting = [(folder, carried) for folder, carried in carries(server) if carried["uploaded"] is None]
    if not waiting:
        fail(f"nothing is carried for {server['name']} yet; run just carry {server['name']} first")
    folder, carried = waiting[-1]
    offline(api, server)
    current = opened(options(api, server))
    if current == carried["to"]:
        fail(f"{server['name']} already plays {carried['to']}")
    taken = carried["taken"][11:16]
    if (
        current != carried["from"]
        or digest(api.data(f"servers/{server['id']}/files/data/{current}/level.dat")) != carried["level_dat"]
    ):
        fail(
            f"{server['name']}'s world changed since it was downloaded at {taken}; run just carry {server['name']} again"
        )
    compare_mods(api, server)
    say(f"{server['name']} is offline, its world unchanged since {taken}, its mods the same as build/server")
    put_world(api, server, folder / "new", carried["to"])
    switch(api, server, carried["to"])
    carried["uploaded"] = datetime.now().astimezone().isoformat(timespec="seconds")
    (folder / CARRY).write_text(json.dumps(carried, indent=2))
    say(
        f"{server['name']} opens {carried['to']} at its next start, set in exaroton's options; "
        f"{carried['from']} stays on it as the way back"
    )
    sweep(api, server, (carried["from"], carried["to"]))


def main():
    parser = argparse.ArgumentParser(
        description="Carry a playtest's road into a fresh world: download the exaroton server's world, pack what stands "
        "in it into its players, grow a new world from a random seed and play it to the team's step with their own "
        "answers, or back to an earlier step of it with --step, lay their story and things into it; --upload puts the "
        "carried world on the server and switches to it, --pack the server pack"
    )
    parser.add_argument("server", nargs="?", help="server name or id; the account's only server when omitted")
    chosen = parser.add_mutually_exclusive_group()
    chosen.add_argument("--upload", action="store_true", help="put the last carried world on the server and play it")
    chosen.add_argument(
        "--pack",
        action="store_true",
        help="put the server pack just pack built on the server: its mods, configs and JourneyMap settings, as build/server holds them",
    )
    chosen.add_argument(
        "--step",
        help="a step of the main quest under way, at or before where the road stands, to carry the road back to, "
        "such as the_keepers_apprentice/the_smoke",
    )
    arguments = parser.parse_args()
    token = os.environ.get("EXAROTON_API_TOKEN")
    if not token:
        sys.exit("EXAROTON_API_TOKEN is not set: put it in .env, which devbox loads")
    api = Api(token)
    server = find_server(api, arguments.server)
    try:
        if arguments.pack:
            put_pack(api, server)
        elif arguments.upload:
            upload(api, server)
        else:
            carry(api, server, arguments.step)
    except ApiError as error:
        fail(str(error))


if __name__ == "__main__":
    main()

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

from exaroton import OFFLINE, RECORDINGS, ROOT, TRANSFER_SECONDS, Api, ApiError, describe, find_server

DEV_SERVER = ROOT / "build/devserver"
SERVER_PACK = ROOT / "build/server/mods"
DEV_DATA = ROOT / "mod/src/generated/dev/data"
DRIVER = ROOT / "scripts/server.py"
OLD = "carry-old"
NEW = "carry-new"
WALKER = "Wayfarer"
SUFFIX = "-carry"
STAMP = "%Y-%m-%d_%H-%M"
CARRY = "carry.json"
SEEDS = 4
WORKERS = 4
SHOWN = 8
STEP_SECONDS = 60
BUILD_SECONDS = 1800
PARTS = " | "
PACKED = "road: packed"
CARRIED = "road: carried to "
REFUSED = "road failed: "
LEVEL = re.compile(r"^level-name=(.*)$", re.MULTILINE)
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


def megabytes(size):
    return f"{size / 1_000_000:.0f} MB"


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


def properties(api, server):
    return api.data(f"servers/{server['id']}/files/data/server.properties").decode("utf-8")


def level_name(text):
    found = LEVEL.search(text)
    return found.group(1).strip() if found and found.group(1).strip() else "world"


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


def grow():
    tried = []
    for _ in range(SEEDS):
        seed = random.getrandbits(63)
        started = time.monotonic()
        try:
            driver("start", "--world", NEW, "--seed", str(seed))
            replies = batch(
                [
                    f"wayfarer pretend join {WALKER}",
                    f'wayfarer road follow "{DEV_SERVER / OLD}"',
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


def carry(api, server):
    offline(api, server)
    level = level_name(properties(api, server))
    stamp = datetime.now().strftime(STAMP)
    folder = RECORDINGS / (stamp + SUFFIX)
    shutil.rmtree(folder, ignore_errors=True)
    download(api, server, level, folder / "old")
    team = players(folder / "old", names(api, server))
    try:
        pack(folder, team)
        seed, step = grow()
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
                "to": f"world-{stamp}",
                "uploaded": None,
            },
            indent=2,
        )
    )
    say(f"carried into {(folder / 'new').relative_to(ROOT)} ({megabytes(size(folder / 'new'))})")
    say(f"next: just carry {server['name']} upload")


def carries(server):
    found = []
    for folder in sorted(RECORDINGS.glob("*" + SUFFIX)):
        if (folder / CARRY).exists():
            carried = json.loads((folder / CARRY).read_text())
            if carried["server"] == server["id"]:
                found.append((folder, carried))
    return found


def compare_mods(api, server):
    if not SERVER_PACK.exists():
        fail("build/server holds no server pack; run just pack and put dist's server pack on the server first")
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
            "put the server pack from just pack on it first"
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
        except ApiError:
            return path
        return None

    with ThreadPoolExecutor(WORKERS) as pool:
        refused = [path for path in pool.map(tried, files) if path is not None]
    for path in refused:
        send(path)
    say(f"uploaded {name}: {megabytes(sum(path.stat().st_size for path in files))} in {took(started)}")


def upload(api, server):
    waiting = [(folder, carried) for folder, carried in carries(server) if carried["uploaded"] is None]
    if not waiting:
        fail(f"nothing is carried for {server['name']} yet; run just carry {server['name']} first")
    folder, carried = waiting[-1]
    offline(api, server)
    text = properties(api, server)
    current = level_name(text)
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
    switched = (
        LEVEL.sub(f"level-name={carried['to']}", text) if LEVEL.search(text) else text + f"level-name={carried['to']}\n"
    )
    api.put(f"servers/{server['id']}/files/data/server.properties", switched.encode("utf-8"))
    say(f"{server['name']} plays {carried['to']}; {carried['from']} stays on it as the way back")
    before = [earlier for _, earlier in carries(server) if earlier["uploaded"] and earlier["to"] == carried["from"]]
    older = before[-1]["from"] if before else None
    if older and older not in (carried["from"], carried["to"]):
        try:
            remove(api, server, older)
            say(f"removed {older} from {server['name']}, two worlds back")
        except ApiError as error:
            say(f"{older} stays on {server['name']}: {error}")
    carried["uploaded"] = datetime.now().astimezone().isoformat(timespec="seconds")
    (folder / CARRY).write_text(json.dumps(carried, indent=2))


def main():
    parser = argparse.ArgumentParser(
        description="Carry a playtest's road into a fresh world: download the exaroton server's world, pack what stands "
        "in it into its players, grow a new world from a random seed and play it to the team's step with their own "
        "answers, lay their story and things into it; --upload puts the carried world on the server and switches to it"
    )
    parser.add_argument("server", nargs="?", help="server name or id; the account's only server when omitted")
    parser.add_argument("--upload", action="store_true", help="put the last carried world on the server and play it")
    arguments = parser.parse_args()
    token = os.environ.get("EXAROTON_API_TOKEN")
    if not token:
        sys.exit("EXAROTON_API_TOKEN is not set: put it in .env, which devbox loads")
    api = Api(token)
    server = find_server(api, arguments.server)
    try:
        (upload if arguments.upload else carry)(api, server)
    except ApiError as error:
        fail(str(error))


if __name__ == "__main__":
    main()

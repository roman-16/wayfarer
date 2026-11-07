import argparse
import json
import os
import re
import shutil
import sys
import time
from pathlib import Path

import drive
import hotswap
import instance
import states
from drive import Failure, Server, reply

ROOT = Path(__file__).resolve().parent.parent
SEAT = drive.seat("devserver")
DEV_SERVER = SEAT.folder
STORE = ROOT / "build/devserver"
SNAPSHOT = DEV_SERVER / "driver/classes"
LAUNCHED = DEV_SERVER / "driver/launched.json"
PROPERTIES = DEV_SERVER / "server.properties"
CHECKPOINTS = ROOT / "build/devserver/checkpoints"
LEVEL = re.compile(r"^level-name=(.*)$", re.MULTILINE)
DAY = re.compile(r"(\d+)$")
START_SECONDS = 900
STEP_SECONDS = 60
STOP_SECONDS = 120
STATE = "state: "
STORY = "story: "
STORY_AT = STORY + "at "
KEPT = STORY + "kept "
WALKER = "Wayfarer"


def dev_server(mode="full"):
    return Server(DEV_SERVER, ["just", "devserver", mode], cwd=ROOT)


def start(arguments):
    mode = arguments.mode
    server = dev_server(mode)
    if arguments.state:
        states.check(arguments.state)
    if arguments.world:
        other = drive.holder(arguments.world, "devserver")
        if other is not None and other.folder != DEV_SERVER:
            raise Failure(
                f'start failed: "{arguments.world}" is open in session {other.owner() or "of your own"}\'s dev server; ask that session to leave it'
            )
    if server.alive():
        launched = json.loads(LAUNCHED.read_text()) if LAUNCHED.exists() else None
        world = (
            arguments.state
            or arguments.fresh
            or arguments.seed is not None
            or (launched or {}).get("world") != arguments.world
        )
        reasons = restarting(launched, mode) or swapped(server)
        if not world and not reasons:
            print("the dev server is running")
            return
        if reasons:
            print("restarting: " + "; ".join(reasons))
        server.stop(STOP_SECONDS)
    if arguments.fresh or arguments.seed is not None:
        empty(saved(arguments.world or ("world" if mode == "full" else f"world-{mode}"), arguments.world is not None))
    if arguments.seed is not None:
        os.environ["WAYFARER_SEED"] = str(arguments.seed)
    if arguments.world:
        os.environ["WAYFARER_WORLD"] = arguments.world
    start = None
    if arguments.state:
        cache = states.folder("server", mode, arguments.state)
        start = states.prepare(cache, DEV_SERVER / ("state-lean" if mode == "lean" else "state"), arguments.rebuild)
        os.environ["WAYFARER_STATE"] = arguments.state
    os.environ |= {
        "WAYFARER_BACKGROUND": "true",
        "WAYFARER_DRIVEN": "true",
        "WAYFARER_GAME_DIRECTORY": str(DEV_SERVER),
        "WAYFARER_PORT": str(SEAT.offset()),
        "WAYFARER_SWAP_PORT": str(hotswap.port("server_swap_port") + SEAT.offset()),
    }
    drive.room("devserver")
    if arguments.world:
        drive.borrowed(DEV_SERVER, STORE, arguments.world)
    started = time.monotonic()
    with drive.locked(ROOT / "build/launch.lock"):
        server.start(arguments.timeout)
    hotswap.remember(SNAPSHOT)
    LAUNCHED.write_text(
        json.dumps({"digests": states.digests(), "mode": mode, "state": arguments.state, "world": arguments.world})
    )
    replies = []
    if arguments.state and start != arguments.state:
        server.run(f"wayfarer pretend join {WALKER}", STEP_SECONDS)
        replies = story(server, f"{arguments.state} {cache}", STEP_SECONDS)
        server.run(f"wayfarer pretend leave {WALKER}", STEP_SECONDS)
    kind = {"far": ", with Distant Horizons and SeedGen,", "full": "", "lean": ", with only the Wayfarer mod,"}[mode]
    if arguments.state:
        at = f" in state {arguments.state}, " + built(arguments.state, start, replies)
    else:
        at = f" in {arguments.world}" if arguments.world else ""
    port = instance.DEV_SERVER_PORTS["server"] + SEAT.offset()
    print(
        f"the dev server is running{kind}{at} at localhost:{port} after {time.monotonic() - started:.0f} s; log in {server.log}"
    )


def saved(name, shared):
    world = DEV_SERVER / name
    if world.exists() or world.is_symlink():
        return world.resolve()
    return (STORE if shared else DEV_SERVER) / name


def empty(save):
    if drive.opened(save):
        raise Failure(f'start failed: "{save.name}" is open in another game; stop that game first')
    try:
        shutil.rmtree(save)
    except FileNotFoundError:
        pass
    except OSError as error:
        raise Failure(f'start failed: "{save.name}" could not be emptied: {error}') from None
    save.mkdir(parents=True)


def restarting(launched, mode):
    if launched is None:
        return ["it was started by hand"]
    reasons = [] if launched["mode"] == mode else ["the mode is another"]
    return reasons + states.changed(launched["digests"], states.digests(), (*states.LAUNCH, "data"))


def built(state, start, replies):
    if start == state:
        return "from the cache,"
    kept = sum(said.startswith(KEPT) for said in replies)
    keeping = f", keeping {kept} earlier step{'s' * (kept != 1)}" if kept else ""
    return (f"built from {start}" if start else "built") + keeping + ","


def awaited(server, text, offset, timeout, taken):
    pattern = re.compile(re.escape(text))

    def match(line):
        said = reply(line)
        if said is None or server.position in taken or not pattern.search(said):
            return None
        taken.add(server.position)
        return said

    return server.wait(match, timeout, offset, f"a reply containing {text!r}")


def run(arguments):
    server = dev_server()
    server.attach()
    steps = arguments.steps or [line for line in sys.stdin.read().splitlines() if line.strip()]
    offset = server.size()
    taken = set()
    for step in steps:
        started = time.monotonic()
        try:
            if step.startswith("await "):
                result = {"replies": [awaited(server, step.removeprefix("await "), offset, arguments.timeout, taken)]}
            elif step == "state":
                result = state(server.run("wayfarer state", arguments.timeout))
            elif step.startswith("story "):
                result = {"replies": story(server, step.removeprefix("story "), arguments.timeout)}
            elif step == "checkpoint":
                result = checkpoint(server, arguments.timeout)
            elif step.split()[0] == "rewind":
                server, result = rewind(step)
                offset = server.size()
                taken = set()
            else:
                result = {"replies": server.run(step, arguments.timeout)}
        except Failure as failure:
            print(json.dumps({"step": step, "error": str(failure)}))
            sys.exit(1)
        seconds = round(time.monotonic() - started, 2)
        print(json.dumps({"step": step} | result | {"seconds": seconds}), flush=True)


def world():
    return DEV_SERVER / LEVEL.search(PROPERTIES.read_text()).group(1).strip()


def checkpoint(server, timeout):
    folder = world()
    day = int(DAY.search(server.run("time query day", timeout)[0]).group(1)) + 1
    server.run("save-off", timeout)
    try:
        server.run("save-all flush", timeout)
        taken = drive.keep(folder, CHECKPOINTS / folder.name, f"day {day}")
    finally:
        server.run("save-on", timeout)
    return {"checkpoint": [f"checkpoint {taken['number']}: {taken['where']}"]}


def rewind(step):
    words = step.split()
    back = int(words[1]) if len(words) > 1 else 1
    folder = world()
    kept = drive.checkpoints(CHECKPOINTS / folder.name)
    if len(kept) < back:
        raise Failure(f"rewind failed: {folder.name} has {len(kept)} checkpoint{'s' * (len(kept) != 1)}")
    launched = json.loads(LAUNCHED.read_text())
    dev_server(launched["mode"]).stop(STOP_SECONDS)
    back_to = drive.rewound(folder, kept[-back])
    os.environ |= {"WAYFARER_BACKGROUND": "true", "WAYFARER_DRIVEN": "true"}
    if launched.get("state"):
        os.environ["WAYFARER_STATE"] = launched["state"]
    if launched.get("world"):
        os.environ["WAYFARER_WORLD"] = launched["world"]
    server = dev_server(launched["mode"])
    server.start(START_SECONDS)
    hotswap.remember(SNAPSHOT)
    return server, {"rewind": [back_to + "; the server started again, so players join again"]}


def story(server, argument, timeout):
    target, *into = argument.split()
    offset = server.size()
    command = f'wayfarer story build "{into[0]}" {target}' if into else f"wayfarer story to {target}"
    replies = server.run(command, timeout)
    taken = set()

    def match(line):
        said = reply(line)
        if said is None or server.position in taken or not said.startswith(STORY):
            return None
        taken.add(server.position)
        return said

    while not any(said.startswith(STORY_AT) for said in replies):
        replies.append(server.wait(match, timeout, offset, f"the story to stand at {target}"))
    return replies


def state(replies):
    found = [reply for reply in replies if reply.startswith(STATE)]
    if not found:
        return {"replies": replies, "error": "the server gave no state; is the Wayfarer mod's dev code loaded?"}
    return {"state": json.loads(Path(found[0].removeprefix(STATE)).read_text())}


def swap_in(server):
    result = hotswap.swap(hotswap.port("server_swap_port") + SEAT.offset(), SNAPSHOT)
    if result["listeners"]:
        server.run("wayfarer listeners " + " ".join(result["listeners"]), STEP_SECONDS)
    return result


def swapped(server):
    result = swap_in(server)
    if any(result[key] for key in ("swapped", "listeners", "needs a restart")):
        print(json.dumps(result))
    return [f"{', '.join(result['needs a restart'])} needs a restart"] if result["needs a restart"] else []


def swap(arguments):
    server = dev_server()
    server.attach()
    print(json.dumps(swap_in(server)))


def stop(arguments):
    server = dev_server()
    if not server.alive():
        print("the dev server is not running")
        return
    server.stop(arguments.timeout)
    print("the dev server has stopped")


def main():
    parser = argparse.ArgumentParser(
        description="Drive the dev server: the whole pack with the Wayfarer mod's dev code, kept running between batches"
    )
    commands = parser.add_subparsers(dest="command", required=True)
    start_parser = commands.add_parser(
        "start",
        help="keep the running server, swapping in changed code first, or launch it when none runs, its world is to "
        "change, its mods or data changed, or a change needs a restart; returns once the server is ready",
    )
    worlds = start_parser.add_mutually_exclusive_group()
    worlds.add_argument("--fresh", action="store_true", help="delete the dev server's world first")
    worlds.add_argument(
        "--seed", type=int, help="delete the dev server's world first and grow the new one from this seed"
    )
    worlds.add_argument(
        "--state",
        metavar="QUEST/STEP",
        help="open the world at a step of the story, such as ash/the_door, built from story/states.toml and kept "
        "in build/states/",
    )
    modes = start_parser.add_mutually_exclusive_group()
    modes.add_argument(
        "--far",
        action="store_const",
        const="far",
        dest="mode",
        help="add Distant Horizons and SeedGen, which draw the far view for the players who join, in a world of its own",
    )
    modes.add_argument(
        "--lean",
        action="store_const",
        const="lean",
        dest="mode",
        help="load only the Wayfarer mod and what it needs, in a world of its own",
    )
    start_parser.set_defaults(mode="full")
    start_parser.add_argument(
        "--world",
        metavar="SAVE",
        help="open a save in build/devserver by its folder name, such as one copied there; with --fresh or --seed it "
        "grows anew",
    )
    start_parser.add_argument(
        "--rebuild",
        action="store_true",
        help="build the state anew instead of from the cache, after a change to code that builds an earlier step",
    )
    start_parser.add_argument("--timeout", type=int, default=START_SECONDS)
    run_parser = commands.add_parser(
        "run",
        help="run console commands in order, each returning once the server has run it, with one line of JSON per step; "
        + "'await <text>' waits for a later reply containing the text, such as an asynchronous /locate's; "
        + "'state' returns the world's story state; "
        + "'story <quest/step>' plays the story to that step and returns once it stands there; "
        + "'checkpoint' keeps a copy of the world in build/devserver/checkpoints/; "
        + "'rewind [n]' puts the world back as its newest checkpoint, or the nth newest, left it and starts the server again",
    )
    run_parser.add_argument("steps", nargs="*")
    run_parser.add_argument("--timeout", type=int, default=STEP_SECONDS)
    commands.add_parser(
        "swap",
        help="compile the Wayfarer mod and swap every changed class into the running server; prints one line of JSON",
    )
    stop_parser = commands.add_parser("stop", help="stop the server; returns once it has exited")
    stop_parser.add_argument("--timeout", type=int, default=STOP_SECONDS)
    arguments = parser.parse_args()
    if (
        arguments.command == "start"
        and arguments.world
        and (arguments.state or arguments.world in ("state", "state-lean"))
    ):
        parser.error("--world opens a save of its own; a state is opened with --state alone")
    try:
        {"run": run, "start": start, "stop": stop, "swap": swap}[arguments.command](arguments)
    except Failure as failure:
        sys.exit(str(failure))


main()

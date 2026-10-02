import argparse
import json
import re
import shutil
import sys
import time
from pathlib import Path

import hotswap
from drive import Failure, Server, reply

ROOT = Path(__file__).resolve().parent.parent
DEV_SERVER = ROOT / "build/devserver"
SNAPSHOT = DEV_SERVER / "driver/classes.json"
START_SECONDS = 900
STEP_SECONDS = 300
STOP_SECONDS = 120
STATE = "state: "


def dev_server(mode="full"):
    return Server(DEV_SERVER, ["just", "devserver", mode], cwd=ROOT)


def start(arguments):
    server = dev_server("lean" if arguments.lean else "full")
    if server.alive():
        print("the dev server is already running")
        return
    if arguments.fresh:
        shutil.rmtree(DEV_SERVER / ("world-lean" if arguments.lean else "world"), ignore_errors=True)
    started = time.monotonic()
    server.start(arguments.timeout)
    hotswap.remember(SNAPSHOT)
    lean = ", with only the Wayfarer mod," if arguments.lean else ""
    print(f"the dev server is running{lean} after {time.monotonic() - started:.0f} s; log in {server.log}")


def awaited(server, text, offset, timeout):
    pattern = re.compile(re.escape(text))

    def match(line):
        said = reply(line)
        return said if said is not None and pattern.search(said) else None

    return server.wait(match, timeout, offset, f"a reply containing {text!r}")


def run(arguments):
    server = dev_server()
    server.attach()
    steps = arguments.steps or [line for line in sys.stdin.read().splitlines() if line.strip()]
    offset = server.size()
    for step in steps:
        started = time.monotonic()
        try:
            if step.startswith("await "):
                result = {"replies": [awaited(server, step.removeprefix("await "), offset, arguments.timeout)]}
            elif step == "state":
                result = state(server.run("wayfarer state", arguments.timeout))
            else:
                result = {"replies": server.run(step, arguments.timeout)}
        except Failure as failure:
            print(json.dumps({"step": step, "error": str(failure)}))
            sys.exit(1)
        seconds = round(time.monotonic() - started, 2)
        print(json.dumps({"step": step} | result | {"seconds": seconds}), flush=True)


def state(replies):
    found = [reply for reply in replies if reply.startswith(STATE)]
    if not found:
        return {"replies": replies, "error": "the server gave no state; is the Wayfarer mod's dev code loaded?"}
    return {"state": json.loads(found[0].removeprefix(STATE))}


def swap(arguments):
    dev_server().attach()
    print(json.dumps(hotswap.swap(hotswap.port("server_swap_port"), SNAPSHOT)))


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
    start_parser = commands.add_parser("start", help="launch unless running; returns once the server is ready")
    start_parser.add_argument("--fresh", action="store_true", help="delete the dev server's world first")
    start_parser.add_argument(
        "--lean", action="store_true", help="load only the Wayfarer mod and what it needs, in a world of its own"
    )
    start_parser.add_argument("--timeout", type=int, default=START_SECONDS)
    run_parser = commands.add_parser(
        "run",
        help="run console commands in order, each returning once the server has run it, with one line of JSON per step; "
        + "'await <text>' waits for a later reply containing the text, such as an asynchronous /locate's; "
        + "'state' returns the world's story state",
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
    try:
        {"run": run, "start": start, "stop": stop, "swap": swap}[arguments.command](arguments)
    except Failure as failure:
        sys.exit(str(failure))


main()

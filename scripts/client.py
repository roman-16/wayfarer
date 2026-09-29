import argparse
import os
import signal
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CLIENT = ROOT / "build/client"
DRIVER = CLIENT / "driver"
LAUNCHER = DRIVER / "launcher.pid"
GAME = DRIVER / "game.pid"
LAUNCH_LOG = CLIENT / "launch.log"
CRASHES = CLIENT / "crash-reports"
ABORT_SECONDS = 30
CHECK_SECONDS = 0.1


def running(pid_file):
    if not pid_file.exists():
        return False
    try:
        os.kill(int(pid_file.read_text()), 0)
    except (ProcessLookupError, ValueError):
        return False
    return True


def alive():
    return running(LAUNCHER) or running(GAME)


def crashes():
    return set(CRASHES.glob("*.txt")) if CRASHES.exists() else set()


def wait_done(timeout, before):
    done = DRIVER / "done.txt"
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if done.exists():
            text = done.read_text()
            done.unlink()
            return text
        new = sorted(crashes() - before)
        if new:
            sys.exit(f"the client crashed; report in {new[0]}\n{new[0].read_text()[:4000]}")
        if not alive():
            tail = LAUNCH_LOG.read_text()[-4000:] if LAUNCH_LOG.exists() else ""
            sys.exit(f"the client is not running; launch log {LAUNCH_LOG}\n{tail}")
        time.sleep(CHECK_SECONDS)
    (DRIVER / "abort").touch()
    report = done.read_text() if done.exists() else ""
    while not report and alive() and time.monotonic() < deadline + ABORT_SECONDS:
        time.sleep(CHECK_SECONDS)
        report = done.read_text() if done.exists() else ""
    done.unlink(missing_ok=True)
    sys.exit(f"the driver did not finish within {timeout} s and was aborted; its log:\n{report}")


def start(arguments):
    if alive():
        print("the client is already running")
        return
    DRIVER.mkdir(parents=True, exist_ok=True)
    for stale in ("abort", "commands.txt", "done.txt", "game.pid", "quit"):
        (DRIVER / stale).unlink(missing_ok=True)
    before = crashes()
    command = ["just", "client"] + (["fresh"] if arguments.fresh else [])
    with LAUNCH_LOG.open("w") as log:
        process = subprocess.Popen(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
    LAUNCHER.write_text(str(process.pid))
    report = wait_done(arguments.timeout, before)
    errors = [line for line in report.splitlines() if line.startswith("error:")]
    print(f"the client is running in the showroom ({len(report.splitlines())} log lines, {len(errors)} errors)")
    print("\n".join(errors))


def run(arguments):
    if not alive():
        sys.exit("the client is not running; start it with: python3 scripts/client.py start")
    steps = arguments.steps or [line for line in sys.stdin.read().splitlines() if line.strip()]
    before = crashes()
    partial = DRIVER / "commands.tmp"
    partial.write_text("\n".join(steps) + "\n")
    partial.replace(DRIVER / "commands.txt")
    print(wait_done(arguments.timeout, before), end="")


def stop(arguments):
    if not alive():
        print("the client is not running")
        return
    (DRIVER / "quit").touch()
    deadline = time.monotonic() + arguments.timeout
    while alive() and time.monotonic() < deadline:
        time.sleep(CHECK_SECONDS)
    if running(GAME):
        os.kill(int(GAME.read_text()), signal.SIGKILL)
        print(f"the client did not exit within {arguments.timeout} s and was killed")
    for pid_file in (GAME, LAUNCHER):
        pid_file.unlink(missing_ok=True)
    (DRIVER / "quit").unlink(missing_ok=True)
    print("the client has exited")


def main():
    parser = argparse.ArgumentParser(
        description="Drive the dev client: every command returns once the game has finished"
    )
    commands = parser.add_subparsers(dest="command", required=True)
    start_parser = commands.add_parser(
        "start", help="launch the client unless it runs; returns once the showroom is built"
    )
    start_parser.add_argument("--fresh", action="store_true", help="start the natural world anew")
    start_parser.add_argument("--timeout", type=int, default=900)
    run_parser = commands.add_parser(
        "run", help="run driver steps (arguments, or one per line on stdin) and print their log"
    )
    run_parser.add_argument("steps", nargs="*")
    run_parser.add_argument("--timeout", type=int, default=600)
    stop_parser = commands.add_parser("stop", help="quit the client; returns once it has exited")
    stop_parser.add_argument("--timeout", type=int, default=120)
    arguments = parser.parse_args()
    {"run": run, "start": start, "stop": stop}[arguments.command](arguments)


main()

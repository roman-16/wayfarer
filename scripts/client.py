import argparse
import json
import math
import os
import re
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
START_SECONDS = 900
STOP_SECONDS = 120
CHECK_SECONDS = 0.1
TOUR = ROOT / "mod/src/generated/dev/wayfarer/tour.json"
SHEETS = CLIENT / "tour"
SCREENSHOTS = CLIENT / "screenshots"
FOUND = re.compile(r"found \d+ \S+ within \d+: (-?\d+) (-?\d+) (-?\d+)")
EYE_HEIGHT = 1.62
SHAFTS = {"ladder": 5, "spiral": 11, "switchback": 11}
SOUTH, WEST, NORTH, EAST = (0, 1), (-1, 0), (0, -1), (1, 0)
SETTLE = ["settle", "wait 10"]


def running(pid_file):
    if not pid_file.exists():
        return False
    try:
        pid = int(pid_file.read_text())
    except ValueError:
        return False
    try:
        if os.waitpid(pid, os.WNOHANG)[0] == pid:
            return False
    except ChildProcessError:
        pass
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
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


def launch(fresh, timeout):
    DRIVER.mkdir(parents=True, exist_ok=True)
    for stale in ("abort", "commands.txt", "done.txt", "game.pid", "quit"):
        (DRIVER / stale).unlink(missing_ok=True)
    before = crashes()
    command = ["just", "client" if fresh else "demo"]
    with LAUNCH_LOG.open("w") as log:
        process = subprocess.Popen(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
    LAUNCHER.write_text(str(process.pid))
    report = wait_done(timeout, before)
    errors = [line for line in report.splitlines() if line.startswith("error:")]
    world = "a fresh natural world" if fresh else "the showroom"
    print(f"the client is running in {world} ({len(report.splitlines())} log lines, {len(errors)} errors)")
    print("\n".join(errors))


def start(arguments):
    if alive():
        print("the client is already running")
        return
    launch(arguments.fresh, arguments.timeout)


def batch(steps, timeout):
    if not alive():
        sys.exit("the client is not running; start it with: python3 scripts/client.py start")
    before = crashes()
    partial = DRIVER / "commands.tmp"
    partial.write_text("\n".join(steps) + "\n")
    partial.replace(DRIVER / "commands.txt")
    return wait_done(timeout, before)


def run(arguments):
    steps = arguments.steps or [line for line in sys.stdin.read().splitlines() if line.strip()]
    print(batch(steps, arguments.timeout), end="")


def view(name, at, look):
    eye = (at[0], at[1] + EYE_HEIGHT, at[2])
    dx, dy, dz = look[0] - eye[0], look[1] - eye[1], look[2] - eye[2]
    yaw = math.degrees(math.atan2(-dx, dz))
    pitch = -math.degrees(math.atan2(dy, math.hypot(dx, dz)))
    return [f"/tp @s {at[0]:.2f} {at[1]:.2f} {at[2]:.2f} {yaw:.1f} {pitch:.1f}", *SETTLE, f"shot tour-{name}"]


def offset(origin, local):
    return [origin[axis] + local[axis] for axis in range(3)]


def exit_of(style, depth):
    if style == "spiral":
        return {4: SOUTH, 7: WEST, 10: NORTH, 1: EAST}[depth % 12]
    if style == "switchback":
        return {6: SOUTH, 1: NORTH}[depth % 10]
    return SOUTH


def descent(style, exit):
    if style == "spiral":
        near, far = 8.5, 2.5
        return {
            SOUTH: ((near, near), (near, far)),
            WEST: ((far, near), (near, near)),
            NORTH: ((far, far), (far, near)),
            EAST: ((near, far), (far, far)),
        }[exit]
    if style == "switchback":
        return {SOUTH: ((7.5, 8.5), (7.5, 2.5)), NORTH: ((2.5, 1.5), (2.5, 8.5))}[exit]
    return ((2.5, 2.5), (2.5, 3.5))


def shrine_views(shrine, pedestal, surface):
    origin = [pedestal[axis] - shrine["pedestal"][axis] for axis in range(3)]
    door_x, door_y, door_z = shrine["door"]
    floor = origin[1] + door_y
    size = SHAFTS[shrine["shaft"]]
    shaft = [origin[0] + door_x - size // 2, 0, origin[2] + door_z + 1]
    ex, ez = exit_of(shrine["shaft"], surface - floor)
    (top_x, top_z), (down_x, down_z) = descent(shrine["shaft"], (ex, ez))
    middle = size / 2
    centre = [shaft[0] + middle, surface + 2, shaft[2] + middle]
    side = (-ez, ex)
    views = {
        "outside": ([centre[0] + ex * 13 + side[0] * 5, surface + 2, centre[2] + ez * 13 + side[1] * 5], centre),
        "above": ([centre[0] - 12, surface + 16, centre[2] + 12], [centre[0], surface, centre[2]]),
        "entrance": ([centre[0] + ex * 6, surface, centre[2] + ez * 6], [centre[0], surface, centre[2]]),
        "stair-top": (
            [shaft[0] + top_x, surface - 1, shaft[2] + top_z],
            [shaft[0] + down_x, surface - 6, shaft[2] + down_z],
        ),
        "stair-bottom": (
            [shaft[0] + size - 2.5, floor, shaft[2] + 1.5],
            [shaft[0] + size - 2.5, floor + 5, shaft[2] + size - 2.5],
        ),
        "stair-door": ([shaft[0] + middle, floor, shaft[2] + 2.5], [shaft[0] + middle, floor + 1.5, shaft[2] - 6]),
    }
    for camera in shrine["cameras"]:
        views[camera["name"]] = (offset(origin, camera["at"]), offset(origin, camera["look"]))
    return views


def sheet(subject, names):
    SHEETS.mkdir(parents=True, exist_ok=True)
    font = subprocess.run(
        ["fc-match", "--format", "%{file}", "sans"], capture_output=True, check=True, text=True
    ).stdout
    arguments = ["-font", font, "-pointsize", "22", "-background", "#202020", "-fill", "white"]
    for name in names:
        arguments += ["-label", name, str(SCREENSHOTS / f"tour-{subject}-{name}.png")]
    target = SHEETS / f"{subject}.jpg"
    subprocess.run(
        [
            "magick",
            "montage",
            *arguments,
            "-geometry",
            "640x360+4+4",
            "-tile",
            "3x",
            "-quality",
            "82",
            str(target),
        ],
        check=True,
    )
    return target


def tour(arguments):
    if alive():
        shut(STOP_SECONDS)
    launch(False, START_SECONDS)
    try:
        photograph(arguments)
    finally:
        shut(STOP_SECONDS)


def photograph(arguments):
    plan = json.loads(TOUR.read_text())
    surface = plan["surface"]
    shrines = [shrine for shrine in plan["shrines"] if not arguments.subjects or shrine["eye"] in arguments.subjects]
    locate = ["gui off", "/gamemode spectator"]
    for shrine in shrines:
        x, z = shrine["centre"]
        locate += [
            f"/execute in wayfarer:showroom run tp @s {x + 0.5} {surface + 10} {z + 0.5}",
            "settle",
            "find wayfarer:shrine_pedestal 16",
        ]
    found = [
        tuple(int(value) for value in match.groups()) for match in FOUND.finditer(batch(locate, arguments.timeout))
    ]
    if len(found) != len(shrines):
        sys.exit(f"found {len(found)} pedestals for {len(shrines)} shrines; the showroom does not match the generator")
    subjects = {}
    for shrine, pedestal in zip(shrines, found, strict=True):
        subjects[shrine["eye"]] = shrine_views(shrine, pedestal, surface)
    if not arguments.subjects or "camp" in arguments.subjects:
        origin = plan["camp"]["origin"]
        subjects["camp"] = {
            camera["name"]: (offset(origin, camera["at"]), offset(origin, camera["look"]))
            for camera in plan["camp"]["cameras"]
        }
    steps = []
    for subject, views in subjects.items():
        for name, (at, look) in views.items():
            steps += view(f"{subject}-{name}", at, look)
    batch(steps + ["/gamemode creative", "gui on"], arguments.timeout)
    for subject, views in subjects.items():
        print(sheet(subject, list(views)))


def stop(arguments):
    if not alive():
        print("the client is not running")
        return
    shut(arguments.timeout)


def shut(timeout):
    (DRIVER / "quit").touch()
    deadline = time.monotonic() + timeout
    while alive() and time.monotonic() < deadline:
        time.sleep(CHECK_SECONDS)
    if running(GAME):
        os.kill(int(GAME.read_text()), signal.SIGKILL)
        print(f"the client did not exit within {timeout} s and was killed")
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
    start_parser.add_argument("--fresh", action="store_true", help="open a fresh natural world instead of the showroom")
    start_parser.add_argument("--timeout", type=int, default=START_SECONDS)
    run_parser = commands.add_parser(
        "run", help="run driver steps (arguments, or one per line on stdin) and print their log"
    )
    run_parser.add_argument("steps", nargs="*")
    run_parser.add_argument("--timeout", type=int, default=600)
    stop_parser = commands.add_parser("stop", help="quit the client; returns once it has exited")
    stop_parser.add_argument("--timeout", type=int, default=STOP_SECONDS)
    tour_parser = commands.add_parser(
        "tour",
        help="launch the client on a fresh showroom, photograph every shrine and the Last Camp, quit the client; "
        "prints one contact sheet per subject",
    )
    tour_parser.add_argument("subjects", nargs="*", help="eyes (and camp) to photograph; all when none are named")
    tour_parser.add_argument("--timeout", type=int, default=1800)
    arguments = parser.parse_args()
    {"run": run, "start": start, "stop": stop, "tour": tour}[arguments.command](arguments)


main()

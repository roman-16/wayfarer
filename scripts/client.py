import argparse
import contextlib
import json
import math
import os
import re
import shutil
import signal
import subprocess
import sys
import time
from pathlib import Path

import hotswap
import states
from drive import Failure, crashes, running

ROOT = Path(__file__).resolve().parent.parent
CLIENT = ROOT / "build/client"
DRIVER = CLIENT / "driver"
LAUNCHER = DRIVER / "launcher.pid"
GAME = DRIVER / "game.pid"
LAUNCHED = DRIVER / "launched.json"
WORLD = DRIVER / "world.json"
SCREEN = DRIVER / "screen.pid"
SNAPSHOT = DRIVER / "classes"
SCREEN_SIZE = "1920x1080x24"
LAUNCH_LOG = CLIENT / "launch.log"
CRASHES = CLIENT / "crash-reports"
DISPLAY_FAILED = "Failed to initialize the mod loading system and display"
ABORT_SECONDS = 30
RUN_SECONDS = 300
START_SECONDS = 900
STOP_SECONDS = 120
CHECK_SECONDS = 0.1
TOUR = ROOT / "mod/src/generated/dev/wayfarer/tour.json"
SHEETS = CLIENT / "tour"
WORKSHOP_SHEETS = CLIENT / "workshop"
WORKSHOP = "wayfarer:workshop"
BOUNDS = re.compile(r"workshop bounds: (-?\d+) (-?\d+) (-?\d+) (-?\d+) (-?\d+) (-?\d+)")
OPTIONS = ROOT / "pack/config/defaultoptions/options.txt"
FLYING_FOV = 1.1
FIELD_OF_VIEW = FLYING_FOV * (70 + 40 * float(re.search(r"^fov:(.*)$", OPTIONS.read_text(), re.MULTILINE).group(1)))
ASPECT = 1280 / 720
FRAMING = 1.1
NOTE = re.compile(r"([a-z]+): (.*)")
STATE = "state: "
KEPT = "story: kept "
FAILED_AT_ONCE = "` failed at once: "
SIDES = {"front": (0, 0, 1), "side": (1, 0, 0), "above": (0, 1, 0.001), "three-quarters": (0.6, 1.0, 0.6)}
SCREENSHOTS = CLIENT / "screenshots"
FOUND = re.compile(r"found \d+ \S+ within \d+: (-?\d+) (-?\d+) (-?\d+)")
EYE_HEIGHT = 1.62
SHAFTS = {"ladder": 5, "spiral": 11, "switchback": 11}
SHAFT_CAMERAS = ("outside", "above", "entrance", "stair-top", "stair-bottom", "stair-door")
PANORAMA = ROOT / "mod/src/main/resources/assets/minecraft/textures/gui/title/background"
PANORAMA_CAMERA = (-217.5, 79.3, 178.5, 270)
PANORAMA_CHUNKS = 16
PANORAMA_FACES = 6
PANORAMA_SEED = 5
PANORAMA_TIME = 23200
SOUTH, WEST, NORTH, EAST = (0, 1), (-1, 0), (0, -1), (1, 0)


def alive():
    return running(LAUNCHER) or running(GAME)


def wait_done(timeout, before):
    done = DRIVER / "done.txt"
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if done.exists():
            text = done.read_text()
            done.unlink()
            return text
        new = sorted(crashes(CRASHES) - before)
        if new:
            sys.exit(f"the client crashed; report in {new[0]}\n{new[0].read_text()[:4000]}")
        launched = LAUNCH_LOG.read_text(errors="replace") if LAUNCH_LOG.exists() else ""
        if DISPLAY_FAILED in launched:
            sys.exit(f"the client could not open its window; launch log {LAUNCH_LOG}\n{launched[-4000:]}")
        if not alive():
            sys.exit(f"the client is not running; launch log {LAUNCH_LOG}\n{launched[-4000:]}")
        time.sleep(CHECK_SECONDS)
    (DRIVER / "abort").touch()
    report = done.read_text() if done.exists() else ""
    while not report and alive() and time.monotonic() < deadline + ABORT_SECONDS:
        time.sleep(CHECK_SECONDS)
        report = done.read_text() if done.exists() else ""
    done.unlink(missing_ok=True)
    sys.exit(f"the driver did not finish within {timeout} s and was aborted; its log:\n{report}")


def screen(visible):
    environment = dict(os.environ)
    if visible:
        return environment
    ready, announce = os.pipe()
    process = subprocess.Popen(
        ["Xvfb", "-displayfd", str(announce), "-screen", "0", SCREEN_SIZE, "-nolisten", "tcp", "-terminate"],
        pass_fds=(announce,),
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        start_new_session=True,
    )
    os.close(announce)
    with os.fdopen(ready) as stream:
        display = stream.readline().strip()
    if not display:
        sys.exit("the hidden screen (Xvfb) did not start; run with --visible to use the real one")
    SCREEN.write_text(str(process.pid))
    environment.pop("WAYLAND_DISPLAY", None)
    return environment | {
        "DISPLAY": f":{display}",
        "WAYFARER_BACKGROUND": "true",
        "WAYFARER_SILENT": "true",
        "XDG_SESSION_TYPE": "x11",
        "__GLX_VENDOR_LIBRARY_NAME": "mesa",
    }


def games():
    for process in Path("/proc").iterdir():
        if process.name.isdigit():
            try:
                if (process / "cwd").resolve() == CLIENT:
                    yield int(process.name)
            except OSError:
                continue


def release_screen():
    if running(SCREEN):
        pid = int(SCREEN.read_text())
        os.kill(pid, signal.SIGTERM)
        deadline = time.monotonic() + STOP_SECONDS
        while running(SCREEN) and time.monotonic() < deadline:
            time.sleep(CHECK_SECONDS)
        if running(SCREEN):
            os.kill(pid, signal.SIGKILL)
    SCREEN.unlink(missing_ok=True)


def launch(world, timeout, visible, mode, state, rebuild, seed):
    DRIVER.mkdir(parents=True, exist_ok=True)
    for stale in ("abort", "commands.txt", "done.txt", "game.pid", "launched.json", "quit", "world.json"):
        (DRIVER / stale).unlink(missing_ok=True)
    release_screen()
    shutil.rmtree(SCREENSHOTS, ignore_errors=True)
    before = crashes(CRASHES)
    command = ["just", "showroom" if world == "showroom" else "play", mode]
    environment = screen(visible) | {"WAYFARER_DRIVEN": "true", "WAYFARER_SEED": "" if seed is None else str(seed)}
    start = None
    if state:
        cache = states.folder("client", mode, state)
        start = states.prepare(cache, CLIENT / "saves" / ("state-lean" if mode == "lean" else "state"), rebuild)
        environment |= {"WAYFARER_STATE": state, "WAYFARER_STATE_CACHE": str(cache)}
    started = time.monotonic()
    with LAUNCH_LOG.open("w") as log:
        process = subprocess.Popen(
            command,
            cwd=ROOT,
            env=environment,
            stdin=subprocess.DEVNULL,
            stdout=log,
            stderr=subprocess.STDOUT,
            start_new_session=True,
        )
    LAUNCHER.write_text(str(process.pid))
    try:
        report = wait_done(timeout, before)
    except SystemExit:
        with contextlib.suppress(ProcessLookupError):
            os.killpg(process.pid, signal.SIGKILL)
        for pid in games():
            with contextlib.suppress(ProcessLookupError):
                os.kill(pid, signal.SIGKILL)
        release_screen()
        raise
    blocked = [line for line in report.splitlines() if line.startswith("blocked:")]
    if blocked:
        shut(STOP_SECONDS)
        sys.exit(f"the client could not open its world: {blocked[0].removeprefix('blocked: ')}")
    hotswap.remember(SNAPSHOT)
    record(states.digests(), mode, visible)
    say(world, state, start, f"launched in {time.monotonic() - started:.0f} s", visible, mode, report)


def built(state, start, report):
    if start == state:
        return "from the cache,"
    kept = report.count(KEPT)
    keeping = f", keeping {kept} earlier step{'s' * (kept != 1)}" if kept else ""
    return (f"built from {start}" if start else "built") + keeping + ","


def say(world, state, start, took, visible, mode, report):
    errors = [line for line in report.splitlines() if line.startswith("error:")]
    where = {
        "play": "a fresh natural world",
        "showroom": "the showroom",
        "state": f"state {state}, " + built(state, start, report),
    }[world]
    screen = ("on the user's screen" if visible else "on a hidden screen") + {
        "far": ", with Distant Horizons' far view",
        "full": "",
        "lean": ", with only the Wayfarer mod",
    }[mode]
    stopped = [line for line in errors if FAILED_AT_ONCE in line]
    if stopped:
        sys.exit(f"opening {state or world} failed: {stopped[0].removeprefix('error: ')}")
    print(
        f"the client is running in {where} {screen} ({took}, {len(report.splitlines())} log lines, {len(errors)} errors)"
    )
    print("\n".join(errors))


def record(digests, mode, visible):
    LAUNCHED.write_text(json.dumps({"digests": digests, "mode": mode, "visible": visible}))


def current():
    if not alive() or not LAUNCHED.exists() or not WORLD.exists():
        return None, None
    return json.loads(LAUNCHED.read_text()), json.loads(WORLD.read_text())


def relaunching(launched, digests, mode, visible):
    if launched is None:
        return ["none runs"]
    reasons = [] if (launched["mode"], launched["visible"]) == (mode, visible) else ["the screen or mode is another"]
    return reasons + states.changed(launched["digests"], digests, states.LAUNCH)


def swap_in():
    try:
        result = hotswap.swap(hotswap.port("client_swap_port"), SNAPSHOT)
    except Failure as failure:
        sys.exit(str(failure))
    if result["listeners"]:
        batch(["listeners " + " ".join(result["listeners"])], RUN_SECONDS)
    return result


def swapped():
    result = swap_in()
    if any(result[key] for key in ("swapped", "listeners", "needs a restart")):
        print(json.dumps(result))
    return [f"{', '.join(result['needs a restart'])} needs a restart"] if result["needs a restart"] else []


def ensure(world, timeout, visible, mode, state=None, rebuild=False, anew=False, seed=None):
    launched, opened = current()
    digests = states.digests()
    reasons = relaunching(launched, digests, mode, visible) or swapped()
    if reasons:
        if alive():
            print("relaunching: " + "; ".join(reasons))
            shut(STOP_SECONDS)
        launch(world, timeout, visible, mode, state, rebuild, seed)
        return
    reloaded = ""
    if states.changed(launched["digests"], digests, ("assets",)):
        batch(["assets"], timeout)
        reloaded = "textures, models, fonts, sounds and lang reloaded, "
    data = states.changed(launched["digests"], digests, ("data",))
    record(digests, mode, visible)
    if world == "showroom" and opened["world"] == "showroom" and not anew and not data:
        print("the client is running in the showroom" + (f" ({reloaded.removesuffix(', ')})" if reloaded else ""))
        return
    switch(world, timeout, visible, mode, state, rebuild, seed, reloaded)


def switch(world, timeout, visible, mode, state, rebuild, seed, reloaded=""):
    started = time.monotonic()
    batch(["leave"], timeout)
    start = None
    if state:
        cache = states.folder("client", mode, state)
        start = states.prepare(cache, CLIENT / "saves" / ("state-lean" if mode == "lean" else "state"), rebuild)
        target = f"state {state} {cache}"
    else:
        subprocess.run(
            ["python3", "scripts/instance.py", "fresh", str(CLIENT), world, "--mode", mode], cwd=ROOT, check=True
        )
        target = world if seed is None else f"{world} {seed}"
    report = batch([f"enter {target}"], timeout)
    say(world, state, start, f"{reloaded}opened in {time.monotonic() - started:.0f} s", visible, mode, report)


def start(arguments):
    if arguments.state:
        states.check(arguments.state)
    world = "state" if arguments.state else "play" if arguments.fresh or arguments.seed is not None else "showroom"
    ensure(
        world,
        arguments.timeout,
        arguments.visible,
        arguments.mode,
        arguments.state,
        arguments.rebuild,
        seed=arguments.seed,
    )


def batch(steps, timeout):
    if not alive():
        sys.exit("the client is not running; start it with: python3 scripts/client.py start")
    before = crashes(CRASHES)
    partial = DRIVER / "commands.tmp"
    partial.write_text("\n".join(steps) + "\n")
    partial.replace(DRIVER / "commands.txt")
    return wait_done(timeout, before)


def run(arguments):
    steps = arguments.steps or [line for line in sys.stdin.read().splitlines() if line.strip()]
    local = {"compare": compare, "views": views}
    pending = []

    def flush():
        if pending:
            entries = parse(batch(pending, arguments.timeout))
            pending.clear()
            report(entries)

    def report(entries):
        for entry in entries:
            print(json.dumps(entry), flush=True)
        if any("error" in entry for entry in entries):
            sys.exit(1)

    for step in steps:
        verb = step.split(" ", 1)[0]
        if verb in local:
            flush()
            report([local[verb](step, arguments.timeout)])
        else:
            pending.append(step)
    flush()


def parse(report):
    entries = []
    for line in report.splitlines():
        if line.startswith("> "):
            entries.append({"step": line.removeprefix("> ")})
            continue
        if not entries:
            entries.append({"step": None})
        found = NOTE.fullmatch(line)
        key, value = found.groups() if found else ("note", line)
        if key == "chat" and value.startswith(STATE):
            entries[-1]["state"] = json.loads(Path(value.removeprefix(STATE)).read_text())
            continue
        entries[-1].setdefault(key, []).append(json.loads(value) if key in ("hud", "inspect") else value)
    return entries


def compare(step, timeout):
    names = step.split()[1:]
    if len(names) != 2:
        return {"step": step, "error": ["compare takes the names of two screenshots"]}
    first, second = (SCREENSHOTS / f"{name}.png" for name in names)
    difference = SCREENSHOTS / f"compare-{names[0]}-{names[1]}.png"
    result = subprocess.run(
        ["magick", "compare", "-metric", "AE", "-fuzz", "2%", str(first), str(second), str(difference)],
        capture_output=True,
        text=True,
    )
    if result.returncode > 1:
        return {"step": step, "error": [result.stderr.strip()]}
    width, height = subprocess.run(
        ["magick", "identify", "-format", "%w %h", str(first)], capture_output=True, check=True, text=True
    ).stdout.split()
    pixels = int(float(result.stderr.split()[0]))
    share = 100 * pixels / (int(width) * int(height))
    return {"step": step, "differing": f"{share:.2f}%", "pixels": pixels, "difference": str(difference)}


def views(step, timeout):
    name = step.removeprefix("views ").strip()
    shots, errors = sides(name, timeout)
    if not shots:
        return {"step": step, "error": errors}
    target = sheet(WORKSHOP_SHEETS / f"{name}.jpg", shots, "2x")
    return {"step": step, "sheet": str(target)} | ({"error": errors} if errors else {})


def sides(name, timeout, chosen=tuple(SIDES)):
    report = batch(["/wayfarer workshop bounds"], timeout)
    found = BOUNDS.search(report)
    if not found:
        return [], ["views needs something in the workshop"]
    low, high = [int(value) for value in found.groups()[:3]], [int(value) + 1 for value in found.groups()[3:]]
    centre = [(low[axis] + high[axis]) / 2 for axis in range(3)]
    corners = [[(low, high)[(index >> axis) & 1][axis] - centre[axis] for axis in range(3)] for index in range(8)]
    steps = ["gui off", "/gamemode spectator"]
    for label in chosen:
        direction = SIDES[label]
        distance = framed(direction, corners)
        length = math.hypot(*direction)
        eye = [centre[axis] + direction[axis] / length * distance for axis in range(3)]
        steps += view(f"workshop-{name}-{label}", (eye[0], eye[1] - EYE_HEIGHT, eye[2]), centre, WORKSHOP)
    entries = parse(batch([*steps, "/gamemode creative", "gui on"], timeout))
    errors = [error for entry in entries for error in entry.get("error", [])]
    return [(label, f"workshop-{name}-{label}") for label in chosen], errors


def framed(direction, corners):
    length = math.hypot(*direction)
    back = [component / length for component in direction]
    right = [back[2], 0.0, -back[0]]
    if math.hypot(*right) < 1e-3:
        right = [1.0, 0.0, 0.0]
    right = [component / math.hypot(*right) for component in right]
    up = [
        back[1] * right[2] - back[2] * right[1],
        back[2] * right[0] - back[0] * right[2],
        back[0] * right[1] - back[1] * right[0],
    ]
    tall = math.tan(math.radians(FIELD_OF_VIEW / 2))
    wide = tall * ASPECT
    needed = 1.0
    for corner in corners:
        sideways = abs(sum(corner[axis] * right[axis] for axis in range(3)))
        upwards = abs(sum(corner[axis] * up[axis] for axis in range(3)))
        nearer = sum(corner[axis] * back[axis] for axis in range(3))
        needed = max(needed, nearer + max(sideways / wide, upwards / tall) * FRAMING)
    return needed


def view(shot, at, look, world):
    eye = (at[0], at[1] + EYE_HEIGHT, at[2])
    dx, dy, dz = look[0] - eye[0], look[1] - eye[1], look[2] - eye[2]
    yaw = math.degrees(math.atan2(-dx, dz))
    pitch = -math.degrees(math.atan2(dy, math.hypot(dx, dz)))
    return [
        f"/execute in {world} run tp @s {at[0]:.2f} {at[1]:.2f} {at[2]:.2f} {yaw:.1f} {pitch:.1f}",
        "settle",
        f"shot {shot}",
    ]


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


def shrine_cameras(shrine, pedestal, surface):
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
    views = (
        ([centre[0] + ex * 13 + side[0] * 5, surface + 2, centre[2] + ez * 13 + side[1] * 5], centre),
        ([centre[0] - 12, surface + 16, centre[2] + 12], [centre[0], surface, centre[2]]),
        ([centre[0] + ex * 6, surface, centre[2] + ez * 6], [centre[0], surface, centre[2]]),
        ([shaft[0] + top_x, surface - 1, shaft[2] + top_z], [shaft[0] + down_x, surface - 6, shaft[2] + down_z]),
        (
            [shaft[0] + size - 2.5, floor, shaft[2] + 1.5],
            [shaft[0] + size - 2.5, floor + 5, shaft[2] + size - 2.5],
        ),
        ([shaft[0] + middle, floor, shaft[2] + 2.5], [shaft[0] + middle, floor + 1.5, shaft[2] - 6]),
    )
    cameras = dict(zip(SHAFT_CAMERAS, views, strict=True))
    for camera in shrine["cameras"]:
        cameras[camera["name"]] = (offset(origin, camera["at"]), offset(origin, camera["look"]))
    return cameras


def sheet(target, shots, tile):
    target.parent.mkdir(parents=True, exist_ok=True)
    font = subprocess.run(
        ["fc-match", "--format", "%{file}", "sans"], capture_output=True, check=True, text=True
    ).stdout
    arguments = ["-font", font, "-pointsize", "22", "-background", "#202020", "-fill", "white"]
    for label, shot in shots:
        arguments += ["-label", label, str(SCREENSHOTS / f"{shot}.png")]
    subprocess.run(
        [
            "magick",
            "montage",
            *arguments,
            "-geometry",
            "640x360+4+4",
            "-tile",
            tile,
            "-quality",
            "82",
            str(target),
        ],
        check=True,
    )
    return target


def tour(arguments):
    ensure("showroom", START_SECONDS, arguments.visible, "full")
    for subject, shots in photograph(arguments.subjects, arguments.timeout).items():
        print(sheet(SHEETS / f"{subject}.jpg", shots, "3x"))


def photograph(chosen, timeout):
    plan = json.loads(TOUR.read_text())
    surface = plan["surface"]
    shrines = [shrine for shrine in plan["shrines"] if not chosen or shrine["eye"] in chosen]
    locate = ["gui off", "/gamemode spectator"]
    for shrine in shrines:
        x, z = shrine["centre"]
        locate += [
            f"/execute in wayfarer:showroom run tp @s {x + 0.5} {surface + 10} {z + 0.5}",
            "settle",
            "find wayfarer:shrine_pedestal 16",
        ]
    found = [tuple(int(value) for value in match.groups()) for match in FOUND.finditer(batch(locate, timeout))]
    if len(found) != len(shrines):
        sys.exit(f"found {len(found)} pedestals for {len(shrines)} shrines; the showroom does not match the generator")
    subjects = {}
    for shrine, pedestal in zip(shrines, found, strict=True):
        subjects[shrine["eye"]] = shrine_cameras(shrine, pedestal, surface)
    if not chosen or "camp" in chosen:
        origin = plan["camp"]["origin"]
        subjects["camp"] = {
            camera["name"]: (offset(origin, camera["at"]), offset(origin, camera["look"]))
            for camera in plan["camp"]["cameras"]
        }
    steps = []
    for subject, cameras in subjects.items():
        for name, (at, look) in cameras.items():
            steps += view(f"tour-{subject}-{name}", at, look, "wayfarer:showroom")
    batch(steps + ["/gamemode creative", "gui on"], timeout)
    return {subject: [(name, f"tour-{subject}-{name}") for name in cameras] for subject, cameras in subjects.items()}


def panorama(arguments):
    ensure("play", START_SECONDS, False, "full", seed=PANORAMA_SEED)
    x, y, z, yaw = PANORAMA_CAMERA
    report = batch(
        [
            "gui off",
            "chat off",
            "/gamemode spectator",
            "/weather clear",
            f"/tp @s {x} {y} {z} {yaw} 0",
            "particles minimal",
            f"distance {PANORAMA_CHUNKS}",
            f"/time set {PANORAMA_TIME}",
            "/tick freeze",
            "settle",
            "panorama title",
            "/tick unfreeze",
            "/gamemode creative",
            "particles all",
            "chat on",
            "gui on",
        ],
        arguments.timeout,
    )
    errors = [error for entry in parse(report) for error in entry.get("error", [])]
    if errors:
        sys.exit(f"panorama failed: {'; '.join(errors)}")
    for face in range(PANORAMA_FACES):
        shutil.copyfile(SCREENSHOTS / f"panorama/title/panorama_{face}.png", PANORAMA / f"panorama_{face}.png")
    print(f"the title's panorama is in {PANORAMA.relative_to(ROOT)}")


def swap(arguments):
    if not alive():
        sys.exit("the client is not running; start it with: python3 scripts/client.py start")
    print(json.dumps(swap_in()))


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
    for pid_file in (GAME, LAUNCHED, LAUNCHER, WORLD):
        pid_file.unlink(missing_ok=True)
    (DRIVER / "quit").unlink(missing_ok=True)
    release_screen()
    print("the client has exited")


def main():
    parser = argparse.ArgumentParser(
        description="Drive the dev client: every command returns once the game has finished"
    )
    commands = parser.add_subparsers(dest="command", required=True)
    start_parser = commands.add_parser(
        "start",
        help="open the showroom, a fresh world or a state in the running client, swapping in changed code first, "
        "or launch it when none runs, its mods, data or screen changed, or a change needs a restart; returns once "
        "the world is open",
    )
    worlds = start_parser.add_mutually_exclusive_group()
    worlds.add_argument("--fresh", action="store_true", help="open a fresh natural world instead of the showroom")
    worlds.add_argument("--seed", type=int, help="open a fresh natural world grown from this seed")
    worlds.add_argument(
        "--state",
        metavar="QUEST/STEP",
        help="open the world at a step of the story, such as ash/the_door, built from story/states.toml and kept "
        "in build/states/",
    )
    start_parser.add_argument(
        "--rebuild",
        action="store_true",
        help="build the state anew instead of from the cache, after a change to code that builds an earlier step",
    )
    start_parser.add_argument("--timeout", type=int, default=START_SECONDS)
    start_parser.add_argument(
        "--visible", action="store_true", help="show the game on the user's screen instead of a hidden one"
    )
    modes = start_parser.add_mutually_exclusive_group()
    modes.add_argument(
        "--far",
        action="store_const",
        const="far",
        dest="mode",
        help="keep Distant Horizons' far view on, out to 1,024 blocks",
    )
    modes.add_argument(
        "--lean",
        action="store_const",
        const="lean",
        dest="mode",
        help="load only the Wayfarer mod and what it needs, in worlds of its own",
    )
    start_parser.set_defaults(mode="full")
    run_parser = commands.add_parser(
        "run", help="run driver steps (arguments, or one per line on stdin) and print their log"
    )
    run_parser.add_argument("steps", nargs="*")
    run_parser.add_argument("--timeout", type=int, default=RUN_SECONDS)
    commands.add_parser(
        "swap",
        help="compile the Wayfarer mod and swap every changed class into the running client; prints one line of JSON",
    )
    panorama_parser = commands.add_parser(
        "panorama",
        help="grow the panorama's world from its seed, photograph the title's panorama from its camera at dawn and "
        "write its six faces into the mod",
    )
    panorama_parser.add_argument("--timeout", type=int, default=START_SECONDS)
    stop_parser = commands.add_parser("stop", help="quit the client; returns once it has exited")
    stop_parser.add_argument("--timeout", type=int, default=STOP_SECONDS)
    tour_parser = commands.add_parser(
        "tour",
        help="open the showroom as start does, photograph every shrine and the Last Camp; "
        "prints one contact sheet per subject",
    )
    tour_parser.add_argument("subjects", nargs="*", help="eyes (and camp) to photograph; all when none are named")
    tour_parser.add_argument("--timeout", type=int, default=1800)
    tour_parser.add_argument(
        "--visible", action="store_true", help="show the game on the user's screen instead of a hidden one"
    )
    arguments = parser.parse_args()
    {"panorama": panorama, "run": run, "start": start, "stop": stop, "swap": swap, "tour": tour}[arguments.command](
        arguments
    )


if __name__ == "__main__":
    main()

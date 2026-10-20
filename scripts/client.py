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
from collections import Counter
from datetime import datetime
from pathlib import Path

import drive
import hotswap
import states
import tomllib
from drive import Failure, crashes, running

ROOT = Path(__file__).resolve().parent.parent
CLIENT = ROOT / "build/client"
DRIVER = CLIENT / "driver"
LAUNCHER = DRIVER / "launcher.pid"
GAME = DRIVER / "game.pid"
LAUNCHED = DRIVER / "launched.json"
WORLD = DRIVER / "world.json"
SAVE = DRIVER / "save.json"
SAVES = CLIENT / "saves"
CHECKPOINTS = CLIENT / "checkpoints"
SHOT_NAME = re.compile(r"t(\d{4})")
RUN_FOLDER = "%Y-%m-%d_%H-%M"
REBUILT = {"play", "play-lean", "showroom", "showroom-lean", "state", "state-lean"}
VIEW_WIDTH = 1280
VIEW_QUALITY = "85"
TICKS_PER_SECOND = 20
TICKS_PER_MINUTE = 60 * TICKS_PER_SECOND
SCREEN = DRIVER / "screen.pid"
SCREEN_DISPLAY = DRIVER / "screen-display"
SCREEN_LOG = DRIVER / "screen.log"
SNAPSHOT = DRIVER / "classes"
SCREEN_SECONDS = 20
SCREEN_SIZE = (1920, 1080)
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
NOW = "now: "
CHECKPOINT = "checkpoint: "
KEPT = "story: kept "
FAILED_AT_ONCE = "` failed at once: "
SIDES = {"front": (0, 0, 1), "side": (1, 0, 0), "above": (0, 1, 0.001), "three-quarters": (0.6, 1.0, 0.6)}
SCREENSHOTS = CLIENT / "screenshots"
FOUND = re.compile(r"found \d+ \S+ within \d+: (-?\d+) (-?\d+) (-?\d+)")
EYE_HEIGHT = 1.62
SHAFTS = {"ladder": 5, "spiral": 11, "switchback": 11}
SHAFT_CAMERAS = ("outside", "above", "entrance", "stair-top", "stair-bottom", "stair-door")
PANORAMA = ROOT / "mod/src/main/resources/assets/minecraft/textures/gui/title/background"
PANORAMA_CAMERA = (-114, 73, 182, 210)
PANORAMA_CHUNKS = 16
PANORAMA_FACES = 6
PANORAMA_SEED = 21
PANORAMA_SIZE = 2048
PANORAMA_SKY_TICKS = 32
PANORAMA_QUALITY = "85-98"
PANORAMA_TIMES = {"dawn": 23400, "dusk": 12600, "night": 13600, "noon": 6000, "sunset": 12000}
SOUTH, WEST, NORTH, EAST = (0, 1), (-1, 0), (0, -1), (1, 0)
BENCH_SEED = tomllib.loads((ROOT / "story/states.toml").read_text())["seed"]
BENCH_SECONDS = 32
BENCH_HEIGHT = 256
BENCH_PITCH = 32
BENCH_SPEED = 8
BENCH_HEADINGS = (-90, 180, 90, 0)
BENCH_SETTLED = (
    "/gamerule wayfarerStory false",
    "/gamemode creative",
    "/time set 6000",
    "/gamerule doDaylightCycle false",
    "/gamerule doWeatherCycle false",
    "vsync off",
)
BENCH_STEPS = (("low", "off"), ("medium", "off"), ("high", "on"), ("ultra", "on"))
MINIMUM_STEPS = (("low", "off"), ("medium", "off"))
MINIMUM_MHZ = 1100
MINIMUM_THREADS = 12
MINIMUM_MHZ_LIMIT = 1200
OTHERS_PERCENT = 5
DEV_SERVER = ROOT / "build/devserver"
DEV_SERVER_ADDRESS = "localhost:25566"
SERVER_FAR_VIEW = CLIENT / "Distant_Horizons_server_data"
PERF = re.compile(r"(\d+) fps on average over \d+ s, 1% low (\d+) fps, worst frame (\d+) ms")
STANDING = re.compile(r"(-?[\d.]+) -?[\d.]+ (-?[\d.]+) facing")


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
        if SCREEN.exists() and not running(SCREEN):
            sys.exit(f"the hidden screen (gamescope) stopped; its log {SCREEN_LOG}, launch log {LAUNCH_LOG}")
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


def screen(visible, size):
    environment = dict(os.environ)
    if visible:
        return environment
    if shutil.which("gamescope") is None:
        sys.exit("the hidden screen needs gamescope from the system (programs.gamescope.enable); run with --visible")
    SCREEN_DISPLAY.unlink(missing_ok=True)
    width, height = (str(side) for side in size)
    announce = (
        f'printf %s "$DISPLAY" > {SCREEN_DISPLAY}.tmp && mv {SCREEN_DISPLAY}.tmp {SCREEN_DISPLAY}; exec sleep infinity'
    )
    alone = {key: value for key, value in environment.items() if key not in ("DISPLAY", "WAYLAND_DISPLAY")}
    with SCREEN_LOG.open("w") as log:
        process = subprocess.Popen(
            ["gamescope", "--backend", "headless", "-W", width, "-H", height, "-w", width, "-h", height]
            + ["--", "sh", "-c", announce],
            env=alone,
            stdin=subprocess.DEVNULL,
            stdout=log,
            stderr=subprocess.STDOUT,
            start_new_session=True,
        )
    SCREEN.write_text(str(process.pid))
    deadline = time.monotonic() + SCREEN_SECONDS
    while not SCREEN_DISPLAY.exists() and process.poll() is None and time.monotonic() < deadline:
        time.sleep(CHECK_SECONDS)
    if not SCREEN_DISPLAY.exists():
        release_screen()
        last = SCREEN_LOG.read_text(errors="replace").strip().splitlines()[-1:] or ["no output"]
        sys.exit(f"the hidden screen (gamescope) did not start: {last[0]}; run with --visible to use the user's screen")
    environment.pop("WAYLAND_DISPLAY", None)
    return environment | {
        "DISPLAY": SCREEN_DISPLAY.read_text(),
        "WAYFARER_BACKGROUND": "true",
        "WAYFARER_SILENT": "true",
        "XDG_SESSION_TYPE": "x11",
    }


def games(folder=CLIENT):
    for process in Path("/proc").iterdir():
        if process.name.isdigit():
            try:
                if (process / "cwd").resolve() == folder:
                    yield int(process.name)
            except OSError:
                continue


def release_screen():
    if running(SCREEN):
        # gamescope 3.16 segfaults and dumps core when asked to stop, so the hidden screen is killed outright.
        os.kill(int(SCREEN.read_text()), signal.SIGKILL)
        deadline = time.monotonic() + STOP_SECONDS
        while running(SCREEN) and time.monotonic() < deadline:
            time.sleep(CHECK_SECONDS)
    for screen_file in (SCREEN, SCREEN_DISPLAY):
        screen_file.unlink(missing_ok=True)


def launch(world, timeout, visible, mode, state, rebuild, seed, processors=None, size=SCREEN_SIZE, save=None):
    DRIVER.mkdir(parents=True, exist_ok=True)
    for stale in ("abort", "commands.txt", "done.txt", "game.pid", "launched.json", "quit", "world.json"):
        (DRIVER / stale).unlink(missing_ok=True)
    release_screen()
    shutil.rmtree(SCREENSHOTS, ignore_errors=True)
    before = crashes(CRASHES)
    command = ["just", "showroom" if world == "showroom" else "play", mode]
    environment = screen(visible, size) | {
        "WAYFARER_DRIVEN": "true",
        "WAYFARER_SEED": "" if seed is None else str(seed),
        "WAYFARER_WORLD": save or "",
    }
    if processors:
        environment["WAYFARER_PROCESSORS"] = str(processors)
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
    record(states.digests(), mode, visible, size)
    say(world, state, start, f"launched in {time.monotonic() - started:.0f} s", visible, mode, report, save)


def built(state, start, report):
    if start == state:
        return "from the cache,"
    kept = report.count(KEPT)
    keeping = f", keeping {kept} earlier step{'s' * (kept != 1)}" if kept else ""
    return (f"built from {start}" if start else "built") + keeping + ","


def say(world, state, start, took, visible, mode, report, save=None):
    errors = [line for line in report.splitlines() if line.startswith("error:")]
    where = {
        "play": "a fresh natural world",
        "save": f"the world {save}",
        "showroom": "the showroom",
        "state": f"state {state}, " + built(state, start, report),
    }[world]
    screen = ("on the user's screen" if visible else "on a hidden screen") + {
        "far": ", with Distant Horizons' far view",
        "full": "",
        "lean": ", with only the Wayfarer mod",
        "shipped": ", with the far view and the shader as shipped",
    }[mode]
    stopped = [line for line in errors if FAILED_AT_ONCE in line]
    if stopped:
        sys.exit(f"opening {state or save or world} failed: {stopped[0].removeprefix('error: ')}")
    print(
        f"the client is running in {where} {screen} ({took}, {len(report.splitlines())} log lines, {len(errors)} errors)"
    )
    print("\n".join(errors))


def shown(visible, size):
    return None if visible else list(size)


def record(digests, mode, visible, size):
    LAUNCHED.write_text(
        json.dumps({"digests": digests, "mode": mode, "size": shown(visible, size), "visible": visible})
    )


def current():
    if not alive() or not LAUNCHED.exists() or not WORLD.exists():
        return None, None
    return json.loads(LAUNCHED.read_text()), json.loads(WORLD.read_text())


def relaunching(launched, digests, mode, visible, size):
    if launched is None:
        return ["none runs"]
    running_on = (launched["mode"], launched["visible"], launched.get("size"))
    reasons = [] if running_on == (mode, visible, shown(visible, size)) else ["the screen or mode is another"]
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


def ensure(
    world, timeout, visible, mode, state=None, rebuild=False, anew=False, seed=None, size=SCREEN_SIZE, save=None
):
    launched, opened = current()
    digests = states.digests()
    reasons = relaunching(launched, digests, mode, visible, size) or swapped()
    if reasons:
        if alive():
            print("relaunching: " + "; ".join(reasons))
            shut(STOP_SECONDS)
        launch(world, timeout, visible, mode, state, rebuild, seed, size=size, save=save)
        return
    reloaded = ""
    if states.changed(launched["digests"], digests, ("assets",)):
        batch(["assets"], timeout)
        reloaded = "textures, models, fonts, sounds and lang reloaded, "
    data = states.changed(launched["digests"], digests, ("data",))
    record(digests, mode, visible, size)
    if world == "showroom" and opened["world"] == "showroom" and not anew and not data:
        print("the client is running in the showroom" + (f" ({reloaded.removesuffix(', ')})" if reloaded else ""))
        return
    if world == "save" and opened == {"state": save, "world": "save"} and SAVE.exists() and not data:
        print(f"the client is running in the world {save}" + (f" ({reloaded.removesuffix(', ')})" if reloaded else ""))
        return
    switch(world, timeout, visible, mode, state, rebuild, seed, reloaded, save)


def switch(world, timeout, visible, mode, state, rebuild, seed, reloaded="", save=None):
    started = time.monotonic()
    batch(["leave"], timeout)
    start = None
    if save:
        target = f"save {save}"
    elif state:
        cache = states.folder("client", mode, state)
        start = states.prepare(cache, CLIENT / "saves" / ("state-lean" if mode == "lean" else "state"), rebuild)
        target = f"state {state} {cache}"
    else:
        subprocess.run(
            ["python3", "scripts/instance.py", "fresh", str(CLIENT), world, "--mode", mode], cwd=ROOT, check=True
        )
        target = world if seed is None else f"{world} {seed}"
    report = batch([f"enter {target}"], timeout)
    say(world, state, start, f"{reloaded}opened in {time.monotonic() - started:.0f} s", visible, mode, report, save)


def start(arguments):
    if arguments.title:
        launched, _ = current()
        if relaunching(launched, states.digests(), arguments.mode, arguments.visible, SCREEN_SIZE) or swapped():
            ensure("showroom", arguments.timeout, arguments.visible, arguments.mode)
        batch(["leave"], arguments.timeout)
        print("the client stands at the title screen")
        return
    if arguments.state:
        states.check(arguments.state)
    if arguments.world in REBUILT:
        sys.exit(
            f"{arguments.world} is the dev client's own world, made anew by its start mode; open a save of your own"
        )
    if arguments.world and not (SAVES / arguments.world / "level.dat").exists():
        saves = sorted(path.name for path in SAVES.iterdir() if (path / "level.dat").exists()) if SAVES.exists() else []
        sys.exit(f"no save {arguments.world}; the saves are: {', '.join(saves) or 'none'}")
    world = (
        "save"
        if arguments.world
        else "state"
        if arguments.state
        else "play"
        if arguments.fresh or arguments.seed is not None
        else "showroom"
    )
    ensure(
        world,
        arguments.timeout,
        arguments.visible,
        arguments.mode,
        arguments.state,
        arguments.rebuild,
        seed=arguments.seed,
        save=arguments.world,
    )


def batch(steps, timeout):
    if not alive():
        sys.exit("the client is not running; start it with: python3 scripts/client.py start")
    before = crashes(CRASHES)
    partial = DRIVER / "commands.tmp"
    partial.write_text("\n".join(steps) + "\n")
    partial.replace(DRIVER / "commands.txt")
    return "\n".join(kept(line) for line in wait_done(timeout, before).splitlines())


def kept(line):
    if not line.startswith(CHECKPOINT):
        return line
    folder, _, where = line.removeprefix(CHECKPOINT).partition(" | ")
    world = Path(folder)
    taken = drive.keep(world, CHECKPOINTS / world.name, where)
    return f"{CHECKPOINT}checkpoint {taken['number']}: {where}"


def rewind(step, timeout):
    words = step.split()
    back = int(words[1]) if len(words) > 1 else 1
    if not SAVE.exists():
        return {"step": step, "error": ["rewind failed: no world the client opened itself is open"]}
    world = Path(json.loads(SAVE.read_text())["folder"])
    kept_ones = drive.checkpoints(CHECKPOINTS / world.name)
    if len(kept_ones) < back:
        return {
            "step": step,
            "error": [f"rewind failed: {world.name} has {len(kept_ones)} checkpoint{'s' * (len(kept_ones) != 1)}"],
        }
    batch(["leave"], timeout)
    back_to = drive.rewound(world, kept_ones[-back])
    report = batch([f"enter save {world.name}", "hud"], timeout)
    errors = [line.removeprefix("error: ") for line in report.splitlines() if line.startswith("error:")]
    return {"step": step, "rewind": [back_to]} | ({"error": errors} if errors else {})


def run_begun(folder, world):
    try:
        begun = datetime.strptime(folder.name, RUN_FOLDER).astimezone()
    except ValueError:
        return {"begun": datetime.now().astimezone().isoformat(timespec="seconds"), "last": None, "ticks": 0}
    positions = Counter(event["player"] for event in drive.recorded_since(world, begun) if event["event"] == "position")
    every = tomllib.loads((ROOT / "balance.toml").read_text())["trail"]["position_seconds"] * TICKS_PER_SECOND
    return {
        "begun": begun.isoformat(timespec="seconds"),
        "last": None,
        "ticks": max(positions.values(), default=0) * every,
    }


def played(folder, clock):
    path = folder / "run.json"
    world = Path(json.loads(SAVE.read_text())["folder"])
    state = json.loads(path.read_text()) if path.exists() else run_begun(folder, world)
    if state["last"] is not None and clock >= state["last"]:
        state["ticks"] += clock - state["last"]
    state["last"] = clock
    path.write_text(json.dumps(state))
    events = drive.recorded_since(world, datetime.fromisoformat(state["begun"]))
    minutes = state["ticks"] // TICKS_PER_MINUTE
    return {
        "deaths": sum(event["event"] == "death" for event in events),
        "played": f"{minutes // 60} h {minutes % 60:02d} min" if minutes >= 60 else f"{minutes} min",
        "rewinds": sum(event["event"] == "rewound" for event in events),
    }


def next_shot(folder):
    numbers = [int(found.group(1)) for path in folder.glob("t*.png") if (found := SHOT_NAME.match(path.stem))]
    return f"t{max(numbers, default=0) + 1:04d}"


def run(arguments):
    steps = arguments.steps or [line for line in sys.stdin.read().splitlines() if line.strip()]
    local = {"compare": compare, "rewind": rewind, "views": views}
    pending = []
    closing = None
    if arguments.shots:
        arguments.shots.mkdir(parents=True, exist_ok=True)
        closing = next_shot(arguments.shots)
        steps = [*steps, f"shot {closing}"]

    def flush():
        if pending:
            entries = parse(batch(pending, arguments.timeout))
            pending.clear()
            report(entries)

    def report(entries):
        clocks = [int(clock) for entry in entries for clock in entry.pop("clock", [])]
        if closing and any(entry.get("step") == f"shot {closing}" for entry in entries):
            entries = [entry for entry in entries if entry.get("step") != f"shot {closing}"]
            if not entries or "step" in entries[-1]:
                entries.append({})
            if clocks and arguments.shots.name == "shots" and SAVE.exists():
                entries[-1]["run"] = played(arguments.shots.parent, clocks[-1])
            taken = SCREENSHOTS / f"{closing}.png"
            if taken.exists():
                shutil.copy2(taken, arguments.shots / taken.name)
                view = SCREENSHOTS / "view" / f"{closing}.jpg"
                view.parent.mkdir(exist_ok=True)
                subprocess.run(
                    ["magick", str(taken), "-resize", f"{VIEW_WIDTH}x", "-quality", VIEW_QUALITY, str(view)], check=True
                )
                entries[-1] |= {"screenshot": str(arguments.shots / taken.name), "view": str(view)}
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
        if line.startswith(NOW):
            entries.append({"seen": json.loads(line.removeprefix(NOW))})
            continue
        if not entries:
            entries.append({"step": None})
        found = NOTE.fullmatch(line)
        key, value = found.groups() if found else ("note", line)
        if key == "chat" and value.startswith(STATE):
            entries[-1]["state"] = json.loads(Path(value.removeprefix(STATE)).read_text())
            continue
        entries[-1].setdefault(key, []).append(json.loads(value) if key in ("hud", "inspect", "seen") else value)
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


def hall_cameras(hall, stone, surface):
    origin = [stone[axis] - hall["stone"][axis] for axis in range(3)]
    door_x, door_y, door_z = hall["door"]
    floor = origin[1] + door_y
    size = SHAFTS[hall["shaft"]]
    shaft = [origin[0] + door_x - size // 2, 0, origin[2] + door_z + 1]
    ex, ez = exit_of(hall["shaft"], surface - floor)
    (top_x, top_z), (down_x, down_z) = descent(hall["shaft"], (ex, ez))
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
    for camera in hall["cameras"]:
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
    halls = [hall for hall in plan["halls"] if not chosen or hall["eye"] in chosen]
    locate = ["gui off", "/gamemode spectator"]
    for hall in halls:
        x, z = hall["centre"]
        locate += [
            f"/execute in wayfarer:showroom run tp @s {x + 0.5} {surface + 10} {z + 0.5}",
            "settle",
            "find wayfarer:calling_stone 16",
        ]
    found = [tuple(int(value) for value in match.groups()) for match in FOUND.finditer(batch(locate, timeout))]
    if len(found) != len(halls):
        sys.exit(f"found {len(found)} stones for {len(halls)} halls; the showroom does not match the generator")
    subjects = {}
    for hall, stone in zip(halls, found, strict=True):
        subjects[hall["eye"]] = hall_cameras(hall, stone, surface)
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
    ensure("play", START_SECONDS, False, "shipped", seed=PANORAMA_SEED, size=(PANORAMA_SIZE, PANORAMA_SIZE))
    x, y, z, yaw = PANORAMA_CAMERA
    steps = [
        "graphics ultra shaders on",
        "gui off",
        "chat off",
        "/gamemode spectator",
        "/gamerule doWeatherCycle false",
        "/weather clear",
        f"/tp @s {x} {y} {z} {yaw} 0",
        "particles minimal",
        f"distance {PANORAMA_CHUNKS}",
        "settle",
        "far",
    ]
    for name, daytime in sorted(PANORAMA_TIMES.items()):
        steps += [
            "/tick unfreeze",
            "/weather clear",
            f"/time set {daytime} true",
            f"sky {PANORAMA_SKY_TICKS}",
            "/tick freeze",
            "settle",
            f"panorama {name}",
        ]
    steps += [
        "/tick unfreeze",
        "/gamerule doWeatherCycle true",
        "/gamemode creative",
        "particles all",
        "chat on",
        "gui on",
    ]
    report = batch(steps, arguments.timeout)
    errors = [error for entry in parse(report) for error in entry.get("error", [])]
    if errors:
        sys.exit(f"panorama failed: {'; '.join(errors)}")
    shutil.rmtree(PANORAMA, ignore_errors=True)
    for name in sorted(PANORAMA_TIMES):
        (PANORAMA / name).mkdir(parents=True)
        for face in range(PANORAMA_FACES):
            shot = SCREENSHOTS / f"panorama/{name}/panorama_{face}.png"
            kept = PANORAMA / name / f"panorama_{face}.png"
            quantize = ["pngquant", "--quality", PANORAMA_QUALITY, "--speed", "1", "--force", "--output", str(kept)]
            subprocess.run([*quantize, str(shot)], check=True)
    print(f"the title's panoramas ({', '.join(sorted(PANORAMA_TIMES))}) are in {PANORAMA.relative_to(ROOT)}")


def bench(arguments):
    minimum = arguments.minimum
    cpus = arguments.cpus or (slowest_cores() if minimum else None)
    if minimum and cpus is None:
        sys.exit("bench --minimum needs --cpus on a processor without slower cores")
    if minimum and shutil.which("nvidia-smi") is None:
        sys.exit("bench --minimum needs nvidia-smi to check the graphics card's clock")
    steps = MINIMUM_STEPS if minimum else BENCH_STEPS
    places = (("alone", []), ("server", [f"join {DEV_SERVER_ADDRESS}"])) if minimum else (("alone", []),)
    nvidia = shutil.which("nvidia-smi")
    locking = minimum and not at_pc()
    for ending in (signal.SIGHUP, signal.SIGTERM):
        signal.signal(ending, lambda number, frame: sys.exit(f"bench stopped by signal {number}"))
    if alive():
        shut(STOP_SECONDS)
    try:
        if locking:
            subprocess.run(
                ["sudo", "--non-interactive", nvidia, f"--lock-gpu-clocks={MINIMUM_MHZ},{MINIMUM_MHZ}"], check=True
            )
            print(f"nobody is at the PC: the graphics card is locked at {MINIMUM_MHZ:,} MHz until the bench ends")
        measure(minimum, cpus, steps, places, "/weather thunder" if arguments.storm else "/weather clear")
    finally:
        if locking:
            subprocess.run(["sudo", "--non-interactive", nvidia, "--reset-gpu-clocks"], check=False)
            print("the graphics card's clock is back to normal")
        elif minimum:
            print("to give the graphics card its speed back, run: sudo nvidia-smi --reset-gpu-clocks")


def at_pc():
    listed = subprocess.run(["loginctl", "list-sessions", "--json=short"], capture_output=True, text=True, check=True)
    for session in json.loads(listed.stdout):
        if session["class"] == "user" and not session["idle"]:
            kind = subprocess.run(
                ["loginctl", "show-session", session["session"], "--property=Type", "--value"],
                capture_output=True,
                text=True,
                check=True,
            )
            if kind.stdout.strip() in ("wayland", "x11"):
                return True
    return False


def measure(minimum, cpus, steps, places, weather):
    try:
        if minimum:
            others = sorted(set(range(os.cpu_count())) - set(expand(cpus)))
            subprocess.run(
                ["python3", "scripts/server.py", "start", "--far", "--seed", str(BENCH_SEED)],
                cwd=ROOT,
                env=os.environ | {"WAYFARER_PROCESSORS": str(len(others))},
                check=True,
            )
            for pid in games(DEV_SERVER):
                pin(pid, ",".join(map(str, others)))
            shutil.rmtree(SERVER_FAR_VIEW, ignore_errors=True)
            launch("play", START_SECONDS, False, "shipped", None, False, BENCH_SEED, MINIMUM_THREADS)
            pin(int(GAME.read_text()), cpus)
        else:
            launch("play", START_SECONDS, False, "shipped", None, False, BENCH_SEED)
        held(minimum)
        print(
            f"benchmarking as the minimum machine: i7-10750H as cores {cpus}, RTX 2070 Super as the graphics card at "
            f"{MINIMUM_MHZ:,} MHz, shader off"
            if minimum
            else "benchmarking this PC: every step, the shader on for high and ultra"
        )
        for where, joining in places:
            first, shaders = steps[0]
            report = batch(
                [*joining, *BENCH_SETTLED, weather, f"graphics {first} shaders {shaders}", "pos"], RUN_SECONDS
            )
            spawn = [float(value) for value in STANDING.search(report).groups()]
            print(opening(where, spawn), flush=True)
            for index, (step, shaders) in enumerate(steps):
                watch = subprocess.Popen(
                    ["nvidia-smi", "pmon", "--delay", "1", "--select", "u"], stdout=subprocess.PIPE, text=True
                )
                result = leg(step, shaders, spawn, BENCH_HEADINGS[index])
                watch.terminate()
                held(minimum)
                print(row(step, where, *result) + sharing(watch.communicate()[0]), flush=True)
    finally:
        if alive():
            shut(STOP_SECONDS)
        if minimum:
            subprocess.run(["python3", "scripts/server.py", "stop"], cwd=ROOT, check=False)


def sharing(watched):
    ours = {int(GAME.read_text())} if GAME.exists() else set()
    if SCREEN.exists():
        ours |= family(int(SCREEN.read_text()))
    samples = {}
    for line in watched.splitlines():
        fields = line.split()
        if line.startswith("#") or len(fields) < 5 or not fields[1].isdigit() or int(fields[1]) in ours:
            continue
        samples.setdefault(fields[-1], []).append(0 if fields[3] == "-" else int(fields[3]))
    busy = {name: sum(load) / len(load) for name, load in samples.items() if sum(load) / len(load) >= OTHERS_PERCENT}
    return (
        f"   others on the card: {', '.join(f'{name} {load:.0f}%' for name, load in sorted(busy.items()))}"
        if busy
        else ""
    )


def family(pid):
    found = {pid}
    for task in Path(f"/proc/{pid}/task").glob("*"):
        with contextlib.suppress(OSError):
            for child in (task / "children").read_text().split():
                found |= family(int(child))
    return found


def leg(step, shaders, spawn, heading):
    x, z = spawn
    steps = [
        f"graphics {step} shaders {shaders}",
        f"/tp @s {x:.1f} {BENCH_HEIGHT} {z:.1f} {heading} {BENCH_PITCH}",
        "fly on",
        f"perf {BENCH_SECONDS}",
        "pos",
        "hold key.forward on",
        f"perf {BENCH_SECONDS}",
        "hold key.forward off",
        "pos",
    ]
    entries = parse(batch(steps, RUN_SECONDS))
    errors = [error for entry in entries for error in entry.get("error", [])]
    if errors:
        sys.exit(f"bench failed at {step}: {'; '.join(errors)}")
    (start_x, start_z), (end_x, end_z) = (
        (float(value) for value in STANDING.search(entry["pos"][0]).groups()) for entry in entries if "pos" in entry
    )
    flown = math.hypot(end_x - start_x, end_z - start_z)
    if flown < BENCH_SECONDS * BENCH_SPEED:
        sys.exit(
            f"bench failed at {step}: the player flew {flown:.0f} blocks in {BENCH_SECONDS} s; something stood in the way"
        )
    return [PERF.search(entry["perf"][0]).groups() for entry in entries if "perf" in entry]


def opening(where, spawn):
    x, z = spawn
    report = batch(
        [f"/tp @s {x:.1f} {BENCH_HEIGHT} {z:.1f} 0 {BENCH_PITCH}", "fly on", f"perf {BENCH_SECONDS}"], RUN_SECONDS
    )
    fps, low, worst = PERF.search(report).groups()
    return f"{'opening':<7} {where:<7} hovering {fps:>4} fps, 1% low {low:>4}, worst {worst:>4} ms"


def row(step, where, hovering, flying):
    def frames(name, values):
        fps, low, worst = values
        return f"{name} {fps:>4} fps, 1% low {low:>4}, worst {worst:>4} ms"

    return f"{step:<7} {where:<7} {frames('hovering', hovering)}   {frames('flying', flying)}"


def slowest_cores():
    speeds = {}
    for cpu in Path("/sys/devices/system/cpu").glob("cpu[0-9]*"):
        top = cpu / "cpufreq/cpuinfo_max_freq"
        if top.exists():
            speeds[int(cpu.name.removeprefix("cpu"))] = int(top.read_text())
    if len(set(speeds.values())) < 2:
        return None
    slowest = min(speeds.values())
    return ",".join(str(cpu) for cpu in sorted(speeds) if speeds[cpu] == slowest)


def expand(cpus):
    for part in cpus.split(","):
        first, _, last = part.partition("-")
        yield from range(int(first), int(last or first) + 1)


def pin(pid, cpus):
    pinned = subprocess.run(
        ["taskset", "--all-tasks", "--pid", "--cpu-list", cpus, str(pid)], capture_output=True, text=True
    )
    if pinned.returncode != 0 and Path(f"/proc/{pid}").exists():
        sys.exit(f"bench failed: process {pid} cannot be held to cores {cpus}: {pinned.stderr.strip()}")


def gpu_clock():
    read = subprocess.run(
        ["nvidia-smi", "--query-gpu=clocks.gr", "--format=csv,noheader,nounits"], capture_output=True, text=True
    )
    return int(read.stdout.split()[0]) if read.returncode == 0 and read.stdout.strip() else 0


def held(minimum):
    if minimum and gpu_clock() > MINIMUM_MHZ_LIMIT:
        sys.exit(
            f"bench failed: the graphics card runs at {gpu_clock():,} MHz and someone is using the PC, so its clock is "
            f"theirs to lock; once they agree, run: sudo nvidia-smi --lock-gpu-clocks={MINIMUM_MHZ},{MINIMUM_MHZ}"
        )


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
        "--title", action="store_true", help="stand at the title screen, to make or load a world as a player does"
    )
    worlds.add_argument(
        "--state",
        metavar="QUEST/STEP",
        help="open the world at a step of the story, such as ash/the_door, built from story/states.toml and kept "
        "in build/states/",
    )
    worlds.add_argument(
        "--world",
        metavar="SAVE",
        help="open a save of the dev client by its folder name, such as a playthrough's world",
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
    modes.add_argument(
        "--shipped",
        action="store_const",
        const="shipped",
        dest="mode",
        help="play as the pack ships: the shader, the far view and 12 chunks, full screen",
    )
    start_parser.set_defaults(mode="full")
    run_parser = commands.add_parser(
        "run",
        help="run driver steps (arguments, or one per line on stdin) and print their log; 'rewind [n]' puts the world "
        "the client opened itself back as its newest checkpoint, or the nth newest, left it",
    )
    run_parser.add_argument("steps", nargs="*")
    run_parser.add_argument("--timeout", type=int, default=RUN_SECONDS)
    run_parser.add_argument(
        "--shots",
        type=Path,
        help="end the batch with a screenshot, numbered after the ones already in this folder and copied into it, "
        "such as a playthrough's shots/",
    )
    commands.add_parser(
        "swap",
        help="compile the Wayfarer mod and swap every changed class into the running client; prints one line of JSON",
    )
    panorama_parser = commands.add_parser(
        "panorama",
        help="grow the panorama's world from its seed, photograph the title's panoramas from its camera at each of their "
        "times of day on a hidden screen drawn by the graphics card, with the far view and the shader as shipped, and "
        "write their six faces into the mod",
    )
    panorama_parser.add_argument("--timeout", type=int, default=START_SECONDS)
    bench_parser = commands.add_parser(
        "bench",
        help="measure the frames of every graphics step on the hidden screen, with the far view as shipped: arriving "
        "somewhere new and flying over new land, alone and, with --minimum, on the dev server; prints one row per run",
    )
    bench_parser.add_argument(
        "--minimum",
        action="store_true",
        help="run as the minimum machine of design/production.md R1: the game believing it has 12 threads and held to the "
        "slower cores, the graphics card's clock locked to its speed (by the bench itself when nobody is at the PC), "
        "Low and Medium with the shader off",
    )
    bench_parser.add_argument("--cpus", help="the cores to hold the game to, as taskset lists them, such as 12-19")
    bench_parser.add_argument(
        "--storm",
        action="store_true",
        help="measure under a thunderstorm, where the particles are thickest, not a clear sky",
    )
    stop_parser = commands.add_parser("stop", help="quit the client; returns once it has exited")
    stop_parser.add_argument("--timeout", type=int, default=STOP_SECONDS)
    tour_parser = commands.add_parser(
        "tour",
        help="open the showroom as start does, photograph every hall and the Last Camp; "
        "prints one contact sheet per subject",
    )
    tour_parser.add_argument("subjects", nargs="*", help="eyes (and camp) to photograph; all when none are named")
    tour_parser.add_argument("--timeout", type=int, default=1800)
    tour_parser.add_argument(
        "--visible", action="store_true", help="show the game on the user's screen instead of a hidden one"
    )
    arguments = parser.parse_args()
    {"bench": bench, "panorama": panorama, "run": run, "start": start, "stop": stop, "swap": swap, "tour": tour}[
        arguments.command
    ](arguments)


if __name__ == "__main__":
    main()

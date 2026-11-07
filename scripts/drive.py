import contextlib
import fcntl
import json
import os
import re
import shutil
import signal
import subprocess
import time
import uuid
from datetime import datetime
from pathlib import Path

import tomllib

ROOT = Path(__file__).resolve().parent.parent
CHECK_SECONDS = 0.1
CLAIM = "session"
GAME_OVERHEAD_GIGABYTES = 4
SEAT_PORTS = 10
CHECKPOINT = "checkpoint.json"
KEPT_CHECKPOINTS = 16
NUMBERED = re.compile(r"\d{4}")
DAY = re.compile(r"day (\d+)")
FAILED = re.compile(r"^[^:<]+ failed: ")
READY = "]: Done ("
ANSI = re.compile(r"\x1b\[[0-9;]*m")
HEADER = re.compile(r"^\[\d")
REPLY = re.compile(r"\[minecraft/MinecraftServer\]: (.*)$")


class Failure(Exception):
    pass


class Seat:
    def __init__(self, kind, number, folder):
        self.kind = kind
        self.number = number
        self.folder = folder

    def offset(self):
        return SEAT_PORTS * self.number

    def owner(self):
        claim = self.folder / CLAIM
        return claim.read_text().strip() if claim.exists() else None


def seat(kind):
    session = os.environ.get("PI_SESSION_ID")
    own = ROOT / "build" / kind
    if not session:
        return Seat(kind, 0, own)
    base = ROOT / "build" / f"{kind}s"
    base.mkdir(parents=True, exist_ok=True)
    with locked(base / ".lock"):
        numbered = sorted(
            (int(folder.name), folder) for folder in base.iterdir() if folder.is_dir() and folder.name.isdigit()
        )
        for number, folder in numbered:
            if (folder / CLAIM).exists() and (folder / CLAIM).read_text().strip() == session:
                return Seat(kind, number, folder)
        for number, folder in numbered:
            if not playing(folder):
                (folder / CLAIM).write_text(session)
                return Seat(kind, number, folder)
        number = numbered[-1][0] + 1 if numbered else 1
        folder = base / str(number)
        folder.mkdir()
        (folder / CLAIM).write_text(session)
        return Seat(kind, number, folder)


def seats(kind):
    found = [Seat(kind, 0, ROOT / "build" / kind)]
    base = ROOT / "build" / f"{kind}s"
    if base.exists():
        found += [
            Seat(kind, int(folder.name), folder)
            for folder in sorted(base.iterdir())
            if folder.is_dir() and folder.name.isdigit()
        ]
    return found


def playing(folder):
    return any(running(folder / "driver" / name) for name in ("launcher.pid", "game.pid", "server.pid"))


@contextlib.contextmanager
def locked(path):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a") as handle:
        fcntl.flock(handle, fcntl.LOCK_EX)
        try:
            yield
        finally:
            fcntl.flock(handle, fcntl.LOCK_UN)


def room(kind):
    available = next(
        int(line.split()[1])
        for line in Path("/proc/meminfo").read_text().splitlines()
        if line.startswith("MemAvailable:")
    )
    heap = tomllib.loads((ROOT / "balance.toml").read_text())["heap"]["gigabytes"]
    needed = (heap + GAME_OVERHEAD_GIGABYTES) * 1024 * 1024
    if available >= needed:
        return
    others = [
        f"session {other.owner() or 'of your own'}'s {other.kind}"
        for other in seats("client") + seats("devserver")
        if playing(other.folder)
    ]
    raise Failure(
        f"{available // (1024 * 1024)} GB of memory free, a game needs {needed // (1024 * 1024)}; running: {', '.join(others) or 'nothing of the pack'}"
    )


def shared(seat_saves, store, kept_apart):
    if seat_saves.resolve() == store.resolve():
        return
    seat_saves.mkdir(parents=True, exist_ok=True)
    store.mkdir(parents=True, exist_ok=True)
    for world in list(seat_saves.iterdir()):
        if (
            world.is_dir()
            and not world.is_symlink()
            and world.name not in kept_apart
            and not opened(world)
            and not (store / world.name).exists()
        ):
            shutil.move(str(world), store / world.name)
            world.symlink_to(store / world.name)
    for world in store.iterdir():
        target = seat_saves / world.name
        if world.is_dir() and world.name not in kept_apart and not target.exists() and not target.is_symlink():
            target.symlink_to(world)
    (seat_saves.parent / "allowed_symlinks.txt").write_text(f"[prefix]{store.resolve()}/\n")


def borrowed(folder, store, world):
    target = folder / world
    if folder.resolve() == store.resolve() or target.exists() or target.is_symlink() or not (store / world).is_dir():
        return
    folder.mkdir(parents=True, exist_ok=True)
    target.symlink_to(store / world)
    (folder / "allowed_symlinks.txt").write_text(f"[prefix]{store.resolve()}/\n")


def opened(world):
    lock = world / "session.lock"
    if not lock.exists():
        return False
    with lock.open("a") as handle:
        try:
            fcntl.lockf(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError:
            return True
        fcntl.lockf(handle, fcntl.LOCK_UN)
    return False


def holder(world_name, kind):
    for other in seats(kind):
        if not playing(other.folder):
            continue
        launched = other.folder / "driver" / "launched.json"
        if launched.exists() and json.loads(launched.read_text()).get("world") == world_name:
            return other
        for name in ("save.json", "world.json"):
            record = other.folder / "driver" / name
            if record.exists() and world_name in record.read_text():
                return other
    return None


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


def crashes(folder):
    return set(folder.glob("*.txt")) if folder.exists() else set()


def reply(line):
    found = REPLY.search(ANSI.sub("", line))
    return found.group(1) if found else None


def left_out(relative):
    return (
        relative.parts[0] == "baritone"
        or relative.as_posix().startswith("wayfarer/record")
        or relative.name.startswith("DistantHorizons")
        or relative.name == "session.lock"
    )


def same(source, target):
    if not target.exists():
        return False
    first, second = source.stat(), target.stat()
    return first.st_size == second.st_size and first.st_mtime_ns == second.st_mtime_ns


def checkpoints(store):
    if not store.exists():
        return []
    return sorted(path for path in store.iterdir() if NUMBERED.fullmatch(path.name) and (path / CHECKPOINT).exists())


def described(checkpoint):
    return json.loads((checkpoint / CHECKPOINT).read_text())


def keep(world, store, where):
    kept = checkpoints(store)
    previous = kept[-1] if kept else None
    number = int(previous.name) + 1 if previous else 1
    partial = store / f"{number:04d}.tmp"
    shutil.rmtree(partial, ignore_errors=True)
    partial.mkdir(parents=True)
    for source in sorted(world.rglob("*")):
        relative = source.relative_to(world)
        if source.is_dir() or left_out(relative):
            continue
        copy = partial / relative
        copy.parent.mkdir(parents=True, exist_ok=True)
        before = previous / relative if previous else None
        with contextlib.suppress(FileNotFoundError):
            if before is not None and same(source, before):
                os.link(before, copy)
            else:
                shutil.copy2(source, copy)
    day = DAY.search(where)
    taken = datetime.now().astimezone().isoformat(timespec="seconds")
    (partial / CHECKPOINT).write_text(
        json.dumps({"day": int(day.group(1)) if day else 0, "number": number, "taken": taken, "where": where})
    )
    partial.rename(store / f"{number:04d}")
    prune(store)
    return described(store / f"{number:04d}")


def prune(store):
    kept = checkpoints(store)
    firsts = {}
    for checkpoint in kept:
        firsts.setdefault(described(checkpoint)["day"], checkpoint)
    staying = set(kept[-KEPT_CHECKPOINTS:]) | set(firsts.values())
    for checkpoint in kept:
        if checkpoint not in staying:
            shutil.rmtree(checkpoint)


def restore(checkpoint, world):
    wanted = set()
    for source in checkpoint.rglob("*"):
        relative = source.relative_to(checkpoint)
        if source.is_dir() or relative.as_posix() == CHECKPOINT:
            continue
        wanted.add(relative)
        target = world / relative
        if not same(source, target):
            target.parent.mkdir(parents=True, exist_ok=True)
            target.unlink(missing_ok=True)
            shutil.copy2(source, target)
    for target in list(world.rglob("*")):
        relative = target.relative_to(world)
        if target.is_file() and relative not in wanted and not left_out(relative):
            target.unlink()
    for later in checkpoints(checkpoint.parent):
        if int(later.name) > int(checkpoint.name):
            shutil.rmtree(later)


def recorded(world, event, fields):
    now = datetime.now().astimezone()
    folder = world / "wayfarer" / "record"
    folder.mkdir(parents=True, exist_ok=True)
    line = {"time": now.isoformat(timespec="milliseconds"), "event": event} | fields
    with (folder / f"{now.date().isoformat()}.jsonl").open("a") as record:
        record.write(json.dumps(line, separators=(",", ":")) + "\n")


def recorded_since(world, since):
    events = []
    for path in sorted((world / "wayfarer" / "record").glob("*.jsonl")):
        for line in path.read_text().splitlines():
            if line.strip():
                event = json.loads(line)
                if datetime.fromisoformat(event["time"]) >= since:
                    events.append(event)
    return events


def rewound(world, checkpoint):
    taken = described(checkpoint)
    restore(checkpoint, world)
    recorded(world, "rewound", {"checkpoint": taken["number"], "taken": taken["taken"], "to": taken["where"]})
    return (
        f"back to checkpoint {taken['number']}: {taken['where']}, kept at {taken['taken'][11:16]}; "
        "the record keeps what came after"
    )


class Server:
    def __init__(self, directory, command, cwd=None):
        self.directory = directory
        self.command = command
        self.cwd = cwd or directory
        self.driver = directory / "driver"
        self.console = self.driver / "console"
        self.pid = self.driver / "server.pid"
        self.log = self.driver / "server.log"
        self.position = 0
        self.reports = directory / "crash-reports"

    def alive(self):
        return running(self.pid)

    def lines(self):
        return self.log.read_text(errors="replace").splitlines() if self.log.exists() else []

    def start(self, timeout):
        self.driver.mkdir(parents=True, exist_ok=True)
        self.console.unlink(missing_ok=True)
        os.mkfifo(self.console)
        self.before = crashes(self.reports)
        console = os.open(self.console, os.O_RDWR)
        with self.log.open("w") as log:
            process = subprocess.Popen(
                self.command,
                cwd=self.cwd,
                stdin=console,
                stdout=log,
                stderr=subprocess.STDOUT,
                start_new_session=True,
            )
        os.close(console)
        self.pid.write_text(str(process.pid))
        try:
            self.wait(lambda line: True if READY in line else None, timeout, 0, "the server to finish starting")
        except Failure:
            self.kill()
            raise

    def attach(self):
        if not self.alive():
            raise Failure(f"the server in {self.directory} is not running")
        self.before = crashes(self.reports)

    def write(self, *commands):
        try:
            console = os.open(self.console, os.O_WRONLY | os.O_NONBLOCK)
        except OSError as error:
            raise Failure(f"the server's console is closed ({error}); it is not running") from error
        with os.fdopen(console, "w") as stream:
            stream.write("".join(command.removeprefix("/") + "\n" for command in commands))

    def size(self):
        return self.log.stat().st_size if self.log.exists() else 0

    def wait(self, match, timeout, offset, what):
        deadline = time.monotonic() + timeout
        pending = b""
        while True:
            with self.log.open("rb") as log:
                log.seek(offset)
                pending += log.read()
                offset = log.tell()
            start = offset - len(pending)
            *complete, pending = pending.split(b"\n")
            for line in complete:
                self.position = start
                start += len(line) + 1
                text = line.decode("utf-8", "replace")
                said = reply(text)
                if said is not None and FAILED.search(said):
                    raise Failure(f"failed at once while waiting for {what}: {said}")
                found = match(text)
                if found is not None:
                    return found
            new = sorted(crashes(self.reports) - self.before)
            if new:
                raise Failure(f"the server crashed; report in {new[0]}\n{new[0].read_text()[:4000]}")
            if not self.alive():
                tail = "\n".join(self.lines()[-40:])
                raise Failure(f"the server stopped while waiting for {what}; log in {self.log}\n{tail}")
            if time.monotonic() >= deadline:
                raise Failure(f"waited {timeout} s for {what} and gave up; log in {self.log}")
            time.sleep(CHECK_SECONDS)

    def run(self, command, timeout):
        token = f"wayfarer_done_{uuid.uuid4().hex[:12]}"
        mark = f"'{token}'"
        offset = self.size()
        self.write(command, f"scoreboard objectives remove {token}")
        replies = []
        continuing = False

        def match(line):
            nonlocal continuing
            if mark in line:
                return replies
            answer = reply(line)
            if answer is not None:
                replies.append(answer)
                continuing = True
            elif continuing and not HEADER.match(ANSI.sub("", line)):
                replies[-1] += "\n" + ANSI.sub("", line)
            else:
                continuing = False
            return None

        return self.wait(match, timeout, offset, f"the server to run {command!r}")

    def asks(self, questions, timeout):
        offset = self.size()
        self.write(*(command for command, _ in questions))
        answers = {}

        def match(line):
            said = reply(line)
            if said is not None:
                for index, (_, answer) in enumerate(questions):
                    found = None if index in answers else answer(said)
                    if found is not None:
                        answers[index] = found
                        break
            return answers if len(answers) == len(questions) else None

        self.wait(match, timeout, offset, f"answers to {len(questions)} commands")
        return [answers[index] for index in range(len(questions))]

    def stop(self, timeout):
        if not self.alive():
            return
        self.write("stop")
        deadline = time.monotonic() + timeout
        while self.alive() and time.monotonic() < deadline:
            time.sleep(CHECK_SECONDS)
        if self.alive():
            self.kill()
            raise Failure(f"the server did not stop within {timeout} s and was killed; log in {self.log}")

    def kill(self):
        if self.alive():
            with contextlib.suppress(ProcessLookupError):
                os.killpg(int(self.pid.read_text()), signal.SIGKILL)

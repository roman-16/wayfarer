import contextlib
import os
import re
import signal
import subprocess
import time
import uuid

CHECK_SECONDS = 0.1
READY = "]: Done ("
ANSI = re.compile(r"\x1b\[[0-9;]*m")
HEADER = re.compile(r"^\[\d")
REPLY = re.compile(r"\[minecraft/MinecraftServer\]: (.*)$")


class Failure(Exception):
    pass


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


class Server:
    def __init__(self, directory, command, cwd=None):
        self.directory = directory
        self.command = command
        self.cwd = cwd or directory
        self.driver = directory / "driver"
        self.console = self.driver / "console"
        self.pid = self.driver / "server.pid"
        self.log = self.driver / "server.log"
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
            *complete, pending = pending.split(b"\n")
            for line in complete:
                found = match(line.decode("utf-8", "replace"))
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

    def ask(self, command, answer, timeout):
        offset = self.size()
        self.write(command)

        def match(line):
            said = reply(line)
            return None if said is None else answer(said)

        return self.wait(match, timeout, offset, f"an answer to {command!r}")

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

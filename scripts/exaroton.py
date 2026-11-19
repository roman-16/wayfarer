import argparse
import base64
import contextlib
import json
import os
import re
import socket
import ssl
import struct
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime
from pathlib import Path

import tomllib

HOST = "api.exaroton.com"
AGENT = "wayfarer-exaroton"
ROOT = Path(__file__).resolve().parent.parent
RECORDINGS = ROOT / "exaroton"
FLOOR = tomllib.loads((ROOT / "balance.toml").read_text())["graphics"]
STREAMS = ("console", "heap", "stats", "tick")
STATUSES = {
    0: "offline",
    1: "online",
    2: "starting",
    3: "stopping",
    4: "restarting",
    5: "saving",
    6: "loading",
    7: "crashed",
    8: "pending",
    9: "transferring",
    10: "preparing",
}
OFFLINE = 0
ONLINE = 1
CRASHED = 7
POLL_SECONDS = 60
REQUEST_SECONDS = 30
RATE_TRIES = 8
TOO_MANY_REQUESTS = 429
TRANSFER_SECONDS = 600
RECORD = "wayfarer/record"
RECORD_FOLDER = "record"
CARRY = "carry.json"
CARRY_SUFFIX = "-carry"
NAMED = 2
DAY = "%d.%m"
LOG_SECONDS = 300
RECONNECT_SECONDS = 5
SILENCE_SECONDS = 90
CONTROL = re.compile(r"\x1b\[[0-9;?]*[A-Za-z]|[\x00-\x08\x0b-\x1f\x7f]")


class ApiError(Exception):
    pass


class Api:
    def __init__(self, token):
        self.token = token

    def open(self, method, path, body=None, headers=None, timeout=REQUEST_SECONDS):
        request = urllib.request.Request(
            f"https://{HOST}/v1/{urllib.parse.quote(path)}",
            data=body,
            method=method,
            headers={"Authorization": f"Bearer {self.token}", "User-Agent": AGENT} | (headers or {}),
        )
        for attempt in range(RATE_TRIES):
            try:
                with urllib.request.urlopen(request, timeout=timeout) as response:
                    return response.read()
            except urllib.error.HTTPError as error:
                if error.code != TOO_MANY_REQUESTS or attempt == RATE_TRIES - 1:
                    raise
                time.sleep(float(error.headers.get("Retry-After") or 2**attempt))
        raise ApiError(f"{method} {path}: no attempt left")

    def get(self, path):
        try:
            body = json.loads(self.open("GET", path))
        except urllib.error.HTTPError as error:
            body = json.load(error)
        if not body.get("success"):
            raise ApiError(f"{path}: {body.get('error')}")
        return body["data"]

    def data(self, path, timeout=REQUEST_SECONDS):
        try:
            return self.open("GET", path, timeout=timeout)
        except urllib.error.HTTPError as error:
            raise ApiError(f"{path}: HTTP {error.code}") from None

    def post(self, path, values):
        self.change("POST", path, json.dumps(values).encode(), {"Content-Type": "application/json"})

    def put(self, path, body, kind="application/octet-stream"):
        self.change("PUT", path, body, {"Content-Type": kind})

    def delete(self, path):
        self.change("DELETE", path, None, {})

    def change(self, method, path, body, headers):
        try:
            answer = json.loads(self.open(method, path, body, headers, TRANSFER_SECONDS))
        except urllib.error.HTTPError as error:
            said = error.read().decode("utf-8", "replace")
            with contextlib.suppress(ValueError):
                said = json.loads(said).get("error") or said
            raise ApiError(f"{method} {path}: HTTP {error.code}: {said}") from None
        if not answer.get("success"):
            raise ApiError(f"{method} {path}: {answer.get('error')}")


class WebSocket:
    def __init__(self, server, token):
        raw = socket.create_connection((HOST, 443), timeout=SILENCE_SECONDS)
        self.socket = ssl.create_default_context().wrap_socket(raw, server_hostname=HOST)
        self.buffer = b""
        key = base64.b64encode(os.urandom(16)).decode()
        self.socket.sendall(
            (
                f"GET /v1/servers/{server}/websocket HTTP/1.1\r\n"
                f"Host: {HOST}\r\n"
                "Upgrade: websocket\r\n"
                "Connection: Upgrade\r\n"
                f"Sec-WebSocket-Key: {key}\r\n"
                "Sec-WebSocket-Version: 13\r\n"
                f"Authorization: Bearer {token}\r\n"
                f"User-Agent: {AGENT}\r\n\r\n"
            ).encode()
        )
        while b"\r\n\r\n" not in self.buffer:
            self.fill()
        head, self.buffer = self.buffer.split(b"\r\n\r\n", 1)
        status = head.split(b"\r\n", 1)[0].decode()
        if " 101 " not in status:
            raise ConnectionError(f"websocket refused: {status}")

    def fill(self):
        chunk = self.socket.recv(65536)
        if not chunk:
            raise ConnectionError("websocket closed by exaroton")
        self.buffer += chunk

    def exact(self, count):
        while len(self.buffer) < count:
            self.fill()
        data, self.buffer = self.buffer[:count], self.buffer[count:]
        return data

    def send(self, payload, opcode=0x1):
        mask = os.urandom(4)
        length = len(payload)
        if length < 126:
            header = struct.pack(">BB", 0x80 | opcode, 0x80 | length)
        elif length < 65536:
            header = struct.pack(">BBH", 0x80 | opcode, 0x80 | 126, length)
        else:
            header = struct.pack(">BBQ", 0x80 | opcode, 0x80 | 127, length)
        self.socket.sendall(header + mask + bytes(byte ^ mask[index % 4] for index, byte in enumerate(payload)))

    def send_json(self, message):
        self.send(json.dumps(message).encode())

    def receive(self):
        message = b""
        pinged = False
        while True:
            try:
                first, second = self.exact(2)
            except TimeoutError:
                if pinged:
                    raise ConnectionError("websocket went silent") from None
                self.send(b"", 0x9)
                pinged = True
                continue
            pinged = False
            opcode = first & 0x0F
            length = second & 0x7F
            if length == 126:
                length = struct.unpack(">H", self.exact(2))[0]
            elif length == 127:
                length = struct.unpack(">Q", self.exact(8))[0]
            mask = self.exact(4) if second & 0x80 else b"\0\0\0\0"
            payload = bytes(byte ^ mask[index % 4] for index, byte in enumerate(self.exact(length)))
            if opcode == 0x8:
                raise ConnectionError("websocket closed by exaroton")
            if opcode == 0x9:
                self.send(payload, 0xA)
            elif opcode != 0xA:
                message += payload
                if first & 0x80:
                    return json.loads(message)

    def close(self):
        self.socket.close()


class Session:
    def __init__(self, folder):
        self.folder = folder
        self.lock = threading.Lock()
        self.status = None
        self.read = {}

    def append(self, name, line):
        with self.lock, open(self.folder / name, "a", encoding="utf-8") as file:
            file.write(line + "\n")

    def record(self, name, data):
        self.append(name, json.dumps({"time": now(), **data}, ensure_ascii=False))

    def write(self, name, text):
        path = self.folder / name
        partial = path.with_name(path.name + ".part")
        partial.write_text(text, encoding="utf-8")
        partial.replace(path)


def now():
    return datetime.now().astimezone().isoformat(timespec="milliseconds")


def say(text):
    print(f"{datetime.now():%H:%M:%S} {text}", flush=True)


def describe(status):
    return STATUSES.get(status, str(status))


def megabytes(size):
    return f"{size / 1_000_000:.0f} MB"


def options(api, server):
    return {
        option["key"]: option["value"] for option in api.get(f"servers/{server['id']}/files/config/server.properties/")
    }


def opened(values):
    return values.get("level-name") or "world"


def carries(server):
    found = []
    for folder in sorted(RECORDINGS.glob("*" + CARRY_SUFFIX)):
        if (folder / CARRY).exists():
            carried = json.loads((folder / CARRY).read_text())
            if carried["server"] == server["id"]:
                found.append((folder, carried))
    return found


def unopened(api, server, level):
    try:
        api.get(f"servers/{server['id']}/files/info/{level}/session.lock")
    except ApiError:
        return True
    return False


def carried_elsewhere(api, server, level):
    uploaded = [carried for _, carried in carries(server) if carried["uploaded"]]
    if not uploaded or uploaded[-1]["to"] == level:
        return None
    last = uploaded[-1]
    never = " and never opened" if unopened(api, server, last["to"]) else ""
    day = datetime.fromisoformat(last["uploaded"]).strftime(DAY)
    return f"{server['name']} opens {level}, but the road was carried to {last['to']} on {day}{never}"


def find_server(api, wanted):
    servers = api.get("servers/")
    chosen = [server for server in servers if wanted in (server["id"], server["name"])] if wanted else servers
    if len(chosen) != 1:
        sys.exit(f"name the server, one of: {', '.join(server['name'] for server in servers)}")
    return chosen[0]


def begin(api, server, session):
    path = f"servers/{server['id']}"
    properties = options(api, server)
    mods = api.get(f"{path}/files/info/mods/").get("children") or []
    session.write(
        "session.json",
        json.dumps(
            {
                "mods": sorted(
                    ({"name": mod["name"], "size": mod["size"]} for mod in mods), key=lambda mod: mod["name"]
                ),
                "properties": properties,
                "ram": api.get(f"{path}/options/ram/"),
                "server": server,
                "started": now(),
            },
            ensure_ascii=False,
            indent=2,
        ),
    )
    return opened(properties)


def fetch_log(api, server, session):
    try:
        content = api.get(f"servers/{server['id']}/logs/").get("content")
    except (ApiError, OSError) as error:
        session.record("polls.jsonl", {"error": str(error)})
        return
    if content:
        session.write("latest.log", content)


def fetch_record(api, server, session, level):
    path = f"servers/{server['id']}"
    try:
        days = api.get(f"{path}/files/info/{level}/{RECORD}/").get("children") or []
    except ApiError:
        return
    (session.folder / RECORD_FOLDER).mkdir(exist_ok=True)
    for day in days:
        try:
            text = api.data(f"{path}/files/data/{level}/{RECORD}/{day['name']}").decode("utf-8")
        except (ApiError, OSError) as error:
            session.record("polls.jsonl", {"error": str(error)})
            continue
        session.write(f"{RECORD_FOLDER}/{day['name']}", text)
        lines = text.splitlines()
        for line in lines[session.read.get(day["name"], 0) :]:
            event = json.loads(line)
            if event.get("event") == "frames" and (
                event["fps"] < FLOOR["floor_fps"] or event["low"] < FLOOR["floor_low_fps"]
            ):
                card = f", graphics card busy {event['gpu']}%" if "gpu" in event else ""
                card += ", on battery" if event.get("battery") else ""
                say(
                    f"{event['player']} fell under the floor: {event['fps']} fps on average, 1% low {event['low']} at "
                    f"{event['preset']}, shaders {'on' if event['shaders'] else 'off'}, far view {event['far_view']} chunks{card}"
                )
        session.read[day["name"]] = len(lines)


def listed(names, sizes):
    shown = ", ".join(f"{name} ({sizes[name]})" for name in names[:NAMED])
    more = f" and {len(names) - NAMED} more" if len(names) > NAMED else ""
    return shown + more


def lost(before, after, since):
    gone = sorted((name for name in before if name not in after), key=lambda name: -before[name])
    shrunk = sorted(
        (name for name in before if name in after and after[name] < before[name]),
        key=lambda name: after[name] - before[name],
    )
    found = {}
    if gone:
        found["region_files_gone"] = [{"name": name, "bytes": before[name]} for name in gone]
        say(
            f"region files gone since {since}: {listed(gone, {name: megabytes(before[name]) for name in gone})}, "
            f"{megabytes(sum(before[name] for name in gone))} in all"
        )
    if shrunk:
        found["region_files_shrunk"] = [{"name": name, "from": before[name], "to": after[name]} for name in shrunk]
        sizes = {name: f"{megabytes(before[name])} to {megabytes(after[name])}" for name in shrunk}
        say(
            f"region files smaller since {since}: {listed(shrunk, sizes)}, "
            f"{megabytes(sum(before[name] - after[name] for name in shrunk))} in all"
        )
    return found


def poll(api, server, session, level):
    path = f"servers/{server['id']}"
    since_log = LOG_SECONDS
    sizes = None
    since = None
    while True:
        snapshot = {}
        try:
            state = api.get(path)
            snapshot["status"] = state["status"]
            snapshot["players"] = state["players"]
            regions = {
                region["name"]: region["size"]
                for region in api.get(f"{path}/files/info/{level}/region/").get("children") or []
            }
            snapshot["region_files"] = len(regions)
            snapshot["empty_region_files"] = sum(1 for size in regions.values() if size == 0)
            snapshot["region_bytes"] = sum(regions.values())
            if sizes is not None:
                snapshot |= lost(sizes, regions, since)
            sizes = regions
            since = f"{datetime.now():%H:%M}"
            snapshot["distant_horizons_bytes"] = api.get(f"{path}/files/info/{level}/data/DistantHorizons.sqlite/")[
                "size"
            ]
        except (ApiError, OSError, KeyError) as error:
            snapshot["error"] = str(error)
        session.record("polls.jsonl", snapshot)
        if snapshot.get("status") not in (None, OFFLINE):
            fetch_record(api, server, session, level)
        if since_log >= LOG_SECONDS and snapshot.get("status") not in (None, OFFLINE):
            fetch_log(api, server, session)
            since_log = 0
        since_log += POLL_SECONDS
        time.sleep(POLL_SECONDS)


def start_streams(channel):
    for stream in STREAMS:
        channel.send_json({"stream": stream, "type": "start", **({"data": {"tail": 0}} if stream == "console" else {})})


def listen(api, server, session, token):
    channel = WebSocket(server["id"], token)
    try:
        while True:
            message = channel.receive()
            kind = message.get("type")
            stream = message.get("stream")
            if stream == "console" and kind == "line":
                session.append("console.log", CONTROL.sub("", message["data"]))
                continue
            session.record("streams.jsonl", message)
            if kind == "ready":
                session.status = api.get(f"servers/{server['id']}")["status"]
                say(f"status: {describe(session.status)}")
                if session.status == ONLINE:
                    start_streams(channel)
            elif kind == "started":
                say(f"{stream} stream started")
            elif kind == "disconnected":
                say(f"game server disconnected: {message.get('data')}")
            elif stream == "status" and kind == "status":
                status = message["data"]["status"]
                if status != session.status:
                    say(f"status: {describe(status)}")
                    if status == ONLINE:
                        start_streams(channel)
                    if status in (OFFLINE, CRASHED):
                        fetch_log(api, server, session)
                    session.status = status
    finally:
        channel.close()


def main():
    parser = argparse.ArgumentParser(
        description="Record an exaroton server's console, status, memory, tick time, log, world sizes and the Wayfarer "
        "mod's play record, with each player's frame rate, until interrupted"
    )
    parser.add_argument("server", nargs="?", help="server name or id; the account's only server when omitted")
    arguments = parser.parse_args()
    token = os.environ.get("EXAROTON_API_TOKEN")
    if not token:
        sys.exit("EXAROTON_API_TOKEN is not set: put it in .env, which devbox loads")
    api = Api(token)
    server = find_server(api, arguments.server)
    folder = RECORDINGS / datetime.now().strftime("%Y-%m-%d_%H-%M")
    folder.mkdir(parents=True, exist_ok=True)
    session = Session(folder)
    level = begin(api, server, session)
    say(f"recording {server['name']}, which opens {level}, into {folder.relative_to(ROOT)} - Escape or Ctrl+C stops it")
    warning = carried_elsewhere(api, server, level)
    if warning:
        say(warning)
    threading.Thread(target=poll, args=(api, server, session, level), daemon=True).start()
    while True:
        try:
            listen(api, server, session, token)
        except (ApiError, ConnectionError, OSError, ValueError) as error:
            say(f"{error}, reconnecting")
        time.sleep(RECONNECT_SECONDS)


if __name__ == "__main__":
    with contextlib.suppress(KeyboardInterrupt):
        main()

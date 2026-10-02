import re
import shutil
import socket
import struct
import subprocess
from pathlib import Path

from drive import Failure

ROOT = Path(__file__).resolve().parent.parent
MOD = ROOT / "mod"
CLASSES = [MOD / "build/classes/java/main", MOD / "build/classes/java/dev"]
HANDSHAKE = b"JDWP-Handshake"
HEADER = struct.Struct(">IIBBB")
REPLY = struct.Struct(">IIBH")
REPLIED = 0x80
VIRTUAL_MACHINE = 1
CLASSES_BY_SIGNATURE = 2
DISPOSE = 6
ID_SIZES = 7
REDEFINE_CLASSES = 18
STATIC = 0x0008
CONSTANT_SIZES = {
    3: 4,
    4: 4,
    5: 8,
    6: 8,
    7: 2,
    8: 2,
    9: 4,
    10: 4,
    11: 4,
    12: 4,
    15: 3,
    16: 2,
    17: 4,
    18: 4,
    19: 2,
    20: 2,
}
ERRORS = {
    60: "the class file is not valid",
    61: "the classes would inherit from each other",
    62: "the class fails verification",
    63: "adding a method is not supported by this Java runtime",
    64: "changing fields is not supported by this Java runtime",
    66: "changing what a class extends or implements is not supported",
    67: "deleting a method is not supported by this Java runtime",
    69: "the class name does not match",
    70: "changing a class's modifiers is not supported",
    71: "changing a method's modifiers is not supported",
}


def port(name):
    found = re.search(rf"(?m)^{name}=(\d+)$", (MOD / "gradle.properties").read_text())
    return int(found.group(1))


def classes():
    return {
        str(path.relative_to(root)): path.read_bytes()
        for root in CLASSES
        if root.exists()
        for path in root.rglob("*.class")
    }


def remember(snapshot):
    shutil.rmtree(snapshot, ignore_errors=True)
    for path, code in classes().items():
        keep(snapshot, path, code)


def keep(snapshot, path, code):
    target = snapshot / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(code)


def static_fields(code):
    count = struct.unpack_from(">H", code, 8)[0]
    offset, index, utf8 = 10, 1, {}
    while index < count:
        tag = code[offset]
        if tag == 1:
            length = struct.unpack_from(">H", code, offset + 1)[0]
            utf8[index] = code[offset + 3 : offset + 3 + length].decode("utf-8", "replace")
            offset += 3 + length
        else:
            offset += 1 + CONSTANT_SIZES[tag]
        index += 2 if tag in (5, 6) else 1
    interfaces = struct.unpack_from(">H", code, offset + 6)[0]
    offset += 8 + 2 * interfaces
    fields = struct.unpack_from(">H", code, offset)[0]
    offset += 2
    found = {}
    for _ in range(fields):
        access, name, descriptor, attributes = struct.unpack_from(">HHHH", code, offset)
        offset += 8
        constant = False
        for _ in range(attributes):
            attribute, length = struct.unpack_from(">HI", code, offset)
            constant |= utf8.get(attribute) == "ConstantValue"
            offset += 6 + length
        if access & STATIC:
            found[utf8[name] + " " + utf8[descriptor]] = constant
    return found


def needs_initialiser(old, new):
    before = static_fields(old)
    return any(not constant for field, constant in static_fields(new).items() if field not in before)


class Debugger:
    def __init__(self, port):
        try:
            self.connection = socket.create_connection(("127.0.0.1", port), timeout=30)
        except OSError as error:
            raise Failure(
                f"nothing listens for code swaps on port {port}; was the game started by this driver?"
            ) from error
        self.connection.sendall(HANDSHAKE)
        if self.receive(len(HANDSHAKE)) != HANDSHAKE:
            raise Failure(f"port {port} does not speak the Java debug protocol")
        self.next = 1
        self.reference = struct.unpack(">5i", self.command(ID_SIZES, b""))[3]

    def receive(self, size):
        data = b""
        while len(data) < size:
            chunk = self.connection.recv(size - len(data))
            if not chunk:
                raise Failure("the game closed the debug connection")
            data += chunk
        return data

    def command(self, command, data):
        sent = self.next
        self.next += 1
        self.connection.sendall(HEADER.pack(HEADER.size + len(data), sent, 0, VIRTUAL_MACHINE, command) + data)
        while True:
            length, answered, flags, error = REPLY.unpack(self.receive(REPLY.size))
            body = self.receive(length - REPLY.size)
            if flags & REPLIED and answered == sent:
                if error:
                    raise Failure(f"the game refused the swap: {ERRORS.get(error, f'debug protocol error {error}')}")
                return body

    def loaded(self, name):
        signature = f"L{name};".encode()
        body = self.command(CLASSES_BY_SIGNATURE, struct.pack(">I", len(signature)) + signature)
        count = struct.unpack(">I", body[:4])[0]
        step = 1 + self.reference + 4
        return [body[4 + index * step + 1 : 4 + index * step + 1 + self.reference] for index in range(count)]

    def redefine(self, definitions):
        data = struct.pack(">I", len(definitions))
        for reference, code in definitions:
            data += reference + struct.pack(">I", len(code)) + code
        self.command(REDEFINE_CLASSES, data)

    def close(self):
        self.command(DISPOSE, b"")
        self.connection.close()


def swap(port, snapshot):
    if not snapshot.exists():
        raise Failure("there is no snapshot of the classes the game started with; start it with this driver")
    compiled = subprocess.run(["just", "compile"], cwd=ROOT, capture_output=True, text=True)
    if compiled.returncode != 0:
        raise Failure(f"the code does not compile:\n{(compiled.stdout + compiled.stderr)[-4000:]}")
    before = {str(path.relative_to(snapshot)): path.read_bytes() for path in snapshot.rglob("*.class")}
    now = classes()
    changed = sorted(path for path, code in now.items() if path in before and before[path] != code)
    restart = [path for path in changed if "/mixin/" in path or needs_initialiser(before[path], now[path])]
    debugger = Debugger(port)
    definitions, unloaded = [], []
    try:
        for path in (path for path in changed if path not in restart):
            references = debugger.loaded(path.removesuffix(".class"))
            definitions += [(reference, now[path]) for reference in references]
            if not references:
                unloaded.append(path)
        if definitions:
            debugger.redefine(definitions)
    finally:
        debugger.close()
    for path, code in now.items():
        if path not in restart:
            keep(snapshot, path, code)
    swapped = sorted({dotted(path) for path in changed if path not in restart and path not in unloaded})
    return {
        "swapped": swapped,
        "loaded when first used": [dotted(path) for path in unloaded],
        "new": [dotted(path) for path in sorted(set(now) - set(before))],
        "needs a restart": [dotted(path) for path in restart],
    }


def dotted(path):
    return path.removesuffix(".class").replace("/", ".")

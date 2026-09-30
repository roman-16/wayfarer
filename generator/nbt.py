import gzip
import struct
from dataclasses import dataclass


@dataclass(frozen=True)
class Byte:
    value: int


@dataclass(frozen=True)
class Float:
    value: float


END, BYTE, INT, FLOAT, STRING, LIST, COMPOUND = 0, 1, 3, 5, 8, 9, 10


def kind(value):
    if isinstance(value, (Byte, bool)):
        return BYTE
    if isinstance(value, int):
        return INT
    if isinstance(value, Float):
        return FLOAT
    if isinstance(value, str):
        return STRING
    if isinstance(value, list):
        return LIST
    if isinstance(value, dict):
        return COMPOUND
    raise TypeError(f"no NBT tag for {value!r}")


def string(text):
    data = text.encode("utf-8")
    return struct.pack(">H", len(data)) + data


def payload(value):
    tag = kind(value)
    if tag == BYTE:
        return struct.pack(">b", int(value.value if isinstance(value, Byte) else value))
    if tag == INT:
        return struct.pack(">i", value)
    if tag == FLOAT:
        return struct.pack(">f", value.value)
    if tag == STRING:
        return string(value)
    if tag == LIST:
        inner = kind(value[0]) if value else END
        return struct.pack(">bi", inner, len(value)) + b"".join(payload(item) for item in value)
    body = b"".join(struct.pack(">b", kind(item)) + string(key) + payload(item) for key, item in value.items())
    return body + struct.pack(">b", END)


def write(path, root):
    data = struct.pack(">b", COMPOUND) + string("") + payload(root)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(gzip.compress(data, mtime=0))

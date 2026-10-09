import argparse
import base64
import hashlib
import json
import subprocess
import tempfile
import threading
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

MODEL = "qwen/qwen3.8-omni-flash"
ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
BITRATE = "64k"
TIMEOUT_SECONDS = 300
BRIEF = """You are the sound designer's ear for Wayfarer, a story-driven Minecraft 1.21.1 modpack played by one to four friends.
The player wakes at a dying stranger's fire, receives his cold ember (a coal that warms and glows as they travel), and walks a vast,
sparse world from camp to camp with a caravan of survivors, searching for twelve shards of a dragon's eye. The tone is quiet,
melancholy folk fantasy: firelight, wind, old roads, a few people, and at night creatures of the dragon's gaze that crumble at dawn.
Everyday music is a Celtic folk-fantasy songpack (harp, strings, flute); the story's own pieces are strings and soft guitar.
The pack's sounds are its own, new to the player, and must sound like one coherent game beside Minecraft's and its mods' sounds.
A rare story moment may be heard and felt; a sound heard dozens of times must stay soft and never grate.
The clips are labelled in the order they come. Clips marked "in the pack" are already chosen; use them to judge how a new one fits beside them.
Clips marked "rejected" were turned down by the player for the reason given; never recommend what they were rejected for.
Judge only what you actually hear; if a clip is silent or you cannot hear it, say so."""


def token():
    command = ["pi", "auth", "print-bearer-token", "--provider", "openrouter"]
    return subprocess.run(command, capture_output=True, text=True, check=True).stdout.strip()


ENCODED = {}
ENCODING = threading.Lock()


def mp3(path):
    with ENCODING:
        source = Path(path).resolve()
        if source not in ENCODED:
            name = hashlib.sha256(str(source).encode()).hexdigest()[:16]
            out = Path(tempfile.gettempdir()) / f"wayfarer-hear-{name}.mp3"
            subprocess.run(
                [
                    "ffmpeg",
                    "-hide_banner",
                    "-loglevel",
                    "error",
                    "-y",
                    "-i",
                    str(source),
                    "-ac",
                    "1",
                    "-b:a",
                    BITRATE,
                    str(out),
                ],
                check=True,
            )
            ENCODED[source] = out.read_bytes()
        return ENCODED[source]


def hear(question, clips, brief=True):
    content = [{"type": "text", "text": BRIEF}] if brief else []
    for label, path in clips:
        audio = base64.b64encode(mp3(path)).decode()
        content.append({"type": "text", "text": f"Clip: {label}"})
        content.append({"type": "input_audio", "input_audio": {"data": audio, "format": "mp3"}})
    content.append({"type": "text", "text": question})
    body = {"model": MODEL, "messages": [{"role": "user", "content": content}]}
    headers = {"Authorization": "Bearer " + token(), "Content-Type": "application/json"}
    request = urllib.request.Request(ENDPOINT, data=json.dumps(body).encode(), headers=headers)
    try:
        reply = json.load(urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS))
    except urllib.error.HTTPError as error:
        raise RuntimeError(f"hear failed: {error.code} {error.read().decode(errors='replace')[:400]}") from error
    return reply["choices"][0]["message"]["content"]


def answer(question, clips, brief):
    try:
        return hear(question, clips, brief)
    except RuntimeError as error:
        return str(error)


def hear_each(question, context, candidates, brief=True):
    with ThreadPoolExecutor(max_workers=len(candidates)) as pool:
        replies = pool.map(lambda candidate: answer(question, [*context, candidate], brief), candidates)
        return list(zip((label for label, _ in candidates), replies, strict=True))


def clip(argument):
    label, separator, path = argument.rpartition("=")
    if separator and Path(path).is_file():
        return label, path
    return Path(argument).stem, argument


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Ask the audio model about clips; a clip is <file> or <label>=<file>, labelled with its file name when no label is given"
    )
    parser.add_argument("question")
    parser.add_argument("clips", nargs="+", type=clip)
    parser.add_argument(
        "--context", action="append", default=[], type=clip, help="a clip sent with every call, before the others"
    )
    parser.add_argument(
        "--each", action="store_true", help="one call per clip, all at once, each with every --context clip"
    )
    parser.add_argument("--plain", action="store_true", help="leave the pack's brief out")
    arguments = parser.parse_args()
    if arguments.each:
        for label, reply in hear_each(
            arguments.question, arguments.context, arguments.clips, brief=not arguments.plain
        ):
            print(f"=== {label}\n{reply}\n")
    else:
        print(hear(arguments.question, [*arguments.context, *arguments.clips], brief=not arguments.plain))

import argparse
import base64
import hashlib
import json
import re
import statistics
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
CALLS = 6
LOUDNESS = "loudnorm=I=-20:TP=-2"
RETRIES = 2
TIMEOUT_SECONDS = 300
UNHEARD = re.compile(r"cannot hear|can't hear|unable to hear|no audible|not audible|inaudible", re.IGNORECASE)
SCORE = re.compile(r"SCORE:\s*\**\s*([0-9]+(?:\.[0-9]+)?)")
BRIEF = """You are the sound designer's ear for Wayfarer, a story-driven Minecraft 1.21.1 modpack played by one to four friends.
The player wakes at a dying stranger's fire, receives his cold ember (a coal that warms and glows as they travel), and walks a vast,
sparse world from camp to camp with a caravan of survivors, searching for twelve shards of a dragon's eye. The tone is quiet,
melancholy folk fantasy: firelight, wind, old roads, a few people, and at night creatures of the dragon's gaze that crumble at dawn.
Everyday music is a Celtic folk-fantasy songpack (harp, strings, flute); the story's own pieces are strings and soft guitar.
The pack's sounds are its own, new to the player, and must sound like one coherent game beside Minecraft's and its mods' sounds.
A rare story moment may be heard and felt; a sound heard dozens of times must stay soft and never grate.
You hear one clip at a time. What else plays in the pack, and what the player turned down, is told to you in words; never recommend what they were rejected for.
Judge only what you actually hear in this clip; if it is silent or you cannot hear it, say only CANNOT HEAR."""


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
                    "-af",
                    LOUDNESS,
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


def hear(question, clip, brief=True):
    label, path = clip
    content = [{"type": "text", "text": BRIEF}] if brief else []
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
    return reply["choices"][0]["message"]["content"] or ""


def answer(question, clip, brief):
    for _ in range(RETRIES + 1):
        try:
            reply = hear(question, clip, brief)
        except RuntimeError as error:
            reply = str(error)
        if reply and not UNHEARD.search(reply) and not reply.startswith("hear failed"):
            return reply
    return reply


def hear_each(question, clips, tries=1, brief=True):
    asked = [clip for clip in clips for _ in range(tries)]
    with ThreadPoolExecutor(max_workers=min(CALLS, len(asked))) as pool:
        replies = list(pool.map(lambda clip: answer(question, clip, brief), asked))
    return [(label, replies[index * tries : (index + 1) * tries]) for index, (label, _) in enumerate(clips)]


def scored(replies):
    scores = [float(found.group(1)) for found in map(SCORE.search, replies) if found]
    return f"median {statistics.median(scores):g} of {', '.join(f'{score:g}' for score in scores)}" if scores else ""


def clip(argument):
    label, separator, path = argument.rpartition("=")
    if separator and Path(path).is_file():
        return label, path
    return Path(argument).stem, argument


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Ask the audio model about clips, each in its own call, at most six at a time; a clip is <file> or <label>=<file>, labelled with its file name when no label is given"
    )
    parser.add_argument("question")
    parser.add_argument("clips", nargs="+", type=clip)
    parser.add_argument(
        "--tries",
        type=int,
        default=1,
        help="ask about every clip this many times, and print the median of their scores",
    )
    parser.add_argument("--plain", action="store_true", help="leave the pack's brief out")
    arguments = parser.parse_args()
    for label, replies in hear_each(arguments.question, arguments.clips, arguments.tries, brief=not arguments.plain):
        print(f"=== {label}")
        for number, reply in enumerate(replies, start=1):
            print(f"--- try {number}\n{reply}\n" if len(replies) > 1 else f"{reply}\n")
        if summary := scored(replies):
            print(f"SCORES: {summary}\n")

import base64
import json
import subprocess
import sys
import tempfile
import urllib.request
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
Judge only what you actually hear; if a clip is silent or you cannot hear it, say so."""


def token():
    command = ["pi", "auth", "print-bearer-token", "--provider", "openrouter"]
    return subprocess.run(command, capture_output=True, text=True, check=True).stdout.strip()


def mp3(path):
    out = Path(tempfile.gettempdir()) / f"wayfarer-hear-{Path(path).stem}.mp3"
    subprocess.run(
        ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(path), "-ac", "1", "-b:a", BITRATE, str(out)],
        check=True,
    )
    return out


def hear(question, clips, brief=True):
    content = [{"type": "text", "text": BRIEF}] if brief else []
    for label, path in clips:
        audio = base64.b64encode(mp3(path).read_bytes()).decode()
        content.append({"type": "text", "text": f"Clip: {label}"})
        content.append({"type": "input_audio", "input_audio": {"data": audio, "format": "mp3"}})
    content.append({"type": "text", "text": question})
    body = {"model": MODEL, "messages": [{"role": "user", "content": content}]}
    headers = {"Authorization": "Bearer " + token(), "Content-Type": "application/json"}
    request = urllib.request.Request(ENDPOINT, data=json.dumps(body).encode(), headers=headers)
    reply = json.load(urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS))
    return reply["choices"][0]["message"]["content"]


if __name__ == "__main__":
    arguments = sys.argv[1:]
    plain = "--plain" in arguments
    arguments = [argument for argument in arguments if argument != "--plain"]
    if len(arguments) < 2:
        raise SystemExit(
            'usage: hear.py [--plain] "<question>" <file> [file ...]; each clip is labelled with its file name'
        )
    print(hear(arguments[0], [(Path(path).stem, path) for path in arguments[1:]], brief=not plain))

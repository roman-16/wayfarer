import argparse
import base64
import difflib
import hashlib
import json
import os
import random
import re
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from datetime import date, datetime
from pathlib import Path

import sounds
import spoken

ROOT = Path(__file__).resolve().parent.parent
KEPT = ROOT / "story/voices"
WORK = ROOT / "build/voices"
API = "https://api.elevenlabs.io"
PREFIX = "Wayfarer: "
NAMED = re.compile(r"^Wayfarer: (\w+) \((\w+)\)$")
PREVIEW = re.compile(r"^(?:like-)?\d+-\d$")
FORMAT = "mp3_44100_128"
LIKE_MODEL = "eleven_ttv_v3"
PREVIEW_TEXT = (100, 1000)
AT_ONCE = 3
LIKE_SECONDS = 64
SEEDS = 2**31
GAP_SECONDS = 0.5
TIMEOUT_SECONDS = 300
SHOWN = 64
CREDITS_PER_CHARACTER = 0.4
TRIM = (
    "silenceremove=start_periods=1:start_threshold=-50dB:start_silence=0.05,areverse,"
    "silenceremove=start_periods=1:start_threshold=-50dB:start_silence=0.1,areverse"
)


class Refused(Exception):
    def __init__(self, message):
        super().__init__(message)
        self.message = message


def fail(what, why):
    sys.exit(f"{what} failed: {why}")


def reason(body):
    try:
        detail = json.loads(body).get("detail", body.decode())
    except (ValueError, AttributeError):
        return body.decode(errors="replace")[: SHOWN * 4]
    if isinstance(detail, dict):
        return detail.get("message") or json.dumps(detail)
    if isinstance(detail, list):
        return "; ".join(item.get("msg", str(item)) for item in detail)
    return str(detail)


def call(method, path, body=None, query=None, audio=False):
    key = os.environ.get("ELEVENLABS_API_KEY")
    if not key:
        raise Refused("ELEVENLABS_API_KEY is not set: put it in .env, which devbox loads")
    url = API + path + ("?" + urllib.parse.urlencode(query) if query else "")
    request = urllib.request.Request(
        url,
        data=None if body is None else json.dumps(body).encode(),
        method=method,
        headers={"Content-Type": "application/json", "User-Agent": "roman-16/wayfarer", "xi-api-key": key},
    )
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
            payload = response.read()
            if audio:
                return payload, response.headers.get("request-id")
            return json.loads(payload) if payload else {}
    except urllib.error.HTTPError as error:
        raise Refused(reason(error.read())) from None
    except urllib.error.URLError as error:
        raise Refused(str(error.reason)) from None


def subscription():
    try:
        return call("GET", "/v1/user/subscription"), None
    except Refused as refused:
        return None, refused.message


def credits_left(plan):
    return plan["character_limit"] - plan["character_count"]


def headline(plan, why):
    if plan is None:
        return f"ElevenLabs: credits unknown ({why})"
    reset = plan.get("next_character_count_reset_unix")
    until = f" until {datetime.fromtimestamp(reset).strftime('%d.%m.%Y')}" if reset else ""
    changes = plan.get("max_voice_add_edits")
    used = f"; voice changes {plan['voice_add_edit_counter']} of {changes} used this cycle" if changes else ""
    return (
        f"ElevenLabs {plan['tier']}: {credits_left(plan):,} of {plan['character_limit']:,} credits left{until}; "
        f"voice slots {plan['voice_slots_used']} of {plan['voice_limit']}{used}"
    )


def slots():
    plan, _ = subscription()
    return f"; voice slots {plan['voice_slots_used']} of {plan['voice_limit']}" if plan else ""


def history_spent(since):
    total, query = 0, {"page_size": 100}
    while True:
        page = call("GET", "/v1/history", query=query)
        items = page.get("history", [])
        for item in items:
            if item.get("date_unix", 0) < since:
                return total
            total += (item.get("character_count_change_to") or 0) - (item.get("character_count_change_from") or 0)
        if not page.get("has_more") or not items:
            return total
        query["start_after_history_item_id"] = page["last_history_item_id"]


def rate():
    path = WORK / "rate.json"
    return json.loads(path.read_text())["credits_per_character"] if path.exists() else CREDITS_PER_CHARACTER


def spent(since, characters):
    try:
        used = history_spent(since)
    except Refused:
        used = None
    after, why = subscription()
    left = f", {credits_left(after):,} left" if after else f"; credits left unknown ({why})"
    if used is None:
        return f"sent {characters:,} characters{left}"
    if characters:
        write_json(WORK / "rate.json", {"credits_per_character": used / characters})
    return f"spent {used:,} credits on {characters:,} characters{left}"


def account():
    found = {}
    query = {"page_size": 100, "search": PREFIX.strip(), "voice_type": "non-default"}
    while True:
        page = call("GET", "/v2/voices", query=query)
        found |= {voice["name"]: voice for voice in page.get("voices", []) if NAMED.match(voice["name"])}
        if not page.get("has_more"):
            return found
        query["next_page_token"] = page["next_page_token"]


def saved_as(found, voice):
    return {name: entry for name, entry in found.items() if NAMED.match(name).group(1) == voice}


def name(voice, record):
    return f"{PREFIX}{voice} ({record['id']})"


def kept(voice):
    path = KEPT / f"{voice}.json"
    return json.loads(path.read_text()) if path.exists() else None


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False, sort_keys=True) + "\n")


def counted(number, word):
    return f"{number:,} {word}{'' if number == 1 else 's'}"


def short(text, width=SHOWN):
    return text if len(text) <= width else text[: width - 4] + " ..."


def ffmpeg(*arguments):
    subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", *map(str, arguments)], check=True)


def pitched(semitones):
    if not semitones:
        return []
    rate = 2 ** (semitones / 12)
    return [f"asetrate={sounds.SAMPLE_RATE}*{rate:.6f}", f"aresample={sounds.SAMPLE_RATE}", f"atempo={1 / rate:.6f}"]


def design_id(record, reference):
    content = json.dumps({"preview": record["preview"], "request": record["request"]}, sort_keys=True).encode()
    return hashlib.sha256(content + (reference or b"")).hexdigest()[:8]


def voice_spec(config, voice):
    if voice not in config["voices"]:
        fail("voice", f"story/voices.toml has no voice {voice}; it has {', '.join(sorted(config['voices']))}")
    return config["voices"][voice]


def like_reference(voice, folder):
    done = spoken.recordings()
    record = kept(voice)
    clips = [
        spoken.clip_path(key)
        for key, entry in sorted(done.items())
        if entry["voice"] == voice and entry["design"] == record["id"] and spoken.clip_path(key).exists()
    ]
    chosen, seconds = [], 0.0
    for clip in clips:
        if seconds >= LIKE_SECONDS:
            break
        chosen.append(clip)
        seconds += sounds.duration(clip)
    out = folder / "reference.mp3"
    inputs = [KEPT / f"{voice}.mp3", *chosen]
    arguments = [part for clip in inputs for part in ("-i", clip)]
    joined = "".join(f"[{index}:a]" for index in range(len(inputs)))
    ffmpeg(*arguments, "-filter_complex", f"{joined}concat=n={len(inputs)}:v=0:a=1,aformat=channel_layouts=mono", out)
    return out, seconds


def design(voice, count, like):
    config = spoken.voices()
    spec = voice_spec(config, voice)
    if "description" not in spec or "text" not in spec:
        fail("design", f"{voice} has no description and preview text in story/voices.toml; write them first")
    if not PREVIEW_TEXT[0] <= len(spec["text"]) <= PREVIEW_TEXT[1]:
        fail("design", f"{voice}'s preview text is {len(spec['text'])} characters; ElevenLabs needs 100 to 1,000")
    settings = config["design"] | spec.get("design", {})
    folder = WORK / voice
    folder.mkdir(parents=True, exist_ok=True)
    reference = None
    if like:
        if kept(voice) is None:
            fail("design", f"{voice} has no kept design, so there is nothing to be like")
        reference, seconds = like_reference(voice, folder)
        model = LIKE_MODEL
        print(
            f"{voice}: {counted(count, 'design')} of 3 previews each, from its description, "
            f"story/voices/{voice}.mp3 and {seconds:.0f} s of its clips as reference audio ({model})"
        )
    else:
        model = settings["model"]
        print(
            f"{voice}: {counted(count, 'design')} of 3 previews each, from its description "
            f"and {len(spec['text'])} characters of preview text ({model})"
        )
    for _ in range(count):
        seed = random.randrange(SEEDS)
        request = {
            "auto_generate_text": False,
            "guidance_scale": settings["guidance"],
            "loudness": settings["loudness"],
            "model_id": model,
            "seed": seed,
            "should_enhance": False,
            "text": spec["text"],
            "voice_description": spec["description"],
        }
        stem = f"like-{seed}" if like else str(seed)
        body = dict(request)
        if like:
            request["prompt_strength"] = body["prompt_strength"] = settings["like_strength"]
            body["reference_audio_base64"] = base64.b64encode(reference.read_bytes()).decode()
            shutil.copy(reference, folder / f"{stem}.reference.mp3")
        try:
            response = call("POST", "/v1/text-to-voice/design", body=body, query={"output_format": FORMAT})
        except Refused as refused:
            fail("design", refused.message)
        previews = []
        for number, preview in enumerate(response["previews"], start=1):
            path = folder / f"{stem}-{number}.mp3"
            path.write_bytes(base64.b64decode(preview["audio_base_64"]))
            previews.append({"file": path.name, "generated_voice_id": preview["generated_voice_id"]})
            print(f"  {path.relative_to(ROOT)}  {preview['duration_secs']:.1f} s")
        reference_name = f"{stem}.reference.mp3" if like else None
        write_json(folder / f"{stem}.json", {"previews": previews, "reference": reference_name, "request": request})
    plan, why = subscription()
    left = f"{credits_left(plan):,} credits left" if plan else f"credits left unknown ({why})"
    print(f"sent {count * len(spec['text']):,} characters of preview text; {left}")


def candidate(voice, preview):
    folder = WORK / voice
    stem, _, number = preview.rpartition("-")
    meta_path = folder / f"{stem}.json"
    if not PREVIEW.match(preview) or not meta_path.exists() or not (folder / f"{preview}.mp3").exists():
        at_hand = sorted(path.stem for path in folder.glob("*.mp3") if PREVIEW.match(path.stem))
        fail(
            "voice",
            f"{voice} has no preview {preview}; at hand: {', '.join(at_hand) or 'none, just voice design ' + voice}",
        )
    meta = json.loads(meta_path.read_text())
    record = {"preview": int(number), "request": meta["request"]}
    reference = folder / meta["reference"] if meta["reference"] else None
    if reference:
        record["reference"] = f"{voice}.reference.mp3"
    record["id"] = design_id(record, reference.read_bytes() if reference else None)
    return record, reference, meta["previews"][int(number) - 1]["generated_voice_id"]


def keep(voice, preview):
    voice_spec(spoken.voices(), voice)
    record, reference, generated = candidate(voice, preview)
    KEPT.mkdir(parents=True, exist_ok=True)
    shutil.copy(WORK / voice / f"{preview}.mp3", KEPT / f"{voice}.mp3")
    kept_reference = KEPT / f"{voice}.reference.mp3"
    if reference:
        shutil.copy(reference, kept_reference)
    else:
        kept_reference.unlink(missing_ok=True)
    write_json(
        KEPT / f"{voice}.json", record | {"generated_voice_id": generated, "kept": date.today().strftime("%d.%m.%Y")}
    )
    earlier = sum(
        1 for entry in spoken.recordings().values() if entry["voice"] == voice and entry["design"] != record["id"]
    )
    print(
        f"{voice}: kept as story/voices/{voice}.mp3, the request that made it (seed {record['request']['seed']}) "
        f"in story/voices/{voice}.json"
    )
    if earlier:
        print(
            f"{voice}: {counted(earlier, 'clip')} by an earlier design kept unchanged; just voice record {voice} --again records them anew"
        )


def create(voice, record, generated):
    body = {
        "generated_voice_id": generated,
        "voice_description": record["request"]["voice_description"],
        "voice_name": name(voice, record),
    }
    return call("POST", "/v1/text-to-voice", body=body)


def speak(voice_id, text, settings):
    seed = random.randrange(SEEDS)
    body = {
        "inputs": [{"text": text, "voice_id": voice_id}],
        "model_id": settings["model"],
        "seed": seed,
        "settings": {"similarity": settings["similarity"], "stability": settings["stability"]},
    }
    audio, request = call("POST", "/v1/text-to-dialogue", body=body, query={"output_format": FORMAT}, audio=True)
    return audio, request, seed


def audition(voice, previews):
    config = spoken.voices()
    pitch = voice_spec(config, voice).get("pitch", 0)
    settings = config["record"]
    chosen = [(preview, *candidate(voice, preview)) for preview in previews]
    try:
        found = account()
    except Refused as refused:
        fail("try", refused.message)
    since = time.time()
    characters = 0
    for preview, record, _, generated in chosen:
        wanted = name(voice, record)
        try:
            if wanted not in found:
                found[wanted] = {"voice_id": create(voice, record, generated)["voice_id"]}
            audio, _, _ = speak(found[wanted]["voice_id"], record["request"]["text"], settings)
        except Refused as refused:
            fail("try", refused.message)
        out = WORK / voice / f"{preview}.{settings['model']}.mp3"
        out.write_bytes(audio)
        if pitch:
            raised = out.with_suffix(".pitched.mp3")
            ffmpeg("-i", out, "-af", ",".join(pitched(pitch)), raised)
            raised.replace(out)
        characters += len(record["request"]["text"])
        print(f'  {out.relative_to(ROOT)}  {sounds.duration(out):.1f} s, saved as "{wanted}"')
    print(f"{spent(since, characters)}{slots()}")
    print(
        f"{voice}: just voice keep {voice} <preview>, then just voice drop {voice}, which deletes the three from the account"
    )


def rebuild(voice, record):
    request = dict(record["request"])
    if "reference" in record:
        request["reference_audio_base64"] = base64.b64encode((KEPT / record["reference"]).read_bytes()).decode()
    print(
        f"{voice}: not in this account; designing it again from seed {record['request']['seed']} "
        f"({len(record['request']['text'])} characters)"
    )
    try:
        response = call("POST", "/v1/text-to-voice/design", body=request, query={"output_format": FORMAT})
    except Refused as refused:
        fail("save", refused.message)
    preview = response["previews"][record["preview"] - 1]
    rebuilt = WORK / voice / "rebuilt.mp3"
    rebuilt.parent.mkdir(parents=True, exist_ok=True)
    rebuilt.write_bytes(base64.b64decode(preview["audio_base_64"]))
    write_json(WORK / voice / "rebuilt.json", {"generated_voice_id": preview["generated_voice_id"], "id": record["id"]})
    print(f"{voice}: a new take of the same request; nothing saved yet")
    print(f"  hear {rebuilt.relative_to(ROOT)} beside story/voices/{voice}.mp3")
    print(f"  the same voice: just voice save {voice} --rebuilt; too far off: just voice design {voice} --like")


def save(voice, rebuilt):
    voice_spec(spoken.voices(), voice)
    record = kept(voice)
    if record is None:
        fail("save", f"{voice} has no kept design: just voice design {voice}, then just voice keep {voice} <preview>")
    wanted = name(voice, record)
    try:
        found = account()
    except Refused as refused:
        fail("save", refused.message)
    mine = saved_as(found, voice)
    others = [entry for entry_name, entry in mine.items() if entry_name != wanted]
    plan, _ = subscription()
    if wanted not in mine and plan and plan["voice_slots_used"] >= plan["voice_limit"] and not others:
        taken = ", ".join(sorted(found)) or "none of them the pack's"
        fail(
            "save",
            f"every voice slot is taken ({plan['voice_slots_used']} of {plan['voice_limit']}): {taken}; just voice drop <voice> frees one",
        )
    for entry in others:
        call("DELETE", f"/v1/voices/{entry['voice_id']}")
        print(f'{voice}: deleted "{entry["name"]}", a design it does not keep')
    if wanted in mine:
        print(f'{voice}: saved as "{wanted}"{slots()}')
        return
    if rebuilt:
        take = WORK / voice / "rebuilt.json"
        cached = json.loads(take.read_text()) if take.exists() else {}
        if cached.get("id") != record["id"]:
            fail("save", f"{voice} has no rebuilt take of its kept design: just voice save {voice} designs one")
    else:
        cached = record
    if "generated_voice_id" in cached:
        try:
            create(voice, record, cached["generated_voice_id"])
            how = "its take rebuilt from the seed" if rebuilt else "its kept preview"
            print(f'{voice}: saved as "{wanted}" from {how}{slots()}')
            return
        except Refused as refused:
            print(f"{voice}: that take can no longer be saved ({refused.message})")
    rebuild(voice, record)


def drop(voice):
    voice_spec(spoken.voices(), voice)
    try:
        mine = saved_as(account(), voice)
    except Refused as refused:
        fail("drop", refused.message)
    if not mine:
        print(f"{voice}: not in this account; nothing to drop")
        return
    for entry in mine.values():
        try:
            call("DELETE", f"/v1/voices/{entry['voice_id']}")
        except Refused as refused:
            fail("drop", refused.message)
    clips = sum(1 for entry in spoken.recordings().values() if entry["voice"] == voice)
    names = ", ".join(f'"{entry_name}"' for entry_name in sorted(mine))
    print(
        f"{voice}: deleted {names} from the account; the repository keeps its recipe, kept preview and {counted(clips, 'clip')}{slots()}"
    )


def select(config, lines, selectors):
    groups = list(dict.fromkeys(line.group for line in lines))
    picked, scenes = [], []
    for selector in selectors:
        group, _, number = selector.rpartition(":")
        if selector in config["voices"]:
            reads = set(config["voices"][selector]["reads"])
            picked += [line for line in lines if line.by in reads]
        elif selector in groups:
            picked += [line for line in lines if line.group == selector]
            scenes.append(selector)
        elif (
            group in groups
            and number.isdigit()
            and any(line.group == group and line.number == int(number) for line in lines)
        ):
            picked += [line for line in lines if line.group == group and line.number == int(number)]
            scenes.append(group)
        else:
            fail(
                "record",
                f"no voice, group or line is called {selector}; voices: {', '.join(sorted(config['voices']))}; "
                f"groups: {', '.join(groups)}; a line is <group>:<number>",
            )
    return list(dict.fromkeys(picked)), list(dict.fromkeys(scenes))


def scene(config, lines, group):
    first = next(iter(sorted(spoken.chosen(config).items())), (None, None))[1]
    takes = [
        take
        for take in spoken.takes(config, [line for line in lines if line.group == group and line.by != "journal"])
        if take.line.by != "player" or take.voice == first
    ]
    clips = [spoken.clip_path(take.key) for take in takes]
    clips = [clip for clip in dict.fromkeys(clips) if clip.exists()]
    if not clips:
        return None
    out = WORK / "scenes" / f"{group.replace('/', '-')}.ogg"
    out.parent.mkdir(parents=True, exist_ok=True)
    rate = config["record"]["sample_rate"]
    with tempfile.TemporaryDirectory() as temporary:
        gap = Path(temporary) / "gap.ogg"
        ffmpeg("-f", "lavfi", "-i", f"anullsrc=r={rate}:cl=mono", "-t", GAP_SECONDS, "-c:a", "libvorbis", gap)
        listing = Path(temporary) / "list.txt"
        listing.write_text("".join(f"file '{clip}'\nfile '{gap}'\n" for clip in clips))
        ffmpeg("-f", "concat", "-safe", "0", "-i", listing, "-c:a", "libvorbis", "-q:a", sounds.QUALITY, out)
    minutes, seconds = divmod(round(sounds.duration(out)), 60)
    return f"{out.relative_to(ROOT)} ({minutes} min {seconds} s)"


UNITS = [
    "zero",
    "one",
    "two",
    "three",
    "four",
    "five",
    "six",
    "seven",
    "eight",
    "nine",
    "ten",
    "eleven",
    "twelve",
    "thirteen",
    "fourteen",
    "fifteen",
    "sixteen",
    "seventeen",
    "eighteen",
    "nineteen",
]
TENS = ["twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"]


def spelled(number):
    if number < len(UNITS):
        return UNITS[number]
    if number < 100:
        return TENS[number // 10 - 2] + ("" if number % 10 == 0 else UNITS[number % 10])
    if number < 1000:
        return UNITS[number // 100] + "hundred" + ("" if number % 100 == 0 else spelled(number % 100))
    return str(number)


def letters(text):
    text = re.sub(r"\d+", lambda found: spelled(int(found.group())), text.lower())
    return re.sub(r"[^a-z]", "", text)


def words(text):
    return [
        word.strip("'") for word in re.findall(r"[a-z0-9']+", text.lower().replace("\u2019", "'")) if word.strip("'")
    ]


def transcribe(path, config):
    boundary = f"----wayfarer{random.randrange(SEEDS)}"
    fields = {"language_code": "en", "model_id": config["check"]["model"], "tag_audio_events": "false"}
    body = b"".join(
        f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode()
        for key, value in fields.items()
    )
    body += (
        f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{path.name}"\r\n'
        f"Content-Type: audio/wav\r\n\r\n"
    ).encode()
    body += path.read_bytes() + f"\r\n--{boundary}--\r\n".encode()
    request = urllib.request.Request(
        API + "/v1/speech-to-text",
        data=body,
        method="POST",
        headers={
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "User-Agent": "roman-16/wayfarer",
            "xi-api-key": os.environ.get("ELEVENLABS_API_KEY", ""),
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
            return json.loads(response.read())["text"]
    except urllib.error.HTTPError as error:
        raise Refused(reason(error.read())) from None
    except urllib.error.URLError as error:
        raise Refused(str(error.reason)) from None


def heard_as(line, heard):
    said, got = words(line.shown), words(heard)
    match = difflib.SequenceMatcher(None, said, got, autojunk=False)
    extra = [
        word for kind, _, _, low, high in match.get_opcodes() if kind in ("insert", "replace") for word in got[low:high]
    ]
    spoken_tags = sorted({word for tag in spoken.tags(line.text) for word in words(tag)} & set(extra) - set(said))
    return difflib.SequenceMatcher(None, letters(line.shown), letters(heard), autojunk=False).ratio(), spoken_tags


def render(source, out, filters, rate, target):
    with tempfile.TemporaryDirectory() as temporary:
        shaped = Path(temporary) / "shaped.wav"
        ffmpeg("-i", source, "-filter_complex", f"[0:a]{filters}", "-ac", 1, "-ar", rate, shaped)
        sounds.loudness(shaped, target, out, rate)


def gaze_copy(config, key):
    rate = config["record"]["sample_rate"]
    render(
        spoken.clip_path(key),
        spoken.clip_path(key + spoken.GAZE),
        config["gaze"]["filter"].format(rate=rate),
        rate,
        sounds.TARGETS["voice"],
    )


def take_one(take, config, voice_id, design, pitch):
    settings, check = config["record"], config["check"]
    rate = settings["sample_rate"]
    out = spoken.clip_path(take.key)
    out.parent.mkdir(parents=True, exist_ok=True)
    best = None
    with tempfile.TemporaryDirectory() as temporary:
        for attempt in range(1, check["takes"] + 1):
            audio, request, seed = speak(voice_id, take.text, settings)
            raw, trimmed = Path(temporary) / "raw.mp3", Path(temporary) / f"trimmed-{attempt}.wav"
            raw.write_bytes(audio)
            ffmpeg("-i", raw, "-af", ",".join([*pitched(pitch), TRIM]), "-ac", 1, "-ar", rate, trimmed)
            heard = transcribe(trimmed, config)
            match, tagged = heard_as(take.line, heard)
            good = match >= check["least_match"] and not tagged
            if best is None or (good, match) > (best[0], best[1]):
                best = (good, match, tagged, heard, trimmed, request, seed, attempt)
            if good:
                break
        good, match, tagged, heard, trimmed, request, seed, attempts = best
        sounds.loudness(trimmed, sounds.TARGETS["voice"], out, rate)
    entry = {
        "check": {"heard": heard, "match": round(match, 3), "takes": attempts},
        "design": design,
        "model": settings["model"],
        "pitch": pitch,
        "recorded": date.today().strftime("%d.%m.%Y"),
        "request": request,
        "seconds": round(sounds.duration(out), 2),
        "seed": seed,
        "sent": take.text,
        "settings": {"similarity": settings["similarity"], "stability": settings["stability"]},
        "text": take.line.text,
        "voice": take.voice,
    }
    if spoken.gazed(config, take):
        gaze_copy(config, take.key)
        entry["gaze"] = config["gaze"]["filter"]
    problem = None
    if not good:
        problem = f"said {', '.join(repr(word) for word in tagged)} aloud" if tagged else f"heard as {heard!r}"
    return entry, problem


def used_voices():
    path = WORK / "used.json"
    return json.loads(path.read_text()) if path.exists() else {}


def ready(voice, found, wanted_later):
    design = kept(voice)
    wanted = name(voice, design)
    if wanted in found:
        return found[wanted]["voice_id"], "saved already"
    plan, why = subscription()
    if plan is None:
        raise Refused(f"the account cannot be read ({why})")
    if plan["voice_add_edit_counter"] >= plan["max_voice_add_edits"]:
        reset = datetime.fromtimestamp(plan["next_character_count_reset_unix"]).strftime("%d.%m.%Y")
        raise Refused(
            f"{plan['voice_add_edit_counter']} of {plan['max_voice_add_edits']} voice changes used until {reset}"
        )
    for entry_name, entry in saved_as(found, voice).items():
        call("DELETE", f"/v1/voices/{entry['voice_id']}")
        found.pop(entry_name)
    taken = plan["voice_slots_used"]
    how = "saved"
    if taken >= plan["voice_limit"]:
        used = used_voices()
        evictable = sorted(
            found,
            key=lambda entry_name: (
                NAMED.match(entry_name).group(1) in wanted_later,
                used.get(NAMED.match(entry_name).group(1), 0),
            ),
        )
        if not evictable:
            raise Refused(
                f"every voice slot is taken ({taken} of {plan['voice_limit']}) by voices that are not the pack's"
            )
        gone = evictable[0]
        call("DELETE", f"/v1/voices/{found.pop(gone)['voice_id']}")
        how = f"saved in {NAMED.match(gone).group(1)}'s slot"
        taken -= 1
    if "generated_voice_id" not in design:
        raise Refused(f"{voice} keeps no preview to save it from: just voice save {voice}")
    voice_id = create(voice, design, design["generated_voice_id"])["voice_id"]
    found[wanted] = {"voice_id": voice_id}
    return voice_id, how if how != "saved" else f"saved, slot {taken + 1} of {plan['voice_limit']}"


def record(selectors, again):
    config = spoken.voices()
    lines = spoken.lines(config)
    chosen, _ = select(config, lines, selectors) if selectors else (lines, [])
    label = " ".join(selectors) or "every line"
    voice_of = spoken.readers(config)
    unvoiced = Counter(line.by for line in chosen if line.by not in voice_of)
    if unvoiced:
        print(f"no voice reads, skipped: {', '.join(f'{by} ({count})' for by, count in sorted(unvoiced.items()))}")
    unrecordable = [line for line in chosen if line.by in voice_of and not line.recordable()]
    if unrecordable:
        print(
            f"cannot be recorded, a word only the game knows: {', '.join(f'{line.group}:{line.number}' for line in unrecordable)}"
        )
    done = spoken.recordings()
    every = list({take.key: take for take in spoken.takes(config, chosen)}.values())
    undesigned = sorted({take.voice for take in every if kept(take.voice) is None})
    if undesigned:
        print(f"not designed yet, skipped: {', '.join(undesigned)}; just voice design <voice>")
    every = [take for take in every if take.voice not in undesigned]
    todo = [take for take in every if again or not spoken.recorded(done, take)]
    regazed = [
        take
        for take in every
        if take not in todo
        and spoken.gazed(config, take)
        and (
            done[take.key].get("gaze") != config["gaze"]["filter"]
            or not spoken.clip_path(take.key + spoken.GAZE).exists()
        )
    ]
    for take in regazed:
        gaze_copy(config, take.key)
        done[take.key]["gaze"] = config["gaze"]["filter"]
    if regazed:
        write_json(spoken.RECORDINGS, done)
        print(f"rendered {counted(len(regazed), 'gaze copy')} anew")
    groups = set()
    problems = []
    if todo:
        since = time.time()
        characters = Counter()
        for take in todo:
            characters[take.voice] += len(take.text)
        plan, why = subscription()
        left = f"; {credits_left(plan):,} left" if plan else f"; credits unknown ({why})"
        print(
            f"{label}: {counted(len(todo), 'line')} silent for {counted(len(characters), 'voice')}, "
            f"{sum(characters.values()):,} characters, about {round(sum(characters.values()) * rate()):,} credits{left}",
            flush=True,
        )
        try:
            found = account()
        except Refused as refused:
            fail("record", refused.message)
        mine = {NAMED.match(entry_name).group(1) for entry_name in found}
        order = sorted(characters, key=lambda voice: (voice not in mine, voice))
        lock = threading.Lock()
        stopped = []
        used = used_voices()
        for position, voice in enumerate(order):
            mine_todo = [take for take in todo if take.voice == voice]
            try:
                voice_id, how = ready(voice, found, set(order[position + 1 :]))
            except Refused as refused:
                stopped.append(f"{voice}: {refused.message}")
                break
            design = kept(voice)["id"]
            pitch = config["voices"][voice].get("pitch", 0)

            def run(take, voice_id=voice_id, design=design, pitch=pitch):
                if stopped:
                    return
                try:
                    entry, problem = take_one(take, config, voice_id, design, pitch)
                except Refused as refused:
                    stopped.append(refused.message)
                    return
                with lock:
                    done[take.key] = entry
                    write_json(spoken.RECORDINGS, done)
                    groups.add(take.line.group)
                    if problem:
                        problems.append(f"{take.line.group}:{take.line.number} ({take.voice}) {problem}")

            with ThreadPoolExecutor(AT_ONCE) as pool:
                list(pool.map(run, mine_todo))
            used[voice] = time.time()
            write_json(WORK / "used.json", used)
            retaken = sum(
                1 for take in mine_todo if take.key in done and done[take.key].get("check", {}).get("takes", 1) > 1
            )
            print(
                f"  {voice:<14} {how}, {counted(len(mine_todo), 'line')}, checked{f', {retaken} taken again' if retaken else ''}",
                flush=True,
            )
            if stopped:
                break
        print(spent(since, sum(characters.values())))
        if problems:
            print(
                f"still off after {config['check']['takes']} takes, kept the closest; hear them, then just voice record <group:line> --again:"
            )
            for problem in problems:
                print(f"  {problem}")
        if stopped:
            print(f"stopped: {stopped[0]}; what was recorded stays, and the same command goes on from there")
    else:
        print(f"{label}: nothing silent to record")
    prune()
    spoken.write_index(config)
    print("the game's index rewritten: mod/src/generated/resources/wayfarer/voices.json")
    heard = [
        scene(config, lines, group) for group in sorted(groups) if not group.startswith(("lang/", "book/", "people"))
    ]
    heard = [line for line in heard if line]
    if heard:
        print(f"hear: {', '.join(heard)}")
    if todo and stopped:
        sys.exit(1)


def stale_clips(config, lines, done):
    current = {take.key for take in spoken.takes(config, lines)}
    on_disk = {f"{path.parent.name}/{path.name.removesuffix('.ogg')}" for path in spoken.CLIPS.glob("*/*.ogg")}
    loose = sorted(key for key in on_disk if key.removesuffix(spoken.GAZE) not in done)
    return [key for key in done if key not in current], loose


def prune():
    config = spoken.voices()
    done = spoken.recordings()
    stale, loose = stale_clips(config, spoken.lines(config), done)
    if not stale and not loose:
        return 0
    print(f"pruned {counted(len(stale) + len(loose), 'clip')} for no line any more:")
    for key in stale:
        spoken.clip_path(key).unlink(missing_ok=True)
        spoken.clip_path(key + spoken.GAZE).unlink(missing_ok=True)
        entry = done.pop(key)
        print(f'  {entry["voice"]:<14} "{short(entry["text"])}"')
    for key in loose:
        spoken.clip_path(key).unlink()
        print(f"  {key}.ogg, which story/voices/recordings.json does not name")
    write_json(spoken.RECORDINGS, done)
    for folder in spoken.CLIPS.glob("*"):
        if folder.is_dir() and not any(folder.iterdir()):
            folder.rmdir()
    return len(stale) + len(loose)


def status():
    config = spoken.voices()
    lines = spoken.lines(config)
    plan, why = subscription()
    print(headline(plan, why))
    try:
        found = account()
    except Refused as refused:
        found = None
        print(f"voices in the account unknown ({refused.message})")
    done = spoken.recordings()
    every = spoken.takes(config, lines)
    rows = [("voice", "kept", "in account", "lines", "recorded", "earlier design", "to record")]
    for voice in sorted(config["voices"]):
        mine = {take.key: take for take in every if take.voice == voice}
        record_ = kept(voice)
        made = [key for key, take in mine.items() if spoken.recorded(done, take)]
        pitch = config["voices"][voice].get("pitch", 0)
        earlier = [
            key
            for key in made
            if record_ is None or done[key]["design"] != record_["id"] or done[key]["pitch"] != pitch
        ]
        left = sum(len(take.text) for key, take in mine.items() if key not in made)
        if found is None:
            where = "?"
        elif record_ and name(voice, record_) in found:
            where = "yes"
        else:
            where = "old design" if saved_as(found, voice) else "no"
        rows.append(
            (
                voice,
                "yes" if record_ else "no",
                where,
                f"{len(mine):,}",
                f"{len(made):,}",
                f"{len(earlier):,}",
                f"{left:,} characters",
            )
        )
    widths = [max(len(row[column]) for row in rows) for column in range(len(rows[0]))]
    print()
    for row in rows:
        cells = [
            cell.ljust(width) if column < 3 or column == 6 else cell.rjust(width)
            for column, (cell, width) in enumerate(zip(row, widths, strict=True))
        ]
        print("  ".join(cells).rstrip())
    voice_of = spoken.readers(config)
    unvoiced = {}
    for line in lines:
        if line.by not in voice_of:
            count, characters = unvoiced.get(line.by, (0, 0))
            unvoiced[line.by] = (count + 1, characters + len(line.text))
    if unvoiced:
        listed = "; ".join(
            f"{by}, {counted(count, 'line')} ({characters:,} characters)"
            for by, (count, characters) in sorted(unvoiced.items(), key=lambda item: -item[1][1])
        )
        print(f"\nno voice yet: {listed}")
    unrecordable = [line for line in lines if line.by in voice_of and not line.recordable()]
    if unrecordable:
        example = unrecordable[0]
        print(
            f"cannot be recorded, a word only the game knows: {counted(len(unrecordable), 'line')}, such as "
            f'{example.group}:{example.number} {example.by} "{short(example.text)}"'
        )
    stale, loose = stale_clips(config, lines, done)
    if stale or loose:
        print(f"{counted(len(stale) + len(loose), 'clip')} for no line any more: just voice prune")


def main():
    parser = argparse.ArgumentParser(
        prog="just voice",
        description="Design, keep, save and record the story's voices with ElevenLabs, from story/voices.toml",
    )
    commands = parser.add_subparsers(dest="command")
    designing = commands.add_parser("design", help="Design previews of a voice from its description and preview text")
    designing.add_argument("voice")
    designing.add_argument("count", nargs="?", type=int, default=1, help="how many designs of 3 previews each")
    designing.add_argument(
        "--like", action="store_true", help="design from its kept preview and its clips as reference audio"
    )
    keeping = commands.add_parser("keep", help="Keep a preview as the voice's design, in story/voices/")
    keeping.add_argument("voice")
    keeping.add_argument("preview", help="the preview's name, such as 2840193117-2")
    saving = commands.add_parser(
        "save", help="Save the kept design in the account, from its kept preview or designed again from its seed"
    )
    saving.add_argument("voice")
    saving.add_argument("--rebuilt", action="store_true", help="save the take rebuilt from the seed, once heard")
    trying = commands.add_parser(
        "try", help="Save previews in the account and have the recording model read their text, as the game plays them"
    )
    trying.add_argument("voice")
    trying.add_argument("previews", nargs="+", metavar="preview")
    commands.add_parser("drop", help="Delete the voice from the account; the repository keeps everything").add_argument(
        "voice"
    )
    recording = commands.add_parser(
        "record",
        help="Record every silent line, or those of voices, groups or single lines, check each take, then prune and write the game's index",
    )
    recording.add_argument(
        "selectors",
        nargs="*",
        metavar="voice|group|group:line",
        help="what to record; everything silent when none is named",
    )
    recording.add_argument("--again", action="store_true", help="record lines that already have a clip anew")
    commands.add_parser("prune", help="Delete the clips that belong to no line any more")
    arguments = parser.parse_args()
    match arguments.command:
        case "design":
            design(arguments.voice, arguments.count, arguments.like)
        case "keep":
            keep(arguments.voice, arguments.preview)
        case "save":
            save(arguments.voice, arguments.rebuilt)
        case "try":
            audition(arguments.voice, arguments.previews)
        case "drop":
            drop(arguments.voice)
        case "record":
            record(arguments.selectors, arguments.again)
        case "prune":
            if not prune():
                print("nothing to prune: every clip belongs to a line")
        case _:
            status()


if __name__ == "__main__":
    main()

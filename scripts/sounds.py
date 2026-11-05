import json
import re
import subprocess
import sys
import tempfile
import urllib.request
from pathlib import Path

import tomllib

ROOT = Path(__file__).resolve().parent.parent
SOUNDS = ROOT / "mod/src/main/resources/assets/wayfarer/sounds"
DOWNLOADS = Path(tempfile.gettempdir()) / "wayfarer-sounds"
AGENT = {"User-Agent": "roman-16/wayfarer"}
LEVEL_UP_LUFS = -17
TARGETS = {"frequent": LEVEL_UP_LUFS - 6, "loop": -30, "music": -18, "rare": LEVEL_UP_LUFS, "voice": LEVEL_UP_LUFS}
TRUE_PEAK = -1.5
SHORTEST_MEASURED = 0.5
SEAM_SECONDS = 1
SAMPLE_RATE = 44100
QUALITY = 5


def page_audio(page):
    html = urllib.request.urlopen(urllib.request.Request(page + "/", headers=AGENT)).read().decode()
    found = (
        re.search(r'og:audio" content="([^"]+)"', html)
        or re.search(r'href="(https?://[^"]+\.(?:mp3|ogg|flac))"', html)
        or re.search(r"data-mp3-url='(https://[^']+)'", html)
    )
    if not found:
        raise SystemExit(f"sounds failed: {page} names no preview and links no audio file")
    preview = re.sub(r"^https://freesound\.org", "", found.group(1))
    return preview.replace("-lq.mp3", "-hq.ogg")


def download(name, page):
    DOWNLOADS.mkdir(exist_ok=True)
    target = DOWNLOADS / f"{name}.ogg"
    if not target.exists():
        request = urllib.request.Request(page_audio(page), headers=AGENT)
        target.write_bytes(urllib.request.urlopen(request).read())
    return target


def duration(path):
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path)],
        capture_output=True,
        text=True,
        check=True,
    )
    return float(out.stdout)


def ffmpeg(*args):
    subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", *map(str, args)], check=True)


def shaped(source, spec, work):
    cut = spec.get("cut", {})
    start = cut.get("start", 0)
    length = cut.get("length", duration(source) - start)
    filters = [f"atrim={start}:{start + length}", "asetpts=PTS-STARTPTS"]
    if "fade_in" in cut:
        filters.append(f"afade=t=in:d={cut['fade_in']}")
    if "fade_out" in cut:
        filters.append(f"afade=t=out:st={length - cut['fade_out']}:d={cut['fade_out']}")
    channels = ["-ac", "1"] if "reach" in spec or "replaces" in spec else []
    out = work / "shaped.wav"
    ffmpeg("-i", source, "-af", ",".join(filters), *channels, "-ar", SAMPLE_RATE, out)
    return out


def seamless(source, work):
    length = duration(source)
    body, tail, head = work / "body.wav", work / "tail.wav", work / "head.wav"
    ffmpeg("-i", source, "-af", f"atrim={SEAM_SECONDS}:{length - SEAM_SECONDS},asetpts=PTS-STARTPTS", body)
    ffmpeg(
        "-i", source, "-af", f"atrim={length - SEAM_SECONDS},asetpts=PTS-STARTPTS,afade=t=out:d={SEAM_SECONDS}", tail
    )
    ffmpeg("-i", source, "-af", f"atrim=0:{SEAM_SECONDS},asetpts=PTS-STARTPTS,afade=t=in:d={SEAM_SECONDS}", head)
    seam, out = work / "seam.wav", work / "seamless.wav"
    ffmpeg("-i", tail, "-i", head, "-filter_complex", "amix=inputs=2:normalize=0", seam)
    ffmpeg("-i", body, "-i", seam, "-filter_complex", "concat=n=2:v=0:a=1", out)
    return out


def loudness(source, target, out, rate=SAMPLE_RATE):
    if duration(source) < SHORTEST_MEASURED:
        peak = re.search(
            r"max_volume: (-?[\d.]+) dB",
            subprocess.run(
                ["ffmpeg", "-hide_banner", "-i", str(source), "-af", "volumedetect", "-f", "null", "-"],
                capture_output=True,
                text=True,
            ).stderr,
        )
        gain = target - LEVEL_UP_LUFS + TRUE_PEAK - float(peak.group(1))
        ffmpeg("-i", source, "-af", f"volume={gain}dB", "-c:a", "libvorbis", "-q:a", QUALITY, out)
        return
    measure = f"loudnorm=I={target}:TP={TRUE_PEAK}:LRA=11:print_format=json"
    report = subprocess.run(
        ["ffmpeg", "-hide_banner", "-i", str(source), "-af", measure, "-f", "null", "-"],
        capture_output=True,
        text=True,
    ).stderr
    measured = json.loads(report[report.rindex("{") : report.rindex("}") + 1])
    second = (
        f"loudnorm=I={target}:TP={TRUE_PEAK}:LRA=11:measured_I={measured['input_i']}:measured_TP={measured['input_tp']}"
        f":measured_LRA={measured['input_lra']}:measured_thresh={measured['input_thresh']}"
        f":offset={measured['target_offset']}:linear=true"
    )
    ffmpeg("-i", source, "-af", second, "-ar", rate, "-c:a", "libvorbis", "-q:a", QUALITY, out)


def make(name, spec, folder, target, loop):
    out = SOUNDS / folder / f"{name}.ogg"
    out.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as temporary:
        work = Path(temporary)
        source = shaped(download(name, spec["page"]), spec, work)
        loudness(seamless(source, work) if loop else source, target, out)
    print(f"{folder}/{name}.ogg: {duration(out):.2f} s at {target} LUFS")


def main(wanted):
    spec = tomllib.loads((ROOT / "story/sounds.toml").read_text())
    for name, effect in spec["effects"].items():
        if not wanted or name in wanted:
            make(name, effect, "effects", TARGETS[effect["kind"]] + effect.get("gain", 0), effect["kind"] == "loop")
    for name, piece in spec["music"].items():
        if name in wanted or (not wanted and not (SOUNDS / "music" / f"{name}.ogg").exists()):
            make(name, piece, "music", TARGETS["music"] + piece.get("gain", 0), False)


if __name__ == "__main__":
    main(set(sys.argv[1:]))

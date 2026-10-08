import argparse
import json
import shutil
import subprocess
import sys
from pathlib import Path

import client
import states
import tomllib

ROOT = Path(__file__).resolve().parent.parent
GALLERY = ROOT / "gallery"
SHOTS = GALLERY / "shots.toml"
WORK = ROOT / "build/gallery"
PAGE = "README.md"
TITLES = {"hud": "HUD"}
SCALES = (2, 3, 4)
USUAL_SCALE = 3
JPEG_QUALITY = "90"
FUZZ = "2%"
SHOT_SECONDS = 900
PICTURES = (".jpg", ".png")
KINDS = {"hud", "screen", "tour", "views", "world"}
TOUR_COLUMNS = 3
NOTE = (
    "Every facet of what a player sees, shot in the dev client by `just gallery` from `shots.toml`. The dev client "
    "draws without the shader and the far view, at 8 chunks; the exported pack has both."
)


def spec():
    shots = tomllib.loads(SHOTS.read_text())["shots"]
    for shot in shots:
        if shot["kind"] not in KINDS:
            sys.exit(f"gallery failed: {shot['id']}: kind is {shot['kind']!r}, not one of {', '.join(sorted(KINDS))}")
        unknown = set(shot.get("sides", [])) - set(client.SIDES)
        if unknown:
            sys.exit(
                f"gallery failed: {shot['id']}: no side {', '.join(sorted(unknown))}; the sides are {', '.join(client.SIDES)}"
            )
    return shots


def slug(shot):
    return shot["id"].replace("/", "-")


def cameras(subject):
    plan = json.loads(client.TOUR.read_text())
    if subject == "camp":
        return [camera["name"] for camera in plan["camp"]["cameras"]]
    shrine = next(shrine for shrine in plan["shrines"] if shrine["eye"] == subject)
    return [*client.SHAFT_CAMERAS, *(camera["name"] for camera in shrine["cameras"])]


def pictures(shot):
    name, kind = shot["id"], shot["kind"]
    if kind in ("hud", "screen"):
        extension = "png" if kind == "screen" else "jpg"
        return [
            (f"{name}-{scale}.{extension}", f"gallery-{slug(shot)}-{scale}", f"Scale {scale}", scale)
            for scale in SCALES
        ]
    if kind == "views":
        return [
            (f"{name}/{side}.jpg", f"workshop-{slug(shot)}-{side}", title(side), None)
            for side in shot.get("sides", client.SIDES)
        ]
    if kind == "tour":
        subject = shot["subject"]
        return [
            (f"{name}/{camera}.jpg", f"tour-{subject}-{camera}", title(camera), None) for camera in cameras(subject)
        ]
    return [(f"{name}.jpg", f"gallery-{slug(shot)}", "", None)]


def title(segment):
    return TITLES.get(segment, segment.replace("-", " ").capitalize())


def chosen(shots, folders):
    if not folders:
        return shots
    return [
        shot for shot in shots if any(shot["id"] == folder or shot["id"].startswith(folder + "/") for folder in folders)
    ]


def open_world(at):
    if at == "showroom":
        client.ensure("showroom", client.START_SECONDS, False, "full", anew=True)
    else:
        states.check(at)
        client.ensure("state", client.START_SECONDS, False, "full", state=at)


def run(shot, steps):
    entries = client.parse(client.batch(steps, SHOT_SECONDS))
    errors = [error for entry in entries for error in entry.get("error", [])]
    if errors:
        client.batch(["/tick unfreeze", "close", "chat on", f"scale {USUAL_SCALE}", "gui on"], SHOT_SECONDS)
        sys.exit(f"gallery failed: {shot['id']}: {'; '.join(errors)}")


def shoot(shot):
    prepare = ["close", "gui on", "chat " + ("on" if shot.get("chat") else "off"), f"scale {USUAL_SCALE}"]
    kind = shot["kind"]
    if kind == "views":
        run(shot, [*prepare, "workshop", "clear", *shot.get("steps", []), "/wayfarer workshop unsign", "/tick freeze"])
        _, errors = client.sides(slug(shot), SHOT_SECONDS, shot.get("sides", tuple(client.SIDES)))
        if errors:
            sys.exit(f"gallery failed: {shot['id']}: {'; '.join(errors)}")
    elif kind == "tour":
        run(shot, [*prepare, *shot.get("steps", []), "/tick freeze"])
        client.photograph([shot["subject"]], SHOT_SECONDS)
    else:
        steps = [*prepare, *shot.get("steps", []), "/tick freeze", "settle"]
        if kind == "screen":
            steps.append("point 0 0")
        for _, name, _, scale in pictures(shot):
            if scale:
                steps.append(f"scale {scale}")
            steps.append(f"shot {name}")
        run(shot, steps)
    client.batch(
        ["/tick unfreeze", "close", "chat on", f"scale {USUAL_SCALE}", "gui on", *shot.get("after", [])], SHOT_SECONDS
    )


def keep(shot):
    found = {}
    for picture, name, _, _ in pictures(shot):
        source = client.SCREENSHOTS / f"{name}.png"
        if not source.exists():
            sys.exit(f"gallery failed: {shot['id']}: the driver wrote no {source.name}")
        target = GALLERY / picture
        fresh = WORK / picture
        fresh.parent.mkdir(parents=True, exist_ok=True)
        if picture.endswith(".png"):
            encode = ["-strip", "-define", "png:exclude-chunks=date,time", f"PNG24:{fresh}"]
        else:
            encode = ["-strip", "-quality", JPEG_QUALITY, str(fresh)]
        subprocess.run(["magick", str(source), *encode], check=True)
        if target.exists() and differing(target, fresh) == 0:
            fresh.unlink()
            found[picture] = "the same"
            continue
        found[picture] = "changed" if target.exists() else "new"
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(fresh, target)
    return found


def differing(first, second):
    result = subprocess.run(
        ["magick", "compare", "-metric", "AE", "-fuzz", FUZZ, str(first), str(second), "null:"],
        capture_output=True,
        text=True,
    )
    if result.returncode > 1:
        return -1
    return int(float(result.stderr.split()[0]))


def stray(shots):
    named = {picture for shot in shots for picture, _, _, _ in pictures(shot)}
    return sorted(
        path.relative_to(GALLERY).as_posix()
        for path in GALLERY.rglob("*")
        if path.suffix in PICTURES and path.relative_to(GALLERY).as_posix() not in named
    )


def pages(shots):
    folders = {}
    for shot in shots:
        folders.setdefault(shot["id"].split("/")[0], []).append(shot)
    written = {PAGE: index(folders)}
    for folder, members in folders.items():
        written[f"{folder}/{PAGE}"] = page(folder, members)
    return written


def index(folders):
    lines = ["# Gallery", "", NOTE, "", "| Folder | Shots | Pictures |", "| --- | --- | --- |"]
    for folder, members in folders.items():
        count = sum(len(pictures(shot)) for shot in members)
        lines.append(f"| [{title(folder)}]({folder}/{PAGE}) | {len(members)} | {count} |")
    return "\n".join(lines) + "\n"


def page(folder, members):
    lines = [f"# {title(folder)}", ""]
    section = None
    for shot in members:
        segments = shot["id"].split("/")
        heading = title(segments[1]) if len(segments) > 1 else title(folder)
        if heading != section:
            lines += [f"## {heading}", ""]
            section = heading
        where = "the showroom" if shot["at"] == "showroom" else f"the story at `{shot['at']}`"
        lines += [f"**{shot['caption']}**, in {where}", ""]
        taken = pictures(shot)
        relative = [(picture.removeprefix(folder + "/"), label) for picture, _, label, _ in taken]
        if shot["kind"] == "world":
            lines += [f"![{shot['caption']}]({relative[0][0]})", ""]
            continue
        columns = TOUR_COLUMNS if shot["kind"] == "tour" else len(relative)
        for start in range(0, len(relative), columns):
            row = relative[start : start + columns]
            lines.append("| " + " | ".join(label for _, label in row) + " |")
            lines.append("|" + " --- |" * len(row))
            lines.append("| " + " | ".join(f"![{label}]({picture})" for picture, label in row) + " |")
            lines.append("")
    return "\n".join(lines)


def problems():
    shots = spec()
    found = []
    for shot in shots:
        for picture, _, _, _ in pictures(shot):
            if not (GALLERY / picture).exists():
                found.append(
                    f"gallery/{picture}: {shot['id']} names it, but it was never shot; run just gallery {shot['id']}"
                )
    found += [
        f"gallery/{picture}: no shot in gallery/shots.toml names it; run just gallery" for picture in stray(shots)
    ]
    for path, text in pages(shots).items():
        if not (GALLERY / path).exists() or (GALLERY / path).read_text() != text:
            found.append(f"gallery/{path}: out of date with gallery/shots.toml; run just gallery")
    return found


def main():
    parser = argparse.ArgumentParser(
        description="Shoot the gallery's pictures from gallery/shots.toml in the dev client, keep each one whose "
        "picture did not change, and write the gallery's pages"
    )
    parser.add_argument(
        "folders", nargs="*", help="shots or folders to shoot, such as journal or hud/compass; all when none are named"
    )
    arguments = parser.parse_args()
    shots = spec()
    wanted = chosen(shots, arguments.folders)
    if not wanted:
        sys.exit(f"gallery failed: no shot is named or lies under {', '.join(arguments.folders)}")
    shutil.rmtree(WORK, ignore_errors=True)
    found = {}
    showroom = False
    ordered = sorted(wanted, key=lambda shot: shot["at"] != "showroom")
    for index, shot in enumerate(ordered):
        try:
            if shot["at"] != "showroom" or not showroom:
                open_world(shot["at"])
                showroom = shot["at"] == "showroom"
            shoot(shot)
        except SystemExit:
            print("the rest: just gallery " + " ".join(rest["id"] for rest in ordered[index:]), flush=True)
            raise
        kept = keep(shot)
        found |= kept
        counts = {status: list(kept.values()).count(status) for status in ("new", "changed", "the same")}
        print(
            f"{shot['id']}: " + ", ".join(f"{count} {status}" for status, count in counts.items() if count), flush=True
        )
    for picture in stray(shots):
        (GALLERY / picture).unlink()
        found[picture] = "deleted"
    for folder in sorted(GALLERY.rglob("*"), reverse=True):
        if folder.is_dir() and not any(folder.iterdir()):
            folder.rmdir()
    for path, text in pages(shots).items():
        (GALLERY / path).parent.mkdir(parents=True, exist_ok=True)
        (GALLERY / path).write_text(text)
    statuses = ("new", "changed", "deleted", "the same")
    print("gallery: " + ", ".join(f"{list(found.values()).count(status)} {status}" for status in statuses))
    for status in statuses[:3]:
        for picture in sorted(picture for picture, found_status in found.items() if found_status == status):
            print(f"  {status:8} gallery/{picture}")


if __name__ == "__main__":
    main()

import argparse
import json
import subprocess
import sys
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path
from xml.sax.saxutils import escape

MAP_PIXELS = 1024
MAP_MARGIN = 32
PATH_COLOURS = ("#d9822b", "#4f9fd9", "#8fc94a", "#d94f8a", "#c9b44a", "#9a6fd9")
BIOME_COLOURS = (
    ("ocean", "#2b4f7a"),
    ("river", "#4f7fb0"),
    ("beach", "#d9cf9a"),
    ("desert", "#d9c27a"),
    ("badlands", "#b0663a"),
    ("swamp", "#4f6b3a"),
    ("mangrove", "#4f6b3a"),
    ("snow", "#e8eef2"),
    ("frozen", "#cfe0ea"),
    ("ice", "#cfe0ea"),
    ("peak", "#9a9a9a"),
    ("mountain", "#8a8a8a"),
    ("taiga", "#3f6b4f"),
    ("jungle", "#3a8a3a"),
    ("savanna", "#a8a84f"),
    ("forest", "#3f7a3f"),
    ("plains", "#7aa84f"),
    ("meadow", "#8ab85f"),
)
QUIET_EVENTS = {"frames", "position"}


def lines(folder):
    candidates = [folder / "record", folder / "wayfarer/record", folder]
    for candidate in candidates:
        files = sorted(candidate.glob("*.jsonl")) if candidate.is_dir() else []
        if files:
            events = [json.loads(line) for path in files for line in path.read_text().splitlines() if line.strip()]
            events = [
                event
                for event in events
                if not (event["event"] == "place" and event.get("label", "").startswith("wayfarer.place."))
            ]
            for event in events:
                event["at"] = datetime.fromisoformat(event["time"])
            return sorted(events, key=lambda event: event["at"])
    sys.exit(f"no play record in {folder}: looked for *.jsonl in {', '.join(str(path) for path in candidates)}")


def clock(moment):
    return moment.strftime("%d.%m. %H:%M")


def minutes(start, end):
    return f"{(end - start).total_seconds() / 60:.0f} min"


def steps(events):
    begun = {}
    rows = []
    for event in events:
        key = (event.get("quest"), event.get("step"))
        if event["event"] == "step begun":
            begun[key] = event
        elif event["event"] == "step ended" and key in begun:
            rows.append((begun.pop(key), event))
    rows += [(start, None) for start in begun.values()]
    return sorted(rows, key=lambda row: row[0]["at"])


def during(events, start, end, player=None):
    return [
        event
        for event in events
        if start["at"] <= event["at"] <= (end["at"] if end else events[-1]["at"])
        and (player is None or event.get("player") == player)
    ]


def timeline(events):
    out = ["## Steps", ""]
    for start, end in steps(events):
        inside = during(events, start, end)
        notes = Counter(
            event["event"] for event in inside if event["event"] in ("death", "hint", "rewound", "stall begun")
        )
        extra = ", ".join(
            f"{count} {name.removesuffix(' begun')}{'s' * (count != 1 and name != 'rewound')}"
            for name, count in notes.items()
        )
        took = minutes(start["at"], end["at"]) if end else "unfinished"
        name = f"{start['quest']} › {start['step']} ({start.get('kind', '')})"
        out.append(f"{name:<56} {clock(start['at'])}  {took:>10}{'  ' + extra if extra else ''}")
    return out


def stalls(events):
    out = ["## Stalls", ""]
    open_stalls = {}
    for event in events:
        if event["event"] == "stall begun":
            open_stalls[event["player"]] = event
        elif event["event"] == "stall ended" and event.get("player") in open_stalls:
            out += stall(events, open_stalls.pop(event["player"]), event)
    for start in open_stalls.values():
        out += stall(events, start, None)
    return out if len(out) > 2 else []


def stall(events, start, end):
    player = start["player"]
    inside = during(events, start, end, player)
    near = [
        event
        for event in events
        if event.get("player") == player and event["event"] == "position" and event["at"] <= start["at"]
    ]
    goal = near[-1].get("goal_blocks") if near else None
    tracked = near[-1].get("tracked", "nothing tracked") if near else "nothing tracked"
    span = f"{start['at']:%H:%M}-{end['at']:%H:%M}" if end else f"{start['at']:%H:%M}-the end"
    kind = "standing still" if start.get("still") else "getting no closer"
    head = f"stall {span}  {player} {kind} at {start['x']} {start['z']}, {tracked}" + (
        f", {goal} blocks from the goal" if goal is not None else ""
    )
    said = [f'"{event["text"]}"' for event in inside if event["event"] == "chat"]
    counts = Counter(event["event"] for event in inside)
    screens = Counter(event["screen"].split(" ")[0] for event in inside if event["event"] == "screen")
    doing = [
        f"held the ember {counts['ember']} times",
        *(f"opened the {name} {count} times" for name, count in screens.items()),
    ]
    clicks = [event["on"] for event in inside if event["event"] == "click"]
    if clicks:
        doing.append("clicked " + ", ".join(clicks))
    return [head, "  " + " · ".join(said + doing)]


def conversations(events):
    out = ["## Choices", ""]
    offered = None
    for event in events:
        if event["event"] == "answers":
            offered = event
        elif event["event"] == "chose":
            question = " | ".join(offered["answers"]) if offered else ""
            out.append(
                f'{clock(event["at"])}  {event["player"]} chose "{event["answer"]}" after {event["seconds"]} s  ({question})'
            )
        elif event["event"] == "again":
            out.append(f"{clock(event['at'])}  {event['player']} asked again after {event['seconds']} s")
    return out if len(out) > 2 else []


def deaths(events):
    out = ["## Deaths and rewinds", ""]
    out += [
        f"{clock(event['at'])}  {event['cause']} at {event['x']} {event['y']} {event['z']}"
        if event["event"] == "death"
        else f"{clock(event['at'])}  rewound to checkpoint {event['checkpoint']}: {event['to']}, kept {event['taken'][11:16]}"
        for event in events
        if event["event"] in ("death", "rewound")
    ]
    return out if len(out) > 2 else []


def workarounds(events):
    out = ["## Workarounds", ""]
    out += [
        f"{clock(event['at'])}  {event.get('player', '')}: {event['command']}"
        for event in events
        if event["event"] == "workaround"
    ]
    return out if len(out) > 2 else []


def weather(events):
    out = ["## Weather", ""]
    began = None
    for event in events:
        if event["event"] == "rain began":
            began = event
        elif event["event"] == "rain ended":
            start = began["at"] if began else events[0]["at"]
            storm = " with thunder" if (began or event).get("thunder") else ""
            out.append(
                f"rain{storm} {clock(start)} to {clock(event['at'])} ({minutes(start, event['at'])}), on day {event['day']}"
            )
            began = None
    if began:
        out.append(
            f"rain{' with thunder' if began.get('thunder') else ''} from {clock(began['at'])} to the end, on day {began['day']}"
        )
    return out if len(out) > 2 else []


def players(events):
    out = ["## Players", ""]
    for player in sorted({event["player"] for event in events if "player" in event}):
        own = [event for event in events if event.get("player") == player]
        frames = [event for event in own if event["event"] == "frames"]
        arrivals = [event["seconds"] for event in own if event["event"] == "arrived"]
        screens = Counter(event["screen"].split(" ")[0] for event in own if event["event"] == "screen")
        parts = [f"{len([event for event in own if event['event'] == 'death'])} deaths"]
        if frames:
            slowest = min(frames, key=lambda event: event["fps"])
            card = f", graphics card busy {slowest['gpu']}%" if "gpu" in slowest else ""
            card += ", on battery" if slowest.get("battery") else ""
            parts.append(f"lowest {slowest['fps']} fps, 1% low {min(event['low'] for event in frames)}{card}")
            parts.append(f"fullest memory {max(event['heap_mb'] for event in frames)} of {frames[0]['heap_max_mb']} MB")
        if arrivals:
            parts.append(f"land drawn {max(arrivals)} s after arriving at worst")
        if screens:
            parts.append("screens " + ", ".join(f"{name} {count}" for name, count in screens.most_common()))
        out.append(f"{player}: " + " · ".join(parts))
    return out


def biome(name):
    for key, colour in BIOME_COLOURS:
        if key in name:
            return colour
    return "#6a7a5a"


def paths(events, target):
    placed = [event for event in events if "x" in event and event.get("dimension") == "minecraft:overworld"]
    if not placed:
        return None
    xs = [event["x"] for event in placed]
    zs = [event["z"] for event in placed]
    span = max(max(xs) - min(xs), max(zs) - min(zs), 64)
    scale = (MAP_PIXELS - 2 * MAP_MARGIN) / span

    def point(event):
        return MAP_MARGIN + (event["x"] - min(xs)) * scale, MAP_MARGIN + (event["z"] - min(zs)) * scale

    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{MAP_PIXELS}" height="{MAP_PIXELS}">',
        '<rect width="100%" height="100%" fill="#1c1a17"/>',
    ]
    for event in placed:
        if event["event"] == "position":
            x, y = point(event)
            parts.append(
                f'<circle cx="{x:.1f}" cy="{y:.1f}" r="6" fill="{biome(event.get("biome", ""))}" opacity="0.5"/>'
            )
    by_player = defaultdict(list)
    for event in placed:
        if "player" in event:
            by_player[event["player"]].append(point(event))
    for index, (player, points) in enumerate(sorted(by_player.items())):
        colour = PATH_COLOURS[index % len(PATH_COLOURS)]
        joined = " ".join(f"{x:.1f},{y:.1f}" for x, y in points)
        parts.append(f'<polyline points="{joined}" fill="none" stroke="{colour}" stroke-width="2"/>')
        parts.append(
            f'<text x="8" y="{20 + index * 18}" fill="{colour}" font-family="monospace" font-size="14">{escape(player)}</text>'
        )
    for event in placed:
        if event["event"] in ("place", "death", "stall begun"):
            x, y = point(event)
            label = event.get("label") or event["event"]
            colour = "#e0b040" if event["event"] == "place" else "#d04040"
            parts.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="4" fill="{colour}"/>')
            parts.append(
                f'<text x="{x + 6:.1f}" y="{y - 6:.1f}" fill="{colour}" font-family="monospace" font-size="12">{escape(label)}</text>'
            )
    parts.append(
        f'<text x="8" y="{MAP_PIXELS - 10}" fill="#a09a90" font-family="monospace" font-size="12">{span} blocks across</text>'
    )
    parts.append("</svg>")
    target.parent.mkdir(parents=True, exist_ok=True)
    svg = target.with_suffix(".svg")
    svg.write_text("\n".join(parts))
    subprocess.run(["magick", str(svg), str(target)], check=True)
    return target


def main():
    parser = argparse.ArgumentParser(
        description="Turn a play record into a report: each step's time, every stall with what the players did, the choices, deaths, rain, screens and frames, and a map of where everyone walked"
    )
    parser.add_argument(
        "folder", type=Path, help="an exaroton session, a playthrough's folder or a save, holding the record's *.jsonl"
    )
    parser.add_argument(
        "--since",
        type=datetime.fromisoformat,
        help="report only what came after this moment, such as a playthrough's start from its run.json",
    )
    arguments = parser.parse_args()
    events = [event for event in lines(arguments.folder) if arguments.since is None or event["at"] >= arguments.since]
    if not events:
        sys.exit(f"nothing in the play record of {arguments.folder} since {arguments.since}")
    head = [
        f"# Play record {clock(events[0]['at'])} to {clock(events[-1]['at'])} ({minutes(events[0]['at'], events[-1]['at'])})",
        "",
    ]
    picture = paths(events, arguments.folder / "report" / "paths.png")
    sections = [
        head,
        timeline(events),
        stalls(events),
        conversations(events),
        deaths(events),
        workarounds(events),
        weather(events),
        players(events),
    ]
    print("\n".join(line for section in sections if section for line in [*section, ""]))
    if picture:
        print(f"Paths: {picture}")


if __name__ == "__main__":
    main()

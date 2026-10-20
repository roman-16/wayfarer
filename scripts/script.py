import json
import re
from pathlib import Path

import tomllib

ROOT = Path(__file__).resolve().parent.parent
STORY = ROOT / "story"
LANG = ROOT / "mod/src/main/resources/assets/wayfarer/lang/en_us.json"
TARGET = ROOT / "build/script.md"
NOTICES = (
    "banner",
    "caravan",
    "compass",
    "ember",
    "fire",
    "gate",
    "gaze",
    "night",
    "notice",
    "stillness",
    "stone",
    "talk",
    "tent",
    "unlock",
    "ward",
)
KEY = re.compile(r"%s")


def ordered(quests):
    done = set()
    left = list(quests)
    order = []
    while left:
        ready = next((quest for quest in left if set(quest.get("after", [])) <= done), left[0])
        left.remove(ready)
        done.add(ready["id"])
        order.append(ready)
    return order


KNOWN = set()


def speaker(people, by):
    if not by:
        return "NARRATOR"
    person = people.get(by, {})
    shown = person.get("name") if by in KNOWN or "unnamed" not in person else person["unnamed"]
    return (shown or by.replace("_", " ").title()).upper()


def said(people, line, indent=""):
    when = f" [when {line['when']}]" if "when" in line else ""
    return f"{indent}{speaker(people, line.get('by')):<10} {line['text']}{when}"


def task(lang, name):
    return KEY.sub("[key]", lang.get(f"wayfarer.task.{name}", name))


def step(people, lang, memories, quest, spec):
    where = spec.get("place") or spec.get("at") or spec.get("with") or ""
    out = [f"### {quest['title']} › {spec['id']} ({spec['kind']}{', ' + where if where else ''})", ""]
    for line in spec.get("lines", []):
        out.append(said(people, line))
    for number, choice in enumerate(spec.get("choices", []), 1):
        needs = f" [needs {choice['needs']}]" if "needs" in choice else ""
        when = f" [when {choice['when']}]" if "when" in choice else ""
        out.append(f"   {number} {choice['text']}{needs}{when}")
        out.append(
            f"       {speaker(people, spec.get('with') or next((line.get('by') for line in reversed(spec.get('lines', [])) if line.get('by')), None)):<10} {choice['reply']}"
        )
    for clue in spec.get("clues", []):
        out.append(f"CLUE       {clue['text']}")
    out.append(f"PANEL      {spec['objective']}")
    for name in spec.get("tasks", []):
        out.append(f"TASK       {task(lang, name)}")
    for person, remarks in sorted(spec.get("remarks", {}).items()):
        for remark in remarks:
            out.append(f"  {speaker(people, person):<10} (clicked) {remark}")
    if "awake" in spec:
        out.append(f"BED        {spec['awake']}")
    for line in spec.get("done", []):
        out.append(said(people, line))
    if "memory" in spec and spec["memory"] in memories:
        memory = memories[spec["memory"]]
        out += ["", f"#### Memory: {memory['title']}", ""]
        for inner in memory["steps"]:
            out += [said(people, line, "  ") for line in inner.get("lines", [])]
            out.append(f"  PANEL      {inner['objective']}")
            out += [said(people, line, "  ") for line in inner.get("done", [])]
    journal = [*spec.get("story", []), *(f"{word}: {text}" for word, text in sorted(spec.get("words", {}).items()))]
    journal += [f"{person}: {text}" for person, text in sorted(spec.get("people", {}).items())]
    if journal:
        out.append("JOURNAL    " + "\n           ".join(journal))
    reward = spec.get("reward")
    if reward:
        out.append(f"REWARD     {reward['title']}: {reward['text']} / {reward['claimed']}")
    KNOWN.update(spec.get("words", {}))
    for line in [*spec.get("lines", []), *spec.get("done", [])]:
        KNOWN.update(line.get("names", []))
    return [*out, ""]


def main():
    quests = tomllib.loads((STORY / "quests.toml").read_text())
    people = tomllib.loads((STORY / "people.toml").read_text())
    lang = json.loads(LANG.read_text())
    memories = {memory["id"]: memory for memory in quests["memories"]}
    out = [
        "# Wayfarer: the script",
        "",
        "Every line, answer, task and notice, in the order a player meets them. Written by `just script`.",
        "",
    ]
    for quest in ordered(quests["quests"]):
        opens = ", ".join(
            filter(
                None,
                [
                    "after " + ", ".join(quest["after"]) if quest.get("after") else "",
                    f"{quest['halls']} halls" if quest.get("halls") else "",
                    "when " + ", ".join(quest["when"]) if quest.get("when") else "",
                ],
            )
        )
        out += [
            f"## {quest['title']} ({quest['kind']}, {quest['act']}{', ' + opens if opens else ''})",
            "",
            f"JOURNAL    {quest['summary']}",
        ]
        out += [said(people, line) for line in quest.get("road", [])]
        out.append("")
        for spec in quest["steps"]:
            out += step(people, lang, memories, quest, spec)
    out += ["## People when clicked, at the fire", ""]
    for person, spec in sorted(people.items()):
        for kind in ("welcomes", "mornings", "remarks"):
            for line in spec.get(kind, []):
                out.append(f"{speaker(people, person):<10} ({kind}) {line}")
    out += ["", "## Notices and refusals", ""]
    for key, text in sorted(lang.items()):
        if key.startswith("wayfarer.") and key.split(".")[1] in NOTICES:
            out.append(f"{key}: {text}")
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    TARGET.write_text("\n".join(out) + "\n")
    print(
        f"wrote {TARGET.relative_to(ROOT)}: {len(quests['quests'])} quests and {len(out)} lines, in the order a player meets them"
    )


if __name__ == "__main__":
    main()

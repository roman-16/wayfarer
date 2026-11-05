import fnmatch
import hashlib
import itertools
import json
import re
from dataclasses import dataclass
from pathlib import Path

import tomllib

ROOT = Path(__file__).resolve().parent.parent
STORY = ROOT / "story"
LANG = ROOT / "mod/src/main/resources/assets/wayfarer/lang/en_us.json"
BALANCE = ROOT / "balance.toml"
RECORDINGS = STORY / "voices/recordings.json"
CLIPS = ROOT / "mod/src/main/resources/assets/wayfarer/sounds/voices"
INDEX = ROOT / "mod/src/generated/resources/wayfarer/voices.json"
GAZE = ".gaze"
TAG = re.compile(r"\[[^\[\]]*\]")
PLACEHOLDER = re.compile(r"\{[a-z_]+\}|%(?:\d+\$)?s")
WORD = re.compile(r"[A-Za-z]")
BEFORE_CHOICES = ("lines", "rises", "seen", "asking", "burial")
OUTCOMES = ("arrived", "caught", "forced", "held", "killed", "late", "opens", "spared", "sunk", "won")
NOTICES = ("failed", "listing", "wave", "each")
FIRE_LINES = ("welcomes", "returns", "evenings", "mornings", "remarks", "cold", "shouts")
TELLERS = ("pip_grown", "narrator")
FORGE_ANSWERS = ("give", "keep")
FORGE_SHORT = "short"
CHOSEN = ("journal", "player")
WAYS = ("east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east")
WAVE_KEYS = ("side_waves", "waves")
HOLLOW = "the_hollow"
PIP = "pip"
DICE = {
    "bank": "narrator",
    "bank_it": "player",
    "better_luck": "dicer",
    "fair": "dicer",
    "lose": "narrator",
    "no": "player",
    "offer": "dicer",
    "one": "narrator",
    "play": "player",
    "play_empty": "player",
    "stake": "dicer",
    "stake_empty": "dicer",
    "their_bank": "narrator",
    "their_one": "narrator",
    "their_throw": "narrator",
    "throw": "narrator",
    "throw_again": "player",
    "walked": "dicer",
    "win": "narrator",
}


def shown(text):
    words = TAG.sub(" ", text)
    words = re.sub(r"[ \t]+", " ", words)
    words = re.sub(r" *\n *", "\n", words)
    return words.strip()


def tags(text):
    return [tag[1:-1] for tag in TAG.findall(text)]


def shown_data(value):
    if isinstance(value, str):
        return shown(value) if TAG.search(value) else value
    if isinstance(value, list):
        return [shown_data(item) for item in value]
    if isinstance(value, dict):
        return {key: shown_data(item) for key, item in value.items()}
    return value


@dataclass(frozen=True)
class Line:
    group: str
    step: str
    by: str
    text: str
    number: int = 0

    @property
    def shown(self):
        return shown(self.text)

    def recordable(self):
        return not PLACEHOLDER.search(self.text)


@dataclass(frozen=True)
class Take:
    line: Line
    voice: str
    text: str
    clip: str

    @property
    def key(self):
        return f"{self.voice}/{self.clip}"


class Forms:
    def __init__(self, quests, people):
        balance = tomllib.loads(BALANCE.read_text())
        self.waves = sorted(
            {
                count
                for table in balance.values()
                if isinstance(table, dict)
                for key, count in table.items()
                if key in WAVE_KEYS
            }
        )
        self.stones = sorted(
            {
                choice["stone"]
                for quest in quests["quests"]
                for step in quest["steps"]
                for choice in step.get("choices", [])
                if "stone" in choice
            }
        )
        fated = next(
            step["fates"]
            for quest in quests["quests"]
            if quest["id"] == HOLLOW
            for step in quest["steps"]
            if "fates" in step
        )
        self.names = {person: people[person]["name"] for person in fated}
        kept = {person for quest in quests["quests"] for step in quest["steps"] for person in step.get("keep", [])}
        self.kept = sorted(
            {label for person in kept for label in (people[person]["name"], people[person].get("unnamed")) if label}
        )

    def lost(self, with_pip):
        groups = []
        for size in range(1, len(self.names) + 1):
            for chosen in itertools.combinations(sorted(self.names), size):
                if (PIP in chosen) == with_pip:
                    ordered = sorted(chosen, key=lambda person: (person != PIP, self.names[person]))
                    names = [self.names[person] for person in ordered]
                    groups.append(names[0] if len(names) == 1 else ", ".join(names[:-1]) + " and " + names[-1])
        return groups

    def fill(self, text, dawns=0, with_pip=None):
        choices = []
        if "{wave}" in text:
            choices.append([{"wave": wave, "waves": waves} for waves in self.waves for wave in range(1, waves + 1)])
        if "{direction}" in text:
            choices.append([{"direction": way} for way in WAYS])
        if "{stone}" in text:
            choices.append([{"stone": stone} for stone in self.stones])
        if "{left}" in text and dawns:
            choices.append([{"left": left} for left in range(1, dawns + 1)])
        if "{lost}" in text and with_pip is not None:
            choices.append([{"lost": names} for names in self.lost(with_pip)])
        if not choices:
            return [text]
        out = []
        for combination in itertools.product(*choices):
            filled = text
            for substitution in combination:
                for name, value in substitution.items():
                    filled = filled.replace("{" + name + "}", str(value))
            out.append(filled)
        return out


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


def asker(step):
    if step["kind"] == "bury":
        return None
    if "asking" in step:
        return step.get("with")
    return next((line["by"] for line in reversed(step.get("lines", [])) if "by" in line), None)


def partner(step):
    return step.get("with") or next((line["by"] for line in step.get("lines", []) if "by" in line), "narrator")


def replier(step, choice):
    return None if choice.get("narrated") else asker(step)


def said(group, step, line):
    by = line.get("voice", line.get("by", "narrator"))
    out = [Line(group, step, by, line["text"])]
    if isinstance(line.get("night"), str):
        out.append(Line(group, step, by, line["night"]))
    return out


def journal(group, step, spec):
    texts = [*spec.get("story", []), *spec.get("people", {}).values(), *spec.get("words", {}).values()]
    texts += [text for _, told in sorted(spec.get("stories", {}).items()) for text in told]
    texts += [spec["unwritten"]] if "unwritten" in spec else []
    texts += [choice["story"] for choice in spec.get("choices", []) if "story" in choice]
    return [Line(group, step, "journal", text) for text in texts]


def memory_lines(group, memory):
    out = []
    for inner in memory["steps"]:
        where = f"{memory['id']}/{inner['id']}"
        out += [line for said_line in inner.get("lines", []) for line in said(group, where, said_line)]
        for choice in inner.get("choices", []):
            out.append(Line(group, where, "player", choice["text"]))
            out.append(Line(group, where, "narrator", choice["reply"]))
        out += [line for said_line in inner.get("done", []) for line in said(group, where, said_line)]
    return out


def step_lines(quest, spec, memories):
    group = quest["id"]
    where = f"{quest['id']}/{spec['id']}"
    out = []
    for key in BEFORE_CHOICES:
        out += [line for said_line in spec.get(key, []) for line in said(group, where, said_line)]
    for choice in spec.get("choices", []):
        out.append(Line(group, where, "player", choice["text"]))
        out.append(Line(group, where, replier(spec, choice) or "narrator", choice["reply"]))
    for key in OUTCOMES:
        out += [line for said_line in spec.get(key, []) for line in said(group, where, said_line)]
    if "short" in spec:
        out.append(Line(group, where, partner(spec), spec["short"]))
    out += [Line(group, where, "narrator", spec[key]) for key in NOTICES if key in spec]
    out += [Line(group, where, clue.get("by", "narrator"), clue["text"]) for clue in spec.get("clues", [])]
    for person, remarks in sorted(spec.get("remarks", {}).items()):
        out += [Line(group, where, person, remark) for remark in remarks]
    out += [line for said_line in spec.get("done", []) for line in said(group, where, said_line)]
    for reward in [spec.get("reward"), *(choice.get("reward") for choice in spec.get("choices", []))]:
        if reward:
            out.append(Line(group, where, "narrator", reward["claimed"]))
    if spec.get("memory") in memories:
        out += memory_lines(group, memories[spec["memory"]])
    return out + journal(group, where, spec)


def quest_lines(quests, forms):
    memories = {memory["id"]: memory for memory in quests["memories"]}
    out = []
    for quest in ordered(quests["quests"]):
        out.append(Line(quest["id"], quest["id"], "journal", quest["summary"]))
        out += [
            line for said_line in quest.get("road", []) for line in said(quest["id"], f"{quest['id']}/road", said_line)
        ]
        for spec in quest["steps"]:
            out += [
                Line(line.group, line.step, line.by, filled)
                for line in step_lines(quest, spec, memories)
                for filled in forms.fill(line.text, dawns=spec.get("dawns", 0))
            ]
        if quest.get("reward"):
            out.append(Line(quest["id"], quest["id"], "narrator", quest["reward"]["claimed"]))
    return out


def epilogue_lines(leaves, forms):
    out = []
    for leaf in leaves:
        if leaf.get("told"):
            for teller in TELLERS:
                out += [
                    Line("years_later", "years_later", teller, text)
                    for text in forms.fill(leaf["text"], with_pip=teller != "pip_grown")
                ]
        else:
            teller = "pip_grown" if leaf.get("by") == PIP else leaf.get("by", "narrator")
            out += [
                Line("years_later", "years_later", teller, text)
                for text in forms.fill(leaf["text"], with_pip=teller != "pip_grown")
            ]
    return out


def people_lines(people):
    out = []
    for person, spec in sorted(people.items()):
        where = f"people/{person}"
        for kind in FIRE_LINES:
            out += [Line("people", where, person, text) for text in spec.get(kind, [])]
        if "lends" in spec:
            out.append(Line("people", where, person, spec["lends"]))
        for _, texts in sorted(spec.get("acts", {}).items()):
            out += [Line("people", where, person, text) for text in texts]
    return out


def books():
    written = tomllib.loads((STORY / "books.toml").read_text())
    eyes = tomllib.loads((ROOT / "generator/eyes.toml").read_text())["eyes"]
    entries = [(entry["id"], entry["author"], entry["pages"]) for entry in written["found"]]
    entries += [(f"people/{eye}", spec["people"], spec["lore"]) for eye, spec in sorted(eyes.items())]
    entries += [
        (entry["id"], collection["author"], entry["pages"])
        for collection in written["collections"]
        for entry in collection["books"]
    ]
    return entries


def book_lines():
    return [Line(book, book, f"book:{author}", page) for book, author, pages in books() for page in pages]


def scene_lines(scenes):
    out = []
    for kind, spec in scenes.items():
        group = f"scene/{kind}"
        out += [Line(group, group, "stranger", text) for text in spec.get("voice", [])]
        if "greeting" in spec:
            out.append(Line(group, group, "pedlar", spec["greeting"]))
        out += [Line(group, group, "herder", spec[key]) for key in ("gift", "warning") if key in spec]
        out += [Line(group, group, "herder", text) for text in spec.get("again", [])]
        if "seen" in spec:
            out.append(Line(group, group, "narrator", spec["seen"]))
        out += [Line(group, group, "narrator", text) for _, text in sorted(spec.get("clues", {}).items())]
        out += [line for said_line in spec.get("ask", []) for line in said(group, group, said_line)]
        for choice in spec.get("choices", []):
            out.append(Line(group, group, "player", choice["text"]))
            out.append(Line(group, group, "narrator", choice["reply"]))
        for key in ("hid", "won"):
            out += [line for said_line in spec.get(key, []) for line in said(group, group, said_line)]
    return out


def forge_short(forge):
    return [
        forge["short"]
        .replace("{wants}", forge["wants"][shape])
        .replace("{need}", forge["numbers"][need])
        .replace("{have}", forge["numbers"][have])
        for shape, need in sorted(forge["lumps"].items())
        for have in range(need)
    ]


def gear_lines(gear):
    forge = gear["forge"]
    out = [
        Line("gear", "gear", "player" if key in FORGE_ANSWERS else "hob", text)
        for key, value in sorted(forge.items())
        if isinstance(value, str)
        for text in (forge_short(forge) if key == FORGE_SHORT else [value])
    ]
    return out + [Line("gear", f"gear/{line['id']}", line["by"], line["text"]) for line in gear["lines"]]


def dice_lines(dice):
    return [Line("dice", "dice", by, dice[key]) for key, by in DICE.items()]


def lang_lines(read, fills, forms):
    lang = json.loads(LANG.read_text())
    out = []
    for key, text in lang.items():
        speakers = [by for by, patterns in sorted(read.items()) if any(fnmatch.fnmatchcase(key, p) for p in patterns)]
        if speakers:
            for filled in (
                [text.replace("%s", value, 1) for value in getattr(forms, fills[key])] if key in fills else [text]
            ):
                out.append(Line(f"lang/{key.split('.')[1]}", key, speakers[0], filled))
    return out


def voices():
    return tomllib.loads((STORY / "voices.toml").read_text())


def lines(config=None):
    config = config or voices()
    quests = tomllib.loads((STORY / "quests.toml").read_text())
    people = tomllib.loads((STORY / "people.toml").read_text())
    forms = Forms(quests, people)
    every = [
        *quest_lines(quests, forms),
        *epilogue_lines(quests["epilogue"], forms),
        *people_lines(people),
        *book_lines(),
        *scene_lines(tomllib.loads((STORY / "scenes.toml").read_text())["scenes"]),
        *dice_lines(tomllib.loads((STORY / "dice.toml").read_text())),
        *gear_lines(tomllib.loads((STORY / "gear.toml").read_text())),
        *lang_lines(config["lang"], config.get("fills", {}), forms),
    ]
    counted = {}
    out = []
    for line in every:
        if WORD.search(line.shown):
            counted[line.group] = counted.get(line.group, 0) + 1
            out.append(Line(line.group, line.step, line.by, line.text, counted[line.group]))
    return out


def readers(config):
    out = {}
    for voice, spec in config["voices"].items():
        for speaker in spec["reads"]:
            out.setdefault(speaker, []).append(voice)
    return out


def chosen(config):
    return {spec["chosen"]: voice for voice, spec in sorted(config["voices"].items()) if "chosen" in spec}


def sent(config, text):
    for word, said_as in sorted(config["say"].items()):
        text = re.sub(rf"\b{re.escape(word)}\b", said_as, text)
    return text


def takes(config, lines):
    voice_of = readers(config)
    out = []
    for line in lines:
        if line.recordable():
            for voice in voice_of.get(line.by, []):
                text = sent(config, line.text)
                out.append(Take(line, voice, text, hashlib.sha256(f"{voice}\n{text}".encode()).hexdigest()[:16]))
    return out


def gazed(config, take):
    return take.line.by in config["gaze"]["reads"]


def clip_path(key):
    return CLIPS / f"{key}.ogg"


def recordings():
    return json.loads(RECORDINGS.read_text()) if RECORDINGS.exists() else {}


def recorded(done, take):
    return take.key in done and clip_path(take.key).exists()


def index(config, lines, done):
    clips = {}
    for take in takes(config, lines):
        if recorded(done, take):
            clips.setdefault(take.voice, {}).setdefault(take.line.shown, [take.key, done[take.key]["seconds"]])
    fixed = {speaker: voices[0] for speaker, voices in readers(config).items() if speaker not in CHOSEN}
    return {
        "chosen": chosen(config),
        "clips": clips,
        "gaze": sorted(config["gaze"]["reads"]),
        "speakers": fixed,
    }


def write_index(config=None):
    config = config or voices()
    INDEX.parent.mkdir(parents=True, exist_ok=True)
    INDEX.write_text(json.dumps(index(config, lines(config), recordings()), indent=2, sort_keys=True) + "\n")

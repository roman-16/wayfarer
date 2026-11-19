import json
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

import instance
import tomllib

ROOT = Path(__file__).resolve().parent.parent
PACK = ROOT / "pack"
FETCHED = ROOT / "generator/credits.json"
MODRINTH = "https://api.modrinth.com"
AGENT = {"User-Agent": "roman-16/wayfarer"}
FOLDERS = {"mods": "Mods", "resourcepacks": "Resource packs", "shaderpacks": "Shader packs"}
OWNED = "LicenseRef-"


def write(music, effects, faces, voices, main):
    works = listed()
    known = json.loads(FETCHED.read_text()) if FETCHED.exists() else {}
    kept = {key: known[key] for key, work in works.items() if key in known and known[key]["project"] == work["project"]}
    missing = {key: work for key, work in works.items() if key not in kept}
    kept |= modrinth({key: work for key, work in missing.items() if work["site"] == "modrinth"})
    kept |= curseforge({key: work for key, work in missing.items() if work["site"] == "curseforge"})
    FETCHED.write_text(json.dumps(dict(sorted(kept.items())), indent=2, ensure_ascii=False) + "\n")
    sections = {
        f"{name} in the pack": [
            entry(works[key]["name"], kept[key]["authors"], kept[key]["licence"], kept[key]["page"])
            for key in sorted(
                (key for key in works if key.startswith(folder + "/")), key=lambda key: works[key]["name"].casefold()
            )
        ]
        for folder, name in FOLDERS.items()
    } | {
        "Music in the Wayfarer mod": [
            f"- {piece['credit']}. {piece['licence']}. {piece['page']}" for _, piece in sorted(music.items())
        ],
        "Sounds in the Wayfarer mod": [
            f"- {effect['credit']}. {effect['licence']}. {effect['page']}" for _, effect in sorted(effects.items())
        ],
        "Fonts in the Wayfarer mod": [
            entry(face["name"], [face["author"]], face["licence"], face["url"]) for _, face in sorted(faces.items())
        ],
        "Voices in the Wayfarer mod": [
            f"- The story's {len(voices)} voices, designed for Wayfarer and made with ElevenLabs. https://elevenlabs.io"
        ],
    }
    (PACK / "CREDITS.md").write_text(
        "# Credits\n" + "".join(f"\n{name}:\n\n" + "\n".join(lines) + "\n" for name, lines in sections.items())
    )
    (main / "wayfarer/credits.json").write_text(
        json.dumps(
            {"mods": sum(1 for key in works if key.startswith("mods/")), "music": composers(music)},
            indent=2,
            ensure_ascii=False,
        )
        + "\n"
    )


def listed():
    works = {}
    for folder in FOLDERS:
        for path in sorted((PACK / folder).glob("*.pw.toml")):
            meta = tomllib.loads(path.read_text())
            update = meta["update"]
            site = "modrinth" if "modrinth" in update else "curseforge"
            project = update["modrinth"]["mod-id"] if site == "modrinth" else str(update["curseforge"]["project-id"])
            works[f"{folder}/{path.name.removesuffix('.pw.toml')}"] = {
                "meta": meta,
                "name": meta["name"],
                "project": project,
                "site": site,
            }
    return works


def entry(name, authors, licence, page):
    return f"- {name}, by {joined(authors)}. {licence}. {page}"


def joined(names):
    return names[0] if len(names) == 1 else ", ".join(names[:-1]) + " and " + names[-1]


def fetched(path, ids):
    query = urllib.parse.urlencode({"ids": json.dumps(sorted(set(ids)))})
    request = urllib.request.Request(f"{MODRINTH}{path}?{query}", headers=AGENT)
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def modrinth(works):
    if not works:
        return {}
    projects = {
        project["id"]: project for project in fetched("/v2/projects", [work["project"] for work in works.values()])
    }
    teams = {
        members[0]["team_id"]: members
        for members in fetched("/v2/teams", [project["team"] for project in projects.values()])
        if members
    }
    owners = [project["organization"] for project in projects.values() if project.get("organization")]
    organisations = (
        {organisation["id"]: organisation["name"] for organisation in fetched("/v3/organizations", owners)}
        if owners
        else {}
    )
    out = {}
    for key, work in works.items():
        project = projects.get(work["project"])
        if project is None:
            raise SystemExit(f"credits failed: Modrinth has no project {work['project']} for pack/{key}.pw.toml")
        members = sorted(
            (member for member in teams.get(project["team"], []) if member.get("accepted", True)),
            key=lambda member: (
                member["role"] != "Owner",
                member.get("ordering", 0),
                member["user"]["username"].casefold(),
            ),
        )
        authors = (
            [organisations[project["organization"]]]
            if project.get("organization") in organisations
            else [member["user"]["username"] for member in members]
        )
        out[key] = {
            "authors": authors,
            "licence": licence(project["license"]),
            "page": f"https://modrinth.com/{project['project_type']}/{project['slug']}",
            "project": work["project"],
        }
    return out


def licence(spec):
    if not spec["id"].startswith(OWNED):
        return spec["id"]
    named = spec["id"].removeprefix(OWNED).replace("-", " ")
    return f"{named} ({spec['url']})" if spec.get("url") else named


def curseforge(works):
    out = {}
    for key, work in works.items():
        meta = work["meta"]
        jar = instance.download(
            instance.source(meta), meta["download"]["hash-format"], meta["download"]["hash"], meta["name"]
        )
        with zipfile.ZipFile(jar) as archive:
            declared = tomllib.loads(archive.read("META-INF/neoforge.mods.toml").decode())
        mod = declared["mods"][0]
        out[key] = {
            "authors": [
                author.strip()
                for author in mod.get("authors", declared.get("authors", "")).replace(" and ", ",").split(",")
                if author.strip()
            ],
            "licence": declared["license"],
            "page": f"https://www.curseforge.com/minecraft/mc-mods/{key.split('/', 1)[1]}",
            "project": work["project"],
        }
    return out


def composers(music):
    pieces = {}
    for _, piece in sorted(music.items()):
        title, by = piece["credit"].rsplit(", by ", 1)
        pieces.setdefault(by, []).append(title)
    return [
        f"{joined(titles)}, by {by}"
        for by, titles in sorted(pieces.items(), key=lambda item: (-len(item[1]), item[0].casefold()))
    ]

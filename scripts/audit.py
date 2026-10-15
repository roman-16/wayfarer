import gzip
import json
import re
import struct
import sys
import zipfile
from pathlib import Path

import tomllib

ROOT = Path(__file__).resolve().parent.parent
MODS_TOML = ROOT / "mod/src/main/templates/META-INF/neoforge.mods.toml"
OVERRIDES = [
    ROOT / "mod/src/main/resources/data",
    ROOT / "mod/src/generated/resources/data",
]


def ingredients(node):
    if isinstance(node, dict):
        for key, value in node.items():
            if key == "result":
                continue
            if key == "item" and isinstance(value, str):
                yield value
            elif key == "tag" and isinstance(value, str):
                yield "#" + value
            else:
                yield from ingredients(value)
    elif isinstance(node, list):
        for value in node:
            yield from ingredients(value)


def overridden(recipe):
    namespace, path = recipe.split(":", 1)
    return any((base / namespace / "recipe" / f"{path}.json").exists() for base in OVERRIDES)


def recipes(jars):
    nether = tomllib.loads((ROOT / "generator/nether.toml").read_text())
    accepted = set(nether["unobtainable"])
    unsourced = set(nether["unsourced_items"]) | {"#" + tag for tag in nether["unsourced_tags"]}
    findings = {}
    for jar in jars:
        with zipfile.ZipFile(jar) as archive:
            for name in archive.namelist():
                parts = name.split("/")
                if (
                    len(parts) < 4
                    or parts[0] != "data"
                    or parts[2] not in ("recipe", "recipes")
                    or not name.endswith(".json")
                ):
                    continue
                recipe = f"{parts[1]}:{'/'.join(parts[3:])[:-5]}"
                if recipe in accepted or overridden(recipe):
                    continue
                try:
                    data = json.loads(archive.read(name))
                except ValueError:
                    continue
                needs = sorted(set(ingredients(data)) & unsourced)
                if needs:
                    findings[recipe] = (jar.name, needs)
    for recipe, (jar, needs) in sorted(findings.items()):
        yield f"{recipe} ({jar}) needs {', '.join(needs)}: give it an Overworld source or list it in generator/nether.toml"


def features(jars):
    spread = set(tomllib.loads((ROOT / "balance.toml").read_text())["structures"]["spread_features"])
    natural = set(tomllib.loads((ROOT / "generator/features.toml").read_text())["natural"])
    kinds = {}
    for jar in jars:
        with zipfile.ZipFile(jar) as archive:
            for name in archive.namelist():
                parts = name.split("/")
                if (
                    len(parts) < 5
                    or parts[0] != "data"
                    or parts[2:4] != ["worldgen", "configured_feature"]
                    or not name.endswith(".json")
                ):
                    continue
                try:
                    kind = json.loads(archive.read(name).decode("utf-8-sig")).get("type", "")
                except ValueError:
                    continue
                if ":" in kind and not kind.startswith("minecraft:"):
                    kinds.setdefault(parts[1], set()).add(kind)
    for namespace, found in sorted(kinds.items()):
        if namespace not in spread | natural:
            yield f"{namespace} places features of its own kind ({', '.join(sorted(found))}): spread them in balance.toml [structures] spread_features, or list them as natural in generator/features.toml"


def merges(path):
    merging = (
        "/tags/" in path.as_posix()
        or "/data_maps/" in path.as_posix()
        or path.as_posix().endswith("loot_modifiers/global_loot_modifiers.json")
    )
    return merging and not json.loads(path.read_text()).get("replace")


def load_order(jars):
    mod_id = re.search(r"(?m)^mod_id=(\S+)", (ROOT / "mod/gradle.properties").read_text()).group(1)
    declared = tomllib.loads(MODS_TOML.read_text().replace("${mod_id}", mod_id))["dependencies"][mod_id]
    after = {dependency["modId"] for dependency in declared if dependency.get("ordering") == "AFTER"}
    owners = {}
    for jar in jars:
        with zipfile.ZipFile(jar) as archive:
            names = archive.namelist()
            if "META-INF/neoforge.mods.toml" not in names:
                continue
            found = re.search(
                r"modId\s*=\s*[\"']([^\"']+)", archive.read("META-INF/neoforge.mods.toml").decode("utf-8", "replace")
            )
            if not found or found.group(1) == mod_id:
                continue
            for name in names:
                if name.startswith("data/"):
                    owners.setdefault(name, set()).add(found.group(1))
    unordered = {}
    for base in OVERRIDES:
        for path in base.glob("*/**/*"):
            name = "data/" + path.relative_to(base).as_posix()
            if path.is_file() and name in owners and not merges(path):
                for owner in owners[name] - after:
                    unordered.setdefault(owner, []).append(name)
    for owner, names in sorted(unordered.items()):
        yield f"the Wayfarer mod replaces {len(names)} of {owner}'s files, such as {min(names)}, but does not load after it: add {owner} with ordering AFTER to {MODS_TOML.relative_to(ROOT)}"


def named_strings(data, name):
    marker = b"\x08" + struct.pack(">H", len(name)) + name
    start = data.find(marker)
    while start != -1:
        offset = start + len(marker)
        (length,) = struct.unpack_from(">H", data, offset)
        yield data[offset + 2 : offset + 2 + length].decode("utf-8", "replace")
        start = data.find(marker, offset + 2 + length)


def template_contents(data):
    return set(named_strings(data, b"Name")), set(named_strings(data, b"pool"))


def worldgen_data(jars):
    found = {"structure": {}, "structure_set": {}, "template_pool": {}}
    templates = {}
    for jar in jars:
        with zipfile.ZipFile(jar) as archive:
            for name in archive.namelist():
                parts = name.split("/")
                if len(parts) < 4 or parts[0] != "data":
                    continue
                if parts[2] in ("structure", "structures") and name.endswith(".nbt"):
                    templates[f"{parts[1]}:{'/'.join(parts[3:])[:-4]}"] = (jar, name)
                elif len(parts) >= 5 and parts[2] == "worldgen" and parts[3] in found and name.endswith(".json"):
                    try:
                        found[parts[3]][f"{parts[1]}:{'/'.join(parts[4:])[:-5]}"] = json.loads(
                            archive.read(name).decode("utf-8-sig")
                        )
                    except ValueError:
                        continue
    for base in OVERRIDES:
        for kind, entries in found.items():
            for path in base.glob(f"*/worldgen/{kind}/**/*.json"):
                folder = path.relative_to(base).parts
                entries[f"{folder[0]}:{'/'.join(folder[3:])[:-5]}"] = json.loads(path.read_text())
    return found, templates


def pool_templates(pool):
    for entry in pool.get("elements", []):
        stack = [entry.get("element", {})]
        while stack:
            element = stack.pop()
            stack.extend(element.get("elements", []))
            location = element.get("location")
            if isinstance(location, str):
                yield location if ":" in location else "minecraft:" + location


def portals(jars):
    found, templates = worldgen_data(jars)
    cut = set(tomllib.loads((ROOT / "generator/owners.toml").read_text())["cut"])
    placed = {
        entry["structure"]
        for structure_set in found["structure_set"].values()
        for entry in structure_set.get("structures", [])
        if isinstance(entry, dict)
    }
    contents, archives = {}, {}
    for structure, data in sorted(found["structure"].items()):
        biomes = data.get("biomes")
        if structure not in placed or structure in cut or biomes == [] or not isinstance(data.get("start_pool"), str):
            continue
        pools, seen, lit = [data["start_pool"]], set(), []
        while pools:
            pool = pools.pop()
            if pool in seen or pool not in found["template_pool"]:
                continue
            seen.add(pool)
            pools.append(found["template_pool"][pool].get("fallback", ""))
            for template in pool_templates(found["template_pool"][pool]):
                if template not in templates:
                    continue
                if template not in contents:
                    jar, name = templates[template]
                    if jar not in archives:
                        archives[jar] = zipfile.ZipFile(jar)
                    contents[template] = template_contents(gzip.decompress(archives[jar].read(name)))
                names, linked = contents[template]
                if "minecraft:nether_portal" in names:
                    lit.append(template)
                pools.extend(linked)
        if lit:
            yield f"{structure} still generates a lit Nether portal in {', '.join(sorted(set(lit)))}: cut it in generator/owners.toml"
    for archive in archives.values():
        archive.close()


def main():
    jars = sorted(Path(sys.argv[1]).glob("*.jar"))
    findings = [*recipes(jars), *features(jars), *portals(jars), *load_order(jars)]
    for finding in findings:
        print(finding)
    if findings:
        sys.exit(f"{len(findings)} audit findings")
    print(
        "no recipe needs a Nether-only item, no structure generates a lit Nether portal, every replaced file loads after its mod, and every structure-like feature is spread or natural"
    )


main()

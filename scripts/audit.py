import json
import sys
import zipfile
from pathlib import Path

import tomllib

ROOT = Path(__file__).resolve().parent.parent
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


def shared_biomes(jars):
    emptied = {"#" + tag for tag in tomllib.loads((ROOT / "generator/removals.toml").read_text())["structure_biomes"]}
    users = {tag: set() for tag in emptied}
    for jar in jars:
        with zipfile.ZipFile(jar) as archive:
            for name in archive.namelist():
                parts = name.split("/")
                if (
                    len(parts) < 5
                    or parts[0] != "data"
                    or parts[2:4] != ["worldgen", "structure"]
                    or not name.endswith(".json")
                ):
                    continue
                try:
                    biomes = json.loads(archive.read(name).decode("utf-8-sig")).get("biomes")
                except ValueError:
                    continue
                if isinstance(biomes, str) and biomes in users:
                    users[biomes].add(f"{parts[1]}:{'/'.join(parts[4:])[:-5]}")
    for tag, structures in sorted(users.items()):
        if len(structures) > 1:
            yield f"generator/removals.toml empties {tag}, which {', '.join(sorted(structures))} all use: remove the one structure through its structure set instead"


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


def main():
    jars = sorted(Path(sys.argv[1]).glob("*.jar"))
    findings = [*recipes(jars), *shared_biomes(jars), *features(jars)]
    for finding in findings:
        print(finding)
    if findings:
        sys.exit(f"{len(findings)} audit findings")
    print(
        "no recipe needs a Nether-only item, no emptied biome tag removes more than its structure, and every structure-like feature is spread or natural"
    )


main()

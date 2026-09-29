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


def main():
    nether = tomllib.loads((ROOT / "generator/nether.toml").read_text())
    accepted = set(nether["unobtainable"])
    unsourced = set(nether["unsourced_items"]) | {"#" + tag for tag in nether["unsourced_tags"]}
    findings = {}
    for jar in sorted(Path(sys.argv[1]).glob("*.jar")):
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
        print(f"{recipe} ({jar}) needs {', '.join(needs)}")
    if findings:
        sys.exit(
            f"{len(findings)} recipes need a Nether-only item: give them an Overworld source or list them in generator/nether.toml"
        )
    print("no recipe needs a Nether-only item")


main()

import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FOLDER = ROOT / "generator/tweaks"
OWN = ROOT / "mod/src/main/resources"


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


def kept(name, archive, unsourced):
    parts = name.split("/")
    if name.endswith("/") or parts[0] not in ("assets", "data"):
        return False
    if parts[0] == "assets":
        return True
    if len(parts) < 4 or parts[2] not in ("recipe", "tags"):
        return False
    return parts[2] == "tags" or not set(ingredients(json.loads(archive.read(name)))) & unsourced


def write(main, nether, spec):
    unsourced = set(nether["unsourced_items"]) | {"#" + tag for tag in nether["unsourced_tags"]}
    for kind in ("resources", "crafting"):
        with zipfile.ZipFile(FOLDER / f"{kind}.zip") as archive:
            for name in archive.namelist():
                if not kept(name, archive, unsourced):
                    continue
                target = main / name
                if target.exists() or (OWN / name).exists():
                    raise SystemExit(f"tweaks failed: Vanilla Tweaks' {name} would replace one of the pack's own files")
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(archive.read(name))
    credits = [f"Credits:\n{spec['credit']}\n"]
    with zipfile.ZipFile(FOLDER / "resources.zip") as archive:
        credits += [archive.read(name).decode() for name in archive.namelist() if name.endswith("_credits.txt")]
    (main / "credits.txt").write_text("\n".join(credits))

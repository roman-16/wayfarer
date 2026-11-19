import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OWN = ROOT / "mod/src/main/resources"
FOLDER = "data/wayfarer/recipe"
SURROUNDED = ["###", "#O#", "###"]
SURROUNDING = 8


def ingredient(name):
    return {"tag": name[1:]} if name.startswith("#") else {"item": name}


def either(names):
    return [ingredient(name) for name in names] if len(names) > 1 else ingredient(names[0])


def result(name, count=1):
    return {"id": name, "count": count}


def recipes(spec):
    colours = spec["colours"]
    dyeing = spec["dyeing"]
    for slab, block in spec["joined"]["slabs"].items():
        yield (
            f"joined/{block.split(':')[1]}_from_slabs",
            {
                "type": "minecraft:crafting_shaped",
                "pattern": ["##"],
                "key": {"#": ingredient(slab)},
                "result": result(block),
            },
        )
    for stairs, block in spec["joined"]["stairs"].items():
        yield (
            f"joined/{block.split(':')[1]}_from_stairs",
            {
                "type": "minecraft:crafting_shapeless",
                "ingredients": [ingredient(stairs)] * 4,
                "result": result(block, 3),
            },
        )
    for block, slab in spec["split"].items():
        yield (
            f"split/{slab.split(':')[1]}",
            {
                "type": "minecraft:crafting_shapeless",
                "ingredients": [ingredient(block)],
                "result": result(slab, 2),
            },
        )
    for family in dyeing["families"]:
        dyed = [f"minecraft:{colour}_{family}" for colour in colours]
        for colour, target in zip(colours, dyed, strict=True):
            yield (
                f"dyeing/{colour}_{family}",
                {
                    "type": "minecraft:crafting_shaped",
                    "group": f"dyeing_{family}",
                    "pattern": SURROUNDED,
                    "key": {
                        "#": [ingredient(other) for other in dyed if other != target],
                        "O": either(spec["dyes"].get(colour, [f"minecraft:{colour}_dye"])),
                    },
                    "result": result(target, SURROUNDING),
                },
            )
        if family in dyeing["bleached"]:
            target = dyeing["bleached"][family]
            yield (
                f"dyeing/{target.split(':')[1]}",
                {
                    "type": "minecraft:crafting_shaped",
                    "pattern": SURROUNDED,
                    "key": {"#": [ingredient(other) for other in dyed], "O": ingredient(dyeing["bleach"])},
                    "result": result(target, SURROUNDING),
                },
            )
    for source, target in spec["smelted"].items():
        for colour in colours:
            yield (
                f"smelted/{colour}_{target}",
                {
                    "type": "minecraft:smelting",
                    "ingredient": ingredient(f"minecraft:{colour}_{source}"),
                    "result": {"id": f"minecraft:{colour}_{target}"},
                    "experience": 0.1,
                    "cookingtime": 200,
                },
            )
    for recipe in spec["shaped"]:
        yield (
            recipe["id"],
            {
                "type": "minecraft:crafting_shaped",
                "pattern": recipe["pattern"],
                "key": {symbol: ingredient(name) for symbol, name in recipe["key"].items()},
                "result": result(recipe["result"], recipe.get("count", 1)),
            },
        )
    for recipe in spec["shapeless"]:
        yield (
            recipe["id"],
            {
                "type": "minecraft:crafting_shapeless",
                "ingredients": [ingredient(name) for name in recipe["ingredients"]],
                "result": result(recipe["result"], recipe.get("count", 1)),
            },
        )


def named(node):
    if isinstance(node, dict):
        if "item" in node:
            yield node["item"]
        elif "tag" in node:
            yield "#" + node["tag"]
        else:
            for key, value in node.items():
                if key != "result":
                    yield from named(value)
    elif isinstance(node, list):
        for value in node:
            yield from named(value)


def write(main, nether, spec):
    unsourced = set(nether["unsourced_items"]) | {"#" + tag for tag in nether["unsourced_tags"]}
    for name, recipe in recipes(spec):
        if set(named(recipe)) & unsourced:
            continue
        path = f"{FOLDER}/{name}.json"
        if (OWN / path).exists():
            raise SystemExit(f"crafting failed: {name} would replace one of the pack's own recipes")
        target = main / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(recipe, indent=2) + "\n")

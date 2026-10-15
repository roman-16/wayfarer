import json

LEGACY_COLOUR = "\u00a7"


def write(main, spec):
    files = {}
    for enchantment, entry in sorted(spec["enchantments"].items()):
        namespace, path = enchantment.split(":")
        names = files.setdefault(entry["key"].split(".")[1], {})
        names[entry["key"]] = f"{LEGACY_COLOUR}{entry['colour']}{spec['icons'][entry['icon']]} {entry['name']}"
        if "description" in entry:
            files.setdefault(namespace, {})[f"enchantment.{namespace}.{path}.desc"] = entry["description"]
    for namespace, lines in sorted(files.items()):
        target = main / f"assets/{namespace}/lang/en_us.json"
        if target.exists():
            raise SystemExit(f"enchantments failed: {target} is written twice")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(dict(sorted(lines.items())), indent=2, ensure_ascii=False) + "\n")

import re
import subprocess
import sys
from pathlib import Path

import tomllib

ROOT = Path(__file__).resolve().parent.parent
DIST = ROOT / "dist"
REPOSITORY = "roman-16/wayfarer"


def fail(why):
    sys.exit(f"publish failed: {why}")


def git(*arguments):
    return subprocess.run(["git", *arguments], cwd=ROOT, capture_output=True, text=True, check=True).stdout.strip()


def notes(version):
    changelog = (ROOT / "CHANGELOG.md").read_text()
    found = re.search(rf"^## \[{re.escape(version)}\][^\n]*\n(.*?)(?=^## |^\[|\Z)", changelog, re.MULTILINE | re.DOTALL)
    if not found:
        fail(f"CHANGELOG.md has no section for {version}")
    return found.group(1).strip() + "\n"


def main():
    version = tomllib.loads((ROOT / "pack/pack.toml").read_text())["version"]
    tag = f"v{version}"
    major = version.split(".")[0]
    files = [
        DIST / f"wayfarer-{version}.jar",
        DIST / f"Wayfarer-{version}-pack.zip",
        DIST / f"Wayfarer-{major}.zip",
        DIST / f"Wayfarer-{version}-server.zip",
    ]
    missing = [file.name for file in files if not file.exists()]
    if missing:
        fail(f"dist/ has no {', '.join(missing)}; run just pack")
    if git("status", "--porcelain"):
        fail("the working tree has uncommitted changes; commit and push first")
    head = git("rev-parse", "HEAD")
    if (
        subprocess.run(
            ["git", "branch", "--remotes", "--contains", head], cwd=ROOT, capture_output=True, text=True
        ).stdout.strip()
        == ""
    ):
        fail("the commit is not pushed; push first")
    if subprocess.run(["gh", "release", "view", tag, "--repo", REPOSITORY], capture_output=True).returncode == 0:
        fail(f"{tag} is already released; raise the version in pack/pack.toml and add its section to CHANGELOG.md")
    subprocess.run(
        [
            "gh",
            "release",
            "create",
            tag,
            *map(str, files),
            "--repo",
            REPOSITORY,
            "--target",
            head,
            "--title",
            f"Wayfarer {version}",
            "--notes-file",
            "-",
        ],
        input=notes(version),
        text=True,
        check=True,
    )
    print(f"published {tag}; Pages serves it at https://roman-16.github.io/wayfarer/{major}/ once its workflow has run")


main()

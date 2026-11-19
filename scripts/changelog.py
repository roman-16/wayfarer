import argparse
import datetime
import itertools
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CHANGELOG = ROOT / "CHANGELOG.md"
UNRELEASED = "## [Unreleased]"
HEADING = re.compile(r"^## .*$", re.MULTILINE)
RELEASE = re.compile(r"^## \[(\d+)\.(\d+)\.(\d+)\] - (\d{4}-\d{2}-\d{2})$")
LINK = re.compile(r"^\[[^\]]+\]: ", re.MULTILINE)


def fail(why):
    sys.exit(f"changelog failed: {why}")


def sections(text):
    for heading, following in itertools.pairwise([*HEADING.finditer(text), None]):
        end = following.start() if following else len(text)
        link = LINK.search(text, heading.end(), end)
        yield heading.group(0), text[heading.end() : link.start() if link else end].strip()


def releases(text):
    for heading, body in sections(text):
        found = RELEASE.match(heading)
        if found:
            yield tuple(map(int, found.group(1, 2, 3))), found.group(4), body


def successors(version):
    major, minor, patch = version
    return {(major, minor, patch + 1), (major, minor + 1, 0), (major + 1, 0, 0)}


def spelled(version):
    return ".".join(map(str, version))


def problems(text):
    headings = [heading for heading, _ in sections(text)]
    if not headings or headings[0] != UNRELEASED:
        yield f"CHANGELOG.md opens with {headings[0] if headings else 'no section'}, not {UNRELEASED}"
    for heading in headings[1:]:
        if heading == UNRELEASED:
            yield f"CHANGELOG.md has {UNRELEASED} below a version"
        elif not RELEASE.match(heading):
            yield f"CHANGELOG.md's {heading!r} is not a version heading like '## [1.2.3] - 2026-11-19'"
    listed = list(releases(text))
    for version, date, _ in listed:
        try:
            datetime.date.fromisoformat(date)
        except ValueError:
            yield f"CHANGELOG.md dates [{spelled(version)}] {date}, which is no day"
    for (newer, newer_date, _), (older, older_date, _) in itertools.pairwise(listed):
        if newer not in successors(older):
            following = ", ".join(spelled(version) for version in sorted(successors(older)))
            yield f"CHANGELOG.md: [{spelled(newer)}] follows [{spelled(older)}]; the next version is one of {following}"
        if newer_date < older_date:
            yield f"CHANGELOG.md dates [{spelled(newer)}] {newer_date}, before [{spelled(older)}] on {older_date}"


def newest(text):
    for version, _, body in releases(text):
        return spelled(version), body
    fail("CHANGELOG.md has no version section like '## [1.2.3] - 2026-11-19'")


def version():
    return newest(CHANGELOG.read_text())[0]


def main():
    parser = argparse.ArgumentParser(
        description="Print the version CHANGELOG.md names: its newest dated section, which is the version of the tree and the one a push to main releases"
    )
    parser.add_argument(
        "--check", action="store_true", help="print what is wrong with the file and fail when anything is"
    )
    parser.add_argument("--notes", action="store_true", help="print the newest version's section, its release notes")
    arguments = parser.parse_args()
    text = CHANGELOG.read_text()
    if arguments.check:
        found = list(problems(text))
        for problem in found:
            print(problem)
        if found:
            fail(f"{len(found)} problems in CHANGELOG.md")
        return
    named, body = newest(text)
    print(body if arguments.notes else named)


if __name__ == "__main__":
    main()

import os
import re
import shutil
import subprocess
import sys
import zipfile
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "build/sources"
MODS = ROOT / "build/client/mods"
ARTIFACTS = ROOT / "mod/build/moddev/artifacts"


def replace(target, build):
    partial = target.with_name(target.name + ".partial")
    shutil.rmtree(partial, ignore_errors=True)
    build(partial)
    shutil.rmtree(target, ignore_errors=True)
    partial.rename(target)


def prune(folder, keep):
    for path in folder.glob("*"):
        if path.name not in keep:
            if path.is_dir():
                shutil.rmtree(path)
            else:
                path.unlink()


def game():
    version = re.search(r"(?m)^neo_version=(\S+)", (ROOT / "mod/gradle.properties").read_text()).group(1)
    target = SOURCES / f"neoforge-{version}"
    if not target.exists():

        def unpack(partial):
            for jar in (
                f"neoforge-{version}-sources.jar",
                f"neoforge-{version}-client-extra-aka-minecraft-resources.jar",
            ):
                with zipfile.ZipFile(ARTIFACTS / jar) as archive:
                    archive.extractall(partial)

        replace(target, unpack)
        print(f"unpacked NeoForge {version} and Minecraft into {target.relative_to(ROOT)}")
    prune(SOURCES, {target.name, "mods"})


def decompile(vineflower, jar):
    target = SOURCES / "mods" / jar.stem
    if target.exists():
        return None
    log = target.with_name(target.name + ".log")

    def run(partial):
        result = subprocess.run(
            ["java", "-Xmx4G", "-jar", str(vineflower), "--log-level=warn", str(jar), str(partial)],
            capture_output=True,
            text=True,
        )
        if result.returncode != 0:
            log.write_text(result.stdout + result.stderr)
            raise RuntimeError(
                f"{jar.name} did not decompile (exit {result.returncode}); its log is {log.relative_to(ROOT)}"
            )

    replace(target, run)
    log.unlink(missing_ok=True)
    print(f"decompiled {jar.name}")
    return jar.name


def mods():
    jars = sorted(MODS.glob("*.jar"))
    if not jars:
        sys.exit(f"no mods in {MODS.relative_to(ROOT)}: run `python3 scripts/instance.py client build/client` first")
    vineflower = next((ROOT / "mod/build/vineflower").glob("vineflower-*.jar"), None)
    if vineflower is None:
        sys.exit("no Vineflower jar: run `just sources`, which fetches it")
    (SOURCES / "mods").mkdir(parents=True, exist_ok=True)
    prune(SOURCES / "mods", {jar.stem for jar in jars} | {jar.stem + ".log" for jar in jars})
    failures, done = [], []
    with ThreadPoolExecutor(max_workers=max(1, (os.cpu_count() or 4) // 4)) as pool:
        for jar, future in [(jar, pool.submit(decompile, vineflower, jar)) for jar in jars]:
            try:
                if future.result():
                    done.append(jar)
            except RuntimeError as error:
                failures.append(str(error))
    for failure in failures:
        print(failure)
    if failures:
        sys.exit(f"{len(failures)} mods did not decompile")
    print(f"{SOURCES.relative_to(ROOT)} holds the game and {len(jars)} mods; {len(done)} decompiled now")


game()
mods()

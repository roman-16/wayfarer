# Wayfarer

A personal modpack repository: it reproduces on any machine from `direnv allow` and `just`, and nothing outside it needs a manual step.

## The rules of the pack

- **The design decides.** `design/` holds what the pack is becoming, and `design/README.md` says the phase and what comes next. Build only what is Settled there; a gap found while building goes back to Open in its document, never decided in code. Design work runs through the `design` skill.
- **The lore decides.** `design/lore.md` is the source of truth. Every mechanic needs a reason there, and every quest, book and item text is written from it with the `lore` skill. A mechanic the lore cannot explain is a mechanic to drop or to give a reason first.
- **Everything looks vanilla.** Every texture, model, animation and structure is made with the `art` skill and judged in the game beside its vanilla relative.
- **There is no Nether.** Nothing may bring it back: no lit portals, no Nether structures, no recipe that needs a Nether-only item without an Overworld source. `just test` audits every recipe in every jar against `generator/nether.toml` and every structure that still generates for a lit portal; a recipe finding is fixed by an Overworld source or accepted there as unobtainable, a portal by cutting its structure in `generator/removals.toml`, never ignored.
- **Every number lives once.** Balancing numbers are in `balance.toml`, and the mod, the generator and the boot test all read them from there. Eyes, their biomes, lore, places and shrine spacing are in `generator/eyes.toml`. A number that tunes something is a power of two, or a sum of two such as 128 + 64 = 192; a number that means something stays as it is, such as time people read, a share like two thirds, a count the story sets, or a fact about vanilla or a mod. A system's numbers move to this when it is rebuilt.
- **Shrine spacing is measured, not guessed.** Biome layout decides how far the next shrine really is, so each tier's `spacing` is tuned by `just survey apply`, which walks journeys to the nearest shrine not yet taken and compares the legs with the targets in `balance.toml`. One survey is noisy (about ±30%): apply, `just generate`, survey again, and stop once the medians sit near the targets.
- **Singleplayer and server behave the same.** Rules live in the mod jar (code and data) or in `pack/config`, never in a world save or a client-only setting.

## Where things go

- `mod/src/generated/`, `pack/config/EndRemastered-NeoForge/Eyes/`, `pack/config/ftbquests/quests/`, `pack/config/improvedmobs/common.toml`, `pack/config/iris.properties`, `pack/config/nohostilesaroundcampfire.json5` and `pack/config/sparsestructures.json5` are written by `just generate`. Change the generator or its sources (`story/quests.toml` for the quest book, `story/books.toml` for the books, `story/fire.toml` for the voice, Maren and the leads, `story/notes.toml` for The Road So Far and Words of the Road), never the output.
- `pack/` is managed by packwiz. Add a mod with `packwiz modrinth add <slug>` from `pack/`; use CurseForge only for a mod that is not on Modrinth. Every mod is `client` or `both`: singleplayer runs the server inside the client, so a `server` mod would be missing there.
- A new config for a mod starts from the default the mod writes on its first boot (`build/server/config` after `just test`, `build/client/config` after `just demo`). Ship only the keys the pack changes when the mod fills in the rest itself, so machine-specific defaults (thread counts and the like) stay per machine.

## Reading the game and the mods

`just sources` writes Minecraft's and NeoForge's code with Parchment names and every mod decompiled with Vineflower, each with its assets and data, into `build/sources/` (`neoforge-<version>/` and `mods/<jar>/`); a mod is decompiled again only when its version changes. Search it with `rg`, naming the path: `rg` skips `build/` and every other generated folder through `.gitignore`.

```console
$ just sources
$ rg "enter_when_safe" build/sources/mods
```

## Seeing the game

Never wait by sleeping. Gates and builds run in the foreground with a generous timeout and return when they finish; the dev client is driven through `scripts/client.py`, whose commands each return the moment the game has finished them:

```console
$ python3 scripts/client.py start            # launch unless running; returns once the showroom is built
$ python3 scripts/client.py run 'fly on' '/tp @s 48.5 12 60 0 20' settle 'shot row'
$ python3 scripts/client.py stop             # returns once the game has exited
$ python3 scripts/client.py tour [eye|camp ...]   # launch, photograph every shrine and the Last Camp, quit
```

The game runs on the user's display from `start` until `stop`, so close every session you open: `stop` as soon as the looking is done, and never hand back to the user with the client still running.

`run` prints the batch log: each step, every chat line the game received (command replies included), errors, and the absolute path of each screenshot, which is written before `run` returns. Steps can also come one per line on stdin. `start --fresh` opens a fresh natural world in survival (`just client`) instead of the showroom (`just demo`). A batch that does not finish within `--timeout` is aborted and its log shows where it stuck.

The client opens on the user's display in the showroom dimension `wayfarer:showroom`: the Last Camp at spawn, the stranger's first and last camps just south of it at z = 24 (x = -16 and 8), each with its name sign on the north side, an item frame wall at z = -10, and the shrines in alphabetical order along x at 40-block steps (hall centres at x = 48, 88, ...; z = 72; the surface at y = 1, each hall as deep as its way down needs), each with its name sign on the north side. The compass works there as a demo: it points at a waymark 750 blocks west of the camp, shows a companion circling the camp and marks the camp's grave. `/locate` finds nothing there; the natural overworld next to it (`/execute in minecraft:overworld run ...`) is kept between launches.

`tour` shoots each shrine from its surface cover down the shaft into the hall, and the Last Camp from every side, into one labelled contact sheet per subject in `build/client/tour/<subject>.jpg`; the hall cameras come from the generator (`mod/src/generated/dev/wayfarer/tour.json`), so every hall is framed from open air with a clear line to what it looks at. It starts the client itself and quits it when done; a whole tour takes about three minutes.

| Step | Does |
|---|---|
| `/<command>` | Runs a command as the player |
| `click <x> <y>` | Clicks the open screen at GUI coordinates |
| `close` | Closes the open screen |
| `find <block> [radius]` | Lists the nearest positions of a block within the horizontal radius (default 32), at any height |
| `fly on\|off` | Toggles creative flight |
| `gui on\|off` | Shows or hides the HUD |
| `key <mapping>` | Presses a key mapping (`key.inventory`); `key escape` closes the open screen |
| `pos` | Logs the player's position, facing and dimension |
| `press <label>` | Clicks the widget with that label on the open screen |
| `quit` | Closes the client |
| `screen` | Logs the open screen and its widgets with labels, positions and sizes |
| `settle` | Waits until earlier commands have run on the server, the chunks around the player are loaded and the terrain is drawn |
| `shot <name>` | Saves a screenshot |
| `type <text>` | Types into the focused field |
| `wait <ticks>` | Waits a fixed time; only for things with no completion signal, such as an animation |

The driver (`mod/src/dev/java/.../Driver.java`) confirms commands with a sentinel (`execute if entity @s`, whose reply it hides), so `sendCommandFeedback` must stay on. After `just generate`, `cd mod && ./gradlew processResources processDevResources` and the step `/reload` load the new data into the running client; new chunks and `/place structure` then use it.

## Gates

Run `just lint`, then `just test`, before calling work done. `just test` builds the server pack, audits recipes, portals and that the Wayfarer mod loads after every mod whose files it replaces, boots the whole pack headless and checks with `/locate` that the stronghold ring sits at its radius, that every shrine generates and that every cut structure is gone.

What no test can see - shaders, sound, the HUD, the feel of a night, the balance - is judged in game by the user; the `playtest` skill is the checklist. Look through the driver first, so the user only judges what a screenshot cannot show.

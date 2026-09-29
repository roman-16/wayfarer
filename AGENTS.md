# Wayfarer

A personal modpack repository: it reproduces on any machine from `direnv allow` and `just`, and nothing outside it needs a manual step.

## The rules of the pack

- **The lore decides.** `story/lore.md` is the source of truth. Every mechanic needs a reason there, and every quest, book and item text is written from it with the `lore` skill. A mechanic the lore cannot explain is a mechanic to drop or to give a reason first.
- **There is no Nether.** Nothing may bring it back: no lit portals, no Nether structures, no recipe that needs a Nether-only item without an Overworld source. `just test` audits every recipe in every jar against `generator/nether.toml`; a finding is fixed by an Overworld source or accepted there as unobtainable, never ignored.
- **Every number lives once.** Balancing numbers are in `balance.toml`, and the mod, the generator and the boot test all read them from there. Eyes, their biomes, palettes, lore and shrine spacing are in `generator/eyes.toml`.
- **Shrine spacing is measured, not guessed.** Biome layout decides how far a shrine really is, so each eye's `spacing` is tuned by `just survey apply` against the trip targets in `balance.toml`. One survey is noisy (about ±30%): apply, `just generate`, survey again, and stop once the medians sit near the targets.
- **Singleplayer and server behave the same.** Rules live in the mod jar (code and data) or in `pack/config`, never in a world save or a client-only setting.

## Where things go

- `mod/src/generated/`, `pack/config/EndRemastered-NeoForge/Eyes/`, `pack/config/ftbquests/quests/`, `pack/config/iris.properties` and `pack/config/sparsestructures.json5` are written by `just generate`. Change the generator or its sources (`story/quests.toml` for the quest book), never the output.
- `pack/` is managed by packwiz. Add a mod with `packwiz modrinth add <slug>` from `pack/`; use CurseForge only for a mod that is not on Modrinth. Every mod is `client` or `both`: singleplayer runs the server inside the client, so a `server` mod would be missing there.
- A new config for a mod starts from the default the mod writes on its first boot (`build/server/config` after `just test`, `build/client/config` after `just client`). Ship only the keys the pack changes when the mod fills in the rest itself, so machine-specific defaults (thread counts and the like) stay per machine.

## Seeing the game

Never wait by sleeping. Gates and builds run in the foreground with a generous timeout and return when they finish; the dev client is driven through `scripts/client.py`, whose commands each return the moment the game has finished them:

```console
$ python3 scripts/client.py start            # launch unless running; returns once the showroom is built
$ python3 scripts/client.py run 'fly on' '/tp @s 40.5 -9 78 180 10' settle 'shot hall'
$ python3 scripts/client.py stop             # returns once the game has exited
```

`run` prints the batch log: each step, every chat line the game received (command replies included), errors, and the absolute path of each screenshot, which is written before `run` returns. Steps can also come one per line on stdin. `start --fresh` starts the natural world anew. A batch that does not finish within `--timeout` is aborted and its log shows where it stuck.

The client opens on the user's display in the showroom dimension `wayfarer:showroom`: the Last Camp at spawn, an item frame wall at z = -10, and the shrines in alphabetical order along x at 32-block steps (hall centres at x = 40, 72, ...; z = 72; hall floor y = -12), each with its name sign on the north side. `/locate` finds nothing there; the natural overworld next to it (`/execute in minecraft:overworld run ...`) is kept between launches.

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

Run `just lint`, then `just test`, before calling work done. `just test` builds the server pack, audits recipes, boots the whole pack headless and checks with `/locate` that the stronghold ring sits at its radius and that every shrine generates.

What no test can see - shaders, sound, the HUD, the feel of a night, the balance - is judged in game by the user; the `playtest` skill is the checklist. Look through the driver first, so the user only judges what a screenshot cannot show.

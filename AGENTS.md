# Wayfarer

A personal modpack repository: it reproduces on any machine from `direnv allow` and `just`, and nothing outside it needs a manual step.

## The rules of the pack

- **The design decides.** `design/` holds what the pack is becoming, and `design/README.md` says the phase and what comes next. Build only what is Settled there; a gap found while building goes back to Open in its document, never decided in code. Design work runs through the `design` skill.
- **The lore decides.** `design/lore.md` is the source of truth. Every mechanic needs a reason there, and every quest, book and item text is written from it with the `lore` skill. A mechanic the lore cannot explain is a mechanic to drop or to give a reason first.
- **Everything looks vanilla.** Every texture, model, animation and structure is made with the `art` skill and judged in the game beside its vanilla relative.
- **There is no Nether.** Nothing may bring it back: no lit portals, no Nether structures, no recipe that needs a Nether-only item without an Overworld source. `just test` audits every recipe in every jar against `generator/nether.toml` and every structure that still generates for a lit portal; a recipe finding is fixed by an Overworld source or accepted there as unobtainable, a portal by cutting its structure in `generator/owners.toml`, never ignored.
- **Every number lives once.** Balancing numbers are in `balance.toml`, and the mod, the generator and the boot test all read them from there. Eyes, their biomes, lore and shrine spacing are in `generator/eyes.toml`. A number that tunes something is a power of two, or a sum of two such as 128 + 64 = 192; a number that means something stays as it is, such as time people read, a share like two thirds, a count the story sets, or a fact about vanilla or a mod. A system's numbers move to this when it is rebuilt.
- **Shrine spacing is measured, not guessed.** Biome layout decides how far the next shrine really is, so each tier's `spacing` is tuned by `just survey apply`, which walks journeys to the nearest shrine not yet taken and compares the legs with the targets in `balance.toml`. One survey is noisy (about ±30%): apply, `just generate`, survey again, and stop once the medians sit near the targets.
- **Singleplayer and server behave the same.** Rules live in the mod jar (code and data) or in `pack/config`, never in a world save or a client-only setting.

## Where things go

- `mod/src/generated/`, `pack/config/EndRemastered-NeoForge/Eyes/`, `pack/config/iris.properties`, `pack/config/nohostilesaroundcampfire.json5` and `pack/config/sparsestructures.json5` are written by `just generate`. Change the generator or its sources (`story/books.toml` for the books), never the output.
- `generator/owners.toml` gives every structure kind of every mod an owner, or cuts it; the owners spaced in `balance.toml` `[structures.every_blocks]` share one placement each. `generator/removals.toml` cuts items, creatures, enchantments and recipes, and names the blocks that take no root and those that keep only in a tent. A mod added to the pack has its structures and anything that breaks a rule of the road entered there.
- `pack/` is managed by packwiz. Add a mod with `packwiz modrinth add <slug>` from `pack/`; use CurseForge only for a mod that is not on Modrinth. Every mod is `client` or `both`: singleplayer runs the server inside the client, so a `server` mod would be missing there.
- A new config for a mod starts from the default the mod writes on its first boot (`build/server/config` after `just test`, `build/client/config` after `just showroom`). Ship only the keys the pack changes when the mod fills in the rest itself, so machine-specific defaults (thread counts and the like) stay per machine.

## Reading the game and the mods

`just sources` writes Minecraft's and NeoForge's code with Parchment names and every mod decompiled with Vineflower, each with its assets and data, into `build/sources/` (`neoforge-<version>/` and `mods/<jar>/`); a mod is decompiled again only when its version changes. Search it with `rg`, naming the path: `rg` skips `build/` and every other generated folder through `.gitignore`.

```console
$ just sources
$ rg "enter_when_safe" build/sources/mods
```

`just registries` writes what the running pack contains into `build/reports/`, from every mod: `registries/<namespace>/<path>.json` for every registry, a list of its entries for those made in code (blocks, items, creatures) and every entry's content for those made from data (structures, structure sets, biomes, loot tables and the rest); `tags/<namespace>/<path>.json` with every tag's members; and `recipes.json`. An entry the game cannot write back holds its error instead. It starts the dev server and stops it after, so run it while the dev server is stopped.

```console
$ just registries
$ rg --count '"trek:' build/reports/registries/minecraft/worldgen/structure.json
```

## Seeing the game

Never wait by guessing. No `sleep`, and no `wait` in a driver batch to give something time to finish: a guessed wait is either too short and flaky or too long and slow. Every step returns the moment its effect is done, by waiting on the event itself (the server's reply, a freshly drawn frame, the next compass reading) or by checking about every second until it holds, and a wait that does not finish in time fails and says where it stuck: a driver step that makes no progress for 60 s fails the batch, naming the step and the state of the game. When a step returns before its effect shows, fix the step so it waits for that effect, never the batch; `wait` is only for watching something that has no end, such as an animation.

Never wait for a failure that is already known. Code that knows something failed says so the moment it knows, in one line `<what> failed: <why>` (`camp failed: no spot ahead is out of sight, ...`), and both drivers end the batch on that line at once, with it as the error, and exit non-zero; a refusal or a note on the way (`camp: the spot at ... falls short`) is worded otherwise and lets the batch go on. Gates and builds run in the foreground with a generous timeout and return when they finish; the dev client is driven through `scripts/client.py`:

```console
$ python3 scripts/client.py start            # launch unless running; returns once the showroom is built
$ python3 scripts/client.py run 'fly on' '/tp @s 48.5 12 60 0 20' settle 'shot row'
$ python3 scripts/client.py stop             # returns once the game has exited
$ python3 scripts/client.py tour [eye|camp ...]   # launch, photograph every shrine and the Last Camp, quit
```

The game runs on a hidden screen from `start` until `stop`: Xvfb from devbox, drawn in software by Mesa, so it never takes over the user's display; `start --visible` and `tour --visible` put it on the user's display, drawn by the graphics card. A hidden game is silent (master volume 0, which Reactive Music's own player follows too); a visible one plays at half volume. A launch that meets a dialog on the way into its world fails at once and names it, since nobody can click it on the hidden screen; the one-time warning that a world uses experimental settings, which every showroom save shows because of its extra worlds, is confirmed by the driver and never comes again. Close every session you open: `stop` as soon as the looking is done, and never hand back to the user with the client still running.

`start --lean` on either driver, and `just showroom lean` or `just play lean`, load only the Wayfarer mod and what it needs (Curios, from the dev classpath) with an empty mods folder, for work that does not need the rest of the pack: the client starts in about 23 s instead of 34, the dev server in about 11 s instead of 16. They play in worlds of their own (`showroom-lean`, `play-lean`, `world-lean`), since opening a world without the mods it was made with damages it.

Every dev client (`just showroom`, `just play` and the driver) runs with test settings that `scripts/instance.py` lays over `pack/config` in `build/client`: no shaders, no Distant Horizons, 8 chunks. `start --far` on the client driver, and `just showroom far` or `just play far`, keep Distant Horizons on, its far view cut to 1,024 blocks; the `far` step waits until it is drawn, which around a new spot takes one and a half to two minutes on the visible screen and much longer on the hidden one. The shipped pack keeps its shaders, its far view and 12 chunks, so those are judged in the exported pack (`just pack`, imported into Prism Launcher).

devbox pins Mesa to the Nix snapshot the Java runtime (`jetbrains.jdk-no-jcef`) comes from (`github:NixOS/nixpkgs/<commit>#mesa`): the hidden screen loads Mesa into the game's Java, and a driver built against a newer C library than Java's does not load. When the Java runtime moves to a newer snapshot, move Mesa's commit with it. The justfile sets `JAVA_HOME` to devbox's Java for every task, and both drivers launch the game and compile through just recipes (`just showroom`, `just play`, `just devserver`, `just compile`), so a shell's own `JAVA_HOME` never matters.

`run` prints one line of JSON per step, as soon as the step is done: the step, and lists of what it produced, such as every chat line the game received while it ran (command replies included) under `chat`, errors under `error`, the absolute path of each screenshot, written before `run` returns, under `screenshot`, and the step's own result under its name (`inspect`, `log`, `perf`, `pos`, `state`, `sheet`). A command step waits until the server has run it, so its replies always sit under it. Steps can also come one per line on stdin. `start --fresh` opens a fresh natural world in survival (`just play`) instead of the showroom (`just showroom`). A batch that does not finish within `--timeout` is aborted and its log shows where it stuck.

The client opens in the showroom dimension `wayfarer:showroom`: the Last Camp at spawn, the stranger's first and last camps just south of it at z = 24 (x = -16 and 8), each with its name sign on the north side, an item frame wall at z = -10, and the shrines in alphabetical order along x at 40-block steps (hall centres at x = 48, 88, ...; z = 72; the surface at y = 1, each hall as deep as its way down needs), each with its name sign on the north side. The compass works there as a demo: it points at a waymark 750 blocks west of the camp, shows a companion circling the camp and marks the camp's grave. `/locate` finds nothing there; the natural overworld next to it (`/execute in minecraft:overworld run ...`) is kept between launches.

The workshop (`wayfarer:workshop`) sits beside the showroom in the same save: empty, flat, a smooth stone floor, noon and clear sky, wiped at every launch. Designs line up along x from the origin, 4 blocks apart, each with its name sign 2 blocks south of it, so the vanilla relative put in with `beside` stands next to the design it is judged against:

```console
$ python3 scripts/client.py run workshop 'place wayfarer:last_camp' 'spawn minecraft:villager' 'views camp' clear
```

`tour` shoots each shrine from its surface cover down the shaft into the hall, and the Last Camp from every side, into one labelled contact sheet per subject in `build/client/tour/<subject>.jpg`; the hall cameras come from the generator (`mod/src/generated/dev/wayfarer/tour.json`), so every hall is framed from open air with a clear line to what it looks at. It starts the client itself and quits it when done; a whole tour takes about three minutes.

| Step | Does |
|---|---|
| `/<command>` | Runs a command as the player |
| `assets` | Reloads every texture, model and sound, as F3+T does, and returns once the reload is done and the loading screen has faded |
| `await <text>` | Waits for a chat line containing the text, such as a quest's reply that comes on the server's own rhythm; each line answers one `await`, so a second `await` of the same text waits for the next such line |
| `beside <thing>` | Puts a creature, structure, template, block or item, usually its vanilla relative, at the workshop's next free spot |
| `clear` | Empties the workshop and puts its floor back |
| `click <x> <y>` | Clicks the open screen at GUI coordinates |
| `close` | Closes the open screen |
| `compare <a> <b>` | Counts the pixels that differ between two screenshots and writes a picture of the difference |
| `far` | Waits until Distant Horizons has generated and drawn the far view around the player (only with `start --far`) |
| `find <block> [radius]` | Lists the nearest positions of a block within the horizontal radius (default 32), at any height |
| `fly on\|off` | Toggles creative flight |
| `gui on\|off` | Shows or hides the HUD |
| `inspect block <x> <y> <z>\|creature <type>\|item\|inventory` | Returns as JSON a block with its block entity, the nearest creature of a type with its data, the held item, or every filled inventory slot |
| `hud` | Waits for the compass reading the server sends after the commands before it and returns it as JSON: its mode and markers |
| `interact <creature>` | Right-clicks the nearest creature of that type within 8 blocks, as a player does |
| `key <mapping>` | Presses a key mapping (`key.inventory`) and returns once the game has taken the press; `key escape` closes the open screen |
| `log` | Returns the warnings and errors the game logged since the last `log`, the newest 64 kept |
| `perf` | Measures the frame rate over one second, and logs the server's tick time and which renderer draws |
| `person <skin> [pose]` | Puts a person at the workshop's next free spot: an Easy NPC body wearing a skin from `generator/skins.toml`, with its model and size, standing or in an Easy NPC pose (`sitting`, `rest`, `sleeping` and the rest) |
| `place <thing>` | Puts a structure, template, block or item at the workshop's next free spot, with its name sign |
| `pos` | Logs the player's position, facing and dimension |
| `press <label>` | Clicks the widget with that label on the open screen, once it is enabled; a label that is not there is an error at once |
| `quit` | Closes the client |
| `screen` | Logs the open screen and its widgets with labels, positions and sizes |
| `settle` | Waits until earlier commands have run on the server, the chunks around the player are loaded and the terrain is drawn |
| `shot <name>` | Saves a screenshot of a frame drawn after every step before it |
| `sky <ticks>` | Watches the client's clock for that many ticks and returns how far it moved, the least and most in one tick, and the speed the server set |
| `spawn <creature> [walking]` | Puts a creature at the workshop's next free spot, standing still, or walking with `walking` |
| `state` | Returns the world's story state: every save file of the Wayfarer mod as JSON |
| `type <text>` | Types into the focused field |
| `views <name>` | Photographs everything in the workshop from the front, the side, above and three-quarters, each camera fitted to it, into `build/client/workshop/<name>.jpg` |
| `wait <ticks>` | Waits a fixed time; only for watching something with no end, such as an animation, never to let a step finish |
| `workshop` | Takes the player into the workshop, south of what stands there |

The driver (`mod/src/dev/java/.../Driver.java`) confirms commands with a sentinel (`execute if entity @s`, whose reply it hides), so `sendCommandFeedback` must stay on. After `just generate`, `cd mod && ./gradlew processResources processDevResources` and the step `/reload` load the new data into the running client; new chunks and `/place structure` then use it.

Changed Java code goes into a running game without a restart: `python3 scripts/client.py swap` or `python3 scripts/server.py swap` compiles the Wayfarer mod and swaps every changed class that is already loaded into the client or the dev server, over a debug port (`client_swap_port` and `server_swap_port` in `mod/gradle.properties`). The JetBrains runtime from devbox takes changed method bodies and added or removed methods and fields. It prints one line of JSON: what was swapped, what loads fresh when first used, what is new, and what needs a restart. Mixins always need one, and so does a loaded class whose static fields start differently, a new one, a changed starting value or a changed constant, since its initialiser does not run again (the swap names it under `needs a restart` instead of swapping it), and anything the game reads only at startup, such as a new `@SubscribeEvent` method; changed command code takes effect after `/reload`, which registers the commands again.

The dev server is driven the same way through `scripts/server.py`: the whole pack with the Wayfarer mod's dev code, in `build/devserver` on port 25566, so it can run beside `just test` and a dev client can join it at `localhost:25566`. It runs headless and keeps its world between launches (`start --fresh` deletes it). Each step of `run` is a console command, returns the moment the server has run it, and prints one line of JSON with its replies; `await <text>` waits for a later reply containing the text, such as an asynchronous `/locate`'s, each reply answering one `await`, and `state` returns the world's story state as on the client. Stop it when done, as with the client.

```console
$ python3 scripts/server.py start            # launch unless running; returns once the server is ready
$ python3 scripts/server.py run 'locate structure wayfarer:shrine/old' 'await The nearest wayfarer:shrine/old'
{"step": "locate structure wayfarer:shrine/old", "replies": ["The nearest wayfarer:shrine/old is at [4608, ~, 384] (4400 blocks away)"], "seconds": 0.7}
$ python3 scripts/server.py stop             # returns once the server has exited
```

Pretend players stand in for a team on the dev server: `wayfarer pretend join <name>` brings one online through the game's own login, so every mod reacts as to a person; `wayfarer pretend leave <name>` logs it out and `wayfarer pretend respawn <name>` presses Respawn after a death. They do what vanilla commands tell them (`tp`, `spreadplayers`, `execute as <name> run ...`), every message one receives comes back as a reply (`Mo hears: ...`), and a returning name comes back where and as it left, so one that left dead joins dead until `wayfarer pretend respawn` brings it back. They never move on their own, not even to fall, so put them on the ground with `spreadplayers`; the chunks around them load wherever a command puts them, and a change of world completes for them, as a person's would. They join the dev client's own world the same way, beside the real player and its chat.

```console
$ python3 scripts/server.py run 'wayfarer pretend join Mo' 'spreadplayers 0 0 0 4 false Mo' 'execute as Mo run say hello'
```

`scripts/drive.py` is the library both drivers and the boot test share: launching and stopping a game, crash reports, and a server's console, whose commands and replies go through `driver/console` and `driver/server.log` in the server's folder.

## Gates

Run `just lint`, then `just test`, before calling work done. `just test` builds the server pack, audits recipes, portals and that the Wayfarer mod loads after every mod whose files it replaces, boots the whole pack headless and checks with `/locate` that the stronghold ring sits at its radius, that every shrine generates and that every cut structure is gone.

What no test can see - shaders, sound, the HUD, the feel of a night, the balance - is judged in game by the user; the `playtest` skill is the checklist. Look through the driver first, so the user only judges what a screenshot cannot show.

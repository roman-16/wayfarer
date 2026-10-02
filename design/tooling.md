# Tooling

How the pack is seen, tried and built. Coding agents build most of it, so the tools are made for them: every command returns as soon as its work is done. These are settled in phase 3, before the spikes need them.

## Settled

### T1 The workshop

- The workshop (`wayfarer:workshop`) is a third world inside the dev client, beside the showroom and the natural world: empty and flat, always at noon under a clear sky, on a plain floor. Moving between the three is one command, never a restart.

  | World | Holds | Started by |
  |---|---|---|
  | The workshop | One design at a time: put in, photographed, tried, refined, put in again | `just showroom` |
  | The showroom | Everything finished, on show: the shrines, the camps, the items, every creature and structure | `just showroom` |
  | A fresh world | The singleplayer experience as a player meets it | `just play` |

- Designs get in with driver steps in `scripts/client.py`, each returning when done:

  | Step | Does |
  |---|---|
  | `workshop` | Takes the player to the workshop |
  | `place <structure>` | Places a structure or piece the generator built at the next free spot, with its name sign |
  | `spawn <creature>` | Brings in a creature, standing still to be photographed, or walking to be tried |
  | `beside <vanilla thing>` | Puts a vanilla creature, item or block next to it, to judge size and style against Minecraft's own |
  | `views <name>` | Photographs it from the front, the side, above and three-quarters into one labelled sheet, as `tour` does for the shrines |
  | `clear` | Empties the workshop |

  ```text
  $ python3 scripts/client.py run workshop 'spawn wayfarer:worm' 'beside minecraft:zombie' 'views worm'
  build/client/workshop/worm.jpg
  ```

- A finished design is listed by the generator for the showroom, so `just showroom` shows it from then on.

**Why:** One running game for making and showing keeps every look a command away, and a plain stage with vanilla beside it lets a design be judged by the same eye a player brings.

### T2 The development environment

- **No sleeping.** A command never waits a guessed time: it waits on an event, such as the world having loaded or a command having run, or on a check repeated about every second until it holds, and returns the moment it does. A wait that does not finish in time fails and says where it stuck, and a failure the game already knows ends the wait at once: code that knows something failed says so in a line `<what> failed: <why>`, and the drivers stop on it with that line as the error. This already holds for `scripts/client.py`, whose steps return when the dev-only driver writes `done.txt`, and for `scripts/boot.py`, which waits for the server's own "Done" line.
- **A server driver.** `scripts/server.py` drives a dev server the way `client.py` drives the client: start, run a batch of commands, stop, each command returning the moment the server has run it, with its replies.
- **Pretend players.** NeoForge's fake players join, leave, vote and walk into the frame on the server without a client, so every rule for a team of one to four has a fast test. A second real dev client joins only for what has to be seen.
- **Changing a running game.** Data reaches it with `processResources` and `/reload`, as today; code is swapped into the running game by the JetBrains Java runtime, and a restart comes only when a change cannot be swapped.
- **Structured output.** Every step of either driver reports what happened as one line of JSON, and a `state` step returns the world's story state: the quests, each player's tracked quest, the caravan, the shards and the shine.
- **Four layers of tests:**

  | Layer | Tests | About how long |
  |---|---|---|
  | Unit tests | Logic without a world: the quest engine, the clock's curve, the votes | Seconds |
  | Game tests | Behaviour in a headless world | A minute |
  | The boot test | The whole pack and its world generation | Minutes |
  | Playtest | What only a person can judge, with the `playtest` skill | An evening |

**Why:** Coding agents build most of the pack, so every loop from a change to seeing it has to be as short as the change allows and never wait on a guess. The server is where the new systems live, a team is a ground truth, and logic tested without a world is tested in seconds.

It raises, for the first spike: that pretend players can vote, once votes exist.

### T3 Making creatures, items and their pictures

An agent designs creatures, items and their pictures and tries them in the game, the way T1's workshop lets it try builds, following the `art` skill (`.agents/skills/art/SKILL.md`).

- **Textures are drawn pixel by pixel as data,** as the pack already does: rows of characters with a palette per variant in `generator/textures.toml`, written by `generator/png.py`, starting from a vanilla relative's texture where one exists (in the client-extra jar under `assets/minecraft/`). An image model may sketch a large reference of what a thing should look like, never the texture itself.
- **Models and animations are data in the generator.** A creature's boxes and bones are listed in TOML; the generator writes GeckoLib's model and animation files and works out the box layout of the texture. Animations are sine curves GeckoLib runs from formulas, as the art skill asks. Blockbench serves for looking, and through its MCP plugin for building a creature too complex for data.
- **An `assets` step** in the client driver reloads every texture, model and sound, as F3+T does by hand, and returns when done, so a look changes without a restart.
- **Art checks in `just lint`:** textures are 16 pixels, or a power of two for creatures; no texture has more than about ten colours, and none has half-transparent pixels where Minecraft draws solid ones; items have their darker outline; model boxes are whole pixels and at least one pixel in every direction. The rest is judged in the workshop against the art skill's checklist.
- The Orca CLI's cloud asset makers stay unused: they draw from a description, where the art skill starts from vanilla's own pixels.

**Why:** The art skill's rules are about exact pixels, and an image model's smooth picture turns to noise at 16 pixels, while Faithful's first rule is to start from vanilla. Data in the generator is how this repository makes everything: it can be read in a diff and needs no window, and the workshop's photographs from every side catch what numbers alone would miss.

### T4 The tools

What an agent has to build and look with, beyond T1 and T2:

| Tool | Does |
|---|---|
| `just sources` | Unpacks Minecraft's and NeoForge's code, with Parchment's readable names, and every mod jar's code decompiled with Vineflower, into `build/sources/`, searchable with ripgrep; it runs again only when a mod changes version |
| `just registries` | One headless boot that writes everything the pack contains, from every mod, as JSON into `build/reports/`: blocks, items, creatures, structures, biomes, loot tables, tags and recipes; it is the list M13's structure pass works from |
| A lean launch | The dev client and server with only the Wayfarer mod and what it needs, for work that does not need the whole pack; the full pack stays the default |
| Driver steps | `inspect` a block, creature, item or inventory as JSON; `log` the errors and warnings since the last step; `perf` the frame rate and the server's tick time through spark; `compare` two pictures for what changed |
| Story checks | In `just lint`: every quest, person, choice and scene points at something that exists, every main quest can be reached whatever order the halls are taken in, and both ways of every decision lead on |
| One server driver | `scripts/boot.py`, the boot test and the survey, runs on `scripts/server.py`, and both drivers share one library |
| Split tests | `just unit`, `just gametest` and `just boot` each on their own, and `just test` running all of them |

- **Searches skip what is generated.** An ignore file keeps `build/`, `mod/build`, `mod/run`, `.devbox` and `dist` out of every search by default, so an agent's context is not spent on built files; `build/sources` is searched on purpose.
- **Every failure says how to fix it,** as `scripts/audit.py` already does.
- **Project skills.** Each system gets a skill of its own when it is built: how the Wayfarer mod is laid out and tested, how the generator builds structures, how the workshop loop runs.
- **A virtual screen.** The dev client runs on a virtual screen by default, as HeadlessMC does, so an agent never takes over the user's display and can run beside them, even two at once; `--visible` puts it on the user's screen. It draws in software, and with the test settings below it runs about as fast as the graphics card does with the pack's full look.
- **Test settings.** Every local dev client, the showroom, a fresh world and every tour, runs without shaders, without Distant Horizons and at 8 chunks, laid over the pack's configs only in the dev client's folder; the shipped pack keeps its shaders, its far view and 12 chunks, and they are judged in the exported pack.

The gates stay local: `just lint` and `just test` run on the machine, not on GitHub.

The tasks and tools are named for what they open or do, and only what the repository uses stays:

| Change | Why |
|---|---|
| `just demo` becomes `just showroom`, and `just client` becomes `just play` | One opens the showroom and the workshop, the other a fresh world played as a player meets it |
| devbox loses `shellcheck`, `jq` and `git` | The repository has no shell scripts, nothing uses `jq`, and git is needed before devbox exists |
| devbox gains `ripgrep` and Vineflower | `just sources` needs them; Vineflower comes from Gradle if nixpkgs has none (not checked) |

**Why:** What slowed this design's own research was unpacking jars by hand to read Minecraft's code and to list what the mods add, and starting all of the pack for every look, so the sources, the registries and a lean launch go first. Checks that run without the game catch an agent's broken data before anyone waits for a launch, and one way to drive a server keeps the tooling free of seams.

## Open

Nothing is open.

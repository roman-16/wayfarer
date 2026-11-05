---
name: playthrough
description: Play Wayfarer from the title screen as a newcomer who knows vanilla Minecraft and nothing of the pack, for hours or days, and write down as a friction log everything that is fun, boring, confusing, broken or stuck, then report it as a list of findings and proposals for the user to plan from. Also holds the newcomer's rules and questions that the finish skill's walkthrough uses. Use when the user calls it, asks for a playtest or playthrough, or says to continue the playthrough.
---

# Playthrough

The user and friends play on exaroton for feel: the music, the shader's mood, a fight, each other's company. This is the other half: the agent plays the pack the way a newcomer meets it, on a fresh seed, at a player's pace, step after step, and writes down every bump, so the friends' evenings never go to a bug or a stuck step. It plays on and writes things down, the way friends talk about what annoys them and keep playing: nothing in the pack is implemented during a run, only a workaround for what stops the play outright and a mend of the run's own tools when they stop it (When something stops the play). What it finds goes to the user as a report: findings and proposals, never their implementation.

## Start or continue

| The user says | The run |
|---|---|
| nothing, `continue` | Continue the newest run in `playtests/ledger.md` that is not reviewed and can go on; when the newest is reviewed and its world can go on, continue that world in a new run (Continue after a review); with neither, start a new one |
| `new`, or where to start, how far, alone or with a friend, a persona, a seed | Start a new run with those, the rest by default |

A new run by default: a world made at the front door, a random seed, alone, the newcomer, the shipped look, played on until the user stops it. Every run's folder is `playtests/<YYYY-MM-DD_HH-MM>/` (ignored by git, like `exaroton/`): `log.md`, `shots/`, `run.json` (the time played since the moment the folder is named for, kept by `run --shots`), and at the end `report.md` and `report/paths.png`.

### A new run

1. Make the folder and add the run's row to `playtests/ledger.md` (`| run | commit | seed | persona | team | from | status | reviewed |`, status `playing`, reviewed empty).
2. Write the log's head (below) with the persona and the goal.
3. `python3 scripts/client.py start --title --shipped`, then `run 'turns on'`. From the title: Singleplayer, Create New World, the world's name `Playthrough <DD.MM.YYYY HH:MM>`, the seed typed on the World tab, everything else as the pack sets it, Create. Watch the loading and the first seconds as a player does.
4. From a quest step instead of the title: `start --state <quest/step> --shipped`; the log says so, since the state was built by the dev code, not played.

### Continue

1. Read `playtests/ledger.md`, then the run's `log.md`: its **Now** first, then the last entries, until the agent knows again what the player knows, wants and fears. Nothing else is read: not `design/`, not the code, not the quest files. The player only knows what they played.
2. `start --world "<save>" --shipped` (the save's folder name, such as `Playthrough 3`), `run 'turns on'`, and look around before moving.
3. After a compaction mid-run, the same: the log is the memory.

### Continue after a review

A reviewed run is closed: its folder keeps what it found on the pack it was played on. Its world plays on in a new run, so the new findings are only the changed pack's:

1. Make the new folder and its ledger row, From naming the world and where it stood (`"Playthrough 3" at the_dry_city/the_caravan, after 2026-10-16_16-58's review`), Commit the pack it now plays.
2. Copy the old log's **Now** into the new log's head as its starting mind, then continue as above, loading the same save.
3. What the review changed is learnt the way a player learns an update: by playing; the log notes it where the player meets it (`fine · The Gear page has an Armour row now`).

## Hands and eyes

While playing, the agent is the player and nobody else.

| Allowed: what a player has | Never while playing: the designer's tools |
|---|---|
| `go <blocks>`, `go <x> <z>` only for coordinates the screen showed, `follow <name or kind>`, `turn <degrees> [pitch]`, `face` | Commands, `/tp`, `fly`, `story`, `state`, `inspect`, `find`, `hud`, `digest`, `still`, `/tick` |
| `gather <block> <count>`, `fight [kind] [here]`, `eat`, `use`, `interact`, `hold`, `key`, `sneak`, `click` (with `shift` for a shift-click), `point`, `press`, `type`, `scale` | Reading `design/`, `story/*.toml`, the code or the logs to know what to do |
| `craft <item> [count]`, `wear <item>`, `hotbar <item> [slot]`, `put <item> [<x> <y> <z>]`, `dig <x> <y> <z>`, `pitch`, `embark`, `ember` (hold up the ember and turn all round), `row <x> <z>`, `dismount`, `idle until <time of day>`, `listen`, `fish [count]`, `scroll <x> <y> <lines>`, items named as the screen names them | |
| `shot`, `screen` (what is on it, the slots of a container), `pos` for the log's own bookkeeping only | F3's coordinates for finding the way: the pack's compass and ember are how a player finds it |
| The band's and chat's lines the driver returns, read as the screen shows them, a line `(silent)` a finding since every line is spoken; `seen`, `heard` and `hurt`, the HUD, the subtitles and the hits as a player perceives them; a `check` line is the dev code's story check, a finding for the log that the player never saw | Teleporting past a walk, skipping a talk, `/time`, `/weather` |
| The run's own: `checkpoint` and `rewind`, as Deaths says; `cheat <command>`, for a workaround only | |

The player has a body (AGENTS.md): during a batch it fights back what attacks with a weapon in reach, never chasing it more than 8 blocks from where the fight began and walking back there after, walks or runs on past what it cannot fight, runs to the nearest fire, tent or goal when it must run, backs off creepers, swims up for air, steps out of fire and eats from the hotbar when nothing hostile is near, each act a line under `body`. It is the hands of a few hundred hours of vanilla, nothing more: what the pack sets the player (heat, cold, the ember, hordes, holds, where to sleep, when to travel) is the player's to decide, and that is where findings come from. Keep a weapon and food in the hotbar, as a vanilla player does, so the body has them.

`go` walks, swims and climbs as a player would, breaking only leaves, in stretches that keep to the surface to the very end, and stops the moment something needs the player: a line on the band after a quiet moment, answers beside the crosshair, a banner, the task panel changing, a screen opening, the way going underground, a hit the body does not answer (heat, cold, fire, hunger, a fall), air running out, health at a third, hunger with nothing to eat, death, arrival. A stop ends the batch: what came after it is `skipped`, so walk in one leg, never the same `go` again and again. Then look and decide. `gather` mines like a player; `fight` fights the nearest monster in sight, or the kind named, as the body does, and stops when it falls, flees or health runs low. Crafting, wearing and the hotbar go through the screens as a player's hands do (`craft`, `wear`, `hotbar`), and a block goes down with `put`, which says why when it cannot; `use` and `interact` click where the crosshair lands, so a click that goes to something else is a finding (`the click went to an interaction box over Hearthstone`). A sea is crossed with `embark`, `row` and `dismount`, and `go` stops at the shore of open water so swimming out is decided; a time of day is waited for with `idle until dusk`; a talk is stood through with `listen`, which returns at its answers or once it falls quiet; the ember is read from the word `seen` gives under the compass; the tent goes up with `pitch`, which finds room for it.

## Pace

- `turns on` after every launch: between batches the game stands paused, as when a player stops to think; inside a batch it runs. Never run with turns off: the world does not wait for the agent's thinking.
- One decision per batch, and one codemode call per batch, the steps on stdin and the closing screenshot numbered into the run's `shots/` by the driver, so every decision is made looking at the screen and the last line's `seen`:

  ```js
  const r = await tools.bash({command: `cd /home/roman/Documents/wayfarer && python3 scripts/client.py run --timeout 900 --shots playtests/<run>/shots <<'EOF'
  go 120
  EOF`, timeout: 1000});
  text(r.output);
  const last = JSON.parse(r.output.trim().split('\n').pop());
  if (last.view) image(await tools.read({path: last.view}));
  ```
- The batch shows what changed, step by step, and the whole state once at its end: read the body there (health, food, temperature, effects) before deciding, as a player glances at the HUD.
- The game keeps its own time: dusk, a talk, a hold run at 20 ticks a second. Wait for them with `await` or by walking on, never with `wait`, which is only for watching something that has no end.
- Play at the length a player would: read every line, look at what is shown, take the detours a curious player takes, and also do the plain survival a player does (wood, a tool, food, a place to sleep), not only the quest.

## The persona

| Persona | Plays like |
|---|---|
| Newcomer (default) | A few hundred hours of vanilla survival on Normal; reads what the screen says once; never looks at a wiki; does what the panel asks, but also wanders when something looks interesting |
| Rusher | Skips through lines, clicks the first answer, sprints, ignores the journal until stuck |
| Wanderer | Leaves the quest for an evening to explore, build and loot, then comes back and has to find the thread again |
| With a friend | The same newcomer, with a pretend friend on the dev server who is late, stands in the wrong place, dies once and logs out once (below) |

## Writing it down

`log.md` is a friction log (Stripe's practice): one concrete user, arriving the way they arrive, nothing too small, neutral words, and a mark wherever they would have given up.

```markdown
# Playthrough 16.10.2026 17:05 · seed 4471982 · alone · newcomer · commit b166dcf
I play survival on Normal, a few hundred hours of vanilla, and never read a wiki. I start a new world and play until I'd stop for the evening.

## Now
In the caravan's camp at dusk, day 2. I know: Maud (the elder, who made me their scout), Agna (healer), Hob (smith, gave me a blade), Pip. The ember pulls; I hold Z to find which way. I want: sleep, then follow the pull. Worried: the journal said something about patterns I haven't understood. Carrying: Hob's blade, 9 bread, a tent.

## 0:02 · day 1, dawn · a fire in a birch wood
- confused · The land is still drawing in and a line already says "You wake on cold ground…". The trees popped in over it. shots/0003.png
- fine · The answers beside the crosshair, 1 to 4.
```

- **Now** sits under the head and is rewritten at every stop and every hour: where the player stands, what they know (names, places, rules, words the story gave), what they want next, what worries them, what they carry, and the lessons: what has killed or nearly killed them and what they do differently now (`Heat: the savanna kills by day; I cross it at dusk with water`). It is the mind a continued run picks up.
- Each entry starts with a kind: `fun`, `fine`, `confused`, `stuck <minutes>`, `bug`, `wrong` (a text, a look, a sound that does not fit), `boring`, `slow`, `gave up here`, `death`, `proposal`, `workaround`. Then what was seen or done and how it felt, and its shot (`run --shots` puts it into `shots/` already).
- A finding written once is not written again: when it comes back, its first entry gets a count (`×3`), and the new entry is only for what is new about it.
- A heading for each place or moment: `## <played> · <day and time> · <place>`, the time played taken from the last line's `run.played` and the day from `body.time`, never counted by hand; deaths and rewinds are counted from `run` too.
- At the end of each quest, a short stretch note: how long it took, whether it was fun, what the player understood the story to be.

## When something stops the play

- **Stuck as a player.** Try what the screen offers for about 10 game minutes, as R3's blind players may before a hint. Then write `gave up here`, and get help as a player could: the journal first, then `cheat wayfarer step`. Only after that, look as the designer for the cause, and write it under `Diagnosis:` in the entry, kept apart from what the player knew.
- **Blocked.** When nothing a player could do gets past (a step that cannot finish, a world that kills every attempt, a driver step that cannot move the player at all), the smallest workaround is allowed: a `cheat` (`cheat wayfarer step again`, `cheat tp ...`), playing on with a player's plain keys where a leg fails, and, only when nothing else gets past, a change to the code. A command goes through `cheat` and nothing else, so the play record keeps it; each is also logged as `Workaround:` with what blocked, what was done and what it changed, and the report lists them all from the record. Nothing else is changed during a run.
- **The same failure again.** A failure that comes back (a crossing that sinks, a fetch that dies to heat on the way, a walk that ends in the same cave) is one finding with its count, never a new entry. The second time, play it differently, as the lessons in **Now** say; the third time, `rewind` or take a workaround and write that this loop is played out, never a fourth time. It holds for failures that are no deaths as much as for deaths.
- **The harness.** When the run's own tools stop the play (a hand a player has and the driver lacks, a step that fails where a player's would not, a check that ends every batch), improve them and play on; the user allows this without being asked. Write the `proposal`, build it, prove it with `just lint` and in the game (in the showroom or the workshop when the playthrough's world would have to be bent to try it), log it as a `workaround` saying what changed, keep its row in AGENTS.md and the hands here current, and run `just test` before the run's next report. Restart the client after changing the legs, since a hot swap renames the callbacks Baritone holds. Only the run's tools change this way; what the pack itself should do better stays a finding.
- **A crash.** The crash report is a finding; restart the client and load the save (`start --world`).

## Deaths

A death is a finding once; walking back for the things it took is a finding once. The first death of a run plays out as a player meets it: the death screen, coming to, the stone on the compass, the walk back to it, all logged. Every later death is logged and then undone:

1. One `death` entry: the death screen's line, whose death it was and why, and its shot. `pack` when the pack killed a player who did what a vanilla player of a few hundred hours does with what the game told them (`death · pack · "Wayfarer overheated to death" in a hole dug for shade, poncho on, 20 → 0 within a minute of "Too hot"`); `mine` when the run's own handling did (a walk into the dark naked, a fight taken at a third of health). A `mine` death is no finding about the pack; it gets one line and a lesson in **Now**.
2. `rewind`: the world goes back to its newest checkpoint, a couple of game minutes earlier, and the entry ends `· rewound`. What replays after it (lines, a scene) is no finding a second time.
3. Play on from the checkpoint with the lesson learnt, as a player who reloads plays more carefully. `rewind 2` when the newest checkpoint is itself in the trap.

Take a `checkpoint` before something that may kill (a sea crossing, a hold, a cave) when the last one is old. The client's driver takes them on its own at quiet moments, the dev server only by hand, and the record keeps every death and rewind for the report.

## Proposals

Whatever should be better, in the pack or in the tools of the run itself (a driver step that stops too often, a leg that walks into a wall, a missing step a player's hands have, the report, the script, this skill), is written down as it happens and the play goes on:

```markdown
- proposal · The leg `go` cannot start from powder snow ("No path found"), so every walk off the Last Camp's shelf failed. Change: let Baritone walk through powder snow, as a player does. Seen: shots/t0135.png
```

Each says what happened, why it matters and what would change, with its shot. The report gathers them, and the user decides what to plan.

## The questions

At every step a newcomer asks these; a no to any is a finding. The `finish` skill's walkthrough asks them over its scope.

| Ask | Fails when |
|---|---|
| What is happening? | Something big happened (a death, an arrival, a loss) and no line says it plainly, or the scene cuts from one state to the next with only a new objective |
| What is that? | A word, a name or a HUD text shows before the story said it, or a person speaks under a name nobody gave |
| Why am I doing this? | The task panel asks for an action the story gave no reason for |
| What do I do now? | The task panel shows no objective, or only the journal says it |
| How? | The step needs a key or a click that no task names with its key |
| Is it working? | Something takes time (resting, walking, waiting for dusk) and nothing counts it |
| Why not? | An action does nothing, or a refusal does not say why and what to do instead |
| Who opened this screen? | A screen opens that the player did not ask for, or opens without a word on what it is for |
| Am I there yet? | A line, a talk or a task comes while the land is still drawing in, or in the first seconds of arriving anywhere |
| Is this fun? | A stretch of walking, waiting or repeating goes on with nothing new said, seen or decided; the same failure is punished twice; something hard-won is lost |
| Does it remember me? | The world forgets what the player did: a choice, a grave, a place left changed |
| What about alone? | Something waits for, asks for or needs a second player, or a fight or a hold is out of reach for one |
| What about my teammate? | One player is done and does not know whom they wait for, or the other does not know they are waited for |
| What about a restart? | Stopping the server inside the step (mid-dusk, mid-hold, mid-build) and starting it again moves the clock, repeats lines, builds twice or crashes |
| What about odd land? | A place the story puts down crashes, floats or comes out thin on another seed |
| What about meanwhile? | The player does something else while the step waits: walks off, goes underground, sleeps in a tent, dies, logs out, untracks the quest, or a teammate does the opposite |
| What about a death? | A player who falls during the step, in a doorway, on stairs or in a ward, cannot reach their grave or take their things back, or the step runs on without them while they are down |
| What else speaks? | A mod's chat line, key, HUD element, starting item or random event that the design does not know of |
| Does it look right? | A block is not what the thing is made of, a person stands inside something or on a tree, a picture is cut or blank |
| Does it sound? | A moment's music or cue does not fire (the client log names each story piece and cue it plays) |
| Where is it? | Something the story gives cannot be found again: no item, charm or tooltip shows it |

## A friend

With a friend, the world lives on the dev server: `python3 scripts/server.py start --fresh --far` (or `--seed`), `run 'op Wayfarer' 'wayfarer pretend join Mo'`, then the client joins it from the title's Multiplayer screen at `localhost:25566`. The pause is the server's: every batch begins with `/tick unfreeze` and ends with `/tick freeze`. Mo moves the way a late friend does, by the friend's commands only (`execute as Mo run ...`, `tp Mo ...`, `wayfarer pretend answer Mo <n>`), each logged as `Mo:`; those are the only commands while playing. Deaths go as Deaths says, with the dev server's own `checkpoint` and `rewind` (`server.py run checkpoint`, `server.py run rewind`), after which the client joins again.

## Stopping

A run goes on until its stretch ends, the user stops it, or nothing more can be played; never because many hours passed. Away (via Apollo), it keeps playing; the last message is the report.

Before stopping, and at least once a day of play:

1. Rewrite **Now**.
2. `just play-report "build/client/saves/<world>" --since <begun>`, with `begun` from the run's `run.json`, copied into `report.md` with its map, for the times, stalls, choices, deaths, rewinds and workarounds of this run.
3. `just script` when a text finding needs its surroundings, read around it.

## The report

`report.md` in the run's folder holds the report; the last message gives it in short. Answer first, then what the user needs:

```text
Played the prologue: 2 h 50 min, 52 min of game time, seed 4471982, alone, newcomer.
Would I keep playing? Yes, from Ash on; the first day's walk dragged.

Top 3
1. Stuck 14 min: only three signs glowed in Tollbridge; the fourth, a footprint, lay under a wagon.  [shots/0142.png]
2. ...

All 17 findings: 1 stuck, 5 bugs, 6 confusing, 2 boring, 3 fun.
Deaths: 5 (3 the pack's, 2 mine), 4 rewound, 1 played out
| # | Killed by       | Whose | Why                                            | Rewound |
| 1 | Something Below | pack  | no tell before the first ram, swimming at dusk | no      |
| 2 | heat            | pack  | shade and water didn't cool, poncho on         | yes     |
Known from earlier runs, still there: 4, one line each
Pacing (from the record): the_last_fire 11 min · the_first_day 17 min, longest quiet 6:10 · ash 24 min
Workarounds: what blocked, what was done, what it changed
Proposals: 9, for the pack 6, for the run's tools 3
```

The findings stay a list, each with what happened, why it matters and what would change: the user decides what to plan. A finding an earlier run's report already holds, and that is still there, is not new: it goes under "Known from earlier runs, still there", one line each, so the new ones lead. Update the ledger row's status: `playing`, `stopped at <quest/step>` or `done`.

## Reviewed

Once the user has a run's findings planned and built, the session that built them marks the run reviewed: the ledger's Reviewed column gets the date and, in a few words, what went in, and the run's `report.md` opens with a `Reviewed <date>:` line. A reviewed run is never continued in its own folder (Continue after a review), and its findings are not planned again.

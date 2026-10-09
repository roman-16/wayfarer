---
name: finish
description: Finish everything built since the last commit, so the user can commit it - the gates, a newcomer's walkthrough of what a player meets, the gallery's shots for what changed, and a report with what the user plays. Use when the user calls it, or asks to run the gates, walk the work through or shoot the gallery. Development runs none of this.
---

# Finish

Development checks each change in the running game as far as the change needs, and nothing more. Finish proves the whole of the uncommitted work once, the state the user is about to commit: what a check proves goes stale with the next change, so it runs here, not after every piece. It never commits; that is the user's, through the `commit` skill.

## The scope

- Everything uncommitted: `git status --short --untracked-files=all` and `git diff HEAD --stat`. When this session already finished part of it, only what changed since.
- From the files, list what a player meets that changed: steps, screens, the HUD, people, creatures, places, items, sounds, text, mods and their configs. That list decides the walkthrough, the shots and the playtest rows.
- When nothing a player meets changed (tooling, tests, documents), only the gates run.

## The order

1. `just lint`, fixing every finding.
2. The walkthrough of the list, fixing every finding.
3. The shots: new ones for every new facet, then `just gallery` with only the scope's shots, and `just test` started detached beside it, since they run beside each other.
4. The report.

A fix after a step means its earlier steps run again for what the fix touched.

## Walk it through

Play what changed the way players will, knowing nothing but what the screen says: alone first, since about half of them play alone, then with a teammate.

- **Alone, in a singleplayer world.** `client.py start --state <quest/step>` opens one; nothing may wait for, ask for or need a second player.
- **Then a dedicated server and a teammate.** Team rules (two thirds of the team, everyone's own rewards, who waits for whom) only show with two players:

  ```console
  $ python3 scripts/server.py start --fresh
  $ python3 scripts/server.py run 'op Wayfarer' 'wayfarer pretend join Mo' 'spreadplayers 0 0 0 4 false Mo'
  $ python3 scripts/client.py start
  $ python3 scripts/client.py run 'join localhost:25566'
  ```

  Move the teammate the way a real one moves, late and in the wrong place (`execute as Mo run ...`, `tp Mo @p`).
- **Start before the new work.** A state (`--state <quest/step>`) brings you to the step before it; walk the new steps themselves, never `story next` past them.
- **The pack's own settings.** Keys come from `pack/config/defaultoptions`; read every chat line on joining, since every mod's greeting reaches the user too.
- **A touched timed step at the player's pace.** `/tick rate 80` checks that a hold, a dusk or a long talk works and works again after a restart; a step whose timing or lines changed is then played once at 20, since how long it feels is a finding too.

Play as someone who knows vanilla Minecraft and nothing else, using only what the screen shows (the task panel, chat, the HUD, the open screen). At every step, a no to any question is a finding.

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
| What about alone? | Something waits for, asks for or needs a second player, or a fight or a hold is out of reach for one |
| What about my teammate? | One player is done and does not know whom they wait for, or the other does not know they are waited for |
| What about a restart? | Stopping the server inside the step (mid-dusk, mid-hold, mid-build) and starting it again moves the clock, repeats lines, builds twice or crashes |
| What about odd land? | A place the story puts down crashes, floats or comes out thin on another seed; when the change touches how a place is put down or what it is built from, build it on the state seed, three others (`server.py start --seed <n>`, then `story <quest/step>`) and the seed of any world the user reported |
| What else speaks? | A mod's chat line, key, HUD element, starting item or random event that the design does not know of |
| Does it look right? | A block is not what the thing is made of (the `art` skill) |
| Does it sound? | A moment's music or cue does not fire (the client log names each story piece and cue it plays), or Reactive Music does not fade out under a piece and come back after |
| Where is it? | Something the story gives cannot be found again: no item, charm or tooltip shows it |

Fix every finding in the code, the text (`lore` skill) or the look (`art` skill); a finding that needs a decision goes to the user as an Open question in its design document. Read both logs for what the work caused: `log` on the client driver, `build/devserver/logs/latest.log`.

## The gallery

The user sees the work through `gallery/`, which git keeps, so a commit shows which pictures are new and which changed. `gallery/shots.toml` lists every shot: its `id` (the path of its pictures), a `caption` saying what it shows, where it is taken (`at`: `showroom` or a state such as `ash/the_cellar`), the driver `steps` that set it up, and its `kind`:

| Kind | Pictures |
|---|---|
| `screen` | A screen over the world, such as the journal, as PNG at interface scales 2, 3 and 4 (`<id>-2.png` and so on) |
| `hud` | The world with the HUD as JPEG at scales 2, 3 and 4 |
| `world` | The world as the player sees it, one JPEG at scale 3 |
| `views` | Everything the steps put in the workshop, from the front, the side, above and three-quarters (`<id>/<side>.jpg`) |
| `tour` | A shrine or the Last Camp (`subject`) through the tour's cameras (`<id>/<camera>.jpg`) |

Every change to something a player sees adds or updates its shots, one for each facet the player can meet: each ribbon and entry of a screen, each state it shows (empty, waiting, done, unfolded), each side of a thing, and each step of the story that shows it. Then `just gallery` shoots only what the scope can show, named one by one or by the narrowest folder, since a story state's shot takes 20 to 30 s and the whole gallery about half an hour:

| What changed | What is shot |
|---|---|
| One journal page, its code or its text | That page's shots |
| The journal's frame, ribbons or list | `journal` |
| One HUD element | The `hud` shots that show it |
| A creature, block, item or model | Its `views` shot |
| A shrine or the Last Camp | Its `tour` shot |
| A story line or step | The shots at the states that show it |
| The stamp (`style.toml`, the fonts, its sprites) | Every screen and HUD shot |
| Code, data or text no screen shows; tools, tests, documents | Nothing |

```console
$ setsid nohup just gallery journal/gear hud/banner-begun > /tmp/gallery.log 2>&1 < /dev/null & echo $! > /tmp/gallery.pid
$ tail --pid="$(cat /tmp/gallery.pid)" -f /dev/null
```

A shot's `after` steps put back what its steps changed beyond the world, such as an option. A shot of a screen outside the world, such as the title or a loading screen, says `outside = true`: it starts in its world and leaves it with `leave`, and the next shot opens its world again. A facet the story reaches only slowly is set up by a dev command among the shot's steps: `/wayfarer story unclaim <reward>` hands a taken reward back, `/wayfarer story said <line>` marks a line of `story/gear.toml` said, and `/wayfarer story gear <row>` shows a Gear row.

Shots in the story run in the story's order, and at each state those that only look (screens, the HUD, the journal, no command) come first: a shot that leaves the story, the HUD, the journal and the inventory as its opening left them is followed in the same opening, and any other shot makes the next one open its state anew, so no shot depends on another (the driver's `digest` compares them). The showroom's shots share one opening, and a `views` shot starts from a cleared workshop. A shot hides the chat unless it says `chat = true`, and the world stands still while it is shot (the driver's `still`): a state's shots are taken 128 ticks after it opened, with animated textures on their first frame, no particles and the HUD's own motion held, so a screen of something unchanged comes out pixel for pixel the same. A HUD or world picture can still differ by a few hundred pixels where another mod moves on its own; that is noise, not a change.

A picture whose every pixel matches the one in `gallery/` (within 2%) keeps its file, so git shows only what moved. `just gallery` writes a page per folder and an index (`gallery/README.md`) with every picture under its caption, deletes the pictures no shot names, and prints what is new, changed and deleted; `just lint` fails while a shot lacks its pictures, a picture lacks its shot, or a page is out of date. A failed run prints the shots it did not reach (`the rest: just gallery ...`): fix the cause and shoot only those.

## The report

Answer first, then only what the user needs:

- **Finished**, with what the gates said.
- **Found and fixed**, one line each.
- **Not checked**, and why.
- **Gallery**: the pictures that are new and changed, with their folders' pages.
- **For you to play**: the `playtest` skill's rows the scope touched, one line each (where to go, what to do, what should happen), leaving out what a picture already shows.

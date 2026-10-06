---
name: walkthrough
description: Play every piece of Wayfarer work a player meets the way a first-time player will, before reporting it done - a dedicated server with a teammate, only what the screen shows, a restart inside every timed step, odd land - and fix what a newcomer would trip over. Use after building or changing anything a player meets - quests and steps, systems, the HUD and screens, player text, and mods or their configs.
---

# Walkthrough

The user's playtest is for how the pack feels. Everything that would leave a player stuck, confused or crashed is found here first, by playing the new work the way the user will: on a server, with a teammate, knowing nothing but what the screen says.

## Set up as the user plays

- **A dedicated server and a teammate.** Team rules (two thirds of the team, everyone's own rewards, who waits for whom) only show with two players. Play on the dev server with the client joined to it and a pretend teammate beside you:

  ```console
  $ python3 scripts/server.py start --fresh
  $ python3 scripts/server.py run 'op Wayfarer' 'wayfarer pretend join Mo' 'spreadplayers 0 0 0 4 false Mo'
  $ python3 scripts/client.py start
  $ python3 scripts/client.py run 'join localhost:25566'
  ```

  The teammate does what you tell it (`execute as Mo run ...`, `tp Mo @p`); move it the way a real teammate would, late and in the wrong place.
- **Start before the new work.** A state (`--state <quest/step>`) may bring you to the step before it; walk the new steps themselves, never `story next` past them.
- **The pack's own settings.** Keys come from `pack/config/defaultoptions`; read every chat line on joining, since every mod's greeting reaches the user too.

## Play by the screen alone

Play as someone who knows vanilla Minecraft and nothing else. At every step, using only what the screen shows (the task panel, chat, the HUD, the open screen) and nothing from the code or the design, answer each question. A no is a finding.

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
| What about my teammate? | One player is done and does not know whom they wait for, or the other does not know they are waited for |
| What about a restart? | Stopping the server inside the step (`server.py stop` mid-dusk, mid-hold, mid-build) and starting it again moves the clock, repeats lines, builds twice or crashes |
| What about odd land? | A place the story puts down (a town, a camp, a hall) crashes, floats or comes out thin on another seed; build it on the state seed and on at least three others (`server.py start --seed <n>`, then `story <quest/step>`), and on the seed of any world the user reported |
| What else speaks? | A mod's chat line, key, HUD element, starting item or random event that the design does not know of |
| Does it look right? | A block is not what the thing is made of, as the art skill's structures rule asks |
| Does it sound? | A moment's music or a cue the design names does not fire (the client log names each story piece and cue it plays), or Reactive Music does not fade out under a piece and come back after |
| Where is it? | Something the story gives cannot be found again: no item, charm or tooltip shows it |

## Then

- Fix every finding before the report: in the code, the text (lore skill) or the look (art skill). A finding that needs a decision goes to the user as an Open question in its design document, never decided in code.
- Read the server's and the client's logs for errors and warnings the new work caused (`log` on the client driver, `build/devserver/logs/latest.log`).
- The report names what the walkthrough found and fixed in one line each, then hands the user the playtest skill's checklist for what only they can judge.
- Close every client and server you started.

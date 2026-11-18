---
name: finish
description: Finish everything built since the last commit, so the user can commit it - the gates, a newcomer's walkthrough of what a player meets, and a report with what the user plays. Use when the user calls it, or asks to run the gates or walk the work through. Development runs none of this.
---

# Finish

Development checks each change in the running game as far as the change needs, and nothing more. Finish proves the whole of the uncommitted work once, the state the user is about to commit: what a check proves goes stale with the next change, so it runs here, not after every piece. It never commits; that is the user's, through the `commit` skill.

## The scope

- Everything uncommitted: `git status --short --untracked-files=all` and `git diff HEAD --stat`. When this session already finished part of it, only what changed since.
- From the files, list what a player meets that changed: steps, screens, the HUD, people, creatures, places, items, sounds, text, mods and their configs. That list decides the walkthrough and the playtest rows.
- When nothing a player meets changed (tooling, tests, documents), only the gates run.

## The order

1. `just voice prune`, which deletes the clips of the texts the change rewrote, then `just lint`, fixing every finding. Lines and speakers waiting for the recording are no finding: recording waits for the user's word. The report says how many wait.
2. The walkthrough of the list, fixing every finding.
3. `just test`.
4. The report.

A fix after a step means its earlier steps run again for what the fix touched.

## Walk it through

Play what changed the way players will, knowing nothing but what the screen says (the `playthrough` skill's newcomer): alone first, since about half of them play alone, then with a teammate.

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
- **Every screen off the title and back.** When the front door, a screen or a mixin of a vanilla screen changed, open each of the title's buttons (Singleplayer, Multiplayer, Mods, Language, Options, Accessibility) and leave it the way a player does, with Done, Back or Cancel, back to the title; a driver that only opens a world never leaves one, which is how a crash on returning to the title reached the user.
- **A touched timed step at the player's pace.** `/tick rate 80` checks that a hold, a dusk or a long talk works and works again after a restart; a step whose timing or lines changed is then played once at 20, since how long it feels is a finding too.
- **Every changed step once at the player's real pace.** From the state before it, play it through at 20 ticks a second, without `story next`, `/tick rate` or a teleport past what a player would walk, waiting at the fire as long as a player would. The dev code's story checks (`StoryChecks`) watch every world and put a `check` line into the driver's batch when a speaker talks out of the listener's sight or before the listener's land is drawn, a step whose condition holds stands still for a minute, a tracked step's compass has nowhere to point for a minute, a person stands inside a block or on a tree's leaves, or a clue a step asks for cannot be clicked; each is a finding. A bug the user met that no check or test caught gets the game test or check that would have, with its fix.

Play as the `playthrough` skill plays, with its hands and eyes, over this scope only, and ask its questions at every step: a no to any is a finding.

Fix every finding in the code, the text (`lore` skill) or the look (`art` skill); a finding that needs a decision goes to the user as an Open question in its design document. Read both logs for what the work caused: `log` on the client driver, `build/devserver/logs/latest.log`.

## The report

Answer first, then only what the user needs:

- **Finished**, with what the gates said.
- **Found and fixed**, one line each.
- **Not checked**, and why.
- **For you to play**: the `playtest` skill's rows the scope touched, one line each (where to go, what to do, what should happen).

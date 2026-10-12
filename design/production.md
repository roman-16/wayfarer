# Production

How the pack reaches its players and keeps up with them. Until the first release it reads:

```text
Unreleased and in development: nothing is published yet, and a world made with one version may not open in the next.
```

## Settled

### R1 Performance: no limits for weaker machines, one shader, a graphics setting

- The pack is never cut back so that weaker machines can run it. A reasonably high-end PC should run it at about 90 fps, as a guide and not a gate, and there is no fixed budget: what a player finds too slow is fixed when it comes up.
- There are two modes: no shader, or Complementary Reimagined with Euphoria Patches, the one shader the pack ships and its looks are judged under. It works with Distant Horizons, scales down to weaker PCs through its profiles, is the most used and best kept of its kind, and looks the way the pack wants. Bliss, Photon, Solas and MakeUp Ultra Fast leave the pack.
- Video Settings, Sodium's screen, opens on a Wayfarer page, first in its list above Sodium's own, with a Graphics group: a Preset that steps through four settings, High on first launch, and Shaders on or off. Each step sets the shader's profile, the draw distance and Distant Horizons' far view together; changing any of them by hand makes the Preset read Custom, and pressing it again picks a step and sets them all. The Wayfarer mod sets Iris's profile, Distant Horizons and the video settings itself. The page is Sodium's own look, the one screen outside the stamp.

  | | Low | Medium | High | Ultra |
  |---|---|---|---|---|
  | Shader profile | Low | Medium | High | Ultra |
  | Draw distance | 8 chunks | 10 | 12 | 16 |
  | Far view | Off | 128 chunks, about 2 km | 256, about 4 km | 512, about 8 km |
  | Far view's share of the processor | Least | Low | Balanced | Balanced |

  ```text
  Options › Video Settings › Wayfarer › Graphics
    Preset    High
    Shaders   [x]
  ```

**Why:** The pack is made for the friends who play it, on the machines they have, and limiting it for weaker ones would cost the atmosphere it is built for. Low is the exception: on a gaming laptop (RTX 2070, i7-10750H) the far view alone took the game from 130 frames a second to 20 to 37, so the lowest step turns it off. One shader means every look is judged once, and one Preset with four steps lets a newcomer pick how hard their machine works without knowing Iris, Distant Horizons and a dozen video settings. It lives where players already look for graphics, in Sodium's Video Settings, and is named Preset because Sodium already has a Graphics option (Fast or Fancy).


### R3 Blind players and the play record

- Two or three friends who never read `design/` play the part under test on the server in one evening. The user plays along as one more teammate and never says what to do or how; voice chat runs as normal and is their thinking aloud. After 10 minutes stuck on one step the user may give one hint, typing `/wayfarer hint` so the record notes it; every hint counts as a stall the screen did not explain, against phase 4's done-when. Friends who played a part blind never test that part blind again.
- Every play on a server, blind or not, leaves a record for the agent to read afterwards. The Wayfarer mod writes it on the server as one JSON line per event in `wayfarer/record/<date>.jsonl`, and `just exaroton` downloads it every minute beside the log, through exaroton's file API, reading only:

  | What | When |
  |---|---|
  | Each player's position, dimension, biome, facing, what they hold, health, food and temperature, and the distance to their tracked goal | Every 16 seconds, and at every event below |
  | A quest or step begun and ended, a task ticked, the tracked quest changed | As it happens |
  | A conversation's lines, the answers offered, who chose what and how long it took | As it happens |
  | A click on a person, a clue or a story block; the ember held up; the journal, a ribbon, the map and the inventory opened | As it happens, the screens sent by the player's game |
  | Chat, and every command typed | As it happens |
  | A death with its cause, a respawn, a grave taken back | As it happens |
  | A tent pitched or packed, a fire lit, a rest, a sleep, a camp reached | As it happens |
  | A place entered (a town, a hall, a camp, a road scene) and a road scene's outcome | As it happens |
  | Something new in an inventory for the first time, a reward taken, a waystone used, a fold | As it happens |
  | A stall: a player not moving, or not getting closer to the tracked goal, for 2 minutes | When it begins and ends |
  | A hint | As it happens |
  | Each player's frame rate, graphics setting and memory | Every minute, sent by their game |

- `just play-report <folder>` turns a recorded session into a report for the agent: a timeline per step with how long the team took against the time it should take; every stall with what each player was doing, saying and looking at; the conversations and choices; deaths and their causes; the screens opened and how soon; where the team walked, as a map of their paths over the terrain with the goals marked; and per player the lowest frame rate and fullest memory. Its findings go into a playtest's list with the user's notes, the stalls the screen did not explain and the hints first.

  ```text
  1.1 The Pull › Find the Diggers' tunnels      42 min (target 15)
    stall 18:12–18:31  all three circled 400 m south of the tunnels
      Mo: "where is it even" · held the ember 0 times · opened the journal twice
  ```

**Why:** A silent observer is how studios test blind, and a hint that counts against the bar keeps the user honest. The agent cannot watch the evening, so the world has to tell it afterwards where people stood, what they tried and said and where they waited; positions with the distance to the goal show a team lost long before anyone says so, and chat and screens show what they looked for.

## Open

### R2 Updates and saves

Before release: how a running world takes a new version without losing its story, places or things; how players' machines and the server get it; and what a version number promises.

### R4 Languages

English only, or German too, and when.

### R5 Release and credits

Private, or published on Modrinth; and how the pack credits the mods, sounds, music and voices it ships (`pack/CREDITS.md` holds the music today).

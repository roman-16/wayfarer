# Production

How the pack reaches its players and keeps up with them. Until the first release it reads:

```text
Unreleased and in development: nothing is published yet, and a world made with one version may not open in the next.
```

## Settled

### R1 Performance: a minimum machine, one shader, a graphics setting

- The pack names its minimum machine: a graphics card like the RTX 2070 Super for laptops, a processor with six cores and twelve threads like the i7-10750H, 16 GB of memory and a screen of 1920 by 1080. On it, Low with the shader off is playable: at least 60 frames a second on average, and a 1% low (the average of the slowest hundredth of frames) of at least 30, while walking, riding or flying over new land, alone and on a server. The first minute after a world opens, a server is joined or a waystone is taken is loading and does not count.
- Above Low the pack is never cut back so that weaker machines can run it. A reasonably high-end PC should run High at about 90 fps, as a guide and not a gate, and what a player finds too slow is fixed when it comes up.
- `python3 scripts/client.py bench --minimum` measures Low and Medium on a development PC held to the minimum machine's speed, and the play record (R3) shows every player's frame rate on a server.
- There are two modes: no shader, or Complementary Reimagined with Euphoria Patches, the one shader the pack ships and its looks are judged under. It works with Distant Horizons, scales down to weaker PCs through its profiles, is the most used and best kept of its kind, and looks the way the pack wants. Bliss, Photon, Solas and MakeUp Ultra Fast leave the pack.
- Both modes show the same night. Without a shader More Darkness darkens it: a moonless night is black beyond the torch, a full moon shows the land in pale grey, caves are black, and the eyes adapt to the dark over a few seconds. One moon serves both: every phase between new and full is as dark as the shader makes it, and the new, partial and full moons' light are three numbers in `balance.toml` `[sky]` that the shader's options and More Darkness both read, so a three-quarter moon is dark with the shader off too. The clouds float at `[sky]` `cloud_height`, 384, above the build limit with and without the shader, so a pillar or a tower never ends in them. With the shader on, the Wayfarer mod switches More Darkness off, since the shader lights the world itself and never reads the light that More Darkness darkens, and the shader's own settings (`generator/shader.toml` and `balance.toml` `[sky]`: the moon's phase on its light, its light at night, its light in caves and colours fading at night) are tuned until a new moon, a full moon, a cave and a torch-lit room look as bright as without it.
- Video Settings, Sodium's screen, opens on a Wayfarer page, first in its list above Sodium's own, with a Graphics group: a Preset that steps through four settings, High on first launch, and Shaders on or off. Each step sets the shader's profile, the draw distance, how far around the player a world of their own keeps running (the simulation distance) and Distant Horizons' far view with its detail together; changing any of them by hand makes the Preset read Custom, and pressing it again picks a step and sets them all. The Wayfarer mod sets Iris's profile, Distant Horizons and the video settings itself. The page is Sodium's own look, the one screen outside the stamp.

  | | Low | Medium | High | Ultra |
  |---|---|---|---|---|
  | Shader profile | Low | Medium | High | Ultra |
  | Draw distance | 6 chunks | 10 | 12 | 16 |
  | Simulation distance | 6 chunks | 10 | 12 | 12 |
  | Far view | 128 chunks, about 2 km | 128, about 2 km | 256, about 4 km | 512, about 8 km |
  | Far view's detail | Low | Medium | Medium | High |
  | Far view's share of the processor | Least | Low | Balanced | Balanced |

  ```text
  Options › Video Settings › Wayfarer › Graphics
    Preset    High
    Shaders   [x]
  ```

- The pack asks the launcher for 8 GB of memory (`balance.toml` `[heap]`), which Prism Launcher and the CurseForge app give a fresh import, up to nine tenths of what the machine has.

**Why:** The pack is made for the friends who play it, on the machines they have, and the far view is what makes its world feel vast, so every step keeps it, Low included. A floor needs a named machine to stand on, and the weakest one a friend played on is the minimum. Playable is judged on the average and the slowest hundredth of frames, since one hitch while new land loads is felt as a stutter and not as an unplayable game. On the minimum machine three things decided the floor, measured with `bench --minimum`: More Darkness read the screen's brightness back from the graphics card every frame and made the processor wait for everything drawn before it, which on a server took Low with the far view from 254 frames a second to 84, so the Wayfarer mod has it read the brightness a few frames later without waiting; Reactive Music's player checked for its next song without pause and kept a third of a core busy, so it rests between checks; and alone, the land generated and kept running around the player costs the processor most, so Low draws and runs 6 chunks and the far view covers what lies beyond. A team in which one friend plays with the shader and another without sees one night, so the dark stays a danger for both and the fire a refuge. One shader means every look is judged once, and one Preset with four steps lets a newcomer pick how hard their machine works without knowing Iris, Distant Horizons and a dozen video settings. It lives where players already look for graphics, in Sodium's Video Settings, and is named Preset because Sodium already has a Graphics option (Fast or Fancy). Prism gives an imported pack 4 GB unless the pack asks for more, too little for this pack and its far view.

### R3 Blind players and the play record

- Two or three friends who never read `design/` play the part under test on the server in one evening. The user plays along as one more teammate and never says what to do or how; voice chat runs as normal and is their thinking aloud. After 10 minutes stuck on one step the user may give one hint, typing `/wayfarer hint` so the record notes it; every hint counts as a stall the screen did not explain, against phase 4's done-when. Friends who played a part blind never test that part blind again.
- Every play, on a server or alone, blind or not, leaves a record for the agent to read afterwards. The Wayfarer mod writes it into the world's folder as one JSON line per event in `wayfarer/record/<date>.jsonl`, and on exaroton `just exaroton` downloads it every minute beside the log, through exaroton's file API, reading only:

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
  | A player's land drawn around them after joining, a change of world or a respawn, with how long it took | As it happens, sent by their game |
  | A hint | As it happens |
  | Each player's frame rate, graphics setting and memory | Every minute, sent by their game |

- `just play-report <folder>` turns a recorded session, a playthrough's folder or a save into a report for the agent: a timeline per step with how long the team took; every stall with what each player was doing, saying and looking at; the conversations and choices; deaths and their causes; the screens opened and how soon; where the team walked, as a map of their paths over the terrain with the goals marked; and per player the lowest frame rate and fullest memory. Its findings go into a playtest's list with the user's notes, the stalls the screen did not explain and the hints first.

  ```text
  1.1 The Pull › Find the Diggers' tunnels      42 min
    stall 18:12-18:31  all three circled 400 m south of the tunnels
      Mo: "where is it even" · held the ember 0 times · opened the journal twice
  ```

**Why:** A silent observer is how studios test blind, and a hint that counts against the bar keeps the user honest. The agent cannot watch the evening, so the world has to tell it afterwards where people stood, what they tried and said and where they waited; positions with the distance to the goal show a team lost long before anyone says so, and chat and screens show what they looked for.

### R6 How long each step should take

R3's report sets each main quest's time against its budget, and each act's against the act's (`story.md`, S2). Steps inside a quest show their time alone.

| Part | Budget | Its main quests |
|---|---|---|
| Prologue | 1 h | The three together |
| Act 1 | 4 h | The Pull 1 h, The Deserter ½ h, The Frost Walkers 1¼ h, The Bell 1¼ h, The Hooded Traveller and The Name a few minutes each |
| Act 2 | 4 h | The Crossing 1 h, The Tide Folk 1 h, The Worm 1¼ h, The Hollow ¾ h, Your Eyes and Malgven's Warning a few minutes each |
| Act 3 | 2½ h | The Morning After ¼ h, The Scribes' Books 1 h, The Rider ¼ h, The Keepers' Door ½ h, The Dreaming a few minutes, and the walk out of the hollow |
| Finale | ½ h | The Gate and The Doors; the dragon in Blind it on top |

```text
1.3 The Frost Walkers   1 h 32 min   budget 1 h 15 min
Act 1                   4 h 40 min   budget 4 h
```

**Why:** The road runs about twelve hours (`vision.md`, D8), so a quest that runs long is found by its number, not by a feeling that the evening dragged; a hall's quest is mostly its walk, so its budget is what tunes the distance to the next hall (`systems.md`, M40).

## Open

### R2 Updates and saves

Before release: how a running world takes a new version without losing its story, places or things; how players' machines and the server get it; and what a version number promises.

### R4 Languages

English only, or German too, and when.

### R5 Release and credits

Private, or published on Modrinth; and how the pack credits the mods, sounds, music and voices it ships (`pack/CREDITS.md` holds the music today). Whatever it settles, nothing ships silent: every line is recorded and every speaker has a voice (`narration.md`, Recording).

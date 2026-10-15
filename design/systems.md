# Systems

Every mechanic the pack needs to tell `story.md`, why the world has it, and the spike (a small throwaway build) that proves it can be built. Each question here gathers what `vision.md` and `story.md` left for phase 3; phase 3 is done when every mechanic has a reason in `lore.md` and a spike that works.

## Settled

### M1 The quest engine: the Wayfarer mod's own

- Quests run on an engine of the Wayfarer mod's own, with its own journal screen for the quests, their steps and the codex. Quests are written as data in `story/quests.toml` and turned into the mod's data by `just generate`. FTB Quests, FTB Teams and FTB Library leave the pack.
- The world's story state lives in the mod's world-save data, as `Journey` already keeps the shards and the ember's waking, so it is one for the whole world (D11).
- A quest is a list of steps, each with a goal (a hall, a kind of place, a person, a memory), and a step's choices are remembered by name for later scenes to check:

  ```toml
  [[quests]]
  id = "the_theft"
  kind = "main"
  act = 1
  after = ["the_last_frost_walker"]

  [[quests.steps]]
  id = "reach_hall"
  goal = { hall = "frost_walkers" }
  journal = "The Frost Walkers' hall. Nell wants a name."

  [[quests.steps]]
  id = "memory"
  memory = "the_theft"

  [[quests.steps]]
  id = "tell_nell"
  talk = "nell"
  choices = [
    { id = "told", text = "The man whose fire I carry.", remember = "nell_knows" },
    { id = "hid", text = "I didn't see his face.", remember = "nell_lied_to" },
  ]
  ```

  The field names only show the idea; the spike settles the real format.

- What the engine and the journal carry, everything the design asks of them:

  | Part | What it does | From |
  |---|---|---|
  | Main and side quests | Listed apart in the journal, each with its current objective; main quests carry the acts, and some carry no shard | D3, D6 |
  | Steps | A quest is steps; a step's goal is a hall, a person, a memory, a fixed place, a place the quest puts down, or the nearest place of a kind ahead | D3, D16, S13 |
  | Order | A quest opens after others, or "by count" after a number of an act's halls, whichever they were | S3, S16 |
  | Tracking | Each player chooses which quest they follow, if any, and the compass shows its goal as the player knows it: by feeling (the ember flares and gutters, exact within 256 blocks), by rumour (a direction and a rough distance, exact within 256 blocks) or by being told (exact from the start) | D6, S7 |
  | Choices | Remembered by name for the whole world and brought back later, in a scene, at a fire, in a person's words or at the doors; an ordinary answer goes to the first click, a hard decision to two thirds of the team | D1, D10, D12 |
  | Failing | Falling never fails a quest; a story that goes wrong turns the quest into a worse version; a challenge that goes wrong lets its step be tried again, resetting only the quest's own people and places; a main quest always leaves a way on | D6 |
  | Clocks | A few quests carry a clock the journal shows, and turn when it runs out | D6 |
  | Rewards | Things and story, never XP: gear, supplies, a people's gift, a shard and its gift. A reward waits in the journal until claimed, so every player claims their own, and a player who was away finds every reward they missed | D7, D11 |
  | Unlocks | Ships, flight, sight through stone, the memories and the frame open with their quests, never with a number of shards | D15 |
  | One state | Quests, choices, shards and people belong to the world, as far as the team has got; what one player does, the world does | D11 |
  | The day | "Make for the caravan" is always there; a quest can keep the player out for a night and set the clock itself, or keep them at a place for a while | D2, D14 |
  | Lines in chat | Every line goes to everyone and waits for its voice before the next | D11, `narration.md` |
  | The journal as a codex | The story so far, every person met, every word of the world, every book and talk, written plainly as "I" from the player's point of view, holding only what the team knows so far, shared and always up to date | D6, D11 |
  | Narration | Journal entries and quest descriptions read aloud like book pages, once each, with a button to hear them again | `narration.md` |

- **The spike works** (`mod/src/dev/java/io/github/roman16/wayfarer/spike/`, dev code that never ships): a three-step quest written as data to the nearest Gazebound outpost ahead, its mark on the compass; a hold whose fall fails the step and whose retry resets only the quest's own people; a choice remembered with its day and brought back by Corin the next day; a reward each player claims once from the journal, a player who joins later told on login that it waits; a journal screen whose codex entry grows with every step; and pretend players and the client sharing one state. What it showed:

  | Finding | For the engine |
  |---|---|
  | Vanilla's own structure search finds the nearest outpost of a kind 1,024 blocks ahead in about 0.2 s | A quest's goal is found once when its step opens, on the server thread, without Async Locator |
  | People spawned before a player is near stand in unloaded chunks and are missed | A quest's people come when a player is within reach and the chunks are loaded, and a retry brings them back the same way |
  | The world's save data is written on a background worker | Anything reading the story state waits for that worker first, as the `state` step does |
  | The compass already takes extra marks from other code (`CompassEvent`) | Quest goals reach the compass through that hook, with no second compass path |
  | One payload of the journal's view per player keeps every screen current | The journal shows the world's state with each player's own claims, sent whenever it changes |
- **The frameworks beside it:** ArcQuest keeps one live quest state for each online player and its licence (All Rights Reserved) forbids changing that, so it cannot hold one state for the whole world and stays out. HollowEngine (CC-BY-NC-SA 4.0) loads with the pack's NeoForge and brings people animated from Blockbench or glTF models and keyframed cutscenes made in its in-game editor, at 33 MB; weighed at the camp (M5) and memory (M8) spikes, it stays out: Easy NPC carries the people, and the engine runs their scenes as data.

**Why:** FTB Quests 2101.1.36 has none of the hard parts, no failing, no clocks, no positions, no world-wide state, and keeping it would put every quest in two systems that must agree. The mod already holds the world's state and speaks the story's lines from data, so the engine grows from what is there; the journal screen is the price.

### M2 The journal, the map and the compass

- The journal opens with a key (J by default), anywhere; there is no item to carry or lose. Its tabs:

  | Tab | Holds |
  |---|---|
  | Quests | Main and side, each with its current step and any clock; the tracked one marked, done and failed apart, and rewards waiting to be claimed |
  | The story so far | What happened, act by act, as "I" |
  | People | Everyone met, what they did and said |
  | Words | Every word of the world: the gaze, the Gazebound, the oath, the scar |
  | Pages | Every book and letter read, to read again |
  | At the fire | Every talk with the voice, Maren and the people |
  | Stones | Where the player fell, with what each stone holds |
  | Map | The world map |

- The map is JourneyMap's world map, opened from its tab in the journal or with M. There is no minimap anywhere on screen, no cave mapping, no radar and no waypoints of the player's own; the places they have found are added by the Wayfarer mod: the Last Camp, Tollbridge and the caravan from the start, and every place a quest leads to once a player has stood within 64 blocks of it, his first camp, the old tunnels, the ford and the rest, so the map keeps where the team has been. Map Atlases leaves the pack.
- The compass is on screen from the first moment of the world, before the ember wakes: until then it reads "the ember is cold" and shows only friends, the player's own tent (▲) and their stones. A tent standing more than 256 blocks away gets its own line under the compass, so a tent left at an old camp is never lost. The first time a goal shows, the task panel says to follow its mark at the top of the screen.
- The compass shows the tracked quest's goal, the player's friends and their stones. A goal known by feeling shows only the ember's flaring, in gold, and its guttering, in blue, with no distance until the exact mark within 256 blocks (D6). Every fall leaves its own stone where the player fell; the compass shows all of them, the one holding the most the strongest. In the journal a stone can be tracked, so the compass points at it like a quest, or let go, which takes it off the compass, as when a player gives their old things up and starts afresh.

  ```text
  Compass ◆ Your stone, 1,240 blocks (bold): 34 things
          ◇ Your stone, 300 blocks (faint): 3 things
  Journal › Stones › The pass: 34 things, 2 days ago   [Track]  [Let it go]
  ```

**Why:** A journal that holds every reward and every word must never be lost, so it is a key, not an item, and the map lives beside it so the screen stays clear: the compass is the only thing on the road that points. JourneyMap's server settings switch cave mapping and the radar off for everyone, which keeps the halls hidden and the rule on the server, not in a client setting. The stone a player wants back is the one with their things in it, not the first or the last, and letting one go lets them start afresh.

**The spike works** (`mod/src/dev/java/io/github/roman16/wayfarer/spike/`, dev code that never ships), with JourneyMap 6.0.9 in the pack in place of Map Atlases: the pack's server settings switch off the minimap, cave mapping, the radar, waypoints, death points and beacons for everyone, in singleplayer as on a server; the outpost a quest found shows on the world map; M and the journal's Map button open it; every fall with things in it leaves a stone the compass shows with what it holds, the journal lists each player's own stones, and letting one go takes it off the compass while the stone stays. What it showed:

| Finding | For the engine |
|---|---|
| JourneyMap keeps its server settings in `journeymap/server/6.0/journeymap.server.global.config` beside `config/`, read from the game folder in singleplayer and the server folder on a server, and fills in every key the file leaves out | The pack ships only the keys it changes, in `pack/journeymap/`, and the instances copy that folder with `config/` |
| JourneyMap opens its map only when no other screen is open, and shows its About screen the first time each version opens it | The journal closes itself before opening the map, and the mod marks the running version's About screen as seen |
| JourneyMap's map key defaults to J, which M2 gives the journal | The mod's own M opens the map, and JourneyMap's J is moved off when the journal takes J |
| The places found reach the map as marks drawn by the mod through JourneyMap's API, not as waypoints, so the server's ban on waypoints does not hide them | Every place found is a mark of the mod's, kept in step with the world's state |
| Gravestone announces each death with an event of its own and builds the stone inside it; its archive of deaths outlives the stone | The mod records each stone right after Gravestone builds it, drops it once its block is gone, and counts its things from the stone itself |
| Gravestone builds a stone even for a fall with nothing in it, and has no setting against it | A stone with nothing in it is not recorded |
| The compass already takes marks from other code, so the single grave of the last death gives way to every stone through the same hook | Stones reach the compass the way quest goals do; the HUD still needs to draw the fullest one strongest |

### M3 Conversations: one scene at a time, for everyone

- Every player online follows a conversation: within reach as subtitles, with the answers numbered beside the crosshair, and farther away in chat, each speaker in a colour of their own (`interface.md`, I4).
- An ordinary answer can be chosen only by a player near the person, within about 32 blocks, and the first choice decides. A hard decision, a shard or a door, can be voted on by every player online, wherever they are, and needs two thirds.
- A person speaks under what the player sees until someone says their name, and under their name from that line on; the name over their head follows. Every person is introduced in their first scene.

  ```text
  <A big man> Stay back! ...It's a person. It's only a person.
  <A woman in green> I'm Agna. I mend people, when there's anything left to mend. That's Hob, our smith.
  <Agna> You've seen them out there. They never take anything. They only open the chest.
  ```

- There is one conversation at a time in the world. A second person spoken to answers with a line of their own and waits.

  ```text
  <Bram> Not now. I'm listening to them.
  ```

- A hard decision waits, and the person with it, until two thirds of the online team have chosen; walking away pauses a conversation, and coming back carries it on.

  ```text
  <Nell> Give it to me. I'll bury it with them.
    [Give it to her 1/3]  [Keep the shard 1/3]   (waiting for Mo)
  ```

**Why:** The story stays one for the whole world, while nobody far away answers for the player standing in front of the person, and the decisions that belong to everyone reach everyone. One scene at a time keeps the chat readable and matches the narration's one voice at a time, and a decision that waits never lets someone's absence choose a shard. A name the player never heard is a riddle in the speaker's place, so names wait for the line that says them.

**The spike works** (`mod/src/dev/java/io/github/roman16/wayfarer/spike/TalkSpike.java`, dev code that never ships), with Easy NPC 7.13.0 in the pack: a right-click on a person's Easy NPC body starts their lines in chat for everyone; an ordinary answer is a link in chat that counts only from a player within 32 blocks, and the first decides; a hard decision shows its tally and who is still missing after every vote, needs two thirds of the players online when it opened, keeps the vote of a player who leaves and takes the vote of one who joins later; and a second person spoken to says they are busy. Walking away and coming back is not in it yet. What it showed:

| Finding | For the engine |
|---|---|
| Easy NPC's body is the creature `easy_npc:humanoid`, and NeoForge raises a click on it before Easy NPC's own handler | The mod takes every click on the pack's people and Easy NPC never opens its own dialog or editor for them, so a person is shaped from the mod's data, not Easy NPC's editor |
| A link in chat runs a command for whoever clicks it | Answers are commands the server checks: who clicked, how far from the person, and whether the vote is still open |
| No Easy NPC jar has a recipe | Its items for making people cannot be made in survival |
| Pretend players can join the dev client's own world, not only the dev server | A rule for a team is tried with the real client and its chat beside pretend players |

### M4 Road scenes: placed ahead, taken away behind

- A scene is set up 64 to 128 blocks ahead on the way the player is going, behind a hill or a bend, never while anyone can see the spot. Once everyone has walked out of sight of it, its props and people are taken away; what the story keeps stays, such as a grave the player dug, a burned cart or the herder's fold.

  ```text
  You crest a ridge. Below, by the stream: a cart on its side, a dead mule, tracks into the reeds.
  <Wend> Somebody left in a hurry. Or didn't.
    Follow the tracks into the reeds  /  Walk on
  ```

- Scenes are picked by rules: each fits the land (a herder on grassland, a cart by a road or a river), the act and what the story knows. One comes about every five minutes with some randomness, never the same twice close together, and a variant remembers the last meeting. The sea in Act 2 and the sky in Act 3 have scenes of their own.
- Players within 128 blocks of each other are one group, and each group gets its own scenes on its own rhythm.
- The voice speaks on the road when something fits, such as a new land, a fight won or a place passed, and never more than once every two to three minutes.

**Why:** Placing and taking away only out of sight is the caravan's rule (D14), so nothing ever appears or vanishes in front of anyone and the world is not littered with old scenes. Scenes that fit and remember are what make Red Dead Redemption 2's encounters feel alive, and a voice that speaks when something fits stays company instead of noise.

**The spike works** (`mod/src/dev/java/io/github/roman16/wayfarer/spike/SceneSpike.java`, dev code that never ships): from the way a player walked, a herder's halt (an Easy NPC body, hay, barrels, a cold fire) goes down 64 to 128 blocks ahead where no player has a line of sight to it, is marked seen when one does, and once every player is 64 blocks past it and out of sight it is taken away with all 36 blocks it replaced put back; the next is due five minutes later, give or take one, which the spike only set and did not wait out. What it showed:

| Finding | For the engine |
|---|---|
| A line of sight traced against blocks' solid shapes is cheap enough every second, and a single birch trunk hides a scene 22 blocks away | Out of sight is a traced line from each player's eyes to the scene's corners, whichever way the player faces |
| Walking into a lake or along a river leaves no dry spot on any of the five ways ahead | The ways ahead fan out wider or the scene waits for land; a sea scene (Act 2) is its own kind |
| The way a player goes is read from where they walked; a teleport says nothing about it | A jump of more than 8 blocks in a tick resets the heading |
| A scene kept only in memory leaves its blocks standing after a restart | Scenes and the blocks they replaced live in the world's save data, so a restart takes them away too |
| A scene the players turn away from before seeing it is taken away as soon as they are out of sight and past | A scene never seen counts as passed, and a variant of it can come again |

### M5 The caravan: a village that walks

- Out of sight means beyond the view distance, 192 blocks on a server at 12 chunks (`scripts/instance.py`) and the player's own render distance in singleplayer, and also behind the land or behind every player's back. Distant Horizons draws the far terrain up to 1,024 chunks and takes in a chunk whenever the server saves it, so a camp going up in the open would pop up in the far view; on open land the camp waits for that as M19 says.
- Where the camp goes:

  | Case | The camp |
  |---|---|
  | The team is spread apart | Ahead of the group with the most players, on the main quest's way |
  | Evening comes at sea | On the nearest land ahead; a night on the water only when a quest wants it, as in The Crossing (2.2) |
  | Someone is underground | On the surface near where they went down |
  | A quest keeps everyone away for days | No camp; the quest says "camp alone tonight" and sets the clock (D2) |
  | The ground | Where a real camp would stand: level, neither a hollow nor an exposed top, near water but not in it, with grass and wood near, and at least 32 blocks from any structure. These are wishes: within 128 blocks past the nearest spot out of sight the camp takes the best spot it finds and settles for it, and never walks on to find a better land |
  | Danger | Never within 16 blocks of lava, fire or another danger, nor on top of a structure |

- The camp is built like a village: the fire in the middle, and around it each household as its own small piece, a tent, a wagon or a lean-to, set at the height of the ground under it, with paths between them that follow the ground. There is no flattened square. A bigger household gets a bigger piece, a person who joins brings a new piece, and after the breaking the pieces of the dead are missing. When the camp is taken down, the blocks it replaced go back.
- Every household's piece must fit: a spot where one has no room is a wish that failed, and the caravan looks on for one where all fit, settling for the spot that misses the fewest only when nothing better lies within its search. Its people always stand on the ground, never in the air.
- The logs round the fire are benches: right-clicking one with an empty hand sits the player on it (Sit), and sitting there is resting by the fire. A log a person already sits on is taken.
- Arriving at the fire, one of the caravan greets the team, a different one from the evening before, each with four welcomes said in turn, so nobody says the same thing twice running; mornings go the same way.
- The camp cannot burn: while it stands, no fire takes hold in it and no lightning strikes it.
- When the camp is taken down it leaves traces, so a team passing the same place days later finds an old campsite: the fire pit with its campfire out and its ring of stones, the paths worn to it, trodden patches where the tents stood, and now and then something left behind, such as a split log or a broken crate. They look like the pack's other old camps, only fresh, and they stay for good (M21).
- Each person's record lives in the world's save data, alive or not, their trust and what they remember; their Easy NPC body is made from it in the evening and taken away in the morning.

**Why:** A camp of small pieces that sit on the land the way a village's houses do looks lived in where one big flattened layout looks dropped from the sky, and a camp whose size follows its people shows the caravan's story at a glance. Vanilla builds villages this way, with fixed pieces for houses and paths that bend to the ground. Real camps keep the same rules, level ground but not a low place, away from ravines, ledges and peaks, with grass, water and wood near ([National Park Service](https://www.nps.gov/subjects/camping/finding-and-setting-up-a-campsite.htm); Jesse Applegate, *A Day with the Cow Column in 1843*), so a hollow is the one place the caravan would never choose on its own. Wishes that give way keep the evening's walk as long as the day planned, in any land.

**The spike works** (`mod/src/dev/java/io/github/roman16/wayfarer/spike/CampSpike.java`, dev code that never ships, with its pieces built by `generator/caravan.py` and its households in `mod/src/dev/resources/wayfarer/spike/caravan.json`): ahead on the way and beyond the view distance, behind the land or behind the player's back, a fire and eight households' pieces go up, a large tent, the healers' tent, a tent of books, a small tent apart, a forge wagon, the bell's cart, a lean-to and a hide shelter, each at the height of its own ground with paths trodden to the fire, and ten people sitting at the fire or strolling round their tents. It replaces about 3,300 blocks in 50 to 100 ms on the server thread and puts them all back when it is taken down out of sight in the morning: a picture before and after differs in 0.4% of its pixels, the water's ripple and the biome's name. A fire set on a tent is put out within a second, and a lightning bolt over the camp does not strike. What it showed:

| Finding | For the engine |
|---|---|
| Distant Horizons 3.3.3 takes in a chunk whenever the server loads or saves it and sends the change to every player within 256 chunks at once (`enableRealTimeUpdates`, on by default); read in its code, not seen, since dev runs draw without it | A camp goes up and comes down only behind the land or behind every player's back, whatever the distance |
| The generator gives a column's height without making its chunk, but at 2 to 10 ms a column on this pack | The search reads heights off the server thread on an 8-block grid, kept for the evening; one search takes 0.3 to 6 s |
| Trees, bushes, lava pools and structures are not in the generator's heights | A spot is surveyed once its chunks are loaded, which takes 50 ms to 2 s; after four spots that fall short the camp settles for the best of them |
| Bushes stand everywhere in this pack's lands | A piece lifts out growth up to 4 blocks tall, puts it back in the morning, and never stands under a tree |
| A piece on a slope sits at the middle height of its ground | Higher ground under it is cut to a step and lower ground filled with dirt, up to 4 blocks, and both go back |
| Brush 30 blocks from one camp caught fire from a lava pool | No danger within 16 blocks of the camp, its fires put out each second and lightning near it stopped |
| Easy NPC's bodies sit (`easy_npc:pose/humanoid/sitting`), stroll round a home (`RANDOM_STROLL_AROUND_HOME`) and wear Minecraft's nine player skins, all set through its API | Easy NPC carries the camp's people; HollowEngine is weighed again at the memories (M8), whose scenes are keyframed |
| A camp kept only in memory stays standing after a restart, its people with it | As for road scenes (M4), camps and the blocks they replaced live in the world's save data |

**The traces work too** (`CampSpike`'s take-down): taken down out of sight, a camp of eight pieces put 3,224 blocks back and left 125 as its old campsite: the ring of stones round the pit with the campfire out, the paths to the fire, and the trodden earth where each piece stood, and at half the camps, chosen by the spot, the firewood log beside the pit. What it showed:

| Finding | For the engine |
|---|---|
| The camp's own ground, the trodden earth, the paths and the stone ring, makes the old campsite once everything above it goes back | A piece's ground layer is its trace, and only the campfire changes, put out |
| A piece on a slope stands partly on earth the camp filled in, which goes back | A trace stays only where the land under it was solid, so nothing floats |
| The traces are ordinary blocks | They need no save data and last through restarts, as M21 asks |

### M6 The day and the night: a scripted clock

- While the story runs, the quest engine drives the clock (D2); once it has ended, the day and the night run as in vanilla. Minecraft's clock runs from 0 at 6:00 to 24,000, with noon at 6,000.
- On the road the clock eases out: its speed is normal speed times the gap left to its target over the gap it started with, so it starts at normal speed, slows the whole way and holds at a low-sun evening at 11,500, about 17:30. From 17:00, once "make for the caravan" is tracked and most of the team is within 256 blocks of the camp, the night falls in a run that arrives: the clock runs at one steady speed and reaches 20:00, the moon up, in `dusk_run_seconds` (64 s from 17:00), and the evening at the fire begins on arrival. Monsters come out at 19:11 in clear weather, about three quarters of the way through. A quest that keeps the player out sets the clock itself, and a hard night does not hold at 20:00 but runs on to dawn.

  | Real minutes on the road | Clock |
  |---|---|
  | 0 | 6:00 |
  | 7 | 12:00 |
  | 13 | 14:30 |
  | 20 | 16:05 |
  | 33 | 17:10 |
  | 46 | 17:25 |

- A day is a night slept. The stillness rule counts nights slept within 256 blocks of one place, and the difficulty that grows per day goes, since the danger grows with the player's shine (S12).
- By day, danger is shade, storms, people and the land: dense forests and caves keep the gaze's creatures alive in daylight, a thunderstorm darkens the sky enough for them to walk, the Gazebound are people and the sun means nothing to them, predators hunt, and the cold, the heat and the water are the land's gates.
- A quest gets no exception from the stillness rule, which already allows two nights in one place; a quest that keeps the player longer happens on a living people's hallowed land (S6), where the gaze cannot see them.
- No night event comes from a calendar. A night the story already holds (a hold, a scripted hard night) takes no other with it, and every horde ends when the sun rises. While the story holds a night, no bed takes anyone, and the refusal says why: "Not tonight. The sky over the ridge is red."
- The gaze finds the caravan when a quest calls for it (The Bell, The Hollow, The Empty Grave), when the caravan has slept three nights in one place, or by a chance each night that grows with the player's shine, and more while Tamsin's bell is with the caravan: the chance is the shine over 128, doubled while the bell travels with it (`balance.toml` `[shine]`), rolled once as the camp's evening begins, and never on a night the story already holds (a hold, a red moon, a visit at the fire). When it falls, the ember warns as the camp goes up: "The ember is uneasy tonight." That night nobody sleeps, no tent opens, the fire's circle holds nothing back, the clock runs on at its own speed instead of waiting at 20:00, the horde comes at midnight, and the next day begins on the road at 6:00. The red moon comes only when the story wants it, once an act, on its climax (`story.md`, S25).

**Why:** A clock the story drives lets every night land where the people are and every hard night land as a set piece, and easing out keeps the sky moving so time never visibly stops. Counting days in nights slept cannot be cheated, and daylight that is safe only in the open keeps the forest, the storm and the Gazebound dangerous on a long day's road.

**The spike works** (`mod/src/dev/java/io/github/roman16/wayfarer/spike/ClockSpike.java`, dev code that never ships, with its targets in `balance.toml` `[clock]`): the road eases from 6:00 towards 17:30 and stands at 11:59 after 7 minutes, 14:33 after 13, 16:05 after 20, 17:08 after 33 and 17:24 after 46, as the table above says; with three players online the evening waits while only one is within 256 blocks of the caravan and begins the moment a second walks in, and monsters can come out 74.8 seconds later; a hard night runs on at normal speed to 6:00, where the next day begins on the road by itself. The pack's per-day difficulty is gone (`pack/config/improvedmobs/common.toml`), so until the shine (M9) monsters grow only with the shards. What it showed:

| Finding | For the engine |
|---|---|
| NeoForge sets a world's clock speed (`setDayTimePerTick`) and sends it with the clock's fraction to every NeoForge client, which then moves its own sky at that speed: the client's clock moved 0 to 2 ticks a tick and never backwards | The engine sets a speed about once a second and never sets the time itself |
| Monsters can come out on open ground at 13,188 (19:11) in clear weather, at 12,969 in rain and at any hour in a thunderstorm, from Minecraft's sky darkening and the overworld's spawn light of 0 to 7 | The run to 20:00 passes the monsters' hour on the way, so the team that walks in late meets them |
| An eased run never arrives: from 17:00 towards 22:00 it reached the monsters' hour in 75 s and 22:00 only after 18.6 minutes, while the evening's talk waited for 22:00 (the second playtest sat at the fire for minutes) | The run to the evening runs at one steady speed and arrives in `dusk_run_seconds`, and a game test holds it to that |
| Heading in at noon starts the evening at 8.3 times normal speed, so the sun races to dusk | The run starts only from 17:00, and an earlier evening passes at the fire (M20) |
| NeoForge's speed does nothing while the daylight cycle is stopped, as the showroom stops it for its noon | The engine turns the daylight cycle on whenever it takes the clock |
| A slept night jumps to the next 6:00 (`ServerLevel`, read in its code, not tried), the same hour a hard night runs to | Dawn, slept through or sat through, starts the road |

### M7 Joining later

- Who appears where, and with what, is D11's table. A tent or a mount counts as far beyond 256 blocks: then it comes back to the player who joins or returns, and one closer stays where it is. A player who logs in inside a tent counts as standing at its door, and a tent anyone is inside never comes back to a pack; on the compass a teammate in a tent shows at its door.
- A hard decision counts the players online when its vote starts: it needs two thirds of them. A player who joins later may still vote, and one who leaves keeps the vote they cast.

  ```text
  <Nell> Give it to me. I'll bury it with them.
    [Give it to her 1/3]  [Keep the shard 1/3]   (needs 2 of 3)
  Mo joined the road.   (Mo can vote; it still needs 2)
  ```

**Why:** A count fixed when the vote starts means nobody can change a decision by logging off, while a friend who arrives late still has their say.

**The spike works** (`mod/src/dev/java/io/github/roman16/wayfarer/spike/JoinSpike.java` and `TentRecall.java`, dev code that never ships), on the dev server with pretend players: a new player appears beside the teammate online longest, and beside that teammate's tent door while they are inside it; a player returning 1,900 blocks from the team appears beside them, and one returning within 256 blocks stays where they left; a new player while nobody is online appears where the last player left. The returning player's tent, 1,943 blocks away, is packed up into their inventory, and pitched again 2,000 blocks from where it stood it opens onto the same inside, its chest still holding 64 diamonds. What it showed:

| Finding | For the engine |
|---|---|
| Nomadic Tents keeps a tent's inside in a world of its own, named by an ID the tent item carries; packing up is what its mallet does, taking the tent's blocks away and handing back the item, and loading the far tent's chunk took 139 ms | Recalling a tent is its own pack-up, run for the player as they appear |
| Only the tent's door knows its owner, and a door is found only where chunks are loaded | The engine notes each player's tents as they are pitched; the spike looked around every player once a second |
| The joining rule runs in the login event and finds standable ground within 4 blocks of the teammate, whose chunks are loaded | Joining needs no chunk loading but the recalled tent's |
| The team's shards and an unclaimed reward come from the world's one state (M1); the mod as it ships still keys shards by FTB team | Shards move into the world's state with the engine (M15), so a joiner has them at once |
| Pretend players kept the chunks where they joined loaded, since a client moves its chunks by answering a teleport | Pretend players move their chunks every tick, so a rule tried with them meets the chunks a person would load |

### M8 The memories: a dream world of their own

- A memory happens in a small dream world of its own, as the tents' insides do, and the player is taken in and brought back to the same spot. While they dream they are gone from the real world, so nothing can hurt them.
- The dream rebuilds the place from the world's own seed, exactly as the world first made it: the same hills, rivers and hall, and nothing the players built or broke since, since that is the past. The memory then changes only what it needs, the hall whole and newer, the shard lit on its calling stone, the Frost Walkers on the stair, the grass halls young, and places again what the pack sets down by code, such as the Last Camp; whatever the memory needs no change to stays as the world made it.
- Nothing in a memory breaks but what its task asks for: while the task asks for wood, the fallen pine's logs give way; anything else stays and says "It's a memory. It stays as it was."
- Everyone within about 32 blocks of the stone dreams it together, every one of them as Wend. A player who was not there can dream it again later from the journal, at a fire: once a memory has been dreamt, its quest's page (under Quests, later under Done) carries a Dream it again button beside Track. It works while the player stands within 4 blocks of a lit campfire, the caravan's included; anywhere else it is greyed and the page says to sit by a fire. Pressing it closes the journal, the ember flares, and the player, with anyone within 32 blocks of them, dreams it from its beginning at the place as it was, standing round its stone, and wakes at the fire where they stood. Dreaming it again changes nothing in the quest and adds nothing to Conversations; alone, it is the one player.

  ```text
  Kai touches the calling stone. The ember flares, in all of you.
  Twenty winters ago. Three of you stand on the stair, and every one of you has Wend's hands.
  ```

- Falling in a memory wakes the player into it again, from its beginning or its last step, and costs nothing in the real world; Wend lived through it.

**Why:** Rebuilt from the seed, the dream is recognisably the place the players stand in, down to the last hill, without copying the present and without tracking which blocks players changed; a world apart leaves the real one untouched for friends who stay in it (D11). Dreaming together keeps nobody watching, and replaying from the journal means nobody misses Wend's life for being away; the button sits on the quest's own page, where its story already is, and any lit campfire counts, so a player alone on the road dreams it again at their own camp.

**The spike worked**, and the memories now run in `mod/src/main/java/io/github/roman16/wayfarer/story/Memories.java`: touching a hall's calling stone makes a dream world with the overworld's own generator and seed and takes everyone within 32 blocks into it, at the same spot; the hall comes back as the world made it, its pillar of planks and torch, which a player had put there, gone, and the memory makes it newer, 805 mossy, cracked and cobwebbed blocks made clean, and walks a Frost Walker to the stair, where she kneels. A pretend player killed in the dream wakes at its beginning with full health and nothing lost, and at the end everyone wakes where they stood and the dream world is let go and deleted. What it showed:

| Finding | For the engine |
|---|---|
| Infiniverse (MIT), which Nomadic Tents carries inside its jar, makes a world while the game runs and lets it go again; its 1.21.1 build is on CurseForge only | The Wayfarer mod carries Infiniverse itself, so the memories do not hang on the tents |
| Around the stone, 98.5% and then 97.4% of 117,649 blocks matched the real place, and every difference listed was a rock blob, granite for stone; the count changed between two dreams of the same place | Not settled: either this pack's generation does not repeat exactly, or the real chunks changed in between. The next memory compares two dreams with each other, and the snapshot taken when a place is first made takes over if they differ where the memory plays |
| Making the dream world took 1.8 to 2.3 s on the server thread and generating its 49 chunks with the whole pack 5.5 to 9.6 s | The dream is made and generated while the ember flares, before anyone is taken in |
| Each dream world left 1.4 to 13.6 MB on disk until deleted | A dream world is deleted as soon as it is let go, so every memory starts fresh |
| A fall is a death the mod cancels: full health, back to the beginning | Falling in a memory costs nothing, as settled |
| Easy NPC walks a person to a spot and calls back on arrival (`EasyNPCActionHandler.moveTo`), so a scene's beats follow one another; HollowEngine would add Kotlin story scripts, a cutscene camera and animated models | Easy NPC carries the memories' people, and the engine runs each scene's beats as data; camera moves for the big moments come from CMDCam or the mod's own code |
| The dream is lit darker than the real hall | Not looked into |
| A player who changes worlds cannot be hurt until their client confirms the move, so pretend players were untouchable in the dream | Pretend players confirm it themselves |

### M9 The clock inside: how brightly the player shines

- The shine is a base for the act, rising through S12's three stages with their quests, plus a step for every shard taken. Agna's remedy lowers it for a night. The team shares its shards, so everyone shines alike (D11).
- It does four things:

  | Effect | How |
  |---|---|
  | Harder | Improved Mobs' difficulty follows the shine, no longer days or shards directly: monsters get tougher and better equipped |
  | More often | More of the gaze's creatures are born near the player at night and in shade |
  | From farther | They notice the player from farther away, and follow longer |
  | Found | The chance each night that the gaze finds the caravan grows with it (M6) |

- It is felt, never shown as a number: the ember on the compass burns brighter, the player's eyes glow in the dark, and the journal says it as "I": "I shine like a lantern now. They find me before I find them."
- The eyes glow from the evening Corin first sees them (2.7) on, in the gaze's violet: the eyes each player's skin draws light up, whatever their size, shape and place, the white of an eye paler than the rest, on the hat layer where that is what shows. The game finds them in the skin itself, so every machine lights the same pixels. They are seen by friends, in third person and on the inventory's figure, brighter the more of the twelve shards the team has taken, fading as the light around the player rises and gone from light 8 up (`balance.toml` `[shine]` `glow_light`).
  - A face whose eyes cannot be read stays dark, as under a mask: a skin with no eyes drawn, a visor or sunglasses over them, a single eye under a patch, or a face the game is unsure of. Corin's line comes all the same.
  - Nobody corrects a guess: there is no setting, and the face test in `just test` holds a glow off the eyes to at most 1 in 16 of the community faces in `generator/faces.toml`.
  - Eyes of two sizes or two colours both glow, eyes up to a pixel off the mirror are still a pair, and a closed eye pairs with the open one. A full helmet covers them and an invisible player shows none.

**Why:** A step for every shard makes each one a cost the player can feel and leaving one a relief they can feel too, and four effects together make the shine being hunted rather than only fighting tougher monsters. A number would turn the clock into a stat to manage; felt, it stays the slow horror of S12. A glow on a forehead or a mouth reads as a bug while no glow reads as hidden eyes, and a setting to correct the guess would cost a screen, a message and storage for a detail most players see only in F5.

**The spike works** (`mod/src/dev/java/io/github/roman16/wayfarer/spike/ShineSpike.java`, dev code that never ships, with its steps in `balance.toml` `[shine]`): the shine is the act's base, 0, 16 or 32, plus 4 for every shard, and Agna's remedy takes 8 off for a night; at act 3 with all twelve shards it is 80. Improved Mobs' difficulty is set to the shine for every player (it reads 80.0 there); a monster born while the team shines 80 notices them from 40 blocks farther, so a zombie 64 blocks away comes for a player that one 48 blocks away leaves alone at shine 0; and at night more monsters are born in the dark around them, four more in half a minute at shine 80. What it showed:

| Finding | For the engine |
|---|---|
| Vanilla fixes how far a monster notices players when the monster is made; a range raised afterwards only makes it follow longer | The engine widens a new monster's targeting with its notice range, a small mixin where the spike reached into the goal |
| Vanilla alone puts 15 to 18 monsters within 64 blocks of a player in half a minute of a hard night, so extra births only show if the count allowed near the player grows with the shine | The cap near a player is `spawn_limit` plus one for every 4 of shine |
| About four in five tries fail vanilla's own darkness and placement checks | The shine's numbers count monsters born, not tries |
| Improved Mobs keeps a difficulty for each player and averages the players within 256 blocks; its command sets it outright | The shine sets it for everyone at once, and the mod's per-shard step (`per_eye`) goes when the engine takes over |
| A pretend player left standing through a fast-forwarded night dies, and a dead player is never hunted | Tests give pretend players Resistance and stand them in the open, since leaves hide them from monsters |

The fourth effect, the chance each night that the gaze finds the caravan, is the night events' to roll (M6) and was not built here.

### M10 The activities, Act 1

| Activity | System | Source |
|---|---|---|
| Build | The tent, the fire and everything carried, which the quest checks are placed | Vanilla, and M12's one-step tent |
| Talk | Conversations in chat with Easy NPC bodies | M3 |
| Reach | A goal at a hard place, with the land as its gate | The quest engine and the gear |
| Investigate | Clues at a place, found with the ember sense | The Wayfarer mod |
| Track | A trail laid ahead, followed with the ember sense | The Wayfarer mod |
| Hold | Something kept alive against waves until dawn or a set time | The Hordes, and the Wayfarer mod |

- **The ember sense** is a key, the one left of X on every keyboard (Z on an English one, Y on a German one). Held, the ember flares, and for a few seconds clues and tracks within about 24 blocks glow faintly. While a goal is known by feeling, the held ember burns brighter the closer the player faces it, so sweeping it round finds the way; where the story laid a trail (the caravan's wheel ruts, the Diggers' marks, a hall's hidden entrance) it glows. Every quest that searches asks for the ember: clues, entrances and tracks are found with it, not by luck. A clue is something in the world the player looks at and clicks; each adds a line to the journal, and the step is done once enough are found. A trail is a line of marks laid on the ground ahead, the way road scenes are (M4), such as hoofprints, blood or a cold fire, and taken away afterwards. The Trail-sight gift (S6) makes tracks glow all the time.

- Only what the step under way can look at glows amber; what the team has already looked at glows white, so a place with four clues shows which are done, and the things a later step will ask about stay dark until then. A step's clues may be looked at in any order.

  ```text
  You hold up the ember. Prints glow in the snow, a hunter's, heading for the ridge.
  Journal: Nell's tracks lead north-east. She's dragging something.
  ```

- **Holding** is keeping something alive: The Hordes sends waves of the gaze's creatures at a place until dawn or a set time, and the quest names what has to survive, such as Corin at the ford, the bell's cart or the herder's fold. If it falls, the step fails and is tried again (D6).

**Why:** The ember is the Eye's light, and light is sight (S19), so a sense that shows what is hidden is what The Witcher 3's witcher senses are, with a reason in the lore, and one key serves both finding and following. Holding something alive turns a night of waves into protecting someone, and a loss into a challenge failed.

**The spike works** (`mod/src/dev/java/io/github/roman16/wayfarer/spike/EmberSpike.java` and `HoldSpike.java`, dev code that never ships): a trail of 16 prints is laid 16 to 44 blocks ahead, bending with the land, and taken away once the player reaches its end; holding up the ember (F8 in the spike) makes the prints and clues within 24 blocks glow amber for 3 seconds for that player alone; clicking a clue adds its line to the journal and counts it towards the step; and in a hold The Hordes' waves come for the herder's fold, which stood 60 seconds with one of its four sheep while 30 monsters came for it, fell in a longer hold, which ended the step at once, and stood 120 seconds under a blood moon after a retry with its sheep made tough. What it showed:

| Finding | For the engine |
|---|---|
| Display entities glow in a colour of their own, and the glow can be sent to one player only, without touching what the others see | The ember sense lights clues and tracks for the player who holds it up |
| Prints lying on the ground vanish in tall grass and show only as small outlines from 16 blocks | Without the sense the tracks are hidden, which Trail-sight changes; how a print looks goes through the art skill |
| G is already taken three times in the pack (Curios, Simply Tooltips, Simple Voice Chat's groups), and a press reaches every mapping on its key | The sense gets a key chosen with every mod's bindings in view |
| A clue is an item lying in the world with an invisible box a right-click reaches | A clue is any small thing the story puts down |
| The Hordes spawns its waves around a player and lets other mods see every monster it spawns; a target added as a monster arrives sends it at the fold before any player | A hold is The Hordes' waves around the player holding it, with the thing to keep alive as the monsters' first target |
| A retry rebuilt the fold, but the failed try's monsters were still there and killed the new sheep in 10 seconds | A retry also takes away the waves of the failed try |

### M10 The activities, Acts 2 and 3

Act 2:

| Activity | System | Source |
|---|---|---|
| Sail | Sailing ships, the sea's own scenes (M4), and a night held on the water when a quest wants it | Small Ships, and the Wayfarer mod |
| Earn | A people's trust, a number per people in the world's state: none, enough to let the team in, or full, which counts towards Keep the oath; raised by a short chain of small quests, each with a turn and never a chore, the first T1 in Act 1 (`story.md`, S29) | The quest engine |
| Raid | Fighting into a Gazebound camp to free someone or take something back | Their outposts and mansions, Improved Mobs, the quest engine |
| Solve | Redstone locks built into a hall's template, and seals that open to a word typed into them | The generator, and a seal block of the Wayfarer mod |
| Survive | Land that kills with heat and cold: body temperature, with the peoples' gifts and the Warmth gift (S6) as warmth and shade | Tough As Nails, with its thirst switched off (`enable_thirst`) |
| Hunt | One unique creature: read about it, find what it hates, track it, fight it | The Wayfarer mod's own creatures, animated with GeckoLib |

Act 3:

| Activity | System | Source |
|---|---|---|
| Sneak | Creatures and Gazebound see and hear: light, weather, stance and movement decide how visible the player is, and noise draws them | Attract to Sound |
| Disguise | Gazebound robes make their people take the player for one of them, unless one looks too long or too close; True-sight (S6) shows others' disguises | The Wayfarer mod |
| Heist | Planned at the camp, quiet or loud, with roles, then carried through by sneaking, disguise or a raid | The Wayfarer mod, on Sneak and Disguise |
| Duel | One against one in a ring the quest marks, which the others watch and cannot step into | Better Combat and Combat Roll, and a GeckoLib champion |
| Chase | A rider who flees along a route; losing sight of him too long tries the step again | Easy NPC's flee objective, vanilla horses, the Wayfarer mod |
| Escort | People walked through danger and kept alive | Easy NPC's follow-player objective |
| Carry | Someone on the player's back: no sprinting, and running wakes them | The Wayfarer mod |
| Tame | Vanilla taming, and the horse that never sleeps with a rule of its own | Vanilla, and the Wayfarer mod |

- Only the land and the weather can make a player dangerously hot or cold: deserts and badlands by day, icy land by night. The season colours the land and brings its snow, but never makes a warm land hot or a cool one icy (M25). The first time a player is too hot or too cold, the task panel says what helps. Fires, lanterns, ovens, running, armour and what they hold warm or cool them, but never past warm or cool, and in the cold lands a fire still brings them back to comfortable.

  ```text
  Noon on the plains, you sit by your campfire: warm, never burning.
  Night on the ice, you sit by your campfire: the cold lets go of you.
  ```

- A heist has roles: the lookout marks guards from above, the lock-hand opens doors, the distraction draws the guards off, and the carrier runs out with the prize. Each player picks one, and roles nobody takes go to the caravan's people, such as Corin as the distraction and Ilse at the locks; alone, the player takes one and the caravan the rest.
- Easy NPC's people can walk (its wiki lists objectives to follow a player, flee, and stroll around a home), so the camp's people also stroll about their own tents.

**Why:** Thirst would be a second bar to refill all day beside the ember's hunger, which is upkeep and not play, so the land's danger is heat and cold, and only the land's: a fire is the cure for cold, so it never burns a player who sits by it, and the first playtest found every lantern and oven overheating players on temperate land. Hunts need creatures no mod has, and GeckoLib is how mods animate them. Attract to Sound already makes stealth real, and its licence (GPL-3.0) lets it be changed. Heist roles are GTA V's heists for a team of up to four, with the caravan making them work alone.

**The spike works** (`mod/src/dev/java/io/github/roman16/wayfarer/spike/WarmthSpike.java`, `ActsSpike.java`, dev code that never ships, with Tough As Nails, GeckoLib and Attract to Sound in the pack): thirst is off (`pack/config/toughasnails/thirst.toml`), and on icy land at night a player who was cold and heading for icy stays neutral with the Warmth gift; a pillager walks to the clang of an anvil 8 blocks away, with Attract to Sound's own raids off (`pack/config/soundattract/raid.toml`); an Easy NPC person follows a player 21 blocks; and a worm whose model, animation and texture the generator writes (`generator/creatures.py`) sways on sine curves; it is built as the Sand Singers' great worm (`mod/src/main/java/io/github/roman16/wayfarer/worm/`). What it showed:

| Finding | For the engine |
|---|---|
| Tough As Nails takes a modifier on every player's temperature, so a gift is a few lines | The gifts that warm, Warmth and the peoples' gear, are temperature modifiers |
| Attract to Sound hears through each player's client, which reports the sounds on its list as it hears them: a noise no player hears draws nothing, and an anvil's clang 24 blocks away went unheard where one 8 blocks away drew the pillager to the spot | The Gazebound hear what a player near them hears; a quest's own noises need a player in earshot |
| Attract to Sound registers with Simple Voice Chat; hearing a player's voice was not tried, since the dev client speaks into no microphone | Voice hearing goes on the playtest list |
| Easy NPC's flee objective never moved its person, likely because vanilla's avoid behaviour picks whom to flee the way it picks whom to fight, and a peaceful Easy NPC person fights nobody (read in the code) | The Chase's rider flees with a goal of the mod's own; following works as Easy NPC ships it |
| A creature written as generator data, its boxes, its sine curves and its texture, loads in GeckoLib and renders without Blockbench | Creatures are made the T3 way; their look goes through the art skill |

### M11 Gates and gifts

- A real wall is a ward around the whole place: inside its area no block can be broken or placed and explosions do nothing, and trying shows faint runes and "The ward holds". It lifts when its seal opens. The peoples warded their halls with the same craft the Keepers used on the frames.
- Pushing through a gate unready has a price:

  | Gate | Pushing through unready | The key |
  |---|---|---|
  | Land: cold | Sinking into powder snow, and Tough As Nails freezing the player: slowed, then hurt | The Frost Walkers' snowshoes, Warmth, a fire |
  | Land: heat | Tough As Nails overheating them: weakened, then hurt | The Sand Singers' shade |
  | Land: deep water | Drowning, and no sight far | The snorkel, the flippers, Deep-sight |
  | Land: darkness | No light, and the gaze's creatures live in the dark | Torches, Dark-sight |
  | Strength | A harder fight, and maybe a fall | Better gear (M18) |
  | Trust | Forcing a way in makes the people hostile and loses their trust, a choice that comes back (D1) | Their trust (Earn) |
  | Tool | A lesser pick breaks it 8 times slower, and drops to its last use as the block breaks: one pick, one block | A pick of the right material |
  | Travel: sea | A vanilla boat can cross, but on open water the ember shines like a beacon, and the sea's creatures and storms come (M22) | Ships, in Act 2 |
  | Travel: sky | The hang glider flies, but in the air the phantoms see the player | Flight in Act 3, and Sky-veil |
  | Knowledge | No way through: these are the real walls | The word, the map, the story |

- The gifts are passive, except the Flare, which is on a key and holds one charge: resting at a fire fills it again. In a team everyone has the gifts of the shards the team took (D11):

  | Gift | What it does | What it opens |
  |---|---|---|
  | Trail-sight | Tracks always glow, without the ember sense | Faster tracking |
  | True-sight | Disguises, illusions and invisible creatures show as faint outlines | Hidden doors in the halls, and Gazebound in disguise |
  | Dark-sight | A dim sight in the dark | Unlit caves as a way through |
  | Warmth | The ember warms the player like a fire | The cold lands |
  | Deep-sight | Clear sight far under water | Underwater passages, such as a hall's side door |
  | Stare | The weaker of the gaze's creatures flee the player's look | Holding without fighting |
  | Far-sight | Through the spyglass, in the hand or its own slot, the view zooms further, and a strike marks a place on the map from far off | Finding places sooner |
  | Sky-veil | Phantoms do not see the player | Safe flight |
  | Night-walk | At night, creatures do not notice the player unless they run | The road by night |
  | The Flare | A burst of the ember's light that crumbles the gaze's creatures within 16 blocks, once until the player rests at a fire | Breaking a horde |
  | Hush | While the player crouches, their heart's light sinks like a dead man's, and the gaze's creatures lose them | Hiding |

**Why:** A ward closes every way around a seal, which unbreakable blocks alone would not, and it has its reason in the lore already. A price for every soft gate lets a bold player go early without making the key pointless. Passive gifts are felt without being managed, and the one gift worth aiming and timing gets a key; a single charge that only a fire fills lets the Flare break a horde, not every wave, and makes when to spend it the choice. A lesser pick that is spent on one block makes every breach cost a mend.

**The spike works** (`mod/src/dev/java/io/github/roman16/wayfarer/spike/WardSpike.java`, `GateSpike.java` and `GiftSpike.java`, dev code that never ships, with its numbers in `balance.toml` `[flare]` and `[tool_gate]`): a ward over a hall of 11 by 7 by 11 blocks held against a player breaking and placing, a bucket emptied from inside and from outside, TNT and a creeper at its walls (25 and 8 of its blocks kept while the ground outside cratered), a piston pushing into it, water, lava and fire coming in through its windows, an Enderman beside a grass wall (one outside took a block) and zombies at its door (without the ward they broke it within a minute), and let a fallen player's grave stand; lifted, the same player broke its wall. A lesser pick takes 8 times as long on gate stone as it would if it were the right one, 75 s for diamond and 100 s for iron where netherite takes 8.3 s, drops nothing, and is at its last use once the block breaks; a bare hand takes vanilla's 250 s. Crouching with Hush, four husks hunting a player lost them within a second and stayed lost, crouching without it changed nothing, a husk the player struck still came for them, and standing up brought all four back. The Flare, on a key, crumbled the three husks within 16 blocks and left one 28 blocks off and a pillager standing, refused a second time, and was full again once the player had rested at a fire. Tough As Nails as the pack ships it, measured on a player standing still:

| | Cold, snowy plains at night | Heat, desert at noon |
|---|---|---|
| Cold or warm | 25 s | 16 s |
| Icy or hot | 50 s | 41 s |
| The price begins | 75 s: freezing, and half speed 3.5 s later | 66 s: overheating, and up to 15% faster, not weaker |
| First hurt | 80 s | 75 s |
| Dead | 124 s | 123 s |

What it showed:

| Finding | For the engine |
|---|---|
| NeoForge's events stop a player breaking, placing and emptying a bucket, explosions, pistons and mobs that grief before anything changes; nothing stops water, lava or fire coming in, nor a mod that breaks blocks itself, such as Improved Mobs | The spike's ward also remembers its blocks and puts back whatever changes, found through the game's neighbour updates; the engine stops fluids, fire and breaking before the change instead, with mixins, since putting a block back after it broke can leave a copy of it on the ground |
| Water held at a window was put back 266 times in a minute, lava 39, as each flowed in again | Fluids are stopped at the ward's edge, never undone inside it |
| Grass under a block turns to dirt, and the ward kept turning it back | A ward freezes its hall's natural changes too, so halls are built from blocks that do not change by themselves |
| Denying mob griefing stopped the Enderman and the zombies at the door; for a creeper it would also have kept the ground outside whole | Mobs near a ward cannot grief, except creepers, whose explosions are cut to the ward by their own event |
| Improved Mobs' door breaking never started in two minutes, with the ward or without it | Its breaking is not seen yet; the engine's hook before a block is destroyed covers it |
| Gravestone places its graves itself, and the ward let the grave stand | A ward lets the pack's own placements through: graves, and the story's own changes |
| The runes, the enchanting table's letters, did not show in the dev client while other particles did; Subtle Effects changes how those letters are drawn | The runes' look is chosen with the art skill and seen in the exported pack |
| The game asks how fast a tool digs and whether it is the right one on the server and the client alike, from tags both know | Gate stone is a block tag, the right material is vanilla's own tool-tier tag, and the crack on screen slows with the rule |
| A pick set to its last use breaks on its next use in vanilla | One pick, one block holds only with M12's last use, where the pick is a bare hand |
| A monster's target is set through one event, which Hush refuses, but a monster already hunting keeps its target until told; a monster the player strikes in the last 4 seconds may hunt them | Hush refuses new targets and clears the old ones each second, and striking from hiding gives the player away |
| Resting at a fire is the Warm effect the fire gives, which other code can see arrive | The Flare fills again when the ember warms |
| Tough As Nails' heat makes the player slightly faster instead of weaker | The Wayfarer mod adds the weakness while the player is overheated |
| The Flare and the spike journal both took F7 | Keys are chosen with every mod's bindings in view (M10) |

### M12 The camp and the gear

- Nomadic Tents is changed so that placing a tent builds the whole tent at once, without a frame or a mallet; packing it up is one click. Its own `enter_when_safe` and `pickup_when_safe` are switched on, so nobody enters or packs up a tent while monsters are near (D13). It stays the pack's tent because it is the one whose inside is a folded space: Let's Do Camping and Simply Tents pitch an ordinary tent in the world, and both are All Rights Reserved.
- A bed inside a tent sleeps only when a lit campfire burns within 16 blocks of the tent outside (D13, `[tent]` `fire_blocks`), room enough to find flat ground for a tent beside the fire. A tent stands only on ground: every block of its floor has ground at most one block below it, so no player fits under it, and elsewhere the tent says "The ground here is too uneven for a tent. Find flatter ground." A tent anyone is inside is never taken down by the story: a player who logs in inside a tent counts as standing at its door, and the caravan brings back only tents nobody is in.
- The first tent, the stranger's, is the kind the land around the Last Camp would build, the same for the whole team: a yurt in cold land and mountains, a tepee on plains, meadows and in forests, a Bedouin tent in desert and badlands, an indlu on the savanna, a shamiyana in jungle and swamp. The Gear page's camp row then grows that kind, tiny to large. Every tent is a world of its own, but the night is the team's: a player lies down in their tent and stays lying until two thirds of the team sleep, in their tents or out, and then it is morning for everyone at once (`[sleep]` `players_percentage`). A pitched tent's inside keeps running while its owner is out, so a furnace in it finishes its work; a packed tent stops.
- A lit fire's circle reaches 32 blocks (`[fire]` `circle_blocks`): no monster spawns inside it, and one that wanders in turns away and lets the team be, so the caravan's whole camp lies inside and a tent beside the fire is never kept shut by monsters at its door. A hold switches the circle off around its own fight.
- Resting is being by a lit fire: a player within 4 blocks of one rests at once, with nothing to wait for and no need to stand still. The camp, the afternoon at the caravan's fire, the Warm effect and the Flare all count that rest.
- The fire's stories stay. If a night has one, the voice's long talk, a person's scene or Maren's visit, it plays once most of the team sits at the caravan's fire, and sleep waits until it is over; a night without one goes straight to sleep. Resting half a minute at a fire still warms the ember, so the player eats like a settled person until the next dusk.
- Every tool, weapon and armour, Silent Gear's, Simply Swords', vanilla's and every other mod's, stops at its last use instead of breaking (D13), and at its last use a tool is a bare hand, in speed and in what drops: a diamond pick at its last use takes nothing from obsidian. Silent Gear already keeps broken gear (`breaks_permanently = false`), and the Wayfarer mod brings the rest to the same rule.
- At a fire, a repair kit mends a worn tool: Silent Gear's repair kit, filled with the tool's own material and set beside the tool in a crafting grid within 4 blocks of a lit campfire, brings it back to half its durability at most, never beyond, and away from a fire it mends nothing. The same kit, filled with the right material, mends vanilla's and Simply Swords' gear too, iron for iron and diamond for diamond. A smith restores it fully: the peoples' smiths ask for goods, and Hob does it for nothing once his side quest has found the old ore (C7).

  ```text
  At the fire you bind the cracked pick with a kit of flint. It will hold: half its strength, no more.
  Hob turns it over. "Leave it with me tonight."
  ```

**Why:** The tent the pack needs is the folded space it already has, and its safety rules are already written into it, so only the mallet goes. The evening is where the story is told, and the sleep rule keeps anyone from sleeping through it. Mending to half keeps the road going while a full repair stays something to reach a smith for, and Silent Gear's own kit is the mending players of the pack already know, bound to the fire as the rest of the road's work is.

**The spike works** (`mod/src/dev/java/io/github/roman16/wayfarer/spike/TentSpike.java` and `LastUseSpike.java`, dev code that never ships, with `[tent]` in `balance.toml` and `enter_when_safe` and `pickup_when_safe` on in `pack/config/nomadictents-common.toml`): a right-click with a small yurt puts up the whole tent at once, in 2 to 3 ms or 110 ms for the first, a right-click on its door takes the player inside, and a crouching right-click on its door with an empty hand packs it into the inventory; with a husk beside the door, entering and packing up are both refused ("there are monsters nearby"). Inside the tent a bed refuses sleep until a lit campfire burns within 4 blocks of the door outside, a fire 7 blocks off is not enough, and a bed outside any tent is furniture. A diamond pick worn to its last use breaks stone at a bare hand's 7.5 s with nothing dropped, and two more blocks leave it at its last use; a Simply Swords cutlass drops from 6 attack damage to a bare hand's 1 and stays; a shovel at its last use makes no path; a diamond chestplate hit for more than it had left stops at its last use and gives 0 armour. What it showed:

| Finding | For the engine |
|---|---|
| Nomadic Tents builds its whole tent from one template call, the call it uses for a tent's inside, and its door keeps the tent, the way it faces and its owner | Placing is that call on the right-click, so the frame and the mallet go, and the mallet's recipe with them |
| `Tent.from` reads a tent item's name, `nomadictents:small_yurt`, as type before size and namespace included, finds no size and makes every tent tiny | The engine reads the size from the item itself |
| Its monster check still refused right after the husk was killed, likely counting it through the second it takes to die | A tent may open a second after the last monster nearby falls |
| The door inside the tent remembers where the player stood outside, so a bed inside finds the fire beside the tent | Sleep looks for a lit campfire within `[tent]` `fire_blocks` of that spot |
| Vanilla sets the respawn point at a bed before it refuses the sleep, outside a tent too | The engine keeps the respawn at the fire (`Fire`), whatever bed is clicked |
| Nothing stops a vanilla item breaking: no event comes before its last point of durability goes. The spike marks an item unbreakable the tick it reaches its last use, puts back one that broke in a single blow, such as an axe hit for 2, and caps armour's wear | The engine stops the break where durability is taken, with a mixin, so no item ever shows the breaking animation |
| An item's attack damage, attack speed and armour come from one event per item, and mining speed and drops from two more | At its last use an item loses its attribute modifiers and digs and drops as a bare hand; its enchantments still count, which the engine takes away too |

### M13 What goes

- **The Last Sea.** The land ends 4,096 blocks past the ring's radius, and the sea runs on to Minecraft's own world edge, with no border of the pack's own. Its cold, through Tough As Nails, starts once the player is out on it and grows with every block: freezing about 1,024 blocks out, deadly within a minute after that, so nobody reaches the edge. A player who falls on the Last Sea leaves their stone on the nearest land.
- **Enchanting.** Enchanted books stay in loot, and only a smith can put one on, overnight: the peoples' smiths for goods, Hob once his side quest is done (C7, `story.md` S33). Mending and every enchantment that runs on XP go, since repair is the fire's and the smith's (M12).
- **Ore.**

  | Ore | In plain stone | In places: old mines and the deep stone around the Ancient Cities |
  |---|---|---|
  | Coal | Half of vanilla's | Rich |
  | Copper, iron | A quarter | Rich |
  | Gold, redstone, lapis | An eighth | Rich |
  | Diamond, emerald | None | Only there |
  | Netherite, crimson iron | None | Only around the Ancient Cities |

- **Backpacks.** Stack upgrades go up to tier 3.
- **Wild animals.** Twice vanilla's: up to 20 around each player instead of 10, since breeding and planting are cut (D7) and hunting feeds the team.
- **Floating islands.** Terralith's sky islands go; its sky biomes stay as plain land.
- **Witches as monsters.** Witches are a living people, the Marsh Mothers' heirs (`story.md`, S29), so none spawn at night, in The Hordes' waves, in raids or in huts, and lightning leaves a villager a villager. The mods' witch huts stand empty, the witches' old huts; the swamp's witches are the story's people.
- **Animals added twice.** Each pair from Naturalist and Wilder Nature, and the crab from Friends and Foes as well, is set side by side in the workshop (`tooling.md`, T1), and the one that looks and behaves better stays, with what it drops counting, since hunting is now a main source of food.
- **Structures.** About one place of a kind every so many blocks:

  | Owner | Kind | About one every |
  |---|---|---|
  | The Twelve Peoples | Their own places | A handful per people, set near their hall by the generator |
  | The Gazebound | Outposts and camps | 1,536 blocks |
  | | Forts and mansions | 6,144 blocks |
  | The settled world | Villages | 768 blocks |
  | | Towns | 3,072 blocks |
  | | Capitals | 12,288 blocks, a few in the world |
  | | Ruins of burnt towns | 512 blocks |
  | Wayfarers and travellers | Campsites | 512 blocks |
  | The first civilisation | Ancient Cities | As rare as vanilla's, deep |

  Every one of the pack's structure kinds gets its owner or is cut in `generator/owners.toml`, and the kinds this table spaces share one placement each.
- **Nether blocks.** A swap table in the generator maps each Nether block to its nearest Overworld look, such as nether bricks to deepslate bricks, blackstone to deepslate, crimson wood to mangrove, warped wood to dark oak, glowstone to sea lantern and soul sand to mud, and applies it to every structure but the Ancient Cities. Trek's two structures with lit portals, its pillager mansion and its portal sword, are cut outright.

**Why:** A sea that freezes needs no wall, and a stone on the shore lets a player who fell out there get their things back. Books that only a smith can use keep loot worth finding and give the smiths their part. Ore thin in the open and rich in places sends the player down the road for it (ground truth 3), and frequencies set by owner keep the world's places telling one story.

### M14 The endings

- **The dragon in Blind it** fights with the sight it was given: every shard set adds to its health and to the crystals that heal it, and with few shards it is half-blind, missing with its breath and its charges and losing the player when they move out of its line. It scales for a team of one to four.

  ```text
  Five shards in the frame. The dragon's breath sweeps a pillar you left a moment ago.
  <The gaze> I can't see you. I can almost see you.
  ```

- **When the world is gone**, in Close the Eye with Pip dead (S8), the world ends: the last scene, the line in the deep dark (S21), the credits. After that it cannot be played again; joining shows only the ending and its last line, then "Start a new road". The save stays on disk, untouched.
- **Playing on as Pip**, every player keeps their inventory and gear, their skin becomes grown Pip, and the journal's "I" becomes Pip's from one new entry on.
- **Give it its sight** makes the caravan's last camp the home: within `[after]` `home_blocks` of it nothing spawns at night and what D7 cut comes back, and everywhere else every night is a hunted one. **Blind it** and **Close the Eye** end the road's rules everywhere: no hordes, no stillness, no ember's hunger, and planting, breeding, beds and chests come back. Each door's world opens its last quest: Build a Home after Blind it and, as Pip, after Close the Eye; The Promised Home after Give it its sight.

  ```text
  Journal › The story so far
    Kai is gone. He walked into the frame and the dark closed behind him.
    I carry his ember now. I'm going to build us a home.
  ```

**Why:** A dragon that fights with the sight it was given makes every shard left behind count in the last fight. Gone has to mean gone, or Pip's death would cost nothing, and watching the world as a spectator is the one thing the closed Eye no longer does. The same journal in a new voice carries the merciful door into what comes after.

**Built and shown in the game** (the dragon in `Dragon.java`, the worlds after the doors in `story/After.java` and `client/Ending.java`, with their numbers in `balance.toml` `[dragon]` and `[after]`): set to 12 shards with two players online, the dragon rose with 768 health and every crystal stood; set to 3, it had 480, three crystals stood, its fireballs flew about 6 blocks wide of the player where at 12 most flew 0.1 wide, its charges went 5 to 12 blocks wide, and it lost the player when a pillar stood between them. A world marked gone shows every player a black screen with the ending's lines and "Start a new road", which Escape does not close; the button leaves for the title screen, and opening the world again shows the ending again and nothing else, the save untouched on disk. Playing on as Pip changed the player's skin, seen from behind, added "I carry his ember now" to the journal after the earlier entries, and from then on wrote a quest's journal entry in Pip's words. What it showed:

| Finding | For the engine |
|---|---|
| Ender Dragon Fight Remastered sets the dragon's health from its own score, `DragonHealth`, the first tick the dragon lives | The engine writes the health the shards give into that score before the fight, and the mod raises the dragon with it |
| The dragon's breath is a projectile that can be turned as it appears, and its charge flies to a point that can be moved | Blindness turns the breath by an angle that misses by up to `blind_aim_blocks` at the player's distance and moves the charge's point as far |
| Even aimed true, the dragon's fireballs mostly landed 10 to 30 blocks from the player, stopped by the terrain and the pillars first | Blindness is felt in the near misses, so `blind_aim_blocks` is judged in play |
| The client replaces any screen opened while it joins a world with the world itself | The ending opens once the player stands in the world, and comes back whenever another screen replaces it |
| The ending is only a screen on the client, and nothing on the server stops a player behind it | The server keeps a gone world's players from acting, so a changed client cannot play on |
| A player's skin comes from their profile, which is signed, so the server cannot send a new one; the client's player list holds the skin and takes one from code | Grown Pip's skin is a texture the pack ships, set in each client's player list; its look goes through the art skill |
| Journal entries are kept as written, so a voice set later changes only the entries that follow | Every journal text the story writes after Close the Eye has a Pip version beside the player's own |

### M15 The verdicts

Nothing is kept for having been built (ground truth 6). Every part of the Wayfarer mod as it ships, and every vanilla system D7 does not decide:

| Part | What it does today | Verdict |
|---|---|---|
| The ember (`Ember`) | Wakes after the first day, one per team | Kept, changed: wakes at the end of P2, and a shared fire's light is one for the world (D11) |
| Halls and shards (`Halls`, `Shards`, the calling stone, the halls) | Twelve halls in two distance tiers, a shard on each calling stone | Kept, changed: the calling stone is the calling stone, the halls have their fates (S6), the two tiers become the three acts' rings, and the Diggers' and the Marsh Mothers' halls are put down ahead by their quests (S24, S29); halls stay free of spawns, now as hallowed ground |
| The compass (`Compass`) | Points at shards, friends and the grave | Kept, changed: the tracked quest's goal as the player knows it (D6), friends and stones (M2) |
| Leads (`Leads`) | Places someone told the player of | Folded into the quest engine: a lead is a goal someone told of (S7) |
| The fire (`Fire`, `Warm`) | The circle keeps monsters off, resting warms the ember, the voice speaks at rest | Kept, changed: the circle and the warming stay, and the voice's long talks and the night's stories play at the caravan's fire (S4, M12) |
| Hunger and rowing | The ember eats four times what a body would (`[hunger]` `exhaustion_factor`); steering a boat costs a shank of food every 256 blocks on top; Spice of Life wants half vanilla's variety, its extra hearts at 2, 12 and 16 foods (`pack/config/solonion.json`) | Kept |
| Nights (`Nights`) | A Sleepless Night every fourth night, a red moon every fifth | Changed: scripted, as the story's and the caravan's events (M6) |
| Daylight (`Daylight`) | The gaze's creatures crumble in the sun | Kept |
| Stillness (`Stillness`) | Staying in one place dims the ember, then brings the horde | Kept, changed: counts nights slept (M6) |
| Tents (`Tents`) | No spawns inside; thrown out on a Sleepless Night | Kept, changed: one-step tents, sleep by a fire (M12) |
| The sky (`Gaze`) | In the air phantoms hunt the player, held off longer by the Ember Veil | Kept: the sky stays dangerous, Sky-veil is a shard's gift, and the Ember Veil stays as gear (M18) |
| Unlocks (`Rules`) | Ships at four shards, aircraft and gliding at six | Changed: they belong to quests (D15) |
| Sight (`Sight`) | Monsters seen through stone at seven shards | Changed: the clock's second stage, at the Jungle Folk's hall (S12) |
| Difficulty (`Difficulty`) | Improved Mobs harder per shard and per day | Changed: follows the shine (M9) |
| Graves (`Graves`) | The grave, marked on the compass | Changed: stones (M2) |
| Hordes in halls (`Hordes`) | A horde passes over someone sheltering in a hall | Kept |
| The frame (`Gate`) | Takes all twelve shards, refuses while Gazebound stand in its hall | Kept, changed: takes any number (D10) and opens the four doors (S8) |
| Maren (`Maren`) | Visits the fire and leaves food | Kept, changed: her part in S10 |
| Story, books, notes and journal (`Story`, `Library`, `Notes`, `Journal`) | The voice's lines, the books, the notes, the FTB Quests book | Replaced by the quest engine and the journal (M1, M2); the books stay as content |
| Teams (`Teams`) | FTB Teams | Replaced: everyone in the world is one team (D11) |
| Camps (`Camps`) | The Last Camp and Wend's first and last camps | Kept: the prologue's camp and the memories' places; the camp generator also builds the caravan's pieces (M5) |
| Structure spread and the ring (`Spread`, `ExactRingPlacement`, the world-generation mixins) | Spreads structures 6 times apart and places the ring of frames | Changed: frequencies by owner (M13); the ring stays |
| Loot (`Loot`) | Fills Lootr chests near players | Kept |
| Hidden pop-ups (the toast mixin) | Hides vanilla's advancement pop-ups | Kept (D7) |
| The showroom and the driver (dev only) | The showroom and `scripts/client.py` | Kept, as tooling (T1, T2), the showroom opened by `just showroom` |
| Friendly fire | Vanilla | Off: the team cannot hurt each other |
| Village raids | Bad Omen after killing a raid captain | Kept as the Gazebound's revenge: kill a captain, and they raid the next village the player visits |
| The wandering trader | Vanilla | One of the road's people (D12), a scene that comes back (M4) |
| Phantoms from sleeplessness | Vanilla | Off: the player sleeps every night anyway, and phantoms come only to the sky |
| Weather, fishing, spawners, the End | Vanilla | Kept: storms are a daylight danger (M6), fishing is food, spawners are dangers in places, and the End is the finale; rain and storms last at most about ten minutes (`[weather]` `rain_most_seconds`), since the road's slow clock made vanilla's ten to twenty feel like a whole day, and after a rain the sky stays clear for four story days (`days_between_rain`), since timers that count real minutes let it rain on almost every slow day |

**Why:** Each part either carries the design as it now stands, is turned to it, or goes, so nothing survives for having been built, and every vanilla system left has a part in the road.

### M16 Balance: start, measure, tune

- Each system starts its numbers in `balance.toml`, as powers of two (AGENTS.md). What a tool can measure gets one, the way `just survey` measures the halls' legs: how long a day on the road runs, how often a road scene comes, how bright the shine grows. What only a player can judge goes into the `playtest` skill's checklist for the Act 1 slice.
- No side of a hard decision is a trap: each side's reward shows within the same act, and its cost no later than the next. The starting point to play and tune:

  | Decision | Taking the shard | Leaving it |
  |---|---|---|
  | The witches' shard (1.4), only for a team that did T1 first | True-sight and a step of shine; the black pool turns plain and their healing with it, and their trust falls back to the minimum | No gift and no shine; their full trust, which counts towards Keep the oath, and two healing brews for each player |
  | Nell's shard (1.8) | Dark-sight and a step of shine; Nell stays but hates the player, which counts at the breaking | No gift and no shine; Nell leaves in peace and may fight beside the player at the gate (F1) |
  | Tamsin's bell (1.10) | Warmth and a step of shine, and the gaze finds the caravan less often; Tamsin never forgives it, and some families leave | The bell's toll keeps the lesser creatures off the camp, and the townsfolk trust the player; the gaze finds the caravan more often |
  | The Tide Folk's two shards (2.3) | Up to two gifts, Deep-sight and Stare, and two steps of shine; their boats are seen and they starve, unless Night Fishing (T2) saves them | Their trust, which counts towards Keep the oath, and their bay stays dark even in Give it its sight |
  | The worm (2.6) | Far-sight and a step of shine; the Sand Singers lose their protector, and the raiders come (T3) | No gift; the Sand Singers trust the player, and the worm stays in the desert |
  | The Grass Kingdom's ward (3.4) | Sky-veil, which makes flight safe, and a step of shine; the walls fall unless the player holds them, and the heir has his land back | The kingdom stands and trusts the player, which counts towards Keep the oath; the heir looks for another way in |
  | The shard or the children (3.5) | Night-walk and a step of shine; the children stay captive | No gift; the children join the caravan and help at the gate (F1) |

**Why:** Numbers that start from a reason and are then measured are how the pack already tunes its halls, and leave the player only what a tool cannot judge. A decision is hard only if both ways cost something the player feels soon enough to remember why.

### M18 Gear and its road

- Silent Gear makes all made gear: tools, weapons and armour from parts, each material with its own traits, graded, salvaged and alloyed. The peoples' own metals are added to it as new materials, which its data files allow without code. Simply Swords' unique weapons stay as rare finds and masterworks, and Artifacts' trinkets as the peoples' gifts and finds.
- Gadgets are what the player carries and travels with, tents and backpacks among them, and they climb tiers too: tents from tiny to mega with their inner layers (Nomadic Tents' sizes and shovels), backpacks from copper up with their upgrades, hooks, gliders, ships and aircraft.
- Each tier's material comes from a place on the road (ground truth 3, M13), and the top of every kind can be reached before the story ends, though not from the start (every name a placeholder):

  | Act | At the player's own bench | Forged by a smith | Gadgets |
  |---|---|---|---|
  | 1 | Flint, copper, iron | Frost iron, from the Frost Walkers' old mine | Copper and iron hooks and backpacks, small and medium tents, the hang glider |
  | 2 | Bort and diamond, from the deep places | Tide steel, Sand Singer glass-bronze | The diamond hook and backpack, large tents, ships |
  | 3 | | Crimson steel from the deep stone around the Ancient Cities, then the top tier from a far metal, forged by the Keepers' smiths or at the far ring | The top hook, backpack and tent, airships |

- What vanilla makes from wood, stone and leather stays as vanilla makes it, so the first hours play as Minecraft does; everything of metal and better is Silent Gear's. Hob hands each player his patterns at the caravan's first fire, and the task panel walks them through the first head and the first tool.

  ```text
  <Hob> And take these. My patterns. Copper and iron want shaping, not just hammering.
  ○ Make a tool head: one of Hob's patterns and its copper or iron in a crafting grid
  ```

- A smith's tier is forged overnight: the work is handed to the smith in the evening and collected from dawn on, each player's by that player alone. The player's own bench stays instant. Hob's forge works as `story.md` S33 has it: right-clicking him with a piece in hand hands it over, and the other hand says what for, nothing to mend it, frost iron to make it again in that, an enchanted book to set it in; before C7 he mends only to half, with a hammer cracked the night Tollbridge burned, and from C7 on he restores fully, forges frost iron and sets books.

  ```text
  You hold out your old blade, the frost iron in your other hand. <Hob> Leave it with me tonight.
  Morning. <Hob> Here. Mind the edge. A sword the colour of river ice.
  ```

- The journal's Gear page shows the road. It has a row for every kind the player can grow into, each appearing when it first matters: weapons; tools (the spyglass among them); armour; against the cold (wool clothes, the fur cloak, snowshoes, Warmth); against the heat (leaf clothes, the drinks that cool, the Sand Singers' shade); under water (the snorkel, the flippers, Deep-sight); in the dark (torches, lanterns, glowsticks, the night-vision goggles, Dark-sight); carrying (backpacks and their upgrades, lunchboxes); the camp (tents from tiny to mega, the crossbeams and shovels that make the inside bigger, the cooking pot); getting about (grappling hooks, the hang glider, ziplines, a horse with its saddle and armour, the stone of one's own); ships, with Act 2; aircraft, with Act 3; food and health (more hearts from food eaten in variety, the meals); and charms (the trinkets and the shards' gifts).
- Each row shows what the player has, what it is good for, which land or gate it opens, and the next tier: what it needs, where that is found and who makes it, "?" until the player learns it, filled in for the whole team by what anyone does (D6, D11). "How it's made" opens JEI on its recipe. Track puts the next step in the task panel as a quest's tasks are, as Horizon Forbidden West turns a missing part into a job; nothing on the page has to be done, and nothing pays for ticking it.
- The prologue and 1.1 have eight rows, each appearing the first time it matters to anyone in the team: weapons when Hob's Blade is taken, tools with his patterns, armour when anyone wears some, carrying and the camp with His Pack, getting about with the first of its things held or ridden, against the cold and against the heat the first time anyone feels either. The other six come with the steps where they first matter. The rows and their tiers live in `story/gear.toml`.
- A tier nobody has named shows only "? Hob knows more." Once anyone holds a row's best named tier, its person says the next tier's line once, at the caravan's evening fire on a later day, and the page fills in its name and "From ?, made by ?": Hob's "the Frost Walkers had a metal that laughed at the cold", Agna's snowshoes and the Sand Singers' clothes. Carrying after iron and the camp after the large yurt stay "?" until Act 2 settles what comes next.
- Track is each player's own. The tracked tier's materials sit in the task panel with their count ("Copper ingots: 1/3, smelted from ore in the hills' stone") and tick when carried, then its making; once it is made a banner says so ("Copper tools made") and tracking ends. A tier still on "?" cannot be tracked.
- Each kind names the one in the caravan who knows it, and at the fire they now and then say a line about its next tier: Hob for metal, weapons, tools, armour and hooks; Agna for clothes against the cold and the heat, food and remedies; Tamsin for tents and the camp; Pip for horses. A few smiths' commissions are side quests with turns of their own, such as Hob's masterwork or a people's blade.

  ```text
  Gear › Against the cold
    Wool clothes · from sheep, at your bench               [How it's made]
    Next: the Frost Walkers' snowshoes · from ? · made by ?        [Track]
  ○ Iron: in caves under the hills   ○ Make an iron pick head with Hob's pattern
  <Hob> Iron's fine. But the Frost Walkers had a metal that laughed at the cold.
  ```

**Why:** Silent Gear is already the deepest gear system for 1.21.1, and Tinkers' Construct has not reached it; its materials as data let the peoples' metals join it. Tiers tied to places send the player down the road for better gear, an overnight forge makes the smiths matter without a waiting bar, and a page instead of "have item X" quests keeps "No quest is a chore". The top within the story lets a player who works for it face the end at their best. A page that says where the next tier is and who knows it, with JEI for the recipe and a Track the player chooses, guides the way Monster Hunter's upgrade tree and Horizon's jobs do without becoming the checklist the old quest book was.

**The spike worked**, and the forge now runs in `mod/src/main/java/io/github/roman16/wayfarer/story/Forge.java`, with frost iron as data in `mod/src/main/resources/data/wayfarer/silentgear_materials/frost_iron.json` and the Gear page in `story/gear.toml`: frost iron is a Silent Gear material written only as data, pale blue, harder-wearing than iron and Sturdy, made from raw frost iron dug in the Frost Walkers' old mine, and blacklisted for every gear type at the bench, so only the smith's forge builds it, through Silent Gear's own API. What the spike showed:

| Finding | For the engine |
|---|---|
| Silent Gear reads materials from any data pack's `silentgear_materials` folder and builds parts and gear from them through its own API | The peoples' metals are data files, each with an ingredient item the Wayfarer mod adds; the forge builds the finished gear through Silent Gear's API |
| A material file missing one key, `part_substitutes`, stopped the world's data packs from loading, and the world did not open | `just lint` checks every material file against Silent Gear's format before any launch |
| A dawn is a day boundary of the overworld's clock, whether the night passes, is slept or is skipped | An order finishes when the overworld's day count moves past the day it was left on |
| What the player learns comes from things they do, carrying a metal or leaving it with a smith | Each tier on the Gear page names the events that fill in its fields, as the journal's other entries do |
| An item lying on the anvil can be picked up by anyone | The smith keeps each player's work and hands it only to the player who left it (`story.md` S33) |

### M19 A camp in plain sight

- When evening begins the compass points at the camp's spot at once, and the camp goes up the first moment no player can see the spot: the land or a wood stands between, or every player has turned away from it.
- If the team walks straight at it across open land and never looks away, it goes up anyway just before the spot comes within the view distance, and a few tents may appear once in the far view.
- While the camp stands, smoke rises over its fire for every player in the overworld: the campfire's own signal smoke within the draw distance, and beyond it a plume drawn in the far view: blocky, shaded puffs of pale grey, about 96 blocks tall, widening and bending downwind as they rise (`balance.toml` `[smoke]`). The plume shows wherever the far view is drawn, out to hundreds of blocks, and only with the far view on, since particles fade into the fog at the draw distance. Before the caravan is met there is none.

  ```text
  Evening  [Main] Make for the caravan.   Compass ◆ 420 blocks
           You turn to watch a horse on the ridge. Behind you, far off, the caravan makes camp.
           You turn back: smoke, and a few tents on the plain.
  ```

**Why:** Distant Horizons shows the camp as soon as the server saves its chunks, on a server as in singleplayer, so a camp that waits for a turned back goes up unseen almost every evening, and one that never waits past the edge never leaves the team walking towards nothing. A few tents appearing far off once is what the lore already promises: in the evening their smoke shows me where. The smoke is the lore's own sign ("in the evening their smoke shows me where"): the land itself leads the team home, and the compass only confirms it; vanilla's campfire smoke rises a few blocks and shows only within 32 of it, so the column is the pack's own.

### M20 Heading in early

- From 17:00, heading in starts the run home: the clock runs to 20:00 in about a minute, and monsters come out on the way (M6).
- Before 17:00, heading in leaves the clock alone, and the team walks in by daylight. Once most of the team has rested at the caravan's fire, the afternoon passes as a time-lapse, the shadows stretching and the sky going gold, then red, in about half a minute from noon, and the evening begins at the fire, inside its circle.

  ```text
  Noon   [Main] Make for the caravan.   Compass ◆ 600 blocks
         The sun stays where it is. You sit down at the fire with Bram.
         The shadows stretch, the sky goes gold, then red. Evening.
  ```

**Why:** From noon the run would make the sun sweep the sky at eight times its speed, while from 17:00 it needs no more than one and a half; an afternoon let go at the fire is what The Witcher 3's meditation until dusk and Red Dead Redemption 2's rest until evening give the player, and the fire's circle keeps it safe. The run keeps its tension where the day really ends.

**The spike works** (`ClockSpike`, with `run_from` and `afternoon_seconds` in `balance.toml` `[clock]`): at noon, with all three players within 256 blocks of the caravan, the clock stays on the road until two of them have rested at its fire, then eases from 12:35 to 22:00 at 19 times normal speed, and monsters can come out 29.4 seconds later, with the team inside the fire's circle; every player's sky follows at 8 to 19 ticks a tick and never steps back. What it showed:

| Finding | For the engine |
|---|---|
| The afternoon is the evening's own ease, only timed to `afternoon_seconds` instead of `dusk_run_seconds` | One curve drives every stretch of the clock, set by how long it must take |
| Resting is the pack's own: within 4 blocks of a lit fire (`[fire]` `rest_blocks`), counted at once, so turning in early costs only the walk to the fire before the sky turns | Sitting on the fire's logs (`mods.md`) is a way to be by it, not a wait |
| Fast-forwarding the server (`/tick sprint`) leaves a client's clock to catch up by hundreds of ticks at once | A sprint measures the server's clock; the sky is judged in real time |

### M21 Old campsites: the caravan's camps stay

- When the caravan's camp is taken down, its traces stay for good, as the camp left them: the fire pit with its campfire out and its ring of stones, the paths worn to it, trodden patches where the tents stood, and now and then something left behind, such as a split log or a broken crate. A team that comes back the same way, days or an act later, finds the camp where it stood.

  ```text
  A ring of stones round a cold pit, paths worn through the grass, a split log by the path. The caravan camped here.
  ```

- This is the caravan's camp only. A camp the player makes on their own out on the road is theirs, and stays or goes as they leave it.
- The caravan takes back only what it set down. A block anyone placed or broke in its camp since it stood, a tent pitched by its fire above all, stays as the team left it when the camp packs up, and no trace is laid over it.
- In the morning the camp stands until the team has walked out of sight of it. A player inside a tent counts as standing at the tent's door, so nobody wakes to find the caravan gone; the first player to step out at the camp gets the morning's line from someone there ("Eat something before you go. Then go on; we'll follow."), and the camp packs once they are on their way.

**Why:** The road the team walked stays in the world, as the Van der Linde gang's old camps do in Red Dead Redemption 2, and traces that never change need nothing to keep them.

### M22 The sea's price: what lives below

- Open water is water more than 64 blocks from any land. Along a coast the sea is quiet; past the last headland, the ember shines like a beacon.
- On open water at night, or at any hour while it rains, a beast of the gaze's own comes up to look: one at a time, rarely, and more often the brighter the team shines (M9). It circles under the boat and rams it: a vanilla boat breaks, a ship holds. At dawn it dives, as the gaze's other creatures crumble, unless the rain still holds it.

  ```text
  Something knocks against the hull. A long shape circles under the boat,
  and an eye the size of a shield looks up at your ember.
  ```

- The weather stays the world's own, and the pack calls no storm. Rain makes the sea worse: the beast comes at any hour, and ships roll harder.

**Why:** One beast that comes only on the open sea, at night or in rain, makes the crossing the gate without making every swim a fight, as Valheim's serpent does, and a boat that breaks where a ship holds keeps ships the key (M11). Minecraft has one weather for the whole world, so a storm called for the sailors would rain on everyone ashore.

**Built** (`mod/src/main/java/io/github/roman16/wayfarer/Sea.java`, with its numbers in `balance.toml` `[sea]`; the beast is the Wayfarer mod's `deep`, a guardian's shape four times its size, gone the black of deep water with one pale violet eye, and it comes only once the story's ships are open): a player in a boat 64 blocks from the nearest land is on open water and one by the coast is not; there the beast, comes one check in 32 (every 8 s) at night or in rain, one check in 12 at the shine of act 3 with every shard, and only while none is near. It rose on its own under a player's boat in daytime rain, broke a vanilla boat with its first ram, rammed a Small Ships cog every 2 s from 8 to 344 damage until it sank after about 75 s with nobody fighting back, and dived when dawn came. What it showed:

| Finding | For the engine |
|---|---|
| Open water is read from the heights of the loaded chunks around the player, every 8 blocks out to 64, and land in any of them ends it | The rule needs no world data of its own; a coast in an unloaded chunk counts as sea, which the view distance makes rare |
| Small Ships' ships are boats to the game, with the hull's health in their own config (a cog has 300) | A ship's damage from the beast is tuned against its hull: at 8 every 2 s a cog lasts about 75 s against a beast nobody fights, which is judged in play |
| Riding a Small Ships ship is refused until 1.11 The Name is done (`Rules`) | The tests stood the player on the deck; ships open as Act 1 ends (D15) |
| `/weather rain` takes some seconds to fade in, and only then does the game count it as raining | The beast follows the rain the world shows, not the forecast |
| Improved Mobs 1.16.0.b crashed the server when a drowned swam at night: its pathfinding reads a search's context outside any search (`SwimNodeEvaluator`). It is the newest build | The Wayfarer mod gives that call a context of its own (`ImprovedMobsPathContextMixin`); a hundred drowned round a boat at night then ran without a crash. Reported upstream as [ImprovedMobs#364](https://github.com/Flemmli97/ImprovedMobs/issues/364); the mixin goes once a fixed build is in the pack |
| The beast's look is a stand-in | It is made with the art skill, as the worm was (T3) |

### M23 A stone of one's own

- Every player carries one waystone of their own, a settled Wayfarer's from W5 The Cache (S24), which waits in the journal for each player. They set it down where they leave the road, a quest left halfway or a camp to come back to, and touch it. From any waystone they have touched since, they can fold back to it, and arriving takes it up into their pack again, so nothing of theirs stays standing. Crouching and taking it up by hand works too, to set it down somewhere else.

  ```text
  You set your stone down at the edge of the marsh and touch it.
  Three days later, from a village waystone far to the east, you fold back. The stone is in your pack again.
  ```

- Every waystone a player has touched, their own stone among them, shows on the world map with its name, and the map can show or hide them.
- A player's own stone is known by its owner, as Waystones records it, so it comes back after a death, from inside a tent and wherever it was set: breaking it or sneaking with an empty hand on it puts it in the pack again.
- The world's own waystones stay where they are, in villages and along old roads, and only fold roads already walked (D7).
- Every fold costs hunger: the ember burns the walk it saves, about one food point for every 256 blocks, so a long fold on an empty stomach leaves the player starving at the stone.

  ```text
  You fold back 2,000 blocks. You arrive at your stone hungry, as if you had walked it.
  ```

**Why:** A detour for better gear should not cost the same long walk twice, and one carried stone gives exactly that, as Breath of the Wild's Travel Medallion does, without letting the world fill up with a teleport network or a stone that becomes a home. Carrying it is the oath's "everything carried", as the tent is. A fold paid in food keeps the road's price, food gathered on the road, where Waystones' own experience cost would need the experience the pack drops.

**The spike works** (`mod/src/dev/java/io/github/roman16/wayfarer/spike/WaystoneSpike.java`, dev code that never ships, with `[waystones]` in `balance.toml` and Waystones' experience cost taken out in `pack/config/waystones-common.toml`): a player's own stone, a Waystones waystone marked as theirs, is set down, named after them and touched; from a waystone set 1,964 blocks away to stand in for a village's, the player folds back to it, arrives 8 food points hungrier (20 to 12), and finds it in their pack again; crouching takes it up by hand; a waystone that is not their own cannot be set down. What it showed:

| Finding | For the engine |
|---|---|
| Waystones fires an event before and after every fold, and its menu folds to any waystone the player touched | The own stone is an ordinary Waystones waystone the mod tracks, and the menu needs nothing of the pack's own |
| Taking the stone up block by block let the game break its lower half and drop a second waystone | The stone's two halves come out together, quietly, before it goes back into the pack |
| A world's waystone broken with a pick drops a waystone the player could carry off | The world's waystones cannot be broken (M23), and only a stone marked as the player's own can be set down |
| The pack's server setting that bans JourneyMap waypoints hides Waystones' own map markers: the world map showed no waystone and no toggle for them | Touched waystones are drawn by the Wayfarer mod as its own map markers, as found places are (M2), with a show and hide of its own |
| The Waystones menu shows its own costs, and a fold's hunger is paid after the fold | Not settled: whether the menu shows the hunger a fold will cost |

### M24 Roads: worn tracks, no built roads

- The land stays wild: the world has no built roads between its places. The paths are what feet make: tracks wear into grass, dirt and sand where people and animals walk often (The Roads More Travelled), and the caravan's old camps stay (M21). The lore's old roads are such tracks, worn between villages by the settled world.

  ```text
  No road runs here. A trodden line through the grass shows where others walked before you.
  ```

- Built roads come back as a question if playing Act 1 shows the land hard to cross or empty.

**Why:** The pack's look is a vast, wild land crossed by a team that finds its own way, and a road between every two villages would draw the walk onto the settled world's lines. RoadWeaver's own page says it does not work with Tectonic 3, which the pack runs, and worn tracks give the old roads the lore needs without either.

### M25 Seasons: set by the story

- Serene Seasons colours the land and brings snow to temperate lands in winter, but never changes how hot or cold the land is (M10), but while the story runs its cycle stands still and the quest engine turns the season: Act 1 runs from late summer into early autumn, Act 2 through mid and late autumn with its storms at sea, Act 3 through early and mid winter, and the frame stands in late winter. Once the story has ended, the cycle runs on its own, as the day does (M6).

  ```text
  The leaves turn as you leave the Frost Walkers' land.
  By the sea, the first autumn storm.
  ```

- Each act's season is part of its look and its gates: the cold is a tool the story uses, and a quest written for summer never meets a winter it did not expect.

**Why:** The story spans perhaps fifty to eighty days, too few for a calendar to give every act its own weather, and a random winter would break quests written for another season; Red Dead Redemption 2 sets its seasons by chapter in the same way, a snowy prologue and then summer. Serene Seasons stops its cycle with a game rule and sets the season by command, so the engine can turn it.

### M27 Anyone alive answers

- Right-clicking any living person always gets an answer. When the story has nothing for them to say, they say a line about the moment the team is in, heard by every player within talk reach of them; clicked again, they walk through their lines for that moment and never repeat one twice running. The dead give no answer, and nobody speaks over a conversation already going.

  ```text
  The cellar door shakes. You right-click Pip.
  <Pip> I'm not scared. I'm not.
  You right-click Hob.
  <Hob> I've got the door. You get the ones that come round.
  ```

- A step of a quest gives each person their lines for it, and a person keeps the lines of the last step that gave them any until the story moves on; past every quest that speaks of them, they have a few lines of their own for any day on the road.
- The caravan greets the team. Once each camp, the first time someone of the team comes within the fire's circle, one of the people there says a line of welcome (`welcomes` in `story/people.toml`), unless a talk is already under way: "There you are. Fire's going." Corin keeps to the edge of the light and greets nobody.

**Why:** A person who stands mute when spoken to breaks the feeling that the caravan is people; a line about the night in hand makes the world answer, and costs a line of text, not a scene.

### M28 Heading in on any day

- Once the caravan's camp has packed up, a "Make for the caravan" entry stands in the journal every day. Its page asks first, since it ends the day for the whole team: "Turn back now? The day ends for the whole team; The Deserter waits for morning.", and its button reads Make for the caravan, then Yes, end the day. Choosing it, or a quest step that ends the day, sets the camp up out of sight ahead (M5, M19) and points the compass at it; if nobody has chosen by 17:00, it is tracked by itself. Until the camp stands, the compass reads "smoke rising" instead of the ember's listening.
- The task panel says who turned back: "Kai turned back towards the caravan. Make for their smoke; The Deserter waits for morning." From 17:00 it reads "The day is ending." instead.
- Before 17:00 the afternoon then passes at its fire (M20); from 17:00 the run home starts (M6).

  ```text
  Journal › Quests   [Make for the caravan]
  17:00  The ember tugs towards smoke.
         [Main] Make for the caravan   Compass ◆ 512 blocks
  ```

**Why:** D2 promises that the team heads in when the day's quests are done or whenever it chooses, and a day whose quest runs long must still end: without a way in, the sun would wait at 17:30 for good, as it did in the first playtest's evening.

### M33 Room for the team's tents at the caravan's fire

- The caravan's fire is the camp's fire: a tent pitched anywhere inside its circle, 32 blocks from it (`[fire]` `circle_blocks`), sleeps by it, as a tent within 8 blocks of any other lit fire does (M12). The camp keeps no pitches of its own; the team finds flat ground among or beyond the wagons.

  ```text
  Evening at the caravan. Task panel:  Sleep in your tent by their fire
  You walk past the forge wagon to a flat patch 20 blocks out, place the tent, go in, lie down. Morning.
  Far from the caravan, a tent with no fire beside it:
    No fire burns near the tent. Pitch it inside the caravan's camp, or light a fire beside it.
  ```

- The task panel's fire task counts done for a tent inside the camp. Before the caravan is first met, the refusal says only that no fire burns beside the tent, since the word comes with the story.
- **Edge cases.** The caravan packed up and gone: its fire is out, so a tent left where it stood wants a fire of its own. A tent beyond 32 blocks: its own fire within 8. A teammate's tent: the same rule. A hard night or a hold: no tent opens anyway (M6). Underground or on a cliff above the camp: the distance counts in three dimensions, so a tent in a cave under the fire still counts within 32.

**Why:** The fire's circle already marks the camp, as a hearth's warmth reaches farther than a campfire's in Valheim (12 m against about 5); reserving pitches would fight every camp's ground and still leave a team of four short of room.

### M26 How often the other owners' places stand

- Every owner's places share one placement per kind, as M13's do, so the world's small places are spread by what they are rather than by how each mod spread them:

  | Owner | Kind | About one every |
  |---|---|---|
  | The settled world | Small places: farms, wells, stalls, watchtowers, graveyards | 512 blocks |
  | | Taverns, where W11's dicers sit | 1,536 blocks |
  | The land | Trees, boulders, log piles, ponds | 256 blocks |
  | The sea | Wrecks, sunken ruins, islands, lighthouses | 1,024 blocks |
  | Under the ground | Dungeons, buried rooms, old refineries | 512 blocks |
  | | Old mines, where M13's rich ore lies | 256 blocks, about vanilla's mineshafts |
  | The Twelve Peoples | Temples, monuments, crypts | As their mods spread them, until each act's halls set them near their hall (built with Acts 2 and 3) |

  ```text
  Measured on a fresh world from four points of the home land: a tavern within 800 to 1,600 blocks,
  one of the settled world's small places within 150 to 830, something of the land's own within 80 to 450.
  ```

- **Edge cases.** A kind that finds no fitting ground in a cell, such as a wreck inland, tries its other places there and else leaves the cell empty, so no place stands out of its land. `/locate` finds each kind by its tag (`#wayfarer:owners/<kind>`).

**Why:** One spacing per owner keeps the world's places telling its story (M13) and gives the dice and the rich ore a place within a day's walk, while the land's small pieces stay common enough that the road never runs bare.

### M17 Distant Horizons SeedGen

- The far view is drawn by Distant Horizons SeedGen, on the client in singleplayer and on the server in multiplayer: the far land's hills, coasts, rivers and trees come from the world's seed, so the horizon fills in fast wherever the team goes. SeedGen draws no buildings (`pack/config/seedgen.json`), so nothing on the horizon is a place that is not there.
- On a server a client's own SeedGen cannot read the server's worldgen and stands aside, and Distant Horizons takes the far view from the server, whose SeedGen draws it from the real land. SeedGen's lines about that in chat are hidden by the pack, since they name mods the player can do nothing about; its log keeps them.

  ```text
  You join the server. No yellow lines about worldgen data in chat. Hills fill in to the edge of sight within a minute or two.
  ```

- SeedGen keeps Distant Horizons' own rule for the work of loading land onto the graphics card, at most half a frame at the frame-rate limit, where its own default allows eight times that (`SeedGenBudgetMixin`), so arriving somewhere new never spends whole frames on loading.
- **Edge cases.** A server without Distant Horizons: the client's far view stops at its render distance. A player who turns the far view off: the same. A graphics card SeedGen cannot use: Distant Horizons still draws, more slowly.

**Why:** The far view is what makes the world feel vast, and the server's SeedGen reads the same land the server makes, so a team sees one horizon; the chat lines are noise for a player who cannot act on them. Eight times Distant Horizons' rule lets loading take up to 15 ms of every frame at the pack's limit of 260, more than a whole frame on the minimum machine (`production.md` R1).

**The test** (SeedGen 0.8.0, a beta under All Rights Reserved, in dev clients started with `--far`, which keeps Distant Horizons on with a far view of 1,024 blocks; the dev step `/wayfarer far phantom` in `mod/src/dev/java/io/github/roman16/wayfarer/spike/FarSpike.java`): SeedGen's own measuring commands against the real generator, then the far view photographed from 150 to 170 blocks away before and after the real land there loaded. What it showed:

| Finding | For the engine |
|---|---|
| SeedGen is on as soon as it is installed, and turns its own structures back on unless its settings carry the mark of its newest defaults | `pack/config/seedgen.json` carries that mark beside `structures` off |
| Terrain heights from the seed match the real world in 90% of 4,096 columns over 4,096 blocks, off by 1.8 blocks on average, 15 blocks for the worst twentieth, 115 at most | The far view's hills, coasts and rivers sit where they really are |
| Near a shore only 47% of the ground blocks match: SeedGen draws grass, stone or sand where Terralith's brushland and gravel beaches lay gravel, dirt and clay | Far land can look greener than it is, and its colour changes when the player comes near |
| Photographed before and after the real land loaded, snowy hills and a lake under cliffs looked nearly the same; the trees stood in other places | The far view is close enough not to be noticed on the move |
| SeedGen draws towns, villages, outposts and the pack's own places from their real definitions, but six vanilla kinds (desert and jungle temples, witch huts, igloos, woodland mansions, ocean monuments) from copies of its own at vanilla spacing (read in its code, not seen in game: the one tried stood in snow) | With the pack's structures spread 8.5 times wider, a far view would show such buildings about seventy times as often as they stand, gone when the player comes near; a pack whose places are quest goals cannot show places that are not there |
| Around a new spot the far view out to 1,024 blocks filled in 91 to 129 s with the graphics card and much slower on the hidden screen; Distant Horizons' own generator was not measured beside it | Fast enough on foot; whether it keeps up with a ship or an aircraft is for the playtest |
| Three launches with C2ME in the pack, which SeedGen warns about, ran without a crash | No change needed |
| On the first server playtest each client's SeedGen could not read Geophilic's worldgen data, stood aside and said so twice in chat, and Distant Horizons downloaded the far view from the server | The pack hides SeedGen's chat lines (`SeedGenSharingMixin`); the far view on a server is the server's |

### M34 Waking after a fall

- A player who falls comes to beside the teammate nearest the place they fell, on safe ground, as a player who joins does (M7); with nobody else on the road, they come to by the last fire they rested at. A bed, a tent or a story's fire never sets where they wake. Their things stay under the stone where they fell, and the task panel says to fetch them while any stone of theirs stands.

  ```text
  You come to beside Mo. The ember found the nearest warmth it knew.
  ○ Fetch your things: the stone where you fell, marked on your compass
  ```

**Why:** In the playtest a fall sent a player to a tent five thousand blocks behind and another to a story's fire the team had long left, alone and naked, and the cold took them in minutes. Waking beside a friend keeps a team together, which the story assumes everywhere, and the last fire is the lore's answer for a player alone.

### M35 The story's places hold

- What the story builds and still needs cannot be broken, built on, burnt, flooded or blown up: the Last Camp with the stranger's grave, Tollbridge's bell tower and its cellar until Ash is done, and the caravan's camp while it stands. Doors, beds and chests in them still work, and a grave in them can be taken back, since nothing may keep a player from their own things. A refusal says why: "Leave it standing: it is all Tollbridge has left."

**Why:** In the playtest one swing at the cellar's fire stopped the story until someone typed a command. A place the story waits on that one player can break ends the story for everyone, and the reasons are the world's own: a grave, a ruin, someone else's camp.

### M36 Everything given has a form

- Whatever the story gives can be found again. The ember sits in a charm slot of its own from the moment the stranger presses it into the player's hand: grey and cold until it wakes, glowing after, never taken off and kept through a death, as the shards are in theirs. Its tooltip says its state and how many shards it holds; a shard is named for its people, "The Diggers' Shard", and its tooltip names the gift it gave. Everything else the story gives is an item.
- The first time the ember is given, the task panel says where it is.

  ```text
  ○ Your ember sits with your charms: open your inventory [E]
  The ember
    Warm, and it glows.
    Shards: 1
  ```

**Why:** The ember is the thing the whole story turns on, and a player who cannot see it has nothing to hold; the shards already sit in their slots, so the ember belongs beside them.

### M37 The fights: owner by owner, act by act, always with a tell

- **Who the team meets where.** Every foe belongs to someone, as every place does (D17), and each act brings its land's and its story's foes; nothing shows up before its act, so the world visibly changes with each:

  | Who | Act 1, on foot | Act 2, the sea | Act 3, the sky |
  |---|---|---|---|
  | The gaze's creatures: night, shade and storms; they dig up at dusk and crumble at dawn | Zombies, skeletons, spiders, creepers | Also drowned at sea, husks in the desert | Also strays in the snow, phantoms in the air |
  | The land's beasts: by day, on their own ground | Wolves, bears, boars | Alligators, scorpions, lions, sharks, piranhas | Polar bears, and the cold itself |
  | The Gazebound: people, at any hour | Road patrols: pillagers and vindicators | Raiders with an evoker and its vexes; their outposts | The heir's army: ravagers, evokers, illusioners, iceologers |
  | The gaze's own beasts: one each, at a story moment | | The sea's beast (M22), the worm (2.6), the horse that never sleeps (W9) | The phantom nest (W12), the dragon (M14) |
  | The deep | The Warden in the Ancient Cities, best crept past (W4) | | |

- **Nothing hurts without a tell,** heard first and seen second. The gaze's creatures make their digging sound as they come up; the Gazebound shout a line before they charge ("There! The ember!"), and their banners show from afar; beasts growl at the edge of their ground and attack only if pushed, hunters at night aside; a named beast's tell is long, the worm's tremor and rising dust, the sea beast's knock on the hull; a hold's next wave is announced on the band (`interface.md`, I4).
- **A fight asks for the team, the ground and the gear,** not only damage. Everyday fights reward the fire's circle, the tent, a shield and a bow; each act's foes ask for that act's gear (M18): a shield and a bow on foot, a ship's cannons at sea, rolling and parrying for the duel, flying past phantoms. Getting past a fight, creeping by or walking on, is always an answer and never a loss, save at the ford (1.3), where the patrol has seen the ember and follows a team that walks off (`story.md`, S28). Each named fight has one idea beyond swinging:

  | Fight | What it asks |
  |---|---|
  | P3 The cellar door | Keep the door standing and the survivors alive; the waves walk in to it and beat on it until it cracks |
  | 1.3 The ford | Keep Corin alive while the Gazebound cross; the water slows them, so the fight is at the crossing |
  | 1.10 The Bell, under the red moon | The bell draws the gaze to the caravan; hold the camp, then decide whether to melt it |
  | 2.2 The Crossing | The sea's beast rams the hull: a boat breaks where a ship holds, and the ship's cannons drive it off |
  | 2.6 The Worm | Learn the Sand Singers' song: the worm comes up to it, and the eye that sees is where it can be hurt; or leave it alive |
  | 2.10 The Hollow, under the red moon | It cannot be won, only survived; who lives was decided by each person's side quest |
  | 3.4 The Ward's champion | A duel in a ring, parried and rolled; the team watches and cannot step in |
  | 3.7 The Empty Grave, under the red moon | The dead rise among the graves until dawn |
  | 3.8 The Rider | A chase, on horseback or in the air |
  | F1 The Gate | Hold the frame against the heir's band, until Maren turns on them |
  | The dragon, in Blind it | It is as blind as the shards set make it (M14) |

- **Scaling.** Everyday foes grow only with the shine (M9). Named fights scale with the size of the team online, their health and their waves per player, as the dragon's health does, never with the shine, so a team of one can win each, and a retry (D6) comes back the same.

**Why:** Valheim makes each biome's creatures its own and lets the next tier appear only once its boss falls, so the world itself says how far the player has come; giving every foe an owner and an act does that for the road. Mike Stout's rule that every enemy asks the player a question, and telegraphs its attack long enough to answer it, keeps the pack's harder nights fair, and The Witcher 3's contracts show a beast read before it is fought. Named fights that scale with the team and not the shine stay winnable for a player who took every shard, and the same on every retry.

It raises, for its build: keeping each act's foes out of the world until their act, from biome spawns to the ravagers of vanilla's raids.

### M38 Deeds: rare feats, hidden until earned

- Every advancement, vanilla's and every mod's, is off (D7). The pack keeps sixteen deeds of its own, each for something rare, with its number in `balance.toml`; their lines are finished with the `lore` skill when built:

  | Deed | Earned when | Its line |
  |---|---|---|
  | The Long Road | 65,536 blocks walked | "I have walked far enough to cross the world and back. My boots have stopped complaining." |
  | Many Fires | Slept by 64 different fires, each 256 blocks from the others | "Sixty-four fires, and not one of them home." |
  | Every Village | Walked into 16 villages | "Sixteen villages. In every one, somebody asked me to stay." |
  | The Edge of the Map | Sailed out on the Last Sea until the ember went cold | "There is nothing past the fog. I went to see." |
  | Held | Came through 8 hard nights that no quest called | "Eight nights the gaze found us, and eight mornings we were still there." |
  | Known on the Road | Met 32 of the road's scenes | "The road has started to recognise me." |
  | Old Friends | Met the herder in every one of his variants | "His dogs come running now." |
  | The Dice | Won 8 games of dice in the taverns | "The peoples' dice like me. The peoples don't." |
  | Untouched | Won the champion's duel without being hit | "He never touched me." |
  | Hands Clean | Went a whole act without killing a person | "Not one of them by my hand, this whole season." |
  | Deep Quiet | Crossed an Ancient City without waking its guard | "I walked through their city, and the guard never woke." |
  | The Slow Way | Reached the frame without folding to a waystone | "Every step of it, walked." |
  | Light Pockets | Reached the frame with four shards or fewer | "I came to the gate nearly empty-handed, and lighter for it." |
  | All Twelve | Reached the frame with all twelve | "All twelve. It can see so much now." |
  | Nobody Left Behind | The Hollow took no one from the caravan | "It took the wagons. It didn't take one of us." |
  | Alone | Reached the frame with nobody left in the caravan | "There is nobody left to follow my marks." |

- No deed is listed before it is earned. When one is, a banner like the quests' slides in under the compass, "A deed: The Long Road", with a sound of its own (`sound.md`), and the deed stands on the journal's Deeds ribbon with its line and the day it was earned; the ribbon appears with the first deed.
- What a body does is its player's own: walking, sleeping, the duel, the dice, the city crept through. What the story decides goes to everyone on the team: the frame with four shards or all twelve, the Hollow, Alone, a whole act's clean hands (D11).
- A deed gives nothing but its line and its place in the journal.

**Why:** Lucas Blair's research on achievements finds that goals announced ahead push players to chase them and change how they play, while unexpected ones reward play that already happened; a deed hidden until earned is a surprise, never a chore, and one that gives nothing is not worth grinding, which the pack takes away everywhere else (D7).

### M39 The world's shape: a home land, a sea round it, and a world of water beyond

- **The home land.** The Last Camp stands in the middle of one land that reaches 6,000 to 8,000 blocks out, about 116 km², never cut by sea, only by rivers and lakes. Its coast follows the land's own shape, not a circle. Every place of Act 1 lies on it, so Act 1 is walked.
- **The sea round it.** An open sea all round the home land, about 3,000 blocks wide, deep and without islands, its far shore out of sight. Act 2 begins by crossing it (2.1 Ships for Everyone, 2.2 The Crossing): about six minutes under sail, long enough for 2.2's night on the water.
- **The world beyond.** About two thirds sea: lands and islands of every size across wide water, so ships carry Act 2 and flight carries Act 3. The land ends at the Last Sea's coast 39,096 blocks out, the ring of frames' 35,000 plus M13's 4,096, and deep sea runs on to Minecraft's own world edge.

  ```text
  Day 6. You follow the ember west through forest and hills, wade a river, and come out on a grey coast.
         Open water to the horizon. The ember pulls back east, towards the swamp.
  2.2    You push off at dusk. The coast sinks behind you. For a long while there is nothing but water and the ember's light on it.
  ```

- **How it is made.** Tectonic's raw continent value (`tectonic:noise/raw_continents`), from which both its terrain and the biomes' continentalness come, gets a term that depends only on the distance from the Last Camp (`WorldShape`, added where the game wires the noise): a lift of 0.625 over the home land, falling over 4,096 blocks to its coast; a sink of 1.25 across the sea; a sink of 0.25 over the world beyond, so about two thirds of it is sea; and a sink of 2.5 from 1,024 blocks before the Last Sea's coast onwards. Over both seas the value is held at -0.5 or above, where Tectonic grows no islands, so they stay deep and bare. Its numbers live in `balance.toml` `[world]`. Valheim makes its world the same way, a disc whose bands are set by distance on top of its noise, its land ending at 10,000 m.
- **Edge cases.**
  - A team that builds a boat in Act 1 can cross by day in fair weather; nothing forbids it. At night or in rain the sea's beast rams a vanilla boat (M13's sea), so a team crosses for good with ships. On the far shore nothing of Act 2 has opened yet, and what they find and take there counts as everything does (D6).
  - Swimming three kilometres is possible, long and cold at night.
  - A world made before the shape keeps the chunks it has; only new land takes the shape, so it needs a fresh world.
  - With more sea, fewer villages, towns and ruins stand out there, since they stand only on land; their spacing (M13, `[structures]`) is measured again once the shape is built.
  - A frame of the ring that would fall in the sea moves along the ring to the nearest land.
  - Distant Horizons draws the far land from the same noise, so the far view shows the same shape (not yet looked at).
  - Terralith hangs its skylands, floating islands, over deep water where its weirdness, cold and dryness meet, the seas' as anywhere; they float in the sky and nothing stands on the water.
  - The world's spawn is on the home land on every seed, since the land round the Last Camp is lifted; a lake at the very centre still moves the spawn to its shore, as vanilla does.

- **Caves open often.** Cave mouths are about twice as common as Tectonic's own: caves reach the surface (`depth_cutoff_start` -0.1) and its noodle and cheese caves run wider (`noodle_additive` -0.2, `cheese_additive` 0.18, `pack/config/tectonic.json`). On four 256-block squares of the states' seed the openings went from 159 to 286, so a team on foot finds a way down most days without digging.

**Why:** On six seeds measured (`ShapeSpike`, dev code that never ships), today's world was about half sea in islands of 2 to 314 km², usually 25 to 47, so most seeds put Act 1's places across water, and two put the Last Camp on an island of 2 km². Built into the game, the shape gives a home land of 117 to 132 km² reaching about 7,750 blocks on every seed measured, a closed sea, and 28 to 34% land beyond it; the acts' own travel, on foot, by ship, by air, then follows the land.

**The spike measured** (`/wayfarer shape <seed> <radius> <step>` and `/wayfarer preview <seed> <home> <sea> <sink>` on the dev server, pictures in `build/shape/`):

| Seed | Today: the camp's land, its reach | With the shape |
|---|---|---|
| 20261005 (the states') | 47 km², 7,223 blocks | 116 km², 7,997 blocks; beyond the sea 22 to 30% land |
| The second playtest's world | 44 km², 7,273 | 118 km², 7,907 |
| 1 | 314 km², 47,068 | |
| 2 | 2 km², an island 286 blocks off the origin | 113 km², 8,108 |
| 3 | 25 km², 8,707 | |
| 5 | 2 km², an island | |

| Finding | For the build |
|---|---|
| The climate sampler's continentalness with vanilla's ocean edges (-0.19, -0.455) gave the same map as the biomes did (47 km² either way) | The previews are faithful to the world's own seas |
| A coast falling over 1,024 blocks came out a near circle; over 4,096 it follows the noise | The home's coast falls over 4,096 blocks |
| A sink of -0.7 left land bridges across the sea; -1.2 closed it on every seed | The sea's sink is -1.2 |
| Tectonic's pack is a built-in data pack at the top of the list, so a data file of the pack's own cannot override its continents | The term is added in code where the noise is wired, for every reference to the continents, a plain one or a spline's coordinate (`ContinentsMixin`, `SplineCoordinateMixin`) |
| Added to `full_continents`, the term moved only the biomes: Tectonic's terrain reads `raw_continents`, and land rose under ocean biomes past the Last Sea | The term goes on `raw_continents`, which both read |
| Tectonic grows islands wherever `raw_continents` falls below -0.5, which the sinks reach | Over the two seas the value is held at -0.5 |
| The previews' lift of 0.5 and outer sink of 0.15, measured on the climate's continentalness, left the real home land at 56 to 85 km² with inland seas and the world beyond at 40% land: `raw_continents` runs from -0.8 to about 0.45 | A lift of 0.625 and an outer sink of 0.25 gave 117 to 132 km² and 28 to 34% |
| On a fresh world of the states' seed the game showed snowy forest on the home land at 6,000 blocks and open water at 9,000; from the Last Camp the nearest Act 2 hall stood 16,209 blocks out, the nearest Act 3 hall 33,187, no Frost Walkers' hall grew, and the nearest frame stood on land at 34,080 | |

### M40 Where each act's halls stand

- **Act 1's halls are put down by their quests, on the home land** (M39), each about a day's walk ahead of the team, on the nearest fitting ground in the way it is walking and out of sight, as the Diggers' tunnels (1.1, S24) and the Marsh Mothers' hall (1.4, S29) already are: the Frost Walkers' winter house and hall in snow (1.6, 1.7, and the frozen lake of 1.8), the First Village's ruins on open plains (1.10). The world grows no copies of Act 1's halls anywhere.

  ```text
  The morning after Corin joins, the ember pulls north. Two days of walking; the air bites; then snow, and a winter house half buried in it.
  ```

- **Act 2's and Act 3's halls grow where the world makes them, in bands.** Act 2's (the Tide Folk's bay, the Drowned City's badlands, the Sand Singers' desert, the Jungle Folk's temple) grow only between the sea's far shore and 24,576 blocks from the Last Camp; Act 3's (the Grass Kingdom, the Shadow Circle, the Scribes, the Grave Keepers) only from 24,576 blocks to the Last Sea's coast, the ring of frames among them. The ember pulls to the nearest of its kind. None grows on the home land.
- **Edge cases.**
  - Every place Act 1 puts down, Tollbridge, the Diggers' tunnels, his first camp, the swamp's hall and the ford on the way to it, is picked within 7,168 blocks of the Last Camp, halfway down the home land's falling coast (`Home`): when the way would lead out to sea, the search turns along the coast, 15 degrees at a time, until its farthest point lies on the home land.
  - A home land without snow: the Frost Walkers' quest puts their hall in the home land's coldest corner and the snow comes with it, as 1.4 makes a bog where no swamp is near.
  - A team that walks far before tracking an Act 1 quest: its hall is placed from where the team stands when the quest is tracked (D6), still on the home land.
  - A team that has left the home land by boat: an Act 1 quest still picks its ground on the home land, searching from the home land's side nearest the team, and the compass points back.
  - A team that sails early and finds an Act 2 or Act 3 hall: it stands there, and what they take counts (D6).
  - A kind of land missing from its band, or a hall of the kind a quest needs lying far off: an Act 2 or Act 3 quest takes the nearest of its halls the world grew within 8,192 blocks of the caravan (`balance.toml` `[halls]`); with none that near, it raises one of that kind on the nearest fitting land within reach, out of sight, as Act 1 puts its halls down. On the states' seed the nearest Drowned City hall lay 26,000 blocks off.
  - With two thirds sea, a band's halls stand farther apart, so each act's spacing is measured with `just survey` (AGENTS.md); its legs are walked from the inner edge of the band, and their targets are `balance.toml` `[halls]` `act_two_leg_blocks` (8,192) and `act_three_leg_blocks` (12,288), Act 2 crossed by ship and Act 3 by air.

**Why:** Act 1 is walked, and its quests already lay down the first two halls a day ahead, so the other two follow suit and the act's pace stays a day a hall whatever the seed; Acts 2 and 3 are travelled by ship and by air, where a hall found on a far coast is the reward of the voyage. The census of the land under the shape (`ShapeSpike`, on four seeds) found every Act 1 land on the home land, snow and ice 6 to 52% of it, forest 10 to 18%, plains 13 to 17%, swamp 1 to 4%, and every Act 2 and Act 3 land in both bands, mountains the rarest at 0.3 to 0.6%; dark forest is not counted by its tag, so its share is not yet measured.

**Built and surveyed** (`BandPlacement`, `generator/eyes.toml` `[acts]`): three surveys of five seeds walked 80 legs an act; at spacings of 125 and 189 chunks the median leg was 8,314 blocks in Act 2 and 11,892 in Act 3, against targets of 8,192 and 12,288.

### M41 Leaving the End after Blind it

- When the dragon falls, its lines end on the sculk, and the exit portal opens on the island as in vanilla. Stepping into it takes each player straight home, to their bed or the world's spawn: vanilla's End Poem and credits never play in a world where the story runs.

  ```text
  Far below, in the deep dark, the sculk creeps one block further. Nobody hears it.
  You step into the dark at the island's heart, and wake at home.
  ```

- A player who comes to the End later, or a teammate who was never there, goes home the same way; in a world with the story off the End is vanilla's, poem and all.

**Why:** The pack's last word is the sculk's line, and Mojang's two voices speaking of the player as a dreamer tell another story straight after it; credits of the pack's own would belong at every door, not only at this one.

### M42 A team that stays at the morning's camp

- The morning's camp stands until the team has walked out of sight of it (M21). If 17:00 comes and it still stands, because someone stayed in sight of it all day (building, crafting, in a tent at the fire or away from the keys), the caravan stays a second night where it stood: "Nobody took the tents down. Tonight the caravan camps where it stood." It calls the team in as on any evening (M28), the dusk run starts for whoever is near, and the evening comes at its fire, without the welcome back, since nobody left.
- A teammate who walked off meanwhile gets "Make for the caravan" pointing back to the same fire; one who leaves after 17:00 finds the camp standing until the next morning. Nobody online means nothing ticks, as on any day. The road's rules hold as on any night: a player who stays still is found by the gaze as anywhere else (S18).

**Why:** A day must end even when nobody leaves (M28), and packing up in sight breaks the caravan's one rule (D14), so the camp that never got to leave simply stays.

### M43 What stays open after the doors

- After every door the side quests the team left open stay in the journal and can still be finished, and the caravan keeps its days, packing each morning and setting up ahead again. Under Blind it and Close the Eye it no longer calls the team in at 17:00, since the road's rules are over, so each door's last quest stands in the task panel; a player can still head in from the journal. Under Give it its sight it calls them in as before, since every night outside home is hunted.

**Why:** The world goes on after the doors, and taking away a quest the team chose to leave open takes away a choice; each door's last quest already says what that world is for.

### M44 What is worn: the Curios panel

- The Curios panel holds what a player wears beside their armour, each in a slot of its own: the ember, the shards, the spyglass, one charm (the totem of undying, Friends & Foes' totems or an Ember Veil) and four clothes, Hood, Coat, Trousers and Boots, which take only Tough As Nails' wool clothes against the cold and leaf clothes against the heat.
- Clothes in their slots count for warmth or coolness as if worn as armour, on top of whatever armour is worn, so a player in iron with a wool hood and coat is a step warmer, and wool both as armour and as clothes is the warmest there is. They are not drawn on the body.
- The spyglass in its slot is looked through with C (Spyglass Improvements), from the hotbar too; from deeper in the bag C does nothing. Far-sight works from the slot as from the hand (M11). Everything worn counts as had on the Gear page (M18), and everything worn goes into the grave and comes back with it.

  ```text
  Inventory › Curios   Ember [ember]  Spyglass [spyglass]  Charm [totem]  Hood [wool hood]  Coat [wool coat]  Trousers [ ]  Boots [ ]
  ```

**Why:** Clothes under armour let a player keep their best gear on in the snow and the desert instead of trading it for wool, and a slot for each worn kind keeps the spyglass and the totem from pushing each other out.

### M45 The Last Camp stands out of the snow

- When the world's spawn lies where it snows, the Last Camp moves to the nearest dry land within 2,048 blocks where it does not snow (`[prologue]` `camp_search_blocks`), and the world's spawn moves with it, so the first night never begins in a blizzard with nothing to wear. With no such ground within reach it stands where the spawn is.

**Why:** A newcomer meets the story at the fire; freezing there before the stranger has said a word teaches only that the start is unfair.

### M46 Loot: a unique now and then

- Simply Swords' unique weapons come about once in 64 chests a player opens, never in a village: its pity starts after 32 chests without one and makes one certain by the 128th (`pack/config/simplyswords/loot.toml`). When Dungeons Arise, whose chests held diamond gear and high enchantments from the first days, is cut (`mods.md`).

**Why:** A unique is the road's rare reward; one in every chest makes it the road's floor.

### M47 Story places are checked when the team comes

- A place a quest puts down far ahead, his first camp, the Diggers' tunnels, the ford, the Marsh Mothers' hall, the Frost Walkers' house, hall and lake, the First Village and the shipyard, is checked the first time a player comes within the view distance and 64 blocks of it, before anyone can see it: if its own blocks are gone, it goes up again where it stood, out of sight. The Marsh Mothers' hall goes up only once a player is within 512 blocks (`[marsh]` `near_blocks`), as S29 has it.
- An operator has a way past anything the check misses: `/wayfarer step` says the tracked quest's step, its place, how far and which way; `/wayfarer step again` puts the step's place down again, the ford and the swamp's hall anew ahead of the caravan and the others where they stood; `/wayfarer step skip` plays the step's outcome, its first answer, a hold won, a memory dreamt, and moves on. Each takes a quest's id to act on another quest. In singleplayer they need cheats on.

  ```text
  /wayfarer step        The Deserter, 1 of 3: Follow the pull: walk where the ember flares. The ford at 4268 64 3030, 2610 blocks south-west.
  /wayfarer step again  The ford is put down again ahead of the caravan, out of sight.
  /wayfarer step skip   Done as if played: The Deserter, 1 of 3: ...
  ```

**Why:** In the third server playtest the ford and the swamp's hall, both built while nobody was near, were gone when the team arrived, and nothing in the logs showed why; a quest's place that is not there ends the playthrough for everyone. A check before the team sees the spot mends it without them knowing, and the commands are the last resort a stuck team needs.

### M48 The settled and the grey coats speak

- A villager says one wary line the first time each day a player right-clicks them, on the band as "A villager", before the trade opens. Their lines follow the story: strangers bring the burning in Act 1; men in grey ask after a fire that walks in Act 2; banners pass at dawn in Act 3; after the doors it is quiet, and they ask the team to stay for supper (`story/people.toml`, `villager`).
- An Illager that spots a player shouts once, "There! The light! Take it!", on the band as the man in grey, at most once in 16 seconds and never over a talk (`gazebound`, `shouts`).

  ```text
  Right-click a villager   <A villager> Strangers bring the burning. One trade, then go.
  A pillager sees you      <A man in grey> To me! It's here, the fire that walks!
  ```

**Why:** The second server playtest asked for villages that feel lived in and enemies with a voice; a line each, rare and in the world's own words, says what the lore says of them, that towns which grow burn and fear strangers, without a talk to sit through. The settled have no name for what burns them, so they never say the gaze.

## Open

Nothing is open.

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
    { id = "told", text = "Tell her it was Wend", remember = "nell_knows" },
    { id = "hid", text = "Say you didn't see", remember = "nell_lied_to" },
  ]
  ```

  The field names only show the idea; the spike settles the real format.

- What the engine and the journal carry, everything the design asks of them:

  | Part | What it does | From |
  |---|---|---|
  | Main and side quests | Listed apart in the journal, each with its current objective; main quests carry the acts, and some carry no shard | D3, D6 |
  | Steps | A quest is steps; a step's goal is a hall, a person, a memory, a fixed place, a place the quest puts down, or the nearest place of a kind ahead | D3, D16, S13 |
  | Order | A quest opens after others, or "by count" after a number of an act's halls, whichever they were | S3, S16 |
  | Tracking | Each player tracks one quest, and the compass shows its goal as the player knows it: by feeling (the ember flares and gutters, exact within 256 blocks), by rumour (a direction and a rough distance, exact within 256 blocks) or by being told (exact from the start) | D6, S7 |
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

- The map is JourneyMap's world map, opened from its tab in the journal or with M. There is no minimap anywhere on screen, no cave mapping, no radar and no waypoints of the player's own; the places they have found are added by the Wayfarer mod. Map Atlases leaves the pack.
- The compass shows the tracked quest's goal, the player's friends and their stones. Every fall leaves its own stone where the player fell; the compass shows all of them, the one holding the most the strongest. In the journal a stone can be tracked, so the compass points at it like a quest, or let go, which takes it off the compass, as when a player gives their old things up and starts afresh.

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

- Every player online sees a conversation's lines in chat, each speaker in a colour of their own, as the voice and Maren have today.
- An ordinary answer can be clicked only by a player near the person, within about 32 blocks, and the first click decides. A hard decision, a shard or a door, can be voted on by every player online, wherever they are, and needs two thirds.
- There is one conversation at a time in the world. A second person spoken to answers with a line of their own and waits.

  ```text
  <Bram> Not now. I'm listening to them.
  ```

- A hard decision waits, and the person with it, until two thirds of the online team have chosen; walking away pauses a conversation, and coming back carries it on.

  ```text
  <Nell> Give it to me. I'll bury it with them.
    [Give it to her 1/3]  [Keep the shard 1/3]   (waiting for Mo)
  ```

**Why:** The story stays one for the whole world, while nobody far away answers for the player standing in front of the person, and the decisions that belong to everyone reach everyone. One scene at a time keeps the chat readable and matches the narration's one voice at a time, and a decision that waits never lets someone's absence choose a shard.

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
- The clock eases out: its speed is always normal speed times the gap left to its target over the gap it started with, so it starts at normal speed, slows the whole way and never arrives. On the road it eases from the morning towards a low-sun evening at 11,500, about 17:30, and holds there. From 17:00, once "make for the caravan" is tracked and most of the team is within 256 blocks of the camp, it eases towards 22:00, timed so monsters come out about 1:15 later: sprinting (about 5.6 blocks a second) the team is in 30 seconds before them, walking (about 4.3) 16 seconds before, and a minute wasted on the way meets them. A quest that keeps the player out sets the clock itself, and a hard night does not ease out at 22:00 but runs on to dawn.

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
- The gaze finds the caravan when a quest calls for it (The Bell, The Hollow, The Empty Grave), when the caravan has slept three nights in one place, or by a chance each night that grows with the player's shine, and more while Tamsin's bell is with the caravan. When the camp goes up that evening the ember warns: "The ember is uneasy tonight." The red moon comes only when the story wants it, once or twice an act.

**Why:** A clock the story drives lets every night land where the people are and every hard night land as a set piece, and easing out keeps the sky moving so time never visibly stops. Counting days in nights slept cannot be cheated, and daylight that is safe only in the open keeps the forest, the storm and the Gazebound dangerous on a long day's road.

**The spike works** (`mod/src/dev/java/io/github/roman16/wayfarer/spike/ClockSpike.java`, dev code that never ships, with its targets in `balance.toml` `[clock]`): the road eases from 6:00 towards 17:30 and stands at 11:59 after 7 minutes, 14:33 after 13, 16:05 after 20, 17:08 after 33 and 17:24 after 46, as the table above says; with three players online the evening waits while only one is within 256 blocks of the caravan and begins the moment a second walks in, and monsters can come out 74.8 seconds later; a hard night runs on at normal speed to 6:00, where the next day begins on the road by itself. The pack's per-day difficulty is gone (`pack/config/improvedmobs/common.toml`), so until the shine (M9) monsters grow only with the shards. What it showed:

| Finding | For the engine |
|---|---|
| NeoForge sets a world's clock speed (`setDayTimePerTick`) and sends it with the clock's fraction to every NeoForge client, which then moves its own sky at that speed: the client's clock moved 0 to 2 ticks a tick and never backwards | The engine sets a speed about once a second and never sets the time itself |
| Monsters can come out on open ground at 13,188 (19:11) in clear weather, at 12,969 in rain and at any hour in a thunderstorm, from Minecraft's sky darkening and the overworld's spawn light of 0 to 7 | The evening reads the weather when it begins and times the run to that hour |
| From 17:24, easing from normal speed reaches 19:11 after 1:53; meeting 1:15 takes 1.5 times normal speed at the start | The evening starts at whatever speed meets `dusk_run_seconds` from where the clock stands |
| Heading in at noon starts the evening at 8.3 times normal speed, so the sun races to dusk | The run starts only from 17:00, and an earlier evening passes at the fire (M20) |
| NeoForge's speed does nothing while the daylight cycle is stopped, as the showroom stops it for its noon | The engine turns the daylight cycle on whenever it takes the clock |
| A slept night jumps to the next 6:00 (`ServerLevel`, read in its code, not tried), the same hour a hard night runs to | Dawn, slept through or sat through, starts the road |

### M7 Joining later

- Who appears where, and with what, is D11's table. A tent or a mount counts as far beyond 256 blocks: then it comes back to the player who joins or returns, and one closer stays where it is.
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
- The dream rebuilds the place from the world's own seed, exactly as the world first made it: the same hills, rivers and hall, and nothing the players built or broke since, since that is the past. The memory then changes only what it needs, the hall whole and newer, the shard lit on its pedestal, the Frost Walkers on the stair, the grass halls young, and places again what the pack sets down by code, such as the Last Camp; whatever the memory needs no change to stays as the world made it.
- Everyone within about 32 blocks of the stone dreams it together, every one of them as Wend. A player who was not there can dream it again later from the journal, at a fire.

  ```text
  Kai touches the calling stone. The ember flares, in all of you.
  Twenty winters ago. Three of you stand on the stair, and every one of you has Wend's hands.
  ```

- Falling in a memory wakes the player into it again, from its beginning or its last step, and costs nothing in the real world; Wend lived through it.

**Why:** Rebuilt from the seed, the dream is recognisably the place the players stand in, down to the last hill, without copying the present and without tracking which blocks players changed; a world apart leaves the real one untouched for friends who stay in it (D11). Dreaming together keeps nobody watching, and replaying from the journal means nobody misses Wend's life for being away.

**The spike works** (`mod/src/dev/java/io/github/roman16/wayfarer/spike/DreamSpike.java`, dev code that never ships): touching a hall's pedestal makes a dream world with the overworld's own generator and seed and takes everyone within 32 blocks into it, at the same spot; the hall comes back as the world made it, its pillar of planks and torch, which a player had put there, gone, and the memory makes it newer, 805 mossy, cracked and cobwebbed blocks made clean, and walks a Frost Walker to the stair, where she kneels. A pretend player killed in the dream wakes at its beginning with full health and nothing lost, and at the end everyone wakes where they stood and the dream world is let go and deleted. What it showed:

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

**Why:** A step for every shard makes each one a cost the player can feel and leaving one a relief they can feel too, and four effects together make the shine being hunted rather than only fighting tougher monsters. A number would turn the clock into a stat to manage; felt, it stays the slow horror of S12.

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

- **The ember sense** is a key. Held, the ember flares, and for a few seconds clues and tracks within about 24 blocks glow faintly. A clue is something in the world the player looks at and clicks; each adds a line to the journal, and the step is done once enough are found. A trail is a line of marks laid on the ground ahead, the way road scenes are (M4), such as hoofprints, blood or a cold fire, and taken away afterwards. The Trail-sight gift (S6) makes tracks glow all the time.

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
| Earn | A people's trust, a number per people in the world's state, raised by a short chain of small quests, each with a turn and never a chore | The quest engine |
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

- A heist has roles: the lookout marks guards from above, the lock-hand opens doors, the distraction draws the guards off, and the carrier runs out with the prize. Each player picks one, and roles nobody takes go to the caravan's people, such as Corin as the distraction and Ilse at the locks; alone, the player takes one and the caravan the rest.
- Easy NPC's people can walk (its wiki lists objectives to follow a player, flee, and stroll around a home), so the camp's people also stroll about their own tents.

**Why:** Thirst would be a second bar to refill all day beside the ember's hunger, which is upkeep and not play, so the land's danger is heat and cold. Hunts need creatures no mod has, and GeckoLib is how mods animate them. Attract to Sound already makes stealth real, and its licence (GPL-3.0) lets it be changed. Heist roles are GTA V's heists for a team of up to four, with the caravan making them work alone.

**The spike works** (`mod/src/dev/java/io/github/roman16/wayfarer/spike/WarmthSpike.java`, `ActsSpike.java` and `WormSpike.java`, dev code that never ships, with Tough As Nails, GeckoLib and Attract to Sound in the pack): thirst is off (`pack/config/toughasnails/thirst.toml`), and on icy land at night a player who was cold and heading for icy stays neutral with the Warmth gift; a pillager walks to the clang of an anvil 8 blocks away, with Attract to Sound's own raids off (`pack/config/soundattract/raid.toml`); an Easy NPC person follows a player 21 blocks; and a worm whose model, animation and texture the generator writes (`generator/creatures.py`) crawls in the workshop, its five segments swaying on sine curves. What it showed:

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
  | Far-sight | Zoom far, and mark a place on the map from far off | Finding places sooner |
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
- A bed inside a tent sleeps only when a lit campfire burns within 4 blocks of the tent outside (D13).
- The fire's stories stay. If a night has one, the voice's long talk, a person's scene or Maren's visit, it plays once most of the team sits at the caravan's fire, and sleep waits until it is over; a night without one goes straight to sleep. Resting half a minute at a fire still warms the ember, so the player eats like a settled person until the next dusk.
- Every tool, weapon and armour, Silent Gear's, Simply Swords', vanilla's and every other mod's, stops at its last use instead of breaking (D13), and at its last use a tool is a bare hand, in speed and in what drops: a diamond pick at its last use takes nothing from obsidian. Silent Gear already keeps broken gear (`breaks_permanently = false`), and the Wayfarer mod brings the rest to the same rule.
- At a fire, a repair kit and the tool's own material mend it back to half its durability at most, never beyond. A smith restores it fully: the peoples' smiths ask for goods, and Hob does it for nothing once his side quest has found the old ore (C7).

  ```text
  At the fire you bind the cracked pick with flint and cord. It will hold: half its strength, no more.
  Hob turns it over. "Leave it with me tonight."
  ```

**Why:** The tent the pack needs is the folded space it already has, and its safety rules are already written into it, so only the mallet goes. The evening is where the story is told, and the sleep rule keeps anyone from sleeping through it. Mending to half keeps the road going while a full repair stays something to reach a smith for.

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
- **Enchanting.** Enchanted books stay in loot, and only a smith can put one on: the peoples' smiths for goods, Hob once his side quest is done (C7). Mending and every enchantment that runs on XP go, since repair is the fire's and the smith's (M12).
- **Ore.**

  | Ore | In plain stone | In places: old mines and the deep stone around the Ancient Cities |
  |---|---|---|
  | Coal | Half of vanilla's | Rich |
  | Copper, iron | A quarter | Rich |
  | Gold, redstone, lapis | An eighth | Rich |
  | Diamond, emerald | None | Only there |
  | Netherite, crimson iron | None | Only around the Ancient Cities |

- **Backpacks.** Stack upgrades go up to tier 3.
- **Animals added twice.** Each pair from Naturalist and Wilder Nature, and the crab from Friends and Foes as well, is set side by side in the workshop (`tooling.md`, T1), and the one that looks and behaves better stays, with what it drops counting, since hunting is now a main source of food.
- **Structures.** About one place of a kind every so many blocks:

  | Owner | Kind | About one every |
  |---|---|---|
  | The Twelve Peoples | Their own places | A handful per people, set near their hall by the generator |
  | The Gazebound | Outposts and camps | 2,048 blocks |
  | | Forts and mansions | 8,192 blocks |
  | The settled world | Villages | 1,024 blocks |
  | | Towns | 4,096 blocks |
  | | Capitals | 16,384 blocks, a few in the world |
  | | Ruins of burnt towns | 512 blocks |
  | Wayfarers and travellers | Campsites | 512 blocks |
  | The first civilisation | Ancient Cities | As rare as vanilla's, deep |

  Every one of the pack's roughly 600 structure kinds gets its owner or is cut, in a table like `generator/removals.toml`, by an agent in phase 3.
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

  ```text
  Journal › The story so far
    Kai is gone. He walked into the frame and the dark closed behind him.
    I carry his ember now. I'm going to build us a home.
  ```

**Why:** A dragon that fights with the sight it was given makes every shard left behind count in the last fight. Gone has to mean gone, or Pip's death would cost nothing, and watching the world as a spectator is the one thing the closed Eye no longer does. The same journal in a new voice carries the merciful door into what comes after.

**The spike works** (`mod/src/dev/java/io/github/roman16/wayfarer/spike/EndingSpike.java` and `EndingClient.java`, dev code that never ships, with its numbers in `balance.toml` `[dragon]`): set to 12 shards with two players online, the dragon rose with 768 health and every crystal stood; set to 3, it had 480, three crystals stood, its fireballs flew about 6 blocks wide of the player where at 12 most flew 0.1 wide, its charges went 5 to 12 blocks wide, and it lost the player when a pillar stood between them. A world marked gone shows every player a black screen with the ending's lines and "Start a new road", which Escape does not close; the button leaves for the title screen, and opening the world again shows the ending again and nothing else, the save untouched on disk. Playing on as Pip changed the player's skin, seen from behind, added "I carry his ember now" to the journal after the earlier entries, and from then on wrote a quest's journal entry in Pip's words. What it showed:

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
| Shrines and shards (`Shrines`, `Shards`, the pedestal, the halls) | Twelve halls in two distance tiers, a shard on each pedestal | Kept, changed: the pedestal is the calling stone, the halls have their fates (S6), the two tiers become the three acts' rings, and the Diggers' hall is always nearest the Last Camp; halls stay free of spawns, now as hallowed ground |
| The compass (`Compass`) | Points at shards, friends and the grave | Kept, changed: the tracked quest's goal as the player knows it (D6), friends and stones (M2) |
| Leads (`Leads`) | Places someone told the player of | Folded into the quest engine: a lead is a goal someone told of (S7) |
| The fire (`Fire`, `Warm`) | The circle keeps monsters off, resting warms the ember, the voice speaks at rest | Kept, changed: the circle and the warming stay, and the voice's long talks and the night's stories play at the caravan's fire (S4, M12) |
| Hunger and rowing | The ember eats double; steering a boat costs food | Kept |
| Nights (`Nights`) | A Sleepless Night every fourth night, a red moon every fifth | Changed: scripted, as the story's and the caravan's events (M6) |
| Daylight (`Daylight`) | The gaze's creatures crumble in the sun | Kept |
| Stillness (`Stillness`) | Staying in one place dims the ember, then brings the horde | Kept, changed: counts nights slept (M6) |
| Tents (`Tents`) | No spawns inside; thrown out on a Sleepless Night | Kept, changed: one-step tents, sleep by a fire (M12) |
| The sky (`Gaze`) | In the air phantoms hunt the player, held off longer by the Ember Veil | Kept: the sky stays dangerous, Sky-veil is a shard's gift, and the Ember Veil stays as gear (M18) |
| Unlocks (`Rules`) | Ships at four shards, aircraft and gliding at six | Changed: they belong to quests (D15) |
| Sight (`Sight`) | Monsters seen through stone at seven shards | Changed: the clock's second stage, at the Jungle Folk's hall (S12) |
| Difficulty (`Difficulty`) | Improved Mobs harder per shard and per day | Changed: follows the shine (M9) |
| Graves (`Graves`) | The grave, marked on the compass | Changed: stones (M2) |
| Hordes in halls (`HordeRefuge`) | A horde passes over someone sheltering in a hall | Kept |
| The frame (`Gate`) | Takes all twelve shards, refuses while Gazebound stand in its hall | Kept, changed: takes any number (D10) and opens the four doors (S8) |
| Maren (`Maren`) | Visits the fire and leaves food | Kept, changed: her part in S10 |
| Story, books, notes and journal (`Story`, `Library`, `Notes`, `Journal`) | The voice's lines, the books, the notes, the FTB Quests book | Replaced by the quest engine and the journal (M1, M2); the books stay as content |
| Teams (`Teams`) | FTB Teams | Replaced: everyone in the world is one team (D11) |
| Camps (`Camps`) | The Last Camp and Wend's first and last camps | Kept: the prologue's camp and the memories' places; the camp generator also builds the caravan's pieces (M5) |
| Structure spread and the ring (`Spread`, `ExactRingPlacement`, the world-generation mixins) | Spreads structures 8.5 times apart and places the ring of frames | Changed: frequencies by owner (M13); the ring stays |
| Loot (`Loot`) | Fills Lootr chests near players | Kept |
| Hidden pop-ups (the toast mixin) | Hides vanilla's advancement pop-ups | Kept (D7) |
| The showroom and the driver (dev only) | The showroom and `scripts/client.py` | Kept, as tooling (T1, T2), the showroom opened by `just showroom` |
| Friendly fire | Vanilla | Off: the team cannot hurt each other |
| Village raids | Bad Omen after killing a raid captain | Kept as the Gazebound's revenge: kill a captain, and they raid the next village the player visits |
| The wandering trader | Vanilla | One of the road's people (D12), a scene that comes back (M4) |
| Phantoms from sleeplessness | Vanilla | Off: the player sleeps every night anyway, and phantoms come only to the sky |
| Weather, fishing, spawners, the End | Vanilla | Kept: storms are a daylight danger (M6), fishing is food, spawners are dangers in places, and the End is the finale |

**Why:** Each part either carries the design as it now stands, is turned to it, or goes, so nothing survives for having been built, and every vanilla system left has a part in the road.

### M16 Balance: start, measure, tune

- Each system starts its numbers in `balance.toml`, as powers of two (AGENTS.md). What a tool can measure gets one, the way `just survey` measures the shrines' legs: how long a day on the road runs, how often a road scene comes, how bright the shine grows. What only a player can judge goes into the `playtest` skill's checklist for the Act 1 slice.
- No side of a hard decision is a trap: each side's reward shows within the same act, and its cost no later than the next. The starting point to play and tune:

  | Decision | Taking the shard | Leaving it |
  |---|---|---|
  | Nell's shard (1.8) | Dark-sight and a step of shine; Nell stays but hates the player, which counts at the breaking | No gift and no shine; Nell leaves in peace and may fight beside the player at the gate (F1) |
  | Tamsin's bell (1.10) | Warmth and a step of shine, and the gaze finds the caravan less often; Tamsin never forgives it, and some families leave | The bell's toll keeps the lesser creatures off the camp, and the townsfolk trust the player; the gaze finds the caravan more often |
  | The Tide Folk's two shards (2.3) | Up to two gifts, Deep-sight and Stare, and two steps of shine; their boats are seen and they starve, unless Night Fishing (T2) saves them | Their trust, which counts towards Keep the oath, and their bay stays dark even in Give it its sight |
  | The worm (2.6) | Far-sight and a step of shine; the Sand Singers lose their protector, and the raiders come (T3) | No gift; the Sand Singers trust the player, and the worm stays in the desert |
  | The Grass Kingdom's ward (3.4) | Sky-veil, which makes flight safe, and a step of shine; the walls fall unless the player holds them, and the heir has his land back | The kingdom stands and trusts the player, which counts towards Keep the oath; the heir looks for another way in |
  | The shard or the children (3.5) | Night-walk and a step of shine; the children stay captive | No gift; the children join the caravan and help at the gate (F1) |

**Why:** Numbers that start from a reason and are then measured are how the pack already tunes its shrines, and leave the player only what a tool cannot judge. A decision is hard only if both ways cost something the player feels soon enough to remember why.

### M18 Gear and its road

- Silent Gear makes all made gear: tools, weapons and armour from parts, each material with its own traits, graded, salvaged and alloyed. The peoples' own metals are added to it as new materials, which its data files allow without code. Simply Swords' unique weapons stay as rare finds and masterworks, and Artifacts' trinkets as the peoples' gifts and finds.
- Gadgets are what the player carries and travels with, tents and backpacks among them, and they climb tiers too: tents from tiny to mega with their inner layers (Nomadic Tents' sizes and shovels), backpacks from copper up with their upgrades, hooks, gliders, ships and aircraft.
- Each tier's material comes from a place on the road (ground truth 3, M13), and the top of every kind can be reached before the story ends, though not from the start (every name a placeholder):

  | Act | At the player's own bench | Forged by a smith | Gadgets |
  |---|---|---|---|
  | 1 | Flint, copper, iron | Frost iron, from the Frost Walkers' old mine | Copper and iron hooks and backpacks, small and medium tents, the hang glider |
  | 2 | Bort and diamond, from the deep places | Tide steel, Sand Singer glass-bronze | The diamond hook and backpack, large tents, ships |
  | 3 | | Crimson steel from the deep stone around the Ancient Cities, then the top tier from a far metal, forged by the Keepers' smiths or at the far ring | The top hook, backpack and tent, airships |

- A smith's tier is forged overnight: the work is left at a forge in the evening and collected at dawn. The player's own bench stays instant.

  ```text
  Hob takes the frost iron and your old blade. "Leave it with me tonight."
  Morning: a sword the colour of river ice lies on the anvil.
  ```

- The journal's Gear page shows the road: every tier of every kind, what it needs, where that is found and who forges it, filled in as the player learns it (D6). A few smiths' commissions are side quests with turns of their own, such as Hob's masterwork or a people's blade.

**Why:** Silent Gear is already the deepest gear system for 1.21.1, and Tinkers' Construct has not reached it; its materials as data let the peoples' metals join it. Tiers tied to places send the player down the road for better gear, an overnight forge makes the smiths matter without a waiting bar, and a page instead of "have item X" quests keeps "No quest is a chore". The top within the story lets a player who works for it face the end at their best.

**The spike works** (`mod/src/dev/java/io/github/roman16/wayfarer/spike/GearSpike.java` and `SilentGearSword.java`, dev code that never ships, with frost iron as data in `mod/src/dev/resources/data/wayfarer/silentgear_materials/frost_iron.json` and the Gear page in `mod/src/dev/resources/wayfarer/spike/gear.json`): frost iron is a Silent Gear material written only as data, pale blue, harder-wearing than iron and with the Chilled trait, made from a stand-in ingot that looks like iron. A player who leaves two frost iron at an anvil hears "Leave it with me tonight"; half a minute later nothing is there, and at the next dawn a Silent Gear sword with a frost iron blade and an oak handle lies on the anvil. The journal's Gear page lists iron as known from the start and frost iron as "needs ?, from ?, made by ?", fills in "frost iron, from the Frost Walkers' old mine" once a player carries some, and "a smith, overnight" once one has left it with a smith. What it showed:

| Finding | For the engine |
|---|---|
| Silent Gear reads materials from any data pack's `silentgear_materials` folder and builds parts and gear from them through its own API | The peoples' metals are data files, each with an ingredient item the Wayfarer mod adds; the forge builds the finished gear through Silent Gear's API |
| A material file missing one key, `part_substitutes`, stopped the world's data packs from loading, and the world did not open | `just lint` checks every material file against Silent Gear's format before any launch |
| A dawn is a day boundary of the overworld's clock, whether the night passes, is slept or is skipped | An order finishes when the overworld's day count moves past the day it was left on |
| What the player learns comes from things they do, carrying a metal or leaving it with a smith | Each tier on the Gear page names the events that fill in its fields, as the journal's other entries do |
| An item lying on the anvil can be picked up by anyone | Not settled: whether only the player who left the work can take the finished piece |

### M19 A camp in plain sight

- When evening begins the compass points at the camp's spot at once, and the camp goes up the first moment no player can see the spot: the land or a wood stands between, or every player has turned away from it.
- If the team walks straight at it across open land and never looks away, it goes up anyway just before the spot comes within the view distance, and a few tents may appear once in the far view.

  ```text
  Evening  [Main] Make for the caravan.   Compass ◆ 420 blocks
           You turn to watch a horse on the ridge. Behind you, far off, the caravan makes camp.
           You turn back: smoke, and a few tents on the plain.
  ```

**Why:** Distant Horizons shows the camp as soon as the server saves its chunks, on a server as in singleplayer, so a camp that waits for a turned back goes up unseen almost every evening, and one that never waits past the edge never leaves the team walking towards nothing. A few tents appearing far off once is what the lore already promises: in the evening their smoke shows me where.

### M20 Heading in early

- From 17:00, heading in starts the run home: the clock eases to 22:00 and monsters can come out 1:15 later (M6).
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
| Resting is the pack's own: still, within 4 blocks of a lit fire, for 30 seconds (`[fire]`), so turning in early costs half a minute at the fire before the sky turns | Sitting on the fire's logs (`mods.md`) can be that rest |
| Fast-forwarding the server (`/tick sprint`) leaves a client's clock to catch up by hundreds of ticks at once | A sprint measures the server's clock; the sky is judged in real time |

### M21 Old campsites: the caravan's camps stay

- When the caravan's camp is taken down, its traces stay for good, as the camp left them: the fire pit with its campfire out and its ring of stones, the paths worn to it, trodden patches where the tents stood, and now and then something left behind, such as a split log or a broken crate. A team that comes back the same way, days or an act later, finds the camp where it stood.

  ```text
  A ring of stones round a cold pit, paths worn through the grass, a split log by the path. The caravan camped here.
  ```

- This is the caravan's camp only. A camp the player makes on their own out on the road is theirs, and stays or goes as they leave it.

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

**The spike works** (`mod/src/dev/java/io/github/roman16/wayfarer/spike/SeaSpike.java`, dev code that never ships, with its numbers in `balance.toml` `[sea]`): a player in a boat 64 blocks from the nearest land is on open water and one by the coast is not; there the beast, for now a guardian four times its size, comes one check in 32 (every 8 s) at night or in rain, one check in 12 at the shine of act 3 with every shard, and only while none is near. It rose on its own under a player's boat in daytime rain, broke a vanilla boat with its first ram, rammed a Small Ships cog every 2 s from 8 to 344 damage until it sank after about 75 s with nobody fighting back, and dived when dawn came. What it showed:

| Finding | For the engine |
|---|---|
| Open water is read from the heights of the loaded chunks around the player, every 8 blocks out to 64, and land in any of them ends it | The rule needs no world data of its own; a coast in an unloaded chunk counts as sea, which the view distance makes rare |
| Small Ships' ships are boats to the game, with the hull's health in their own config (a cog has 300) | A ship's damage from the beast is tuned against its hull: at 8 every 2 s a cog lasts about 75 s against a beast nobody fights, which is judged in play |
| Riding a Small Ships ship is refused before 4 shards by the mod as it ships (`Rules`, `[unlocks]`) | The tests stood the player on the deck; ships open with Act 2's quest (D15) |
| `/weather rain` takes some seconds to fade in, and only then does the game count it as raining | The beast follows the rain the world shows, not the forecast |
| Improved Mobs 1.16.0.b crashed the server when a drowned swam at night: its pathfinding reads a search's context outside any search (`SwimNodeEvaluator`). It is the newest build | The Wayfarer mod gives that call a context of its own (`ImprovedMobsPathContextMixin`); a hundred drowned round a boat at night then ran without a crash. Reported upstream as [ImprovedMobs#364](https://github.com/Flemmli97/ImprovedMobs/issues/364); the mixin goes once a fixed build is in the pack |
| The beast's look is a stand-in | It is made with the art skill, as the worm was (T3) |

### M23 A stone of one's own

- Every player carries one waystone of their own. They set it down where they leave the road, a quest left halfway or a camp to come back to, and touch it. From any waystone they have touched since, they can fold back to it, and arriving takes it up into their pack again, so nothing of theirs stays standing. Crouching and taking it up by hand works too, to set it down somewhere else.

  ```text
  You set your stone down at the edge of the marsh and touch it.
  Three days later, from a village waystone far to the east, you fold back. The stone is in your pack again.
  ```

- Every waystone a player has touched, their own stone among them, shows on the world map with its name, and the map can show or hide them.
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

### M17 Distant Horizons SeedGen

- The far view is drawn by Distant Horizons SeedGen, on the client and on the server: the far land's hills, coasts, rivers and trees come from the world's seed, so the horizon fills in fast wherever the team goes.
- SeedGen draws no buildings. A town, a temple or a shrine shows in the far view only once the land around it has really been made, as it does in vanilla, so nothing on the horizon is a place that is not there.

  ```text
  You crest the ridge. Hills roll out to the edge of sight, a river shining between them.
  Nothing tells you yet that a village waits behind the third hill.
  ```

**Why:** The far view is what makes the world feel vast, and SeedGen fills it fast with land that matches the real one. Its own copies of vanilla buildings ignore the pack's spread and would show temples that vanish on approach, which a pack whose places are quest goals cannot afford.

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

### M24 Roads: worn tracks, no built roads

- The land stays wild: the world has no built roads between its places. The paths are what feet make: tracks wear into grass, dirt and sand where people and animals walk often (The Roads More Travelled), and the caravan's old camps stay (M21). The lore's old roads are such tracks, worn between villages by the settled world.

  ```text
  No road runs here. A trodden line through the grass shows where others walked before you.
  ```

- Built roads come back as a question if playing Act 1 shows the land hard to cross or empty.

**Why:** The pack's look is a vast, wild land crossed by a team that finds its own way, and a road between every two villages would draw the walk onto the settled world's lines. RoadWeaver's own page says it does not work with Tectonic 3, which the pack runs, and worn tracks give the old roads the lore needs without either.

### M25 Seasons: set by the story

- Serene Seasons colours the land, sets the cold and the heat through Tough As Nails and brings snow to temperate lands in winter, but while the story runs its cycle stands still and the quest engine turns the season: Act 1 runs from late summer into early autumn, Act 2 through mid and late autumn with its storms at sea, Act 3 through early and mid winter, and the frame stands in late winter. Once the story has ended, the cycle runs on its own, as the day does (M6).

  ```text
  The leaves turn as you leave the Frost Walkers' land.
  By the sea, the first autumn storm.
  ```

- Each act's season is part of its look and its gates: the cold is a tool the story uses, and a quest written for summer never meets a winter it did not expect.

**Why:** The story spans perhaps fifty to eighty days, too few for a calendar to give every act its own weather, and a random winter would break quests written for another season; Red Dead Redemption 2 sets its seasons by chapter in the same way, a snowy prologue and then summer. Serene Seasons stops its cycle with a game rule and sets the season by command, so the engine can turn it.

## Open

Nothing is open.

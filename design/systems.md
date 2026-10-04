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
- **The frameworks beside it:** ArcQuest keeps one live quest state for each online player and its licence (All Rights Reserved) forbids changing that, so it cannot hold one state for the whole world and stays out. HollowEngine (CC-BY-NC-SA 4.0) loads with the pack's NeoForge and brings people animated from Blockbench or glTF models and keyframed cutscenes made in its in-game editor, at 33 MB; it is weighed again at the camp (M5) and memory (M8) spikes against Easy NPC's poses and walking.

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

### M6 The day and the night: a scripted clock

- While the story runs, the quest engine drives the clock (D2); once it has ended, the day and the night run as in vanilla. Minecraft's clock runs from 0 at 6:00 to 24,000, with noon at 6,000.
- The clock eases out: its speed is always normal speed times the gap left to its target over the gap it started with, so it starts at normal speed, slows the whole way and never arrives. On the road it eases from the morning towards a low-sun evening at 11,500, about 17:30, and holds there. Once "make for the caravan" is tracked and most of the team is within 256 blocks of the camp, it eases towards 22:00, timed so monsters come out about 1:15 later: sprinting (about 5.6 blocks a second) the team is in 30 seconds before them, walking (about 4.3) 16 seconds before, and a minute wasted on the way meets them. A quest that keeps the player out sets the clock itself, and a hard night does not ease out at 22:00 but runs on to dawn.

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

It raises, for the spike: the clock easing out on a server with several players; its targets and speeds in `balance.toml`, tuned so the dusk run meets its 1:15, since easing from normal speed alone would take about 1:47 to the hour monsters come out, which is about 13,200 from memory and not checked; and the pack's per-day difficulty taken out.

### M7 Joining later

- Who appears where, and with what, is D11's table. A tent or a mount counts as far beyond 256 blocks: then it comes back to the player who joins or returns, and one closer stays where it is.
- A hard decision counts the players online when its vote starts: it needs two thirds of them. A player who joins later may still vote, and one who leaves keeps the vote they cast.

  ```text
  <Nell> Give it to me. I'll bury it with them.
    [Give it to her 1/3]  [Keep the shard 1/3]   (needs 2 of 3)
  Mo joined the road.   (Mo can vote; it still needs 2)
  ```

**Why:** A count fixed when the vote starts means nobody can change a decision by logging off, while a friend who arrives late still has their say.

It raises, for the spike: a second player joining a world in progress appears next to the host with the team's shards and an unclaimed reward, and their tent from 384 blocks away comes back with a full chest inside; a returning player logging in far from the team appears next to them. Nomadic Tents keeps a tent's inside under its ID, which makes the recall possible; found in its classes, not tried.

### M8 The memories: a dream world of their own

- A memory happens in a small dream world of its own, as the tents' insides do. The generator builds each memory's place as it was then, the shard lit on its pedestal, the Frost Walkers on the stair, the grass halls young, and the player is taken in and brought back to the same spot. While they dream they are gone from the real world, so nothing can hurt them.
- Everyone within about 32 blocks of the stone dreams it together, every one of them as Wend. A player who was not there can dream it again later from the journal, at a fire.

  ```text
  Kai touches the calling stone. The ember flares, in all of you.
  Twenty winters ago. Three of you stand on the stair, and every one of you has Wend's hands.
  ```

- Falling in a memory wakes the player into it again, from its beginning or its last step, and costs nothing in the real world; Wend lived through it.

**Why:** Every place a memory needs is the pack's own, so the generator can build it as it was, and a world apart leaves the real one untouched for friends who stay in it (D11). Dreaming together keeps nobody watching, and replaying from the journal means nobody misses Wend's life for being away.

It raises, for the spike: a dream world entered from a calling stone by two players together, a scene built by the generator, a fall that restarts it, and the return to the same spot.

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

It raises, for the spike: the shine driving Improved Mobs' difficulty, which the Wayfarer mod already raises per shard today (`balance.toml` `per_eye`), the spawns around the player and how far creatures notice them; and its steps in `balance.toml`.

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

It raises, for the spike: the ember sense showing a clue and a trail laid ahead, and a hold against The Hordes' waves with a fold to keep standing.

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

It raises, for the spike: Tough As Nails with thirst off and the gifts as its warmth; one GeckoLib creature; Attract to Sound against the Gazebound, with its own raids set aside so they do not fight The Hordes and the hard nights (M6), and its voice-chat hearing tried with Simple Voice Chat; Easy NPC walking to follow and to flee.

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
  | Tool | A lesser pick breaks it ten times slower and wears down to its last use | A pick of the right material |
  | Travel: sea | A vanilla boat can cross, but on open water the ember shines like a beacon, and the sea's creatures and storms come | Ships, in Act 2 |
  | Travel: sky | The hang glider flies, but in the air the phantoms see the player | Flight in Act 3, and Sky-veil |
  | Knowledge | No way through: these are the real walls | The word, the map, the story |

- The gifts are passive, except the Flare, which is on a key with a cooldown. In a team everyone has the gifts of the shards the team took (D11):

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
  | The Flare | A burst of the ember's light that crumbles the gaze's creatures around the player | Breaking a horde |
  | Hush | While the player crouches, their heart's light sinks like a dead man's, and the gaze's creatures lose them | Hiding |

**Why:** A ward closes every way around a seal, which unbreakable blocks alone would not, and it has its reason in the lore already. A price for every soft gate lets a bold player go early without making the key pointless. Passive gifts are felt without being managed, and the one gift worth aiming and timing gets a key.

It raises, for the spike: a ward that holds against breaking, placing and explosions; one gift of each kind, passive and keyed; and each price's numbers in `balance.toml`.

### M12 The camp and the gear

- Nomadic Tents is changed so that placing a tent builds the whole tent at once, without a frame or a mallet; packing it up is one click. Its own `enter_when_safe` and `pickup_when_safe` are switched on, so nobody enters or packs up a tent while monsters are near (D13). It stays the pack's tent because it is the one whose inside is a folded space: Let's Do Camping and Simply Tents pitch an ordinary tent in the world, and both are All Rights Reserved.
- A bed inside a tent sleeps only when a lit campfire burns within 4 blocks of the tent outside (D13).
- The fire's stories stay. If a night has one, the voice's long talk, a person's scene or Maren's visit, it plays once most of the team sits at the caravan's fire, and sleep waits until it is over; a night without one goes straight to sleep. Resting half a minute at a fire still warms the ember, so the player eats like a settled person until the next dusk.
- Every tool, weapon and armour, Silent Gear's, Simply Swords', vanilla's and every other mod's, stops at its last use instead of breaking (D13); Silent Gear already keeps broken gear (`breaks_permanently = false`), and the Wayfarer mod brings the rest to the same rule.
- At a fire, a repair kit and the tool's own material mend it back to half its durability at most, never beyond. A smith restores it fully: the peoples' smiths ask for goods, and Hob does it for nothing once his side quest has found the old ore (C7).

  ```text
  At the fire you bind the cracked pick with flint and cord. It will hold: half its strength, no more.
  Hob turns it over. "Leave it with me tonight."
  ```

**Why:** The tent the pack needs is the folded space it already has, and its safety rules are already written into it, so only the mallet goes. The evening is where the story is told, and the sleep rule keeps anyone from sleeping through it. Mending to half keeps the road going while a full repair stays something to reach a smith for.

It raises, for the spike: one-step placing in Nomadic Tents, the campfire within 4 blocks for sleep, and the last-use rule on a vanilla tool and a Simply Swords weapon.

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

It raises, for the spike: a dragon whose health, crystals and aim follow a number, an ended world that only shows its ending, and a player's skin and journal voice changed.

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

It raises, for the spike: a new Silent Gear material from data, a forge that finishes at dawn, and the Gear page filling in.

### M19 A camp in plain sight

- When evening begins the compass points at the camp's spot at once, and the camp goes up the first moment no player can see the spot: the land or a wood stands between, or every player has turned away from it.
- If the team walks straight at it across open land and never looks away, it goes up anyway just before the spot comes within the view distance, and a few tents may appear once in the far view.

  ```text
  Evening  [Main] Make for the caravan.   Compass ◆ 420 blocks
           You turn to watch a horse on the ridge. Behind you, far off, the caravan makes camp.
           You turn back: smoke, and a few tents on the plain.
  ```

**Why:** Distant Horizons shows the camp as soon as the server saves its chunks, on a server as in singleplayer, so a camp that waits for a turned back goes up unseen almost every evening, and one that never waits past the edge never leaves the team walking towards nothing. A few tents appearing far off once is what the lore already promises: in the evening their smoke shows me where.

## Open

### M17 Distant Horizons SeedGen

Distant Horizons SeedGen draws Distant Horizons' far terrain from the seed instead of generating it, five to ten times faster in its own tests; it is needed on the client and optional on the server, where the server's world-generation mods must be on the client too. It fakes trees and places structures its own way, while the pack changes world generation a lot (the shrine ring, the structure spread, the removals, the Last Sea), so a test world checks that the far view matches what is really there. If it goes in, the pack ships it switched on, since it is thought to be off by default (not checked).

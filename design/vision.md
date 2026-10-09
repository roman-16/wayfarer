# Vision

What Wayfarer is, what the player does every minute, hour and act, and who they turn out to be. Every later decision is checked against this document.

## In two sentences

Wayfarer is a journey across a vast, sparse world to bring back the dragon's broken Eye. You carry a dead man's ember and the voice inside it, which turns out to be the dragon's, until the end asks whom you believe and what you are.

Each hour you walk from camp to camp on a road that keeps offering something (people, scenes, weather, hard nights) and follow a quest that has you track, fight, talk or choose. Every choice comes back later.

## Where the pack stands

The pack ships as a journey to twelve eye shards, and it bores:

- **One verb, twelve times.** The only goal is the next shard, reached the same way each time: follow the ember for about 19 minutes on foot (a 5,000 block leg, tier one) or about 31 (8,000 blocks, tier two), take a stair down, take the shard, read two pages. About five hours of straight-line walking in all.
- **The road asks nothing.** Nothing between two shrines needs anything from the player, so better gear has nothing to answer.
- **The quest book is a checklist.** 52 of its 63 quests are "have item X", and most pay XP levels.
- **The story waits at the end of the walk.** It comes as book pages at the pedestal and one talk a day at the fire.

## Ground truths

What every decision here and in later phases has to respect.

1. **Fix the play, not the text.** Story laid on the same loop stays boring. Story is told by turning it into something the player does.
2. **Sparse world, dense road.** Vast land and rare, wide places are the pack's look, so variety travels with the player: encounters on the road, terrain that is an obstacle, nights and weather, a voice that walks along.
3. **A gate sends you somewhere.** Whatever opens a way comes from another place on the map, never from grinding in one spot, which the ember's stillness rule punishes anyway.
4. **Story and play decide each other.** Every mechanic needs a reason in the lore, and every story beat needs something to do.
5. **One to four players, and alone as often as not.** About half the players play alone, so everything is built for one player first and works the same for a team of up to four: a team shares one ember, singleplayer behaves like a server, and every quest, fight, vote and twist works for one as well as for four.
6. **What ships is a toolbox.** The ember, nights, stillness, fire, tents, the compass, the shrines and the generator each get a verdict in phase 3; none is kept for having been built.
7. **Fun is found in play.** Act 1 is built and played before the other acts are written in detail.
8. **Minecraft is the base plate.** The pack is designed the way The Witcher, GTA or Cyberpunk would design it, not the way vanilla plays: anything missing is added with a mod found or written, a mod that does not fit is changed or removed, and anything in the way is taken out of vanilla or the mods, from caves to the endless world. What vanilla cannot do only says what a thing costs to build, never whether it is in.

## Settled

### D1 Pillars

The five rules every decision is checked against:

| Pillar | Fits | Does not fit |
|---|---|---|
| **The road is the game.** What happens on the way matters as much as where it leads | A scene waiting past the next hill; the hall that turns the quest | A 5,000-block leg with nothing on it; a base to go home to |
| **Stay a while, never long.** A quest may keep the player at a place, and the road always takes them on | Three days at the Salt Folk's winter camp | The wheat farm, the trading hall |
| **No quest is a chore.** Every quest is fun to play, and a big twist comes only where it matters | Chasing down the herder's lost dogs; the empty shard hall | "Bring 16 iron ingots" |
| **Every choice comes back.** What the player chooses shows up again later. About half the choices are hard: the right thing against the easy one, or two right things against each other. The other half have a plain right answer. The big decisions, the shards that can be left and the four doors, are always hard | Leaving the shard in the cradle; helping the herder, who then shares his fire | A choice that changes nothing |
| **You are not who you think you are.** The world knows what the player is before they do | The Enderman that does not flinch; the elder who bows | A prophecy that tells the player who they are on the first day |

**Why:** Each pillar grows from a settled decision (the road and the stillness from D2, the quests and choices from D10, who you are from D5 and D9), and together they let a decision be made or refused quickly, the way God of War's three pillars shaped even its smallest side quests. Hard choices stay heavy because they are not everywhere, and the easy ones still show who the player is and teach that the world remembers.

It raises, for phase 3: how the engine remembers every choice and brings it back, in a scene, at a fire or in the ending.

### D2 The loops: a day out from the caravan

A typical day starts and ends at the caravan (D14), but it is the typical day, not every day: a side quest can take the player away on their own for days, and the story can change the whole rhythm, down to the caravan dying halfway and the player going on alone. In the morning the caravan packs up and the player sets out on the day's quests. While the story runs, the day and the night are scripted: the story decides when night falls, and when a hard night or a red moon comes. Once the story has ended, whatever the door, the day and the night run as in vanilla. The sun moves the whole time but never runs out:

| Stretch | The clock |
|---|---|
| Out on the road | Eases out from the morning: it starts at normal speed and runs slower and slower towards an evening with the sun low but still bright, which it never quite reaches |
| Making for the caravan | From 17:00, once "make for the caravan" is tracked and most of the team is within 256 blocks of the camp, it eases out the same way towards about 22:00: dark, with monsters out beyond the fire's circle. Monsters come out about 1:15 later, so the team is in by dusk if it heads straight in, and meets them on the way if it dawdles. Earlier, the team walks in by daylight, and the afternoon passes at the caravan's fire (`systems.md`, M20) |
| Asleep | The next morning |

The evening at the caravan is talk, the people's stories, whatever the camp needs, and then sleep. The team shares one sky, so "most of the team" is the same two thirds that sleep needs, and a quest that keeps the player out for a night sets the clock itself. Quests change the shape whenever they need to. One keeps the player at a place for a while, never long, because a Wayfarer still has to walk on; another keeps them out for a night in the wild.

Hard nights come with the story and the caravan, never with the calendar. A Sleepless Night is the gaze finding the caravan: its horde comes to the caravan's fire and the player holds it, when a quest calls for it, when the caravan stays too long in one place, or when the player shines bright enough to be found (M6). On a hard night the clock does not ease out at 22:00 but runs on to dawn, so the night has to be survived. The red moon is a turn the story uses, once an act, on its worst night (`story.md`, S25).

| Loop | What the player does |
|---|---|
| Minute | Sees something to focus on about every 40 seconds, most of it travelling with them: wildlife and monsters, weather, the skyline, the voice. About every five minutes a written scene waits out of sight ahead |
| Day | Sets out from the caravan, walks the day's quests, makes for the caravan when they are done or whenever the team chooses, and spends the night at its fire. The road's scenes make each day its own, and quests bend its shape |
| Act | A ring farther from the Last Camp, opened by a new way to travel, with the gear its land asks for and harder nights, and closed by a revelation at a fire |

A typical day (every name a placeholder):

```text
Morning  The caravan is packing. Old Tamsin: "Go on. We'll follow your marks."
         [Main] The Salt Road - the Salt Folk's tracks, east of the dry lake
Road     A wolf pack shadows you along the ridge. Thunder rolls in from the west
         <Wend> Storm's coming in off the flats. Walk faster, or don't, I'm not your mother.
         A Gazebound patrol is questioning a herder: step in, hide, or walk on
         The tracks climb a basalt cliff: the hook, or the long way round
Evening  [Main] Make for the caravan.   Compass ◆ 640 blocks
         The wagons are drawn up by a stream. The herder you helped sits at the fire with the children
Night    Stories at the fire, then the tent
```

**Why:** Minecraft's day is the beat the pack's fires, tents and stillness already live on, and a day that runs from the caravan's morning to its fire keeps that beat while every night lands where the people and the stories are; a clock that eases out instead of stopping keeps the sky moving, so no one sees time freeze; nobody fights a long night alone in the wild unless a quest asks it, and a hard night that comes with the story lands as a climax instead of a chore on a calendar. The minute follows The Witcher 3's rule of 40 seconds with what travels with the player, as ground truth 2 asks, and acts follow distance because it is the one structure every random world shares, as Valheim's biome bands do on every seed.

The clock's curve, what counts a day, daylight's dangers and staying at a place are settled in `systems.md` (M6).

### D3 Main quests: as long as the story needs, played many ways

- A shard takes as many main quests as its story needs: one for some, a chain of several for others. Some main quests carry no shard and tell the story between them: Wend's past, Maren, the Gazebound, the two reveals.

  ```text
  The Salt Folk's shard   4 quests: the empty hall, the caravan's trail, the winter camp, the cradle
  The sealed hall         1 quest: find the word, open the seal
  Wend's first camp       no shard: who he was when he was young
  ```

- Main and side quests are built from 22 activities in six groups:

  | Group | Activity | Like | In Wayfarer |
  |---|---|---|---|
  | Find out | Investigate | The Witcher's senses, Cyberpunk's braindance | In the empty hall the ember plays the shard's last memory: who took it, and which way they went |
  | | Track | Red Dead Redemption 2's hunting | Following the caravan's hoofprints and cold fires |
  | | Solve | | A seal that opens to a word from another people's book |
  | | Talk | The Witcher's and Cyberpunk's conversations | Talking the elder round, or lying to a Gazebound captain |
  | Get in | Sneak | Cyberpunk's stealth | Slipping into a Gazebound hall at night |
  | | Disguise | Hitman | Gazebound robes their own people do not look at twice |
  | | Heist | GTA V's heists | The theft from the grass halls: quiet through the cellars or loud over the wall, each player of a team in a role |
  | | Raid | Far Cry's outposts | Fighting into a camp |
  | Fight | Hunt | The Witcher's contracts | Reading up on the beast that sees, brewing what it hates, finding its lair |
  | | Duel | Red Dead Redemption 2's duels, The Witcher's fist fights | A Gazebound champion, one on one |
  | | Hold | | The herder's fold through a Sleepless Night |
  | | Chase | GTA's chases | A thief riding off with the shard |
  | Cross | Reach | Zelda's climbing | A summit, the sea floor, the deep dark |
  | | Survive | Death Stranding, The Long Dark | The frozen waste on three days' food |
  | | Carry | Death Stranding | The sick child to the healer; run, and she wakes |
  | | Escort | Red Dead Redemption 2's wagons | The caravan through the pass, alive |
  | | Sail | | Open water by the stars |
  | Make | Build | Minecraft | The bridge mended, the fallen stair raised |
  | | Dig | Minecraft's archaeology | The earth brushed off a swallowed hall |
  | Live | Earn | The Witcher's village quests | The Salt Folk seen through their winter |
  | | Play | Gwent, Red Dead Redemption 2's poker | The peoples' dice game at the fire |
  | | Tame | Red Dead Redemption 2's horse breaking | The horse that never sleeps |

- The activities come in waves: Act 1 brings a handful, and every act adds new ones together with its new way to travel.

**Why:** Letting each shard's story set its length puts a quick seal beside a four-quest hunt for a caravan, the way Cyberpunk 2077's 33 main missions differ in size. Many activities keep "No quest is a chore" true over a long road, and adding them act by act leaves something new to learn until the end, as Ghost of Tsushima does with the ghost's tools.

It raises:

- **Phase 3:** each activity's system, settled in `systems.md` (M10).

### D4 Gates: mostly danger, a few real walls

- Most gates are danger: land that is deadly or slow without its key, which a bold player can still push through early at a price. A few are real walls that no pick breaks and nothing builds past, and they carry the big moments.

  ```text
  The pass is white with frost. Without snowshoes you sink, and the cold bites.
    Push on at half your health, or go round by the valley: a day longer.
  The seal on the hall door does not crack under any pick. It opens to a word.
  ```

- Seven kinds of gate, each opened by something from another place:

  | Gate | What blocks the way | What opens it | Where the key comes from |
  |---|---|---|---|
  | Land | Cold, heat, deep water, darkness | Gear: snowshoes, a shade, flippers | A people's place, whose gifts are such keys today |
  | Strength | A fortress, a champion, a beast | Gear and skill | Better materials from farther out |
  | Knowledge | A seal, a hidden way | A word, a map, a story | Another people's book, someone met on the road |
  | Trust | A people's camp, a guarded valley | Their trust | Helping them, or someone who speaks for the player |
  | Tool | Stone a lesser pick does not break | A tool of a material from elsewhere | The deep dark's netherite and the like |
  | Travel | The sea, the sky | A ship, wings, an aircraft | An act's turn |
  | Shard's gift | What only the Eye's powers pass, such as a dark only the Eye sees through | A power the shard gives | A hall; a shard left behind means going on without its gift |

- In a team, knowledge and trust count for everyone, and every player carries their own gear.
- A potion or food that opens a land gate, such as water breathing or night vision, counts as its key: its ingredients come only from another place.

**Why:** Digging and building anywhere is part of what Minecraft is, so most gates work with that freedom: the land is the danger, as in Valheim, where nothing stops a player walking into the swamp on the first day except that it kills them. A shard's gift turns D10's choice to leave a shard into a power the player goes on without, the way each Divine Beast in Breath of the Wild gives its champion's ability.

It raises:

- **Phase 3:** real walls, the prices of soft gates and the gifts' powers, settled in `systems.md` (M11).

### D5 The twist: the passenger, and the Eye's own

- You wake at a dying stranger's fire with no name, and he gives you his ember.
- His voice walks with you. It talks on the road, not only at fires: about the land, the fight just won, the place you skipped. You grow to like him.

  ```text
  <Wend> Birches. Everything watches you from a birch wood.
  ```

- The world treats you strangely, and the clues pile up: Endermen do not flinch from your gaze, and the peoples' guardians bow to you.
- **First reveal:** the voice is the dragon, wearing Wend's voice.
- **Second reveal:** you are the thirteenth shard, grown into a person by the gaze to walk its Eye home. That is why the voice reaches you at all. The stranger knew, and gave you the ember anyway.
- Near the end the real Wend's last whisper surfaces, and the ending asks whom you believe and what you are.
- In a team, the story is one, shared by all (D11), and it speaks to "you": every player takes the twist as their own.

**Why:** The passenger turns the long legs into company, with the cheapest machinery there is: lines on triggers. The Eye's own gives the player a past and the shock, and explains why the voice can reach them. Both grow out of the ember and the voice at the fire.

The reveals' places are settled in `story.md` (S2), and the voice's in S4.

### D6 The quest log: the journal, the compass and the map

- The journal lists the main and side quests, each with its current objective. Each player chooses which quest they follow, if any, and the compass shows its goal as the player knows it:

  | How the player knows the goal | What the compass shows |
  |---|---|
  | By feeling: a hall, or anything only the ember senses | No mark and no distance from afar: the ember flares when the player walks towards it and gutters when they walk away, and held up and swept round it burns brighter the closer they face the goal; the exact mark within 256 blocks |
  | By rumour: "somewhere north-east" | A direction and a rough distance, and the exact mark within 256 blocks, once the place shows itself |
  | By being told: a person, a page or a sign named the place | The exact mark from the start, with its distance |

  ```text
  [Main] The Pull - the ember pulls somewhere
  Compass  The ember · flaring ... The ember · guttering
  Compass  ◆ 241 blocks

  [Side] Bram's Son - the outpost Bram named
  Compass  ◆ north-east, 1,812 blocks
  ```

- The tracked quest, its objective and its tasks, how to do what the step asks and each control the first time it is needed, stand in the task panel at the top right of the screen (`interface.md`, I1).

- A quest is followed by choice, as The Witcher 3, Cyberpunk 2077 and GTA V let the player pick up a job when they are ready. A new quest waits in the journal ("New in your journal"), and nothing of it happens until someone tracks it: its places are not put down, its people do not start talking, its holds and visits do not come and the voice keeps quiet. Tracking it ("Track" on its page) begins it; when it is done nothing is tracked, the task panel stands empty and the compass shows only friends, stones and, from 17:00, the caravan's smoke, until the team chooses the next. What has begun finishes, a talk, a hold or a dream, and "Stop tracking" puts the quest aside where it stands. What the team does always counts: a shard taken, a gift found or a clue clicked counts for its quest whether it is tracked or not, and right-clicking the person a step waits on starts the talk and tracks its quest. In a team a quest moves while anyone tracks it, and a teammate's tracked goal shows faintly on the others' compasses. The day is not a quest: the caravan's "Make for the caravan" still comes by itself at 17:00, so night always falls. The prologue, the first evening, follows itself, The Last Fire to Ash, since it teaches the journal; from The Pull on, the team chooses, and the first time nothing is tracked the task panel says once to open the journal and press Track.

  ```text
  Done: The Pull.        Task panel: (empty)
  New in your journal J: His First Camp
  ...the team builds, hunts, explores...
  Journal › His First Camp › [Track]    A new road: His First Camp
  ```

- What failing means depends on what went wrong:
  - Falling never fails a quest: the player wakes at their fire, and the quest waits where they left it.
  - When the story goes wrong (the herder dies, the Salt Folk get away), the quest turns into a worse version of itself.
  - When a challenge goes wrong (a duel lost, the fold overrun, the thief escaped), the player can try that step again. Only the quest's own people and places reset, never the world.
  - A main quest always leaves a way on.

  ```text
  You fall to the horde. You wake at your fire; the herder's fold still waits.
  The thief's horse outruns yours.  Step failed. Try again from the crossroads?
  The herder dies in the raid.      [Main] The Salt Road - his dogs follow you now
  ```

- Most quests wait for the player. A few carry a clock the journal shows, and when it runs out the quest turns.

  ```text
  [Side] The Fever - the child needs the healer before the third dawn   (2 days left)
  ```

- The journal remembers everything, and explains it the way the player would tell a friend: the story so far, every person met and what they did to the player, every word of the world from the gaze to the Gazebound, every book and every talk at the fire. Each entry is written plainly, from the player's own point of view, and holds only what the player knows so far: a word they have never heard is not in the journal, and an entry grows as they learn more, so the stranger stays the stranger until Maren names him. A player who has forgotten who someone is, or where the story stands, can look it up. The journal is the team's, shared by every player and always up to date (D11); its entries speak as "I", so every player reads them as their own.

  ```text
  Journal › People › Corin
    A deserter from the Gazebound. He served under Wend for six winters.
    He saved my life at the ford, and he still won't say what Wend made him do.
  ```

- A map shows the world the player has seen, from above, and the places they have found, opened from the journal; it needs no item. Quest goals stay on the compass (`systems.md`, M2).

**Why:** A hall is found by a feeling, so finding one stays a search across the land, while a place someone named is simply known; reading the land matters where the story says it should, not on every errand, which is the trap Death Stranding's critics saw in The Witcher 3, whose horse follows the road by itself; the map remembers where the player has been without telling them where to go. A retry that resets only the quest's own scene works on a server where others keep playing, and a clock the player can see adds the road's urgency without The Witcher 3's quests that fail unseen once the story moves on.

### D7 What goes

A system, place, creature or item, from vanilla or from a mod, goes or changes when it pays the player for staying in one place or for grinding instead of walking, since resources and better tools come from the road, or when it does not belong in the world: the Nether, jokes and nods, land in the sky, things out of their age.

| System | Verdict | Instead |
|---|---|---|
| Enchanting table and XP | Cut | Gear found in places and shard gifts (D4); quests pay in things and story |
| Anvils, the grindstone, and repairing by joining two tools | Cut | Mending at the fire and restoring at a smith (D13) |
| Villager trading | Villagers become people (D12) who trade a little; no breeding, no trading halls | |
| Planting crops and trees, breeding animals, and with them composters, beehive farms and bone meal on crops | Cut | Foraging, hunting, fishing, the peoples' stores and cooking at the fire |
| Mining | Caves stay, but rich ore lies in places, such as a people's old mine or the deep stone round an Ancient City, not everywhere | |
| Beds | Work only inside a tent; anywhere else furniture | The tent (D13) |
| Sleeping bags and hammocks (Comforts) | Cut | The bed in the tent |
| Ender chests | Cut | The backpack |
| Placed storage of every kind | Only inside a tent (D13) | The backpack and the tent |
| Backpack upgrades | The tank, pump, battery, XP pump, anvil and inception upgrades and the Omega stack upgrade are cut; backpacks still grow, and stack upgrades go up to tier 3 | |
| Bounty boards (Bountiful) | Cut | Quests and the road's scenes |
| Lodestone and recovery compasses | Cut | The Wayfarer compass |
| Map Atlases | Cut | JourneyMap's world map in the journal (`systems.md`, M2) |
| Nature's Compass | Cut | Leads, people and the land |
| Just Enough Resources | Cut; JEI's recipes stay | People, books and leads |
| Advancements, vanilla's and every mod's, with their pop-ups | Cut | The pack's own deeds, only for rare feats, hidden until earned (`systems.md`, M38) |
| The best gear | Made by the peoples' smiths, overnight; everyday tools at the player's own bench (`systems.md`, M18) | |
| The endless world | It ends in the Last Sea | |
| Lit Nether portals built into structures: Trek's pillager mansion and portal sword | Cut, as "There is no Nether" demands | |
| Nether blocks in Overworld structures, from blackstone and nether bricks to crimson and warped wood, glowstone and soul sand | Swapped for Overworld blocks, except in the Ancient Cities, which drew on the Nether | |
| Jokes, nods and real-world references: Moog's duck and beach bar; Trek's snowman, hobbit hole, pyramid of Anubis and Maya temple; CTOV's Christmas villages; Terralith's Alpha Islands | Cut | |
| Floating land: Terralith's Skylands, Moog's floating islands, Trek's floating farm | Cut | The sky is the dragon's |
| The Moobloom, the Minisheep, the Sniffer, and the golems a player builds (iron, snow, copper, tuff) | Cut | |
| Animals added twice, by Naturalist and Wilder Nature (boar, deer, elephant, giraffe, hedgehog, hippo, lion, turkey, scorpion) and the crab by Friends and Foes as well | The better one of each stays | |
| Artifacts: Eternal Steak, Everlasting Beef, Golden Hook, Helium Flamingo, Warp Drive, Villager Hat, Whoopee Cushion, Novelty and Plastic Drinking Hats | Cut | |
| Harvest, blue and super moons | Cut | The red moon |
| Immersive Aircraft's biplane and quadrocopter, Exposure's film cameras | Cut | The airships and the gyrodyne |

Kept on purpose: furnaces, brewing, horses, boats, maps, rails and minecarts, coordinates on the debug screen, waystones, which only fold roads already walked, the volcanic and basalt biomes, the Rascal and the Glare, and Supplementaries' road signs.

The world ends 4,096 blocks past the ring of frames, in the Last Sea: the land gives way to a cold, foggy sea that grows deadly the farther out a player sails. The sea runs on to Minecraft's own world edge, which nobody lives to reach (`systems.md`, M13).

```text
Past the far ring the forests thin to grass, and the grass to bare stone.
The land ends at a grey coast. Beyond it lies the Last Sea, with fog on the water.
You sail out. The fog closes in, the ember gutters, the water turns cold.
Turn back, or the cold takes you.
```

**Why:** Everything that makes resources where the player stands, or makes a home, works against the road, so it goes; Breath of the Wild dropped experience points altogether because its power comes from what the player finds. The Last Sea makes the lore's ring "past the edge of every map" literal and gives the world an end the player can reach. What does not belong, a Nether brick in a forest, a joke, a drone, breaks the story the world tells.

The Last Sea, enchanting, ore, the stack upgrade, the animals added twice, the structures and the Nether blocks are settled in `systems.md` (M13).

### D8 Scope: a 25-30 hour road

| Part | Size |
|---|---|
| Main story | About 25-30 hours, 12-15 evenings of two hours, in about 30-40 main quests |
| Side content | About half again: 12-15 hours, perhaps 30 side quests |
| Road scenes | A pool of about 60-80 scenes that come back in variants, each three or four times over a run and never twice close together |

```text
The herder again, further north now. His dogs know you before he does.
```

**Why:** A group of friends can finish 25-30 hours before life gets in the way, as Cyberpunk 2077 (26 hours) and GTA V (32) show, and D11's catch-up covers the evenings someone misses. Side content of half again, as in GTA V, keeps the main road in front; a scene that comes back in a variant, as Red Dead Redemption 2's encounters do, carries D2's five-minute cadence and can remember the player.

### D9 The ending: four doors the road opens

At the frame the ending asks whom the player believes and what they are, with four doors:

| Door | Answers | Gives | Costs |
|---|---|---|---|
| Blind it | Believes Wend | The Eye set and the dragon killed while it is half-blind; the road's rules end; a home | The voice they grew to like dies with it |
| Give it its sight | Believes the voice | The Eye brought home, the player with it: a home nothing comes to at night | Every lit window, the peoples they met among them, shines in its sight again, and it cannot keep its promise: it cannot look away |
| Keep the oath | Refuses what they are | Every shard given back, and the road | No home: one day they give the ember to someone kind at a fire |
| Close the Eye | Accepts what they are | They step into the frame and the Eye, with them in it, closes for good: the dragon finally sleeps, and the gaze ends | Themselves |

- Blind it and Give it its sight are always open. The road opens the other two: Keep the oath needs peoples who trust the player, such as ones they left a shard with, and Close the Eye needs the whole truth of what they are.
- The journal closes with a page for every people the player met and what became of them.
- A team makes one choice, because the frame takes the Eye once.

```text
At the frame Maren breaks Wend's seal and reads. Her band draws their blades.
<Wend> Set it. Then come to me, and I'll give you a home nothing finds at night.
Under his voice, older and thin: ...don't let it look at them...
The Salt Folk's riders stand behind you. They brought their shard.
  Blind it    Give it its sight    Keep the oath    Close the Eye
```

**Why:** Four doors answer both of D5's questions, two each, and apart from the dragon fight each is mostly a scene and a change to the world's rules. Opening them by the road, as Cyberpunk 2077 opens its endings through side stories, keeps the choice in the player's hand and makes D10's decisions pay where they are seen.

What opens the closed doors, the shards' part and the world after each door are settled in `story.md` (S8), who steps in for Close the Eye in S5, and how a team agrees in D12.

### D10 The shards: twelve fates, and not all of them taken

- The ember still leads to twelve hidden halls, but what is in a hall starts its main quest instead of ending it. Every shard has met its own fate since the breaking, and no two are found the same way: one still waits behind a seal, one was taken by the Gazebound, two lie on one pedestal, one has grown into a beast that sees, one warms a living people's children.

  ```text
  The ember draws you down a stair under a dead oak. The pedestal is cracked and bare.
  Scratched into the lectern: "Taken north by the Salt Folk, to warm their children. Forgive us."
  [Main] The Salt Road - the caravan that carries it, north of the dry lake
  ```

- Some shards, not all, end in a decision between two ways, each with its reward and its risk, so neither is the safe answer. Taking the shard rewards with the shard, and risks something such as its people turning against the player. Leaving it rewards with something else, such as their help later on, and costs the shard's gift.

  ```text
  The elder: "Take it, and the winter takes them."
  Take it   The shard is yours. The Salt Folk's riders shadow you for days
  Leave it  <Wend> Soft. That's one fewer at the gate.
  ```

- A frame takes the Eye with however many shards it holds, and the player learns this early, from the Keepers' records Ilse reads in Act 1. Each shard taken is a gift for the player and more sight for the dragon at the end; the voice insists on all twelve. Shards left with people who trust the player may come back to the frame in their hands.

  ```text
  Ilse, at the fire: "The Keepers wrote it down. A frame takes whatever of the Eye you bring it."
  <The stranger> Don't listen to her. All twelve, or none of this matters.
  Journal › The Eye: 6 shards. A frame takes it, however many it holds.
  ```

**Why:** The hall under its cover is the moment the pack already does well; as the turn of a quest instead of its end it stays and stops repeating, the way The Witcher 3 sends Geralt to every place Ciri has already left. A reward and a risk on both sides keep the player weighing the decision instead of picking the safe answer, and a consequence that comes later and in sight is what CD Projekt's quest design asks for. A player who believes a missing shard ends the game never leaves one, so the game says early that it does not.

The balance of each decision is settled in `systems.md` (M16).

### D11 The team: one world, one story

- Everyone on a world is one team. There is no team to make, join or leave, and singleplayer is a team of one.
- What one player does, the world does. Progress, decisions and the ending count for everyone; only what is a player's own stays theirs: their inventory, gear, tent and where they stand.
- The story's state belongs to the world, not to a player: one set of quests, decisions, shards and people, always as far as the team has got. Whoever is online, the world behaves as if the furthest player were there.
- The journal is the team's, shared and always up to date for every player. The story's lines in chat go to everyone.
- Joining later is a rule of the game, not a scene of the story:

  | Who joins | Where they appear | What they have |
  |---|---|---|
  | The world's very first player | The Last Camp, with the prologue | The normal start |
  | A new player, while others are online | Next to a teammate, on safe ground | An empty inventory, as after a death but with no grave; the team's shards and progress at once, and every reward of every quest the team finished waiting unclaimed in the journal, the early quests' backpack and tent among them |
  | A returning player, while others are online | Next to a teammate, unless they are already close by | Their own inventory and every reward they missed |
  | Anyone, while nobody else is online | A returning player where they logged out; a new player where the last player logged out | As above |

  They join the teammate who has been online longest, usually the host; if that teammate is in a tent or a memory, they appear where the teammate went in.

  | Edge case | What happens |
  |---|---|
  | Their tent stands more than 256 blocks from where they appear | It is packed up and put back in their inventory with its inside and everything stored in it, or dropped at their feet if the inventory is full. A tent closer by stays where it is |
  | Their horse or pack animal is more than 256 blocks away | It comes along |
  | Their boat, ship or aircraft is far away | It stays, and the map shows where |
  | They died before logging out | Their grave stays where they fell, and the compass marks it |
  | Their respawn fire is far away | Their respawn point moves to the fire of the teammate they joined |
  | The team is in a challenge or a decision | They join it as it stands; decisions made stay made |
  | The day clock | They count towards "most of the team" from the moment they arrive |

```text
Lena joined the road.
Journal: 3 quests were finished while you were away. Their rewards are waiting
```

**Why:** Up to four friends on one server should never manage teams, and a friend who misses an evening must not fall behind the people they play with. One state for the whole world means nobody is ever behind, and placing a joiner next to the team keeps everyone together without a scene the story has to explain.

The catch-up lives in the quest engine (`systems.md`, M1), and the distances and votes for joining in M7.

### D12 People: a world to talk to

- The world has people to talk to: travellers on the road, the peoples in their camps, the Gazebound, and characters who come back, such as the herder, the caravan's elder and Maren. The pack builds them with Easy NPC, and in phase 6 every one of them is voiced (`narration.md`).

```text
The herder looks up from his fire. "You again. The dogs remember you."
```

- Easy NPC gives the people their bodies, looks, poses and trading; the Wayfarer mod carries the conversations, for the whole team (D11). Clicking a person starts one: their lines stand as subtitles for everyone near and come in chat for those farther away, and the answers stand numbered beside the crosshair (`interface.md`, I4). The first answer chosen decides an ordinary one; a hard decision, such as a shard or a door, needs two thirds of the team, and everyone sees who chose what. Trading stays Easy NPC's own screen, one player at a time.

  ```text
  Corin  They've got my brother at the outpost. I can't ask you. I'm asking anyway.
    1 Go tonight   2 Not yet   3 Why should we?
  Kai chose: Go tonight

  Nell  Give it to me. I'll bury it with them.
    1 Give it to her 1/3   2 Keep the shard 2/3
  ```

**Why:** As the pack stands the world feels empty. People on the road carry the variety ground truth 2 asks for, and people who come back are where "Every choice comes back" shows. Easy NPC's own dialogues open as a screen for the one player who clicked and check conditions per player, while the story is one for the whole world, so the conversation runs from the Wayfarer mod's data, for everyone, without a screen that would stop a player in a team.

How far a conversation reaches and how votes wait are settled in `systems.md` (M3).

### D13 The camp and the gear

- A tent is up as soon as it is placed. It does not open while a monster is hunting the player nearby.

  ```text
  The tent flap won't open. Something is hunting you.
  ```

- A bed works only inside a tent; anywhere else it is furniture. Sleep needs that bed, a lit campfire within 4 blocks of the tent outside, and that night's story at the fire to have ended. On a Sleepless Night nobody sleeps.
- Storage of every kind, from chests and barrels to placed backpacks, safes and sacks, can be placed only inside a tent: the tent is storage that travels, like the backpack.
- No tool, weapon or armour ever breaks. Down to its last use it stays there, working like a bare hand or protecting like nothing, until it is repaired. At a fire a repair kit and materials mend it partway; a people's smith restores it fully.

  ```text
  At the fire you bind the cracked pick with flint and cord. It will hold, about half as good.
  The Salt Folk's smith takes it from you: "Leave it till morning."
  ```

**Why:** Making camp is the beat of every day (D2), so it takes a moment, not a tool and a second step, and a tent that opened with monsters on the player's heels would end every fight; vanilla's own bed already refuses while a monster is within 8 blocks. Gear that never vanishes keeps a good tool worth caring for, and mending at the fire against restoring at a smith gives both the evening and the peoples a part in it.

The tents, the fire's stories and mending are settled in `systems.md` (M12).

### D14 The caravan

- The player travels with a caravan: the survivors of a people the gaze found. Its people are characters (D12) who talk, ask for help, quarrel and die, and it grows and shrinks with the player's choices, as The Banner Saga's does.
- The caravan never travels where anyone sees it. In the morning its camp packs up while the player sets off, and once every player is out of sight of it, the camp and its people are taken off the world. Whoever stays or comes back finds it still there, packing.
- During the day the camp is nowhere. When the day's quests are done, or whenever the team chooses to make for the caravan, the camp is set up where the story wants it, on the way to the next goal, but never within sight of any player: a site too close to someone is passed over for another. The compass points to it. Story camps, such as a people's winter camp, stand at fixed places.
- The player is the caravan's scout, and it follows the marks they leave.
- Some days the caravan does not arrive, and finding it is the quest.
- The caravan is the story's to change: it can lose people, split, or die out halfway, and the player goes on alone.

```text
Morning  The caravan is packing. Old Tamsin: "Go on. We'll follow your marks."
Evening  [Main] Make for the caravan.   Compass ◆ 640 blocks
         The wagons are drawn up by a stream. Someone has the fire going.
```

**Why:** The caravan carries ground truth 2's variety with the player, gives every evening people whose stories go on, and puts faces on "Every choice comes back"; Red Dead Redemption 2 moves its gang's camp off-screen between chapters the same way. Taking the camp down and setting it up only out of sight keeps it from ever vanishing or appearing in front of anyone. How far out of sight is, where the camp goes and how it is built are settled in `systems.md` (M5).

### D15 Unlocks follow the story

What opens a new part of the game belongs to a quest or an act, never to a number of shards:

| Unlock | Belongs to |
|---|---|
| The voice speaks | The first hall |
| Ships | The quest that opens Act 2 |
| Flight | The quest that opens Act 3 |
| Seeing through stone | The quest where the Eye in the player first sees |
| Monsters growing stronger | How brightly the player shines, stage by stage (S12), not each shard or day |
| The memories | Their quests |
| The ember knowing the frame | The quest that opens the finale |

**Why:** D10 lets a player leave shards behind, and a count would lock them out of the sea or the sky for being kind. Tying each unlock to a quest also lets the story place it where it lands best.

### D16 Quests use the world's own places

- A quest can be written for a kind of place instead of one spot: a desert village, a Gazebound outpost, a ruined temple. When the player takes it, the compass points at the nearest place of that kind ahead, on the way the story is going, never back the way they came. Main quests, side quests and the day's quests all do it.
- The world's generated structures are where the story happens, not scenery between the halls.
- A quest may also place a place of its own where it needs one, such as the herder's fold or a thieves' camp.
- Every kind of place has quests written for it, from Gazebound outposts and villages to the peoples' temples, the Ancient Cities and old campsites; strongholds need none of their own, since the story leads there.
- The caravan's evening camp goes up toward where the day's quest led.

```text
[Side] Ash on the Wind - the Gazebound took the herder's son to one of their outposts
Compass  ◄ north-east, about 1,800 blocks
```

**Why:** A quest written once for a kind of place lands on every such place in the world, which carries D8's scenes and side quests across a random world and gives the pack's hundreds of generated structures a reason to be visited. Pointing ahead keeps the day moving with the story instead of sending the player back.

It raises:

- **Phase 3:** how the nearest place ahead is found without stalling the server, which Async Locator already does for the pack's searches, and how far "nearest" may be with structures 8.5 times farther apart than vanilla.
- **Phase 3:** whether a place one quest has used can serve another.

### D17 Every place has an owner

- Every kind of structure the world generates belongs to someone in the story (S11): the Gazebound, the settled world, the Twelve Peoples, the Keepers, the first civilisation, or Wayfarers and travellers. A kind with no owner goes.
- Each kind is generated as often as its owner's lore says:

  | Owner | How often |
  |---|---|
  | The Twelve Peoples | A handful of places per people, near their land and hall |
  | The Gazebound | Often enough that a day's road passes one now and then |
  | The settled world | Villages fairly common, towns rarer, a few capitals in the world; ruins, what the gaze leaves of burnt towns, common |
  | The Keepers | The twelve strongholds of the far ring |
  | The first civilisation | Ancient Cities, rare and deep |
  | Wayfarers and travellers | Campsites, common |

- The places come from structure mods kept, changed or added, or from the pack's own generator, whichever the owners and frequencies need.

**Why:** A quest that sends the player to a temple of the Sand Singers only means something if there are few of them and they are theirs, and a ruin explains itself once it is a town the gaze burned. Giving every kind an owner keeps all the world's places telling the same story.

The frequencies and the pass through every structure kind are settled in `systems.md` (M13).

## Open

Nothing is open.

## References

| Work | What to take |
|---|---|
| Cyberpunk 2077 | A second voice that lives with you and has its own agenda |
| [Death Stranding](https://www.gamedeveloper.com/design/a-design-discussion-on-death-stranding) | Terrain as the obstacle and gear as the answer; its critics name the trap of a road where only the points of interest matter |
| GTA V | Several lives on one road |
| [The Witcher 3 and Cyberpunk 2077 quest design](https://www.gamedeveloper.com/marketing/10-key-takeaways-from-the-quest-design-of-cyberpunk-2077-and-the-witcher-3) | Every quest turns; no fetch quests; delayed, visible consequences; make the player want the next piece of story |

# Interface

Everything the player reads off the screen outside the world: the HUD, the pack's screens, and the front door before a world opens. Its looks are made with the `art` skill and its words with the `lore` skill; what it plays is in `sound.md`.

## Settled

### I1 The task panel: what to do now, and how

- The top right of the screen always shows the tracked quest's title and its current objective, and under them the tasks: how to do what the step asks, and the controls the first time each is needed, every one with its key. A task ticks the moment the player does it and fades, a step's how-to too (holding up the ember ticks once it has been held up in that step, turning towards the pull once the ember has flared); a control once learned never shows again for that player. Counts stay live: the blocks still to walk, who the team still waits for; a short rest by the fire shows no countdown. Packing the tent comes first among the morning's tasks, and a player who walks 64 blocks from their tent still pitched, outside the caravan's camp, hears once that day that it stands behind them. When evening takes over the panel to make for the caravan, it names the quest that waits: "The day is ending. Make for their smoke; His First Camp can wait for morning."
- How-to lines never go to chat; chat keeps the world's own explanations, and the people's lines for whoever is out of a conversation's reach (I4).

  ```text
  The Last Fire
  Sit with the stranger.
  ○ Open your journal [J]
  ○ Choose an answer: press its number [1] to [3]

  The First Day
  Walk until his camp is far behind: 312 blocks to go.

  The First Day
  Pitch your tent, light a campfire beside it, and rest by the fire.
  ● Pitch your tent: right-click the ground with it
  ○ Rest by the fire: stand near it
  ○ Waiting for Carinthia to rest by the fire
  ```

**Why:** The Witcher 3 and Cyberpunk 2077 keep the tracked quest and its objectives in the top right, so players know where to look, and teaching each control the moment it is first needed, ticked off when done, teaches without a tutorial or a wall of text. The first playtest stalled at every step where only the journal or chat said what to do and how.

### I2 The rest of the HUD: the compass and one line, banners for notices

- The top of the screen is the compass. Its band always shows, with every mark on it: the tracked goal, the teammates, each as their skin's face in an iron rim so none is mistaken for a direction, the player's tent and a grave. The tracked goal's distance stands under its mark, and any other mark's distance shows while the player faces it; a mark more than 8 blocks above or below the player (`balance.toml` `[compass]` `level_blocks`) has a small arrow up or down beside its number, drawn in the number's own colour.
- Under the band stands one line at most, the most urgent thing now: the gaze watching (its bar), else a sleepless night, else the ember's state (cold, dim, listening, its feeling while the goal is felt and not known); the ember's line shows for about eight seconds when it changes and after the ember is held up, and is gone otherwise (`style.toml` `[motion]` `linger`). Hallowed ground, the fire's circle or a hall's, is a small icon set into the band's right end cap, so nothing hangs off one side and the band reads centred on the crosshair. A boss bar pushes the band down.
- A quest begun, a quest done and a reward waiting come as the stamp's leather strip under the compass (`style.md`, L5) for about four seconds, with a short sound of their own (`sound.md`, A6), the same for every player; a waiting reward also stays a task in the panel until it is taken. None of them comes while someone is still speaking: each waits for the talk's last line, so a reward shows after the line that hands it over. Chat keeps the people and the story.
- The task panel keeps the top right (I1) and never reaches into the bottom quarter of the screen; it shows a task or the objective whole or not at all, never cut off mid-line. Travelers Titles' place names stay, smaller and lower on the screen, so they never cross the compass. Jade's box shows what the player looks at while they sneak and is gone otherwise, ending in the name of the mod it comes from; it has no key. An item's tooltip names its mod while Shift is held. The people's nameplates show on hover.
- What comes to the player shows at the bottom right, above vanilla's subtitles when those are on, for about four seconds each, through Pick Up Notifier in the stamp's look: every item picked up from the ground, merged as it counts up ("Oak Log ×12"), every arrow collected, and what the story gives (the ember, a shard, a reward taken, a dream's kit, a grave's things) in gold, and every new entry in the journal ("Journal · People: Agna"). What is taken from a chest or crafted shows nothing, and the journal's sound plays with its line.

  ```text
        ◂  SW   Φ   W   NW  ▸  [fire]       MAIN ROAD
                [3,253]                       Φ His First Camp
         [THE EMBER · FLARING]                Find his first camp, by a lake.
                                              ○ Open your journal [J]
           [leather strip]  Φ A NEW ROAD
                            The Pull
  ```

**Why:** Skyrim's compass bar and The Witcher 3's tracker are where players already look, and the most-endorsed Witcher 3 interface mod, Friendly HUD, mostly hides what does not matter yet; one line by urgency keeps a warning seen without a stack of five strips. A banner with a sound, as The Witcher 3's "New quest", marks a turn of the story that a line in chat lets slip by.

### I3 The journal screen: a book, list left, entry right

- J opens the journal as I5's leather-bound book, the same on every ribbon: the left page lists, the right page shows the entry chosen there. News carries a small gold dot until it is opened: a person, a word, a page, a gear row, a stone, a quest that is new or has a new objective, and a reward waiting. What the team lived through or read carries none, The Story So Far, Conversations, Pages and the quests done, since the team already knows it; the pencil's sound likewise marks only a new person, word or page. Turning a page sounds like vanilla's book (`sound.md`, A6).
- On Quests the left page lists the rewards waiting first, then under a quiet "Main road" heading the main quest and Make for the caravan, under "Side road" the side quests, with the quests done folded away at the bottom. A heading is never chosen. The right page opens with the quest's own summary, where it comes from and what it asks, then holds the chosen quest's story as "I", its current objective with its tasks, what was found, any clock, and a Track ribbon; the tracked quest wears the Wayfarer's mark.
- Eleven ribbons are there, M2's eight, M18's Gear, M38's Deeds (`systems.md`) and Afterwards (I9), hung by use in three groups with a small gap between them: what the player acts on (Quests, Gear, Map, Stones), what they know (The Story So Far, People, Words, Pages, Conversations) and the record (Deeds, Afterwards), so the tools opened many times an evening stay at the top and a ribbon that appears late only ever joins a lower group. Each is a leather ribbon with its icon and its name on hover, its page titled with a line under the title saying what it holds (Conversations: "Every talk, word for word", newest first), each appearing the first time it holds an entry: the first night shows Quests, The Story So Far, People and the Map; Gear comes with Hob's patterns at the caravan's first fire, Pages with the first book, Conversations with the first talk, Stones with the first fall, Deeds with the first deed, Afterwards when the door's scene ends. A new ribbon carries the gold dot, and the first time Gear appears the task panel says where it is.
- A waiting reward is the first entry, in gold. Its right page shows the items in the stamp's slots, a line on what it is and one Take button; taking it plays its sound and puts the items in the inventory, or at the player's feet when it is full, and never ends a step. Until it is taken the task panel keeps it as a task.

  ```text
  ┌ Quests ──────────────┬ The Pull ─────────────────┐ [quests icon]
  │ ● His Pack  (Take)   │ The ember pulled me west  │ [story icon]
  │ Φ The Pull           │ the morning after Ash...  │ [people icon]
  │   The Road           │ ○ Follow the ember        │ [words icon]
  │   Make for the camp  │                  [Track]  │ [map icon]
  │ + Done (3)           │                           │
  └──────────────────────┴───────────────────────────┘
  Task panel   ○ Take His Pack: open your journal [J] and press Take
  ```

- A ribbon's line under the title wraps onto as many lines as it needs. The Story So Far shows its newest chapter first, as Conversations does. Make for the caravan's page asks before it ends anyone's day: "Turn back now? The day ends for the whole team; The Deserter waits for morning.", and its button reads Make for the caravan, then Yes, end the day.
- The Map ribbon opens the map, and closing the map (Esc or M) comes back to the journal on the ribbon it was opened from; the map key opens the map alone and closes back to the world.

**Why:** The Witcher 3 and Cyberpunk 2077 keep the list beside the chosen entry, with its objectives and a Track button, so one entry is found without scrolling, and ribbons that appear with their first entry spare a newcomer eight empty tabs. The first playtest missed the Take button in a list; a reward first, in gold, with its items shown and one button, cannot be missed.

### I4 Conversations: subtitles and numbered answers, no screen

- A conversation opens no screen, so nobody stops moving or looking. Its lines stand as subtitles at the bottom centre on the stamp's char panel (`style.md`, L5), the speaker's name in capitals in that person's own colour (`style.toml` `[speakers]`, the same colours chat gives them), at the pace that waits for reading (`balance.toml` `[talk]`).
- Answers stand as a short list right of the crosshair, numbered from 1; while they stand, the number keys choose an answer instead of a hotbar slot. The first time answers come, the task panel says to press an answer's number. Every set of answers ends with "Say that again.", which says the conversation again from its first line, without what its lines first set off (a sound, a piece, someone leaving), and then shows the answers again.
- A conversation waits for the team: it begins only when someone stands with the people in it and can see them, or right-clicks one of them, and everyone who speaks in a scene stands in it, and a person's line waits while nobody is in reach of them, so a player alone never misses a scene or reads it in chat. Every player within the conversation's reach sees the same subtitles and answers, and "Mo chose: Bury him" once one is chosen. Teammates farther away get its lines in chat, since the story is shared. A hard decision shows its answers and their tally to every player online, wherever they are (`systems.md`, M3). The journal's Conversations keeps every talk (I3).
- Nobody talks to a player who has not arrived yet. A player arrives when their game has drawn the land around them, after joining a world, a change of world (a tent, a memory, the End) or a respawn, and 4 seconds have passed since, so the first thing a new world gives is the fire crackling in a whole land, then the first line (`balance.toml` `[talk]` `arrive_seconds`). Until then no talk starts in their reach and the lines of one that would wait; a game that never says it has drawn the land counts as arrived after 32 seconds (`arrive_most_seconds`). A talk already running for a teammate goes on, and what the newcomer missed is in their journal's Conversations.
- A conversation never runs over a fight or a screen. While a monster within 32 blocks is after anyone in its reach and can see them, its next line waits, the narrator says once "Something is hunting you. The talk goes on once it is dead or gone.", and each hunter wears the hold's dark red outline through walls until it dies or stops hunting; the talk goes on 4 seconds after the last one gives up (`balance.toml` `[talk]`). A fight that is itself the step, a hold, a duel, a chase, the worm, the sea or a memory, says its lines as they come. While someone in its reach has a screen open, the inventory, the journal, the chat, its next line waits up to 16 seconds, then goes on. The first time a conversation has ended for a player who has opened the journal, the task panel says "Missed a line? Every word said is in your journal, under Conversations" until they open it.
- The voice speaks through the ember each player carries, so its lines stand as subtitles for every player, wherever they are.
- What a line says a person does, they do as it is said: Hob gets up on "Hob gets up", the hooded woman sits as she sits down, Drifa comes down off her roof as she climbs down. A person moves by walking whenever a player can see where they stand or where they go, and appears or vanishes only out of everyone's sight, or in the smoke a line tells of. They walk the way a player would, at a walking pace, round wagons, logs and walls and through open doors, wading shallow water; with no way through, they stand facing where they are going and are put there the moment nobody looks, or after 30 seconds without getting closer even if someone does, so nothing waits on them for good; they never turn on the spot. Someone walked home behind the team keeps their pace, and catches up only where nobody sees it. Standing up from a seat steps off it.
- A soft tick sounds when the band speaks after a quiet moment, the first line of a talk or a lone notice, never on every line of a running talk, so a player looking elsewhere knows something new stands there (`sound.md`, A6).
- The task panel names a step's objective only once the talk that ends the step before it is over, so it never runs ahead of what is being said.
- The story's notices are the narrator's lines on the band, never chat: a shard taken and what it remembers, a hold's waves and its door giving way, the ember tugging towards smoke, a teammate turning back, the dawn's first lesson, a worn tool, the night's warning, the ember warming at a fire or dimming. The narrator's lines reach every player of the team wherever they are, in a tent too; one for a player reaches only them. A narrator's line about what is found at a place ("Prints cross your way") stands as a subtitle for those there, a tent beside it included, and reaches the rest in chat with the name of a teammate who is there. A big beat gets its silence: after a death the story waits about sixteen seconds (`balance.toml` `[talk]`) before the narrator says what happened, and a step's tasks show only once its lines are said. A lost hold offers trying the night again as an answer, and offers it again after a restart. Chat keeps only what players say, and a conversation's lines for those too far to see it.
- While answers stand, the task panel gives way to them and to the band: it shows as many rows as fit above them, the answer task first.

  ```text
                       +    1  Bury him
                            2  Leave him to the fire
                            3  Ask about the shards
  ─────────────────────────────────────────────────────────
             The stranger  Don't let it... the shards. I took them.
  ```

**Why:** The first playtest's new players did not know answers in chat could be clicked, nor that chat had to be opened first; numbers beside the crosshair need neither. Cyberpunk 2077 keeps the player free to look round while choices stand, which a team on a server needs, where The Witcher 3's screen would hold one player still while the night goes on around the others.

### I5 The look: the stamp

- Everything the pack draws on the screen, and vanilla's and the mods' interfaces with it, wears one design system, the stamp, Ash and Ember (`style.md`): char panels with iron rims, leather strips and buttons, parchment only in books, the pack's own fonts, and the Wayfarer's mark as its emblem.
- One accent colour, ember gold, means "the ember or the story points here": the tracked quest, the compass's goal, a reward waiting. Text is bone, quiet for what is secondary and dim for what is done; ink on parchment.
- The journal is a book with a charred leather cover and parchment pages that opens over the dimmed world (I3); notices come on a leather strip (I2); conversations stand on a char panel (I4); the story's own things, the ember, the shards and the stone of one's own, show their tooltip with an ember rim.
- Each piece is drawn with the `art` skill and judged on the style board and where the player meets it, over bright and dark land, under the shader the pack ships, at interface scales 2, 3 and 4. Jade's box and Travelers Titles' place names follow the same look as far as their configs reach.

  ```text
  Top right    MAIN ROAD / Φ His First Camp (gold) / Find his first camp, by a lake. / ○ Open your journal [J]
  Compass      [leather band]  SW   W   Φ   NW       [3,253] under the mark
  Press J      A charred leather book opens over the dimmed world, leather ribbons with icons on its edge
  Notice       [leather strip]  Φ A NEW ROAD / The Pull
  ```

**Why:** The games the pack follows give every screen one source in their world, Breath of the Wild's Sheikah Slate, Red Dead Redemption 2's journal, and keep the HUD bare, so a look of the Wayfarer's own things, a book, char, iron and the ember's gold, makes the screens part of the story; one stamp on every screen, vanilla's and the mods' included, keeps the pack from reading as Minecraft's grey menus with a modpack's pieces between them.

### I6 The front door: Wayfarer's own title, the story so far while loading

- The title screen is Wayfarer's own: a "WAYFARER" logo in the stamp, the Wayfarer's mark between two iron rules over the word, its letters the display face's (`style.md`, L3) built three times larger in ember gold; a panorama taken at the Last Camp on a lake shore, the stranger by its fire and a town across the water, at dawn, noon, sunset, dusk or under a full moon, one of them at random each time the game starts; no yellow splash lines; the stamp's buttons without Minecraft Realms, which cannot run the pack, and Report a Bug beside Mods; nothing in the corners but Mojang's copyright, no version lines and no mod's notices; and the pack's own title piece (`sound.md`, A4), which plays on every screen while no world is open, the server and world lists and the options included, and on while a world is made and loads, and fades out as the player stands in it. The launcher shows the lit ember as the pack's icon wherever the launcher takes one.
- While a world loads, joining a server included, the last page of The Story So Far stands on parchment over the dimmed panorama, as The Witcher 3 recaps the story on its loading screens, with vanilla's own progress line under it. The page belongs to the world, not to its folder or the server's address, so a world made again under the same name or a server whose world was reset shows the beginning again; a server tells its world apart while the player joins, before the terrain loads. A new world, or a server joined for the first time, shows the narrator's line that leads into the first scene: "A fire burns low in the grey before dawn. Someone sits by it, waiting for you to wake." The narrator reads it, and a later page's newest entry is read in the player's voice; the loading screen stands at least 1 s, and the voice fades as the world appears (`narration.md`).
- The first time Singleplayer or Multiplayer is pressed, a screen in the stamp asks for the player's voice before the list opens, with A man, A woman and Silent; the answer opens the list and is never asked again, and Music & Sounds changes it (`narration.md`).
- Create New World opens with what the story needs: Survival, commands off, the pack's land, and the difficulty I7 settles. Its World tab holds only the seed, with no world type, no Generate Structures and no Bonus Chest, and its More tab Game Rules and Data Packs, without Experiments. Creating the world goes straight to loading, with no warning about experimental settings, since every world of the pack has them.
- The pause menu has Journal and Map where Advancements and Statistics stood, since the pack hides advancements (D7) and uses none of vanilla's statistics, Report a Bug beside Mods, and none of vanilla's feedback, bug report or player reporting buttons, which lead to Mojang; Options takes the full row on a server, and Open to LAN stays beside it in singleplayer.
- Report a Bug, on the title and in the pause menu, opens a screen that says the pack's bug form opens on GitHub with the player's version, quest and place filled in, with Open the Form, Open the Logs (the game's logs folder, so latest.log can be dragged into the form) and Back; the screen stays until Back, so both can be opened. The form stands on its own on GitHub too, for a game that crashed: it asks what happened, how to make it happen again and for the log, the crash report or pictures, and says where each of the filled-in facts is found. A game that fails to load names the same issues page in its crash report. From the title only the version is filled in; on a server the world goes on while the browser is open, as it does under any pause there.
- The death screen shows no score, since the pack has no experience; every death has its own words, a fall that began in water too ("Wayfarer fell out of the water", which Minecraft 1.21.1 itself leaves as its raw key).

  ```text
  Title     WAYFARER  ·  the Last Camp at dawn turning slowly  ·  Singleplayer  Multiplayer  Mods | Report a Bug  Options  Quit
  Loading   [parchment]  I buried him by his fire. The ember in my hand is warm now.
  Voice     Your voice  ·  The story is read aloud. This voice says your answers and reads your journal to you.  [A man] [A woman] [Silent]
  Pause     Back to Game  ·  Journal  ·  Map  ·  Options  ·  Mods | Report a Bug  ·  Save and Quit
  Report    Wayfarer's bug form opens on GitHub, with your version, quest and place filled in.  [Open the Form] [Open the Logs] [Back]
  ```

**Why:** The first minute sets the tone the rest has to keep, and vanilla's title with its yellow jokes says "a modpack" before the story can say anything; a recap while loading brings a player who comes back after a week into the story where they left it, as The Witcher 3 does. A new world that starts as the story needs keeps a newcomer from choosing settings they cannot judge yet, and the vanilla options stay for those who can. A bug is fixed only once it can be made to happen again, which takes the version, the place, the quest and the log; the game knows the first three and a player rarely does, so the button fills them in and opens the folder that holds the fourth, where vanilla's own buttons would send a pack's bug to Mojang.

### I7 Options and access

- **Subtitles.** Vanilla's Subtitles option covers the pack's own sounds too, each with a short subtitle in the lore's voice ("Ember flares", "Embers die down", "Something claws out of the ground"), off by default as in vanilla. Conversations always show as subtitles, since they are the dialogue (I4).
- **Pace.** A conversation keeps one pace for everyone, since its lines are shared: 8 characters a second and at least 4 seconds a line (`balance.toml` `[talk]`). The two lines before the current one stay faintly above it, and any line missed is in the journal's Conversations.
- **Size.** Everything the pack draws over the world, the compass and its line, the banners, the notices, the task panel and the conversations, has a size of its own, HUD Size in Options, Accessibility Settings, beside Show Subtitles: Small, Normal or Large, Small by default. Small draws it one whole scale step under vanilla's GUI scale, never under 2, so at GUI scale 3 it is the size it has at 2; Normal draws it at the GUI scale and Large one step over. Whole steps keep every pixel crisp. Vanilla's hotbar, hearts and effects follow the GUI scale. It is each player's own, kept on their machine.
- **Colour.** Nothing is told by colour alone: the ember's state is also a word, a task done is also a tick, and a teammate on the compass also shows their face.
- **Flashing.** The red moon's pulse and a memory's flash play in full by default; vanilla's Hide Lightning Flashes, off by default, calms them too, so one switch covers every flash.
- **Difficulty.** New worlds and the server pack start on Normal; Hard is chosen in Create New World or the server's settings, and a team that comes for the story picks Easy.

**Why:** Vanilla's own switches are where players already look, so the pack hangs its options on them and adds only HUD Size, since its own HUD at vanilla's scale took too much of the screen at GUI scale 3 in the first playtest. A shared line cannot run at each player's speed, so it keeps one pace and stays findable afterwards. Normal lets the story be played by friends of every skill, and Hard is one setting away.

### I8 Inventories: already sorted, recipes in JEI

- Gear worn to its last use, which works like a bare hand until mended, carries a small crack in its slot's top-left corner in the stamp's danger red, wherever the item is drawn: the hotbar, the inventory, a chest, the backpack.
- **Sorted.** Whatever a player opens is already sorted. A chest, barrel, ender chest, shulker box or backpack is sorted when the last player looking into it closes it, and a player's own three rows are sorted whenever something lands in them while their inventory is closed. Nothing moves while a screen is open, and a chest a teammate still has open waits for them.
- **One order.** Tools and weapons, then armour and clothes, then food, then blocks, then everything else; within each kind Minecraft's own things first, then each mod's together by the mod's name, and within those by name, with stacks merged and the fullest first, so a Farmer's Delight meal sits with the bread, not with the mod's blocks. A middle-click sorts the inventory under the cursor in the same order at any time (Inventory Sorter). A backpack keeps its own "sort by" button and its memory and no-sort slots.
- **Never sorted.** The hotbar, armour, the off-hand and the curios, and the workstations whose slots mean something: furnaces, hoppers, the crafting grid. A loot chest shows its loot as found the first time it is opened, and is sorted when it is closed.
- **No recipe book.** Recipes are JEI's, R and U on an item, and the Gear page's "How it's made" (M18); the recipe book's button is gone from the inventory, the crafting table, the furnaces, the cooking pot and the curios screen.

  ```text
  Close a chest   [Oak Log 64][Bread 3][      ][Iron Axe][Cobblestone 12][Oak Log 9]
  Open it again   [Iron Axe][Bread 3][Cobblestone 12][Oak Log 64][Oak Log 9][ ]
  Press E         the three rows sorted the same way; the hotbar as you left it
  ```

**Why:** Tidying is chores, not play, and a camp chest a team shares reads the same to everyone when it keeps one order by kind, which an order by amount would shuffle each time a count changed. Sorting as a chest is closed, not as it opens, leaves loot as it was found and never moves a stack under a teammate's cursor; the hotbar stays the player's own, since that is where the layout is muscle memory. The recipe book repeats JEI, and its green book was the one vanilla button left outside the stamp.

### I9 Afterwards: a page for every people met

- When the door's scene ends (`vision.md`, D9; `story.md`, S8), the journal opens by itself on its last ribbon, Afterwards, "What became of them", with the gold dot. It holds one page for every people the player met, in the order they were met: the twelve halls' peoples, the gone ones known through who is left of them (Drifa for the Frost Walkers, the witches for the Marsh Mothers, the Drowned City's last for theirs). A people is met when the player reached its hall or spoke with its people.
- Each page is one short paragraph in the narrator's voice, speaking to "you" in the past tense as Fallout's ending slides do, so it reads the same after every door, made of two parts: what the player did at that people's hall (the shard left, taken, or given to Drifa to bury, and the hall's own decision), and what the door did to their land. Every part is written with the `lore` skill.

  ```text
  Afterwards › The Tide Folk
    You left their two stones together. The boats still go out at night, and nothing watches them.
    When the Eye closed, the bay went dark for good, and they stopped hooding their lamps.
  ```

- A team gets one set of pages for its world, since it made one choice at the frame (D12); each player's journal shows it.
- After Close the Eye with Pip dead the player never wakes (S8): there the pages turn on the black screen, one after another, after the frame going still, and Years later (`story.md`, S53), the sculk's line and the credits follow them, ending on "Start a new road". Each stays as long as it takes to read (`style.toml` `[motion]` `read`, per character), and a click or a key turns it sooner.
- On the ending's black screen a page's people and a speaker's name stand in gold above their text, a speaker's line in quotes below it; the narrator's lines and the credits stand in bone alone. The Afterwards ribbon's button, The ending, plays Years later and the credits again.
- The pages stay in the journal for the game that goes on after the door. The journal opens by itself only for those online when the scene ends; a player who joins later finds the ribbon with its gold dots.

**Why:** Fallout's and Dragon Age's ending slides answer "what happened to them" people by people, and that answer is where the road's choices pay: a shard left or taken weighs most when the player reads what it did. Two parts per page keep the writing to what the choices really decide, the hall and the door.

## Open

Nothing is open.

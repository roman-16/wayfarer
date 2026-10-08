# Interface

Everything the player reads off the screen outside the world: the HUD, the pack's screens, and the front door before a world opens. Its looks are made with the `art` skill and its words with the `lore` skill; what it plays is in `sound.md`.

## Settled

### I1 The task panel: what to do now, and how

- The top right of the screen always shows the tracked quest's title and its current objective, and under them the tasks: how to do what the step asks, and the controls the first time each is needed, every one with its key. A task ticks the moment the player does it and fades; a control once learned never shows again for that player. Counts stay live: the blocks still to walk, the seconds rested, who the team still waits for.
- How-to lines never go to chat; chat keeps the world's own explanations, and the people's lines for whoever is out of a conversation's reach (I4).

  ```text
  The Last Fire
  Sit with the stranger.
  ☐ Open your journal (J)
  ☐ Choose an answer: press its number

  The First Day
  Walk until his camp is far behind: 312 blocks to go.

  The First Day
  Pitch your tent, light a campfire beside it, and rest by the fire.
  ☑ Pitch your tent: right-click the ground with it
  ☐ Rest by the fire: stand near it
  ☐ Waiting for Carinthia to rest by the fire
  ```

**Why:** The Witcher 3 and Cyberpunk 2077 keep the tracked quest and its objectives in the top right, so players know where to look, and teaching each control the moment it is first needed, ticked off when done, teaches without a tutorial or a wall of text. The first playtest stalled at every step where only the journal or chat said what to do and how.

### I2 The rest of the HUD: the compass and one line, banners for notices

- The top of the screen is the compass. Its band always shows, with every mark on it: the tracked goal, the teammates, the player's tent and a grave. The tracked goal's distance stands under its mark, and any other mark's distance shows while the player faces it.
- Under the band stands one line at most, the most urgent thing now: the gaze watching (its bar), else a sleepless night, else the ember's feeling while the goal is felt and not known. Hallowed ground, the fire's circle or a shrine's, is a small icon at the band's end. A boss bar pushes the band down.
- A quest begun, a quest done and a reward waiting come as a strip of parchment under the compass for about four seconds, with a short sound of their own (`sound.md`, A6), the same for every player; a waiting reward also stays a task in the panel until it is taken. Chat keeps the people and the story.
- The task panel keeps the top right (I1). Travelers Titles' place names stay, smaller and lower on the screen, so they never cross the compass. Jade's box starts off, and its key brings it back without the name of the mod each thing comes from; the people's nameplates show on hover.

  ```text
        ◂ · SW · ✦ · W · NW · ▸  ♨           His First Camp
                 3,253                         Find his first camp, by a lake.
           the ember · flaring                 ☐ Open your journal (J)

           [parchment]  A new road: The Pull
  ```

**Why:** Skyrim's compass bar and The Witcher 3's tracker are where players already look, and the most-endorsed Witcher 3 interface mod, Friendly HUD, mostly hides what does not matter yet; one line by urgency keeps a warning seen without a stack of five strips. A banner with a sound, as The Witcher 3's "New quest", marks a turn of the story that a line in chat lets slip by.

### I3 The journal screen: a book, list left, entry right

- J opens the journal as I5's leather-bound book, the same on every ribbon: the left page lists, the right page shows the entry chosen there. New entries carry a small gold dot until they are opened, and turning a page sounds like vanilla's book (`sound.md`, A6).
- On Quests the left page lists the rewards waiting first, then the main quest, the side quests and Make for the caravan, with the quests done folded away at the bottom. The right page holds the chosen quest's story as "I", its current objective with its tasks, what was found, any clock, and a Track ribbon; the tracked quest wears the gold ✦.
- Nine ribbons are there, M2's eight and M18's Gear (`systems.md`), each appearing the first time it holds an entry: the first night shows Quests, The Story So Far, People and the Map; Gear comes with Hob's patterns at the caravan's first fire, Pages with the first book, At the Fire with the first talk, Stones with the first fall. A new ribbon carries the gold dot, and the first time Gear appears the task panel says where it is.
- A waiting reward is the first entry, in gold. Its right page shows the items in slots like vanilla's inventory, a line on what it is and one Take button; taking it plays its sound and puts the items in the inventory, or at the player's feet when it is full, and never ends a step. Until it is taken the task panel keeps it as a task.

  ```text
  ┌ Quests ──────────────┬ The Pull ─────────────────┐ ▮Quests
  │ ● His Pack  (Take)   │ The ember pulled me west  │ ▮Story
  │ ✦ The Pull           │ the morning after Ash...  │ ▮People
  │   The Road           │ ☐ Follow the ember        │ ▮Words
  │   Make for the camp  │                  [Track]  │ ▮Map
  │ ▸ Done (3)           │                           │
  └──────────────────────┴───────────────────────────┘
  Task panel   ☐ Take His Pack: open your journal (J)
  ```

**Why:** The Witcher 3 and Cyberpunk 2077 keep the list beside the chosen entry, with its objectives and a Track button, so one entry is found without scrolling, and ribbons that appear with their first entry spare a newcomer eight empty tabs. The first playtest missed the Take button in a list; a reward first, in gold, with its items shown and one button, cannot be missed.

### I4 Conversations: subtitles and numbered answers, no screen

- A conversation opens no screen, so nobody stops moving or looking. Its lines stand as subtitles at the bottom centre on I5's parchment band, the speaker's name in gold, at the pace that waits for reading (`balance.toml` `[talk]`).
- Answers stand as a short list right of the crosshair, numbered 1 to 4; while they stand, the number keys choose an answer instead of a hotbar slot. The first time answers come, the task panel says to press an answer's number.
- Every player within the conversation's reach sees the same subtitles and answers, and "Mo chose: Bury him" once one is chosen. Players farther away get its lines in chat, since the story is shared. A hard decision shows its answers and their tally to every player online, wherever they are (`systems.md`, M3). The journal's At the Fire keeps every talk (I3).
- The voice speaks through the ember each player carries, so its lines stand as subtitles for every player, wherever they are.
- The story's notices are the narrator's lines on the band, never chat: a shard taken and what it remembers, a hold's waves and its door giving way, the ember tugging towards smoke, a teammate turning back, the dawn's first lesson, a worn tool, the night's warning. One for the whole team reaches every player wherever they are; one for a player reaches only them. A lost hold offers trying the night again as an answer, and offers it again after a restart. Chat keeps only what players say, and a conversation's lines for those too far to see it.
- While answers stand, the task panel gives way to them and to the band: it shows as many rows as fit above them, the answer task first.

  ```text
                       +    1  Bury him
                            2  Leave him to the fire
                            3  Ask about the shards
  ─────────────────────────────────────────────────────────
             The stranger  Don't let it... the shards. I took them.
  ```

**Why:** The first playtest's new players did not know answers in chat could be clicked, nor that chat had to be opened first; numbers beside the crosshair need neither. Cyberpunk 2077 keeps the player free to look round while choices stand, which a team on a server needs, where The Witcher 3's screen would hold one player still while the night goes on around the others.

### I5 The look: the Wayfarer's own things, drawn the way Mojang draws

- Everything the pack draws looks like it comes from the Wayfarer's own things, drawn in vanilla's pixels, at vanilla's scale and in vanilla's font, which also keeps every language's letters (`production.md`, R4).
- The HUD has no frames: vanilla's font with its shadow, over a faint band like chat's only where text sits, so it reads over a bright, shaded sky or snow.
- One accent colour, ember gold, means "the ember or the story points here": the tracked quest, the compass's mark, a reward waiting. Everything else is white, or grey for what is done or only a hint.
- The journal is a leather-bound book that opens over the dimmed world: the quests on the left page, the chosen one's story on the right, and ribbon bookmarks along its edge for Quests, The Story So Far, People, Words and the Map.
- Notices slide in as a strip of parchment, the way vanilla's toasts do.
- The story's own things, the ember, the shards and the stone of one's own, show their tooltip in an ember-gold frame.
- Each piece is drawn with the `art` skill and judged beside its vanilla relative (the book screen, the toasts, the tooltip), under each of the shader packs the pack ships and at interface scales 2, 3 and 4. Jade's box and Travelers Titles' place names follow the same look as far as their configs reach, which is checked when they are restyled.

  ```text
  Top right    His First Camp                      (ember gold)
               Find his first camp, by a lake.     (white)
               ☑ Open your journal (J)             (grey, fading)
  Compass      SW  ·  W  ·  NW       ✦ His first camp  3,253
  Press J      A leather book opens over the dimmed world. Ribbons: Quests · The Story So Far · People · Words · Map
  Notice       [parchment]  A new road: The Pull
  ```

**Why:** The games the pack follows give every screen one source in their world, Breath of the Wild's Sheikah Slate, Red Dead Redemption 2's journal, and keep the HUD bare, so a look of the Wayfarer's own things, a book, parchment and the ember's gold, makes the screens part of the story while the pixels, scale and font keep it Minecraft. Vanilla's grey windows read as a settings menu, which is how the first plays found the journal.

### I6 The front door: Wayfarer's own title, the story so far while loading

- The title screen is Wayfarer's own: a "WAYFARER" logo in Minecraft's logo style, a panorama taken at the Last Camp at dawn, the fire, the bedroll and the road leading away, no yellow splash lines, vanilla's buttons, and the pack's own title piece (`sound.md`, A4). The launcher shows an ember as the pack's icon wherever the launcher takes one.
- While a world loads, the last page of The Story So Far stands on parchment over the dimmed panorama, as The Witcher 3 recaps the story on its loading screens; a new world shows one line from the lore. The voice reads it once narration comes (`narration.md`, N1).
- Create New World opens with what the story needs: Survival, commands off, the pack's land, and the difficulty I7 settles. Vanilla's options stay under More World Options for anyone who wants them.
- The pause menu has Journal and Map where Advancements stood, since the pack hides advancements (D7); the rest is vanilla's.

  ```text
  Title     WAYFARER  ·  the Last Camp at dawn turning slowly  ·  Singleplayer  Multiplayer  Mods  Options  Quit
  Loading   [parchment]  I buried him by his fire. The ember in my hand is warm now.
  Pause     Back to Game  ·  Journal  ·  Map  ·  Options  ·  Save and Quit
  ```

**Why:** The first minute sets the tone the rest has to keep, and vanilla's title with its yellow jokes says "a modpack" before the story can say anything; a recap while loading brings a player who comes back after a week into the story where they left it, as The Witcher 3 does. A new world that starts as the story needs keeps a newcomer from choosing settings they cannot judge yet, and the vanilla options stay for those who can.

### I7 Options and access

- **Subtitles.** Vanilla's Subtitles option covers the pack's own sounds too, each with a short subtitle in the lore's voice ("Ember flares", "Embers die down", "Something claws out of the ground"), off by default as in vanilla. Conversations always show as subtitles, since they are the dialogue (I4).
- **Pace.** A conversation keeps one pace for everyone, since its lines are shared: 8 characters a second and at least 4 seconds a line (`balance.toml` `[talk]`). The two lines before the current one stay faintly above it, and any line missed is in the journal's At the Fire.
- **Size.** Everything the pack draws follows vanilla's GUI scale, and the conversation subtitles have a size option of their own, Conversation Size in Options, Accessibility Settings, beside Show Subtitles: Small, Normal or Large, Normal by default, each a whole screen pixel smaller or larger per letter pixel, so letters stay crisp. It is each player's own, kept on their machine.
- **Colour.** Nothing is told by colour alone: the ember's state is also a word, a task done is also a tick, and a teammate on the compass also shows their initial.
- **Flashing.** The red moon's pulse and a memory's flash play in full by default; vanilla's Hide Lightning Flashes, off by default, calms them too, so one switch covers every flash.
- **Difficulty.** New worlds and the server pack start on Normal; Hard is chosen in Create New World or the server's settings, and a team that comes for the story picks Easy.

**Why:** Vanilla's own switches are where players already look, so the pack hangs its options on them and adds only Conversation Size, which the conversations need. A shared line cannot run at each player's speed, so it keeps one pace and stays findable afterwards. Normal lets the story be played by friends of every skill, and Hard is one setting away.

## Open

Nothing is open.

# Sound

Everything the player hears but the voice (`narration.md`): what plays where, and where it comes from. It is built with each quest from the bar on (`slice.md`, step 4), and every piece and sound is chosen with the `sound` skill and put to the user by ear.

## Settled

### Music for the story's moments

Music plays at points the story chooses, not by place and weather: the battle piece at every hold, a piece at every death the player watched, at each act's revelation at the fire, a short theme of its own for each memory, and one on each act's first morning or first flight; outside a hold, an evening has at most two (`story.md`, S25). Everyday play, the road, ordinary days and nights, keeps Reactive Music and its own songs.

**Why:** A loss the player has watched coming lands hardest with music under it, and the road's quiet makes the moments that have music stand out.

### Free to use

Every sound effect and piece of music the pack chooses and ships itself is free to use in it; a mod's own sounds and music come with the mod (A4). The pack's own sounds come from free libraries, new to the player: CC0 and public domain first (Freesound, OpenGameArt, Musopen's recordings on Wikimedia Commons, found through Openverse), then CC BY 3.0 or 4.0 with its credit in the pack, then Pixabay; Minecraft's own sound events only where the thing is vanilla's own, referenced and never copied. Nothing NC or ND, and not Free To Use, whose free licence covers only videos. A classical piece is free only when its recording is CC0 or public domain too.

**Why:** The pack is shared and played on servers, so nothing in it may need a licence bought or asked for, and sounds of the pack's own, which the player has never heard in Minecraft, make its moments its own.

### How a piece plays

The Wayfarer mod plays a story piece itself, in the Music category, and Reactive Music fades its own music out over a few seconds while it plays and back in after (its `soundsMuteMusicIgnoreDistance`). A piece started by a line plays once to its end; a piece that belongs to a step, such as a hold, loops until the step ends and then fades out. Stepping into a tent or another world carries a playing piece through the door; only leaving the game stops it.

```text
<The stranger> Don't let it... the shards. I took them.   ♪ What We Don't Say swelling in, Reactive Music fading out
[Hold] The cellar door - wave 3 of 4                      ♪ the battle piece, looping until the last wave
```

**Why:** Reactive Music already steps aside for any sound named in its config, so the story's moments never fight the everyday music.

### The moments

Each quest adds its moments here as it is built, along the road's curve (`story.md`, S25).

| Moment | Sound | Source |
|---|---|---|
| The stranger's death (P1), from his cough through the burial, swelling in over sixteen seconds and low under the lines | What We Don't Say, piano, strings, winds and voice, its first four minutes, once | Scott Buckley, CC BY 4.0, [scottbuckley.com.au](https://www.scottbuckley.com.au/library/what-we-dont-say/) |
| The stranger presses the ember into the player's hand (P1) | mystic 2, ambient magic | Zixem, CC0, [Freesound](https://freesound.org/people/Zixem/sounds/69506) |
| The ember wakes at dusk (P2) | Magic 2.Wouw+SinePad, a soft warm swell | gsb1039, CC0, [Freesound](https://freesound.org/people/gsb1039/sounds/430894) |
| The hold at the cellar door (P3), and every horde night | Heartfelt Battle, looping while it lasts | request, CC0, OpenGameArt |
| The dawn after the hold (P3) | Emotional Original Soundtrack - BETTER LIFE, piano and strings from its thirtieth second, once | Magmi.Soundtracks, CC BY 4.0, [Freesound](https://freesound.org/people/Magmi.Soundtracks/sounds/522207) |
| The evening at the caravan's first fire (P3) | Amberlight, soft piano with strings coming in under it, once, from its start | Scott Buckley, CC BY 4.0, [scottbuckley.com.au](https://www.scottbuckley.com.au/library/amberlight/) |
| The Gazebound's horn: across the water at the ford before anything shows (1.3), and calling them back after; theirs wherever they come later | Battle_horn_1, one breathy war-horn blast | kirmm, CC0, [Freesound](https://freesound.org/people/kirmm/sounds/392180) |
| The battle piece at the ford (1.3) | Heartfelt Battle, looping while the hold lasts | request, CC0, OpenGameArt |
| The first memory, His First Camp (1.2), from waking in it until waking from it | Home Was You, piano, strings and accordion, once | Scott Buckley, CC BY 4.0, [scottbuckley.com.au](https://www.scottbuckley.com.au/library/home-was-you/) |
| A shard is taken | Glass-metal Resonant Transition 13, glass and metal resonating | newlocknew, CC-BY 4.0, [Freesound](https://freesound.org/people/newlocknew/sounds/711059) |
| True-sight shows a false wall, first beside the Marsh Mothers' calling stone (1.4) | 9. Esfumar Vanish, a soft smoky whoosh, its 1.7 s middle | cabusta9, CC0, [Freesound](https://freesound.org/people/cabusta9/sounds/444710) |
| The hooded woman leaves the caravan's fire in smoke (1.5) | Fire Torch Whoosh 2, a low, breathy gust through a fire with a smoky tail, its first 1.9 s | kev_durr, CC BY 4.0, [Freesound](https://freesound.org/people/kev_durr/sounds/426209) |
| A reward is taken from the journal | Item Received, things stowed into a satchel | spookymodem, CC0, [Freesound](https://freesound.org/people/spookymodem/sounds/249815) |
| A creature of the gaze crumbles at dawn | Cinematic Dragon Sound - Dragon Hiss 4, its first 0.85 s | Soughtaftersounds, CC BY 3.0, [Freesound](https://freesound.org/people/Soughtaftersounds/sounds/239650) |
| The caravan's camp, heard within about 64 blocks while it stands | Bonfire, looping | Electroviolence, CC0, [Freesound](https://freesound.org/people/Electroviolence/sounds/234542) |
| A burning town, heard within about 128 blocks before it shows | FIREPLACE, looping | leosalom, CC BY 3.0, [Freesound](https://freesound.org/people/leosalom/sounds/234288) |
| A blow on the door at a hold | Door Hits; multiple, fists pounding wood | RossBell, CC0, [Freesound](https://freesound.org/people/RossBell/sounds/389418) |
| A hall's calling stone, near its entrance | Choir Hum Loop for Game Loot, looping | armandem, CC0, [Freesound](https://freesound.org/people/armandem/sounds/825228) |
| A memory opening | Bell 1 reverse, its last 5 s, fading in over the first 2 | Mornagar, CC0, [Freesound](https://freesound.org/people/Mornagar/sounds/160734) |
| The dawn warning of a hard night | Dark Horn Blast | hykenfreak, CC BY 4.0, [Freesound](https://freesound.org/people/hykenfreak/sounds/218072) |
| A creature of the gaze climbs out of the ground at night | Growl - Large Creature, Fantasy, eased out at its end | 8bitmyketison, CC0, [Freesound](https://freesound.org/people/8bitmyketison/sounds/699932) |
| The ember flares a step brighter towards a felt goal | Fire.wav, a short flare whoosh | danielpodlovics, CC0, [Freesound](https://freesound.org/people/danielpodlovics/sounds/530355) |
| The ember gutters a step darker, away from a felt goal | embers 4, embers crackling down | AuDRoger, CC0, [Freesound](https://freesound.org/people/AuDRoger/sounds/471158) |
| The held ember lights the clues and tracks around | Shiny Object of Value - Rare Loot Find | LilMati, CC0, [Freesound](https://freesound.org/people/LilMati/sounds/659677) |
| A goal's mark appears on the compass | Hand Crank Music Box (E5 Note), one music-box note | moodyfingers, CC0, [Freesound](https://freesound.org/people/moodyfingers/sounds/732942) |
| A tent pitched | Umbrella Close 3, camping canvas closing with a click | Geoff-Bremner-Audio, CC BY 4.0, [Freesound](https://freesound.org/people/Geoff-Bremner-Audio/sounds/790255) |
| A tent packed up | Canvas Tent 1, canvas rustling | kevinkace, CC0, [Freesound](https://freesound.org/people/kevinkace/sounds/66773) |
| Stepping into a lit fire's circle | Fire, Campfire, Bonfire, a campfire's crackle swelling in, cut to about 3 s | yaros_nov, CC0, [Freesound](https://freesound.org/people/yaros_nov/sounds/434026) |
| A quest begun: the parchment banner under the compass (`interface.md`, I2) | Magic 3.Glitter+Arrival, a glittering arrival | gsb1039, CC0, [Freesound](https://freesound.org/people/gsb1039/sounds/430893) |
| A quest done: the banner | Level Up/Mission Complete (Fantasy) | SilverIllusionist, CC BY 4.0, [Freesound](https://freesound.org/people/SilverIllusionist/sounds/665203) |
| A task ticked in the panel (I1) | Tarea completa, its first second | valeulloam97, CC0, [Freesound](https://freesound.org/people/valeulloam97/sounds/541389) |
| A friend marks a spot for the team (Ping Wheel's mark), from the mark's direction, in place of Ping Wheel's digital chime | Kalimba C3, one warm pluck, 2 dB under the other frequent sounds | dvdfu, CC0, [Freesound](https://freesound.org/people/dvdfu/sounds/536549) |
| A new person, word or page in the journal: the gold dot on its ribbon (I3) | Pencil, Writing, Close, A, cut to 4 s and eased out | InspectorJ, CC BY 4.0, [Freesound](https://freesound.org/people/InspectorJ/sounds/398271) |
| The journal opened, a ribbon or an entry chosen | Page Turn, a page flip | qubodup, CC0, [Freesound](https://freesound.org/people/qubodup/sounds/442857) |
| An answer chosen in a conversation (I4) | aturax_leatherBookUI_92, a wooden click | GeoffAturax, CC BY 4.0, [Freesound](https://freesound.org/people/GeoffAturax/sounds/532189) |
| The band speaks after a quiet moment: the first line of a talk or a lone notice (I4) | Wood Click 1, a tiny dry wood tap | Sawuare, CC0, [Freesound](https://freesound.org/people/Sawuare/sounds/500926) |
| The second memory, The Theft (1.7), from waking in it until the lake | Desolate (Part II), its first 160 s, once | Matthew Pablo, CC BY 3.0, [OpenGameArt](https://opengameart.org/content/desolate) |
| The third memory, The Bell (1.10), from waking in it until waking from it | Memories of a Lost Autumn, harp, accordion and flute, once | InspectorJ, CC BY 3.0, [OpenGameArt](https://opengameart.org/content/memories-of-a-lost-autumn-rpg-orchestral-essentials-conversation-music) |
| The red moon's hold at the caravan (1.10) | Heartfelt Battle, looping until the last wave | request, CC0, OpenGameArt |
| Tamsin's bell, rung once each evening as the camp goes up while the team keeps it (1.10) | Church Bell 10, one stroke of a large bell, its tail eased out | nomiqbomi, CC0, [Freesound](https://freesound.org/people/nomiqbomi/sounds/578542) |
| The Name at the caravan's fire (1.11), from the hooded woman's first line to her leaving | Forget Me Not, solo piano, its first 160 s, once | Kistol, CC0, [OpenGameArt](https://opengameart.org/content/forget-me-not) |
| The night on the water (2.2), beside Tamsin's boat while the sea's beast and the drowned come | The battle piece, looping | As at every hold |
| Act 2's first morning (2.2), from the first sight of the far shore over the landing | White Dawn, slow strings and pads lifting, 4 s to 100 s, fading over the last 16 s, once | Scott Buckley, CC BY 4.0, [scottbuckley.com.au](https://www.scottbuckley.com.au/library/white-dawn/) |
| His last camp (2.5), the memory of the night he gave in, from its first line | A Dragon's Lullabye, harp and strings, a dark lullaby, once | Scott Buckley, CC BY 4.0, [scottbuckley.com.au](https://www.scottbuckley.com.au/library/a-dragons-lullabye/) |
| The title screen and the loading screens after it, until the world shows (`interface.md`, I6) | FORGIVENESS, piano, strings and a music box, looping | Magmi.Soundtracks, CC0, [Freesound](https://freesound.org/people/Magmi.Soundtracks/sounds/478246) |

Every sound the pack makes for its own things is its own, from a free library, chosen by ear; where a file is cut or faded, the table says so.

**Why:** Strings carry the death and the morning after it, a looping piece holds a fight of unknown length, a guitar suits a fire among friends, and sounds the player has never heard in Minecraft make the pack's moments its own. Each is judged by ear in the playtest.

### The land's sound and the everyday music

- Everyday music is Reactive Music's own songpack, Adventure Redefined, with every set it ships, stepping aside for the story's pieces. Its songs are the mod's: Vindsvept's under CC BY 4.0, Adrian von Ziegler's for non-commercial use, Mufaya's with no licence stated. The pack takes them up again only if that becomes a problem, such as a claim on a player's video or a mod that changes its terms.
- The land keeps AmbientSounds (birds, insects, wind, water and caves by land and hour), Sound Physics Remastered (echo in caves and halls, sound muffled through walls), Distant Thunders (thunder late by distance) and Sounds (about 170 effects for the interface, chat, items and blocks). Each is judged by ear in the playtest and configured only where it jars; their volumes are A7's.
- The pack's sound aims to be one coherent whole, not vanilla's: a sound of the pack's own is judged beside what plays around it, vanilla's and these mods' alike (A5, A6).
- The title screen plays the pack's own piece, FORGIVENESS (The moments), in place of Reactive Music's title songs.

**Why:** The songpack and the land's mods already give the road and the land their sound, so the pack changes them only where the playtest or a licence finds a fault. A pack that sounds like one game matters more than one that sounds like vanilla, since its mods already move it away from vanilla everywhere.

### The mix

- Each of the pack's sounds sits under the slider a player would look for it under:

  | Slider | The pack's sounds |
  |---|---|
  | Music | The story's pieces and the title piece |
  | Players | What the story gives (the ember given and waking, a shard, a reward), the ember flaring and guttering, the clues' glow, the compass's mark |
  | Blocks | The tent |
  | Ambient | The fire's circle, the caravan's camp, a burning town, a calling stone |
  | Hostile | The gaze's creatures rising and crumbling, the blows on a door, the hard night's horn |
  | Master | The interface: the banners, a task ticked, a journal entry, a page, an answer, as vanilla's own clicks are |
  | Voice | The narrator (`narration.md`, N1) |

- The story wins, friends are left alone: Reactive Music fades out under a story piece and the land's sounds play on; while the narrator reads, all music dips to half and the land's sounds to two thirds, and comes back after. Simple Voice Chat lowers nothing and nothing lowers it; each player sets it with its own slider.
- How loud a sound is follows how often it comes, measured beside what plays around it: a rare moment, a shard, the ember waking, a quest done, the horn, as loud as vanilla's level-up; a frequent one, flaring, guttering, a tick, a page, an answer, about half that; a loop, the camp, a burning town, a calling stone, under the land's own ambience, swelling as the player comes near and fading with distance.

**Why:** A player who finds the horn too loud reaches for Hostile, and one who wants quiet menus for Master, as they do in vanilla. A sound heard thirty times in a search has to be quiet to stay welcome, and a moment that comes twelve times in the whole game can afford to be heard; friends talking is the player's own business, so voice chat is left to its own slider.

## Open

Nothing is open; each quest adds its moments to The moments.

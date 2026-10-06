# Sound

Everything the player hears but the voice (`narration.md`): what plays where, and where it comes from. It is built with each quest from the bar on (`slice.md`, step 4), and every piece and sound is chosen with the `sound` skill and put to the user by ear.

## Settled

### Music for the story's moments

Music plays at points the story chooses, not by place and weather: strings when someone dies, a fight's piece at a hold, something lighter on a good night at the fire. Everyday play, the road, ordinary days and nights, keeps Reactive Music and its own songs.

**Why:** A loss the player has watched coming lands hardest with music under it, and the road's quiet makes the moments that have music stand out.

### Free to use

Every sound effect and piece of music the pack ships is free to use in it. Effects come first from Minecraft's own sound events, referenced and never copied; then CC0 and public domain (Freesound, OpenGameArt, Musopen's recordings on Wikimedia Commons, found through Openverse); then CC-BY 4.0 with its credit in the pack; Pixabay last. Nothing NC or ND, and not Free To Use, whose free licence covers only videos. A classical piece is free only when its recording is CC0 or public domain too.

**Why:** The pack is shared and played on servers, so nothing in it may need a licence bought or asked for, and Minecraft's own sounds keep the pack sounding like Minecraft.

### How a piece plays

The Wayfarer mod plays a story piece itself, in the Music category, and Reactive Music fades its own music out over a few seconds while it plays and back in after (its `soundsMuteMusicIgnoreDistance`). A piece started by a line plays once to its end; a piece that belongs to a step, such as a hold, loops until the step ends and then fades out.

```text
<The stranger> Don't let it... the shards. I took them.   ♪ The Death of Åse, Reactive Music fading out
[Hold] The cellar door - wave 3 of 4                      ♪ the battle piece, looping until the last wave
```

**Why:** Reactive Music already steps aside for any sound named in its config, so the story's moments never fight the everyday music.

### The moments

Each quest adds its moments here as it is built, along the road's curve (`story.md`, S25).

| Moment | Sound | Source |
|---|---|---|
| The stranger's death (P1), from his cough through the burial | Edvard Grieg, The Death of Åse (Peer Gynt Suite No. 1), once | Musopen Symphony, CC0, Wikimedia Commons |
| The stranger presses the ember into the player's hand (P1) | A respawn anchor charging, low, under amethyst ringing | Minecraft |
| The ember wakes at dusk (P2) | A fire charge, low, then a beacon starting | Minecraft |
| The hold at the cellar door (P3), and every horde night | Heartfelt Battle, looping while it lasts | request, CC0, OpenGameArt |
| The dawn after the hold (P3) | Edvard Grieg, Morning Mood (Peer Gynt Suite No. 1), once | Musopen Symphony, CC0, Wikimedia Commons |
| The evening at the caravan's first fire (P3) | Calm fireplace guitar song, once | ShggothSlave, CC0, OpenGameArt |
| A shard is taken | An eye set into an End portal frame, then a conduit waking | Minecraft |
| A reward is taken from the journal | Something going into a bundle, then an item picked up | Minecraft |

**Why:** Strings carry the death and the morning after it, a looping piece holds a fight of unknown length, a guitar suits a fire among friends, and what the player gets sounds like Minecraft because it is Minecraft. Each is judged by ear in the playtest.

## Open

### A4 The land's sound and the everyday music

What the land sounds like by land, hour, weather and season, and a verdict on the mods that make it today, each on its defaults: AmbientSounds, Sounds, Sound Physics Remastered and Distant Thunders. And the everyday music: Reactive Music plays its own songpack, Adventure Redefined (61 tracks by Vindsvept, Adrian von Ziegler and Mufaya), with sets for the title screen, a home (a position saved per player, in a pack about never settling), bosses and monsters nearby; to decide which of its sets stay.

### A5 The pack's sounds still to make

The ember flaring and guttering, the compass, the clues' glow, the tent, the fire's circle, the caravan's camp, a burning town heard before it shows, a door straining at a hold, the gaze's creatures rising and crumbling (sand breaking, today), the calling stone, a memory opening, and the dawn warning of a hard night (the raid horn, today): a sound for each, and where it comes from.

### A6 The interface's sounds

A quest begun or done, a task ticked, a journal entry, a conversation's line. Taking a reward has its sound.

### A7 The mix

What is loud and what gives way: to the voice, and to Simple Voice Chat; each sound's category; and how the story's music meets the land's sound and Reactive Music's.

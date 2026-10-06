---
name: sound
description: Choose and ship Wayfarer's music and sound effects - what plays at a story moment, which sound marks getting the ember, a shard or a reward - from sources the pack may use, put to the user by ear before anything ships. Use whenever a step, an item or a system gets a sound or a piece of music, or when one is changed; the art skill gives things their look, the lore skill their words, this one their sound.
---

# Sound

`design/sound.md` says what plays where. The agent cannot hear: it can prove where a sound comes from and that the pack may use it, never how it sounds. So every sound and every piece is chosen by the user, by ear.

## Before choosing a sound

1. **Know what it tells the player.** Find the moment or the thing in `design/lore.md` and `design/story.md`: who makes the sound, what it means, how often the player hears it. A fact the lore does not have goes to the user first.
2. **Find its vanilla relative.** Every sound has a vanilla cousin: the respawn anchor beside the ember, the End portal frame beside a shard, the raid horn beside a hard night's warning. Reuse or pitch vanilla's before looking further. Vanilla's events are listed in `minecraft/sounds.json` and its files kept in the game's asset objects, found through the index the dev environment downloads (`~/.gradle/caches/neoformruntime/assets/indexes/17.json`, each entry's hash naming its file under `objects/`); try one in game with `/playsound`.

## What gets sound

- **Music only for the story's moments.** A death, a hold, the morning after, a good night at the fire. Everyday play, the road, the land, the weather, ordinary days and nights, stays with Reactive Music and its own songs; the pack's pieces are rare, so they mean something.
- **A sound for what the story gives.** Getting the ember, its waking, a shard, a reward: anything the player gets that matters is heard as it arrives.
- **Every step's design names its sound.** A step that is a moment says which piece or sound it has, or that it has none, in its design.

## Where it may come from

In this order; nothing else ships.

| Source | Use |
|---|---|
| Minecraft's own sound events | First for effects. Referenced by name in `story/sounds.toml`, never copied, so no licence question; tried in game with `/playsound` |
| CC0 and public domain: [Freesound](https://freesound.org), [OpenGameArt](https://opengameart.org), Wikimedia Commons (Musopen's recordings) | Found through [Openverse](https://openverse.org), whose API needs no key: `https://api.openverse.org/v1/audio/?q=<words>&license=cc0` |
| CC-BY 4.0 (Freesound, [Scott Buckley](https://www.scottbuckley.com.au/library/), incompetech) | With its credit line in `story/sounds.toml`, which the generator writes into the pack's credits |
| [Pixabay](https://pixabay.com) | Last: its own licence allows use inside the pack, never as a file on its own; record the licence page as it read on the download date |

Never: a file whose licence is unknown, anything NC or ND, [Free To Use](https://freetouse.com) (its free licence covers only videos, and games need a paid one), music from a game, film or band, or a classical piece whose recording is not free too. A classical piece is free only when the composer died over 70 years ago and the recording is CC0 or public domain.

## Put it to the user

Before a sound or a piece ships, put it to the user with the `questionnaire` tool, one question per sound:

- **Where** it plays: the moment, the step and the line, or the item.
- **What it is for**: what the player should feel or understand, and how often they will hear it.
- **Two to four options**, the recommended one first: for a Minecraft sound the `/playsound` line that plays it, for any other its page link, licence and length.

Only the chosen option ships. When the user says to pick, take the recommended ones and say so in the report, so the playtest judges them.

## Ship it

- **Sources live in `story/sounds.toml`.** A cue (`[cues.<id>]`) is layers of sound events, each with its pitch, volume and delay in ticks; a piece (`[music.<id>]`) has its file, credit, licence, page and download date, so the proof stays with the pack. `just generate` writes `sounds.json`, the cues and the credits from it; never edit those.
- **Files.** Music is OGG Vorbis, stereo, streamed, in `mod/src/main/resources/assets/wayfarer/sounds/music/<id>.ogg`, its loudness matched to the others with ffmpeg's `loudnorm` (integrated -18 LUFS, true peak -1.5 dB). An effect from outside Minecraft is OGG Vorbis in `sounds/effects/`, mono for anything placed in the world, since Minecraft plays a stereo file with no direction and no falloff; cut tight with short fades; with several variants when the player hears it often; its loudness measured with `loudnorm` beside its vanilla relative. Keep the download in `/tmp/`, never in the repository.
- **Events.** Every sound event the pack adds, but a piece of music, has a subtitle written with the `lore` skill, and plays in the category `design/sound.md` A7 decides.
- **How it plays.** A line's `sound` plays a cue to every player as the line is said, and a line's `music` starts a piece once; a step's `music` loops while the step lasts and fades out when it ends. Story music plays in the Music category, and Reactive Music fades its own out while it plays (`pack/config/ReactiveMusic.json5`).

## Check it

The hidden dev client is silent, so prove a sound by its event, never by ear: the log names every cue (`cue <id> for <player>`, on the server) and every piece (`story music <id>`, on the client), and a missing file shows as a sound warning in the client log (`log`). The playtest skill's checklist names each sound for the user to judge by ear.

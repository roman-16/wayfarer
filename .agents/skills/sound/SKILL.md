---
name: sound
description: Choose and ship Wayfarer's music and sound effects - what plays at a story moment, which sound marks getting the ember, a shard or a reward - from sources the pack may use, put to the user by ear before anything ships. Use whenever a step, an item or a system gets a sound or a piece of music, or when one is changed; the art skill gives things their look, the lore skill their words, this one their sound.
---

# Sound

`design/sound.md` says what plays where. The agent cannot hear: it can prove where a sound comes from and that the pack may use it, never how it sounds. So every sound and every piece is chosen by the user, by ear.

## Before choosing a sound

1. **Know what it tells the player.** Find the moment or the thing in `design/lore.md` and `design/story.md`: who makes the sound, what it means, how often the player hears it. A fact the lore does not have goes to the user first.
2. **Hear what plays around it.** The pack's sounds are its own, new to the player, not Minecraft's: a moment the player has never heard before is a moment they notice. But it sits among what the player already hears, vanilla's and the pack's mods' sounds (`design/sound.md`, A4), so a new sound matches them in loudness and does not clash in character; judge it beside them. Vanilla's events are listed in `minecraft/sounds.json` and its files kept in the game's asset objects, found through the index the dev environment downloads (`~/.gradle/caches/neoformruntime/assets/indexes/17.json`, each entry's hash naming its file under `objects/`); try one in game with `/playsound`.

## What gets sound

- **Music only for the story's moments.** A death, a hold, the morning after, a good night at the fire. Everyday play, the road, the land, the weather, ordinary days and nights, stays with Reactive Music and its own songs; the pack's pieces are rare, so they mean something.
- **A sound for what the story gives.** Getting the ember, its waking, a shard, a reward: anything the player gets that matters is heard as it arrives.
- **Every step's design names its sound.** A step that is a moment says which piece or sound it has, or that it has none, in its design.

## Where it may come from

In this order; nothing else ships.

| Source | Use |
|---|---|
| CC0 and public domain: [Freesound](https://freesound.org), [OpenGameArt](https://opengameart.org), Wikimedia Commons (Musopen's recordings) | First. Found through [Openverse](https://openverse.org), whose API needs no key: `https://api.openverse.org/v1/audio/?q=<words>&license=cc0` |
| CC BY 3.0 or 4.0 (Freesound, [Scott Buckley](https://www.scottbuckley.com.au/library/), incompetech) | With its credit line in `story/sounds.toml`, which the generator writes into the pack's credits |
| [Pixabay](https://pixabay.com) | Its own licence allows use inside the pack, never as a file on its own; record the licence page as it read on the download date |
| Minecraft's own sound events | Only where the thing is vanilla's own, a vanilla block or creature. Referenced by name in `story/sounds.toml`, never copied |

A mod's own sounds and music come with the mod under its licence and are judged by ear like the rest (Reactive Music's songpack, `design/sound.md` A4). What the pack chooses itself never is: a file whose licence is unknown, anything NC or ND, [Free To Use](https://freetouse.com) (its free licence covers only videos, and games need a paid one), music from a game, film or band, or a classical piece whose recording is not free too. A classical piece is free only when the composer died over 70 years ago and the recording is CC0 or public domain.

## Listen before the user does

The agent hears through an audio model: `just hear "<question>" [--context <label>=<file>]... [--each] <label>=<file>...` (or `hear()` and `hear_each()` in `scripts/hear.py`) sends the clips to Qwen3.8 Omni Flash on OpenRouter, signed in through pi's own OpenRouter login, and prints its answer. Use it on every candidate and every cut before the user hears it, and to choose when the user leaves the choice to it.

- **One call per candidate, all at once.** A call takes a minute or more, so candidates are never asked about one after another: `--each` asks about every clip in its own call at the same time, each with every `--context` clip before it (the `--context` options come before the candidates), and prints the answers under their labels. Ask about the candidates together in one call only to have them compared side by side.
- **Give it the picture, not just the file.** A judgement is only as good as what it knows, and more context judges better than less. Every call carries the brief in `scripts/hear.py` (the pack, its story, its tone, its music); add to that:
  - **The moment as the player lives it:** what they do and see, what happens just before and just after, the line chat shows, who hears it (one player, the team, anyone near), and how often.
  - **Everything this moment has had:** every clip ever proposed, chosen or rejected for it, the one that plays now and the ones the user turned down, each labelled with what happened to it and the user's own words ("NO HOPE, rejected: too rushed"), found in `story/sounds.toml`, `design/sound.md` and the session's earlier rounds. The model then judges against what the user already heard and said, never only against the brief.
  - **The relatives already in the pack:** the sounds chosen for the moments around it, labelled "in the pack", so it judges how the new one sits beside them; for an ember sound, the other ember sounds; for an interface sound, the page, the tick and the compass mark.
  - **The world around it:** a vanilla relative from the game's assets (the index in Before choosing a sound) and twenty seconds of the everyday music from Reactive Music's songpack (`build/sources/mods/reactivemusic-*/musicpack/music/`), so it hears the pack's character.
  - **The candidates, each labelled** with a letter and what it is, and the question last: a line and a score per candidate, its choice with the reason, then where to start and end it, the fades, and the level against a named relative.
- **Ask neutrally first.** Without the brief and the moment (`--plain`), it is asked only "What do you hear?": told it is a door, it describes a door, and it calls a second of silence "a musical note".
- **Trust the ear for character, the meter for numbers.** Loudness and length come from ffmpeg (`ebur128`, `ffprobe`), and so does whether an end fades: the model calls a faded end abrupt, so measure the last tenth of a second against the peak (`-sseof -0.1`, `volumedetect`; 24 dB under the peak or more is a fade). It cannot know a loop repeats, so its "abrupt end" on a loop is the seamless join. It judges what is heard, what jars, what fits and where to cut.
- **What it says becomes data.** Its cuts go into the sound's `cut`, its level into `gain` (dB against the sound's loudness class, a power of two or a sum of two), and `just sounds <id>` makes the file again; then hear it again.
- **It advises; the user decides.** Its findings go with the options put to the user; it chooses only where the user leaves the choice to it, and the report says it did.

## Put it to the user

Before a sound or a piece ships, put it to the user with the `questionnaire` tool, one question per sound:

- **Where** it plays, told as the player lives it: what they are doing, what they see, what happens just before and after, and the step or item it belongs to.
- **What it is for**: what the player should feel or understand, and how often they will hear it.
- **Two to four options**, the recommended one first: for a free sound its page link, licence, length and what its maker says it is; for a Minecraft sound the `/playsound` line that plays it.

Only the chosen option ships. When the user says to pick, take the recommended ones and say so in the report, so the playtest judges them.

## Ship it

- **Sources live in `story/sounds.toml`.** A sound (`[effects.<id>]`) has its credit, licence, page and download date, so the proof stays with the pack, and how it is made and heard: its `kind` (`rare`, `frequent` or `loop`, the loudness classes of `design/sound.md` The mix), a `gain` in dB against that class where the ear asks for it, its `slider`, a `cut` (`start`, `length`, `fade_in`, `fade_out`, in seconds) where only part of the file is wanted, and a `reach` in blocks for a sound placed in the world. A piece (`[music.<id>]`) has the same proof, and `loop = true` where it loops. Its subtitle is `subtitles.wayfarer.<id>` in the language file, written with the `lore` skill. `just generate` writes `sounds.json`, the mod's sound data and the credits from it; never edit those.
- **Files.** `just sounds [ids]` makes them from `story/sounds.toml`: it fetches each sound's preview from its Freesound page into `/tmp/`, cuts it, makes a placed sound mono (Minecraft plays a stereo file with no direction and no falloff), joins a loop's end into its start so it repeats without a seam, and matches its loudness to its class with ffmpeg's `loudnorm`: a rare sound at vanilla's level-up (-17 LUFS), a frequent one 6 dB under, a loop at -30 LUFS, a piece of music at -18; true peak -1.5 dB. Effects land in `mod/src/main/resources/assets/wayfarer/sounds/effects/<id>.ogg`, music in `sounds/music/<id>.ogg`, OGG Vorbis. A very short, punchy sound may stay under its class where its peak already touches the ceiling; judge it by ear.
- **How it plays.** `Sounds.play` plays a sound to one player, to every player or in the world at a spot, under its slider; `Sounds.loop` keeps a loop going at a place while any player is within its reach. A line's `sound` plays to every player as the line is said, and a line's `music` starts a piece once; a step's `music` loops while the step lasts and fades out when it ends. Story music plays in the Music category, and Reactive Music fades its own out while it plays (`pack/config/ReactiveMusic.json5`).

## Check it

The hidden dev client is silent, so prove a sound by its event, never by ear: the log names every sound played to a player (`sound <id> for <player>`, on the server) and every piece (`story music <id>`, on the client), and a missing file shows as a sound warning in the client log (`log`). The playtest skill's checklist names each sound for the user to judge by ear.

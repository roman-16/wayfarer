# Design

What Wayfarer is becoming, decided here before anything is built. Each document keeps its **Settled** decisions, every one with its reason, apart from its **Open** questions, numbered so an answer can name them. Sessions run through the `design` skill.

## Now

**Phase 4, Vertical slice.** Its done-when is not reached: friends have not played Act 1 blind yet. The building has run ahead of it: the whole road is built as the road of about twelve hours has it.

- **Settled and built:** the road of about twelve hours (`vision.md`, D8; `story.md`, S2, S6, S16, S25): 23 main quests and six halls, the heir carrying the other five shards to the frame, walked the whole way with the sea crossed once and the land beyond it walkable (`systems.md`, M39, M40), no flight, the story told plainly (D18), and recording on the user's word (`narration.md`). `slice.md` and `acts.md` say, step by step, what each quest is.
- **Built:** the bar (the stamp on every screen, the front door, the graphics Preset and the options, the sounds and music) and the whole road as the twelve-hour road has it: Act 1, six quests and three halls (`slice.md`, steps 1 to 8); Acts 2 and 3, eleven quests and three halls, and the finale with the worlds after each door and Years later, the epilogue told by a grown Pip (`acts.md`); every side quest; every line rewritten to say the thing itself (D18); the world's shape (`systems.md`, M39): the home land, a deep sea about 2,048 blocks wide round it, the outer continent out to the Last Sea with its fog and cold (M13), and the ring of frames at about 17,408 blocks, with every hall put down ahead by its quest (M40); the deeds (`systems.md`, M38); the journal's Afterwards pages (`interface.md`, I9); the Promised Home at the team's own last fire when nobody of the caravan is left (`story.md`, S49); only the narrow caves reaching the surface (`systems.md`, M39); and the credits written from the pack (`production.md`, R5). Not built yet: the lands where a shard was left staying dark after Give it its sight (`story.md`, S8), where the build keeps only the caravan's home (`systems.md`, M14).
- **Played:** the user played the prologue and 1.1 twice, and both playtests' notes are built in. An agent played the whole road from the title screen to 2.4 as a newcomer, at its real distances (`playtests/2026-10-16_16-58`), and its findings are built in: a player alone now gets armour from Hob's patterns, nights and falls that end, shade that cools, and each act's foes only in their act. Everything else has been played only by agents through the driver, from named states that skip the travel between steps.
- **Judged in the user's absence:** the looks and sounds chosen during the away runs (`acts.md`, `slice.md` steps 6 to 8) wait for the user's eye and ear.

Next: **the user plays** in the exported pack, with the `playtest` skill's checklist, from the title to the doors on foot. Then friends play Act 1 blind on the server, with the play record (R3) built once the user says they will play, and its report setting each quest against its budget (R6); that ends phase 4. Before that blind play the R2 build ships: version numbers, story migrations within a major, and the pack hosted so each Prism instance updates itself on launch; the friends' server, made before the world's shape, has its road carried to a fresh world (`production.md`, R2). Every line is read aloud with its audio tags, and recording waits for the user's word (`narration.md`). The first release is private, to friends, in English, with credits written from the pack (R4, R5); publishing on Modrinth is decided after the blind plays.

## Phases

| Phase | Produces | Done when |
|---|---|---|
| 1 Vision | `vision.md`: pillars, the player's fantasy, the loops, the twist, the ending, scope, references, what is out | Two sentences say what the game is and what the player does each hour |
| 2 Story and world | `story.md`, and `lore.md` rewritten from it: acts, beats, characters, factions, regions, every main and side quest with its place, verb, gate and turn | Every main and side quest has a place, a verb and a turn |
| 3 Systems | `systems.md`: progression and gates, the quest log, road encounters, dialogue, the voice on the road, a verdict on every vanilla system and every Wayfarer mechanic, and a spike proving each new system can be built | Every mechanic has a reason in the lore and a spike that works |
| 4 Vertical slice | The prologue and 1.1 at shipping quality in sound, music, interface and the front door, within the performance budget; then the rest of Act 1 built to that bar | Friends who never read `design/` played Act 1 blind, the session record shows no stall the screen did not explain, and it was not boring |
| 5 Production | The other acts, one at a time, every quest built to the bar | Every act is in, each played blind |
| 6 Polish | Balance across the whole road, the mix across acts, and the release (`production.md`) | |

## Documents

| Document | Holds | State |
|---|---|---|
| `interface.md` | The HUD, the pack's screens and the front door: everything read off the screen outside the world | Settled |
| `lore.md` | What is true in the world, written from `story.md`; the pack's texts are written from it | Canon |
| `mods.md` | Mods, resource packs and data packs weighed for the pack, each with a verdict | Picked: every Use goes in with phase 4 |
| `narration.md` | How every line is read aloud and played, who reads it in which voice, how it is recorded and kept recorded, and why | Settled |
| `production.md` | How the pack reaches its players and keeps up with them: the performance budget, updates and saves, blind players, languages, the release | Settled |
| `acts.md` | How phase 5 builds Acts 2 and 3 and the finale, quest by quest, and each step's state | Built at twelve hours |
| `slice.md` | How phase 4 builds Act 1, quest by quest, and each step's state | Built at twelve hours |
| `sound.md` | Everything the player hears but the voice: what plays where, and where it comes from | Settled; each quest adds its moments |
| `story.md` | The story, act by act | Settled |
| `style.md` | How everything looks: the stamp every screen and the HUD wear, and how the world's blocks, items, creatures, skins and structures are made | Settled |
| `systems.md` | Every mechanic, why the world has it, and the spike that proves it | Settled |
| `tooling.md` | How the pack is seen, tried and built, by coding agents: the worlds, the commands, the tools | Settled |
| `vision.md` | The pillars, the loops, the twist, the ending, the scope | Settled |

# Narration

Everything in Wayfarer that tells the story in words is read aloud: the talk on the band, books and lecterns, the journal, the ending and the recap. The audio comes from ElevenLabs, recorded from the same text the player sees, and plays in the Voice/Speech sound category at twice the gain of its sliders (`design/sound.md`, The mix). A line is recorded when the user says to record (Recording). This file holds the decisions.

## How it plays

- **Chat waits for its voice.** A line on the band is spoken as it appears and stands while it is spoken; the next line comes a beat after it ends (`balance.toml` `[talk]`). A line still silent stands for its reading time, as before it was recorded. This holds for the voice, Malgven and every person the player talks to.
- **A voice comes from where its speaker stands.** A person's line sounds from their body, quieter and to one side as the player stands farther off or turns, as every other sound does, and from the tent's door for a player inside a tent. The voice in the ember and the narrator sound inside the player's head.
- **The player has a voice of their own.** The first time the player presses Singleplayer or Multiplayer, the game asks for it before the list opens: a man, a woman, or silent; Music & Sounds changes it later. Until it is answered, the man or the woman goes by the skin's arms (slim arms a woman). It says the answers they choose, from their body, so teammates near them hear it too, and it reads their journal to them. A team's vote is spoken by the player whose vote decides it.
- **A book reads the page that is open.** Opening a page plays it in its author's voice; turning to another stops it and plays that one. A page plays once while the player is in the world: turning back to a page already heard stays silent, and a small play button on the page plays it again on purpose. This holds for books in the hand, at a lectern and the halls' books. A page in the old script the player has not learnt stays silent and has no button.
- **The journal reads an entry when asked.** Read aloud on an entry reads it in the player's voice; nothing in the journal is read on its own, and choosing another entry stops it.
- **The ending reads its leaves.** The ending, the Afterwards pages and Years later play each leaf as it is turned to, grown Pip's leaves in his voice and the rest in the narrator's. The credits stay silent.
- **The recap reads its newest entry.** Joining a world, the loading screen shows the journal's last page as before and reads its newest entry in the player's voice, or the first page's line in the narrator's for a world not yet begun. The loading screen stands at least 1 s, and once the world is ready the voice fades out over 1 s as the world appears.
- **One voice at a time.** A new line on the band cuts off what is left of the one before, a turned page replaces the page before it, and everything else waits behind the voice that is speaking. Lines that follow one another on their own, on the band or within an entry or a leaf read in several, are parted by the same beat of a second (`balance.toml` `[talk]`); a line the player cuts in on, by turning a page, starts at once. A line on the band waits while a page is read, at most as long as it waits for any open screen.
- **Music makes room.** The pack's own music drops while a voice speaks and comes back after it.
- **What is not read:** item names and tooltips, buttons and menus, the compass's labels, warnings on the action bar, signs, and the credits.

Edge cases: a player who walks off mid-line hears the voice fade with the distance, and one beyond talk reach gets the line in chat without a voice; a line goes on when the player steps into or out of a tent; closing the voice question with Escape goes back to the title and asks again the next time, and a game launched straight into a world or server never asks; the game paused pauses the voice; a faster `/tick rate` cuts a clip off when the next line comes; Voice/Speech at 0 silences every voice and the lines keep their spoken length; a player who picked silent hears the others' answers but says their own in silence; a world saved with an older text of a journal entry reads that entry silently.

**Why:** Chat that waits for its voice and pages that play as they are turned keep the words and the voice together, so nothing is read over or cut off, and a page that plays once does not repeat itself while the player turns back and forth. The journal is written in the first person, so it is read in the player's own voice, never in the narrator's. A recap held a moment and faded as the world comes reads like a "previously" that gives way to the game.

## The words

- **A line carries its performance.** Its text holds audio tags in square brackets, such as `[sighs]`, `[whispers]`, `[voice breaking]` or `[pause]`, where the delivery changes: breath, laughter, tears, gasps, swallowing, hesitation, emotion and pace, as many as its moment carries. The game shows the words without the tags; the recording sends them. Tags never make a sound effect, such as wind or a door, since the world makes its own sounds.
- **Every speaker says their own words.** Narration never quotes a person aloud: a quoted line is split, the narrator saying what happens and the person their words in their own voice, and a quoted note, log or journal page is a line of its own that the band shows as narration and the writer's voice reads.
- **Words only the game knows** are recorded in every form when the forms are few: the words on the stranger's stone, a hold's wave counts, the dawns left, who the hollow took, and the frost iron Hob still needs. When they are many, the line is written so that nothing varying is spoken: the dice's throws, pots and stakes show on the action bar while the voice tells the throw, and a friend who brings the player back is not named.

**Why:** Tags in the text keep words and performance in one place, so an edit to either records the line again and a clip never plays a delivery its words have outgrown. A narrator quoting a gruff smith in her own voice breaks the spell that a person's own voice keeps.

## The cast

Every voice is designed for the pack, none is one of ElevenLabs' built-in voices. Two roles share a voice only when they never speak in the same step, which `just lint` checks; `story/voices.toml` says who reads what.

| Voice | Reads |
|---|---|
| Narrator | Every line nobody speaks: the memories' scene text, the road's scenes, clues, the story's notices on the band, the Afterwards pages, the ending, the first recap, and Years later's leaves when Pip was lost |
| Wend | Wend at the Last Camp and in the memories, the voice until the first reveal and the gaze after it (one speaker in the story), Wend reading, his ledger, letter and orders |
| Pip, Maud, Cadoc, Alruna, Hob, Agna, Drifa | Themselves, and Drifa the Frost Walkers' book |
| Malgven | Malgven and her notes |
| Bram | Himself |
| Pip, grown | Pip's leaves of Years later |
| A Gazebound man | The Gazebound on the road, Brannoc, the captains' pages but Wend's, and the Gazebound's books |
| The oldest Keeper | The oldest Keeper, the Keepers' records and library books, and A Keeper's Note |
| An old man | Oswin, Wil, the man from the crossing, the archivist of the deep city, and the journal in the Cache |
| Murdo, the heir, Edda, Sefa | Themselves; Murdo the Tide Folk's remedy and book, Sefa the Sand Singers' book |
| A man | The fisher, the herder, the pedlar, the traveller, the dicer, the Tide wardens and the shipwright's log |
| A woman | The villagers |
| An old woman | The old woman in furs, Hesper the pilgrim, and the Jungle Folk's prayer and hall book |
| A child | Davy, Tobin, the captives and Cecily |
| The player, a man; the player, a woman | The answers and the journal of each player who picks them |
| The Diggers, the First Village, the Scribes of the Peaks | Each dead people's book in its hall, a voice of its own |

- **Young voices are raised.** ElevenLabs refuses to design any young voice, through the API and on its website alike, so Pip and the shared child's voice are a small, light adult voice raised in pitch as each line is recorded (`pitch`, in semitones: Pip one, the child two).
- **The narrator is a designed voice, dry.** It reads a third of everything spoken, so any effect laid over it would grate long before the end.
- **Clips are dry, and the gaze has its own copy.** Each of Wend's clips is recorded once and copied with the gaze's undertone by a filter in `story/voices.toml` `[gaze]`; the game plays the copy once the voice has become the gaze. Tuning the undertone renders the copies again on this machine and records nothing.

**Why:** Designed voices give each of the caravan's eight and their households a voice of its own, which four built-in voices could not, and a voice shared only by roles that never meet is never heard talking to itself. A people that still lives reads its own book in the voice the player met it by; the dead peoples each get one, since the books are all that is left of them. An undertone rendered into a copy sounds the same on every machine and leaves the game's sound mods nothing to fight over.

## Recording

- **Recording waits for the user's word.** A clip belongs to its voice and its exact text with its tags: a text rewritten loses its clip in the same change (`just voice prune`) and stays silent, the game showing it for its reading time, until the user says to record. Then `just voice record` records everything silent at once, checks it and prunes the clips of texts that changed. `just lint` names every line still silent and every speaker still without a voice, and passes; before the release nothing may be silent (`production.md`, R5).
- **Every take is checked.** ElevenLabs' speech-to-text writes down what each take says and compares it with its words; a take that skips words or reads a tag aloud is recorded again, and the user hears every scene of the story by ear while fire lines, books and the interface's lines rest on the check.
- **Every voice lives in the repository.** `story/voices.toml` holds each voice's description, preview text and settings; `story/voices/` holds each kept design, the exact request that made it with its seed and the preview it made, and every clip's text and how it was made. An account is only where a voice is saved for the moment: any account rebuilds it from the repository, and `just voice record` saves the voices it needs and drops the one used longest ago when every slot is taken. The seed makes a new take of the same request, never the same audio, so the rebuilt take is heard beside the kept preview before it is saved, and a voice that came out too far off is designed again from its own preview and clips as reference audio.
- **A voice is chosen as the game will play it.** A design's three previews come from the design model, and the lines are recorded by `eleven_v4`, which renders the same voice cleaner and steadier; so each candidate is saved and read by `eleven_v4` before the user chooses by ear, and the two not chosen are deleted again.
- **A new character gets a voice when the user calls the recording.** Until then their lines stay silent. Then a role that never speaks in the same step as a shared voice's roles, and fits it, joins that voice's reads; any other is designed with the `sound` skill and put to the user, before the lines are recorded.
- **Clips are mono Ogg Vorbis at 22,050 Hz**, trimmed of silence and matched in loudness, about 6.5 KB a second.

Edge cases: a run cut off by credits, voice changes or the network keeps every clip it made and goes on from there; a voice redesigned after its lines were recorded keeps those clips, counted as by an earlier design until recorded anew; a new account saves each voice again from the repository before recording.

**Why:** A clip tied to its exact text and tags is never paid for twice and never plays over words that changed, and recording only on the user's word spends credits once, on words that have settled, while `just lint` keeps every silent line in sight so none is forgotten. A voice that exists only in an account is lost with the account, and the seed alone is not proof, since a model update can break it without saying so.

## What it costs

- **The words.** About 210,000 characters: 107,000 for the voices of the story, 46,000 of journal and 2,000 of answers for each of the two player voices, and 4,300 of the peoples' books. `just voice` counts what is left for each voice.
- **The price.** Recording costs about 0.22 credits a character, $0.08 for 1,000 characters through the API, so the whole pack about $17 and $20 to $25 with its retakes; checking every take costs $0.22 an hour of speech, about $1. Designing a voice costs its preview text for three previews and the text again for each in `eleven_v4`, about 1,500 credits and 3 voice changes.
- **The account.** Any plan records: `just voice record` works within its voice slots and voice changes, and stops where the voice changes run out until they reset. Neither the voices nor the clips need attribution: support confirmed it on 20.10.2026, and both replies are kept.

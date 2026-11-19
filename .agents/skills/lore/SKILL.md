---
name: lore
description: Write Wayfarer's player-facing text - quest titles and descriptions, lectern and loot books, item names and tooltips, conversation lines, notices and HUD messages - in the pack's voice and consistent with design/lore.md. Use whenever text a player reads is added or changed.
---

# Lore

## Before writing

Read `design/lore.md`. It is the only source of what is true in this world. When the text needs a fact the lore does not have, stop and propose the fact to the user first; never invent it inside a quest.

Then read the text around the change as the player meets it: `just script` writes the whole story into `build/script.md` in play order, every line, answer, task, journal entry and notice, so the new words are read beside what comes before and after them. Reading a whole act there is how a name used before it is given, a fact told twice or a line that contradicts an earlier one shows.

## Voice

- **Spoken, not narrated.** People speak in their own voice, on the conversation band at the bottom of the screen (`design/interface.md`, I4): the stranger tired and dry, Maud hard, Agna practical, Hob gruff and kind, Pip a child. The voice speaks as Wend, warm and wry, from the first hall on, named as the stranger until Malgven names him in The Name. The journal speaks as "I", plainly, the way the player would tell a friend. A line nobody speaks is the narrator's: second person, present tense, what is seen ("The fire pops."). Books are written by whoever wrote them: the peoples to whoever finds their hall, the Keepers dry, careful and tired.
- **Only what they know.** A person says only what they could know: the settled have no name for what burns their towns. The journal holds only what the team knows so far, and an entry grows as they learn.
- **Short lines, as many as the moment needs.** A spoken line is one or two sentences and fits two rows of the band, its speaker's name included (`just lint` measures it); a moment that matters gets more lines, never longer ones. A quest summary is two sentences and one concrete instruction, an objective one instruction, a journal line one or two sentences. A book page holds about 250 characters; aim for three sentences.
- **Say the thing itself.** Every line means exactly what its words say, so a player who reads English as a second language gets it the first time: no idioms ("can't account for", "everyone's in", "walks in your smoke"), no word in its old sense ("wants" for needs), and no figure of speech or title standing in for a fact ("the finder's ember is dark", "the fire that walks", "opened over the heart"). A mystery is something not told yet, never something told as a riddle: a hook names what it hints at plainly ("the dragon's eye", not "the eye of the one who flies"). The Crown of the Grass King is the model: plain sentences, and a hook that names its dragon.
- **Plain words.** No fantasy vocabulary for its own sake, no capitalised Concepts beyond the lore's own names (the Eye, the ember, the gaze, the Keepers, the Gazebound, the Twelve Peoples, the halls, the calling stones, the frames).
- **The rule, then the reason.** When text explains a mechanic, say what happens and why the world makes it happen: "It will not take root: roots are stillness, and the ember withers what a Wayfarer plants."
- **Hopeful at the end of each piece.** The world is hard; the text is not bleak.
- **Written for the ear.** Every line is read aloud (`design/narration.md`), so it carries its performance as audio tags where the delivery changes (`[sighs]`, `[voice breaking]`, `[pause]`), as the `sound` skill's Voices says; the game never shows them, so a tag never carries what the player must read. Narration never quotes a person: the narrator says what happens and the person their own words, on a line of their own. A word only the game knows is one of a few recorded forms or not spoken at all.

## What the player knows

Write for a player who knows vanilla Minecraft and nothing else, and who has read only what the screen showed them so far: chat, the task panel, the HUD. The design documents are not on their screen.

- **Fill the ledger first.** Before writing a step, list what the player has been shown, what they have seen without understanding, and what someone has told them, from the world's first minute up to this step. Everything the new text leans on must be on that list; whatever is missing is set up earlier by someone saying it, or the line goes.
- **No word before the story says it.** The HUD, the task panel, the first-time hints, keys and item names use only words the player has met by the earliest moment they can show; before that they show what the player sees, or nothing. The compass says nothing about the ember until the stranger has said "That's an ember." A word stands for one thing, the same everywhere.
- **Teaching is plain; flavour may be dark.** A line that tells the player what to do or how a rule works names nothing they have not met. A dying man's broken words, a book, a voice's hint may name what is not yet understood: that is a hook, and the story answers it later.
- **A person is named once introduced.** Until someone says a person's name, the band labels them by what the player sees (`unnamed` in `story/people.toml`, "An old woman"); the line that says it marks it known (`names` on the line). Every person introduces themself, or is introduced, in their first scene.
- **Clear what, hidden why.** The player always knows what is happening on the surface: who is dying, what was handed over, what just happened, what to do next. The mystery is why: who he is, why he knows their face, what the shards are.
- **A secret comes with its reason.** A person who keeps something back says why in the same line ("I swore at his fire never to say his name"), so the player knows what is withheld and by whom; a silence without a reason reads as a riddle.

## Scenes

A moment that matters is played as a scene on the band, line by line, never told as a summary of itself. The test: if a line could stand as a bullet in a design document, it is a summary; narrow to one moment and render it.

- **Show something a camera could see.** Every beat has a body, an object, a sound: the grey face, the shaking fist, his head dropping to his chest. What a person's body shows in the world matches what the text says (the art skill's poses).
- **Set up before you take away.** A death, a loss, a betrayal is foreshadowed so it feels inevitable when it comes: the dying say they are dying and why, in their own words, before the end.
- **Say what happened.** After a big beat, the narrator states it plainly ("The old man is dead."), then leaves a short silence of one line before anything asks the player to act. Never cut from one state to the next with only a new objective.
- **A reason, and why now.** A side quest's first line says why the person asks now, tied to something the team just lived through ("All of that woman's people dead, and she knew every one by name. It made me count my own.").
- **A reason before a task.** The story gives the reason for an action before the task panel names it: "You can't leave him here for the crows." comes before "Bury him". A rule someone gives comes with its reason in their voice.
- **Give it room.** Important information comes in focused moments with nothing else to do; small talk can happen while walking. Do not rush the scenes that are meant to be remembered.
- **Go cold after.** Grief is a line, not a paragraph: the road goes on, and the dead push the living onward.
- **The player opens screens.** Never open a screen on the player unasked. A choice that needs typing is offered as an answer first, with ready answers beside it, and only that answer opens the screen, with a task saying what to do there.
- **A team hears a line once.** A line that belongs to the moment, such as a handover or a reaction, is said once for the team; one meant for a single player goes to that player alone.
- **Remarks come in sets.** What a person answers when right-clicked during a step is at least two lines, so the second click says something new.
- **Answers are lines the character says.** A choice reads as something the player's character says or does, in their voice, and makes clear what happens when clicked.

## Teaching

- **Say what to do, where the player looks.** Every step's objective is one instruction, shown in the task panel (`design/interface.md`, I1); a player who never opens the journal still knows what to do next.
- **Name every key the first time.** A step that needs a key or a click lists its task in the panel, worded with the key itself (`Open your journal %s`, filled by the key the player has bound through `Tasks.key` and drawn in a key cap, so never in brackets), the first time it is needed. How-to lines never go to chat, which keeps the story.
- **One new thing at a time, before the stakes.** A step teaches one new control or rule, where getting it wrong costs little, and a later step asks for it under pressure; a fight, a hold or a chase never introduces a rule.
- **Count what takes long.** Walking and waiting show how far along they are: blocks to go, whom the team waits for. A short rest shows no countdown.
- **Every refusal and every wait says why and what moves it on.** "It will not stand here: a Wayfarer keeps no hoard in the world, only in a tent." names the rule, the reason and the way.

## Where text lives

| Text | File |
|---|---|
| Quests: titles, summaries, objectives, people's lines, the narrator's lines, clues, rewards, choices and the journal's entries (the story so far, people, words) | `story/quests.toml` |
| A step's tasks in the task panel (`design/interface.md`, I1): which ones a step shows | `story/quests.toml`, `tasks` on the step |
| What a person answers when right-clicked with nothing to say in the story (M27): their lines for a step | `story/quests.toml`, `remarks.<person>` on the step |
| The people's names, and the lines they answer with on any day on the road | `story/people.toml` |
| Hall books (per eye) | `generator/eyes.toml`, `people` and `lore` |
| Books: the memories' pages, the loot books, the pages found on the road | `story/books.toml` |
| Mod messages, HUD, item names, and the task panel's tasks (`wayfarer.task.<id>`) | `mod/src/main/resources/assets/wayfarer/lang/en_us.json` |
| An enchantment's name with its icon, and a description for one that has none | `generator/enchantments.toml` |
| Another mod's text the pack rewords | `mod/src/main/resources/assets/<that mod's namespace>/lang/en_us.json`; only a file in the same namespace reliably wins |

All text is English. After editing, run `just generate` for generated text and `just voice prune` for every line written or rewritten, which leaves it silent until the user says to record; `just lint`, run by the `finish` skill, fails on a translation key the code uses but the language file does not name, and on a book title or page too long for Minecraft.

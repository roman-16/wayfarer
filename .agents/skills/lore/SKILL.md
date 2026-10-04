---
name: lore
description: Write Wayfarer's player-facing text - quest titles and descriptions, lectern and loot books, item names and tooltips, chat and HUD messages - in the pack's voice and consistent with design/lore.md. Use whenever text a player reads is added or changed.
---

# Lore

## Before writing

Read `design/lore.md`. It is the only source of what is true in this world. When the text needs a fact the lore does not have, stop and propose the fact to the user first; never invent it inside a quest.

## Voice

- **Spoken, not narrated.** People speak in chat in their own voice: the stranger tired and dry, Tamsin hard, Agna practical, Hob gruff and kind, Pip a child. The voice speaks as Wend, warm and wry, from the first hall on. The journal speaks as "I", plainly, the way the player would tell a friend. A line nobody speaks is the narrator's: second person, present tense, what is seen ("The fire pops."). Books are written by whoever wrote them: the peoples to whoever finds their hall, the Keepers dry, careful and tired.
- **Only what they know.** A person says only what they could know: the settled have no name for what burns their towns. The journal holds only what the team knows so far, and an entry grows as they learn.
- **Short.** A chat line is one or two sentences. A quest summary is two sentences and one concrete instruction, an objective one instruction, a journal line one or two sentences. A book page holds about 250 characters; aim for three sentences.
- **Plain words.** No fantasy vocabulary for its own sake, no capitalised Concepts beyond the lore's own names (the Eye, the ember, the gaze, the Keepers, the Gazebound, the Twelve Peoples, the halls, the calling stones, the frames).
- **The rule, then the reason.** When text explains a mechanic, say what happens and why the world makes it happen: "It will not take root: roots are stillness, and the ember withers what a Wayfarer plants."
- **Hopeful at the end of each piece.** The world is hard; the text is not bleak.

## Where text lives

| Text | File |
|---|---|
| Quests: titles, summaries, objectives, people's lines, the narrator's lines, clues, rewards, choices and the journal's entries (the story so far, people, words) | `story/quests.toml` |
| The people's names and colours in chat | `story/people.toml` |
| Shrine books (per eye) | `generator/eyes.toml`, `people` and `lore` |
| Books: the memories' pages, the loot books, the pages found on the road | `story/books.toml` |
| Mod messages, HUD, item names | `mod/src/main/resources/assets/wayfarer/lang/en_us.json` |
| Another mod's text the pack rewords | `mod/src/main/resources/assets/<that mod's namespace>/lang/en_us.json`; only a file in the same namespace reliably wins |

All text is English. After editing, run `just generate` for generated text and `just lint`, which fails on a translation key the code uses but the language file does not name, and on a book title or page too long for Minecraft.

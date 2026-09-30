---
name: lore
description: Write Wayfarer's player-facing text - quest titles and descriptions, lectern and loot books, item names and tooltips, chat and HUD messages - in the pack's voice and consistent with story/lore.md. Use whenever text a player reads is added or changed.
---

# Lore

## Before writing

Read `story/lore.md`. It is the only source of what is true in this world. When the text needs a fact the lore does not have, stop and propose the fact to the user first; never invent it inside a quest.

## Voice

- **Spoken, not narrated.** The last Wayfarer's journal is written by him to the player, in the first person. Shrine books are written by the people who hid the shard, to whoever finds it. The memories are the Eye remembering: the first Wayfarers speaking as one, as "we". The Keepers write like record-keepers: dry, careful, tired.
- **Short.** A book page holds about 250 characters; aim for three sentences. A quest description is two sentences and one concrete instruction. A HUD or chat line is one sentence.
- **Plain words.** No fantasy vocabulary for its own sake, no capitalised Concepts beyond the lore's own names (the Eye, the ember, the gaze, the Keepers, the Gazebound, the Twelve Peoples, the true gate).
- **The rule, then the reason.** When text explains a mechanic, say what happens and why the world makes it happen: "The sea does not carry you yet. It opens at 4 eyes."
- **Hopeful at the end of each piece.** The world is hard; the text is not bleak.

## Where text lives

| Text | File |
|---|---|
| Shrine books and eye quests (per eye) | `generator/eyes.toml`, `people`, `hint` and `lore` |
| The Last Camp journal, the twelve memories, the loot books | `story/books.toml` |
| The Last Camp grave | `generator/generate.py`, `last_camp` |
| Mod messages, HUD, item names | `mod/src/main/resources/assets/wayfarer/lang/en_us.json` |
| Another mod's text the pack rewords | `mod/src/main/resources/assets/<that mod's namespace>/lang/en_us.json`; only a file in the same namespace reliably wins |
| The quest book: chapters, quests, the book title | `story/quests.toml` |

All text is English. After editing, run `just generate` for generated text and `just lint`, which fails on a translation key the code uses but the language file does not name, and on a quest that comes after a quest that does not exist.

# Narration

Everything in Wayfarer that speaks in words is to be read aloud: books, chat, signs and the rest. The audio comes from ElevenLabs, generated from the same text the player sees, and plays in the Voice/Speech sound category. It is recorded once, in phase 6, from the final texts, since every recording costs money; a text rewritten after its recording is recorded again. This file holds the decisions.

## How it plays

- **Chat waits for its voice.** A line in chat is read aloud as it appears, and the next line comes only once the line before has been spoken: a line whose audio runs three seconds holds the chat for three seconds. This holds for the voice, Maren and every person the player talks to.
- **A book reads the page that is open.** Opening a page plays its narration; turning to the next page stops it and plays the next one. A page plays only once: turning back to a page already heard does not play it again, but a small play button on the page plays it again on purpose. This holds at a lectern, from the inventory and in the journal.
- **A sign reads itself when right-clicked.** Every sign the pack places is waxed, so a right-click reads it instead of opening it for editing.
- **One voice at a time.** When two things would speak at once, such as the voice on the road while a person is talking, the second waits for the first.
- **What else is read aloud:** journal entries (the story so far, people, words, quests) and quest descriptions, played like book pages; the memories' scene text; place names on arrival; letters, lecterns and notes in the world; the ending and its last line.
- **What is not:** item names and tooltips, buttons and menus, and short warnings that repeat often, such as "The tent flap won't open".

**Why:** Chat that waits for its voice and pages that play as they are turned keep the words and the voice together, so nothing is read over or cut off, and a page that plays once does not repeat itself while the player turns back and forth. Warnings that come again and again would grate when spoken every time.

## The cast

| Role | Who speaks | Reads | Voice | Effect |
|---|---|---|---|---|
| Wend | The dying stranger at the Last Camp: a Wayfarer who broke the oath and became a Gazebound captain | His words at the Last Camp, his lines in the memories, his ledger, the last captain's page, his letter to Maren, his real last whisper, and the voice until the first reveal | Brian | none |
| The gaze | The dragon wearing Wend's voice, found in the ember's memory (`story.md`, S4) | The voice once it is unmasked at the end of Act 2, and its lines in the End | Brian | undertone |
| Maren | The Gazebound evoker Wend raised | Her lines at the fire and the gate, and her notes | not chosen | none |
| The caravan | Tamsin, Corin, Nell, Bram, Ilse, Agna, Hob, Pip and their households | Their lines in conversations, at the fire and on the road | not chosen | none |
| The world's people | The Grass King's heir, the living peoples, travellers, the settled | Their lines in conversations and scenes | not chosen | none |
| The Twelve Peoples | The peoples who hid the shards, living and gone | Their books in their halls and places | not chosen | none |
| The Keepers | The Wayfarers who would not walk, guarding the twelve frames | Their records and library books, and their words at the door | Bill | none |
| The Gazebound | The Illager cult under the Grass King's heir | Their books, and the captains' pages but the last, which is Wend's | Harry | none |
| The archivist | A record-keeper of an Ancient City | The Ancient City books, among them the one that hints at the silence | George | none |
| The narrator | Nobody in the story | Every line nobody speaks: the memories' scene text, the road's scenes, place names on arrival, the ending and its last line | open (N1) | open (N1) |

## Why these choices

- **Effects only where the voice is not a person's.** Everyone in the cast speaks aloud or wrote a book, and a dry recording says exactly that; only the gaze, an eye speaking through the player, gets an effect.
- **Wend reads his own pages.** The last captain's page is in his hand, so a player who finds it before Maren names him hears the voice they carry in a Gazebound captain's orders. Any effect on either would hide that.
- **The gaze keeps his voice.** It found Wend's voice in the ember's memory and has none of its own (`story.md`, S4), so it speaks as Brian; once the mask drops, a low undertone under the same voice tells the player what they already know.
- **The caravan and the world's people need voices of their own.** None is chosen yet; with eight at the fire, their households and the people on the road, the built-in voices may be too few to tell them apart (not counted).
- **Keep effects subtle.** The gaze talks all through Act 3, so its undertone must stay pleasant through a long talk at the fire.

## What it costs

The characters are counted once the texts are written in phase 5; the books and lines the pack ships today come to about 24,300.

The account is on the free plan. It cannot use Voice Library voices or design voices through the API, so the cast uses ElevenLabs' built-in voices only. The free plan's monthly credit limit and its attribution and non-commercial terms were not verified; check them before generating. A paid plan would unlock library and designed voices.

## Open

### N1 The narrator

Who reads the lines nobody in the story speaks: the memories' scene text, the road's scenes, place names, the ending and its last line, and with which effect. Sarah, the built-in voice with an ethereal effect, is free since the memories became Wend's own life.

### N2 The plan and its credits

Whether the free plan's monthly credits, its attribution and its non-commercial terms carry every line, or a paid plan is needed, which would also unlock Voice Library and designed voices. Settled before the first recording.

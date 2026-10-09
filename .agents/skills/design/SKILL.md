---
name: design
description: Run a Wayfarer design session - take the next open question in design/, research how games and mods answer it, offer options with a recommendation, and write the user's answer back as a settled decision. Use when the user asks to continue the design, work on the vision, story, world or systems, or answer an open design question. A design session writes documents in design/ only.
---

# Design

`design/` decides what Wayfarer becomes. A session moves one to three questions from Open to Settled and changes nothing outside `design/`.

## Start

1. Read `design/README.md` for the phase and Next.
2. Read `design/vision.md` whole: its ground truths and Settled decisions bind every answer. Read the document that holds Next, and `design/lore.md` when the question touches the world.
3. Take Next, unless the user names another question. When it needs a question that is still open, say so and take that one first.

## Work a question

- **Research before proposing.** How the games the user holds up (The Witcher 3, Cyberpunk 2077, GTA V, Death Stranding) and others answer it, with the `exa` skill; what Minecraft 1.21.1, NeoForge and the pack's mods can do, from the code, the jars or the mod pages, never from memory. Mark what was not verified.
- **Show it as play.** Each option is what the player sees and does: a moment of play as a transcript, chat lines, a screen. Mechanism comes after, if at all.
- **Use plain words.** The user is a software developer, not a game developer: explain every game-design term the first time it comes up, and keep technical terms but leave code detail out.
- **Numbers are powers of two.** A number that tunes something (a distance, a radius, a count, a rate) is a power of two, 4, 8, 16, 32, 64 and so on, or a sum of two, such as 128 + 64 = 192. A number that means something stays as it is: time people read (half a minute, five minutes, 22:00), shares that read as shares (two thirds of the team), counts the story sets (twelve shards, four doors), and facts about vanilla or a mod.
- **Say how the player learns it.** Every option says what the player already knows at that moment (from the screen alone, knowing only vanilla Minecraft), what tells them what to do and why, which key or click it takes, what shows it is working, what a player alone does, since about half play alone, and what a teammate sees; a step or mechanic without those is not ready to settle.
- **Say how it sounds and where it lives.** A moment says whether it has music or a sound (the `sound` skill), and anything the story gives says where the player finds it again: an item, a charm or a tooltip.
- **Check it against the ground.** An option that breaks a ground truth or a Settled decision says which, and why it is worth it anyway.
- **Recommend one.** Lead with it and its reason, then ask with the `questionnaire` tool.

## Write it down

- The answer goes under Settled in its document: the decision as the player meets it, then **Why** in one or two sentences. It leaves Open.
- Questions the answer raises go under Open with the next free number, or under the decision with the phase that settles them.
- A Settled decision changes only when the user reopens it; it then goes back to Open with what reopened it. A new direction the user gives reopens whatever it contradicts: carry it back through every older decision, quest and document it touches, rewrite them to fit, and say which.
- `design/lore.md` is written from `story.md` and changes with it.
- Update Next in `design/README.md`. When a phase's done-when holds, propose the next phase; the user moves it.

## Bounds

- Write only in `design/`: no code, no TOML, no generator, no build.
- Never settle what the user did not decide. A gap found on the way becomes an Open question.
- End with what was settled, what was opened and what is Next. Committing is the user's call, through the `commit` skill.

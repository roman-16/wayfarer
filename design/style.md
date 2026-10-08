# Style

How everything in Wayfarer looks. The world keeps Minecraft's own look, since the pack retextures none of vanilla's blocks and items and everything new has to stand beside them. Everything drawn over the world is the pack's own: one design system, the stamp, worn by the pack's screens and HUD, by vanilla's interface and by the mods'. Every look is made with the `art` skill; the numbers and colours live once, in `style.toml`.

## Settled

### L1 The stamp: Ash and Ember

- The interface looks like the kit of a traveller who sleeps by a fire: charred leather, smoke-dark canvas, blackened iron fittings and soot-browned parchment, lit by one warm colour, the ember's gold.
- Everything on the screen wears it: the HUD and the pack's screens, vanilla's menus, inventories and containers, tooltips, toasts, the hotbar, and the mods' screens. A screen that keeps vanilla's grey breaks the stamp, so it is brought in (L6).
- The Wayfarer's mark, a circle with one line through it running out above and below, the sign the stranger cut in the tunnels and the Cache's Wayfarer signed their journal with, is the stamp's emblem: it marks the tracked quest, the compass's goal, the title of the task panel, the notices, the journal's spine and the logo.

```text
Compass   [leather band, iron end caps]   W   NW   Φ   N   NE        512 on an iron plate under the gold mark
Panel     [char canvas, iron rim and corner caps]   MAIN ROAD / Φ The First Day / Rest by your fire. / ○ Open your journal [J]
Notice    [leather strip, stitched, iron end caps]   Φ A NEW ROAD / The Pull (display face, gold)
Journal   [charred leather cover, iron corners]  parchment pages, a leather spine with the mark, leather ribbons with icons
```

**Why:** The user wanted the interface to be the pack's own, one stamp everywhere, not Minecraft's grey menus. Reading the references (The Long Dark's chalk on dark, Sable's book typography, Pentiment's one manuscript language, Dredge's muted panels, Sea of Stars' pixel frames) gives one rule: a single material language, tied to the world's story, read the same on every screen. The fire is the pack's centre, so its char, iron and ember are the interface's.

### L2 Tokens

- Every colour, alpha and timing is a token in `style.toml`; the generator draws every sprite from it, the mod reads it through `Stamp`, and `just lint` fails on a colour written anywhere else in the interface code.
- Ramps of five shades for each material: ash (char surfaces), leather, iron, paper, ember; and ramps for the few other things that need a hue: gaze (violet), red (hearts, danger), frost (cold), moss.
- Text: bone for what is read, label for tracked capitals, quiet for what is secondary, dim for what is done, gold for what the story points at, ink and red ink on paper, danger, and gaze.
- What colours mean: gold means "the ember or the story points here" (the tracked quest, the compass's goal, a waiting reward, a chosen answer's bar); violet is the gaze's threat and nothing else; red is danger. Nothing is told by colour alone: every coloured thing also has a word, a mark or a shape (I7).

**Why:** Three golds and three inks had already drifted apart in the code before the stamp; one source keeps the stamp one stamp.

### L3 Type

- Running text everywhere, chat, menus, every mod and the pack's own screens, is **m5x7** by Daniel Linssen (CC0), set as the game's default font with Minecraft's own fonts behind it for any letter it lacks; it keeps Minecraft's line of 9 pixels, so other mods' layouts hold.
- Titles, names on notices and the logo are **Alagard** by Pix3M (CC BY 3.0, credited in `pack/CREDITS.md`), the display face; it has no German letters, which are drawn into it if German comes (`production.md`, R4).
- Labels are capitals tracked one pixel apart in the running face (MAIN ROAD, A NEW ROAD, THE EMBER IS COLD). Keys stand in key caps, small iron plates with the key's letter, wherever a text names a key: the task panel, the notices, the answers, the journal.
- Both fonts are drawn from their outlines into bitmap fonts by the generator (`generator/fonts.toml`), pixel for pixel at their own size.

**Why:** The typeface is the strongest single sign of a design system; open-licence pixel faces were chosen over drawing new ones (the user's call), m5x7 for its light, round reading and its accented letters, Alagard for its medieval title shapes.

### L4 Materials and detail

- **Char** (the ash ramp): every panel's surface, see-through on the HUD so the land shows faintly behind, opaque on screens.
- **Iron**: rims with a bevel lit from the top left, corner caps with a rivet, end caps, slots' bevels, key caps, plates for small numbers.
- **Leather**: strips (notices), the compass band, buttons, tabs, ribbons, the hotbar's strap and the journal's cover, with stitching where a craftsman would sew.
- **Parchment**: only books and pages, things a player holds, the journal, Pages, a book at a lectern and the loading recap, with soot at the edges; ink on parchment, never light text.
- **Detail lives at the interface pixel.** One art pixel is one interface pixel at every scale, as in Minecraft's own interface: more shades, grain, wear and fittings than vanilla, never finer pixels. Ornaments sit only at ends and corners; a few cracks and scuffs, never noise.

**Why:** Dark surfaces with light text read over snow at noon, a cave and a burning town at night, under the shader, where paper panels wash out; finer detail than the interface pixel turns to noise at scale 2 and stops matching the world's blocks.

### L5 Components

| Component | Material | Used for |
|---|---|---|
| Panel | Char, iron rim and corner caps | The task panel, the conversation band, the answers, screens, toasts, the style board |
| Plate | Char, thin iron rim | Distances, the line under the compass, the hallowed-ground icon |
| Band | Leather, iron end caps | The compass |
| Strip | Leather, stitched, iron end caps | Notices |
| Button | Leather plate, iron rim; gold rim when pointed at; char when disabled | Every button, vanilla's and the mods' |
| Key cap | Iron | Keys in any text |
| Slot | Char well, iron bevel; gold frame when selected | Inventories, the hotbar, rewards |
| Tooltip | Char, iron rim; ember rim for the story's own things | Every tooltip |
| Book | Leather cover with iron corners, parchment pages, a leather spine with the mark, leather ribbons with icons, gold for the open one | The journal and every book |
| Divider | A line with diamond ends, iron or ink | Under titles |
| Mark, ring, disc | Gold or ink | The tracked quest, open and done tasks, new entries |
| Meter | Cells of the gaze's violet | The gaze |
| Icons | White, tinted where drawn | The journal's ribbons, the compass's tent, stone, hall and fire |

The dev client's style board (driver step `board`, gallery `interface/board/`) shows every component in every state.

### L6 Coverage: vanilla's and the mods' interfaces

- The pack draws vanilla's most seen sprites itself: buttons, sliders, text fields, tabs, check boxes, scrollers, the small icon buttons, page arrows, the hotbar with its selection and off-hand, slots, the effect backgrounds and the toasts.
- Everything else in vanilla's six interface greys, every container background and every mod texture that copies them, is recoloured into the stamp when the game loads it: the fill to char with grain, the bevels to iron. Dark labels (vanilla's grey, 404040) turn bone wherever a screen was recoloured, except on paper. So a mod's screen wears the stamp without a texture of its own.
- A mod whose screen does not follow vanilla's greys is brought in through its own config or a texture in its namespace: Jade's box is the stamp's tooltip through a theme the generator writes from the tokens, and JourneyMap's full map wears its own Victorian theme, leather and brass, the nearest of its themes. Sodium's video settings keep Sodium's own look, the one screen the stamp does not reach yet.
- The stamp draws vanilla's book screen too, a charred leather cover with its parchment page, so a book at a lectern or in hand reads like the journal. Sign editing and hanging signs keep their wood, and the title's logo and panorama are the front door's (I6).

**Why:** One recolour reaches every container and most of the pack's mods at once, ships none of Mojang's or the mods' art, and keeps what each screen shows where it was; the sprites drawn by hand are the ones a player looks at most.

### L7 Motion and sound

- Things kindle in and burn out: a notice slides in and fades over a quarter of a second (`style.toml` `[motion]`), and nothing in the interface bounces, spins or flashes.
- The interface's sounds are A6's (`sound.md`): a quest begun or done, a task ticked, a journal page, an answer chosen.

**Why:** Motion that comes and goes like fire light fits the stamp; anything livelier pulls the eye from the world.

### L8 The world

- The world keeps Minecraft's own look: 16 by 16 textures lit from the top left in vanilla's ramps, Mojang's plain shapes, Fresh Animations' liveliness as the bar for creatures, and the shaders on top. Every new block, item, creature, skin and structure is made with the `art` skill and judged in the workshop beside its vanilla relative.
- **Items** read at a glance in a hotbar slot, the silhouette carrying the idea; tools and weapons lie on the diagonal with vanilla's stick for a handle.
- **Blocks** look right alone and tiled on a three by three wall, with no repeat the eye can find.
- **Creatures** have one idea and a silhouette that reads at night; dead ones lie as death leaves them (`wayfarer:fallen`), never a tipped live creature.
- **Skins** use both layers: hair, collars, coats, aprons and boots stand off the body; the settled world's people come from combinations of build, age, clothes and wear, never two alike.
- **Structures** have masses, one hero, depth, odd widths and three to five blocks, sit on their land, and every block is what that part is made of; wear tells what happened there.
- **The world's lights are the stamp's two lights.** Light the pack adds to the world is ember gold where it comes from the ember (its glow, the clues' amber, the tracks) and violet where it shows the gaze, so a player who knows the interface reads the world's magic the same way.
- Where the world meets the interface, item icons are world art shown inside the stamp's slots and frames; the interface frames them and never redraws them.

**Why:** The pack does not retexture vanilla, so anything new that left Minecraft's look would stand out against every vanilla block beside it; the interface is the pack's own because nothing of vanilla's stands beside it on the screen once the stamp covers it.

## Open

Nothing is open.

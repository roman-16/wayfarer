---
name: art
description: Design how anything in Wayfarer looks - item, block and creature textures, item and creature models and their animations, structures, rooms and camps - so it reads as if Mojang had made it and belongs to its people in design/lore.md. Use whenever something a player sees is added or changed; the lore skill writes its words, this one its look.
---

# Art

Everything a player sees has to look as if it was always in Minecraft: the same pixel grid, the same light, the same few colours, the same plain shapes, and a reason in the lore for every detail. Detail is welcome; noise is not.

## Before making anything

1. **Know whose it is.** Find the thing's owner and story in `design/lore.md` and `design/story.md`: a people, the Gazebound, the settled world, the Keepers, the first civilisation, a Wayfarer (D17 in `design/vision.md`). Its materials, colours and wear follow from who made it, how long ago, and what happened to it since. A fact the lore does not have goes to the user first; the lore skill and the design skill say how.
2. **Find its vanilla relatives.** Every new thing has a vanilla cousin to measure against: a sword beside a new blade, a zombie beside a new creature, a village house beside a new camp. Vanilla's own textures and models are in `mod/build/moddev/artifacts/neoforge-*-client-extra-aka-minecraft-resources.jar` under `assets/minecraft/` (605 item, 1,061 block and 540 creature textures); read their pixels and palettes before drawing, and reuse a vanilla colour before inventing one.
3. **Build in the workshop.** Every look is judged in the game, not as a file: put it in the workshop, put its vanilla relative beside it, and photograph it from every side (`tooling.md`, T1):

   ```console
   $ python3 scripts/client.py run workshop 'spawn wayfarer:worm' 'beside minecraft:zombie' 'views worm'
   ```

## Textures

- **The grid is sacred.** Blocks and items are 16 by 16 pixels; one pixel on the texture is one pixel on the model, never stretched or squashed. Never mix resolutions (mixels): no texture drawn at a higher resolution, and no model element smaller than one pixel.
- **Few colours, in ramps.** Build each material as a ramp from a midtone with one shadow and one highlight first, then a few more shades; shift hue and saturation a little between shades, not only brightness. Keep a texture to about ten colours, and reuse vanilla's own ramps for vanilla materials: iron is vanilla's iron, oak is vanilla's oak.
- **Light from the top left.** Items are lit from the top left; a creature's top and front are brighter than its bottom and back, face by face and across faces.
- **Draw, never brush.** Place every pixel on purpose with a pencil, a fill and an eraser; no soft brushes and no filter noise, since noise adds no information.
- **Avoid the classic faults:** banding (shades in lines that show the grid), pillow shading (shades in rings from the edge inwards), pancake shading (light on one side, dark on the other regardless of shape), the darkest colour touching the lightest without a step between, lone pixels, staircase jaggies on lines that should have an even step, and large areas of one flat colour on rough materials.
- **Items** fit inside 16 by 16 with a clearly darker outline, shaded to the top-left light, and read at a glance in a hotbar slot: the silhouette carries the idea, the inside only the material. Tools and weapons lie on the diagonal, handle at the bottom left, as vanilla's do, and a tool's handle is vanilla's stick unless the lore says otherwise.
- **Blocks** look right on their own and tiled: check every block texture on a three by three wall early, with only two or three shades, and keep checking while shading; any repeat the eye can find is a tiling fault.
- **Creature textures** use box UV, unwrapped by the model, with north as the front.
- **In this repository** item textures are drawn as rows of characters with a palette per variant in `generator/textures.toml`, written by `generator/png.py`; a new texture follows the same form.

## Models

- **Few elements.** The model gives the shape, the texture the detail; keep the element count as low as the thing allows while it stays recognisable.
- **Plain shapes.** A round thing is one box (a barrel, a log, a pumpkin); a slant is one rotated element, not a staircase of small ones; neighbouring elements are never rotated to fake a curve. Small parts are a plane with transparent pixels rather than many tiny boxes.
- **Proportions tell character.** A block is 16 pixels, a metre; the player is bulkier than a person, so what they use is sized for them. Small things stay visible before they stay to scale. A big head on a small body reads as harmless, a heavy torso and arms as strong, a long thin body as wrong.
- **Simplify to the idea.** Mojang removed the phantom's mouth because it did nothing in play and looked like a sock puppet; every part of a creature must earn its place by showing what it does. Start from the creature's job in the story and its threat, then give it one clear idea: the warden is blind and hunts by sound, so its face has no eyes and its chest has the souls it hears with.
- **Silhouette first.** A creature must be known by its outline alone, at a distance, at night.
- **Animation is plain and readable.** Vanilla-like motion is a few bones swinging on sine curves, limbs opposite each other, a slow idle breath; no flourishes. Loops close cleanly, and the pack's Fresh Animations sets the bar for how lively a vanilla creature may be. Creatures are animated with GeckoLib (`design/systems.md`, M10), built in Blockbench, which an agent can drive through the Blockbench MCP plugin (T3).

## Structures, rooms and camps

- **Massing first.** Before a single detail, shape the volumes: a main mass and two or three smaller ones at different heights, a third to a half of the main one's size, so the outline reads against the sky. A single box is never a building.
- **One hero.** Every structure has one focal point, the gate, the tallest tower, the hall's pedestal, clearly bigger or brighter than the rest, and detail thickens towards it. Leave calm wall between detailed parts so the eye can rest.
- **Depth makes shadow.** Never put walls and pillars in one plane: pull the frame one block out, set windows and doors one block back, give roofs an overhang. Stairs, slabs, trapdoors, walls, fences and panes add depth of less than a block. A wall over about 8 blocks without a pillar, band or recess looks empty.
- **Odd widths.** Wall runs of 5, 7 or 9 blocks have a centre for a door, a window or a roof peak; an even width has none.
- **A palette of three to five, each with a job.** A base for the bulk, a darker block for recesses and shadow, a texture block mixed in at about one in four, a trim block, and one accent kept under a tenth of the faces. Blocks share an undertone; stretch one material through its stairs, slabs, walls and cracked, mossy, chiseled and polished variants before adding another. Depth comes from value contrast, not more colours. In this repository a material is a weighted list of blocks in a `Material`, mixed by coherent noise (`generator/blocks.py`), so neighbouring blocks agree instead of speckling.
- **Ground it.** A structure sits on its land: foundations reach down into the slope, the palette uses the land's own stone and wood, and paths and pieces follow the ground the way a village's do (M5); nothing floats or sits on a flattened square.
- **Wear tells time and story.** Weather where weather reaches: moss and cracks on the low, damp, shaded and north-facing parts, sound blocks high and sheltered. Break a ruin where it would break, the roof and the tops of walls first, and drop the fallen blocks below it; let roots, vines and a tree grow through what has stood longest. Keep the shape symmetric but the wear never mirrored. The generator's detail passes (`generator/detail.py`: rubble, moss, webs, roots, vines, skirting, cornices) do this by density and seed.
- **Every room was used.** A room shows what happened in it: who lived there, what they left, why they left. A hall of the Frost Walkers keeps their furs and their dead; a Gazebound outpost keeps their banners, their bread and their scars. Loot and books sit where their owner would have put them.
- **Gameplay first.** YUNG builds his structures for the way through them: more than one way in, places to be clever, rewards for the bold. A structure is a place to play before it is a picture.

## Checklist

Before calling a look done, in the workshop beside its vanilla relative:

- Would a player believe Mojang made it, or that it came with the pack's other mods?
- Does it read at a glance: an item in its slot, a creature by its outline at night, a structure by its silhouette from far off?
- Is every colour from a ramp, and every material vanilla's where vanilla has it?
- Is the grid whole: no mixels, no stretched pixels, no element under a pixel?
- Is it free of banding, pillow and pancake shading, noise and tiling repeats?
- Does a structure have its masses, its hero, its depth, its odd widths and its three to five blocks, and does it sit on its land?
- Does every detail have a reason in the lore: who made it, and what happened to it since?

## Sources

- Blockbench Wiki, *Minecraft Style Guide*: element count, shapes, UV and mixels, proportions, ramps and palettes, shading faults, item, block and creature textures.
- Faithful, *32x Texturing Guidelines*: one-pixel lines, vanilla palettes, transition colours, banding, flat areas, noise.
- Minecraft.net, *Meet the Phantom*, *Meet the Warden*, *Meet the Ravager*: how Mojang simplifies a creature to its idea.
- Minecraft Wiki, *Texture Update*: Jappa's 1.14 redraw of nearly every texture.
- Craftdex, *The Architecture of Builds*, and Astroworld's guides on detail and palettes: massing, hierarchy, depth, odd widths, palette roles.
- Minecraft.net, *Make Your Houses Better*: depth, texture mixing, detail blocks, landscape.
- CurseForge, *10 Questions with YUNG*: structures designed gameplay-first.

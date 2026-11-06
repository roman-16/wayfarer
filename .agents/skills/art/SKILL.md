---
name: art
description: Design how anything in Wayfarer looks - item, block and creature textures, item and creature models and their animations, structures, rooms and camps, so they read as if Mojang had made them and belong to their people in design/lore.md, and the pack's screens and HUD, which wear the stamp of design/style.md. Use whenever something a player sees is added or changed; the lore skill writes its words, this one its look.
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

4. **Show it to the user.** Before a look is final, put its pictures from the workshop or the driver to the user with the `questionnaire` tool, the recommended one first.

## The order of work

The same five steps make a building, a creature, an item or a screen, in this order, since a fault in an early step cannot be fixed by a later one:

1. **The feeling.** Say in one sentence what it should make a player feel (a burnt town: haste and grief; a hall: age and awe; the journal: a traveller's own book), and gather two or three references, from real places, other games, builders and artists. Write down what each gives (a colour, a shape, a texture, a setting) and rebuild that in the pack's own way; never copy one whole.
2. **The shape.** Block out the silhouette before any detail, and place the big features first: a building's masses with its wheel, tower or stair down, a creature's outline and its one idea, a screen's panels and the place the eye lands first. Start from a plain shape and break it once or twice, a notch, a jut, a taller part, a rounded corner; a plain box is dull and a shape broken everywhere is noise. A rectangle near 1 : 1.6, such as 9 by 15, sits well. Measure against what the player knows: a door for a building, the player for a creature, a slot for a screen.
3. **The colours.** Choose the palette before placing a block or a pixel: each colour with a darker, a lighter and a washed-out neighbour, a shade between any two that meet so they blend instead of jumping, and the part that must read apart, a roof, a mane, a panel's frame, in a colour of its own. Keep contrast to what the eye needs; every colour shouting at once looks like 2011.
4. **The texture.** Mix the same colour's smooth and rough variants of the same material, planks and stripped logs, smooth stone and cobble, a clean and a worn pixel ramp; rough where wear reaches, at the edges, the bottom and the weather side, smooth where it is sheltered. At the player's distance every block and pixel is seen, so each has a reason; a harsh jump between two colours gets a few of the shade between, down to a button or a sign.
5. **The ground.** It belongs where it stands: a building in its land, with what feet and weather leave around it, coarse dirt, gravel, pebbles, fallen leaves, a path to its door, and something that moves, smoke, water, a banner, an animal; a creature in its land, its light and its sounds; a screen over the world, under the shader. Without it, the thing looks dropped into a field.

Through all five, fly off and turn round often: judge it from far, where only the silhouette and the colours count, and from the player's eye up close, and redo what jars the moment it jars.

## Until it looks good

A look is done when it looks good, not when it is there. The first version that technically works is a start: a dead mule tipped onto its side or turned upside down is still a live mule lying wrong, with open eyes, stiff legs and a breath. Spend the time it takes:

- **Judge it where the player meets it.** Walk up to it at eye height, from every side and close enough to see the eyes, in its scene, not only in the workshop's daylight.
- **Every part says it.** Go through the thing part by part, the limbs, the head, the eyes, the ears, the tail, the cloth, and ask what each would be doing in that state; one part left at its default gives the whole away.
- **Change the method, not the bar.** When a cheap way cannot get there, a mod's pose, a vanilla trick or a tilted live creature, build what can: a renderer, a model or a texture of the pack's own.
- **Look again after every change**, with screenshots set side by side, until nothing in them still reads wrong.

## Variety

Variety is the key. Anything a player meets more than once, people, buildings, walls, camps, the things on a shelf, is boring the moment it repeats, and the eye finds a repeat at once.

- **Vary what the eye reads first.** A set of things differs in silhouette, layout, pose, material, colour and wear, never only in a number: three copies of one house a block taller or shorter, or thirty dead in one pose in the same clothes with the same wound, are one thing repeated.
- **Vary on several axes at once.** People differ in build, age, clothes and their colours, wear (soot, tears, mud, blood) and pose. Buildings differ in footprint, floors, roof, the mix of their walls, openings, porches, chimneys, yards and fences, and in how far they have fallen. A wall has depth, a second and a third block, a trim and a break; a flat run of one block is never a wall.
- **Variety has to make sense.** It follows what happened in the place, never chance alone: everything lies where its story put it. In a town that burned at night most of the dead lie indoors, where they hid, some at the doors they tried to get out of, fewer in the streets where they fled; a house is burnt where the fire took it; a camp's gear lies where its people used it.
- **The body says what the text says.** A person's pose, place and wear show their state before anyone speaks: the dying sit propped up, grey and slow, the dead slump or lie where they fell, the frightened crouch at the back of the room. A pose never contradicts the scene's lines.
- **Judge the whole set.** Photograph many of them together, a street or a crowd, and ask whether any two could be swapped without anyone noticing; if they could, they are not different enough.

## Textures

- **The grid is sacred.** Blocks and items are 16 by 16 pixels; one pixel on the texture is one pixel on the model, never stretched or squashed. Never mix resolutions (mixels): no texture drawn at a higher resolution, and no model element smaller than one pixel.
- **Few colours, in ramps.** Build each material as a ramp from a midtone with one shadow and one highlight first, then a few more shades; shift hue and saturation a little between shades, not only brightness. Keep a texture to about ten colours, and reuse vanilla's own ramps for vanilla materials: iron is vanilla's iron, oak is vanilla's oak.
- **Light from the top left.** Items are lit from the top left; a creature's top and front are brighter than its bottom and back, face by face and across faces.
- **Draw, never brush.** Place every pixel on purpose with a pencil, a fill and an eraser; no soft brushes and no filter noise, since noise adds no information.
- **Avoid the classic faults:** banding (shades in lines that show the grid), pillow shading (shades in rings from the edge inwards), pancake shading (light on one side, dark on the other regardless of shape), the darkest colour touching the lightest without a step between, lone pixels, staircase jaggies on lines that should have an even step, and large areas of one flat colour on rough materials.
- **Items** fit inside 16 by 16 with a clearly darker outline, shaded to the top-left light, and read at a glance in a hotbar slot: the silhouette carries the idea, the inside only the material. Tools and weapons lie on the diagonal, handle at the bottom left, as vanilla's do, and a tool's handle is vanilla's stick unless the lore says otherwise.
- **Blocks** look right on their own and tiled: check every block texture on a three by three wall early, with only two or three shades, and keep checking while shading; any repeat the eye can find is a tiling fault.
- **Creature textures** use box UV, unwrapped by the model, with north as the front.
- **Skins use both layers.** The outer layer (hat, jacket, sleeves and trousers), drawn half a pixel off the body, lifts what stands proud of it: hair and beards, collars, coats, shawls, aprons, belts, cuffs, boots and torn flaps, so a person has depth as vanilla's own skins do. It stays empty only where nothing stands out.
- **In this repository** item textures are drawn as rows of characters with a palette per variant in `generator/textures.toml`, written by `generator/png.py`; a new texture follows the same form. People's skins are drawn the same way, face by face, in `generator/skins.toml` (`generator/skins.py`): each person names their model (`humanoid` or `humanoid_slim`, whose arms are three pixels wide) and size, the letters their outer layer lifts from the body beneath it (`raised`, by part) or an outer grid of its own (`hat_*`, `jacket_*`, `sleeve_*`, `pants_*`), a palette whose letters are flat colours or three-shade ramps the generator mixes by coherent noise, and a grid per face of head, body, arms and legs, with the side face drawn once and mirrored; Easy NPC wears them from `wayfarer:textures/entity/people/`, and `person <skin> [pose]` in the workshop shows them. The settled world's ordinary people come from `generator/townsfolk.py`, which combines build, age, skin tone, hair, beard, clothes and their colours, and the wear of what happened to them (soot, singed hems and cuffs, torn sleeves, rips, mud, blood, a lost boot), into skins no two of which are alike; their poses, `generator/poses.py`, are Easy NPC poses the generator writes as data. A dead creature is a `wayfarer:fallen` body (`Fallen.lay`): `client/FallenRenderer.java` draws the creature's own vanilla model and texture from a model layer of its own, so Fresh Animations never moves it, lays it on its side and poses every part as death leaves it, the upper legs sagging to the ground, the neck stretched out with the head on the ground, the ears back and the tail limp, and draws its eyes shut over the texture the way Mojang shuts the sleeping fox's, with the white of the eye turned black (`generator/textures.toml`); never a tipped or upside-down live creature, which still stands, stares and breathes.

## Models

- **Few elements.** The model gives the shape, the texture the detail; keep the element count as low as the thing allows while it stays recognisable.
- **Plain shapes.** A round thing is one box (a barrel, a log, a pumpkin); a slant is one rotated element, not a staircase of small ones; neighbouring elements are never rotated to fake a curve. Small parts are a plane with transparent pixels rather than many tiny boxes.
- **Proportions tell character.** A block is 16 pixels, a metre; the player is bulkier than a person, so what they use is sized for them. Small things stay visible before they stay to scale. A big head on a small body reads as harmless, a heavy torso and arms as strong, a long thin body as wrong.
- **Simplify to the idea.** Mojang removed the phantom's mouth because it did nothing in play and looked like a sock puppet; every part of a creature must earn its place by showing what it does. Start from the creature's job in the story and its threat, then give it one clear idea: the warden is blind and hunts by sound, so its face has no eyes and its chest has the souls it hears with.
- **Silhouette first.** A creature must be known by its outline alone, at a distance, at night.
- **Animation is plain and readable.** Vanilla-like motion is a few bones swinging on sine curves, limbs opposite each other, a slow idle breath; no flourishes. Loops close cleanly, and the pack's Fresh Animations sets the bar for how lively a vanilla creature may be. Creatures are animated with GeckoLib (`design/systems.md`, M10), built in Blockbench, which an agent can drive through the Blockbench MCP plugin (T3).

## Structures, rooms and camps

- **Massing first.** Before a single detail, shape the volumes: a main mass and two or three smaller ones at different heights, a third to a half of the main one's size, so the outline reads against the sky. A single box is never a building.
- **One hero.** Every structure has one focal point, the gate, the tallest tower, the hall's calling stone, clearly bigger or brighter than the rest, and detail thickens towards it. Leave calm wall between detailed parts so the eye can rest.
- **Depth makes shadow.** Never put walls and pillars in one plane: pull the frame one block out, set windows and doors one block back, give roofs an overhang. Keep a roof's edge thin with slabs and stairs so it never looks thick, and let the side turned from the sun take the darker blocks. Stairs, slabs, trapdoors, walls, fences and panes add depth of less than a block. A wall over about 8 blocks without a pillar, band or recess looks empty.
- **Odd widths.** Wall runs of 5, 7 or 9 blocks have a centre for a door, a window or a roof peak; an even width has none.
- **Every block is what the thing is made of.** A tent's canvas is wool, a charred wall is dark wood, a people's hall is lit by lamps and candles; a block is never chosen for its colour or texture alone when what it is says otherwise (mushroom stems as canvas, dirt in a plank wall, froglights in a desert hall). This holds for every structure the pack builds, and a block from another mod's structure that reads wrong is cut through `generator/owners.toml`.
- **A palette of three to five, each with a job.** A base for the bulk, a darker block for recesses and shadow, a texture block mixed in at about one in four, a trim block, and one accent kept under a tenth of the faces. Blocks share an undertone; stretch one material through its stairs, slabs, walls and cracked, mossy, chiseled and polished variants before adding another. Depth comes from value contrast, not more colours. In this repository a material is a weighted list of blocks in a `Material`, mixed by coherent noise (`generator/blocks.py`), so neighbouring blocks agree instead of speckling.
- **Mark the way in.** Every building with a door or doorway marks it in its generator with `t.entrance((x, floor, z), facing)`, on the floor block under the threshold, facing out; `Footing` then sets the floor level with the land before that door, blends the land round it and cuts steps to it (`design/systems.md`, M59). A place with no way in, a camp or a ring of stones, stands where it moves the least earth.
- **Ground it.** A structure sits on its land: foundations reach down into the slope, the palette uses the land's own stone and wood, and paths and pieces follow the ground the way a village's do (M5); nothing floats or sits on a flattened square.
- **Wear tells time and story.** Weather where weather reaches: moss and cracks on the low, damp, shaded and north-facing parts, sound blocks high and sheltered. Break a ruin where it would break, the roof and the tops of walls first, and drop the fallen blocks below it; let roots, vines and a tree grow through what has stood longest. Keep the shape symmetric but the wear never mirrored. The generator's detail passes (`generator/detail.py`: rubble, moss, webs, roots, vines, skirting, cornices) do this by density and seed.
- **Every room was used.** A room shows what happened in it: who lived there, what they left, why they left. A hall of the Frost Walkers keeps their furs and their dead; a Gazebound outpost keeps their banners, their bread and their scars. Loot and books sit where their owner would have put them.
- **No two alike.** A place built from repeated pieces gives every piece kind variants that differ in what the eye reads first, as Variety asks, and turns them to face their street, so neighbours never match.
- **Gameplay first.** YUNG builds his structures for the way through them: more than one way in, places to be clever, rewards for the bold. A structure is a place to play before it is a picture.

## Screens and the HUD

Everything drawn over the world wears the stamp, `design/style.md`: the pack's HUD and screens, and vanilla's and the mods' interfaces with them. It does not follow Minecraft's interface; it follows its own system, and every screen is judged against the system, not against vanilla's menus.

- **Tokens first.** Every colour is a token in `style.toml`: a ramp shade (ash, leather, iron, paper, ember, gaze, red, frost, moss) or a text colour (bone, label, quiet, dim, gold, ink, ink red, danger, gaze). A colour the stamp lacks is added there with its meaning, never written into a sprite or the code; `just lint` fails on a colour literal in the interface code.
- **Components before screens.** A screen is built from the stamp's components (L5): panel, plate, band, strip, button, key cap, slot, tooltip, book, divider, mark, ring, meter, icons. A new need gets a component, drawn in `generator/stamp.py` from the shared parts (grain, rims, plates, rivets, cracks), shown on the style board (`board <page>`), and then used; never a one-off frame.
- **Materials say what a thing is.** Char for surfaces, iron for rims, caps, slots and keys, leather for strips, bands, buttons, ribbons and covers, parchment only for books and pages. Text is light on char and leather, ink on parchment.
- **Detail at the interface pixel.** One art pixel is one interface pixel at every scale: more shades, grain, wear and fittings than Minecraft's interface, never finer pixels; ornaments only at ends and corners.
- **Type.** Running text in m5x7, titles in Alagard (`Stamp.title`), labels in tracked capitals (`Stamp.label`), keys in key caps (`Inline`, from a key the server marks with `Tasks.key`).
- **Judge it where it lives.** Photograph it on the style board and where the player meets it, over snow at noon and in the dark, under the shader, at interface scales 2, 3 and 4.
- **The front door.** The title's logo is drawn in `generator/stamp.py` from the display face like any sprite; its panorama is a photograph of the world, framed like a structure (a hero to one side, depth and sky on the others, nothing at the lens), taken by `python3 scripts/client.py panorama` and judged on the title at every scale before it is kept.
- **Vanilla's and the mods' screens** come in through the stamp's own sprites for vanilla's widgets, the recolour of vanilla's six interface greys for every texture that copies them, and, where a mod draws in colours of its own, its config or a texture in its namespace (L6).

## Checklist

Before calling a look done, beside its vanilla relative:

- Would a player believe Mojang made it, or, on the screen, that it stands on the style board with the stamp's components?
- Up close, from every side, does every part read as what the story says it is, and does it look good, not only present?
- Seen many together, could any two be swapped unnoticed, and does where each stands make sense for what happened there?
- Does it read at a glance: an item in its slot, a creature by its outline at night, a structure by its silhouette from far off?

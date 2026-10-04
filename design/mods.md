# Mods

Mods, resource packs and data packs weighed for Wayfarer, each with a verdict, and what the pack could build itself. Nothing here is in the pack until Roman picks it. Checked on 02.10.2026 against Modrinth's and CurseForge's pages for a NeoForge 1.21.1 build; none of it was tried in the game yet.

**The shaders decide the atmosphere.** The shipped pack draws with a shader pack (Complementary Reimagined with Euphoria Patches) through Iris. A shader pack draws its own sky, fog, clouds, light shafts and bloom, and Iris ignores resource packs' core shaders and Polytone's post shaders while one is on. So mods and packs that change the fog, the sky or the lighting do nothing in the shipped pack, or fight the shader; particles, sounds, models and creatures work either way.

Verdicts: **Use** as it is, **Use changed** (configured or edited), **Take a feature** (build that part ourselves or with another mod), **Try** (judge in the workshop or a playtest before deciding), **Leave out**.

## The list

| Asked for | What it is | NeoForge 1.21.1 | Verdict | Why |
|---|---|---|---|---|
| Windy | Wind streaks drifting through the air, client-side (LGPL-3.0) | 1.1.1 | Try | Wind travels with the player, which is what the dense road wants (ground truth 2), and particles survive shaders; judge whether it reads as wind or as clutter beside Particle Rain's storm wind |
| Bushy pink petals, wildflowers & leaf litter | Thicker models for three flower blocks (Apache-2.0) | Yes, but wildflowers and leaf litter only exist from 1.21.5 | Try | Only the pink petals part applies; judge it beside vanilla with the art skill |
| Bushier Bushes | Fuller models for the bush blocks | No: the bush blocks came in 1.21.5 | Leave out | Its blocks do not exist in 1.21.1. For fuller foliage, Motschen's Better Leaves (MIT, works with shaders) is the 1.21.1 alternative, to be judged in the workshop |
| xali's Enhanced Vanilla | Small texture updates across vanilla | Newest build is for 1.20.5 to 1.21, and it expects OptiFine's features | Leave out | It restyles the vanilla textures every Wayfarer texture is judged against (art skill), and half of it needs OptiFine |
| Auroras | Auroras in cold night skies | Already in the pack (1.6.2) | Use | Kept; whether Complementary's own aurora doubles it is not checked |
| Biome Particle Weather | Weather drawn as particles | Fabric and Forge only | Leave out | Particle Rain, already in the pack, does the same on NeoForge, by the same idea |
| Visual Effects+ | Biome fog, colored fog and particles through Polytone (All Rights Reserved) | Yes | Take a feature | Its fog is the shader's job; its biome particles are worth having, built as our own Polytone data (see below) |
| Shine | Bloom, colored and rim lighting, water reflections, grass blades (All Rights Reserved, needs Sodium) | 2.0.2 | Leave out | Its own page says bloom, post-processing and rim light switch off while an Iris shader pack is on; what is left overlaps Subtle Effects and Particular |
| Wakes | Wakes and splashes behind boats | Fabric only; Wakes Reforged (GPL-3.0) is the NeoForge port, 1.4.1 | Try Wakes Reforged | Act 2 crosses the sea (S2, M10); whether it draws wakes behind Small Ships' ships, not only vanilla boats, is not checked |
| Particular Reforged | Falling leaves from every tree, cave dust, 3D water splashes, waterfall cascades, fireflies (LGPL-3.0) | 1.5.7 | Use changed | Falling leaves, cave dust and splashes are new to the pack; its fireflies, cascades and chest bubbles are off, since Subtle Effects already has them |
| Cosy Critters & Creepy Crawlies | Bird flocks that land and scatter, moths at exposed lights, tiny spiders from cobwebs, client-side (MIT) | 0.3.3 | Use | Wildlife on the road is the minute loop's own example (D2); moths at the caravan's lanterns and spiders in old halls tell where people are and are not. Its Hat Man easter egg stays off |
| Benigamer's Enhanced Visuals | Biome fog, sky and grass colours and particles through Polytone (All Rights Reserved) | Yes | Leave out | Its fog and sky are the shader's job, and new grass colours take the land away from vanilla's |
| Recrafted | A paid, early-access overhaul of textures, sounds and visuals | Only 26.1.2, needs Polytone, Continuity, EMF and Sodium | Leave out | Wrong version, paid, and a new look rather than vanilla's |
| Soft Imprints | Footprints in snow and sand from anything that walks, client-side (MIT) | 2.8.0 | Use | Tracks in snow are the Frost Walkers' land and Nell's hunt (1.6, M10's Track); it is only a picture on each player's screen, so the trails a quest lays stay the mod's own |
| Musgo | Alternative log and leaf models | Found only on re-upload sites, not on Modrinth or CurseForge | Leave out | packwiz installs from Modrinth or CurseForge only, and its origin is not known |
| Sunbathing Godrays | Light shafts through Polytone post shaders (MIT) | Yes, with Polytone | Leave out | Complementary already draws light shafts; whether a Polytone post shader even runs under an Iris shader pack is not checked |
| Better Smoke | Taller, drifting campfire smoke | Fabric only | Take a feature | The caravan's smoke seen from afar is the lore's own sign (lore: "in the evening their smoke shows me where"); build it ourselves (see below) |
| Nature X | Fuller foliage, 3D plants, core shaders (All Rights Reserved) | 12.2, needs Sodium, Continuity and Polytone | Leave out | Its core shaders do nothing under Iris, and its fuller look leaves vanilla's |
| Atmospherics | Per-biome fog, sky, clouds, stars and comets in real time (All Rights Reserved) | 2.6.5.2 | Leave out | All of it is what the shader draws; it only shows with shaders off |
| Maa's Morning Fog | Fog at dawn that lifts, as a data pack | Only 1.21.11, which made fog data | Leave out | 1.21.1 has no fog in data; a morning fog would come from the shader's settings, if Euphoria offers one (not checked) |
| True Darkness | Pitch-black moonless nights and caves | No 1.21.1 build; Truly Dark (All Rights Reserved) has one | Leave out | More Darkness, already in the pack, does this with moon phases and eyes that adapt to the dark |

## Already in the pack, worth changing

| Mod | Change | Why |
|---|---|---|
| Subtle Effects | Add the pack's biomes to its biome particles: pollen in Terralith's flower lands, spores in its mushroom lands, sculk dust stays in the deep dark | Its config lists only vanilla's biomes (`pollenBiomes`, `mushroomSporeBiomes`), so most of the pack's lands get none |
| Particle Rain | Its mist and haze, judged with the shader on | Mist after rain and in wet lands is the morning and swamp feel the fog packs promised; the haze is off by default because it can look odd with shaders |

## Worth adding

| Mod | What it is | NeoForge 1.21.1 | Verdict | Why |
|---|---|---|---|---|
| Polytone | Resource packs that add particles at blocks and in biomes, block and biome colours, sounds, lightmaps (by the author of Supplementaries and Moonlight, already in the pack) | 1.21-4.5.3 | Use | The tool for the pack's own atmosphere as data: ash drifting in burnt towns (S11), embers above the caravan's fire, motes in the halls, each written in the generator like the rest |
| Snow! Real Magic! | Snow that piles up in snowfall and settles on leaves and plants (MIT) | 12.2.3 | Try | The Frost Walkers' land and the cold gate (M11) get snow that builds while the team walks; it changes blocks over time, so camps and wards must put it back or keep it off |
| Not Enough Animations | Third-person animations for eating, reading maps and books, holding items | 1.12.6 | Use | In a team of one to four everyone watches everyone: a friend reading the journal or eating at the fire shows it |
| Camera Overhaul | Gentle camera roll and sway when moving and turning (GPL-3.0) | 2.1.2 | Try | Makes walking feel like walking; some players get seasick from it, so it ships gentle and can be turned off |
| CMDCam | Smooth camera paths for scenes (LGPL-2.1) | 2.2.9 | Try | The memories' openings (M8), the reveals and the doors (M14) want a camera that moves on its own; weighed with HollowEngine at the memories |
| Serene Seasons | Seasons that change colours and temperatures, from the makers of Tough As Nails (All Rights Reserved) | 10.1.0.9 | A design question first | The journey runs for many weeks of nights, so seasons could pass across the acts and winter could close passes; it changes land gates, the clock and the cold, so it belongs in `systems.md` before any build |
| Noisium | Faster world generation | 2.3.0, from August 2024 | Try | The camp's search reads the generator's heights at 2 to 10 ms a column (M5); whether it works beside C2ME, already in the pack, is not checked |

Checked and not for 1.21.1 on NeoForge: Presence Footsteps, Falling Leaves, Visuality and FabricSit. Left out on purpose: LambDynamicLights, since the shader already lights a held torch, and Emotecraft, which is far bigger than the one pose the fire needs.

## Worth building

| Idea | What the player sees | How | Ties to |
|---|---|---|---|
| The caravan's smoke from afar | A thin column of smoke on the evening horizon, over the hills, long before the camp is in sight | Particles end at the view distance, so the column is drawn in Distant Horizons' far view through its API for custom shapes (`IDhApiCustomRenderRegister`, read in its code, not tried) | Lore: "in the evening their smoke shows me where"; M19 |
| Sitting at the fire | Players and the caravan's people sit on the fire's logs, and sitting is the rest that lets an early afternoon pass | A seat on the fire's logs in the Wayfarer mod; Easy NPC's sitting pose for the people | M20, the fire (M12) |
| Ash of the burnt towns | Ash drifts over black timber and settles on the road; the gaze's creatures crumble to ash at dawn | Polytone particles at the burnt towns' blocks, and the Wayfarer mod's own at dawn | S11, Daylight (M15) |
| Tracks a quest lays | Hoofprints, blood and a cold fire leading over the ridge, there for every player | Marks laid like road scenes and taken away after (M4), drawn with the ember sense's glow | M10's Track and the ember sense |
| The shine you can see | A friend's eyes glow faintly in the dark, brighter with every shard | A glowing layer on the player's eyes, drawn by the Wayfarer mod and kept by the shader | S12, M9 |

## What to look at next

Each Use and Try goes into the dev client with the shader on, beside the pack as it is, and is judged with the art skill: whether it reads as Minecraft, whether it fights the shader, and its cost in frames (`perf`). The dev client draws without shaders, so the shader's side is judged in the exported pack (`just pack`), as the playtest skill does.

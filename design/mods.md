# Mods

Mods, resource packs and data packs weighed for Wayfarer, each with a verdict, and what the pack could build itself. Nothing here is in the pack until the user picks it. The user picked every Use in both lists and the cuts; they go into the pack in phase 4, Serene Seasons with it (M25), and RoadWeaver stays out (M24). Checked on 02.10.2026, 04.10.2026 and 09.10.2026 against Modrinth's and CurseForge's pages for a NeoForge 1.21.1 build; none of it was tried in the game yet.

**The shaders decide the atmosphere.** The shipped pack draws with a shader pack (Complementary Reimagined with Euphoria Patches) through Iris. A shader pack draws its own sky, fog, clouds, light shafts and bloom, and Iris ignores resource packs' core shaders and Polytone's post shaders while one is on. So mods and packs that change the fog, the sky or the lighting do nothing in the shipped pack, or fight the shader; particles, sounds, models and creatures work either way.

Verdicts: **Use** as it is, **Use changed** (configured or edited), **Take a feature** (build that part ourselves or with another mod), **Try** (judge in the workshop or a playtest before deciding), **Leave out**.

## The list

| Asked for | What it is | NeoForge 1.21.1 | Verdict | Why |
|---|---|---|---|---|
| Windy | Wind streaks drifting through the air, client-side (LGPL-3.0) | 1.1.1 | Use | Wind travels with the player, which is what the dense road wants (ground truth 2), and particles survive shaders; judge whether it reads as wind or as clutter beside Particle Rain's storm wind |
| Bushy pink petals, wildflowers & leaf litter | Thicker models for three flower blocks (Apache-2.0) | Yes, but wildflowers and leaf litter only exist from 1.21.5 | Use | Only the pink petals part applies; judge it beside vanilla with the art skill |
| Bushier Bushes | Fuller models for the bush blocks | No: the bush blocks came in 1.21.5 | Leave out | Its blocks do not exist in 1.21.1. For fuller foliage, Motschen's Better Leaves (MIT, works with shaders) is the 1.21.1 alternative, to be judged in the workshop |
| xali's Enhanced Vanilla | Small texture updates across vanilla | Newest build is for 1.20.5 to 1.21, and it expects OptiFine's features | Leave out | It restyles the vanilla textures every Wayfarer texture is judged against (art skill), and half of it needs OptiFine |
| Auroras | Auroras in cold night skies | Already in the pack (1.6.2) | Use | Kept; whether Complementary's own aurora doubles it is not checked |
| Biome Particle Weather | Weather drawn as particles | Fabric and Forge only | Leave out | Particle Rain, already in the pack, does the same on NeoForge, by the same idea |
| Visual Effects+ | Biome fog, colored fog and particles through Polytone (All Rights Reserved) | Yes | Take a feature | Its fog is the shader's job; its biome particles are worth having, built as our own Polytone data (see below) |
| Shine | Bloom, colored and rim lighting, water reflections, grass blades (All Rights Reserved, needs Sodium) | 2.0.2 | Leave out | Its own page says bloom, post-processing and rim light switch off while an Iris shader pack is on; what is left overlaps Subtle Effects and Particular |
| Wakes | Wakes and splashes behind boats | Fabric only; Wakes Reforged (GPL-3.0) is the NeoForge port, 1.4.1 | Use Wakes Reforged | Act 2 crosses the sea (S2, M10); whether it draws wakes behind Small Ships' ships, not only vanilla boats, is not checked |
| Particular Reforged | Falling leaves from every tree, cave dust, 3D water splashes, waterfall cascades, fireflies (LGPL-3.0) | 1.5.7 | Use changed | Falling leaves, cave dust and splashes are new to the pack; its fireflies, cascades and chest bubbles are off, since Subtle Effects already has them |
| Cosy Critters & Creepy Crawlies | Bird flocks that land and scatter, moths at exposed lights, tiny spiders from cobwebs, client-side (MIT) | 0.3.3 | Leave out | Its birds, moths and spiders are flat sprites, which read as stickers beside the vanilla creatures around them |
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

## Roads, rivers, animals, animations and the interface

| Asked for | What it is | NeoForge 1.21.1 | Verdict | Why |
|---|---|---|---|---|
| The Roads More Travelled | Grass, dirt, sand and plants wear into paths where players, riders and led animals walk often, and grow back when left (CC-BY-NC-4.0) | 0.5.1 | Use | The caravan's camp ground and a returning team's trail wear in on their own, which old campsites (M21) and Track want; whether a team that keeps moving wears a path at all is watched in the playtest |
| Respawning Animals | Wild animals despawn far from players and spawn again, as monsters do; tamed, named and ridden ones stay (MPL-2.0, Puzzles Lib) | 21.1.2 | Use | Breeding and planting are cut (D7), so hunting feeds the team; without it every land the team camps in or comes back to is hunted empty for good |
| RoadWeaver | Roads woven between villages and chosen structures, with bridges, tunnels, signposts, lamps and roadside benches and campfires (MIT) | 2.3.1 | Leave out (M24) | The lore already has old roads, where the world's waystones stand; but roads change how the team crosses the land, where road scenes (M4) and the caravan's camps (M5) go, and whether "Sparse world, dense road" means a road you follow; with villages 8.5 times farther apart they would be very long, and its lamps and signposts are not vanilla's |
| Streams: Reflowing | Streams that follow the land downhill, pool into lakes and drain into rivers that flow to the sea, boats able to go upstream (All Rights Reserved) | 2.14.4 | Use | Water to follow and to camp by (M5 wants water near the camp), and currents for Act 2's boats; it adds world generation, so the boot test's time is watched, and SeedGen's far view will not draw the streams |
| Blooming Biosphere | A data pack rewriting every overworld biome, with seven new ones and new sky, fog and grass colours (CC-BY-NC-SA-4.0) | A data pack for 1.21.1 (1.1.6, February 2025) | Leave out | Terralith and Tectonic already shape the land; two biome rewrites fight each other, and its colours are the shader's and the art skill's to judge |
| Immersive Thunder | Thunder that sounds the way distance makes it sound | Only 1.21.5 and newer | Use Distant Thunders instead | Distant Thunders (MIT, 1.1.0 for NeoForge 1.21.1) delays thunder by the distance of the strike; a storm by day brings the gaze's creatures (M15), so hearing how far off it is matters |
| Mod Menu | A list of the installed mods | Fabric and Quilt only | Leave out | NeoForge's own Mods screen does it |
| Chat Animation | Chat lines slide in smoothly (MIT) | 1.3.3 | Use | Conversations with the caravan's people and the voice run in chat (M3), line by line; lines that arrive gently read as speech, not as a log |
| Immersive UI | Animated inventory: particle trails behind rare items, wiggling and growing items, a sliding hotbar (All Rights Reserved, Shatterbyte Lib) | 0.3.3 | Use | The user's pick: an inventory that feels alive, and its trails mark the rare finds the gear road (M18) is made of |
| Inventory Sorter | A middle click sorts the inventory under the cursor, on the server (GPL-3.0, on CurseForge only) | 24.0.24 | Use | The user's pick: one click sorts the player's own inventory or a chest alike, where Nemo's Inventory Sorting gives each inventory a key of its own and cannot tell which one the cursor is over; its mouse-wheel moving stays off, since Mouse Tweaks has the wheel |
| Visual Workbench | Items lie on the crafting table while crafting (MPL-2.0, Puzzles Lib, already in the pack) | 21.1.2 | Use | Small, looks like vanilla, and the bench stays the player's own instant craft (M18) |
| Tooltip Rareness | Item names coloured by rarity groups | Fabric only | Use Rarity Core instead | The user's pick. Rarity Core (GPL-3.0, 1211.14.9 for NeoForge 1.21.1) gives every item one of seven rarities, from its own lists for over 70 mods and from its recipes, and draws it behind the item, which the pack keeps for the Epic tier and above (purple, gold, light red, red, in `pack/config/raritycore/RarityStyle.json`) so a backdrop marks a find; it feeds Legendary Tooltips' borders and Immersive UI's trails |
| Legendary Tooltips | Ornate tooltip borders by rarity (CC-BY-NC-ND-4.0, needs Iceberg and Prism Lib) | 1.5.5 | Leave out | The stamp draws every tooltip itself, with an ember rim for the story's own things (`style.md`, L5), and an item's worth already shows in its name's colour and Rarity Core's line; a second frame system would break the stamp |
| Detailed Animations | A resource pack for Entity Model Features: walking, running, climbing, jumping and landing animations for players (All Rights Reserved) | Yes, its dependencies are in the pack | Use | In a team everyone watches everyone walk; whether it fights Better Combat's attack animations and Not Enough Animations is checked when it goes in |
| Not Enough Animations | Third-person eating, map reading and item holding | 1.12.6 | Use | As above in Worth adding |
| 3D Skin Layers | The outer layer of a skin drawn in 3D | 1.11.3 | Use | Players are seen up close all evening at the fire; whether it also draws Easy NPC's people's skins is not checked |
| Fresh Animations | Animated vanilla creatures through Entity Model Features | Already in the pack | Use | Kept |
| More Mob Variants | New looks for old creatures: zombies, skeletons, spiders, cows and more | Only up to 1.20 on Fabric and Forge | Leave out | Its NeoForge 1.21.1 port, More Mob Variants: Renewed (V1.0.0), crashes a dedicated server at startup, loading a client renderer class |
| Spawn Animations | Monsters dig out of the ground or appear in a puff when they spawn; server-side | 1.11.6 | Use | The gaze's creatures crumble at dawn (M15); rising out of the earth at dusk is the other half, and it shows a team where the night begins; its licence is the author's own and not read |
| Flow | Eased inventory transitions | Fabric only | Leave out | Not on NeoForge, and a flourish vanilla's interface does not have |
| Eating Animation | First-person eating and drinking sprites | Fabric only | Leave out | No NeoForge port; Not Enough Animations covers what friends see |
| LambDynamicLights | Light from held and dropped torches | 4.8.11 | Leave out | As before: the shader already lights what a player holds |
| Continuity | Connected textures for glass, bookshelves and the like | Only through Sinytra Connector | Leave out | Native alternatives exist (Fusion, Athena), but the pack ships no resource pack that uses connected textures, and seamless glass is not vanilla's look |

## The front door, the map and a greeting

| Asked for | What it is | NeoForge 1.21.1 | Verdict | Why |
|---|---|---|---|---|
| FancyMenu | Lays out the title screen, the pause menu and other screens as data, with conditions, variables and text read from files; its addon Drippy Loading Screen does the game's own loading screen (DSMSL 3.1, a licence of its own) | 3.9.14 | Leave out | I6 needs no layout tool: the logo and the panorama replace vanilla's textures, the splash lines are a vanilla option, and the recap and the pause menu's Journal and Map need the Wayfarer mod's own code anyway, so FancyMenu would only move the drawing into its files, at the cost of two more mods and a licence of its own. The Wayfarer mod draws the front door |
| Xaero's World Map | A self-writing fullscreen map, with server-side profiles that switch features off per player (All Rights Reserved) | 1.47.0 | Keep JourneyMap | JourneyMap is in and working (M2): the pack's server settings switch off its minimap, cave mapping, radar and waypoints for everyone, and the places found are marks the mod draws through its stable API. Xaero's looks cleaner and is lighter, and its server profiles could do the switching too, but how a mod draws its own marks on it is not checked, and the switch would rebuild the map link and the marks. Worth a look side by side if JourneyMap's map fails I5's look |
| Dap ur Homies | Fist-bump a friend with a charged key: five tiers up to explosions and a fire sphere, plus throwing and catching each other (MIT) | 3.0.0, but on NeoForge only through Sinytra Connector and Forgified Fabric API | Leave out; Emotecraft carries the greeting | It needs a Fabric bridge under the whole pack, and its explosions and throws break the road's rules and its tone |
| Emotecraft | An emote wheel on a key, emotes synced to every player, new emotes made in Blockbench (GPL-3.0, needed on both sides) | 2.4.12 | Try, with the pack's own emotes | A team of friends gets a language without chat: a clasped-hands greeting for two, a wave, warming the hands at a fire, sitting at the fire, kneeling at a grave, pointing the way, each made with the art skill so it looks vanilla. Its wheel keeps a key, since it is social and changes nothing in the world. It carries its own copy of the player-animation library that Better Combat and Combat Roll share, so it is tried beside them and Not Enough Animations first; players may still add emotes of their own |

## From playing Prominence II

The user and a friend played Prominence II: Hasturian Era on 09.10.2026; the user picked these two from it.

| Asked for | What it is | NeoForge 1.21.1 | Verdict | Why |
|---|---|---|---|---|
| What Are They Up To | Lets every player see when another is typing, has a screen open or is idle, drawn in the world (All Rights Reserved, on client and server; needs CoroUtil, All Rights Reserved) | 1.21.0-1.2.7, CoroUtil 1.21.0-1.3.8 | Use | The user's pick: in a team of one to four, a friend standing still with the journal, the map or the inventory open shows it, so nobody talks to someone who is not there; alone it shows nothing. Checked when it goes in: whether its poses fight Better Combat, Not Enough Animations and Detailed Animations, and whether what it draws reads as Minecraft (art skill) |
| HT's TreeChop | Trees come down chop by chop instead of log by log: swings at the trunk wear it down until the whole tree falls (MIT, on client and server) | 0.19.3 | Use | The user's pick: a felled tree falls as a tree, instead of leaving a floating crown. Checked when it goes in, none of it read in its code yet: the First Camp memory, where the fallen pine's logs are broken and the middle pine must stand for its notch; whether its fells respect the wards round the Last Camp, Tollbridge, the caravan's camp and the Diggers' hall, and whether it fells buildings made of logs; its keys, entered unbound in `keybindings.txt`; and any mark it draws by the crosshair, weighed against the stamp |

## Already in the pack, worth changing

| Mod | Change | Why |
|---|---|---|
| Subtle Effects | Add the pack's biomes to its biome particles: pollen in Terralith's flower lands, spores in its mushroom lands, sculk dust stays in the deep dark | Its config lists only vanilla's biomes (`pollenBiomes`, `mushroomSporeBiomes`), so most of the pack's lands get none |
| Simple Voice Chat | Ships its setup guide as done, with voice activation on and the microphone unmuted; V opens its menu to switch to push-to-talk or change the sensitivity | Its "Press V to set up" line is a greeting the design does not have, and with every voice key unbound a skipped guide would leave nobody able to talk; friends talk as in a call from the first join (the user's choice, 07.10.2026) |
| Particle Rain | Its mist and haze, judged with the shader on | Mist after rain and in wet lands is the morning and swamp feel the fog packs promised; the haze is off by default because it can look odd with shaders |

## Cuts in mods already in the pack

Already decided and still in the pack, carried out in phase 4: Comforts' sleeping bags and hammocks, Bountiful, Nature's Compass, Just Enough Resources, Exposure's cameras, FTB Quests, FTB Teams, FTB Library and Patchouli (the journal takes their place), the cut Artifacts, Friends & Foes' Moobloom and built golems, Wilder Nature's Minisheep, Immersive Aircraft's biplane and quadrocopter, the cut backpack upgrades, the other moons, and vanilla's enchanting table, anvil, grindstone, beds, ender chest, lodestone and recovery compasses (D7).

Found in the pack's mods, each breaking a Settled rule:

| Mod | Cut | The rule it breaks |
|---|---|---|
| Waystones | Warp stones, warp, bound, return and portal scrolls, warp plates, portstones, sharestones, attuned shards, and crafting waystones | One stone of one's own, and the world's waystones fold only roads already walked (M23); a warp stone folds from anywhere, and plates and sharestones are a teleport network |
| Silent Gear | Its elytra | Flight opens with Act 3's first quest (S16, 3.2 Wings); a crafted elytra flies whenever its materials are found |
| Silent Gear | Its stone anvil, flax and fluffy seeds, and netherwood sapling | Anvils are cut, planting is cut, and there is no Nether (D7) |
| Spelunkery | Its diamond grindstone and magnetic compass | The grindstone is cut; a compass that finds ore undoes "ore in places" and the cut compasses (what it points at is not checked) |
| Wilder Nature | Its bounty board with commissions, provision requests and elite bounties, and its Burst of Experience | Bounty boards are cut with Bountiful, and experience is cut (D7) |
| Sophisticated Core | Its ender linker and experience bucket | Ender chests and experience are cut (D7) |
| Just Zoom | The whole mod | Far-sight, a shard's gift, is the zoom (M11); while everyone can zoom, the gift gives nothing. The zoom moves into the gift |
| Farmer's Delight, Supplementaries | Their seeds and saplings stay as ingredients and never take root; their cabinets, baskets, crates, sacks, safes and jars hold things only inside a tent | Planting is cut and storage lives in the tent (D7); the rules cover them once they are built, so they are a check, not a cut |

## Worth adding

| Mod | What it is | NeoForge 1.21.1 | Verdict | Why |
|---|---|---|---|---|
| Polytone | Resource packs that add particles at blocks and in biomes, block and biome colours, sounds, lightmaps (by the author of Supplementaries and Moonlight, already in the pack) | 1.21-4.5.3 | Use | The tool for the pack's own atmosphere as data: ash drifting in burnt towns (S11), embers above the caravan's fire, motes in the halls, each written in the generator like the rest |
| Snow! Real Magic! | Snow that piles up in snowfall and settles on leaves and plants (MIT) | 12.2.3 | Use | The Frost Walkers' land and the cold gate (M11) get snow that builds while the team walks; it changes blocks over time, so camps and wards must put it back or keep it off |
| Not Enough Animations | Third-person animations for eating, reading maps and books, holding items | 1.12.6 | Use | In a team of one to four everyone watches everyone: a friend reading the journal or eating at the fire shows it |
| Camera Overhaul | Gentle camera roll and sway when moving and turning (GPL-3.0) | 2.1.2 | Leave out | The user tried it in play and disliked the view swaying with every step to the side |
| CMDCam | Smooth camera paths for scenes (LGPL-2.1) | 2.2.9 | Use | The memories' openings (M8), the reveals and the doors (M14) want a camera that moves on its own; weighed with HollowEngine at the memories |
| Serene Seasons | Seasons that change colours and temperatures, from the makers of Tough As Nails (All Rights Reserved) | 10.1.0.9 | Use (M25) | The story sets the season by act, with the cycle stopped while it runs (M25) |
| Noisium | Faster world generation | 2.3.0, from August 2024 | Use | The camp's search reads the generator's heights at 2 to 10 ms a column (M5); whether it works beside C2ME, already in the pack, is not checked |
| Carry On | Pick up and carry creatures and blocks with what is in them (LGPL-3.0) | Yes | Use changed | Act 3's Carry (someone on your back, no running) is this mod's core; limited to the people a quest names, since carrying full chests around sidesteps "storage only in a tent" |
| ParCool | Climbing ledges, vaulting, wall runs and rolls (LGPL-3.0) | 3.4.3.3; 4.0.1.0 crashes a dedicated server at startup | Use changed | Reach (a goal at a hard place) gets a body that climbs; only climbing and vaulting, since Combat Roll already rolls and wall runs are not vanilla's feel |

Checked and not for 1.21.1 on NeoForge: Presence Footsteps, Falling Leaves, Visuality and FabricSit. Left out on purpose: LambDynamicLights, since the shader already lights a held torch.

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

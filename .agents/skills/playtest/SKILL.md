---
name: playtest
description: The in-game checklist for what Wayfarer's tests cannot see - shaders, sound, the HUD, shrines, nights, flight and balance - handed to the user after a change, with the results written back into balance.toml or the right config. Use after changing anything the player sees, hears or feels, or when the user asks what to playtest.
---

# Playtest

`just test` proves the pack boots, every shrine generates and the stronghold ring sits at its radius. It cannot judge how anything looks, sounds or feels. That is the user's, in game.

## Hand the user a checklist

Pick the items the change touched, and give each as one line: where to go, what to do, what should happen. `just showroom` opens the showroom, where every shrine stands in a row and the compass points at a waymark 750 blocks west of the camp; `/locate structure wayfarer:shrine/<eye>` and `/tp` find the shrines in the natural world next to it. `just play` opens a fresh world in survival, for everything that has to be met the way a player meets it. Both run without shaders, without Distant Horizons (unless started with `far`, which keeps a far view of 1,024 blocks) and at 8 chunks, so shaders, the far view and anything judged by its look in the world are checked in the exported pack: `just pack`, imported into Prism Launcher.

| Area | Check |
|---|---|
| First hour | No tutorial or recipe toasts appear; there is no quest book; the Last Camp sits whole at spawn, its fire burning |
| Tent | No title flashes on entering; no monster follows through the door or spawns inside |
| Shrine | The entrance blends into the land and is easy to walk past, yet findable once the eye sits on the ribbon; the way down (spiral, switchback or ladder) is walkable without ducking and climbs back out; the hall is lit, quiet and beautiful; the people's name fades in underground with the eye beneath it; the eye floats over the pedestal until your team takes it, then is gone for your team only; looking at the eye outlines the pedestal and a right-click takes the shard; the hall floor is whole, with no pits; the lectern opens the memory for that shard, then the people's own book |
| Shards | A taken shard lands in the Shards slots of the Curios panel for every teammate, cannot be taken out and stays after death; with all twelve, one click on a frame sets them all |
| Compass | The ribbon turns with the player; it tells that a shard calls, never which one; far away the heat words follow the walk within about 20 blocks and the distance rounds to 500; inside a shrine hall it reads a hallowed hall, and in a fire's circle on an ordinary night or by day it reads the fire's circle; within 500 blocks the eye sits on the ribbon at the entrance; teammates and your grave show on it; it goes quiet in a plane |
| Gaze | The bar fills while flying and drains four times slower on the ground; at night it fills at once; phantoms come when it is full; a veil lengthens it |
| Nights | Dawn warns of a horde night (every fourth), a blood moon (every fifth) and both (every twentieth) with a title, a chat line and the icon beside the compass; on horde nights nobody sleeps, no tent opens and anyone inside is set outside at dusk; a shrine hall still shelters; blood moons are red and still sleepable |
| Fire | On an ordinary night monsters stop at the edge of a lit campfire's circle, one that wanders in turns away, and skeletons do not shoot into it; on a blood moon they walk in; on a horde night the horde comes through; Illagers walk up to it; standing still at the fire for half a minute gives Warm until tomorrow's dusk, and hunger drains half as fast while it lasts |
| Stillness | On the second day within 256 blocks of one place the ember dims; on the third the compass reads the ember is dim and a horde comes that night; walking away brightens it |
| Glimpse | From seven shards, standing still at night outlines every monster within 32 blocks through stone, visible under the shaders |
| After the dragon | Hunger is normal, no horde nights, no blood moons, no dimming, monsters stop growing stronger |
| Death | A gravestone stands where you fell; breaking it puts every item back in its slot, backpack and veil included |
| Unlocks | Ships refuse to carry the player before 4 eyes, aircraft and wings before 6, with the message saying why |
| Daylight | After sunrise, monsters under the open sky crumble to ash within about a minute, helmets or not; in caves and under roofs they stay; named monsters and Illagers stay; the first time, a grey line explains why |
| Rowing | A thousand blocks in a boat or ship cost about two and a half shanks |
| Inventory | C sorts your inventory A-Z in every inventory screen; with any screen open but a text field focused, WASD keeps walking; items show their rarity behind them, rare ones trail sparks and get a framed tooltip |
| Silent Gear | Its blueprints, parts and machines work; crimson iron ore turns up in the deep dark; crimson steel takes breeze rods; the alloy forge takes polished deepslate; there is no Silent Gear elytra or stone anvil |
| Loot | Every structure chest opens as a Lootr chest at once, never with Lootr's emergency conversion message |
| Last Camp | Sits whole at spawn on the first world, not buried or floating |
| Looks and sound | In the exported pack: shaders, Distant Horizons at 1024 chunks, ambience and music; the far view fills in fast around a new place, its land matches what you find on arrival, it shows no buildings until you come near, and it keeps up on a ship and in the air; biomes feel wide and Geophilic's touches sit well beside Terralith; structures and desert or swamp decorations are rare, not a skyline; caves open to the surface often enough; Jade stays in the corner and ignores plants; note anything that clashes |
| Places | Over a few thousand blocks: a village about every 1,024 blocks, a Gazebound outpost every 2,048, ruins of burnt towns and old campsites every few hundred, towns and forts rare, a capital rarer still; no duck, beach bar, snowman, hobbit hole, Christmas village, floating island or mage tower; Nether blocks in old buildings read as deepslate, mangrove and dark oak |
| The road's rules | No experience orbs or bar; enchanting tables, anvils and grindstones lie cold; saplings and seeds will not take; chests, barrels and placed backpacks stand only in a tent; beds sleep only in a tent; animals do not breed; pumpkins on iron or snow make no golem; each refusal says why above the hotbar |
| The land | Late summer, and it stays late summer; animals come back to land hunted bare; monsters dig up out of the ground at dusk; streams run downhill into lakes and rivers; well-walked grass wears into a path; snow piles up in snowfall; footprints show in snow and sand |
| Ore | Thin in plain stone, coal more than iron and copper, gold, redstone and lapis rare, no diamonds or emeralds; old mines and the Ancient Cities rich as ever |
| Feel | Thunder rolls in seconds after a far strike; chat lines slide in; players' skins show their outer layer in 3D and others eat and read maps in third person; the camera sways gently; leaves fall, birds land and scatter, moths circle lights; ParCool climbs ledges and vaults, nothing else; Carry On picks up nothing |
| Balance | How long each leg to the next eye felt, whether nights felt hard but fair, whether gear was worth making, and whether hunger drove foraging without nagging |

## Bring the results back

Every number the user wants changed goes to `balance.toml`, or to the mod's config in `pack/config` when it is another mod's number. Worldgen numbers (shrine and stronghold distances, structure spread) only change new chunks: test them in a fresh world.

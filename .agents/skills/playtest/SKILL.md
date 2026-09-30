---
name: playtest
description: The in-game checklist for what Wayfarer's tests cannot see - shaders, sound, the HUD, shrines, nights, flight and balance - handed to the user after a change, with the results written back into balance.toml or the right config. Use after changing anything the player sees, hears or feels, or when the user asks what to playtest.
---

# Playtest

`just test` proves the pack boots, every shrine generates and the stronghold ring sits at its radius. It cannot judge how anything looks, sounds or feels. That is the user's, in game.

## Hand the user a checklist

Pick the items the change touched, and give each as one line: where to go, what to do, what should happen. `just demo` opens the showroom, where every shrine stands in a row and the compass points at a waymark 750 blocks west of the camp; `/locate structure wayfarer:shrine/<eye>` and `/tp` find the shrines in the natural world next to it. `just client` opens a fresh world in survival, for everything that has to be met the way a player meets it.

| Area | Check |
|---|---|
| First hour | The Last Fire quest hands every player a tent, the Wayfarer's Mallet and a bedroll; one mallet hit raises the tent; sleeping in it completes the tent quest; the compass stays hidden until The Ember Wakes |
| Tent | No title flashes on entering; no monster follows through the door or spawns inside |
| Shrine | The entrance blends into the land and is easy to walk past, yet findable once the eye sits on the ribbon; the way down (spiral, switchback or ladder) is walkable without ducking and climbs back out; the hall is lit, quiet and beautiful; the people's name fades in underground with the eye beneath it; the eye floats over the pedestal until your team takes it, then is gone for your team only; the lectern opens the memory for that shard, then the people's own book |
| Shards | A taken shard lands in the Shards slots of the Curios panel for every teammate, cannot be taken out and stays after death; with all twelve, one click on a frame sets them all |
| Compass | The ribbon turns with the player; it tells that a shard calls, never which one; far away the heat words follow the walk within about 20 blocks and the distance rounds to 1,000; within 500 blocks the eye sits on the ribbon at the entrance; teammates and your grave show on it; it goes quiet in a plane |
| Gaze | The bar fills while flying and drains four times slower on the ground; at night it fills at once; phantoms come when it is full; a veil lengthens it |
| Nights | Dawn warns of a horde night (every fourth), a blood moon (every fifth) and both (every twentieth) with a title, a chat line and the icon beside the compass; on horde nights nobody sleeps, no tent opens and anyone inside is set outside at dusk; a shrine hall still shelters; blood moons are red and still sleepable |
| Fire | On an ordinary night monsters stop at the edge of a lit campfire's circle, one that wanders in turns away, and skeletons do not shoot into it; on a blood moon they walk in; on a horde night the horde comes through; Illagers walk up to it; standing still at the fire for half a minute gives Warm until tomorrow's dusk, and hunger drains half as fast while it lasts |
| The voice | After the second shard, resting at a fire outside a tent speaks one step a day in chat, in its current name (the stranger, Wend, the gaze); never in a tent; each talk lands in At the Fire |
| Leads | A lead line gives a direction and distance, the compass bar marks it at any distance and names it within 500 blocks, his camps stand whole on the ground where the mark points, and arriving prints what is found, adds the page and hands over the cache; each shrine book ends with its people's place and the gift is there |
| Maren | She stands at the edge of the firelight, cannot be hurt, speaks and leaves in smoke with food by the fire; at the gate her band blocks the frame, she reads the letter and fights them without touching you; after the dragon she sits at your first fire |
| Stillness | On the second day within 256 blocks of one place the ember dims; on the third the compass reads the ember is dim and a horde comes that night; walking away brightens it |
| Glimpse | From seven shards, standing still at night outlines every monster within 32 blocks through stone, visible under the shaders |
| After the dragon | Hunger is normal, no horde nights, no blood moons, no dimming, monsters stop growing stronger |
| Death | A gravestone stands where you fell; breaking it puts every item back in its slot, backpack and veil included |
| Journal | Chapters appear only once reached; every book you open is kept in Pages to read again; The Road So Far and Words of the Road fill in as things happen and never spoil what comes; an open lead shows in At the Fire until found; every quest pays something |
| Unlocks | Ships refuse to carry the player before 4 eyes, aircraft before 6, with the message saying why |
| Last Camp | Sits whole at spawn on the first world, not buried or floating |
| Looks and sound | Shaders, Distant Horizons at 1024 chunks, ambience and music; biomes feel wide; caves open to the surface often enough; Jade stays in the corner and ignores plants; note anything that clashes |
| Balance | How long each leg to the next eye felt, whether nights felt hard but fair, whether gear was worth making, and whether hunger drove foraging without nagging |

## Bring the results back

Every number the user wants changed goes to `balance.toml`, or to the mod's config in `pack/config` when it is another mod's number. Worldgen numbers (shrine and stronghold distances, structure spread) only change new chunks: test them in a fresh world.

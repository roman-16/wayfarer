---
name: playtest
description: The in-game checklist for what Wayfarer's tests cannot see - shaders, sound, the HUD, shrines, nights, flight and balance - handed to the user after a change, with the results written back into balance.toml or the right config. Use after changing anything the player sees, hears or feels, or when the user asks what to playtest.
---

# Playtest

`just test` proves the pack boots, every shrine generates and the stronghold ring sits at its radius. It cannot judge how anything looks, sounds or feels. That is the user's, in game.

## Hand the user a checklist

Pick the items the change touched, and give each as one line: where to go, what to do, what should happen. `just client` opens the showroom, where every shrine stands in a row; `/locate structure wayfarer:shrine/<eye>` and `/tp` find them in the natural world next to it.

| Area | Check |
|---|---|
| Shrine | The surface sign is easy to miss but findable; the tunnel is walkable; the hall is lit, quiet and beautiful; the title fades in underground, not on the surface above it; the pedestal gives the eye once per team |
| Compass | Far away the heat words follow the walk within about 20 blocks; the distance rounds to 1,000; within 500 blocks the arrow turns as the player turns; it goes silent in a plane |
| Gaze | The bar fills while flying and drains four times slower on the ground; at night it fills at once; phantoms come when it is full; a veil lengthens it |
| Nights | New moon is dark but playable; horde nights come every fifth night and stop at a shrine; a campfire keeps a small circle safe |
| Unlocks | Ships refuse to carry the player before 4 eyes, aircraft before 6, with the message saying why |
| Last Camp | Sits whole at spawn on the first world, not buried or floating |
| Looks and sound | Shaders, Distant Horizons, ambience and music; note anything that clashes |
| Balance | How long each trip to the next eye felt, and whether nights felt hard but fair |

## Bring the results back

Every number the user wants changed goes to `balance.toml`, or to the mod's config in `pack/config` when it is another mod's number. Worldgen numbers (shrine and stronghold distances, structure spread) only change new chunks: test them in a fresh world.

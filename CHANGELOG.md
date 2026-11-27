# Changelog

All notable changes to Wayfarer are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and Wayfarer adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html) as the pack reads it: a patch fixes and tunes, a minor version adds content, and both load into a road under way; a major version changes the land's shape or the story's structure, and a road begun on an older major does not open in it.

## [Unreleased]

## [1.1.0] - 2026-11-27

### Highlights

- **One address for every version** - import the pack once from `https://roman-16.github.io/wayfarer/Wayfarer.zip`; an instance imported from `/1/` no longer updates.

### Changed

- Prism imports the pack from `https://roman-16.github.io/wayfarer/Wayfarer.zip`, and the instance follows every release, majors included, so a new major ends the roads begun before it. An instance imported from `/1/` no longer updates: import the new address once.

## [1.0.2] - 2026-11-20

### Highlights

- A new world waits until everyone has pressed Wake, so the team hears the stranger together.
- The burning town's night plays as it should: the cellar door is the last sign, and the waves come in from the town and beat on it.
- Talks keep going while one player has a menu open, and "Say that again." moved to 0, so a hotbar key can't trigger it.

### Added

- A new world waits, everyone asleep by the stranger's fire, until every player online has pressed Wake.
- Auto-Sort in Options, Controls, beside Auto-Jump: switched off, your own inventory and the chests you close stay as you left them.

### Changed

- "Say that again." stands apart under the answers on its own key, 0, and a number key pressed in the first half second after answers appear picks nothing.
- A talk waits for a menu only while everyone listening has one open, so one player's inventory no longer holds up the scene for the rest.
- Hunger runs at vanilla speed until the ember wakes at the first dusk.
- Every spoken line fits two rows of the subtitle band, and everyone you can right-click during a step has at least two things to say.
- When a hold's creatures cannot reach you, the line says to go out to them, and it comes only once they have had time to find a way.

### Removed

- Visual Workbench: each player crafts in a grid of their own again.

### Fixed

- The burning town's cellar door is always the fourth sign, opens and closes with its sound, and looks the same to everyone.
- Hob says his line once when the team takes his blade.
- Tollbridge's barrels no longer hold Simply Swords' weapons.
- Warmth earned at a fire while a talk runs is given when the talk ends.
- The survivors no longer turn on the spot on their way to the bell.
- The cellar hold's waves walk in from the town and beat on the door, instead of waiting in caves under it.

## [1.0.1] - 2026-11-19

### Fixed

- Fixed the Prism instance stopping at its pre-launch command before it fetched the pack, so the game never started. An instance already imported keeps the broken command: import it again from `https://roman-16.github.io/wayfarer/1/Wayfarer.zip`.

## [1.0.0] - 2026-11-19

### Added

- The whole road, about twelve hours from the stranger's fire to the frame: the prologue, three acts, six halls and the doors, each with the world it leaves behind, and Years later.
- Every side quest, the deeds, and the journal's Afterwards pages.
- The world's shape: the home land, the deep sea round it, the outer continent out to the Last Sea, and the ring of frames.
- Updates that arrive by themselves: the Prism instance fetches each new 1.x version before it starts, and a road begun on 1.x loads in every later 1.x.

[unreleased]: https://github.com/roman-16/wayfarer/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/roman-16/wayfarer/compare/v1.0.2...v1.1.0
[1.0.2]: https://github.com/roman-16/wayfarer/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/roman-16/wayfarer/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/roman-16/wayfarer/releases/tag/v1.0.0

# Changelog

All notable changes to Better Pets are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Better Pets is versioned semantically (`MAJOR.MINOR.PATCH`).
It runs on Minecraft 26.3, 26.2 and 26.1 (Fabric + Forge + NeoForge, Java 25) and requires
[Mycel](https://github.com/MyCelium-GG/MyCel-Lib) (`my.celium.org`).

## [1.3.0] - 2026-11-10

The winter update.

### Added
- Pet Scarf: craft it from 3 wool and a string, dye it any colour, then right-click your own dog or cat to put it on. It sits around the neck with an end hanging down, and moves with the pet. Take it off from the pet menu; it drops if the pet dies.
- Dogs sleep on your bed: when you go to bed, your dogs come and lie down on the end of it, the rest on the floor next to it. Dogs you told to sit stay where they are.
- Snowy coats: dogs and cats out in falling snow slowly turn white, shake it off with a puff of snow when they get under a roof, and wash it off in water.
- Settings: `scarves`, `sleepOnBed`, `snowyCoats` (all on).

### Changed
- New pet menu, built from scratch as a winter night landscape: northern lights, a full moon, snowy hills and pine woods, a cabin with lit windows, a Christmas tree and falling snow. Your pets are tabs under a string of lights, the pet stands in the snow, and its hearts, whereabouts, scarf and food sit on a panel beside it. Click the name to rename it; scroll or use the arrow keys to switch pets.
- The pet in the menu only watches your cursor while it is near the pet, so it no longer swings around while you use the buttons.
- Better Pets is now needed on the client too (the Pet Scarf is a new item). Install it on the server and on every client.

## [1.2.2] - 2026-11-04

### Changed
- Built for Mycel 1.6.1: chat messages get the new MyCelium look, and Mycel can share anonymous stats if you agree. What is sent: https://wiki.mycelium-gg.org/privacy#mycel-stats
- The "Found" line above the hotbar has no check mark anymore.
- `/pets` matches the new look: a header with your pet count, one row per pet, and plain underlined Menu, Find, Call and Name buttons instead of `[menu][find]...`.
- Death alerts say what happened ("Rex was slain by Zombie") instead of hiding it in a hover, and use no skull symbol.

### Added
- Anonymous stats (how many pets, which species, which features get used) so we know what to improve. They follow your answer on Mycel's stats screen and the `shareStats` setting on servers; nothing else is asked. What is sent: https://wiki.mycelium-gg.org/privacy#better-pets-stats

## [1.2.1] - 2026-11-02

### Changed
- Settings screen: on/off settings now show their real state when it opens (fixed in Mycel).
- Built against the newest Fabric API, Forge and NeoForge, including the first stable NeoForge for 26.3.

## [1.2.0] - 2026-10-31

### Added

- Pet menu: shift-right-click your pet, or `/pets menu [pet]`, or `[menu]` in
  `/pets`. A custom screen with all your pets, a live view of the selected one,
  rename box, health bar, stats (horse speed and jump too) and click-to-feed
  with the food you carry, plus sit/stand, gear, find, call, give and forget.
- Without the mod on the client, the same menu opens as a chest screen, so
  vanilla clients keep working.
- Config: `petMenu`.
- `/pets` commands accept a pet's UUID.

### Changed

- Sneak-right-clicking your own horse, llama, camel or nautilus opens the pet
  menu instead of its inventory; the menu's Gear button opens the inventory.
  Turn this off with `petMenu`.

## [1.1.0] - 2026-10-28

### Added

- Minecraft 26.2 and 26.1 (26.1, 26.1.1, 26.1.2) support. Each release now
  has jars for 26.3, 26.2 and 26.1 on Fabric, Forge and NeoForge; pick the
  ones named for your Minecraft.

### Changed

- Requires Mycel 1.5.1 (for the same Minecraft version).

## [1.0.4] - 2026-10-26

### Changed

- Requires Mycel 1.4.1.

### Fixed

- NeoForge no longer stops at a warning screen during start-up because of
  this mod's icon setting.

## [1.0.3] - 2026-10-25

### Changed

- Requires Mycel 1.4.0. Operators now get one chat line per session when a
  MyCelium mod has an update, and `/mycel mods` marks it.

### Fixed

- A pet list that could not be read was overwritten on the next save. It is
  now kept as `betterpets.json.broken` in the world's `data` folder.
- Saving failed on file systems without atomic moves (some network drives).

## [1.0.2] - 2026-10-23

### Changed

- Requires Mycel 1.3.1. Messages use Mycel's shared message kit: they start
  with a bold `Pets ›` tag like every MyCelium mod, and every color comes from
  the MyCelium wiki (no more orange brand or legacy colors).

## [1.0.1] - 2026-10-19

### Changed

- Updated Mycel lib to 1.3.0: Mycel's chat messages now use the MyCelium wiki's
  red style.
- Checks for new versions through the MyCelium wiki (one log line when an
  update is out; turn off with `enableUpdateChecker` in `config/mycel.json`).

## [1.0.0] - 2026-10-18

### Added

- Initial release.
- `/pets`: every pet you own (wolves, cats, parrots, horses, llamas,
  camels, nautiluses, modded tamables) with health, sitting/lead state,
  distance and direction, and clickable find/call/name buttons. Pets in
  unloaded chunks keep their last known position.
- `/pets find`: the pet glows and an action-bar arrow with distance and
  height points to it.
- `/pets name` supports colored names: `&` codes, hex and MiniMessage
  tags (gradients, rainbow).
- `/pets call` (with cooldown), `/pets name` (no name tag needed),
  `/pets give` (to another player), `/pets forget`.
- Owners cannot hurt their own pets unless sneaking.
- A lead held by the pet's owner no longer snaps; the pet is pulled along.
- Death alert with clickable coordinates; dead pets stay listed until
  forgotten.
- Pet list saved per world (`<world>/data/betterpets.json`).
- Config (`config/betterpets.json`): `enabled`, `protectFromOwner`,
  `strongLeads`, `deathAlerts`, `allowCall`, `callCooldownSeconds`,
  `allowRename`, `allowGive`, `findSeconds`. Reload live with
  `/mycel reload betterpets`; config screen available from the mod list on
  Forge and NeoForge.
- Server side only: works with vanilla clients. Built on Mycel 1.2.1
  (`com.github.MyCelium-GG:MyCel-Lib:1.2.1` via JitPack).

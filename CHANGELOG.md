# Changelog

All notable changes to Better Pets are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Better Pets is versioned semantically (`MAJOR.MINOR.PATCH`).
It runs on Minecraft 26.3, 26.2 and 26.1 (Fabric + Forge + NeoForge, Java 25) and requires
[Mycel](https://github.com/MyCelium-GG/MyCel-Lib) (`my.celium.org`).

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

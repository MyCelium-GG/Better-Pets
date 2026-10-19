# Changelog

All notable changes to Better Pets are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Better Pets is versioned semantically (`MAJOR.MINOR.PATCH`).
It runs on Minecraft 26.3 (Fabric + Forge + NeoForge, Java 25) and requires
[Mycel](https://github.com/MyCelium-GG/MyCel-Lib) (`my.celium.org`).

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

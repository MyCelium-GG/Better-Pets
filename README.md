# Better Pets

[![License](https://img.shields.io/github/license/MyCelium-GG/MyCel-Lib)](LICENSE)

Owning a dog, cat, parrot or horse, done right. `/pets` lists every pet you
own and where it is - even in unloaded chunks. Find one with a glow and an
on-screen arrow, call it to you, rename it without a name tag, give it to a
friend. Your own sword no longer hurts your pets, a lead on your pet never
snaps, and when a pet dies you get the exact spot. Server side: players
need nothing installed.

Built on [Mycel](https://github.com/MyCelium-GG/MyCel-Lib) (`my.celium.org`), the shared
core for the MyCelium ecosystem: configuration, platform abstraction and
diagnostics are reused.

| | |
|---|---|
| Mod id | `betterpets` |
| Minecraft | **26.3** · Java **25** |
| Loaders | Fabric, Forge, NeoForge |
| Side | Server (works in singleplayer and with vanilla clients) |

## Behavior

Every tamed animal with an owner counts as a pet: wolves, cats, parrots,
horses, donkeys, mules, llamas, camels, nautiluses and modded tamables.

- **`/pets`** lists them all, one line each:

  ```
  Pets › Your pets (5)
  1. Rex ♥40/40 sitting 7 m E [find][call][name]
  2. Luna ♥10/10 sitting 5.7 km NW (2 h ago) [find][call][name]
  3. ☠ Parrot died 5 min ago 12 m N [forget]
  ```

  Hover a name for the species and health, hover the distance for the
  coordinates, click it to copy them. The buttons run the commands below.
  Pets in unloaded chunks keep their last known spot ("2 h ago").
- **`/pets find <pet>`** makes the pet glow for 30 s and shows
  `Rex ↑→ 23 m ▲4` above the hotbar: the arrow is relative to where you
  look, the triangle shows the height difference. It stops when you are
  next to it. Far-away pets point to where they were last seen.
- **`/pets call <pet>`** teleports a loaded pet next to you (30 s cooldown).
- **`/pets name <pet> <name>`** renames it - no name tag, no anvil, works
  for unnamed pets by their list number (`/pets name 2 Luna`). Colors work
  with legacy codes (`&b&lBiscuit`, `&#ff55aaRex`, `{#ff55aa}`) and
  MiniMessage tags (`<red>`, `<#ff55aa>`, `<b>`, `<gradient:gold:red>`,
  `<rainbow>`), mixed freely; up to 50 letters, colors not counted.
- **`/pets give <pet> <player>`** hands the pet to another online player.
- **`/pets forget <pet>`** removes a dead or lost pet from the list.
- **Safe from you**: your hits, sweeps, arrows and tridents do not hurt your
  own pets. Sneak while attacking if you really mean it.
- **Strong leads**: a pet on your lead is pulled along when you sprint,
  fly or fall instead of the lead snapping at 12 blocks.
- **Death alert**: `☠ Rex died at -129 66 -36 (overworld)`, click to copy.

`<pet>` is a name (`Rex`, `"Sir Barks"`, a unique start like `bis`) or the
number from `/pets`. Tab completion suggests both.

The pet list is saved per world in `<world>/data/betterpets.json`.

## Configuration (`config/betterpets.json`)

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Master switch (also toggleable live via `Mycel.setEnabled`). |
| `protectFromOwner` | `true` | Owners cannot hurt their own pets unless sneaking. |
| `strongLeads` | `true` | Pets on their owner's lead are pulled along instead of snapping off. |
| `deathAlerts` | `true` | Tell the owner where a pet died. |
| `allowCall` | `true` | Allow `/pets call`. |
| `callCooldownSeconds` | `30` | Seconds between two calls per player (0-3600). |
| `allowRename` | `true` | Allow `/pets name`. |
| `allowGive` | `true` | Allow `/pets give`. |
| `findSeconds` | `30` | How long `/pets find` glows and points (5-300). |

Reload live with `/mycel reload betterpets`. A config screen is available
from the mod list on Forge and NeoForge.

## Building

JDK 25, then:

```sh
./gradlew build            # all loaders + tests
./gradlew :common:test     # unit tests only
```

Mycel itself comes from [JitPack](https://jitpack.io/#MyCelium-GG/MyCel-Lib)
as one universal jar for all loaders
(`com.github.MyCelium-GG:MyCel-Lib:1.2.1`). No local setup needed.

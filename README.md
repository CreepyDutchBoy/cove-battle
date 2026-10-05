# Cove Battle

A legacy-console-style **Battle** minigame for the Cove map, as a NeoForge mod.

- **Minecraft** 1.21.1
- **NeoForge** 21.1.253+
- Loads on **client and server** — the server runs the match, the client draws the HUD
- **Self-updating** from this repository's GitHub releases

**Playable as of 1.1.0.** A full match runs: rounds, grace period, loot tiers, restocking,
closing border and a winner. Presentation is vanilla boss bar / action bar / title / sidebar
driven from the server, so a vanilla client needs nothing extra. Custom client rendering,
registered custom items and shaders are later phases.

## Install (once)

Drop `covebattle.jar` into your instance's `mods/` folder. For the PrismLauncher instance
*Minigames 1.21.1*:

```
~/.local/share/PrismLauncher/instances/Minigames 1.21.1/minecraft/mods/covebattle.jar
```

After that you never install it by hand again: every later version arrives through the
built-in updater.

## Modes and mutators

Three rule sets, picked in **Modes & Mutators** (main menu hub, or `/cbmutators` in game, or
`/covebattle mode`):

| Mode | What it is |
|---|---|
| **Classic** | The console mini game: vanilla loot only, no custom gear, no screen effects, no closing border — Showdown is glowing survivors, as on console. 180s rounds. |
| **Remastered** *(default)* | Classic plus the custom items, the screen shaders and the closing border. 90s rounds. |
| **Mutators** | Remastered with every number exposed on a slider. |

Twenty tunables, each a slider: grace period, fight length, border step, border floor, restock
interval and count, rounds to win, loot amount, max health, damage dealt, move speed, ability
cooldown, and toggles for custom items, fall damage, hunger, natural regen, friendly fire,
showdown glow, closing border and screen effects.

The board is server-authoritative. The client edits a local copy, **Apply** sends one payload, the
server checks you are an operator and broadcasts the result, so everyone's board agrees. Settings
persist to `config/covebattle-rules.json`. Picking Classic or Remastered loads that preset into the
sliders, so a preset is a starting point rather than a dead end.

Command-line equivalents: `/covebattle mode <classic|remastered|mutators>`,
`/covebattle mutator list`, `/covebattle mutator set <key> <value>`,
`/covebattle mutator preset <classic|remastered>`.

## Testing with bots

`/covebattle bot add <n>` spawns stand-in players built on NeoForge's `FakePlayer`, so team
assignment, eliminations and round and match transitions can be exercised with nobody connected.
`/covebattle bot kill <name>` eliminates one, `bot list` and `bot clear` do the obvious. They are
real `ServerPlayer` instances, so they take the same path a human does.

## How a match plays

| Phase | What happens |
|---|---|
| **Lobby** | Border open, clickable start prompt in chat. `/covebattle start` or the button begins a match. |
| **Grace** (15s) | Everyone is placed on podiums around the centre, frozen and invulnerable, with a countdown. |
| **Fight** (90s) | Open combat. The remaining time is spelled out on the action bar only every 30 seconds; the rest of the time it shows your health, loot count and your opponent's health. |
| **Showdown** | The main timer expires: the border appears at radius 80 and steps down to 15, and the remaining players glow so nobody can stall by hiding. |
| **Round end** (10s) | Round win awarded to the last player standing. Sidebar tracks the score. |
| **Match end** | First to 2 round wins takes it. Leaderboard of rounds, kills and containers looted, then back to the lobby. |

A round with nobody left is a tie. If the border reaches its floor and both players are still
alive after 30 seconds, that is also a tie and scores nothing.

### Modes

`/covebattle start solo` — free-for-all, last player standing takes the round.
`/covebattle start teams` — Red against Blue, last team with anyone standing takes the round.
Bare `/covebattle start` uses `[rules] defaultMode`. Up to **32 players**, **16 per team**;
friendly fire is off and teammates show through invisibility.

### Arena markers

A creative tab, **Cove Battle: Arena Markers**, holds six blocks. Place them instead of editing
coordinates — the arena scan finds them because each one carries a block entity, so they turn up in
the same chunk walk that finds the chests.

| Marker | Does |
|---|---|
| Arena Centre | The middle: loot tier origin, border centre, spawn-ring fallback |
| Lobby Spawn | Where players are sent between matches |
| Red / Blue Team Spawn | Team spawns; place up to sixteen of each |
| Free-for-all Spawn | Solo spawns; place as many as you expect players |
| Arena Bound | Place at the edge; the furthest one sets the play radius |

Anything you do not place falls back to the config, so a bare world still works. **Chests and
barrels are never markers** — they are detected automatically, so there is nothing to place or
maintain for loot. Right-click any marker to be told what it is.

### Custom items

A second tab, **Cove Battle: Items**. They are real registered items, and they appear in loot:
the ability items are a centre-tier prize, so the middle stays worth contesting.

| Item | Does |
|---|---|
| Coveslayer | Netherite-tier sword, faster swing, epic rarity |
| Storm Egg | Calls visual-only lightning on what you are looking at, up to 40 blocks, 3.5♥ in a small splash — it cannot set your map on fire |
| Smoke Bomb | Invisibility and speed for you, blindness and nausea for hostiles within 10 blocks |
| War Horn | Strength II to your side within 24 blocks, glowing on hostiles within 64; reusable on a 10s cooldown |
| Siphon Flask | Heals you 3♥ plus absorption, drains 2♥ and weakens the nearest hostile within 12 |
| Ghost Boots | Outer-tier trinket |

Team awareness is real: abilities never hit your own side.

### Loot

Two tiers, defined in code (`BattleLoot`) rather than as data-pack tables:

- **Centre** (within 12 blocks of the arena centre) — the best gear: diamond and netherite
  weapons with enchantments, diamond armour, turtle helmets, golden apples, ender pearls, a rare
  totem. Risk and reward, as in console Battle and Survival Games.
- **Outer** — iron and stone tools and weapons, iron and chainmail armour, bread, apples, arrows.
  Plentiful but modest.

Chests, trapped chests and barrels are all discovered the same way: by asking each chunk in the
arena for its block entities. They all answer to the same `Container` interface, so barrels cannot
silently go unfilled the way they did in the datapack. Looted containers restock every 30
seconds, four at a time, skipping any a player is standing near — console behaviour.

### Commands

| Command | Permission | Does |
|---|---|---|
| `/covebattle` / `status` | anyone | Phase, round, container counts, update state |
| `/covebattle help` | anyone | Rules and timings |
| `/covebattle version` | anyone | Mod and Minecraft version |
| `/covebattle start` / `stop` / `reset` | op (2) | Match control; `reset` restores the border and clears loot |
| `/covebattle arena info` / `rescan` | op (2) | Arena geometry; re-find containers |
| `/covebattle debug skip` | op (2) | End the current phase next tick |
| `/covebattle debug fill` | op (2) | Stock every container without starting a match |
| `/covebattle debug border <radius>` | op (2) | Apply the arena border directly |
| `/covebattle update check` / `now` | op (2) | Check, or check and install |

### Config — the game half

`config/covebattle-common.toml` also carries `[arena]` (centre x/y/z, play radius, border floor,
centre tier radius, podium radius), `[timings]` (grace, match, border step, round end, restock)
and `[rules]` (rounds to win, restock count, minimum players, force adventure, showdown glow).

**Set `[arena] centreX/Y/Z` to your map's chest platform.** It defaults to `45 64 251`, which is
where the old datapack's arena was.

Adventure mode is kept on players while `forceAdventure` is true, but **creative and spectator are
never touched**, so building and moderating still work.

## The Cove map, in the jar

The main menu gains a **Cove Battle** button. It opens a hub with:

- **Play the Cove map** — unpacks the bundled map into `saves/` and loads it
- **Join a Cove Battle server** — the normal multiplayer screen
- **Install the map only** — unpack without playing

Hosting is not a separate button on purpose: in Minecraft you host by playing a world and
using **Esc → Open to LAN**, so the hub says that rather than pretending to stand up a server.

The map ships inside the jar (20 MB), so the mod download carries it. See
[MAP_CREDITS.md](MAP_CREDITS.md) for provenance and the redistribution caveat.

## Screen effects

Two post-processing shaders, both inferred from what the client already knows, so no custom
packets are involved:

- **Eliminated** — while spectating, colour drains out and the edges close in
- **Showdown** — while the world border is shrinking, a breathing red rim and raised contrast

Either can be turned off with `clientScreenEffects`. If a shader fails to load it is disabled
for the session and logged; it can never take the game down with it.

## How updating works

On startup the mod asks GitHub for this repository's releases, newest first, and takes the
first one that

1. is not a draft (and not a prerelease, unless you opt in),
2. ships a `covebattle.update.json` manifest,
3. declares the **same Minecraft version** you are running, and
4. has a higher version than what is installed.

It then downloads `covebattle.jar`, checks it against the SHA-256 in the manifest, and
replaces the jar in `mods/`.

**The update applies the next time you start the game.** No JVM can swap a jar it has
already loaded, so nothing pretends to hot-reload. You get a chat line and a log line
saying a restart is needed.

Deliberate choices worth knowing:

- The jar filename is **fixed** (`covebattle.jar`). Versioned filenames risk two copies in
  `mods/` and NeoForge refusing to boot on a duplicate mod id. Any stale `covebattle*.jar`
  is removed after a successful update.
- Replacing a running jar is safe on Linux and macOS. On Windows the move fails while the
  game is running; that is reported as a failed check, never a crash.
- A failed or offline check is logged and ignored. It never blocks startup or gameplay.

### Config — `config/covebattle-common.toml`

| Key | Default | Meaning |
|---|---|---|
| `checkOnStartup` | `true` | Ask GitHub for a newer release at launch |
| `autoUpdate` | `true` | Install it, rather than only reporting it |
| `allowPrerelease` | `false` | Also accept releases flagged prerelease |
| `notifyInChat` | `true` | Tell players when an update was installed |
| `repoOwner` / `repoName` | `CreepyDutchBoy` / `cove-battle` | Where releases come from |

### Commands

| Command | Permission | Does |
|---|---|---|
| `/covebattle version` | anyone | Mod and Minecraft version |
| `/covebattle status` | anyone | Channel, auto-install setting, last check result |
| `/covebattle update check` | op (level 2) | Check now, report only |
| `/covebattle update now` | op (level 2) | Check now and install |

## Development

The local default JVM may be newer than 21, so point Gradle at JDK 21:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

./gradlew build packageDist   # jar + sha256 + manifest in build/dist/
./gradlew runClient           # dev client
./gradlew runServer           # dev server
```

### Testing the updater without Minecraft

`io.github.creepydutchboy.covebattle.update` has **no Minecraft or NeoForge imports**, so the
whole flow runs standalone:

```bash
javac -d /tmp/out -cp gson.jar $(find src/main/java -path '*/update/*.java')
java -cp /tmp/out:gson.jar io.github.creepydutchboy.covebattle.update.UpdaterCli \
  --current 1.0.0 --mc 1.21.1 --mods /tmp/testmods --apply
```

### Cutting a release

```bash
git tag v1.0.1 && git push origin v1.0.1
```

`.github/workflows/release.yml` builds the jar, stages `covebattle.jar`,
`covebattle.jar.sha256` and `covebattle.update.json`, and publishes the release. Installed
copies pick it up on their next launch.

Use a **prerelease** to stage a build that should not reach normal installs.

## Testing status

Verified headlessly on a dev server (`runServer` + RCON):

- Mod loads on client and server distributions; command tree registers
- Container discovery: 5 placed, 4 registered, the one 89 blocks out correctly excluded;
  3 centre / 1 outer tiering correct; chests, trapped chests and barrels all found
- Loot generation per tier, including enchantments landing on the item
- Border applied and restored (radius 30 → 60 wide, 80 → 160 wide, reset → vanilla)
- `start` refuses with too few players; `reset` clears loot and restores the border
- Updater: standalone install, up-to-date, wrong-Minecraft-version and unknown-repo paths

**Not verifiable without players**, so these need a real 2-player run: the grace freeze and
countdown, combat and elimination, round and match transitions, the showdown glow, restock
timing, and every title card and sidebar actually appearing on screen.

## Licence

MIT

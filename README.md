# Cove Battle

A legacy-console-style **Battle** minigame for the Cove map, as a NeoForge mod.

- **Minecraft** 1.21.1
- **NeoForge** 21.1.253+
- Loads on **client and server** — the server runs the match, the client draws the HUD
- **Self-updating** from this repository's GitHub releases

> **Phase 1 (this release)** is the foundation only: build, both distributions, config,
> commands and the self-updater. No minigame logic yet — that is phase 2.

## Install (once)

Drop `covebattle.jar` into your instance's `mods/` folder. For the PrismLauncher instance
*Minigames 1.21.1*:

```
~/.local/share/PrismLauncher/instances/Minigames 1.21.1/minecraft/mods/covebattle.jar
```

After that you never install it by hand again: every later version arrives through the
built-in updater.

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

## Licence

MIT

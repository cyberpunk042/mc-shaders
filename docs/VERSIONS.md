# Version matrix and provenance

Every external version this project pins, where the number came from, and how
confident we are in it. Values live in [`gradle.properties`](../gradle.properties);
this file explains them.

## Why this file exists

The environment that bootstrapped this repository could not reach any Minecraft
Maven host (`maven.fabricmc.net`, `maven.neoforged.net`, `maven.architectury.dev`
and `api.modrinth.com` all refuse the connection), and only had JDK 21 available
where 26.x needs 25. Nothing in the Minecraft layer was resolved or compiled
there. Rather than present guessed numbers as verified, each one is recorded
below with its source and status.

**The first successful CI run is what promotes these from `reported` to
`verified`.** If a build fails on dependency resolution, the fix is almost
certainly a number in `gradle.properties`, not the build logic.

## Minecraft

| Version | Status | Released | Notes |
|---|---|---|---|
| `26.2` | Current stable | 2026-06-16 | "Chaos Cubed". Sulfur caves; **experimental Vulkan renderer**; friends list. Our primary target. |
| `26.3` | Upcoming | Q3 2026 | In snapshots (snapshot 7 as of 2026-08). Declared in `gradle.properties` but **not** in `active_versions`. |
| `26.1` | Superseded | 2026-03 | The version that broke modding wide open — see below. |

### What 26.1 changed, and why it matters here

26.1 is the reason this project starts on 26.x rather than 1.21.x:

- **The game ships deobfuscated.** Real class, field, method and variable names.
  The mappings dance (Yarn vs Mojang vs intermediary) largely goes away.
- **Java 25** became the floor for the Gradle JVM.
- Tooling moved to **Loom 1.15** and **Gradle 9.4.0**.

Fabric described the 26.1 tooling and API changes as the largest they had ever
made for a single release.

### The renderer transition

26.1 was expected to be the last release supporting **only** OpenGL. 26.2 ships
an experimental **Vulkan** backend, and OpenGL is slated for removal once Vulkan
is stable.

This is the single most consequential fact for a shader mod, and it is why the
architecture puts an `EffectBackend` interface between the framework and the
graphics API. See [ARCHITECTURE.md](ARCHITECTURE.md).

## Pinned versions

| Key | Value | Status | Source |
|---|---|---|---|
| `stonecutter_version` | `0.9.7` | Reported | Gradle Plugin Portal listing, published 2026-07-19 |
| `fabric_loom_version` | `1.15-SNAPSHOT` | Reported | Fabric's 26.1 announcement ("use Loom 1.15 and Gradle 9.4.0") |
| `moddevgradle_version` | `2.0.141` | Reported | Gradle Plugin Portal listing for `net.neoforged.moddev` |
| `gradle_version` | `9.4.0` | Reported | Fabric's 26.1 announcement |
| `java_version` | `25` | Reported | Fabric's 26.1 announcement (minimum for the Gradle JVM) |
| `mc_26_2_fabric_loader` | `0.18.4` | Reported | Fabric's 26.1 announcement, latest stable loader |
| `mc_26_2_fabric_api` | `0.157.0+26.2` | Reported | Modrinth version listing, published 2026-08-10 |
| `mc_26_2_neoforge` | `26.2.0.35-beta` | **Low confidence** | Derived from the documented `26.2.0.x` prefix scheme and a build number that was already stale when read. Expect to bump this. |
| `mc_26_3_*` | `PIN_ON_RELEASE` | Placeholder | Intentionally invalid so a premature enable fails loudly rather than silently building the wrong thing. |

## Adding 26.3 when it releases

1. Pin `mc_26_3_fabric_api` and `mc_26_3_neoforge` to real versions.
2. Add `26.3` to `active_versions`.

That is the whole change. Stonecutter derives the extra targets, and the loader
build scripts look their coordinates up by key. No build script edits.

## Why not 1.21.x as well

1.21 still holds the larger share of players and mods, and NeoForge's 1.21 line
has accumulated the most mods of any version. Supporting it was considered and
deliberately deferred: 1.21 predates the 26.1 deobfuscation, so it needs the
whole mappings apparatus and a second set of rendering assumptions. That is a
real project, not a version bump, and it should not slow down getting the
framework right on current versions first.

If it is wanted later, the Stonecutter matrix is the mechanism, and the
`EffectBackend` seam already isolates most of what would differ.

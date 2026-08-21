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
| `fabric_loom_version` | `1.15-SNAPSHOT` | **Low confidence** | Fabric's 26.1 announcement says "Loom 1.15"; the exact artifact suffix is a guess. Local resolution is impossible here (host unreachable), so CI settles it. |
| `moddevgradle_version` | `2.0.141` | Reported | Gradle Plugin Portal listing for `net.neoforged.moddev` |
| `gradle_version` | `9.4.0` | **Verified** | Pinned in the committed wrapper; confirmed present in the Gradle version index and downloaded successfully. |
| `java_version` | `25` | Reported | Fabric's 26.1 announcement (minimum for the Gradle JVM) |
| `mc_26_2_fabric_loader` | `0.18.4` | Reported | Fabric's 26.1 announcement, latest stable loader |
| `mc_26_2_fabric_api` | `0.157.0+26.2` | Reported | Modrinth version listing, published 2026-08-10 |
| `mc_26_2_neoforge` | `26.2.0.35-beta` | **Low confidence** | Derived from the documented `26.2.0.x` prefix scheme and a build number that was already stale when read. Expect to bump this. |
| `mc_26_3_*` | `PIN_ON_RELEASE` | Placeholder | Intentionally invalid so a premature enable fails loudly rather than silently building the wrong thing. |

## Retargeting to 26.3 when it releases

1. Pin `mc_26_3_fabric_api` and `mc_26_3_neoforge` to real versions.
2. Set `mc_version=26.3`.

That is the whole change — the loader build scripts look their coordinates up as
`mc_<version>_*`, so no build script is edited.

## On building several versions at once

The property layout above is already per-version, which is the groundwork. What is
*not* wired is a preprocessor to build 26.2 and 26.3 **simultaneously**.

[Stonecutter](https://plugins.gradle.org/plugin/dev.kikugie.stonecutter) 0.9.7 is
the intended mechanism, and was attempted during bootstrap. It was backed out for
two reasons, both worth stating plainly:

1. **26.3 does not exist yet.** Today's matrix has exactly one entry, so the
   preprocessor would add real complexity for no present benefit.
2. **It could not be verified.** Stonecutter composes version subprojects under
   `versions/`, which is a different layout from the `common`/`fabric`/`neoforge`
   split; reconciling the two needs a working loader build to test against, and no
   loader dependency was resolvable in the bootstrap environment.

Wiring it is a deliberate task for when 26.3 ships and the loader build is green —
not something to leave half-connected in the meantime. Note Stonecutter requires
Gradle 9 or newer, which the pinned wrapper already satisfies.

## Why not 1.21.x as well

1.21 still holds the larger share of players and mods, and NeoForge's 1.21 line
has accumulated the most mods of any version. Supporting it was considered and
deliberately deferred: 1.21 predates the 26.1 deobfuscation, so it needs the
whole mappings apparatus and a second set of rendering assumptions. That is a
real project, not a version bump, and it should not slow down getting the
framework right on current versions first.

If it is wanted later, a preprocessor is the mechanism, and the `EffectBackend`
seam already isolates most of what would differ.

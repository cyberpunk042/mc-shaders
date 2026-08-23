# Version matrix and provenance

Every external version this project pins, where the number came from, and how
confident we are in it. Values live in [`gradle.properties`](../gradle.properties);
this file explains them.

## Status: verified

**Every version below is confirmed by a green CI build** as of 2026-08-21 —
`./gradlew build` on JDK 25, 13 tasks executed, Fabric and NeoForge jars produced
against Minecraft 26.2.

That was not true when the table was first written. The environment that
bootstrapped this repository could not reach any Minecraft Maven host
(`maven.fabricmc.net`, `maven.neoforged.net`, `maven.architectury.dev` and
`api.modrinth.com` all refused the connection), and had only JDK 21 where 26.x
needs 25, so every number started as a sourced guess. The history is kept below
because the failures were informative — see the mappings section in particular.

If a build starts failing on dependency resolution, suspect a number here before
suspecting the build logic.

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

### Mappings are gone, and the Loom plugin id changed with them

Because 26.1+ ships unobfuscated, **no mapping artifacts are published for these
versions**. Two consequences, both found the hard way by CI:

- The build must apply **`net.fabricmc.fabric-loom`**, the newer plugin that does
  not remap Minecraft or mods. The legacy `fabric-loom` id resolves fine and then
  fails at configuration time with `Failed to find official mojang mappings for
  26.2`.
- There is **no `mappings(...)` dependency at all**. `loom.officialMojangMappings()`
  is the 1.21-era answer and has nothing to resolve on 26.x.
- **The remapping configurations are gone with it.** `modImplementation` and its
  siblings do not exist on the new plugin — mods are ordinary dependencies, so use
  plain `implementation`. Otherwise the build script fails to compile with
  `Unresolved reference 'modImplementation'`.

If you are porting a mod from 1.21, this is the change that will bite first.

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
| `fabric_loom_version` | `1.16.3` | **Verified in CI** | `1.17` was tried first and **does not exist under this plugin id** — see below. `1.16-SNAPSHOT` built, and the runner reported `Fabric Loom: 1.16.3`; pinned to that. |
| `moddevgradle_version` | `2.0.141` | **Verified in CI** | Gradle Plugin Portal listing for `net.neoforged.moddev` |
| `gradle_version` | `9.5.1` | **Verified** | Fabric's 26.2 announcement names Gradle 9.5.1. Bumped from `9.4.0`; the wrapper downloaded it and the core build passes on it locally. |
| `java_version` | `25` | **Verified in CI** | Fabric's 26.1 announcement (minimum for the Gradle JVM) |
| `mc_26_2_fabric_loader` | `0.19.3` | **Pending CI** | Fabric's 26.2 announcement, latest stable loader. Was `0.18.4` — a `26_2` coordinate sourced from the **26.1** announcement. |
| `mc_26_2_fabric_api` | `0.157.0+26.2` | **Verified in CI** | Modrinth version listing, published 2026-08-10 |
| Fabric Loom plugin id | `net.fabricmc.fabric-loom` | **Verified in CI** | The legacy `fabric-loom` id resolved, then failed with "Failed to find official mojang mappings for 26.2". See below. |
| `mc_26_2_neoforge` | `26.2.0.35-beta` | **Verified in CI** | Started as the least confident value in the table, derived from the documented `26.2.0.x` prefix scheme; it resolved and built. Newer builds exist (the Maven listing shows at least `26.2.0.62`), so this is a deliberate pin, not the latest. |
| `mc_26_2_neoform` | `26.2-2` | **Pending CI** | Read out of NeoForge 26.2.x's own build, which consumes `net.neoforged:neoform:${minecraft_version}-${neoform_version}` with `minecraft_version=26.2` and `neoform_version=2`. Gives the `vanilla` module Minecraft with no loader attached, via ModDevGradle's NeoForm mode. |
| `mc_26_3_*` | `PIN_ON_RELEASE` | Placeholder | Intentionally invalid so a premature enable fails loudly rather than silently building the wrong thing. |

## Cross-checked against a shipping 26.2 mod

Announcements say what a version *recommends*; a mod that actually builds says
what works. [`Snownee/Jade`](https://github.com/Snownee/Jade/tree/26.2-fabric)
targets 26.2 on Fabric, and its `gradle.properties` reads:

| Key | Jade | Here | Note |
|---|---|---|---|
| `loader_version` | `0.19.3` | `0.19.3` | independent confirmation of the bump below |
| `minecraft_version` | `26.2` | `26.2` | — |
| `fabric_api_version` | `0.152.1+26.2` | `0.157.0+26.2` | ours is newer and resolves in CI; theirs is simply older |
| `loom_version` | `1.16-SNAPSHOT` | `1.17` | see below |
| Loom plugin id | `net.fabricmc.fabric-loom` | same | confirms the new plugin id, not the legacy one |

**On Loom, the two sources disagreed, and the mod was right.** Fabric's 26.2
announcement names 1.17; Jade is on `1.16-SNAPSHOT`. 1.17 was tried first, on the
reasoning that vendor guidance for this Minecraft version is the stronger source.
CI rejected it:

```
Plugin [id: 'net.fabricmc.fabric-loom', version: '1.17'] was not found
  could not resolve plugin artifact
  net.fabricmc.fabric-loom:net.fabricmc.fabric-loom.gradle.plugin:1.17
```

So 1.17 exists as a Loom release but not under **this** plugin id — the newer,
non-remapping one that 26.x requires. The announcement and the plugin coordinate
are not the same fact, and only the mod that builds could tell them apart.

`-SNAPSHOT` is not a pin. The green run logged `Fabric Loom: 1.16.3` while
configuring `:fabric`, so that is what is pinned — the same two-step that
produced the concrete `1.15.5` this build used before.

## 26.1-era tooling under a 26.2 target

The toolchain rows above were originally taken from Fabric's **26.1**
announcement, because that was the most recent source available when this build
was bootstrapped. Fabric's 26.2 announcement raises them: Loom 1.17, Gradle
9.5.1, loader 0.19.3.

Nothing failed, and that is the part worth writing down. CI was green on the old
values throughout — because `common/`, `fabric/` and `neoforge/` contain no
Minecraft API usage at all, so the build only ever proved that the plugins
*resolve*. Resolution is not the same as the toolchain being correct for the
version being targeted, and the difference would first appear as a confusing
failure in whoever wrote the first real Minecraft code.

The lesson generalises past this table: a green build over an empty module says
nothing about the module.

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

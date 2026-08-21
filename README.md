# MC Shaders

A dimension-aware shader effect framework for Minecraft.

Bind visual effects — fog, colour grading, distortion, bloom — to dimensions, and
have them blend smoothly as players move between worlds. Effects are described as
backend-neutral data, so the same binding renders on OpenGL today and Vulkan when
Minecraft's renderer transition completes.

> **Status: early.** The framework core is built and tested. It does not draw
> anything yet — see [Where this actually is](#where-this-actually-is).

## Why it is built this way

Minecraft 26.2 ships an experimental Vulkan renderer alongside OpenGL, and
OpenGL is slated for removal once Vulkan stabilises. A shader mod written
directly against GLSL today gets rewritten when that lands.

So the graphics API sits behind a single interface. Everything above it is plain
data: a description of what the frame should look like, never how to draw it.
Swapping renderers becomes an addition rather than a migration.

The same decision makes the framework testable without a game running — the core
has no Minecraft dependency at all, and its test suite runs on a bare JDK.

## Targets

| | |
|---|---|
| **Minecraft** | 26.2 (current stable); 26.3 prepared, retarget when it ships |
| **Loaders** | Fabric, NeoForge |
| **Java** | 25 for the mod, 21 for the framework core |
| **Gradle** | 9.4.0, pinned in the committed wrapper |

Minecraft coordinates are looked up per version from `gradle.properties`, so
retargeting is one property change rather than a build script edit. Version
provenance and confidence levels — including which numbers are still unverified —
are in [docs/VERSIONS.md](docs/VERSIONS.md).

## Layout

```
core/       the framework — pure Java, zero Minecraft dependencies
common/     Minecraft-facing code shared across loaders
fabric/     Fabric adapter
neoforge/   NeoForge adapter
docs/       architecture, roadmap, version provenance
```

## Building

Use the wrapper; it pins Gradle 9.4.0.

The core builds anywhere, with no Minecraft toolchain and no access to Minecraft
Maven hosts:

```sh
cd core && ../gradlew test
```

The shared module needs JDK 25, but still no Minecraft Maven:

```sh
./gradlew :common:build --configure-on-demand
```

The loader modules additionally need reachable Fabric/NeoForge Maven:

```sh
./gradlew build
```

## How it works

```java
// Describe a look
EffectStack netherHeat = EffectStack.of(
    EffectLayer.of("heat_haze", EffectKind.DISTORT,
        EffectParams.builder().scalar("amplitude", 0.02).build()),
    EffectLayer.of("ember_grade", EffectKind.COLOR_GRADE,
        EffectParams.builder().color("tint", 1.0f, 0.6f, 0.4f, 1.0f).build()));

// Bind it to a dimension, conditionally if you like
BindingRegistry registry = BindingRegistry.of(
    DimensionBinding.of("nether_base", DimensionId.minecraft("the_nether"), netherHeat));

// Drive it each frame — transitions and capability filtering are handled
pipeline.frame(worldState, deltaTicks, frameContext);
```

Bindings merge **per layer**, not per stack. A pack that overrides `heat_haze`
leaves `ember_grade` untouched, so packs compose instead of fighting.

Conditions are declarative data — time of day (ranges wrap across midnight), Y
range, weather, biome tag, submersion — combined with and/or/not.

## Where this actually is

Built and verified:

- Parameter model with interpolation
- Effect layers and stacks with per-id merge semantics
- Dimension bindings and the condition algebra
- Transitions with easing and mid-blend retargeting
- Capability-aware compilation to a render plan
- The backend seam, with a no-op implementation

**52 tests, 0 failures.**

Not built yet:

- Any rendering backend that draws (M2)
- Datapack loading — bindings are programmatic for now (M3)
- The demo dimensions (M4)

Verified beyond the core: the root build, the composite substitution of `core`
into `common`, and `:common:build` itself all run clean.

**The Fabric and NeoForge modules have never been resolved against live Maven** —
the environment this was bootstrapped in could reach no Minecraft Maven host, so
`fabric_loom_version` and `mc_26_2_neoforge` in particular are unverified guesses.
CI's `loaders` job is advisory until its first green run pins them.

See [docs/ROADMAP.md](docs/ROADMAP.md).

## License

MIT — see [LICENSE](LICENSE).

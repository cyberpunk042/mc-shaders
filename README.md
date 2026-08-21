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
| **Minecraft** | 26.2 (current stable), 26.3 wired but inert until release |
| **Loaders** | Fabric, NeoForge |
| **Java** | 25 for the mod, 21 for the framework core |

Multiversion is handled by Stonecutter: adding 26.3 is two lines in
`gradle.properties`, not a branch. Version provenance and confidence levels are
in [docs/VERSIONS.md](docs/VERSIONS.md).

## Layout

```
core/       the framework — pure Java, zero Minecraft dependencies
common/     Minecraft-facing code shared across loaders
fabric/     Fabric adapter
neoforge/   NeoForge adapter
docs/       architecture, roadmap, version provenance
```

## Building

The core builds anywhere, with no Minecraft toolchain and no access to Minecraft
Maven hosts:

```sh
cd core && gradle test
```

The full mod needs JDK 25 and reachable Fabric/NeoForge Maven:

```sh
gradle build
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

The Minecraft-layer build scripts have **not** been resolved against live Maven;
the environment this was bootstrapped in could not reach any Minecraft Maven host.
CI's Minecraft job is advisory until that first green run pins the version table.

See [docs/ROADMAP.md](docs/ROADMAP.md).

## License

MIT — see [LICENSE](LICENSE).

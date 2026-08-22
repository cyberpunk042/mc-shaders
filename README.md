# MC Shaders

A dimension-aware shader effect framework for Minecraft.

Bind visual effects — fog, colour grading, distortion, bloom — to dimensions, and
have them blend smoothly as players move between worlds. Effects are described as
backend-neutral data, so the same binding renders on OpenGL today and Vulkan when
Minecraft's renderer transition completes.

> **Status: early.** The framework is built, tested, and builds into Fabric and
> NeoForge jars for Minecraft 26.2. It does not draw anything yet — see
> [Where this actually is](#where-this-actually-is).

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
| **Artifacts** | `mcshaders-core` (no Minecraft), `mcshaders-api` (for mods) |

Minecraft coordinates are looked up per version from `gradle.properties`, so
retargeting is one property change rather than a build script edit. Version
provenance and confidence levels — including which numbers are still unverified —
are in [docs/VERSIONS.md](docs/VERSIONS.md).

## Using it as a library

Other mods can contribute effects, backends and dimension looks; the pure-Java core
is also usable in any JVM project with no Minecraft involved.

```kotlin
dependencies {
    implementation("net.cyberpunk042:mcshaders-api:0.2.0")   // in a Minecraft mod
    implementation("net.cyberpunk042:mcshaders-core:0.2.0")  // anywhere else
}
```

```java
// Give a dimension a look
McShadersAPI.registerBinding(DimensionBinding.of(
        "mymod:dreamscape", DimensionId.parse("mymod:dreamscape"), look));

// Add an effect type of your own
McShadersAPI.registerEffect(EffectDefinition.of("mymod:kaleidoscope", "mymod"));

// Or supply an entire renderer
McShadersAPI.registerBackend(new MyBackendFactory());
```

Registration closes on first use of the backend, so register from your mod's
initialiser. Load order between mods does not matter: backends are chosen by declared
priority, and colliding effect types are refused rather than silently shadowed.

Full guide, including how to implement a backend:
**[docs/USING_AS_A_LIBRARY.md](docs/USING_AS_A_LIBRARY.md)**.

## Layout

```
core/       the framework — pure Java, zero Minecraft dependencies
check/      shader-pack checker — pure Java, runs without the game
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

So does the checker:

```sh
cd check && ../gradlew test
```

The shared module needs JDK 25, but still no Minecraft Maven:

```sh
./gradlew :common:build --configure-on-demand
```

The loader modules additionally need reachable Fabric/NeoForge Maven:

```sh
./gradlew build
```

## Checking a shader pack

A post-processing chain is bound to its shaders by convention and to its uniform
blocks by *byte offset*. Nothing in the normal toolchain checks either. A shader
that moved leaves a chain that loads, catches its own exception and silently does
nothing; a uniform block whose two declarations have drifted apart leaves a shader
reading real numbers from the wrong places. Neither produces an error anywhere.

`check/` finds both, from text, with no GPU and no game:

```sh
cd check && ../gradlew run --args="/path/to/assets"
```

Released versions publish a runnable distribution to GitHub Packages as
`net.cyberpunk042:mcshaders-check`, so using it does not mean cloning this
repository.

It walks every `post_effect/*.json` under that directory and reports missing
shaders, unresolved includes, targets read before they are written, targets
nothing reads, inputs whose sampler the shader does not declare, and uniform
blocks the pipeline and the shader disagree about — with the byte offset where
the disagreement starts. It exits non-zero on errors, so it can gate a build.
Warnings and notes are printed and do not fail.

Being a separate build with no Minecraft dependency is deliberate: a pack should
be checkable in someone else's CI, by someone with no interest in building this
mod.

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
- Public API for third parties: effect types, backend contribution with priority
  selection, and binding registration, all with a defined registration lifecycle
- GLSL include resolution, with cycle reporting and `#line` source mapping
- The geometry half: shape maths, the shape model, motion, and the field system
  (shapes and the links between them) — ported out of `the-virus-block-mc` with
  characterisation tests it did not have before
- Static checking that needs no GPU: std140 placement, reading a uniform block
  back out of GLSL, comparing two declarations byte by byte, and a CLI that exits
  non-zero so a pack can be gated in CI
- An editing schema, an edit session with undo, and a tuning store that keeps what
  was edited
- The editor itself — schema-driven screens on Fabric, opened by an unbound key

**360 tests in `core` and 35 in `check`, 0 failures** — both verified 2026-08-22.

Not built yet:

- Any rendering backend that draws (M2). The fog entry point is now established
  from a mod that compiles on 26.2; the post-effect chain is not — see
  [docs/RENDERING-26.2.md](docs/RENDERING-26.2.md)
- Datapack loading — bindings are programmatic for now (M3)
- The demo dimensions (M4)

Two honest caveats about the editor. It compiles against the real 26.2 API in CI,
which proves its calls exist — not that the screens look right; that needs someone
to open them. And the mod registers no effects of its own yet, so on a fresh
install it correctly opens a screen saying there is nothing to edit.

The whole build is green in CI: `./gradlew build` on JDK 25 produces Fabric and
NeoForge jars against Minecraft 26.2, which pins every version in
[docs/VERSIONS.md](docs/VERSIONS.md).

Getting there surfaced two 26.x migration details worth knowing if you are porting
from 1.21 — the Loom plugin id changed and mappings are gone entirely. Both are
written up in VERSIONS.md.

See [docs/ROADMAP.md](docs/ROADMAP.md).

The effects being ported from `the-virus-block-mc` have been run through the checker;
what it found — four chains naming archived shaders, one uniform block that thirteen
chains share and disagree with, and several hosts that stopped being extended when their
shader was — is written up in
[docs/VIRUS-BLOCK-SHADER-STATE.md](docs/VIRUS-BLOCK-SHADER-STATE.md), along with the
things in that report that look alarming and are correct by design.

## License

MIT — see [LICENSE](LICENSE).

# MC Shaders++

Change how a Minecraft dimension **looks** — by editing JSON, not by writing shaders.

```json
{ "id": "overworld_storm", "dimension": "minecraft:overworld", "priority": 20,
  "condition": { "type": "weather", "weather": "thunder" },
  "stack": { "layers": [ { "id": "atmosphere", "kind": "fog", "type": "mcshaders:fog",
    "params": { "start": 8.0, "end": 56.0,
                "color": { "r": 0.325, "g": 0.361, "b": 0.404, "a": 1.0 } } } ] } }
```

Drop that in a datapack, `/reload`, and the Overworld's fog closes in when it thunders.
Looks are described as data, so they can be authored, layered by priority, gated on
world conditions, and eased between — with no Java and no GLSL.

> ### Honest status
>
> **Everything is built and tested. Nothing has been run in a game.**
>
> The framework has 585 tests. CI compiles the mod against the real Minecraft 26.2 jar
> on both loaders, so every API call in it exists — that part is read from source, not
> guessed. But **a mixin that compiles has not been shown to apply, and an event that
> compiles has not been shown to fire.** Fog reaching the screen, `/reload` loading a
> file, and the editor screens have each been verified only as far as the compiler goes.
>
> If you are looking for an Iris-style shader pack, this is not one — see
> [docs/SHADERS.md](docs/SHADERS.md), which separates the three different things
> "shaders in Minecraft" can mean.

**New here?** [docs/SHADERS.md](docs/SHADERS.md) explains what this does in plain terms
and is the right place to start.

## What is in here

Two halves, and they are at different stages.

**Looks** — what the whole screen does. Fog that closes in when it thunders, per
dimension, on conditions, written as datapack JSON with no code. See
[BINDINGS.md](docs/BINDINGS.md).

**Forms** — things with a shape. A sun, a magic circle, a beam, a cage: **16 shapes**
composed with fill, colour, arrangement patterns and animation into field layers. All
of it tested; **none of it drawn yet**, because no renderer consumes a mesh. See
[SHAPES.md](docs/SHAPES.md).

And a **checker** that validates shader packs without the game, which works today and
needs neither half.

**The effects this exists to carry forward** — the sun (`field_visual_v7`, the
Panteleymonov Sun; `v6` the pulsar before it, `v8` the electric aura forked from it, each
with a god-rays HDR twin), `magic_circle`, the shockwaves — are 21 post-processing chains in
[`the-virus-block-mc`](https://github.com/cyberpunk042/the-virus-block-mc), catalogued by
name in [EFFECTS.md](docs/EFFECTS.md). **None are ported yet**, and nothing here can run
one — that needs the chain runtime, and reversed-Z makes every depth-reading shader a
real port rather than a copy.

## What works, and what does not

| | |
|---|---|
| The look model — layers, params, conditions, priority merge, easing | **Tested**, no Minecraft needed — format reference in [docs/BINDINGS.md](docs/BINDINGS.md) |
| Shader-pack checker (`check/`) | **Works today**, standalone, no game |
| The geometry engine — 16 shapes, 63 arrangement patterns, animation, fields | **Tested**, no Minecraft needed — catalogue in [docs/SHAPES.md](docs/SHAPES.md) |
| Anything drawing that geometry | **Not built** — nothing outside `core` consumes a `Mesh` |
| Fields authorable as data | **Not built** — the model is Java only; bindings are the half that is data |
| Datapack loading and `/reload` | **Compile-verified**; never observed loading a file |
| Fog reaching the frame | **Compile-verified**; never observed changing a pixel |
| In-game editor (both loaders) | **Compiles**; screens never opened |
| Its own post-processing chains | **Not built** — 26.2 entry points not established |
| The 21 effects from `the-virus-block-mc` | **Not ported.** Catalogued in [docs/EFFECTS.md](docs/EFFECTS.md); 19 of 21 have wiring errors on 1.21.6 today |
| **Running your own GLSL** | **Not built.** You can contribute a *backend* that draws however you like, but nothing here loads shader source — see [docs/SHADERS.md](docs/SHADERS.md#can-i-bring-my-own-shaders) |
| Coexistence with Iris and friends | **Not built** |

The first launch is instrumented for this gap: after 200 frames the mod logs which links
of the chain ran and, for any that did not, what to look at — so the first run produces a
diagnosis rather than a mystery.

## Why it is built this way

Minecraft 26.2 ships an experimental Vulkan renderer alongside OpenGL, and OpenGL is
slated for removal once Vulkan stabilises. A shader mod written directly against GLSL
today gets rewritten when that lands.

So the graphics API sits behind one interface, and everything above it is plain data:
a description of what the frame should look like, never how to draw it. Swapping
renderers becomes an addition rather than a migration.

The same decision makes the framework testable without a game — the core has no
Minecraft dependency at all and its suite runs on a bare JDK. That is why the honest
status above can be as specific as it is.

## Targets

| | |
|---|---|
| **Minecraft** | 26.2 (current stable); 26.3 prepared, retarget when it ships |
| **Loaders** | Fabric and NeoForge, at parity |
| **Java** | 25 for the mod, 21 for the framework core |
| **Gradle** | 9.5.1, pinned in the committed wrapper |
| **Artifacts** | `mcshaders-core`, `mcshaders-api`, `mcshaders-check` — on release; none published yet |

Minecraft coordinates are looked up per version from `gradle.properties`, so retargeting
is one property change rather than a build-script edit. Version provenance — including
which numbers are verified in CI and which are not — is in
[docs/VERSIONS.md](docs/VERSIONS.md).

## Using it from your own mod

```kotlin
dependencies {
    implementation("net.cyberpunk042:mcshaders-api:0.2.0")   // in a Minecraft mod
    implementation("net.cyberpunk042:mcshaders-core:0.2.0")  // anywhere on the JVM
}
```

```java
// Give a dimension a look
McShadersAPI.registerBinding(DimensionBinding.of(
        "mymod:dreamscape", DimensionId.parse("mymod:dreamscape"), look));

// Add an effect type of your own
McShadersAPI.registerEffect(EffectDefinition.of("mymod:kaleidoscope", "mymod"));

// Make it tunable from the in-game editor
McShadersAPI.registerSchema("mymod:kaleidoscope", schema);

// Or supply an entire renderer
McShadersAPI.registerBackend(new MyBackendFactory());
```

Registration closes on first use of the backend, so register from your initialiser. Load
order between mods does not matter: backends are chosen by declared priority, and
colliding effect types are refused rather than silently shadowed.

Full guide — including how to implement a backend:
**[docs/USING_AS_A_LIBRARY.md](docs/USING_AS_A_LIBRARY.md)**. Its examples are run as
tests, so they cannot quietly go stale.

## Layout

```
core/       the framework — pure Java, zero Minecraft
check/      shader-pack checker — pure Java, runs without the game
common/     the published API — still no Minecraft, so it stays usable off-game
vanilla/    vanilla Minecraft, no loader API — shared by both loaders
fabric/     Fabric adapter — registration only
neoforge/   NeoForge adapter — registration only
datapack/   the looks this mod ships, authored once for both loaders
docs/       architecture, roadmap, version provenance, 26.2 findings
```

`vanilla/` is why Fabric and NeoForge behave identically rather than being kept in step
by hand: the work lives there once, and each loader keeps only the few lines that differ.

## Building

The core and the checker build anywhere — no Minecraft toolchain, no Minecraft Maven:

```sh
cd core  && ../gradlew test
cd check && ../gradlew test
```

The published API needs JDK 25, but still no Minecraft Maven:

```sh
./gradlew :common:build --configure-on-demand
```

The loader modules additionally need reachable Fabric/NeoForge Maven:

```sh
./gradlew build
```

## Checking a shader pack

Independent of the rest, and useful on packs unrelated to this mod. A chain is bound to
its shaders by naming convention and to its uniform blocks by **byte offset**, and
nothing in the normal toolchain checks either — so a renamed shader leaves a chain that
loads, swallows its error and does nothing.

```sh
cd check && ../gradlew run --args="/path/to/assets"
```

Reads text, needs no GPU, exits non-zero on errors so it can gate a build. Published as
`mcshaders-check` so it can run in someone else's CI without cloning this repo.

It cannot tell you whether the GLSL compiles. That needs a driver.

## Documentation

| | |
|---|---|
| [SHADERS.md](docs/SHADERS.md) | **Start here.** What this does, in plain terms |
| [BINDINGS.md](docs/BINDINGS.md) | The datapack format: every field, all ten condition types |
| [EFFECTS.md](docs/EFFECTS.md) | **The effects catalogue** — the sun by version, `magic_circle`, the shockwaves, and what porting them needs |
| [SHAPES.md](docs/SHAPES.md) | The geometry engine: 16 shapes, patterns, fields — a different mechanism |
| [USING_AS_A_LIBRARY.md](docs/USING_AS_A_LIBRARY.md) | Consuming it from a mod or off-game |
| [ROADMAP.md](docs/ROADMAP.md) | What is done, what is next, and what each milestone did *not* do |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | Module layout and data flow |
| [VERSIONS.md](docs/VERSIONS.md) | Every pinned version, with how confident we are in it |
| [RENDERING-26.2.md](docs/RENDERING-26.2.md) | 26.2 render-path findings, with sources |
| [DATAPACKS-26.2.md](docs/DATAPACKS-26.2.md) · [BLOCKS-26.2.md](docs/BLOCKS-26.2.md) · [PORTALS-26.2.md](docs/PORTALS-26.2.md) | The same, per area |
| [PORTING.md](docs/PORTING.md) | What came from `the-virus-block-mc`, and the licence split |
| [VIRUS-BLOCK-SHADER-STATE.md](docs/VIRUS-BLOCK-SHADER-STATE.md) | What the checker found in those packs |

Every 26.2 document says what was verified, from which source, **and what was not**.
That convention exists because this project has been wrong before by remembering an API
instead of reading one.

## License

Contributions are welcome — [CONTRIBUTING.md](CONTRIBUTING.md) has the build commands,
what a good change looks like here, and the one piece of 26.2 evidence that would
unblock the most.

MIT for the engine — see [LICENSE](LICENSE). Content ported from
`the-virus-block-mc` carries its own terms; the split is recorded in
[docs/PORTING.md](docs/PORTING.md).

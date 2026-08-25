# Architecture

## The shape of the problem

Two forces pull in opposite directions:

1. **Shader mods are graphics-API code.** They are the most coupled thing you can
   write to a specific renderer.
2. **Minecraft's renderer is mid-transition.** 26.2 ships an experimental Vulkan
   backend beside OpenGL, and OpenGL is slated for removal.

Writing GLSL against today's OpenGL means rewriting when Vulkan lands. The design
below exists to make that a *substitution* rather than a *rewrite*.

## The layering

```
        ┌─────────────────────────────────────────────────────────┐
        │  Datapacks / config / other mods                        │
        └───────────────────────┬─────────────────────────────────┘
                                │  declare bindings
        ┌───────────────────────▼─────────────────────────────────┐
        │  core/          pure Java — no Minecraft, no OpenGL      │
        │                                                          │
        │   BindingRegistry ──resolve(WorldState)──> EffectStack    │
        │            │                                    │        │
        │            │                              Transition     │
        │            │                                    │        │
        │            └──> EffectCompiler ──> EffectGraph  <┘        │
        └───────────────────────┬─────────────────────────────────┘
                                │  EffectGraph (backend-neutral IR)
        ┌───────────────────────▼─────────────────────────────────┐
        │  EffectBackend        ← the seam                         │
        │   ├─ OpenGLBackend    (planned)                          │
        │   ├─ VulkanBackend    (planned, when the API settles)    │
        │   └─ NoOpBackend      (servers, tests, fallback)         │
        └─────────────────────────────────────────────────────────┘

        ┌─────────────────────────────────────────────────────────┐
        │  common/  Minecraft-facing, loader-independent           │
        │  fabric/  neoforge/   thin per-loader adapters           │
        └─────────────────────────────────────────────────────────┘
```

The important property: **everything above `EffectBackend` is plain data.** An
`EffectGraph` says *"a fog pass at 0.4 strength, then a colour grade"*. It never
says *"bind this shader program"*. Both a Vulkan and an OpenGL backend consume
the identical graph.

That is also why `core/` has zero Minecraft dependencies. It is not purity for
its own sake — it is what lets `core`'s 471 tests (counted 2026-08-25) run on a
plain JDK with no game, no GPU, and no network access to Minecraft's Maven hosts.

## The per-frame cycle

`ShaderPipeline.frame()` is the whole runtime, once per frame:

1. **Sample.** The Minecraft layer fills a `WorldState` — dimension, time of day,
   Y level, weather, biome tags, submersion. Plain data, no Minecraft types.
2. **Resolve.** `BindingRegistry.resolve(state)` evaluates every binding's
   condition and merges the survivors by priority into one `EffectStack`.
3. **Retarget, if changed.** A different resolution starts a `Transition` from
   what is currently on screen. Unchanged resolutions do *not* restart it — that
   bug would freeze every blend at t=0.
4. **Advance.** The transition steps forward by the frame delta.
5. **Compile.** `EffectCompiler` turns the blended stack into an `EffectGraph`,
   dropping anything the backend cannot draw and recording why.
6. **Render.** The backend executes the graph.

## Design decisions worth knowing

### Merging is per-layer, not per-stack

Bindings merge by *layer id*. A high-priority binding that redefines `fog` leaves
the rest of a lower-priority binding's stack intact.

Without this, tweaking one dimension's fog would mean restating its entire look,
and every pack would fight every other pack. With it, a base dimension look and a
"night-time addition" compose without either knowing about the other.

### Transitions fade unmatched layers rather than dropping them

When blending stack A into stack B, a layer present only in A fades out and one
present only in B fades in. Dropping them instead would make a portal crossing
pop visibly — exactly the artefact the transition system exists to remove.

### Retargeting starts from what is on screen

A player crossing two portals in quick succession must not snap back to the
original look. `Transition.retarget()` starts the new blend from `current()`.

### Malformed input degrades, never crashes

Unsupported effect kinds, exceeded pass limits, colour channels set to NaN, a
parameter that is a string where a number was wanted: all of these produce a
warning and a skipped effect. A broken pack should cost a player some visuals,
never their session. Every one of these paths is covered by a test.

### Conditions are data, not lambdas

`Condition` is a closed algebra of records rather than arbitrary predicates. That
is what lets a datapack express them declaratively, and lets the framework
serialise, diff and reason about them. Extending it is a deliberate act.

### Time-of-day ranges wrap

`TimeOfDay(13000, 1000)` spans midnight. Without wrapping, every night-time rule
would have to be written as two.

## What is deliberately not here yet

This is a base, and it is honest about its edges:

- **No backend implementation.** `NoOpBackend` is the only one. Writing the
  OpenGL one means calling into 26.2's render internals, which have not been
  verified against real sources — see [ROADMAP.md](ROADMAP.md) M2.
- **No datapack loading.** The registry is built programmatically; the
  serialisation layer is M3.
- **No dimensions.** The demo dimensions are M4, once the framework they
  demonstrate actually renders.
- **No mixins.** None are needed until there is a render hook to install.

The ordering is deliberate: the framework is the product, and it is easier to
verify without a renderer attached than with one.

# Roadmap

Ordered so that each milestone is verifiable before the next depends on it.

## M1 — Framework core ✅ done

The backend-neutral effect model, in pure Java with no Minecraft dependency.

- Parameter system with interpolation (`ParamValue`, `EffectParams`)
- Effect layers and stacks with per-id merging (`EffectLayer`, `EffectStack`)
- Dimension bindings with a declarative condition algebra (`BindingRegistry`)
- Timed transitions with easing and mid-blend retargeting (`Transition`)
- Capability-aware compilation to a render plan (`EffectCompiler`, `EffectGraph`)
- The backend seam (`EffectBackend`) plus a no-op implementation

**Verified:** 52 tests, 0 failures, on JDK 21.

## M2 — First rendering backend

Make it draw something.

- Confirm 26.2's post-processing entry points against real sources — **do not
  write this from memory**, the API is new and unverified here
- `OpenGLBackend` implementing `EffectBackend`
- Client render hook feeding `ShaderPipeline.frame()`
- `WorldState` sampler pulling dimension, time, Y, weather, biome tags
- Backend selection with graceful fallback to `NoOpBackend`

**Done when:** a hardcoded fog binding visibly changes the frame in-game.

**Risk:** this is the milestone that touches Minecraft internals directly. Budget
for the API not matching expectations.

## M3 — Declarative bindings

Make it authorable without Java.

- Codecs for `EffectLayer`, `EffectStack`, `Condition`, `DimensionBinding`
- Datapack loading from `data/<ns>/mcshaders/bindings/*.json`
- Reload handling — build a fresh registry, swap atomically, ease into it
- Pack-facing validation errors that name the file and the field

**Done when:** a dimension's look can be changed by editing JSON and running
`/reload`, with no restart.

## M4 — Demo dimensions

Prove the framework with content.

- Two dimensions with distinct visual identities, built entirely on M3's
  datapack format — no privileged access
- Custom sky, fog and colour grading per dimension
- Portal transitions exercising the blend path

**Done when:** the demo dimensions use nothing a third-party pack could not.
If they need a private API, that is a bug in the framework, not the demo.

## M5 — Vulkan readiness

- `VulkanBackend` against 26.2+'s experimental renderer
- Capability negotiation between the two backends
- Verify identical `EffectGraph` input produces matching output on both

**Done when:** the same binding renders equivalently on either backend, and the
selection is invisible to pack authors.

## M6 — Ecosystem fit

- Behaviour alongside Iris and other shader mods — cooperate, do not fight
- Public API surface for other mods to register bindings
- Performance budget and profiling hooks
- 26.3 promoted from placeholder to a real target

## Cross-cutting, ongoing

- ~~**Pin the versions.**~~ Done 2026-08-21 — the loaders job went green, pinning
  the whole table, and is now blocking. See [VERSIONS.md](VERSIONS.md).
- **Gradle 10 readiness.** The loader build reports "Deprecated Gradle features
  were used in this build, making it incompatible with Gradle 10." Worth a pass
  with `--warning-mode all` to see whether it is our scripts or the Loom/MDG
  plugins; if it is theirs, this waits on their updates.
- **Consider a newer NeoForge build.** `26.2.0.35-beta` is pinned and works, but
  newer builds exist.
- **Wire multiversion** once 26.3 ships and the loader build is green. The
  per-version property layout is already in place; see VERSIONS.md for why the
  preprocessor was deliberately backed out rather than left half-connected.
- Keep `core/` free of Minecraft imports. It is the reason the framework is
  testable at all; the first convenience import that breaks it costs that.

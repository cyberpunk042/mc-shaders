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

**Verified:** 127 tests, 0 failures, on JDK 21.

## M1.5 — Library surface ✅ done

Making the framework consumable by other people.

- `McShadersAPI` — the supported entry point, with `@Stable`/`@Experimental` markers
- `EffectDefinition` + `EffectRegistry` — third-party effect types, namespaced,
  refused on collision rather than last-write-wins
- `BackendFactory` + `BackendRegistry` — contributed renderers, priority selection,
  fall-through on failed initialisation, contained factory exceptions
- Registration lifecycle that closes on first use, so it is correct on both loaders
  without depending on either's lifecycle events
- Published to GitHub Packages as `mcshaders-core` and `mcshaders-api`, with sources
  and javadoc
- [USING_AS_A_LIBRARY.md](USING_AS_A_LIBRARY.md), whose examples are themselves tests

## M1.6 — GLSL include resolution ✅ done

A shader library of any size needs includes, and GLSL has none. This is the
expansion step, in `core` — pure string processing, so it is fully testable
without a GPU or a game.

- Include-once, so shared dependencies do not produce duplicate definitions
- Cycles reported with the chain that formed them. Include-once alone already
  prevents runaway recursion, so a cycle would otherwise be swallowed silently —
  but GLSL has no forward declarations, so a cycle means symbols referenced
  before they are declared, and that is worth saying out loud
- `#line` directives with an integer source index plus a `SourceMap`, so driver
  errors name the file a human has to edit. GLSL 150's `#line` takes integers
  only, which is why the file names live in a side table
- `#version` is kept as the first line, ahead of any emitted `#line`
- Total: every call returns a result or throws, and the result is never its input
  unchanged. Returning the input as a failure signal is what lets a compile hook
  re-enter with identical input and recurse until the stack dies

## M1.7 — Shape maths foundation ✅ done

First slice of the visual-engine extraction from the-virus-block-mc. See
[PORTING.md](PORTING.md) for the licensing split and the measured scope.

- `ShapeMath` — sphere, spheroid, ellipsoid, ovoid, egg, pear, droplet, bullet and
  cone vertex generation, normals, blending
- `SimplexNoise`, `ShapeState`, `ShapeStage`, and the shape vocabulary enums
- Validation annotations the shape model uses

Ported with characterisation tests, which it had none of before. They earned their
keep immediately — see the note at the end of PORTING.md.

## M1.8 — Shape model ✅ done

`Shape` plus `SphereShape`, `RingShape`, `CylinderShape` and `PrismShape`, with
the enums, effect records and `CellType`/`Facing`/`Axis` they depend on.

- JSON binding stripped on the way across; `@JsonField` metadata retained so a
  codec layer above core can still serialise the model
- `core` gains JOML — pure-Java maths, the one allowed dependency class
- Contract tests covering all four shapes uniformly, which is what catches an
  automated strip having quietly damaged one of them

## M1.9 — Motion model ✅ done

`Transform`, `OrbitConfig`/`OrbitConfig3D`, and the `animation` and `energy`
packages — 48 files, the transitive closure `LinkResolver` needs.

- `AnchorResolver` excluded: it takes a `PlayerEntity`, so it is genuinely
  Minecraft-coupled and belongs in `common`
- `Waveform.SINE` reimplemented on `java.lang.Math`, since Minecraft's
  lookup-table `MathHelper` cannot come into core. Behavioural change, so the
  tests pin its landmarks

## M1.10 — Field model (wires) ✅ done

`Primitive`, `PrimitiveLink`, `LinkResolver` and `SimplePrimitive`, plus the
shape, pattern, appearance, fill and visibility packages behind them.

- Upstream's link test was a `main()` printing ticks and crosses in
  `src/main/java`, so it never ran in a build. Its three cases plus ten more are
  now `LinkModelTest`
- `ColorTheme`, `ColorThemeRegistry` and `ColorResolver` excluded as
  Minecraft-coupled; `FieldColor.mix` dropped, its javadoc and its delegate
  having disagreed about what it did

## M1.11 — Static checking ✅ done

The part of "does this shader pack work?" that needs no GPU.

- `core.layout` — std140 placement, reading a block back out of GLSL, and
  comparing two declarations of it by byte offset
- `core.chain` — a chain of passes and targets, and the checks that need the
  whole chain rather than one pass
- `check/` — a JSON codec, a resource-tree provider, and a CLI that exits
  non-zero, so a pack can be gated in CI without the game
- Findings against `the-virus-block-mc` recorded in
  [PORTING.md](PORTING.md), with what each does and does not establish

**Not done by this:** whether the GLSL compiles. That needs a driver.

## M1.12 — Editing schema ✅ done

`core.schema` — what is tunable about an effect, described without a toolkit, so
the same description drives a screen, a web page or a command line.

- Defaults are `ParamValue`, not floats, so a colour is one control
- Bounds come from `ValueRange`, sharing the vocabulary the library already has
- `SchemaAudit` compares a schema against the effect it claims to describe

`core.edit.EditSession` is the sitting on top of it: coerce an edit, remember
enough to undo it, know what changed. The session performs the edit rather than
offering a history to push to, so history cannot be forgotten.

**Not done by this:** any actual UI — see M1.13, which is where that arrived.

**Why it waited.** At the time, `common/`, `fabric/` and `neoforge/` contained
five files with **zero** `net.minecraft` imports between them: no Minecraft API
had ever been compiled in this repository. A screen would have been the first,
written from memory against a 26.2 API that could not be read from here. What
unblocked it was not deciding to risk it but finding a mod that ships on 26.2 and
reading its GUI calls out of the source — see [RENDERING-26.2.md](RENDERING-26.2.md).
The same condition still holds for the render hooks in M2, which is why they are
still unwritten.

## M1.13 — The editor ✅ done

The first Minecraft API compiled in this repository, and the screen M1.12 declined
to write from memory.

- `SchemaScreen` — controls generated from an `EffectSchema`, so adding a parameter
  to a schema adds its control with no screen code touched
- `EffectPickerScreen` when several effects are editable, `NothingToEditScreen` when
  none are. A key that opens nothing is indistinguishable from a key that is broken
- `EditorKey` — registered unbound, because a mod claiming a key the player did not
  ask for is a nuisance
- `SchemaRegistry` + `McShadersAPI.registerSchema` — the link that was missing
  between an effect existing and an editor being able to reach it
- `TuningStore` + `McShadersAPI.tuning()` — where the results live once the screen
  closes

**On the store.** Without it the editor was a screen that looked like an editor:
every control worked, every value coerced, undo and redo behaved, and pressing
Escape threw all of it away, because the session was built fresh from the schema's
defaults on each press and nothing ever read it back. That is the same defect this
project keeps finding elsewhere — a declaration with nothing verifying it connects
to a consumer — and it was ours. The store's first consumer is the screen itself on
reopening, which is what makes the round trip observable rather than aspirational;
a renderer is the second, and reads `TuningStore.effective`.

**The editor is no longer empty.** `BuiltinEffects` registers `mcshaders:fog` —
chosen because it is the one effect whose entry point has been read out of a mod
that compiles on 26.2, and because M2's done-when is a fog binding. `CoronaEffect`
and `HorizonEffect` were deliberately left out: both are geometry effects with no
established hook, and they would have filled the editor without connecting it to
anything.

**Not done by this:** the screens compile and are wired correctly, which is not the
same as their looking right. That needs someone to open them. And the mod registers
no effects of its own yet, so on a fresh install the empty-state screen is the
correct result rather than a failure.

**Both loaders now, as of the `vanilla/` module.** The three screens and the
choose-which-screen logic moved to `vanilla/`; only binding a key stayed per-loader,
because that genuinely differs. On NeoForge the mapping and its category register
through `RegisterKeyMappingsEvent` — a **mod-bus** event, unlike everything else this
mod subscribes to — while the tick that opens the screen is on the game bus. NeoForge
deprecates `KeyMapping.Category.register` in favour of the event, so the vanilla static
is not used there.

## M2 — First rendering backend

Make it draw something.

- ~~Confirm 26.2's post-processing entry points against real sources~~ — partly
  done: see [RENDERING-26.2.md](RENDERING-26.2.md). The engine-wide changes are
  confirmed; **the fog entry point is now established** — `FogRenderer#setupFog`
  in the new `net.minecraft.client.renderer.fog` package, read out of a mod that
  compiles on 26.2, along with camera, render-state and partial-tick access. The
  **post-effect chain** hook points are still unread, and still need either the
  26.2 sources or a 26.2 mod doing post-processing
- Because of that split, the fog binding is the part of this milestone that is
  unblocked — and it is what the done-when below actually asks for. The full
  chain is not
- `OpenGLBackend` implementing `EffectBackend`, registered through the same
  `BackendFactory` path third parties use — if the built-in renderer needs privileged
  access, that is a gap in the public API
- ~~`OpenGLBackend` … registered through the same `BackendFactory` path~~ — **done
  in shape, not in scope**: `fog.FogBackend` is registered that way and is the first
  backend the mod contributes at all. Before it, selection always fell through to
  `NoOpBackend`, so every resolved effect was compiled and discarded. It renders
  nothing itself, which is what fog *is* on this platform: vanilla owns the fog and a
  mod's part is to change the numbers it is about to use
- Client render hook feeding `ShaderPipeline.frame()` — the hook is established as
  `LevelExtractionEvents.END_EXTRACTION`, **not** `START_MAIN`, and not the
  `WorldRenderEvents` that no longer exists. See [RENDERING-26.2.md](RENDERING-26.2.md)
- `WorldState` sampler pulling dimension, time, Y, weather, biome tags — **five of
  six are verified; time of day is not**. `Level#getDayTime()` does not exist on 26.2
  and no client accessor for its replacement was found, so `Condition.TimeOfDay`
  cannot yet be evaluated. A sampler must say so rather than quietly reporting noon
- Backend selection with graceful fallback to `NoOpBackend`

**Both loaders, as of the `vanilla/` module.** NeoForge has the render hook
(`ExtractLevelRenderStateEvent`) and applies fog through `ViewportEvent.RenderFog`,
which hands over the same `FogData` the Fabric mixin reaches. `WorldSampler` and
`FogApply` are shared, so the two behave identically by construction rather than by
being kept in step — which matters because *which* `FogData` pair to write is still
open, and one in-game test should answer it for both.

**Done when:** a hardcoded fog binding visibly changes the frame in-game.

**Reading the result.** Every link is written and none has been watched running, so
after 200 frames the mod says once, in the log, which links ran and — when one did
not — what to look at. A dead render hook, a mixin that never applied, and a binding
matching no dimension all look identical from a chair: the fog just does not change.
`ChainCheck` distinguishes them. It lives in `common` and is the one part of the
render path with tests, because counting and interpreting need no Minecraft.

**Risk:** this is the milestone that touches Minecraft internals directly. Budget
for the API not matching expectations.

## M3 — Declarative bindings

Make it authorable without Java.

- ~~Codecs for `EffectLayer`, `EffectStack`, `Condition`, `DimensionBinding`~~ —
  **done**: `common.codec.BindingCodec`, both directions, with pack-facing errors
  that name the file and the path (`nether.json at stack.layers[2].params.dir[1]`).
  It lives in `common` rather than `core` for the same reason the shader checker's
  codec does: the engine models bindings, it does not parse them, so the published
  library carries no JSON dependency. 22 tests, and `common` had none before this
- The 26.2 reload API is confirmed — see [DATAPACKS-26.2.md](DATAPACKS-26.2.md).
  It also settles which listener to use: `SimpleJsonResourceReloadListener` is
  generic over a DFU codec on 26.2, which does not compose with our gson codec, so
  M3 uses the plain `ResourceManagerReloadListener` and feeds `BindingLoader`
- ~~Datapack loading from `data/<ns>/mcshaders/bindings/*.json`~~ — **done**:
  `BindingLoader` turns a set of files into a registry, skipping the broken ones
  rather than blanking every dimension over one typo, and reporting what it
  skipped and what overrode what. `BindingReloadListener` is what finally hands it
  the files: the last unread link was `FileToIdConverter#listMatchingResources` plus
  `Resource#openAsReader`, both now read out of vanilla 26.2 — see
  [DATAPACKS-26.2.md](DATAPACKS-26.2.md)
- ~~Reload handling — build a fresh registry, swap atomically, ease into it~~ —
  **done**: the listener is registered on the main entrypoint, so `/reload` reaches
  `McShaders.setRegistry` and `Transition` eases into the result
- **A bug found in the wiring, not by it.** `loadBindings` applied the pack set
  wholesale, so the first `/reload` replaced every binding a mod had registered in
  Java — and since `registerBinding` throws once registration closes, nothing could
  put them back. This mod's own `beyond_depths` was among them. The reasoning behind
  wholesale replacement is about *packs*, which a player can remove; it never applied
  to bindings compiled into a mod. Packs are now layered *over* the registered set,
  and a pack binding reusing a registered id overrides that one rather than deleting
  the rest. `BindingLayeringTest` pins it, and the first version of that test was
  itself wrong — it captured the baseline through the method under test, so four of
  its assertions passed against the unfixed code. Mutation caught it
- ~~Pack-facing validation errors that name the file and the field~~ — **done**:
  `nether.json at stack.layers[2].params.dir[1]: expected a number, found a string`

**Done when:** a dimension's look can be changed by editing JSON and running
`/reload`, with no restart.

**Where that stands:** every link is written and the chain is closed, but the same
caveat as M2 applies — nobody has run it. What is *known* not to be covered is
dedicated servers: bindings are `data/` content, so the reload is server-side, and
on a dedicated server it fills a registry in a JVM that renders nothing. Singleplayer
and LAN share a JVM and are what this milestone's done-when describes. Syncing a
server's bindings to its clients is unwritten and is not claimed.

**Both loaders now have one.** The shared vanilla work lives in a `vanilla/` module —
vanilla Minecraft, no loader API — so Fabric and NeoForge each carry a two-line
adapter rather than a copy of the scan. That module is what `common` could not be:
`common` is published as `mcshaders-api` and a Minecraft dependency would make it
unusable outside the game.

## M4 — Demo dimensions

Prove the framework with content.

- Two dimensions with distinct visual identities, built entirely on M3's
  datapack format — no privileged access
- ~~Custom sky, fog and colour grading per dimension~~ — **fog only, and that is not a
  shortcut**: fog is the one effect with a backend, so it is the only thing a
  dimension's look can currently consist of. Sky and colour grading wait on their own
  backends rather than on this milestone
- Portal transitions exercising the blend path

**Second look shipped, on a vanilla dimension.** `overworld_weather.json` restyles
`minecraft:overworld` — far pale air normally, closing in during rain or thunder,
suppressed underwater where water owns the fog. It is on a vanilla dimension
deliberately: that needs no custom dimension type, so it is the thing a pack author
is likeliest to want first, and it proves the format on a dimension the framework
does not own.

It also exercises the format past the one condition type `beyond_depths` used. That
file is `y_range` alone; this one nests `all` / `any` / `not` over `weather` and
`submerged`, and every one of them travels through `BindingCodec` in
`PackDimensionsTest` rather than a hand-built fixture — so a field name that does not
match the codec fails a test rather than failing quietly in someone's world. Writing
it caught exactly that: `all`/`any`/`not` take `of`, not `conditions`.

**A second demo dimension is still missing**, and deliberately so: authoring its
`dimension_type` means values (`skybox`, `timelines`, `cardinal_light`) that cannot be
checked against any 26.2 source available here, and an invalid one fails at pack load
with the dimension silently absent. Better to ship a look that is verified than a
dimension that might not exist.

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
  were used in this build, making it incompatible with Gradle 10." Half answered:
  `core` and `check` — the two included builds, which use no third-party plugins —
  are **clean** under `--warning-mode all` on Gradle 9.5.1. So nothing in the pure-Java
  half is the cause, and it is the root build that carries it.
  Localising it further to our scripts vs Loom/MDG needs a machine with **both** JDK 25
  and network access to the Fabric and NeoForged Maven hosts: the root build's
  toolchain is 25, and `fabric/build.gradle.kts` cannot even be *configured* offline
  because Loom is unresolvable. If it turns out to be the plugins', this waits on
  their updates.
- **Consider a newer NeoForge build.** `26.2.0.35-beta` is pinned and works, but
  newer builds exist.
- **Wire multiversion** once 26.3 ships and the loader build is green. The
  per-version property layout is already in place; see VERSIONS.md for why the
  preprocessor was deliberately backed out rather than left half-connected.
- Keep `core/` free of Minecraft imports. It is the reason the framework is
  testable at all; the first convenience import that breaks it costs that.

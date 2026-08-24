# Rendering on 26.2 — what was confirmed, and what it costs

M2 in the [roadmap](ROADMAP.md) says: *confirm 26.2's post-processing entry
points against real sources — do not write this from memory*. This is that
confirmation, gathered before any Minecraft-facing code was written.

**Source.** NeoForged's [26.1.x → 26.2 migration
primer](https://github.com/neoforged/.github/blob/main/primers/26.2/index.md),
cross-checked against Fabric's 26.2 announcement and the vanilla assets at
[26.1.2](https://github.com/InventivetalentDev/minecraft-assets/tree/26.1.2).
Everything below is quoted or paraphrased from those; nothing is from memory.

**Second source.** [`Snownee/Jade`](https://github.com/Snownee/Jade/tree/26.2-fabric)
at `26.2.11` — a mod that actually compiles on 26.2. Every signature attributed to
it below was read out of its source, not remembered, and the GUI ones have since
been compiled against the real API in CI.

## Why this document exists

> **Superseded in part, and left standing because the reasoning still applies to
> the render path.** When this was written, `common/`, `fabric/` and `neoforge/`
> held five files with **zero** `net.minecraft` imports between them and no
> Minecraft API had ever been compiled here. That is no longer true: those three
> now hold **9 files, 4 of which import `net.minecraft` or `com.mojang`**
> (`SchemaScreen`, `EffectPickerScreen`, `NothingToEditScreen`, `EditorKey` — the
> editor), and CI compiles them against the real 26.2 API on every push. The
> paragraph below is kept in its original tense because it is the argument that
> produced this document, and because it still holds exactly where nothing has
> been compiled yet: the post-effect chain.

`common/`, `fabric/` and `neoforge/` contain five files with **zero**
`net.minecraft` imports between them. No Minecraft API has ever been compiled in
this repository, so there is no existing usage to pattern-match against, and any
first attempt written from recollection would be guesswork checked only by CI.
The list below is short enough to read and specific enough to code against.

## The changes that bear on this project

### Vulkan is real, and the abstraction was right

> For each `com.mojang.blaze3d.opengl` class, there is a parallel in
> `com.mojang.blaze3d.vulkan`.

The `EffectBackend` seam was chosen on the expectation that OpenGL would not be
the only target. That expectation is now a fact in the game itself, and M5
("Vulkan readiness") has something concrete to aim at rather than a rumour.

### Depth comparison is inverted — a migration hazard for the shader corpus

> All pipelines using `DepthStencilState` have inverted depth test values:
> `CompareOp` now uses `GREATER_THAN_OR_EQUAL` instead of `LESS_THAN_OR_EQUAL`.
> Stencil values are now their additive inverses.

That is a reversed-Z depth buffer. It matters here because the shader corpus in
`the-virus-block-mc` reads the depth buffer and does arithmetic on it:

```glsl
float linearizeDepth(float depth, float near, float far) {
    float ndcZ = depth * 2.0 - 1.0;
    return (2.0 * near * far) / (far + near - ndcZ * (far - near));
}
```

That is the conventional-Z formula. Under reversed-Z the buffer holds 1.0 at the
near plane and 0.0 at the far plane, so this returns distances that are wrong —
and wrong in a way that still produces plausible-looking numbers rather than an
error. It appears in `post/include/depth_utils.glsl` and again, separately, in
`post/include/camera/depth.glsl`; anything reading the depth buffer would need
revisiting, which is `magic_circle`, `virus_block`, the god-ray occlusion pass
and the archived `depth_*` family.

**This is not an explanation of any current bug.** The mod targets 1.21.6, where
this change has not happened. It is a hazard for moving that corpus to 26.2, and
it is written down here so the move does not discover it by looking wrong
in-game.

### Getting at the screen has moved

> `Minecraft#screen` → `Minecraft#gui.screen()`
> `Minecraft#setOverlayMessage` → `Gui#hud.setOverlayMessage`

`Gui` now owns the UI surface and `Hud` is a separate class. Any editing screen
this project grows goes through `Gui`, not `Minecraft` — which is precisely the
call a screen written from memory would have got wrong.

### Uniforms and samplers are bound as a group

> `RenderPipeline#getSamplers`, `getUniforms` → `getBindGroupLayouts`
> New class `BindGroupLayout` manages samplers and uniforms separately from
> pipelines.

The layout checker in `core.layout` compares a block's declarations by byte
offset, which is a std140 property and unaffected. What changes is the *binding*
API a backend calls — relevant to M2, not to the checker.

### Render targets carry a format

> `RenderTarget` now requires `GpuFormat`; `RenderTargetDescriptor` uses
> `Vector4fc` for clear colour (replaces `int`).
> `TextureFormat` → `GpuFormat`, renamed three-component style: `RGBA8` →
> `RGBA8_UNORM`.

`core.chain.TargetSpec` currently models a size and nothing else, which matches
the post-effect JSON — vanilla's own targets are `{}`. If a format ever appears
in that JSON, `TargetSpec` is where it goes.

### Immediate-mode vertex rendering is gone

> `MultiBufferSource` is removed entirely… `Tesselator` removed…
> Direct vertex uploads outside vanilla chunk rendering are no longer supported.

Full-screen post-processing passes do not need any of it. Worth knowing before
someone reaches for the familiar idiom.

> **Corrected 2026-08-24, and the correction matters.** The paraphrase above ends
> at the removal and reads as though nothing replaced it. The primer's own sentence
> does not: *"The feature rendering system has been overhauled to **completely
> replace** `MultiBufferSource` and any direct vertex uploads outside of vanilla
> chunk rendering."* There is a replacement, it is named, and a mod can draw
> arbitrary geometry through it. See
> [What replaced immediate mode](#what-replaced-immediate-mode-the-feature-rendering-system)
> below. Read as written, this section would have retired the field renderer before
> it was attempted.

## The post-effect chain hook points, which are still not established

> Renamed from "The Fabric hook points" once the fog hook below turned out to be
> established. The chain is the part that is not.

The primer is a NeoForge document, so how a *Fabric* mod attaches to the
post-processing chain is not in it. That was researched separately and the honest
result is: **not established**, for a reason worth recording.

What is established:

- Fabric has no dedicated post-processing API. Mods mixin, and the usual targets
  are `GameRenderer` and `LevelRenderer` — which is what `the-virus-block-mc`
  does, injecting at `PostEffectPass#render`.
- Those targets moved in 26.2. Per the primer, `LevelRenderer#renderLevel` →
  `render`, the constructor changed significantly, and a new `LevelExtractor`
  took over render-state extraction. Any injection point picked from memory is
  aiming at a signature that no longer exists.

What is not, and the trap in the way of getting it:

Searching for this returns Yarn javadoc — `PostEffectProcessor` in
`net.minecraft.client.gl`, `WorldRendererMixin` injecting into
`WorldRenderer.render()`. All of it is **1.21.x**, and all of it is in **Yarn
naming**, which 26.x does not use. `WorldRenderer` is Yarn's name for what Mojang
calls `LevelRenderer`; the primer's use of `LevelRenderer` is the tell that it is
quoting official names.

So the readily-available documentation for this is doubly wrong for this project:
wrong version, wrong mapping. Copying a signature out of it would compile against
nothing and waste a CI round-trip apiece. Establishing the real entry points needs
the 26.2 sources open — which the build environment cannot reach — or a Fabric mod
doing post-processing on 26.2 to read.

**A Fabric mod on 26.2 was found and read**, which was worth doing even though it
does no post-processing. [`Snownee/Jade`](https://github.com/Snownee/Jade/tree/26.2-fabric)
mixins into `BossHealthOverlay`, `CreativeModeInventoryScreen`, `GuiGraphics`,
`GuiGraphicsExtractor`, `FogRenderer` and `PreparedTextBuilder` — all in Mojang
naming, and two corroborate the primer directly: `PreparedTextBuilder` matches the
claim that `Font#drawInBatch` gave way to `prepareText` → `PreparedText`, and
`GuiGraphicsExtractor` appears exactly where the primer says it does.

So the GUI surface an editing screen would need is readable after all, from a mod
that compiles against it. What is still missing is a 26.2 mod doing
*post-processing* — Jade does not, so the chain hook points remain unestablished.

## The fog hook, which is established

The section above was written as though Jade gave nothing toward M2. It gave one
thing, and the roadmap makes it the important one: **M2's acceptance criterion is
"a hardcoded fog binding visibly changes the frame in-game"**, and Jade mixins
`FogRenderer`.

Read from `snownee/jade/mixin/FogRendererMixin.java` on the `26.2-fabric` branch,
tag `26.2.11`:

| Fact | Value |
|---|---|
| Package | `net.minecraft.client.renderer.fog` — fog has its own package now |
| Class | `FogRenderer` |
| Method | `setupFog(Camera, int, DeltaTracker, float, ClientLevel)` |
| Returns | `org.joml.Vector4f` — the fog colour |
| Carrier | `FogData`, a **local variable** inside `setupFog`, not a parameter |
| Fields read | `FogData#renderDistanceStart`, `FogData#renderDistanceEnd` |

Two details that a signature copied from memory would have got wrong. The package
is new: `net.minecraft.client.renderer.fog.FogRenderer`, not the
`net.minecraft.client.renderer.FogRenderer` the 1.21.x sources show. And `FogData`
is a **local**, which is why Jade reaches it with MixinExtras'
`@Local(name = "fog")` rather than an argument capture — anything wanting to
*change* fog rather than read it has to capture that local, or modify the returned
`Vector4f`.

Its fields are read from another package without an accessor, so they are public.
Jade only reads them; whether writing to them at `TAIL` actually affects the frame
is **not** established by this, and cannot be until it is run.

### Two more signatures, incidentally

From `JadeClient.java` and `overlay/RayTracing.java` in the same checkout — worth
recording because the M2 `WorldState` sampler needs exactly this kind of access:

- `Minecraft#gameRenderer` is still a field, and `GameRenderer#mainCamera()` still
  returns `Camera`.
- `GameRenderer#gameRenderState()` exists and carries `lightmapRenderState`, which
  has `darknessEffectScale`. This is the primer's render-state extraction change
  showing up in a real call site.
- `Minecraft#getDeltaTracker()` → `DeltaTracker#getGameTimeDeltaPartialTick(boolean)`
  is how partial tick is obtained.

### And one corroboration of the version table

Jade's `gradle.properties` pins `loader_version=0.19.3`, which is exactly what
[VERSIONS.md](VERSIONS.md) pins for `mc_26_2_fabric_loader`. Its Fabric API is
`0.152.1+26.2` against our `0.157.0+26.2`; both are valid 26.2 builds and ours is
the newer, so this corroborates the loader pin without contradicting the API pin.

## What M2 still needs before it can start typing

Established now: the fog entry point, camera and render-state access, partial tick.

Still not established: the post-processing **chain** — `PostChain`, `PostEffectPass`
or whatever 26.2 calls them, and where a mod attaches its own passes. Jade does not
touch them, so this still needs either the 26.2 sources or a 26.2 mod that does
post-processing.

That means M2 splits cleanly, and the fog half is the half that is unblocked:

1. **A fog binding through `FogRenderer#setupFog`** — signature known, no chain
   needed, and it is what the milestone's done-when actually asks for.
2. **The full post-effect chain** — still blocked on reading.

Reading is what moved (1) from blocked to unblocked. Nothing here has been run, so
none of it is proven to *work* — only proven to exist, in Mojang naming, on 26.2.

## Sampling the world, and where the per-frame hook goes

Read after the fog section above, from Fabric API branch `26.2`, NeoForge `26.2.x`
and Paper at `mcVersion=26.2`. NeoForge and Paper patch files are diffs against
Mojang-mapped 26.2 source, so a line with no `+`/`-` prefix is verbatim vanilla.

### The hook is `END_EXTRACTION`, not `START_MAIN`

`WorldRenderEvents` no longer exists. Its successor is
`net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents`, and 26.2
splits a frame into an **extraction** phase and a **drawing** phase — everything
needed for rendering is gathered first, then drawn.

That split decides which hook is correct, and the answer is not the obvious one:

| | Fires | Hands you |
|---|---|---|
| `LevelRenderEvents.START_MAIN` | inside the main pass, after the sky is drawn | `GameRenderer`, `LevelRenderer`, `LevelRenderState` |
| `LevelExtractionEvents.END_EXTRACTION` | after render states are extracted, before anything is drawn | the above **plus `ClientLevel`, `Camera`, `DeltaTracker`** |

`END_EXTRACTION` wins twice over. It runs earlier, and its
`LevelExtractionContext` hands over exactly the three objects a `WorldState`
sampler needs, so nothing has to reach for `Minecraft.getInstance()`.

**Fog is computed before `START_MAIN`, provably.** `LevelRenderer#render` takes the
fog as *parameters* — Fabric's own mixin into it declares
`GpuBufferSlice terrainFog, Vector4f fogColor`, and vanilla's call site passes
`cameraState.fogData.color` in. Both are already populated before `render` is
entered, and `START_MAIN` fires inside it. So publishing at `START_MAIN` would have
the fog mixin reading the *previous* frame's values.

**One link is not closed.** The `setupFog` call site appears in no patch context in
any tree, so while extraction demonstrably precedes drawing, whether `setupFog`
belongs to the drawing phase is inferred from its caller rather than proven. If it
turns out to run during extraction, its order against `END_EXTRACTION` is undecided.
Worth confirming with a timestamp at both sites before depending on it.

### Time of day is gone, and it blocks a condition

`Level#getDayTime()` does not exist on 26.2 — **zero occurrences** across Fabric API,
NeoForge, Paper and Jade. It has been replaced by two new packages:

- `net.minecraft.world.clock` — `ClockManager#getTotalTicks(Holder<WorldClock>)`,
  with a client implementation `net.minecraft.client.ClientClockManager` held by
  `ClientPacketListener`
- `net.minecraft.world.timeline` — `Timelines.OVERWORLD_DAY`, whose
  `getCurrentTicks(clockManager)` is the tick-within-day and `getPeriodCount` the day
  number

A dimension names its clock through `level.dimensionType().defaultClock()`.

**What is missing is the client accessor**: no getter reaching `ClientClockManager`
from `Minecraft` or `ClientLevel` was found. Until that is established,
`Condition.TimeOfDay` cannot be evaluated correctly, and a sampler must say so rather
than quietly reporting noon — a binding gated on night that simply never fires is
indistinguishable from a binding that is broken.

`ClientLevel#getGameTime()` exists but is total elapsed ticks, not tick-of-day.

### The rest of a WorldState, verified

| Field | Call | Note |
|---|---|---|
| dimension | `clientLevel.dimension().identifier()` | |
| Y | `camera.position().y` | **not** `camera.getY()` — see below |
| weather | `level.getRainLevel(partialTick)`, `getThunderLevel(partialTick)` | floats; better for fog than the booleans anyway |
| biome tags | `level.getBiome(pos).tags()` → `Stream<TagKey<Biome>>` | |
| submerged | `levelState().cameraRenderState.fogType` | |

Four traps in that table, each of which compiles-then-misbehaves or does not compile
at all:

- **`ResourceLocation` is gone entirely** — it is `net.minecraft.resources.Identifier`
  now. Zero occurrences of the old name across three 26.2 trees against 751 imports of
  the new one.
- **The accessors are asymmetric.** `ResourceKey#identifier()` but
  `TagKey#location()`. One line in NeoForge's `TagsCommand` uses both in the same
  expression. Assuming the rename was uniform gets the tag one wrong.
- **`Camera#getFluidInCamera()` is gone.** Use `cameraState.fogType`
  (`net.minecraft.world.level.material.FogType`, which did *not* move into the new fog
  package). This is reachable from the render state, so it needs no mixin.
- **`Camera#getY()` has no evidence behind it.** The `camera.getY()` that does appear
  in 26.2 source is on an `Entity` field named `camera`, not on
  `net.minecraft.client.Camera`. Use `position().y`.

### `setupFog` returns `FogData` — resolved

This section previously recorded a conflict: Jade's mixin declares
`CallbackInfoReturnable<Vector4f>`, while a NeoForge patch showed `FogRenderer`
building a `FogData` and returning it. Reading the whole patch settles it in
NeoForge's favour, and the resolution is better for us than either guess.

The patch's context lines — verbatim vanilla 26.2 — are the method's own body:

```java
FogData fog = new FogData();
this.computeFogColor(camera, partialTickTime, level, renderDistanceInChunks,
                     darkenWorldAmount, fog.color);
for (FogEnvironment fogEnvironment : FOG_ENVIRONMENTS) {
    if (fogEnvironment.isApplicable(fogType, entity)) {
        fogEnvironment.setupFog(fog, camera, level, renderDistanceInBlocks, deltaTracker);
        break;
    }
}
float renderDistanceFogSpan = Mth.clamp(renderDistanceInBlocks / 10.0F, 4.0F, 64.0F);
fog.renderDistanceStart = renderDistanceInBlocks - renderDistanceFogSpan;
fog.renderDistanceEnd = renderDistanceInBlocks;
return fog;
```

Jade's generic is simply wrong, and harmlessly so: Mixin validates a target's
*parameters* but the `CallbackInfoReturnable` type argument is erased, and Jade never
calls `getReturnValue()`. A shipped mod is evidence about parameters, not about the
generic — which is the general lesson, not a fact about Jade.

**Note the second `setupFog`.** `FogEnvironment#setupFog(FogData, Camera, Level,
float, DeltaTracker)` is a different method that happens to share the name. It is
called *by* the one above. `@Mixin(FogRenderer.class)` disambiguates, but anything
searching by name alone will find both.

### `FogData` has four fields that matter, in two pairs

| Field | Set by | Meaning |
|---|---|---|
| `color` | `computeFogColor(..., fog.color)` | the fog colour |
| `renderDistanceStart` / `renderDistanceEnd` | vanilla, unconditionally, after the environment loop | the view-distance fog |
| `environmentalStart` / `environmentalEnd` | whichever `FogEnvironment` applies | the in-water / in-lava / in-powder-snow fog |

The pairs are not interchangeable, and picking the wrong one is the kind of mistake
that renders as "nothing happened". Jade *reads* `renderDistance*` to learn the view
distance. NeoForge's own fog-modification event exposes the other pair —
`ViewportEvent.RenderFog` maps `environmentalEnd` to the far plane and
`environmentalStart` to the near plane — which is a strong hint that `environmental*`
is the pair a mod changing atmosphere is meant to write.

**Still open, and it needs the game:** which pair mc-shaders should write for a
dimension look. `environmental*` matches the intent and matches what NeoForge exposes;
`renderDistance*` is what vanilla sets last and is unconditional. Both are reachable;
only running it will show which one moves the frame.

### Writing at `TAIL` should reach the frame

Previously unestablished, now a chain of verified links rather than a hope. The
`@Local(name = "fog") FogData` a mixin captures **is** the object returned, and
`GameRenderer` passes `cameraState.fogData.color` into `LevelRenderer#render` — so
what `setupFog` returns is what the frame is drawn with. Mutating it at `TAIL`, after
vanilla has finished filling it, therefore changes what the renderer receives.

That is not the same as having seen it work. It is the difference between an
untested plan and a guess.

### A path that may avoid the mixin

`cameraState.fogData` and `cameraState.fogType` both live on `CameraRenderState`,
and `levelState().cameraRenderState` is reachable from both render contexts. If fog
data can be written during extraction and is read during drawing, the fog mixin is
unnecessary and the frame-lag question disappears with it. That depends on the same
unclosed link above — when `setupFog` runs — so it is a lead, not a plan.

## The GUI API, read out of Jade rather than remembered

Jade's `src/main/java/snownee/jade/gui/` is several real screens compiled against
26.2. Reading them settles what an editing screen needs, and three of the answers
are changes that writing from 1.21 memory would have got wrong — each one a
compile error at best, and at worst code that looks right and overrides nothing.

| From 1.21 | In 26.2 | How it was established |
|---|---|---|
| `Screen#render(GuiGraphics, int, int, float)` | **`Screen#extractRenderState(GuiGraphicsExtractor, int, int, float)`** | `BaseOptionsScreen` declares it `@Override` and calls `super`, so it is the superclass's method |
| `Screen#keyPressed(int, int, int)` | **`Screen#keyPressed(KeyEvent)`**, with `keyEvent.key()` for the code | `BaseOptionsScreen:237`, `@Override` |
| `Minecraft#setScreen(Screen)` | **`minecraft.gui.setScreen(Screen)`** | every call site in Jade goes through `.gui` |

The first is the one that matters. 26.2 has moved GUI drawing to an
extract-render-state model — the same shift the primer describes for the level
renderer, applied to screens — so a `render` override written from memory would
compile as a new method that nothing calls, and the screen would come out blank
with no error anywhere.

What has *not* changed, and can be relied on:

- `Screen(Component title)` as the super constructor, `protected void init()` as
  the place to build the screen.
- `addRenderableWidget(w)` adds a widget and returns it.
- `Button.builder(Component, onPress).bounds(x, y, w, h).build()`.
- `AbstractSliderButton(x, y, width, height, Component, double value01)` with
  `protected void updateMessage()` and `protected void applyValue()` — the value
  is normalised 0–1, and the subclass maps it to its own range.
- `EditBox(font, x, y, width, height, Component)`.
- `CycleButton.builder(nameProvider, initial).withValues(values)`.
- `Component.translatable(key)` and `Component.literal(text)`.
- `onClose()`, `shouldCloseOnEsc()`, `removed()`.

**Jade never overrides a render method on a screen at all.** Its screens are
assembled from widgets in `init()`, and `extractRenderState` is used only for
tooltips. That is worth copying rather than merely noting: a screen built entirely
from widgets does not touch the one part of this API that changed most, so it is
the shape least likely to be wrong.

These were read from source rather than remembered, and are now **compiled**: the
screen and keybind built on every call above went through CI's `Fabric + NeoForge`
job against real Minecraft 26.2 and passed on the first attempt. That is the
difference between strong evidence and proof, and it is worth stating which one
this is — the whole reason for reading Jade rather than typing from memory was
that a wrong signature here costs a CI round-trip apiece, and reading cost none.

The same table is therefore safe to build on. The list of what did *not* change is
proven by the same run.

## What replaced immediate mode: the feature rendering system

**Source.** The same NeoForged 26.2 primer, read again for this specifically, plus a
web search that surfaced the Fabric API version. Quotes below are the primer's words.
Where something is *not* on the page, this says so rather than filling the gap.

This is what a field renderer has to be built on, because `LayerGeometry` produces
`Mesh` — vertices and indices — and the old idiom for getting those onto the screen is
the one that was removed.

### The three things a custom feature needs

> *"three things a required: a `SubmitNode` to represent the data of the submitted
> feature, the `FeatureRenderPhase`(s) to store the `SubmitNode`s and supply them to
> the correct renderer, and the `FeatureRenderer` responsible for rendering the
> submissions."*

`SubmitNode` is *"a node for some submitted element that holds a record of the
submitted data that is used by the specified `FeatureRenderer`."* Two interfaces it
can carry are named: `TranslucentSubmit`, which supplies `distanceToCameraSq()` for
ordering, and `BatchableSubmit`, which supplies `batchKey()` for grouping. The primer
shows a custom `ExampleSubmit` implementing both — **which is the load-bearing fact
here: mods define their own submit types.**

`FeatureRenderPhase` is *"responsible for collecting these submissions and supplying
them to an `$Output` for rendering"* and defines three methods: `submit` — *"what the
`OrderedSubmitNodeCollector#submit*` methods delegate to"* — `isEmpty`, and `sortInto`
*"for passing the submissions to their appropriate group via `$Output#accept`."*

### The renderer's lifecycle

> *"The rendering process is broken into two sections: preparation and execution."*

| Phase | Methods | What happens |
|---|---|---|
| Preparation | `beginPrepare` → `prepareGroup` → `finishPrepare` | *"turning the submitted elements into an intermediate state, usually buffer data stored in the `StagedVertexBuffer`"* |
| Execution | `executeGroup` → `finishExecute` | `executeGroup` is *"for using a `RenderPass` to draw the target color and depth texture"*; `finishExecute` *"for cleaning up the renderer for the next render frame"* |

`StagedVertexBuffer` is where a mod's own vertices go. One constraint is called out
explicitly: *"the only thing that should be stored during `prepareGroup` is the draw
reference (for `StagedVertexBuffer` this is `StagedVertexBuffer$Draw`)."*

Related, and in this project's favour: `VertexFormat` and `VertexFormatElement` were
*"partially rewritten into a more dynamic framework"*, with elements *"constructed when
building the `VertexFormat` by adding attributes via `$Builder#addAttribute`."* A mesh
whose vertex layout is decided by content rather than by a fixed enum has somewhere to
go.

The dispatcher is reached at
`Minecraft.getInstance().gameRenderer.featureRenderDispatcher()`, and
`FeatureRenderDispatcher` *"now takes in a `SubmitNodeStorage` as part of its render
methods."*

### Registration, read out of the Fabric API's own 26.2 source

The primer does not give a mod-facing registration API. It says only: *"Assume we can
inject into the end of the `FeatureRendererDispatcher` constructor and that
`featureRenderers` is accessible."* That is an assumption for the sake of its example,
not an entry point.

**Fabric has a real one**, and it is two calls. Both were read from
`FabricMC/fabric-api` at branch `26.2`, in
`fabric-rendering-v1/src/client/java/net/fabricmc/fabric/api/client/rendering/v1/` —
not from a search summary, and not remembered.

```java
// FeatureRendererRegistry.java
public final class FeatureRendererRegistry {
    public static <T extends SubmitNode> void register(
            FeatureRendererType<T> type, Supplier<FeatureRenderer<T>> renderer)
}

// FabricOrderedSubmitNodeCollector.java
public interface FabricOrderedSubmitNodeCollector {
    default <T extends SubmitNode> void submitCustom(SubmitRenderPhase<T> phase, T node)
}
```

`FeatureRendererRegistry`'s class javadoc ties the two together: *"The registry for
custom `FeatureRenderer`s. Custom feature renderers must be registered during client
initialization for them to be usable with `submitCustom`."*

And `submitCustom`'s own javadoc is the sentence that settles the original question:
*"**Submit an arbitrary `SubmitNode`** with a custom `FeatureRendererType`."* Its
parameters are documented as *"The phase this node will be rendered with"* and *"The
node to render"*, and its type parameter as *"The kind of node we're rendering, either a
`SubmitNode` or `TranslucentSubmit`."* It points at `SubmitRenderPhases` for *"Vanilla's
built-in render phases."*

So on Fabric the shape is: define a `SubmitNode` subtype and a `FeatureRendererType`
for it, implement `FeatureRenderer`, `register` during **client initialisation**, and
call `submitCustom` per frame. Arbitrary geometry is not a workaround here — it is what
the API says it is for.

This repository pins `mc_26_2_fabric_api=0.157.0+26.2`, and the capability arrived in
`0.153.0+26.2`, so **no version bump is needed to start.**

> **A name to not pattern-match onto.** Searching for this repeatedly surfaces
> `LivingEntityFeatureRendererRegistrationCallback` — a 1.20-era API for *entity*
> feature renderers (armour, elytra), which is a different thing. The 26.2 tree makes
> the distinction visible: `LivingEntityFeatureRenderEvents.java` sits in the same
> directory as `FeatureRendererRegistry.java`, as a separate file. Two meanings of
> "feature renderer" live side by side.

### What a `SubmitNode` actually looks like

The primer's own example, quoted:

```java
public record ExampleSubmit(Matrix4fc pose, RenderType type, ...)
    implements TranslucentSubmit, BatchableSubmit {
    @Override
    public FeatureRendererType<? extends TranslucentSubmit> featureType() {
        // The identifier for our feature renderer.
    }

    @Override
    public Object batchKey() {
        // The batch key should be an object that allows the renderer or
        // phase to hold its state for as long as possible before switching.
        return this.type;
    }

    @Override
    public float distanceToCameraSq() {
        // Compute the camera distance.
        return TranslucentSubmit.computeDistanceToCameraSq(this.pose);
    }
}
```

Three things worth noticing. It is **a record**, so it is the same kind of object this
project already passes around. It carries a `Matrix4fc pose` and a `RenderType` — and
`LayerGeometry.Piece` already carries a `Transform` and an `Appearance`, so the mapping
is a conversion rather than a redesign; the mesh goes where the `...` is. And
`distanceToCameraSq` has a supplied implementation,
`TranslucentSubmit.computeDistanceToCameraSq(pose)`, so ordering is not ours to get
right.

### The phase is usually not yours to write

> *"If you are making use of `SubmitNode` or `TranslucentSubmit` with no additional
> sorting behavior, then you can instead make use of `SimpleFeatureRenderPhase` and
> `TranslucentFeatureRenderPhase`, respectively."*

That removes one of the three pieces for the common case. A field layer wants
translucency and has no ordering requirement beyond camera distance, which is exactly
what `TranslucentFeatureRenderPhase` covers. Assume the phase is built-in until
something specific forces otherwise.

### A `FeatureRendererType` is made by a static factory

The registration signature names the type; the primer shows one being built:

```java
public static final FeatureRendererType<ExampleSubmit> TYPE =
        FeatureRenderer.type("examplemod:example_submit");
```

A namespaced id, held in a static. Nothing registry-shaped, and nothing loader-specific
— this is vanilla API, which matters because it means the type and the node can live in
shared code with only the registration call split per loader.

### The NeoForge side, and the pairing in full

Read from `neoforged/NeoForge` at branch `26.2.x`, in
`src/client/java/net/neoforged/neoforge/client/event/`. The earlier 404s were a source-set
mistake, worth naming because it will catch the next person too: NeoForge keeps client
code in a **separate `src/client/` source set**, not under `src/main/`. Fabric API does
the same. Nothing lives at `src/main/java/.../client`.

```java
// RegisterFeatureRenderersEvent.java
public final class RegisterFeatureRenderersEvent extends Event {
    public <S extends SubmitNode> void register(
            FeatureRendererType<S> type, FeatureRenderer<S> renderer)
}
```

*"Fired when a `FeatureRenderDispatcher` collects all `FeatureRenderer`s for mods to
register renderers for custom `SubmitNode`s."* It fires on **`NeoForge.EVENT_BUS`** — the
game bus — and **only on the logical client**, which is the same bus and the same
failure mode already named for the two events in the NeoForge section below: subscribing
on the mod bus produces no error and never runs.

NeoForge also has a per-frame hook purpose-built for this, which Fabric reaches by a
different name:

```java
// SubmitCustomGeometryEvent.java
public final class SubmitCustomGeometryEvent extends Event {
    public LevelRenderState getLevelRenderState()
    public SubmitNodeCollector getSubmitNodeCollector()
    public PoseStack getPoseStack()
    public Iterable<? extends IRenderableSection> getRenderableSections()
}
```

*"This event can be used to submit custom geometry outside of `BlockEntityRenderer`s,
`EntityRenderer`s and Particles. Custom render state used by the submits must be
extracted in `ExtractLevelRenderStateEvent` and stored in the provided
`LevelRenderState`. This event is fired between particle submission and rendering of
opaque submits."*

The whole path pairs, and closely:

| What | Fabric | NeoForge |
|---|---|---|
| Register the renderer | `FeatureRendererRegistry.register(type, Supplier<FeatureRenderer<T>>)`, at client init | `RegisterFeatureRenderersEvent#register(type, FeatureRenderer<S>)`, game bus, client only |
| Submit per frame | `LevelRenderEvents.COLLECT_SUBMITS` → `LevelRenderContext#submitNodeCollector()` | `SubmitCustomGeometryEvent#getSubmitNodeCollector()` |
| Extract render state | `LevelExtractionEvents.END_EXTRACTION` | `ExtractLevelRenderStateEvent` |

One difference, small but real: Fabric takes a **`Supplier<FeatureRenderer<T>>`**, NeoForge
takes the **instance**.

The two submit phases describe the same moment in the frame in almost the same words.
Fabric's `COLLECT_SUBMITS` is *"called after opaque terrain is drawn … and all submit
nodes from entities, block entities, and particles are added to the submit node storage,
and before any submit geometry is drawn"*, and says *"Use this event to add additional
submits to `LevelRenderContext#submitNodeCollector()`."* NeoForge's is *"fired between
particle submission and rendering of opaque submits."*

**The extraction row is the one already built.** Both loaders require custom render state
for a submit to be extracted during the extraction phase — and those are the exact two
events `WorldSampler` already sits on, per [the NeoForge section](#the-neoforge-side-read-from-its-262-source)
below. The per-frame hook this project established for sampling the world is the same
hook a field renderer needs for its state. That is a pairing that already exists in this
repository rather than one to go and build.

### The familiar idiom is not gone, it moved

The last unknown here was how vertices actually reach a `StagedVertexBuffer`. The answer
is that for the common case **you never touch one**, and the code you write is very close
to what a 26.1 mod already looked like:

> *"If your `SubmitNode` contains a `RenderType`, then you can extend
> `RenderTypeFeatureRenderer` instead. `RenderTypeFeatureRenderer` functions similarly to
> the now removed `MultiBufferSource`, where within `buildGroup`, the `VertexConsumer`
> can be obtained from the render type via `getVertexBuffer`."*

```java
public class ExampleFeatureRenderer extends RenderTypeFeatureRenderer<ExampleSubmit> {
    // The unique identifier that represents this feature renderer.
    public static final FeatureRendererType<ExampleSubmit> TYPE =
            FeatureRenderer.type("examplemod:example_submit");

    @Override
    protected void buildGroup(FeatureFrameContext context, List<ExampleSubmit> submits) {
        // For each submit
        for (ExampleSubmit submit : submits) {
            // Get the `VertexConsumer` to write to
            VertexConsumer builder = this.getVertexBuilder(submit.renderType());

            // Write the vertex data
            builder.addVertex(...);
        }
    }
}
```

**`VertexConsumer` was never removed.** `MultiBufferSource` was, and the section above
that quotes its removal is easy to read as though the whole idiom went with it. It did
not: `VertexConsumer` is still how vertices are written, and `RenderTypeFeatureRenderer`
is the thing that hands you one.

Three consequences, and they all shrink the job:

- **One method, not five.** `buildGroup(FeatureFrameContext, List<T>)` replaces the
  `beginPrepare`/`prepareGroup`/`finishPrepare`/`executeGroup`/`finishExecute` lifecycle
  for anything that can express itself as a `RenderType`.
- **`StagedVertexBuffer` becomes an implementation detail.** The question of whether it
  takes a caller-supplied `VertexFormat` stops mattering: the `RenderType` carries the
  format, exactly as it did before 26.2.
- **The precondition is already met.** The base class applies *"if your `SubmitNode`
  contains a `RenderType`"*, and the primer's own example node carries one — as would a
  field submit, since a layer already picks a blend mode.

> **A discrepancy in the source, left as found.** The prose says `getVertexBuffer`; the
> code example says `getVertexBuilder`. One of them is a typo and this document does not
> know which. Whichever compiles is the answer, and finding out costs one CI round.

With that, nothing between here and a first renderer is unread. What remains is a
decision rather than a lookup — see the two consequences below.

> `docs.neoforged.net`, `fabricmc.net` and `docs.fabricmc.net` are all egress-blocked
> from this environment. Everything above came from `github.com` and
> `raw.githubusercontent.com`, which are reachable — go there first.

### What this means for the field renderer

The path exists and is buildable. It is also a different shape from the old idiom: not
"get a `VertexConsumer`, push vertices, done", but a submit node, a phase, and a
renderer with a five-method lifecycle, registered per loader.

Two consequences worth stating before anyone starts:

1. **The loader split is narrow, and now measured.** Both halves are established
   above and they pair row for row. The submit node, the `FeatureRendererType`, the
   `FeatureRenderer` and the mesh-to-buffer work are all vanilla API and belong in
   `vanilla/`; only two calls differ — registering the renderer and reaching the
   per-frame collector. That is the same shape as the fog and GUI paths after all, not a
   departure from them.
2. **Screen-space is a real alternative, not a fallback.** `the-virus-block-mc`'s own
   `field_visual_*` effects are post-processing chains, not tessellated geometry, and
   this repository's chain and shader infrastructure already targets that path. Whether
   fields should be drawn as geometry at all, or raymarched as an effect, is a design
   question this document does not answer — but it is now clear that both are open,
   and only one of them requires the system described above.

## Cross-references

- [ROADMAP.md](ROADMAP.md) M2, M5
- [VERSIONS.md](VERSIONS.md) — the toolchain pins and how each was verified
- [PORTING.md](PORTING.md) — why the two projects target different versions

## The NeoForge side, read from its 26.2 source

Everything above was established against Fabric. NeoForge reaches the same two points
by different means, and the pairing is closer than "nearest equivalent":

| What | Fabric | NeoForge |
|---|---|---|
| Per-frame sample | `LevelExtractionEvents.END_EXTRACTION` | `ExtractLevelRenderStateEvent` |
| Reach the frame's `FogData` | mixin at `FogRenderer#setupFog` TAIL | `ViewportEvent.RenderFog` → `getFogData()` |

`ExtractLevelRenderStateEvent`'s own javadoc says it is *"fired when the LevelRenderer
extracts level render state, **after all vanilla states have been extracted**"* — which
is what `END_EXTRACTION` means — and it exposes `getLevel()`, `getCamera()`,
`getDeltaTracker()` and `getRenderState()`. Those are the same four values Fabric's
`LevelExtractionContext` provides, at the same types: in particular both hand back
`net.minecraft.client.renderer.state.level.LevelRenderState`.

That agreement is what lets `WorldSampler` be one method taking four vanilla
arguments, in `vanilla/`, rather than a copy per loader.

Both NeoForge events fire on **`NeoForge.EVENT_BUS`** — the game bus, per each event's
javadoc — and are client-only. Subscribing on the mod bus produces no error and never
runs, which is the failure mode worth naming.

## Which `FogData` pair — the prior has moved

`FogApply` still writes `renderDistanceStart`/`End`, and the reasoning for it is
unchanged: vanilla sets that pair unconditionally, whereas `environmentalStart`/`End`
is only written when a `FogEnvironment` applies, and ordinary air may have none.

But NeoForge's `ViewportEvent.RenderFog` — the API its ecosystem has used to change
fog for years — names its accessors over the *environmental* pair, verbatim from source:

```java
public float getNearPlaneDistance() { return fogData.environmentalStart; }
public float getFarPlaneDistance()  { return fogData.environmentalEnd; }
public void setNearPlaneDistance(float distance) { fogData.environmentalStart = distance; }
```

So the loader with the longest history of fog-modifying mods treats that pair as *the*
fog-distance API. **That is a prior, not a proof** — it does not dispose of the
argument above, and NeoForge also exposes `getFogData()` mutably, so its own users can
write either pair; it simply names one.

The practical consequence: if fog does not visibly change in game, swapping to the
environmental pair is the first experiment, and because both loaders write through
`FogApply` it is now one line in one file rather than two changes that could drift.

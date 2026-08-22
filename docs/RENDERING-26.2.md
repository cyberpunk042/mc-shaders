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

## Cross-references

- [ROADMAP.md](ROADMAP.md) M2, M5
- [VERSIONS.md](VERSIONS.md) — the toolchain pins and how each was verified
- [PORTING.md](PORTING.md) — why the two projects target different versions

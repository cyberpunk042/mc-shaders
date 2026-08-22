# Rendering on 26.2 — what was confirmed, and what it costs

M2 in the [roadmap](ROADMAP.md) says: *confirm 26.2's post-processing entry
points against real sources — do not write this from memory*. This is that
confirmation, gathered before any Minecraft-facing code was written.

**Source.** NeoForged's [26.1.x → 26.2 migration
primer](https://github.com/neoforged/.github/blob/main/primers/26.2/index.md),
cross-checked against Fabric's 26.2 announcement and the vanilla assets at
[26.1.2](https://github.com/InventivetalentDev/minecraft-assets/tree/26.1.2).
Everything below is quoted or paraphrased from those; nothing is from memory.

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

## What this does not cover

The primer is a NeoForge document. Fabric-specific entry points — how a mod hooks
the post-processing chain, what `ShaderLoader` looks like now — are not in it, and
are **not established here**. That research belongs with the first M2 commit, not
this one.

## Cross-references

- [ROADMAP.md](ROADMAP.md) M2, M5
- [VERSIONS.md](VERSIONS.md) — the toolchain pins and how each was verified
- [PORTING.md](PORTING.md) — why the two projects target different versions

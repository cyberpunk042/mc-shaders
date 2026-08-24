# The state of the-virus-block-mc's shaders

A snapshot, taken 2026-08-22, of what `mcshaders-check` reports against
[`the-virus-block-mc`](https://github.com/cyberpunk042/the-virus-block-mc)'s shader tree.
It exists so that the decisions about porting those effects can be made from evidence
rather than from memory, and so the findings survive the session that produced them.

**It is a snapshot, and snapshots go stale.** Reproduce it with:

```sh
cd check && ../gradlew installDist
./build/install/mcshaders-check/bin/mcshaders-check \
    ../../the-virus-block-mc/src/main/resources/assets
```

Everything below is that tool's output, spot-checked against the files by hand — the
checker had three defects of its own that this exercise found, so its output is not
taken on faith. See the commit *"Make the layout check model std140, after running it on
real shaders"* for what those were.

For what each chain *is* — the sun by version, `magic_circle`, the shockwaves — see
[EFFECTS.md](EFFECTS.md). This page is the state; that one is the catalogue.

## The headline

**21 chains. 19 have errors. Every shader compiles.**

That second sentence is the useful one: `glslangValidator` accepts every shader in the
tree. Nothing here is a syntax problem or a GLSL-version problem. Every failure is a
*wiring* problem — a chain naming a file that moved, or a uniform block the host and the
shader describe differently. Those are cheap to fix and invisible until something looks
wrong on screen.

## Four chains name shaders that are not there

`depth_full`, `depth_passthrough`, `depth_redtint`, `depth_test` each reference
`the-virus-block:post/depth_*`, and those files are in `post/_archive/`. The chains and
the archived files are the same event seen from both ends: they are four of the
27 files no chain reaches, and the four `MISSING_SHADER` errors.

This is a decision, not a repair. Either the shaders come back out of `_archive` or the
four chains go. Nothing can tell you which from the outside.

## Thirteen chains share one wrong block

Every `field_visual_*` chain binds a block called `FieldVisualConfig`, and they all
disagree with the shader in the same two places:

> For how the thirteen *authored* `field_visual` configs line up with that same block —
> a different layer from the drift below — see
> [VIRUS-BLOCK-FIELD-VISUAL-STATE.md](VIRUS-BLOCK-FIELD-VISUAL-STATE.md).


| byte | the shader reads | the host writes |
|---|---|---|
| 264 | `GeoWaveResolution` | `GeoSmoothRadius` |
| 268 | `GeoWaveAmplitude` | `GeoReserved` |
| 376 | `ColorBlendMode` | `ReservedSlot3` |
| 380 | `EruptionContrast` | `ReservedSlot4` |

Four slots, in two pairs. Everything between and after them lines up. This is one
declaration drifting from another — fix it in the one place both sides are generated
from and thirteen chains are fixed at once.

Then the two families diverge:

- **Non-HDR** (`v1`, `v2`, `v3`, `v5`, `v6`, `v7`, `v7_hdr_full`, `v8`, `geodesic`):
  the shader reads 64 more scalar slots than the host writes, starting at byte 672 with
  `FlamesEdge`. Those hold whatever was in the buffer.
- **HDR** (`v5_hdr`, `v6_hdr`, `v7_hdr`, `v8_hdr`): the two part company at byte 672 and
  never line up again, and the host writes 800 bytes into a block the shader declares as
  928. The HDR variants also declare `InSampler` and `DepthSampler` with nothing bound to
  them — eight warnings across the four.

## `magic_circle` is the worst of them

One slot wrong at byte 56, then an unbounded divergence at 92 — and the shader declares
528 bytes while the chain writes 96. **432 bytes of that block are whatever the buffer
happened to contain.** Whatever this effect does on screen, it is not what the shader was
written to do.

## `shockwave_ring` stops less than half way

The shader reads 52 scalar slots from byte 144 on, starting with `ShapeType`, and nothing
writes them.

## `shockwave_glow` reads a target before anything writes it

Its `Mask` input binds the target `mask`, which no earlier pass has written. Legal, and
almost certainly not intended: it samples whatever was in that texture.

## What is *not* a problem

Worth stating, because it is 230 of the report's lines and it looks alarming:

**Reserved slots.** From byte 464 the shaders declare `Reserved29_*`, `Reserved30_*`,
`Reserved_InvViewProj`, `Reserved_ViewProj` — and the chains fill them with camera
position, forward and up vectors, field of view, near and far planes, and two matrices.
The shader has deliberately reserved that space and ignores it. This is how the camera
data is passed. It is reported as INFO and needs nothing.

**Matrices written as loose floats.** The chains spell a `mat4` as sixteen consecutive
`float` entries, because Minecraft's post-effect JSON has no matrix type and no vector
type. Against a shader's `vec4[4]` that is the same sixty-four bytes. An earlier version
of the checker called this 8 type errors and 24 stray writes per block — 416 across the
tree, all false. It does not any more.

## The 27 files no chain reaches

Four are the archived `depth_*` shaders discussed above. Two pairs
(`core/fresnel_entity`, `core/post/shockwave_fullscreen`) are core shaders, which
Minecraft names directly rather than through a chain — expected. The rest are includes
that nothing includes, among them a cluster that only refers to itself:
`sdf_library.glsl` defines `sdf_merge` and `round_merge`, which are also defined in
`sdf/operations.glsl`; the live shaders include the latter. Two files carry a comment
saying they require `sdf_library.glsl` and then do not include it — they work because
their caller includes `sdf/operations.glsl` first.

## Where this leaves the port

Ordered by what they cost to resolve:

1. **The `FieldVisualConfig` drift** is four slots in one shared declaration, and fixing
   it fixes thirteen chains. This is the cheapest large win in the tree.
2. **The unwritten tails** (`field_visual` non-HDR, `shockwave_ring`, `magic_circle`) are
   a host that stopped being extended when the shader was. Each is a decision about
   whether those fields are still wanted.
3. **The `depth_*` four** are a yes-or-no about the archive.
4. **The HDR variants' unbound samplers and unbounded divergence** need the HDR path
   looked at as a whole rather than patched slot by slot.

None of this speaks to whether these effects should reach 26.2 at all — reversed-Z makes
any depth-reading shader a real port rather than a copy, and that is a separate question
from whether they are correct on 1.21.6 today.

# The effects catalogue

The visual work this project exists to carry forward lives in
[`the-virus-block-mc`](https://github.com/cyberpunk042/the-virus-block-mc) as **21
post-processing chains** on 1.21.6. This is what they are, named, so that "the sun" or
"the magic circle" refers to a specific file rather than an idea.

**None of them are in this repository.** Nothing here has been ported, and nothing here
can run one yet — see [what porting them needs](#what-porting-them-needs).

For what the checker reports against them — 19 of the 21 have wiring errors, while every
shader compiles — see [VIRUS-BLOCK-SHADER-STATE.md](VIRUS-BLOCK-SHADER-STATE.md). This
page is the *what*; that one is the *state*.

> **A snapshot**, taken from the chain files themselves. Reproduce the inventory with:
> ```sh
> ls the-virus-block-mc/src/main/resources/assets/the-virus-block/post_effect/
> ```

## The sun — `field_visual_*`

**Thirteen chains, one effect, eight iterations.** This is the sun: a world-positioned
energy field, raymarched in screen space against the depth buffer.

Its parameter block says what it is better than prose can — from the mod's own
`field_visual_param_dictionary.md`:

| Parameter | Role |
|---|---|
| `CenterX/Y/Z` + `Radius` | where the field sits in the world, and how far it reaches |
| `PrimaryColor` | core — the brightest inner colour |
| `SecondaryColor` | edge, at the boundary transition |
| `TertiaryColor` | outer glow, the subtle atmosphere |
| `HighlightColor` | specular hot spots |
| **`RayColor`** | **corona and rays — the external emanations** |

### The versions

Each shader says what it is in its own header. These are not iterations of one effect so
much as **a line of different ones**, and the later they get the more specific they are:

| Chain | What its own header calls it |
|---|---|
| `field_visual_v1` | Basic Raymarched Energy Orb |
| `field_visual_v2` | Shadertoy Energy Orb |
| `field_visual_v3` | Raymarched Energy Orb |
| `field_visual_v5` | Pulsar Projected |
| `field_visual_v6` | **Raymarched Pulsar** |
| `field_visual_v7` | **Panteleymonov Sun** |
| `field_visual_v8` | **Electric Aura** — *"forked from V7 (Panteleymonov Sun). New: electric plasma rays with pulsating rings, ground transposition"* |
| `field_visual_geodesic` | Animated Geodesic Sphere |

So the sun proper is **v7**, v6 is the pulsar it came after, and v8 is v7 forked toward
something electric rather than solar. That lineage is worth knowing before choosing which
one to port: they are siblings, not supersessions, and v8 says so in its own first lines.

**There is no v4.** The sequence goes v1, v2, v3, v5 — nothing named v4 exists anywhere
in the tree.

Each of `v5` `v6` `v7` `v8` also has an HDR twin, which is the same effect with **ray
values above 1.0 preserved** rather than clamped — that is what the god-rays passes
consume:

| Chain | Passes | Fragment shader |
|---|---|---|
| the eight above | 2 | `post/field_visual_*` — effect, then blit |
| `field_visual_v5_hdr` · `v6_hdr` · `v7_hdr` · `v8_hdr` | **7** | `post/hdr/field_visual_*_hdr` — effect → blit → god-rays mask → accumulate → blur → blur → composite |
| `field_visual_v7_hdr_full` | 2 | `post/field_visual_v7` — **see below** |

**The HDR variants are the god-rays pipeline.** Seven passes: the field is rendered, a
mask is built from it, the rays are accumulated, blurred twice, and composited back. That
is where `GOD_RAYS_360_FIX.md` and `GOD_RAYS_ROBUSTNESS_PLAN.md` in that repo apply.

**`field_visual_v7_hdr_full` is not an HDR variant.** Its file is **byte-identical to
`field_visual_v7.json`** — two passes, naming the non-HDR `post/field_visual_v7` shader,
with no god-rays passes at all. The name says otherwise. This is why the checker groups
it with the non-HDR family rather than the HDR one, and it is worth deciding what the
name was meant to mean before porting anything that relies on it.

### What the checker says about them

All thirteen share the **`FieldVisualConfig` four-slot drift** — the same two pairs of
fields, at bytes 264/268 and 376/380, described one way by the shader and another by the
host. One shared declaration; fixing it fixes thirteen chains, and it is the cheapest
large win in the tree.

Past that the families diverge: the non-HDR ones read 64 scalar slots the host never
writes, from `FlamesEdge` at byte 672. The HDR ones part company at the same byte and
never realign — the host writes 800 bytes into a block the shader declares as 928 — and
each declares `InSampler` and `DepthSampler` with nothing bound.

## `magic_circle`

Two passes, its own `MagicCircleConfig` block. **The worst-wired chain in the tree**: one
slot wrong at byte 56, an unbounded divergence from 92, and a shader declaring 528 bytes
against a chain writing 96 — so **432 bytes of that block are whatever the buffer
happened to contain.** Whatever it draws on screen, it is not what the shader was written
to draw.

## `shockwave_ring` and `shockwave_glow`

Two passes each. `shockwave_ring` has its own `ShockwaveConfig` and stops less than half
way through it: 52 scalar slots from `ShapeType` at byte 144 are never written.
`shockwave_glow` carries only `BlitConfig` and samples a target called `mask` that no
earlier pass has written — legal, and almost certainly not intended.

Design notes for these live in that repo's `GPU_SHOCKWAVE_SOLUTION.md` and
`SHOCKWAVE_WORLD_RECONSTRUCTION.md`.

## `virus_block`

Two passes, its own `VirusBlockParams`. The effect the mod is named for.

## `depth_full` · `depth_passthrough` · `depth_redtint` · `depth_test`

Four depth-visualisation chains, each naming a `post/depth_*` shader that now lives in
`post/_archive/`. They are the four `MISSING_SHADER` errors and four of the 27 files no
chain reaches — the same event from both ends.

**This is a decision, not a repair.** Either the shaders come back out of the archive or
the chains go, and nothing can tell you which from the outside.

## What porting them needs

Two things, neither of which is effort:

**A backend that runs vanilla-style chains.** None of these can execute here. The offline
half is built and tested — include resolution with `#line` mapping, the chain and pass
model, std140 layout, and the checker over all three — but the runtime that executes a
chain does not exist, because the 26.2 entry points have not been read out of any source
that compiles on 26.2. See [SHADERS.md](SHADERS.md#can-i-bring-my-own-shaders).

**Reversed-Z.** Every one of these reads the depth buffer. 26.2's depth convention makes
any depth-reading shader a real port rather than a copy, and that is a separate question
from whether they are correct on 1.21.6 today.

## Not to be confused with the geometry engine

[SHAPES.md](SHAPES.md) covers `core`'s shapes, fields and tessellation — a different
mechanism entirely. These chains are **fullscreen post-processing**: they read the frame
and the depth buffer and march a field in screen space. The geometry engine builds
meshes. A sun here is a raymarched field; a sphere there is triangles. Neither is
currently drawn by this repository.

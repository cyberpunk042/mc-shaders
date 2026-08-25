# The state of the-virus-block-mc's field-visual content

A snapshot, taken 2026-08-24, of how the thirteen authored `field_visual` configs line
up with the `FieldVisualConfig` uniform block their shaders read.

It exists because the geometry-versus-post-processing question had evidence on only one
side. [VIRUS-BLOCK-FIELD-STATE.md](VIRUS-BLOCK-FIELD-STATE.md) measures the `field_*`
content against this repository's field model: 62 of 64 shape files read *and* build.
Nothing measured the other path. This is that measurement, and it deliberately stops at
measuring.

## Where this sits among the other three

Four documents now cover this ground, and they answer different questions:

| Document | The question it answers |
|---|---|
| [EFFECTS.md](EFFECTS.md) | What each chain *is* — the catalogue |
| [VIRUS-BLOCK-SHADER-STATE.md](VIRUS-BLOCK-SHADER-STATE.md) | What `mcshaders-check` reports about the chains and their blocks |
| [PORTING.md](PORTING.md) | What porting them would cost, and where the block declarations disagree |
| **this page** | Whether the *authored content* fits the block it feeds |

`PORTING.md` names **three** declarations of `FieldVisualConfig` — the GLSL block, the
`FieldVisualUBO` Java record, and the pipeline JSON — and establishes that they describe
a *layout*. The thirteen config files are a fourth thing that is not a layout at all:
they are the **values**. No page covered them, which is why the block was well understood
and the content was not.

## The block

`shaders/post/include/core/field_visual_base.glsl` declares one
`layout(std140) uniform FieldVisualConfig` containing **200 `float` members and 2 `mat4`,
928 bytes.** `PORTING.md` counts the same block as 208 entries, which is this counting
each matrix as four slots — the two agree.

Only the 200 floats are in scope below. The matrices are transforms, not authored scalars.

## The headline

**74 of 78 authored parameters land on a block member, and all 15 colour components do.**

| | count | |
|---|---:|---|
| config files | 13 | 11 `ENERGY_ORB`, 2 `GEODESIC` |
| distinct `params` keys | 78 | |
| → land on a block member | **74** | matched case-insensitively on name |
| colour arrays | 5 | `primaryColor`, `secondaryColor`, `tertiaryColor`, `highlightColor`, `rayColor` |
| → components landing | **15 / 15** | rgb each; the block's alpha slots go unsupplied |

The files are loaded — `FragmentRegistry` carries a `FIELD_VISUAL_FOLDER` constant for
them. Note that the same class's javadoc lists fourteen config folders and `field_visual`
is not among them, so the list is stale; the constant is the truth.

## The four that do not land

**Two are verified, and are not gaps at all.** `showCorona` and `showExternalRays` are
booleans packed into a single `RayCoronaFlags` float, and the shaders unpack them
themselves:

```glsl
float showExternalRays = mod(rayCoronaFlags, 2.0) >= 1.0 ? 1.0 : 0.0;   // bit 0
float showCorona       = mod(rayCoronaFlags, 4.0) >= 2.0 ? 1.0 : 0.0;   // bit 1
```

That is read out of `field_visual_v2.fsh` and `field_visual_v3.fsh`, not inferred from
the names. A port has to preserve the packing, not the two keys.

**Two are unexplained.** `animationSpeed` and `previewRadius` have no reader anywhere in
the mod's 1,040 Java files. The block has an `AnimSpeed` and a `Radius`, and it is
tempting to call these renames — but `animationSpeed` appears in Java only inside
`ShockwaveTypes`, which is a different effect, and `previewRadius` appears nowhere at
all. They may be dead keys, or read somewhere this search did not reach. **Recorded as
unexplained rather than resolved**, because guessing a mapping is exactly how a value
ends up in the wrong slot.

## The 111 members the content never supplies

That number wants context before it reads as a hole:

| count | what they are |
|---:|---|
| 30 | `GodRay*` — a feature family no config file uses |
| 26 | `Reserved*` / padding — not real members |
| 24 | other configurable slots, left at their defaults |
| 19 | `V8*` — a feature family no config file uses |
| 7 | host-computed per frame: `CenterX/Y/Z`, `Radius`, `Phase`, `EffectType`, `Version` |
| 5 | alpha channels; the content supplies rgb only |

So 49 of the 111 are two unused feature families and 26 are padding. The seven
host-computed ones are *correctly* absent — a config file has no business declaring the
camera-relative centre.

The 24 "other" in full: `AnimSpeed`, `TimeScale`, `ShapeType`, `NoiseBaseLevel`,
`TransRotationX`, `TransRotationY`, `LightBackLight`, `TimingSceneDuration`,
`TimingCrossfade`, `TimingLoopMode`, `TimingAnimFrequency`, `VignetteAmount`,
`VignetteRadius`, `TintAmount`, `DistortionSpeed`, `BlendMode`, `FadeIn`, `FadeOut`,
`RayCoronaFlags`, `ColorBlendMode`, `EruptionContrast`, `V2CoreRadiusScale`, `CamMode`,
`DebugMode`.

`RayCoronaFlags` is in that list because the content supplies its two *bits* rather than
the slot. `ColorBlendMode` and `EruptionContrast` are two of the four slots
`VIRUS-BLOCK-SHADER-STATE.md` reports as drifted between shader and host — a different
defect at a different layer, noted here only so the overlap is not mistaken for one
finding seen twice.

## What this does not say

- **It is a name correspondence, not a behaviour.** Nothing here shows a value would
  render correctly, only that a slot exists with that name. The byte-offset drift
  `PORTING.md` documents is a separate question at a separate layer, and this
  measurement would look identical whether that drift were fixed or not.
- **It does not say this repository can consume any of it.** `mc-shaders` ships no
  schema for `ENERGY_ORB` or `GEODESIC`; `energy_orb` appears only in core's tests and
  in `USING_AS_A_LIBRARY.md`, as an example. `SchemaRegistry` is a registry you fill,
  and nothing fills it with these.
- **Thirteen files is a small sample.** Two effect types, and eleven of the thirteen are
  the same one.

## Reproducing it

Requires a checkout of `the-virus-block-mc` beside this one. Nothing from that repository
is vendored here and nothing should be — `PORTING.md` calls embedding CC content in an
MIT artifact *"a design error, not a packaging detail."*

```sh
cd ../the-virus-block-mc && python3 - <<'EOF'
import re, json, glob, collections
src = open('src/main/resources/assets/the-virus-block/shaders/post/include/core/field_visual_base.glsl').read()
body = re.sub(r'//.*', '', re.search(r'FieldVisualConfig\s*\{(.*?)\n\};', src, re.S).group(1))
fields = re.findall(r'\bfloat\s+([A-Za-z_]\w*)\s*;', body)
params, colors = collections.Counter(), collections.Counter()
for f in sorted(glob.glob('config/the-virus-block/field_visual/*.json')):
    d = json.load(open(f))
    params.update((d.get('params') or {}).keys())
    colors.update(k for k in d if k.endswith('Color'))
fieldset = {f.lower() for f in fields}
expanded = {((c[:-5] if c[:-5] != 'ray' else 'raycolor') + ch).lower()
            for c in colors for ch in 'rgb'}
print(f"block: {len(fields)} float members")
print(f"params landing: {sum(p.lower() in fieldset for p in params)}/{len(params)}")
print(f"colour components landing: {sum(e in fieldset for e in expanded)}/{len(expanded)}")
print("unmatched:", sorted(p for p in params if p.lower() not in fieldset))
EOF
```

## What was built from it

`FieldVisualSchemas` in `common` turns this measurement into two `EffectSchema`s — 62
parameters in 19 groups for `ENERGY_ORB`, 13 in 4 for `GEODESIC` — so an editor has
something to render. It closes the "no schema for these types" row in the table below.

**Nothing registers them.** `BuiltinEffects` claims `mcshaders:fog` "and nothing else",
and these are not this mod's effects; the effect type is an argument, and whoever owns
the type registers it in their own namespace.

**The presets are the authority, not the comments.** Every bound widens to admit every
value the presets use, because the block's documented ranges are contradicted by its own
content — `coreSize` says `0-1` and reaches 10, `intensity` says `0-2` and reaches 4.32.
`geoAnimMode` is the same in another form: four modes enumerated, a fifth value shipped,
which is why it is a slider and not a choice.

That rule is easy to get half-right, and the first draft did. It widened only where a
range was stated, so `v2CoronaBrightness` — comment default `0.15`, three presets at
`1.0` — came out bounded `[0, 0.3]`. Nothing about the file looked wrong;
`FieldVisualSchemasTest` caught it against the real presets.

Where each bound comes from: **4** the block's comments, **31** the observed spread
across presets, **40** placeholders spanning `[0, 2v]` where every preset agrees on one
value and there is therefore no evidence of a limit at all.

**Labels are mechanical on purpose.** The block describes 149 of its members and those
would read better than `"Core Size"`. That mod is CC BY-ND-NC — *NoDerivatives* — so the
numbers extracted from those comments are used and the sentences are not. The same
author owns both repositories, so richer labels are theirs to permit.

**One gap this surfaced in our own API:** `ControlKind` has no free-numeric-entry option.
A scalar of genuinely unknown range cannot be described honestly, because `SLIDER` with
`Bounds.NONE` renders as a control from 0 to 0 — which is why 40 parameters carry a
placeholder span instead. A `NUMBER` kind would fix it, and is an API change rather than
a correction.

## What it is evidence for

The open question is whether fields in this repository are geometry or post-processing.
Both halves now have numbers, and they are not the same kind of number:

| | geometry path | post-processing path |
|---|---|---|
| content that loads | 62/64 shapes read, **62 built**, 0 empty | 74/78 params land, 15/15 colours |
| what already exists here | `LayerGeometry`, tessellators, the 26.2 submit path researched | chain infrastructure; **no schema for these effect types** |
| what already exists there | a mod that never applied `radiusMatch` | 13 chains, a 200-member block, 13 authored configs |
| what is blocking | five format decisions; four of seven directories read 0 | the block drift, and a schema nobody has written |

**This page does not choose between them, and neither number is a recommendation.** The
geometry side is further along in this repository; the post-processing side is further
along in the one the content comes from. That is the shape of the decision, and it is
the operator's.

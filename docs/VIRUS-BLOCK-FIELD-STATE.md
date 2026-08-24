# The state of the-virus-block-mc's field content

A snapshot, taken 2026-08-24, of how much of
[`the-virus-block-mc`](https://github.com/cyberpunk042/the-virus-block-mc)'s field
configuration this engine can currently read. It is the field-side companion to
[VIRUS-BLOCK-SHADER-STATE.md](VIRUS-BLOCK-SHADER-STATE.md), and exists for the same
reason: so the decisions about porting that content can be made from evidence rather
than from memory, and so the findings survive the session that produced them.

`docs/PORTING.md` states the requirement this measures — *"the engine must be able to
load content it does not contain"* — and the shader half of it has had
`mcshaders-check` since 2026-08-22. The field half had nothing. This is the first
measurement of it.

**It is a snapshot, and snapshots go stale.**

## How it was measured, and why there is no command to give you

Each content file was wrapped into the slot of a `FieldLayer` it belongs to — a
`field_fills/` file into a primitive's `fill`, a `field_masks/` file into its
`visibility` — and put through `FieldCodec.read`, and whatever came back
was then put through `LayerGeometry.build`. The carrier layer was written by
`FieldCodec` itself so it could not be the thing at fault. Two early runs reported `0`
across the board because the carrier *was* at fault; that is why it is built from the
model now, and why the numbers below were re-taken after each correction.

Reproduce every number on this page with:

```sh
./gradlew :common:test --tests '*FieldContentScanTest' \
    -Dmcshaders.fieldContent=../the-virus-block-mc/config/the-virus-block
```

`FieldContentScanTest` is skipped without that property, which is every CI run, because
the content is not in this repository. It asserts that it *looked* rather than what it
found: the counts belong to somebody else's tree and change when they edit it, so
failing on a number would make it a tripwire on a repository we do not control. It does
fail on finding nothing, because a wrong path reporting zero everywhere is
indistinguishable from content that is entirely unreadable — which is how the first runs
of this scan misled its author.

There is deliberately no `mcshaders-fields` **command**, because **where one would live
is an open question, not an oversight.** `check/` is an included build precisely so it
needs no Minecraft toolchain — `settings.gradle.kts` says a shader pack should be
validatable "in someone else's CI, by someone who has no interest in building this
mod", and the same argument applies here. But `FieldCodec` lives in `common/`, which is
a subproject of the root build, and an included build cannot depend on a subproject.
Resolving that means either moving the field codec or making `common` an included
build. A test needs none of that decided, which is why the reproduction above is one.

Nothing from that repository is vendored here, and nothing should be: `PORTING.md` is
explicit that the configs are CC content while this repository is MIT, and that
embedding one in the other is *"a design error, not a packaging detail."* The checker
already reads content it does not own; this follows it.

## The headline

**Of the seven directories measured, two load completely, one loads in part, and four
load nothing at all.**

| directory | files | read | built | what stops the rest |
|---|---:|---:|---:|---|
| `field_masks` | 25 | **25** | **25** | — |
| `field_shapes` | 64 | **62** | **62** | two files that are not shape documents (below) |
| `field_fills` | 15 | **3** | **3** | two distinct causes, six files each |
| `field_animations` | 26 | 0 | 0 | `skipUnless` is not honoured on read |
| `field_appearances` | 16 | 0 | 0 | `"@primary"` palette references |
| `field_arrangements` | 62 | 0 | 0 | the file's keys are not the record's |
| `field_links` | 9 | 0 | 0 | the file's keys are not the record's |

Fifteen directories were **not** measured — `field_beams`, `field_force`, `field_orbits`,
`field_presets`, `field_profiles`, `field_visual`, `field_follows`, `field_predictions`,
`field_shockwave`, `dimension_profiles`, `effect_palettes` and the four that hold only a
README. Most have no single core record to map onto, which is itself worth knowing.

The useful sentence is the first row. `field_masks` reads 25 of 25 with no special
handling at all, which means the format and the model do agree where nobody has
diverged. Every failure below is a specific, nameable divergence rather than a general
incompatibility.

## Reading is not the hard part any more

**`built` is `read`, in every row.** Every file the codec accepts,
`LayerGeometry.build` turns into pieces, and not one of them produces only empty meshes.
The second column was added because the first had stopped being the interesting one:
`LayerGeometry` existed and was tested, but only against fixtures this repository wrote
itself, and content it has never seen is the whole question.

That is a real result and it is also a narrow one. Three things it does **not** say:

- **It is not a claim about correctness.** The scan counts what threw and what came back
  empty. A mesh with the wrong number of vertices in it passes both.
- **It cannot see a substitution.** Two of the 64 shape files ask for the `TYPE_A` and
  `TYPE_E` sphere algorithms, and those two do not have a mesh form at all — the
  tessellator logs `use direct rendering. Falling back to LAT_LON for mesh` and hands
  back a lat-lon sphere. Non-empty, no exception, different shape. Counting it as built
  is accurate and misleading at the same time, which is why it is written down here
  rather than left in a log line nobody reads.
- **It says nothing about the four directories that read nothing.** Zero files built out
  of zero files read is not evidence of anything.

The build ran with `RadiusPolicy.IGNORE`, because a measurement must not decide the thing
it is measuring: any other policy would be this scan inventing how `radiusMatch` resizes
a shape, which is one of the open questions below.

## `field_shapes`: 62 of 64, and it was 0 of 64 yesterday

Before the fix in *"Let a layer file leave out what the record already defaults"*, this
was **0 of 64** — 62 of them failing on `missing 'transform'`, which is a key that
belongs to the *layer*, not to any shape. `FieldLayer` carried no omission directives,
so nothing hand-written could be read at all. That commit has the detail.

The two that still do not read are content, not codec:

- **`smooth_sphere.json`** carries no `type`. It is `{radius, latSteps, lonSteps,
  algorithm}` — a *fragment* meant to be applied to an already-chosen shape, which is
  the use case `@JsonField`'s `aliases` documents. It is not a standalone shape
  document and should not be read as one. Read against a sphere anyway, it fails a
  second time on `"algorithm": "ICOSPHERE"`, which is not one of the five names core
  accepts — `ICO_SPHERE` is, and matching is case-insensitive, but the underscore is
  not optional. That is a content-side value core never had, and it is the only one in
  the tree.
- **`wide_disc.json`** names `"disc"`. **The source mod's own `ShapeTypeAdapter` does
  not handle that either** — its switch has eight cases (`sphere`, `ring`, `prism`,
  `cylinder`, `polyhedron`, `jet`, `rays`, `molecule`) and `disc` is not among them.
  Its fields (`radius`, `innerRadius`, `y`) are a ring's. Stale content.

Neither is a reason to change anything here.

## `field_fills`: two separate causes, six files each

| | files | cause |
|---|---:|---|
| read | 3 | `fill_nodepth`, `fill_solid`, `fill_solid_doublesided` — explicit depth, no cage |
| fail | 6 | `no way to read a CageOptions` |
| fail | 6 | `missing 'depthTest'` |

**The cage six.** `CageOptions` is a sealed interface with seven implementations, and
the codec's polymorphism covers only `Shape` and `Primitive`. The real cage objects
carry **no discriminator at all** — `{"latitudeCount": 8, "longitudeCount": 16,
"showEquator": true, "showPoles": true}` — so which implementation that denotes is a
format decision, not a gap to close mechanically. The candidates: take it from the
sibling shape's type (the interface's own javadoc says "each shape type has its own
implementation", and a sphere cannot have a torus cage); infer it from which fields are
present; or require an explicit `type` and migrate the content.

**The depth six.** `FillConfig.depthTest` and `depthWrite` carry no omission directives,
and **every file that omits one omits both** — so annotating only `depthTest` moves
nothing. `depthTest` is `true` in all six of the record's constants, which makes its
default obvious. `depthWrite` is not: `SOLID` sets it `false` and every line-based
constant sets it `true`, so its default is *conditional on `mode`*, which the annotation
vocabulary has no way to express. All six of the omitting files happen to be `WIREFRAME`
or `CAGE`, so a flat `true` would read this corpus correctly and quietly misread a
future solid fill.

## `field_animations`: a decision the codec already made, meeting content

All 26 fail, and the reason is recorded in `FieldCodec`'s own source:

> `skipUnless` is deliberately NOT honoured. It says when a value may be left out and
> never what it was, and the model's "inactive" values are populated records rather than
> nulls — `Animation.NONE.spin()` is a full `SpinConfig`.

That was the right call for round-tripping. Its consequence, visible only against
hand-written content, is that a file may not omit any of `Animation`'s twelve config
slots — and `alpha_flicker.json` omits eleven of them, which is exactly what
`skipUnless` was for.

An asymmetric reading would resolve it: the **writer** keeps emitting everything, so
round-tripping and data loss are untouched, while the **reader** accepts absence.
Omission only ever originates in a file a person wrote, where there is no prior value to
lose. What it should supply is the open part, and the model gives two different answers:

- Every one of the twelve types has a `NONE` constant, and `NONE.isActive()` is `false`
  for all twelve — verified reflectively, not assumed.
- But `Animation.NONE` itself holds `<Type>.NONE` for only **three** of them (`spin`,
  `pulse`, `alphaPulse`) and plain `null` for the other nine.

So "absent → `Type.NONE`" is uniform and always inactive, but a file omitting every key
would *not* equal `Animation.NONE` — which matters, because `SimplePrimitive.animation`
is annotated `skipIfEqualsConstant = "Animation.NONE"`. "Absent → what `Animation.NONE`
holds" reproduces it exactly and is inconsistent between the three and the nine. Both
are defensible; they differ observably.

## `field_appearances` and the palette

All 16 fail on `expected an object`, and the file explains itself:

```json
{ "name": "Bright", "color": "@primary", "alpha": 1.0, "brightness": 1.5 }
```

`"@primary"` is a **reference into a palette** — `effect_palettes/` holds them, and the
source mod resolves it through a `ReferenceResolver`. Core's `Appearance` expects a
colour object. This is not a naming mismatch or a missing directive; it is a content
feature that has no counterpart here. The same file also shows `$primitives/…` path
references in `field_primitives/README.md`, so the reference syntax is broader than
colour.

## `field_arrangements` and `field_links`: the model moved

These fail on key names, and the divergence is not cosmetic.

`field_links` files say `"primitiveId"`; core's `PrimitiveLink` calls it `target`. That
much is what `@JsonField`'s `aliases` exists for. But the same files say
`"mirror": false` where core's `mirror` is an `Axis` — **a type change, not a rename** —
so an alias alone would not read them. These files also carry `radiusMatch`, which is
the link flag `docs/USING_AS_A_LIBRARY.md` already records as resolving correctly and
then changing nothing on screen.

`field_arrangements` files say `quad` / `segment` / `sector`; `ArrangementConfig` wants
`defaultPattern` and a set of per-part slots. 62 files, one shape of mismatch.

## Where this leaves the port

Ordered by what they cost to resolve:

1. **`field_animations` (26 files)** is the cheapest large win and needs only a choice
   between two spelled-out options, both of which leave the writer alone.
2. **`field_fills` depth (6 files)** needs one decision — accept a flat `depthWrite`
   default that is wrong for solid fills, or give the annotation vocabulary a
   conditional default.
3. **`field_fills` cage (6 files)** needs the discriminator question answered before any
   code is written.
4. **`field_links` (9 files)** is an alias plus a type change, so it is a content
   migration or a model change, not an annotation.
5. **`field_arrangements` (62 files)** is the largest count and the least examined; the
   mismatch is systematic enough that it wants looking at as a whole.

None of these are blocked on effort. All five are blocked on a decision about what the
file format should say, which is why this page states them rather than picking.

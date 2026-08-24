# Shapes, fields and what you can build

This is the geometry half of the project. [SHADERS.md](SHADERS.md) covers looks applied
to the whole screen — fog, colour grading, post-processing. This page covers **things
with a form**: a beam, a cage, a ring, a shell.

For the *effects* — the sun, the magic circle, the shockwaves, by name and version — see
[EFFECTS.md](EFFECTS.md). Those are post-processing chains and a separate mechanism.

> **Read this first.** Everything below is **built and tested — and nothing draws it
> yet.** The shapes tessellate, the models compose, the maths is under test. But no
> renderer outside `core` consumes a `Mesh`, so a field you build today is a model in
> memory, not pixels. That is stated here rather than discovered after you have built
> one. See [what is missing](#what-is-missing).

## The catalogue

**16 shapes**, every one of them reachable by name and producing real geometry:

| | |
|---|---|
| **Round** | `sphere`, `capsule`, `torus`, `ring`, `cylinder`, `cone` |
| **Angular** | `cube`, `prism`, `tetrahedron`, `octahedron`, `icosahedron`, `dodecahedron` |
| **Directional** | `beam`, `jet`, `rays` |
| **Composite** | `molecule` |

```java
Shape ring = ShapeRegistry.create("ring", Map.of());
Mesh mesh = Tessellator.tessellateAuto(ring);
```

Every name in that table is checked to produce indexable geometry through that exact
call. The check reads the list from the registry rather than restating it, so a shape
added without wiring it up fails a test instead of silently drawing nothing — which is
precisely what six of them were doing until recently.

## The layers you compose

A shape is the form. Four other models describe everything else, and a **primitive** is
one of each bound together:

| Layer | What it decides | Some of what is there |
|---|---|---|
| **Shape** | The form | the 16 above, each with its own parameters |
| **Fill** | Solid, hollow, or a cage | per-shape cage options — sphere, cylinder, cone, prism, ring, torus, polyhedron |
| **Appearance** | Colour and transparency | gradients and direction, colour sets, colour distribution, alpha ranges, glow |
| **Arrangement** | Which cells draw, and how their vertices order | 63 patterns across five cell types, plus generated shuffles |
| **Animation** | How it moves | spin, pulse, wave, twist, wiggle, precession, travel, lifecycle stages, easing |

A **field layer** groups primitives so they move, fade and resolve together.

There is also an **energy** model — flicker, travel, radiative interaction — and a
**transform** stack, and **visibility masks** for hiding parts of a form.

## Two worked examples

> **These are geometry, not the effects of the same name.** `the-virus-block-mc` has real
> chains called `field_visual_v6`, `field_visual_v7` and `magic_circle`, and they are
> **fullscreen post-processing** — a field raymarched in screen space against the depth
> buffer. They are catalogued in [EFFECTS.md](EFFECTS.md). What follows builds the same
> *ideas* out of meshes, which is a different mechanism with a different look. Neither is
> a port of the other, and neither is drawn yet.

Composed rather than named: no type here is a sun or a circle, and that is the point —
the catalogue is small and the combinations are not. **Both examples are executed by a
test**, so they assemble and tessellate rather than merely look plausible.

### A glowing core with a corona

A glowing, pulsing core with a corona of rays turning slowly around it.

```java
SphereShape body = SphereShape.of(1.0f);
Primitive core = SimplePrimitive.of("core", body.getType(), body)
        .withAppearance(Appearance.glowing("#ffd27f", 0.9f))
        .withAnimation(Animation.pulse(0.06f, 0.4f));

RaysShape flares = RaysShape.EMISSION;
Primitive corona = SimplePrimitive.of("corona", flares.getType(), flares)
        .withAppearance(Appearance.glowing("#ff9c3f", 1.0f))
        .withAnimation(Animation.spin(0.01f));

FieldLayer sun = FieldLayer.of("sun", List.of(core, corona));
```

`RaysShape` also ships `ABSORPTION`, `LASER_GRID` and `PULSE` presets, so the same two
lines give you an implosion or a beam array instead.

### Counter-turning rings

Concentric rings turning against each other — the counter-rotation is what makes it read
as a sigil rather than a hoop.

```java
RingShape outerRing = RingShape.at(1.40f, 1.50f, 0f);
Primitive outer = SimplePrimitive.of("outer", outerRing.getType(), outerRing)
        .withAppearance(Appearance.glowing("#8fd3ff", 0.8f))
        .withAnimation(Animation.spin(0.02f));

RingShape innerRing = RingShape.at(0.70f, 0.78f, 0f);
Primitive inner = SimplePrimitive.of("inner", innerRing.getType(), innerRing)
        .withAppearance(Appearance.glowing("#c9a2ff", 0.8f))
        .withAnimation(Animation.spin(-0.035f));   // counter-turning

FieldLayer circle = FieldLayer.of("magic_circle", List.of(outer, inner));
```

Add a third ring, give the rings an arrangement pattern so only some segments draw, and
you have a runic band — that is the same two-line shape with different constants.

## What is missing

Stated plainly, because the gap is specific rather than general:

**No renderer consumes a `Mesh`.** Nothing in `common`, `vanilla`, `fabric` or
`neoforge` references the type. The geometry is produced and then has nowhere to go.
What is needed is a backend that takes a tessellated mesh and draws it — the same kind
of gap described for post-processing chains in
[SHADERS.md](SHADERS.md#can-i-bring-my-own-shaders).

**Fields are not authorable as data yet.** Bindings — the fog looks in
[BINDINGS.md](BINDINGS.md) — are datapack JSON. Fields are Java only; the model was
deliberately kept free of serialisation on the way across, so the codec layer above it
is still to be written.

What *is* written is its specification. The model carries `@JsonField` on 326 fields
across 36 types — names, aliases, and the conditions under which a value may be omitted
— and every one of those directives is now checked to resolve against the code it
describes. That does not make fields loadable; it means the spec a codec would implement
is known-good rather than assumed, which was not true before.

**Some link semantics are unsettled.** Primitives can link to one another, and what a
link like `radiusMatch` should mean *per shape type* — one radius for a sphere, inner
and outer for a ring, radius and top for a cylinder — has not been decided. The examples
above use no links for that reason.

## Where this came from

The shape, mesh, pattern, animation and field packages were ported from
`the-virus-block-mc`, where they had no tests. What exists here is that geometry code
plus the invariants worth having on it: no index past the end of a vertex list, no NaN
coordinate, no point outside the bounds the shape declares. See
[PORTING.md](PORTING.md).

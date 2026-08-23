# `datapack/` — the mod's own data

Shipped by both loaders, authored once here.

## Why it is not in `common/`

`common` is bundled into each loader jar as a **library** (`include` on Fabric,
`jarJar` on NeoForge). A jar-in-jar library is not loaded as a mod, so its `data/`
directory is never read as a datapack — the classes come across, the data does
not. Datapack content has to sit in a jar the loader treats as a mod, which means
each loader's own resources.

Rather than keep two copies in step by hand, this directory is added as a
resources source directory by both loader builds. One copy, shipped twice.

## What is in it

| File | What it is |
|---|---|
| `data/mcshaders/dimension_type/beyond.json` | How the dimension behaves and how vanilla renders it |
| `data/mcshaders/dimension/beyond.json` | The dimension itself: which type, and what generates its terrain |
| `data/mcshaders/mcshaders/bindings/beyond_depths.json` | Fog that closes in below Y 48 in `mcshaders:beyond` |
| `data/mcshaders/mcshaders/bindings/overworld_weather.json` | The Overworld: pale and far normally, closing in during a storm |
| `data/mcshaders/mcshaders/bindings/overworld_forest.json` | A green-tinted haze in forest biomes |

**`mcshaders:beyond` is a placeholder name.** It is the demo dimension behind the
Ancient City frame; rename it whenever there is a better one — two files and one
string.

## Where the field names came from

Not from memory. `dimension_type` changed shape in 26.x: the flat `bed_works` /
`piglin_safe` / `effects` fields are gone, replaced by an `attributes` map keyed by
namespaced ids, plus new top-level `skybox`, `cardinal_light` and `timelines`.

Every field and every non-visual value here was copied from vanilla's own
`the_nether` at the `26.2` ref of the [mcmeta](https://github.com/misode/mcmeta)
vanilla-data mirror. Only these were chosen rather than copied:

- `ambient_light`, and the four `minecraft:visual/*` values — the look
- `coordinate_scale: 1.0` — no distance compression, unlike the nether's 8
- `monster_spawn_block_light_limit: 0`, `sky_light_level: 0.0` — dark
- `has_fixed_time: true` — no day cycle

Values like `cardinal_light: "nether"`, `skybox: "none"` and
`timelines: "#minecraft:in_nether"` are kept verbatim **because they are the only
values of those fields anyone here has actually seen**. Inventing a plausible
alternative is how a datapack fails to load with an unhelpful error.

The generator is `minecraft:nether` noise settings with the `minecraft:nether`
biome preset — a straight reuse, verified as a working pair in vanilla's own
`dimension/the_nether.json`, so there is somewhere to stand without authoring
noise settings.

## The dynamic half

The `bindings/` files are the other side of the division of labour: things a
`dimension_type` has no way to say. `beyond_depths.json` is fog that closes in below
Y 48; the Overworld pair changes with the weather and the biome you are standing in.
Above that depth mc-shaders contributes nothing and the static base above stands
alone — that is the intended outcome, not a gap.

There is deliberately no `beyond_base` restating the dimension's ordinary fog. The
`dimension_type` already carries those numbers, and a second copy of a number is a
number that drifts: the one a pack author edited would stop being the one in force,
with nothing to say so.

The doubled `mcshaders` is not a typo. The first is the namespace; the second is
this mod's own directory beneath it. That nesting is what lets a third-party pack
contribute at `data/<their-namespace>/mcshaders/bindings/` without colliding with
some other mod that also wants a `bindings` directory — which a flat
`data/<ns>/bindings/` would not.

**These files are loaded now** — `BindingReloadListener` reads them on both loaders at
datapack reload. With the caveat that runs through this whole project: that listener
compiles against the real 26.2 API, and has never been observed reading a file. What is
certain is that the files parse, because tests load these exact ones and resolve them
the way the renderer does.

`beyond_depths` is also still registered in Java, from `BuiltinBindings`, and a test
requires the file to equal what the code registers. The two are not redundant: a pack
binding of the same id overrides the registered one, so the pair is what proves
overriding works rather than replacing.

The Overworld pair carries the other half of the format. `beyond_depths` uses only
`y_range`; between them the Overworld files exercise `always`, `any`, `all`, `not`,
`weather`, `submerged` and `biome_tag` — every condition type except `time_of_day`,
which is left out on purpose because it cannot be read on 26.2 and a demo gated on it
would ship a look that never appears.

## What is not here yet

Nothing reaches this dimension yet — but the API surface a portal needs is now
established rather than guessed. The teleport call and the opening scan are in
[../docs/PORTALS-26.2.md](../docs/PORTALS-26.2.md); registering the frame block and
catching the right-click that lights it are in
[../docs/BLOCKS-26.2.md](../docs/BLOCKS-26.2.md), and what a portal block does when
an entity stands in it is in PORTALS-26.2.md too — it turns out not to teleport at
all. What is left is writing it.

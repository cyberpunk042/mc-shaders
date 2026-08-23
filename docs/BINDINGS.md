# The binding format

A **binding** gives a dimension a look, as data. This is the complete field
reference: everything below was read out of
[`BindingCodec`](../common/src/main/java/net/cyberpunk042/mcshaders/codec/BindingCodec.java),
which is the only thing that parses these files, and every JSON block on this page is
parsed by a test so it cannot drift from the codec.

For what a binding *is* and how it relates to the rest of the mod, see
[SHADERS.md](SHADERS.md). For how the files reach the game, see
[DATAPACKS-26.2.md](DATAPACKS-26.2.md).

## Where the files go

```
data/<your-namespace>/mcshaders/bindings/<anything>.json
```

The doubled directory is deliberate. The first segment is **your** namespace; the
second is this mod's directory beneath it. That nesting is what lets your pack
contribute bindings without colliding with some other mod that also wants a
`bindings/` directory.

The file name is not the binding id — the `id` field is. Name files however you like.

## A complete binding

```json
{
  "id": "overworld_storm",
  "dimension": "minecraft:overworld",
  "priority": 20,
  "condition": { "type": "weather", "weather": "thunder" },
  "stack": {
    "layers": [
      {
        "id": "atmosphere",
        "kind": "fog",
        "type": "mcshaders:fog",
        "blend": "alpha",
        "weight": 1.0,
        "priority": 0,
        "params": {
          "start": 8.0,
          "end": 56.0,
          "color": { "r": 0.325, "g": 0.361, "b": 0.404, "a": 1.0 }
        }
      }
    ]
  }
}
```

### Binding fields

| Field | Required | Default | What it is |
|---|---|---|---|
| `id` | **yes** | — | Identifies the binding. A binding with the same id replaces an earlier one, which is how a pack overrides one the mod registered in Java |
| `dimension` | **yes** | — | Which dimension this applies to. `namespace:path`; a bare `path` means `minecraft:path`, so `"overworld"` and `"minecraft:overworld"` are the same |
| `priority` | no | `0` | Higher wins where two bindings define the same layer id. Ties break by `id`, so the outcome is deterministic rather than map-order |
| `condition` | no | `always` | When this binding is in force |
| `stack` | no | empty | The layers it contributes |

**A file may hold one binding or an array of them.** Both are accepted:

```json
[
  {
    "id": "overworld_air",
    "dimension": "minecraft:overworld",
    "priority": 0,
    "stack": { "layers": [
      { "id": "atmosphere", "kind": "fog", "params": { "start": 48.0, "end": 192.0 } }
    ] }
  },
  {
    "id": "overworld_deep",
    "dimension": "minecraft:overworld",
    "priority": 10,
    "condition": { "type": "y_range", "min": -64.0, "max": 0.0 },
    "stack": { "layers": [
      { "id": "atmosphere", "kind": "fog", "params": { "start": 4.0, "end": 32.0 } }
    ] }
  }
]
```

One file per dimension is the obvious layout; one file listing several is the obvious
way to ship a small pack. Neither is refused.

## How bindings combine

Every binding whose `dimension` matches and whose `condition` currently holds is
active at once. They merge **in ascending priority**, and merging is **by layer id**.

That is the part worth understanding: a high-priority binding that redefines one layer
leaves the rest of a lower-priority binding's stack intact. So a pack can change a
dimension's fog without restating its colour grade, and the two files above give the
Overworld one `atmosphere` layer whose numbers change below Y 0 — not two competing
layers.

Give a layer the **same id** to replace it; give it a **different id** to add to it.

## Layers

| Field | Required | Default | What it is |
|---|---|---|---|
| `id` | **yes** | — | The merge key, per above |
| `kind` | **yes** | — | What sort of effect this is (below) |
| `type` | no | none | The effect type a backend registered, e.g. `mcshaders:fog`. Without one the layer describes an effect nothing is claiming to draw |
| `blend` | no | `alpha` | How it composites (below) |
| `weight` | no | `1.0` | Strength |
| `priority` | no | `0` | Order within the stack |
| `params` | no | empty | Whatever the effect type reads |

**`kind`** is one of: `color_grade`, `fog`, `distort`, `bloom`, `vignette`,
`chromatic`, `grain`, `custom`.

**`blend`** is one of: `replace`, `alpha`, `add`, `multiply`, `screen`.

Both are written lower-case and read case-insensitively — `"alpha"` and `"ALPHA"` both
work, because failing one of them teaches nobody anything.

## Parameters

**Parameters are typed by their JSON shape, not by a tag.** The five shapes do not
overlap, so the mapping is unambiguous in both directions:

| You write | You get |
|---|---|
| `0.5` | a scalar |
| `true` | a flag |
| `"wobble"` | text |
| `[1.0, 0.0, 0.0]` | a vector — exactly three numbers, or it is an error |
| `{ "r": 1, "g": 0.6, "b": 0.4, "a": 1 }` | a colour — `a` defaults to `1.0` |

```json
{
  "speed": 0.5,
  "on": true,
  "label": "wobble",
  "dir": [1.0, 0.0, 0.0],
  "tint": { "r": 1.0, "g": 0.6, "b": 0.4, "a": 1.0 }
}
```

> **The one trap.** `"speed": "0.5"` — a quoted number — is a **text parameter, not an
> error.** Text parameters are legitimate and the codec does not know what `speed` is
> supposed to be, so nothing at this layer can catch it. The layer that does know is the
> effect's schema, and that is where a wrong-typed value against a *declared* parameter
> gets reported. This is a real gap between the two layers rather than a hidden one.

Parameter names are whatever the effect type reads. `mcshaders:fog` reads `start`,
`end` and `color`.

## Conditions

A condition decides whether a binding is in force right now. Omit `condition` entirely
and the binding always applies.

Every condition is an object with a `type`. These are all of them:

| `type` | Extra fields | Holds when |
|---|---|---|
| `always` | — | always |
| `never` | — | never |
| `submerged` | — | the camera is in a fluid |
| `y_range` | `min`, `max` | the camera's Y is in the range |
| `weather` | `weather` | the weather is `clear`, `rain` or `thunder` |
| `biome_tag` | `tag` | the camera's biome has the tag |
| `time_of_day` | `from`, `to` | **never — see below** |
| `all` | `of` (array) | every listed condition holds |
| `any` | `of` (array) | at least one holds |
| `not` | `of` (one condition) | the listed one does not |

Naming a type that is not on this list is an error that lists them all back to you.

```json
{ "type": "y_range", "min": 0.0, "max": 48.0 }
```

```json
{ "type": "biome_tag", "tag": "#minecraft:is_forest" }
```

`all`, `any` and `not` nest, so conditions compose to whatever you need:

```json
{
  "type": "all",
  "of": [
    { "type": "any", "of": [
      { "type": "weather", "weather": "rain" },
      { "type": "weather", "weather": "thunder" }
    ] },
    { "type": "not", "of": { "type": "submerged" } }
  ]
}
```

Note the shape difference: `all` and `any` take an **array** in `of`; `not` takes a
**single condition**.

> **`time_of_day` cannot work on 26.2.** The day time is no longer readable from the
> client, so a binding gated on it parses fine and then silently never fires. It is
> listed here because the codec accepts it and you will find it in the source — not
> because you should use it. If you gate a look on it, that look will not appear.

## Errors

A file that does not parse is reported by **name and path within the file**, and the
rest of the pack still loads. A broken file costs you that file, not the pack:

```
overworld_weather.json at [1].condition: missing 'of'
```

## What this does not cover

**Dedicated servers.** Bindings are `data/` content, so the reload happens server-side.
In singleplayer and LAN the integrated server shares the client's JVM and this works.
On a dedicated server the reload runs where nothing renders, and the connecting client
never receives these files. Syncing them over the network is not written, and nothing
here should be read as though it were.

**Whether the look reaches your screen.** The parsing, merging and condition evaluation
on this page are tested. The render path that consumes the result compiles against the
real 26.2 API but has never been observed drawing a frame — see the status table in
[SHADERS.md](SHADERS.md), which says so plainly rather than implying otherwise.

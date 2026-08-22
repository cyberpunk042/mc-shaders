# Portals and dimension access on 26.2 — what was confirmed

Getting a player into another dimension. Read out of a mod that compiles on 26.2
rather than remembered.

**Source.** [`GreencraftMC/NeoTeleport`](https://github.com/GreencraftMC/NeoTeleport)
at `1.2.1`, whose Fabric module declares `minecraft "com.mojang:minecraft:26.2"`,
`fabric-loader:0.19.3`, and a `fabric.mod.json` depending on `minecraft: "~26.2"`
and `java: ">=25"`. That loader version is the same one
[VERSIONS.md](VERSIONS.md) pins and the same one Jade pins — three independent
mods agreeing on it.

Companions: [RENDERING-26.2.md](RENDERING-26.2.md) for the render path,
[DATAPACKS-26.2.md](DATAPACKS-26.2.md) for the reload path.

## The chain

| Piece | Signature |
|---|---|
| Parse a dimension id | `net.minecraft.resources.Identifier.tryParse(String)` |
| Make its key | `ResourceKey.create(Registries.DIMENSION, identifier)` |
| Resolve the level | `MinecraftServer#getLevel(ResourceKey<Level>)` → `ServerLevel` |
| Move the player | `Entity#teleport(TeleportTransition)` |
| The transition | `net.minecraft.world.level.portal.TeleportTransition` |
| Read current dimension | `entity.level().dimension().identifier()` |

Verbatim from the source, with the surrounding bookkeeping removed:

```java
Identifier dimensionId = Identifier.tryParse(dimension);
ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));

player.teleport(new TeleportTransition(
        level,
        new Vec3(x, y, z),
        Vec3.ZERO,                                  // delta
        yRot, xRot,
        TeleportTransition.PLACE_PORTAL_TICKET));
```

Two details a signature written from memory would get wrong.

**Teleporting takes a `TeleportTransition`, not coordinates.** One object carries
the destination level, position, delta, both rotations and a post-teleport action.
The older idiom of a `teleport(level, x, y, z, yaw, pitch)` overload is not what
this mod calls.

**Reading a dimension's id is `.identifier()`.** `entity.level().dimension()`
gives a `ResourceKey`, and its id accessor here is `identifier()` — the 1.21.x
`location()` is what a search turns up, and what a port from that era would write.

`TeleportTransition.PLACE_PORTAL_TICKET` is the post-teleport action used for a
portal-style move; it is a constant on the class, and it is the one a mod moving a
player between dimensions actually passes.

## What this decides for the Ancient City portal

The destination half is solved: given `mcshaders:beyond` as a string, the four
lines above put a player in it.

It also means the **frame geometry no longer blocks anything**, which was not
obvious. The chosen behaviour is "lit like a nether portal", and vanilla's nether
portal does not hardcode a shape — it scans outward from the ignition point for an
opening bounded by the frame material. Doing the same needs no knowledge of the
Ancient City's exact dimensions, which two secondary sources disagreed about and
which `minecraft.wiki` is unreachable from here to settle.

The gate is not the shape. Reinforced deepslate is unobtainable in survival and
immune to pistons, so the only frame anyone can light is one the game generated.

That scan is pure logic over a block predicate, so it lives in `core` and is
tested without a game — see `core.portal.FrameScan`. Its tests draw each world as
a picture and hold the scan to the claim above: every frame in them is a different
shape, and none of those shapes appear in the scan itself.

## The other two links

Both are now read, by the same method, and are written up separately in
[BLOCKS-26.2.md](BLOCKS-26.2.md):

- **The use event** — `Block#useItemOn`, returning `InteractionResult`, with
  flint and steel matched through the vanilla `minecraft:creeper_igniters` tag.
- **Registering a block** — `BlockBehaviour.Properties.of().setId(key)`, which is
  mandatory before construction on 26.2.

NeoTeleport has neither: it is command-driven, registering no blocks and handling
no block interaction. The sources for those are Fabric API's own 26.2 branch,
Blockus and PolyDecorations.

## A portal block does not teleport

This corrects the plan the rest of this document was written around. Reading what
vanilla actually does, rather than assuming a portal calls the teleport above,
changes the design.

Vanilla's `NetherPortalBlock.entityInside` is this, in full:

```java
if (entity.canUsePortal(false)) {
    entity.setAsInsidePortal(this, pos);
}
```

That is the whole body. It registers the entity as being in a portal and returns.
`Entity#handlePortal()`, driven from `baseTick()`, ticks that registration on later
frames and asks the block for a destination when it matures:

```java
public @Nullable TeleportTransition getPortalDestination(
        ServerLevel currentLevel, Entity entity, BlockPos portalEntryPos)
```

`EndPortalBlock` and `EndGatewayBlock` have the same shape, which is three
implementations agreeing rather than one example.

**So the block supplies a destination; the entity machinery performs the travel.**
Calling `teleport(TeleportTransition)` from `entityInside` would teleport on the
first tick of contact, which is not how a portal feels, and would skip the
machinery entirely.

That machinery is also the answer to oscillation, which is worth being explicit
about because the obvious workaround is worse than the thing it replaces.
`canUsePortal` consults a cooldown that `setAsInsidePortal` maintains, and it
persists as the NBT key `PortalCooldown`. Hand-rolling a `Map<UUID, Long>` — which
is what a command-driven mod like NeoTeleport legitimately does, having no portal —
would not survive a relog and would not stop a mob.

The teleport chain earlier in this document is still correct; it is simply the
answer to a different question. It is how a *command* teleports. A portal block
should implement the interface and let vanilla call it.

## The hook signature changed on 26.2

```java
protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                            InsideBlockEffectApplier effectApplier, boolean isPrecise)
```

Six parameters, returning `void`. On 1.21.x this was four — `InsideBlockEffectApplier`
(in `net.minecraft.world.entity`) and `isPrecise` are both new, so a remembered
override silently fails to override anything.

Confirmed from two independent trees, and from an argument order rather than a
declaration alone: NeoForge `26.2.x` (`patches/.../CropBlock.java.patch:55`) carries
the declaration as unchanged context, and Paper at `mcVersion=26.2`
(`patches/sources/.../LilyPadBlock.java.patch:8`) carries
`super.entityInside(state, level, pos, entity, effectApplier, isPrecise)`, which
pins the order. Both files are diffs against Mojang-mapped 26.2 source, so a context
line is verbatim vanilla.

`Fluid` has a similar but *different* overload — no `BlockState`, no `isPrecise` —
which is the kind of near-match that gets copied by accident.

## What is still unverified

Read this before writing code against any of it. Each of these is either absent from
every 26.2 source reachable here, or attested only by a version-unpinned mapping
dump, which is not evidence about 26.2:

- **`Entity#isOnPortalCooldown()`** and **`getDimensionChangingDelay()`** — zero
  occurrences across Paper 26.2 and NeoForge 26.2.x. They may exist and simply be
  unused. Prefer `getPortalCooldown() > 0` or `canUsePortal(boolean)`, both of which
  are attested.
- **The package of the `Portal` interface** and of the portal processor type. The
  interface must exist — it types `setAsInsidePortal`'s first argument — but no 26.2
  source here names it.
- **Whether a five-argument `entityInside` overload still exists** alongside the
  six-argument one. Neither Paper nor NeoForge patches `BlockBehaviour.entityInside`,
  so neither says. Every vanilla 26.2 override found uses six.

There is also no custom-portal mod on 26.2 to copy from: `customportalapi` stops at
1.21, and the one Immersive Portals fork on 26.2 teleports through its own entity
and its `changeDimension` mixin is not registered in any of its mixin configs, so it
never applies. Vanilla is the reference.

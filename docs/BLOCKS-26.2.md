# Blocks and interaction on 26.2

Companion to [PORTALS-26.2.md](PORTALS-26.2.md), which covers the teleport call and
finding a frame's opening. This covers the two links that were still unread: putting
a block in the world, and knowing when somebody right-clicks one.

Nothing here is remembered. Every signature is copied out of a mod that compiles on
26.2, and the one vanilla data value is copied out of vanilla.

## Where it comes from

| Source | Ref | Evidence it is 26.2 |
|---|---|---|
| [`FabricMC/fabric`](https://github.com/FabricMC/fabric) (Fabric API) | branch `26.2`, `370a4fc`, `version=0.158.0` | `gradle.properties` `minecraft_version=26.2`; `options.release = 25` |
| [`Brandcraf06/Blockus`](https://github.com/Brandcraf06/Blockus) | `master`, `6922851` | `minecraft_version=26.2`, `loader_version=0.19.3`; `fabric.mod.json` depends `minecraft "~26.2-"`, `java ">=25"` |
| [`Patbox/PolyDecorations`](https://github.com/Patbox/PolyDecorations) | `master`, `415d220` | `minecraft_version=26.2`, `options.release = 25` — but see the caveat below |
| [misode/mcmeta](https://github.com/misode/mcmeta) | ref `26.2-data` | vanilla's own data at that version |

`loader_version=0.19.3` in Blockus is the same pin this repo and Jade use, which is
a small independent check that all three are aiming at the same thing.

**Caveat on PolyDecorations.** Its `fabric.mod.json` still declares
`minecraft ">=1.20.3-"` and `java ">=17"`. That metadata is stale; the build is
unambiguously 26.2. Its code therefore proves a signature *compiles* on 26.2, which
is what it is used for here, and proves nothing about the mod's declared support.

## Registering a block

```java
private static ResourceKey<Block> keyOf(String id) {
    return ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MOD_ID, id));
}

public static final ResourceKey<Block> CHUTE_BLOCK_KEY = keyOf("chute");
public static final ChuteBlock CHUTE_BLOCK =
        new ChuteBlock(BlockBehaviour.Properties.of().setId(CHUTE_BLOCK_KEY));
public static final BlockItem CHUTE_ITEM = new BlockItem(CHUTE_BLOCK,
        new Item.Properties().setId(ResourceKey.create(Registries.ITEM, CHUTE_BLOCK_KEY.identifier())));

Registry.register(BuiltInRegistries.BLOCK, CHUTE_BLOCK_KEY, CHUTE_BLOCK);
Registry.register(BuiltInRegistries.ITEM, CHUTE_BLOCK_KEY.identifier(), CHUTE_ITEM);
```

— Fabric API's own testmod, `fabric-api-lookup-api-v1/.../FabricApiLookupTest.java:46-69`.
Blockus does the same thing through a builder
(`utils/helper/BlockBuilder.java:78-101`), which is corroboration rather than a
second design.

Four things a 1.21 habit gets wrong:

- **`.setId(...)` is mandatory** before the `Block` is constructed. The properties
  carry the id now; it is not supplied only at registration.
- **The id class is `net.minecraft.resources.Identifier`**, not `ResourceLocation`,
  and it is built with `Identifier.fromNamespaceAndPath(namespace, path)`.
- **`ResourceKey#identifier()`** is the accessor — the same rename as
  `Level#dimension().identifier()` in PORTALS-26.2.md, so it is a rename across the
  codebase rather than a quirk of one class.
- **`Registry.register` takes either** a `ResourceKey` or an `Identifier`. Both
  appear in the verified sources; neither is more correct.

A block item is a second, separate registration. A block registered without one
exists in the world and cannot be held, which for a portal block is the desired
outcome, not an omission.

## Knowing when a block is right-clicked

The vanilla methods, on 26.2:

```java
InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                            Player player, InteractionHand hand, BlockHitResult hit)

InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                 Player player, BlockHitResult hit)
```

The return type is `net.minecraft.world.InteractionResult`. The
`ItemInteractionResult` that existed briefly around 1.20.5–1.21.1 is **gone**, which
is the single most likely thing to be remembered wrongly.

The signatures above are not inferred from an override. Fabric API mixes into the
vanilla base class and its `@Inject` descriptors name the vanilla shape directly
(`fabric-events-interaction-v0/.../BlockBehaviourBlockStateBaseMixin.java:41-57`):
`BlockState#useItemOn(ItemStack, Level, Player, InteractionHand, BlockHitResult)`
and `BlockState#useWithoutItem(Level, Player, BlockHitResult)`, both returning
`InteractionResult`. A mixin that named them wrongly would fail to apply, so this is
the strongest evidence available short of running the game.

### Three places to hook, and which to use

| | Fires | Passes with |
|---|---|---|
| `UseBlockCallback.EVENT` | before vanilla dispatches, for every block | `InteractionResult.PASS` |
| `BlockEvents.USE_ITEM_ON` | at the head of `BlockState#useItemOn` | `null` |
| Override on your own `Block` | when vanilla reaches your block | — |

Both events are in `net.fabricmc.fabric.api.event.player`, in
`fabric-events-interaction-v0`. **There is no `.v1` successor** — unlike the resource
and rendering APIs, which did move. Do not go looking for one.

The two sentinels differ, which is a real trap: `UseBlockCallback` continues on
`InteractionResult.PASS`, and `BlockEvents.*` continues on `null`. Returning `PASS`
from a `BlockEvents` listener stops the chain.

For the portal, the override is the right hook. The frame is our own block, so
there is no reason to inspect every right-click in the world to find the ones that
land on it.

### Lighting it, concretely

```java
} else if (!state.getValue(LIT) && stack.is(ItemTags.CREEPER_IGNITERS)) {
    world.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
    ...
    stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND
            ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
    return InteractionResult.SUCCESS_SERVER;
} else {
    return InteractionResult.TRY_WITH_EMPTY_HAND;
}
```

— PolyDecorations' `BrazierBlock.java:98-121`, which is a block lit with flint and
steel: the same interaction the portal needs.

`ItemTags.CREEPER_IGNITERS` is the right test rather than comparing against
`Items.FLINT_AND_STEEL`. It is a vanilla tag, and at ref `26.2-data` it holds
exactly `minecraft:flint_and_steel` and `minecraft:fire_charge` — verified against
vanilla's own data, not inferred from the mod. Using the tag means a fire charge
lights the portal too, which is what a player who has lit a nether portal expects.

Two details worth carrying over: `stack.hurtAndBreak(int, Player, EquipmentSlot)`
takes an equipment slot on 26.2, and `SUCCESS_SERVER` exists alongside `SUCCESS`.

`TRY_WITH_EMPTY_HAND` is the interesting return. It falls through to
`useWithoutItem` rather than ending the interaction, so a block that declines an
item still gets its bare-handed behaviour. Returning `PASS` or `FAIL` instead would
swallow it silently.

## What this decides for the portal

Everything that was blocked on a source now has one:

- The frame block registers as above, with no block item.
- It overrides `useItemOn`, tests `ItemTags.CREEPER_IGNITERS`, and on a match runs
  [`FrameScan`](../core/src/main/java/net/cyberpunk042/mcshaders/core/portal/FrameScan.java)
  from the struck position.
- A `Result` that `isPortal()` fills the interior; anything else returns
  `TRY_WITH_EMPTY_HAND` so the block behaves normally when it is not a frame.
- The portal block itself does **not** teleport. See
  [PORTALS-26.2.md](PORTALS-26.2.md) — an earlier draft of this line said it did,
  and that was wrong.

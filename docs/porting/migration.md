# Migration state

**No whole-world upgrade path is validated. No 1.20.1/26.2 network compatibility is claimed.**

## Implemented primitives

Minecraft resource IDs now use `Identifier`. TACZ pack IDs and namespaces retain their string values. The new Gson adapter validates identifiers and keeps string serialization. Public Java APIs using ResourceLocation require source recompilation using Identifier; method names that contain ResourceLocation are retained where they are TACZ-owned API names.

TACZ-owned item scalar fields retain their exact names in `minecraft:custom_data`. `ItemDataAccessor.get` returns a detached snapshot. Commit changes through `set` or `update`; mutating a returned tag is no longer a write to an item. This preserves vanilla component equality and copy isolation. Baseline defaults and clamping rules in the item accessors are retained.

Nested attachment stacks use `tacz:attachments`, with the target `ItemContainerContents` persistent and network codecs. Stable slots are scope=0, muzzle=1, stock=2, grip=3, laser=4, extended_mag=5. More than six encoded slots are rejected. Each stack retains its own complete target component patch. Use `IGun.updateAttachmentTag` for edits to an installed attachment. The zoom operation now explicitly writes back.

The version-0 TACZ attachment converter accepts legacy byte Count, item id and nested tag, preserves the original slot compounds under `tacz:legacy_attachments_v0`, and sets `tacz:attachment_schema=1`. It validates all slots before editing a gun and is idempotent after success. Unknown custom NBT is copied. Unknown item IDs, invalid counts/types, conflicting component/legacy data, unsupported schema versions, legacy Forge capabilities, and legacy vanilla display/enchantment/attribute/etc. fields are rejected with an explanation; their original data is not modified. This is deliberately limited to TACZ-owned attachment state and does not perform Mojang's historical vanilla data fixes.

Focused target tests cover non-default skin/zoom/laser/extension fields, backup preservation, repeat conversion, nested item copy isolation, item-codec save/load, and rollback on unsupported input. They use a vanilla stick as the registered item fixture; TACZ mod registration and gameplay have not passed.

## Events and network API

TACZ events expose a typed static `BUS`, for example `GunShootEvent.BUS`. Subscribe through this bus or the target `@SubscribeEvent` annotation. A native cancelling listener returns `true`; there is no EventBus 6 `setCanceled` state. Ordinary listeners stop after cancellation. Use a `Priority.MONITOR` listener with `(event, boolean cancelled)` to observe both outcomes. Inherited TACZ events retain propagation to their parent event types. `src/portingChecks/java/com/tacz/guns/porting/EventChecks.java` is a compiling example against real target buses. KubeJS's own cancel operation transfers into the bus through the optional bridge; actual companion execution is not yet verified.

Tick handlers use the target's Pre/Post event classes. Handlers that used both baseline phases retain both subscriptions. Client-only compatibility initialization moved into ClientCompatRegistry, selected by Forge's client distribution annotation.

Both TACZ channels require exact integer protocol **262002**. All original play message IDs and directions remain in their original order. The mapping and acknowledgment messages now run in CONFIGURATION, with a per-connection token; old login indices and reflective login packet lists have no target equivalent. Add-ons that registered custom handshake messages must use Forge's configuration tasks and explicit channel builders. `NetworkHandler.sendToServer` replaces the removed SimpleChannel convenience method. `IMessage.handle` takes CustomPayloadEvent.Context directly. No cross-version networking is supported.

Entity-data IDataSerializer implementations now take RegistryFriendlyByteBuf for network operations and HolderLookup.Provider for NBT operations. This is required to preserve arbitrary registered item components. Mapping numbering is validated completely before publication; mismatched key sets now disconnect explicitly instead of installing a partial table. Capability NBT retains ClassKey/DataKey/Value entries and preserves unresolved entries. This does not yet convert legacy vanilla item NBT embedded in a capability.

## Remaining migration gates

- Top-level 1.20.1 item/world conversion and recovery procedures.
- Remaining item NBT call sites outside the data accessors, entity/block/player persistence and Forge capabilities.
- Gun-pack Minecraft-facing recipe/tag/loot/pack format migration; TACZ public pack format must be preserved.
- Configuration and public extension examples; all optional integrations.
- Actual default-pack loading, Lua execution, runtime copy/split/craft/refit/save/reconnect validation.

## Recipes, resources and Lua — continuation

All baseline recipe IDs are unchanged; the Minecraft data directory is now `recipe`. The full 183-file mapping is resource-moves.json. The ten vanilla recipes use string/tag ingredients and result `id`; TACZ recipes retain their custom materials/result fields through compatibility codecs. Deferred gun/ammo/attachment results retain quantities and tab overrides. The 100-round .22 WMR recipe remains 100 rounds; crafting splits physical outputs to item limits and at most 99 per stack. Arbitrary legacy custom item definitions outside the target item-codec count range (1..99) currently fail explicitly and still need migration policy; they must not silently disappear.

The `forge` item tags used by shipped recipes remain available through aliases into target `c` tags. Names which changed are glass → glass_blocks, gunpowder → gunpowders and leather → leathers; other 14 paths keep their suffix. All 17 vanilla memberships were checked against Forge 1.20.1-47.3.19 and 26.2-65.1.0 universal JARs. Custom additions, pack precedence and reload remain unverified.

The target RecipeSerializer is a record holding codecs. GunSmithTableSerializer now exposes CODEC, STREAM_CODEC and SERIALIZER; RecipeHolder supplies the ID, bound through bindId. The old fromJson/fromNetwork/toNetwork entry points are replaced. RecipeInput is GunSmithTableInput. TACZ's own menu owns material consumption; vanilla recipe-book grid placement is inapplicable, as in the baseline. The new recipe list message is play ID 32, following all 31 original IDs, and carries resolved item components. Live cache refresh on login/reload/JEI must still be validated.

Gun-pack folders and ZIPs use target resource suppliers. Legacy recipe paths are translated in memory; target-path duplicates override legacy-path definitions within one pack. Only shaped/shapeless vanilla recipe bodies are upgraded by this path adapter; other Minecraft-facing pack format changes still need auditing. Source files are not rewritten. Pack suppliers open fresh ZIP/folder resources for metadata and full loads to avoid retaining closed handles.

`tacz:blood_strike_1` is now data-driven under painting_variant, with the same asset and size. ModPainting.BLOOD_STRIKE_1 is a ResourceKey rather than RegistryObject. BulletHoleOption.CODEC is a MapCodec and STREAM_CODEC handles networking. Minecraft particle commands use target structured arguments; the original positional parsing helper is retained for Java callers, not installed as a vanilla command parser.

LootTableInjection.fromJson now requires registry-aware DynamicOps from the reload, and CommonAssetsManager.reloadAndRegister accepts that context. Global loot modifiers use target MapCodecs and LootTable arguments. Dynamic loot functions/conditions and actual drops remain unverified.

LuaNbtAccessor retains its constructor, factory and scalar methods, and adds getBoolean(String) while retaining the old explicit-compound overload. It is now a class with a live item view. All put methods commit components; getCompound returns a view of that current nested key, and a removed key is not recreated by a stale child. putCompound inserts a snapshot; retrieve the attached child for later edits. The internal nbt() accessor returns a snapshot for items; mutating it does not save. Standalone CompoundTag access remains mutable. These Java source/aliasing changes require add-on review and shipped-script gameplay checks.

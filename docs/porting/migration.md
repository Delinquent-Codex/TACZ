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

## Block and item data — 2026-09-09

Block constructors now take BlockBehaviour.Properties with setId applied by registration; the block MapCodecs use these constructors. Register an add-on block/item with its own ResourceKey before constructing it. Block entities use ValueInput/ValueOutput and getUpdateTag(HolderLookup.Provider). Render AABB endpoints preserve the original lower-corner coordinates.

Statue nested items explicitly recognized as 1.20.1 are data-fixed from data version 3465. Legacy capability payloads, mixed representations, unknown IDs and counts outside 1..99 fail without changing the input. Failed statue data stays raw, including values which are not compounds. On removal, a typed block-entity-data component carries the saved block data on a placeable statue item; the empty loot drop is suppressed. Normal vanilla drop gamerules still apply. No live recovery workflow has passed yet, and this helper is not a complete top-level world migration.

Target owners accept legacy uppercase fields and current ResolvableProfile syntax, including shorthand player names. Names convert from legacy component JSON; failed values retain their format marker. Resolved profiles retain custom texture/cape/elytra/model patches. Async completion is guarded against replacement/removal. Other mods' fields, arbitrary future block schemas, signed skin resolution and actual skin rendering still need integration tests.

TACZ's custom HideFlags meaning is retained under tacz:tooltip_hide_flags by the explicit legacy converters. Vanilla also used HideFlags and consumes it during item data fixing; conversion of top-level world items has not yet been intercepted, so those paths remain a release blocker. Ammo-box dyes now live in DYED_COLOR. The crafting_dye recipe retains custom ammo data while recoloring.

ItemStackMixin restores pack-dependent limits for AmmoItem because the old Forge Item.getMaxStackSize(ItemStack) hook is absent. It overrides the target ItemInstance default on ItemStack; other items still read MAX_STACK_SIZE. It has not been exercised through FML/Mixin transformation. Shipped ammo limits are 6..64. Larger external stack definitions and quantities exceeding the target codec's absolute 99-item bound remain an unresolved compatibility gate; no silent quantity reduction is authorized.

Attachment ID access commits the existing six legacy silencer-ID corrections through CUSTOM_DATA. The removed verifyTagAfterLoad override is gone; nested/raw legacy conversion and actual inventory transfer must still be audited. Gun and animation Lua contexts now obtain live component-backed access, and clear stale accessors for invalid/empty items.

## Entity and explosion API — 2026-09-09

BulletSpawnData keeps the original additional-spawn field order; target Forge writes the surrounding entity packet. No 1.20.1 client compatibility is implied. Bullets remain unsaved; target minecarts retain their custom name and now cache/synchronize a ResolvableProfile using TaczProfile. Legacy minecarts with only a valid player name resolve it on the server. Malformed cached profiles currently fall back to name-based resolution; arbitrary malformed target profile recovery still needs review.

Transient movement modifier UUIDs become tacz:extra_gun_speed and tacz:gun_weight_speed. Their amounts and additive-base/multiplicative-total operations remain. Server tick timing still uses the target server's 100-entry nanosecond ring and baseline thresholds.

ProjectileExplosion now extends ServerExplosion; explode returns an int as required by the target instead of void. The Level constructor remains, but execution requires ServerLevel. getToBlow/clearToBlow/finalizeExplosion remain for source callers; client effects are sent by ExplodeUtil. Target Forge detonation listeners can edit both block and entity lists. Block destruction and loot use native target callbacks. Blast-protection knockback now follows EXPLOSION_KNOCKBACK_RESISTANCE; multiple armor pieces and custom enchantment data must be compared in-game because vanilla's enchantment implementation changed. Invalid/nonpositive radius handling is explicitly safer than baseline NaN/infinite-ray behavior.

## Client rendering API — 2026-09-10

The TACZ-used SBM interfaces move to com.tacz.guns.api.client.animation.IFPAnimationInstance and com.tacz.guns.api.client.renderer.IFPGeoItemRenderer. The handler moves to com.tacz.guns.client.event.FirstPersonRenderHandler. TaczClientItemExtensions supplies TACZ's renderer through the retained Forge item initialization hook; TaczItemRenderer uses SubmitNodeCollector. Call submitByItem to establish a collector scope, or enter RenderSubmission explicitly around direct model rendering. Geometry snapshots own vertex values, so later model resets cannot change queued geometry. Add-ons using upstream SBM packages or removed MultiBufferSource APIs need source migration; complete third-party compatibility is not claimed.

MAE 1.1.2 stays unchanged and is now explicitly bundled by JarJar rather than supplied by an obsolete SBM nested jar. Upstream SBM source adaptations preserve LGPL notices in META-INF/licenses; MAE has its MIT notice. Broader package/license closure remains an open release gate.

Minecraft-facing item definitions move to assets/tacz/items. Seven TACZ custom renderer models use tacz:item with the existing base display transforms; builtin/entity parents are removed from those seven base resources. Three target/statue/minecart items use native model paths. Ammo boxes use range dispatch at thresholds 0..8 and the native dye color source, retaining all variant model resources. TACZ gun-pack model/animation paths are unchanged. Actual model bounds, foil/outline behavior, GUI/world transforms and repeated reload must be validated in a running client.

## World render state and movement API — 2026-09-11

Block/entity renderer entry points now use extractRenderState and submit, with snapshots of mutable world inputs. RenderSubmission carries the collector through Bedrock and deferred callbacks. Model reset and later draws cannot change already captured vertices. Target head visibility/rotation and traversal pose stacks are restored after rendering, including failures. All GPU scopes, shader adapters, crumbling/outline propagation and complete third-person paths remain open.

Bullet holes use native SingleQuadParticle terrain quads; their public provider now accepts the target RandomSource argument. The allowed fade-threshold value 1 is explicitly defined as fully visible until expiry, avoiding the old division-by-zero. Their block material comes from the target model-data-aware particle material lookup.

LegacyWalkProvider/LegacyWalkDistance restore the source walkDist semantics for scripts and non-player firing spread. The required EntityWalkMixin captures resolved movement before getBlockSpeedFactor and snapshots at baseTick. It ignores vertical movement, retains source float rounding, emission/passenger guards and TACZ's current-plus-delta extrapolation. It is transient per entity, like the original vanilla fields; no save schema is introduced. Hook bytecode is checked, while FML application and gameplay timing remain unverified.

Mixin compatibilityLevel denotes supported class language features, not the Java runtime requirement. Exact target Mixin 0.8.7 supports named levels only through JAVA_21, and validates features with LanguageFeatures.scan. The new Java 25 class passes that scanner at JAVA_17. Java 25 remains the compiler/runtime toolchain; complete mixin/refmap packaging and all actual injections still require validation.

## Effects and GUI extraction — 2026-09-11

BeamRenderer keeps its configuration lookup and public entry points but submits geometry to the active RenderSubmission collector. The accelerated consumer is resolved during preparation; shader/rendering companion compatibility is still unverified. Muzzle flashes own copied shot inputs. Carried-gun rendering separates entity/inventory extraction from native item submission; LayerGunTransform copies all pack vectors.

Custom tooltip implementations now implement getHeight(Font), extractText and extractImage with GuiGraphicsExtractor. Text alpha is explicit because the new GUI drops zero-alpha text. The first-person arm mixin targets AvatarRenderer.renderHand and does not copy transforms onto sleeves, which are now child parts.

All target mixins declare remap=false and omit SRG refmaps because 26.2 ships named classes. Required injections remain required; this does not establish that every old descriptor has been migrated. StairBlockAccessor.invokeGetModelBlock remains as a default source bridge to the real base field. AbstractButton click timestamps use the new mouse event signature.

Camera gun-hurt data is copied during Camera.extractRenderState. Bob hooks now consume CameraRenderState, and the hand render call scopes hand/world cancellation events with finally cleanup. The old getFov-based flag is removed because FOV calculation moved to Camera. The existing GunHurtBobTweak.onHurtBobTweak entry point delegates to the same captured-state implementation. Live spectator/local/remote views and event ordering still need validation.

## Humanoid callbacks and GUI — 2026-09-12

ThirdPersonManager and IThirdPersonAnimation retain their source callback signatures. Callbacks now run once when the main humanoid render state is extracted, with the actual entity, and changed part values are replayed on matching main/armor bones. The shared model is restored afterward. Callbacks which depend on the old per-model invocation cadence, mutate hierarchy/geometry instead of pose, or coordinate with another mod's animation hook require explicit compatibility work and are not validated. Builtin hold/aim/minigun values have CPU checks. Unknown armor bone layouts skip unmatched child paths while applying matching parent values.

Forge's old overlay events/interfaces are absent in 26.2 (commented-out source files are not usable classes). TACZ overlays now implement ForgeLayer and register through AddGuiOverlayLayersEvent. Extension code must use GuiGraphicsExtractor, live input records and native layer registration. Text requires explicit alpha; image tint is captured per blit. Screens and widgets extract deferred state; direct GL color/depth/blend calls no longer define their draw operations. The baseline empty refit background and custom HUD callbacks under F1 remain intentional. Default non-refit screen backgrounds follow target native extraction and need visual comparison.

The gunsmith preview uses a native picture-in-picture texture and copied item state rather than changing global model-view/scissor state. The target owns texture lifetime, resize, depth and final compositing. Source dimensions/origin/rotation/scale are checked; GPU scope effects, external shaders and exact font/item lighting remain open. The original checkbox atlas, removed from the target, is retained unchanged under the TACZ namespace with source SHA-256 recorded in status/verification; no image generation or recoloring was applied.

## Client resource and particle input — 2026-09-12

Display ItemStack Gson decoding now uses LegacyPackCodecs with registry operations supplied at decode time. Legacy item NBT is data-fixed; native component JSON is preserved. ItemTransform deserializers use the real target Gson implementations via narrow access transformers. Keyboard/mouse matching uses native event records and key categories; all 21 shipped locales retain their original category label through the new `key.category.tacz.guns` alias. The old language key is retained.

Both gun and ammo particle definitions now use LegacyParticleParser. It supports the 1.20.1 positional dust, dust-color-transition, block/block-marker/falling-dust, item-with-NBT, sculk-charge, shriek, vibration and TACZ bullet-hole forms. Native `particle{...}` SNBT is decoded through the target registry codec, including other mods' native particle codecs. Source JSON is not rewritten. Item particles use the same legacy item data fixer. Vibration retains source double-to-float narrowing before block-position flooring; legacy dust scale still clamps to 0.01..4.

Limitations: native dust stores 24-bit RGB, so legacy channels truncate to 8 bits (at most 1/255 loss). Legacy out-of-range/non-finite colors, malformed/trailing arguments and unknown mod-specific positional formats fail explicitly. Blank names no longer call the parser with null; invalid names produce a warning with the pack value. Other changes to vanilla particle types (for example removed ambient_entity_effect and new required options on formerly simple types) are not universal migration guarantees. All eight active default-pack particle names pass; custom-pack visual effects and every particle provider still need a live client. Server command syntax remains native; this adapter applies to TACZ pack definitions only.

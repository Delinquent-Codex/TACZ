# Forge 26.2 port checkpoint

Work started 2026-09-07. **Incomplete; no installable target JAR exists.**

- Project: `C:\Users\voltp\Downloads\TACZ-26.2`.
- Branch: `port/forge-26.2`.
- Baseline and initial HEAD: `b43eb84c38e9768d8e73c8b14f0b845669704b38`.
- Origin: `https://github.com/Delinquent-Codex/TACZ.git`, branch `1.20.1`.
- Upstream branch `1.20.1` and tag `1.1.8-hotfix` resolved to the same commit via `git ls-remote`. Published file 8141310 has the same 4,354 ZIP members; 4,352 byte-identical. The nested SimpleBedrockModel differs only in its manifest (119 other members byte-identical). GunSoundInstance.TaczSound differs by one compiler-generated bridge method present in the local build. See inventory/reference-jar-comparison.json and reference-model-comparison.json. Do not claim identical binaries or observed gameplay equivalence.
- Baseline: detached worktree `../TACZ-port-reference/baseline-1.20.1`; tracked files untouched.
- Exact target: Minecraft **26.2**, Minecraft Forge **65.1.0**, recommended on the official downloads page on 2026-09-07.
- Official MDK ZIP SHA-1: `83257cb3f4b6fedd6fe829b65982270f38bf960c`, matches the downloads page.
- MDK specifies Java **25**, Gradle **9.5.0**, ForgeGradle **7**. Old Java 17, Parchment, reobfuscation and mixin refmap assumptions must be replaced according to target sources.
- Portable JDKs downloaded from Adoptium and SHA-256 verified: Temurin `17.0.20.1+1` and `25.0.4.1+1`, under `../TACZ-port-reference/jdk17` and `jdk25`. System installation and global environment unchanged.

## Work order

1. Baseline build, exact target MDK build, provenance, complete file/content/dependency inventory.
2. Pin the target build; resolve required libraries and port lifecycle/registries.
3. Persistence, pack loading, Lua, mechanics and networking.
4. Rendering, animation, audio, UI, crafting, commands and integrations.
5. Release packaging, clean installations, multiplayer, migrations and performance gates.

## Current work

- Target MDK `tasks --all` and `build` passed; these are MDK checks, not TACZ runtime evidence.
- Target wrapper and build moved to Gradle 9.5.0 (SHA-256 pinned), ForgeGradle 7.0.36, JarJar 0.2.3, Java 25, Forge 65.1.0. Target `tasks --all` passed.
- Source inventory: 4,187 files, 652 Java files, 3,508 resources; default pack has 3,322 files, 54 gun IDs, 24 ammo IDs, 99 attachment IDs, 3 block IDs. All shipped JSON parses with Gson-style comments supported by the inventory reader.
- 193 source files migrated from ResourceLocation constructors/types and buffer operations to verified Identifier APIs. Added the replacement Gson identifier adapter. Replaced four SRG access-transformer names with verified target names; removed the deleted I18n.language access in favor of Language.getInstance().
- Component-backed item data: five accessors use detached custom_data snapshots and explicit writes. Nested attachments use a six-slot, saved/networked `tacz:attachments` component. Versioned conversion backs up legacy attachment compounds, validates all slots before writing, preserves custom extension fields, and rejects unsupported vanilla metadata. Full saved-world/item migration remains unverified.
- `verifyPortingPrimitives` passed 29 assertions using real target registry/item/component classes, including nested codec round trips and failed-conversion atomicity. This isolated check does not build or launch TACZ.
- TACZ event declarations/posts now use typed EventBus 7 buses and native boolean cancellation; optional script dispatch is separated from core class loading. Priority, conditional cancellation, monitor, inherited event and script dispatch tests passed 15 assertions in `verifyPortingEvents`; actual KubeJS execution is still unmet. Tick subscriptions preserve the baseline's start/end or both-phase cadence. Obsolete GUI hooks and further lifecycle APIs still need migration.
- All 31 play messages now use target SimpleChannel, exact protocol 262001, explicit direction/play phase and CustomPayloadEvent.Context. Item-bearing gun events use registry-aware component codecs. Login indices were replaced by a configuration task with a connection-specific token and acknowledgment after successful mapping installation. No blocking network-thread latch. Mappings reject duplicate, unknown, missing or excessive keys before replacing the live table. **Live handshake and multiplayer remain unverified.**
- Entity-data serializers now pass registry lookup context into saved item codecs, and use RegistryFriendlyByteBuf on the wire. Capabilities attach through AttachCapabilitiesEvent.Entities; DataHolder uses AutoRegisterCapability. Capability deserialization prepares all known values first and preserves unknown entries. Lifecycle/save/load runtime validation remains open.
- `verifyPortingNetwork` passed 61 assertions: mappings, malformed input, eight real gun-event packet codecs, nested attachment components, primitive entity-data serializers and optional item codecs. These tests do not instantiate a real network connection.
- Full target `compileJava` failed three times at the 1,000-error reporting limit. Initial phase: `../TACZ-port-reference/logs/target-compile-03.log` (9 seconds); the same command with `--continue` also ran the 29 passing persistence checks. Obsolete render APIs, networking, capabilities, crafting and registry signatures remain.
- `gradle/legacy-dependencies.gradle` is explicit temporary scaffolding: old companion signatures remain compile-only, every integration source stays compiled, and required SimpleBedrockModel/MAE runtime closure is unresolved. **Do not release an artifact with this scaffolding.**
- Untouched baseline initial `tasks --all` failed after 16m53 with NoSuchElementException; cached retry with `--stacktrace` passed and did not reproduce the failure. Baseline `build jarJar` passed in 1m19. All tracked baseline files remain unchanged. Baseline all-JAR SHA-256: `e57aed76ee7c18a773db0fdde95e883aa438f7625c8863defd805c04b109d265`.
- Initial checkpoint uncapped main compilation: **1,132 errors**, after raising the reporting limit to 5,000. 716 are in client sources; remaining categories include blocks, entities, crafting, integrations and resources. All three focused checks passed in that same invocation (15 + 61 + 29 assertions). Evidence: `evidence/target-compile-06.log`, source input hashes in `evidence/tested-inputs-06.json`. This is still a failed build.
- First local implementation checkpoint: `eb33814c4d82fb5df37138e67f228b96389b1d28`. Further changes below are preserved in the worktree; inspect `git log -1` and `git status` for subsequent checkpoints. No repository AGENTS.md was found.

## Continuation — 2026-09-08

- Paintings use the target data registry: same `tacz:blood_strike_1`, texture and 2-by-2-block size. Bullet-hole particles use MapCodec and StreamCodec with bounded direction decoding. Loot modifiers use MapCodec and target LootTable arguments; injections decode with reload registry/condition context.
- Gunsmith recipes use target MapCodec/registry-aware stream codecs, RecipeInput and holder IDs. Server-side material allocation handles overlapping requirements; failed extraction or output insertion refunds materials. Quantities including the shipped 100-round .22 WMR recipe are retained across target-safe output stacks. Live crafting/refund behavior is unverified.
- Added explicit resolved-recipe sync after pack sync, because 26.2 clients have no full RecipeManager. Original 31 message IDs remain; recipe sync is appended as ID 32. Both channels now require **262002**. Client cache clears on logout. Live login/reload/JEI refresh remains unverified.
- Moved all 183 recipe resources to singular `recipe` paths without changing recipe IDs; 10 vanilla recipes use target ingredient/result syntax. Added 17 legacy Forge material-tag aliases; resolved vanilla membership matches baseline exactly (`inventory/recipe-tag-aliases.json`). Every moved path is recorded in `resource-moves.json` and the inventory has no unaccounted missing files.
- Folder/ZIP gun-pack loading uses target suppliers with fresh resource ownership per opening. Legacy `recipes/` paths are exposed as `recipe/` without rewriting source packs; target definitions take precedence for the same ID. Full gun-pack discovery, layered overrides and reload are still open.
- Legacy custom recipe items use Minecraft's item data fixer from 1.20.1 data version 3465, retaining name/damage/painting-variant components and unknown extension data in focused fixtures. This is separate from the earlier limited attachment converter; whole-world migration is still unverified.
- LuaNbtAccessor now reads current item custom data and commits scalar/nested writes through component updates. Actual LuaJ scripts passed; full shipped gun scripts and gameplay remain unverified.
- Exact target reports resource pack format **88.0**, data pack format **107.1**. The root metadata includes both in its shared numeric range; dynamically supplied gun-pack metadata uses the exact format for its PackType. Forge/MC version constraints remain exact. The initial MDK-based range failed the client-format test and was corrected.
- Latest full main compile: **1,050 errors** (712 client, 98 block, 67 compat, 60 entity, 40 util, 32 item, 13 mixin, 7 api, 6 command, 5 config, 4 resource, 3 event, 2 network, 1 sound). No diagnostics currently point at crafting/init/inventory/loot sources; this is not a successful build or runtime verification.
- Same invocation passed **596 assertions**: crafting 421, content 31, packs 25, Lua 14, network 61, events 15, primitives 29; 32 seconds. Evidence: `evidence/target-compile-11.log`, `evidence/tested-inputs.json` (4,223 build/source/resource/library hashes), refreshed `inventory/compile-errors.json`. Earlier failed fixture/metadata checks remain in logs 08 and 10. No target JAR exists.

## Resume

Next: block/entity/item persistence and registrations, then remaining common resource/config/API errors, before the rendering rewrite. Block entities now require target ValueInput/ValueOutput APIs; preserve BlockId, gun items, target owner/name and update packets. Inspect exact generated 26.2 sources before changing signatures. Also audit the new crafting transaction and recipe sync with a real server as soon as the full mod compiles. Recipe codec, menu, FML registration and live sync coverage are still open despite passing helper tests.

Required model library/MAE runtime closure, all optional integrations, complete rendering/audio/UI, clean packaged installations, multiplayer and migration/performance gates remain unmet. Do not replace the target, exclude main sources, or report focused checks as a full build.

Read this file and verification.md, inspect Git and current logs/processes, and resume at the actual failing gate. All reference downloads, source JARs, JDKs and MDK remain in `../TACZ-port-reference`; do not restart completed downloads. No build process remains running after compile-11 (a Gradle daemon may remain idle).

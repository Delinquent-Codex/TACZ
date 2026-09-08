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
- Full target `compileJava` failed three times at the 1,000-error reporting limit. Latest: `../TACZ-port-reference/logs/target-compile-03.log` (9 seconds); the same command with `--continue` also ran the 29 passing persistence checks. Obsolete render APIs, networking, capabilities, crafting and registry signatures remain.
- `gradle/legacy-dependencies.gradle` is explicit temporary scaffolding: old companion signatures remain compile-only, every integration source stays compiled, and required SimpleBedrockModel/MAE runtime closure is unresolved. **Do not release an artifact with this scaffolding.**
- Untouched baseline initial `tasks --all` failed after 16m53 with NoSuchElementException; cached retry with `--stacktrace` passed and did not reproduce the failure. Baseline `build jarJar` passed in 1m19. All tracked baseline files remain unchanged. Baseline all-JAR SHA-256: `e57aed76ee7c18a773db0fdde95e883aa438f7625c8863defd805c04b109d265`.
- Latest uncapped main compilation: **1,132 errors**, after raising the reporting limit to 5,000. 716 are in client sources; remaining categories include blocks, entities, crafting, integrations and resources. All three focused checks passed in that same invocation (15 + 61 + 29 assertions). Evidence: `evidence/target-compile-06.log`, source input hashes in `evidence/tested-inputs.json`, diagnostic inventory in `inventory/compile-errors.json`. This is still a failed build.
- A local implementation checkpoint is being recorded on `port/forge-26.2`; inspect `git log -1` and `git status` for its actual revision and later work. No target artifact exists. No repository AGENTS.md was found.

## Resume

Next: finish common registration/persistence/recipes before client rendering. Inspect `inventory/compile-errors.json` against the current files. Remaining init errors are paintings (now data-driven), particle codecs, loot MapCodecs, and recipe serializers. Required model library/MAE closure and the new rendering backend remain major work. Then validate actual FML registration and the configuration handshake once the complete mod compiles. Do not replace the target or exclude required sources.

Read this file and `verification.md`, inspect `git status --short` and current processes/logs, and resume at the actual failing gate. Reference downloads, extracted target sources (including EventBus 7.0.5), JDKs and MDK live in `../TACZ-port-reference`. Do not restart completed downloads or imply runtime tests have passed. No build process remains running as of this checkpoint.

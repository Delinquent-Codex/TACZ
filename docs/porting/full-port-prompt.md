# TACZ: Complete Minecraft Java 1.20.1 → Forge 26.2 Port

You are the implementation engineer responsible for completing the port of Timeless and Classics Zero (TACZ) from Minecraft Java Edition 1.20.1 to Minecraft Java Edition 26.2 using Minecraft Forge. Work in my existing TACZ-26.2 project. Implement the port, build it, test it, and deliver the resulting source and usable mod artifact with evidence of feature parity.

The objective is the entire existing mod: its behavior, content, visuals, animation, audio, data, customization, scripting, networking, dedicated-server support, public extension points, and compatibility integrations. A build-system update or a reduced demonstration is only an intermediate milestone. Continue through implementation and validation without repeatedly asking whether to proceed.

**Sources and target**

| Purpose | Source |
| --- | --- |
| Primary source repository | https://github.com/Delinquent-Codex/TACZ |
| Source branch | `1.20.1` |
| Reference commit observed while preparing this prompt | `b43eb84c38e9768d8e73c8b14f0b845669704b38` |
| Original project and published behavior | https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero |
| Upstream linked by the project | https://github.com/MCModderAnchor/TACZ |
| Exact destination | Minecraft Java Edition **26.2**, **Minecraft Forge** |
| Official target downloads | https://files.minecraftforge.net/net/minecraftforge/forge/index_26.2.html |

Research snapshot, September 7, 2026: the inspected repository declares mod version `1.1.8-hotfix`, Minecraft Forge `1.20.1-47.3.19`, and Java 17. The Forge 26.2 download page lists `65.1.3` as latest and `65.1.0` as recommended. These are reference observations: recheck the repository, official artifacts, and target requirements before selecting and pinning the actual toolchain. Do not confuse Minecraft 26.2 with Forge's own version number.

The source build and properties are available at [build.gradle](https://github.com/Delinquent-Codex/TACZ/blob/b43eb84c38e9768d8e73c8b14f0b845669704b38/build.gradle) and [gradle.properties](https://github.com/Delinquent-Codex/TACZ/blob/b43eb84c38e9768d8e73c8b14f0b845669704b38/gradle.properties). The [CurseForge project](https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero) identifies `tacz-1.20.1-1.1.8-hotfix.jar` as its main 1.20.1 release at the time of this snapshot. Verify the relationship between the chosen source commit and any reference release JAR; identical version strings alone do not prove identical contents.

**1. Working rules**

- Inspect the actual project before editing. Read applicable repository instructions, inspect Git status, remotes, branches, existing port work, and build configuration. Preserve user changes and useful existing migration work.
- Keep the 1.20.1 source available as a reference, using an isolated checkout or worktree when useful. Make target changes on a suitable port branch. Preserve history and avoid destructive resets.
- Use Forge for Minecraft 26.2. Changing loaders or Minecraft versions requires an explicit scope decision from me. Do not quietly substitute NeoForge, Fabric, 26.1, or 1.21.x.
- Ground API migrations in the exact target's sources, official MDK, dependency metadata, and documentation. Search primary sources when needed. Verify method signatures, event phases, lifecycle behavior, and dependencies before using them.
- Carry out routine local edits, dependency setup, builds, debugging, and tests autonomously within available permissions. Respect actual environment restrictions. Ask only when missing input materially blocks correct progress.
- Preserve existing functionality while replacing obsolete implementation details. Keep changes attributable to the port. Document intentional behavior changes and their reasons.
- Do not obtain a successful build by excluding required source files, removing content, disabling essential mixins, discarding integrations, swallowing failures, or substituting empty implementations.
- Temporary scaffolding must be tracked and replaced before completion. Do not fabricate APIs, download coordinates, tests, logs, screenshots, performance numbers, or results.
- A compile result, a successful development launch, and a tested release JAR are separate claims. Mark each accurately.
- Prepare reviewable changes and release materials locally. Public releases, publishing to CurseForge, pushing branches, or sending messages require authorization for those actions; they are not necessary to complete the local implementation.

**2. Establish the source baseline and target toolchain**

Resolve the exact source commit and record it. If my workspace already contains a more recent or partially ported revision, identify its ancestry and preserve its changes. Compare it with the reference commit and the relevant upstream release. Explain meaningful differences without silently replacing my fork with upstream.

Build the untouched 1.20.1 baseline in isolation if the environment permits. Record its JDK, Gradle wrapper, repositories, dependencies, command, exit status, and any existing failures. Capture reference gameplay and assets where executable access exists. When reference runtime access is unavailable, distinguish source-derived expectations from behavior actually observed.

Inspect the official Forge 26.2 MDK and select an available supported Forge build. Pin Minecraft, Forge, JDK, Gradle, build plugins, and relevant dependencies. Prefer the recommended build unless a documented compatibility need justifies a newer one. Verify artifact resolution rather than merely copying version numbers.

Derive the required Java version from the exact target. Do not carry Java 17 forward by habit or guess the target JDK from the year. Inspect the target's naming, mapping, and packaging model; retain or replace reobfuscation, refmap, access-transformer, and remapping steps according to actual requirements. Do not assume the old ForgeGradle/Parchment setup remains valid.

If the target or a dependency cannot be resolved, determine whether the cause is an unavailable release, incorrect coordinates, authentication, networking, or a local environment issue. Record the attempted source and concrete error. Continue independent work without claiming the blocked component works or changing the target.

**3. Create an exhaustive inventory and parity ledger**

Inventory the complete tracked source tree and all shipped resources, generated resources, local libraries, embedded libraries, and default gun-pack contents. The inspected reference contains 652 Java files; use this only as a cross-check, not as a definition of scope or a progress percentage.

Read registration, resource-discovery, event, scripting, and packaging code so the inventory captures behavior hidden behind data-driven systems. Count content by its actual identifiers, not merely by item classes. Include assets embedded in archives and content exported from the mod JAR.

Maintain `docs/porting/feature-parity.md` with these columns:

| Feature or content ID | Source evidence | Expected behavior | Target implementation | Validation evidence | Status or blocker |
| --- | --- | --- | --- | --- | --- |

Use explicit statuses such as `not started`, `in progress`, `implemented, unverified`, `verified`, `externally blocked`, and `not applicable, with evidence`. Keep externally blocked features visible as unmet requirements. Do not remove them from the completion denominator.

Track source-to-target file disposition separately: retained, modified, replaced with a named equivalent, merged into another implementation, or removed with a reason. A replacement can provide parity without retaining the original class. A missing source file is acceptable only when its purpose is accounted for.

Cover every discovered subsystem. Use the following sections as mandatory audit areas, expanding them with source findings. Conditional examples must be confirmed in the baseline; this port does not require inventing unrelated features.

**4. Build, packaging, startup, and registration**

Port the Gradle wrapper, settings, properties, plugins, repositories, dependency scopes, resource processing, generated data, run configurations, metadata, manifests, access transformers, mixin configuration, and release packaging.

Keep the established `tacz` mod ID and content namespaces wherever possible. Migrate registration for items, blocks, block entities, entities, menus, recipes, particles, sounds, attributes, paintings, damage types, and loot systems found in the source. Preserve registry identifiers or implement explicit migration for unavoidable changes.

Audit mod-event-bus and gameplay-event-bus subscriptions, setup ordering, resource availability, and client/common/server boundaries. Dedicated-server startup must not load client rendering, windowing, input, or sound classes through static initializers, class signatures, mixins, or optional compatibility entry points.

Use the target's real loader metadata and dependency range syntax. The release artifact must declare the target it has actually been tested against. Remove obsolete runtime flags only after verifying their purpose. Provide working Windows and Unix build instructions using the repository wrapper.

Inspect nested-JAR packaging and dependency closure. The installed artifact must contain or correctly declare everything needed at runtime. Compile-only or development-runtime availability is not proof that a clean installation works. Remove obsolete version-locked libraries from the final package after their replacements are implemented.

**5. Gun mechanics and entity behavior**

Preserve every firearm, ammunition type, magazine or ammo-storage behavior, attachment, skin, and special content entry discovered in the baseline. Preserve original defaults, formulas, timings, limits, and customization semantics unless a necessary change is documented.

Audit firing modes, firing cadence, shot scheduling, ammunition consumption, chambers, bolt actions, reload stages, empty and partial reloads, interruption, cancellation, drawing, holstering, inspection, aiming, zoom, movement restrictions, sprinting, crawling, melee, recoil, spread, heat, and cooling wherever implemented.

Port projectile creation, movement, collision, hit detection, headshots, damage attribution, armor interaction, knockback, and configured effects. Preserve penetration, explosive behavior, or other specialized mechanics if the source supports them. Review behavior around entity removal, chunk boundaries, world changes, and delayed events.

Preserve attachment restrictions, modifiers, installation and removal, ammunition capacity effects, sights, muzzle behavior, laser configuration, and skin selection. Ensure a UI update corresponds to the authoritative item state and gameplay calculation.

Audit the source's player and living-entity gun-operator APIs. Preserve non-player usage supported by those APIs. Test death, respawn, item swapping, dropping, inventory movement, disconnect, reconnect, and dimension transitions for stale state, duplicate ammunition, or repeated actions.

**6. Item data, persistence, and migration**

Inventory every field stored on items, entities, block entities, players, inventories, configs, and world data. Include identifiers, ammunition, attachments, skin choices, fire mode, upgrades, locks, and other fields actually present in the source.

Migrate legacy NBT access and serialization to the target's supported item-data, component, codec, and synchronization mechanisms where required. Preserve meaningful unknown extension data where possible. Confirm copy, split, stackability, equality, crafting, loot creation, serialization, and network round trips.

Provide versioned conversions for TACZ-owned formats when needed. Conversions must be repeatable without further damage, validate their input, preserve a recoverable original, and explain unsupported data. Test representative legacy fixtures and resulting target data, including nested attachment state and non-default values.

Treat existing gun-pack compatibility, saved-item compatibility, and upgrading an entire Minecraft world as separate capabilities. Do not promise universal 1.20.1 world compatibility with 26.2 from a TACZ serializer test. Validate supported world upgrade paths on disposable copies with the required mod environment and report their actual scope.

**7. Rendering, animation, and audio**

Treat visual and audio parity as required functionality. Audit first-person, third-person, inventory, dropped-item, item-frame, block-entity, projectile, and attachment rendering wherever the source implements them.

Port model loading, bone transforms, interpolation, animation blending, state machines, animation events, sound keyframes, hand poses, camera motion, aim transitions, recoil motion, reload sequences, and inspection sequences. Preserve glTF or Bedrock-model paths where the baseline uses them; do not infer that a specific external animation library owns every animation.

Preserve scope views, reticles, zoom behavior, attachment placement, muzzle flashes, shell effects, beams, bullet holes, particles, lighting, transparency, texture sampling, and LOD behavior found in the source. Compare timing and transitions as well as static appearance.

Inspect the actual 26.2 rendering backend and Forge hooks. Adapt old render-state assumptions, buffers, shaders, render targets, camera access, and resource lifecycles to the target. Verify thread ownership and render-state isolation. Do not introduce calls copied from a different Minecraft version without checking them.

Re-evaluate every rendering mixin against its real target. Preserve the intended behavior using supported hooks or precise replacement injections. Do not hide failed injections by lowering requirements indiscriminately. Validate production behavior as well as development behavior.

Preserve sound assets and sound selection, distance behavior, attenuation, positional tracking, volume configuration, and synchronization. Confirm local and remote players hear the correct events without duplication.

Check different GUI scales, aspect ratios, FOV settings, left- and right-hand use where supported, and repeated resource reloads. Shader or alternative-renderer compatibility needs its own tested configuration and exact dependency versions.

**8. Gun packs, assets, resources, and Lua**

Audit the complete built-in pack, including the source path `assets/tacz/custom/tacz_default_gun`, its export behavior, and all related resources. Inventory guns, ammunition, attachments, skins, models, textures, sounds, language entries, recipes, tags, animations, scripts, and pack metadata by identifier and reference.

Preserve pack discovery, supported directory and ZIP layouts, namespaces, override priorities, enablement, conversion behavior, validation, error reporting, resource reload, and server synchronization. Verify the source's handling of both `/tacz reload` and Minecraft resource/data reload workflows before implementing their target equivalents.

Check every built-in reference for a resolvable target. Detect missing content, duplicate IDs, invalid model references, absent textures or sounds, unsupported pack metadata, and incomplete generated data. Adapt Minecraft-facing resource formats and directory conventions to the actual target without unnecessarily changing TACZ's public pack format.

Preserve existing pack compatibility wherever technically possible through loaders or explicit conversion. Exercise both ZIP and directory packs when supported, including custom namespaces, overrides, repeated reloads, malformed entries, and nonstandard packs. Report unsupported cases precisely rather than silently dropping them.

Port Lua execution, exposed APIs, constants, callbacks, animation state contexts, gun-logic state machines, resource lookup, timing, and exception reporting. Verify the actual LuaJ fork and MAE integration in the dependency graph. Test real shipped scripts and representative custom scripts, including cancellation and reload lifecycle behavior.

Audit server-provided resource data and archive handling for bounded payloads, valid paths, lifecycle cleanup, and correct ownership. Malformed data must not corrupt the active pack cache or leave a partially applied reload.

The [project README](https://github.com/Delinquent-Codex/TACZ/blob/b43eb84c38e9768d8e73c8b14f0b845669704b38/readme.md) identifies GPL-3.0 for code and CC BY-NC-ND 4.0 for assets. Preserve attribution and notices, inspect bundled dependency terms, and track asset provenance. Do not assume the code license also permits every asset adaptation or redistribution. Record any concrete unresolved distribution permission separately while continuing authorized implementation and local validation.

**9. Networking and dedicated-server parity**

Inventory every message, codec, direction, sender, recipient, handshake phase, synchronization trigger, and cache. Port them to the supported Forge 26.2 networking model with explicit version negotiation and suitable threading.

Preserve gun-pack synchronization, entity-data mappings, initial login state, timestamps, tracking updates, shot and reload events, crafting results, refit updates, item swaps, sound events, and other source messages. Resolve login/configuration/play lifecycle differences from target sources.

Keep gameplay authoritative on the server. Validate claimed items, ammunition, attachment operations, crafting inputs, timing, target references, and permissions. Client prediction must reconcile with server state without double firing or double consumption. Check malformed packets, out-of-order actions, and repeated messages where relevant.

Test two independent clients connected to a dedicated Forge 26.2 server. Verify visibility and sound for the shooter, remote observer, late joiner, and reconnecting player. Check reloads, refits, death, respawn, dimension changes, inventory transfers, and pack/config changes across those roles.

Test protocol mismatch and missing or incompatible content with clear errors. Distinguish a target-client/target-server compatibility check from cross-version networking; do not claim that 1.20.1 clients can join a 26.2 server.

**10. UI, crafting, configuration, commands, and content**

Port all discovered screens, menus, widgets, overlays, tooltips, toast notifications, keybindings, and config controls. Include the gunsmith table, refit screen, pack progress/list UI, gun HUD, heat indicator, interaction hints, and other registered UI. Check both visual layout and server-backed transactions.

Preserve normal clicks, shift-clicks, drag operations, slot restrictions, recipe selection, recipe filters, crafting outputs, consumption, and rejected transactions. Test survival and creative behavior separately where they differ.

Preserve every block and entity, including gunsmith-table variants, targets, statues, and target minecarts where present. Check placement, state, rendering, menus, persistence, break behavior, drops, loot, and recipes.

Preserve common, client, and server configuration defaults, validation, overrides, persistence, and synchronization. Preserve command names, arguments, suggestions, permissions, effects, and failure messages. Audit all subcommands rather than implementing only reload.

Preserve translations and translation keys. Inventory the complete shipped language set and detect lost keys. Retain accessibility and input options supported by the baseline, including mouse sensitivity, aiming options, and controller integration where available.

**11. Dependencies, optional integrations, and public API**

Build a dependency matrix recording source version, purpose, scope, target availability, target coordinates, transitive requirements, bundling strategy, relevant notices, and validation status.

The inspected build references Simple Bedrock Model, MAE, LuaJ, Commons Math, BCEL, Mixin/MixinExtras, JEI, Cloth Config, Player Animator, Shoulder Surfing, controller support, shader/rendering integrations, KubeJS/Rhino/Architectury, and development/test mods. The `libs` directory includes version-specific model and Oculus JARs. Confirm the actual role and transitive closure of each; do not treat every development dependency as a required end-user mod.

Audit the actual compatibility packages, including accelerated rendering, Carry On, Cloth Config, Controllable, JEI, KubeJS, Oculus, OptiFine, Player Animator, and Shoulder Surfing. Discover any additional paths through mixins, service registration, or reflection.

For every required library, use a verified 26.2-compatible release, port the necessary permitted source, or implement a complete equivalent with behavior tests. Keep that work within a bounded supporting dependency scope. A placeholder renderer, empty animation bridge, or fake library does not satisfy the requirement.

For each optional integration, check whether the companion project supports Forge 26.2. Preserve TACZ's integration when a compatible dependency exists and test both dependency-present and dependency-absent configurations. Keep unavailable integrations isolated so core startup remains functional, but mark their parity as externally blocked. Do not claim compatibility based on an annotation, a source-only adapter, or a NeoForge artifact.

Preserve public APIs, events, item builders, gun operators, resource access, attachment modifiers, and scripting hooks. Preserve their semantics where binary compatibility is impossible across the Minecraft change. Document source migration requirements and provide small real examples or fixtures that compile against the target API.

“Everything” means everything in the chosen TACZ baseline and its integration surface. Inventory referenced third-party packs and add-ons as compatibility subjects; do not silently turn this into ownership of every separate project on CurseForge. Report inaccessible or unavailable compatibility subjects honestly.

**12. Implement in a practical sequence**

Keep a short dependency-aware plan and move directly from discovery into code changes. A reasonable sequence is:

1. Baseline provenance, target verification, inventories, and initial build configuration.
2. Registries, metadata, common/server separation, and dependency resolution.
3. Persistence, item data, resources, pack loading, and scripting.
4. Authoritative mechanics, entities, synchronization, and networking.
5. Rendering, animation, audio, input, and UI.
6. Crafting, commands, remaining content, and optional integrations.
7. Migration checks, packaged-artifact runtime tests, performance checks, and release documentation.

Adjust this sequence when actual dependencies require it. Use small coherent changes and targeted checks. Preserve progress between sessions. Avoid endlessly rewriting plans or repeatedly applying bulk replacements without understanding the remaining failures.

**13. Validate behavior and the actual release JAR**

Use meaningful automated tests for changed logic, serialization, packet codecs, schema conversion, and resource reference integrity. Use the target's supported game-test or integration facilities where appropriate. Mocks may isolate logic but must not stand in for actual mod loading or rendering evidence.

At minimum, complete and record these gates:

| Gate | Required evidence |
| --- | --- |
| Reproducible build | Exact source revision, pinned toolchain, wrapper command, exit status, resolved dependencies, and artifact path |
| Complete packaging | Metadata, classes, resources, mixins, required nested libraries, notices, and absence of obsolete or missing runtime dependencies |
| Dedicated-server startup | Clean target installation reaches a usable world and shuts down cleanly without client-class loading failures |
| Client startup | Clean target installation reaches the title screen and an interactive world without missing assets or required injection failures |
| Gameplay | Fire modes, reload paths, attachment operations, crafting, damage, state transitions, and baseline special cases behave correctly |
| Visual/audio parity | Recorded observations or captures covering representative model and animation paths, scopes, UI, particles, local audio, and remote audio |
| Multiplayer | Two-client dedicated-server scenarios covering synchronization, reconnect, respawn, dimension changes, and authoritative state |
| Content coverage | Every built-in definition is enumerated and validated; every distinct mechanic, model path, and scripted behavior has appropriate runtime coverage |
| Pack and script support | Default pack plus representative external/custom fixtures load, execute, override, synchronize, and reload correctly |
| Persistence | Non-default item/config data survives save/load and supported conversions preserve its meaning |
| Integration coverage | Exact optional-mod configurations tested; absent dependencies handled; unavailable cases listed |
| Performance and stability | Measured representative load, sustained use, repeated reloads, and resource cleanup with methodology and limitations |

Build with the repository wrapper using the tasks actually provided by the verified target toolchain. On Windows use `.\gradlew.bat`; on Unix use `./gradlew`. Confirm task names before using them. Record commands that were actually executed, not a list of commands that might work.

Test the produced installable JAR in clean client and dedicated-server installations. A Gradle development run can conceal packaging and classpath defects. Re-test relevant runtime gates after changes that affect the final artifact; associate evidence with the tested commit and artifact hash.

Compare performance under a documented workload and environment. Distinguish Minecraft-version differences from changes attributable to the port. Investigate concrete frame-time, tick-time, allocation, network, or resource-leak regressions. Do not report guessed FPS, zero bugs, or exhaustive compatibility.

If GUI, GPU, account access, another client, or a dependency prevents a test, finish all executable checks and mark the exact remaining gate unverified. Provide a reproducible procedure and expected result. Missing runtime evidence prevents an unconditional full-completion claim.

**14. Deliverables and continuity**

Deliver the modified repository and the actual installable Forge 26.2 JAR. Use a clear filename reflecting Minecraft and mod versions. Provide its path, SHA-256, source revision, required Java/Forge versions, installation instructions, and exact build command. Provide source artifacts where the project requires them.

Keep concise, maintained documentation under `docs/porting/`, adapting existing project conventions when appropriate:

- `status.md`: current state, decisions, exact blockers, recent verification, and next concrete steps.
- `feature-parity.md`: exhaustive feature/content ledger with evidence.
- `dependencies.md`: dependency and optional-integration matrix.
- `migration.md`: gun-pack, saved-data, configuration, and API migration details.
- `verification.md`: commands, tested revisions/artifacts, results, relevant logs, and remaining manual procedures.
- `release-notes.md`: supported environments, installation, changes, known limitations, and distribution-specific issues.

Before a context limit or unavoidable pause, save a checkpoint with the branch, baseline and current revisions, dirty-worktree state, completed subsystems, failing checks, and next actionable step. On continuation, read that checkpoint and inspect the actual files before resuming. Continue from existing progress without redoing completed discovery or claiming work ran while the session was inactive.

Keep progress messages short and factual: what works now, the current obstacle, and the next step that addresses it. Prefer implementation and evidence over repeated promises of completion.

**15. Definition of complete**

You may describe this as a complete port only when every in-scope source feature and content entry has a verified target equivalent, the installable artifact passes the required client/server and multiplayer gates, supported migrations are validated, and no required function survives only as a stub, exclusion, disabled hook, or untested assumption.

Report core behavior, optional integrations, legacy-data compatibility, and release/distribution readiness separately. An unavailable companion mod, missing runtime test, or unresolved packaging requirement remains a visible gap. Do not relabel partial work as complete by changing the scope or hiding blocked ledger rows.

Your final report must state the exact target and source, what was delivered, where the JAR is, how to build and run it, what was actually tested, and every remaining limitation. If a full port is blocked, deliver all completed work and identify the smallest concrete action needed to continue.

Begin now: inspect the existing TACZ-26.2 workspace, establish the source baseline, verify the official Forge 26.2 toolchain, create the inventory, and proceed into implementation. Continue through the full port and validation workflow for as long as the environment allows useful authorized progress.

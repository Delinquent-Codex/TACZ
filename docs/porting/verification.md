# Verification log

Date: 2026-09-07. Windows 11 amd64. Baseline revision: `b43eb84c38e9768d8e73c8b14f0b845669704b38`. Initial implementation checkpoint is `eb33814c`; subsequent local progress is recorded below on `port/forge-26.2`. Reference workspaces and complete command output live in `../TACZ-port-reference`; relevant logs will also be checkpointed under `evidence/`.

| Workspace / command actually executed | Toolchain | Result | Evidence |
| --- | --- | --- | --- |
| Baseline `git clone --branch 1.20.1 --single-branch … .` | Git 2.53.0.windows.3 | pass, exact reference commit | Origin and local worktree refs |
| Baseline `.\gradlew.bat --version --console=plain` | Temurin 17.0.20.1+1 | pass, Gradle 7.5.1 | baseline-gradle.txt |
| Baseline `.\gradlew.bat tasks --all --console=plain` | Same; original unmodified build | failed after 16m53 with NoSuchElementException | baseline-tasks.log |
| Baseline `.\gradlew.bat tasks --all --stacktrace --console=plain` | Same, populated caches | pass in 8s; original failure did not reproduce | baseline-tasks-stacktrace.log |
| Baseline `.\gradlew.bat build jarJar --console=plain` | Same, Forge 1.20.1-47.3.19 | pass in 1m19; no source tests in baseline | baseline-build.log; baseline build/libs |
| Official MDK `.\gradlew.bat --version --console=plain` | Temurin 25.0.4.1+1 | pass, Gradle 9.5.0 | mdk-gradle.txt |
| Official MDK `.\gradlew.bat tasks --all --console=plain` | ForgeGradle 7.0.36 / Forge 26.2-65.1.0 | pass, generated exact target game sources | mdk-tasks.log |
| Official MDK `.\gradlew.bat build --console=plain` | Same | pass, example mod only | mdk-build.log |
| TACZ `.\gradlew.bat tasks --all --console=plain` | Pinned target build | initial custom-run DSL error corrected using inspected ForgeGradle source; pass | target-tasks.log |
| TACZ `.\gradlew.bat compileJava --console=plain` | Target, all main source files | failed at 1,000-error reporting limit, twice | target-compile-01.log, target-compile-02.log |
| TACZ `.\gradlew.bat compileJava verifyPortingPrimitives --continue --console=plain` | Target, all main source files | full compilation failed at 1,000-error limit; 29 isolated primitive checks passed | target-compile-03.log |
| TACZ `.\gradlew.bat verifyPortingEvents --console=plain` | EventBus 7.0.5 annotation validator and strict runtime checks | pass, 13 event-contract assertions; no KubeJS or game runtime | event-checks.log |
| TACZ `.\gradlew.bat verifyPortingNetwork --console=plain` | Actual target packet classes, registry fixture, component codecs | pass, 61 assertions after correcting two fixture constant names; no live connection | network-checks.log |
| TACZ `.\gradlew.bat compileJava verifyPortingEvents verifyPortingNetwork verifyPortingPrimitives --continue --console=plain` | All main source files; 5,000 diagnostic limit | main compilation failed with 1,132 errors; focused checks passed 15/61/29 respectively; 14s | evidence/target-compile-06.log |
| TACZ `.\gradlew.bat dependencies --configuration runtimeClasspath --console=plain` | Target | pass; required model/MAE closure still incomplete | target-runtime-dependencies.log |
| TACZ `.\gradlew.bat verifyPortingPrimitives --console=plain` | Real target classes and registry fixture | pass, 29 assertions; no FML mod load, client/server or gameplay test | primitive-checks.log |
| `python tools/porting/inventory.py --baseline ../TACZ-port-reference/baseline-1.20.1` | Bundled Python, stdlib | pass; baseline JSON parse errors=0 | inventory/summary.json |

## Required gates still open

The local evidence directory records selected full logs and SHA-256 hashes of all Java, resource and build inputs at this checkpoint. Earlier logs remain in the reference workspace. The baseline/reference ZIP comparison found no missing/extra members; two members differ, as described in status.md. Bytecode inspection is provenance evidence, not an observed runtime parity check. `git diff --check` with CR-at-EOL handling passed after the changes.

Full target build and dependency closure, complete nested packaging, clean dedicated-server world/start/stop, clean client title/world, all gameplay and visual/audio parity, two-client multiplayer, all definitions and mechanic coverage, real Lua and external pack reload/sync, persistence beyond focused fixtures, companion-present/absent tests, and measured performance/stability. No target release artifact hash can be supplied yet.

## Reproduction

Set JAVA_HOME to a Java 25 JDK and run the repository wrapper. Windows: `.\gradlew.bat verifyPortingPrimitives --console=plain`; Unix: `./gradlew verifyPortingPrimitives --console=plain`. Full compilation: replace `verifyPortingPrimitives` with `compileJava`. The Unix command is provided for reproduction and was not executed on this Windows host.

After full compilation and packaging work is finished, test the actual nested JAR in separate clean Forge 65.1.0 client/server game directories. Capture title/world, representative guns and all distinct mechanic/model/script paths, then connect two independent clients and test shooter/observer/late join/reconnect/respawn/dimension/item transfer scenarios. Record tested commit, JAR SHA-256, Java/Forge versions and logs for each run. No runtime gate may be marked passed by the focused primitive task or the MDK build.

## Continuation verification — 2026-09-08

- `verifyPortingContent`: 31 assertions passed: all six particle directions through real data/network codecs, default display ID, invalid/truncated data, painting asset/key/size/metadata and placeable reference.
- `verifyPortingCrafting`: 421 assertions passed: real Minecraft 1.20.1-to-26.2 item fixes for names/damage/painting variants/extensions; invalid item inputs; old ingredient alternatives; overlapping requirements, insufficient totals, integer-overflow case, 400 independent arithmetic allocation cases; split outputs retain 100 items. The fixture initializes a real Forge ingredient registry and real CompoundIngredient serializer. Its initial missing-registry failure was corrected in fixture setup, not bypassed in production.
- `verifyPortingPacks`: 25 assertions passed for real target PathPackResources and FilePackResources: namespace/legacy recipe discovery, ingredient/result conversion, duplicate-ID precedence, pass-through client data, absent resources, unchanged original folder/ZIP bytes and both pack metadata types. Client format 88.0 and server format 107.1 were read from the actual target; initial metadata failure is in compile-10.
- `verifyPortingLua`: 14 assertions passed with actual LuaJ script execution and LuaNbtAccessor: scalar writes, nested writes, current-data reads, component equality/copy isolation, external edits retained, detached snapshots, removed-child behavior, standalone NBT and Java long precision. This does not exercise shipped gun scripts, ScriptManager or game entities.
- Seventeen legacy Forge tag aliases were resolved recursively in baseline and target Forge universal JARs. Every resolved vanilla item set matched exactly. Reference and target artifact paths plus mappings are described in migration.md and inventory/recipe-tag-aliases.json. Companion-added tag membership remains a runtime gate.
- Latest command: `gradlew.bat compileJava verifyPortingCrafting verifyPortingContent verifyPortingPacks verifyPortingLua verifyPortingNetwork verifyPortingEvents verifyPortingPrimitives --continue --console=plain`. Java 25, all main sources, 5,000-error limit. **Main build failed with 1,050 diagnostics; all seven focused suites passed, 596 assertions total; 32 seconds.** See evidence/target-compile-11.log and evidence/tested-inputs.json. The prior compile-06 input manifest is retained as evidence/tested-inputs-06.json.
- `git diff --check` passed. Inventory regenerated from untouched baseline; all 183 resource relocations explicitly mapped. No main source exclusions, target release artifact, real FML launch, client/server world, multiplayer, visual/audio or performance success is claimed.

Recipe result codecs, actual registered TACZ items, crafting extraction/refund, recipe cache/network lifecycle, data reload ordering, default/custom pack content and companion integration still require live tests. These remain open even where the compiler no longer reports a source error.

## Block/item verification — 2026-09-09

The exact command and all 4,230 input hashes are in evidence/tested-inputs.json. Main compile failed with 885 diagnostics (692 client, 63 compat, 60 entity, 39 util, 13 mixin, 9 item, 6 command, 3 api). All nine focused tasks passed 636 assertions in the same invocation. evidence/target-compile-22.log records the full output; earlier input manifests for compile-11 and compile-16 are retained separately.

verifyPortingStoredData passed 31 assertions using actual Minecraft codecs, data fixers, ValueInput/ValueOutput and network buffers. Cases include nested attachment conversion/backups, custom fields, source immutability, empty and invalid items, exact recovery-item data through save/network, malformed raw values, tooltip flags, dye color, owner UUID/name/texture signature, shorthand/dynamic profiles, skin-patch preservation and component names. It does not instantiate a TACZ block entity, resolve an online profile or exercise placement/removal.

verifyPortingDye passed 9 assertions against the shipped recipe JSON and actual target DyeRecipe. A plain Item registered only in the fixture represents tacz:ammo_box; the production AmmoBoxItem class and FML lifecycle are not loaded. Tests bind the 16 actual vanilla dyes, check valid/invalid material combinations, ammo/custom-data preservation, input immutability and recoloring. Initial fixture failures are in reference logs target-compile-17/18, stored-dye-checks-19 and dye-checks-20; corrected standalone run dye-checks-21 passed. Both Forge's registry and its named wrapper must be reopened for the isolated fixture, and the real Forge ingredient registry must be initialized.

All previous focused suites still pass. The newly added ItemStackMixin has only source compilation evidence; FML/Mixin application, pack-dependent ammo limits, standalone attachment fixes, gameplay melee/movement, full scripts, block inventories/recovery, online skins, actual GUI, configuration lifecycle and pack sync remain unverified. No helper or fixture result substitutes for the required runtime/parity gates.

## Entity/explosion verification — 2026-09-09

The exact full command is in evidence/tested-inputs.json with 4,235 input hashes. compile-26 passed 651 focused assertions with 814 main errors. compile-27 passed 16 geometry and 15 projectile assertions with 796 main errors. The latest compile-28 invocation passed all eleven suites, **669 assertions**, while all main sources still fail with **796 diagnostics** (692 client, 63 compat, 16 util, 13 mixin, 9 item, 3 api). It finished in 53 seconds. Relevant complete logs are preserved in evidence/.

verifyPortingProjectileData checks a 102-byte golden fixture derived from baseline b43eb84c's writeSpawnData field order, actual target buffer round trip, every truncated prefix, tracer defaults, wrong types, short arrays and numeric size overrides. It is not a captured live spawn packet. verifyPortingExplosions checks all 15 distinct samples, nearest-visible falloff, full/partial cover, boundary/out-of-range/center cases, translation and invalid radii, plus reflection showing the actual target block/fire finalizers are protected on the transformed test classpath. These tests do not prove packaged access-transformer application, actual rays in a world, Forge explosion listeners, damage/armor, drops/fire, sound/particles or player velocity.

Target entity persistence/profile synchronization, server shooting timing, speed attributes, command effects and the explicit additional-spawn factory still require FML client/server tests. No subsystem is marked complete from compiler diagnostics alone.

## First-person/model verification — 2026-09-10

Full compile-33 failed with 705 main diagnostics, while all fourteen focused suites passed 750 assertions in 1m1s. Exact command and 4,263 input hashes are in evidence/tested-inputs.json. Prior checkpoints 29/30/31/32 and the MAE runtime dependencyInsight output are retained in evidence/. All main source files remain in compilation.

verifyPortingFirstPerson (23) exercises the actual switching controller with controlled clock/callback fixtures: vanilla/custom transitions, draw/tick callbacks, item copies, put-away deadline, rapid pending selections, slot changes, forced off-hand swaps, zero duration and reset. It also exercises unmodified MAE interpolation, sorting, endpoints, scale and additive blending with target JOML. No live inventory, ItemInHandRenderer transformation, shipped gun animation or sound keyframes were executed.

verifyPortingGeometry (20) compares real Bedrock cube/per-face output against source-derived positions, UVs, normals, mirror/rotation/translation, colors and packed lighting; checks immutable snapshots/replay and nested collector scope cleanup. It uses CPU consumers and a collector proxy, not a graphics device. GPU ordering, translucency, scope/stencil state and visual performance remain unverified.

verifyPortingItemModels (38) decodes seven shipped custom model definitions through actual target ItemModels dispatch, checks their base models/display resources, decodes three native model definitions, compares all nine ammo thresholds/model references against baseline, and executes the actual target Dye codec and component color selection. It does not run resource reload/model baking, the full range-dispatch property, a TACZ item instance or FML. Missing-renderer behavior uses Minecraft's missing model, not an empty success.

MAE 1.1.2 resolves on runtimeClasspath with Java 25 (dependencyInsight; evidence/target-mae-runtime-31.log). Actual nested-JAR inclusion and clean client/server runtime closure require the target release build, which still fails. No graphics or gameplay parity is inferred from these checks.

## World rendering/movement verification — 2026-09-11

compile-39 failed with 556 main-source diagnostics while all seventeen focused suites passed 1,115 assertions in 1m10s. evidence/tested-inputs-39.json retains the exact command and 4,273 input hashes. After correcting the minecart's superclass name/leash submissions and adding a Mixin feature scan, compile-40 failed with the same 556 diagnostics while world rendering (296), bullet holes (57), and walking distance (13) passed, 366 assertions in 15s. Current input hashes are in evidence/tested-inputs.json. The cumulative 1,116-assertion suite has not all been repeated together after the last change.

verifyPortingWorldRendering checks source-derived rail origins, yaw, content scale/offset, hurt direction, tracer dimensions/visibility/decay, bone hierarchy, inherited full light, hidden functional callbacks, exception pose restoration and deferred-work cleanup. It parses all 264 default-pack and five internal models with the production POJOs/CubesItem deserializer, constructs the actual BedrockModel, submits to a CPU collector, and checks 24 vertices per source cube with finite positions/normals/UVs. It does not invoke specialized gun/attachment functional models, texture loading, actual animation scripts, skin services or a GPU.

verifyPortingBulletHoles exercises actual QuadParticleRenderState.add/buildLayer/clear. For each face, all four emitted vertices and UVs are compared with the baseline formula, including winding and .01-times-size surface offset. Terrain transparency, tint/light packing, cooling, final fade, zero lifetime and the corrected threshold-one behavior are checked. No real particle engine/world instance is created.

verifyPortingWalkDistance checks horizontal accumulation, float scale, vertical exclusion, repeated moves cancelling net displacement, tick snapshots, extrapolation, anchors and independent instances. ASM inspection of the exact target Entity bytecode finds one getBlockSpeedFactor invocation and the live movement Vec3 local at that point, plus baseTick. The actual Mixin 0.8.7 LanguageFeatures scanner accepts the Java 25-compiled EntityWalkMixin under its declared JAVA_17 feature level. This checks bytecode prerequisites, not Mixin transformation or live collision/teleport/vehicle/multiplayer behavior.

Reference bytecode evidence is retained for source MinecartRenderer transforms/decorations, Entity walkDist/walkDistO updates and target Mixin feature validation. Follow-up visual checks must include alternating target profiles/hit angles, statue item changes, all minecart orientations/slopes, observer and first-person tracers, six bullet-hole faces, resource reload, shadows, transparency, outlines, font bidi/formatting and optional shader/rendering companions. No target JAR exists yet.

## Effects and carried-gun verification — 2026-09-11

Commands in evidence logs 41–45 compile every main source with --continue and run selected focused checks. Compile-41: 481 errors, geometry 20 passed, effects fixture failed because the baseline slot includes five degenerate quads. Correcting that expectation gave compile-42: 481 errors, effects 52 passed. Compile-43: 464 errors, effects 59 and first-person 23 passed, 15s. After tooltip types were fixed, annotation processing ran farther: compile-44 stopped early with 120 missing-type/remapping diagnostics, effects 59 passed, 5s. Explicit named mappings and the stair accessor correction gave compile-45: 99 early diagnostics, effects 59 and walk 13 passed, 7s. Later method errors are not yet enumerated again; no count here denotes a successful main build.

verifyPortingEffects checks all sixteen source beam vertices/UVs/colors/fade/light, transformed coordinates, snapshots, third-person dimensions, native pipeline descriptions, two flash layers (including retained degenerate faces), timing/scale/rotation/anchor ownership, copied carried-item transforms and exact bytecode signatures for the native arm/carried-item hooks. It checks source shader text, not GLSL compilation, GPU appearance or actual accelerated rendering. The missing-hand wrapper/first-person arm target checks do not apply Mixin. Exact input hashes for compile-45 are in evidence/tested-inputs.json.

Named target classes require no SRG refmap; remap=false removes only nonexistent name translation, not injection requirements. defaultRequire remains 1. The processor still warns about obsolete GameRenderer/HumanoidModel targets; these are outstanding failures regardless of whether javac reports a warning. Full packaged FML transformation remains required.

Compile-46 runs compileJava verifyPortingEffects verifyPortingEvents --continue --console=plain: 99 early diagnostics, effects 68 and events 15 passed, 21s. New checks cover captured hurt easing/yaw/pitch, inclusive 500 ms expiry, finished ticks, matrix order, immunity to later hit changes and actual target camera/bob/hand scope descriptors. GameRenderer's missing target warnings are resolved; HumanoidModel's old setupAnim is still invalid. See evidence/tested-inputs.json (4,283 hashes). These remain bytecode/data checks, not FML transformation or live camera evidence.

## Humanoid and GUI verification — 2026-09-12

Compile-47/48 retain failing restoration fixtures (late-added children, then live scale). The production capture now traverses the current model hierarchy and captures all nine PartPose values explicitly, plus visibility/skipDraw. Compile-49 passed third-person 19 with 98 early main diagnostics. Native model/armor hold and aim values and failure cleanup are checked; actual entities, transformed mixins, queued renderer behavior and companions are not.

Compile-50/51/52 continued migrating GUI types: 56/39/17 early main diagnostics. HUD layer checks first passed five ordering/condition/hook assertions. Compile-53 enabled the opt-in JDK25 FLOW diagnostic policy and exposed 211 errors in method bodies while preserving processors and all sources. Compile-54 corrected GUI constructor/color/interpolation/tooltip/scissor APIs: 194 errors, HUD 13 and third-person 19 passed, 18s. Exact command and all 4,296 hashes are in evidence/tested-inputs-54.json. The ordinary build remains failed; FLOW is diagnostic only and does not prove code generation or packaging.

The 13 HUD assertions invoke the actual ForgeLayeredDraw on an isolated layer graph with recording callbacks, then compare preview state/matrices against the baseline viewport and transforms at three zoom scales. They do not create a GUI graphics device, load fonts/textures or apply HudMixin. Extra production widget classes compile in the focused source set. The retained checkbox atlas is an unchanged Minecraft 1.20.1 asset (668 bytes, SHA-256 99fefa4fba7d408b46f7b8f239726cb1add828c21f8c6c12c38dc7590a48d7fb); its use, screen backgrounds, native item lighting and preview composition remain visual gates. The source GunLevelUpToast was unused with drawing commented out before this port.

Cumulative focused-suite total is 1,216 across 20 tasks; they have not all been repeated together after this checkpoint. Compile-54 still diagnoses one private selected-slot access in GunRefitScreen (the earlier blanket GUI statement was incorrect), and no native GUI, renderer, sound, gameplay, FML/server/client or installable target artifact has been verified.

## Input, resources, particle options and companion verification — 2026-09-12

Compile-55: 133 all-source FLOW errors, HUD 35 + crafting 424 + first-person 23 + item models 38 = 520 passing assertions, 29s. The new HUD coverage includes the native key category and all 21 locale aliases. Crafting uses the actual display Gson adapter for legacy damage/name/extensions and native components.

Compile-56 first switched to the real Shoulder Surfing Forge 26.2 artifact and added the particle adapter: 138 main errors plus one focused compile error. It exposed changed companion APIs and a wrong HolderLookup.Provider operation helper; no particle test passed in this run. Those errors were corrected in source. Compile-57: 130 main errors, particle options 43 passed, 14s; exact hashes in tested-inputs-57.json.

Compile-58 ran every focused task together and passed **1,284 assertions across 21 suites**; main compile still failed with **130 errors** in 1m21s. Full command, result lines and 4,300 source/build/resource/library hashes are in evidence/tested-inputs-58.json. All main sources and annotation processors remain enabled. The optional JDK25 FLOW flag only continues attribution for diagnostics; it does not generate successful mod classes from failed inputs.

Build-59 ran the ordinary `.\gradlew.bat build jarJar --console=plain`, without the FLOW flag: exit 1, 15 early missing-type diagnostics, 4s. See evidence/target-build-59.log and tested-inputs-59.json. JarJar never produced an installable TACZ artifact. The current error inventory deliberately retains the more complete FLOW-58 diagnostics.

Particle checks compare legacy dust/transition channel order and scale clamps, all three old block variants and state properties, item NBT data fixing (damage/name/extension), sculk roll, shriek delay, vibration coordinate rounding/timing, TACZ bullet-hole positional data, native SNBT/component forms, malformed input rejection and all eight active shipped particle entries. Baseline command-parser behavior was inspected in the actual 1.20.1 SRG JAR bytecode; target options/parser sources come from the pinned Forge Mavenizer source JAR. GPU/provider appearance and custom pack execution remain unverified.

Shoulder Surfing's downloaded runtime and API source artifacts matched published hashes. Actual target source confirmed plugin discovery still reads shouldersurfing_plugin.json. Baseline registrar bytecode confirmed the old Predicate checks both hand slots. The migrated adapter has no errors in FLOW-57/58, but has not been loaded through FML or the companion's event bus; no live crosshair/recoil or absent-mod startup claim is made.

Checkpoint audit: all 4,300 working input hashes matched FLOW-58 after documentation updates; the untouched baseline worktree was clean, inventory regeneration reported zero JSON parse errors and no unaccounted baseline files, and the Gradle-resolved Shoulder Surfing runtime JAR matched the inspected SHA-256. `git -c core.whitespace=cr-at-eol diff --check` passed; this accommodates the existing CRLF-indexed zh_cn.json without changing its line endings.


## Native scope backend — 2026-09-13 checkpoint

- Run 60: new production backend classes compiled; FLOW main remained 130 errors. The CPU suite reached a fixture-only Identifier constructor error (the native String pipeline-location overload assumes the default namespace). Corrected to the Identifier overload.
- Run 61: scope CPU suite passed 241 assertions; Effects 68 and Geometry 20 also passed; main remained 130.
- Runs 62–64: offscreen harness fixes used the public GlBackend factory, supplied target LWJGL native artifacts, and corrected fence creation to occur **before** submit. These attempts did not pass the GPU suite.
- Run 65: first OpenGL pass, 12 real pixel checks. Runs 66–67 exposed missing native-library bootstrap and game-version initialization in the standalone Vulkan harness; no Vulkan pass was claimed for those attempts.
- Run 68: OpenGL and Vulkan each passed 12 pixel checks after calling the real NativeLibrariesBootstrap and SharedConstants version detection. Run 69 adds the production aperture fan and passes **14 pixel checks on each backend**.
- Run 69 executes all **24** verification tasks together: **1,553 assertions** (1,525 CPU/description/codec assertions plus 28 GPU pixel assertions). Full FLOW main compilation remains 130 errors. Total command duration 2m05s. Full command and 4,309 input hashes: `evidence/tested-inputs-69.json` / current tested-inputs.json; complete log: `evidence/target-compile-69.log`.
- Run 70: ordinary `.\gradlew.bat build jarJar --console=plain` still fails with 15 early missing-type errors in 5s. `evidence/tested-inputs-70.json` records the same unchanged source/build inputs. Primary compile-errors.json retains run 69's fuller FLOW diagnostic inventory.

The hidden-window tests create real target GPU devices, pipelines, textures, native staged vertex/index buffers and readbacks. They execute ScopeFeatureRenderer, ScopePipelines, ScopeShader and ScopeAperture. Fixture shader source and prepared texture/transform context are explicit constructor inputs; no main class is stubbed or excluded. These tests do not instantiate Minecraft/FML or prove entity/text/laser shader compatibility, actual gun visual parity, lifecycle integration or runtime performance. Vulkan validation layers were disabled. Scope-rendering.md records devices, constraints and next integration work.

Reproduce the GPU fixtures with Java 25 using `verifyPortingScopeGpu` and `verifyPortingScopeVulkan`; they require functioning corresponding host graphics drivers and use hidden windows. The dedicated portingGpuNatives configuration is test-only and matches the target's LWJGL 3.4.1. Normal compile/build tasks continue to include every main source.


## Native feature capture, scope integration and actual shaders — 2026-09-13

- Run 71: native CustomFeatureRenderer bridge exercised through both GPU backends (14 pixel checks each), plus scope CPU 241; main FLOW still 130. Required phase Mixins and narrow protected-f native-builder access transformation added. No FML launch occurred.
- Run 72: the new native builder fixture failed an equality assertion because PreparedRenderType includes ScissorState identity. Run 73 compared captured geometry instead and passed 20 native assertions; main still 130.
- Run 74: non-accelerated attachment/gun scope wiring plus distinct output handling; scope 241, native 27, OpenGL/Vulkan 18 pixels each, geometry 20 and effects 68 pass. Main errors dropped to 110. Run 75: explicit blit migration, main 101; focused fixture compilation failed on a compound var declaration. The failed run is retained.
- Run 76: native Model.Simple arm snapshot and texture-blit fixtures pass; scope 241, native 29, OpenGL/Vulkan 18 pixels each, geometry 26, effects 68. Main FLOW 100. Run 77 adds actual target/TACZ GLSL compilation; both backends pass 69 shader/version assertions plus 18 pixels each, main 100.
- **Run 78 executes all 25 tasks together and passes 1,734 assertions**: 1,560 CPU/native-description/codec checks, 36 GPU pixel checks and 138 target shader compilation/version checks. Main FLOW compilation still fails with 100 errors; command duration 1m24s. Full command/result lines and 4,317 source/build/resource/library hashes are in evidence/tested-inputs-78.json. Primary error inventory retains this fuller failed compile.
- **Run 79 ordinary build:** `.\gradlew.bat build jarJar --console=plain`, exit 1, 15 early missing-type errors, 3s. Same source/build inputs as 78; tested-inputs-79.json and target-compile-79.log retain evidence. No target mod JAR was produced.

Native checks exercise actual Custom/Model/Text/Item/BlockModelFeatureRenderer code, real glyph layout/shadows with a supplied fixture glyph, tint/light/overlay, Simple-model pose freezing, scope sequence and cleanup. The target getVertexBuilder method is actually access transformed on the focused runtime classpath. Capture method calls in these tests do **not** establish Mixin interception or FML registration. Native item foil setup is reused but not runtime-tested with Minecraft/atlas context.

Separate-output pixel checks preserve an unmasked smaller target and masked same-size outputs with independent depth rejection. The fixtures still use simple geometry/shaders for pixel colors; they do not establish fabulous frame-graph composition. The additional shader tests compile the real pinned resource text for 17 native/TACZ pipelines and three scope variants per pipeline, with the native GLSL preprocessor/import rules. They do not bind real game textures, bake assets or draw actual guns.

Both GPU tasks read Mavenizer's 26.2 client.jar, SHA-256 `40896ee9f1e2bec3c934daac7e93d41e9e3d9c2f8ae0ca366d52ffbfd1afa290`; logs record the file/hash/version. Override a different cache layout with `-PportingClientAssets=<absolute client.jar path>`. The current target's native OpenGL/Vulkan devices are used; Vulkan validation layers remain disabled. No actual game/FML/companion/release gate is closed by this checkpoint.


## Script recipe data and JEI ingredient display — 2026-09-13

Run 80: main FLOW dropped to 87 errors; Lua 14 and events 15 passed. A new painting round-trip fixture failed because it created a different registry lookup after decoding the holder. Native codecs correctly rejected that holder ownership mismatch. Run 81 reuses the decoding lookup and separately verifies rejection with a missing dynamic registry: crafting **433**, Lua 14, events 15 passed; main remained 87.

Run 82 includes the native JEI display migration and runs all **25** tasks: **1,743 assertions pass**, main FLOW fails with **86 errors**, duration 1m23s. Breakdown: 1,569 CPU/native-description/codec assertions, 36 GPU pixel assertions, 138 target shader compilation/version assertions. Exact command and 4,318 input hashes are in evidence/tested-inputs-82.json and current tested-inputs.json. No source exclusions or disabled injection requirements are introduced.

Run 83: ordinary `.\gradlew.bat build jarJar --console=plain`, exit 1, **13 early missing-type errors**, 3s. Same source/build inputs, recorded in tested-inputs-83.json; primary compile-errors.json retains FLOW-82. No installable artifact exists.

The new ScriptRecipeData tests run its real target codecs without KubeJS: component serialization, legacy tag replacement before data fixing, unknown extension fields, source immutability, native/legacy ingredient alternatives, painting holder round trips, missing registry rejection and startup registry fallback. Custom gun registration, KubeJS/Rhino execution, and JEI GUI/layout/reload behavior are not tested. Old companion binary signatures remain temporary scaffolding and prevent release readiness.

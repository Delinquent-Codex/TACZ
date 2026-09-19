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

The core build and nested packaging pass as of run 101; the installed dedicated server completes world/start/reload/save/stop with separately recorded host diagnostics. The complete packaged mod passes instrumented installed-client gameplay and TACZ-only saved-world/normal-close checks on both backends (see installed-client checkpoint below). Still open: complete gameplay and visual/audio parity, broader multiplayer transitions, every mechanic/content path, external pack sync, persistence beyond focused fixtures, companion-present behavior and measured performance/stability. A development candidate hash is recorded below; no release is approved.

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

## Production scope through FML and live client assets — 2026-09-13

The separate test project `tools/porting/scope-fixture` compiles the actual main scope classes, three scope Mixins and vertex helpers. It generates its selected Mixin list from the production JSON (retaining required/defaultRequire=1), applies the production AT and uses Forge's real client launch. Root main sources, dependencies and Mixin configuration are unchanged. This is not a build or launch of the full TACZ mod.

Executed from the repository root with `JAVA_HOME=C:\Users\voltp\Downloads\TACZ-port-reference\jdk25\jdk-25.0.4.1+1`:

```powershell
.\gradlew.bat -p tools/porting/scope-fixture verifyScopeFml -PfixtureGraphicsBackend=OPENGL --console=plain
.\gradlew.bat -p tools/porting/scope-fixture verifyScopeFml -PfixtureGraphicsBackend=VULKAN --console=plain
```

- `scope-fml-16`: **exit 0, 48 assertions, 24s**, actual OpenGL 4.6 core, AMD Radeon 760M, driver 26.8.1.260810; persistent mapping and direct state access enabled.
- `scope-fml-17`: **exit 0, 48 assertions, 23s**, actual Vulkan 1.4.351, NVIDIA RTX 5050 Laptop, driver 616.92; uniform alignment 64. The task rejects a fallback to another graphics backend.
- Each creates its own UUID-named flat world with seed 262 under the fixture run directory, joins through an integrated server, and logs orderly disconnect, world/chunk saving and shutdown. Only Minecraft, Forge and `tacz_scope_fixture` are loaded. This is **not dedicated-server or TACZ gameplay evidence**.
- The first 14 checks cover actual AT access, dispatcher constructor injection, simple/translucent phase interception and pass-through, submission/mask order, immutable Model.Simple geometry, completed-job submission, exception cleanup and dispatcher execution. Ten more use real font glyphs/shadows and real baked sword/compass models with standard and special glint. No registry-binding or fake item-model/font services are used.
- **24 live GPU checks per backend** draw four scenes at 128x128 through the real dispatcher, ShaderManager and texture bindings: vanilla font/shadow, a textured panel, an enchanted diamond sword and an enchanted compass. Each checks nonempty native output, allowing/rejecting masks, writes without color changes, alpha-tested ocular writes, and inversion. Font/panel permitting outputs equal every native RGBA byte. Glint UVs depend on wall time; its comparisons require identical alpha coverage and make no claim about identical RGB. Mask rejection leaves all bytes zero. This complements the older standalone pixel/shader tests; it does not establish actual TACZ gun visuals, camera transforms or performance.

`scope-fml-16-inputs.json` and `scope-fml-17-inputs.json` were written **before launch** from Gradle's actual compilation inputs and resolved runtime classpath, with SHA-256 hashes. Matching result JSON, complete Gradle logs and debug logs are retained in evidence/. Root run 82's 4,318 source/build/resource/library hashes were rechecked with zero changes, so its 25-suite / 1,743-assertion result and 86-error FLOW compile remain the latest root evidence. No new root build success is implied.

Fixture development failures are retained: 01/02 corrected AT/task configuration, 03 corrected target GUI access, 04 corrected Groovy String/GString selection, 05 exposed separate class/resource outputs and loader conflicts (fixed with ForgeGradle's merge-source-sets setting). Run 06 loaded but did not finish its menu callback and was stopped; run 07 uses a render event and passed 14 checks. Run 08 fixed static ModList access. Run 09 passed the font checks but item construction failed because 26.2 default components had not been bound by world loading; run 10 entered a real world and passed 24. Run 11 fixed the new test's target texture/render-type signatures. Runs 12/13 reached live GPU comparison but the test incorrectly assumed JOML's default Vector4f had zero alpha; its denied image was entirely opaque black. An explicit transparent clear fixes the test, with no production renderer change. Run 14 passed 36 checks on OpenGL; run 15 passed 36 on Vulkan. Runs 16/17 add mask writes/inversion and are the final evidence above. Routine OSHI process-information and unauthenticated Realms warnings are present; no Realms/multiplayer validation is claimed.

Remaining gates: compile and load full TACZ alongside every production Mixin; installable JAR/dependency closure; dedicated server, actual guns/packs/scripts, gameplay/two-client behavior, migrations, external companions, fabulous composition and representative performance. The fixture does not close those gates.


## Native scope entry points, AR consumer boundary and JEI API — 2026-09-14

Continued from local checkpoint 12b4be7e. All required main sources, annotation processors and Mixin requirements remain enabled. The original accelerated GL stencil routines are replaced with the native ordered renderer; public entry points remain native source bridges. No AR compatibility or performance claim is made. The separate Forge fixture invokes the same new ScopeCapture.submit helper in its native pixel tests.

| Run | Command / scope | Actual result |
| --- | --- | --- |
| 84 | compileJava + AR consumer, scope and native-scope tasks, diagnostic FLOW / continue | Main 30 errors; scope 241 + native 29 pass. New consumer test fails loading lwjgl.dll. 13s. |
| 85 | Same requested tasks, test native runtime added | Main 30 errors; focused compilation fails because NativeLibrariesBootstrap.loadLibraries requires IOException handling. 37s. |
| 86 | compileJava + all 27 focused tasks, diagnostic FLOW / continue | Main 30 errors; all 1,754 assertions pass, 1m49s. |
| 87 | compileJava -PportingDiagnosticFlow | After JEI common API migration: 25 errors, no JEI diagnostics, 14s. |
| 88 | compileJava + all 27 focused tasks, diagnostic FLOW / continue | Main 25 errors; all 1,754 assertions pass, 1m42s. |
| 89 | build jarJar | Ordinary compiler fails early with 10 errors, 5s. No TACZ JAR. |

Every command starts with `.\gradlew.bat` and uses `--console=plain`. Exact expanded command strings and input hashes are in evidence/tested-inputs-84.json through tested-inputs-89.json. The current primary tested-inputs.json and compile-errors.json retain diagnostic run 88; ordinary run 89 is separate. Each manifest was recorded from completed output before changing tested inputs. Current root inputs total 4,319. The eleven new checks are six consumer capability checks with the actual old AR interface and five facade-linkage checks with the actual AR JAR absent. The test corrects its missing native library/bootstrap setup; no product failure was hidden. Aggregate 1,754 comprises 1,580 CPU/native/API/codec assertions, 36 standalone GPU pixel checks and 138 target shader compilation/version checks.

The actual legacy AR `VertexConsumerExtension.getAccelerated` bytecode casts directly to IAcceleratedVertexConsumer. The native target BufferBuilder and TACZ VertexCapture do not implement that companion interface. ARCompatImpl.isAccelerated now uses instanceof, and the BedrockPart Mixin checks capability before casting. The positive accelerated path, mesh caching, companion FML injection and runtime remain unverified. `verifyPortingARAbsent` validates that CoreFeature is not found on its test classpath before exercising the real facade; changing only a LOADED flag is not counted as absence of an artifact.

The JEI common API 30.32.0.221 was downloaded from its official Maven repository, with published SHA-1 checks and retained SHA-256/source/POM evidence. Its 181 Java sources have no loader imports; the JAR has no mod descriptor. `dependencyInsight --dependency jei --configuration compileClasspath --console=plain` and the corresponding runtimeClasspath command passed: only the new common API resolves for compilation; no JEI runtime dependency matches. No Fabric/NeoForge runtime is installed or used as a Forge substitute. API migration includes recipe types, dimensions, GUI extraction and subtype null semantics. Complete TACZ code generation, actual JEI initialization/UI, layout/recipe refresh, and dependency-present compatibility are not established by a failed FLOW compile with no JEI diagnostics.

Separate fixture commands remain:

```powershell
.\gradlew.bat -p tools/porting/scope-fixture verifyScopeFml -PfixtureGraphicsBackend=OPENGL --console=plain
.\gradlew.bat -p tools/porting/scope-fixture verifyScopeFml -PfixtureGraphicsBackend=VULKAN --console=plain
```

- scope-fml-18: exit 0, **56 assertions**, 51s, OpenGL 4.6 / AMD Radeon 760M / driver 26.8.1.260810.
- scope-fml-19: exit 0, **56 assertions**, 43s, Vulkan 1.4.351 / NVIDIA RTX 5050 Laptop / driver 616.92.
- Matching result JSON, prelaunch input manifests, debug logs and Gradle logs are retained. Both 28-input fixture manifests match the final sources. Each run creates its own real flat world and logs clean integrated-server/client shutdown. No owned fixture process remains running.
- Eight additional assertions cover shared/nested capture ownership, parent mask inheritance, single-job publication, surrounding order, exception propagation, no partial outer publication and successful reuse after failure. The 24 live pixel comparisons per backend now exercise the shared submission helper; previous font/panel RGBA and glint-alpha limits remain unchanged.

These 112 FML-fixture assertions are separate from the 1,754 root focused checks. No full TACZ entry point, actual gun model, other TACZ Mixins, dedicated server, multiplayer, gameplay, audio, migration, companion or performance gate is claimed. Remaining errors are Cloth 6, Oculus 9, KubeJS 2 and Player Animator 8; see the current compile inventory. The next task is verified API migration or explicit optional-companion isolation, then full compilation/packaging and the original runtime gates.

## Core build and installed-server continuation — 2026-09-15

- Runs 90–92 resolved Cloth/Oculus compilation, then isolated unavailable optional adapters. Run 91 exposed ForgeGradle's implicit AT resource-owner selection; specifying the real main AT path fixed all 18 access errors. Run 92 retains 2 KubeJS + 8 Player Animator errors. No required Mixin was relaxed or disabled.
- Run 93 was the first successful `build jarJar` with production core sources and the new absence boundary. Runs 94–96 fixed full-server loading defects: unsupported OnlyIn annotations, empty automatic subscribers, and client callback bytecode resolved during server network registration. Run 95's metadata check caught the final empty subscriber; the existing active first-person hook remains. Server pack loading then exposed item component binding order and legacy loot functions, corrected using native templates and custom-data loot conversion.
- **Run 97: core `build jarJar` and all 28 focused tasks pass, 1,783 assertions, 1m35s.** Exact command and 4,325 input hashes: `evidence/tested-inputs-97.json` (also primary tested-inputs.json). It includes 23 production class/absence/annotation checks, crafting 439, 36 GPU pixel checks and 138 target shader checks across both backends. These counts do not replace live gameplay/visual parity.
- **Run 98: `verifyOptionalAdapters --continue` fails with 10 diagnostics**, KubeJS 2 and Player Animator 8. All inputs match run 97. `inventory/compile-errors.json` records these optional-source failures; zero main diagnostics is not full-port completion.
- Development dedicated-server attempts 1–7 are preserved with inputs and logs. 1 stopped for EULA; 2–5 found the described load/side/binding failures; 6 reached a world with content errors and was forcibly stopped; 7 loaded 1,769 recipes but its stdin harness timed out. None is represented as a clean lifecycle pass.
- The official Forge 65.1.0 installer was downloaded and checked against its official SHA-1, `6b7d4ccc26957536585454613e96962ae45decd0`. The `.sha1` endpoint initially returned HTTP 403; downloading the JAR and checking the checksum on the official download listing succeeded. Full installer log and provenance are stored in evidence. A fresh directory `../TACZ-port-reference/installations/server-26.2` contains no mods except the built TACZ nested JAR.
- Installed-server attempt 1 reached a world and executed TACZ reload; the harness expected an untranslated message and failed on the actual translated success. Cleanup sent stop and the server exited 0. The failure is retained, not silently relabeled.
- **Installed-server attempt 2 passes functional lifecycle checks:** RCON list, TACZ reload, vanilla reload, flush save and stop; world startup plus both reloads each report 1,769 recipes. Exit 0, no forced termination, and post-stop all-dimensions save are present. RCON binds only 127.0.0.1 with an ephemeral local password; credentials are excluded from evidence. Reproducer: `tools/porting/server_smoke.py`.
- This is **not an error-free log**: Windows OSHI Perflib counter lookup and Netty's Linux/BSD native probes emit host diagnostics. The controller records all three separately, with `result=passed-with-host-diagnostics` and `error_free_log=false`. No TACZ load/pack/recipe error occurs in that attempt. It does not test player connections, live crafting, loot or world/item upgrades.
- Candidate 97: `build/libs/tacz-26.2-1.1.8-hotfix-port.1.jar`, 57,874,213 bytes, SHA-256 **9bf9c7f5e8b49486aee29d61e74da34fdccb99b92666444376ab9a5c786df234**. The archive has 4,817 entries/935 TACZ classes, six nested libraries, and no unavailable-adapter discovery/classes. Its source archive retains all 18 optional source/resource entries. See `core-candidate-97.json`. It remains a development candidate.

## Full packaged-client continuation — 2026-09-15/16

`tools/porting/runtime-fixture` loads the entire candidate JAR, all production Mixins and embedded libraries alongside a small test mod in the real Forge development client. It hashes actual fixture sources/resources/build files, candidate and runtime classpath before launch. These are not clean installed-client runs. Attempts share one directory and must run sequentially.

- Attempts 1 and 13 failed fixture compilation (old instance ModList call and wrong GunReloadEvent accessor, respectively), before client launch.
- Attempts 2–4 timed out at a loading overlay. Attempt 4 overlapped part of 3 and is diagnostic-only. The render thread dump in attempt 2 showed a running render loop. Attempt 5 was interrupted by a user continuation and has a partial log, not a completed runtime result.
- Attempt 6's executor watchdog exposed a Forge LoadingErrorScreen caused by the fixture's missing pack metadata. Forge had shut down its event bus, explaining the silent render-event diagnostic path. Adding target pack metadata fixed that fixture warning. Attempt 7 reached first-run accessibility onboarding; the fixture now calls its normal continuation. Attempt 8 reached world creation but its 18-character development username violated Minecraft's 16-character maximum; `TaczFixture` fixes that. Attempt 9 passed the first 45 content checks, then failed an incorrect call to deprecated `TimelessAPI.getRecipe` (also empty in the untouched baseline). The fixture now inspects the actual ClientRecipeCache; no production stub was introduced or altered.
- **Attempt 10 passes 48 assertions**, OpenGL, 28s: real title/world, complete client/common content indices (54 guns, 24 ammunition, 99 attachments, three blocks), all default/custom tab icons, native painting recipe output and all **173 synchronized gunsmith recipes**. Integrated server/client close normally.
- **Attempts 11 / 12 pass 51 assertions each**, OpenGL 34s / Vulkan 31s. Each additionally equips an actual AK-47 through server inventory synchronization, observes an active first-person animation and **119 cancelled native main-hand render events**. OpenGL uses AMD Radeon 760M / 26.8.1.260810; Vulkan uses NVIDIA RTX 5050 Laptop / 616.92. Attempt 12 also records and verifies the actual requested backend. Both close normally. No pixel comparison follows from a render-hook count.
- **Attempt 14 is a failed lifecycle run**, OpenGL, 49s, exit -8. All 66 selected world/held-gun/gameplay assertions passed first: survival AK-47 shot accepted by the client, exactly one server shoot event and one actual projectile on each side, magazine 30→29 with chamber retained, then real play-packet/Lua partial reload restoring magazine 30 while reserve falls 64→63. Both sides observe reload state and remain stable afterward. The subsequent normal client quit hit Minecraft's post-main watchdog because two non-daemon firing-pool workers remained alive. Preserve both the provisional passing JSON and the actual failure/crash log; the Gradle verifier correctly fails on process exit.
- The root scheduler fix now uses Forge's real GameShuttingDownEvent (physical client only, not world logout) to stop the pool. Periodic firing now cancels its own ScheduledFuture instead of casting the executing Thread to a future. The inspected exact target Minecraft.stop calls ForgeEventFactory.onGameShuttingDown; the target watchdog waits 15 seconds before exit -8. Post-fix validation is recorded below when completed.

Evidence is `evidence/tacz-client-N.log`, `tacz-client-N-inputs.json` and `tacz-client-N-result.json` where that attempt reached launch/results; attempt 14 also has its full crash report. Candidate97 was used for attempts 1–14. No remote socket handshake, second independent client, input binding, visual/audio parity or performance result is claimed.

The logs retain Windows OSHI Perflib diagnostics and Realms authentication errors from the development username/token, plus seven missing item-model particle materials. The AK-47 warns about `minecraft:ar_akilo_drop`, `minecraft:ar_akilo_raise_shoulder`, and during partial reload `tacz:ak47/ak47_reload_tactical`. These identifiers already exist in untouched baseline animation/display JSON and have no matching shipped OGG. Individual reload-keyframe sounds do exist. The baseline references/assets were not replaced with invented sound aliases; audio reference completeness and audible parity remain open.

## Scheduler fix and final candidate validation — 2026-09-16

- Run 100 failed one main-source diagnostic after moving the firing callback into a self-cancelling task: `this.useSilenceSound()` now referred to that inner task. It was corrected to the enclosing LocalPlayerShoot instance. The failed log/input manifest remains retained.
- **Run 101 succeeds:** `build jarJar` and all 28 focused tasks, **1,783 assertions**, 1m46s. `evidence/tested-inputs-101.json` and primary tested-inputs.json preserve the exact command and **4,325 hashes**. No production inputs changed afterward. Run 102 deliberately rechecks optional adapters and still fails 2 KubeJS + 8 Player Animator diagnostics on identical inputs; inventory/compile-errors.json records that failure.
- **Client runs 15 / 16 succeed with 66 assertions each**, OpenGL / Vulkan, **37s each**, using the new candidate. Both repeat content/world/held-gun checks and actual survival shot/partial Lua reload through the normal client API and play messages. Their snapshots show magazine 30→29→30, chamber retained and reserve 64→63; server/client projectile counts are one, authoritative shoot/reload event counts are one, both observe reload states, and state remains stable for the final one-second observation. The Minecraft/Forge processes now exit 0 after saving all integrated dimensions. This validates the physical shutdown hook after firing; it does not verify every burst/charge mode, reconnect or audio/visual parity.
- **Installed server run 3 succeeds with the new candidate** in the same isolated official installation: list, TACZ reload, vanilla reload, save-all flush and stop; all three loads contain 1,769 recipes, exit 0, no forced stop. The exact three OSHI/Netty host diagnostics persist and are classified as before, with error_free_log=false. No test mod is installed on this server and no player connection is part of this check.
- Current candidate SHA-256 **a320d7a8dc613352addf99e66caf79d1deb3009a0951b0ce648b385fe24a0c71**, **57,876,343 bytes**, 4,818 entries / 936 TACZ classes. `audit_candidate.py` compares all 936 compiled main classes against the JAR byte-for-byte and all 18 optional source/resource files against the source archive, rejects blocked runtime adapters/discovery, and records six nested hashes and notices. `evidence/core-candidate-101.json` is current; candidate97/server2/client1–14 remain historical.

Reproduction from the repository root with Java 25:

```powershell
.\gradlew.bat -p tools/porting/runtime-fixture verifyTaczClient -PfixtureGraphicsBackend=OPENGL -PfixtureGameplay=true --console=plain
.\gradlew.bat -p tools/porting/runtime-fixture verifyTaczClient -PfixtureGraphicsBackend=VULKAN -PfixtureGameplay=true --console=plain
python tools/porting/audit_candidate.py --output docs/porting/evidence/core-candidate-local.json
```

Use `tools/porting/runtime-fixture/README.md` for modes, evidence paths and limits; run clients sequentially. `server_smoke.py --help` describes the isolated installed-server harness. Do not pass `--initialize` to an existing world. Its local RCON credentials stay outside Git/evidence. Full-port gates remain open as listed in status.md and audit-areas.md.

Final checkpoint review: all 4,325 root input hashes still match run101; all nine fixture/candidate input hashes match both client15 and client16; installed-server3 candidate/harness/log hashes match. The untouched baseline has no tracked changes, whitespace checks pass, and no owned client/installed-server process remains running.

## Official installed-client verification — 2026-09-16

Production revision `da7059e0`, candidate101 SHA-256 `a320d7a8dc613352addf99e66caf79d1deb3009a0951b0ce648b385fe24a0c71`; no production edits in these runs. Installed with the verified official Forge 26.2-65.1.0 installer using `java -jar ../TACZ-port-reference/downloads/forge-26.2-65.1.0-installer.jar --installClient ../TACZ-port-reference/installations/client-26.2`. Only this separate directory received a minimal launcher_profiles.json and the verified vanilla client/profile. Patched client SHA-1 `f9ef709fa7988febfca4c91b87d3d1ad8a438097` matches the installer (79,191,195 bytes). Installer logs are `evidence/forge-client-install-1.log` and `forge-client-installer-detail.log`.

`tools/porting/installed_client.py` consumes the actual inherited vanilla/Forge release profile, verifies every allowed/merged library and asset against official metadata, and launches `forge_client` with Java 25. Its 126 classpath files are 125 installed libraries plus the vanilla client, not the development runtime. The normal game directory supplies only read-only, checksum-verified public asset/library caches. An explicit offline singleplayer test identity uses no launcher account credentials; authentication/Realms are not tested. Input/result manifests preserve actual JVM arguments, libraries, candidate, installed mods, controller and (for instrumented runs) fixture source/build/JAR hashes. Logs and results are distinct from the development fixture evidence.

| Installed attempt | Configuration | Result |
| --- | --- | --- |
| 1 | OpenGL, full TACZ plus packaged fixture | 66 checks, exit 0, no forced stop; original controller retained as installed-client-harness-1.py |
| 2 | Vulkan, full TACZ plus packaged fixture | 66 checks, 31.1s, exit 0, no forced stop; fixture source/build hashes also recorded |
| 3 | OpenGL, only TACZ, reopen run2 world | **Failed:** unconfirmed experimental custom world, no join in 120s; controller forcibly stopped owned JVM; raw failure preserved |
| 4 | OpenGL, full TACZ plus fixture with native disposable-world confirmation | 66 checks, 29.6s, exit 0; result records confirmed experimental state |
| 5 | OpenGL, only TACZ, reopen run4 world | **Passed**, 28.0s, at least ten seconds joined, ordinary window close, exit 0, all dimensions saved |
| 6 | Vulkan, only TACZ, reopen run4 world | **Passed**, at least ten seconds joined, ordinary window close, exit 0, all dimensions saved |

The exact target WorldOpenFlows asks for backup/confirmation when custom world settings are experimental and PrimaryLevelData has no confirmed flag. The test fixture's fresh-world creation bypasses the selection dialog. Its server-thread setup now records the native `withConfirmedWarning(lifecycle != Lifecycle.stable())` transition used by the normal world-open flow, solely for that newly created disposable test world. Run3's level.dat was inspected read-only and had the flag false. No production world-loading code, warning screen, or user save was changed. These tests do not establish upgrading 1.20.1 worlds or complete item persistence.

Actual commands after setting JAVA_HOME to the pinned JDK25 (Python denotes the bundled Python executable):

```powershell
.\gradlew.bat -p tools/porting/runtime-fixture jar --console=plain
python tools/porting/installed_client.py --installation ../TACZ-port-reference/installations/client-26.2 --java ../TACZ-port-reference/jdk25/jdk-25.0.4.1+1/bin/java.exe --backend OPENGL --evidence-prefix docs/porting/evidence/installed-client-4
$worldId = (Get-Content docs/porting/evidence/installed-client-4-result.json -Raw | ConvertFrom-Json).fixture_result.world
python tools/porting/installed_client.py --installation ../TACZ-port-reference/installations/client-26.2 --java ../TACZ-port-reference/jdk25/jdk-25.0.4.1+1/bin/java.exe --backend OPENGL --clean-world $worldId --evidence-prefix docs/porting/evidence/installed-client-5
python tools/porting/installed_client.py --installation ../TACZ-port-reference/installations/client-26.2 --java ../TACZ-port-reference/jdk25/jdk-25.0.4.1+1/bin/java.exe --backend VULKAN --clean-world $worldId --evidence-prefix docs/porting/evidence/installed-client-6
```

Fixture jar builds 1/2/3 passed; their logs are retained. Run4 records the revised fixture inputs; runs5/6 have exactly the candidate in mods and no TACZ_RUNTIME_RESULT marker. All six raw logs and input/result manifests are retained. Earlier raw controller versions preserve the exact failed/run1 orchestration; the current controller includes a strict ten-second dwell check. Host OSHI/Realms diagnostics and missing model-particle/inherited sound references still appear. Successful lifecycle is not an error-free log, captured visual/audio comparison, actual keyboard/UI interaction, remote multiplayer or broad gameplay result.

## Two installed clients and dedicated networking — 2026-09-18

Production revision `da7059e0`, installed-client checkpoint `cf4dee9b`, unchanged candidate101 SHA-256 `a320d7a8dc613352addf99e66caf79d1deb3009a0951b0ce648b385fe24a0c71`. This continuation adds test infrastructure, not production mechanics changes. `tools/porting/multiplayer_smoke.py` builds a fresh isolated tree from the official installed profiles. The dedicated server has only TACZ; the two independent clients have the complete candidate and a client-only RemoteCheck fixture under the normal `forge_client` profile. No development classpath, replacement server implementation or direct server gun-operator calls are used.

The server binds to 127.0.0.1:25570 with RCON on 127.0.0.1:25580 and private randomized credentials. Offline-mode is confined to that fresh installation; the fixtures use distinct offline TaczShooter/TaczObserver UUIDs and never read account credentials. This tests real TCP/configuration/play traffic, not authenticated Internet play, WAN latency or external hosting. Mutable config/world/player data is separate. Public profile libraries/assets are shared as hard links and checked against official hashes before use; mismatched shared files fail without overwriting them.

The shooter joins alone and verifies 54 client/common guns, 24 common ammo, 99 common attachments and 173 recipes. Native item commands equip an AK-47 with 30 magazine rounds and a loaded chamber plus a legal 60-round reserve stack. One client-operator shot leaves 29 rounds. The observer joins late and sees current equipment without historical shot events. A second shot reaches both clients with exact event/projectile counts and leaves 28 rounds. A partial Lua reload consumes two reserve rounds, restores 30 magazine rounds and leaves the chamber loaded. Both clients observe reload state and one reload event. The shooter disconnects and reconnects in the same JVM, receives synchronized content and saved 30/58 ammo again, then fires successfully to 29/58. Authoritative values are independently inspected through RCON data queries. All clients must quit normally and the dedicated server must save every dimension and exit 0.

| Attempt | Result and evidence boundary |
| --- | --- |
| multiplayer-1 | Failed after 7 checks: redundant survival-mode command returns no feedback; exact target RconClient sends no packet for an empty response, so the harness times out. Cleanup exits normally. |
| multiplayer-2 | Failed after 10 checks: native item command rejects 64 reserve rounds because TACZ's stack limit is 60. First live shot/server magazine passed. Harness now validates equip responses and uses 60. |
| multiplayer-3 | Failed after 22 checks: late observer receives a still-existing free projectile, invalidating the harness's assumed expiry. Raw result preserved; investigated separately below. |
| multiplayer-4 | Failed after 45 checks: Windows denies replacement of a command file while Java reads it. Shot/reload and reconnect configuration checks passed before the harness race. Numbered immutable publications replace shared filenames. |
| multiplayer-5 | **Passed 60 checks**, OpenGL, 77.3s, two clients and server exit 0 without forced termination. Loaded-area backstop and actual server-side removal check make the late-join premise explicit. |
| multiplayer-6 | **Passed 66 checks**, Vulkan, 87.2s, including a fourth free-flight shot and chunk-resume probe, then all normal exits. |
| multiplayer-7 | **Passed 68 checks**, OpenGL, 81.2s: same scenario/probe plus accepted native client options; normal client/server exits, no forced stop. |

All failed attempts and prior controller/RemoteCheck versions are retained. Fixture JAR builds4/5/6 pass and have separate logs. Root build101 remains the applicable production build, not a new root build claim. Each client input/result report captures candidate, test JAR, actual source/build/controller, installed libraries/profile/arguments and logs; the overall report records server libraries, shim, RCON results and every numbered client snapshot. Current reports also capture initial options and server settings with the password omitted. An individual client's `passed` status only means its command executor quit normally; overall gameplay success is determined by the multiplayer result.

Attempts1–6 used a client-only simulation-distance option of 2; the native client rejects it below its minimum5 and uses a fallback. Their gameplay observations remain actual results, but these are not clean-option logs. The controller now uses5 (the dedicated server independently retains2); run7 verifies absence of native option parse errors on both clients. OSHI Perflib and unsupported Netty Linux/BSD probe logging diagnostics, offline Realms errors, missing item-particle materials and inherited missing AK-47 sound references are retained. No error-free log or complete visual/audio claim is made.

The separate Vulkan run6 boundary probe found a projectile at `[-0.6664473064433487, -59.05692269670157, -48.354945333514564]` after both two and four seconds, with nonzero motion. Native `forceload` of the disposable flight corridor resumes simulation and the projectile disappears. `evidence/projectile-tick-source-comparison.json` shows that movement, friction, gravity and tickCount/lifetime discard logic are unchanged from the baseline; only client particle dispatch/accessor changes occur in tick(). This supports simulation suspension as the explanation for attempt3. It is a target observation plus source comparison, not an observed 1.20.1 runtime comparison or general chunk-lifecycle parity proof. The port's projectile lifetime was not changed to wall-clock time.

Actual commands: build fixture with `.\gradlew.bat -p tools/porting/runtime-fixture jar --console=plain`, then use the command in its README with root/evidence suffix5 for OpenGL, suffix6 plus `--backend VULKAN --boundary-probe` for Vulkan. Java25 and the pinned official installations are unchanged. Do not reuse a scenario root; fixed loopback ports require sequential scenarios. Broader firing modes, empty/cancelled reloads, damage, respawn/death, dimension changes, item transfer, custom packs/config differences, latency, presentations and companion-present scenarios remain open.

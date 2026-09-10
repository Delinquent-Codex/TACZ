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

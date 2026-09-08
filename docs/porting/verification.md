# Verification log

Date: 2026-09-07. Windows 11 amd64. Baseline revision: `b43eb84c38e9768d8e73c8b14f0b845669704b38`. Current port changes are uncommitted on `port/forge-26.2`. Reference workspaces and complete command output live in `../TACZ-port-reference`; relevant logs will also be checkpointed under `evidence/`.

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

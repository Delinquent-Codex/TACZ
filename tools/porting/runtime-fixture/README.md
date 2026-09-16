# Packaged TACZ client fixture

This separate Forge 65.1.0 test mod loads the complete root nested JAR from
`build/libs/tacz-26.2-1.1.8-hotfix-port.1.jar`. It does not compile selected TACZ
classes or replace/disable the production Mixin configuration. It is a **Forge
development launch with the packaged mod**, not a clean installed-client test.

Build the root project first using Java 25. From the repository root:

```powershell
.\gradlew.bat build jarJar --console=plain
.\gradlew.bat -p tools/porting/runtime-fixture verifyTaczClient -PfixtureGraphicsBackend=OPENGL -PfixtureGameplay=true --console=plain
.\gradlew.bat -p tools/porting/runtime-fixture verifyTaczClient -PfixtureGraphicsBackend=VULKAN -PfixtureGameplay=true --console=plain
```

The corresponding Unix wrapper is `./gradlew`; that command has not been tested
on this Windows host. Run clients sequentially: they share the fixture's `run/`
directory. Each launch creates a fresh disposable flat world. The fixture uses
the normal onboarding continuation and title-screen world-creation path.

Without either optional flag, 48 assertions check loaded content, tabs, native
custom-item results and all 173 synchronized gunsmith recipes. `-PfixtureHoldGun=true`
adds actual server inventory equip, active first-person animation and at least
60 intercepted main-hand frames for an AK-47 (51 assertions total).
`-PfixtureGameplay=true` includes that held-gun test, switches to survival, gives
64 reserve rounds, calls the real client operator shoot/reload API, and observes
authoritative ammunition, real client/server projectiles, reload states and
event counts (66 assertions total). It never invokes the server shoot/reload
methods directly. Actions pass through TACZ's normal play packets over the
integrated connection.

The prelaunch manifest hashes the candidate, actual compilation/resources/build
inputs and runtime classpath. Preserve `run/runtime-fixture-inputs.json`,
`run/runtime-fixture-result.json` and the complete Gradle log together. The
verification task requires a passing result, the requested actual backend and
successful client process exit. **A passing JSON written before a shutdown crash
is not a successful run** (see attempt 14 in the verification log).

Watchdogs capture FML startup warnings/errors or action timeouts, then request
ordinary client shutdown. A three-minute Gradle timeout bounds unexpected hangs.
No screenshots, pixel comparisons, audio capture, remote multiplayer, keyboard
bindings, clean installed-client, complete mechanic/content coverage, optional
companions or performance claims follow from these checks. Host OSHI/Realms
diagnostics, missing model-particle warnings and baseline missing animation/sound
references remain visible in the logs; see `docs/porting/verification.md`.

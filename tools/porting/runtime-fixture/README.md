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
bindings, complete mechanic/content coverage, optional
companions or performance claims follow from these checks. Host OSHI/Realms
diagnostics, missing model-particle warnings and baseline missing animation/sound
references remain visible in the logs; see `docs/porting/verification.md`.

## Installed release profile

`tools/porting/installed_client.py` also runs the packaged fixture JAR in a separate
installation made by the official Forge installer. Build this fixture with
`.\gradlew.bat -p tools/porting/runtime-fixture jar --console=plain`, then follow
the actual commands in `docs/porting/verification.md`. This route uses installed
release libraries and assets, with `forge_client`, and verifies their metadata
checksums and evidence hashes. It uses an offline test identity and no launcher
account credentials. The normal Minecraft directory is never modified.

Instrumented installed runs have TACZ plus this fixture in mods. `--clean-world`
removes only the copied fixture, reopens the named saved world with TACZ alone,
requires ten seconds joined, then requests ordinary closure of the owned JVM
window and requires normal exit and saved dimensions. Run clients sequentially.
The fixture prepares only its own fresh disposable world's native experimental
confirmation state, matching WorldOpenFlows, so a later quick-play reopen can
proceed without a confirmation dialog. This does not bypass production warnings
or validate legacy-world migration. Installed runs 1/2/4 pass 66 checks; clean runs 5/6
pass lifecycle on OpenGL/Vulkan. Failed3 is retained with its forced-stop result.

## Two-client dedicated-server fixture

`tools/porting/multiplayer_smoke.py` creates fresh server, shooter and observer
directories from the verified installed templates. The server contains only the
candidate JAR; this client-only test mod is present in the two independent release
clients. The isolated server binds to 127.0.0.1, uses offline test identities and
has a random private RCON password outside the evidence. No account credentials
are read and no authenticated Internet multiplayer result is implied.

RemoteCheck invokes the actual client gun operator for shoot/reload, observes real
client events, projectiles, synchronized content and player equipment, and uses
native disconnect/connect calls. Ordinary RCON commands equip and inspect the
server inventory. Numbered command/response files coordinate the test; they do not
replace any game packets or authoritative gameplay. Each file is atomically
published once to avoid Windows sharing races. Both processes receive a normal
quit, and the server must save and stop normally for the overall result to pass.

The scenario checks initial sync, late observer join, live shot/reload broadcast,
exact magazine/chamber/reserve changes and continued firing after a same-process
shooter reconnect. A stone backstop keeps the initial projectiles in ticking
chunks, with server-side removal verified before late join. `--boundary-probe`
adds a separate free-flight observation and temporarily force-loads a distant
corridor to test removal after suspended simulation resumes. It changes only the
disposable test world; no production chunk loading or projectile logic changes.

```powershell
python tools/porting/multiplayer_smoke.py --root ../TACZ-port-reference/installations/multiplayer-7 --client-template ../TACZ-port-reference/installations/client-26.2 --server-template ../TACZ-port-reference/installations/server-26.2 --java ../TACZ-port-reference/jdk25/jdk-25.0.4.1+1/bin/java.exe --backend OPENGL --boundary-probe --evidence-prefix docs/porting/evidence/multiplayer-7
```

Choose a **new** root and evidence prefix for every run. `--backend VULKAN` is also
supported. Run controller scenarios sequentially because they use ports 25570 and
25580; each scenario intentionally runs its two clients concurrently. Public
libraries/assets are hard-linked from installed templates, while all mutable
state is separate; the verifier rejects checksum mismatches in shared files.
Server config is recorded with its RCON password omitted. Input/result manifests,
full logs and all command observations belong together. Individual client reports
only confirm the command executor and normal exit; behavioral assertions live in
the overall multiplayer result. Earlier failures remain in the evidence history.

`--reload-probe` adds six source-derived AK-47/M870 cases: empty magazine,
early/post-shell cancellation via attempted fire, resumed partial reload,
reserve exhaustion and a complete per-shell empty reload. Native synchronized
phases are sampled each client tick; server inventory queries independently check
ammo. Setup respects the asynchronous draw transition and client state lock.
The original auto-reload default is observed disabled. No reload state is injected.

`--bolt-probe` fires all six loaded M870 shells through the client operator.
The production client tick initiates its automatic bolt packets and the actual
shipped Lua transfers magazine rounds into the chamber. The controller checks
each chamber/bolt transition, nine projectiles per shot on both clients, reserve
retention, the final empty state and dry-fire rejection. It does not directly call
the server bolt API or validate pellet damage, sound or pixel output. Options can
be combined; see verification.md for results and exact evidence boundaries.

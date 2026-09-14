# Scope renderer Forge client fixture

This is a separate test mod, **not the full TACZ port**. It uses the pinned Forge
26.2-65.1.0 client and compiles the production scope classes and three production
scope Mixins directly from `src/main/java`. Its generated Mixin config selects
those entries from the production config, retaining `required` and
`defaultRequire: 1`. It uses the production access transformer.

From the repository root, with Java 25 selected:

```powershell
.\gradlew.bat -p tools/porting/scope-fixture verifyScopeFml --console=plain
```

The first run downloads vanilla assets through ForgeGradle. The client opens a
small window, waits for its loading overlay to finish, creates a uniquely named
flat test world under this fixture's `run/saves`, runs checks after joining,
writes `run/scope-fixture-result.json`, and shuts down its integrated server and
client. World loading is necessary: 26.2 binds default item components during
world data loading, so a menu-only test cannot construct real default stacks.
`verifyScopeFml` fails if the
client crashes, omits its result, or reports a failed assertion. Each run removes
only the preceding fixture result before launching. The run directory is
separate from TACZ's development clients and the user's installations.
The run task has a six-minute timeout. Generated worlds are retained for inspection.

To require a particular backend (a fallback fails the verification task):

```powershell
.\gradlew.bat -p tools/porting/scope-fixture verifyScopeFml -PfixtureGraphicsBackend=OPENGL --console=plain
.\gradlew.bat -p tools/porting/scope-fixture verifyScopeFml -PfixtureGraphicsBackend=VULKAN --console=plain
```

Before launching, the task writes `run/scope-fixture-inputs.json`, with SHA-256
hashes for actual compilation inputs, metadata, generated config/AT, build files,
wrapper and resolved runtime classpath. Preserve this alongside the matching result
and complete Gradle log before running again. Successful historical runs and their
limitations are recorded in `docs/porting/verification.md`.

The checks exercise actual FML discovery, Mixin transformation, the access
transformer, native simple/translucent phase interception, pass-through outside
capture, mask/vertex ownership, exception cleanup, and registration and execution
through the game's feature dispatcher. No fake Minecraft services or replacement
scope implementations are supplied. The live client also resolves vanilla fonts,
baked diamond-sword and compass item models, and standard/special glint. At 128x128,
GPU readbacks compare native rendering with permitting/rejecting masks, ocular
writes and inversion. Font and textured-panel comparisons require every RGBA byte
to match. Glint texture matrices depend on wall time, so those comparisons require
equal alpha coverage; they do not claim identical glint RGB at different times.
These vanilla assets and small geometry fixtures do not establish actual TACZ gun
visual parity or performance.

This does not satisfy the full-mod build, packaged TACZ client, dedicated server,
world/gameplay, multiplayer, companion, migration or performance gates. Do not
install or distribute the fixture as TACZ. The root build and all its required
sources and Mixins remain unchanged by this test project.

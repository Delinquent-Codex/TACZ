# Unreleased Forge 26.2 port

**Development checkpoint only. No usable Forge 26.2 TACZ JAR has been produced. Do not install the 1.20.1 baseline JAR into 26.2.**

Intended target: Minecraft Java 26.2, Minecraft Forge 65.1.0, Java 25. Mod ID remains `tacz`; intended version is `1.1.8-hotfix-port.1`.

Completed foundations: exact-source clone and isolated baseline build, verified target MDK build, pinned build tooling, inventory and parity ledger, identifier migration, initial component-backed item and attachment persistence with focused target tests. This is not a complete feature port.

Full compilation, model-library port, rendering, networking, lifecycle migration, packs/Lua, UI/content, integrations and every packaged runtime gate remain incomplete. `gradle/legacy-dependencies.gradle` is temporary migration scaffolding and prevents release readiness. Build attempts and current limitations are in `verification.md` and `status.md`.

Preserve upstream attribution: code GPL-3.0; assets CC BY-NC-ND 4.0, as specified in the original README and metadata. Original textures, sounds and Bedrock model assets remain; Minecraft-facing item-model definitions, recipe paths and JSON formats have been migrated for local validation. Any future adaptation/distribution permission question must be recorded separately. No public push, upload, release or publication has been performed.

Final installation instructions, source revision, artifact path/hash and supported migration claims will be supplied only after an actual release candidate passes its gates.

The latest continuation adds native laser/flash submissions, carried-item and humanoid pose states, camera/arm hooks, HUD/screens/tooltips, key input and client resource decoding. Legacy particle argument adapters and the verified Forge 26.2-5.0.11 Shoulder Surfing API adapter are implemented. The native sampled-mask scope backend is implemented and passes offscreen OpenGL/Vulkan fixtures. All 24 verification tasks passed 1,553 assertions together, including 28 GPU pixel checks. Attachment/gun integration is unfinished; no in-game or packaged-runtime support is claimed.

Ordinary build/jarJar still fails (15 early missing-type diagnostics); the fuller FLOW diagnostic compile reports 130 errors in scope/stencil rendering and optional integrations. Several companions have no verified Forge 26.2 artifact in the inspected providers. Their adapters and all source remain in scope. Runtime dependency closure, client/server startup, gameplay, multiplayer, visual/audio parity, migrations and performance gates remain unmet.

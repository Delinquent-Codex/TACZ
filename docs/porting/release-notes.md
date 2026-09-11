# Unreleased Forge 26.2 port

**Development checkpoint only. No usable Forge 26.2 TACZ JAR has been produced. Do not install the 1.20.1 baseline JAR into 26.2.**

Intended target: Minecraft Java 26.2, Minecraft Forge 65.1.0, Java 25. Mod ID remains `tacz`; intended version is `1.1.8-hotfix-port.1`.

Completed foundations: exact-source clone and isolated baseline build, verified target MDK build, pinned build tooling, inventory and parity ledger, identifier migration, initial component-backed item and attachment persistence with focused target tests. This is not a complete feature port.

Full compilation, model-library port, rendering, networking, lifecycle migration, packs/Lua, UI/content, integrations and every packaged runtime gate remain incomplete. `gradle/legacy-dependencies.gradle` is temporary migration scaffolding and prevents release readiness. Build attempts and current limitations are in `verification.md` and `status.md`.

Preserve upstream attribution: code GPL-3.0; assets CC BY-NC-ND 4.0, as specified in the original README and metadata. Original textures, sounds and Bedrock model assets remain; Minecraft-facing item-model definitions, recipe paths and JSON formats have been migrated for local validation. Any future adaptation/distribution permission question must be recorded separately. No public push, upload, release or publication has been performed.

Final installation instructions, source revision, artifact path/hash and supported migration claims will be supplied only after an actual release candidate passes its gates.

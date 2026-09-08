# Mandatory behavioral audit

All areas below remain in scope. The generated file and definition ledger supplements these scenarios; it does not prove semantic coverage by counting files.

| Area | Baseline evidence | Required target validation | Status |
| --- | --- | --- | --- |
| Build and packaging | build.gradle, settings.gradle, gradle.properties, META-INF metadata, mixin configs, libs | Wrapper build, nested dependency closure, source JAR, clean packaged startup | in progress |
| Registration and lifecycle | GunMod; init; client/init | Same registry IDs; event phases; server class loading; generated resources | in progress; runtime unverified |
| Gun mechanics and operators | api/entity; entity; api/item; item | All fire/charge/bolt modes, cadence, chamber, magazine, reload/cancel/interruption, aim, sprint, crawl, melee, heat, damage, headshots, armor, penetration, explosions; player and non-player operators | not started |
| Persistence and migration | item; api/item; compat/kubejs/util; entity | Non-default IDs, ammo, fire mode, attachments, skins, extensions; copy/split/craft/loot; saved component/NBT round trips; recoverable idempotent conversions | in progress; runtime unverified |
| Rendering and animation | client/renderer; api/client; client/resource; mixin/client | First/third person, inventory, dropped, frame, projectile, table, attachment; Bedrock/glTF, state machines, scopes, recoil, camera, shell, beam, lighting, shader hooks; reload lifecycle | not started |
| Sound | client/sound; client/resource; network/message/ServerMessageSound | Positional/attenuation/distance/config; sound keyframes; local/remote playback without duplication | not started |
| Packs and Lua | resource; api/resource; api/vmlib; client/resource; util/GetJarResources | Complete default pack; directory/ZIP; namespaces/overrides/errors; export/backup/conversion; client/data/command reload; actual Lua scripts and APIs; bounded network data and cleanup | in progress |
| Networking and dedicated server | network; resource/network; api/entity | All 31 play messages plus login mapping/acknowledgment; protocol negotiation; authority; late join/reconnect; two clients; respawn/dimension/inventory transitions | in progress; runtime unverified |
| UI and crafting | client/gui; inventory; crafting | Gunsmith/refit/pack UI/HUD/heat/tooltips/keys; survival/creative transactions, shift/drag/slot limits; GUI scale/aspect/FOV/handedness | not started |
| Blocks and entities | block; block/entity; entity; init | Tables/variants, targets, statues, target minecarts; placement/state/render/persist/break/loot/recipe | not started |
| Config and commands | config; command; init/CommandRegistry | Every key/default/range, persistence and sync; every subcommand/argument/suggestion/permission/error | not started |
| Translations and input | all lang resources; client/input; compat/controllable | Every locale/key retained; sensitivity, aim modes and supported controller behavior | not started |
| Libraries and integrations | build.gradle; libs; compat; compat mixins | Model/MAE/LuaJ/Math/BCEL/Mixin closure; AR, Carry On, Cloth, Controllable, JEI, KubeJS/Rhino/Architectury, Oculus/OptiFine, Player Animator, Shoulder Surfing; present/absent configurations | in progress; runtime unverified |
| Public extension surface | api; compat/kubejs; resources | Semantics of events/builders/operators/resource/modifier/Lua hooks; compiling target examples; third-party subjects listed separately | in progress; runtime unverified |
| Performance, stability and delivery | All areas | Measured documented workload, repeated reload and sustained use; tested artifact hash/revision; local release materials and licensing/provenance | not started |

# Dependency and integration ledger

Target: Minecraft 26.2 / Forge 65.1.0 / Java 25. No companion compatibility is inferred from a NeoForge or Fabric artifact. `gradle/legacy-dependencies.gradle` is temporary compilation scaffolding, must be replaced before release, and does not supply runtime parity. All original compatibility classes remain in the main compilation.

| Dependency | Source version / coordinate | Purpose and original scope | Target decision / availability | Runtime closure and notice | Validation |
| --- | --- | --- | --- | --- | --- |
| Forge | net.minecraftforge:forge:1.20.1-47.3.19 | Loader/game | net.minecraftforge:forge:26.2-65.1.0 | MDK verified; LGPL notices in reference source | Target MDK build passed; TACZ does not compile |
| ForgeGradle | 5.1.+ resolved 5.1.77 | Build | net.minecraftforge:forgegradle:7.0.36 pinned | Gradle 9.5.0; Mavenizer 0.5.21 | Target tasks passed |
| Parchment/Librarian | 2023.08.20-1.20.1 / 1.+ | Source mappings | Removed for named 26.2 classes | Exact generated target sources used | Identifier/access-transformer signatures checked |
| Mixin/MixinGradle | 0.8.5 processor / 0.7-SNAPSHOT | Required injections, build adapter | Target Mixin 0.8.7; MixinGradle removed | All required mixin configs retained; old injections/refmaps still need audit | Not runtime tested |
| MixinExtras | 0.3.6 | Bundled injection helpers | Explicit pin 0.5.4 matches target closure | Target Forge itself supplies MixinExtras; nested packaging audit pending | Dependency tree resolved; focused checks resolve pinned inputs |
| JarJar | ForgeGradle 5 embedded plugin | Nested libraries | net.minecraftforge.jarjar 0.2.3 | Separate ForgeGradle 7 plugin | Task discovery passed; target packaging not passed |
| Simple Bedrock Model | 2.2.2-forge+mc1.20.1, source tag 0a4a4084 | TACZ directly uses two interfaces and first-person handler | Those three surfaces adapted into TACZ; old jar removed from compile/runtime classpaths; other upstream APIs not provided | LGPL-3.0 and attribution in META-INF/licenses; reference jars retained | 23 lifecycle/MAE checks; live animation and external add-on compatibility unmet |
| MAE | com.maydaymemory:mae:1.1.2 | Java animation engine, formerly supplied by SBM | Same unmodified library, implementation + JarJar; target JOML/fastutil | MIT notice included; jsr305 excluded, JOML/fastutil supplied by target | Runtime dependencyInsight passes Java 25; interpolation/blend checks pass; nested artifact/runtime and gun animation unverified |
| LuaJ core/JSE | com.github.FiguraMC.luaj:luaj-core / luaj-jse:3.0.8-figura | Required Lua VM; minecraftLibrary + nested JAR | Same fork resolves on target build | JSE requires core and BCEL; notices to be included in release audit | Shipped scripts not yet executed on target |
| Commons Math | org.apache.commons:commons-math3:3.6.1 | Interpolation, bundled | Same Java library | Apache-2.0 | Resolved; animation tests pending |
| BCEL | org.apache.bcel:bcel:6.6.1 | LuaJ compilation, bundled | Same Java library | Apache-2.0; commons-lang3 provided by target closure | Resolved; Lua compiler exercise pending |
| JEI | mezz.jei:jei-1.20.1-common-api / forge-api:15.0.0.12; runtime forge:15.20.0.112 | Optional recipe UI | Inspected 26.2 upstream has NeoForge/Fabric configuration, no Forge target in gradle.properties; Forge availability remains unmet | Companion not bundled | Externally blocked for inspected Forge target; adapter pending |
| Cloth Config | me.shedaniel.cloth:cloth-config-forge:11.1.106 | Optional config screens; development implementation | Target Forge release search pending | Companion not bundled | not started |
| Controllable / Framework | curse.maven:controllable-317269:7943200 / framework-549225:7573740 | Controller API; compileOnly / dev implementation | Target Forge release search pending | Companion not bundled; framework transitive role retained | not started |
| Shoulder Surfing | curse.maven:shoulder-surfing-reloaded-243190:5455954 | Optional camera/reticle integration; dev implementation | Target Forge release search pending | Companion not bundled; old metadata range not yet migrated | not started |
| Player Animator | dev.kosmx.player-anim:player-animation-lib-forge:1.0.2-rc1+1.20 | Optional third-person animation; dev implementation | Upstream inspected Forge branch targets 1.21/1.21.1; no verified Forge 26.2 artifact | Companion not bundled | Availability unresolved, animation parity unmet |
| Oculus legacy/new | local mc1.20.1-1.6.13 / mc1.20.1-1.7.0 | Optional shader adapters, compileOnly | Obsolete target signatures; replacement research pending | Local jars retained only as reference inputs | not started |
| OptiFine | reflection in compat/optifine | Optional shader adapter | Exact Forge 26.2 environment not verified | Not bundled | not started |
| Embeddium | curse.maven:embeddium-908741:5175031 | Rendering API, compileOnly | Target Forge research pending | Not bundled | not started |
| Accelerated Rendering | curse.maven:accelerated-rendering-1314021:7342975 | Optional renderer and separate required-if-present mixins | Target Forge research pending | Not bundled; mixin config retained | not started |
| Carry On | curse.maven:carry-on-274259:4882500 | Runtime optional integration, blacklist | Target Forge research pending | Not bundled | not started |
| KubeJS | dev.latvian.mods:kubejs-forge:2001.6.5-build.14 | Optional events/builders/recipes/scripts, compileOnly | Target Forge research pending | Not bundled | not started |
| Rhino | dev.latvian.mods:rhino-forge:2001.2.3-build.6 | KubeJS JS engine compile API | Target Forge research pending | Not bundled | not started |
| Architectury | dev.architectury:architectury-forge:9.2.14 | KubeJS companion API | Target Forge research pending | Not bundled | not started |
| Spark | curse.maven:spark-361579:4738952 | Development profiler only | Not added to target runs before target version verified | Not an end-user requirement | performance gate pending |
| Selene / MmmMmmMmmMmm | curse.maven:selene-499980:5478857 / mmmmmmmmmmmm-225738:5319203 | Development melee target fixtures | Not added to target runs before target version verified | Not end-user requirements | gameplay gate pending |
| Sound Physics Remastered | curse.maven:sound-physics-remastered-535489:7032235 | Development runtime sound compatibility subject | Target research pending | Not bundled | not started |
| Create / Flywheel / Registrate | properties 0.5.1.j-55 / 0.6.11-13 / MC1.20-1.3.3 | Unused version properties; no active dependency declarations | Removed unused properties only | No feature inferred solely from a property | Source build evidence |

Primary sources: [MDK examples](https://github.com/MinecraftForge/MDKExamples), [SimpleBedrockModel releases](https://github.com/MCModderAnchor/SimpleBedrockModel/releases), [JEI 26.2 properties](https://github.com/mezz/JustEnoughItems/blob/26.2/gradle.properties), [Player Animator Forge build](https://github.com/KosmX/minecraftPlayerAnimator/blob/1.21/minecraft/forge/build.gradle). Local `inventory/archive-members.json` includes recursively expanded nested-JAR hashes.

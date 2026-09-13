# Scope rendering continuation

The native sampled-mask backend is implemented in `client/renderer/scope/`, with CPU fixtures and real offscreen OpenGL/Vulkan checks. **Attachment/gun call paths still use the original stencil sequence and fail compilation.** The backend is not yet a working in-game scope port. Preserve those paths until every affected submission is captured, including functional renderers.

Source: baseline b43eb84c, `BedrockAttachmentModel`, `BedrockGunModel`, `RenderHelper`, `AttachmentRender`, and the Oculus/AR adapters. Target: pinned Forge 26.2-65.1.0 Mavenizer `remapped-javadoc.jar` and `recompiled.jar`.

## Required behavior

| Stage | Baseline operation to preserve |
| --- | --- |
| Ocular IDs | Clear stencil to zero; traverse ocular paths in reverse; stencil test GREATER with reference i+1, KEEP/KEEP/REPLACE, no color/depth writes. Higher reference wins overlaps where geometry passes depth. Preserve the texture's alpha rejection too. |
| Simple sight | Write ocular IDs, render each corresponding division with EQUAL i+1 and depth test disabled, disable stencil, render body and remaining parts. |
| Scope | Render ring without clipping; write ocular IDs; render body where stencil equals zero; apply aperture inversion; render the black ocular where stencil equals i+1 and division where it equals ~(i+1)&255. |
| Mixed sight/scope | Write only scope ocular IDs before body clipping; then add the other ocular IDs; invert only scope apertures. Non-scope divisions use EQUAL i+1. Preserve the two mask phases, not just the final mask. |
| Aperture | Radius is 80 * scopeViewRadiusModifier * aimingProgress. Triangle fan has 90 segments, z=-90, center derived from posed ocular center x/y *16*90. Invert stencil only where the corresponding ocular ID and depth test pass. Keep the source projection/pose conventions. |
| Gun body | Render the installed scope first. Scope-only uses EQUAL 0 for the gun; mixed mode uses GREATER with reference 127 (127 > stored value). KEEP operations preserve the mask until the body is finished, then clear it. |
| Bounds and state | Source rejects division-loop index i>127; values near 127/128 need explicit reference tests. Preserve animated ancestor transforms, LOD context, light/overlay, texture/translucency, ring/body visibility, laser ordering and queued functional renderers. Restore all temporary visibility, poses and state on exceptions. |
| Optional renderers | AR separates ring/body/reticle/gun into ordered layers starting at -943 with callbacks; Oculus flushes its actual special buffer type. Both old APIs are incompatible with target collectors. No Forge 26.2 companion was verified for these. |

The attachment entry point and specialized render methods both call `super.render` in the baseline. Audit the actual submitted geometry before changing this apparent duplication; source structure alone is not evidence that it is visually redundant.

## Verified target constraints

- `DepthStencilState` contains depth compare/write/bias only. **Correction to the earlier audit:** Forge's `RenderTarget.enableStencil()` and `GpuDevice.createTexture(..., boolean stencil)` do exist in the pinned sources. OpenGL allocates a stencil-capable depth texture; the default backend overload (used by Vulkan) discards the flag. There is still no native pipeline stencil compare/write description. Old global shader setters and `MultiBufferSource` are absent.
- `SubmitNodeCollector.CustomGeometryRenderer.render` is invoked by `CustomFeatureRenderer.buildGroup` during **preparation**. Actual draws happen later through `RenderTypeFeatureRenderer.executeGroup` and `PreparedRenderType.drawFromBuffer`. GL state changes inside the geometry callback cannot bracket the resulting draw.
- `SubmitNodeCollection` selects solid/outline/translucent phases based on RenderType. Phase sorting can consolidate or reorder compatible geometry. `order(int)` alone does not express interleaved mask passes or resource dependencies.
- `FeatureRenderDispatcher` owns a `FeatureRendererMap`, calls begin/prepare/finish preparation, uploads its StagedVertexBuffer, then executes phase groups and finally finishExecute. `ScopeFeatureDispatcherMixin` now registers the custom renderer at constructor return; actual Mixin/FML application remains untested.

## Implemented backend

- `ScopeMaskState` expresses reference-left ALWAYS/EQUAL/GREATER, COLOR/REPLACE/INVERT, reference clamping to 0..255 and the reticle depth override. The software reference exhausts every eight-bit comparison pair and checks 100 independent mixed programs against GL-style fragment/stencil evaluation.
- `ScopeRenderPlan` owns immutable geometry/state commands. Its pass scheduler clears the first mask, copies the previous mask before each write, alternates two attachments, and keeps color commands on the current mask. Commands inside one job never consolidate or reorder. Cleanup runs on failure.
- `ScopeFeatureRenderer` prepares vertices in the native StagedVertexBuffer and executes real render passes later. Each job owns two RGBA8 mask textures and their views; fenced cleanup keeps them alive through command submission. Dimensions come from the actual output target/override. A job currently requires one color/depth pair and rejects target changes explicitly.
- `ScopePipelines` retains the original pipeline's shader defines (including string values), geometry format/topology, bindings, culling, color/blending and depth bias. Mask updates disable blending/depth writes; sight reticles can disable depth testing. Multiple color outputs are rejected explicitly.
- `ScopeShader` wraps the current fragment source, preserving its alpha rejection and color computation, and uses pixel-aligned texelFetch for the eight-bit mask. The native device owns compiled programs; after its shader-cache clear, the wrapper recompiles from the current source. Pack shaders need one main and a vec4 fragColor output. Missing/unsupported shaders fail visibly.
- `ScopeAperture` expands the original 90-segment fan to 270 triangle vertices, retaining Mth trig, radius/aim scaling, center convention and z=-90. It preserves acceptance of division index 127 and rejection of 128.

## Validation and limits

`verifyPortingScopes` passes 241 CPU/native-description assertions. `verifyPortingScopeGpu` and `verifyPortingScopeVulkan` execute the actual production ScopeFeatureRenderer using a hidden GLFW window, native GPU device, target StagedVertexBuffer, offscreen textures and pixel readback. They cover overlapping ocular IDs, partial/final masks, blackouts, reticles, gun clipping, independent alpha/depth rejection, the production aperture fan, repeated resize and shader-source replacement/cache clear. The shader fixture supplies a small POSITION_COLOR program and prepared texture/transform context; it does not instantiate Minecraft or substitute any main-source class.

OpenGL was exercised on AMD Radeon 760M Graphics (3.3 core, driver 26.8.1.260810); Vulkan on NVIDIA GeForce RTX 5050 Laptop GPU (1.4.351, driver 616.92). Target-native library bootstrap and LWJGL 3.4.1 native artifacts are used. Vulkan validation layers are not enabled. Logs record the actual devices. These results do **not** establish FML/Mixin application, native entity/text/laser shader compilation, gun-pack model visuals, first-person projection equivalence, fabulous output behavior, other GPUs/drivers or actual resource-pack reload integration.

Per-job full-size texture allocation/copy overhead has not been profiled at game resolution. Resource pooling and measured performance remain open. No runtime parity/release gate is closed by these fixtures.

## Next concrete implementation

Capture the attachment and clipped gun body into ONE immutable job while animated state is live. Do not submit the scope separately and let the normal collector reorder the body afterward. `ScopeFeatureRenderer.submit` routes the atomic job into the native translucent-custom phase; the phase can reorder different jobs, never commands inside a job.

The current plan supports captured custom geometry. Extend capture/preparation for **all** affected native submissions before replacing the old calls: TextShowRender submits native text; arms submit native models; functional attachment/item renderers may enqueue other feature types. Capturing only RenderSubmission.submit would let these bypass masks and ordering. A possible next approach is to capture native SubmitNodes and prepare/execute owned native feature renderers inside the scope job, with the mask applied at actual PreparedRenderType draws. This approach is not implemented and requires checking feature grouping/index ownership, target consistency and cleanup.

Keep the source's mixed scope-only mask before the body, later sight mask phase, division depth rules, scope ring/laser ordering and both super.render calls pending geometry evidence. Restore all temporary part visibility and ancestor poses in finally blocks. Preserve required AR/Oculus integration scope; their old buffer/GL callbacks are incompatible and have no verified Forge 26.2 companion yet. RenderHelper's public implicit-texture blit contract also remains unresolved.

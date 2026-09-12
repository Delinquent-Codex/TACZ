# Scope rendering continuation

This is a source/API audit for the next implementation, **not an implemented or tested renderer**. The main build still contains the original stencil calls and fails. Preserve the baseline until an equivalent native pass implementation exists.

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

- `DepthStencilState` contains depth compare/write/bias only. There are no stencil fields in the inspected native pipeline description; `RenderTarget.enableStencil`, old global shader setters and `MultiBufferSource` are absent.
- `SubmitNodeCollector.CustomGeometryRenderer.render` is invoked by `CustomFeatureRenderer.buildGroup` during **preparation**. Actual draws happen later through `RenderTypeFeatureRenderer.executeGroup` and `PreparedRenderType.drawFromBuffer`. GL state changes inside the geometry callback cannot bracket the resulting draw.
- `SubmitNodeCollection` selects solid/outline/translucent phases based on RenderType. Phase sorting can consolidate or reorder compatible geometry. `order(int)` alone does not express interleaved mask passes or resource dependencies.
- `FeatureRenderDispatcher` owns a `FeatureRendererMap`, calls begin/prepare/finish preparation, uploads its StagedVertexBuffer, then executes phase groups and finally finishExecute. The inspected constructor registers native feature renderers directly. No custom scope registration hook has been implemented.

## Next concrete implementation

Capture an immutable scope draw sequence and each affected gun/attachment batch while its animated pose is live. Implement an ordered feature renderer with explicit native mask resources and depth/alpha-aware passes; a sampled mask texture is a candidate because native stencil state is absent. Define how partial and final masks reach body and reticle passes, and how a scope job stays isolated from other items, hands, previews and frames. Do not rely on mutable global textures surviving deferred sorting.

Before replacing the source calls, verify target resource creation, copies, render-pass bindings, shader reload, resize and close APIs. Validate mask overlap, depth rejection, mixed ocular selection, aperture boundaries, body clipping, interruption cleanup and strict ordering with independent reference fixtures. Then test actual GPU output, shader compilation, resource reload and repeated resize. CPU geometry or shader-source checks cannot establish stencil/visual equivalence.

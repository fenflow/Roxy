# Modern engine bridge development

Version 0.4.0 uses the original external Voxy renderer as its normal terrain
engine. The 0.2.x classes remain for historical tests and the reducer probe, but
LegacyTerrain is not initialized. See README.md and VALIDATION.md for the tested
configuration and explicit coverage limits.

## Boundary

The Java 8 Forge entrypoint obtains immutable geometry snapshots on the client
thread using GTNHLib and RenderBlocks. The Java 21 child-loader bridge supplies
modern platform interfaces without loading modern Minecraft or Fabric.
ExternalVoxyLoader validates the pinned JAR, resolves its own RocksDB dependency,
and redirects platform references in memory. ModelInputPatch changes model-input
calls and adapts legacy full-cube face-lighting metadata; the original six-view baker, software rasterizer, mesh generator, world
engine, GPU traversal, renderer and shaders come from the external JAR.

The normal loader is reused across world changes so original static GPU resources
retain one lifecycle. Each world's renderer, worker pool and database are closed
on disconnect or resource reload. Development hot loading is opt-in through
`roxy.developmentBridgeJar`; never require it for installation.

## Diagnostics

- `/roxy engineprobe`: original compute shader and GPU readback.
- `/roxy modelprobe`: original baker with stone, stairs, logs, leaves, water, ice,
  grass and snow; images go under `roxy/model-probe`.
- `/roxy rendererprobe`: isolated pipeline construction and teardown only.
- `/roxy reload`: full game resource reload and model recapture.
- `-Droxy.rendererDiagnostics=true`: original GPU counters and source depth summary
  every 120 frames in `roxy/render-diagnostics.txt`. This adds readback overhead.
- With diagnostics enabled, `/roxy testview <yaw> <pitch>` fixes a reproducible
  camera direction for visual checks (legacy `/tp` has no rotation arguments).

Probes are not substitutes for observing the actual terrain renderer. Test through
the installed production JAR, with no development-loader or memory override flags.

## Rendering invariants

- The legacy world spans section indices 0 through 15, with exclusive max 16.
- The camera projection and model-view matrices come from GTNH's emulated legacy
  state; Voxy handles its own distant projection and final depth conversion.
- Angelica's built sections feed original Voxy near-terrain coverage.
- The core hook runs before native translucent terrain and leaves the original
  RenderGlobal calls intact for Angelica's mixins. Reset the handoff for each
  opaque pass. Establish writable depth/stencil and unclipped viewport state
  before Voxy's framebuffer clears; GTNH can leave stencil writes disabled.
- Save/restore actual core OpenGL state. Deprecated attribute-stack operations are
  invalid in Angelica's core context. Restore indexed buffer ranges, image units,
  all texture targets that Voxy's unit unbinds can affect, samplers, programs,
  vertex arrays, framebuffers, pixel storage, and depth/stencil state.
- Model capture precedes asynchronous Voxy model jobs; those jobs must not invoke
  Minecraft block renderers from worker threads.
- Capture received packets after `Chunk.fillChunk` completes. Retain unfinished
  chunks after unload, prioritize nonempty upper sections fairly, and merge block
  updates into pending work. Run original Voxy ingestion on its bounded worker
  queue; Minecraft block/model callbacks stay on the client thread. Preserve the
  observation queue when changing shaders in the same world.
- Treat mod model callbacks as a fault boundary. Cache a static texture fallback
  once per model after an ordinary exception or linkage error. Do not swallow VM
  errors, and do not fabricate tile entities merely to run unsafe renderers.
- Shader packs use their own native Voxy interface. Publish Voxy matrices before
  Angelica updates custom uniforms, render before Angelica's deferred phase, and
  skip shadow passes. Restore indexed blending and all touched texture, sampler,
  image, UBO and SSBO state. Validate shader contracts before allocating Voxy's
  renderer services; keep all pack-specific decisions in the external pack.
- RenderBlocks may bake directional shade into vertex colour without setting
  GTNHLib's shade flag. Recover that indication before supplying the original
  baker. Fluid snapshots provide the separate viewing-face surroundings expected
  by Voxy, including full-height stacked sides and a lowered open surface.
- Keep the user's external JAR unchanged. Do not add original Voxy source, classes,
  resources, dependencies or generated transformed output to distribution files.

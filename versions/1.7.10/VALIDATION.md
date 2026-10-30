# GTNH terrain validation

## 0.4.0 capture, model isolation and shader bridge — 2026-10-30

- All 40 tests passed on Java 21.0.13 and Java 25.0.5, with the pinned original
  Voxy JAR and actual Angelica 2.2.10 JAR supplied; zero skips or failures.
  Full builds, Spotless, Checkstyle and `verifyNoBundledVoxy` passed.
- New regressions cover fair surface-first capture of 290 chunks, retaining all
  16 sections across movement, merging updates into partially captured chunks,
  one-time model fallback after exceptions/linkage failures, and propagation of
  VM failures. Transformer tests verify valid operand stacks against real
  Minecraft 1.7.10 and Angelica classes. Biome alias tests preserve host mappings.
  Existing full-metadata (60000), original model baker and world database tests
  continue to pass.
- Live configuration: GTNH 2.9.0-beta-3, Angelica 2.2.10, Java 25.0.5,
  Windows 11, RTX 3060 / OpenGL 4.6; four native render-distance chunks and
  a 64-chunk Voxy distance. Testing used a disposable integrated-server world.
- Reproduced the reported `thaumicbases:advAlchFurnace:3` null-tile exception
  by placing the furnace in the test world. The generic model boundary logged
  a static texture fallback and kept the renderer running. Subsequent views
  encountered additional failing models without disabling Voxy. This does not
  imply that tile-dependent machine appearances are exact.
- The initial 289-chunk view settled at 4,636 captured sections (including block
  updates) and zero pending chunks. The old periodic full recaptures were removed.
  Four teleports spanning 512 blocks in 15 seconds under Complementary retained
  received chunks; capture drained to zero pending with 23,615 sections processed.
  Terrain remained visible after leaving the native render area. These counts
  include revisits/updates and are not counts of unique stored sections.
- Tested the latest official versions available at test time:
  **Complementary Reimagined r5.9.3**, **Photon 1.4**, **BSL 10.1.8**.
  Each executed the external `IrisVoxyRenderPipeline` with `glError=0` in sampled
  diagnostics. Their original ZIPs were unchanged. Switching Complementary to
  Photon rebuilt the pipeline and reopened the same world database successfully.
- The shared bridge supplies Voxy's source files/macros, current/previous camera
  matrices, depth samplers, custom uniforms and dependencies, material mappings,
  texture/sampler bindings, render targets, blend state and storage buffers.
  BSL exposed missing modern biome constants in Angelica; generic aliases map
  renamed legacy biomes and use nonmatching IDs for absent modern biomes.
  There are no checks for shader pack names and no bundled pack shaders.
- Inspected elevated terrain, nearby forest shadows, snow/ice shoreline views,
  and moving native/LOD boundaries. BSL's final development run remained ON at
  9,428 captured sections, zero pending chunks and eight model fallbacks.
  No depth flicker or double-drawn nearby terrain was observed in those views.
  Finite visual checks cannot establish absence of all visual bugs; transparency,
  unknown-terrain edges and special model appearances remain limitations.
- Compatibility JAR SHA-256:
  `4327bcb88a5d75a1974927da54c71c12e88e7b8f5bc2ec6bbf06ff6bb4a26581`.
  The separate original Voxy JAR still hashes to
  `7f8226cf64a4f5dc064e1075965714b718ac5f42fb28f826135a37c64e66e10b`.
- A clean packaged-JAR run (no development-loader override) rendered all three
  packs. The final shader shutdown regression exposed a paused-menu lifecycle
  gap; pipeline changes now happen before frame preparation/drawing as well as
  on ticks. Repeating Photon-to-normal switching with the corrected release kept
  Voxy ON, with 4,357 sections, zero pending chunks and eight model fallbacks;
  normal-pipeline diagnostics reported `glError=0`. No renderer/shutdown errors
  appeared in that final run. Binary and sources package audits found zero
  bundled external classes, assets or nested JARs.

Shader packs must expose Voxy's native interface and also work with Angelica.
Other packs/settings, all dimensions, dedicated multiplayer and other GPU vendors
remain unverified. Capture may lag at extreme travel speeds; retained pending
chunks consume memory until processed. GPU readback diagnostics were enabled for
these observations, so this is not a performance benchmark.

## 0.3.0 original Voxy engine — 2026-10-30

This version replaces the active 0.2.x custom draw backend with the external
VoxyRenderSystem. Older results below describe older implementations.

- All 32 checks passed on Java 21.0.13 and Java 25.0.5, with the pinned original
  Voxy JAR supplied and no skipped tests. Full builds, formatting/style checks,
  and `verifyNoBundledVoxy` passed.
- Integration tests execute original WorldEngine, WorldUpdater, reducers,
  serialization, Zstandard and RocksDB code. All five LOD levels persist across
  reopening, including negative coordinates and metadata 60000. Air updates remove
  stored geometry. The registry adapter persists its own records in Voxy's store.
- The original six-view model baker passes a 1,536-pixel cube-face regression.
  Binary-call validation checks actual transformed Voxy platform descriptors,
  including interface linkage. A section-height regression verifies that the
  legacy 0–255 world produces a nonempty top-level traversal range.
- Fluid input tests exercise independently captured top and side geometry through
  the original baker. A RenderBlocks colour regression checks recovery of legacy
  directional shading. The render hook is verified against the actual 1.7.10
  EntityRenderer bytecode, retaining its original calls and valid operand stacks.
- A model-lighting regression distinguishes translucent full cubes from fluids,
  opaque blocks and inset geometry. Only complete translucent cubes request
  adjacent-cell light, matching the legacy RenderBlocks convention.
- Live game: GTNH 2.9.0-beta-3, Angelica 2.2.10, Java 25.0.5, Windows 11,
  RTX 3060 / OpenGL 4.6. The installed compatibility JAR discovered Voxy under its
  original filename in `mods`, enabled automatically and restored the database.
- Voxy's original asynchronous mesher, model store, hierarchy traversal, MDIC GPU
  renderer and SSAO visibly drew mountains, trees, snow, water and terrain.
  A reference view reported 154 visible sections and approximately 282,778 quads,
  with `glError=0`. At approximately one kilometre, visible level-0 and level-1
  sections confirmed that adaptive subdivision was active.
- Examined elevated and shoreline views, near-terrain occlusion and fog handoff,
  daytime/nighttime lightmap changes, travel, and same-JVM world exit/re-entry.
  The reference scene had no observed depth flicker or double-drawn near terrain.
  Static views and a finite set of camera positions cannot prove absence of all
  visual bugs. Unvisited terrain gaps and differing fluid appearances remain limits.
- Reloading the world retained the same GPU allocation counts: 27 buffers / about
  640 MiB and six textures / about 548 MiB. The default geometry budget is 512 MiB;
  the world, GTNH atlas and other mods require additional resources.
- Fixed zero visible sections caused by GTNH's zero stencil write mask. Voxy now
  renders immediately before native translucent terrain, preserving Angelica's
  original terrain calls. The bridge establishes Voxy's expected writable state and
  restores the game state, including indexed buffer ranges, texture/image units,
  dispatch buffers, depth/stencil masks and sampler bindings.
- In-world block additions/removals beyond native render distance update the
  original Voxy meshes. Full resource reload recaptures models and resumes the
  renderer without increasing its stable GPU allocation counts.
- The final installed JAR was checked at the elevated reference view and two
  nearby shoreline positions. GPU readback confirmed ice's translucent/shaded
  flags and adjacent-face lighting metadata; the large ice brightness mismatch
  was reduced. No depth flicker or near-terrain duplication was observed in these
  views. Ice/water composition can still differ from Angelica's native rendering.
- Final compatibility JAR SHA-256:
  `d6d2b75de47ba84721b5c2359c8bf941b76b273223e8f2185fcb0c8596ce0b2b`.
- The unchanged external Voxy SHA-256 is
  `7f8226cf64a4f5dc064e1075965714b718ac5f42fb28f826135a37c64e66e10b`.
  The package audit found 73 compatibility bridge classes and zero Voxy classes,
  Voxy assets, Minecraft classes, or nested JARs.

Angelica 2.1.61, other GPU vendors, dedicated multiplayer, all dimensions,
tile-dependent machine models and every resource pack remain unverified.

## 0.2.1 installation and terrain fixes — 2026-10-30

- Reproduced a missing external JAR with a regression test. It now reports
  the missing dependency, the absolute checked path, and installation steps.
  The previous generic Java warning was emitted for every startup exception.
- Verified unsupported-JAR errors include the required release and both hashes;
  class-version errors retain the JVM exception and running Java version;
  wrapped ABI failures and cache access errors are not mislabeled as Java errors.
- Voxy is identified by its Fabric mod ID in `mods` or `mods/1.7.10`, without
  renaming. Tests cover arbitrary filenames, unrelated/broken JARs, selecting the
  pinned binary among multiple versions, and the old location as a fallback.
- All 23 tests passed on Java 21.0.13 and 25.0.5, including the real external Voxy reduction
  and discovery tests with no skipped tests. Java 25.0.1 itself was not tested here.
- Full build, Checkstyle, Spotless, and `verifyNoBundledVoxy` passed.
- Cross-chunk face culling suppresses solid interior walls; unknown neighbors
  are not treated as air. Lighting can sample adjacent chunks. Neighbor changes
  invalidate affected meshes, retaining the previous mesh until its replacement
  is ready. Regression tests cover solid seams, real cliffs, negative coordinates,
  unknown neighbors, lighting, and neighbor replacement.
- The projection is extended before near terrain writes depth, so LODs share its
  depth mapping. Terrain now uses the external reducer's two-block level, whose
  offset and spatial ordering are checked against the real external JAR.
- The cache format/directory is v2. Old caches are preserved; terrain is recaptured.
  Buffered cache I/O avoids byte-at-a-time compression overhead.
- GTNH 2.9.0-beta-3 with Angelica 2.2.10 and Java 25.0.5 loaded the original Voxy
  filename from `mods` in a world with the old fallback path disabled.
- Visually checked the rebuilt cache in the disposable world from elevated,
  downward-facing views and after moving the camera 64 blocks. Mountain, snow,
  and forest LODs remained behind nearer terrain without the tall interior walls.
  Toggling LODs off/on and re-entering the world worked; 192 cached chunks restored
  on re-entry, then capture reached 357 chunks after moving, with zero reported
  Roxy errors. A lower view at Y=95 retained detailed near trees in front of the
  LOD mountain and forest. The near/far resolution and fluid transitions remain
  visible; this is not seamless full-model rendering.
  This checks the identified defects in the local fixture, not the reporter's
  exact world, resource packs, or Angelica 2.1.61.
- 0.2.1 JAR SHA-256:
  `7019e993a41c3fc3c3619abc3de451c5466b108075f5bb9b2861f0f394547853`.

## 0.2.0 in-game validation — 2026-10-29

This module now has a legacy terrain backend. The external Voxy JAR supplies its
original voxel reduction; Voxy's modern GPU renderer and world engine are not run.

## Automated checks

Using JDK 25 for Gradle and JDK 8 / 21 toolchains:

```text
gradlew.bat build -ProxyVoxyJar=<original-0.2.16-beta+1.21.11.jar>
BUILD SUCCESSFUL
10 tests, 0 failures, 0 skipped
spotlessCheck, checkstyleMain, checkstyleTest: passed
verifyNoBundledVoxy: passed
```

Tests cover full EndlessIDs metadata, opacity/state identity, packed light and
biome ranges, invalid external-JAR rejection, original Voxy reduction on empty,
uniform, sparse and spatially patterned sections, the level-2 ABI offset/order,
cache round trips with negative coordinates and 16-bit metadata, corrupt cache
headers/palette sizes, exposed-face lighting, and unchanged external-JAR bytes.

Release JAR SHA-256:
`d127a1c09322e686f06bef5e9807c21a4b78134d7c2ece4fd5ed285b7a62998f`.

External Voxy JAR SHA-256, unchanged after testing:
`7f8226cf64a4f5dc064e1075965714b718ac5f42fb28f826135a37c64e66e10b`.

## In-game environment

- Separate extraction of the supplied GTNH 2.9.0-beta-3 Java 17–26 archive.
- Java 21.0.13, Forge 10.13.4.1614, Angelica 2.2.10, LWJGL3ify 3.0.31,
  EndlessIDs 1.7.4; 322 loaded mods.
- NVIDIA RTX 3060, OpenGL 4.6, driver 616.92; 1024 x 640 game area, vsync 60 Hz.
- A disposable creative singleplayer save, `Roxy Bridge Test`, with offline test
  identity `RoxyTest`. Original pack archives were not modified.
- Normal render distance reduced from 12 to 4 chunks to expose LODs; LOD radius 32.

## Observed checks

- Loaded the main menu and world repeatedly without a Roxy startup error.
- Captured terrain with GTNH blocks and extended metadata through the external
  Voxy classes. The earlier probe found and fixed a four-bit metadata assumption.
- Drew textured distant terrain, biome tints, and lightmap lighting. The first
  visual run found opaque-face lighting sampled inside solids. Changed it to use
  the adjacent air cell. An aerial view then found missing light in empty upper
  sections; those sections now preserve their light data too.
- Restarted the entire Java client and restored 289 cached chunks. The diagnostic
  showed zero capture errors and 94 drawn LOD chunks / 35,006 quads in that view.
- Traversed desert, highland and snow-forest areas at elevated camera positions.
  The snow-covered mountain remained visible outside the normal render distance.
- Observed daylight, rain/snow and sunset; LOD lightmap illumination changed with
  the world rather than remaining fixed at capture time.
- Reloaded resources through the Resource Packs screen without changing selected
  packs. Returned to the world with textured LODs intact, zero reported Roxy
  errors, and 138 visible LOD chunks / 50,396 quads in that view.
- The client counter was generally 56–57 FPS at the 60 Hz vsync cap in these views;
  one travel/loading sample showed 49 FPS. These observations are a smoke test,
  not a controlled performance benchmark or a claim about other hardware.
- Entered the Nether through a portal using the exact release JAR. Captured 289 Nether chunks into a separate `dim-1` cache with no Roxy capture or rendering error in the client log.
- Saved and returned to the title screen; exited the tested clients normally.

The first launches used a minimal asset setup and logged missing sounds. The
complete official 1.7.10 asset objects were subsequently downloaded and SHA-1
verified for the release-candidate run. The local launch also honors Forge's
Guava 17 override instead of mixing it with vanilla's Guava 15.

## Scope

This is a compatibility alpha with four-block textured terrain cells, opaque far
fluid approximations, a derived client cache, and a legacy draw backend. It does
not validate modern Voxy GPU features, shader packs, server import/worldgen,
custom distant machine models, dedicated multiplayer, or every GTNH dimension.
See README for installation and implementation limits.

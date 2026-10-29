# GTNH terrain validation — 2026-10-29

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

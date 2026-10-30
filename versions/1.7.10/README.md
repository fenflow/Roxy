# Roxy 1.7.10 / GTNH — original Voxy engine alpha

Version 0.3.0 runs the original external Voxy GPU renderer, model baker, world
engine/database, and adaptive LOD hierarchy inside GTNH. Roxy supplies the legacy
Minecraft/Forge/Angelica interfaces and lifecycle. The original Voxy JAR remains
separate and unchanged. The previous 0.2.x custom terrain renderer is no longer
initialized.

This is a playable development alpha for the configuration below. It has been
visually tested in a disposable GTNH world; it is not a guarantee that every
GTNH block, dimension, resource pack, GPU, or camera angle renders correctly.
See [validation](VALIDATION.md) for evidence and remaining coverage gaps.

## Install

1. Use the **Java 17–26 edition of GTNH 2.9.0-beta-3**, running **Java 21 or newer**,
   with **Angelica 2.2.10** and the pack's LWJGL3ify installation.
2. Remove older Roxy JARs and put `roxy-0.3.0-gtnh-alpha.jar` in `.minecraft/mods`.
3. Obtain the original [Voxy 0.2.16-beta for Minecraft 1.21.11](https://modrinth.com/mod/voxy/version/H3w2nVdU)
   separately and put it in the same `mods` folder. Keep its original filename.
4. Disable Iris shader packs, launch the game, enter a world, and explore. Voxy
   starts automatically and retains observed terrain after you leave an area.

```text
.minecraft/mods/
  roxy-0.3.0-gtnh-alpha.jar
  voxy-0.2.16-beta+1.21.11.jar
```

Roxy identifies Voxy by its Fabric mod ID in `mods` or `mods/1.7.10`, then verifies
the binary. The old `roxy/voxy.jar` location remains a fallback. No renaming is
required. Only Modrinth version `H3w2nVdU` is accepted; its SHA-256 is:

`7f8226cf64a4f5dc064e1075965714b718ac5f42fb28f826135a37c64e66e10b`

Use a GPU/driver supporting Voxy's modern compute and direct-state-access OpenGL
requirements. Live tests used an NVIDIA RTX 3060 with OpenGL 4.6 on Windows 11.
Other GPU vendors and operating systems have not been validated here.

## What runs in game

| Responsibility | Implementation |
| --- | --- |
| Nearby chunks, entities, machines, gameplay | GTNH and Angelica |
| Legacy block IDs, metadata, biomes, geometry, atlas and lightmap access | Roxy compatibility code |
| Six-view model baking, rasterization and model texture generation | Original external Voxy |
| Voxel reduction, five-level hierarchy, world updates, section serialization, RocksDB/Zstandard storage | Original external Voxy, with Roxy registry adapters |
| Asynchronous mesh generation, GPU hierarchy/occlusion traversal, indirect drawing, SSAO and composition | Original external Voxy |
| Camera matrices, framebuffer/depth handoff, near-chunk coverage and lifecycle | Roxy compatibility code |

Roxy captures RenderBlocks geometry through GTNHLib and feeds it to Voxy's original
baker. It does not execute Fabric's entrypoint or modern Minecraft mixins. The
original Voxy rendering pipeline performs the distant terrain rendering. The
model adapter also preserves legacy directional shading and adjacent-face lighting
for translucent full cubes such as ice; fluid and inset geometry keep their own
lighting rules.

## Controls and storage

- `/roxy on`, `/roxy off`: start or stop Voxy, including capture.
- `/roxy status`: renderer state, ingested section count and distance or failure.
- `/roxy hud`: toggle the status overlay; F3 also shows original Voxy GPU counters.
- `/roxy distance 64`: set maximum distance, from 8 to 128 chunks.
- `/roxy reload`: reload game resources and rebuild captured models.
- `/roxy probe`: check the original reducer independently of terrain rendering.

The default radius is 64 chunks (1,024 blocks). Normal video render distance
controls nearby terrain; set it below the Voxy distance to see LODs. Voxy chooses
resolution adaptively and uses full-resolution sections near the boundary.
Increasing distance does not generate unexplored terrain or request extra chunks
from a server. Unobserved areas remain absent and can expose cached terrain edges.

Settings use the `voxy` category of `config/roxy.cfg`. The original Voxy world
database lives under `roxy/voxy-worlds/<world-or-server-hash>/dim<id>`. Registry
names, full metadata and biome identities survive restarts. The 0.2.x legacy
cache is preserved but not imported; revisit terrain to populate the new database.
If a server resets its world at the same address, move aside its derived database
while the game is closed. Roxy does not write to Minecraft region files.

The default geometry allocation is 512 MiB, in addition to Voxy's model textures
and other buffers. An explicit `-Dvoxy.geometryBufferSizeOverrideMB=...` overrides
it. The tested scene used roughly 640 MiB of Voxy buffers and 548 MiB of textures;
GTNH, Angelica and the game atlas require additional memory.

## Tested target and limits

| Component | Tested version |
| --- | --- |
| GTNH | 2.9.0-beta-3 |
| Minecraft / Forge | 1.7.10 / 10.13.4.1614 |
| Angelica | 2.2.10, shader packs disabled |
| GTNHLib / Hodgepodge | 0.11.46 / 2.7.196 |
| EndlessIDs | 1.7.4, including 16-bit metadata |
| LWJGL3ify / LWJGL | 3.0.31 / 3.4.2 |
| Automated test runtimes | Java 21.0.13 and 25.0.5 |
| Live game runtime | Java 25.0.5 |

- Tile-entity-dependent appearances, animated special renderers, connected
  textures, and neighbor-dependent models are not fully represented by a static
  block/metadata snapshot. Detailed nearby rendering remains Angelica's job.
- Ice and water can still differ in appearance across the native/LOD boundary;
  this alpha does not promise pixel-identical transparency or bug-free visuals.
- Iris shader packs are unsupported and produce an explicit renderer error.
  Disable the pack and use `/roxy on` to retry. Environmental fog hides distant
  drawing underwater, in lava, or during blindness.
- Capture has a soft six-millisecond/four-section budget per tick. Block and
  lighting changes receive priority; loaded chunks also refresh periodically.
  Expensive mod callbacks can exceed the soft budget.
- The database grows with explored terrain. There is no server importer,
  background world generation, or dedicated multiplayer validation in this alpha.
- Angelica 2.1.61, other pack releases, all dimensions and all modded machine
  appearances remain unverified. No claim of completely bug-free visuals is made.

## Troubleshooting

`/roxy status` and `logs/fml-client-latest.log` report the real failure. A missing
Voxy JAR is not a Java version problem. `UnsupportedClassVersionError` means the
launcher must select Java 21+; merely installing a newer runtime is insufficient.
An unsupported binary error means the exact external release above is required.

Developer commands `/roxy engineprobe`, `/roxy modelprobe`, and
`/roxy rendererprobe` exercise individual components, not full gameplay.
`-Droxy.rendererDiagnostics=true` writes GPU/depth diagnostics to
`roxy/render-diagnostics.txt` and adds readback overhead. Leave it off normally.

## External-JAR boundary and build

Published artifacts contain compatibility code only. They contain no Voxy
classes, shaders, assets, sources, nested JARs, or transformed class files. The
isolated loader reads the user's pinned original JAR and adapts its platform
references, model-input calls and legacy face-lighting metadata in memory. It extracts the JAR's own RocksDB JNI
dependency into a local runtime cache. No transformed Voxy output is saved.

This describes the technical distribution boundary; it is not permission from
Voxy's author or a legal guarantee. Voxy remains subject to its own license.

This directory is an independent Gradle build; the root project is modern Roxy.
Use JDK 25 for Gradle with JDK 8 and 21 available as compiler toolchains:

```powershell
.\gradlew.bat build '-ProxyVoxyJar=C:/path/to/voxy-0.2.16-beta+1.21.11.jar'
.\gradlew.bat test '-ProxyTestJava=25' '-ProxyVoxyJar=C:/path/to/voxy-0.2.16-beta+1.21.11.jar'
```

The external JAR is required to compile the bridge and run integration tests; no
build task downloads or republishes it. Install `build/libs/roxy-0.3.0-gtnh-alpha.jar`,
not the `-dev` or `-sources` artifacts. `verifyNoBundledVoxy` audits the artifact.

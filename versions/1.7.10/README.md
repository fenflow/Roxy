# Roxy 1.7.10 / GTNH — terrain compatibility alpha

This branch supplies a Forge 1.7.10 compatibility layer for a separately installed,
original Voxy JAR. It uses Voxy's original voxel reduction code with a new legacy
rendering backend and client cache. It does **not** run Voxy's modern GPU renderer,
world engine, model baker, Fabric entrypoint, or mixins.

The legacy backend displays textured, four-block LOD cells for terrain your client
has visited. Nearby blocks, machines, entities, and gameplay remain rendered by
GTNH/Angelica. This is an early playable alpha, not feature parity with
modern Roxy/Voxy. See [validation](VALIDATION.md) for the exact tests and limits.

## Install

1. Use the **Java 17–26 edition of GTNH 2.9.0-beta-3**, with **Java 21 or newer**.
2. Put `roxy-0.2.0-gtnh-alpha.jar` in the instance's `.minecraft/mods` folder.
   Remove older Roxy probe JARs from that folder.
3. Obtain the original [Voxy 0.2.16-beta for Minecraft 1.21.11](https://modrinth.com/mod/voxy/version/H3w2nVdU)
   separately and place it at **`.minecraft/roxy/voxy.jar`**.
4. Launch the pack and explore. LODs are collected over several seconds as chunks
   arrive. They remain available after leaving an area or restarting the game.

Do not put the modern Voxy JAR in Forge's `mods` folder. Java 8 and Java 17 cannot
execute its Java 21 classes. An unsupported or missing external JAR disables Roxy
with a client message; the rest of GTNH can still run.

Only Modrinth version `H3w2nVdU` is accepted. Its SHA-256 is:

`7f8226cf64a4f5dc064e1075965714b718ac5f42fb28f826135a37c64e66e10b`

## Controls

- `/roxy on` and `/roxy off`: toggle distant drawing. Capturing continues when off.
- `/roxy status`: cache, capture, restored-chunk, drawing, and error counters.
- `/roxy hud`: toggle the two-line diagnostic overlay. F3 also shows it.
- `/roxy distance 32`: set the LOD radius, between 8 and 64 chunks.
- `/roxy probe`: independently test the external binary on the current section.

The default LOD radius is 32 chunks (512 blocks). The normal video render distance
controls where LODs begin. Set it below the LOD radius to see the difference.
Increasing LOD distance does not generate or download unexplored terrain. On a
server, only chunks actually sent to the client can be captured.

Configuration lives in `.minecraft/config/roxy.cfg`. Derived terrain files live
under `.minecraft/roxy/cache-v1/<world-or-server-hash>/dim<id>`. Cache files store
registry names and full metadata, not unstable numeric Forge block IDs. Biome
tints are resolved while capturing; Voxy's temporary biome IDs are not persisted.
If a server resets its world at the same address, remove its derived cache while
the game is closed. Roxy never writes to Minecraft region files or save data.

## Supported target

| Component | Tested version |
| --- | --- |
| GTNH | 2.9.0-beta-3 |
| Minecraft / Forge | 1.7.10 / 10.13.4.1614 |
| Angelica | 2.2.10, shader packs disabled |
| GTNHLib | 0.11.46 |
| Hodgepodge | 2.7.196 |
| EndlessIDs | 1.7.4, including 16-bit metadata |
| LWJGL3ify / LWJGL | 3.0.31 / 3.4.2 |
| Runtime used for validation | Java 21.0.13 |

## Current limits

- Far terrain uses textured cubes at four-block resolution. Tile-dependent
  machine appearances, non-cube models, connected textures, and exact foliage
  shapes are approximated. Near terrain retains its normal detailed models.
- Distant fluids are opaque approximations. Shader-pack integration is not
  implemented; distant drawing suspends when Angelica/Iris has a pack active.
- Surface lighting uses the adjacent air cell and GTNH's live lightmap. LODs are
  hidden underwater, in lava, and during blindness to preserve environmental fog.
- This is a client-observation cache, with no server importer, world generation,
  modern Voxy database support, GPU occlusion traversal, or adaptive LOD hierarchy.
- Capture runs on the client thread, at most two sections and a soft 4 ms budget
  per tick. A single expensive mod block callback can exceed that budget. Nearby
  chunks refresh approximately every five seconds; others approximately every
  minute, subject to queue backlog.
- Disk IO is asynchronous and bounded; GPU uploads are limited to two chunks per
  frame. At most 4,096 chunk snapshots are retained in memory. The disk cache grows
  with explored terrain. Cache files use compressed, atomic replacement.
- Other GTNH versions, dedicated multiplayer servers, and all modded dimensions
  require additional validation. A successful build is not evidence for them.

## External-JAR boundary

No Voxy classes, shaders, assets, source, nested dependencies, or transformed
class files are distributed. An isolated loader reads three classes from the
user's original JAR: `VoxelizedSection`, `Mipper`, and
`WorldVoxilizedSectionMipper`. It changes Mapper references in memory to an
independent legacy ABI adapter. Voxy's original implementation computes the LOD
values. The external JAR remains unchanged on disk.

The legacy cache, lifecycle hooks, block/metadata mapping, and draw backend are
compatibility code in Roxy's namespace. `verifyNoBundledVoxy` checks the built
artifact for Voxy namespaces, assets, and nested JARs. This distribution does not
include permission from Voxy's author; the separate Voxy release declares ARR.

## Build

This directory is an independent Gradle build. The root build remains the modern
NeoForge project. Use JDK 25 for Gradle, with JDK 8 and JDK 21 discoverable as
compiler/test toolchains:

```powershell
.\gradlew.bat build '-ProxyVoxyJar=C:/path/to/voxy-0.2.16-beta+1.21.11.jar'
```

The production JAR is `build/libs/roxy-0.2.0-gtnh-alpha.jar`; do not install the
`-dev` or `-sources` JAR. Omitting `roxyVoxyJar` skips the external-binary integration
test. No build task downloads or republishes Voxy.

## References

- [Roxy upstream](https://github.com/RasaNovum/Roxy)
- [Voxy external release](https://modrinth.com/mod/voxy/version/H3w2nVdU)
- [GTNH build template](https://github.com/GTNewHorizons/ExampleMod1.7.10)
- [Angelica](https://github.com/GTNewHorizons/Angelica)

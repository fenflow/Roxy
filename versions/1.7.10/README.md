# Roxy 1.7.10 / GTNH — terrain compatibility alpha

This branch supplies a Forge 1.7.10 compatibility layer for a separately installed,
original Voxy JAR. It uses Voxy's original voxel reduction code with a new legacy
rendering backend and client cache. It does **not** run Voxy's modern GPU renderer,
world engine, model baker, Fabric entrypoint, or mixins.

The legacy backend displays textured, two-block LOD cells for terrain your client
has visited. Nearby blocks, machines, entities, and gameplay remain rendered by
GTNH/Angelica. This is an early playable alpha, not feature parity with
modern Roxy/Voxy. See [validation](VALIDATION.md) for the exact tests and limits.

## Install

1. Use the **Java 17–26 edition of GTNH 2.9.0-beta-3**, with **Java 21 or newer**.
2. Put `roxy-0.2.1-gtnh-alpha.jar` in the instance's `.minecraft/mods` folder.
   Remove older Roxy probe JARs from that folder.
3. Obtain the original [Voxy 0.2.16-beta for Minecraft 1.21.11](https://modrinth.com/mod/voxy/version/H3w2nVdU)
   separately and put it in the **same `.minecraft/mods` folder**. Keep its original
   filename; Roxy identifies Voxy by its `fabric.mod.json` metadata and verifies
   the supported binary's SHA-256. The `mods/1.7.10` subfolder is also searched.
4. Launch the pack and explore. LODs are collected over several seconds as chunks
   arrive. They remain available after leaving an area or restarting the game.

Java 8 and Java 17 cannot execute Voxy's Java 21 classes.
An unsupported or missing external JAR disables Roxy
with a client message; the rest of GTNH can still run.

Both JARs go in the mods folder:

```text
.minecraft/mods/
  roxy-0.2.1-gtnh-alpha.jar
  voxy-0.2.16-beta+1.21.11.jar
```

In Prism, open the selected instance's Minecraft folder to find the correct
`.minecraft`. No renaming is needed. Existing 0.2.0 installations using
`.minecraft/roxy/voxy.jar` still work as a fallback when no Voxy is found in mods.

### Startup troubleshooting

Version 0.2.0 displayed "Use Java 21+" for **every** startup exception, including
a missing external JAR. That message alone does not mean Java is too old.
Version 0.2.1 reports the actual exception, the checked path, and the running JVM;
`/roxy status` repeats the failure and `/roxy probe` checks the external JAR again.

- **No external Voxy JAR found / NoSuchFileException:** put the original linked
  Voxy release in this instance's `mods` folder. Re-enter the world after
  correcting it, or relaunch the instance. Roxy logs the selected JAR's path.
- **Unsupported Voxy JAR:** use the exact linked release below. Other Voxy
  releases, including ones with the same version number for a different Minecraft
  version, are not accepted. The error includes the actual and expected SHA-256.
- **UnsupportedClassVersionError:** select Java 21+ for this instance in the
  launcher. Installing a newer Java without selecting it does not change the JVM
  used by the game.
- Other errors retain their exception and root cause. Check
  `logs/fml-client-latest.log`; changing Angelica cannot fix a missing external JAR.

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
under `.minecraft/roxy/cache-v2/<world-or-server-hash>/dim<id>`. Cache files store
registry names and full metadata, not unstable numeric Forge block IDs. Biome
tints are resolved while capturing; Voxy's temporary biome IDs are not persisted.
If a server resets its world at the same address, remove its derived cache while
the game is closed. Roxy never writes to Minecraft region files or save data.
The old four-block `cache-v1` is left untouched. Explore again to populate the
finer cache after upgrading from 0.2.0.

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
| Runtimes used for validation | Java 21.0.13 and 25.0.5 |

## Current limits

- Far terrain uses textured cubes at two-block resolution. Tile-dependent
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

The production JAR is `build/libs/roxy-0.2.1-gtnh-alpha.jar`; do not install the
`-dev` or `-sources` JAR. Omitting `roxyVoxyJar` skips the external-binary integration
test. No build task downloads or republishes Voxy.

## References

- [Roxy upstream](https://github.com/RasaNovum/Roxy)
- [Voxy external release](https://modrinth.com/mod/voxy/version/H3w2nVdU)
- [GTNH build template](https://github.com/GTNewHorizons/ExampleMod1.7.10)
- [Angelica](https://github.com/GTNewHorizons/Angelica)

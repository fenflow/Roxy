# Roxy 1.7.10 / GTNH compatibility work

**Status: experimental bridge probe, not a working LOD renderer. Do not install this expecting distant terrain.**

This branch follows Roxy's external-JAR compatibility approach. It does not fork,
copy, compile, bundle, or publish Voxy's implementation or assets. The original
Voxy JAR is supplied separately and stays unchanged on disk. Roxy currently loads
three original classes through an isolated loader and redirects their Mapper
references to an independently implemented legacy adapter in memory. Voxy's own
code performs the LOD reduction.

This distribution arrangement avoids including Voxy in the Roxy artifact. It is
not a statement of permission from Voxy's author or a legal determination about
runtime interoperability. Voxy's release declares All Rights Reserved.

## Exact target

| Component | Version |
| --- | --- |
| Supplied modpack | GT New Horizons 2.9.0-beta-3 |
| Minecraft / Forge | 1.7.10 / 10.13.4.1614 |
| Angelica | 2.2.10 |
| GTNHLib | 0.11.46 |
| Hodgepodge | 2.7.196 |
| LWJGL3ify / LWJGL | 3.0.31 / 3.4.2 |
| Runtime Java | 21+ in the Java 17-26 pack |
| External Voxy | 0.2.16-beta for 1.21.11, Modrinth H3w2nVdU |

The external Voxy classes require Java 21. The supplied Java 8 pack and Java 17
runtime cannot execute them. The adapter itself compiles to Java 8 bytecode for
compatibility with legacy Forge tooling.

Pinned Voxy SHA-256:
`7f8226cf64a4f5dc064e1075965714b718ac5f42fb28f826135a37c64e66e10b`.

## Build and test

This is a separate Gradle build. The repository's root build still targets
modern NeoForge and must not be used for the legacy module.

Use JDK 25 for Gradle (required by GTNH build conventions 2.0.33), with JDK 8 and
JDK 21 discoverable as toolchains. From this directory:

```powershell
.\gradlew.bat build
.\gradlew.bat test '-ProxyVoxyJar=C:/path/to/voxy-0.2.16-beta+1.21.11.jar'
```

The binary integration test is explicitly skipped without `roxyVoxyJar`. It must
pass with that property before claiming that the external Voxy integration was
tested. No task downloads or republishes Voxy. `verifyNoBundledVoxy` checks the
mod JAR for Voxy classes, assets, and nested JARs.

## What works so far

- A Forge 1.7.10 client entrypoint and `/roxy probe` client command.
- Legacy block registry names, metadata, opacity, biome columns and light data
  are converted to Voxy's voxel layout without changing the world.
- A private class loader restricts Voxy loading to `VoxelizedSection`, `Mipper`
  and `WorldVoxilizedSectionMipper`. Fabric entrypoints and mixins are not started.
- Runtime Mapper rebinding allows Voxy's original reduction code to create all
  five LOD levels, including empty, uniform and sparse chunk sections.
- Unknown external binaries are rejected before loading code.

For a developer probe only, put the reobfuscated `build/libs/roxy-0.1.0-gtnh-dev.jar`
in a **test instance's** `mods` directory. Put the original Voxy JAR at
`.minecraft/roxy/voxy.jar`, outside `mods`, then run `/roxy probe` in a world on
Java 21+. Do not put modern Voxy directly in Forge 1.7.10's `mods` folder.

The probe samples the player's current 16-cubed section and discards the result.
Its block and biome IDs are temporary, so it must not be connected to persistent
storage without adding stable world-scoped mappings.

## Required before this is a usable backport

1. Persistent world/dimension identity, block and biome mappings, chunk updates,
   unload/reload behavior, background ingestion and bounded queues.
2. Adapters for Voxy's world engine, storage and lifecycle, replacing modern
   Minecraft/NBT/registry dependencies at the runtime boundary.
3. Legacy `RenderBlocks` / mod block geometry and texture-atlas capture for Voxy's
   model baker, including GTNH metadata and tile-dependent block appearances.
4. Voxy GPU renderer initialization and an Angelica 2.2.10 render hook, framebuffer,
   depth, camera, fog, GL-state and resource-reload adapters.
5. Separate integration with Angelica's shader pipeline and validation of world
   switching, terrain updates, fluids, lighting and GL resource cleanup.
6. In-game visual and performance tests in the supplied GTNH pack.

The renderer is not enabled by this build. A passing build or probe is not evidence
that any of the unfinished rendering work is functional.

## References

- [Roxy upstream](https://github.com/RasaNovum/Roxy)
- [Original Voxy release and ARR declaration](https://modrinth.com/mod/voxy/version/H3w2nVdU)
- [GTNH build template](https://github.com/GTNewHorizons/ExampleMod1.7.10)
- [Angelica](https://github.com/GTNewHorizons/Angelica)

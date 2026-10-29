# Bridge verification — 2026-10-29

This records an initial compatibility milestone. **No Voxy terrain rendering was tested or implemented.**

## Build

From `versions/1.7.10`, using JDK 25 for Gradle and JDK 8 / 21 toolchains:

```text
gradlew.bat build -ProxyVoxyJar=<path-to-original-0.2.16-beta+1.21.11.jar>
BUILD SUCCESSFUL
6 tests, 0 failures, 0 skipped
spotlessCheck: passed
checkstyleMain / checkstyleTest: passed
verifyNoBundledVoxy: passed
```

Tests cover palette capacity, unsigned light/biome/state fields, independent
metadata/opacity states, full EndlessIDs metadata, rejection of unknown binaries,
and execution of the original Voxy section and mipping classes. The binary test
checks uniform, empty, and sparse sections, coordinates, non-air counts, and that
the external JAR's SHA-256 is unchanged.

Built mod SHA-256:
`b11b6cd564e230da1113b4671bb537adb3c1d97b4a77732f18ad521f59ed23a9`.

External Voxy JAR SHA-256:
`7f8226cf64a4f5dc064e1075965714b718ac5f42fb28f826135a37c64e66e10b`.

## Supplied GTNH pack

- Extracted a separate copy of `GT_New_Horizons_2.9.0-beta-3_Java_17-26.zip`.
- Used Java 21.0.13, Forge 10.13.4.1614, Angelica 2.2.10, LWJGL3ify 3.0.31,
  and EndlessIDs 1.7.4 from that pack.
- Installed the reobfuscated Roxy probe into the isolated copy's `mods` directory;
  the original Voxy JAR was kept outside `mods`, at `roxy/voxy.jar`.
- Reached the main menu with 322 loaded mods, then created a separate creative
  world named `Roxy Bridge Test`.
- First in-game probe exposed a four-bit metadata assumption. Confirmed that
  EndlessIDs extends metadata to 16 bits, removed the truncation assumption,
  added a regression test, rebuilt and restarted the client.
- Reopened the same world and ran `/roxy probe` on the same terrain section.

The client returned:

```text
Roxy bridge passed: 4681 voxels across 5 LOD levels, 31 legacy states. Renderer not implemented yet.
```

The test launch initially included both Minecraft's Guava 15 and Forge's Guava 17.
Correcting the local launch classpath to honor Forge's override resolved the
access-transformer startup failure. The pack's mod files were not changed for
that correction. The minimal smoke-test asset setup lacked sounds, producing
missing-sound warnings; this does not validate sound or full launcher packaging.

## Limits

The successful command captures and mipmaps one live section, then discards it.
It does not start Voxy's world engine, persist data, bake models, render distant
terrain, integrate shaders, or test multiplayer/dimension changes. Java 8 and
Java 17 cannot execute the original Java 21 Voxy classes. See README for the
remaining implementation work.

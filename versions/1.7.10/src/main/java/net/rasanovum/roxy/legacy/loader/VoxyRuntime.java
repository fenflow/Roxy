package net.rasanovum.roxy.legacy.loader;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.rasanovum.roxy.legacy.bridge.LegacyMapper;

/** Runs a deliberately bounded part of the user's original Voxy binary. */
public final class VoxyRuntime implements Closeable {

    public static final String SUPPORTED_SHA256 = "7f8226cf64a4f5dc064e1075965714b718ac5f42fb28f826135a37c64e66e10b";
    private static final String MIPPER = "me.cortex.voxy.common.world.other.Mipper";
    private static final String SECTION_MIPPER = "me.cortex.voxy.common.voxelization.WorldVoxilizedSectionMipper";
    private static final String SECTION = "me.cortex.voxy.common.voxelization.VoxelizedSection";
    private final ZipFile jar;
    private final Method mip;
    private final Method createSection;
    private final Method setPosition;
    private final Method mipSection;
    private final Field sectionData;
    private final Field nonAirCount;

    public VoxyRuntime(Path path) throws IOException, ReflectiveOperationException {
        path = path.toAbsolutePath()
            .normalize();
        String actualHash;
        try {
            actualHash = sha256(path);
        } catch (NoSuchFileException missing) {
            throw new IOException(
                "Missing external Voxy JAR: " + path
                    + ". Put the original Voxy 0.2.16-beta for MC 1.21.11 (Modrinth H3w2nVdU) in mods and re-enter the world.",
                missing);
        } catch (IOException unreadable) {
            throw new IOException(
                "Cannot read external Voxy JAR: " + path + ". " + unreadable.getMessage(),
                unreadable);
        }
        if (!SUPPORTED_SHA256.equals(actualHash)) {
            throw new IOException(
                "Unsupported Voxy JAR: " + path
                    + ". Required: original Voxy 0.2.16-beta for MC 1.21.11 (Modrinth H3w2nVdU). SHA-256 found "
                    + actualHash
                    + "; expected "
                    + SUPPORTED_SHA256
                    + ".");
        }
        jar = new ZipFile(path.toFile());
        try {
            ClassLoader loader = new ClassLoader(VoxyRuntime.class.getClassLoader()) {

                @Override
                protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    synchronized (getClassLoadingLock(name)) {
                        if (!name.startsWith("me.cortex.voxy.")) {
                            return super.loadClass(name, resolve);
                        }
                        if (!name.equals(MIPPER) && !name.equals(SECTION) && !name.equals(SECTION_MIPPER)) {
                            throw new ClassNotFoundException("Unadapted Voxy class: " + name);
                        }
                        Class<?> type = findLoadedClass(name);
                        if (type == null) {
                            try {
                                byte[] data = readClass(name);
                                if (name.equals(MIPPER) || name.equals(SECTION_MIPPER)) {
                                    data = MapperReferencePatch.apply(data);
                                }
                                type = defineClass(name, data, 0, data.length);
                            } catch (IOException error) {
                                throw new ClassNotFoundException(name, error);
                            }
                        }
                        if (resolve) {
                            resolveClass(type);
                        }
                        return type;
                    }
                }
            };
            Class<?> mipper = loader.loadClass(MIPPER);
            Class<?> section = loader.loadClass(SECTION);
            mip = mipper.getMethod(
                "mip",
                long.class,
                long.class,
                long.class,
                long.class,
                long.class,
                long.class,
                long.class,
                long.class,
                LegacyMapper.class);
            createSection = section.getMethod("createEmpty");
            setPosition = section.getMethod("setPosition", int.class, int.class, int.class);
            mipSection = loader.loadClass(SECTION_MIPPER)
                .getMethod("mipSection", section, LegacyMapper.class);
            sectionData = section.getField("section");
            nonAirCount = section.getField("lvl0NonAirCount");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            jar.close();
            throw error;
        }
    }

    public long mip(long[] eightVoxels, LegacyMapper mapper) throws ReflectiveOperationException {
        if (eightVoxels.length != 8) {
            throw new IllegalArgumentException("Exactly eight child voxels are required");
        }
        return ((Long) mip.invoke(
            null,
            eightVoxels[0],
            eightVoxels[1],
            eightVoxels[2],
            eightVoxels[3],
            eightVoxels[4],
            eightVoxels[5],
            eightVoxels[6],
            eightVoxels[7],
            mapper)).longValue();
    }

    public Object convert(int sectionX, int sectionY, int sectionZ, long[] voxels, LegacyMapper mapper)
        throws ReflectiveOperationException {
        if (voxels.length != 4096) {
            throw new IllegalArgumentException("Expected a 16 x 16 x 16 section");
        }
        Object result = createSection.invoke(null);
        setPosition.invoke(result, sectionX, sectionY, sectionZ);
        long[] data = (long[]) sectionData.get(result);
        System.arraycopy(voxels, 0, data, 0, voxels.length);
        int nonAir = 0;
        for (long voxel : voxels) {
            if (!LegacyMapper.isAir(voxel)) {
                nonAir++;
            }
        }
        nonAirCount.setInt(result, nonAir);
        mipSection.invoke(null, result, mapper);
        return result;
    }

    public long[] copyData(Object section) throws IllegalAccessException {
        return ((long[]) sectionData.get(section)).clone();
    }

    private byte[] readClass(String name) throws IOException {
        ZipEntry entry = jar.getEntry(name.replace('.', '/') + ".class");
        if (entry == null || entry.getSize() > 1024 * 1024) {
            throw new IOException("Missing or oversized Voxy class " + name);
        }
        try (InputStream input = jar.getInputStream(entry)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (output.size() + read > 1024 * 1024) {
                    throw new IOException("Oversized Voxy class " + name);
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    public static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, count);
                }
            }
            StringBuilder result = new StringBuilder();
            for (byte value : digest.digest()) {
                result.append(String.format("%02x", value & 255));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    @Override
    public void close() throws IOException {
        jar.close();
    }
}

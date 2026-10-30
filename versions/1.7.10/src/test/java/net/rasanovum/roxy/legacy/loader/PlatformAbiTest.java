package net.rasanovum.roxy.legacy.loader;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipFile;

import org.junit.Assume;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** Checks the external binary's actual call sites, including class-versus-interface linkage. */
public class PlatformAbiTest {

    @Test
    public void rendererPlatformCallsMatchAdapters() throws Exception {
        String jar = System.getProperty("roxy.voxyJar");
        Assume.assumeNotNull(jar);
        Set<String> failures = new TreeSet<>();
        try (ExternalVoxyLoader loader = new ExternalVoxyLoader(Paths.get(jar), Files.createTempDirectory("roxy-abi-"));
            ZipFile zip = new ZipFile(jar)) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                String path = entry.getName();
                if (!path.startsWith("me/cortex/voxy/client/core/") || !path.endsWith(".class")) continue;
                String name = path.substring(0, path.length() - 6)
                    .replace('/', '.');
                if (!VoxyPlatformPatch.mappedClass(name)
                    .equals(name)) continue;
                byte[] bytes;
                try (java.io.InputStream in = zip.getInputStream(entry)) {
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int size;
                    while ((size = in.read(buffer)) != -1) out.write(buffer, 0, size);
                    bytes = ModelInputPatch.apply(name, VoxyPlatformPatch.apply(out.toByteArray()));
                }
                new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {

                    @Override
                    public MethodVisitor visitMethod(int access, String method, String descriptor, String signature,
                        String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {

                            @Override
                            public void visitMethodInsn(int opcode, String owner, String called, String desc,
                                boolean itf) {
                                if (!owner.startsWith(VoxyPlatformPatch.BRIDGE)) return;
                                try {
                                    Class<?> type = Class.forName(owner.replace('/', '.'), false, loader);
                                    if (type.isInterface() != itf)
                                        throw new IllegalStateException("interface flag " + itf);
                                    Type[] args = Type.getArgumentTypes(desc);
                                    Class<?>[] parameters = new Class<?>[args.length];
                                    for (int i = 0; i < args.length; i++) parameters[i] = resolve(args[i], loader);
                                    if (called.equals("<init>")) type.getConstructor(parameters);
                                    else {
                                        java.lang.reflect.Method target = type.getMethod(called, parameters);
                                        if (!Type.getMethodDescriptor(target)
                                            .equals(desc)) throw new NoSuchMethodException("return descriptor");
                                    }
                                } catch (Exception | LinkageError error) {
                                    failures.add(owner + "." + called + desc + " : " + error + " (from " + name + ")");
                                }
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        assertTrue(String.join("\n", failures), failures.isEmpty());
    }

    private static Class<?> resolve(Type type, ClassLoader loader) throws ClassNotFoundException {
        switch (type.getSort()) {
            case Type.BOOLEAN:
                return boolean.class;
            case Type.BYTE:
                return byte.class;
            case Type.CHAR:
                return char.class;
            case Type.SHORT:
                return short.class;
            case Type.INT:
                return int.class;
            case Type.FLOAT:
                return float.class;
            case Type.LONG:
                return long.class;
            case Type.DOUBLE:
                return double.class;
            case Type.VOID:
                return void.class;
            default:
                return Class.forName(
                    type.getSort() == Type.ARRAY ? type.getDescriptor()
                        .replace('/', '.') : type.getClassName(),
                    false,
                    loader);
        }
    }
}

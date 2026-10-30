package net.rasanovum.roxy.legacy.loader;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipFile;

import org.junit.Assume;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.BasicVerifier;

public class ShaderPhaseTransformerTest {

    @Test
    public void actualAngelicaLifecycleHasAllHooksAndValidStackFrames() throws Exception {
        String jar = System.getProperty("roxy.angelicaJar");
        Assume.assumeNotNull(jar);
        Set<String> hooks = new HashSet<String>();
        try (ZipFile zip = new ZipFile(jar)) {
            assertNotNull(zip.getEntry("net/coderbot/iris/shaderpack/StringPair.class"));
            for (String name : new String[] { "shaderpack.include.ShaderPackSourceNames", "gl.shader.StandardMacros",
                "shaderpack.ProgramSet", "pipeline.DeferredWorldRenderingPipeline", "uniforms.CommonUniforms",
                "samplers.IrisSamplers" }) {
                String typeName = "net.coderbot.iris." + name;
                byte[] original;
                try (InputStream stream = zip.getInputStream(zip.getEntry(typeName.replace('.', '/') + ".class"))) {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = stream.read(buffer)) >= 0) bytes.write(buffer, 0, count);
                    original = bytes.toByteArray();
                }
                byte[] result = new ShaderPhaseTransformer().transform(typeName, typeName, original);
                ClassNode type = new ClassNode(Opcodes.ASM9);
                new ClassReader(result).accept(type, 0);
                for (MethodNode method : type.methods) {
                    boolean changed = false;
                    for (AbstractInsnNode instruction : method.instructions.toArray()) {
                        if (!(instruction instanceof MethodInsnNode)) continue;
                        MethodInsnNode call = (MethodInsnNode) instruction;
                        if (!call.owner.startsWith("net/rasanovum/roxy/")) continue;
                        hooks.add(call.name);
                        changed = true;
                    }
                    if (changed) new Analyzer<BasicValue>(new BasicVerifier()).analyze(type.name, method);
                }
            }
        }
        for (String hook : new String[] { "sources", "defines", "programSet", "pipeline", "uniforms", "samplers",
            "prepareShaderFrame", "beforeShaderDeferred" })
            assertTrue("Missing hook in actual Angelica binary: " + hook, hooks.contains(hook));
    }
}

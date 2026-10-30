package net.rasanovum.roxy.legacy.loader;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

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

public class RenderPhaseTransformerTest {

    @Test
    public void realLegacyRendererKeepsTerrainCallsAndValidStackFrames() throws Exception {
        byte[] original;
        try (InputStream stream = getClass()
            .getResourceAsStream("/net/minecraft/client/renderer/EntityRenderer.class")) {
            assertNotNull("The actual legacy renderer must be available to validate the hook", stream);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) >= 0) bytes.write(buffer, 0, count);
            original = bytes.toByteArray();
        }
        byte[] transformed = new RenderPhaseTransformer()
            .transform("ignored", "net.minecraft.client.renderer.EntityRenderer", original);
        ClassNode type = new ClassNode(Opcodes.ASM9);
        new ClassReader(transformed).accept(type, 0);
        int terrainCalls = 0, handoffs = 0;
        for (MethodNode method : type.methods) {
            if (!method.desc.equals("(FJ)V")
                || !(method.name.equals("renderWorld") || method.name.equals("func_78471_a"))) continue;
            new Analyzer<BasicValue>(new BasicVerifier()).analyze(type.name, method);
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.owner.equals("net/minecraft/client/renderer/RenderGlobal")
                    && (call.name.equals("sortAndRender") || call.name.equals("func_72719_a"))) terrainCalls++;
                if (call.name.equals("beforeTerrainPass")) handoffs++;
            }
        }
        assertTrue("All vanilla graphics branches must retain their terrain calls", terrainCalls >= 3);
        assertEquals("Each original call must have a pass-aware handoff", terrainCalls, handoffs);
    }
}

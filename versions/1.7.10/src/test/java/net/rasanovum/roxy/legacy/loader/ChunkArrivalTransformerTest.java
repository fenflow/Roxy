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

public class ChunkArrivalTransformerTest {

    @Test
    public void actualPacketFillCapturesEverySuccessfulReturnWithValidFrames() throws Exception {
        byte[] original;
        try (InputStream stream = getClass().getResourceAsStream("/net/minecraft/world/chunk/Chunk.class")) {
            assertNotNull(stream);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) >= 0) bytes.write(buffer, 0, count);
            original = bytes.toByteArray();
        }
        byte[] result = new ChunkArrivalTransformer().transform("ignored", "net.minecraft.world.chunk.Chunk", original);
        ClassNode type = new ClassNode(Opcodes.ASM9);
        new ClassReader(result).accept(type, 0);
        int returns = 0, hooks = 0;
        for (MethodNode method : type.methods) {
            if (!method.desc.equals("([BIIZ)V")) continue;
            new Analyzer<BasicValue>(new BasicVerifier()).analyze(type.name, method);
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction.getOpcode() == Opcodes.RETURN) {
                    returns++;
                    assertTrue(instruction.getPrevious() instanceof MethodInsnNode);
                    assertEquals("chunkReceived", ((MethodInsnNode) instruction.getPrevious()).name);
                }
                if (instruction instanceof MethodInsnNode
                    && ((MethodInsnNode) instruction).name.equals("chunkReceived")) hooks++;
            }
        }
        assertTrue(returns > 0);
        assertEquals(returns, hooks);
    }
}

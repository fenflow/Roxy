package net.rasanovum.roxy.legacy.loader;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Observe client packet data after it has been installed, rather than an empty ChunkEvent.Load. */
public final class ChunkArrivalTransformer implements IClassTransformer {

    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !transformedName.equals("net.minecraft.world.chunk.Chunk")) return bytes;
        ClassNode type = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(type, 0);
        int count = 0;
        for (MethodNode method : type.methods) {
            if (!(method.name.equals("fillChunk") || method.name.equals("func_76607_a"))
                || !method.desc.equals("([BIIZ)V")) continue;
            for (AbstractInsnNode insn : method.instructions.toArray()) if (insn.getOpcode() == Opcodes.RETURN) {
                InsnList hook = new InsnList();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                hook.add(
                    new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "net/rasanovum/roxy/legacy/terrain/ModernTerrain",
                        "chunkReceived",
                        "(Lnet/minecraft/world/chunk/Chunk;)V",
                        false));
                method.instructions.insertBefore(insn, hook);
                count++;
            }
        }
        if (count == 0) throw new IllegalStateException("Could not hook completed legacy chunk packets");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        type.accept(writer);
        return writer.toByteArray();
    }
}

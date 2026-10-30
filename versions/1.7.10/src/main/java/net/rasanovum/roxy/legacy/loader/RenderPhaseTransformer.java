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

/** Leaves original calls intact for Angelica/Iris mixins, adding a pass-aware callback ahead of them. */
public final class RenderPhaseTransformer implements IClassTransformer {

    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !"net.minecraft.client.renderer.EntityRenderer".equals(transformedName)) return bytes;
        ClassNode type = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(type, 0);
        int hooks = 0;
        for (MethodNode method : type.methods) {
            if (!method.desc.equals("(FJ)V")
                || !(method.name.equals("renderWorld") || method.name.equals("func_78471_a"))) continue;
            int temporary = method.maxLocals;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (!call.owner.equals("net/minecraft/client/renderer/RenderGlobal")
                    || !(call.name.equals("sortAndRender") || call.name.equals("func_72719_a"))
                    || !call.desc.equals("(Lnet/minecraft/entity/EntityLivingBase;ID)I")) continue;
                InsnList before = new InsnList();
                before.add(new VarInsnNode(Opcodes.DSTORE, temporary));
                before.add(new VarInsnNode(Opcodes.ISTORE, temporary + 2));
                before.add(new VarInsnNode(Opcodes.ILOAD, temporary + 2));
                before.add(new VarInsnNode(Opcodes.DLOAD, temporary));
                before.add(
                    new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "net/rasanovum/roxy/legacy/terrain/ModernTerrain",
                        "beforeTerrainPass",
                        "(ID)V",
                        false));
                before.add(new VarInsnNode(Opcodes.ILOAD, temporary + 2));
                before.add(new VarInsnNode(Opcodes.DLOAD, temporary));
                method.instructions.insertBefore(call, before);
                hooks++;
            }
            method.maxLocals = temporary + 3;
        }
        if (hooks == 0) throw new IllegalStateException("Roxy could not locate the legacy terrain render passes");
        ClassWriter output = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        type.accept(output);
        return output.toByteArray();
    }
}

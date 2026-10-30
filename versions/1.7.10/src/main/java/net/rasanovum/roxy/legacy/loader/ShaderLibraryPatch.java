package net.rasanovum.roxy.legacy.loader;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** The legacy Gson version expresses lenient JSON parsing through setLenient(). */
final class ShaderLibraryPatch {

    static byte[] apply(String name, byte[] input) {
        if (!name.equals("me.cortex.voxy.client.iris.IrisShaderPatch")) return input;
        ClassNode type = new ClassNode(Opcodes.ASM9);
        new ClassReader(input).accept(type, 0);
        for (MethodNode method : type.methods) for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode) instruction;
                if (field.owner.equals("com/google/gson/Strictness") && field.name.equals("LENIENT"))
                    method.instructions.remove(field);
            } else if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.owner.equals("com/google/gson/GsonBuilder") && call.name.equals("setStrictness")) {
                    call.name = "setLenient";
                    call.desc = "()Lcom/google/gson/GsonBuilder;";
                }
            }
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        type.accept(writer);
        return writer.toByteArray();
    }
}

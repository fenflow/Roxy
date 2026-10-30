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

/** Registers the public Voxy shader-pack contract with Angelica's Iris lifecycle. */
public final class ShaderPhaseTransformer implements IClassTransformer {

    private static final String HOOK = "net/rasanovum/roxy/legacy/shader/ShaderHooks";

    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !transformedName.startsWith("net.coderbot.iris.")) return bytes;
        ClassNode type = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(type, 0);
        boolean changed = false;
        for (MethodNode method : type.methods) {
            if (transformedName.endsWith(".StandardMacros") && method.name.equals("getVanillaBiomeDefines")) {
                for (AbstractInsnNode insn : method.instructions.toArray())
                    if (insn.getOpcode() == Opcodes.ARETURN) method.instructions.insertBefore(
                        insn,
                        new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "net/rasanovum/roxy/legacy/shader/ShaderBiomeAliases",
                            "extend",
                            "(Ljava/util/Map;)Ljava/util/Map;",
                            false));
                changed = true;
            }
            if (transformedName.endsWith(".DeferredWorldRenderingPipeline") && method.name.equals("beginLevelRendering")
                && method.desc.equals("()V")) {
                method.instructions.insert(
                    new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "net/rasanovum/roxy/legacy/terrain/ModernTerrain",
                        "prepareShaderFrame",
                        "()V",
                        false));
                changed = true;
            }
            String hook = null, descriptor = null;
            int[] arguments = null;
            boolean returnValue = false;
            if (transformedName.endsWith(".ShaderPackSourceNames") && method.name.equals("findPotentialStarts")) {
                hook = "sources";
                descriptor = "(Lcom/google/common/collect/ImmutableList;)Lcom/google/common/collect/ImmutableList;";
                returnValue = true;
            } else if (transformedName.endsWith(".StandardMacros")
                && method.name.equals("createStandardEnvironmentDefines")) {
                    hook = "defines";
                    descriptor = "(Lcom/google/common/collect/ImmutableList;)Lcom/google/common/collect/ImmutableList;";
                    returnValue = true;
                } else if (transformedName.endsWith(".ProgramSet") && method.name.equals("<init>")) {
                    hook = "programSet";
                    descriptor = "(Ljava/lang/Object;Ljava/lang/Object;Ljava/util/function/Function;Ljava/lang/Object;)V";
                    arguments = new int[] { 0, 1, 2, 4 };
                } else
                    if (transformedName.endsWith(".DeferredWorldRenderingPipeline") && method.name.equals("<init>")) {
                        hook = "pipeline";
                        descriptor = "(Ljava/lang/Object;Ljava/lang/Object;)V";
                        arguments = new int[] { 0, 1 };
                    } else if (transformedName.endsWith(".CommonUniforms")
                        && method.name.equals("addNonDynamicUniforms")) {
                            hook = "uniforms";
                            descriptor = "(Ljava/lang/Object;)V";
                            arguments = new int[] { 0 };
                        } else if (transformedName.endsWith(".IrisSamplers")
                            && method.name.equals("addRenderTargetSamplers")) {
                                hook = "samplers";
                                descriptor = "(Ljava/lang/Object;)V";
                                arguments = new int[] { 0 };
                            }
            if (hook != null) for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn.getOpcode() != (returnValue ? Opcodes.ARETURN : Opcodes.RETURN)) continue;
                InsnList code = new InsnList();
                if (arguments != null)
                    for (int argument : arguments) code.add(new VarInsnNode(Opcodes.ALOAD, argument));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, hook, descriptor, false));
                method.instructions.insertBefore(insn, code);
                changed = true;
            }
            if (transformedName.endsWith(".DeferredWorldRenderingPipeline") && method.name.equals("beginHand")
                && method.desc.equals("()V")) {
                method.instructions.insert(
                    new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "net/rasanovum/roxy/legacy/terrain/ModernTerrain",
                        "beforeShaderDeferred",
                        "()V",
                        false));
                changed = true;
            }
        }
        if (!changed) return bytes;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        type.accept(writer);
        return writer.toByteArray();
    }
}

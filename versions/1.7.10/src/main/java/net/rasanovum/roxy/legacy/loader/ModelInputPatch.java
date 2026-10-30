package net.rasanovum.roxy.legacy.loader;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Adapts the modern Minecraft model input and legacy face-lighting convention. */
final class ModelInputPatch implements Opcodes {

    private static final String BAKERY = "me/cortex/voxy/client/core/model/bakery/";
    private static final String BRIDGE = VoxyPlatformPatch.BRIDGE;

    private ModelInputPatch() {}

    static byte[] apply(String name, byte[] input) {
        if (name.equals("me.cortex.voxy.client.core.model.ModelFactory")) return adaptLighting(input);
        if (!name.equals((BAKERY + "SoftwareModelTextureBakery").replace('/', '.'))
            && !name.equals((BAKERY + "ReuseVertexConsumer").replace('/', '.'))) return input;
        ClassReader reader = new ClassReader(input);
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new ClassVisitor(ASM9, writer) {

            @Override
            public MethodVisitor visitMethod(int access, String method, String desc, String signature,
                String[] exceptions) {
                // Modern BakedQuad is replaced by captured GTNH quads; remove the unused input overload.
                if (name.endsWith("ReuseVertexConsumer") && method.equals("quad")) return null;
                if (method.equals("shouldReturnAirForFluid")) return null;
                MethodVisitor mv = super.visitMethod(access, method, desc, signature, exceptions);
                if (method.equals("setupTexture")) {
                    mv.visitCode();
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(
                        GETFIELD,
                        BAKERY + "SoftwareModelTextureBakery",
                        "rasterizer",
                        "L" + BAKERY + "SoftwareRasterizer;");
                    mv.visitMethodInsn(
                        INVOKESTATIC,
                        BRIDGE + "GeometryHooks",
                        "setupTexture",
                        "(L" + BAKERY + "SoftwareRasterizer;)V",
                        false);
                    mv.visitInsn(RETURN);
                    mv.visitMaxs(1, 1);
                    mv.visitEnd();
                    return null;
                }
                if (method.equals("bakeBlockModel") || method.equals("bakeFluidState")) {
                    mv.visitCode();
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(
                        GETFIELD,
                        BAKERY + "SoftwareModelTextureBakery",
                        "vc",
                        "L" + BAKERY + "ReuseVertexConsumer;");
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitVarInsn(ALOAD, 2);
                    if (method.equals("bakeFluidState")) mv.visitVarInsn(ILOAD, 3);
                    else mv.visitInsn(ICONST_M1);
                    mv.visitMethodInsn(
                        INVOKESTATIC,
                        BRIDGE + "GeometryHooks",
                        "append",
                        "(L" + BAKERY
                            + "ReuseVertexConsumer;L"
                            + BRIDGE
                            + "BlockState;L"
                            + BRIDGE
                            + "PlatformTypes$Layer;I)V",
                        false);
                    mv.visitInsn(RETURN);
                    mv.visitMaxs(4, method.equals("bakeFluidState") ? 4 : 3);
                    mv.visitEnd();
                    return null;
                }
                return mv;
            }
        }, 0);
        return writer.toByteArray();
    }

    private static byte[] adaptLighting(byte[] input) {
        ClassWriter output = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        final int[] hooks = { 0 };
        new ClassReader(input).accept(new ClassVisitor(ASM9, output) {

            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                String[] exceptions) {
                MethodVisitor visitor = super.visitMethod(access, name, desc, signature, exceptions);
                if (!name.equals("processTextureBakeResult")) return visitor;
                return new MethodVisitor(ASM9, visitor) {

                    private boolean modelMetadata;

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String field, String descriptor) {
                        super.visitFieldInsn(opcode, owner, field, descriptor);
                        if (opcode == GETFIELD && field.equals("metadataCache") && descriptor.equals("[J"))
                            modelMetadata = true;
                    }

                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == LASTORE && modelMetadata) {
                            super.visitVarInsn(ALOAD, 2);
                            super.visitMethodInsn(
                                INVOKESTATIC,
                                BRIDGE + "GeometryHooks",
                                "adaptLighting",
                                "(JL" + BRIDGE + "BlockState;)J",
                                false);
                            modelMetadata = false;
                            hooks[0]++;
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        }, 0);
        if (hooks[0] != 1) throw new IllegalStateException("Unsupported Voxy model lighting boundary");
        return output.toByteArray();
    }
}

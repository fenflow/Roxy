package net.rasanovum.roxy.legacy.modern;

import static org.lwjgl.opengl.GL45C.*;

/** Restores core-profile state after external-engine operations; no deprecated attribute-stack calls. */
public final class GlStateScope implements AutoCloseable {

    private final int program = glGetInteger(GL_CURRENT_PROGRAM);
    private final int active = glGetInteger(GL_ACTIVE_TEXTURE);
    private final int vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
    private final int draw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
    private final int read = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
    private final int[] textureTargets = { GL_TEXTURE_1D, GL_TEXTURE_2D, GL_TEXTURE_3D, GL_TEXTURE_1D_ARRAY,
        GL_TEXTURE_2D_ARRAY, GL_TEXTURE_CUBE_MAP, GL_TEXTURE_CUBE_MAP_ARRAY, GL_TEXTURE_RECTANGLE, GL_TEXTURE_BUFFER,
        GL_TEXTURE_2D_MULTISAMPLE, GL_TEXTURE_2D_MULTISAMPLE_ARRAY };
    private final int[] textureBindings = { GL_TEXTURE_BINDING_1D, GL_TEXTURE_BINDING_2D, GL_TEXTURE_BINDING_3D,
        GL_TEXTURE_BINDING_1D_ARRAY, GL_TEXTURE_BINDING_2D_ARRAY, GL_TEXTURE_BINDING_CUBE_MAP,
        GL_TEXTURE_BINDING_CUBE_MAP_ARRAY, GL_TEXTURE_BINDING_RECTANGLE, GL_TEXTURE_BINDING_BUFFER,
        GL_TEXTURE_BINDING_2D_MULTISAMPLE, GL_TEXTURE_BINDING_2D_MULTISAMPLE_ARRAY };
    private final int[][] textures = new int[12][textureTargets.length];
    private final int[] imageKeys = { GL_IMAGE_BINDING_NAME, GL_IMAGE_BINDING_LEVEL, GL_IMAGE_BINDING_LAYERED,
        GL_IMAGE_BINDING_LAYER, GL_IMAGE_BINDING_ACCESS, GL_IMAGE_BINDING_FORMAT };
    private final int[][] images = new int[7][imageKeys.length];
    private final int[] samplers = new int[12];
    private final int[] ssbo = new int[10];
    private final int[] ubo = new int[10];
    private final long[] ssboStart = new long[10], ssboSize = new long[10];
    private final long[] uboStart = new long[10], uboSize = new long[10];
    private final int[] targets = { GL_ARRAY_BUFFER, GL_ELEMENT_ARRAY_BUFFER, GL_PIXEL_PACK_BUFFER,
        GL_PIXEL_UNPACK_BUFFER, GL_UNIFORM_BUFFER, GL_SHADER_STORAGE_BUFFER, GL_DRAW_INDIRECT_BUFFER,
        GL_DISPATCH_INDIRECT_BUFFER, 0x80ee };
    private final int[] bindings = { GL_ARRAY_BUFFER_BINDING, GL_ELEMENT_ARRAY_BUFFER_BINDING,
        GL_PIXEL_PACK_BUFFER_BINDING, GL_PIXEL_UNPACK_BUFFER_BINDING, GL_UNIFORM_BUFFER_BINDING,
        GL_SHADER_STORAGE_BUFFER_BINDING, GL_DRAW_INDIRECT_BUFFER_BINDING, GL_DISPATCH_INDIRECT_BUFFER_BINDING,
        0x80ef };
    private final int[] buffers = new int[targets.length];
    private final int[] enableKeys = { GL_BLEND, GL_CULL_FACE, GL_DEPTH_TEST, GL_STENCIL_TEST, GL_SCISSOR_TEST,
        GL_RASTERIZER_DISCARD, GL_FRAMEBUFFER_SRGB, GL_MULTISAMPLE };
    private final boolean[] enabled = new boolean[enableKeys.length];
    private final int[] viewport = new int[4];
    private final int[] scissor = new int[4];
    private final int depthFunction = glGetInteger(GL_DEPTH_FUNC);
    private final boolean depthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
    private final int frontFace = glGetInteger(GL_FRONT_FACE);
    private final int cullFace = glGetInteger(GL_CULL_FACE_MODE);
    private final int provoking = glGetInteger(GL_PROVOKING_VERTEX);
    private final int blendSrcRgb = glGetInteger(GL_BLEND_SRC_RGB);
    private final int blendDstRgb = glGetInteger(GL_BLEND_DST_RGB);
    private final int blendSrcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA);
    private final int blendDstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
    private final int blendEquationRgb = glGetInteger(GL_BLEND_EQUATION_RGB);
    private final int blendEquationAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA);
    private final int[] colourMask = new int[4];
    private final double[] depthRange = new double[2];
    private final int[] pixelKeys = { GL_PACK_ALIGNMENT, GL_PACK_ROW_LENGTH, GL_PACK_IMAGE_HEIGHT, GL_PACK_SKIP_PIXELS,
        GL_PACK_SKIP_ROWS, GL_PACK_SKIP_IMAGES, GL_UNPACK_ALIGNMENT, GL_UNPACK_ROW_LENGTH, GL_UNPACK_IMAGE_HEIGHT,
        GL_UNPACK_SKIP_PIXELS, GL_UNPACK_SKIP_ROWS, GL_UNPACK_SKIP_IMAGES };
    private final int[] pixelValues = new int[pixelKeys.length];
    private final int[] stencilKeys = { GL_STENCIL_FUNC, GL_STENCIL_REF, GL_STENCIL_VALUE_MASK, GL_STENCIL_WRITEMASK,
        GL_STENCIL_FAIL, GL_STENCIL_PASS_DEPTH_FAIL, GL_STENCIL_PASS_DEPTH_PASS, GL_STENCIL_BACK_FUNC,
        GL_STENCIL_BACK_REF, GL_STENCIL_BACK_VALUE_MASK, GL_STENCIL_BACK_WRITEMASK, GL_STENCIL_BACK_FAIL,
        GL_STENCIL_BACK_PASS_DEPTH_FAIL, GL_STENCIL_BACK_PASS_DEPTH_PASS };
    private final int[] stencil = new int[stencilKeys.length];

    public GlStateScope() {
        for (int i = 0; i < enableKeys.length; i++) enabled[i] = glIsEnabled(enableKeys[i]);
        glGetIntegerv(GL_VIEWPORT, viewport);
        glGetIntegerv(GL_SCISSOR_BOX, scissor);
        glGetIntegerv(GL_COLOR_WRITEMASK, colourMask);
        glGetDoublev(GL_DEPTH_RANGE, depthRange);
        for (int i = 0; i < pixelKeys.length; i++) pixelValues[i] = glGetInteger(pixelKeys[i]);
        for (int i = 0; i < stencilKeys.length; i++) stencil[i] = glGetInteger(stencilKeys[i]);
        for (int i = 0; i < 12; i++) {
            glActiveTexture(GL_TEXTURE0 + i);
            for (int t = 0; t < textureTargets.length; t++) textures[i][t] = glGetInteger(textureBindings[t]);
            samplers[i] = glGetIntegeri(GL_SAMPLER_BINDING, i);
        }
        for (int i = 0; i < images.length; i++)
            for (int k = 0; k < imageKeys.length; k++) images[i][k] = glGetIntegeri(imageKeys[k], i);
        for (int i = 0; i < 10; i++) {
            ssbo[i] = glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING, i);
            ubo[i] = glGetIntegeri(GL_UNIFORM_BUFFER_BINDING, i);
            ssboStart[i] = glGetInteger64i(GL_SHADER_STORAGE_BUFFER_START, i);
            ssboSize[i] = glGetInteger64i(GL_SHADER_STORAGE_BUFFER_SIZE, i);
            uboStart[i] = glGetInteger64i(GL_UNIFORM_BUFFER_START, i);
            uboSize[i] = glGetInteger64i(GL_UNIFORM_BUFFER_SIZE, i);
        }
        for (int i = 0; i < targets.length; i++) buffers[i] = glGetInteger(bindings[i]);
        glActiveTexture(active);
    }

    public void close() {
        glUseProgram(program);
        glBindVertexArray(vao);
        for (int i = 0; i < 10; i++) {
            restoreRange(GL_SHADER_STORAGE_BUFFER, i, ssbo[i], ssboStart[i], ssboSize[i]);
            restoreRange(GL_UNIFORM_BUFFER, i, ubo[i], uboStart[i], uboSize[i]);
        }
        for (int i = 0; i < targets.length; i++) {
            if (targets[i] == GL_ELEMENT_ARRAY_BUFFER && vao == 0) continue;
            glBindBuffer(targets[i], buffers[i]);
        }
        for (int i = 0; i < 12; i++) {
            glActiveTexture(GL_TEXTURE0 + i);
            for (int t = 0; t < textureTargets.length; t++) glBindTexture(textureTargets[t], textures[i][t]);
            glBindSampler(i, samplers[i]);
        }
        for (int i = 0; i < images.length; i++) {
            int[] state = images[i];
            glBindImageTexture(i, state[0], state[1], state[2] != 0, state[3], state[4], state[5]);
        }
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, read);
        for (int i = 0; i < enableKeys.length; i++) {
            if (enabled[i]) glEnable(enableKeys[i]);
            else glDisable(enableKeys[i]);
        }
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        glScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
        glDepthFunc(depthFunction);
        glDepthMask(depthMask);
        glDepthRange(depthRange[0], depthRange[1]);
        glColorMask(colourMask[0] != 0, colourMask[1] != 0, colourMask[2] != 0, colourMask[3] != 0);
        glFrontFace(frontFace);
        glCullFace(cullFace);
        glProvokingVertex(provoking);
        glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
        glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
        for (int i = 0; i < pixelKeys.length; i++) glPixelStorei(pixelKeys[i], pixelValues[i]);
        for (int i = 0; i < 2; i++) {
            int face = i == 0 ? GL_FRONT : GL_BACK;
            int offset = i * 7;
            glStencilFuncSeparate(face, stencil[offset], stencil[offset + 1], stencil[offset + 2]);
            glStencilMaskSeparate(face, stencil[offset + 3]);
            glStencilOpSeparate(face, stencil[offset + 4], stencil[offset + 5], stencil[offset + 6]);
        }
        glActiveTexture(active);
    }

    private static void restoreRange(int target, int index, int buffer, long start, long size) {
        if (buffer != 0 && size > 0) glBindBufferRange(target, index, buffer, start, size);
        else glBindBufferBase(target, index, buffer);
    }
}

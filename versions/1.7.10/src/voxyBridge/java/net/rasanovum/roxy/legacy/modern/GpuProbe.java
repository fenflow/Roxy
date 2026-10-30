package net.rasanovum.roxy.legacy.modern;

import static org.lwjgl.opengl.GL45C.*;

import me.cortex.voxy.client.core.gl.Capabilities;
import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;

/** Executes and checks an original external Voxy compute shader in the game's GL context. */
public final class GpuProbe {

    private GpuProbe() {}

    public static String run() {
        int program = glGetInteger(GL_CURRENT_PROGRAM);
        int binding0 = glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING, 0);
        int binding1 = glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING, 1);
        int genericBinding = glGetInteger(GL_SHADER_STORAGE_BUFFER_BINDING);
        GlBuffer output = null;
        GlBuffer input = null;
        Shader shader = null;
        try {
            Capabilities.init();
            if (!Capabilities.INSTANCE.compute) throw new IllegalStateException("Voxy compute shaders unavailable");
            output = new GlBuffer(8 * 4);
            input = new GlBuffer(8 * 8, GL_DYNAMIC_STORAGE_BIT);
            int[] entries = new int[16];
            for (int i = 0; i < 8; i++) {
                entries[2 * i] = 0x9000 + i;
                entries[2 * i + 1] = 7 - i;
            }
            glNamedBufferSubData(input.id, 0, entries);
            shader = Shader.make()
                .define("TYPE", "uint")
                .define("SETTING_BUFFER_BINDING", "0")
                .define("ENTRY_BUFFER_BINDING", "1")
                .add(ShaderType.COMPUTE, "voxy:util/set.comp")
                .compile();
            shader.bind();
            glUniform1ui(0, 8);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, output.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, input.id);
            glDispatchCompute(1, 1, 1);
            glMemoryBarrier(GL_BUFFER_UPDATE_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT);
            int[] result = new int[8];
            glGetNamedBufferSubData(output.id, 0, result);
            for (int i = 0; i < 8; i++) if (result[i] != 0x9000 + 7 - i)
                throw new IllegalStateException("External compute readback mismatch at " + i);
            return "original Voxy shader executed; 8/8 GPU results verified; OpenGL " + glGetString(GL_VERSION);
        } finally {
            glUseProgram(program);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, binding0);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, binding1);
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, genericBinding);
            if (shader != null) shader.free();
            if (input != null) input.free();
            if (output != null) output.free();
        }
    }
}

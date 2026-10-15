package io.github.roman16.wayfarer.client;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL32;
import org.lwjgl.system.MemoryUtil;

public final class LuminanceReadback {
    private static final int FRAMES_IN_FLIGHT = 3;
    private static final int PIXEL_BYTES = 4;
    private static final int[] buffers = new int[FRAMES_IN_FLIGHT];
    private static final long[] fences = new long[FRAMES_IN_FLIGHT];
    private static final ByteBuffer read = MemoryUtil.memAlloc(PIXEL_BYTES).order(ByteOrder.LITTLE_ENDIAN);
    private static int next;
    private static int pixel;

    private LuminanceReadback() {}

    public static void request() {
        if (buffers[0] == 0) {
            for (int slot = 0; slot < FRAMES_IN_FLIGHT; slot++) {
                buffers[slot] = GL15.glGenBuffers();
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, buffers[slot]);
                GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, PIXEL_BYTES, GL15.GL_STREAM_READ);
            }
        }
        collect();
        if (fences[next] == 0) {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, buffers[next]);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
            fences[next] = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            next = (next + 1) % FRAMES_IN_FLIGHT;
        }
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
    }

    public static int pixel() {
        return pixel;
    }

    private static void collect() {
        for (int step = 0; step < FRAMES_IN_FLIGHT; step++) {
            int slot = (next + step) % FRAMES_IN_FLIGHT;
            if (fences[slot] == 0) {
                continue;
            }
            int status = GL32.glClientWaitSync(fences[slot], 0, 0);
            if (status != GL32.GL_ALREADY_SIGNALED && status != GL32.GL_CONDITION_SATISFIED) {
                return;
            }
            GL32.glDeleteSync(fences[slot]);
            fences[slot] = 0;
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, buffers[slot]);
            GL15.glGetBufferSubData(GL21.GL_PIXEL_PACK_BUFFER, 0, read);
            pixel = read.getInt(0);
        }
    }
}

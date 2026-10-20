package io.github.roman16.wayfarer.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.roman16.wayfarer.HudState;
import io.github.roman16.wayfarer.TentPitch;
import io.github.roman16.wayfarer.client.stamp.Stamp;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

final class TentOutlines {
    private static final double CLEARANCE = 0.002;
    private static final float CHANNEL = 255f;

    private TentOutlines() {}

    static void render(RenderLevelStageEvent event) {
        TentPitch.Outlines outlines = HudState.tentOutlines;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || System.currentTimeMillis() - HudState.tentOutlinedAt > Stamp.LINGER_MILLIS
                || outlines.blocked().isEmpty() && outlines.gaps().isEmpty()) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        draw(pose, lines, outlines.blocked(), Stamp.BONE);
        draw(pose, lines, outlines.gaps(), Stamp.DANGER);
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static void draw(PoseStack pose, VertexConsumer lines, List<BlockPos> at, int color) {
        float red = (color >> 16 & 0xFF) / CHANNEL;
        float green = (color >> 8 & 0xFF) / CHANNEL;
        float blue = (color & 0xFF) / CHANNEL;
        for (BlockPos pos : at) {
            LevelRenderer.renderLineBox(pose, lines, new AABB(pos).inflate(CLEARANCE), red, green, blue, 1);
        }
    }
}

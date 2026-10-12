package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.CallingStoneBlockEntity;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class StoneAim {
    private static final double TOP = 0.875;

    private StoneAim() {}

    public static void aim(Minecraft minecraft, float partialTick) {
        Entity camera = minecraft.getCameraEntity();
        if (camera == null || minecraft.level == null || minecraft.player == null) {
            return;
        }
        double reach = minecraft.player.blockInteractionRange();
        Vec3 from = camera.getEyePosition(partialTick);
        Vec3 to = from.add(camera.getViewVector(partialTick).scale(reach));
        HitResult current = minecraft.hitResult;
        double nearest = current == null || current.getType() == HitResult.Type.MISS ? reach : current.getLocation().distanceTo(from);
        BlockPos target = null;
        int radius = (int) Math.ceil(reach / 16) + 1;
        ChunkPos centre = new ChunkPos(BlockPos.containing(from));
        for (int x = centre.x - radius; x <= centre.x + radius; x++) {
            for (int z = centre.z - radius; z <= centre.z + radius; z++) {
                LevelChunk chunk = minecraft.level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (!(entity instanceof CallingStoneBlockEntity stone)) {
                        continue;
                    }
                    Optional<Vec3> hit = CallingStoneRenderer.eye(stone).flatMap(eye -> eye.clip(from, to));
                    if (hit.isPresent() && hit.get().distanceTo(from) < nearest) {
                        nearest = hit.get().distanceTo(from);
                        target = stone.getBlockPos();
                    }
                }
            }
        }
        if (target != null) {
            minecraft.hitResult = new BlockHitResult(Vec3.atBottomCenterOf(target).add(0, TOP, 0), Direction.UP, target, false);
            minecraft.crosshairPickEntity = null;
        }
    }
}

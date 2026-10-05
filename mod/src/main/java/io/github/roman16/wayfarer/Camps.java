package io.github.roman16.wayfarer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public final class Camps {
    private static final int CLEARANCE = 10;

    private Camps() {}

    public static BlockPos place(ServerLevel level, ResourceLocation template, BlockPos centre) {
        StructureTemplate camp = level.getStructureManager().getOrCreate(template);
        Vec3i size = camp.getSize();
        int x = centre.getX() - size.getX() / 2;
        int z = centre.getZ() - size.getZ() / 2;
        level.getChunk(centre);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre.getX(), centre.getZ());
        BlockPos origin = new BlockPos(x, y - 1, z);
        for (int dx = 0; dx < size.getX(); dx++) {
            for (int dz = 0; dz < size.getZ(); dz++) {
                int column = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz);
                for (int fill = column; fill < origin.getY(); fill++) {
                    level.setBlock(new BlockPos(x + dx, fill, z + dz), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                for (int clear = origin.getY() + 1; clear <= origin.getY() + CLEARANCE; clear++) {
                    level.setBlock(new BlockPos(x + dx, clear, z + dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        camp.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.random, Block.UPDATE_CLIENTS);
        return origin;
    }
}

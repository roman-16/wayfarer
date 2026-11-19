package io.github.roman16.wayfarer;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

public final class HallRoom extends StructurePiece {
    public HallRoom(BoundingBox room) {
        super(Wayfarer.HALL_ROOM.get(), 0, room);
    }

    public HallRoom(StructurePieceSerializationContext context, CompoundTag tag) {
        super(Wayfarer.HALL_ROOM.get(), tag);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {}

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
            BoundingBox chunk, ChunkPos chunkPos, BlockPos pivot) {}
}

package io.github.roman16.wayfarer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class EntrancePiece extends TemplateStructurePiece {
    private static final int FOOTING = 24;
    private static final String INSET = "inset";
    private static final String ROTATION = "Rotation";

    private final int inset;

    public EntrancePiece(StructureTemplateManager templates, ResourceLocation template, BlockPos position, Rotation rotation, int inset) {
        super(Wayfarer.ENTRANCE_PIECE.get(), 0, templates, template, template.toString(), settings(rotation), position);
        this.inset = inset;
    }

    public EntrancePiece(StructureTemplateManager templates, CompoundTag tag) {
        super(Wayfarer.ENTRANCE_PIECE.get(), tag, templates, template -> settings(Rotation.valueOf(tag.getString(ROTATION))));
        this.inset = tag.getInt(INSET);
    }

    private static StructurePlaceSettings settings(Rotation rotation) {
        return Underground.settings(rotation);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString(ROTATION, placeSettings.getRotation().name());
        tag.putInt(INSET, inset);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
            BoundingBox chunk, ChunkPos chunkPos, BlockPos pivot) {
        super.postProcess(level, structures, generator, random, chunk, chunkPos, pivot);
        for (int x = Math.max(boundingBox.minX(), chunk.minX()); x <= Math.min(boundingBox.maxX(), chunk.maxX()); x++) {
            for (int z = Math.max(boundingBox.minZ(), chunk.minZ()); z <= Math.min(boundingBox.maxZ(), chunk.maxZ()); z++) {
                if (!overShaft(x, z)) {
                    ground(level, new BlockPos.MutableBlockPos(x, boundingBox.minY(), z));
                }
            }
        }
    }

    private boolean overShaft(int x, int z) {
        return x >= boundingBox.minX() + inset && x <= boundingBox.maxX() - inset
                && z >= boundingBox.minZ() + inset && z <= boundingBox.maxZ() - inset;
    }

    private static void ground(WorldGenLevel level, BlockPos.MutableBlockPos pos) {
        BlockState surface = level.getBlockState(pos);
        if (!surface.isCollisionShapeFullBlock(level, pos)) {
            return;
        }
        BlockState footing = surface.is(BlockTags.DIRT) ? Blocks.DIRT.defaultBlockState() : surface;
        for (int depth = 0; depth < FOOTING; depth++) {
            pos.move(Direction.DOWN);
            BlockState below = level.getBlockState(pos);
            if (!below.canBeReplaced() && below.getFluidState().isEmpty()) {
                return;
            }
            level.setBlock(pos, footing, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {}
}

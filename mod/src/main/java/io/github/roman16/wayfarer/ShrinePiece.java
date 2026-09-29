package io.github.roman16.wayfarer;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class ShrinePiece extends TemplateStructurePiece {
    private static final String ROTATION = "Rotation";

    public ShrinePiece(StructureTemplateManager templates, ResourceLocation template, BlockPos position, Rotation rotation) {
        super(Wayfarer.SHRINE_PIECE.get(), 0, templates, template, template.toString(), settings(rotation), position);
    }

    public ShrinePiece(StructureTemplateManager templates, CompoundTag tag) {
        super(Wayfarer.SHRINE_PIECE.get(), tag, templates, template -> settings(Rotation.valueOf(tag.getString(ROTATION))));
    }

    private static StructurePlaceSettings settings(Rotation rotation) {
        return new StructurePlaceSettings().setRotation(rotation);
    }

    public boolean is(ResourceLocation template) {
        return templateName.equals(template.toString());
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString(ROTATION, placeSettings.getRotation().name());
    }

    @Override
    protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {}
}

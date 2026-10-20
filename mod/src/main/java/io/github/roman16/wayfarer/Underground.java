package io.github.roman16.wayfarer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

public final class Underground {
    private Underground() {}

    public static StructurePlaceSettings settings(Rotation rotation) {
        return settings(rotation, BlockPos.ZERO);
    }

    public static StructurePlaceSettings settings(Rotation rotation, BlockPos pivot) {
        return new StructurePlaceSettings().setRotation(rotation).setRotationPivot(pivot).setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING);
    }
}

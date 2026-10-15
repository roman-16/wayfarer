package io.github.roman16.wayfarer.client;

import com.mojang.logging.LogUtils;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiCustomRenderObjectFactory;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiCustomRenderRegister;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderableBoxGroup;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiWorldProxy;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3d;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBox;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBoxGroupShading;
import io.github.roman16.wayfarer.Balance;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.slf4j.Logger;

final class FarSmoke {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NAME = "wayfarer:caravan_smoke";
    private static final int FULL_LIGHT = 15;
    private static final int GREY_HIGH = 236;
    private static final int GREY_LOW = 200;
    private static final int PUFF_STEP = 4;
    private static final int CLUSTER = 3;
    private static final double LEAN = 0.32;
    private static final double SMALLEST = 2.5;
    private static final double SWAY = 0.35;
    private static final double SIDE = 0.6;
    private static final double BREAKS = 0.75;
    private static IDhApiCustomRenderRegister register;
    private static IDhApiRenderableBoxGroup column;
    private static BlockPos drawn;

    private FarSmoke() {}

    static void show(Optional<BlockPos> fire, boolean far) {
        IDhApiWorldProxy world = DhApi.Delayed.worldProxy;
        IDhApiCustomRenderObjectFactory factory = DhApi.Delayed.customRenderObjectFactory;
        if (world == null || factory == null || !world.worldLoaded()) {
            return;
        }
        IDhApiLevelWrapper level = world.getSinglePlayerLevel();
        IDhApiCustomRenderRegister now = level == null ? null : level.getRenderRegister();
        if (now == null) {
            return;
        }
        if (now != register) {
            register = now;
            column = null;
            drawn = null;
        }
        if (fire.isEmpty()) {
            if (column != null) {
                column.setActive(false);
            }
            return;
        }
        if (!fire.get().equals(drawn)) {
            if (column != null) {
                register.remove(column.getId());
            }
            column = build(factory, fire.get());
            try {
                register.add(column);
            } catch (IllegalArgumentException exception) {
                LOGGER.error("far smoke failed: Distant Horizons refused the column at {}", fire.get(), exception);
                column = null;
                return;
            }
            drawn = fire.get();
        }
        column.setActive(far);
    }

    private static IDhApiRenderableBoxGroup build(IDhApiCustomRenderObjectFactory factory, BlockPos fire) {
        Balance.Smoke smoke = Balance.get().smoke();
        RandomSource random = RandomSource.create(fire.asLong());
        List<DhApiRenderableBox> boxes = new ArrayList<>();
        for (int height = 0; height < smoke.columnBlocks(); height += PUFF_STEP) {
            double rise = (double) height / smoke.columnBlocks();
            if (rise > BREAKS && random.nextBoolean()) {
                continue;
            }
            double size = (SMALLEST + rise * (smoke.widthBlocks() * 2 - SMALLEST)) * (1 + random.triangle(0, SWAY));
            double lean = rise * rise * smoke.columnBlocks() * LEAN;
            double x = lean + random.triangle(0, size * SWAY);
            double z = lean + random.triangle(0, size * SWAY);
            int grey = (int) Math.round(GREY_LOW + rise * (GREY_HIGH - GREY_LOW));
            Color colour = new Color(grey, grey, grey);
            boxes.add(cube(x, 1 + height, z, size, colour));
            for (int beside = 0; beside < CLUSTER; beside++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double small = size * SIDE * (1 + random.triangle(0, SWAY));
                boxes.add(cube(x + Math.cos(angle) * size / 2, 1 + height + random.nextDouble() * size / 2, z + Math.sin(angle) * size / 2, small, colour));
            }
        }
        IDhApiRenderableBoxGroup group = factory.createRelativePositionedGroup(NAME, new DhApiVec3d(fire.getX() + 0.5, fire.getY(), fire.getZ() + 0.5), boxes);
        group.setSkyLight(FULL_LIGHT);
        group.setBlockLight(0);
        group.setSsaoEnabled(false);
        group.setShading(DhApiRenderableBoxGroupShading.getDefaultShaded());
        return group;
    }

    private static DhApiRenderableBox cube(double x, double bottom, double z, double size, Color colour) {
        double half = size / 2;
        return new DhApiRenderableBox(new DhApiVec3d(x - half, bottom, z - half), new DhApiVec3d(x + half, bottom + size, z + half), colour, EDhApiBlockMaterial.AIR);
    }
}

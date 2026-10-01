package io.github.roman16.wayfarer.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.roman16.wayfarer.HudState;
import io.github.roman16.wayfarer.ShrinePedestalBlockEntity;
import io.github.roman16.wayfarer.Shrines;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

public final class ShrinePedestalRenderer implements BlockEntityRenderer<ShrinePedestalBlockEntity> {
    private static final float HEIGHT = 1.4F;
    private static final float BOB = 0.08F;
    private static final float SCALE = 2.0F;
    private static final float GROUND_LIFT = 0.125F;
    private static final float GROUND_HALF = 0.25F;

    public ShrinePedestalRenderer(BlockEntityRendererProvider.Context context) {}

    public static Optional<ResourceLocation> floating(ShrinePedestalBlockEntity pedestal) {
        return pedestal.shrine().filter(shrine -> !HudState.compass.claimed().contains(shrine));
    }

    public static Optional<AABB> eye(ShrinePedestalBlockEntity pedestal) {
        double centre = HEIGHT + GROUND_LIFT * SCALE;
        double half = GROUND_HALF * SCALE;
        return floating(pedestal).map(shrine -> new AABB(0.5 - half, centre - half - BOB, 0.5 - half, 0.5 + half, centre + half + BOB, 0.5 + half)
                .move(pedestal.getBlockPos()));
    }

    @Override
    public void render(ShrinePedestalBlockEntity pedestal, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        floating(pedestal).ifPresent(shrine -> {
            float time = pedestal.getLevel() == null ? 0 : pedestal.getLevel().getGameTime() + partialTick;
            pose.pushPose();
            pose.translate(0.5, HEIGHT + Mth.sin(time / 20) * BOB, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(180 - Minecraft.getInstance().gameRenderer.getMainCamera().getYRot()));
            pose.scale(SCALE, SCALE, SCALE);
            Minecraft.getInstance().getItemRenderer().renderStatic(new ItemStack(Shrines.eye(shrine)), ItemDisplayContext.GROUND,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, buffers, pedestal.getLevel(), 0);
            pose.popPose();
        });
    }
}

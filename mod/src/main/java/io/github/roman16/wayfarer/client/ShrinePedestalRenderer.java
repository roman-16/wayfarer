package io.github.roman16.wayfarer.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.roman16.wayfarer.HudState;
import io.github.roman16.wayfarer.ShrinePedestalBlockEntity;
import io.github.roman16.wayfarer.Shrines;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class ShrinePedestalRenderer implements BlockEntityRenderer<ShrinePedestalBlockEntity> {
    private static final float HEIGHT = 1.4F;
    private static final float BOB = 0.08F;
    private static final float SCALE = 2.0F;

    public ShrinePedestalRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(ShrinePedestalBlockEntity pedestal, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pedestal.shrine().filter(shrine -> !HudState.compass.claimed().contains(shrine)).ifPresent(shrine -> {
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

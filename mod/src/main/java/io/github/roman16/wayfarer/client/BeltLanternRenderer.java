package io.github.roman16.wayfarer.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

final class BeltLanternRenderer implements ICurioRenderer {
    private static final float PIXEL = 1 / 16f;
    private static final float SIDE = 3.5f * PIXEL;
    private static final float BELT = 10 * PIXEL;
    private static final float FRONT = -2.5f * PIXEL;
    private static final float SCALE = 0.5f;
    private static final float WALK = 0.6662f;
    private static final float SWAY = 0.35f;

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slot, PoseStack pose, RenderLayerParent<T, M> parent,
            MultiBufferSource buffers, int light, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!(parent.getModel() instanceof HumanoidModel<?> humanoid) || !(stack.getItem() instanceof BlockItem item)) {
            return;
        }
        BlockState lantern = item.getBlock().defaultBlockState();
        if (lantern.hasProperty(LanternBlock.HANGING)) {
            lantern = lantern.setValue(LanternBlock.HANGING, true);
        }
        pose.pushPose();
        humanoid.body.translateAndRotate(pose);
        pose.translate(SIDE, BELT, FRONT);
        pose.mulPose(Axis.XP.rotation(-humanoid.body.xRot + Mth.cos(limbSwing * WALK + Mth.PI) * SWAY * limbSwingAmount));
        pose.mulPose(Axis.ZP.rotationDegrees(180));
        pose.scale(SCALE, SCALE, SCALE);
        pose.translate(-0.5f, -1f, -0.5f);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(lantern, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY,
                RenderType.cutout());
        pose.popPose();
    }
}

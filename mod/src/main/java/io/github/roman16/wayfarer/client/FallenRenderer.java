package io.github.roman16.wayfarer.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.roman16.wayfarer.Wayfarer;
import io.github.roman16.wayfarer.story.Fallen;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;

public final class FallenRenderer extends EntityRenderer<Fallen> {
    public static final ModelLayerLocation GOAT = new ModelLayerLocation(Wayfarer.id("fallen_goat"), "main");
    public static final ModelLayerLocation MULE = new ModelLayerLocation(Wayfarer.id("fallen_mule"), "main");
    private static final float MODEL_HEIGHT = 1.501F;
    private static final float GOAT_BODY_CENTRE = 12.5F / 16;
    private static final float GOAT_BODY_HALF_WIDTH = 5.55F / 16;
    private static final float GOAT_SCALE = 1;
    private static final float HORSE_BODY_CENTRE = 8 / 16F;
    private static final float HORSE_BODY_HALF_WIDTH = 5.05F / 16;
    private static final float MULE_SCALE = 0.92F;
    private static final float QUARTER_TURN = 90;
    private static final float SHADOW = 0.5F;
    private static final float JITTER = 0.25F;
    private static final List<String> HIDDEN_BODY = List.of("left_chest", "right_chest", "saddle");
    private static final List<String> HIDDEN_HEAD = List.of("head_saddle", "left_saddle_line", "left_saddle_mouth", "mouth_saddle_wrap",
            "right_saddle_line", "right_saddle_mouth");
    private static final List<String> HIDDEN_ROOT = List.of("left_front_baby_leg", "left_hind_baby_leg", "right_front_baby_leg",
            "right_hind_baby_leg");

    private final Map<EntityType<?>, Look> looks;

    public FallenRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = SHADOW;
        ModelPart mule = context.bakeLayer(MULE);
        HIDDEN_ROOT.forEach(name -> mule.getChild(name).visible = false);
        HIDDEN_BODY.forEach(name -> mule.getChild("body").getChild(name).visible = false);
        HIDDEN_HEAD.forEach(name -> mule.getChild("head_parts").getChild(name).visible = false);
        looks = Map.of(
                EntityType.GOAT, new Look(context.bakeLayer(GOAT), ResourceLocation.withDefaultNamespace("textures/entity/goat/goat.png"),
                        Wayfarer.id("textures/entity/fallen/goat_eyes.png"), GOAT_SCALE, GOAT_BODY_CENTRE, GOAT_BODY_HALF_WIDTH, FallenRenderer::goat),
                EntityType.MULE, new Look(mule, ResourceLocation.withDefaultNamespace("textures/entity/horse/mule.png"),
                        Wayfarer.id("textures/entity/fallen/mule_eyes.png"), MULE_SCALE, HORSE_BODY_CENTRE, HORSE_BODY_HALF_WIDTH, FallenRenderer::horse));
    }

    @Override
    public void render(Fallen body, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        Look look = looks.get(body.creature());
        if (look == null) {
            return;
        }
        float down = body.leftDown() ? 1 : -1;
        look.pose().pose(look.model(), down, RandomSource.create(body.getUUID().getLeastSignificantBits()));
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180 - body.getYRot()));
        poseStack.translate(0, look.halfWidth() * look.scale(), 0);
        poseStack.mulPose(Axis.ZP.rotationDegrees(down * QUARTER_TURN));
        poseStack.translate(0, -(MODEL_HEIGHT - look.centre()) * look.scale(), 0);
        poseStack.scale(-look.scale(), -look.scale(), look.scale());
        poseStack.translate(0, -MODEL_HEIGHT, 0);
        look.model().render(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(look.texture())), light, OverlayTexture.NO_OVERLAY);
        look.model().render(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(look.eyes())), light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(body, yaw, partialTick, poseStack, buffers, light);
    }

    private static void horse(ModelPart model, float down, RandomSource random) {
        String lower = down > 0 ? "left" : "right";
        String upper = down > 0 ? "right" : "left";
        leg(model.getChild(lower + "_front_leg"), -0.45F, 0, random);
        leg(model.getChild(lower + "_hind_leg"), 0.35F, 0, random);
        leg(model.getChild(upper + "_front_leg"), -0.15F, -down * 0.8F, random);
        leg(model.getChild(upper + "_hind_leg"), 0.6F, -down * 0.8F, random);
        model.getChild(lower + "_front_leg").setPos(down * 4, 14, -10);
        model.getChild(upper + "_front_leg").setPos(-down * 4, 14, -10);
        ModelPart neck = model.getChild("head_parts");
        neck.setPos(0, 4, -12);
        neck.setRotation(1.4F + jitter(random), -down * 0.3F, 0);
        ModelPart head = neck.getChild("head");
        head.getChild("left_ear").setRotation(-2.8F, 0, 0.15F);
        head.getChild("right_ear").setRotation(-2.8F, 0, -0.15F);
        model.getChild("body").getChild("tail").setRotation(1.1F + jitter(random), 0, -down * 0.5F);
    }

    private static void goat(ModelPart model, float down, RandomSource random) {
        String lower = down > 0 ? "left" : "right";
        String upper = down > 0 ? "right" : "left";
        leg(model.getChild(lower + "_front_leg"), -0.5F, 0, random);
        leg(model.getChild(lower + "_hind_leg"), 0.4F, 0, random);
        leg(model.getChild(upper + "_front_leg"), -0.2F, -down * 0.7F, random);
        leg(model.getChild(upper + "_hind_leg"), 0.55F, -down * 0.7F, random);
        model.getChild("head").setRotation(-0.35F + jitter(random), -down * 0.25F, 0);
    }

    private static void leg(ModelPart leg, float swing, float sag, RandomSource random) {
        leg.setRotation(swing + jitter(random), 0, sag + jitter(random));
    }

    private static float jitter(RandomSource random) {
        return (random.nextFloat() - 0.5F) * JITTER;
    }

    @Override
    public ResourceLocation getTextureLocation(Fallen body) {
        Look look = looks.get(body.creature());
        return look == null ? ResourceLocation.withDefaultNamespace("textures/entity/horse/mule.png") : look.texture();
    }

    private interface Pose {
        void pose(ModelPart model, float down, RandomSource random);
    }

    private record Look(ModelPart model, ResourceLocation texture, ResourceLocation eyes, float scale, float centre, float halfWidth, Pose pose) {}
}

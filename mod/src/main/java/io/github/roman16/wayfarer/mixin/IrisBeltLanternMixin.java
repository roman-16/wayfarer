package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.BeltLantern;
import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import net.irisshaders.iris.api.v0.item.IrisItemLightProvider;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.IdMapUniforms$HeldItemSupplier", remap = false)
public abstract class IrisBeltLanternMixin {
    @Shadow
    @Final
    private InteractionHand hand;

    @Shadow
    @Final
    private Object2IntFunction<NamespacedId> itemIdMap;

    @Shadow
    private int intID;

    @Shadow
    private int lightValue;

    @Shadow
    private Vector3f lightColor;

    @Inject(method = "update", at = @At("TAIL"))
    private void wayfarer$beltLanternLightsLikeTheOffHand(CallbackInfo info) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (hand != InteractionHand.OFF_HAND || player == null) {
            return;
        }
        ItemStack lantern = BeltLantern.worn(player);
        if (lantern.isEmpty()) {
            return;
        }
        IrisItemLightProvider light = (IrisItemLightProvider) lantern.getItem();
        int emission = light.getLightEmission(player, lantern);
        if (emission > lightValue) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(lantern.getItem());
            intID = itemIdMap.applyAsInt(new NamespacedId(id.getNamespace(), id.getPath()));
            lightValue = emission;
            lightColor = light.getLightColor(player, lantern);
        }
    }
}

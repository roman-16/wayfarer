package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.story.Marks;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FarSight {
    private static final String GIFT = "far_sight";
    private static final float SIXTEENTHS = 16;

    private FarSight() {}

    public static void mark(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.player.isScoping() || !Gifts.has(GIFT)) {
            return;
        }
        while (minecraft.options.keyAttack.consumeClick()) {
            HitResult hit = minecraft.player.pick(Balance.get().farSight().markBlocks(), 1, false);
            if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
                PacketDistributor.sendToServer(new Marks.Mark(block.getBlockPos()));
            }
        }
    }

    public static void zoom(ViewportEvent.ComputeFov event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.usedConfiguredFov() && minecraft.player != null && minecraft.player.isScoping() && Gifts.has(GIFT)) {
            event.setFOV(event.getFOV() * Balance.get().farSight().zoomSixteenths() / SIXTEENTHS);
        }
    }
}

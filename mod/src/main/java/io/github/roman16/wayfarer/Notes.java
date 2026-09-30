package io.github.roman16.wayfarer;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class Notes {
    private static final TagKey<Block> WAYSTONES = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("waystones", "waystones"));

    private Notes() {}

    public static void trigger(ServerPlayer player, String trigger) {
        Library.INSTANCE.notes(trigger).forEach(quest -> Journal.complete(player, quest));
    }

    public static boolean first(ServerPlayer player, String name) {
        List<String> firsts = player.getData(Wayfarer.FIRSTS);
        if (firsts.contains(name)) {
            return false;
        }
        List<String> seen = new ArrayList<>(firsts);
        seen.add(name);
        player.setData(Wayfarer.FIRSTS, List.copyOf(seen));
        trigger(player, "first:" + name);
        return true;
    }

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getLevel().getBlockState(event.getPos()).is(WAYSTONES)
                && first(player, "waystone")) {
            player.sendSystemMessage(Component.translatable("wayfarer.first.waystone").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }
}

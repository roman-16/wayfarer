package io.github.roman16.wayfarer;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class Journal {
    private static final ResourceLocation BOOK = ResourceLocation.fromNamespaceAndPath("ftbquests", "book");

    private Journal() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getData(Wayfarer.JOURNAL_GIVEN)) {
            return;
        }
        BuiltInRegistries.ITEM.getOptional(BOOK).ifPresent(book -> {
            ItemStack journal = new ItemStack(book);
            if (!player.getInventory().add(journal)) {
                player.drop(journal, false);
            }
        });
        player.setData(Wayfarer.JOURNAL_GIVEN, true);
    }
}

package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Library;
import io.github.roman16.wayfarer.Sounds;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class Pages {
    private static final String JOURNAL_ENTRY = "journal_entry";

    private Pages() {}

    public static void read(MinecraftServer server, String book) {
        if (Progress.of(server).read(book)) {
            Sounds.play(server, JOURNAL_ENTRY);
            Journal.syncAll(server);
        }
    }

    private static void read(MinecraftServer server, ItemStack stack) {
        WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (content != null) {
            Library.INSTANCE.find(content.title().raw(), content.author()).ifPresent(book -> read(server, book));
        }
    }

    @SubscribeEvent
    public static void onReadBook(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            read(player.server, event.getItemStack());
        }
    }

    @SubscribeEvent
    public static void onReadLectern(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level().getBlockEntity(event.getPos()) instanceof LecternBlockEntity lectern) {
            read(player.server, lectern.getBook());
        }
    }
}

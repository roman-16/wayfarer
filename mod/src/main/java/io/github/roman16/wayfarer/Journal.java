package io.github.roman16.wayfarer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class Journal {
    private static final ResourceLocation BOOK = ResourceLocation.fromNamespaceAndPath("ftbquests", "book");
    private static final String BOOK_ID = "wayfarer_book";
    private static final String FTB_QUESTS = "ftbquests";

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
        Notes.trigger(player, "start");
    }

    @SubscribeEvent
    public static void onReadItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            id(event.getItemStack()).ifPresent(book -> read(player, book));
        }
    }

    @SubscribeEvent
    public static void onReadLectern(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level().getBlockEntity(event.getPos()) instanceof LecternBlockEntity lectern)) {
            return;
        }
        Optional<ResourceLocation> shrine = Shrines.inside(player.serverLevel(), event.getPos());
        if (shrine.isEmpty()) {
            id(lectern.getBook()).ifPresent(book -> read(player, book));
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        openShrineBook(player, shrine.get());
    }

    public static void openShrineBook(ServerPlayer player, ResourceLocation shrine) {
        List<ResourceLocation> claimed = Journey.claimed(player);
        int index = claimed.contains(shrine) ? claimed.indexOf(shrine) : claimed.size();
        List<Component> pages = new ArrayList<>();
        String memory = Library.INSTANCE.memory(index);
        Library.INSTANCE.book(memory).ifPresent(book -> book.pages().forEach(page -> pages.add(Component.literal(page))));
        read(player, memory);
        Library.INSTANCE.people(shrine).ifPresent(people -> {
            people.pages().forEach(page -> pages.add(Component.literal(page)));
            read(player, Library.INSTANCE.people().get(shrine));
        });
        Leads.place(player, shrine).ifPresent(pages::add);
        PacketDistributor.sendToPlayer(player, new BookPayload(pages));
    }

    public static void read(ServerPlayer player, String id) {
        Library.INSTANCE.book(id).ifPresent(book -> {
            complete(player, book.quest());
            Notes.trigger(player, "book:" + id);
        });
    }

    public static void complete(ServerPlayer player, String quest) {
        if (!ModList.get().isLoaded(FTB_QUESTS) || quest.isEmpty()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        String command = "ftbquests change_progress " + player.getGameProfile().getName() + " complete " + quest;
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withSuppressedOutput(), command);
    }

    private static Optional<String> id(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(BOOK_ID)) {
            return Optional.empty();
        }
        return Optional.of(data.copyTag().getString(BOOK_ID));
    }
}

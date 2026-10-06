package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.story.Engine;
import io.github.roman16.wayfarer.story.Progress;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import top.theillusivec4.curios.api.CuriosApi;

public final class Ember {
    public static final String SLOT = "ember";
    private static final String NAME = "ember";
    private static final int LIT = 1;
    private static final int SYNC_TICKS = 20;

    private Ember() {}

    public static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> CuriosApi.registerCurio(Wayfarer.EMBER.get(), Shards.LOCKED));
    }

    public static boolean carried(MinecraftServer server) {
        return Journey.awake(server) || Engine.running(server) && Progress.of(server).named(NAME);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % SYNC_TICKS == 0) {
            sync(player);
        }
    }

    public static void sync(ServerPlayer player) {
        ItemStack ember = carried(player.server) ? ember(player.server) : ItemStack.EMPTY;
        CuriosApi.getCuriosInventory(player).flatMap(curios -> curios.getStacksHandler(SLOT)).ifPresent(handler -> {
            var stacks = handler.getStacks();
            if (!ItemStack.isSameItemSameComponents(stacks.getStackInSlot(0), ember)) {
                stacks.setStackInSlot(0, ember);
            }
        });
    }

    private static ItemStack ember(MinecraftServer server) {
        ItemStack ember = new ItemStack(Wayfarer.EMBER.get());
        boolean lit = Journey.awake(server);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.translatable(lit ? "wayfarer.ember.warm" : "wayfarer.ember.cold").withStyle(ChatFormatting.GRAY));
        int shards = Journey.claimed(server).size();
        if (shards > 0) {
            lore.add(Component.translatable("wayfarer.ember.shards", shards).withStyle(ChatFormatting.GOLD));
        }
        if (lit) {
            ember.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(LIT));
        }
        ember.set(DataComponents.LORE, new ItemLore(lore));
        return ember;
    }
}

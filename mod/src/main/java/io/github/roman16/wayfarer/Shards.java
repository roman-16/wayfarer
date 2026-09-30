package io.github.roman16.wayfarer;

import java.util.List;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

public final class Shards {
    public static final String SLOT = "shard";

    private static final String ENDREM = "endrem";
    private static final String EYE_SUFFIX = "_eye";
    private static final String TAKEN = "taken";
    private static final int SYNC_TICKS = 100;
    private static final ICurioItem LOCKED = new ICurioItem() {
        @Override
        public boolean canUnequip(SlotContext slotContext, ItemStack stack) {
            return false;
        }
    };

    private Shards() {}

    public static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> BuiltInRegistries.ITEM.entrySet().stream()
                .filter(entry -> entry.getKey().location().getNamespace().equals(ENDREM)
                        && entry.getKey().location().getPath().endsWith(EYE_SUFFIX))
                .forEach(entry -> CuriosApi.registerCurio(entry.getValue(), LOCKED)));
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
        List<ResourceLocation> claimed = Journey.claimed(player);
        CuriosApi.getCuriosInventory(player).flatMap(curios -> curios.getStacksHandler(SLOT)).ifPresent(handler -> {
            var stacks = handler.getStacks();
            for (int slot = 0; slot < stacks.getSlots(); slot++) {
                ItemStack shard = slot < claimed.size() ? new ItemStack(Shrines.eye(claimed.get(slot))) : ItemStack.EMPTY;
                if (!ItemStack.matches(stacks.getStackInSlot(slot), shard)) {
                    stacks.setStackInSlot(slot, shard);
                }
            }
        });
        for (ResourceLocation shrine : claimed) {
            AdvancementHolder advancement = player.server.getAdvancements().get(Wayfarer.id("shards/" + Shrines.eyeKey(shrine)));
            if (advancement != null && !player.getAdvancements().getOrStartProgress(advancement).isDone()) {
                player.getAdvancements().award(advancement, TAKEN);
            }
        }
    }
}

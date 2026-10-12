package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.story.Story;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
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
    static final ICurioItem LOCKED = new ICurioItem() {
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

    private static ItemStack shard(ResourceLocation hall) {
        ItemStack shard = new ItemStack(Halls.eye(hall));
        List<Component> lore = new ArrayList<>();
        gift(Halls.eyeKey(hall)).ifPresent(gift -> lore.add(Component.translatable("wayfarer.gift." + gift).withStyle(ChatFormatting.GOLD)));
        shard.set(DataComponents.LORE, new ItemLore(lore));
        return shard;
    }

    private static Optional<String> gift(String eye) {
        return Story.get().quests().stream().flatMap(quest -> quest.steps().stream())
                .filter(step -> step.kind().equals("shard") && step.spec().has("gifts") && step.spec().getAsJsonObject("gifts").has(eye))
                .map(step -> step.spec().getAsJsonObject("gifts").get(eye).getAsString()).findFirst();
    }

    public static void sync(ServerPlayer player) {
        List<ResourceLocation> claimed = Journey.claimed(player.server);
        CuriosApi.getCuriosInventory(player).flatMap(curios -> curios.getStacksHandler(SLOT)).ifPresent(handler -> {
            var stacks = handler.getStacks();
            for (int slot = 0; slot < stacks.getSlots(); slot++) {
                ItemStack shard = slot < claimed.size() ? shard(claimed.get(slot)) : ItemStack.EMPTY;
                if (!ItemStack.matches(stacks.getStackInSlot(slot), shard)) {
                    if (stacks.getStackInSlot(slot).isEmpty()) {
                        Notices.given(player, List.of(shard));
                    }
                    stacks.setStackInSlot(slot, shard);
                }
            }
        });
        for (ResourceLocation hall : claimed) {
            AdvancementHolder advancement = player.server.getAdvancements().get(Wayfarer.id("shards/" + Halls.eyeKey(hall)));
            if (advancement != null && !player.getAdvancements().getOrStartProgress(advancement).isDone()) {
                player.getAdvancements().award(advancement, TAKEN);
            }
        }
    }
}

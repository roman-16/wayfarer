package io.github.roman16.wayfarer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.event.village.WandererTradesEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public final class Cuts {
    private static final TagKey<Block> ROOTLESS = TagKey.create(Registries.BLOCK, Wayfarer.id("rootless"));
    private static final TagKey<Block> STORAGE = TagKey.create(Registries.BLOCK, Wayfarer.id("storage"));
    private static final Bundled<Spec> SPEC = Bundled.json("/wayfarer/cuts.json", json -> new Spec(ids(json.getAsJsonArray("creatures")),
            ids(json.getAsJsonArray("enchantments")), ids(json.getAsJsonArray("features")), ids(json.getAsJsonArray("items"))));

    private Cuts() {}

    private record Spec(Set<ResourceLocation> creatures, Set<ResourceLocation> enchantments, Set<ResourceLocation> features, Set<ResourceLocation> items) {}

    public static boolean feature(ResourceLocation feature) {
        return SPEC.get().features().contains(feature);
    }

    public static boolean cut(ItemStack stack) {
        return SPEC.get().items().contains(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                || EnchantmentHelper.getEnchantmentsForCrafting(stack).keySet().stream().anyMatch(Cuts::cut);
    }

    private static boolean cut(Holder<Enchantment> enchantment) {
        return enchantment.unwrapKey().map(key -> SPEC.get().enchantments().contains(key.location())).orElse(false);
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        dropRecipes(event.getServer());
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD && server != null) {
            dropRecipes(server);
        }
    }

    private static void dropRecipes(MinecraftServer server) {
        List<RecipeHolder<?>> kept = server.getRecipeManager().getRecipes().stream()
                .filter(recipe -> !cut(recipe.value().getResultItem(server.registryAccess())))
                .toList();
        server.getRecipeManager().replaceRecipes(kept);
    }

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        event.getTrades().values().forEach(Cuts::dropTrades);
    }

    @SubscribeEvent
    public static void onWandererTrades(WandererTradesEvent event) {
        dropTrades(event.getGenericTrades());
        dropTrades(event.getRareTrades());
    }

    private static void dropTrades(List<VillagerTrades.ItemListing> listings) {
        listings.replaceAll(listing -> (trader, random) -> {
            MerchantOffer offer = listing.getOffer(trader, random);
            return offer == null || cut(offer.getResult()) ? null : offer;
        });
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ExperienceOrb || SPEC.get().creatures().contains(BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBreed(BabyEntitySpawnEvent event) {
        event.setCanceled(true);
        tell(event.getCausedByPlayer(), "wayfarer.cut.breed");
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getPlacedBlock().is(ROOTLESS)) {
            refuse(event, player, "wayfarer.cut.root");
        } else if (event.getPlacedBlock().is(STORAGE) && !Tents.isTent(player.level().dimension())) {
            refuse(event, player, "wayfarer.cut.storage");
        }
    }

    private static void refuse(BlockEvent.EntityPlaceEvent event, ServerPlayer player, String key) {
        event.setCanceled(true);
        tell(player, key);
        player.server.execute(() -> player.containerMenu.sendAllDataToRemote());
    }

    @SubscribeEvent
    public static void onSleep(CanPlayerSleepEvent event) {
        if (!Tents.isTent(event.getLevel().dimension())) {
            event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
            tell(event.getEntity(), "wayfarer.cut.sleep");
        }
    }

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        Item item = event.getLevel().getBlockState(event.getPos()).getBlock().asItem();
        if (event.getEntity() instanceof ServerPlayer player && item != Items.AIR && SPEC.get().items().contains(BuiltInRegistries.ITEM.getKey(item))) {
            event.setCanceled(true);
            tell(player, "wayfarer.cut.cold");
        }
    }

    private static void tell(Player player, String key) {
        if (player != null) {
            player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY), true);
        }
    }

    private static Set<ResourceLocation> ids(JsonArray array) {
        return array.asList().stream().map(JsonElement::getAsString).map(ResourceLocation::parse).collect(Collectors.toUnmodifiableSet());
    }

    public static final class Loot extends LootModifier {
        public static final MapCodec<Loot> CODEC = RecordCodecBuilder.mapCodec(instance -> codecStart(instance).apply(instance, Loot::new));

        public Loot(LootItemCondition[] conditions) {
            super(conditions);
        }

        @Override
        protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
            loot.removeIf(stack -> SPEC.get().items().contains(BuiltInRegistries.ITEM.getKey(stack.getItem())));
            loot.forEach(stack -> EnchantmentHelper.updateEnchantments(stack, enchantments -> enchantments.removeIf(Cuts::cut)));
            loot.removeIf(stack -> stack.is(Items.ENCHANTED_BOOK) && EnchantmentHelper.getEnchantmentsForCrafting(stack).isEmpty());
            return loot;
        }

        @Override
        public MapCodec<? extends IGlobalLootModifier> codec() {
            return CODEC;
        }
    }
}

package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.mixin.CompoundContainerAccessor;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class Sorting {
    public static final Comparator<ItemStack> ORDER = Comparator.comparing(Sorting::kind)
            .thenComparing(Sorting::mod)
            .thenComparing(stack -> stack.getHoverName().getString())
            .thenComparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
            .thenComparingInt(ItemStack::getDamageValue);
    private static final String BACKPACKS = "sophisticatedcore";
    private static final Set<UUID> LOOKING = new HashSet<>();
    private static final Map<UUID, Integer> TIDY = new HashMap<>();

    private Sorting() {}

    public record Looking(boolean open) implements CustomPacketPayload {
        public static final Type<Looking> TYPE = new Type<>(Wayfarer.id("looking"));
        public static final StreamCodec<ByteBuf, Looking> STREAM_CODEC = ByteBufCodecs.BOOL.map(Looking::new, Looking::open);

        @Override
        public Type<Looking> type() {
            return TYPE;
        }
    }

    private enum Kind {
        TOOLS,
        ARMOUR,
        FOOD,
        BLOCKS,
        OTHER
    }

    private static String mod(ItemStack stack) {
        String namespace = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        return namespace.equals(ResourceLocation.DEFAULT_NAMESPACE) ? "" : namespace;
    }

    public static void look(ServerPlayer player, boolean open) {
        if (open) {
            LOOKING.add(player.getUUID());
        } else {
            LOOKING.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.containerMenu == player.inventoryMenu && !LOOKING.contains(player.getUUID())) {
            if (TIDY.getOrDefault(player.getUUID(), 0) != fingerprint(player.getInventory())) {
                tidy(player.getInventory());
                TIDY.put(player.getUUID(), fingerprint(player.getInventory()));
            }
        }
    }

    @SubscribeEvent
    public static void onClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        AbstractContainerMenu menu = event.getContainer();
        if (ModList.get().isLoaded(BACKPACKS)) {
            Backpacks.sort(player, menu);
        }
        if ((menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu) && !watched(player, menu)) {
            tidy(stored(menu), player);
        }
    }

    @SubscribeEvent
    public static void onLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        LOOKING.remove(event.getEntity().getUUID());
        TIDY.remove(event.getEntity().getUUID());
    }

    public static void tidy(Inventory inventory) {
        List<ItemStack> main = inventory.items.subList(Inventory.getSelectionSize(), Inventory.INVENTORY_SIZE);
        List<ItemStack> laid = laid(main, main.size(), stack -> inventory.getMaxStackSize(stack));
        for (int index = 0; index < main.size(); index++) {
            main.set(index, laid.get(index));
        }
    }

    public static void tidy(List<Slot> slots, Player player) {
        if (slots.isEmpty() || slots.stream().anyMatch(slot -> slot.hasItem() && !slot.mayPickup(player))) {
            return;
        }
        List<ItemStack> laid = laid(slots.stream().map(Slot::getItem).toList(), slots.size(), stack -> slots.getFirst().getMaxStackSize(stack));
        for (int index = 0; index < slots.size(); index++) {
            slots.get(index).set(laid.get(index));
        }
    }

    public static List<Slot> stored(AbstractContainerMenu menu) {
        return menu.slots.stream().filter(slot -> !(slot.container instanceof Inventory)).toList();
    }

    private static List<ItemStack> laid(List<ItemStack> stacks, int room, ToIntFunction<ItemStack> most) {
        List<ItemStack> piles = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            piles.stream().filter(pile -> ItemStack.isSameItemSameComponents(pile, stack)).findFirst()
                    .ifPresentOrElse(pile -> pile.grow(stack.getCount()), () -> piles.add(stack.copy()));
        }
        piles.sort(ORDER);
        List<ItemStack> laid = new ArrayList<>();
        for (ItemStack pile : piles) {
            int limit = Math.min(most.applyAsInt(pile), pile.getMaxStackSize());
            for (int left = pile.getCount(); left > 0; left -= limit) {
                laid.add(pile.copyWithCount(Math.min(limit, left)));
            }
        }
        while (laid.size() < room) {
            laid.add(ItemStack.EMPTY);
        }
        return laid;
    }

    private static Kind kind(ItemStack stack) {
        Equipable equipable = Equipable.get(stack);
        if (equipable != null && equipable.getEquipmentSlot().isArmor()) {
            return Kind.ARMOUR;
        }
        if (stack.isDamageableItem()) {
            return Kind.TOOLS;
        }
        if (stack.has(DataComponents.FOOD)) {
            return Kind.FOOD;
        }
        return stack.getItem() instanceof BlockItem ? Kind.BLOCKS : Kind.OTHER;
    }

    private static int fingerprint(Inventory inventory) {
        int print = 1;
        for (ItemStack stack : inventory.items.subList(Inventory.getSelectionSize(), Inventory.INVENTORY_SIZE)) {
            print = 31 * print + (stack.isEmpty() ? 0 : ItemStack.hashItemAndComponents(stack) * 31 + stack.getCount());
        }
        return print;
    }

    private static boolean watched(ServerPlayer closing, AbstractContainerMenu menu) {
        Set<Container> held = new HashSet<>();
        stored(menu).forEach(slot -> held.addAll(halves(slot.container)));
        return closing.server.getPlayerList().getPlayers().stream()
                .filter(player -> player != closing && player.containerMenu != player.inventoryMenu)
                .flatMap(player -> stored(player.containerMenu).stream())
                .flatMap(slot -> halves(slot.container).stream())
                .anyMatch(held::contains);
    }

    private static List<Container> halves(Container container) {
        return container instanceof CompoundContainer both
                ? Stream.of(((CompoundContainerAccessor) both).wayfarer$first(), ((CompoundContainerAccessor) both).wayfarer$second()).flatMap(half -> halves(half).stream()).toList()
                : List.of(container);
    }
}

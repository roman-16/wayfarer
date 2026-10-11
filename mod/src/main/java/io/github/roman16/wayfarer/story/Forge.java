package io.github.roman16.wayfarer.story;

import com.google.gson.JsonObject;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Bundled;
import io.github.roman16.wayfarer.Clock;
import io.github.roman16.wayfarer.FrostIron;
import io.github.roman16.wayfarer.Notices;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.fml.ModList;

final class Forge {
    static final String QUEST = "the_lost_ore";
    private static final String SMITH = "hob";
    private static final String SCRATCH = "forge";
    private static final String PIECE = "piece";
    private static final String WITH = "with";
    private static final String JOB = "job";
    private static final String DAY = "day";
    private static final Bundled<JsonObject> LINES = Bundled.json("/wayfarer/gear.json", json -> json.getAsJsonObject(SCRATCH));

    private Forge() {}

    private enum Job { BOOK, FORGE, MEND }

    private record Order(ItemStack piece, Job job, ItemStack with, long day) {}

    static boolean interact(ServerPlayer player, Entity body) {
        if (People.who(body).filter(SMITH::equals).isEmpty() || !body.isAlive()) {
            return false;
        }
        MinecraftServer server = player.server;
        long today = Math.floorDiv(Engine.overworld(server).getDayTime(), Clock.DAY);
        List<Order> orders = orders(server, player);
        if (orders.stream().anyMatch(order -> order.day() < today)) {
            hand(player, body, orders, today);
            return true;
        }
        ItemStack held = player.getMainHandItem();
        if (!held.isDamageableItem()) {
            if (orders.isEmpty()) {
                return false;
            }
            say(player, body, line("not_yet"));
            return true;
        }
        boolean hammer = Progress.of(server).done(QUEST);
        ItemStack other = player.getOffhandItem();
        if (other.is(Wayfarer.RAW_FROST_IRON)) {
            forge(player, body, held, hammer, orders, today);
        } else if (other.is(Items.ENCHANTED_BOOK)) {
            book(player, body, held, other, hammer, orders, today);
        } else {
            mend(player, body, held, hammer, orders, today);
        }
        return true;
    }

    private static void mend(ServerPlayer player, Entity body, ItemStack held, boolean hammer, List<Order> orders, long today) {
        if (!held.isDamaged()) {
            say(player, body, line("whole"));
        } else if (!hammer && held.getDamageValue() <= held.getMaxDamage() / 2) {
            say(player, body, line("half"));
        } else {
            take(player, body, new Order(held.copy(), Job.MEND, ItemStack.EMPTY, today), orders, hammer ? "leave" : "leave_half");
            held.setCount(0);
        }
    }

    private static void forge(ServerPlayer player, Entity body, ItemStack held, boolean hammer, List<Order> orders, long today) {
        if (!hammer) {
            say(player, body, line("no_hammer"));
            return;
        }
        JsonObject lumps = LINES.get().getAsJsonObject("lumps");
        Optional<String> shape = ModList.get().isLoaded("silentgear") ? FrostIron.shape(held).filter(lumps::has) : Optional.empty();
        if (shape.isEmpty()) {
            say(player, body, line("not_mine"));
            return;
        }
        if (FrostIron.made(held)) {
            say(player, body, line("already"));
            return;
        }
        int need = lumps.get(shape.get()).getAsInt();
        int have = player.getInventory().countItem(Wayfarer.RAW_FROST_IRON.get());
        if (have < need) {
            JsonObject lines = LINES.get();
            say(player, body, line("short").replace("{wants}", lines.getAsJsonObject("wants").get(shape.get()).getAsString())
                    .replace("{need}", number(need)).replace("{have}", number(have)));
            return;
        }
        int left = need;
        for (ItemStack stack : allStacks(player)) {
            if (left > 0 && stack.is(Wayfarer.RAW_FROST_IRON)) {
                int used = Math.min(left, stack.getCount());
                stack.shrink(used);
                left -= used;
            }
        }
        take(player, body, new Order(held.copy(), Job.FORGE, new ItemStack(Wayfarer.RAW_FROST_IRON.get(), need), today), orders, "leave");
        held.setCount(0);
    }

    private static void book(ServerPlayer player, Entity body, ItemStack held, ItemStack book, boolean hammer, List<Order> orders, long today) {
        if (!hammer) {
            say(player, body, line("book_no_hammer"));
            return;
        }
        if (fitting(held, book).isEmpty()) {
            say(player, body, line("book_wont"));
            return;
        }
        take(player, body, new Order(held.copy(), Job.BOOK, book.copyWithCount(1), today), orders, "leave");
        held.setCount(0);
        book.shrink(1);
    }

    private static Map<Holder<Enchantment>, Integer> fitting(ItemStack piece, ItemStack book) {
        ItemEnchantments written = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments already = piece.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        Map<Holder<Enchantment>, Integer> fits = new LinkedHashMap<>();
        for (var entry : written.entrySet()) {
            Holder<Enchantment> enchantment = entry.getKey();
            boolean compatible = already.keySet().stream().allMatch(other -> other.equals(enchantment) || Enchantment.areCompatible(other, enchantment));
            if (piece.supportsEnchantment(enchantment) && compatible && already.getLevel(enchantment) < entry.getIntValue()) {
                fits.put(enchantment, entry.getIntValue());
            }
        }
        return fits;
    }

    private static void take(ServerPlayer player, Entity body, Order order, List<Order> orders, String said) {
        List<Order> kept = new ArrayList<>(orders);
        kept.add(order);
        save(player, kept);
        say(player, body, line(said));
    }

    private static void hand(ServerPlayer player, Entity body, List<Order> orders, long today) {
        boolean hammer = Progress.of(player.server).done(QUEST);
        List<Order> kept = new ArrayList<>();
        for (Order order : orders) {
            if (order.day() >= today) {
                kept.add(order);
                continue;
            }
            ItemStack made = work(order, hammer);
            ItemStack shown = made.copy();
            if (player.getInventory().add(made)) {
                Notices.given(player, List.of(shown));
            } else {
                kept.add(order);
            }
        }
        save(player, kept);
        say(player, body, line(kept.stream().anyMatch(order -> order.day() < today) ? "room" : "here"));
    }

    private static ItemStack work(Order order, boolean hammer) {
        ItemStack piece = order.piece().copy();
        return switch (order.job()) {
            case MEND -> {
                piece.setDamageValue(hammer ? 0 : Math.min(piece.getDamageValue(), piece.getMaxDamage() / 2));
                yield piece;
            }
            case FORGE -> FrostIron.shape(piece).map(shape -> FrostIron.forge(piece, shape)).orElse(piece);
            case BOOK -> {
                Map<Holder<Enchantment>, Integer> fits = fitting(piece, order.with());
                EnchantmentHelper.updateEnchantments(piece, enchantments -> fits.forEach(enchantments::upgrade));
                yield piece;
            }
        };
    }

    private static List<ItemStack> allStacks(ServerPlayer player) {
        List<ItemStack> stacks = new ArrayList<>(player.getInventory().offhand);
        stacks.addAll(player.getInventory().items);
        return stacks;
    }

    private static List<Order> orders(MinecraftServer server, ServerPlayer player) {
        HolderLookup.Provider registries = server.registryAccess();
        List<Order> orders = new ArrayList<>();
        for (Tag element : Progress.of(server).scratch(SCRATCH).getList(player.getStringUUID(), Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) element;
            ItemStack piece = ItemStack.parseOptional(registries, tag.getCompound(PIECE));
            if (!piece.isEmpty()) {
                orders.add(new Order(piece, Job.valueOf(tag.getString(JOB)), ItemStack.parseOptional(registries, tag.getCompound(WITH)), tag.getLong(DAY)));
            }
        }
        return orders;
    }

    private static void save(ServerPlayer player, List<Order> orders) {
        HolderLookup.Provider registries = player.server.registryAccess();
        ListTag list = new ListTag();
        for (Order order : orders) {
            CompoundTag tag = new CompoundTag();
            tag.put(PIECE, order.piece().save(registries));
            tag.putString(JOB, order.job().name());
            if (!order.with().isEmpty()) {
                tag.put(WITH, order.with().save(registries));
            }
            tag.putLong(DAY, order.day());
            list.add(tag);
        }
        Progress progress = Progress.of(player.server);
        if (list.isEmpty()) {
            progress.scratch(SCRATCH).remove(player.getStringUUID());
        } else {
            progress.scratch(SCRATCH).put(player.getStringUUID(), list);
        }
        progress.setDirty();
    }

    private static String line(String key) {
        return LINES.get().get(key).getAsString();
    }

    private static String number(int count) {
        return LINES.get().getAsJsonArray("numbers").get(count).getAsString();
    }

    private static void say(ServerPlayer player, Entity body, String text) {
        int reach = Balance.get().talk().reachBlocks();
        Talk.tell(player.server.getPlayerList().getPlayers().stream()
                .filter(listener -> listener.level() == body.level() && listener.distanceToSqr(body) <= (double) reach * reach).toList(),
                People.label(player.server, SMITH), Component.literal(text));
    }
}

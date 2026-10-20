package io.github.roman16.wayfarer;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;

final class Fishing {
    private static final EntityDataAccessor<Boolean> BITING = biting();
    private static final int LANDING_TICKS = 60;
    private static final int BITE_TICKS = 900;
    private static final int REELED_TICKS = 30;

    private enum Phase { CAST, FLYING, WAITING, REELED }

    private Fishing() {}

    static void fish(Minecraft minecraft, String argument) {
        LocalPlayer player = minecraft.player;
        int wanted = argument.isBlank() ? 1 : Integer.parseInt(argument.strip());
        if (!takeRod(player)) {
            return;
        }
        Map<String, Integer> before = counts(player);
        int[] caught = {0};
        int[] casts = {0};
        long[] since = {0};
        Phase[] phase = {Phase.CAST};
        Driver.after(() -> {
            if (Body.busy()) {
                Driver.progress();
                return false;
            }
            if (!(player.getMainHandItem().getItem() instanceof FishingRodItem)) {
                phase[0] = Phase.CAST;
                return !takeRod(player);
            }
            long now = minecraft.level.getGameTime();
            FishingHook hook = player.fishing;
            switch (phase[0]) {
                case CAST -> {
                    if (hook == null) {
                        KeyMapping.click(minecraft.options.keyUse.getKey());
                        casts[0]++;
                        since[0] = now;
                        Driver.progress();
                        phase[0] = Phase.FLYING;
                    }
                }
                case FLYING -> {
                    if (hook != null && hook.isInWater()) {
                        since[0] = now;
                        phase[0] = Phase.WAITING;
                    } else if (now - since[0] > LANDING_TICKS) {
                        if (hook != null) {
                            KeyMapping.click(minecraft.options.keyUse.getKey());
                        }
                        Driver.fail("fish failed: the line found no water ahead; face open water within a few blocks and fish again");
                        return false;
                    }
                }
                case WAITING -> {
                    if (hook == null) {
                        Driver.fail("fish failed: the line came in on its own after " + caught[0] + " caught");
                        return false;
                    }
                    if (hook.getEntityData().get(BITING)) {
                        KeyMapping.click(minecraft.options.keyUse.getKey());
                        caught[0]++;
                        since[0] = now;
                        Driver.progress();
                        phase[0] = Phase.REELED;
                    } else if (now - since[0] > BITE_TICKS) {
                        KeyMapping.click(minecraft.options.keyUse.getKey());
                        since[0] = now;
                        Driver.progress();
                        phase[0] = Phase.REELED;
                    }
                }
                case REELED -> {
                    if (hook == null && now - since[0] > REELED_TICKS) {
                        if (caught[0] >= wanted) {
                            Driver.note("fish: " + landed(before, counts(player)) + " in " + casts[0] + (casts[0] == 1 ? " cast" : " casts"));
                            return true;
                        }
                        phase[0] = Phase.CAST;
                    }
                }
            }
            return false;
        }, "waiting for a bite", null);
    }

    private static boolean takeRod(LocalPlayer player) {
        int slot = IntStream.range(0, Inventory.getSelectionSize()).filter(index -> player.getInventory().getItem(index).getItem() instanceof FishingRodItem)
                .findFirst().orElse(-1);
        if (slot < 0) {
            Driver.fail("fish failed: no fishing rod in the hotbar");
            return false;
        }
        player.getInventory().selected = slot;
        return true;
    }

    private static String landed(Map<String, Integer> before, Map<String, Integer> after) {
        String gained = after.entrySet().stream().filter(entry -> entry.getValue() > before.getOrDefault(entry.getKey(), 0))
                .map(entry -> (entry.getValue() - before.getOrDefault(entry.getKey(), 0)) + " " + entry.getKey()).sorted().collect(Collectors.joining(", "));
        return gained.isEmpty() ? "nothing landed in the inventory" : "landed " + gained;
    }

    private static Map<String, Integer> counts(LocalPlayer player) {
        Map<String, Integer> counts = new HashMap<>();
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty()) {
                counts.merge(stack.getHoverName().getString(), stack.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    @SuppressWarnings("unchecked")
    private static EntityDataAccessor<Boolean> biting() {
        try {
            Field field = FishingHook.class.getDeclaredField("DATA_BITING");
            field.setAccessible(true);
            return (EntityDataAccessor<Boolean>) field.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

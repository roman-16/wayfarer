package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.story.Tasks;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.silentchaos512.gear.api.item.GearItem;
import net.silentchaos512.gear.item.CompoundPartItem;
import net.silentchaos512.gear.item.blueprint.AbstractBlueprintItem;
import net.silentchaos512.gear.setup.gear.PartTypes;

public final class Patterns {
    private static final String HEAD = "head";
    private static final String TOOL = "tool";

    private Patterns() {}

    public static Optional<String> task(ServerPlayer player) {
        if (player.getInventory().items.stream().noneMatch(stack -> stack.getItem() instanceof AbstractBlueprintItem)) {
            return Optional.empty();
        }
        if (!Firsts.seen(player, "task." + HEAD)) {
            return Optional.of(HEAD);
        }
        return Firsts.seen(player, "task." + TOOL) ? Optional.empty() : Optional.of(TOOL);
    }

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getCrafting().getItem() instanceof CompoundPartItem part && part.getPartType() == PartTypes.MAIN.get()) {
            Tasks.did(player, HEAD);
        } else if (event.getCrafting().getItem() instanceof GearItem) {
            Tasks.did(player, TOOL);
        }
    }
}

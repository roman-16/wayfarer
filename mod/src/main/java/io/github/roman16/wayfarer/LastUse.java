package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.story.People;
import io.github.roman16.wayfarer.story.Progress;
import io.github.roman16.wayfarer.story.Talk;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class LastUse {
    private static final List<Holder<Attribute>> BARE = List.of(Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS, Attributes.ATTACK_DAMAGE,
            Attributes.ATTACK_SPEED, Attributes.KNOCKBACK_RESISTANCE);
    private static final String SMITH = "hob";

    private LastUse() {}

    public static boolean lastUse(ItemStack stack) {
        return stack.has(DataComponents.MAX_DAMAGE) && stack.getMaxDamage() > 0 && stack.getDamageValue() >= stack.getMaxDamage() - 1;
    }

    public static int room(ItemStack stack, int damage) {
        return damage <= 0 ? damage : Math.min(damage, Math.max(0, stack.getMaxDamage() - 1 - stack.getDamageValue()));
    }

    public static void worn(ServerPlayer player, ItemStack stack) {
        if (!lastUse(stack) || !Firsts.first(player, "last_use")) {
            return;
        }
        Talk.tell(player, Component.translatable("wayfarer.gear.worn", stack.getHoverName()));
        if (Progress.of(player.server).met(SMITH)) {
            Talk.tell(List.of(player), People.label(player.server, SMITH), Component.translatable("wayfarer.gear.smith"));
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        ItemStack tool = event.getEntity().getMainHandItem();
        if (lastUse(tool)) {
            event.setNewSpeed(event.getNewSpeed() / Math.max(1, tool.getDestroySpeed(event.getState())));
        }
    }

    @SubscribeEvent
    public static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        if (lastUse(event.getEntity().getMainHandItem())) {
            event.setCanHarvest(!event.getTargetBlock().requiresCorrectToolForDrops());
        }
    }

    @SubscribeEvent
    public static void onAttributes(ItemAttributeModifierEvent event) {
        if (lastUse(event.getItemStack())) {
            BARE.forEach(event::removeAllModifiersFor);
        }
    }

    @SubscribeEvent
    public static void onToolUse(BlockEvent.BlockToolModificationEvent event) {
        if (lastUse(event.getHeldItemStack())) {
            event.setCanceled(true);
        }
    }
}

package io.github.roman16.wayfarer.spike;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.Unbreakable;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;
import net.neoforged.neoforge.event.entity.player.PlayerDestroyItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class LastUseSpike {
    private static final List<Holder<Attribute>> BARE = List.of(Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS, Attributes.ATTACK_DAMAGE,
            Attributes.ATTACK_SPEED, Attributes.KNOCKBACK_RESISTANCE);
    private static final int GAMEMASTER = 2;
    private static final String HELD = "wayfarer_last_use";

    private static final List<Broken> broken = new ArrayList<>();

    private LastUseSpike() {}

    private record Broken(ServerPlayer player, int slot, ItemStack kept) {}

    static boolean lastUse(ItemStack stack) {
        return stack.has(DataComponents.MAX_DAMAGE) && stack.getDamageValue() >= stack.getMaxDamage() - 1;
    }

    private static boolean held(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.contains(HELD);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("lastuse")
                        .requires(source -> source.hasPermission(GAMEMASTER))
                        .then(Commands.literal("hit").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(context -> hit(EntityArgument.getPlayer(context, "player"), EntityArgument.getEntity(context, "target"))))))
                        .then(Commands.literal("wear").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("slot", StringArgumentType.word())
                                        .then(Commands.argument("left", IntegerArgumentType.integer(1))
                                                .executes(context -> wear(EntityArgument.getPlayer(context, "player"),
                                                        EquipmentSlot.byName(StringArgumentType.getString(context, "slot")),
                                                        IntegerArgumentType.getInteger(context, "left")))))))
                        .then(Commands.literal("gear").then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> gear(EntityArgument.getPlayer(context, "player")))))));
    }

    private static int hit(ServerPlayer player, Entity target) {
        float before = target instanceof LivingEntity living ? living.getHealth() : 0;
        player.resetAttackStrengthTicker();
        player.attack(target);
        float after = target instanceof LivingEntity living ? living.getHealth() : 0;
        say(player.server, "lastuse: %s hit %s with %s for %.1f".formatted(player.getScoreboardName(), target.getName().getString(),
                describe(player.getMainHandItem()), before - after));
        return 1;
    }

    private static int wear(ServerPlayer player, EquipmentSlot slot, int left) {
        ItemStack stack = player.getItemBySlot(slot);
        stack.setDamageValue(stack.getMaxDamage() - left);
        say(player.server, "lastuse: %s's %s".formatted(player.getScoreboardName(), describe(stack)));
        return 1;
    }

    private static int gear(ServerPlayer player) {
        say(player.server, "lastuse: %s holds %s; attack damage %.1f, armour %.1f, wearing %s".formatted(player.getScoreboardName(),
                describe(player.getMainHandItem()), player.getAttributeValue(Attributes.ATTACK_DAMAGE), player.getAttributeValue(Attributes.ARMOR),
                describe(player.getItemBySlot(EquipmentSlot.CHEST))));
        return 1;
    }

    private static String describe(ItemStack stack) {
        if (stack.isEmpty()) {
            return "nothing";
        }
        String name = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return stack.has(DataComponents.MAX_DAMAGE) ? "%s, %d of %d left%s".formatted(name, stack.getMaxDamage() - stack.getDamageValue(),
                stack.getMaxDamage(), lastUse(stack) ? ", at its last use" : "") : name;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (Broken each : broken) {
            if (each.player().getInventory().getItem(each.slot()).isEmpty()) {
                each.player().getInventory().setItem(each.slot(), each.kept());
                say(each.player().server, "lastuse: %s's %s would have broken and is kept".formatted(each.player().getScoreboardName(),
                        describe(each.kept())));
            }
        }
        broken.clear();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            List<ItemStack> all = new ArrayList<>(player.getInventory().items);
            all.addAll(player.getInventory().armor);
            all.addAll(player.getInventory().offhand);
            for (ItemStack stack : all) {
                if (lastUse(stack) && !stack.has(DataComponents.UNBREAKABLE)) {
                    stack.set(DataComponents.UNBREAKABLE, new Unbreakable(false));
                    CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(HELD, true));
                    say(player.server, "lastuse: %s's %s".formatted(player.getScoreboardName(), describe(stack)));
                } else if (!lastUse(stack) && held(stack)) {
                    stack.remove(DataComponents.UNBREAKABLE);
                    CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(HELD));
                    say(player.server, "lastuse: %s's %s is mended".formatted(player.getScoreboardName(), describe(stack)));
                }
            }
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

    @SubscribeEvent
    public static void onArmorHurt(ArmorHurtEvent event) {
        for (Map.Entry<EquipmentSlot, ArmorHurtEvent.ArmorEntry> entry : event.getArmorMap().entrySet()) {
            ItemStack armour = entry.getValue().armorItemStack;
            if (armour.has(DataComponents.MAX_DAMAGE)) {
                int room = Math.max(0, armour.getMaxDamage() - 1 - armour.getDamageValue());
                event.setNewDamage(entry.getKey(), Math.min(event.getNewDamage(entry.getKey()), room));
            }
        }
    }

    @SubscribeEvent
    public static void onDestroyed(PlayerDestroyItemEvent event) {
        ItemStack original = event.getOriginal();
        if (event.getHand() != null && original.has(DataComponents.MAX_DAMAGE) && event.getEntity() instanceof ServerPlayer player) {
            ItemStack kept = original.copyWithCount(1);
            kept.setDamageValue(kept.getMaxDamage() - 1);
            int slot = event.getHand() == InteractionHand.MAIN_HAND ? player.getInventory().selected : Inventory.SLOT_OFFHAND;
            broken.add(new Broken(player, slot, kept));
        }
    }

    private static void say(MinecraftServer server, String text) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(text), false);
    }
}

package io.github.roman16.wayfarer.story;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Record;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import toughasnails.api.temperature.TemperatureHelper;

public final class Trail {
    private static final int CHECK_TICKS = 20;
    private static final int TICKS_PER_SECOND = 20;
    private static final String WORDS = "words";
    private static final Map<UUID, Set<ResourceLocation>> carried = new HashMap<>();
    private static final Map<UUID, String> standing = new HashMap<>();
    private static final Map<UUID, Watch> watches = new HashMap<>();

    private Trail() {}

    private static final class Watch {
        Vec3 from;
        Optional<BlockPos> target;
        double goal;
        long since;
        boolean stalled;

        Watch(Vec3 from, Optional<BlockPos> target, double goal, long since) {
            this.from = from;
            this.target = target;
            this.goal = goal;
            this.since = since;
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("hint")
                        .executes(context -> hint(context.getSource(), ""))
                        .then(Commands.argument(WORDS, StringArgumentType.greedyString())
                                .executes(context -> hint(context.getSource(), StringArgumentType.getString(context, WORDS))))));
    }

    private static int hint(CommandSourceStack source, String words) {
        Record.event(source.getServer(), "hint", source.getPlayer(), line -> line.addProperty("text", words));
        source.sendSuccess(() -> Component.translatable("wayfarer.hint.noted"), false);
        return 1;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            carried.put(player.getUUID(), items(player));
            watches.remove(player.getUUID());
            standing.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_TICKS != 0) {
            return;
        }
        Balance.Trail trail = Balance.get().trail();
        boolean position = server.getTickCount() % (trail.positionSeconds() * TICKS_PER_SECOND) == 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            items(server, player);
            if (Engine.running(server)) {
                place(server, player);
            }
            if (position) {
                position(server, player);
                stall(server, player, trail);
            }
        }
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Entity target = event.getTarget();
        Optional<String> person = People.who(target);
        Optional<String> clue = target.getTags().stream().filter(tag -> tag.startsWith(Steps.CLUE)).findFirst();
        if (person.isPresent() || clue.isPresent()) {
            Record.event(player.server, "click", player, line -> line.addProperty("on", person.orElseGet(() -> clue.get().substring(Steps.CLUE.length()))));
        }
    }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ResourceLocation block = BuiltInRegistries.BLOCK.getKey(event.getLevel().getBlockState(event.getPos()).getBlock());
            if (block.getNamespace().equals(Wayfarer.MODID)) {
                Record.event(player.server, "click", player, line -> line.addProperty("on", block.toString()));
            }
        }
    }

    private static void items(MinecraftServer server, ServerPlayer player) {
        Set<ResourceLocation> known = carried.computeIfAbsent(player.getUUID(), uuid -> items(player));
        for (ResourceLocation item : items(player)) {
            if (known.add(item)) {
                Record.event(server, "item", player, line -> line.addProperty("item", item.toString()));
            }
        }
    }

    private static Set<ResourceLocation> items(ServerPlayer player) {
        Set<ResourceLocation> items = new HashSet<>();
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty()) {
                items.add(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            }
        }
        return items;
    }

    private static void place(MinecraftServer server, ServerPlayer player) {
        Progress progress = Progress.of(server);
        String here = progress.places.entrySet().stream()
                .filter(entry -> Language.getInstance().has(Places.label(server, entry.getKey())))
                .filter(entry -> player.blockPosition().distSqr(entry.getValue()) <= (double) Places.arrival(entry.getKey()) * Places.arrival(entry.getKey()))
                .map(Map.Entry::getKey).findFirst().orElse("");
        String before = standing.put(player.getUUID(), here);
        if (!here.isEmpty() && !here.equals(before)) {
            Record.event(server, "place", player, line -> {
                line.addProperty("place", here);
                line.addProperty("label", Component.translatable(Places.label(server, here)).getString());
            });
        }
    }

    private static void position(MinecraftServer server, ServerPlayer player) {
        Optional<BlockPos> goal = Engine.goal(player);
        Record.event(server, "position", player, line -> {
            line.addProperty("biome", player.serverLevel().getBiome(player.blockPosition()).unwrapKey().map(key -> key.location().toString()).orElse(""));
            line.addProperty("facing", Math.round(player.getYRot()));
            line.addProperty("held", BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString());
            line.addProperty("health", Math.round(player.getHealth()));
            line.addProperty("food", player.getFoodData().getFoodLevel());
            line.addProperty("temperature", TemperatureHelper.getTemperatureForPlayer(player).name().toLowerCase(Locale.ROOT));
            Engine.tracked(player).ifPresent(active -> line.addProperty("tracked", active.quest().id() + "/" + active.step().id()));
            goal.ifPresent(pos -> line.addProperty("goal_blocks", (int) Math.sqrt(player.blockPosition().distSqr(pos))));
        });
    }

    private static void stall(MinecraftServer server, ServerPlayer player, Balance.Trail trail) {
        long now = server.getTickCount();
        Optional<BlockPos> target = Engine.goal(player);
        double goal = target.map(pos -> Math.sqrt(player.blockPosition().distSqr(pos))).orElse(Double.MAX_VALUE);
        Watch watch = watches.computeIfAbsent(player.getUUID(), uuid -> new Watch(player.position(), target, goal, now));
        boolean going = !target.equals(watch.target) || (goal == Double.MAX_VALUE ? player.position().distanceTo(watch.from) > trail.stallBlocks() : goal < watch.goal - trail.stallBlocks());
        if (going) {
            if (watch.stalled) {
                long seconds = (now - watch.since) / TICKS_PER_SECOND;
                Record.event(server, "stall ended", player, line -> line.addProperty("seconds", seconds));
            }
            watches.put(player.getUUID(), new Watch(player.position(), target, goal, now));
            return;
        }
        if (!watch.stalled && now - watch.since >= (long) trail.stallSeconds() * TICKS_PER_SECOND) {
            watch.stalled = true;
            Record.event(server, "stall begun", player, line -> line.addProperty("still", player.position().distanceTo(watch.from) <= trail.stallBlocks()));
        }
    }
}

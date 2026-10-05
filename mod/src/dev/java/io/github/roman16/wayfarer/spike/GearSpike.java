package io.github.roman16.wayfarer.spike;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import io.github.roman16.wayfarer.Wayfarer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class GearSpike {
    private static final String DATA = Wayfarer.MODID + "_spike_gear";
    private static final long DAY_TICKS = 24000L;
    private static final String FOUND = "found_frost_iron";
    private static final int FROST_PER_BLADE = 2;
    private static final int GAMEMASTER = 2;
    private static final JsonArray GEAR = JsonParser.parseString(resource("/wayfarer/spike/gear.json")).getAsJsonArray();
    private static final int LOOK_TICKS = 20;
    private static final String SILENT_GEAR = "silentgear";
    private static final String SMITH = "smith_frost_iron";
    private static final String UNKNOWN = "?";
    private static Item frostIron;

    private GearSpike() {}

    private static String resource(String path) {
        try (InputStream stream = GearSpike.class.getResourceAsStream(path)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @EventBusSubscriber(modid = Wayfarer.MODID)
    public static final class Items {
        private Items() {}

        @SubscribeEvent
        public static void onRegister(RegisterEvent event) {
            event.register(Registries.ITEM, helper -> {
                frostIron = new Item(new Item.Properties()) {
                    @Override
                    public Component getName(ItemStack stack) {
                        return Component.literal("Frost Iron");
                    }
                };
                helper.register(Wayfarer.id("frost_iron"), frostIron);
            });
        }
    }

    private record Order(BlockPos anvil, String player, long day) {}

    static final class Smithy extends SavedData {
        private final List<Order> orders = new ArrayList<>();
        private final Set<String> learned = new TreeSet<>();

        static Smithy of(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(Smithy::new, Smithy::load, null), DATA);
        }

        private static Smithy load(CompoundTag tag, HolderLookup.Provider registries) {
            Smithy smithy = new Smithy();
            tag.getList("orders", Tag.TAG_COMPOUND).forEach(entry -> {
                CompoundTag order = (CompoundTag) entry;
                smithy.orders.add(new Order(BlockPos.of(order.getLong("anvil")), order.getString("player"), order.getLong("day")));
            });
            tag.getList("learned", Tag.TAG_STRING).forEach(entry -> smithy.learned.add(entry.getAsString()));
            return smithy;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag orders = new ListTag();
            for (Order order : this.orders) {
                CompoundTag entry = new CompoundTag();
                entry.putLong("anvil", order.anvil().asLong());
                entry.putString("player", order.player());
                entry.putLong("day", order.day());
                orders.add(entry);
            }
            tag.put("orders", orders);
            ListTag learned = new ListTag();
            this.learned.forEach(key -> learned.add(StringTag.valueOf(key)));
            tag.put("learned", learned);
            return tag;
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("gear")
                        .requires(source -> source.hasPermission(GAMEMASTER))
                        .then(Commands.literal("use").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(context -> use(EntityArgument.getPlayer(context, "player"), BlockPosArgument.getBlockPos(context, "pos"))))))
                        .then(Commands.literal("forget").executes(context -> forget(context.getSource().getServer())))));
    }

    private static int use(ServerPlayer player, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
        InteractionResult result = player.gameMode.useItemOn(player, player.serverLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        say(player.server, "gear: %s used %s on %d %d %d: %s".formatted(player.getScoreboardName(), player.getMainHandItem().getHoverName().getString(),
                pos.getX(), pos.getY(), pos.getZ(), result));
        return 1;
    }

    private static int forget(MinecraftServer server) {
        Smithy smithy = Smithy.of(server);
        smithy.learned.clear();
        smithy.orders.clear();
        smithy.setDirty();
        say(server, "gear: the journal's Gear page forgets what it learned");
        return 1;
    }

    private static long day(MinecraftServer server) {
        return server.overworld().getDayTime() / DAY_TICKS;
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        if (!held.is(frostIron) || !event.getLevel().getBlockState(event.getPos()).is(BlockTags.ANVIL)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (held.getCount() < FROST_PER_BLADE || !ModList.get().isLoaded(SILENT_GEAR)) {
            player.sendSystemMessage(Component.literal("<Hob> That's not enough for a blade."));
            return;
        }
        held.shrink(FROST_PER_BLADE);
        Smithy smithy = Smithy.of(player.server);
        smithy.orders.add(new Order(event.getPos(), player.getScoreboardName(), day(player.server)));
        smithy.setDirty();
        learn(player.server, SMITH);
        say(player.server, "<Hob> Frost iron. Leave it with me tonight.");
        say(player.server, "gear: %s left %d frost iron at the anvil on day %d".formatted(player.getScoreboardName(), FROST_PER_BLADE, day(player.server)));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % LOOK_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getInventory().items.stream().anyMatch(stack -> stack.is(frostIron))) {
                learn(server, FOUND);
            }
        }
        Smithy smithy = Smithy.of(server);
        for (Order order : List.copyOf(smithy.orders)) {
            if (day(server) <= order.day()) {
                continue;
            }
            for (ServerLevel level : server.getAllLevels()) {
                if (level.isLoaded(order.anvil()) && level.getBlockState(order.anvil()).is(BlockTags.ANVIL)) {
                    ItemStack blade = SilentGearSword.frostIron();
                    ItemEntity lying = new ItemEntity(level, order.anvil().getX() + 0.5, order.anvil().getY() + 1.05, order.anvil().getZ() + 0.5, blade, 0, 0, 0);
                    lying.setPickUpDelay(0);
                    level.addFreshEntity(lying);
                    smithy.orders.remove(order);
                    smithy.setDirty();
                    say(server, "Morning: a sword the colour of river ice lies on the anvil.");
                    say(server, "gear: %s's blade is ready on day %d: %s".formatted(order.player(), day(server), blade.getHoverName().getString()));
                    break;
                }
            }
        }
    }

    private static void learn(MinecraftServer server, String key) {
        Smithy smithy = Smithy.of(server);
        if (smithy.learned.add(key)) {
            smithy.setDirty();
            say(server, "gear: the journal's Gear page learns %s".formatted(key));
        }
    }


    private static void say(MinecraftServer server, String text) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(text), false);
    }
}

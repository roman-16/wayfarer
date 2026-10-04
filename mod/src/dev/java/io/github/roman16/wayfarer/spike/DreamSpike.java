package io.github.roman16.wayfarer.spike;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.markusbordihn.easynpc.api.action.EasyNPCActionHandler;
import de.markusbordihn.easynpc.api.pose.ModelPoseAPI;
import de.markusbordihn.easynpc.data.action.MoveActionData;
import de.markusbordihn.easynpc.data.skin.SkinDataEntry;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import de.markusbordihn.easynpc.handler.SkinHandler;
import io.github.roman16.wayfarer.Wayfarer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.commoble.infiniverse.api.InfiniverseAPI;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class DreamSpike {
    private static final String BODY = "easy_npc:humanoid";
    private static final int CHUNKS = 3;
    private static final int GAMEMASTER = 2;
    private static final String INFINIVERSE = "infiniverse";
    private static final ResourceLocation KNEELING = ResourceLocation.fromNamespaceAndPath("easy_npc", "pose/humanoid/crouching");
    private static final int LISTED = 8;
    private static final Map<Block, Block> NEWER = Map.of(
            Blocks.CRACKED_STONE_BRICKS, Blocks.STONE_BRICKS,
            Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICKS,
            Blocks.CRACKED_DEEPSLATE_TILES, Blocks.DEEPSLATE_TILES,
            Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE,
            Blocks.MOSSY_STONE_BRICKS, Blocks.STONE_BRICKS,
            Blocks.COBWEB, Blocks.AIR,
            Blocks.VINE, Blocks.AIR);
    private static final ResourceLocation PEDESTAL = Wayfarer.id("shrine_pedestal");
    private static final int SEEK = 8;
    private static final int SPAN = 24;
    private static final TicketType<ChunkPos> TICKET = TicketType.create("wayfarer_dream", Comparator.comparingLong(ChunkPos::toLong));
    private static final int TOGETHER = 32;
    private static final List<ResourceKey<Level>> ended = new ArrayList<>();
    @Nullable
    private static Dream dream;

    private DreamSpike() {}

    @EventBusSubscriber(modid = Wayfarer.MODID)
    public static final class WithInfiniverse {
        private WithInfiniverse() {}

        @SubscribeEvent
        public static void onSetup(FMLCommonSetupEvent event) {
            if (ModList.get().isLoaded(INFINIVERSE)) {
                NeoForge.EVENT_BUS.register(DreamSpike.class);
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("dream")
                        .requires(source -> source.hasPermission(GAMEMASTER))
                        .then(Commands.literal("end").executes(context -> end(context.getSource())))
                        .then(Commands.literal("start").executes(context -> start(context.getSource())))));
    }

    private static int start(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer toucher = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        if (dream != null) {
            say(server, "dream failed: a memory is already dreamt in " + dream.key.location());
            return 0;
        }
        ServerLevel real = toucher.serverLevel();
        BlockPos stone = BlockPos.betweenClosedStream(toucher.blockPosition().offset(-SEEK, -SEEK, -SEEK), toucher.blockPosition().offset(SEEK, SEEK, SEEK))
                .filter(pos -> BuiltInRegistries.BLOCK.getKey(real.getBlockState(pos).getBlock()).equals(PEDESTAL)).map(BlockPos::immutable)
                .min(Comparator.comparingDouble(pos -> pos.distSqr(toucher.blockPosition()))).orElse(toucher.blockPosition());
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Wayfarer.id("dream_" + Long.toHexString(System.currentTimeMillis())));
        LevelStem stem = new LevelStem(real.dimensionTypeRegistration(), real.getChunkSource().getGenerator());
        long started = System.nanoTime();
        ServerLevel level = InfiniverseAPI.get().getOrCreateLevel(server, key, () -> stem);
        dream = new Dream(key, level, real, stone, toucher.position(), toucher.getYRot());
        for (ServerPlayer player : real.players()) {
            if (player.blockPosition().distSqr(stone) <= TOGETHER * TOGETHER) {
                dream.dreamers.put(player.getUUID(), new Return(player.position(), player.getYRot(), player.getXRot()));
            }
        }
        dream.started = started;
        level.getChunkSource().addRegionTicket(TICKET, new ChunkPos(stone), CHUNKS, new ChunkPos(stone));
        real.getChunkSource().addRegionTicket(TICKET, new ChunkPos(stone), CHUNKS, new ChunkPos(stone));
        say(server, "dream: %s touches the stone at %d %d %d; %d dream it together in %s, made in %d ms".formatted(toucher.getScoreboardName(),
                stone.getX(), stone.getY(), stone.getZ(), dream.dreamers.size(), key.location(), (System.nanoTime() - started) / 1_000_000));
        return 1;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (ResourceKey<Level> key : List.copyOf(ended)) {
            if (server.getLevel(key) == null) {
                ended.remove(key);
                Path folder = server.getWorldPath(LevelResource.ROOT).resolve("dimensions").resolve(key.location().getNamespace()).resolve(key.location().getPath());
                say(server, "dream: %s is gone, %d KB of it deleted".formatted(key.location(), delete(folder) / 1024));
            }
        }
        if (dream == null || dream.ready) {
            return;
        }
        ChunkPos centre = new ChunkPos(dream.stone);
        for (int x = centre.x - CHUNKS; x <= centre.x + CHUNKS; x++) {
            for (int z = centre.z - CHUNKS; z <= centre.z + CHUNKS; z++) {
                if (dream.level.getChunkSource().getChunkNow(x, z) == null || dream.real.getChunkSource().getChunkNow(x, z) == null) {
                    return;
                }
            }
        }
        dream.ready = true;
        long generated = (System.nanoTime() - dream.started) / 1_000_000;
        say(server, "dream: " + compare(dream));
        int renewed = renew(dream);
        int people = people(dream);
        for (Map.Entry<UUID, Return> entry : dream.dreamers.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                Return from = entry.getValue();
                player.teleportTo(dream.level, from.pos.x, from.pos.y, from.pos.z, from.yRot, from.xRot);
            }
        }
        say(server, "dream: twenty winters ago; %d chunks generated in %d ms, %d blocks made newer, %d of the past there, %d dreamers taken in"
                .formatted((2 * CHUNKS + 1) * (2 * CHUNKS + 1), generated, renewed, people, dream.dreamers.size()));
    }

    private static String compare(Dream dream) {
        int same = 0;
        int differ = 0;
        List<String> listed = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(dream.stone.offset(-SPAN, -SPAN, -SPAN), dream.stone.offset(SPAN, SPAN, SPAN))) {
            BlockState real = dream.real.getBlockState(pos);
            BlockState dreamt = dream.level.getBlockState(pos);
            if (real.equals(dreamt)) {
                same++;
                continue;
            }
            differ++;
            if (listed.size() < LISTED) {
                listed.add("%s %d %d %d is %s in the dream".formatted(name(real), pos.getX(), pos.getY(), pos.getZ(), name(dreamt)));
            }
        }
        return "the dream matches the real place in %d of %d blocks around the stone%s".formatted(same, same + differ,
                listed.isEmpty() ? "" : "; where they differ: " + String.join(", ", listed));
    }

    private static int renew(Dream dream) {
        int renewed = 0;
        for (BlockPos pos : BlockPos.betweenClosed(dream.stone.offset(-SPAN, -SPAN, -SPAN), dream.stone.offset(SPAN, SPAN, SPAN))) {
            Block newer = NEWER.get(dream.level.getBlockState(pos).getBlock());
            if (newer != null) {
                dream.level.setBlock(pos, newer.defaultBlockState(), Block.UPDATE_CLIENTS);
                renewed++;
            }
        }
        return renewed;
    }

    private static int people(Dream dream) {
        Entity body = EntityType.byString(BODY).map(type -> type.create(dream.level)).orElse(null);
        if (!(body instanceof EasyNPC<?> npc)) {
            return 0;
        }
        BlockPos from = BlockPos.containing(dream.start).offset(4, 0, 0);
        body.moveTo(from.getX() + 0.5, from.getY(), from.getZ() + 0.5, 0, 0);
        body.setCustomName(Component.literal("A Frost Walker"));
        if (body instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
        dream.level.addFreshEntity(body);
        SkinHandler.setSkin(npc, SkinDataEntry.createDefaultSkin("NOOR"));
        BlockPos stair = BlockPos.containing(dream.start).offset(1, 0, 1);
        MinecraftServer server = dream.level.getServer();
        EasyNPCActionHandler.moveTo(npc, stair, MoveActionData.DEFAULT, () -> {
            ModelPoseAPI.setPose(npc, KNEELING);
            say(server, "dream: A Frost Walker reached the stair and kneels");
        });
        return 1;
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (dream == null || !(event.getEntity() instanceof ServerPlayer player) || player.level() != dream.level) {
            return;
        }
        event.setCanceled(true);
        player.setHealth(player.getMaxHealth());
        player.clearFire();
        player.removeAllEffects();
        Return from = dream.dreamers.getOrDefault(player.getUUID(), new Return(dream.start, dream.yRot, 0));
        player.teleportTo(dream.level, from.pos.x, from.pos.y, from.pos.z, from.yRot, from.xRot);
        say(player.server, "dream: %s falls, and wakes into the memory again at its beginning".formatted(player.getScoreboardName()));
    }

    private static int end(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        if (dream == null) {
            say(server, "dream failed: nobody is dreaming");
            return 0;
        }
        int woken = 0;
        for (Map.Entry<UUID, Return> entry : dream.dreamers.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                Return to = entry.getValue();
                player.teleportTo(dream.real, to.pos.x, to.pos.y, to.pos.z, to.yRot, to.xRot);
                woken++;
            }
        }
        dream.real.getChunkSource().removeRegionTicket(TICKET, new ChunkPos(dream.stone), CHUNKS, new ChunkPos(dream.stone));
        dream.level.getChunkSource().removeRegionTicket(TICKET, new ChunkPos(dream.stone), CHUNKS, new ChunkPos(dream.stone));
        InfiniverseAPI.get().markDimensionForUnregistration(server, dream.key);
        ended.add(dream.key);
        say(server, "dream: %d woke where they stood; %s is let go".formatted(woken, dream.key.location()));
        dream = null;
        return 1;
    }

    private static long delete(Path folder) {
        if (!Files.exists(folder)) {
            return 0;
        }
        try (Stream<Path> paths = Files.walk(folder)) {
            long size = 0;
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                size += Files.isRegularFile(path) ? Files.size(path) : 0;
                Files.delete(path);
            }
            return size;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String name(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
    }

    private static void say(MinecraftServer server, String text) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(text), false);
    }

    private record Return(Vec3 pos, float yRot, float xRot) {}

    private static final class Dream {
        private final ResourceKey<Level> key;
        private final ServerLevel level;
        private final ServerLevel real;
        private final BlockPos stone;
        private final Vec3 start;
        private final float yRot;
        private final Map<UUID, Return> dreamers = new HashMap<>();
        private boolean ready;
        private long started;

        private Dream(ResourceKey<Level> key, ServerLevel level, ServerLevel real, BlockPos stone, Vec3 start, float yRot) {
            this.key = key;
            this.level = level;
            this.real = real;
            this.stone = stone;
            this.start = start;
            this.yRot = yRot;
        }
    }
}

package io.github.roman16.wayfarer;

import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.story.Prologue;
import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

public final class WorldSetup {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ID = "id";
    private static final String NAME = "wayfarer_world";
    private static final String SEASON_CYCLE = "doSeasonCycle";

    private WorldSetup() {}

    private static final class Done extends SavedData {
        boolean done;
        String id = UUID.randomUUID().toString();

        static Done load(CompoundTag tag, HolderLookup.Provider registries) {
            Done state = new Done();
            state.done = tag.getBoolean("done");
            state.id = tag.getString(ID);
            return state;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putBoolean("done", done);
            tag.putString(ID, id);
            return tag;
        }
    }

    public record WorldId(String id) implements CustomPacketPayload {
        public static final Type<WorldId> TYPE = new Type<>(Wayfarer.id("world_id"));
        public static final StreamCodec<ByteBuf, WorldId> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(WorldId::new, WorldId::id);

        @Override
        public Type<WorldId> type() {
            return TYPE;
        }
    }

    public record Telling(ServerConfigurationPacketListener listener) implements ICustomConfigurationTask {
        private static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type(Wayfarer.id("world_id"));

        @Override
        public void run(Consumer<CustomPacketPayload> sender) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                sender.accept(new WorldId(state(server).id));
            }
            listener.finishCurrentTask(TYPE);
        }

        @Override
        public ConfigurationTask.Type type() {
            return TYPE;
        }
    }

    public static String id(Path world) {
        try {
            CompoundTag tag = NbtIo.readCompressed(world.resolve("data").resolve(NAME + ".dat"), NbtAccounter.unlimitedHeap());
            return tag.getCompound("data").getString(ID);
        } catch (IOException exception) {
            return "";
        }
    }

    private static Done state(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Done::new, Done::load, null), NAME);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        Done state = state(server);
        if (state.done) {
            return;
        }
        server.getGameRules().getRule(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE).set(Balance.get().sleep().playersPercentage(), server);
        server.getGameRules().getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, server);
        stopSeasons(server);
        placeLastCamp(server.overworld());
        state.done = true;
        state.setDirty();
    }

    private static void stopSeasons(MinecraftServer server) {
        GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
            @Override
            public void visitBoolean(GameRules.Key<GameRules.BooleanValue> key, GameRules.Type<GameRules.BooleanValue> type) {
                if (key.getId().equals(SEASON_CYCLE)) {
                    server.getGameRules().getRule(key).set(false, server);
                }
            }
        });
    }

    private static void placeLastCamp(ServerLevel level) {
        BlockPos spawn = level.getSharedSpawnPos();
        if (snowy(level, spawn.getX(), spawn.getZ())) {
            Optional<BlockPos> warmer = warmer(level, spawn);
            warmer.ifPresentOrElse(found -> level.setDefaultSpawnPos(found, 0),
                    () -> LOGGER.warn("no ground without snow lies within {} blocks of the spawn; the Last Camp stands in the snow", Balance.get().prologue().campSearchBlocks()));
        }
        Prologue.lastCamp(level, Camps.place(level, Wayfarer.id("last_camp"), level.getSharedSpawnPos()));
    }

    private static Optional<BlockPos> warmer(ServerLevel level, BlockPos spawn) {
        Balance.Prologue prologue = Balance.get().prologue();
        for (int reach = prologue.campStepBlocks(); reach <= prologue.campSearchBlocks(); reach += prologue.campStepBlocks()) {
            int around = Math.max(8, (int) Math.round(Math.PI * 2 * reach / prologue.campStepBlocks()));
            for (int step = 0; step < around; step++) {
                double angle = Math.PI * 2 * step / around;
                int x = spawn.getX() + (int) Math.round(Math.cos(angle) * reach);
                int z = spawn.getZ() + (int) Math.round(Math.sin(angle) * reach);
                if (land(level, x, z) && !snowy(level, x, z)) {
                    return Optional.of(new BlockPos(x, ground(level, x, z), z));
                }
            }
        }
        return Optional.empty();
    }

    private static boolean snowy(ServerLevel level, int x, int z) {
        BlockPos at = new BlockPos(x, ground(level, x, z), z);
        return biome(level, at).value().coldEnoughToSnow(at);
    }

    private static boolean land(ServerLevel level, int x, int z) {
        BlockPos at = new BlockPos(x, ground(level, x, z), z);
        Holder<Biome> biome = biome(level, at);
        return at.getY() > level.getChunkSource().getGenerator().getSeaLevel() && !biome.is(BiomeTags.IS_RIVER) && !biome.is(BiomeTags.IS_OCEAN);
    }

    private static Holder<Biome> biome(ServerLevel level, BlockPos at) {
        return level.getUncachedNoiseBiome(QuartPos.fromBlock(at.getX()), QuartPos.fromBlock(at.getY()), QuartPos.fromBlock(at.getZ()));
    }

    private static int ground(ServerLevel level, int x, int z) {
        return level.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, level.getChunkSource().randomState());
    }
}

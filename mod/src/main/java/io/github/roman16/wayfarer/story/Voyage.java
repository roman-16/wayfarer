package io.github.roman16.wayfarer.story;

import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Clock;
import io.github.roman16.wayfarer.Nights;
import io.github.roman16.wayfarer.Sea;
import io.github.roman16.wayfarer.Waves;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

public final class Voyage {
    static final String FAR_SHORE = "far_shore";
    private static final String NIGHTFALL = "21:00";
    private static final String DUSKED = "dusked";
    private static final String STARTED = "voyage_started";
    private static final String ENDED = "voyage_ended";
    private static final String BOAT = "voyage_boat";
    private static final String BEAST = "voyage_beast";
    private static final String WAVE = "voyage_wave";
    private static final String WAVE_AT = "voyage_wave_at";
    private static final String CALM_AT = "voyage_calm_at";
    private static final String ALONE_SINCE = "voyage_alone_since";
    private static final String LISTED = "voyage_listed";
    private static final String DROWNED = "wayfarer_voyage";
    private static final String HELD = "held";
    private static final String SUNK = "sunk";
    private static final ResourceLocation COG = ResourceLocation.fromNamespaceAndPath("smallships", "cog");
    private static final String[] ABOARD = {"maud", "pip"};
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MARCH = 64;
    private static final int CELL = 8;
    private static final int OFFSHORE = 512;
    private static final int TURN_STEP = 8;
    private static final int WIDEST_TURN = 32;
    private static final int SPAWN_NEAR = 6;
    private static final int SPAWN_FAR = 12;
    private static final int SPAWN_DEPTH = 3;
    private static final int SETTLE_TICKS = 20;
    private static final String SETTLED = "settled";
    private static final int SINKING = 2;
    private static final double STATION_PULL = 0.25;
    private static final int TICKS_PER_SECOND = 20;
    @Nullable
    private static CompletableFuture<BlockPos> searching;
    @Nullable
    private static UUID station;

    private Voyage() {}

    static void find(MinecraftServer server) {
        Progress progress = Progress.of(server);
        if (searching != null || progress.place(FAR_SHORE).isPresent()) {
            return;
        }
        ServerLevel level = Engine.overworld(server);
        BlockPos from = progress.place(Shipyard.NAME).or(() -> progress.place(Caravan.NAME)).orElseGet(level::getSharedSpawnPos);
        Vec3 flat = new Vec3(from.getX(), 0, from.getZ());
        Vec3 out = flat.lengthSqr() < 1 ? Caravan.way(server) : flat.normalize();
        Unseen.Heights heights = new Unseen.Heights(level);
        int sea = level.getChunkSource().getGenerator().getSeaLevel();
        Balance.World world = Balance.get().world();
        int beyond = world.homeBlocks() + world.seaBlocks() - world.shoreBlocks();
        int farthest = world.homeBlocks() + world.seaBlocks() + world.coastBlocks();
        searching = CompletableFuture.supplyAsync(() -> landfall(heights, from, out, sea, beyond, farthest), Util.backgroundExecutor());
    }

    static void reset() {
        searching = null;
        station = null;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % SETTLE_TICKS == 0 && Engine.running(event.getServer())) {
            settle(event.getServer());
        }
        if (station == null) {
            return;
        }
        ServerLevel level = Engine.overworld(event.getServer());
        Entity boat = level.getEntity(station);
        Optional<ServerPlayer> sailor = sailor(event.getServer(), level);
        if (boat == null || sailor.isEmpty() || !(sailor.get().getVehicle() instanceof Entity ship) || ship == boat) {
            return;
        }
        Vec3 side = Vec3.directionFromRotation(0, ship.getYRot() + 90).scale(Balance.get().voyage().besideBlocks());
        Vec3 want = ship.position().add(side.x, 0, side.z);
        Vec3 off = new Vec3(want.x - boat.getX(), 0, want.z - boat.getZ());
        if (off.lengthSqr() > 1) {
            boat.setPos(boat.getX() + off.x * STATION_PULL, boat.getY(), boat.getZ() + off.z * STATION_PULL);
            boat.setYRot(ship.getYRot());
        }
    }

    static void tick(MinecraftServer server) {
        if (searching == null || !searching.isDone()) {
            return;
        }
        try {
            BlockPos shore = searching.join();
            Progress.of(server).place(FAR_SHORE, shore);
            Progress.of(server).setDirty();
            LOGGER.info("the far shore lies at {}", shore);
            Journal.syncAll(server);
        } catch (CompletionException failure) {
            LOGGER.error("the far shore's search failed: {}", failure.getCause().toString());
        }
        searching = null;
    }

    private static void settle(MinecraftServer server) {
        Progress progress = Progress.of(server);
        Optional<BlockPos> shore = progress.place(FAR_SHORE);
        if (shore.isEmpty() || progress.scratch(FAR_SHORE).getBoolean(SETTLED)) {
            return;
        }
        ServerLevel level = Engine.overworld(server);
        int reach = Balance.get().voyage().landfallBlocks();
        if (Engine.players(server).stream().noneMatch(player -> player.level() == level && player.blockPosition().closerThan(shore.get().atY(player.getBlockY()), reach))
                || !level.isLoaded(shore.get())) {
            return;
        }
        BlockPos best = null;
        for (int dx = -reach; dx <= reach; dx += CELL) {
            for (int dz = -reach; dz <= reach; dz += CELL) {
                BlockPos column = shore.get().offset(dx, 0, dz);
                if (!level.isLoaded(column) || best != null && column.distSqr(shore.get()) >= best.atY(shore.get().getY()).distSqr(shore.get())) {
                    continue;
                }
                BlockPos ground = Ground.above(level, column);
                if (level.getFluidState(ground.below()).isEmpty() && level.getFluidState(ground).isEmpty() && !level.getBlockState(ground.below()).is(BlockTags.LEAVES)
                        && ground.getY() >= level.getChunkSource().getGenerator().getSeaLevel()) {
                    best = ground;
                }
            }
        }
        if (best == null) {
            return;
        }
        progress.place(FAR_SHORE, best);
        progress.scratch(FAR_SHORE).putBoolean(SETTLED, true);
        progress.setDirty();
        Journal.syncAll(server);
        LOGGER.info("the far shore settles on dry land at {}", best);
    }

    private static BlockPos landfall(Unseen.Heights heights, BlockPos from, Vec3 out, int sea, int beyond, int farthest) {
        Vec3 flat = Vec3.atLowerCornerOf(from).multiply(1, 0, 1);
        BlockPos nearest = null;
        for (int turned = -WIDEST_TURN; turned <= WIDEST_TURN; turned += TURN_STEP) {
            Vec3 way = out.yRot((float) Math.toRadians(turned));
            for (int distance = OFFSHORE; flat.add(way.scale(distance)).length() <= farthest; distance += MARCH) {
                Vec3 point = flat.add(way.scale(distance));
                BlockPos at = BlockPos.containing(point);
                if (point.length() < beyond || heights.biome(at.getX(), sea, at.getZ()).is(BiomeTags.IS_OCEAN)) {
                    continue;
                }
                BlockPos land = at;
                for (int back = MARCH; back >= 0; back -= CELL) {
                    BlockPos before = BlockPos.containing(flat.add(way.scale(distance - back)));
                    if (!heights.wet(before.getX(), before.getZ())) {
                        land = before;
                        break;
                    }
                }
                if (nearest == null || land.distSqr(from) < nearest.distSqr(from)) {
                    nearest = land;
                }
                break;
            }
        }
        if (nearest != null) {
            return nearest.atY(heights.surface(nearest.getX(), nearest.getZ()));
        }
        BlockPos far = BlockPos.containing(flat.add(out.scale(beyond - flat.length())));
        return far.atY(sea);
    }

    static void sail(MinecraftServer server, Engine.Active active) {
        ServerLevel level = Engine.overworld(server);
        Optional<ServerPlayer> sailor = sailor(server, level);
        if (sailor.isEmpty()) {
            return;
        }
        if (!Nights.night(level)) {
            if (!active.scratch().getBoolean(DUSKED)) {
                active.scratch().putBoolean(DUSKED, true);
                Progress.of(server).setDirty();
                Clock.pass(level, NIGHTFALL, Balance.get().clock().afternoonSeconds(), null);
            }
            return;
        }
        Engine.complete(server, active);
    }

    private static Optional<ServerPlayer> sailor(MinecraftServer server, ServerLevel level) {
        return Engine.players(server).stream().filter(player -> player.level() == level && player.getVehicle() instanceof Boat
                && Sea.open(level, player.blockPosition())).findFirst();
    }

    static void tick(MinecraftServer server, Engine.Active active) {
        ServerLevel level = Engine.overworld(server);
        CompoundTag scratch = active.scratch();
        Balance.Voyage voyage = Balance.get().voyage();
        long now = level.getGameTime();
        if (scratch.getBoolean(ENDED)) {
            return;
        }
        if (!scratch.getBoolean(STARTED)) {
            sailor(server, level).ifPresent(player -> start(server, active, level, player));
            return;
        }
        Entity boat = scratch.hasUUID(BOAT) ? level.getEntity(scratch.getUUID(BOAT)) : null;
        if (boat != null && (!boat.isAlive() || boat.getY() < level.getChunkSource().getGenerator().getSeaLevel() - SINKING)) {
            end(server, active, level, SUNK);
            return;
        }
        boolean defended = boat != null && Engine.players(server).stream().anyMatch(player -> player.level() == level && player.isAlive()
                && player.distanceToSqr(boat) <= (double) voyage.reachBlocks() * voyage.reachBlocks());
        if (!defended) {
            long alone = scratch.contains(ALONE_SINCE) ? scratch.getLong(ALONE_SINCE) : now;
            scratch.putLong(ALONE_SINCE, alone);
            if (now - alone >= (long) voyage.leftSeconds() * TICKS_PER_SECOND) {
                end(server, active, level, SUNK);
            }
            return;
        }
        scratch.remove(ALONE_SINCE);
        if (boat instanceof Boat hull && hull.getDamage() >= voyage.listingDamage() && !scratch.getBoolean(LISTED)) {
            scratch.putBoolean(LISTED, true);
            Talk.notice(Component.literal(active.step().text("listing")).withStyle(ChatFormatting.DARK_RED));
        }
        int wave = scratch.getInt(WAVE);
        List<Drowned> left = drowned(level, boat, voyage);
        if (!scratch.contains(WAVE_AT)) {
            if (wave >= voyage.waves()) {
                end(server, active, level, HELD);
                return;
            }
            if (now - scratch.getLong(CALM_AT) < (long) voyage.calmSeconds() * TICKS_PER_SECOND || Talk.busy()) {
                return;
            }
            scratch.putLong(WAVE_AT, now);
            Talk.notice(Component.literal(active.step().text("wave").replace("{wave}", String.valueOf(wave + 1))
                    .replace("{waves}", String.valueOf(voyage.waves()))).withStyle(ChatFormatting.DARK_RED));
            rise(server, level, boat, wave, voyage);
            Progress.of(server).setDirty();
            return;
        }
        if (left.isEmpty() || now - scratch.getLong(WAVE_AT) >= (long) voyage.waveSeconds() * TICKS_PER_SECOND) {
            scratch.remove(WAVE_AT);
            scratch.putInt(WAVE, wave + 1);
            scratch.putLong(CALM_AT, now);
            Progress.of(server).setDirty();
        }
    }

    private static void start(MinecraftServer server, Engine.Active active, ServerLevel level, ServerPlayer sailor) {
        Balance.Voyage voyage = Balance.get().voyage();
        Entity ship = sailor.getVehicle();
        Vec3 side = Vec3.directionFromRotation(0, (ship == null ? sailor : ship).getYRot() + 90).scale(voyage.besideBlocks());
        Vec3 at = (ship == null ? sailor : ship).position().add(side);
        Optional<Entity> made = EntityType.byString(COG.toString()).map(type -> type.create(level));
        if (made.isEmpty() || !(made.get() instanceof Boat boat)) {
            server.getPlayerList().broadcastSystemMessage(Component.literal("the crossing failed: Small Ships has no " + COG).withStyle(ChatFormatting.RED), false);
            return;
        }
        int sea = level.getChunkSource().getGenerator().getSeaLevel();
        boat.moveTo(at.x, sea - 0.4, at.z, (ship == null ? sailor : ship).getYRot(), 0);
        boat.setCustomName(Component.translatable("wayfarer.voyage.boat"));
        level.addFreshEntity(boat);
        for (String person : ABOARD) {
            Entity body = People.person(level, person, boat.position(), boat.getYRot(), "sitting");
            body.startRiding(boat, true);
        }
        CompoundTag scratch = active.scratch();
        scratch.putBoolean(STARTED, true);
        scratch.putUUID(BOAT, boat.getUUID());
        station = boat.getUUID();
        scratch.putLong(CALM_AT, level.getGameTime());
        Sea.rise(level, boat.position().add(side.normalize().scale(voyage.riseBlocks())), boat).ifPresent(beast -> scratch.putUUID(BEAST, beast.getUUID()));
        Progress.of(server).setDirty();
        LOGGER.info("the night on the water begins at {}, Maud's boat beside {}", boat.blockPosition(), sailor.getScoreboardName());
    }

    static void resume(MinecraftServer server, Engine.Active active) {
        CompoundTag scratch = active.scratch();
        if (!scratch.getBoolean(STARTED)) {
            return;
        }
        if (!scratch.getBoolean(ENDED) && scratch.hasUUID(BOAT)) {
            station = scratch.getUUID(BOAT);
        }
        ServerLevel level = Engine.overworld(server);
        Entity boat = scratch.hasUUID(BOAT) ? level.getEntity(scratch.getUUID(BOAT)) : null;
        Entity beast = scratch.hasUUID(BEAST) ? level.getEntity(scratch.getUUID(BEAST)) : null;
        if (boat != null && beast != null) {
            Sea.hunt(beast, boat);
        }
    }

    private static void rise(MinecraftServer server, ServerLevel level, Entity boat, int wave, Balance.Voyage voyage) {
        long team = Math.max(1, Engine.players(server).stream().filter(player -> player.level() == level).count());
        int count = Waves.size(server, wave, team);
        int sea = level.getChunkSource().getGenerator().getSeaLevel();
        for (int index = 0; index < count; index++) {
            Drowned drowned = EntityType.DROWNED.create(level);
            if (drowned == null) {
                continue;
            }
            double angle = level.random.nextDouble() * Math.PI * 2;
            double distance = SPAWN_NEAR + level.random.nextDouble() * (SPAWN_FAR - SPAWN_NEAR);
            BlockPos at = BlockPos.containing(boat.getX() + Math.cos(angle) * distance, sea - SPAWN_DEPTH, boat.getZ() + Math.sin(angle) * distance);
            if (!level.getFluidState(at).isSource()) {
                continue;
            }
            drowned.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360, 0);
            EventHooks.finalizeMobSpawn(drowned, level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
            drowned.setPersistenceRequired();
            drowned.addTag(DROWNED);
            Hold.outline(drowned, true);
            level.addFreshEntity(drowned);
        }
    }

    private static List<Drowned> drowned(ServerLevel level, Entity near, Balance.Voyage voyage) {
        return level.getEntitiesOfClass(Drowned.class, new AABB(near.blockPosition()).inflate(voyage.reachBlocks()),
                drowned -> drowned.isAlive() && drowned.getTags().contains(DROWNED));
    }

    private static void end(MinecraftServer server, Engine.Active active, ServerLevel level, String outcome) {
        CompoundTag scratch = active.scratch();
        scratch.putBoolean(ENDED, true);
        station = null;
        Balance.Voyage voyage = Balance.get().voyage();
        Entity beast = scratch.hasUUID(BEAST) ? level.getEntity(scratch.getUUID(BEAST)) : null;
        if (beast != null) {
            Sea.dive(level, beast);
        }
        Entity boat = scratch.hasUUID(BOAT) ? level.getEntity(scratch.getUUID(BOAT)) : null;
        if (boat != null) {
            for (Entity drowned : drowned(level, boat, voyage)) {
                level.sendParticles(ParticleTypes.BUBBLE, drowned.getX(), drowned.getY(), drowned.getZ(), CELL, 0.3, 0.5, 0.3, 0.05);
                Hold.outline(drowned, false);
                drowned.discard();
            }
            List.copyOf(boat.getPassengers()).forEach(passenger -> {
                passenger.stopRiding();
                passenger.discard();
            });
            level.sendParticles(ParticleTypes.CLOUD, boat.getX(), boat.getY() + 1, boat.getZ(), CELL * 4, 1.5, 0.5, 1.5, 0.02);
            boat.discard();
        }
        for (String person : ABOARD) {
            People.find(server, person).filter(body -> body.level() == level && body.getVehicle() == null && boat == null).ifPresent(Entity::discard);
        }
        Progress progress = Progress.of(server);
        progress.choices.put(active.quest().id() + "/" + active.step().id(), outcome);
        progress.setDirty();
        Talk.say(active.quest().id(), null, active.step().lines(outcome), Engine.fill(progress));
        Balance.Clock clock = Balance.get().clock();
        Clock.pass(level, Steps.DAWN, clock.dawnSeconds(), clock.road());
        LOGGER.info("the night on the water ends: {}", outcome);
        Talk.then(active.quest().id(), () -> Engine.complete(server, active));
    }
}

package io.github.roman16.wayfarer.spike;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.markusbordihn.easynpc.api.pose.ModelPoseAPI;
import de.markusbordihn.easynpc.data.objective.ObjectiveDataEntry;
import de.markusbordihn.easynpc.data.objective.ObjectiveType;
import de.markusbordihn.easynpc.data.skin.SkinDataEntry;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import de.markusbordihn.easynpc.handler.SkinHandler;
import io.github.roman16.wayfarer.Wayfarer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class CampSpike {
    private static final int[] ANGLES = {0, 15, -15, 30, -30, 45, -45, 60, -60};
    private static final int APART = 6;
    private static final int AROUND = 32;
    private static final int AWAY = 32;
    private static final String BODY = "easy_npc:humanoid";
    private static final int BUSH = 4;
    private static final JsonObject CARAVAN = JsonParser.parseString(resource("/wayfarer/spike/caravan.json")).getAsJsonObject();
    private static final int CELL = 8;
    private static final int CHECK_TICKS = 20;
    private static final int CHUNKS = 2;
    private static final int CROWDED = 2;
    private static final Set<Block> DANGERS = Set.of(Blocks.BEE_NEST, Blocks.BEEHIVE, Blocks.CACTUS, Blocks.CALIBRATED_SCULK_SENSOR,
            Blocks.FIRE, Blocks.MAGMA_BLOCK, Blocks.POINTED_DRIPSTONE, Blocks.POWDER_SNOW, Blocks.SCULK_SENSOR, Blocks.SCULK_SHRIEKER, Blocks.SOUL_FIRE,
            Blocks.SPAWNER, Blocks.SWEET_BERRY_BUSH, Blocks.TNT, Blocks.TRIAL_SPAWNER, Blocks.WITHER_ROSE);
    private static final int EMBERS = 2;
    private static final int EXPOSED = 8;
    private static final int FARTHER = 16;
    private static final int FIRE_SHIFT = 8;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final int FOUNDATION = 4;
    private static final int GAMEMASTER = 2;
    private static final int GAP = 2;
    private static final int HERE_BLOCKS = 24;
    private static final int HOLLOW = 8;
    private static final int LOW = 4;
    private static final int NEAR_WATER = 8;
    private static final String NO_ROOM = " (no room)";
    private static final int[] NUDGES = {0, 15, -15, 30, -30, 45, -45, 60, -60, 90, -90, 120, -120, 150, -150, 180};
    private static final int REACH = 8;
    private static final int REACH_OUT = 16;
    private static final int REJECTED = 64;
    private static final int RING = 12;
    private static final int SAFE = 16;
    private static final int SAFE_ABOVE = 8;
    private static final int SAFE_BELOW = 4;
    private static final int SEARCH_SPAN = 128;
    private static final int SEARCH_STEP = 32;
    private static final int SIGHT_NEAR = 4;
    private static final int SIGHT_OWN = 16;
    private static final int SIGHT_STEP = 8;
    private static final ResourceLocation SITTING = ResourceLocation.fromNamespaceAndPath("easy_npc", "pose/humanoid/sitting");
    private static final int STEEP = 4;
    private static final int SURVEY = 16;
    private static final int TALLEST = 8;
    private static final TicketType<ChunkPos> TICKET = TicketType.create("wayfarer_camp", Comparator.comparingLong(ChunkPos::toLong));
    private static final int TRIES = 4;
    @Nullable
    private static Camp camp;

    private CampSpike() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("camp")
                        .requires(source -> source.hasPermission(GAMEMASTER))
                        .then(Commands.literal("ahead").executes(context -> ahead(context.getSource(), context.getSource().getRotation().y))
                                .then(Commands.argument("way", FloatArgumentType.floatArg(-180, 180))
                                        .executes(context -> ahead(context.getSource(), FloatArgumentType.getFloat(context, "way")))))
                        .then(Commands.literal("here").executes(context -> here(context.getSource())))
                        .then(Commands.literal("morning").executes(context -> morning(context.getSource())))
                        .then(Commands.literal("status").executes(context -> status(context.getSource())))));
    }

    private static int ahead(CommandSourceStack source, float way) throws CommandSyntaxException {
        ServerPlayer leader = source.getPlayerOrException();
        if (busy(source)) {
            return 0;
        }
        camp = new Camp(leader.serverLevel(), leader.blockPosition(), way);
        camp.stage = Stage.SEARCHING;
        camp.leader = leader.getScoreboardName();
        search(source.getServer(), camp);
        return 1;
    }

    private static void search(MinecraftServer server, Camp camp) {
        List<Viewer> viewers = viewers(camp.level);
        Viewer leader = viewers.stream().filter(viewer -> viewer.name.equals(camp.leader)).findFirst().orElse(null);
        if (leader == null) {
            return;
        }
        int view = server.getPlayerList().getViewDistance();
        if (camp.heights == null) {
            camp.heights = new Heights(camp.level);
        }
        Heights heights = camp.heights;
        List<BlockPos> rejected = List.copyOf(camp.rejected);
        camp.started = System.nanoTime();
        camp.pending = CompletableFuture.supplyAsync(() -> {
            int nearest = (view + CHUNKS + 2) * 16;
            int read = heights.read;
            Map<String, Integer> reasons = new TreeMap<>();
            List<Spot> spots = new ArrayList<>();
            for (int distance = nearest; distance <= nearest + SEARCH_SPAN; distance += SEARCH_STEP) {
                for (int angle : ANGLES) {
                    Vec3 way = Vec3.directionFromRotation(0, camp.facing + angle);
                    BlockPos centre = BlockPos.containing(leader.eye.x + way.x * distance, 0, leader.eye.z + way.z * distance);
                    String misfit = rejected.stream().anyMatch(spot -> spot.distSqr(centre.atY(spot.getY())) < REJECTED * REJECTED)
                            ? "tried" : misfit(heights, centre);
                    if (misfit != null) {
                        reasons.merge(misfit, 1, Integer::sum);
                        continue;
                    }
                    spots.add(new Spot(centre.atY(heights.surface(centre.getX(), centre.getZ())), distance,
                            score(heights, centre) - (distance - nearest) / FARTHER));
                }
            }
            spots.sort(Comparator.comparingInt(Spot::score).reversed());
            for (Spot spot : spots) {
                String blocker = viewers.stream().map(viewer -> blocker(heights, viewer, spot.centre, view)).filter(found -> found != null)
                        .findFirst().orElse(null);
                if (blocker == null) {
                    return new Found(spot.centre, "%d blocks ahead of %s, the best of %d spots (score %d); %d heights read"
                            .formatted(spot.distance, leader.name, spots.size(), spot.score, heights.read - read));
                }
                reasons.merge("in sight", 1, Integer::sum);
            }
            return new Found(null, "%s; %d heights read".formatted(reasons, heights.read - read));
        }, Util.backgroundExecutor());
    }

    private static int here(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (busy(source)) {
            return 0;
        }
        Vec3 way = Vec3.directionFromRotation(0, player.getYRot());
        BlockPos centre = BlockPos.containing(player.getX() + way.x * HERE_BLOCKS, player.getY(), player.getZ() + way.z * HERE_BLOCKS);
        camp = new Camp(player.serverLevel(), centre, player.getYRot());
        camp.settling = true;
        load(camp, "%d blocks in front of %s, in sight".formatted(HERE_BLOCKS, player.getScoreboardName()));
        return 1;
    }

    private static boolean busy(CommandSourceStack source) {
        if (camp == null) {
            return false;
        }
        say(source.getServer(), "camp failed: one is already up at %s".formatted(where(camp.centre)));
        return true;
    }

    private static int morning(CommandSourceStack source) {
        if (camp != null && camp.stage == Stage.SEARCHING) {
            camp = null;
            say(source.getServer(), "camp: stopped looking for a spot");
            return 1;
        }
        if (camp == null || camp.stage != Stage.STANDING) {
            say(source.getServer(), "camp failed: nothing to take down");
            return 0;
        }
        camp.stage = Stage.WAITING;
        camp.reason = "";
        return 1;
    }

    private static int status(CommandSourceStack source) {
        String text = camp == null ? "no camp" : "%s at %s, %d pieces, %d people, %d blocks replaced%s"
                .formatted(camp.stage.name().toLowerCase(), where(camp.centre), camp.pieces.size(), camp.people.size(), camp.originals.size(),
                        camp.reason.isEmpty() ? "" : "; " + camp.reason);
        say(source.getServer(), "camp: " + text);
        return 1;
    }

    private static void load(Camp camp, String why) {
        camp.stage = Stage.LOADING;
        camp.started = System.nanoTime();
        camp.ticket = camp.chunk();
        camp.level.getChunkSource().addRegionTicket(TICKET, camp.ticket, CHUNKS, camp.ticket);
        say(camp.level.getServer(), "camp: setting up at %s, %s".formatted(where(camp.centre), why));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (camp == null) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel level = camp.level;
        switch (camp.stage) {
            case SEARCHING -> {
                if (camp.pending == null) {
                    search(server, camp);
                } else if (camp.pending.isDone()) {
                    Found found = (Found) camp.pending.join();
                    camp.pending = null;
                    String took = "; %d ms off the server thread".formatted((System.nanoTime() - camp.started) / 1_000_000);
                    if (found.centre != null) {
                        camp.centre = found.centre;
                        load(camp, found.why + took);
                    } else if (!camp.fallbacks.isEmpty()) {
                        settle(camp);
                    } else {
                        CampSpike.camp = null;
                        say(server, "camp failed: no spot ahead is out of sight, " + found.why + took);
                    }
                }
            }
            case LOADING -> {
                if (loaded(level, camp, false)) {
                    long loading = (System.nanoTime() - camp.started) / 1_000_000;
                    long started = System.nanoTime();
                    Survey survey = survey(level, camp.centre);
                    String hard = survey.hard == null ? build(level, camp) : survey.hard;
                    List<String> wishes = new ArrayList<>(survey.wishes);
                    long cramped = camp.pieces.stream().filter(piece -> piece.endsWith(NO_ROOM)).count();
                    if (cramped > 0) {
                        wishes.add("no room for %d of %d households".formatted(cramped, camp.pieces.size()));
                    }
                    if (hard == null && (camp.settling || wishes.isEmpty())) {
                        level.getChunkSource().removeRegionTicket(TICKET, camp.ticket, CHUNKS, camp.ticket);
                        camp.stage = Stage.STANDING;
                        say(server, "camp: placed at %s%s: %s; %d paths, %d blocks replaced, %d people; chunks loaded in %d ms, built in %d ms"
                                .formatted(where(camp.centre), wishes.isEmpty() ? "" : ", short of " + String.join(" and ", wishes),
                                        String.join(", ", camp.pieces), camp.paths, camp.originals.size(), camp.people.size(), loading,
                                        (System.nanoTime() - started) / 1_000_000));
                        return;
                    }
                    takeDown(level, camp);
                    level.getChunkSource().removeRegionTicket(TICKET, camp.ticket, CHUNKS, camp.ticket);
                    String spot = "the spot at %s %s; chunks loaded in %d ms".formatted(where(camp.centre),
                            hard != null ? "is unsafe or unbuildable, " + hard : "falls short, " + String.join(" and ", wishes), loading);
                    if (camp.leader.isEmpty()) {
                        CampSpike.camp = null;
                        say(server, "camp failed: " + spot);
                        return;
                    }
                    say(server, "camp: " + spot);
                    if (hard == null) {
                        camp.fallbacks.add(new Fallback(camp.centre, wishes));
                    }
                    camp.rejected.add(camp.centre);
                    camp.originals.clear();
                    camp.people.clear();
                    camp.pieces.clear();
                    camp.paths = 0;
                    camp.boxes.clear();
                    camp.tries++;
                    if (camp.tries >= TRIES && !camp.fallbacks.isEmpty()) {
                        settle(camp);
                    } else {
                        camp.stage = Stage.SEARCHING;
                    }
                }
            }
            case WAITING -> {
                if (server.getTickCount() % CHECK_TICKS == 0) {
                    guard(level, camp);
                }
                if (camp.pending == null && server.getTickCount() % CHECK_TICKS == 0) {
                    List<Viewer> viewers = viewers(level);
                    int view = server.getPlayerList().getViewDistance();
                    Heights heights = new Heights(level);
                    BlockPos centre = camp.centre;
                    camp.pending = CompletableFuture.supplyAsync(() -> viewers.stream().map(viewer -> blocker(heights, viewer, centre, view))
                            .filter(found -> found != null).findFirst().orElse(""), Util.backgroundExecutor());
                } else if (camp.pending != null && camp.pending.isDone()) {
                    String blocker = (String) camp.pending.join();
                    camp.pending = null;
                    if (blocker.isEmpty()) {
                        camp.stage = Stage.UNLOADING;
                        camp.started = System.nanoTime();
                        camp.ticket = camp.chunk();
                        level.getChunkSource().addRegionTicket(TICKET, camp.ticket, CHUNKS, camp.ticket);
                    } else if (!blocker.equals(camp.reason)) {
                        camp.reason = blocker;
                        say(server, "camp: waiting to be taken down: " + blocker);
                    }
                }
            }
            case UNLOADING -> {
                if (loaded(level, camp, true)) {
                    long loading = (System.nanoTime() - camp.started) / 1_000_000;
                    int gone = takeDown(level, camp);
                    level.getChunkSource().removeRegionTicket(TICKET, camp.ticket, CHUNKS, camp.ticket);
                    say(server, "camp: taken down at %s, %d blocks put back, %d of %d people gone; chunks loaded in %d ms"
                            .formatted(where(camp.centre), camp.originals.size(), gone, camp.people.size(), loading));
                    CampSpike.camp = null;
                }
            }
            case STANDING -> {
                if (server.getTickCount() % CHECK_TICKS == 0) {
                    guard(level, camp);
                }
            }
        }
    }

    private static int takeDown(ServerLevel level, Camp camp) {
        int gone = 0;
        for (UUID id : camp.people) {
            Entity person = level.getEntity(id);
            if (person != null) {
                person.discard();
                gone++;
            }
        }
        List<Map.Entry<BlockPos, BlockState>> originals = new ArrayList<>(camp.originals.entrySet());
        for (int index = originals.size() - 1; index >= 0; index--) {
            level.setBlock(originals.get(index).getKey(), originals.get(index).getValue(), FLAGS);
        }
        return gone;
    }

    private static void settle(Camp camp) {
        Fallback best = camp.fallbacks.stream().min(Comparator.comparingInt(fallback -> fallback.wishes.size())).orElseThrow();
        camp.centre = best.centre;
        camp.settling = true;
        load(camp, "the best of %d spots tried, short of %s".formatted(camp.tries, String.join(" and ", best.wishes)));
    }

    private static boolean loaded(ServerLevel level, Camp camp, boolean entities) {
        for (int x = camp.ticket.x - CHUNKS; x <= camp.ticket.x + CHUNKS; x++) {
            for (int z = camp.ticket.z - CHUNKS; z <= camp.ticket.z + CHUNKS; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null || entities && !level.areEntitiesLoaded(ChunkPos.asLong(x, z))) {
                    return false;
                }
            }
        }
        return true;
    }

    @Nullable
    private static String misfit(Heights heights, BlockPos centre) {
        return heights.wet(centre.getX(), centre.getZ()) ? "in water" : null;
    }

    private static int score(Heights heights, BlockPos centre) {
        int ground = heights.surface(centre.getX(), centre.getZ());
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        int total = 0;
        boolean water = false;
        List<BlockPos> around = ring(centre, AROUND);
        around.removeFirst();
        for (BlockPos point : around) {
            int y = heights.surface(point.getX(), point.getZ());
            low = Math.min(low, y);
            high = Math.max(high, y);
            total += y;
            water |= heights.wet(point.getX(), point.getZ());
        }
        int score = water ? NEAR_WATER : 0;
        if (ground * around.size() < total - LOW * around.size()) {
            score -= HOLLOW;
        }
        if (ground > high + LOW) {
            score -= EXPOSED;
        }
        List<BlockPos> camp = ring(centre, RING);
        int flatLow = camp.stream().mapToInt(point -> heights.surface(point.getX(), point.getZ())).min().orElse(ground);
        int flatHigh = camp.stream().mapToInt(point -> heights.surface(point.getX(), point.getZ())).max().orElse(ground);
        long soaked = camp.stream().filter(point -> heights.wet(point.getX(), point.getZ())).count();
        return score - (flatHigh - flatLow) - (int) soaked * NEAR_WATER;
    }

    private static List<BlockPos> ring(BlockPos centre, int radius) {
        List<BlockPos> points = new ArrayList<>(List.of(centre));
        for (int step = 0; step < 8; step++) {
            double angle = Math.toRadians(step * 45);
            points.add(centre.offset((int) Math.round(Math.cos(angle) * radius), 0, (int) Math.round(Math.sin(angle) * radius)));
        }
        return points;
    }

    private static List<Viewer> viewers(ServerLevel level) {
        return level.players().stream().map(player -> new Viewer(player.getScoreboardName(), player.getEyePosition(),
                Vec3.directionFromRotation(0, player.getYRot()), player.chunkPosition())).toList();
    }

    @Nullable
    private static String blocker(Heights heights, Viewer viewer, BlockPos centre, int view) {
        ChunkPos camp = new ChunkPos(centre);
        int away = (int) Math.sqrt(viewer.eye.distanceToSqr(centre.getX() + 0.5, viewer.eye.y, centre.getZ() + 0.5));
        for (int x = camp.x - CHUNKS; x <= camp.x + CHUNKS; x++) {
            for (int z = camp.z - CHUNKS; z <= camp.z + CHUNKS; z++) {
                if (ChunkTrackingView.isWithinDistance(viewer.chunk.x, viewer.chunk.z, view, x, z, true)) {
                    return "%s is within view distance, %d blocks away".formatted(viewer.name, away);
                }
            }
        }
        Vec3 towards = new Vec3(centre.getX() + 0.5 - viewer.eye.x, 0, centre.getZ() + 0.5 - viewer.eye.z).normalize();
        if (towards.dot(viewer.look) < 0) {
            return null;
        }
        Vec3 side = new Vec3(-towards.z, 0, towards.x).scale(RING);
        for (Vec3 offset : List.of(Vec3.ZERO, side, side.reverse())) {
            Vec3 point = Vec3.atBottomCenterOf(centre).add(offset);
            Vec3 target = new Vec3(point.x, heights.surface(Mth.floor(point.x), Mth.floor(point.z)) + TALLEST, point.z);
            if (!behindLand(heights, viewer.eye, target)) {
                return "%s faces it and could see it over the land, %d blocks away".formatted(viewer.name, away);
            }
        }
        return null;
    }

    private static boolean behindLand(Heights heights, Vec3 eye, Vec3 target) {
        Vec3 line = target.subtract(eye);
        double length = line.length();
        Vec3 step = line.scale(1 / length);
        for (double travelled = SIGHT_NEAR; travelled < length - SIGHT_OWN; travelled += SIGHT_STEP) {
            Vec3 point = eye.add(step.scale(travelled));
            if (heights.surface(Mth.floor(point.x), Mth.floor(point.z)) > point.y) {
                return true;
            }
        }
        return false;
    }

    private static Survey survey(ServerLevel level, BlockPos centre) {
        String danger = danger(level, centre);
        if (danger != null) {
            return new Survey(danger, List.of());
        }
        String inside = neighbour(level, centre, REACH_OUT);
        if (inside != null) {
            return new Survey("%s in the way".formatted(inside), List.of());
        }
        List<String> wishes = new ArrayList<>();
        String neighbour = neighbour(level, centre, REACH_OUT + AWAY);
        if (neighbour != null) {
            wishes.add("%s within %d blocks".formatted(neighbour, AWAY));
        }
        int columns = 0;
        int trees = 0;
        int water = 0;
        for (int x = -SURVEY; x <= SURVEY; x++) {
            for (int z = -SURVEY; z <= SURVEY; z++) {
                if (x * x + z * z > SURVEY * SURVEY) {
                    continue;
                }
                columns++;
                int top = ground(level, centre.getX() + x, centre.getZ() + z);
                if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, centre.getX() + x, centre.getZ() + z) - 1 - top > BUSH) {
                    trees++;
                } else if (!level.getBlockState(new BlockPos(centre.getX() + x, top, centre.getZ() + z)).getFluidState().isEmpty()) {
                    water++;
                }
            }
        }
        if (trees * CROWDED > columns) {
            wishes.add("%d%% under trees".formatted(100 * trees / columns));
        }
        if (water * CROWDED > columns) {
            wishes.add("%d%% under water".formatted(100 * water / columns));
        }
        return new Survey(null, wishes);
    }

    @Nullable
    private static String danger(ServerLevel level, BlockPos centre) {
        int reach = REACH_OUT + SAFE;
        for (int x = -reach; x <= reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                if (x * x + z * z > reach * reach) {
                    continue;
                }
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, centre.getX() + x, centre.getZ() + z) - 1;
                for (int y = top - SAFE_BELOW; y <= top + SAFE_ABOVE; y++) {
                    BlockPos pos = new BlockPos(centre.getX() + x, y, centre.getZ() + z);
                    BlockState state = level.getBlockState(pos);
                    if (state.getFluidState().is(FluidTags.LAVA) || DANGERS.contains(state.getBlock())) {
                        return "%s at %s".formatted(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath(), where(pos));
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    private static String neighbour(ServerLevel level, BlockPos centre, int reach) {
        BoundingBox around = new BoundingBox(centre.getX() - reach, level.getMinBuildHeight(), centre.getZ() - reach, centre.getX() + reach,
                level.getMaxBuildHeight(), centre.getZ() + reach);
        for (int x = (centre.getX() - reach) >> 4; x <= (centre.getX() + reach) >> 4; x++) {
            for (int z = (centre.getZ() - reach) >> 4; z <= (centre.getZ() + reach) >> 4; z++) {
                for (StructureStart start : level.structureManager().startsForStructure(new ChunkPos(x, z), structure -> true)) {
                    BoundingBox box = start.getBoundingBox();
                    if (box.intersects(around) && box.maxY() >= centre.getY() - SAFE_BELOW) {
                        return String.valueOf(level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(start.getStructure()));
                    }
                }
            }
        }
        return null;
    }

    private static void guard(ServerLevel level, Camp camp) {
        if (!level.isLoaded(camp.centre)) {
            return;
        }
        int put = 0;
        for (BoundingBox box : camp.boxes) {
            for (BlockPos pos : BlockPos.betweenClosed(box.minX() - EMBERS, box.minY() - EMBERS, box.minZ() - EMBERS, box.maxX() + EMBERS,
                    box.maxY() + EMBERS, box.maxZ() + EMBERS)) {
                if (level.getBlockState(pos).is(BlockTags.FIRE)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                    put++;
                }
            }
        }
        if (put > 0) {
            say(level.getServer(), "camp: put out %d fire at the camp".formatted(put));
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (camp != null && !camp.boxes.isEmpty() && event.getEntity() instanceof LightningBolt bolt && event.getLevel() == camp.level
                && bolt.blockPosition().distSqr(camp.centre.atY(bolt.getBlockY())) < (REACH_OUT + SAFE) * (REACH_OUT + SAFE)) {
            event.setCanceled(true);
            say(camp.level.getServer(), "camp: a lightning bolt at %s did not strike the camp".formatted(where(bolt.blockPosition())));
        }
    }

    @Nullable
    private static String build(ServerLevel level, Camp camp) {
        List<BoundingBox> taken = new ArrayList<>();
        List<BlockPos> shifts = new ArrayList<>();
        for (int dx = -FIRE_SHIFT; dx <= FIRE_SHIFT; dx += FIRE_SHIFT / 2) {
            for (int dz = -FIRE_SHIFT; dz <= FIRE_SHIFT; dz += FIRE_SHIFT / 2) {
                shifts.add(camp.centre.offset(dx, 0, dz));
            }
        }
        shifts.sort(Comparator.comparingDouble(spot -> spot.distSqr(camp.centre)));
        Placed fire = null;
        for (BlockPos spot : shifts) {
            fire = place(level, camp, "fire", spot, Rotation.NONE, taken);
            if (fire != null) {
                break;
            }
        }
        if (fire == null) {
            return "no room for the fire";
        }
        camp.centre = fire.centre;
        camp.boxes = taken;
        List<BlockPos> seats = new ArrayList<>();
        BlockPos.betweenClosedStream(fire.box).filter(pos -> level.getBlockState(pos).is(Blocks.STRIPPED_SPRUCE_LOG))
                .forEach(pos -> seats.add(pos.immutable()));
        List<JsonElement> households = CARAVAN.getAsJsonArray("households").asList();
        long together = households.stream().filter(household -> !apart(household)).count();
        int slot = 0;
        for (JsonElement entry : households) {
            JsonObject household = entry.getAsJsonObject();
            String piece = household.get("piece").getAsString();
            StructureTemplate template = level.getStructureManager().get(ResourceLocation.fromNamespaceAndPath(Wayfarer.MODID, "caravan/" + piece)).orElseThrow();
            double angle = camp.facing + 180 + (apart(household) ? 180 / (double) together : 360.0 * slot++ / together);
            int radius = 4 + Math.max(template.getSize().getX(), template.getSize().getZ()) / 2 + GAP + (apart(household) ? APART : 0);
            Placed placed = null;
            for (int nudge : NUDGES) {
                for (int extra = 0; extra <= REACH && placed == null; extra += 2) {
                    Vec3 way = Vec3.directionFromRotation(0, (float) (angle + nudge));
                    BlockPos at = camp.centre.offset((int) Math.round(way.x * (radius + extra)), 0, (int) Math.round(way.z * (radius + extra)));
                    placed = place(level, camp, piece, at, facing(camp.centre, at), taken);
                }
                if (placed != null) {
                    break;
                }
            }
            if (placed == null) {
                camp.pieces.add(piece + NO_ROOM);
                continue;
            }
            camp.pieces.add(piece);
            path(level, camp, placed, taken);
            for (JsonElement person : household.getAsJsonArray("people")) {
                JsonObject who = person.getAsJsonObject();
                boolean sits = who.get("at").getAsString().equals("fire") && !seats.isEmpty();
                BlockPos spot = sits ? seats.removeFirst().above() : placed.front.above();
                spawn(level, camp, who, spot, placed.centre, sits);
            }
        }
        return null;
    }

    private static boolean apart(JsonElement household) {
        return household.getAsJsonObject().has("apart") && household.getAsJsonObject().get("apart").getAsBoolean();
    }

    private static Rotation facing(BlockPos centre, BlockPos at) {
        int dx = centre.getX() - at.getX();
        int dz = centre.getZ() - at.getZ();
        if (Math.abs(dz) >= Math.abs(dx)) {
            return dz < 0 ? Rotation.NONE : Rotation.CLOCKWISE_180;
        }
        return dx > 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90;
    }

    @Nullable
    private static Placed place(ServerLevel level, Camp camp, String piece, BlockPos at, Rotation rotation, List<BoundingBox> taken) {
        StructureTemplate template = level.getStructureManager().get(ResourceLocation.fromNamespaceAndPath(Wayfarer.MODID, "caravan/" + piece)).orElseThrow();
        BlockPos pivot = new BlockPos(template.getSize().getX() / 2, 0, template.getSize().getZ() / 2);
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setRotationPivot(pivot).setIgnoreEntities(true)
                .setKnownShape(true);
        BlockPos origin = at.subtract(pivot);
        BoundingBox footprint = template.getBoundingBox(settings, origin);
        if (taken.stream().anyMatch(box -> box.inflatedBy(1).intersects(footprint.minX(), footprint.minZ(), footprint.maxX(), footprint.maxZ()))) {
            return null;
        }
        List<Integer> tops = new ArrayList<>();
        for (int x = footprint.minX(); x <= footprint.maxX(); x++) {
            for (int z = footprint.minZ(); z <= footprint.maxZ(); z++) {
                int top = ground(level, x, z);
                BlockState ground = level.getBlockState(new BlockPos(x, top, z));
                if (!ground.getFluidState().isEmpty() || level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1 - top > BUSH) {
                    return null;
                }
                tops.add(top);
            }
        }
        tops.sort(Integer::compare);
        int y = tops.get(tops.size() / 2);
        if (tops.getLast() - y > STEEP || y - tops.getFirst() > STEEP) {
            return null;
        }
        BlockPos placedOrigin = origin.atY(y);
        BoundingBox box = template.getBoundingBox(settings, placedOrigin);
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), y - FOUNDATION, box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            remember(level, camp, pos);
        }
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), y - FOUNDATION, box.minZ(), box.maxX(), Math.max(box.maxY(), y + BUSH), box.maxZ())) {
            if (growth(level.getBlockState(pos))) {
                remember(level, camp, pos);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
            }
        }
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                terrace(level, camp, x, z, y);
            }
        }
        template.placeInWorld(level, placedOrigin, placedOrigin, settings, level.getRandom(), FLAGS);
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                foundation(level, camp, x, z, y, box.maxY());
            }
        }
        taken.add(box);
        Direction front = rotation.rotate(Direction.NORTH);
        BlockPos centre = at.atY(y);
        BlockPos door = centre.relative(front, template.getSize().getZ() / 2 + 1);
        int doorY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, door.getX(), door.getZ()) - 1;
        return new Placed(box, centre, door.atY(doorY));
    }

    private static int ground(ServerLevel level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        while (growth(level.getBlockState(new BlockPos(x, top, z)))) {
            top--;
        }
        return top;
    }

    private static boolean growth(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || !state.isAir() && state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    private static void terrace(ServerLevel level, Camp camp, int x, int z, int y) {
        int top = ground(level, x, z);
        if (top <= y) {
            return;
        }
        BlockState surface = level.getBlockState(new BlockPos(x, top, z));
        for (int height = y; height <= top; height++) {
            remember(level, camp, new BlockPos(x, height, z));
            level.setBlock(new BlockPos(x, height, z), height == y ? surface : Blocks.AIR.defaultBlockState(), FLAGS);
        }
    }

    private static void foundation(ServerLevel level, Camp camp, int x, int z, int y, int top) {
        int lowest = Integer.MIN_VALUE;
        for (int height = y; height <= top; height++) {
            BlockPos pos = new BlockPos(x, height, z);
            if (!level.getBlockState(pos).isAir() && !level.getBlockState(pos).equals(camp.originals.get(pos))) {
                lowest = height;
                break;
            }
        }
        if (lowest == Integer.MIN_VALUE) {
            return;
        }
        for (int depth = 1; depth <= FOUNDATION; depth++) {
            BlockPos below = new BlockPos(x, lowest - depth, z);
            BlockState state = level.getBlockState(below);
            if (!state.isAir() && !state.canBeReplaced() || !state.getFluidState().isEmpty()) {
                return;
            }
            remember(level, camp, below);
            level.setBlock(below, Blocks.DIRT.defaultBlockState(), FLAGS);
        }
    }

    private static void path(ServerLevel level, Camp camp, Placed placed, List<BoundingBox> taken) {
        Vec3 from = Vec3.atCenterOf(placed.front);
        Vec3 to = Vec3.atCenterOf(camp.centre);
        double length = Math.sqrt(from.distanceToSqr(to.x, from.y, to.z));
        for (double travelled = 0; travelled < length - 3; travelled += 0.5) {
            int x = Mth.floor(from.x + (to.x - from.x) * travelled / length);
            int z = Mth.floor(from.z + (to.z - from.z) * travelled / length);
            if (taken.stream().anyMatch(box -> box.isInside(x, box.minY(), z))) {
                continue;
            }
            BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
            BlockState ground = level.getBlockState(top);
            if (!ground.is(BlockTags.DIRT) || ground.is(Blocks.DIRT_PATH) || camp.originals.containsKey(top)) {
                continue;
            }
            remember(level, camp, top);
            remember(level, camp, top.above());
            if (level.getBlockState(top.above()).canBeReplaced()) {
                level.setBlock(top.above(), Blocks.AIR.defaultBlockState(), FLAGS);
            }
            level.setBlock(top, Blocks.DIRT_PATH.defaultBlockState(), FLAGS);
            camp.paths++;
        }
    }

    private static void remember(ServerLevel level, Camp camp, BlockPos pos) {
        camp.originals.putIfAbsent(pos.immutable(), level.getBlockState(pos));
    }

    private static void spawn(ServerLevel level, Camp camp, JsonObject who, BlockPos spot, BlockPos home, boolean sits) {
        Entity body = EntityType.byString(BODY).map(type -> type.create(level)).orElse(null);
        if (body == null) {
            return;
        }
        double yaw = Math.toDegrees(Math.atan2(camp.centre.getZ() - spot.getZ(), camp.centre.getX() - spot.getX())) - 90;
        body.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, (float) yaw, 0);
        body.setYHeadRot((float) yaw);
        body.setCustomName(Component.literal(who.get("name").getAsString()));
        if (body instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
        level.addFreshEntity(body);
        camp.people.add(body.getUUID());
        if (body instanceof EasyNPC<?> npc) {
            SkinHandler.setSkin(npc, SkinDataEntry.createDefaultSkin(who.get("skin").getAsString()));
            if (sits) {
                ModelPoseAPI.setPose(npc, SITTING);
            } else {
                npc.getEasyNPCNavigationData().setHomePosition(home);
                npc.getEasyNPCObjectiveData().addOrUpdateCustomObjective(new ObjectiveDataEntry(ObjectiveType.RANDOM_STROLL_AROUND_HOME));
            }
        }
    }

    private static String where(BlockPos pos) {
        return "%d %d %d".formatted(pos.getX(), pos.getY(), pos.getZ());
    }

    private static void say(MinecraftServer server, String text) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(text), false);
    }

    private static String resource(String name) {
        try (InputStream stream = CampSpike.class.getResourceAsStream(name)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private enum Stage {
        LOADING,
        SEARCHING,
        STANDING,
        UNLOADING,
        WAITING
    }

    private record Placed(BoundingBox box, BlockPos centre, BlockPos front) {}

    private record Viewer(String name, Vec3 eye, Vec3 look, ChunkPos chunk) {}

    private record Found(@Nullable BlockPos centre, String why) {}

    private record Spot(BlockPos centre, int distance, int score) {}

    private record Survey(@Nullable String hard, List<String> wishes) {}

    private record Fallback(BlockPos centre, List<String> wishes) {}

    private static final class Heights {
        private final ChunkGenerator generator;
        private final LevelHeightAccessor height;
        private final RandomState random;
        private final Map<Long, Integer> surfaces = new HashMap<>();
        private final Map<Long, Boolean> wet = new HashMap<>();
        private int read;

        private Heights(ServerLevel level) {
            this.generator = level.getChunkSource().getGenerator();
            this.height = LevelHeightAccessor.create(level.getMinBuildHeight(), level.getHeight());
            this.random = level.getChunkSource().randomState();
        }

        private int surface(int x, int z) {
            int cellX = Math.floorDiv(x, CELL) * CELL;
            int cellZ = Math.floorDiv(z, CELL) * CELL;
            return surfaces.computeIfAbsent(BlockPos.asLong(cellX, 0, cellZ), key -> {
                read++;
                return generator.getBaseHeight(cellX, cellZ, Heightmap.Types.WORLD_SURFACE_WG, height, random) - 1;
            });
        }

        private boolean wet(int x, int z) {
            int cellX = Math.floorDiv(x, CELL) * CELL;
            int cellZ = Math.floorDiv(z, CELL) * CELL;
            return wet.computeIfAbsent(BlockPos.asLong(cellX, 0, cellZ), key -> {
                read++;
                return generator.getBaseHeight(cellX, cellZ, Heightmap.Types.OCEAN_FLOOR_WG, height, random) - 1 < surface(cellX, cellZ);
            });
        }
    }

    private static final class Camp {
        private BlockPos centre;
        private final float facing;
        private final ServerLevel level;
        private final LinkedHashMap<BlockPos, BlockState> originals = new LinkedHashMap<>();
        private final List<UUID> people = new ArrayList<>();
        private final List<String> pieces = new ArrayList<>();
        private final List<BlockPos> rejected = new ArrayList<>();
        private final List<Fallback> fallbacks = new ArrayList<>();
        private boolean settling;
        private int tries;
        private List<BoundingBox> boxes = new ArrayList<>();
        private String leader = "";
        private ChunkPos ticket = ChunkPos.ZERO;
        @Nullable
        private Heights heights;
        private int paths;
        @Nullable
        private CompletableFuture<?> pending;
        private String reason = "";
        private Stage stage = Stage.LOADING;
        private long started;

        private Camp(ServerLevel level, BlockPos centre, float facing) {
            this.level = level;
            this.centre = centre;
            this.facing = facing;
        }

        private ChunkPos chunk() {
            return new ChunkPos(centre);
        }
    }
}

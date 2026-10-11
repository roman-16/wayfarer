package io.github.roman16.wayfarer.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Bundled;
import io.github.roman16.wayfarer.Camps;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.VanillaGameEvent;
import org.slf4j.Logger;

public final class Mine {
    static final String NAME = "frost_mine";
    static final String DEEP = "frost_mine_deep";
    static final String CART = "frost_mine_cart";
    private static final String SITE = "frost_mine_site";
    private static final String LOW = "frost_mine_low";
    private static final String HIGH = "frost_mine_high";
    private static final String TURN = "frost_mine_turn";
    private static final String LAID = "frost_mine_laid";
    private static final String CART_CLUE = "ore_cart";
    private static final String RUTS_CLUE = "ore_ruts";
    private static final String RUTS = "frost_mine_ruts";
    private static final String SHRIEK = "shriek";
    private static final String SHRIEKED = "shrieked";
    private static final ResourceLocation HEAD = Wayfarer.id("frost/mine_head");
    private static final ResourceLocation STAGE = Wayfarer.id("frost/mine_stage");
    private static final ResourceLocation BOTTOM = Wayfarer.id("frost/mine_deep");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Bundled<JsonObject> PLAN = Bundled.json("/wayfarer/frost.json", json -> json.getAsJsonObject("mine"));
    private static final int TURN_STEP_DEGREES = 15;
    private static final int TURN_MOST_DEGREES = 90;
    private static final int CART_OUT = 7;
    private static final int RUTS_FROM = 3;
    private static final int RUTS_SHOWN = 5;
    private static final double RUT_STEP = 1.5;
    private static final double RUT_APART = 0.55;
    private static final float RUT_WIDTH = 0.22f;
    private static final float RUT_LENGTH = 1.6f;
    private static final double BEND = 5;
    private static final double WAVE = 96;
    private static final double SINK = 0.125;
    private static final int DRIFT_LAYERS = 2;
    private static final int LOAD_OFFSET = 4;
    private static final double DRIFT = 0.25;
    private static final int SHIFT = 32;
    private static final int SHIFT_STEP = 8;
    private static final int LEVEL_ABOVE = 2;
    private static final int CLEARING = 12;

    private Mine() {}

    private record Rut(Vec3 at, Vec3 along) {}

    static void find(MinecraftServer server) {
        Progress progress = Progress.of(server);
        if (progress.place(SITE).isPresent()) {
            return;
        }
        BlockPos stair = progress.place(Frost.HALL).orElseThrow(() -> new IllegalStateException("the Frost Walkers' mine needs their hall first"));
        BlockPos house = progress.place(Frost.HOUSE_SITE).orElse(stair);
        Vec3 away = Vec3.atLowerCornerOf(stair.subtract(house)).multiply(1, 0, 1);
        away = away.lengthSqr() == 0 ? new Vec3(1, 0, 0) : away.normalize();
        ServerLevel level = Engine.overworld(server);
        Unseen.Heights heights = new Unseen.Heights(level);
        int reach = Balance.get().frost().mineBlocks();
        BlockPos site = null;
        for (int turned = 0; turned <= TURN_MOST_DEGREES && site == null; turned += TURN_STEP_DEGREES) {
            for (int sign : turned == 0 ? new int[] {1} : new int[] {1, -1}) {
                Vec3 at = Vec3.atLowerCornerOf(stair).add(away.yRot((float) Math.toRadians(turned * sign)).scale(reach));
                int x = (int) Math.round(at.x);
                int z = (int) Math.round(at.z);
                if (site == null && !heights.wet(x, z) && Frost.snowy(heights, x, z) && Frost.flat(heights, x, z)) {
                    site = new BlockPos(x, heights.surface(x, z), z);
                }
            }
        }
        if (site == null) {
            Vec3 at = Vec3.atLowerCornerOf(stair).add(away.scale(reach));
            site = new BlockPos((int) Math.round(at.x), heights.surface((int) Math.round(at.x), (int) Math.round(at.z)), (int) Math.round(at.z));
        }
        Vec3 from = Vec3.atLowerCornerOf(site.subtract(stair)).multiply(1, 0, 1).normalize();
        Vec3 cart = Vec3.atBottomCenterOf(stair).add(from.scale(CART_OUT));
        progress.place(SITE, site);
        progress.place(CART, BlockPos.containing(cart.x, heights.surface((int) Math.floor(cart.x), (int) Math.floor(cart.z)), cart.z));
        progress.yaws.put(TURN, (float) Frost.facing(from).ordinal());
        progress.setDirty();
        LOGGER.info("the Frost Walkers' old mine lies at {}, its ore cart by the hall's stair at {}", site, progress.place(CART).orElseThrow());
    }

    static void tick(MinecraftServer server) {
        Progress progress = Progress.of(server);
        if (progress.place(SITE).isEmpty()) {
            return;
        }
        ServerLevel level = Engine.overworld(server);
        BlockPos site = progress.place(SITE).get();
        if (progress.place(NAME).isEmpty() && Frost.loaded(level, site)) {
            build(server, level, site);
        }
        BlockPos cart = progress.place(CART).orElseThrow();
        if (!progress.scratch(LAID).getBoolean(CART_CLUE) && Frost.loaded(level, cart) && level.areEntitiesLoaded(ChunkPos.asLong(cart))) {
            cart(server, level, cart);
        }
        if (progress.place(NAME).isPresent()) {
            lay(server, level);
            hold(level, progress);
        }
    }

    private static void build(MinecraftServer server, ServerLevel level, BlockPos found) {
        Progress progress = Progress.of(server);
        JsonObject plan = PLAN.get();
        Rotation turn = Rotation.values()[progress.yaws.getOrDefault(TURN, 0f).intValue()];
        JsonObject head = plan.getAsJsonObject("head");
        int reach = shaft(head).getX() + 1;
        BlockPos site = flattest(level, found, reach);
        int ground = Frost.top(level, site.getX(), site.getZ());
        progress.place(SITE, site.atY(ground));
        Camps.clearing(level, site.atY(ground), CLEARING, true);
        level(level, site, ground, reach);
        BlockPos headOrigin = place(level, HEAD, site.atY(ground - head.get("ground").getAsInt()), shaft(head), turn);
        Camps.snow(level, site.atY(ground), CLEARING);
        int stage = plan.getAsJsonObject("stage").get("height").getAsInt();
        JsonObject deep = plan.getAsJsonObject("deep");
        int height = deep.getAsJsonArray("high").get(1).getAsInt() + 1;
        int chamber = deep.getAsJsonArray("chamber").get(1).getAsInt();
        int stages = Math.max(0, Math.ceilDiv(headOrigin.getY() - height + chamber - Balance.get().frost().mineFloor(), stage));
        stages += stages % 2;
        for (int index = 1; index <= stages; index++) {
            place(level, STAGE, site.atY(headOrigin.getY() - stage * index), shaft(plan.getAsJsonObject("stage")),
                    index % 2 == 0 ? turn : turn.getRotated(Rotation.CLOCKWISE_180));
        }
        BlockPos column = site.atY(headOrigin.getY() - stage * stages - height);
        BlockPos deepOrigin = place(level, BOTTOM, column, shaft(deep), turn);
        StructurePlaceSettings settings = settings(shaft(deep), turn);
        BlockPos chamberAt = deepOrigin.offset(StructureTemplate.calculateRelativePosition(settings, local(deep.getAsJsonArray("chamber"))));
        BoundingBox box = level.getStructureManager().getOrCreate(BOTTOM).getBoundingBox(settings, deepOrigin);
        progress.place(NAME, site.atY(ground + 1));
        progress.place(DEEP, chamberAt);
        progress.place(LOW, new BlockPos(box.minX(), box.minY(), box.minZ()));
        progress.place(HIGH, new BlockPos(box.maxX(), box.maxY(), box.maxZ()));
        progress.setDirty();
        Frost.release(level, found);
        LOGGER.info("the Frost Walkers' old mine stands at {}, {} stages down to the old wall at {}", site.atY(ground), stages, chamberAt);
        Journal.syncAll(server);
    }

    private static BlockPos flattest(ServerLevel level, BlockPos found, int reach) {
        BlockPos best = found;
        double lowest = Double.MAX_VALUE;
        for (int dx = -SHIFT; dx <= SHIFT; dx += SHIFT_STEP) {
            for (int dz = -SHIFT; dz <= SHIFT; dz += SHIFT_STEP) {
                BlockPos centre = found.offset(dx, 0, dz);
                int high = Integer.MIN_VALUE;
                int low = Integer.MAX_VALUE;
                boolean wet = false;
                for (int x = -reach; x <= reach; x++) {
                    for (int z = -reach; z <= reach; z++) {
                        int top = Frost.top(level, centre.getX() + x, centre.getZ() + z);
                        high = Math.max(high, top);
                        low = Math.min(low, top);
                        wet |= !level.getFluidState(new BlockPos(centre.getX() + x, top, centre.getZ() + z)).isEmpty();
                    }
                }
                double rough = high - low + Math.sqrt(dx * dx + dz * dz) / SHIFT_STEP;
                if (!wet && rough < lowest) {
                    lowest = rough;
                    best = centre;
                }
            }
        }
        return best;
    }

    private static void level(ServerLevel level, BlockPos site, int ground, int reach) {
        for (int x = -reach; x <= reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                int top = Frost.top(level, site.getX() + x, site.getZ() + z);
                BlockPos column = new BlockPos(site.getX() + x, top, site.getZ() + z);
                BlockState surface = level.getBlockState(column);
                for (int y = ground + 1; y <= top + 1 + LEVEL_ABOVE; y++) {
                    level.setBlock(column.atY(y), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                for (int y = top + 1; y <= ground; y++) {
                    level.setBlock(column.atY(y), surface, Block.UPDATE_CLIENTS);
                }
                if (top > ground && level.getBlockState(column.atY(ground)).is(BlockTags.DIRT)) {
                    level.setBlock(column.atY(ground), surface, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static BlockPos place(ServerLevel level, ResourceLocation id, BlockPos column, BlockPos shaft, Rotation turn) {
        StructureTemplate template = level.getStructureManager().getOrCreate(id);
        BlockPos origin = column.offset(-shaft.getX(), 0, -shaft.getZ());
        template.placeInWorld(level, origin, origin, settings(shaft, turn), level.random, Block.UPDATE_CLIENTS);
        return origin;
    }

    private static StructurePlaceSettings settings(BlockPos shaft, Rotation turn) {
        return new StructurePlaceSettings().setRotation(turn).setRotationPivot(shaft);
    }

    private static BlockPos shaft(JsonObject piece) {
        JsonArray xz = piece.getAsJsonArray("shaft");
        return new BlockPos(xz.get(0).getAsInt(), 0, xz.get(1).getAsInt());
    }

    private static void cart(MinecraftServer server, ServerLevel level, BlockPos at) {
        Progress progress = Progress.of(server);
        Vec3 along = along(progress);
        AbstractMinecart cart = EntityType.MINECART.create(level);
        if (cart == null) {
            throw new IllegalStateException("a minecart could not be made for the Frost Walkers' ore cart");
        }
        cart.moveTo(at.getX() + 0.5, snowTop(level, at.getX(), at.getZ()) - SINK, at.getZ() + 0.5, Frost.yaw(new Vec3(-along.z, 0, along.x)), 0);
        cart.setDisplayBlockState(Blocks.COBBLED_DEEPSLATE.defaultBlockState());
        cart.setDisplayOffset(LOAD_OFFSET);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos drift = at.offset(dx, 0, dz);
                BlockPos above = drift.atY(Frost.top(level, drift.getX(), drift.getZ()) + 1);
                if ((dx != 0 || dz != 0) && (level.getBlockState(above).isAir() || level.getBlockState(above).is(Blocks.SNOW))
                        && level.getBlockState(above.below()).isFaceSturdy(level, above.below(), Direction.UP)) {
                    level.setBlock(above, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, DRIFT_LAYERS), Block.UPDATE_CLIENTS);
                }
            }
        }
        cart.setNoGravity(true);
        cart.setInvulnerable(true);
        cart.addTag(Steps.CLUE + CART_CLUE);
        level.addFreshEntity(cart);
        Vec3 across = new Vec3(-along.z, 0, along.x);
        Vec3 start = Vec3.atBottomCenterOf(at).add(along.scale(RUTS_FROM));
        for (int step = 0; step < RUTS_SHOWN; step++) {
            Vec3 here = start.add(along.scale(step * RUT_STEP));
            for (int side : new int[] {1, -1}) {
                rut(level, new Rut(here.add(across.scale(side * RUT_APART)), along), RUTS);
            }
        }
        Clues.box(level, start.add(along.scale(RUTS_SHOWN * RUT_STEP / 2)).add(0, ground(level, start) - start.y, 0), RUTS_CLUE);
        progress.scratch(LAID).putBoolean(CART_CLUE, true);
        progress.setDirty();
        Frost.release(level, at);
    }

    private static void hold(ServerLevel level, Progress progress) {
        BlockPos at = progress.place(CART).orElseThrow();
        level.getEntitiesOfClass(AbstractMinecart.class, new AABB(at).inflate(RUTS_FROM), cart -> cart.getTags().contains(Steps.CLUE + CART_CLUE))
                .forEach(cart -> {
                    Vec3 home = new Vec3(at.getX() + 0.5, cart.getY(), at.getZ() + 0.5);
                    if (cart.position().distanceToSqr(home) > DRIFT * DRIFT) {
                        cart.setDeltaMovement(Vec3.ZERO);
                        cart.moveTo(home.x, home.y, home.z, cart.getYRot(), 0);
                    }
                });
    }

    private static Vec3 along(Progress progress) {
        BlockPos site = progress.place(SITE).orElseThrow();
        BlockPos cart = progress.place(CART).orElseThrow();
        return Vec3.atLowerCornerOf(site.subtract(cart)).multiply(1, 0, 1).normalize();
    }

    private static void lay(MinecraftServer server, ServerLevel level) {
        Progress progress = Progress.of(server);
        Set<Long> laid = new HashSet<>();
        for (long chunk : progress.scratch(LAID).getLongArray(LAID)) {
            laid.add(chunk);
        }
        Map<Long, List<Rut>> ruts = new LinkedHashMap<>();
        ruts(progress).forEach(rut -> ruts.computeIfAbsent(ChunkPos.asLong(BlockPos.containing(rut.at())), key -> new ArrayList<>()).add(rut));
        boolean changed = false;
        for (Map.Entry<Long, List<Rut>> chunk : ruts.entrySet()) {
            if (laid.contains(chunk.getKey()) || level.getChunkSource().getChunkNow(ChunkPos.getX(chunk.getKey()), ChunkPos.getZ(chunk.getKey())) == null
                    || !level.areEntitiesLoaded(chunk.getKey())) {
                continue;
            }
            chunk.getValue().forEach(rut -> rut(level, rut, RUTS));
            laid.add(chunk.getKey());
            changed = true;
        }
        if (changed) {
            progress.scratch(LAID).put(LAID, new LongArrayTag(laid.stream().mapToLong(Long::longValue).toArray()));
            progress.setDirty();
        }
    }

    private static List<Rut> ruts(Progress progress) {
        BlockPos cart = progress.place(CART).orElseThrow();
        Vec3 along = along(progress);
        Vec3 across = new Vec3(-along.z, 0, along.x);
        Vec3 from = Vec3.atBottomCenterOf(cart).add(along.scale(RUTS_FROM + RUTS_SHOWN * RUT_STEP));
        Vec3 to = Vec3.atBottomCenterOf(progress.place(SITE).orElseThrow()).subtract(along.scale(PLAN.get().getAsJsonObject("head").getAsJsonArray("shaft")
                .get(0).getAsInt() + 1));
        double length = to.subtract(from).multiply(1, 0, 1).length();
        List<Rut> ruts = new ArrayList<>();
        for (double s = 0; s < length; s += RUT_STEP) {
            Vec3 here = path(from, along, across, s, length);
            Vec3 ahead = path(from, along, across, s + RUT_STEP, length).subtract(here).normalize();
            Vec3 side = new Vec3(-ahead.z, 0, ahead.x);
            for (int sign : new int[] {1, -1}) {
                ruts.add(new Rut(here.add(side.scale(sign * RUT_APART)), ahead));
            }
        }
        return ruts;
    }

    private static Vec3 path(Vec3 from, Vec3 along, Vec3 across, double s, double length) {
        double fade = Math.min(1, Math.min(s, length - s) / (WAVE / 2));
        return from.add(along.scale(s)).add(across.scale(BEND * Math.sin(s * Math.PI * 2 / WAVE) * Math.max(0, fade)));
    }

    private static void rut(ServerLevel level, Rut rut, String clue) {
        int x = (int) Math.floor(rut.at().x);
        int z = (int) Math.floor(rut.at().z);
        int top = Frost.top(level, x, z);
        BlockPos ground = new BlockPos(x, top, z);
        BlockState below = level.getBlockState(ground);
        if (!level.getFluidState(ground).isEmpty() || below.is(Blocks.ICE) || below.is(BlockTags.LEAVES)) {
            return;
        }
        BlockState above = level.getBlockState(ground.above());
        boolean snow = above.is(Blocks.SNOW) || below.is(Blocks.SNOW_BLOCK) || below.is(Blocks.POWDER_SNOW);
        double y = surface(level, x, z);
        BlockState worn = snow ? Blocks.LIGHT_GRAY_CONCRETE_POWDER.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
        Clues.mark(level, new Vec3(rut.at().x, y + 0.01, rut.at().z), worn, RUT_WIDTH, RUT_LENGTH, rut.along(), clue);
    }

    private static double snowTop(Level level, int x, int z) {
        int top = Frost.top(level, x, z);
        BlockState above = level.getBlockState(new BlockPos(x, top + 1, z));
        return above.is(Blocks.SNOW) ? top + 1 + above.getValue(SnowLayerBlock.LAYERS) / 8.0 : top + 1;
    }

    private static double surface(Level level, int x, int z) {
        int top = Frost.top(level, x, z);
        BlockState above = level.getBlockState(new BlockPos(x, top + 1, z));
        return above.is(Blocks.SNOW) ? top + 1 + (above.getValue(SnowLayerBlock.LAYERS) - 1) / 8.0 : top + 1;
    }

    private static double ground(Level level, Vec3 at) {
        return Frost.top(level, (int) Math.floor(at.x), (int) Math.floor(at.z)) + 1;
    }

    private static BlockPos local(JsonArray xyz) {
        return Prologue.vector(xyz);
    }

    static Optional<BoundingBox> deep(MinecraftServer server) {
        Progress progress = Progress.of(server);
        return progress.place(LOW).flatMap(low -> progress.place(HIGH).map(high -> BoundingBox.fromCorners(low, high)));
    }

    @SubscribeEvent
    public static void onGameEvent(VanillaGameEvent event) {
        if (!event.getVanillaEvent().equals(GameEvent.SHRIEK) || !(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (!Engine.running(server) || deep(server).filter(box -> box.isInside(BlockPos.containing(event.getEventPosition()))).isEmpty()) {
            return;
        }
        for (Engine.Active active : Engine.active(server)) {
            if (active.step().spec().has(SHRIEK) && !active.scratch().getBoolean(SHRIEKED)) {
                active.scratch().putBoolean(SHRIEKED, true);
                Progress.of(server).setDirty();
                Talk.say(active.quest().id(), event.getEventPosition(), new Story.Line(null, active.step().text(SHRIEK)));
            }
        }
    }
}

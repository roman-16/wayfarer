package io.github.roman16.wayfarer.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Bundled;
import io.github.roman16.wayfarer.Underground;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

final class Marsh {
    static final String NAME = "marsh";
    static final String HALL = "marsh_hall";
    static final String CELLAR = "marsh_cellar";
    static final String WITCHES = "witches";
    static final String SITE = "marsh_site";
    private static final String TURN = "marsh_turn";
    private static final String BOG = "marsh_bog";
    private static final String ORIGIN = "marsh_origin";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Bundled<JsonObject> PLAN = Bundled.json("/wayfarer/marsh.json");
    static final TagKey<Biome> SWAMPS = TagKey.create(Registries.BIOME, Wayfarer.id("has_structure/hall/witch"));
    private static final TicketType<ChunkPos> TICKET = TicketType.create("wayfarer_marsh", Comparator.comparingLong(ChunkPos::toLong));
    private static final String[] PEOPLE = {"rue", "sedge", "tansy"};
    private static final String FISHER = "fisher";
    private static final int GRID = 64;
    private static final int INSIDE = 32;
    private static final int LOAD_RADIUS = 6;
    private static final int CLEAR = 16;
    private static final int TICKS_PER_SECOND = 20;
    private static final int FLAGS = Block.UPDATE_CLIENTS;
    private static final int TREES = 12;
    private static final int TREE_FREE = 20;
    private static final int POTION_COLOR = 0x4B6B30;
    private static final float TRACK_SCALE = 1.0625f;
    private static final double THROW_SPEED = 0.75;
    private static final double PUDDLES = 0.09;
    private static final double PUDDLE = 0.22;
    private static final double PATCHES = 0.13;
    private static final double MUDDY = 0.25;
    private static final double MOSSY = -0.3;
    private static final double LILIES = 0.4;
    private static final double LILY = 0.35;

    private Marsh() {}

    static void find(MinecraftServer server) {
        Progress progress = Progress.of(server);
        if (progress.place(SITE).isPresent()) {
            return;
        }
        ServerLevel level = Engine.overworld(server);
        Balance.Marsh balance = Balance.get().marsh();
        Unseen.Heights heights = new Unseen.Heights(level);
        int ford = Balance.get().ford().aheadBlocks();
        Home.Aim aim = Home.aim(Caravan.last(server), Caravan.way(server), balance.searchBlocks(), (origin, bent) -> Unseen.warm(heights, origin, bent, ford));
        BlockPos from = aim.from();
        Vec3 way = aim.way();
        Optional<BlockPos> swamp = swamp(heights, level.getChunkSource().getGenerator().getSeaLevel(), from, way, balance);
        BlockPos site = swamp.orElseGet(() -> land(heights, from, way, balance));
        progress.place(SITE, site);
        progress.place(HALL, site);
        progress.yaws.put(TURN, (float) facing(way).ordinal());
        if (swamp.isEmpty()) {
            progress.place(BOG, site);
        }
        progress.setDirty();
        LOGGER.info("the Marsh Mothers' hall lies under {}{}, the way turned {} degrees from the caravan's", site, swamp.isPresent() ? ", in a swamp" : ", in a bog the quest makes",
                Home.degrees(Caravan.way(server), way));
    }

    private static Optional<BlockPos> swamp(Unseen.Heights heights, int sea, BlockPos from, Vec3 way, Balance.Marsh balance) {
        Vec3 side = new Vec3(-way.z, 0, way.x);
        BlockPos best = null;
        double nearest = Double.MAX_VALUE;
        for (int ahead = balance.aheadBlocks(); ahead <= balance.searchBlocks(); ahead += GRID) {
            for (int across = -balance.sideBlocks(); across <= balance.sideBlocks(); across += GRID) {
                double cost = ahead + Math.abs(across) * 2.0;
                if (cost >= nearest) {
                    continue;
                }
                Vec3 at = Vec3.atLowerCornerOf(from).add(way.scale(ahead)).add(side.scale(across));
                int x = (int) Math.round(at.x);
                int z = (int) Math.round(at.z);
                if (swampy(heights, sea, x, z) && swampy(heights, sea, x + INSIDE, z) && swampy(heights, sea, x - INSIDE, z)
                        && swampy(heights, sea, x, z + INSIDE) && swampy(heights, sea, x, z - INSIDE)) {
                    best = new BlockPos(x, heights.surface(x, z), z);
                    nearest = cost;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean swampy(Unseen.Heights heights, int sea, int x, int z) {
        Holder<Biome> biome = heights.biome(x, sea, z);
        return biome.is(SWAMPS) && !Unseen.snowy(biome, new BlockPos(x, sea, z));
    }

    private static BlockPos land(Unseen.Heights heights, BlockPos from, Vec3 way, Balance.Marsh balance) {
        for (int back = 0; back < balance.aheadBlocks(); back += GRID) {
            for (int sign : back == 0 ? new int[] {1} : new int[] {-1, 1}) {
                Vec3 at = Vec3.atLowerCornerOf(from).add(way.scale(balance.bogBlocks() + back * sign));
                int x = (int) Math.round(at.x);
                int z = (int) Math.round(at.z);
                if (!heights.wet(x, z) && flat(heights, x, z) && !Unseen.snowy(heights.biome(x, heights.surface(x, z), z), new BlockPos(x, heights.surface(x, z), z))) {
                    return new BlockPos(x, heights.surface(x, z), z);
                }
            }
        }
        Vec3 at = Vec3.atLowerCornerOf(from).add(way.scale(balance.bogBlocks()));
        int x = (int) Math.round(at.x);
        int z = (int) Math.round(at.z);
        return new BlockPos(x, heights.surface(x, z), z);
    }

    private static boolean flat(Unseen.Heights heights, int x, int z) {
        int middle = heights.surface(x, z);
        for (int[] step : new int[][] {{INSIDE, 0}, {-INSIDE, 0}, {0, INSIDE}, {0, -INSIDE}}) {
            if (Math.abs(heights.surface(x + step[0], z + step[1]) - middle) > 2) {
                return false;
            }
        }
        return true;
    }

    private static Rotation facing(Vec3 way) {
        Rotation best = Rotation.NONE;
        double most = -Double.MAX_VALUE;
        for (Rotation turn : Rotation.values()) {
            BlockPos front = StructureTemplate.transform(new BlockPos(0, 0, 1), Mirror.NONE, turn, BlockPos.ZERO);
            double toward = -(front.getX() * way.x + front.getZ() * way.z);
            if (toward > most) {
                most = toward;
                best = turn;
            }
        }
        return best;
    }

    static void tick(MinecraftServer server) {
        Progress.of(server).place(SITE).filter(site -> near(server, site)).ifPresent(site -> raise(server));
        watch(server);
    }

    static void raise(MinecraftServer server) {
        Progress progress = Progress.of(server);
        Optional<BlockPos> site = progress.place(SITE);
        if (site.isPresent() && progress.place(ORIGIN).isEmpty() && loaded(Engine.overworld(server), site.get())) {
            place(server, site.get());
        }
    }

    private static boolean near(MinecraftServer server, BlockPos site) {
        int near = Balance.get().marsh().nearBlocks();
        return Engine.players(server).stream().anyMatch(player -> player.level().dimension() == Level.OVERWORLD
                && Steps.horizontal(player.blockPosition(), site) <= (double) near * near);
    }

    static void unbuild(MinecraftServer server) {
        Progress.of(server).places.remove(ORIGIN);
        Progress.of(server).setDirty();
    }

    static void forget(MinecraftServer server) {
        Progress progress = Progress.of(server);
        List.of(SITE, BOG, ORIGIN, HALL, CELLAR, NAME, "seat_" + FISHER).forEach(progress.places::remove);
        for (String person : PEOPLE) {
            progress.places.remove("seat_" + person);
            progress.yaws.remove("seat_" + person);
        }
        progress.yaws.remove(TURN);
        progress.wards.remove(NAME);
        progress.witnesses.remove(HALL);
        progress.witnesses.remove(NAME);
        progress.setDirty();
    }

    private static boolean loaded(ServerLevel level, BlockPos site) {
        ChunkPos chunk = new ChunkPos(site);
        level.getChunkSource().addRegionTicket(TICKET, chunk, LOAD_RADIUS, chunk);
        for (int x = chunk.x - LOAD_RADIUS + 1; x < chunk.x + LOAD_RADIUS; x++) {
            for (int z = chunk.z - LOAD_RADIUS + 1; z < chunk.z + LOAD_RADIUS; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void place(MinecraftServer server, BlockPos site) {
        ServerLevel level = Engine.overworld(server);
        Progress progress = Progress.of(server);
        if (progress.place(BOG).isPresent()) {
            bog(level, site);
        }
        Rotation turn = turn(server);
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(turn);
        BlockPos pool = local(PLAN.get().getAsJsonObject("places").getAsJsonArray("pool"));
        int water = Ground.land(level, site.getX(), site.getZ());
        BlockPos hall = new BlockPos(site.getX(), water - pool.getY(), site.getZ()).subtract(turned(settings, pool.atY(0)));
        build(level, hall, turn);
        progress.place(ORIGIN, hall);
        record(server, level, hall, settings, turn);
        level.getChunkSource().removeRegionTicket(TICKET, new ChunkPos(site), LOAD_RADIUS, new ChunkPos(site));
        Standing.keep(server, HALL);
        Standing.keep(server, NAME);
        LOGGER.info("the Marsh Mothers' hall stands at {}, its pool at {}, turned {}", progress.place(HALL).orElseThrow(), world(server, "pool"), turn);
        Journal.syncAll(server);
    }

    static void build(ServerLevel level, BlockPos hall, Rotation turn) {
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(turn);
        BlockPos rest = hall.offset(turned(settings, local(PLAN.get().getAsJsonArray("origin"))));
        JsonObject standing = PLAN.get().getAsJsonObject("hut");
        clear(level, BoundingBox.fromCorners(hall.offset(turned(settings, local(standing.getAsJsonArray("from")))),
                hall.offset(turned(settings, local(standing.getAsJsonArray("to"))))));
        level(level, hall, settings, local(PLAN.get().getAsJsonObject("places").getAsJsonArray("pool")).getY());
        StructurePlaceSettings below = Underground.settings(turn);
        template(level, "marsh/way").placeInWorld(level, rest, rest, below, level.random, FLAGS);
        template(level, "hall/witch/hall").placeInWorld(level, hall, hall, below, level.random, FLAGS);
        template(level, "marsh/hut").placeInWorld(level, rest, rest, settings, level.random, FLAGS);
    }

    static JsonObject plan() {
        return PLAN.get();
    }

    private static void record(MinecraftServer server, ServerLevel level, BlockPos hall, StructurePlaceSettings settings, Rotation turn) {
        Progress progress = Progress.of(server);
        JsonObject plan = PLAN.get();
        float yaw = turn.ordinal() * 90f;
        progress.place(HALL, hall.offset(turned(settings, local(plan.getAsJsonArray("stone")))));
        progress.place(CELLAR, world(server, "cellar"));
        for (String person : PEOPLE) {
            progress.place("seat_" + person, world(server, person));
            progress.yaws.put("seat_" + person, yaw);
        }
        progress.place("seat_" + FISHER, world(server, FISHER));
        progress.yaws.put("seat_" + FISHER, yaw + 90);
        progress.place(NAME, world(server, "rue"));
        JsonObject ward = plan.getAsJsonObject("ward");
        progress.ward(NAME, BoundingBox.fromCorners(hall.offset(turned(settings, local(ward.getAsJsonArray("from")))),
                hall.offset(turned(settings, local(ward.getAsJsonArray("to"))))));
        progress.setDirty();
        for (String person : PEOPLE) {
            Places.seat(server, person).ifPresent(seat -> People.place(level, person, seat.at(), seat.yaw(), "standing"));
        }
        Places.seat(server, FISHER).ifPresent(seat -> People.place(level, FISHER, seat.at(), seat.yaw(), "sleeping"));
        plan.getAsJsonObject("clues").entrySet().forEach(clue -> Clues.marker(level, hall.offset(turned(settings, local(clue.getValue().getAsJsonArray()))), clue.getKey()));
        for (JsonElement mark : plan.getAsJsonArray("marks")) {
            Clues.display(level, hall.offset(turned(settings, local(mark.getAsJsonArray()))), TRACK_SCALE, NAME);
        }
        for (JsonElement wall : plan.getAsJsonArray("false_walls")) {
            Clues.veil(level, hall.offset(turned(settings, local(wall.getAsJsonArray()))));
        }
    }

    private static void level(ServerLevel level, BlockPos hall, StructurePlaceSettings settings, int ground) {
        int rise = PLAN.get().get("rise").getAsInt();
        for (JsonElement cell : PLAN.get().getAsJsonArray("ground")) {
            JsonArray xz = cell.getAsJsonArray();
            BlockPos column = hall.offset(turned(settings, new BlockPos(xz.get(0).getAsInt(), ground, xz.get(1).getAsInt())));
            for (int up = 1; up <= rise; up++) {
                BlockPos above = column.above(up);
                if (!level.getBlockState(above).isAir()) {
                    level.setBlock(above, Blocks.AIR.defaultBlockState(), FLAGS);
                }
            }
        }
    }

    private static void clear(ServerLevel level, BoundingBox box) {
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int y = box.minY(); y <= box.maxY() + CLEAR; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(Blocks.MANGROVE_ROOTS) || state.is(Blocks.VINE)
                            || state.canBeReplaced() && !state.isAir() && state.getFluidState().isEmpty()) {
                        level.setBlock(pos, state.getFluidState().isEmpty() ? Blocks.AIR.defaultBlockState() : Blocks.WATER.defaultBlockState(), FLAGS);
                    }
                }
            }
        }
    }

    private static void bog(ServerLevel level, BlockPos centre) {
        int radius = Balance.get().marsh().bogRadiusBlocks();
        int ground = centre.getY();
        ImprovedNoise noise = new ImprovedNoise(RandomSource.create(level.getSeed() ^ centre.asLong()));
        Holder<Biome> swamp = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.SWAMP);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (!inside(x, z, radius)) {
                    continue;
                }
                int wx = centre.getX() + x;
                int wz = centre.getZ() + z;
                int y = Ground.land(level, wx, wz);
                for (int up = y + 1; up <= y + CLEAR * 2; up++) {
                    BlockPos above = new BlockPos(wx, up, wz);
                    BlockState state = level.getBlockState(above);
                    if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.canBeReplaced() && !state.isAir() && state.getFluidState().isEmpty()) {
                        level.setBlock(above, Blocks.AIR.defaultBlockState(), FLAGS);
                    }
                }
                BlockPos surface = new BlockPos(wx, y, wz);
                double puddle = noise.noise(wx * PUDDLES, 0, wz * PUDDLES);
                double patch = noise.noise(wx * PATCHES, 64, wz * PATCHES);
                if (y <= ground && puddle > PUDDLE) {
                    level.setBlock(surface.below(), Blocks.MUD.defaultBlockState(), FLAGS);
                    level.setBlock(surface, Blocks.WATER.defaultBlockState(), FLAGS);
                    if (noise.noise(wx * LILIES, 128, wz * LILIES) > LILY) {
                        level.setBlock(surface.above(), Blocks.LILY_PAD.defaultBlockState(), FLAGS);
                    }
                } else if (level.getFluidState(surface).isEmpty()) {
                    BlockState top = patch > MUDDY ? Blocks.MUD.defaultBlockState() : patch < MOSSY ? Blocks.MOSS_BLOCK.defaultBlockState()
                            : Blocks.GRASS_BLOCK.defaultBlockState();
                    level.setBlock(surface, top, FLAGS);
                }
            }
        }
        List<ChunkAccess> chunks = new ArrayList<>();
        for (int cx = (centre.getX() - radius) >> 4; cx <= (centre.getX() + radius) >> 4; cx++) {
            for (int cz = (centre.getZ() - radius) >> 4; cz <= (centre.getZ() + radius) >> 4; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                chunk.fillBiomesFromNoise((qx, qy, qz, sampler) -> inside(QuartPos.toBlock(qx) - centre.getX(), QuartPos.toBlock(qz) - centre.getZ(), radius)
                        ? swamp : chunk.getNoiseBiome(qx, qy, qz), level.getChunkSource().randomState().sampler());
                chunk.setUnsaved(true);
                chunks.add(chunk);
            }
        }
        level.getChunkSource().chunkMap.resendBiomesForChunks(chunks);
        trees(level, centre, radius);
    }

    private static boolean inside(int x, int z, int radius) {
        double wobble = Math.sin(Math.atan2(z, x) * 5) * radius / 8.0;
        return x * x + z * z <= (radius + wobble) * (radius + wobble);
    }

    private static void trees(ServerLevel level, BlockPos centre, int radius) {
        level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolder(TreeFeatures.MANGROVE).ifPresent(tree -> {
            for (int index = 0; index < TREES; index++) {
                double angle = index * 2.399963;
                double reach = TREE_FREE + (radius - TREE_FREE) * ((index * 37 % TREES) + 0.5) / TREES;
                int x = centre.getX() + (int) Math.round(Math.cos(angle) * reach);
                int z = centre.getZ() + (int) Math.round(Math.sin(angle) * reach);
                tree.value().place(level, level.getChunkSource().getGenerator(), level.random, new BlockPos(x, Ground.land(level, x, z) + 1, z));
            }
        });
    }

    private static void watch(MinecraftServer server) {
        Progress progress = Progress.of(server);
        if (progress.place(ORIGIN).isEmpty() || progress.trust(WITCHES) < 0) {
            return;
        }
        BoundingBox ward = progress.wards.get(NAME);
        int under = progress.place(ORIGIN).get().getY() + PLAN.get().get("under").getAsInt();
        Engine.main(server).filter(active -> Steps.entered(active) && active.step().spec().has("forbids")).ifPresent(active -> Engine.players(server).stream()
                .filter(player -> player.level().dimension() == Level.OVERWORLD && ward != null && ward.isInside(player.blockPosition())
                        && player.getY() < under)
                .findFirst().ifPresent(player -> force(server, active, player)));
    }

    private static void force(MinecraftServer server, Engine.Active active, ServerPlayer intruder) {
        Progress progress = Progress.of(server);
        Talk.reset();
        progress.shun(active.step().text("forbids"));
        progress.told.computeIfAbsent(active.quest().id(), key -> new ArrayList<>()).add(active.step().text("forced_story"));
        int ticks = Balance.get().marsh().shunnedSeconds() * TICKS_PER_SECOND;
        BoundingBox ward = progress.wards.get(NAME);
        for (ServerPlayer player : Engine.players(server)) {
            if (player.level().dimension() == Level.OVERWORLD && ward.inflatedBy(CLEAR).isInside(player.blockPosition())) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks));
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks));
            }
        }
        People.find(server, PEOPLE[0]).filter(body -> body instanceof LivingEntity).ifPresent(body -> {
            ThrownPotion potion = new ThrownPotion(body.level(), (LivingEntity) body);
            ItemStack bottle = new ItemStack(Items.SPLASH_POTION);
            bottle.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.of(POTION_COLOR),
                    List.of(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks), new MobEffectInstance(MobEffects.WEAKNESS, ticks))));
            potion.setItem(bottle);
            Vec3 toward = intruder.position().subtract(body.getEyePosition());
            potion.shoot(toward.x, toward.y + toward.horizontalDistance() * 0.2, toward.z, (float) THROW_SPEED, 2);
            body.level().addFreshEntity(potion);
        });
        Talk.say(active.quest().id(), People.find(server, PEOPLE[0]).map(Entity::position).orElse(null), active.step().lines("forced"),
                Engine.fill(progress));
        Engine.jump(server, active, active.step().text("forced_to"));
    }

    static BlockPos world(MinecraftServer server, String place) {
        BlockPos hall = Progress.of(server).place(ORIGIN).orElseThrow(() -> new IllegalStateException("the Marsh Mothers' hall is not built yet"));
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(turn(server));
        return hall.offset(turned(settings, local(PLAN.get().getAsJsonObject("places").getAsJsonArray(place))));
    }

    private static Rotation turn(MinecraftServer server) {
        return Rotation.values()[Progress.of(server).yaws.getOrDefault(TURN, 0f).intValue()];
    }

    private static BlockPos turned(StructurePlaceSettings settings, BlockPos local) {
        return StructureTemplate.calculateRelativePosition(settings, local);
    }

    private static StructureTemplate template(ServerLevel level, String piece) {
        return level.getStructureManager().getOrCreate(Wayfarer.id(piece));
    }

    private static BlockPos local(JsonArray xyz) {
        return Prologue.vector(xyz);
    }

    static void move(MinecraftServer server, int x, int z) {
        Progress progress = Progress.of(server);
        List.of(SITE, BOG, ORIGIN).forEach(progress.places::remove);
        progress.place(SITE, new BlockPos(x, Ground.land(Engine.overworld(server), x, z), z));
        progress.setDirty();
    }

    static Optional<BlockPos> site(Progress progress) {
        return progress.place(SITE);
    }

    static boolean built(MinecraftServer server) {
        return Progress.of(server).place(ORIGIN).isPresent();
    }
}

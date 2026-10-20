package io.github.roman16.wayfarer.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Bundled;
import io.github.roman16.wayfarer.Camps;
import io.github.roman16.wayfarer.Underground;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

final class ReadingHouse {
    static final String NAME = "reading_house";
    static final String QUEST = "the_keepers_apprentice";
    private static final String SITE = "reading_house_site";
    private static final String ORIGIN = "reading_house_origin";
    private static final String TURN = "reading_house_turn";
    private static final String STAIR = "reading_house_stair";
    private static final String VAULT = "reading_house_vault";
    private static final String BURIED = "reading_house_buried";
    private static final String ANSEL = "ansel";
    private static final String SCORCHED_BOOK = "scorched_book";
    private static final String ASH_PRINTS = "ash_prints";
    private static final String PRINTS = "reading_house_prints";
    private static final String ANSEL_SKIN = "keeper_ansel";
    private static final ResourceLocation HOUSE = Wayfarer.id("reading_house/house");
    private static final ResourceLocation VAULT_TEMPLATE = Wayfarer.id("reading_house/vault");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Bundled<JsonObject> PLAN = Bundled.json("/wayfarer/reading_house.json");
    private static final int GRID = 16;
    private static final int SEARCH_STEP = 32;
    private static final int ROUGH = 4;
    private static final int FOOT = 12;
    private static final int UNSEEN = 64;
    private static final int HILL = 64;
    private static final int SURROUND = 8;
    private static final double PRINT_STEP = 0.9;
    private static final double FOOT_APART = 0.25;
    private static final float PRINT_WIDTH = 0.2f;
    private static final float PRINT_LENGTH = 0.35f;
    private static final double CLICKED = 1;
    private static final int CANDLES = 3;
    private static final int CLEARING = 16;

    private ReadingHouse() {}

    static void find(MinecraftServer server) {
        Progress progress = Progress.of(server);
        if (progress.place(SITE).isPresent()) {
            return;
        }
        ServerLevel level = Engine.overworld(server);
        Balance.ReadingHouse balance = Balance.get().readingHouse();
        BlockPos camp = Caravan.last(server);
        Home.Aim aim = Home.aim(camp, Caravan.way(server), balance.aheadBlocks());
        Vec3 ahead = Vec3.atLowerCornerOf(aim.from()).add(aim.way().scale(balance.aheadBlocks()));
        Unseen.Heights heights = new Unseen.Heights(level);
        int sea = level.getChunkSource().getGenerator().getSeaLevel();
        BlockPos best = null;
        double highest = -Double.MAX_VALUE;
        for (boolean strict : new boolean[] {true, false}) {
            int reach = strict ? balance.searchBlocks() : balance.searchBlocks() * 2;
            int step = strict ? SEARCH_STEP : SEARCH_STEP * 2;
            for (int dx = -reach; dx <= reach; dx += step) {
                for (int dz = -reach; dz <= reach; dz += step) {
                    int x = (int) Math.round(ahead.x) + dx;
                    int z = (int) Math.round(ahead.z) + dz;
                    int y = heights.surface(x, z);
                    if (strict && y > sea + HILL || !fits(heights, sea, x, y, z, strict)) {
                        continue;
                    }
                    int rough = rough(heights, x, y, z);
                    if (rough > ROUGH * 2) {
                        continue;
                    }
                    double lift = strict ? prominence(heights, x, y, z) : sea - y;
                    double score = lift - rough * ROUGH - Math.sqrt((double) dx * dx + dz * dz) / GRID;
                    if (score > highest) {
                        highest = score;
                        best = new BlockPos(x, y, z);
                    }
                }
            }
            if (best != null) {
                break;
            }
        }
        BlockPos site = best != null ? best
                : BlockPos.containing(ahead.x, heights.surface((int) Math.round(ahead.x), (int) Math.round(ahead.z)), ahead.z);
        Vec3 along = Vec3.atLowerCornerOf(site.subtract(aim.from())).multiply(1, 0, 1);
        progress.place(SITE, site);
        progress.yaws.put(TURN, (float) Frost.facing(along.lengthSqr() == 0 ? aim.way() : along.normalize()).ordinal());
        progress.setDirty();
        LOGGER.info("the Keepers' reading-house lies at {}{}", site, best != null ? ", on the best ground near the way" : ", with no fitting ground near");
    }

    private static boolean fits(Unseen.Heights heights, int sea, int x, int y, int z, boolean strict) {
        Holder<Biome> biome = heights.biome(x, y, z);
        return !heights.wet(x, z) && y > sea && !biome.is(BiomeTags.IS_OCEAN) && !biome.is(BiomeTags.IS_RIVER) && !biome.is(Marsh.SWAMPS)
                && !(strict && Unseen.snowy(biome, new BlockPos(x, y, z)));
    }

    private static double prominence(Unseen.Heights heights, int x, int y, int z) {
        double around = 0;
        int samples = 0;
        for (int step = 0; step < SURROUND; step++) {
            double angle = Math.PI * 2 * step / SURROUND;
            around += heights.surface(x + (int) Math.round(Math.cos(angle) * GRID * 4), z + (int) Math.round(Math.sin(angle) * GRID * 4));
            samples++;
        }
        return y - around / samples;
    }

    private static int rough(Unseen.Heights heights, int x, int y, int z) {
        int most = 0;
        for (int[] corner : new int[][] {{FOOT, FOOT}, {-FOOT, FOOT}, {FOOT, -FOOT}, {-FOOT, -FOOT}, {FOOT, 0}, {-FOOT, 0}, {0, FOOT}, {0, -FOOT}}) {
            most = Math.max(most, heights.wet(x + corner[0], z + corner[1]) ? ROUGH * ROUGH : Math.abs(heights.surface(x + corner[0], z + corner[1]) - y));
        }
        return most;
    }

    static void tick(MinecraftServer server) {
        Progress progress = Progress.of(server);
        if (progress.place(SITE).isEmpty()) {
            return;
        }
        ServerLevel level = Engine.overworld(server);
        BlockPos site = progress.place(SITE).get();
        if (progress.place(ORIGIN).isEmpty() && Frost.loaded(level, site)) {
            build(server, level, site);
        }
        if (progress.place(ORIGIN).isPresent() && progress.done(QUEST) && !progress.scratch(QUEST).getBoolean(BURIED)) {
            bury(server, level);
        }
    }

    private static void build(MinecraftServer server, ServerLevel level, BlockPos site) {
        Progress progress = Progress.of(server);
        Rotation turn = turn(progress);
        Camps.clearing(level, site.atY(Frost.top(level, site.getX(), site.getZ())), CLEARING, true);
        BlockPos origin = Camps.place(level, HOUSE, site, turn);
        StructurePlaceSettings settings = Camps.settings(level, HOUSE, turn);
        int depth = PLAN.get().get("vault_depth").getAsInt();
        StructureTemplate vault = level.getStructureManager().getOrCreate(VAULT_TEMPLATE);
        vault.placeInWorld(level, origin.below(depth), origin.below(depth), Underground.settings(turn, Camps.pivot(level, HOUSE)), level.random,
                Block.UPDATE_CLIENTS);
        BoundingBox box = level.getStructureManager().getOrCreate(HOUSE).getBoundingBox(settings, origin);
        progress.place(ORIGIN, origin);
        progress.place(NAME, world(origin, settings, "door"));
        progress.ward(NAME, new BoundingBox(box.minX(), box.minY() - depth, box.minZ(), box.maxX(), box.maxY(), box.maxZ()));
        BlockPos lectern = world(origin, settings, "lectern");
        BlockPos stair = world(origin, settings, "stair");
        BlockPos vaultSeat = world(origin, settings, "vault").below(depth);
        progress.place("seat_" + STAIR, stair);
        progress.yaws.put("seat_" + STAIR, Frost.yaw(Vec3.atLowerCornerOf(progress.place(NAME).orElseThrow().subtract(stair)).multiply(1, 0, 1)));
        progress.place("seat_" + VAULT, vaultSeat);
        progress.yaws.put("seat_" + VAULT, Frost.yaw(Vec3.atLowerCornerOf(stair.subtract(vaultSeat)).multiply(1, 0, 1)));
        progress.setDirty();
        Clues.marker(level, lectern, SCORCHED_BOOK);
        prints(level, settings, origin);
        BlockPos ansel = world(origin, settings, "ansel");
        float yaw = Frost.yaw(Vec3.atLowerCornerOf(lectern.subtract(ansel)).multiply(1, 0, 1));
        People.spawn(level, ANSEL_SKIN, Vec3.atBottomCenterOf(ansel), yaw, "fallen_back", Steps.CLUE + ANSEL);
        Frost.release(level, site);
        LOGGER.info("the Keepers' reading-house stands at {}, its door at {}, its vault {} blocks down", origin, progress.place(NAME).orElseThrow(), depth);
        Journal.syncAll(server);
    }

    private static void prints(ServerLevel level, StructurePlaceSettings settings, BlockPos origin) {
        JsonArray line = PLAN.get().getAsJsonArray("prints");
        Vec3 from = Vec3.atBottomCenterOf(world(origin, settings, line.get(0).getAsJsonArray()));
        Vec3 to = Vec3.atBottomCenterOf(world(origin, settings, line.get(1).getAsJsonArray()));
        Vec3 path = to.subtract(from).multiply(1, 0, 1);
        Vec3 along = path.normalize();
        Vec3 across = new Vec3(-along.z, 0, along.x);
        BlockState ash = Blocks.GRAY_CONCRETE_POWDER.defaultBlockState();
        int index = 0;
        for (double s = 1; s < path.length(); s += PRINT_STEP, index++) {
            Vec3 at = from.add(along.scale(s)).add(across.scale(index % 2 == 0 ? FOOT_APART : -FOOT_APART));
            BlockPos cell = BlockPos.containing(at.x, from.y, at.z);
            double y = level.getBlockState(cell).isAir() ? cell.getY() : cell.getY() + 1.0 / 16;
            Clues.mark(level, new Vec3(at.x, y + 0.01, at.z), ash, PRINT_WIDTH, PRINT_LENGTH, along, path.length() - s < CLICKED ? ASH_PRINTS : PRINTS);
        }
        Clues.box(level, to, ASH_PRINTS);
    }

    private static void bury(MinecraftServer server, ServerLevel level) {
        Progress progress = Progress.of(server);
        BlockPos door = progress.place(NAME).orElseThrow();
        if (!level.isLoaded(door) || !level.areEntitiesLoaded(ChunkPos.asLong(door))
                || server.getPlayerList().getPlayers().stream().anyMatch(player -> player.level() == level && player.blockPosition().closerThan(door, UNSEEN))) {
            return;
        }
        List<Entity> dead = level.getEntities((Entity) null, new AABB(door).inflate(GRID), entity -> entity.getTags().contains(Steps.CLUE + ANSEL));
        dead.forEach(Entity::discard);
        StructurePlaceSettings settings = Camps.settings(level, HOUSE, turn(progress));
        BlockPos grave = world(progress.place(ORIGIN).orElseThrow(), settings, "grave");
        BlockPos foot = grave.relative(settings.getRotation().rotate(Direction.SOUTH));
        for (BlockPos mound : new BlockPos[] {grave.below(), foot.below()}) {
            level.setBlock(mound, Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        level.setBlock(grave, Blocks.STONE_BRICK_WALL.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(grave.above(), Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, CANDLES), Block.UPDATE_CLIENTS);
        progress.scratch(QUEST).putBoolean(BURIED, true);
        progress.setDirty();
        LOGGER.info("Alruna buried Ansel at {}", grave);
    }

    private static Rotation turn(Progress progress) {
        return Rotation.values()[progress.yaws.getOrDefault(TURN, 0f).intValue()];
    }

    private static BlockPos world(BlockPos origin, StructurePlaceSettings settings, String key) {
        return world(origin, settings, PLAN.get().getAsJsonArray(key));
    }

    private static BlockPos world(BlockPos origin, StructurePlaceSettings settings, JsonArray local) {
        return origin.offset(StructureTemplate.calculateRelativePosition(settings, Prologue.vector(local)));
    }
}

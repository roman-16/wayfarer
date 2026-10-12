package io.github.roman16.wayfarer.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Bundled;
import io.github.roman16.wayfarer.Camps;
import io.github.roman16.wayfarer.CallingStoneBlockEntity;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import org.slf4j.Logger;

final class FirstVillage {
    static final String NAME = "first_village";
    static final String STONE = "first_village_stone";
    static final String MELT = "first_village_melt";
    private static final String HALL = "first_village_hall";
    private static final String SITE = "first_village_site";
    private static final String GREEN = "first_village_green";
    private static final String TURN = "first_village_turn";
    private static final String HALL_LOW = "first_village_hall_low";
    private static final String HALL_HIGH = "first_village_hall_high";
    private static final String RUINS_LOW = "first_village_ruins_low";
    private static final String RUINS_HIGH = "first_village_ruins_high";
    private static final String FOUNDER = "founder";
    private static final String RIDER = "rider";
    private static final ResourceLocation EVIL = Wayfarer.id("hall/evil");
    private static final TagKey<Biome> PLAINS = TagKey.create(Registries.BIOME, Wayfarer.id("has_structure/hall/evil"));
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Bundled<JsonObject> PLAN = Bundled.json("/wayfarer/first_village.json");
    private static final TicketType<ChunkPos> TICKET = TicketType.create("wayfarer_first_village", Comparator.comparingLong(ChunkPos::toLong));
    private static final Founder[] FOUNDERS = {
        new Founder("founder_01", 3, 0, "standing"),
        new Founder("founder_02", -3, 1, "standing"),
        new Founder("founder_03", 1, -3, "standing"),
        new Founder("founder_04", -2, 4, "sitting_crossed"),
        new Founder("founder_05", -4, -3, "standing"),
    };
    private static final int SEARCH_STEP = 128;
    private static final int TRIES = 64;
    private static final int ROUGH = 4;
    private static final int FOOT = 16;
    private static final int DRY = 20;
    private static final int HILL = 64;
    private static final int CLEARING = 32;
    private static final int GREEN_RADIUS = 40;
    private static final int WOBBLES = 5;
    private static final double WOBBLE_SHARE = 8;
    private static final int EDGE = 3;
    private static final int EDGE_X = 73856093;
    private static final int EDGE_Z = 19349663;
    private static final int RISE = 2;
    private static final int DREAM_CHUNKS = 4;
    private static final int RIDERS = 3;
    private static final int RIDGE = 40;
    private static final int RIDER_APART = 3;
    private static final int HEADINGS = 8;

    private FirstVillage() {}

    private record Founder(String skin, int dx, int dz, String pose) {}

    static void find(MinecraftServer server) {
        Progress progress = Progress.of(server);
        if (progress.place(SITE).isPresent()) {
            return;
        }
        ServerLevel level = Engine.overworld(server);
        Balance.FirstVillage balance = Balance.get().firstVillage();
        BlockPos camp = Caravan.last(server);
        Home.Aim aim = Home.aim(camp, Caravan.way(server), balance.aheadBlocks());
        Vec3 ahead = Vec3.atLowerCornerOf(aim.from()).add(aim.way().scale(balance.aheadBlocks()));
        long began = System.nanoTime();
        Unseen.Heights heights = new Unseen.Heights(level);
        int sea = level.getChunkSource().getGenerator().getSeaLevel();
        int reach = balance.searchBlocks();
        List<int[]> offsets = new ArrayList<>();
        for (int dx = -reach; dx <= reach; dx += SEARCH_STEP) {
            for (int dz = -reach; dz <= reach; dz += SEARCH_STEP) {
                offsets.add(new int[] {dx, dz});
            }
        }
        offsets.sort(Comparator.comparingInt(offset -> offset[0] * offset[0] + offset[1] * offset[1]));
        BlockPos best = null;
        boolean plains = false;
        for (boolean strict : new boolean[] {true, false}) {
            int fewest = ROUGH * 2 + 1;
            int tried = 0;
            for (int[] offset : offsets) {
                int x = (int) Math.round(ahead.x) + offset[0];
                int z = (int) Math.round(ahead.z) + offset[1];
                if (tried >= TRIES || fewest <= ROUGH || !open(heights.biome(x, sea, z), strict)) {
                    continue;
                }
                tried++;
                int y = heights.surface(x, z);
                if (y <= sea || y > sea + HILL || strict && Unseen.snowy(heights.biome(x, y, z), new BlockPos(x, y, z)) || heights.wet(x, z)) {
                    continue;
                }
                int rough = rough(heights, x, y, z);
                if (rough < fewest) {
                    fewest = rough;
                    best = new BlockPos(x, y, z);
                    plains = strict;
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
        if (!plains) {
            progress.place(GREEN, site);
        }
        progress.yaws.put(TURN, (float) Frost.facing(along.lengthSqr() == 0 ? aim.way() : along.normalize()).ordinal());
        progress.setDirty();
        String ground = best == null ? ", with no fitting ground near, to be greened" : plains ? ", on open plains near the way" : ", on the most even land near the way, to be greened";
        LOGGER.info("the First Village's ruins lie at {}{}, found in {} ms", site, ground, (System.nanoTime() - began) / 1_000_000);
    }

    private static boolean open(Holder<Biome> biome, boolean strict) {
        if (strict) {
            return biome.is(PLAINS) && !biome.is(Tags.Biomes.IS_SNOWY) && !biome.is(Tags.Biomes.IS_ICY);
        }
        return !biome.is(BiomeTags.IS_OCEAN) && !biome.is(BiomeTags.IS_RIVER) && !biome.is(BiomeTags.IS_BEACH) && !biome.is(Marsh.SWAMPS)
                && !biome.is(Tags.Biomes.IS_MOUNTAIN) && !biome.is(Tags.Biomes.IS_WINDSWEPT) && !biome.is(Tags.Biomes.IS_BADLANDS)
                && !biome.is(Tags.Biomes.IS_DESERT);
    }

    private static void green(ServerLevel level, BlockPos centre) {
        Holder<Biome> plains = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
        for (int x = -GREEN_RADIUS - EDGE; x <= GREEN_RADIUS + EDGE; x++) {
            for (int z = -GREEN_RADIUS - EDGE; z <= GREEN_RADIUS + EDGE; z++) {
                if (!inside(x, z, Math.floorMod(x * EDGE_X ^ z * EDGE_Z, EDGE * 2 + 1) - EDGE)) {
                    continue;
                }
                int wx = centre.getX() + x;
                int wz = centre.getZ() + z;
                BlockPos top = new BlockPos(wx, level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1, wz);
                while (top.getY() > level.getMinBuildHeight()) {
                    BlockState state = level.getBlockState(top);
                    if (state.is(Blocks.SNOW_BLOCK)) {
                        break;
                    } else if (state.is(BlockTags.SNOW)) {
                        level.setBlock(top, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    } else if (!state.getCollisionShape(level, top).isEmpty() || !state.getFluidState().isEmpty()) {
                        break;
                    }
                    top = top.below();
                }
                BlockState ground = level.getBlockState(top);
                if (ground.is(Blocks.SNOW_BLOCK) || ground.is(Blocks.DIRT) && level.canSeeSky(top.above())) {
                    level.setBlock(top, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                } else if (ground.hasProperty(SnowyDirtBlock.SNOWY)) {
                    level.setBlock(top, ground.setValue(SnowyDirtBlock.SNOWY, false), Block.UPDATE_CLIENTS);
                }
            }
        }
        List<ChunkAccess> chunks = new ArrayList<>();
        for (int cx = (centre.getX() - GREEN_RADIUS) >> 4; cx <= (centre.getX() + GREEN_RADIUS) >> 4; cx++) {
            for (int cz = (centre.getZ() - GREEN_RADIUS) >> 4; cz <= (centre.getZ() + GREEN_RADIUS) >> 4; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                chunk.fillBiomesFromNoise((qx, qy, qz, sampler) -> inside(QuartPos.toBlock(qx) - centre.getX(), QuartPos.toBlock(qz) - centre.getZ(), 0)
                        ? plains : chunk.getNoiseBiome(qx, qy, qz), level.getChunkSource().randomState().sampler());
                chunk.setUnsaved(true);
                chunks.add(chunk);
            }
        }
        level.getChunkSource().chunkMap.resendBiomesForChunks(chunks);
    }

    private static boolean inside(int x, int z, int jitter) {
        double reach = GREEN_RADIUS + Math.sin(Math.atan2(z, x) * WOBBLES) * GREEN_RADIUS / WOBBLE_SHARE + jitter;
        return x * x + z * z <= reach * reach;
    }

    private static int rough(Unseen.Heights heights, int x, int y, int z) {
        int most = 0;
        for (int[] side : new int[][] {{FOOT, 0}, {-FOOT, 0}, {0, FOOT}, {0, -FOOT}, {DRY, DRY}, {-DRY, DRY}, {DRY, -DRY}, {-DRY, -DRY}}) {
            most = Math.max(most, Math.abs(heights.surface(x + side[0], z + side[1]) - y));
            if (most > ROUGH * 2 || heights.wet(x + side[0], z + side[1])) {
                return ROUGH * ROUGH;
            }
        }
        return most;
    }

    static void tick(MinecraftServer server) {
        Progress progress = Progress.of(server);
        ServerLevel level = Engine.overworld(server);
        progress.place(SITE).filter(site -> progress.place(HALL_LOW).isEmpty() && Frost.loaded(level, site)).ifPresent(site -> build(server, level, site));
    }

    private static void build(MinecraftServer server, ServerLevel level, BlockPos site) {
        Progress progress = Progress.of(server);
        Rotation turn = Rotation.values()[progress.yaws.getOrDefault(TURN, 0f).intValue()];
        Camps.clearing(level, site.atY(Frost.top(level, site.getX(), site.getZ())), CLEARING, false);
        Places.Hall hall = Places.raise(level, EVIL, site);
        Places.empty(level, hall.stone());
        BlockPos well = hall.stair();
        progress.place(GREEN).ifPresent(any -> green(level, well));
        JsonObject plan = PLAN.get();
        List<BoundingBox> boxes = new ArrayList<>(List.of(BoundingBox.fromCorners(well, well)));
        for (JsonElement entry : plan.getAsJsonArray("houses")) {
            piece(level, well, turn, entry.getAsJsonObject(), boxes, false).ifPresent(house -> boxes.add(house.box()));
        }
        Placed pit = piece(level, well, turn, plan.getAsJsonObject("pit"), boxes.subList(1, boxes.size()), true).orElseThrow();
        boxes.add(pit.box());
        BoundingBox ruins = BoundingBox.encapsulatingBoxes(boxes).orElseThrow();
        BlockPos melt = pit.origin().offset(StructureTemplate.calculateRelativePosition(pit.settings(), Prologue.vector(plan.getAsJsonArray("melt"))));
        BoundingBox kept = new BoundingBox(ruins.minX(), ruins.minY() - RISE, ruins.minZ(), ruins.maxX(), ruins.maxY() + RISE, ruins.maxZ());
        progress.place(NAME, well);
        progress.place(STONE, hall.stone());
        progress.place(MELT, melt);
        progress.place(HALL_LOW, new BlockPos(hall.box().minX(), hall.box().minY(), hall.box().minZ()));
        progress.place(HALL_HIGH, new BlockPos(hall.box().maxX(), hall.box().maxY(), hall.box().maxZ()));
        progress.place(RUINS_LOW, new BlockPos(kept.minX(), kept.minY(), kept.minZ()));
        progress.place(RUINS_HIGH, new BlockPos(kept.maxX(), kept.maxY(), kept.maxZ()));
        progress.ward(NAME, kept);
        progress.ward(HALL, hall.box());
        progress.setDirty();
        Frost.release(level, site);
        LOGGER.info("the First Village's ruins stand round {}, the casting pit's melt at {}, the empty stone at {}", well, melt, hall.stone());
        Journal.syncAll(server);
    }

    private record Placed(BlockPos origin, StructurePlaceSettings settings, BoundingBox box) {}

    private static Optional<Placed> piece(ServerLevel level, BlockPos well, Rotation turn, JsonObject spec, List<BoundingBox> taken, boolean needed) {
        JsonArray offset = spec.getAsJsonArray("offset");
        ResourceLocation template = Wayfarer.id(spec.get("template").getAsString());
        Vec3i size = level.getStructureManager().getOrCreate(template).getSize();
        for (Rotation around : Rotation.values()) {
            Rotation facing = turn.getRotated(around);
            BlockPos local = StructureTemplate.transform(new BlockPos(offset.get(0).getAsInt(), 0, offset.get(1).getAsInt()), Mirror.NONE, facing, BlockPos.ZERO);
            BlockPos centre = well.offset(local.getX(), 0, local.getZ());
            int reach = Math.max(size.getX(), size.getZ()) / 2;
            boolean crowded = taken.stream().anyMatch(box -> box.inflatedBy(1).intersects(centre.getX() - reach, centre.getZ() - reach, centre.getX() + reach,
                    centre.getZ() + reach));
            if ((crowded || wet(level, centre, reach)) && !(needed && around == Rotation.COUNTERCLOCKWISE_90)) {
                continue;
            }
            Rotation rotation = facing.getRotated(Rotation.values()[spec.has("turn") ? spec.get("turn").getAsInt() : 0]);
            BlockPos origin = Camps.place(level, template, centre, rotation);
            StructurePlaceSettings settings = Camps.settings(level, template, rotation);
            return Optional.of(new Placed(origin, settings, level.getStructureManager().getOrCreate(template).getBoundingBox(settings, origin)));
        }
        LOGGER.info("the First Village's {} found no dry ground round the well, so it is left out", template);
        return Optional.empty();
    }

    private static boolean wet(ServerLevel level, BlockPos centre, int reach) {
        for (BlockPos column : BlockPos.betweenClosed(centre.offset(-reach, 0, -reach), centre.offset(reach, 0, reach))) {
            BlockPos top = column.atY(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()) - 1);
            if (!level.getFluidState(top).isEmpty() || level.getBlockState(top).is(BlockTags.ICE)) {
                return true;
            }
        }
        return false;
    }

    static boolean built(MinecraftServer server) {
        return Progress.of(server).place(HALL_LOW).isPresent();
    }

    static boolean past(MinecraftServer server, ServerLevel dream) {
        Progress progress = Progress.of(server);
        BlockPos well = progress.place(NAME).orElseThrow();
        dream.getChunkSource().addRegionTicket(TICKET, new ChunkPos(well), DREAM_CHUNKS, new ChunkPos(well));
        BoundingBox hall = BoundingBox.fromCorners(progress.place(HALL_LOW).orElseThrow(), progress.place(HALL_HIGH).orElseThrow());
        BoundingBox ruins = BoundingBox.fromCorners(progress.place(RUINS_LOW).orElseThrow(), progress.place(RUINS_HIGH).orElseThrow());
        if (!Memories.copy(server, dream, ruins) || !Memories.copy(server, dream, hall)) {
            return false;
        }
        progress.place(GREEN).ifPresent(any -> green(dream, well));
        if (dream.getBlockEntity(progress.place(STONE).orElseThrow()) instanceof CallingStoneBlockEntity stone) {
            stone.hold(Optional.of(EVIL));
        }
        BlockPos melt = progress.place(MELT).orElseThrow();
        dream.setBlock(melt, Blocks.LAVA_CAULDRON.defaultBlockState(), Block.UPDATE_ALL);
        Vec3 centre = Vec3.atBottomCenterOf(melt);
        dream.setBlock(ground(dream, melt.offset(-1, 0, 3)), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true), Block.UPDATE_ALL);
        BlockPos bronze = ground(dream, melt.offset(3, 0, -2));
        dream.setBlock(bronze, Blocks.WEATHERED_COPPER.defaultBlockState(), Block.UPDATE_ALL);
        dream.setBlock(bronze.above(), Blocks.EXPOSED_CUT_COPPER_SLAB.defaultBlockState(), Block.UPDATE_ALL);
        dream.setBlock(ground(dream, melt.offset(4, 0, -1)), Blocks.OXIDIZED_COPPER.defaultBlockState(), Block.UPDATE_ALL);
        for (Founder founder : FOUNDERS) {
            Vec3 feet = Vec3.atBottomCenterOf(ground(dream, melt.offset(founder.dx(), 0, founder.dz())));
            Entity body = People.spawn(dream, founder.skin(), feet, Frost.yaw(centre.subtract(feet).multiply(1, 0, 1)), founder.pose(), Memories.ROLE + FOUNDER);
            if (founder.dx() > 0 && body instanceof LivingEntity living) {
                living.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SHOVEL));
            }
        }
        riders(dream, well);
        return true;
    }

    private static BlockPos ground(ServerLevel level, BlockPos column) {
        return column.atY(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()));
    }

    private static void riders(ServerLevel dream, BlockPos well) {
        Vec3 ridge = Vec3.ZERO;
        int highest = Integer.MIN_VALUE;
        for (int heading = 0; heading < HEADINGS; heading++) {
            double angle = Math.PI * 2 * heading / HEADINGS;
            Vec3 way = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            int x = (int) Math.floor(well.getX() + way.x * RIDGE);
            int z = (int) Math.floor(well.getZ() + way.z * RIDGE);
            int y = dream.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y > highest) {
                highest = y;
                ridge = way;
            }
        }
        Vec3 across = new Vec3(-ridge.z, 0, ridge.x);
        float yaw = Frost.yaw(ridge.scale(-1));
        for (int index = 0; index < RIDERS; index++) {
            Vec3 at = Vec3.atBottomCenterOf(well).add(ridge.scale(RIDGE)).add(across.scale((index - 1) * RIDER_APART));
            int x = (int) Math.floor(at.x);
            int z = (int) Math.floor(at.z);
            Mob horse = EntityType.HORSE.create(dream);
            Mob rider = (index == 1 ? EntityType.PILLAGER : EntityType.VINDICATOR).create(dream);
            if (horse == null || rider == null) {
                continue;
            }
            horse.moveTo(at.x, Frost.top(dream, x, z) + 1, at.z, yaw, 0);
            horse.setYHeadRot(yaw);
            rider.moveTo(at.x, Frost.top(dream, x, z) + 1, at.z, yaw, 0);
            rider.setYHeadRot(yaw);
            for (Mob mob : new Mob[] {horse, rider}) {
                mob.setNoAi(true);
                mob.setInvulnerable(true);
                mob.addTag(Memories.ROLE + RIDER);
                dream.addFreshEntity(mob);
            }
            rider.startRiding(horse, true);
        }
    }
}

package io.github.roman16.wayfarer.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Bundled;
import io.github.roman16.wayfarer.Camps;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

final class Mansion {
    static final String NAME = "mansion";
    static final String CELLAR = "mansion_cellar";
    private static final String SITE = "mansion_site";
    private static final String ORIGIN = "mansion_origin";
    private static final String TURN = "mansion_turn";
    private static final String CAGE_OPEN = "mansion_cage_open";
    private static final String QUEST = "the_mansion";
    private static final String CAGE_STEP = "the_cage";
    private static final String CAPTIVE = "seat_mansion_captive_";
    private static final ResourceLocation HOUSE = Wayfarer.id("mansion/house");
    private static final Bundled<JsonObject> PLAN = Bundled.json("/wayfarer/mansion.json");
    private static final TagKey<Biome> DARK_FOREST = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_dark_forest"));
    private static final List<Integer> SEARCH = List.of(1, 2);
    private static final double CANOPY = 3;
    private static final double PATH = 2;
    private static final Logger LOGGER = LogUtils.getLogger();

    private Mansion() {}

    static void find(MinecraftServer server) {
        Progress progress = Progress.of(server);
        if (progress.place(SITE).isPresent()) {
            return;
        }
        BlockPos from = Caravan.last(server);
        int ahead = Balance.get().mansion().aheadBlocks();
        BlockPos site = Stream.<BiPredicate<Holder<Biome>, BlockPos>>of(Mansion::dark, Mansion::wooded)
                .flatMap(fits -> SEARCH.stream().flatMap(times -> Errands.fitting(server, from, ahead * times, fits, 0).stream())).findFirst()
                .orElseGet(() -> Errands.ahead(server, from, ahead, Mansion::wooded, 0));
        progress.place(SITE, site);
        progress.place(NAME, site);
        progress.setDirty();
        LOGGER.info("the Gazebound house will stand at {}", site);
    }

    private static boolean dark(Holder<Biome> biome, BlockPos at) {
        return biome.is(Biomes.DARK_FOREST) || biome.is(DARK_FOREST);
    }

    private static boolean wooded(Holder<Biome> biome, BlockPos at) {
        return biome.is(BiomeTags.IS_FOREST) && !biome.value().coldEnoughToSnow(at);
    }

    static boolean ready(MinecraftServer server, String name) {
        return Progress.of(server).place(ORIGIN).isPresent();
    }

    static void unbuild(MinecraftServer server) {
        Progress progress = Progress.of(server);
        List.of(ORIGIN, CAGE_OPEN).forEach(progress.places::remove);
        progress.setDirty();
    }

    static void tick(MinecraftServer server) {
        Progress progress = Progress.of(server);
        ServerLevel level = Engine.overworld(server);
        progress.place(SITE).filter(site -> progress.place(ORIGIN).isEmpty() && HallSearch.loaded(level, site)).ifPresent(site -> {
            build(server, level, site);
            HallSearch.release(level, site);
        });
        if (progress.place(ORIGIN).isPresent() && progress.place(CAGE_OPEN).isEmpty() && progress.past(QUEST, CAGE_STEP) && level.isLoaded(progress.place(ORIGIN).get())) {
            openCage(server, level);
        }
    }

    private static void build(MinecraftServer server, ServerLevel level, BlockPos site) {
        Progress progress = Progress.of(server);
        BlockPos from = progress.place(Caravan.NAME).orElse(site.offset(0, 0, -1));
        Vec3 toward = Vec3.atLowerCornerOf(from.subtract(site)).multiply(1, 0, 1);
        Rotation turn = Frost.facing(toward.lengthSqr() < 1 ? new Vec3(0, 0, 1) : toward.normalize().scale(-1));
        JsonObject plan = PLAN.get();
        BlockPos origin = Camps.place(level, HOUSE, site, turn, plan.get("floor").getAsInt());
        StructurePlaceSettings settings = Camps.settings(level, HOUSE, turn);
        BlockPos cellar = local(origin, settings, plan, "cellar");
        progress.place(NAME, local(origin, settings, plan, "front"));
        progress.place(CELLAR, cellar);
        JsonArray cage = plan.getAsJsonArray("cage");
        for (int captive = 0; captive < cage.size(); captive++) {
            BlockPos seat = origin.offset(StructureTemplate.calculateRelativePosition(settings, Prologue.vector(cage.get(captive).getAsJsonArray())));
            progress.place(CAPTIVE + (captive + 1), seat);
            progress.yaws.put(CAPTIVE + (captive + 1), Frost.yaw(Vec3.atLowerCornerOf(cellar.subtract(seat)).multiply(1, 0, 1)));
        }
        BoundingBox box = level.getStructureManager().getOrCreate(HOUSE).getBoundingBox(settings, origin);
        progress.ward(NAME, box);
        if (!dark(level.getBiome(site), site)) {
            grove(level, box, local(origin, settings, plan, "front"));
        }
        Clues.marker(level, local(origin, settings, plan, "desk"), "ledger");
        Clues.marker(level, local(origin, settings, plan, "door"), "cage");
        progress.place(ORIGIN, origin);
        progress.yaws.put(TURN, (float) turn.ordinal());
        progress.setDirty();
        Standing.keep(server, NAME);
        Journal.syncAll(server);
        LOGGER.info("the Gazebound house stands at {}, its cellar at {}", origin, cellar);
    }

    private static void grove(ServerLevel level, BoundingBox box, BlockPos front) {
        Optional<Holder.Reference<ConfiguredFeature<?, ?>>> tree = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolder(TreeFeatures.DARK_OAK);
        if (tree.isEmpty()) {
            LOGGER.error("the Gazebound house's grove failed: the game has no dark oak");
            return;
        }
        Balance.Mansion balance = Balance.get().mansion();
        BlockPos centre = box.getCenter();
        Vec3 door = Vec3.atBottomCenterOf(front);
        Vec3 out = door.subtract(Vec3.atBottomCenterOf(centre)).multiply(1, 0, 1);
        Vec3 way = out.lengthSqr() < 1 ? new Vec3(0, 0, 1) : out.normalize();
        double clear = Math.hypot(box.getXSpan(), box.getZSpan()) / 2 + CANOPY;
        int reach = balance.groveBlocks();
        int gap = balance.groveGap();
        RandomSource random = RandomSource.create(centre.asLong());
        int planted = 0;
        for (int dx = -reach; dx <= reach; dx += gap) {
            for (int dz = -reach; dz <= reach; dz += gap) {
                int x = centre.getX() + dx + random.nextInt(gap) - gap / 2;
                int z = centre.getZ() + dz + random.nextInt(gap) - gap / 2;
                double distance = Math.hypot(x - centre.getX(), z - centre.getZ());
                Vec3 from = new Vec3(x + 0.5, door.y, z + 0.5).subtract(door);
                double along = from.dot(way);
                if (distance > reach || distance < clear || along > -CANOPY && from.subtract(way.scale(along)).length() < PATH + CANOPY) {
                    continue;
                }
                BlockPos top = Ground.above(level, new BlockPos(x, 0, z));
                if (level.getBlockState(top).is(Blocks.SNOW)) {
                    level.removeBlock(top, false);
                }
                if (level.getBlockState(top.below()).is(BlockTags.DIRT) && tree.get().value().place(level, level.getChunkSource().getGenerator(), random, top)) {
                    planted++;
                }
            }
        }
        LOGGER.info("the Gazebound planted {} dark oaks round their house", planted);
    }

    private static void openCage(MinecraftServer server, ServerLevel level) {
        Progress progress = Progress.of(server);
        BlockPos origin = progress.place(ORIGIN).orElseThrow();
        Rotation turn = Rotation.values()[progress.yaws.getOrDefault(TURN, 0f).intValue()];
        StructurePlaceSettings settings = Camps.settings(level, HOUSE, turn);
        for (JsonElement bar : PLAN.get().getAsJsonArray("bars")) {
            level.destroyBlock(origin.offset(StructureTemplate.calculateRelativePosition(settings, Prologue.vector(bar.getAsJsonArray()))), false);
        }
        progress.place(CAGE_OPEN, origin);
        progress.setDirty();
        Standing.keep(server, NAME);
        LOGGER.info("the Gazebound house's cage is open");
    }

    private static BlockPos local(BlockPos origin, StructurePlaceSettings settings, JsonObject plan, String key) {
        return origin.offset(StructureTemplate.calculateRelativePosition(settings, Prologue.vector(plan.getAsJsonArray(key))));
    }
}

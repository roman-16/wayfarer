package io.github.roman16.wayfarer.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Bundled;
import io.github.roman16.wayfarer.Camps;
import io.github.roman16.wayfarer.Library;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Variant;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.slf4j.Logger;

public final class Errands {
    static final String MISSING = "missing";
    static final String OUTPOST = "errand_outpost";
    static final String CAIRNS = "cairns";
    static final String SEAL = "seal";
    static final String SETTLED = "settled";
    static final String BURNING = "burning";
    static final String RITE = "rite";
    static final String BRAM = "bram_outpost";
    static final String DEN = "den";
    static final String BUNDLE = "bundle";
    static final String LAST_BUNDLE = "last_bundle";
    static final String NEVER_SLEEPS = "never_sleeps";
    static final String NEST = "nest";
    static final String FIRST_GATE = "first_gate";
    static final String NEVER_SLEEPS_TAG = Wayfarer.MODID + "_never_sleeps";
    private static final String SITE = "_site";
    private static final String BUILT = "_built";
    private static final String CAIRNS_SITE = "cairns_site";
    private static final String CAIRNS_ORIGIN = "cairns_origin";
    private static final String SEAL_SITE = "seal_site";
    private static final String SEAL_ORIGIN = "seal_origin";
    private static final String SEAL_OPEN = "seal_open";
    private static final String TURN = "errands_turn_";
    private static final String SEAL_QUEST = "the_seal";
    private static final String SEAL_MARKS = "the_marks";
    private static final ResourceLocation RUIN = Wayfarer.id("errands/seal");
    private static final ResourceLocation RING = Wayfarer.id("errands/cairns");
    private static final ResourceLocation NEST_TEMPLATE = Wayfarer.id("errands/nest");
    private static final ResourceLocation CITY = ResourceLocation.withDefaultNamespace("ancient_city");
    private static final String GATE_BOOK = "first_gate/last_record";
    private static final String DEN_CLUE = "den";
    private static final String[] PRINTS = {"print_1", "print_2"};
    private static final int[] PRINTS_BEYOND = {4, 8};
    private static final List<Outpost> RAISED_OUTPOSTS = List.of(new Outpost(OUTPOST, "brannoc", -40), new Outpost(RITE, "novice", 40), new Outpost(BRAM, "bram_son", 0));
    private static final List<Wayside> WAYSIDES = List.of(new Wayside(DEN, 0), new Wayside(BUNDLE, 20), new Wayside(LAST_BUNDLE, -20),
            new Wayside(NEVER_SLEEPS, 0), new Wayside(NEST, 0), new Wayside(FIRST_GATE, 0));
    private static final TagKey<Structure> VILLAGES = TagKey.create(Registries.STRUCTURE, Wayfarer.id("owners/village"));
    static final TagKey<Structure> OUTPOSTS = TagKey.create(Registries.STRUCTURE, Wayfarer.id("owners/outpost"));
    private static final Bundled<JsonObject> PLAN = Bundled.json("/wayfarer/errands.json");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int WIDEST = 90;
    private static final int TURN_STEP = 15;
    private static final int SAMPLE = 64;
    private static final int SPIRAL = 4;
    private static final int STAND_OFF = 3;
    private static final int FLAT = 8;
    private static final int BIAS = 40;
    private static final int CIRCLE = 2;
    private static final int CIRCLE_AWAY = 4;
    private static final int LOOK_BLOCKS = 32;
    private static final int BEFORE_GATE = 6;
    private static final int HEART_REACH = 32;
    private static final int HOLLOW_BUDGET = 32768;
    @Nullable
    private static Hollowing hollowing;
    private static final double HORSE_SPEED = 0.3375;
    private static final double HORSE_JUMP = 1;
    private static final double HORSE_HEALTH = 30;
    private static final int STEEP = 3;
    private static final String[] MARKS = {"word_1", "word_2", "word_3"};

    private Errands() {}

    private record Outpost(String name, String person, int bias) {}

    private record Wayside(String name, int bias) {}

    private record Hollowing(StructureStart start, Deque<BoundingBox> left) {}

    static void find(MinecraftServer server, String name) {
        Progress progress = Progress.of(server);
        ServerLevel level = Engine.overworld(server);
        BlockPos from = Caravan.last(server);
        int ahead = Balance.get().errands().aheadBlocks();
        switch (name) {
            case MISSING, SETTLED, BURNING -> {
                if (progress.place(name + SITE).isEmpty()) {
                    Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator().findNearestMapStructure(level,
                            level.registryAccess().registryOrThrow(Registries.STRUCTURE).getTag(VILLAGES).map(set -> (HolderSet<Structure>) set)
                                    .orElse(HolderSet.direct()), from, Balance.get().errands().searchChunks(), false);
                    BlockPos site = found != null ? surface(level, found.getFirst()) : ahead(server, from, ahead, (biome, at) -> true, 0);
                    progress.place(name + SITE, site);
                    progress.place(name, site);
                    progress.setDirty();
                    LOGGER.info("the village for {} is at {} ({})", name, site, found != null ? "grown" : "to be raised");
                }
            }
            case OUTPOST, RITE, BRAM -> {
                Outpost outpost = RAISED_OUTPOSTS.stream().filter(raised -> raised.name().equals(name)).findFirst().orElseThrow();
                if (progress.place(name + SITE).isEmpty()) {
                    BlockPos site = ahead(server, from, ahead, (biome, at) -> true, outpost.bias());
                    progress.place(name + SITE, site);
                    progress.place(name, site);
                    progress.setDirty();
                    LOGGER.info("the outpost for {} will stand at {}", outpost.person(), site);
                }
            }
            case DEN, BUNDLE, LAST_BUNDLE, NEVER_SLEEPS, NEST, FIRST_GATE -> {
                Wayside wayside = WAYSIDES.stream().filter(place -> place.name().equals(name)).findFirst().orElseThrow();
                if (progress.place(name + SITE).isEmpty()) {
                    BlockPos site = switch (name) {
                        case NEST -> Act3.high(server, from);
                        case NEVER_SLEEPS -> fitting(server, from, ahead, Errands::grass, wayside.bias())
                                .or(() -> fitting(server, from, ahead * 2, Errands::grass, wayside.bias())).orElseGet(() -> ahead(server, from, ahead, Errands::grass, 0));
                        case DEN, BUNDLE, LAST_BUNDLE -> ahead(server, from, ahead / 2, (biome, at) -> !Unseen.snowy(biome, at), wayside.bias());
                        default -> ahead(server, from, ahead, (biome, at) -> true, wayside.bias());
                    };
                    progress.place(name + SITE, site);
                    progress.place(name, site);
                    progress.setDirty();
                    LOGGER.info("the errand's {} will be at {}", name, site);
                }
            }
            case CAIRNS -> {
                if (progress.place(CAIRNS_SITE).isEmpty()) {
                    BlockPos site = snow(server, from).orElseGet(() -> ahead(server, from, ahead, (biome, at) -> true, 0));
                    progress.place(CAIRNS_SITE, site);
                    progress.place(CAIRNS, site);
                    progress.setDirty();
                    LOGGER.info("the Frost Walkers' old winter grounds are at {}", site);
                }
            }
            case SEAL -> {
                if (progress.place(SEAL_SITE).isEmpty()) {
                    BlockPos site = ahead(server, from, ahead, (biome, at) -> !Unseen.snowy(biome, at), BIAS);
                    progress.place(SEAL_SITE, site);
                    progress.place(SEAL, site);
                    progress.setDirty();
                    LOGGER.info("the Keepers' sealed house is at {}", site);
                }
            }
            default -> throw new IllegalStateException("errands have no place " + name);
        }
    }

    static boolean ready(MinecraftServer server, String name) {
        Progress progress = Progress.of(server);
        return switch (name) {
            case MISSING, SETTLED, BURNING -> progress.place(name + BUILT).isPresent();
            case OUTPOST, RITE, BRAM -> RAISED_OUTPOSTS.stream().filter(raised -> raised.name().equals(name)).anyMatch(raised -> progress.place("seat_" + raised.person()).isPresent());
            case DEN, BUNDLE, LAST_BUNDLE, NEVER_SLEEPS, NEST, FIRST_GATE -> progress.place(name + BUILT).isPresent();
            case CAIRNS -> progress.place(CAIRNS_ORIGIN).isPresent();
            case SEAL -> progress.place(SEAL_ORIGIN).isPresent();
            default -> false;
        };
    }

    static void reset() {
        hollowing = null;
    }

    static void tick(MinecraftServer server) {
        Progress progress = Progress.of(server);
        ServerLevel level = Engine.overworld(server);
        for (String name : new String[] {MISSING, SETTLED, BURNING}) {
            progress.place(name + SITE).filter(site -> progress.place(name + BUILT).isEmpty() && HallSearch.loaded(level, site)).ifPresent(site -> {
                village(server, level, name, site);
                HallSearch.release(level, site);
            });
        }
        for (Outpost outpost : RAISED_OUTPOSTS) {
            progress.place(outpost.name() + SITE).filter(site -> progress.place("seat_" + outpost.person()).isEmpty() && HallSearch.loaded(level, site)).ifPresent(site -> {
                outpost(server, level, outpost, site);
                HallSearch.release(level, site);
            });
        }
        for (Wayside wayside : WAYSIDES) {
            progress.place(wayside.name() + SITE).filter(site -> progress.place(wayside.name() + BUILT).isEmpty() && HallSearch.loaded(level, site)).ifPresent(site -> {
                if (wayside(server, level, wayside.name(), site)) {
                    HallSearch.release(level, site);
                }
            });
        }
        progress.place(CAIRNS_SITE).filter(site -> progress.place(CAIRNS_ORIGIN).isEmpty() && HallSearch.loaded(level, site)).ifPresent(site -> {
            cairns(server, level, site);
            HallSearch.release(level, site);
        });
        progress.place(SEAL_SITE).filter(site -> progress.place(SEAL_ORIGIN).isEmpty() && HallSearch.loaded(level, site)).ifPresent(site -> {
            seal(server, level, site);
            HallSearch.release(level, site);
        });
        if (progress.place(SEAL_ORIGIN).isPresent() && progress.place(SEAL_OPEN).isEmpty() && opened(progress)
                && level.isLoaded(progress.place(SEAL_ORIGIN).get())) {
            crumble(server, level);
        }
    }

    private static boolean opened(Progress progress) {
        return progress.done(SEAL_QUEST) || progress.open(SEAL_QUEST) && Story.get().quest(SEAL_QUEST)
                .map(quest -> progress.step(SEAL_QUEST) > quest.steps().stream().map(Story.Step::id).toList().indexOf(SEAL_MARKS)).orElse(false);
    }

    private static BlockPos surface(ServerLevel level, BlockPos at) {
        return at.atY(new Unseen.Heights(level).surface(at.getX(), at.getZ()));
    }

    private static boolean flat(Unseen.Heights heights, int x, int z, int y) {
        for (int dx = -FLAT; dx <= FLAT; dx += FLAT) {
            for (int dz = -FLAT; dz <= FLAT; dz += FLAT) {
                if (Math.abs(heights.surface(x + dx, z + dz) - y) > STEEP || heights.wet(x + dx, z + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean grass(Holder<Biome> biome, BlockPos at) {
        return biome.is(BiomeTags.IS_SAVANNA) || biome.is(Tags.Biomes.IS_PLAINS);
    }

    private static Optional<BlockPos> fitting(MinecraftServer server, BlockPos from, int distance, BiPredicate<Holder<Biome>, BlockPos> fits, int bias) {
        BlockPos found = ahead(server, from, distance, fits, bias);
        ServerLevel level = Engine.overworld(server);
        Unseen.Heights heights = new Unseen.Heights(level);
        return fits.test(heights.biome(found.getX(), found.getY(), found.getZ()), found) ? Optional.of(found) : Optional.empty();
    }

    private static BlockPos ahead(MinecraftServer server, BlockPos from, int distance, BiPredicate<Holder<Biome>, BlockPos> fits, int bias) {
        ServerLevel level = Engine.overworld(server);
        Vec3 way = Caravan.way(server).yRot((float) Math.toRadians(bias));
        Unseen.Heights heights = new Unseen.Heights(level);
        BlockPos dry = null;
        for (int turned = 0; turned <= WIDEST; turned += TURN_STEP) {
            for (int sign : turned == 0 ? new int[] {1} : new int[] {1, -1}) {
                Vec3 bent = way.yRot((float) Math.toRadians(turned * sign));
                for (int out = distance; out >= distance / 2; out -= SAMPLE) {
                    int x = from.getX() + (int) Math.round(bent.x * out);
                    int z = from.getZ() + (int) Math.round(bent.z * out);
                    if (heights.wet(x, z)) {
                        continue;
                    }
                    BlockPos at = new BlockPos(x, heights.surface(x, z), z);
                    if (!flat(heights, x, z, at.getY())) {
                        continue;
                    }
                    if (fits.test(heights.biome(x, at.getY(), z), at)) {
                        return at;
                    }
                    dry = dry == null ? at : dry;
                }
            }
        }
        if (dry != null) {
            return dry;
        }
        int x = from.getX() + (int) Math.round(way.x * distance);
        int z = from.getZ() + (int) Math.round(way.z * distance);
        return new BlockPos(x, heights.surface(x, z), z);
    }

    private static Optional<BlockPos> snow(MinecraftServer server, BlockPos from) {
        ServerLevel level = Engine.overworld(server);
        Unseen.Heights heights = new Unseen.Heights(level);
        int reach = Balance.get().errands().snowReachBlocks();
        for (int distance = SAMPLE * 4; distance <= reach; distance += SAMPLE * 4) {
            int around = (int) Math.ceil(2 * Math.PI * distance / (SAMPLE * 4));
            for (int index = 0; index < around; index++) {
                double angle = 2 * Math.PI * index / around;
                int x = from.getX() + (int) Math.round(Math.cos(angle) * distance);
                int z = from.getZ() + (int) Math.round(Math.sin(angle) * distance);
                if (heights.wet(x, z)) {
                    continue;
                }
                BlockPos at = new BlockPos(x, heights.surface(x, z), z);
                if (Unseen.snowy(heights.biome(x, at.getY(), z), at)) {
                    return Optional.of(at);
                }
            }
        }
        return Optional.empty();
    }

    static List<Holder<Structure>> kinds(ServerLevel level, TagKey<Structure> tag) {
        List<Holder<Structure>> kinds = new ArrayList<>();
        level.registryAccess().registryOrThrow(Registries.STRUCTURE).getTagOrEmpty(tag).forEach(kinds::add);
        return kinds;
    }

    static Optional<StructureStart> raise(ServerLevel level, List<Holder<Structure>> kinds, BlockPos site) {
        Optional<StructureStart> start = start(level, kinds, site);
        start.ifPresent(found -> place(level, found));
        return start;
    }

    private static void place(ServerLevel level, StructureStart start) {
        BoundingBox box = start.getBoundingBox();
        ChunkPos.rangeClosed(new ChunkPos(box.minX() >> 4, box.minZ() >> 4), new ChunkPos(box.maxX() >> 4, box.maxZ() >> 4))
                .forEach(chunk -> start.placeInChunk(level, level.structureManager(), level.getChunkSource().getGenerator(), level.getRandom(),
                        new BoundingBox(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(), chunk.getMaxBlockX(), level.getMaxBuildHeight(),
                                chunk.getMaxBlockZ()), chunk));
    }

    private static Optional<StructureStart> start(ServerLevel level, List<Holder<Structure>> kinds, BlockPos site) {
        Holder<Biome> biome = level.getBiome(site);
        List<Holder<Structure>> fitting = new ArrayList<>(kinds);
        fitting.sort(Comparator.comparing((Holder<Structure> kind) -> !kind.value().biomes().contains(biome))
                .thenComparing(kind -> kind.unwrapKey().map(key -> key.location().toString()).orElse("")));
        ChunkPos middle = new ChunkPos(site);
        for (Holder<Structure> kind : fitting) {
            for (int ring = 0; ring <= SPIRAL; ring++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    for (int dz = -ring; dz <= ring; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                            continue;
                        }
                        StructureStart start = kind.value().generate(level.registryAccess(), level.getChunkSource().getGenerator(),
                                level.getChunkSource().getGenerator().getBiomeSource(), level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(),
                                new ChunkPos(middle.x + dx, middle.z + dz), 0, level, any -> true);
                        if (start.isValid()) {
                            LOGGER.info("{} will stand at {}", kind.unwrapKey().map(key -> key.location().toString()).orElse("?"), start.getBoundingBox());
                            return Optional.of(start);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static int hollow(ServerLevel level, BoundingBox box) {
        int cleared = 0;
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY() + 1, box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.CAVE_AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
            cleared++;
        }
        return cleared;
    }

    private static void village(MinecraftServer server, ServerLevel level, String name, BlockPos site) {
        Progress progress = Progress.of(server);
        Optional<BlockPos> grown = Optional.empty();
        for (Holder<Structure> kind : level.registryAccess().registryOrThrow(Registries.STRUCTURE).getTagOrEmpty(VILLAGES)) {
            StructureStart start = level.getChunk(site.getX() >> 4, site.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS).getStartForStructure(kind.value());
            if (start != null && start.isValid() && !start.getPieces().isEmpty()) {
                grown = Optional.of(start.getPieces().getFirst().getBoundingBox().getCenter());
                break;
            }
        }
        BlockPos middle = grown.or(() -> raise(level, kinds(level, VILLAGES), site).map(start -> start.getBoundingBox().getCenter())).orElse(site);
        String person = switch (name) {
            case MISSING -> "wil";
            case SETTLED -> "settled_wayfarer";
            default -> name;
        };
        seat(server, level, person, middle, progress.place(Caravan.NAME).orElse(middle));
        progress.place(name, progress.place("seat_" + person).orElseThrow());
        progress.place(name + BUILT, middle);
        progress.setDirty();
        LOGGER.info("the village for {} stands round {}", name, middle);
    }

    private static void outpost(MinecraftServer server, ServerLevel level, Outpost outpost, BlockPos site) {
        Progress progress = Progress.of(server);
        BlockPos from = progress.place(Caravan.NAME).orElse(site);
        Optional<BoundingBox> box = raise(level, kinds(level, OUTPOSTS), site).map(StructureStart::getBoundingBox);
        BlockPos middle = box.map(BoundingBox::getCenter).orElse(site);
        int reach = box.map(found -> Math.max(found.getXSpan(), found.getZSpan()) / 2 + STAND_OFF).orElse(STAND_OFF);
        Vec3 toward = Vec3.atLowerCornerOf(from.subtract(middle)).multiply(1, 0, 1);
        Vec3 out = toward.lengthSqr() < 1 ? new Vec3(1, 0, 0) : toward.normalize();
        BlockPos foot = BlockPos.containing(Vec3.atLowerCornerOf(middle).add(out.scale(reach)));
        seat(server, level, outpost.person(), foot, middle);
        progress.place(outpost.name(), middle);
        if (outpost.name().equals(RITE)) {
            rite(level, progress.place("seat_" + outpost.person()).orElseThrow(), out);
        }
        progress.setDirty();
    }

    private static void rite(ServerLevel level, BlockPos novice, Vec3 out) {
        Vec3 side = out.yRot((float) Math.toRadians(90));
        BlockPos centre = standing(level, BlockPos.containing(Vec3.atLowerCornerOf(novice).add(side.scale(CIRCLE_AWAY))));
        for (int dx = -CIRCLE; dx <= CIRCLE; dx++) {
            for (int dz = -CIRCLE; dz <= CIRCLE; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) == CIRCLE) {
                    BlockPos ground = standing(level, centre.offset(dx, 0, dz)).below();
                    level.setBlock(ground, Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_ALL);
                    if (Math.abs(dx) == Math.abs(dz)) {
                        level.setBlock(ground.above(), Blocks.CANDLE.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }
        BlockPos ash = standing(level, centre).below();
        level.setBlock(ash, Blocks.SOUL_SOIL.defaultBlockState(), Block.UPDATE_ALL);
        Clues.marker(level, ash.above(), "circle");
        Clues.marker(level, novice, "coat");
        LOGGER.info("the rite's circle lies at {}", centre);
    }

    private static boolean wayside(MinecraftServer server, ServerLevel level, String name, BlockPos site) {
        Progress progress = Progress.of(server);
        BlockPos ground = standing(level, site);
        if (name.equals(FIRST_GATE) && !city(level, site)) {
            return false;
        }
        BlockPos at = switch (name) {
            case DEN -> den(server, level, ground);
            case BUNDLE, LAST_BUNDLE -> {
                level.setBlock(ground, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), Block.UPDATE_ALL);
                Clues.marker(level, ground, name);
                yield ground;
            }
            case NEVER_SLEEPS -> {
                horse(level, ground);
                yield ground;
            }
            case NEST -> {
                BlockPos origin = Camps.place(level, NEST_TEMPLATE, ground, Rotation.NONE);
                JsonArray centre = PLAN.get().getAsJsonObject("nest").getAsJsonArray("centre");
                yield origin.offset(Prologue.vector(centre));
            }
            case FIRST_GATE -> gate(level, Objects.requireNonNull(hollowing).start());
            default -> throw new IllegalStateException("errands have no wayside place " + name);
        };
        progress.place(name, at);
        progress.place(name + BUILT, at);
        progress.setDirty();
        LOGGER.info("the errand's {} stands at {}", name, at);
        Journal.syncAll(server);
        hollowing = null;
        return true;
    }

    private static BlockPos den(MinecraftServer server, ServerLevel level, BlockPos ground) {
        BlockPos from = Progress.of(server).place(Caravan.NAME).orElse(ground);
        Vec3 away = Vec3.atLowerCornerOf(ground.subtract(from)).multiply(1, 0, 1);
        Vec3 on = away.lengthSqr() < 1 ? new Vec3(1, 0, 0) : away.normalize();
        for (int index = 0; index < PRINTS.length; index++) {
            BlockPos print = standing(level, BlockPos.containing(Vec3.atLowerCornerOf(ground).add(on.scale(PRINTS_BEYOND[index]))));
            Clues.print(level, print, PRINTS[index]);
        }
        BlockPos hole = ground.below();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            level.setBlock(hole.relative(side), Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(hole.below(), Blocks.ROOTED_DIRT.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(hole, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        Fox fox = EntityType.FOX.create(level);
        if (fox != null) {
            fox.moveTo(hole.getX() + 0.5, hole.getY(), hole.getZ() + 0.5, 0, 0);
            fox.setPersistenceRequired();
            level.addFreshEntity(fox);
        }
        Clues.marker(level, hole, DEN_CLUE);
        return hole;
    }

    private static void horse(ServerLevel level, BlockPos ground) {
        Horse horse = EntityType.HORSE.create(level);
        if (horse == null) {
            return;
        }
        horse.moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
        horse.setVariant(Variant.WHITE);
        horse.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HORSE_HEALTH);
        horse.setHealth(horse.getMaxHealth());
        horse.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(HORSE_SPEED);
        horse.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(HORSE_JUMP);
        horse.setPersistenceRequired();
        horse.addTag(NEVER_SLEEPS_TAG);
        level.addFreshEntity(horse);
    }

    private static boolean city(ServerLevel level, BlockPos site) {
        if (hollowing == null) {
            List<Holder<Structure>> kinds = List.of(level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, CITY)));
            Optional<StructureStart> found = start(level, kinds, site);
            if (found.isEmpty()) {
                LOGGER.error("the first gate failed: no Ancient City would stand near {}, so its book lies on the surface", site);
                lectern(level, standing(level, site));
                Progress.of(level.getServer()).place(FIRST_GATE, standing(level, site));
                Progress.of(level.getServer()).place(FIRST_GATE + BUILT, standing(level, site));
                return false;
            }
            hollowing = new Hollowing(found.get(), new ArrayDeque<>(found.get().getPieces().stream().map(StructurePiece::getBoundingBox).toList()));
        }
        int budget = HOLLOW_BUDGET;
        while (!hollowing.left().isEmpty() && budget > 0) {
            budget -= hollow(level, hollowing.left().poll());
        }
        if (!hollowing.left().isEmpty()) {
            return false;
        }
        place(level, hollowing.start());
        return true;
    }

    private static BlockPos gate(ServerLevel level, StructureStart raised) {
        BoundingBox whole = raised.getBoundingBox();
        BoundingBox first = raised.getPieces().getFirst().getBoundingBox().inflatedBy(HEART_REACH);
        BoundingBox heart = new BoundingBox(Math.max(first.minX(), whole.minX()), whole.minY(), Math.max(first.minZ(), whole.minZ()), Math.min(first.maxX(), whole.maxX()),
                whole.maxY(), Math.min(first.maxZ(), whole.maxZ()));
        List<BlockPos> frame = BlockPos.betweenClosedStream(heart).filter(pos -> level.getBlockState(pos).is(Blocks.REINFORCED_DEEPSLATE)).map(BlockPos::immutable)
                .toList();
        BlockPos centre = frame.isEmpty() ? heart.getCenter().atY(heart.minY())
                : new BlockPos((int) frame.stream().mapToInt(BlockPos::getX).average().orElseThrow(), frame.stream().mapToInt(BlockPos::getY).min().orElseThrow(),
                        (int) frame.stream().mapToInt(BlockPos::getZ).average().orElseThrow());
        boolean across = frame.stream().map(BlockPos::getZ).distinct().count() == 1;
        BlockPos lectern = before(level, centre, across ? Direction.SOUTH : Direction.EAST).or(() -> before(level, centre, across ? Direction.NORTH : Direction.WEST))
                .orElse(centre);
        LOGGER.info("the first gate's frame of {} stones stands at {} in {}; its lectern at {}", frame.size(), centre, heart, lectern);
        lectern(level, lectern);
        return lectern;
    }

    private static void lectern(ServerLevel level, BlockPos at) {
        level.setBlock(at, Blocks.LECTERN.defaultBlockState(), Block.UPDATE_ALL);
        Library.get().book(GATE_BOOK).ifPresent(book -> LecternBlock.tryPlaceBook(null, level, at, level.getBlockState(at), book.written()));
    }

    private static Optional<BlockPos> before(ServerLevel level, BlockPos frame, Direction facing) {
        for (int out = BEFORE_GATE; out <= BEFORE_GATE * 3; out++) {
            BlockPos column = frame.relative(facing, out);
            for (int down = -2; down <= HEART_REACH / 2; down++) {
                BlockPos at = column.below(down);
                if (level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir() && level.getBlockState(at.below()).isSolidRender(level, at.below())) {
                    return Optional.of(at.immutable());
                }
            }
        }
        return Optional.empty();
    }

    @SubscribeEvent
    public static void onHorseJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Horse horse && horse.getTags().contains(NEVER_SLEEPS_TAG) && !event.getLevel().isClientSide()) {
            horse.goalSelector.addGoal(0, new LookAtPlayerGoal(horse, Player.class, LOOK_BLOCKS, 1));
        }
    }

    @SubscribeEvent
    public static void onHorseDeath(LivingDeathEvent event) {
        if (event.getEntity().getTags().contains(NEVER_SLEEPS_TAG) && event.getEntity().level() instanceof ServerLevel level
                && !(event.getEntity() instanceof Horse horse && horse.isTamed())) {
            Progress progress = Progress.of(level.getServer());
            progress.places.remove(NEVER_SLEEPS + BUILT);
            progress.setDirty();
        }
    }

    private static void seat(MinecraftServer server, ServerLevel level, String person, BlockPos near, BlockPos facing) {
        Progress progress = Progress.of(server);
        List<BlockPos> taken = progress.places.entrySet().stream().filter(entry -> entry.getKey().startsWith("seat_") && !entry.getKey().equals("seat_" + person))
                .map(Map.Entry::getValue).toList();
        BlockPos ground = null;
        for (int dx = -STAND_OFF * 2; dx <= STAND_OFF * 2; dx += STAND_OFF) {
            for (int dz = -STAND_OFF * 2; dz <= STAND_OFF * 2; dz += STAND_OFF) {
                BlockPos spot = standing(level, near.offset(dx, 0, dz));
                if ((dx != 0 || dz != 0) && (ground == null || spot.getY() < ground.getY()) && level.getFluidState(spot.below()).isEmpty()
                        && taken.stream().noneMatch(seat -> seat.closerThan(spot, STAND_OFF))) {
                    ground = spot;
                }
            }
        }
        if (ground == null) {
            ground = standing(level, near.offset(STAND_OFF, 0, 0));
        }
        progress.place("seat_" + person, ground);
        progress.yaws.put("seat_" + person, Frost.yaw(Vec3.atLowerCornerOf(facing.subtract(ground)).multiply(1, 0, 1)));
        progress.setDirty();
        Journal.syncAll(server);
    }

    private static BlockPos standing(ServerLevel level, BlockPos at) {
        level.getChunk(at);
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
    }

    private static void cairns(MinecraftServer server, ServerLevel level, BlockPos site) {
        Progress progress = Progress.of(server);
        BlockPos origin = Camps.place(level, RING, site, Rotation.NONE);
        JsonObject plan = PLAN.get().getAsJsonObject("cairns");
        BlockPos centre = origin.offset(Prologue.vector(plan.getAsJsonArray("centre")));
        BlockPos drifa = origin.offset(Prologue.vector(plan.getAsJsonArray("drifa")));
        progress.place(CAIRNS_ORIGIN, origin);
        progress.place(CAIRNS, centre);
        progress.place("seat_cairns_drifa", drifa);
        progress.yaws.put("seat_cairns_drifa", Frost.yaw(Vec3.atLowerCornerOf(centre.subtract(drifa))));
        progress.ward(CAIRNS, level.getStructureManager().getOrCreate(RING).getBoundingBox(Camps.settings(level, RING, Rotation.NONE), origin));
        progress.setDirty();
        Camps.snow(level, centre, PLAN.get().getAsJsonObject("cairns").getAsJsonArray("size").get(0).getAsInt());
        LOGGER.info("the cairn ring stands at {}", origin);
        Journal.syncAll(server);
    }

    private static void seal(MinecraftServer server, ServerLevel level, BlockPos site) {
        Progress progress = Progress.of(server);
        BlockPos from = progress.place(Caravan.NAME).orElse(site);
        Vec3 toward = Vec3.atLowerCornerOf(site.subtract(from)).multiply(1, 0, 1);
        Rotation turn = Frost.facing(toward.lengthSqr() < 1 ? new Vec3(0, 0, 1) : toward.normalize());
        BlockPos origin = Camps.place(level, RUIN, site, turn);
        progress.place(SEAL_ORIGIN, origin);
        progress.yaws.put(TURN + SEAL, (float) turn.ordinal());
        progress.place(SEAL, world(level, origin, turn, PLAN.get().getAsJsonObject("seal").getAsJsonArray("front")));
        progress.ward(SEAL, level.getStructureManager().getOrCreate(RUIN).getBoundingBox(Camps.settings(level, RUIN, turn), origin));
        progress.setDirty();
        JsonArray marks = PLAN.get().getAsJsonObject("seal").getAsJsonArray("marks");
        for (int index = 0; index < MARKS.length; index++) {
            Clues.marker(level, world(level, origin, turn, marks.get(index).getAsJsonArray()), MARKS[index]);
        }
        LOGGER.info("the Keepers' sealed house stands at {}, turned {}", origin, turn);
        Journal.syncAll(server);
    }

    private static void crumble(MinecraftServer server, ServerLevel level) {
        Progress progress = Progress.of(server);
        BlockPos origin = progress.place(SEAL_ORIGIN).orElseThrow();
        Rotation turn = Rotation.values()[progress.yaws.getOrDefault(TURN + SEAL, 0f).intValue()];
        for (JsonElement stone : PLAN.get().getAsJsonObject("seal").getAsJsonArray("door")) {
            BlockPos at = world(level, origin, turn, stone.getAsJsonArray());
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(at)), at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5,
                    16, 0.3, 0.3, 0.3, 0.05);
            level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        level.playSound(null, origin, SoundEvents.SAND_BREAK, SoundSource.BLOCKS, 1, 0.6f);
        progress.place(SEAL_OPEN, origin);
        progress.setDirty();
        LOGGER.info("the Keepers' seal crumbles at {}", origin);
    }

    private static BlockPos world(ServerLevel level, BlockPos origin, Rotation turn, JsonArray local) {
        return origin.offset(StructureTemplate.calculateRelativePosition(Camps.settings(level, RUIN, turn), Prologue.vector(local)));
    }
}

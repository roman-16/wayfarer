package io.github.roman16.wayfarer.story;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Bundled;
import io.github.roman16.wayfarer.Camps;
import io.github.roman16.wayfarer.ShrinePedestalBlockEntity;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

final class Act3Halls {
    static final String WARD = "ward";
    static final String WARD_STONE = "ward_stone";
    static final String WARD_CHAMPION = "ward_champion";
    static final String WARD_LINES = "ward_lines";
    static final String MANSION_HALL = "mansion_hall";
    static final String MANSION = "mansion";
    static final String MANSION_STAND = "mansion_stand";
    static final String MANSION_CHAIR = "mansion_chair";
    static final String SCRIBES = "scribes";
    static final String SCRIBES_STONE = "scribes_stone";
    static final String GRAVEYARD = "graveyard";
    static final String GRAVEYARD_STONE = "graveyard_stone";
    private static final ResourceLocation GATEHOUSE = Wayfarer.id("act3/gatehouse");
    private static final ResourceLocation HOUSE = Wayfarer.id("act3/house");
    private static final ResourceLocation GRAVES = Wayfarer.id("act3/graves");
    private static final ResourceLocation TENT = Wayfarer.id("caravan/tent");
    private static final ResourceLocation SHADOW = Wayfarer.id("shrine/magical");
    private static final HallSite WARD_SITE = new HallSite(WARD, Wayfarer.id("shrine/corrupted"), WARD, 3);
    private static final HallSite MANSION_SITE = new HallSite(MANSION_HALL, SHADOW, MANSION_HALL, 3);
    private static final HallSite SCRIBES_SITE = new HallSite(SCRIBES, Wayfarer.id("shrine/cryptic"), SCRIBES, 3);
    private static final HallSite GRAVEYARD_SITE = new HallSite(GRAVEYARD, Wayfarer.id("shrine/undead"), GRAVEYARD, 3);
    private static final Bundled<JsonObject> PLAN = Bundled.json("/wayfarer/act3.json");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BUILT = "_built";
    private static final String TURN = "_turn";
    private static final String GATE_OPEN = "ward_gate_open";
    private static final String WARD_QUEST = "the_ward";
    private static final String WARD_DUEL = "the_champion";
    private static final int COVER = 10;
    private static final int BESIDE = 2;
    private static final int CAMP = 24;
    private static final int TENTS = 3;
    private static final int BEYOND_CAMP = 16;
    private static final int FIRE_AWAY = 2;
    private static final int HOUSE_AWAY = 96;

    private Act3Halls() {}

    static void find(MinecraftServer server, String name) {
        BlockPos from = Caravan.last(server);
        site(name).find(server, from);
    }

    private static HallSite site(String name) {
        return switch (name) {
            case WARD, WARD_STONE, WARD_CHAMPION, WARD_LINES -> WARD_SITE;
            case MANSION_HALL, MANSION, MANSION_STAND -> MANSION_SITE;
            case SCRIBES, SCRIBES_STONE -> SCRIBES_SITE;
            case GRAVEYARD, GRAVEYARD_STONE -> GRAVEYARD_SITE;
            default -> throw new IllegalStateException("act 3 has no hall place " + name);
        };
    }

    static void reset() {
        WARD_SITE.reset();
        MANSION_SITE.reset();
        SCRIBES_SITE.reset();
        GRAVEYARD_SITE.reset();
    }

    static boolean ready(MinecraftServer server, String name) {
        Progress progress = Progress.of(server);
        return switch (name) {
            case WARD, WARD_STONE, WARD_CHAMPION -> progress.place(WARD + BUILT).isPresent();
            case WARD_LINES -> progress.place(WARD_LINES).isPresent();
            case MANSION_HALL, MANSION, MANSION_STAND -> progress.place(MANSION + BUILT).isPresent();
            case SCRIBES, SCRIBES_STONE -> progress.place(SCRIBES + BUILT).isPresent();
            case GRAVEYARD, GRAVEYARD_STONE -> progress.place(GRAVEYARD + BUILT).isPresent();
            default -> false;
        };
    }

    static void tick(MinecraftServer server) {
        Progress progress = Progress.of(server);
        ServerLevel level = Engine.overworld(server);
        for (HallSite site : new HallSite[] {WARD_SITE, MANSION_SITE, SCRIBES_SITE, GRAVEYARD_SITE}) {
            site.tick(server);
        }
        build(server, level, WARD_SITE, WARD, Act3Halls::ward);
        build(server, level, MANSION_SITE, MANSION, Act3Halls::mansion);
        build(server, level, SCRIBES_SITE, SCRIBES, Act3Halls::scribes);
        build(server, level, GRAVEYARD_SITE, GRAVEYARD, Act3Halls::graveyard);
        if (progress.place(WARD + BUILT).isPresent() && progress.place(GATE_OPEN).isEmpty() && duelWon(progress)) {
            open(server, level);
        }
    }

    private interface Builder {
        void build(MinecraftServer server, ServerLevel level, BlockPos top, BlockPos stone);
    }

    private static void build(MinecraftServer server, ServerLevel level, HallSite site, String name, Builder builder) {
        Progress progress = Progress.of(server);
        site.entrance(progress).filter(top -> progress.place(name + BUILT).isEmpty() && Halls.loaded(level, top)).ifPresent(top -> {
            BlockPos stone = Halls.stone(level, site.low(progress).orElseThrow(), site.high(progress).orElseThrow()).orElse(top);
            builder.build(server, level, top, stone);
            progress.place(name + BUILT, top);
            progress.setDirty();
            Halls.release(level, top);
            Journal.syncAll(server);
        });
    }

    private static Vec3 outward(MinecraftServer server, BlockPos top) {
        BlockPos from = Progress.of(server).place(Caravan.NAME).orElse(top.offset(0, 0, -1));
        Vec3 back = Vec3.atLowerCornerOf(from.subtract(top)).multiply(1, 0, 1);
        return back.lengthSqr() < 1 ? new Vec3(0, 0, -1) : back.normalize();
    }

    private static void seat(Progress progress, String name, BlockPos stone, BlockPos top) {
        Vec3 toward = Vec3.atLowerCornerOf(top.subtract(stone)).multiply(1, 0, 1);
        Vec3 inward = toward.lengthSqr() < 1 ? new Vec3(0, 0, 1) : toward.normalize();
        progress.place("seat_" + name, BlockPos.containing(Vec3.atLowerCornerOf(stone).add(inward.scale(BESIDE))));
        progress.yaws.put("seat_" + name, Frost.yaw(inward.scale(-1)));
    }

    private static void ward(MinecraftServer server, ServerLevel level, BlockPos top, BlockPos stone) {
        Progress progress = Progress.of(server);
        Vec3 out = outward(server, top);
        StructureTemplate template = level.getStructureManager().getOrCreate(GATEHOUSE);
        BlockPos centre = Camps.dryBeside(level, top, out, COVER + template.getSize().getZ() / 2.0 + BESIDE, template.getSize().getX() / 2);
        Rotation turn = Frost.facing(Vec3.atLowerCornerOf(top.subtract(centre)).multiply(1, 0, 1).normalize());
        BlockPos origin = Camps.place(level, GATEHOUSE, centre, turn);
        StructurePlaceSettings settings = Camps.settings(level, GATEHOUSE, turn);
        JsonObject plan = PLAN.get().getAsJsonObject("gatehouse");
        progress.place(WARD + "_origin", origin);
        progress.yaws.put(WARD + TURN, (float) turn.ordinal());
        progress.place(WARD_CHAMPION, origin.offset(StructureTemplate.calculateRelativePosition(settings, Prologue.vector(plan.getAsJsonArray("champion")))));
        progress.place(WARD, progress.place(WARD_CHAMPION).orElseThrow());
        progress.ward(WARD, template.getBoundingBox(settings, origin));
        Vec3 across = new Vec3(-out.z, 0, out.x);
        for (int index = 0; index < TENTS; index++) {
            Vec3 at = Vec3.atLowerCornerOf(centre).add(out.scale(CAMP)).add(across.scale((index - 1) * 9));
            Camps.place(level, TENT, BlockPos.containing(at), Frost.facing(out.scale(-1)));
        }
        lines(progress, level, centre, out);
        progress.place(WARD_STONE, stone);
        seat(progress, "ward_warden", stone, top);
        progress.setDirty();
        LOGGER.info("the Grass Kingdom's gatehouse stands at {}, its stone at {}", origin, stone);
    }

    private static void lines(Progress progress, ServerLevel level, BlockPos centre, Vec3 out) {
        BlockPos far = BlockPos.containing(Vec3.atLowerCornerOf(centre).add(out.scale(CAMP + BEYOND_CAMP)));
        level.getChunk(far);
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, far);
        BlockPos fire = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(Vec3.atLowerCornerOf(ground).add(out.scale(FIRE_AWAY))));
        level.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState(), Block.UPDATE_ALL);
        progress.place(WARD_LINES, ground);
        progress.place("seat_" + WARD_LINES, ground);
        progress.yaws.put("seat_" + WARD_LINES, Frost.yaw(out.scale(-1)));
    }

    static boolean past(MinecraftServer server, ServerLevel dream) {
        Progress progress = Progress.of(server);
        Rotation turn = Rotation.values()[progress.yaws.getOrDefault(MANSION + TURN, 0f).intValue()];
        Camps.build(dream, HOUSE, progress.place(MANSION + "_origin").orElseThrow(), turn);
        return true;
    }

    private static boolean duelWon(Progress progress) {
        return progress.choice(WARD_QUEST, WARD_DUEL).isPresent();
    }

    private static void open(MinecraftServer server, ServerLevel level) {
        Progress progress = Progress.of(server);
        BlockPos origin = progress.place(WARD + "_origin").orElseThrow();
        if (!level.isLoaded(origin)) {
            return;
        }
        Rotation turn = Rotation.values()[progress.yaws.getOrDefault(WARD + TURN, 0f).intValue()];
        StructurePlaceSettings settings = Camps.settings(level, GATEHOUSE, turn);
        for (JsonElement bar : PLAN.get().getAsJsonObject("gatehouse").getAsJsonArray("gate")) {
            BlockPos at = origin.offset(StructureTemplate.calculateRelativePosition(settings, Prologue.vector(bar.getAsJsonArray())));
            level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        level.playSound(null, origin, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1, 0.5f);
        progress.place(GATE_OPEN, origin);
        progress.setDirty();
        LOGGER.info("the Grass Kingdom opens its gate at {}", origin);
    }

    private static void mansion(MinecraftServer server, ServerLevel level, BlockPos top, BlockPos stone) {
        Progress progress = Progress.of(server);
        Places.empty(level, stone);
        Vec3 out = outward(server, top);
        Rotation turn = Frost.facing(out.scale(-1));
        StructureTemplate template = level.getStructureManager().getOrCreate(HOUSE);
        BlockPos centre = Camps.dryBeside(level, top, out.yRot((float) Math.toRadians(45)), HOUSE_AWAY, template.getSize().getX() / 2);
        BlockPos origin = Camps.place(level, HOUSE, centre, turn);
        StructurePlaceSettings settings = Camps.settings(level, HOUSE, turn);
        JsonObject plan = PLAN.get().getAsJsonObject("house");
        BlockPos stand = local(origin, settings, plan, "stand");
        level.setBlock(stand, Wayfarer.SHRINE_PEDESTAL.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(stand) instanceof ShrinePedestalBlockEntity pedestal) {
            pedestal.hold(Optional.of(SHADOW));
        }
        progress.place(MANSION, local(origin, settings, plan, "front"));
        progress.place(MANSION_STAND, stand);
        JsonArray cage = plan.getAsJsonArray("cage");
        for (int captive = 0; captive < cage.size(); captive++) {
            BlockPos seat = origin.offset(StructureTemplate.calculateRelativePosition(settings, Prologue.vector(cage.get(captive).getAsJsonArray())));
            progress.place("seat_mansion_captive_" + (captive + 1), seat);
            progress.yaws.put("seat_mansion_captive_" + (captive + 1), Frost.yaw(Vec3.atLowerCornerOf(stand.subtract(seat)).multiply(1, 0, 1)));
        }
        progress.ward(MANSION, template.getBoundingBox(settings, origin));
        Clues.marker(level, local(origin, settings, plan, "desk"), "ledger");
        progress.place(MANSION_CHAIR, local(origin, settings, plan, "chair"));
        progress.place(MANSION + "_origin", origin);
        progress.yaws.put(MANSION + TURN, (float) turn.ordinal());
        Clues.marker(level, local(origin, settings, plan, "chair"), "chair");
        Clues.print(level, top, "tracks");
        progress.setDirty();
        LOGGER.info("the Gazebound house stands at {}, the Shadow Circle's shard on a stand at {}", origin, stand);
    }

    private static BlockPos local(BlockPos origin, StructurePlaceSettings settings, JsonObject plan, String key) {
        return origin.offset(StructureTemplate.calculateRelativePosition(settings, Prologue.vector(plan.getAsJsonArray(key))));
    }

    private static void scribes(MinecraftServer server, ServerLevel level, BlockPos top, BlockPos stone) {
        Progress progress = Progress.of(server);
        progress.place(SCRIBES_STONE, stone);
        progress.setDirty();
        LOGGER.info("the Scribes' stone is at {}", stone);
    }

    private static void graveyard(MinecraftServer server, ServerLevel level, BlockPos top, BlockPos stone) {
        Progress progress = Progress.of(server);
        Vec3 out = outward(server, top);
        StructureTemplate template = level.getStructureManager().getOrCreate(GRAVES);
        BlockPos centre = Camps.dryBeside(level, top, out, COVER + template.getSize().getZ() / 2.0 + BESIDE, template.getSize().getX() / 2);
        Rotation turn = Frost.facing(Vec3.atLowerCornerOf(top.subtract(centre)).multiply(1, 0, 1).normalize());
        BlockPos origin = Camps.place(level, GRAVES, centre, turn);
        StructurePlaceSettings settings = Camps.settings(level, GRAVES, turn);
        BlockPos empty = local(origin, settings, PLAN.get().getAsJsonObject("graves"), "empty");
        Clues.marker(level, empty, "wend_grave");
        progress.place(GRAVEYARD, empty);
        progress.place(GRAVEYARD_STONE, stone);
        progress.ward(GRAVEYARD, template.getBoundingBox(settings, origin));
        progress.setDirty();
        LOGGER.info("the Grave Keepers' graveyard lies at {}, the empty grave at {}", origin, empty);
    }
}

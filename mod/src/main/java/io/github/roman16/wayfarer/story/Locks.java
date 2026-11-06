package io.github.roman16.wayfarer.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Bundled;
import io.github.roman16.wayfarer.Camps;
import io.github.roman16.wayfarer.Footing;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

final class Locks {
    static final String NAME = "locks";
    static final String STONE = "locks_stone";
    private static final String QUEST = "the_locks";
    private static final String HEIR_STEP = "the_heir";
    private static final String SHARD_STEP = "the_shard";
    private static final String ORIGIN = "locks_origin";
    private static final String TURN = "locks_turn";
    private static final String OPENED = "opened";
    private static final String MAN = Wayfarer.MODID + "_heirs_man";
    private static final String MARK = "mark";
    private static final String HEIR = "heir";
    private static final String GIFT = "stone_sight";
    private static final ResourceLocation TEMPLATE = Wayfarer.id("locks/house");
    private static final HallSite SITE = new HallSite("locks_hall", Wayfarer.id("hall/rogue"), NAME, 2);
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Bundled<JsonObject> PLAN = Bundled.json("/wayfarer/locks.json");
    private static final int AWAY = 32;
    private static final int BESIDE = 2;
    private static final int CLOSE = 8;
    private static final int COVER = 10;
    private static final int MEN = 2;
    private static final int SIDE = 3;

    private Locks() {}

    static void find(MinecraftServer server) {
        Progress progress = Progress.of(server);
        SITE.find(server, Caravan.last(server));
    }

    static void reset() {
        SITE.reset();
    }

    static void unbuild(MinecraftServer server) {
        Progress progress = Progress.of(server);
        SITE.forget(progress);
        List.of(ORIGIN, STONE).forEach(progress.places::remove);
    }

    static boolean ready(MinecraftServer server, String name) {
        return Progress.of(server).place(name.equals(STONE) ? STONE : ORIGIN).isPresent();
    }

    static void tick(MinecraftServer server) {
        SITE.tick(server);
        Progress progress = Progress.of(server);
        ServerLevel level = Engine.overworld(server);
        SITE.entrance(progress).filter(top -> (progress.place(ORIGIN).isEmpty() || progress.place(STONE).isEmpty()) && HallSearch.loaded(level, top))
                .ifPresent(top -> {
                    if (progress.place(ORIGIN).isEmpty()) {
                        house(server, level, top);
                    }
                    if (progress.place(STONE).isEmpty()) {
                        hall(server, level);
                    }
                    HallSearch.release(level, top);
                });
        progress.place(STONE).filter(stone -> level.areEntitiesLoaded(ChunkPos.asLong(stone))).ifPresent(stone -> keep(server, level, stone));
    }

    private static Optional<String> step(MinecraftServer server) {
        return Engine.active(server).stream().filter(active -> active.quest().id().equals(QUEST)).map(active -> active.step().id()).findFirst();
    }

    private static void keep(MinecraftServer server, ServerLevel level, BlockPos stone) {
        Optional<String> step = step(server);
        boolean waiting = step.filter(id -> id.equals(HEIR_STEP) || id.equals(SHARD_STEP)).isPresent();
        Optional<Entity> heir = People.find(server, HEIR).filter(body -> body.level() == level);
        List<Entity> men = level.getEntities((Entity) null, new AABB(stone).inflate(AWAY), entity -> entity.getTags().contains(MAN));
        if (waiting && men.isEmpty()) {
            men = men(level, stone);
        } else if (!waiting && heir.isEmpty() && !men.isEmpty()) {
            men.forEach(Entity::discard);
            return;
        }
        boolean seen = step.filter(HEIR_STEP::equals).isPresent() && heir
                .filter(body -> level.players().stream().noneMatch(player -> player.distanceToSqr(body) <= (double) CLOSE * CLOSE)).isPresent();
        heir.filter(body -> body.hasGlowingTag() != seen).ifPresent(body -> body.setGlowingTag(seen));
        men.stream().filter(man -> man.hasGlowingTag() != seen).forEach(man -> man.setGlowingTag(seen));
    }

    private static List<Entity> men(ServerLevel level, BlockPos stone) {
        Progress progress = Progress.of(level.getServer());
        Vec3 seat = progress.place("seat_locks_heir").map(Vec3::atBottomCenterOf).orElse(Vec3.atBottomCenterOf(stone));
        Vec3 toward = Vec3.atBottomCenterOf(stone).subtract(seat).multiply(1, 0, 1);
        Vec3 facing = toward.lengthSqr() < 1 ? new Vec3(0, 0, 1) : toward.normalize();
        Vec3 across = new Vec3(-facing.z, 0, facing.x);
        for (int index = 0; index < MEN; index++) {
            Mob man = EntityType.VINDICATOR.create(level);
            if (man == null) {
                continue;
            }
            Vec3 at = seat.add(across.scale(index == 0 ? SIDE : -SIDE));
            man.moveTo(at.x, seat.y, at.z, Frost.yaw(facing.scale(-1)), 0);
            man.setYHeadRot(Frost.yaw(facing.scale(-1)));
            man.setNoAi(true);
            man.setInvulnerable(true);
            man.setSilent(true);
            man.setPersistenceRequired();
            man.addTag(MAN);
            level.addFreshEntity(man);
        }
        return level.getEntities((Entity) null, new AABB(stone).inflate(AWAY), entity -> entity.getTags().contains(MAN));
    }

    private static void house(MinecraftServer server, ServerLevel level, BlockPos top) {
        Progress progress = Progress.of(server);
        BlockPos from = progress.place(Caravan.NAME).orElse(top.offset(0, 0, -1));
        Vec3 back = Vec3.atLowerCornerOf(from.subtract(top)).multiply(1, 0, 1);
        Vec3 outward = back.lengthSqr() < 1 ? new Vec3(0, 0, -1) : back.normalize();
        StructureTemplate template = level.getStructureManager().getOrCreate(TEMPLATE);
        BlockPos centre = Camps.dryBeside(level, top, outward, COVER + template.getSize().getZ() / 2.0 + BESIDE, template.getSize().getZ() / 2);
        Rotation turn = Frost.facing(Vec3.atLowerCornerOf(top.subtract(centre)).multiply(1, 0, 1).normalize());
        BlockPos origin = Camps.place(level, TEMPLATE, centre, turn);
        StructurePlaceSettings settings = Camps.settings(level, TEMPLATE, turn);
        progress.place(ORIGIN, origin);
        progress.yaws.put(TURN, (float) turn.ordinal());
        progress.place(NAME, world(progress, level, PLAN.get().getAsJsonArray("front")));
        for (String side : List.of("back", "front")) {
            BlockPos outside = world(progress, level, PLAN.get().getAsJsonArray(side));
            Direction out = Direction.getNearest(outside.getX() - centre.getX(), 0, outside.getZ() - centre.getZ());
            Footing.approach(level, outside.below().relative(out.getOpposite()), out);
        }
        progress.ward(NAME, template.getBoundingBox(settings, origin));
        progress.setDirty();
        for (JsonElement mark : PLAN.get().getAsJsonArray("marks")) {
            Clues.display(level, world(progress, level, mark.getAsJsonArray()), 1.02f, MARK);
        }
        Standing.keep(server, NAME);
        LOGGER.info("the Jungle Folk's lock house stands at {}, turned {}", origin, turn);
        Journal.syncAll(server);
    }

    private static void hall(MinecraftServer server, ServerLevel level) {
        Progress progress = Progress.of(server);
        BlockPos low = SITE.low(progress).orElseThrow();
        BlockPos high = SITE.high(progress).orElseThrow();
        Optional<BlockPos> found = HallSearch.stone(level, low, high);
        if (found.isEmpty()) {
            LOGGER.error("the locks failed: the Jungle Folk's hall between {} and {} has no calling stone", low, high);
            progress.place(STONE, SITE.entrance(progress).orElse(low));
            progress.setDirty();
            return;
        }
        BlockPos stone = found.get();
        Vec3 toward = Vec3.atLowerCornerOf(SITE.entrance(progress).orElse(stone).subtract(stone)).multiply(1, 0, 1);
        Vec3 inward = toward.lengthSqr() < 1 ? new Vec3(0, 0, 1) : toward.normalize();
        BlockPos seat = BlockPos.containing(Vec3.atLowerCornerOf(stone).add(inward.scale(BESIDE)));
        progress.place("seat_locks_heir", seat);
        progress.yaws.put("seat_locks_heir", Frost.yaw(inward));
        progress.place(STONE, stone);
        progress.setDirty();
        LOGGER.info("the Jungle Folk's stone is at {}; the heir will wait at {}", stone, seat);
    }

    static boolean asleep(ServerPlayer player, BlockPos stone) {
        MinecraftServer server = player.server;
        if (!Progress.of(server).place(STONE).filter(stone::equals).isPresent() || step(server).filter(id -> !id.equals(SHARD_STEP)).isEmpty()) {
            return false;
        }
        player.displayClientMessage(Component.translatable(step(server).filter(HEIR_STEP::equals).isPresent() ? "wayfarer.locks.watched" : "wayfarer.locks.asleep")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
        return true;
    }

    static void tick(MinecraftServer server, Engine.Active active) {
        ServerLevel level = Engine.overworld(server);
        Progress progress = Progress.of(server);
        if (progress.place(ORIGIN).isEmpty()) {
            return;
        }
        CompoundTag scratch = active.scratch();
        JsonObject plan = PLAN.get();
        JsonArray doors = plan.getAsJsonArray("doors");
        int opened = scratch.getInt(OPENED);
        boolean solved = switch (opened) {
            case 0 -> levers(progress, level, plan);
            case 1 -> word(progress, level, plan);
            case 2 -> plates(progress, level, plan);
            default -> true;
        };
        if (!solved) {
            return;
        }
        BlockPos door = world(progress, level, doors.get(Math.min(opened, doors.size() - 1)).getAsJsonArray());
        if (opened < doors.size()) {
            level.setBlock(door, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(door.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.playSound(null, door, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1, 0.6f);
            scratch.putInt(OPENED, opened + 1);
            progress.setDirty();
        }
        if (opened + 1 >= doors.size()) {
            progress.gifts.add(GIFT);
            progress.setDirty();
            Engine.complete(server, active);
        } else {
            Talk.say(active.quest().id(), Vec3.atCenterOf(door), active.step().lines("opens"), Engine.fill(progress));
        }
    }

    private static boolean levers(Progress progress, ServerLevel level, JsonObject plan) {
        List<BlockPos> down = plan.getAsJsonArray("down").asList().stream().map(xyz -> world(progress, level, xyz.getAsJsonArray())).toList();
        for (JsonElement lever : plan.getAsJsonArray("levers")) {
            BlockPos at = world(progress, level, lever.getAsJsonArray());
            BlockState state = level.getBlockState(at);
            if (!state.hasProperty(LeverBlock.POWERED) || state.getValue(LeverBlock.POWERED) != down.contains(at)) {
                return false;
            }
        }
        return true;
    }

    private static boolean word(Progress progress, ServerLevel level, JsonObject plan) {
        if (!(level.getBlockEntity(world(progress, level, plan.getAsJsonArray("sign"))) instanceof SignBlockEntity sign)) {
            return false;
        }
        String written = String.join(" ", List.of(sign.getFrontText().getMessages(false)).stream().map(Component::getString).toList()).toLowerCase(Locale.ROOT);
        return written.contains(plan.get("word").getAsString());
    }

    private static boolean plates(Progress progress, ServerLevel level, JsonObject plan) {
        for (JsonElement plate : plan.getAsJsonArray("plates")) {
            BlockState state = level.getBlockState(world(progress, level, plate.getAsJsonArray()));
            if (!state.hasProperty(PressurePlateBlock.POWERED) || !state.getValue(PressurePlateBlock.POWERED)) {
                return false;
            }
        }
        return true;
    }

    private static BlockPos world(Progress progress, ServerLevel level, JsonArray local) {
        Rotation turn = Rotation.values()[progress.yaws.getOrDefault(TURN, 0f).intValue()];
        return progress.place(ORIGIN).orElseThrow().offset(StructureTemplate.calculateRelativePosition(Camps.settings(level, TEMPLATE, turn), Prologue.vector(local)));
    }
}

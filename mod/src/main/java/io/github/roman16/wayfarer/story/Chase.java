package io.github.roman16.wayfarer.story;

import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

final class Chase {
    static final String CAUGHT = "caught";
    private static final String RIDER = Wayfarer.MODID + "_rider";
    private static final String HORSE = "chase_horse";
    private static final String MAN = "chase_man";
    private static final int TURN_STEP = 30;
    private static final int FULL_TURN = 360;
    private static final Logger LOGGER = LogUtils.getLogger();

    private Chase() {}

    static void tick(MinecraftServer server, Engine.Active active) {
        ServerLevel level = Engine.overworld(server);
        CompoundTag scratch = active.scratch();
        Balance.Chase balance = Balance.get().chase();
        Entity found = scratch.hasUUID(HORSE) ? level.getEntity(scratch.getUUID(HORSE)) : null;
        Optional<ServerPlayer> nearest = Engine.players(server).stream().filter(player -> player.isAlive() && player.level().dimension() == Level.OVERWORLD)
                .min(Comparator.comparingDouble(player -> found != null ? player.distanceToSqr(found) : 0));
        Entity horse = found;
        if (nearest.isEmpty()) {
            return;
        }
        ServerPlayer player = nearest.get();
        if (horse == null) {
            if (scratch.hasUUID(HORSE)) {
                end(server, active);
                return;
            }
            horse = mount(server, active, level, player);
            if (horse == null) {
                return;
            }
        }
        double distance = horse.distanceTo(player);
        if (distance <= balance.caughtBlocks()) {
            end(server, active);
            return;
        }
        double pace = distance <= balance.nearBlocks() ? balance.fastSixteenths() / 16.0 : distance <= balance.farBlocks() ? balance.slowSixteenths() / 16.0 : 0;
        if (pace <= 0) {
            return;
        }
        Vec3 away = horse.position().subtract(player.position()).multiply(1, 0, 1);
        Vec3 way = away.lengthSqr() < 1 ? new Vec3(1, 0, 0) : away.normalize();
        for (int turned = 0; turned < FULL_TURN; turned += TURN_STEP) {
            Vec3 bent = way.yRot((float) Math.toRadians(turned % (2 * TURN_STEP) == 0 ? turned / 2 : -turned / 2));
            Vec3 next = horse.position().add(bent.scale(pace));
            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(next));
            if (level.getFluidState(ground.below()).isEmpty() && level.getFluidState(ground).isEmpty() && Math.abs(ground.getY() - horse.getY()) <= 1) {
                float yaw = (float) Math.toDegrees(Math.atan2(-bent.x, bent.z));
                horse.moveTo(next.x, ground.getY(), next.z, yaw, 0);
                horse.setYHeadRot(yaw);
                return;
            }
        }
    }

    private static Entity mount(MinecraftServer server, Engine.Active active, ServerLevel level, ServerPlayer player) {
        Horse horse = EntityType.HORSE.create(level);
        Pillager man = EntityType.PILLAGER.create(level);
        if (horse == null || man == null) {
            return null;
        }
        Vec3 side = player.getLookAngle().multiply(1, 0, 1).normalize().scale(Balance.get().chase().startBlocks());
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(player.position().add(side)));
        horse.moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, 0, 0);
        horse.setNoAi(true);
        horse.setInvulnerable(true);
        horse.setPersistenceRequired();
        horse.addTag(RIDER);
        man.moveTo(horse.getX(), horse.getY(), horse.getZ(), 0, 0);
        man.setNoAi(true);
        man.setInvulnerable(true);
        man.setPersistenceRequired();
        man.addTag(RIDER);
        level.addFreshEntity(horse);
        level.addFreshEntity(man);
        man.startRiding(horse, true);
        active.scratch().putUUID(HORSE, horse.getUUID());
        active.scratch().putUUID(MAN, man.getUUID());
        Progress.of(server).setDirty();
        LOGGER.info("the rider breaks away at {}", ground);
        return horse;
    }

    private static void end(MinecraftServer server, Engine.Active active) {
        Progress progress = Progress.of(server);
        String key = active.quest().id() + "/" + active.step().id();
        if (progress.choices.containsKey(key)) {
            return;
        }
        ServerLevel level = Engine.overworld(server);
        Vec3 where = null;
        for (String part : new String[] {HORSE, MAN}) {
            if (active.scratch().hasUUID(part)) {
                Entity found = level.getEntity(active.scratch().getUUID(part));
                if (found != null) {
                    where = found.position();
                    found.discard();
                }
            }
        }
        progress.choices.put(key, CAUGHT);
        progress.setDirty();
        Talk.say(active.quest().id(), where, active.step().lines(CAUGHT), Engine.fill(progress));
        Talk.then(active.quest().id(), () -> Engine.complete(server, active));
    }
}

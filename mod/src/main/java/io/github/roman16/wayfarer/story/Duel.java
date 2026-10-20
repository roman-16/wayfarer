package io.github.roman16.wayfarer.story;

import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.slf4j.Logger;

public final class Duel {
    static final String WON = "won";
    private static final String FOE = Wayfarer.MODID + "_duel";
    private static final String BODY = "duel_body";
    private static final String SCALE = "scale";
    private static final String NIGHT = "night";
    private static final String RISES = "rises";
    private static final String BURNS = "burns";
    private static final int BURN_REACH = 5;
    private static final String IMPROVED_MOBS = "IMFlags";
    private static final List<String> IMPROVED_MOBS_DONE = List.of("GearEnchanted", "ModifiedArmor", "ModifiedAttributes", "ModifiedHeld");
    private static final Logger LOGGER = LogUtils.getLogger();

    private Duel() {}

    static void tick(MinecraftServer server, Engine.Active active) {
        Story.Step step = active.step();
        ServerLevel level = Engine.overworld(server);
        CompoundTag scratch = active.scratch();
        Optional<BlockPos> at = Places.place(server, step.text("at"));
        if (at.isEmpty() || !level.areEntitiesLoaded(ChunkPos.asLong(at.get()))) {
            return;
        }
        Entity foe = scratch.hasUUID(BODY) ? level.getEntity(scratch.getUUID(BODY)) : null;
        if (foe == null && (!step.spec().has(NIGHT) || level.isNight())) {
            spawn(server, active, level, at.get());
        }
    }

    private static void spawn(MinecraftServer server, Engine.Active active, ServerLevel level, BlockPos at) {
        Story.Step step = active.step();
        Entity created = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(step.text("foe"))).create(level);
        if (!(created instanceof Mob foe)) {
            return;
        }
        Balance.Duel balance = Balance.get().duel();
        long team = Math.max(1, Engine.players(server).stream().filter(player -> player.level().dimension() == Level.OVERWORLD).count());
        foe.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        EventHooks.finalizeMobSpawn(foe, level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
        foe.getAttribute(Attributes.MAX_HEALTH).setBaseValue(balance.healthBase() + balance.healthPerPlayer() * (team - 1));
        foe.setHealth(foe.getMaxHealth());
        if (step.spec().has(SCALE)) {
            foe.getAttribute(Attributes.SCALE).setBaseValue(step.number(SCALE));
        }
        if (foe instanceof Phantom) {
            foe.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        }
        foe.setCustomName(Component.literal(step.text("name")));
        foe.setCustomNameVisible(true);
        foe.setPersistenceRequired();
        foe.addTag(FOE);
        foe.addTag(FOE + "_" + active.quest().id());
        untouchedByImprovedMobs(foe);
        level.addFreshEntity(foe);
        active.scratch().putUUID(BODY, foe.getUUID());
        Talk.say(active.quest().id(), foe.position(), step.lines(RISES), Engine.fill(Progress.of(server)));
        Progress.of(server).setDirty();
        LOGGER.info("the duel's {} stands at {}", step.text("name"), at);
    }

    private static void burn(ServerLevel level, BlockPos at) {
        BlockPos.betweenClosed(at.offset(-BURN_REACH, -1, -BURN_REACH), at.offset(BURN_REACH, 2, BURN_REACH)).forEach(pos -> {
            if (level.getBlockState(pos).is(Blocks.DEAD_BUSH) || level.getBlockState(pos).is(Blocks.COBWEB)) {
                level.setBlock(pos, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
            }
        });
    }

    private static void untouchedByImprovedMobs(Mob foe) {
        CompoundTag saved = foe.saveWithoutId(new CompoundTag());
        CompoundTag flags = saved.getCompound(IMPROVED_MOBS);
        IMPROVED_MOBS_DONE.forEach(flag -> flags.putBoolean(flag, true));
        saved.put(IMPROVED_MOBS, flags);
        foe.load(saved);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level) || !event.getEntity().getTags().contains(FOE)) {
            return;
        }
        MinecraftServer server = level.getServer();
        UUID dead = event.getEntity().getUUID();
        Engine.active(server).stream().filter(active -> active.step().kind().equals("duel") && active.scratch().hasUUID(BODY)
                && active.scratch().getUUID(BODY).equals(dead)).findFirst().ifPresent(active -> {
                    Progress progress = Progress.of(server);
                    progress.choices.put(active.quest().id() + "/" + active.step().id(), WON);
                    progress.setDirty();
                    if (active.step().spec().has(BURNS)) {
                        Places.place(server, active.step().text(BURNS)).ifPresent(at -> burn(level, at));
                    }
                    Talk.say(active.quest().id(), event.getEntity().position(), active.step().lines(WON), Engine.fill(progress));
                    Talk.then(active.quest().id(), () -> Engine.complete(server, active));
                });
    }
}

package io.github.roman16.wayfarer.story;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Bundled;
import io.github.roman16.wayfarer.Camps;
import io.github.roman16.wayfarer.Journey;
import io.github.roman16.wayfarer.Shards;
import io.github.roman16.wayfarer.Wayfarer;
import io.github.roman16.wayfarer.worm.GreatWorms;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Instrument;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;

public final class SandSingers {
    static final String NAME = "sand";
    static final String LAIR = "sand_lair";
    private static final String CAMP_ORIGIN = "sand_origin";
    private static final String LAIR_SITE = "sand_lair_site";
    private static final String LAIR_BUILT = "sand_lair_built";
    private static final String HEALTH = "worm_health";
    private static final String WORM = "worm";
    private static final String UP_UNTIL = "worm_up_until";
    private static final String NEXT = "worm_next";
    private static final String WOKEN = "worm_woken";
    private static final String AWAY_SINCE = "worm_away_since";
    private static final String SPARED = "spared";
    private static final String KILLED = "killed";
    private static final ResourceLocation TEMPLATE = Wayfarer.id("sand/camp");
    private static final String EYE = "old";
    private static final ResourceLocation HALL = Wayfarer.id("hall/" + EYE);
    private static final HallSite SITE = new HallSite(NAME, HALL, NAME, 2);
    private static final ResourceLocation SING = ResourceLocation.withDefaultNamespace("sing_goat_horn");
    private static final String GECKOLIB = "geckolib";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Bundled<JsonObject> PLAN = Bundled.json("/wayfarer/sand.json");
    private static final int TICKS_PER_SECOND = 20;
    private static final int BESIDE = 24;
    private static final int BOWL = 12;
    private static final int BOWL_DEPTH = 3;
    private static final int BONES = 16;
    private static final int RISE_NEAR = 6;
    private static final int DUST = 96;
    private static final int STILL = 1;
    private static final int DRY_STEP = 16;
    private static final TagKey<Biome> SAND = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_desert"));
    private static final Map<UUID, Vec3> resting = new HashMap<>();
    private static final Map<UUID, Long> restingSince = new HashMap<>();

    private SandSingers() {}

    static void find(MinecraftServer server) {
        Progress progress = Progress.of(server);
        SITE.find(server, progress.place(Caravan.NAME).orElseGet(Engine.overworld(server)::getSharedSpawnPos));
    }

    static void reset() {
        SITE.reset();
    }

    static boolean ready(MinecraftServer server, String name) {
        Progress progress = Progress.of(server);
        return name.equals(LAIR) ? progress.place(LAIR_BUILT).isPresent() : progress.place(CAMP_ORIGIN).isPresent();
    }

    static void tick(MinecraftServer server) {
        SITE.tick(server);
        Progress progress = Progress.of(server);
        ServerLevel level = Engine.overworld(server);
        SITE.entrance(progress).filter(top -> progress.place(LAIR_SITE).isEmpty()).ifPresent(top -> {
            BlockPos from = progress.place(Caravan.NAME).orElse(top);
            Vec3 away = Vec3.atLowerCornerOf(top.subtract(from)).multiply(1, 0, 1).normalize();
            BlockPos lair = dry(level, BlockPos.containing(Vec3.atLowerCornerOf(top).add(away.scale(Balance.get().worm().lairBlocks()))), top);
            progress.place(LAIR_SITE, lair);
            progress.place(LAIR, lair);
            progress.setDirty();
        });
        SITE.entrance(progress).filter(top -> progress.place(CAMP_ORIGIN).isEmpty() && HallSearch.loaded(level, top)).ifPresent(top -> {
            camp(server, level, top);
            HallSearch.release(level, top);
        });
        progress.place(LAIR_SITE).filter(site -> progress.place(LAIR_BUILT).isEmpty() && HallSearch.loaded(level, site)).ifPresent(site -> {
            lair(server, level, site);
            HallSearch.release(level, site);
        });
    }

    private static BlockPos dry(ServerLevel level, BlockPos wanted, BlockPos hall) {
        Unseen.Heights heights = new Unseen.Heights(level);
        int reach = Balance.get().worm().lairBlocks();
        BlockPos fallback = null;
        for (int ring = 0; ring <= reach; ring += DRY_STEP) {
            int around = Math.max(1, (int) Math.ceil(2 * Math.PI * ring / DRY_STEP));
            for (int index = 0; index < around; index++) {
                double angle = 2 * Math.PI * index / around;
                int x = wanted.getX() + (int) Math.round(Math.cos(angle) * ring);
                int z = wanted.getZ() + (int) Math.round(Math.sin(angle) * ring);
                if (heights.wet(x, z) || Math.hypot(x - hall.getX(), z - hall.getZ()) < reach / 2.0) {
                    continue;
                }
                BlockPos at = new BlockPos(x, heights.surface(x, z), z);
                if (heights.biome(x, at.getY(), z).is(SAND)) {
                    return at;
                }
                fallback = fallback == null ? at : fallback;
            }
        }
        return fallback == null ? wanted : fallback;
    }

    private static void camp(MinecraftServer server, ServerLevel level, BlockPos top) {
        Progress progress = Progress.of(server);
        Vec3 side = new Vec3(1, 0, 0);
        BlockPos centre = BlockPos.containing(Vec3.atLowerCornerOf(top).add(side.scale(BESIDE)));
        Rotation turn = Rotation.NONE;
        BlockPos origin = Camps.place(level, TEMPLATE, centre, turn);
        StructurePlaceSettings settings = Camps.settings(level, TEMPLATE, turn);
        JsonObject plan = PLAN.get();
        BlockPos sefa = origin.offset(StructureTemplate.calculateRelativePosition(settings, Prologue.vector(plan.getAsJsonArray("sefa"))));
        BlockPos fire = origin.offset(StructureTemplate.calculateRelativePosition(settings, Prologue.vector(plan.getAsJsonArray("fire"))));
        progress.place("seat_sand_sefa", sefa);
        progress.yaws.put("seat_sand_sefa", Frost.yaw(Vec3.atLowerCornerOf(fire.subtract(sefa))));
        progress.place(CAMP_ORIGIN, origin);
        progress.place(NAME, sefa);
        progress.ward(NAME, level.getStructureManager().getOrCreate(TEMPLATE).getBoundingBox(settings, origin));
        progress.setDirty();
        for (int index = 0; index < 2; index++) {
            Entity camel = EntityType.CAMEL.create(level);
            if (camel != null) {
                camel.moveTo(fire.getX() + 4 + index * 2, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, fire.getX() + 4, fire.getZ() + 4),
                        fire.getZ() + 4, index * 90f, 0);
                level.addFreshEntity(camel);
            }
        }
        LOGGER.info("the Sand Singers' camp stands at {}", origin);
        Journal.syncAll(server);
    }

    private static void lair(MinecraftServer server, ServerLevel level, BlockPos site) {
        Progress progress = Progress.of(server);
        int floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, site.getX(), site.getZ()) - 1;
        for (int dx = -BOWL; dx <= BOWL; dx++) {
            for (int dz = -BOWL; dz <= BOWL; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > BOWL) {
                    continue;
                }
                int x = site.getX() + dx;
                int z = site.getZ() + dz;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                int bottom = floor - (int) Math.round(BOWL_DEPTH * (1 - distance / BOWL));
                for (int y = bottom + 1; y <= Math.max(top, floor) + BOWL_DEPTH * 2; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                level.setBlock(new BlockPos(x, bottom, z), Blocks.SAND.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(new BlockPos(x, bottom - 1, z), Blocks.SANDSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        for (int index = 0; index < BONES; index++) {
            double angle = Math.PI * 2 * index / BONES;
            int x = site.getX() + (int) Math.round(Math.cos(angle) * (BOWL - 1));
            int z = site.getZ() + (int) Math.round(Math.sin(angle) * (BOWL - 1));
            if (Math.floorMod(index * 7, 3) == 0) {
                continue;
            }
            BlockPos bone = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            level.setBlock(bone, Blocks.BONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        BlockPos spear = new BlockPos(site.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, site.getX(), site.getZ()), site.getZ());
        level.setBlock(spear, Blocks.SPRUCE_FENCE.defaultBlockState(), Block.UPDATE_CLIENTS);
        Clues.marker(level, spear, "spear");
        progress.place(LAIR, spear);
        progress.place(LAIR_BUILT, spear);
        progress.setDirty();
        LOGGER.info("the worm's bowl is at {}", spear);
        Journal.syncAll(server);
    }

    static boolean past(MinecraftServer server, ServerLevel dream) {
        Optional<BlockPos> lair = Progress.of(server).place(LAIR);
        if (lair.isPresent() && ModList.get().isLoaded(GECKOLIB)) {
            LivingEntity body = GreatWorms.GREAT_WORM.get().create(dream);
            if (body != null) {
                Vec3 at = Vec3.atBottomCenterOf(lair.get()).add(BOWL / 2.0, -STILL, 0);
                body.moveTo(at.x, at.y, at.z, 0, 0);
                body.getAttribute(Attributes.SCALE).setBaseValue(Balance.get().worm().scale());
                body.setInvulnerable(true);
                dream.addFreshEntity(body);
            }
        }
        return true;
    }

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !sings(event.getItemStack())) {
            return;
        }
        MinecraftServer server = player.server;
        if (!Engine.running(server)) {
            return;
        }
        Engine.active(server).stream().filter(active -> active.step().kind().equals("worm") && Steps.entered(active)).findFirst()
                .ifPresent(active -> horn(server, active, player));
    }

    private static boolean sings(ItemStack stack) {
        Holder<Instrument> instrument = stack.get(DataComponents.INSTRUMENT);
        return stack.is(Items.GOAT_HORN) && instrument != null && instrument.unwrapKey().map(key -> key.location().equals(SING)).orElse(false);
    }

    private static void horn(MinecraftServer server, Engine.Active active, ServerPlayer player) {
        Optional<BlockPos> lair = Places.place(server, LAIR);
        Balance.Worm worm = Balance.get().worm();
        if (lair.isEmpty() || player.blockPosition().distSqr(lair.get()) > (double) worm.groundBlocks() * worm.groundBlocks()) {
            return;
        }
        CompoundTag scratch = active.scratch();
        if (up(server, scratch)) {
            return;
        }
        Vec3 ahead = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(RISE_NEAR));
        rise(server, active, ahead);
    }

    private static boolean up(MinecraftServer server, CompoundTag scratch) {
        return scratch.hasUUID(WORM) && Engine.overworld(server).getEntity(scratch.getUUID(WORM)) != null;
    }

    private static void rise(MinecraftServer server, Engine.Active active, Vec3 at) {
        if (!ModList.get().isLoaded(GECKOLIB)) {
            server.getPlayerList().broadcastSystemMessage(Component.literal("the worm failed: GeckoLib is not in this game"), false);
            return;
        }
        ServerLevel level = Engine.overworld(server);
        CompoundTag scratch = active.scratch();
        Balance.Worm worm = Balance.get().worm();
        LivingEntity body = GreatWorms.GREAT_WORM.get().create(level);
        if (body == null) {
            return;
        }
        int floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(at.x), (int) Math.floor(at.z));
        body.moveTo(at.x, floor - STILL, at.z, 0, 0);
        long team = Math.max(1, Engine.players(server).stream().filter(player -> player.level() == level).count());
        double most = worm.health() + (double) worm.healthPerPlayer() * (team - 1);
        body.getAttribute(Attributes.MAX_HEALTH).setBaseValue(most);
        body.getAttribute(Attributes.SCALE).setBaseValue(worm.scale());
        body.setHealth(scratch.contains(HEALTH) ? Math.min((float) most, scratch.getFloat(HEALTH)) : (float) most);
        level.addFreshEntity(body);
        dust(level, body.position());
        level.playSound(null, body.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2, 0.5f);
        scratch.putUUID(WORM, body.getUUID());
        scratch.putLong(UP_UNTIL, level.getGameTime() + (long) worm.upSeconds() * TICKS_PER_SECOND);
        scratch.putBoolean(WOKEN, true);
        Progress.of(server).setDirty();
        if (!scratch.getBoolean(WOKEN + "_said")) {
            scratch.putBoolean(WOKEN + "_said", true);
            Talk.say(active.quest().id(), body.position(), active.step().lines("rises"), Engine.fill(Progress.of(server)));
        }
    }

    private static void dust(ServerLevel level, Vec3 at) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()), at.x, at.y + 1, at.z, DUST, 1.5, 1, 1.5, 0.1);
    }

    private static void dive(MinecraftServer server, CompoundTag scratch, Entity body) {
        ServerLevel level = Engine.overworld(server);
        if (body instanceof LivingEntity living) {
            scratch.putFloat(HEALTH, living.getHealth());
        }
        dust(level, body.position());
        body.discard();
        scratch.remove(WORM);
        scratch.putLong(NEXT, level.getGameTime() + (long) Balance.get().worm().downSeconds() * TICKS_PER_SECOND);
        Progress.of(server).setDirty();
    }

    static void tick(MinecraftServer server, Engine.Active active) {
        ServerLevel level = Engine.overworld(server);
        CompoundTag scratch = active.scratch();
        Balance.Worm worm = Balance.get().worm();
        Optional<BlockPos> lair = Places.place(server, LAIR);
        if (lair.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        boolean near = Engine.players(server).stream().anyMatch(player -> player.level() == level && player.isAlive()
                && player.blockPosition().distSqr(lair.get()) <= (double) worm.groundBlocks() * worm.groundBlocks());
        Entity body = scratch.hasUUID(WORM) ? level.getEntity(scratch.getUUID(WORM)) : null;
        if (!near) {
            long away = scratch.contains(AWAY_SINCE) ? scratch.getLong(AWAY_SINCE) : now;
            scratch.putLong(AWAY_SINCE, away);
            if (body != null) {
                dive(server, scratch, body);
            }
            if (now - away >= (long) worm.leaveSeconds() * TICKS_PER_SECOND) {
                if (scratch.getBoolean(WOKEN)) {
                    scratch.remove(HEALTH);
                    scratch.remove(WOKEN);
                    scratch.remove(AWAY_SINCE);
                    Progress.of(server).setDirty();
                } else {
                    end(server, active, SPARED);
                }
            }
            return;
        }
        scratch.remove(AWAY_SINCE);
        if (body != null) {
            if (now >= scratch.getLong(UP_UNTIL)) {
                dive(server, scratch, body);
                return;
            }
            bite(server, level, body, worm);
            return;
        }
        if (scratch.getBoolean(WOKEN) && now >= scratch.getLong(NEXT)) {
            Engine.players(server).stream().filter(player -> player.level() == level && player.blockPosition().distSqr(lair.get())
                    <= (double) worm.groundBlocks() * worm.groundBlocks()).max((a, b) -> Long.compare(still(a, now), still(b, now)))
                    .ifPresent(player -> rise(server, active, player.position()));
        }
        Engine.players(server).forEach(player -> still(player, now));
    }

    private static long still(ServerPlayer player, long now) {
        Vec3 last = resting.get(player.getUUID());
        if (last == null || last.distanceToSqr(player.position()) > STILL) {
            resting.put(player.getUUID(), player.position());
            restingSince.put(player.getUUID(), now);
        }
        return now - restingSince.getOrDefault(player.getUUID(), now);
    }

    private static void bite(MinecraftServer server, ServerLevel level, Entity body, Balance.Worm worm) {
        if (level.getGameTime() % ((long) worm.biteSeconds() * TICKS_PER_SECOND) != 0) {
            return;
        }
        double reach = worm.biteBlocks() * worm.scale();
        Engine.players(server).stream().filter(player -> player.level() == level && player.distanceToSqr(body) <= reach * reach)
                .forEach(player -> player.hurt(level.damageSources().mobAttack((LivingEntity) body), worm.biteDamage()));
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level) || !ModList.get().isLoaded(GECKOLIB)
                || event.getEntity().getType() != GreatWorms.GREAT_WORM.get()) {
            return;
        }
        MinecraftServer server = level.getServer();
        Engine.active(server).stream().filter(active -> active.step().kind().equals("worm")).findFirst().ifPresent(active -> {
            dust(level, event.getEntity().position());
            Journey.of(server).claim(HALL);
            server.getPlayerList().getPlayers().forEach(Shards::sync);
            Steps.gifts(Progress.of(server), active.step(), List.of(EYE));
            end(server, active, KILLED);
        });
    }

    private static void end(MinecraftServer server, Engine.Active active, String outcome) {
        Progress progress = Progress.of(server);
        String key = active.quest().id() + "/" + active.step().id();
        if (progress.choices.containsKey(key)) {
            return;
        }
        progress.choices.put(key, outcome);
        progress.setDirty();
        Talk.say(active.quest().id(), null, active.step().lines(outcome), Engine.fill(progress));
        LOGGER.info("the worm's fight ends: {}", outcome);
        Talk.then(() -> Engine.complete(server, active));
    }
}

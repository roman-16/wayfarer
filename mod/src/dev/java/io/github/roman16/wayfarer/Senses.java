package io.github.roman16.wayfarer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import toughasnails.api.temperature.TemperatureHelper;

final class Senses {
    static final String FALL = "a fall";
    private static final double AROUND_BLOCKS = 32;
    private static final double GLOW_BLOCKS = 96;
    private static final int AROUND_LIMIT = 12;
    private static final double CHUNK = 16;
    private static final int SCAN_BLOCKS = 16;
    private static final int SCAN_HEIGHT = 8;
    private static final double HEIGHT_BLOCKS = 3;
    private static final String GRAVESTONE = "gravestone";
    private static final double DARK_BLOCKS = 8;
    private static final int DARK_LIGHT = 4;
    private static final double NEAR_BLOCKS = 8;
    private static final double CLOSE_BLOCKS = 16;
    private static final double OWN_BLOCKS = 2;
    private static final double ABOVE_BLOCKS = 6;
    private static final double COMING = 0.02;
    private static final int TICKS_PER_DAY = 24000;
    private static final int TICKS_PER_SECOND = 20;
    private static final int SECONDS_PER_MINUTE = 60;
    private static final int AIR_STEPS = 10;
    private static final int FULL_FOOD = 20;
    private static final int PERCENT = 100;
    private static final int HOTBAR = 9;
    private static final long TREND_TICKS = 200;
    private static final String TOUGH_AS_NAILS = "toughasnails";
    private static final String TAN_THIRST = "toughasnails.thirst";
    static final String HUNGER = "hunger";
    private static final Set<SoundSource> HEARD = Set.of(SoundSource.AMBIENT, SoundSource.BLOCKS, SoundSource.HOSTILE, SoundSource.NEUTRAL,
            SoundSource.PLAYERS, SoundSource.WEATHER);
    private static final String[] SIDES = {"behind", "behind-left", "left", "ahead-left", "ahead", "ahead-right", "right", "behind-right", "behind"};
    private static final Map<String, String> KINDS = Map.ofEntries(Map.entry("cactus", "a cactus"), Map.entry("campfire", "fire"), Map.entry("cramming", "crowding"),
            Map.entry("drown", "drowning"), Map.entry("dryout", "drying out"), Map.entry("fall", FALL), Map.entry("fallingBlock", "a falling block"),
            Map.entry("flyIntoWall", "a wall"), Map.entry("freeze", "cold"), Map.entry("hotFloor", "a hot floor"), Map.entry("inFire", "fire"),
            Map.entry("inWall", "a wall around the head"), Map.entry("lava", "lava"), Map.entry("lightningBolt", "lightning"), Map.entry("magic", "poison"),
            Map.entry("onFire", "burning"), Map.entry("starve", HUNGER), Map.entry("sweetBerryBush", "a berry bush"), Map.entry("toughasnails.hyperthermia", "heat"),
            Map.entry(TAN_THIRST, "thirst"), Map.entry("wither", "withering"));
    private static final List<Heard> heard = new ArrayList<>();
    private static final List<String> hurts = new ArrayList<>();
    @Nullable
    private static Entity lastAttacker;
    private static long lastHurt = Long.MIN_VALUE / 2;
    private static String lastKind = "something";
    private static float lastHealth = -1;
    @Nullable
    private static String lastTemperature;
    private static String trend = "";
    private static long changedAt;
    private static boolean listening;

    private record Heard(String text, Vec3 at) {}

    private record Sighted(double distance, String line) {}

    private Senses() {}

    static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (!listening) {
            listening = true;
            minecraft.getSoundManager().addListener((sound, events, range) -> hear(minecraft, sound, events.getSubtitle() == null ? null : events.getSubtitle().getString(),
                    range));
        }
        float health = player.getHealth();
        DamageSource source = player.getLastDamageSource();
        if (lastHealth >= 0 && health < lastHealth && player.isAlive() && (source != null || lastHealth - health >= 1)) {
            hurt(minecraft, lastHealth - health, source);
        }
        lastHealth = health;
        feel(player);
    }

    static void forget() {
        heard.clear();
        hurts.clear();
        lastAttacker = null;
        lastHealth = -1;
        lastHurt = Long.MIN_VALUE / 2;
        lastKind = "something";
        lastTemperature = null;
        trend = "";
    }

    private static void hear(Minecraft minecraft, SoundInstance sound, @Nullable String subtitle, float range) {
        LocalPlayer player = minecraft.player;
        if (subtitle == null || player == null || sound.isRelative() || !HEARD.contains(sound.getSource())) {
            return;
        }
        Vec3 at = new Vec3(sound.getX(), sound.getY(), sound.getZ());
        double distance = at.distanceTo(player.position());
        if (sound.getAttenuation() != SoundInstance.Attenuation.NONE && distance > range
                || sound.getSource() == SoundSource.PLAYERS && distance < OWN_BLOCKS) {
            return;
        }
        heard.add(new Heard(subtitle, at));
    }

    private static void hurt(Minecraft minecraft, float amount, @Nullable DamageSource source) {
        LocalPlayer player = minecraft.player;
        lastHurt = minecraft.level.getGameTime();
        Entity attacker = source == null ? null : source.getEntity();
        lastAttacker = attacker instanceof LivingEntity && attacker != player ? attacker : null;
        Vec3 from = source == null ? null : source.getSourcePosition() != null ? source.getSourcePosition() : attacker == null ? null : attacker.position();
        String side = from == null ? "" : " from " + side(player, from);
        lastKind = kind(minecraft, source);
        hurts.add(Math.max(1, Math.round(amount)) + side + ": " + lastKind);
    }

    static String lastKind() {
        return lastKind;
    }

    private static String kind(Minecraft minecraft, @Nullable DamageSource source) {
        if (source == null) {
            return "something";
        }
        Entity attacker = source.getEntity();
        if (attacker != null && attacker != minecraft.player) {
            return visible(minecraft, attacker) ? describe(attacker) : "something unseen";
        }
        if (source.getMsgId().equals(TAN_THIRST) && minecraft.player.getFoodData().getFoodLevel() == 0) {
            return HUNGER;
        }
        return KINDS.getOrDefault(source.getMsgId(), source.getMsgId());
    }

    static boolean hurtLately(Minecraft minecraft, int ticks) {
        return minecraft.level.getGameTime() - lastHurt <= ticks;
    }

    @Nullable
    static Entity lastAttacker() {
        return lastAttacker != null && lastAttacker.isAlive() && !lastAttacker.isRemoved() ? lastAttacker : null;
    }

    static List<String> heardSince() {
        LocalPlayer player = Minecraft.getInstance().player;
        Map<String, Heard> nearest = new LinkedHashMap<>();
        Map<String, Integer> counted = new LinkedHashMap<>();
        for (Heard sound : heard) {
            counted.merge(sound.text(), 1, Integer::sum);
            nearest.merge(sound.text(), sound, (was, now) -> player != null && now.at().distanceTo(player.position()) < was.at().distanceTo(player.position()) ? now : was);
        }
        heard.clear();
        return nearest.values().stream().map(sound -> {
            String where = player == null ? "" : ", " + side(player, sound.at()) + ", " + distance(player, sound.at());
            int times = counted.get(sound.text());
            return sound.text() + where + (times > 1 ? " (" + times + " times)" : "");
        }).toList();
    }

    static List<String> hurtSince() {
        List<String> taken = List.copyOf(hurts);
        hurts.clear();
        return taken;
    }

    static JsonObject body(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        JsonObject body = new JsonObject();
        body.addProperty("health", Math.round(player.getHealth()) + " of " + Math.round(player.getMaxHealth()));
        if (player.getAbsorptionAmount() > 0) {
            body.addProperty("absorption", Math.round(player.getAbsorptionAmount()));
        }
        body.addProperty("food", player.getFoodData().getFoodLevel() + " of " + FULL_FOOD);
        body.addProperty("saturation", Math.round(player.getFoodData().getSaturationLevel()));
        body.addProperty("armour", player.getArmorValue());
        if (player.getAirSupply() < player.getMaxAirSupply()) {
            body.addProperty("air", Math.max(0, player.getAirSupply()) * AIR_STEPS / player.getMaxAirSupply() + " of " + AIR_STEPS);
        }
        String temperature = temperature(player);
        if (temperature != null) {
            body.addProperty("temperature", temperature);
        }
        JsonArray effects = new JsonArray();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            String name = effect.getEffect().value().getDisplayName().getString() + (effect.getAmplifier() > 0 ? " " + (effect.getAmplifier() + 1) : "");
            effects.add(effect.isInfiniteDuration() ? name : name + ", " + clock(effect.getDuration()));
        }
        if (!effects.isEmpty()) {
            body.add("effects", effects);
        }
        body.addProperty("hand", held(player.getMainHandItem()));
        if (!player.getOffhandItem().isEmpty()) {
            body.addProperty("off hand", held(player.getOffhandItem()));
        }
        JsonArray hotbar = new JsonArray();
        for (int slot = 0; slot < HOTBAR; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                hotbar.add(slot + 1 + " " + held(stack));
            }
        }
        body.add("hotbar", hotbar);
        body.addProperty("time", time(minecraft.level.getDayTime()));
        String weather = weather(minecraft);
        if (weather != null) {
            body.addProperty("weather", weather);
        }
        return body;
    }

    static JsonArray around(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        List<Sighted> sighted = new ArrayList<>();
        StreamSupport.stream(minecraft.level.entitiesForRendering().spliterator(), false)
                .filter(entity -> entity instanceof LivingEntity && !(entity instanceof ArmorStand) && entity != player && entity.isAlive()
                        && inView(minecraft, entity.getBoundingBox().getCenter())
                        && (entity.distanceTo(player) <= AROUND_BLOCKS && visible(minecraft, entity) || entity.isCurrentlyGlowing() && entity.distanceTo(player) <= GLOW_BLOCKS))
                .forEach(entity -> {
                    boolean through = !(entity.distanceTo(player) <= AROUND_BLOCKS && visible(minecraft, entity));
                    sighted.add(new Sighted(entity.distanceTo(player), describe(entity) + ", " + Math.round(entity.distanceTo(player)) + " blocks "
                            + side(player, entity.position()) + (side(player, entity.position()).matches("above|below") ? ""
                            : height(entity.getBoundingBox().getCenter().y - player.getEyeY()))
                            + (through ? ", glowing through what stands between at " + entity.blockPosition().toShortString() : "")
                            + (coming(player, entity) ? ", coming" : "")));
                });
        StreamSupport.stream(minecraft.level.entitiesForRendering().spliterator(), false)
                .filter(entity -> !(entity instanceof LivingEntity) && !(entity instanceof Interaction) && !(entity instanceof HangingEntity) && entity.isPickable()
                        && !entity.isCurrentlyGlowing() && entity != player.getVehicle() && entity.distanceTo(player) <= AROUND_BLOCKS
                        && inView(minecraft, entity.getBoundingBox().getCenter()) && visible(minecraft, entity))
                .forEach(entity -> sighted.add(new Sighted(entity.distanceTo(player), describe(entity) + ", " + Math.round(entity.distanceTo(player)) + " blocks "
                        + side(player, entity.position()) + height(entity.getBoundingBox().getCenter().y - player.getEyeY()))));
        Vec3 eyes = player.getEyePosition();
        StreamSupport.stream(minecraft.level.entitiesForRendering().spliterator(), false)
                .filter(entity -> !(entity instanceof LivingEntity) && entity.isCurrentlyGlowing() && entity.distanceTo(player) <= GLOW_BLOCKS
                        && inView(minecraft, entity.getBoundingBox().getCenter()))
                .forEach(entity -> {
                    Vec3 centre = entity.getBoundingBox().getCenter();
                    BlockPos under = BlockPos.containing(centre);
                    String what = minecraft.level.getBlockState(under).isAir() ? "something" : minecraft.level.getBlockState(under).getBlock().getName().getString();
                    sighted.add(new Sighted(eyes.distanceTo(centre), what + " glowing in the ember's light, " + Math.round(eyes.distanceTo(centre)) + " blocks "
                            + side(player, centre) + height(centre.y - eyes.y) + " at " + under.toShortString()));
                });
        notable(minecraft).forEach(sighted::add);
        JsonArray around = new JsonArray();
        sighted.stream().sorted(Comparator.comparingDouble(Sighted::distance)).limit(AROUND_LIMIT).forEach(seen -> around.add(seen.line()));
        return around;
    }

    private static List<Sighted> notable(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        Set<BlockPos> found = new LinkedHashSet<>();
        ChunkPos centre = player.chunkPosition();
        int chunks = (int) Math.ceil(AROUND_BLOCKS / CHUNK);
        for (int dx = -chunks; dx <= chunks; dx++) {
            for (int dz = -chunks; dz <= chunks; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(centre.x + dx, centre.z + dz, false);
                if (chunk != null) {
                    chunk.getBlockEntities().keySet().stream().filter(pos -> notable(level.getBlockState(pos))).forEach(found::add);
                }
            }
        }
        BlockPos here = player.blockPosition();
        BlockPos.betweenClosed(here.offset(-SCAN_BLOCKS, -SCAN_HEIGHT, -SCAN_BLOCKS), here.offset(SCAN_BLOCKS, SCAN_HEIGHT, SCAN_BLOCKS)).forEach(pos -> {
            if (notable(level.getBlockState(pos))) {
                found.add(pos.immutable());
            }
        });
        Vec3 eyes = player.getEyePosition();
        return found.stream().filter(pos -> sighted(minecraft, pos)).map(pos -> {
            Vec3 centreOf = Vec3.atCenterOf(pos);
            double up = centreOf.y - eyes.y;
            String height = height(up);
            String name = level.getBlockState(pos).getBlock().getName().getString().toLowerCase(Locale.ROOT);
            return new Sighted(eyes.distanceTo(centreOf), "a " + name + ", " + Math.round(eyes.distanceTo(centreOf)) + " blocks " + side(player, centreOf) + height
                    + " at " + pos.toShortString());
        }).toList();
    }

    private static String height(double up) {
        return Math.abs(up) >= HEIGHT_BLOCKS ? ", " + Math.round(Math.abs(up)) + (up > 0 ? " above" : " below") : "";
    }

    private static boolean notable(BlockState state) {
        Block block = state.getBlock();
        return state.is(BlockTags.BEDS) && state.getValue(BedBlock.PART) == BedPart.HEAD
                || block instanceof LeverBlock || state.is(BlockTags.BUTTONS)
                || block instanceof CampfireBlock
                || state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.BARREL) || state.is(Blocks.LECTERN)
                || block.asItem() == Items.CRAFTING_TABLE
                || state.is(BlockTags.DOORS) && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                || BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(GRAVESTONE);
    }

    private static boolean sighted(Minecraft minecraft, BlockPos pos) {
        LocalPlayer player = minecraft.player;
        Vec3 centre = Vec3.atCenterOf(pos);
        double distance = player.getEyePosition().distanceTo(centre);
        if (distance > AROUND_BLOCKS || !inView(minecraft, centre)) {
            return false;
        }
        BlockHitResult hit = minecraft.level.clip(new ClipContext(player.getEyePosition(), centre, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        boolean open = hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos)
                || hit.getBlockPos().distManhattan(pos) == 1 && minecraft.level.getBlockState(hit.getBlockPos()).is(minecraft.level.getBlockState(pos).getBlock());
        int light = Math.max(minecraft.level.getMaxLocalRawBrightness(pos), minecraft.level.getMaxLocalRawBrightness(pos.above()));
        return open && (distance <= DARK_BLOCKS || light >= DARK_LIGHT);
    }

    @Nullable
    static String crosshair(Minecraft minecraft) {
        String aimed = aimed(minecraft);
        return aimed == null ? null : aimed + ", " + Math.round(minecraft.hitResult.getLocation().distanceTo(minecraft.player.getEyePosition())) + " blocks";
    }

    @Nullable
    static String aimed(Minecraft minecraft) {
        HitResult hit = minecraft.hitResult;
        if (hit == null || hit.getType() == HitResult.Type.MISS || minecraft.player == null) {
            return null;
        }
        if (hit instanceof EntityHitResult aimed) {
            return describe(aimed.getEntity());
        }
        return minecraft.level.getBlockState(((BlockHitResult) hit).getBlockPos()).getBlock().getName().getString();
    }

    static String span(long ticks) {
        long seconds = ticks / TICKS_PER_SECOND;
        return seconds >= SECONDS_PER_MINUTE ? seconds / SECONDS_PER_MINUTE + " min " + seconds % SECONDS_PER_MINUTE + " s" : seconds + " s";
    }

    static boolean visible(Minecraft minecraft, Entity entity) {
        LocalPlayer player = minecraft.player;
        if (entity.isInvisibleTo(player) || !player.hasLineOfSight(entity)) {
            return false;
        }
        return entity.distanceTo(player) <= DARK_BLOCKS || minecraft.level.getMaxLocalRawBrightness(BlockPos.containing(entity.getEyePosition())) >= DARK_LIGHT
                || entity.isCurrentlyGlowing() || entity.isOnFire();
    }

    static boolean inView(Minecraft minecraft, Vec3 at) {
        LocalPlayer player = minecraft.player;
        double vertical = minecraft.options.fov().get() / 2.0;
        double aspect = (double) minecraft.getWindow().getWidth() / Math.max(1, minecraft.getWindow().getHeight());
        double horizontal = Math.toDegrees(Math.atan(Math.tan(Math.toRadians(vertical)) * aspect));
        Vec3 look = at.subtract(player.getEyePosition());
        double yaw = Mth.wrapDegrees(Math.toDegrees(Math.atan2(look.z, look.x)) - 90 - player.getYRot());
        double pitch = -Math.toDegrees(Math.atan2(look.y, look.horizontalDistance())) - player.getXRot();
        return Math.abs(yaw) <= horizontal && Math.abs(pitch) <= vertical;
    }

    static boolean hostile(Entity entity) {
        return (entity instanceof Enemy || entity.getType().getCategory() == MobCategory.MONSTER) && !(entity instanceof EasyNPC<?>) && !(entity instanceof Mob mob && mob.isNoAi()) && !(entity instanceof Player);
    }

    static boolean gone(Entity entity) {
        return entity.isRemoved() && !(entity instanceof LivingEntity living && living.isDeadOrDying());
    }

    static boolean coming(LocalPlayer player, Entity entity) {
        Vec3 moved = entity.position().subtract(entity.xo, entity.yo, entity.zo);
        Vec3 towards = player.position().subtract(entity.position()).normalize();
        return moved.dot(towards) > COMING;
    }

    static String describe(Entity entity) {
        if (entity instanceof Interaction) {
            return "an interaction box over " + entity.level().getBlockState(BlockPos.containing(entity.getBoundingBox().getCenter())).getBlock().getName().getString();
        }
        String name = entity.getDisplayName().getString().toLowerCase(Locale.ROOT);
        return entity.hasCustomName() || entity instanceof Player ? entity.getDisplayName().getString() : (name.matches("[aeiou].*") ? "an " : "a ") + name;
    }

    static String side(LocalPlayer player, Vec3 at) {
        Vec3 look = at.subtract(player.getEyePosition());
        if (Math.abs(look.y) > ABOVE_BLOCKS && Math.abs(look.y) > look.horizontalDistance()) {
            return look.y > 0 ? "above" : "below";
        }
        double turn = Mth.wrapDegrees(Math.toDegrees(Math.atan2(look.z, look.x)) - 90 - player.getYRot());
        return SIDES[(int) Math.round((turn + 180) / 45)];
    }

    private static String distance(LocalPlayer player, Vec3 at) {
        double blocks = at.distanceTo(player.position());
        return blocks < NEAR_BLOCKS ? "near" : blocks < CLOSE_BLOCKS ? "close" : "far";
    }

    private static String held(ItemStack stack) {
        return stack.isEmpty() ? "nothing" : stack.getCount() > 1 ? stack.getCount() + " " + stack.getHoverName().getString() : stack.getHoverName().getString();
    }

    private static String clock(int ticks) {
        int minutes = ticks / TICKS_PER_SECOND / SECONDS_PER_MINUTE;
        return minutes > 0 ? minutes + " min" : "under a minute";
    }

    static String time(long dayTime) {
        long day = dayTime / TICKS_PER_DAY + 1;
        long tick = dayTime % TICKS_PER_DAY;
        String part = tick < 1000 ? "dawn" : tick < 5000 ? "morning" : tick < 7000 ? "noon" : tick < 11500 ? "afternoon" : tick < 13500 ? "dusk"
                : tick < 22500 ? "night" : "dawn";
        return "day " + day + ", " + part;
    }

    @Nullable
    private static String weather(Minecraft minecraft) {
        if (!minecraft.level.isRaining()) {
            return null;
        }
        BlockPos at = minecraft.player.blockPosition();
        return switch (minecraft.level.getBiome(at).value().getPrecipitationAt(at)) {
            case NONE -> null;
            case RAIN -> minecraft.level.isThundering() ? "thunder" : "rain";
            case SNOW -> "snow";
        };
    }

    @Nullable
    private static String temperature(LocalPlayer player) {
        String frozen = player.getPercentFrozen() > 0 ? ", freezing " + Math.round(player.getPercentFrozen() * PERCENT) + "%" : "";
        if (!ModList.get().isLoaded(TOUGH_AS_NAILS)) {
            return frozen.isEmpty() ? null : frozen.substring(2);
        }
        String change = trend.isEmpty() || player.level().getGameTime() - changedAt > TREND_TICKS ? "" : ", " + trend;
        return Temperature.level(player) + change + Temperature.overheating(player) + frozen;
    }

    private static void feel(LocalPlayer player) {
        if (!ModList.get().isLoaded(TOUGH_AS_NAILS)) {
            return;
        }
        String level = Temperature.level(player);
        if (lastTemperature != null && !lastTemperature.equals(level)) {
            trend = Temperature.warmer(level, lastTemperature) ? "rising" : "falling";
            changedAt = player.level().getGameTime();
        }
        lastTemperature = level;
    }

    private static final class Temperature {
        private static final String[] NAMES = {"icy", "cold", "mild", "warm", "hot"};

        private Temperature() {}

        static String level(LocalPlayer player) {
            return NAMES[TemperatureHelper.getTemperatureForPlayer(player).ordinal()];
        }

        static boolean warmer(String level, String than) {
            return List.of(NAMES).indexOf(level) > List.of(NAMES).indexOf(than);
        }

        static String overheating(LocalPlayer player) {
            float share = TemperatureHelper.getPercentHyperthermic(player);
            return share > 0 ? ", overheating " + Math.round(share * PERCENT) + "%" : "";
        }
    }
}

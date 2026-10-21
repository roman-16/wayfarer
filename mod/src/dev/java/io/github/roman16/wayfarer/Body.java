package io.github.roman16.wayfarer;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.pathing.goals.GoalRunAway;
import baritone.api.pathing.goals.GoalXZ;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

final class Body {
    private static final int REACTION_TICKS = 5;
    private static final double THREAT_BLOCKS = 6;
    private static final double SEEN_ATTACKER_BLOCKS = 16;
    private static final double CREEPER_BLOCKS = 4;
    private static final double CREEPER_CLEAR_BLOCKS = 8;
    private static final double QUIET_BLOCKS = 16;
    private static final double REACH_BLOCKS = 3;
    private static final double LOST_BLOCKS = 24;
    private static final double PURSUIT_BLOCKS = 8;
    private static final double HOME_BLOCKS = 2;
    private static final double REFUGE_BLOCKS = 32;
    private static final double SAFE_BLOCKS = 3;
    private static final double CORNERED_BLOCKS = 6;
    private static final String GOAL = "where I was going";
    private static final double SHIELD_BLOCKS = 5;
    private static final int RUN_BLOCKS = 16;
    private static final int STEP_OUT_BLOCKS = 3;
    private static final int RECENT_TICKS = 100;
    private static final int REPATH_TICKS = 10;
    private static final int FLEE_TICKS = 200;
    private static final int RETURN_TICKS = 400;
    private static final int BACK_OFF_TICKS = 256;
    private static final int BREATHE_TICKS = 200;
    private static final int STEP_OUT_TICKS = 60;
    private static final int QUENCH_BLOCKS = 8;
    private static final int QUENCH_TICKS = 160;
    private static final int EAT_TICKS = 100;
    private static final int FED_TICKS = 20;
    private static final int BREATHED_TICKS = 200;
    private static final int HUNGRY_FOOD = 14;
    private static final int HEALING_FOOD = 18;
    private static final int STARVING_FOOD = 6;
    private static final int MOST_FOES = 2;
    private static final int HOTBAR = 9;
    private static final int AIR_STEPS = 10;
    private static final float LOW_SHARE = 1 / 3f;
    private static final float READY = 1;
    private static final float HALF = 0.5f;
    private static final float UPWARD = -60;
    private static final double TOUCH = 0.05;

    private enum Doing { NOTHING, BREATHE, STEP_OUT, QUENCH, BACK_OFF, FIGHT, FLEE, RETURN, EAT }

    private record Refuge(String name, Vec3 at) {}

    private static final List<String> said = new ArrayList<>();
    private static final Set<Integer> passed = new HashSet<>();
    private static final Set<Integer> letGo = new HashSet<>();
    private static Doing doing = Doing.NOTHING;
    @Nullable
    private static Entity target;
    private static List<Entity> foes = List.of();
    private static long ticks;
    private static int strikes;
    private static float healthFrom;
    private static int foodFrom;
    private static int slotFrom;
    private static int countFrom;
    private static String eating = "";
    private static String fled = "";
    @Nullable
    private static Vec3 home;
    @Nullable
    private static Refuge refuge;
    private static Vec3 fleeFrom = Vec3.ZERO;
    private static double returnFrom;
    private static boolean standing;
    private static long fledAt = Long.MIN_VALUE / 2;
    private static boolean started;
    private static int noticed;
    private static long fedAt = Long.MIN_VALUE / 2;
    private static long breathedAt = Long.MIN_VALUE / 2;
    private static boolean tookOver;
    private static boolean treading;
    private static boolean finished;
    @Nullable
    private static String handBack;

    private Body() {}

    static void tick(Minecraft minecraft, @Nullable Legs.Kind leg) {
        LocalPlayer player = minecraft.player;
        standing = leg == null || leg == Legs.Kind.IDLE || leg == Legs.Kind.EAT;
        if (doing == Doing.NOTHING) {
            home = null;
        }
        if (!player.isAlive() || !minecraft.gameMode.getPlayerMode().isSurvival() || !minecraft.level.tickRateManager().runsNormally()
                || player.isPassenger()) {
            if (doing != Doing.NOTHING) {
                end(minecraft, null);
            }
            tread(minecraft, false);
            noticed = 0;
            foes = List.of();
            return;
        }
        tread(minecraft, doing == Doing.NOTHING && deep(player) && (standing || leg == Legs.Kind.LISTEN));
        foes = foes(minecraft, leg);
        noticed = foes.isEmpty() ? 0 : noticed + 1;
        Doing wanted = wanted(minecraft, leg);
        if (wanted != Doing.NOTHING && (doing == Doing.NOTHING || wanted.ordinal() < doing.ordinal())) {
            if (doing != Doing.NOTHING) {
                end(minecraft, null);
            }
            begin(minecraft, wanted, leg);
        }
        if (doing != Doing.NOTHING) {
            ticks++;
            act(minecraft);
        }
    }

    private static void tread(Minecraft minecraft, boolean tread) {
        if (tread || treading) {
            minecraft.options.keyJump.setDown(tread);
            treading = tread;
        }
    }

    static boolean acting() {
        return doing != Doing.NOTHING;
    }

    static String doing() {
        return switch (doing) {
            case BACK_OFF -> "backing off from a creeper";
            case BREATHE -> "swimming up for air";
            case EAT -> "eating";
            case FIGHT -> "fighting";
            case FLEE -> "running";
            case NOTHING -> "";
            case QUENCH -> "putting the fire out";
            case RETURN -> "walking back";
            case STEP_OUT -> "stepping out of fire";
        };
    }

    static boolean busy() {
        return doing != Doing.NOTHING && doing != Doing.BREATHE;
    }

    static boolean answering() {
        return doing != Doing.NOTHING || !foes.isEmpty();
    }

    static List<String> drain() {
        List<String> lines = List.copyOf(said);
        said.clear();
        return lines;
    }

    static boolean takeTookOver() {
        boolean taken = tookOver;
        tookOver = false;
        return taken;
    }

    static boolean takeFinished() {
        boolean taken = finished;
        finished = false;
        return taken;
    }

    @Nullable
    static String takeHandBack() {
        String taken = handBack;
        handBack = null;
        return taken;
    }

    static void forget() {
        doing = Doing.NOTHING;
        target = null;
        home = null;
        refuge = null;
        fledAt = Long.MIN_VALUE / 2;
        foes = List.of();
        noticed = 0;
        fedAt = Long.MIN_VALUE / 2;
        breathedAt = Long.MIN_VALUE / 2;
        passed.clear();
        letGo.clear();
        said.clear();
        tookOver = false;
        treading = false;
        finished = false;
        handBack = null;
    }

    static boolean hasFood(LocalPlayer player) {
        return food(player) >= 0;
    }

    private static Doing wanted(Minecraft minecraft, @Nullable Legs.Kind leg) {
        LocalPlayer player = minecraft.player;
        boolean screen = minecraft.screen != null;
        if (player.isUnderWater() && player.getAirSupply() < player.getMaxAirSupply() / 2) {
            return Doing.BREATHE;
        }
        if (screen && !Senses.hurtLately(minecraft, 1)) {
            return Doing.NOTHING;
        }
        if (burning(minecraft)) {
            return Doing.STEP_OUT;
        }
        if (player.isOnFire() && !player.isInWater() && water(minecraft) != null) {
            return Doing.QUENCH;
        }
        if (creeper(minecraft) != null) {
            return Doing.BACK_OFF;
        }
        if (leg == Legs.Kind.FIGHT && Legs.engaged() || foes.isEmpty() || noticed < REACTION_TICKS) {
            return leg != null && leg != Legs.Kind.FIGHT && leg != Legs.Kind.EAT && hungry(minecraft) ? Doing.EAT : Doing.NOTHING;
        }
        Entity nearest = foes.getFirst();
        boolean reachable = !deep(nearest) || deep(player);
        boolean hurt = player.getHealth() <= player.getMaxHealth() * LOW_SHARE;
        boolean able = armed(player) && !hurt && foes.size() <= MOST_FOES && reachable;
        boolean near = reach(player, nearest) <= REACH_BLOCKS || reach(player, nearest) <= REACH_BLOCKS * 2 && Senses.hurtLately(minecraft, REPATH_TICKS)
                || (home == null || flat(nearest.position(), home) <= PURSUIT_BLOCKS)
                && (!letGo.contains(nearest.getId()) || nearest.distanceTo(player) <= PURSUIT_BLOCKS);
        if (able && near || hurt && cornered(minecraft, nearest)) {
            return doing == Doing.FLEE && able ? Doing.FLEE : Doing.FIGHT;
        }
        if (leg == Legs.Kind.GO || leg == Legs.Kind.FOLLOW) {
            walkOn(player, !armed(player) ? "unarmed" : foes.size() > MOST_FOES ? "outnumbered" : hurt ? "too hurt to fight"
                    : !reachable ? "it is in deep water" : "it keeps its distance");
            return Doing.NOTHING;
        }
        boolean struck = Senses.hurtLately(minecraft, REPATH_TICKS);
        if (able && !struck || minecraft.level.getGameTime() - fledAt < FLEE_TICKS && !struck) {
            return Doing.NOTHING;
        }
        return Doing.FLEE;
    }

    private static void begin(Minecraft minecraft, Doing next, @Nullable Legs.Kind leg) {
        LocalPlayer player = minecraft.player;
        if (next == Doing.NOTHING) {
            return;
        }
        if (minecraft.screen != null) {
            minecraft.screen.onClose();
        }
        release(minecraft);
        baritone().getPathingBehavior().cancelEverything();
        boolean hostile = next == Doing.BACK_OFF || next == Doing.FIGHT || next == Doing.FLEE;
        tookOver |= leg == null && hostile;
        if (hostile && home == null) {
            home = player.position();
        }
        doing = next;
        ticks = 0;
        strikes = 0;
        healthFrom = player.getHealth();
        foodFrom = player.getFoodData().getFoodLevel();
        started = false;
        target = next == Doing.FIGHT ? foes.getFirst() : next == Doing.BACK_OFF ? creeper(minecraft) : null;
        fled = names(foes);
        if (next == Doing.FLEE) {
            runFor(minecraft);
        }
        if (next == Doing.FIGHT) {
            arm(player);
        }
        if (next == Doing.EAT) {
            slotFrom = player.getInventory().selected;
            player.getInventory().selected = food(player);
            countFrom = player.getMainHandItem().getCount();
            eating = player.getMainHandItem().getHoverName().getString().toLowerCase(Locale.ROOT);
        }
    }

    private static void act(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        switch (doing) {
            case BREATHE -> {
                if (!player.isUnderWater()) {
                    boolean again = minecraft.level.getGameTime() - breathedAt < BREATHED_TICKS;
                    breathedAt = minecraft.level.getGameTime();
                    end(minecraft, again ? null : "swam up for air, air " + air(player));
                } else if (ticks > BREATHE_TICKS) {
                    end(minecraft, "could not reach air, air " + air(player));
                    handBack = "under water and no way up, air " + air(player);
                } else {
                    player.setXRot(UPWARD);
                    minecraft.options.keyShift.setDown(false);
                    minecraft.options.keyJump.setDown(true);
                    minecraft.options.keyUp.setDown(true);
                }
            }
            case STEP_OUT -> {
                if (!burning(minecraft)) {
                    end(minecraft, "stepped out of the fire, health " + health(player));
                } else if (ticks > STEP_OUT_TICKS) {
                    end(minecraft, "could not step out of the fire, health " + health(player));
                    handBack = "standing in fire, health " + health(player);
                } else if (ticks == 1) {
                    baritone().getCustomGoalProcess().setGoalAndPath(new GoalRunAway(STEP_OUT_BLOCKS, player.blockPosition()));
                } else if (!baritone().getPathingBehavior().isPathing()) {
                    minecraft.options.keyDown.setDown(true);
                }
            }
            case QUENCH -> {
                if (!player.isOnFire() || player.isInWater()) {
                    end(minecraft, "put the fire out in water, health " + health(player));
                } else if (ticks > QUENCH_TICKS) {
                    end(minecraft, "could not reach water to put the fire out, health " + health(player));
                } else if (ticks % REPATH_TICKS == 1) {
                    BlockPos water = water(minecraft);
                    if (water != null) {
                        baritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(water));
                    }
                }
            }
            case BACK_OFF -> backOff(minecraft);
            case FIGHT -> fight(minecraft);
            case FLEE -> flee(minecraft);
            case RETURN -> comeBack(minecraft);
            case EAT -> eat(minecraft);
            case NOTHING -> {
            }
        }
    }

    private static void backOff(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        Entity creeper = target;
        if (creeper == null || !creeper.isAlive() || creeper.isRemoved()) {
            end(minecraft, (creeper != null && creeper.isRemoved() && creeper.distanceTo(player) < CREEPER_CLEAR_BLOCKS ? "the creeper blew up" : "the creeper is gone")
                    + ", health " + health(player));
            return;
        }
        double distance = creeper.distanceTo(player);
        boolean hissing = hissing((Creeper) creeper);
        Entity attacker = Senses.hurtLately(minecraft, REPATH_TICKS) ? Senses.lastAttacker() : null;
        boolean pressed = attacker != null && attacker != creeper && attacker.isAlive() && reach(player, attacker) <= REACH_BLOCKS && distance > CREEPER_BLOCKS;
        if (!hissing && distance >= CREEPER_CLEAR_BLOCKS || pressed || ticks > BACK_OFF_TICKS) {
            end(minecraft, (pressed ? "stopped backing off from a creeper, " + Senses.describe(attacker) + " struck me"
                    : hissing ? "backed off from a creeper that kept after me" : "backed off from a creeper") + ", health " + health(player));
            return;
        }
        if (shielded(player)) {
            look(player, creeper.getEyePosition());
            minecraft.options.keyUse.setDown(true);
        } else if (!knockBack(minecraft, creeper) && ticks % REPATH_TICKS == 1) {
            baritone().getCustomGoalProcess().setGoalAndPath(new GoalRunAway(CREEPER_CLEAR_BLOCKS, creeper.blockPosition()));
        }
    }

    private static boolean knockBack(Minecraft minecraft, Entity creeper) {
        LocalPlayer player = minecraft.player;
        if (!weapon(player.getMainHandItem()) || reach(player, creeper) > REACH_BLOCKS || player.getAttackStrengthScale(HALF) < READY) {
            return false;
        }
        look(player, creeper.getEyePosition());
        if (!aimed(minecraft, creeper)) {
            return false;
        }
        KeyMapping.click(minecraft.options.keyAttack.getKey());
        return true;
    }

    private static void fight(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        Entity foe = target;
        if (foe != null && Senses.gone(foe)) {
            end(minecraft, "struck " + Senses.describe(foe) + " " + times(strikes) + ", it went out of sight; health " + fromTo(player));
            return;
        }
        if (foe == null || !foe.isAlive() || foe.isRemoved()) {
            end(minecraft, "struck " + (foe == null ? "it" : Senses.describe(foe)) + " " + times(strikes) + ", it fell; health " + fromTo(player));
            return;
        }
        double out = flat(player.position(), home);
        if (out >= PURSUIT_BLOCKS && reach(player, foe) > REACH_BLOCKS) {
            if (letGo.add(foe.getId())) {
                said.add("let " + Senses.describe(foe) + " go " + Senses.side(player, foe.position()) + ", " + Math.round(out)
                        + " blocks from where I stood; health " + fromTo(player));
            }
            stop(minecraft, true);
            return;
        }
        if (foe.distanceTo(player) > LOST_BLOCKS) {
            end(minecraft, Senses.describe(foe) + " got away, " + Math.round(foe.distanceTo(player)) + " blocks " + Senses.side(player, foe.position())
                    + "; health " + fromTo(player));
            return;
        }
        if (player.getHealth() <= player.getMaxHealth() * LOW_SHARE && !cornered(minecraft, foe)) {
            said.add("fought " + Senses.describe(foe) + ", health " + fromTo(player) + ": broke off");
            fled = names(foes.isEmpty() ? List.of(foe) : foes);
            doing = Doing.FLEE;
            ticks = 0;
            release(minecraft);
            runFor(minecraft);
            return;
        }
        if (deep(foe) && !deep(player)) {
            end(minecraft, Senses.describe(foe) + " went into deep water: left it; health " + fromTo(player));
            return;
        }
        if (strike(minecraft, foe, ticks, false)) {
            strikes++;
        }
    }

    static boolean strike(Minecraft minecraft, Entity foe, long tick, boolean stand) {
        LocalPlayer player = minecraft.player;
        look(player, foe.getEyePosition());
        double distance = reach(player, foe);
        IBaritone baritone = baritone();
        boolean shield = shielded(player);
        if (distance > REACH_BLOCKS) {
            minecraft.options.keyUse.setDown(shield && ranged(foe) && distance < SHIELD_BLOCKS * 2);
            if (!stand && tick % REPATH_TICKS == 1 && !(foe instanceof FlyingMob)) {
                baritone.getCustomGoalProcess().setGoalAndPath(new GoalNear(foe.blockPosition(), 1));
            }
            return false;
        }
        baritone.getPathingBehavior().cancelEverything();
        if (player.getAttackStrengthScale(HALF) >= READY) {
            minecraft.options.keyUse.setDown(false);
            if (!player.isUsingItem() && aimed(minecraft, foe)) {
                KeyMapping.click(minecraft.options.keyAttack.getKey());
                return true;
            }
            return false;
        }
        minecraft.options.keyUse.setDown(shield);
        return false;
    }

    private static void runFor(Minecraft minecraft) {
        refuge = refuge(minecraft);
        fleeFrom = minecraft.player.position();
    }

    private static void flee(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        List<Entity> near = foes.stream().filter(foe -> foe.distanceTo(player) < RUN_BLOCKS).toList();
        boolean safe = refuge != null && flat(player.position(), refuge.at()) <= SAFE_BLOCKS;
        boolean shaken = refuge == null && near.isEmpty() && !Senses.hurtLately(minecraft, REPATH_TICKS * 2);
        if (safe || shaken || ticks > FLEE_TICKS) {
            String how = safe && flat(player.position(), fleeFrom) <= HOME_BLOCKS ? "stayed by " + refuge.name() + " with " + fled + " about"
                    : "ran from " + fled + (safe ? " to " + refuge.name() : "");
            fledAt = minecraft.level.getGameTime();
            end(minecraft, how + "; health " + fromTo(player));
            handBack = how + ", health " + health(player);
            return;
        }
        if (refuge != null) {
            if (!baritone().getCustomGoalProcess().isActive()) {
                baritone().getCustomGoalProcess().setGoalAndPath(new GoalNear(BlockPos.containing(refuge.at()), (int) SAFE_BLOCKS - 1));
            }
        } else if (ticks % REPATH_TICKS == 1 && !near.isEmpty()) {
            BlockPos[] from = near.stream().map(Entity::blockPosition).toArray(BlockPos[]::new);
            baritone().getCustomGoalProcess().setGoalAndPath(new GoalRunAway(RUN_BLOCKS, from));
        }
    }

    private static void comeBack(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        double away = flat(player.position(), home);
        if (away <= HOME_BLOCKS) {
            end(minecraft, "walked back " + Math.round(returnFrom) + " blocks to where I stood");
        } else if (ticks > RETURN_TICKS) {
            end(minecraft, "could not walk back to where I stood, " + Math.round(away) + " blocks " + Senses.side(player, home));
        } else if (ticks % REPATH_TICKS == 1 && !baritone().getCustomGoalProcess().isActive()) {
            baritone().getCustomGoalProcess().setGoalAndPath(new GoalNear(BlockPos.containing(home), 1));
        }
    }

    private static void eat(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        ItemStack held = player.getMainHandItem();
        boolean eaten = held.getCount() < countFrom || !held.getHoverName().getString().toLowerCase(Locale.ROOT).equals(eating);
        if (eaten) {
            minecraft.options.keyUse.setDown(false);
            player.getInventory().selected = slotFrom;
            fedAt = minecraft.level.getGameTime();
            end(minecraft, "ate " + eating + ", food " + foodFrom + " → " + player.getFoodData().getFoodLevel());
            return;
        }
        if (!foes.isEmpty() || ticks > EAT_TICKS) {
            minecraft.options.keyUse.setDown(false);
            player.getInventory().selected = slotFrom;
            end(minecraft, foes.isEmpty() ? "could not eat " + eating : "stopped eating: " + Senses.describe(foes.getFirst()) + " came");
            return;
        }
        minecraft.options.keyUse.setDown(!started || player.isUsingItem());
        started |= player.isUsingItem();
    }

    private static void end(Minecraft minecraft, @Nullable String line) {
        if (line != null) {
            said.add(line);
        }
        stop(minecraft, line != null);
    }

    private static void stop(Minecraft minecraft, boolean done) {
        release(minecraft);
        baritone().getPathingBehavior().cancelEverything();
        Doing ended = doing;
        doing = Doing.NOTHING;
        target = null;
        double away = home == null ? 0 : flat(minecraft.player.position(), home);
        if (done && standing && (ended == Doing.FIGHT || ended == Doing.BACK_OFF) && away > HOME_BLOCKS) {
            doing = Doing.RETURN;
            ticks = 0;
            returnFrom = away;
            return;
        }
        finished = true;
    }

    @Nullable
    private static boolean cornered(Minecraft minecraft, Entity foe) {
        LocalPlayer player = minecraft.player;
        if (!armed(player) || foe.distanceTo(player) > PURSUIT_BLOCKS || deep(foe) && !deep(player)) {
            return false;
        }
        if (boxed(minecraft, player)) {
            return true;
        }
        Refuge shelter = refuge(minecraft);
        return shelter != null && !shelter.name().equals(GOAL) && flat(player.position(), shelter.at()) <= CORNERED_BLOCKS;
    }

    private static boolean boxed(Minecraft minecraft, LocalPlayer player) {
        BlockPos feet = player.blockPosition();
        return Direction.Plane.HORIZONTAL.stream().map(feet::relative)
                .allMatch(side -> !minecraft.level.getBlockState(side).getCollisionShape(minecraft.level, side).isEmpty());
    }

    private static Refuge refuge(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        List<Refuge> places = new ArrayList<>();
        ChunkPos.rangeClosed(player.chunkPosition(), (int) REFUGE_BLOCKS / 16 + 1)
                .flatMap(chunk -> minecraft.level.getChunk(chunk.x, chunk.z).getBlockEntities().values().stream())
                .filter(entity -> entity instanceof CampfireBlockEntity && lit(entity.getBlockState()))
                .forEach(entity -> places.add(new Refuge("the fire", Vec3.atBottomCenterOf(entity.getBlockPos()))));
        HudState.compass.markers().stream().filter(marker -> marker.kind() == CompassPayload.Kind.TENT)
                .forEach(marker -> places.add(new Refuge("the tent", new Vec3(marker.x() + HALF, player.getY(), marker.z() + HALF))));
        GoalXZ goal = Legs.destination();
        if (goal != null) {
            places.add(new Refuge(GOAL, new Vec3(goal.getX() + HALF, player.getY(), goal.getZ() + HALF)));
        }
        return places.stream().filter(place -> flat(player.position(), place.at()) <= REFUGE_BLOCKS)
                .min(Comparator.comparingDouble(place -> flat(player.position(), place.at()))).orElse(null);
    }

    private static double flat(Vec3 from, Vec3 to) {
        return Math.hypot(from.x - to.x, from.z - to.z);
    }

    private static List<Entity> foes(Minecraft minecraft, @Nullable Legs.Kind leg) {
        LocalPlayer player = minecraft.player;
        Entity attacker = Senses.hurtLately(minecraft, RECENT_TICKS) ? Senses.lastAttacker() : null;
        return StreamSupport.stream(minecraft.level.entitiesForRendering().spliterator(), false)
                .filter(entity -> entity.isAlive() && entity != player && !(entity instanceof Creeper))
                .filter(entity -> entity == attacker && entity.distanceTo(player) <= SEEN_ATTACKER_BLOCKS && (Senses.visible(minecraft, entity)
                        || entity.distanceTo(player) <= SHIELD_BLOCKS)
                        || Senses.hostile(entity) && !(entity instanceof EnderMan) && entity.distanceTo(player) <= THREAT_BLOCKS
                        && Senses.visible(minecraft, entity) && (Senses.coming(player, entity) || entity.distanceTo(player) <= REACH_BLOCKS + 1))
                .sorted(Comparator.comparingDouble(player::distanceTo))
                .collect(Collectors.toList());
    }

    @Nullable
    private static Entity creeper(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        return StreamSupport.stream(minecraft.level.entitiesForRendering().spliterator(), false)
                .filter(entity -> entity instanceof Creeper creeper && creeper.isAlive() && creeper.distanceTo(player) <= CREEPER_BLOCKS
                        && (hissing(creeper) || creeper.distanceTo(player) <= REACH_BLOCKS))
                .min(Comparator.comparingDouble(player::distanceTo))
                .orElse(doing == Doing.BACK_OFF ? target : null);
    }

    private static boolean hissing(Creeper creeper) {
        return creeper.getSwelling(1) > 0;
    }

    @Nullable
    private static BlockPos water(Minecraft minecraft) {
        BlockPos feet = minecraft.player.blockPosition();
        return BlockPos.betweenClosedStream(feet.offset(-QUENCH_BLOCKS, -2, -QUENCH_BLOCKS), feet.offset(QUENCH_BLOCKS, 2, QUENCH_BLOCKS))
                .filter(pos -> minecraft.level.getFluidState(pos).is(FluidTags.WATER) && minecraft.level.getFluidState(pos.above()).isEmpty()
                        && minecraft.level.getBlockState(pos.above()).getCollisionShape(minecraft.level, pos.above()).isEmpty())
                .map(BlockPos::immutable)
                .min(Comparator.comparingDouble(pos -> pos.distSqr(feet)))
                .orElse(null);
    }

    private static boolean burning(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        BlockState below = minecraft.level.getBlockState(player.blockPosition().below());
        boolean touching = BlockPos.betweenClosedStream(player.getBoundingBox().inflate(TOUCH).move(0, -TOUCH, 0))
                .map(pos -> minecraft.level.getBlockState(pos)).anyMatch(state -> state.is(BlockTags.FIRE) || lit(state));
        return player.isInLava() || touching || below.is(Blocks.MAGMA_BLOCK) && !player.isSteppingCarefully();
    }

    private static boolean lit(BlockState state) {
        return state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT);
    }

    private static boolean hungry(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        int food = player.getFoodData().getFoodLevel();
        boolean wanting = (food <= HUNGRY_FOOD || player.getHealth() < player.getMaxHealth() && food < HEALING_FOOD)
                && minecraft.level.getGameTime() - fedAt > FED_TICKS;
        boolean quiet = StreamSupport.stream(minecraft.level.entitiesForRendering().spliterator(), false)
                .noneMatch(entity -> Senses.hostile(entity) && entity.isAlive() && entity.distanceTo(player) <= QUIET_BLOCKS);
        return wanting && quiet && minecraft.screen == null && player.onGround() && !player.isInWater() && food(player) >= 0;
    }

    private static int food(LocalPlayer player) {
        int best = -1;
        int most = 0;
        boolean starving = player.getFoodData().getFoodLevel() <= STARVING_FOOD;
        for (int slot = 0; slot < HOTBAR; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            FoodProperties food = stack.getFoodProperties(player);
            if (food != null && player.canEat(food.canAlwaysEat()) && (food.effects().isEmpty() && !raw(player, stack) || starving) && food.nutrition() > most) {
                best = slot;
                most = food.nutrition();
            }
        }
        return best;
    }

    private static boolean raw(LocalPlayer player, ItemStack stack) {
        return player.level().getRecipeManager().getRecipeFor(RecipeType.SMOKING, new SingleRecipeInput(stack), player.level()).isPresent();
    }

    private static boolean armed(LocalPlayer player) {
        for (int slot = 0; slot < HOTBAR; slot++) {
            if (weapon(player.getInventory().getItem(slot))) {
                return true;
            }
        }
        return false;
    }

    private static void arm(LocalPlayer player) {
        if (weapon(player.getMainHandItem())) {
            return;
        }
        for (int slot = 0; slot < HOTBAR; slot++) {
            if (weapon(player.getInventory().getItem(slot))) {
                player.getInventory().selected = slot;
                said.add("took " + player.getMainHandItem().getHoverName().getString());
                return;
            }
        }
    }

    private static boolean weapon(ItemStack stack) {
        return (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.getItem() instanceof TridentItem) && !LastUse.lastUse(stack);
    }

    private static boolean shielded(LocalPlayer player) {
        return player.getOffhandItem().getUseAnimation() == UseAnim.BLOCK && player.getMainHandItem().getUseAnimation() == UseAnim.NONE;
    }

    private static boolean ranged(Entity foe) {
        return foe instanceof AbstractSkeleton || foe instanceof Pillager;
    }

    static boolean deep(Entity entity) {
        return entity.isUnderWater() || entity.isInWater() && entity.level().getFluidState(entity.blockPosition().below()).is(FluidTags.WATER);
    }

    private static boolean aimed(Minecraft minecraft, Entity foe) {
        HitResult hit = minecraft.hitResult;
        return hit instanceof EntityHitResult entity && entity.getEntity() == foe
                || (hit == null || hit.getType() == HitResult.Type.MISS) && reach(minecraft.player, foe) <= REACH_BLOCKS;
    }

    private static double reach(LocalPlayer player, Entity foe) {
        return Math.max(0, foe.distanceTo(player) - foe.getBbWidth() / 2);
    }

    private static void walkOn(LocalPlayer player, String why) {
        for (Entity foe : foes) {
            if (passed.add(foe.getId())) {
                said.add(Senses.describe(foe) + " came at me " + Senses.side(player, foe.position()) + ", " + why + ": walked on");
            }
        }
    }

    private static void look(LocalPlayer player, Vec3 at) {
        Vec3 look = at.subtract(player.getEyePosition());
        player.setYRot((float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90);
        player.setXRot((float) -(Mth.atan2(look.y, look.horizontalDistance()) * Mth.RAD_TO_DEG));
    }

    private static void release(Minecraft minecraft) {
        for (KeyMapping key : List.of(minecraft.options.keyUp, minecraft.options.keyDown, minecraft.options.keyLeft, minecraft.options.keyRight,
                minecraft.options.keyJump, minecraft.options.keySprint, minecraft.options.keyAttack, minecraft.options.keyUse)) {
            key.setDown(false);
        }
    }

    private static String names(List<Entity> entities) {
        return entities.stream().map(Senses::describe).distinct().collect(Collectors.joining(" and "));
    }

    private static String times(int count) {
        return count == 1 ? "once" : count + " times";
    }

    private static String fromTo(LocalPlayer player) {
        return Math.round(healthFrom) + " → " + Math.round(player.getHealth());
    }

    private static String health(LocalPlayer player) {
        return Math.round(player.getHealth()) + " of " + Math.round(player.getMaxHealth());
    }

    private static String air(LocalPlayer player) {
        return Math.max(0, player.getAirSupply()) * AIR_STEPS / player.getMaxAirSupply() + " of " + AIR_STEPS;
    }

    private static IBaritone baritone() {
        return BaritoneAPI.getProvider().getPrimaryBaritone();
    }
}

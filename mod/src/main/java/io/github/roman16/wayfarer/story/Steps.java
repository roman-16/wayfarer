package io.github.roman16.wayfarer.story;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Clock;
import io.github.roman16.wayfarer.Fire;
import io.github.roman16.wayfarer.Halls;
import io.github.roman16.wayfarer.Journey;
import io.github.roman16.wayfarer.Music;
import io.github.roman16.wayfarer.Nights;
import io.github.roman16.wayfarer.Record;
import io.github.roman16.wayfarer.Tents;
import io.github.roman16.wayfarer.Shards;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class Steps {
    static final String CLUE = Wayfarer.MODID + "_clue_";
    static final String CLUE_BOX = Wayfarer.MODID + "_clue_box";
    static final String FOUND = Wayfarer.MODID + "_found_";
    static final String OWN_CAMP = "own_camp";
    static final String DAWN = "06:00";
    private static final String[] WAYS = {"east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east"};
    private static final String ENTERED = "entered";
    private static final String STARTED = "started";
    private static final String DECLINED = "declined";
    private static final String STONE = "stone";
    private static final String MOON = "moon";
    private static final String DAY = "day";
    private static final String WON_BEFORE = "won_before";
    private static final Set<String> SPOKEN_APART = Set.of("say", "talk", "visit");
    private static final String TAGGED = "tagged";
    static final String BURIED = "buried";
    private static final String BURIAL = "burial";
    static final String MUSIC = "music";
    static final String RESTED = "rested";
    static final String RESTED_AT = "rested_at";
    static final String RESTED_TICKS = "rested_ticks";
    private static final String UNTIL = "until";
    private static final int BLIND_TICKS = 40;
    private static final int SEEN_NEAR = 8;
    private static final int TICKS_PER_SECOND = 20;
    private static final String TOUCH = "touch";
    private static final String LEAVES = "leaves";
    private static final String ASKING = "asking";
    private static final String CALLS = "calls";
    private static final String MOVED = "moved";
    private static final String MOVES = "moves";
    private static final String FATES = "fates";
    private static final String AWAITS = "awaits";
    private static final String LOSSES = "losses";
    private static final String AWAY = "away";
    static final String LIVES = "lives";
    static final String LOST = "lost";
    private static final String HOLLOW = "the_hollow/";
    static final String BUILDS = "builds";
    private static final Set<String> NIGHT_KINDS = Set.of("dawn", "dusk", "hold", "sail", "sea");
    private static final double KEPT = 1.5;
    private static final int PAST_THE_WALK = 2;
    static final String WALK_TO = "walk_to";

    private Steps() {}

    static boolean started(Engine.Active active) {
        return active.scratch().getBoolean(STARTED);
    }

    static boolean entered(Engine.Active active) {
        return active.scratch().getBoolean(ENTERED);
    }

    static boolean ownNight(Story.Step step) {
        return NIGHT_KINDS.contains(step.kind());
    }

    static void reset(MinecraftServer server, Engine.Active active) {
        List.copyOf(active.scratch().getAllKeys()).forEach(active.scratch()::remove);
        Progress.of(server).setDirty();
    }

    static void enter(MinecraftServer server, Engine.Active active) {
        Story.Step step = active.step();
        active.scratch().putBoolean(ENTERED, true);
        Progress.of(server).setDirty();
        Record.event(server, "step begun", null, line -> {
            line.addProperty("quest", active.quest().id());
            line.addProperty("step", step.id());
            line.addProperty("kind", step.kind());
        });
        if (!step.kind().equals(Caravan.KIND) && active.quest().kind().equals(Engine.MAIN)) {
            Caravan.walkedOn(server);
        }
        moves(server, step);
        music(server, step);
        moon(server, step);
        if (step.spec().has(LEAVES)) {
            step.spec().getAsJsonArray(LEAVES).forEach(person -> People.leave(server, person.getAsString()));
        }
        if (step.spec().has(BUILDS)) {
            Places.build(server, step.text(BUILDS));
        }
        List<Story.Step> steps = active.quest().steps();
        int index = steps.indexOf(step);
        if (index + 1 < steps.size() && steps.get(index + 1).kind().equals("dusk")) {
            Places.build(server, steps.get(index + 1).text("place"));
        }
        toll(server, active);
        if (active.quest().steps().indexOf(step) == 0) {
            Deeds.begun(server, active.quest());
        }
        switch (step.kind()) {
            case "talk" -> {}
            case "dusk" -> {
                Balance.Clock clock = Balance.get().clock();
                active.scratch().putLong(UNTIL, Clock.pass(Engine.overworld(server), clock.dusk(), clock.afternoonSeconds(), clock.night()));
                Places.build(server, step.text("place"));
            }
            case "hold" -> Hold.start(server, active);
            case "dawn" -> active.scratch().putLong(UNTIL, Clock.pass(Engine.overworld(server), DAWN, Balance.get().clock().dawnSeconds(), Balance.get().clock().road()));
            case "caravan" -> Caravan.headIn(server);
            case "reach", "sail" -> Places.build(server, step.text("place"));
            case "clues", "say" -> {
                if (step.spec().has("place")) {
                    Places.build(server, step.text("place"));
                }
                if (step.kind().equals("say")) {
                    Talk.say(active.quest().id(), anchor(server, step), step.lines(), Engine.fill(Progress.of(server)));
                    Talk.then(active.quest().id(), () -> Engine.complete(server, active));
                }
            }
            case "vision" -> Places.build(server, step.text("at"));
            case "locks" -> Places.build(server, Locks.NAME);
            case "escort" -> Escort.enter(server, active);
            case "walk" -> {
                if (!felt(active)) {
                    break;
                }
                BlockPos from = Engine.at(server, step.text("from"));
                Vec3 way = Caravan.way(server).multiply(1, 0, 1).normalize().scale(Places.leaving(step.text("from")) * PAST_THE_WALK);
                Progress.of(server).place(WALK_TO, BlockPos.containing(from.getX() + way.x, from.getY(), from.getZ() + way.z));
                Progress.of(server).setDirty();
            }
            case "sleep" -> active.scratch().putLong(DAY, Math.floorDiv(Engine.overworld(server).getDayTime(), Clock.DAY));
            case "wins" -> active.scratch().putInt(WON_BEFORE, Dice.won(server));
            default -> {}
        }
        if (!SPOKEN_APART.contains(step.kind())) {
            Talk.say(active.quest().id(), null, step.lines(), Engine.fill(Progress.of(server)));
        }
        Journal.syncAll(server);
    }

    static Optional<String> goal(Engine.Active active) {
        Story.Step step = active.step();
        return switch (step.kind()) {
            case "reach" -> Optional.of(step.text("place"));
            case "sail", "sea" -> Optional.of(Voyage.FAR_SHORE);
            case "vision" -> Optional.of(step.text("at"));
            case "worm" -> Optional.of(SandSingers.LAIR);
            case "locks" -> Optional.of(Locks.NAME);
            case "escort" -> Optional.of(step.text("place"));
            case "give" -> Optional.of(step.spec().has("place") ? step.text("place") : Caravan.NAME);
            case "caravan", "visit" -> Optional.of(Caravan.NAME);
            case "rest", "hold" -> Optional.of(step.text("at"));
            case "shard" -> Optional.of(step.text("at"));
            case "clues" -> step.spec().has("place") ? Optional.of(step.text("place")) : Optional.empty();
            case "memory" -> Optional.of(step.spec().has("place") ? step.text("place") : Story.get().memory(step.text("memory")).place());
            case "walk" -> felt(active) ? Optional.of(WALK_TO) : Optional.empty();
            case "talk" -> Optional.ofNullable(step.with()).map(person -> Places.PERSON + person + seated(step, person).map(seat -> Places.AT + seat).orElse(""));
            default -> Optional.empty();
        };
    }

    private static Optional<String> seated(Story.Step step, String person) {
        return step.spec().has(MOVES) && step.spec().getAsJsonObject(MOVES).has(person)
                ? Optional.of(step.spec().getAsJsonObject(MOVES).getAsJsonObject(person).get("place").getAsString())
                : Optional.empty();
    }

    static boolean felt(Engine.Active active) {
        return active.step().spec().has("known") && active.step().text("known").equals("feeling");
    }

    static void music(MinecraftServer server, Story.Step step) {
        if (step.spec().has(MUSIC)) {
            Music.play(server, step.text(MUSIC), true);
        }
    }

    private static void moon(MinecraftServer server, Story.Step step) {
        if (step.spec().has(MOON)) {
            Nights.redMoon(server);
        }
    }

    static void resume(MinecraftServer server, Engine.Active active) {
        music(server, active.step());
        moon(server, active.step());
        Balance.Clock clock = Balance.get().clock();
        long until = active.scratch().getLong(UNTIL);
        switch (active.step().kind()) {
            case "dusk" -> {
                Clock.until(Engine.overworld(server), until, clock.afternoonSeconds(), clock.night());
                Places.build(server, active.step().text("place"));
            }
            case "hold" -> Hold.resume(server, active);
            case "dawn" -> Clock.until(Engine.overworld(server), until, clock.dawnSeconds(), clock.road());
            case "reach", "sail" -> Places.build(server, active.step().text("place"));
            case "vision" -> Places.build(server, active.step().text("at"));
            case "sea" -> Voyage.resume(server, active);
            case "bury" -> {
                if (active.scratch().contains(BURIED)) {
                    ask(active);
                }
            }
            case "shard" -> {
                if (active.step().with() != null && active.scratch().contains(STONE)) {
                    active.scratch().remove(STONE);
                    Progress.of(server).setDirty();
                }
            }
            default -> {}
        }
    }

    static void tick(MinecraftServer server, Engine.Active active) {
        Story.Step step = active.step();
        CompoundTag scratch = active.scratch();
        switch (step.kind()) {
            case "talk" -> {
                String with = step.with();
                if (!scratch.getBoolean(STARTED) && !scratch.getBoolean(DECLINED) && !Talk.busy() && with != null && Engine.pursued(server, active.quest())
                        && People.find(server, with).filter(body -> seen(server, body, step.spec().has(CALLS) ? step.number(CALLS)
                                : Balance.get().talk().startBlocks()) && !(Nights.hard(server) && Caravan.within(server, Engine.overworld(server), body.position()))).isPresent()) {
                    start(server, active);
                }
            }
            case "walk" -> {
                BlockPos from = Engine.at(server, step.text("from"));
                int away = Places.leaving(step.text("from"));
                if (Engine.players(server).stream().anyMatch(player -> player.level().dimension() == Level.OVERWORLD
                        && horizontal(player.blockPosition(), from) >= (double) away * away)) {
                    Engine.complete(server, active);
                }
            }
            case "dusk" -> {
                Optional<BlockPos> waiting = Places.place(server, step.text("place"));
                int within = Places.arrival(step.text("place"));
                if (Clock.passed() && Places.ready(server, step.text("place")) && !Talk.busy() && Engine.players(server).stream().anyMatch(player -> Fire.resting(player)
                        || waiting.filter(at -> player.level().dimension() == Level.OVERWORLD && player.blockPosition().distSqr(at) <= (double) within * within).isPresent())) {
                    Journey.of(server).wake();
                    Engine.complete(server, active);
                }
            }
            case "reach" -> {
                Shores.tick(server, active);
                Optional<BlockPos> goal = Places.place(server, step.text("place"));
                int within = Places.arrival(step.text("place"));
                boolean below = goal.filter(at -> !Engine.overworld(server).canSeeSky(at.above())).isPresent();
                if (goal.isPresent() && Places.ready(server, step.text("place")) && Engine.players(server).stream()
                        .anyMatch(player -> player.level().dimension() == Level.OVERWORLD && player.blockPosition().distSqr(goal.get()) <= (double) within * within
                                && (!below || !player.level().canSeeSky(player.blockPosition())) && sees(player, goal.get()))) {
                    Engine.complete(server, active);
                }
            }
            case "horse" -> {
                Optional<BlockPos> fire = Places.place(server, Caravan.NAME);
                int near = Balance.get().story().nearBlocks();
                if (fire.isPresent()) {
                    Engine.overworld(server).getEntitiesOfClass(AbstractHorse.class, new AABB(fire.get()).inflate(near),
                            horse -> horse.isTamed() && (!step.spec().has(TAGGED) || horse.getTags().contains(step.text(TAGGED)))).stream()
                            .findFirst().ifPresent(horse -> {
                                if (step.spec().has("keeper")) {
                                    Caravan.adopt(server, horse, step.text("name"), step.text("keeper"));
                                }
                                Engine.complete(server, active);
                            });
                }
            }
            case "shard" -> {
                List<String> taken = taken(server, active);
                List<ResourceLocation> claimed = Journey.claimed(server);
                if (!taken.isEmpty() && taken.stream().allMatch(eye -> claimed.contains(shard(eye)))) {
                    gifts(Progress.of(server), step, taken);
                    Engine.complete(server, active);
                } else if (step.with() != null && !scratch.contains(STONE) && !Talk.busy() && Engine.pursued(server, active.quest())
                        && People.find(server, step.with()).filter(body -> seen(server, body, step.spec().has(CALLS) ? step.number(CALLS)
                                : Balance.get().talk().startBlocks())).isPresent()) {
                    ask(server, active, Engine.at(server, step.text("at")));
                }
            }
            case "hold" -> Hold.tick(server, active);
            case "sail" -> Voyage.sail(server, active);
            case "vision" -> Vision.tick(server, active);
            case "worm" -> SandSingers.tick(server, active);
            case "locks" -> Locks.tick(server, active);
            case "escort" -> Escort.tick(server, active);
            case "chase" -> Chase.tick(server, active);
            case "slay" -> {
                if (Journey.over(server)) {
                    Engine.complete(server, active);
                }
            }
            case "wait" -> {
                if (step.spec().has(AWAITS) && Progress.of(server).done(step.text(AWAITS))) {
                    Engine.complete(server, active);
                }
            }
            case "sea" -> Voyage.tick(server, active);
            case "visit" -> Visit.tick(server, active);
            case "gather" -> {
                if (Engine.players(server).stream().anyMatch(player -> carried(player, step) >= step.number("count"))) {
                    Engine.complete(server, active);
                }
            }
            case "wins" -> {
                if (won(server, active) >= step.number("count")) {
                    Engine.complete(server, active);
                }
            }
            case "read" -> {
                if (read(server, step) >= step.spec().getAsJsonArray("pages").size()) {
                    Engine.complete(server, active);
                }
            }
            case "dawn" -> {
                if (Clock.passed() && !Talk.busy()) {
                    Engine.complete(server, active);
                }
            }
            case "caravan" -> {
                if (Clock.evening() && (Places.ready(server, Caravan.NAME) && home(server) || camped(server) && !active.quest().act().equals(Engine.PROLOGUE))) {
                    Engine.complete(server, active);
                }
            }
            case "sleep" -> {
                if (Math.floorDiv(Engine.overworld(server).getDayTime(), Clock.DAY) > scratch.getLong(DAY)) {
                    Engine.complete(server, active);
                }
            }
            default -> {}
        }
    }

    static boolean interact(ServerPlayer player, Engine.Active active, Entity target) {
        Story.Step step = active.step();
        MinecraftServer server = player.server;
        switch (step.kind()) {
            case "talk" -> {
                if (!active.scratch().getBoolean(STARTED) && !Talk.busy() && People.who(target).filter(person -> person.equals(step.with())
                        || active.scratch().getBoolean(DECLINED) && step.lines().stream().anyMatch(line -> person.equals(line.by()))).isPresent()) {
                    Engine.track(player, active.quest().id());
                    start(server, active);
                    return true;
                }
            }
            case "give" -> {
                if (People.who(target).filter(person -> person.equals(step.with())).isPresent() && wanted(step, player.getMainHandItem())) {
                    give(player, active, target);
                    return true;
                }
            }
            case "bury" -> {
                if (!active.scratch().contains(BURIED) && People.who(target).filter(person -> person.equals(step.text("whom"))).isPresent()) {
                    bury(player, active, target);
                    return true;
                }
            }
            case "memory" -> {
                if (step.spec().has(TOUCH) && touches(target, step.text(TOUCH))) {
                    Optional<BlockPos> stone = Memories.stone(server, Story.get().memory(step.text("memory")));
                    return stone.isPresent() && Memories.touch(player, stone.get());
                }
            }
            case "clues" -> {
                for (Story.Clue clue : step.clues()) {
                    if (!target.getTags().contains(CLUE + clue.id()) || Engine.found(active.scratch()).contains(clue.id())) {
                        continue;
                    }
                    if (clue.last() && !counts(server, active, clue)) {
                        int left = needed(server, active) - 1 - Engine.found(active.scratch()).size();
                        player.displayClientMessage(Component.translatable(left == 1 ? "wayfarer.clue.not_yet_one" : "wayfarer.clue.not_yet", left), true);
                        return true;
                    }
                    if (counts(server, active, clue)) {
                        Engine.find(active.scratch(), clue.id());
                        Progress.of(server).setDirty();
                        Tasks.did(player, "look");
                        Clues.found(player.serverLevel(), target, clue.id());
                        Talk.say(active.quest().id(), target.position(), new Story.Line(clue.by(), clue.text()));
                        if (clue.opens() != null) {
                            Pages.openOldScript(player, clue.opens());
                        }
                        if (Engine.found(active.scratch()).size() >= needed(server, active)) {
                            Talk.then(active.quest().id(), () -> Engine.complete(server, active));
                        }
                        Journal.syncAll(server);
                        return true;
                    }
                }
            }
            default -> {}
        }
        return false;
    }

    static List<String> eyes(Story.Step step) {
        return step.spec().getAsJsonArray("eyes").asList().stream().map(JsonElement::getAsString).toList();
    }

    static ResourceLocation shard(String eye) {
        return Shards.id(eye);
    }

    static List<String> taken(MinecraftServer server, Engine.Active active) {
        Story.Step step = active.step();
        Optional<String> chosen = Progress.of(server).choice(active.quest().id(), step.id());
        if (chosen.isPresent()) {
            return step.choices().stream().filter(choice -> choice.id().equals(chosen.get())).findFirst().map(Story.Choice::takes).orElse(List.of());
        }
        List<ResourceLocation> claimed = Journey.claimed(server);
        return eyes(step).stream().filter(eye -> claimed.contains(shard(eye))).toList();
    }

    static void gifts(Progress progress, Story.Step step, List<String> eyes) {
        if (!step.spec().has("gifts")) {
            return;
        }
        JsonObject gifts = step.spec().getAsJsonObject("gifts");
        eyes.stream().filter(gifts::has).map(eye -> gifts.get(eye).getAsString()).forEach(progress.gifts::add);
        progress.setDirty();
    }

    static boolean camped(MinecraftServer server) {
        return Engine.most(server, Engine.players(server).stream().filter(Clock::camped).count());
    }

    static boolean home(MinecraftServer server) {
        BlockPos fire = Places.place(server, Caravan.NAME).orElseThrow();
        int near = Balance.get().story().nearBlocks();
        return Engine.most(server, Engine.players(server).stream().filter(player -> player.level().dimension() == Level.OVERWORLD
                && horizontal(player.blockPosition(), fire) <= (double) near * near).count());
    }

    static int won(MinecraftServer server, Engine.Active active) {
        return Dice.won(server) - active.scratch().getInt(WON_BEFORE);
    }

    static int read(MinecraftServer server, Story.Step step) {
        Progress progress = Progress.of(server);
        return (int) step.spec().getAsJsonArray("pages").asList().stream().filter(page -> progress.pages.contains(page.getAsString())).count();
    }

    static int carried(ServerPlayer player, Story.Step step) {
        return player.getInventory().items.stream().filter(stack -> wanted(step, stack)).mapToInt(ItemStack::getCount).sum();
    }

    private static boolean wanted(Story.Step step, ItemStack stack) {
        return step.spec().has("tag") ? stack.is(TagKey.create(Registries.ITEM, ResourceLocation.parse(step.text("tag"))))
                : stack.is(BuiltInRegistries.ITEM.get(ResourceLocation.parse(step.text("item"))));
    }

    private static void give(ServerPlayer player, Engine.Active active, Entity body) {
        Story.Step step = active.step();
        int count = step.number("count");
        if (carried(player, step) < count) {
            Talk.say(active.quest().id(), body.position(), new Story.Line(step.with(), step.text("short")));
            return;
        }
        int left = count;
        for (ItemStack stack : player.getInventory().items) {
            if (left > 0 && wanted(step, stack)) {
                int taken = Math.min(left, stack.getCount());
                stack.shrink(taken);
                left -= taken;
            }
        }
        Engine.track(player, active.quest().id());
        Engine.complete(player.server, active);
    }

    static void rested(ServerPlayer player, Engine.Active active, BlockPos fire) {
        Story.Step step = active.step();
        MinecraftServer server = player.server;
        switch (step.kind()) {
            case "camp" -> {
                if (Tents.pitchedNear(player.serverLevel(), fire, Balance.get().tent().fireBlocks())) {
                    Progress.of(server).place(OWN_CAMP, fire);
                    CompoundTag rested = active.scratch().getCompound(RESTED);
                    rested.putBoolean(player.getStringUUID(), true);
                    active.scratch().put(RESTED, rested);
                    Progress.of(server).setDirty();
                    if (Engine.most(server, Engine.players(server).stream().filter(member -> rested.contains(member.getStringUUID())).count())) {
                        Engine.complete(server, active);
                    }
                }
            }
            case "rest" -> {
                Optional<BlockPos> at = Places.place(server, step.text("at"));
                int within = Balance.get().story().nearBlocks();
                long now = player.serverLevel().getGameTime();
                if (at.isEmpty() || horizontal(fire, at.get()) > (double) within * within) {
                    return;
                }
                long last = active.scratch().getLong(RESTED_AT);
                long gained = last == 0 ? Fire.REST_CHECK_TICKS : Math.min(now - last, Fire.REST_CHECK_TICKS);
                active.scratch().putLong(RESTED_AT, now);
                int rested = (int) Math.min(active.scratch().getInt(RESTED_TICKS) + gained, (long) Balance.get().story().restSeconds() * TICKS_PER_SECOND);
                active.scratch().putInt(RESTED_TICKS, rested);
                Progress.of(server).setDirty();
                if (rested >= Balance.get().story().restSeconds() * TICKS_PER_SECOND && !Talk.busy()) {
                    Engine.complete(server, active);
                }
            }
            default -> {}
        }
    }

    static void start(MinecraftServer server, Engine.Active active) {
        Story.Step step = active.step();
        active.scratch().putBoolean(STARTED, true);
        Progress.of(server).setDirty();
        if (step.spec().has("place")) {
            Places.build(server, step.text("place"));
        }
        List<Story.Line> lines = active.scratch().getBoolean(DECLINED) ? List.of(step.lines().getLast()) : step.lines();
        active.scratch().remove(DECLINED);
        Talk.say(active.quest().id(), anchor(server, step), lines, Engine.fill(Progress.of(server)));
        if (step.choices().isEmpty()) {
            Talk.then(active.quest().id(), () -> Engine.complete(server, active));
        } else {
            offer(server, active);
        }
    }

    static void offer(MinecraftServer server, Engine.Active active) {
        Story.Step step = active.step();
        String asker = step.lines().stream().map(Story.Line::by).filter(by -> by != null).reduce((first, second) -> second).orElse(null);
        Vec3 at = asker == null ? null : People.find(server, asker).map(Entity::position).orElse(null);
        Progress progress = Progress.of(server);
        List<Story.Choice> offered = step.choices().stream().filter(choice -> choice.needs() == null || progress.steps.containsKey(choice.needs()))
                .filter(choice -> choice.when() == null || choice.refused() != null || Engine.holds(progress, choice.when())).toList();
        Talk.ask(new Talk.Offer(active.quest().id(), asker, offered, at, (player, choice) -> Engine.chosen(player, active.quest().id(), step.id(), asker, choice)));
    }

    private static void bury(ServerPlayer player, Engine.Active active, Entity body) {
        MinecraftServer server = player.server;
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLIND_TICKS, 0, false, false, false));
        body.discard();
        BlockPos sign = Prologue.grave(player.serverLevel());
        active.scratch().putLong(BURIED, sign.asLong());
        Progress.of(server).setDirty();
        Talk.say(active.quest().id(), Vec3.atCenterOf(sign), active.step().lines(BURIAL), Talk.Placeholders.NONE);
        ask(active);
        Journal.syncAll(server);
    }

    private static void ask(Engine.Active active) {
        Vec3 grave = Vec3.atCenterOf(BlockPos.of(active.scratch().getLong(BURIED)));
        Talk.ask(new Talk.Offer(active.quest().id(), null, active.step().choices(), grave,
                (player, choice) -> Engine.chosen(player, active.quest().id(), active.step().id(), null, choice)));
    }

    static void chosen(ServerPlayer player, Engine.Active active, Story.Choice choice) {
        MinecraftServer server = player.server;
        if (active.step().kind().equals("bury")) {
            inscribe(server, active, choice);
        } else if (choice.again()) {
            active.scratch().remove(STARTED);
            active.scratch().putBoolean(DECLINED, true);
            Progress.of(server).setDirty();
        } else if (!choice.takes().isEmpty()) {
            choice.takes().forEach(eye -> Halls.take(player, shard(eye)));
        } else {
            Engine.complete(server, active);
        }
    }

    static void ask(MinecraftServer server, Engine.Active active, BlockPos stone) {
        if (active.scratch().contains(STONE) && Talk.busy()) {
            return;
        }
        Story.Step step = active.step();
        active.scratch().putLong(STONE, stone.asLong());
        Progress.of(server).setDirty();
        Talk.say(active.quest().id(), Vec3.atCenterOf(stone), step.lines(ASKING), Engine.fill(Progress.of(server)));
        Talk.ask(new Talk.Offer(active.quest().id(), step.with(), step.choices(), Vec3.atCenterOf(stone),
                (player, choice) -> Engine.chosen(player, active.quest().id(), step.id(), step.with(), choice), true));
    }

    private static void inscribe(MinecraftServer server, Engine.Active active, Story.Choice choice) {
        String words = choice.stone() == null ? "" : choice.stone();
        Prologue.engrave(Engine.overworld(server), BlockPos.of(active.scratch().getLong(BURIED)), words);
        Progress progress = Progress.of(server);
        progress.stone = words;
        progress.setDirty();
        Engine.complete(server, active);
    }

    @Nullable
    static Vec3 anchor(MinecraftServer server, Story.Step step) {
        if (step.with() != null) {
            Optional<Entity> body = People.find(server, step.with());
            if (body.isPresent()) {
                return body.get().position();
            }
        }
        for (String key : new String[] {"at", "place"}) {
            if (step.spec().has(key)) {
                Optional<BlockPos> place = Progress.of(server).place(step.text(key));
                if (place.isPresent() && step.kind().equals("reach")) {
                    return arriver(server, place.get(), Places.arrival(step.text(key))).orElseGet(() -> Vec3.atCenterOf(place.get()));
                }
                if (place.isPresent()) {
                    return Vec3.atCenterOf(place.get());
                }
            }
        }
        return null;
    }

    private static Optional<Vec3> arriver(MinecraftServer server, BlockPos place, int within) {
        return Engine.players(server).stream().filter(player -> player.level().dimension() == Level.OVERWORLD)
                .filter(player -> player.blockPosition().distSqr(place) <= (double) within * within)
                .min(Comparator.comparingDouble(player -> player.blockPosition().distSqr(place))).map(Entity::position);
    }

    static void toll(MinecraftServer server, Engine.Active active) {
        if (active.step().spec().has(FATES)) {
            fates(server, active);
        }
        if (active.step().spec().has(LOSSES)) {
            losses(server, active);
        }
    }

    private static void fates(MinecraftServer server, Engine.Active active) {
        Progress progress = Progress.of(server);
        active.step().spec().getAsJsonObject(FATES).entrySet().forEach(entry -> progress.choices.computeIfAbsent(active.quest().id() + "/" + entry.getKey(),
                key -> !Caravan.with(server, entry.getKey()) ? AWAY : kept(progress, entry.getValue().getAsString()) ? LIVES : LOST));
        progress.setDirty();
    }

    static boolean alone(Progress progress) {
        return progress.choices.keySet().stream().anyMatch(key -> key.startsWith(HOLLOW))
                && progress.choices.entrySet().stream().noneMatch(fate -> fate.getKey().startsWith(HOLLOW) && fate.getValue().equals(LIVES));
    }

    private static boolean kept(Progress progress, String by) {
        return by.contains("=") ? Engine.chose(progress, by) : progress.done(by);
    }

    private static void losses(MinecraftServer server, Engine.Active active) {
        Progress progress = Progress.of(server);
        String prefix = active.quest().id() + "/";
        progress.choices.forEach((key, fate) -> {
            if (key.startsWith(prefix) && fate.equals(LOST)) {
                String person = key.substring(prefix.length());
                People.find(server, person).ifPresent(Entity::discard);
                progress.joined.remove(person);
            }
        });
        progress.setDirty();
    }

    static void moves(MinecraftServer server, Story.Step step) {
        moves(server, step.spec());
        if (step.spec().has(MOVES)) {
            CompoundTag moved = Progress.of(server).scratch(MOVED);
            step.spec().getAsJsonObject(MOVES).keySet().forEach(moved::remove);
            Progress.of(server).setDirty();
        }
    }

    static void stage(MinecraftServer server, @Nullable JsonObject stage) {
        if (stage == null) {
            return;
        }
        moves(server, stage);
        if (stage.has(MOVES)) {
            CompoundTag moved = Progress.of(server).scratch(MOVED);
            stage.getAsJsonObject(MOVES).entrySet().forEach(entry -> moved.putString(entry.getKey(), entry.getValue().toString()));
            Progress.of(server).setDirty();
        }
        poses(server, stage);
        faces(server, stage);
        if (stage.has(LEAVES)) {
            stage.getAsJsonArray(LEAVES).forEach(person -> People.leave(server, person.getAsString()));
        }
    }

    private static void moves(MinecraftServer server, JsonObject spec) {
        if (spec.has(MOVES)) {
            spec.getAsJsonObject(MOVES).entrySet().forEach(entry -> move(server, entry.getKey(), entry.getValue().getAsJsonObject()));
        }
    }

    private static void move(MinecraftServer server, String person, JsonObject where) {
        Places.seat(server, where.get("place").getAsString()).ifPresent(seat -> {
            Entity body = People.place(Engine.overworld(server), person, seat.at(), seat.yaw(), where.get("pose").getAsString());
            if (where.has("holds") && body instanceof LivingEntity living) {
                living.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(where.get("holds").getAsString()))));
            }
        });
    }

    static void keep(MinecraftServer server) {
        ServerLevel level = Engine.overworld(server);
        for (Engine.Active active : Engine.active(server)) {
            if (!entered(active) || !active.step().spec().has(MOVES)) {
                continue;
            }
            Optional<String> left = Progress.of(server).choice(active.quest().id(), active.step().id()).flatMap(chosen -> active.step().choices().stream()
                    .filter(choice -> choice.id().equals(chosen)).findFirst()).map(Story.Choice::leaves);
            CompoundTag moved = Progress.of(server).scratch(MOVED);
            active.step().spec().getAsJsonObject(MOVES).entrySet().forEach(entry -> {
                JsonObject where = moved.contains(entry.getKey()) ? JsonParser.parseString(moved.getString(entry.getKey())).getAsJsonObject()
                        : entry.getValue().getAsJsonObject();
                Places.seat(server, where.get("place").getAsString())
                        .filter(seat -> level.areEntitiesLoaded(ChunkPos.asLong(BlockPos.containing(seat.at()))) && !People.leaving(entry.getKey())
                                && !left.filter(entry.getKey()::equals).isPresent()
                                && People.find(server, entry.getKey()).filter(body -> body.level() == level && body.position().closerThan(seat.at(), KEPT)).isEmpty())
                        .ifPresent(seat -> move(server, entry.getKey(), where));
            });
        }
    }

    static void faces(MinecraftServer server, Story.Step step) {
        faces(server, step.spec());
    }

    private static void faces(MinecraftServer server, JsonObject spec) {
        if (!spec.has("faces")) {
            return;
        }
        for (JsonElement person : spec.getAsJsonArray("faces")) {
            People.find(server, person.getAsString()).ifPresent(body -> Engine.players(server).stream().filter(player -> player.level() == body.level())
                    .min(Comparator.comparingDouble(player -> player.distanceToSqr(body))).ifPresent(player -> {
                        float yaw = Frost.yaw(player.position().subtract(body.position()).multiply(1, 0, 1));
                        body.setYRot(yaw);
                        body.setYHeadRot(yaw);
                        body.setYBodyRot(yaw);
                    }));
        }
    }

    private static void poses(MinecraftServer server, JsonObject spec) {
        if (!spec.has("poses")) {
            return;
        }
        spec.getAsJsonObject("poses").entrySet()
                .forEach(entry -> People.find(server, entry.getKey()).ifPresent(body -> People.pose(body, entry.getValue().getAsString())));
    }


    private static boolean seen(MinecraftServer server, Entity body, int blocks) {
        return Engine.players(server).stream().anyMatch(player -> player.level() == body.level() && player.distanceToSqr(body) <= (double) blocks * blocks
                && Sight.sees(player, body) && Arrivals.there(player));
    }

    private static boolean touches(Entity entity, String clue) {
        return entity.getTags().contains(CLUE + clue) || entity.getTags().contains(FOUND + clue);
    }

    static boolean lookable(MinecraftServer server, Entity entity) {
        return Engine.active(server).stream().anyMatch(active -> switch (active.step().kind()) {
            case "clues" -> wanted(server, active).stream().anyMatch(clue -> entity.getTags().contains(CLUE + clue));
            case "memory" -> active.step().spec().has(TOUCH) && touches(entity, active.step().text(TOUCH));
            default -> false;
        });
    }

    static int needed(MinecraftServer server, Engine.Active active) {
        Set<String> unlaid = Progress.of(server).unlaid;
        long laid = active.step().clues().stream().filter(clue -> !unlaid.contains(unlaid(active.quest().id(), clue.id()))).count();
        return (int) Math.min(active.step().number("needed"), laid);
    }

    static String unlaid(String quest, String clue) {
        return quest + "/" + clue;
    }

    static List<String> wanted(MinecraftServer server, Engine.Active active) {
        return active.step().clues().stream().filter(clue -> !Engine.found(active.scratch()).contains(clue.id()) && counts(server, active, clue))
                .map(Story.Clue::id).toList();
    }

    private static boolean counts(MinecraftServer server, Engine.Active active, Story.Clue clue) {
        boolean lastOnly = Engine.found(active.scratch()).size() >= needed(server, active) - 1;
        if (clue.last()) {
            return lastOnly;
        }
        Set<String> unlaid = Progress.of(server).unlaid;
        return !lastOnly || active.step().clues().stream().noneMatch(other -> other.last() && !unlaid.contains(unlaid(active.quest().id(), other.id())));
    }

    private static boolean sees(ServerPlayer player, BlockPos place) {
        return player.blockPosition().closerThan(place, SEEN_NEAR) || Sight.clear(player.level(), player.getEyePosition(), Vec3.atCenterOf(place.above()));
    }

    static String direction(BlockPos from, BlockPos to) {
        double angle = Math.toDegrees(Math.atan2(to.getZ() - from.getZ(), to.getX() - from.getX()));
        return WAYS[Math.floorMod((int) Math.round(angle / 45), WAYS.length)];
    }

    static double horizontal(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }
}

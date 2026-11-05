package io.github.roman16.wayfarer.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Bundled;
import io.github.roman16.wayfarer.Clock;
import io.github.roman16.wayfarer.Firsts;
import io.github.roman16.wayfarer.GearMaterials;
import io.github.roman16.wayfarer.Heat;
import io.github.roman16.wayfarer.Wayfarer;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import top.theillusivec4.curios.api.CuriosApi;

public final class Gear {
    private static final String TASK = "gear";
    private static final String HAD = "had";
    private static final String ROW = "row:";
    private static final String SAID = "said:";
    private static final String KNOWN = "had:";
    private static final int CHECK_TICKS = 20;
    private static final Bundled<Data> DATA = Bundled.json("/wayfarer/gear.json", Gear::load);

    private Gear() {}

    private record Need(@Nullable ResourceLocation item, @Nullable ResourceLocation tag, int count, String name, String where) {
        int carried(ServerPlayer player) {
            Predicate<ItemStack> wanted = item != null ? stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(item)
                    : stack -> stack.is(TagKey.create(Registries.ITEM, tag));
            return inventory(player).filter(wanted).mapToInt(ItemStack::getCount).sum();
        }
    }

    private record Thing(String key, String name, String good, String made, @Nullable String make, @Nullable ResourceLocation jei,
            List<JsonObject> have, List<Need> needs, @Nullable String namedBy, boolean hidden, boolean apart, @Nullable String tent) {}

    private record Row(String id, String title, String who, JsonObject appears, boolean ladder, List<Thing> all) {
        List<Thing> things() {
            String kind = Prologue.tent();
            return all.stream().filter(thing -> thing.tent() == null || thing.tent().equals(kind)).toList();
        }
    }

    private record Line(String id, String by, String text, List<String> when, Optional<Item> carries, Optional<String> before, Optional<String> after) {}

    private record Data(JsonObject opens, List<Row> rows, List<Line> lines) {}

    public record Track(String thing) implements CustomPacketPayload {
        public static final Type<Track> TYPE = new Type<>(Wayfarer.id("gear_track"));
        public static final StreamCodec<ByteBuf, Track> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(Track::new, Track::thing);

        @Override
        public Type<Track> type() {
            return TYPE;
        }
    }

    public static void track(ServerPlayer player, String key) {
        Progress progress = Progress.of(player.server);
        if (key.isEmpty()) {
            progress.gearTracking.remove(player.getUUID());
        } else {
            Optional<Thing> thing = thing(key);
            if (thing.isEmpty() || thing.get().make() == null || !known(player.server, thing.get()) || had(player, thing.get())) {
                return;
            }
            progress.gearTracking.put(player.getUUID(), key);
        }
        progress.setDirty();
        Journal.sync(player);
        Tasks.refresh(player);
    }

    static void tasks(ServerPlayer player, Map<String, Component> open) {
        Progress progress = Progress.of(player.server);
        if (!opened(player.server, progress)) {
            return;
        }
        if (DATA.get().rows().stream().anyMatch(row -> appeared(progress, row)) && !Firsts.seen(player, Tasks.FIRST + TASK)) {
            open.put(TASK, Component.translatable("wayfarer.task.gear", Tasks.key(Tasks.JOURNAL_KEY)));
        }
        Optional<Thing> tracked = Optional.ofNullable(progress.gearTracking.get(player.getUUID())).flatMap(Gear::thing);
        if (tracked.isEmpty() || had(player, tracked.get())) {
            return;
        }
        Thing thing = tracked.get();
        for (int index = 0; index < thing.needs().size(); index++) {
            Need need = thing.needs().get(index);
            int carried = need.carried(player);
            if (carried < need.count()) {
                open.put(TASK + "/" + thing.key() + "/" + index,
                        need.where().isEmpty() ? Component.translatable("wayfarer.task.gear.need_bare", capitalised(need.name()), carried, need.count())
                                : Component.translatable("wayfarer.task.gear.need", capitalised(need.name()), carried, need.count(), need.where()));
            }
        }
        open.put(TASK + "/" + thing.key(), Component.literal(thing.make()));
    }

    static JsonArray view(ServerPlayer player) {
        MinecraftServer server = player.server;
        Progress progress = Progress.of(server);
        mark(server, progress);
        String tracking = progress.gearTracking.getOrDefault(player.getUUID(), "");
        JsonArray rows = new JsonArray();
        if (!opened(server, progress)) {
            return rows;
        }
        for (Row row : DATA.get().rows()) {
            if (!appeared(progress, row)) {
                continue;
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("id", row.id());
            entry.addProperty("title", row.title());
            entry.addProperty("who", People.label(server, row.who()));
            JsonArray things = new JsonArray();
            for (Thing thing : row.things()) {
                boolean known = known(server, thing);
                if (!known && HAD.equals(thing.namedBy())) {
                    continue;
                }
                JsonObject shown = new JsonObject();
                shown.addProperty("key", thing.key());
                shown.addProperty("known", known);
                shown.addProperty("apart", thing.apart());
                if (known) {
                    shown.addProperty("name", thing.name());
                    shown.addProperty("good", thing.good());
                    shown.addProperty("made", thing.made());
                    shown.addProperty("mystery", thing.made().isEmpty());
                    shown.addProperty("had", had(player, row, thing));
                    shown.addProperty("trackable", thing.make() != null);
                    shown.addProperty("tracked", thing.key().equals(tracking));
                    if (thing.jei() != null) {
                        shown.addProperty("jei", thing.jei().toString());
                    }
                    JsonArray needs = new JsonArray();
                    thing.needs().forEach(need -> {
                        JsonObject needed = new JsonObject();
                        needed.addProperty("name", need.name());
                        needed.addProperty("count", need.count());
                        needed.addProperty("where", need.where());
                        needs.add(needed);
                    });
                    shown.add("needs", needs);
                }
                things.add(shown);
            }
            entry.add("things", things);
            rows.add(entry);
        }
        return rows;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_TICKS != 0 || !Engine.running(server) || server.getPlayerList().getPlayerCount() == 0) {
            return;
        }
        Progress progress = Progress.of(server);
        boolean changed = mark(server, progress);
        long day = Math.floorDiv(Engine.overworld(server).getDayTime(), Clock.DAY);
        for (Line line : DATA.get().lines()) {
            if (!progress.gearMet.containsKey(line.id()) && line.before().filter(progress::done).isEmpty() && line.after().filter(quest -> !progress.done(quest)).isEmpty()
                    && (line.when().stream().flatMap(key -> located(key).stream()).anyMatch(found -> anyone(server, found.row(), found.thing()))
                    || line.carries().filter(item -> server.getPlayerList().getPlayers().stream().anyMatch(player -> player.getInventory().contains(new ItemStack(item)))).isPresent())) {
                progress.gearMet.put(line.id(), day);
                changed = true;
            }
        }
        changed |= speak(server, progress, day);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Optional<Thing> tracked = Optional.ofNullable(progress.gearTracking.get(player.getUUID())).flatMap(Gear::thing);
            if (tracked.isPresent() && had(player, tracked.get())) {
                progress.gearTracking.remove(player.getUUID());
                Banners.show(player, Component.translatable("wayfarer.banner.gear"), Component.literal(tracked.get().name()));
                changed = true;
            }
        }
        if (changed) {
            progress.setDirty();
            Journal.syncAll(server);
        }
    }

    private static boolean mark(MinecraftServer server, Progress progress) {
        boolean changed = false;
        for (Row row : DATA.get().rows()) {
            if (!appeared(progress, row) && appears(server, progress, row.appears())) {
                changed |= progress.gear.add(ROW + row.id());
            }
            for (Thing thing : row.things()) {
                if (HAD.equals(thing.namedBy()) && anyone(server, row, thing)) {
                    changed |= progress.gear.add(KNOWN + thing.key());
                }
            }
        }
        if (changed) {
            progress.setDirty();
        }
        return changed;
    }

    private static boolean speak(MinecraftServer server, Progress progress, long day) {
        long time = Math.floorMod(Engine.overworld(server).getDayTime(), Clock.DAY);
        int circle = Balance.get().fire().circleBlocks();
        boolean seated = Caravan.standing(server).filter(camp -> server.getPlayerList().getPlayers().stream()
                .anyMatch(player -> player.level() == Engine.overworld(server) && player.distanceToSqr(camp.getCenter()) <= (double) circle * circle)).isPresent();
        if (Talk.busy() || !seated || time < Clock.ticks(Balance.get().clock().evening())) {
            return false;
        }
        if (progress.gearSpoke == day) {
            return false;
        }
        for (Line line : DATA.get().lines()) {
            Long met = progress.gearMet.get(line.id());
            if (met != null && met < day && !progress.gear.contains(SAID + line.id()) && People.find(server, line.by()).isPresent()) {
                Talk.say(Talk.ROAD, null, new Story.Line(line.by(), line.text()));
                said(progress, line.id());
                progress.gearSpoke = day;
                return true;
            }
        }
        return false;
    }

    static boolean show(Progress progress, String row) {
        if (DATA.get().rows().stream().noneMatch(found -> found.id().equals(row))) {
            return false;
        }
        progress.gear.add(ROW + row);
        progress.setDirty();
        return true;
    }

    static boolean said(Progress progress, String line) {
        if (DATA.get().lines().stream().noneMatch(found -> found.id().equals(line))) {
            return false;
        }
        progress.gear.add(SAID + line);
        progress.setDirty();
        return true;
    }

    private static boolean appears(MinecraftServer server, Progress progress, JsonObject appears) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        boolean claimed = appears.has("claimed") && !progress.claims.getOrDefault(appears.get("claimed").getAsString(), Set.of()).isEmpty();
        boolean opened = appears.has("opened") && progress.step(appears.get("opened").getAsString()) >= 0;
        boolean felt = appears.has("feels") && ModList.get().isLoaded("toughasnails")
                && players.stream().anyMatch(player -> Heat.feels(player).filter(appears.get("feels").getAsString()::equals).isPresent());
        boolean had = appears.has("has") && appears.getAsJsonArray("has").asList().stream().flatMap(key -> located(key.getAsString()).stream())
                .anyMatch(found -> anyone(server, found.row(), found.thing()));
        return claimed || opened || felt || had;
    }

    private static boolean opened(MinecraftServer server, Progress progress) {
        return appears(server, progress, DATA.get().opens());
    }

    private static boolean appeared(Progress progress, Row row) {
        return progress.gear.contains(ROW + row.id());
    }

    private static boolean known(MinecraftServer server, Thing thing) {
        if (thing.hidden()) {
            return false;
        }
        if (thing.namedBy() == null) {
            return true;
        }
        Progress progress = Progress.of(server);
        return progress.gear.contains(HAD.equals(thing.namedBy()) ? KNOWN + thing.key() : SAID + thing.namedBy());
    }

    private record Located(Row row, Thing thing) {}

    private static Optional<Located> located(String key) {
        for (Row row : DATA.get().rows()) {
            for (Thing thing : row.things()) {
                if (thing.key().equals(key)) {
                    return Optional.of(new Located(row, thing));
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Thing> thing(String key) {
        return located(key).map(Located::thing);
    }

    private static boolean anyone(MinecraftServer server, Row row, Thing thing) {
        return server.getPlayerList().getPlayers().stream().anyMatch(player -> had(player, row, thing));
    }

    private static boolean had(ServerPlayer player, Thing thing) {
        return located(thing.key()).map(found -> had(player, found.row(), thing)).orElse(false);
    }

    private static boolean had(ServerPlayer player, Row row, Thing thing) {
        if (!row.ladder() || thing.apart()) {
            return holds(player, thing);
        }
        List<Thing> rungs = row.things().stream().filter(rung -> !rung.apart()).toList();
        return rungs.subList(rungs.indexOf(thing), rungs.size()).stream().anyMatch(rung -> holds(player, rung));
    }

    private static boolean holds(ServerPlayer player, Thing thing) {
        return thing.have().stream().anyMatch(wanted -> matches(player, wanted));
    }

    private static boolean matches(ServerPlayer player, JsonObject wanted) {
        if (wanted.has("ridden")) {
            return player.getStats().getValue(Stats.CUSTOM.get(Stats.HORSE_ONE_CM)) > 0;
        }
        if (wanted.has("gift")) {
            return Progress.of(player.server).gift(wanted.get("gift").getAsString());
        }
        if (wanted.has("gear")) {
            if (!ModList.get().isLoaded("silentgear")) {
                return false;
            }
            ResourceLocation material = ResourceLocation.parse(wanted.get("material").getAsString());
            String kind = wanted.get("gear").getAsString();
            return inventory(player).anyMatch(stack -> GearMaterials.main(stack, kind).filter(material::equals).isPresent());
        }
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(wanted.get("item").getAsString()));
        return inventory(player).anyMatch(stack -> stack.is(item));
    }

    private static Stream<ItemStack> inventory(ServerPlayer player) {
        Stream<ItemStack> carried = Stream.of(player.getInventory().items, player.getInventory().armor, player.getInventory().offhand).flatMap(List::stream);
        Stream<ItemStack> worn = CuriosApi.getCuriosInventory(player).map(curios -> curios.getEquippedCurios()).stream()
                .flatMap(curios -> IntStream.range(0, curios.getSlots()).mapToObj(curios::getStackInSlot));
        return Stream.concat(carried, worn);
    }

    private static Data load(JsonObject json) {
        List<Row> rows = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("rows")) {
            JsonObject row = element.getAsJsonObject();
            String id = row.get("id").getAsString();
            List<Thing> things = new ArrayList<>();
            for (JsonElement thingElement : row.getAsJsonArray("things")) {
                things.add(thing(id, thingElement.getAsJsonObject()));
            }
            rows.add(new Row(id, row.get("title").getAsString(), row.get("who").getAsString(), row.getAsJsonObject("appears"),
                    row.has("ladder") && row.get("ladder").getAsBoolean(), List.copyOf(things)));
        }
        List<Line> lines = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("lines")) {
            JsonObject line = element.getAsJsonObject();
            lines.add(new Line(line.get("id").getAsString(), line.get("by").getAsString(), line.get("text").getAsString(),
                    line.has("when") ? line.getAsJsonArray("when").asList().stream().map(JsonElement::getAsString).toList() : List.of(),
                    line.has("carries") ? Optional.of(BuiltInRegistries.ITEM.get(ResourceLocation.parse(line.get("carries").getAsString()))) : Optional.empty(),
                    line.has("before") ? Optional.of(line.get("before").getAsString()) : Optional.empty(),
                    line.has("after") ? Optional.of(line.get("after").getAsString()) : Optional.empty()));
        }
        return new Data(json.getAsJsonObject("opens"), List.copyOf(rows), List.copyOf(lines));
    }

    private static Thing thing(String row, JsonObject thing) {
        List<Need> needs = new ArrayList<>();
        if (thing.has("needs")) {
            for (JsonElement element : thing.getAsJsonArray("needs")) {
                JsonObject need = element.getAsJsonObject();
                needs.add(new Need(need.has("item") ? ResourceLocation.parse(need.get("item").getAsString()) : null,
                        need.has("tag") ? ResourceLocation.parse(need.get("tag").getAsString()) : null, need.get("count").getAsInt(),
                        need.get("name").getAsString(), need.has("where") ? need.get("where").getAsString() : ""));
            }
        }
        List<JsonObject> have = thing.has("have") ? thing.getAsJsonArray("have").asList().stream().map(JsonElement::getAsJsonObject).toList() : List.of();
        return new Thing(row + "/" + thing.get("id").getAsString(), text(thing, "name"), text(thing, "good"), text(thing, "made"),
                thing.has("make") ? thing.get("make").getAsString() : null, thing.has("jei") ? ResourceLocation.parse(thing.get("jei").getAsString()) : null,
                have, List.copyOf(needs), thing.has("named_by") ? thing.get("named_by").getAsString() : null,
                thing.has("hidden") && thing.get("hidden").getAsBoolean(), thing.has("apart") && thing.get("apart").getAsBoolean(),
                thing.has("tent") ? thing.get("tent").getAsString() : null);
    }

    private static String capitalised(String text) {
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }

    private static String text(JsonObject thing, String key) {
        return thing.has(key) ? thing.get(key).getAsString() : "";
    }
}

package io.github.roman16.wayfarer.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.github.roman16.wayfarer.Library;
import io.github.roman16.wayfarer.Shine;
import io.github.roman16.wayfarer.Stones;
import io.github.roman16.wayfarer.Wayfarer;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class Journal {
    private static final List<String> PLACES = List.of(Caravan.NAME, Prologue.LAST_CAMP, Tollbridge.NAME);

    private static final int COUNT_BLOCKS = 16;

    private Journal() {}

    public record View(String json) implements CustomPacketPayload {
        private static final int LONGEST = 1 << 18;
        public static final Type<View> TYPE = new Type<>(Wayfarer.id("journal"));
        public static final StreamCodec<ByteBuf, View> STREAM_CODEC = ByteBufCodecs.stringUtf8(LONGEST).map(View::new, View::json);

        @Override
        public Type<View> type() {
            return TYPE;
        }
    }

    public record Track(String quest) implements CustomPacketPayload {
        public static final Type<Track> TYPE = new Type<>(Wayfarer.id("track"));
        public static final StreamCodec<ByteBuf, Track> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(Track::new, Track::quest);

        @Override
        public Type<Track> type() {
            return TYPE;
        }
    }

    public record Seen(String entry) implements CustomPacketPayload {
        public static final Type<Seen> TYPE = new Type<>(Wayfarer.id("seen"));
        public static final StreamCodec<ByteBuf, Seen> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(Seen::new, Seen::entry);

        @Override
        public Type<Seen> type() {
            return TYPE;
        }
    }

    public record Claim(String reward) implements CustomPacketPayload {
        public static final Type<Claim> TYPE = new Type<>(Wayfarer.id("claim"));
        public static final StreamCodec<ByteBuf, Claim> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(Claim::new, Claim::reward);

        @Override
        public Type<Claim> type() {
            return TYPE;
        }
    }

    public record Dream(String memory) implements CustomPacketPayload {
        public static final Type<Dream> TYPE = new Type<>(Wayfarer.id("dream"));
        public static final StreamCodec<ByteBuf, Dream> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(Dream::new, Dream::memory);

        @Override
        public Type<Dream> type() {
            return TYPE;
        }
    }

    public static void syncAll(MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(Journal::sync);
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new View(view(player).toString()));
    }

    static JsonObject view(ServerPlayer player) {
        MinecraftServer server = player.server;
        Progress progress = Progress.of(server);
        JsonObject view = new JsonObject();
        JsonArray quests = new JsonArray();
        for (Story.Quest quest : Story.get().quests()) {
            if (!progress.steps.containsKey(quest.id()) || Engine.waiting(progress, quest)) {
                continue;
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("id", quest.id());
            entry.addProperty("title", Engine.title(progress, quest));
            entry.addProperty("kind", quest.kind());
            entry.addProperty("prologue", quest.act().equals(Engine.PROLOGUE));
            entry.addProperty("summary", quest.summary());
            boolean done = progress.done(quest.id());
            entry.addProperty("done", done);
            JsonArray told = new JsonArray();
            progress.told.getOrDefault(quest.id(), List.of()).forEach(told::add);
            entry.add("story", told);
            Memories.dreamt(progress, quest).ifPresent(memory -> entry.addProperty("memory", memory));
            if (!done) {
                Story.Step step = quest.steps().get(progress.step(quest.id()));
                entry.addProperty("objective", objective(player, new Engine.Active(quest, step, progress.scratch(quest.id()))));
                JsonArray found = new JsonArray();
                step.clues().stream().filter(clue -> Engine.found(progress.scratch(quest.id())).contains(clue.id())).forEach(clue -> found.add(clue.text()));
                entry.add("found", found);
            }
            quests.add(entry);
        }
        view.add("quests", quests);
        JsonArray rewards = new JsonArray();
        for (Story.Reward reward : Engine.rewards()) {
            if (progress.rewards.contains(reward.key())) {
                JsonObject entry = new JsonObject();
                entry.addProperty("key", reward.key());
                entry.addProperty("title", reward.title());
                entry.addProperty("text", reward.text());
                entry.addProperty("claimed", progress.claimed(reward.key(), player.getUUID()));
                JsonArray items = new JsonArray();
                reward.items().forEach(item -> items.add(ItemStack.CODEC.encodeStart(player.registryAccess().createSerializationContext(JsonOps.INSTANCE),
                        Engine.stack(player, reward.key(), item)).getOrThrow()));
                entry.add("items", items);
                rewards.add(entry);
            }
        }
        view.add("rewards", rewards);
        JsonArray chapters = new JsonArray();
        progress.told.forEach((quest, lines) -> {
            JsonObject chapter = new JsonObject();
            chapter.addProperty("key", quest);
            chapter.addProperty("title", Story.get().quest(quest).map(found -> Engine.title(progress, found)).orElse(quest));
            JsonArray text = new JsonArray();
            lines.forEach(text::add);
            chapter.add("lines", text);
            chapters.add(chapter);
        });
        view.add("story", chapters);
        JsonArray conversations = new JsonArray();
        progress.conversations.forEach((key, heard) -> {
            JsonObject talk = new JsonObject();
            talk.addProperty("key", key);
            talk.addProperty("title", Story.get().quest(key).map(found -> Engine.title(progress, found)).orElse(""));
            JsonArray lines = new JsonArray();
            heard.forEach(line -> {
                JsonObject said = new JsonObject();
                said.addProperty("by", line.by());
                said.addProperty("text", line.text());
                lines.add(said);
            });
            talk.add("lines", lines);
            conversations.add(talk);
        });
        view.add("conversations", conversations);
        JsonArray pages = new JsonArray();
        progress.pages.forEach(id -> Library.get().book(id).ifPresent(book -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("key", id);
            entry.addProperty("title", book.title());
            entry.addProperty("author", book.author());
            JsonArray text = new JsonArray();
            book.pages().forEach(text::add);
            entry.add("pages", text);
            pages.add(entry);
        }));
        view.add("pages", pages);
        view.add("gear", Gear.view(player));
        view.add("people", entries(progress.people, person -> People.label(player.server, person)));
        view.add("words", entries(progress.words, word -> word));
        JsonArray places = new JsonArray();
        List<BlockPos> named = new ArrayList<>();
        Stream.concat(PLACES.stream(), progress.visited.stream().filter(name -> !PLACES.contains(name))).forEach(name -> progress.place(name).ifPresent(pos -> {
            JsonObject place = named(Places.label(server, name), pos);
            if (name.equals(Caravan.NAME)) {
                place.addProperty("camp", true);
            } else {
                named.add(pos);
            }
            places.add(place);
        }));
        List<Caravan.Camped> camps = Caravan.camps(server);
        double near = (double) Places.VISIT_BLOCKS * Places.VISIT_BLOCKS;
        camps.subList(0, Math.max(0, camps.size() - 1)).stream()
                .filter(camped -> named.stream().noneMatch(pos -> Steps.horizontal(pos, camped.centre()) <= near))
                .forEach(camped -> {
                    JsonObject camp = place("place.camp", camped.centre());
                    camp.addProperty("camp", true);
                    camp.addProperty("day", camped.day() + 1);
                    places.add(camp);
                });
        for (int mark = 0; mark < Marks.count(); mark++) {
            progress.place(Marks.MARK + mark).ifPresent(pos -> places.add(place("place.mark", pos)));
        }
        view.add("places", places);
        view.addProperty("headIn", Caravan.canHeadIn(server));
        Engine.tracked(player).ifPresent(active -> view.addProperty("tracked", active.quest().id()));
        JsonArray seen = new JsonArray();
        progress.seen.getOrDefault(player.getUUID(), Set.of()).forEach(seen::add);
        view.add("seen", seen);
        JsonArray stones = new JsonArray();
        for (Stones.Stone stone : Stones.mine(player)) {
            JsonObject entry = place("stone", stone.pos());
            entry.addProperty("things", stone.things());
            stones.add(entry);
        }
        view.add("stones", stones);
        view.add("afterwards", Afterwards.view(player.server));
        view.add("deeds", Deeds.view(player));
        JsonArray gifts = new JsonArray();
        progress.gifts.forEach(gifts::add);
        view.add("gifts", gifts);
        view.addProperty("eyes", Shine.glow(server));
        return view;
    }

    static String objective(ServerPlayer player, Engine.Active active) {
        Story.Step step = active.step();
        String objective = step.objective().replace("{found}", String.valueOf(Engine.found(active.scratch()).size()))
                .replace("{needed}", step.spec().has("needed") ? String.valueOf(Steps.needed(player.server, active)) : "");
        if (step.kind().equals("wins")) {
            objective = objective.replace("{won}", String.valueOf(Math.min(Steps.won(player.server, active), step.number("count")))).replace("{count}", step.text("count"));
        }
        if (step.kind().equals("read")) {
            objective = objective.replace("{read}", String.valueOf(Steps.read(player.server, step)))
                    .replace("{pages}", String.valueOf(step.spec().getAsJsonArray("pages").size()));
        }
        if (step.kind().equals("gather") || step.kind().equals("give")) {
            objective = objective.replace("{carried}", String.valueOf(Math.min(Steps.carried(player, step), step.number("count"))))
                    .replace("{count}", step.text("count"));
        }
        if (step.kind().equals("walk")) {
            BlockPos from = Engine.at(player.server, step.text("from"));
            double walked = Math.sqrt(Math.pow(player.getX() - from.getX(), 2) + Math.pow(player.getZ() - from.getZ(), 2));
            int left = Math.max(0, Places.leaving(step.text("from")) - (int) walked);
            objective = objective.replace("{left}", String.valueOf(Math.ceilDiv(left, COUNT_BLOCKS) * COUNT_BLOCKS));
        }
        return objective;
    }

    private static JsonArray entries(Map<String, List<String>> lines, java.util.function.Function<String, String> title) {
        JsonArray entries = new JsonArray();
        lines.forEach((key, said) -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("key", key);
            entry.addProperty("title", title.apply(key));
            JsonArray text = new JsonArray();
            said.forEach(text::add);
            entry.add("lines", text);
            entries.add(entry);
        });
        return entries;
    }

    private static JsonObject place(String key, BlockPos pos) {
        return named("wayfarer." + key, pos);
    }

    private static JsonObject named(String name, BlockPos pos) {
        JsonObject place = new JsonObject();
        place.addProperty("name", name);
        place.addProperty("x", pos.getX());
        place.addProperty("z", pos.getZ());
        return place;
    }
}

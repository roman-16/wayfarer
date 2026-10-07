package io.github.roman16.wayfarer.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Stones;
import io.github.roman16.wayfarer.Wayfarer;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class Journal {
    private static final Map<String, String> PLACES = Map.of(Prologue.LAST_CAMP, "place.last_camp", Tollbridge.NAME, "place.tollbridge",
            Caravan.NAME, "place.caravan");

    private static final int COUNT_BLOCKS = 16;

    private Journal() {}

    public record View(String json) implements CustomPacketPayload {
        public static final Type<View> TYPE = new Type<>(Wayfarer.id("journal"));
        public static final StreamCodec<ByteBuf, View> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(View::new, View::json);

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
        for (Story.Quest quest : Story.INSTANCE.quests()) {
            if (!progress.steps.containsKey(quest.id())) {
                continue;
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("id", quest.id());
            entry.addProperty("title", quest.title());
            entry.addProperty("kind", quest.kind());
            entry.addProperty("summary", quest.summary());
            boolean done = progress.done(quest.id());
            entry.addProperty("done", done);
            JsonArray told = new JsonArray();
            progress.told.getOrDefault(quest.id(), List.of()).forEach(told::add);
            entry.add("story", told);
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
            chapter.addProperty("title", Story.INSTANCE.quest(quest).map(Story.Quest::title).orElse(quest));
            JsonArray text = new JsonArray();
            lines.forEach(text::add);
            chapter.add("lines", text);
            chapters.add(chapter);
        });
        view.add("story", chapters);
        view.add("people", entries(progress.people, person -> Story.INSTANCE.person(person).name()));
        view.add("words", entries(progress.words, word -> word));
        JsonArray places = new JsonArray();
        PLACES.forEach((name, key) -> progress.place(name).ifPresent(pos -> places.add(place(key, pos))));
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
        return view;
    }

    static String objective(ServerPlayer player, Engine.Active active) {
        Story.Step step = active.step();
        String objective = step.objective().replace("{found}", String.valueOf(Engine.found(active.scratch()).size()))
                .replace("{needed}", step.spec().has("needed") ? step.text("needed") : "");
        if (step.kind().equals("walk")) {
            BlockPos from = Engine.at(player.server, step.text("from"));
            double walked = Math.sqrt(Math.pow(player.getX() - from.getX(), 2) + Math.pow(player.getZ() - from.getZ(), 2));
            int left = Math.max(0, Balance.INSTANCE.prologue().awayBlocks() - (int) walked);
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
        JsonObject place = new JsonObject();
        place.addProperty("name", "wayfarer." + key);
        place.addProperty("x", pos.getX());
        place.addProperty("z", pos.getZ());
        return place;
    }
}

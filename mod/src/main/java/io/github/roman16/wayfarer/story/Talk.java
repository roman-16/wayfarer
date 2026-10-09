package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Music;
import io.github.roman16.wayfarer.Sounds;
import io.github.roman16.wayfarer.Wayfarer;
import io.netty.buffer.ByteBuf;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class Talk {
    public static final String ROAD = "road";
    private static final String ANSWER_CHOSEN = "answer_chosen";
    private static final int TICKS_PER_SECOND = 20;
    private static final Deque<Speech> queue = new ArrayDeque<>();
    private static final Set<UUID> answering = new HashSet<>();
    private static final Map<UUID, Integer> votes = new HashMap<>();
    private static long clock;
    private static long next;
    @Nullable
    private static String speaking;
    @Nullable
    private static Offer offer;

    private Talk() {}

    private sealed interface Speech permits Said, Notice, Pause, Then, Ask {}

    private record Said(@Nullable String fire, @Nullable Vec3 at, Story.Line line, String text) implements Speech {}

    private record Notice(Component line) implements Speech {}

    private record Pause() implements Speech {}

    private record Then(Runnable run) implements Speech {}

    private record Ask(Offer offer) implements Speech {}

    public record Offer(@Nullable String fire, @Nullable String speaker, List<Story.Choice> choices, @Nullable Vec3 at, Answer answer, boolean vote) {
        public Offer(@Nullable String fire, @Nullable String speaker, List<Story.Choice> choices, @Nullable Vec3 at, Answer answer) {
            this(fire, speaker, choices, at, answer, false);
        }
    }

    public interface Answer {
        void chosen(ServerPlayer player, Story.Choice choice);
    }

    public record Subtitle(String by, Component text, int ticks) implements CustomPacketPayload {
        public static final Type<Subtitle> TYPE = new Type<>(Wayfarer.id("subtitle"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Subtitle> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Subtitle::by,
                ComponentSerialization.TRUSTED_STREAM_CODEC, Subtitle::text,
                ByteBufCodecs.VAR_INT, Subtitle::ticks,
                Subtitle::new);

        @Override
        public Type<Subtitle> type() {
            return TYPE;
        }
    }

    public record Answers(List<String> choices) implements CustomPacketPayload {
        public static final Type<Answers> TYPE = new Type<>(Wayfarer.id("answers"));
        public static final StreamCodec<ByteBuf, Answers> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).map(Answers::new, Answers::choices);

        @Override
        public Type<Answers> type() {
            return TYPE;
        }
    }

    public record Pick(int index) implements CustomPacketPayload {
        public static final Type<Pick> TYPE = new Type<>(Wayfarer.id("pick"));
        public static final StreamCodec<ByteBuf, Pick> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(Pick::new, Pick::index);

        @Override
        public Type<Pick> type() {
            return TYPE;
        }
    }

    public static void say(@Nullable String fire, @Nullable Vec3 at, List<Story.Line> lines, Placeholders placeholders) {
        for (Story.Line line : lines) {
            if (line.pause()) {
                queue.add(new Pause());
            }
            queue.add(new Said(fire, at, line, placeholders.fill(line.text())));
        }
    }

    public static void say(@Nullable String fire, @Nullable Vec3 at, Story.Line line) {
        say(fire, at, List.of(line), Placeholders.NONE);
    }

    public static void notice(Component line) {
        queue.add(new Notice(line));
    }

    public static void then(Runnable run) {
        queue.add(new Then(run));
    }

    public static void ask(Offer offer) {
        queue.add(new Ask(offer));
    }

    public static void tell(ServerPlayer listener, Component text) {
        tell(List.of(listener), "", text);
    }

    public static void tell(List<ServerPlayer> listeners, String by, Component text) {
        Subtitle subtitle = new Subtitle(by, text, reading(text.getString()));
        listeners.forEach(listener -> PacketDistributor.sendToPlayer(listener, subtitle));
    }

    public static Optional<String> speaker() {
        return clock < next ? Optional.ofNullable(speaking) : Optional.empty();
    }

    public static boolean busy() {
        return !queue.isEmpty() || offer != null;
    }

    public static boolean answering(ServerPlayer player) {
        return offer != null && near(player, offer.at());
    }

    public static Optional<Offer> open() {
        return Optional.ofNullable(offer);
    }

    public static void reset() {
        queue.clear();
        offer = null;
        votes.clear();
        next = clock;
    }

    private static void heard(MinecraftServer server, Said said) {
        Story.Line line = said.line();
        if (said.fire() != null) {
            Progress.of(server).hear(said.fire(), line.by() == null ? "" : People.label(server, line.by()), said.text());
            Journal.syncAll(server);
        }
        People.introduce(server, line.names());
        if (line.sound() != null) {
            Sounds.play(server, line.sound());
        }
        if (line.music() != null) {
            Music.play(server, line.music(), false);
        }
        if (line.vanishes() != null) {
            Visit.vanish(server, line.vanishes(), line.left());
        }
    }

    private static int reading(String text) {
        Balance.Talk talk = Balance.get().talk();
        return Math.max(talk.leastSeconds(), Math.ceilDiv(text.length(), talk.charactersPerSecond())) * TICKS_PER_SECOND;
    }

    public static MutableComponent line(MinecraftServer server, @Nullable String by, Component text) {
        if (by == null) {
            return text.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        }
        return Component.literal("<" + People.label(server, by) + "> ").withStyle(Story.get().person(by).color())
                .append(text.copy().withStyle(ChatFormatting.WHITE));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getPlayerList().getPlayerCount() == 0 || !server.tickRateManager().runsNormally()) {
            return;
        }
        clock++;
        while (offer == null && !queue.isEmpty() && clock >= next && listened(server, queue.peek())) {
            Speech speech = queue.poll();
            switch (speech) {
                case Said said -> {
                    Optional<Vec3> at = where(server, said);
                    String by = said.line().by() == null ? "" : People.label(server, said.line().by());
                    Component text = Component.literal(said.text());
                    deliver(server, new Subtitle(by, text, reading(said.text())), at.orElse(null), at.isPresent() ? line(server, said.line().by(), text) : null);
                    heard(server, said);
                    speaking = said.line().by();
                    next = clock + reading(said.text());
                }
                case Notice notice -> {
                    speaking = null;
                    deliver(server, new Subtitle("", notice.line(), reading(notice.line().getString())), null, null);
                    next = clock + reading(notice.line().getString());
                }
                case Pause pause -> next = clock + (long) Balance.get().talk().pauseSeconds() * TICKS_PER_SECOND;
                case Then then -> then.run().run();
                case Ask ask -> offer = ask.offer();
            }
        }
        offer(server);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        reset();
        answering.clear();
    }

    public static void pick(ServerPlayer player, int index) {
        if (offer == null || index < 0 || index >= offer.choices().size() || !near(player, offer.at())) {
            return;
        }
        Offer answered = offer;
        Story.Choice picked = answered.choices().get(index);
        if (answered.vote()) {
            votes.put(player.getUUID(), index);
            long agreed = votes.values().stream().filter(vote -> vote == index).count();
            if (!Engine.most(player.server, agreed)) {
                Component chose = Component.translatable("wayfarer.talk.chose", player.getDisplayName(), picked.text());
                deliver(player.server, new Subtitle("", chose, reading(chose.getString())), answered.at(), null);
                answering.clear();
                offer(player.server);
                return;
            }
            votes.clear();
        }
        offer = null;
        offer(player.server);
        if (answered.fire() != null) {
            Progress.of(player.server).hear(answered.fire(), player.getDisplayName().getString(), picked.text());
        }
        next = clock + reading(picked.text());
        Tasks.did(player, "answer");
        Sounds.play(player.server, ANSWER_CHOSEN);
        Component chose = Component.translatable("wayfarer.talk.chose", player.getDisplayName(), picked.text());
        deliver(player.server, new Subtitle("", chose, reading(chose.getString())), answered.at(),
                answered.at() == null ? null : chose.copy().withStyle(ChatFormatting.DARK_GRAY));
        answered.answer().chosen(player, picked);
    }

    private static void offer(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean shown = offer != null && near(player, offer.at());
            if (shown && answering.add(player.getUUID())) {
                PacketDistributor.sendToPlayer(player, new Answers(answers(server, offer)));
            } else if (!shown && answering.remove(player.getUUID())) {
                PacketDistributor.sendToPlayer(player, new Answers(List.of()));
            }
        }
    }

    private static List<String> answers(MinecraftServer server, Offer shown) {
        List<String> texts = shown.choices().stream().map(Story.Choice::text).toList();
        int online = Engine.players(server).size();
        if (!shown.vote() || online < 2) {
            return texts;
        }
        return IntStream.range(0, texts.size())
                .mapToObj(index -> texts.get(index) + " " + votes.values().stream().filter(vote -> vote == index).count() + "/" + online).toList();
    }

    private static boolean listened(MinecraftServer server, Speech speech) {
        if (!(speech instanceof Said said)) {
            return true;
        }
        Optional<Vec3> at = where(server, said);
        return at.isEmpty() || server.getPlayerList().getPlayers().stream().anyMatch(player -> near(player, at.get()));
    }

    private static Optional<Vec3> where(MinecraftServer server, Said said) {
        String by = said.line().by();
        if (by == null || Story.get().person(by).voice()) {
            return Optional.empty();
        }
        Optional<Entity> body = People.find(server, by);
        if (body.isPresent()) {
            return body.map(Entity::position);
        }
        return Optional.ofNullable(said.at());
    }

    private static void deliver(MinecraftServer server, Subtitle subtitle, @Nullable Vec3 at, @Nullable Component far) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (near(player, at)) {
                PacketDistributor.sendToPlayer(player, subtitle);
            } else if (far != null) {
                player.sendSystemMessage(far);
            }
        }
    }

    private static boolean near(ServerPlayer player, @Nullable Vec3 at) {
        int reach = Balance.get().talk().reachBlocks();
        return at == null || (player.level().dimension() == Level.OVERWORLD || Memories.dreaming(player)) && at.distanceToSqr(player.position()) <= (double) reach * reach;
    }

    public interface Placeholders {
        Placeholders NONE = text -> text;

        String fill(String text);
    }
}

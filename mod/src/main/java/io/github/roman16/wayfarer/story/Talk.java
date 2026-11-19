package io.github.roman16.wayfarer.story;

import com.google.gson.JsonArray;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Clock;
import io.github.roman16.wayfarer.Firsts;
import io.github.roman16.wayfarer.Music;
import io.github.roman16.wayfarer.Narration;
import io.github.roman16.wayfarer.Record;
import io.github.roman16.wayfarer.Sounds;
import io.github.roman16.wayfarer.Tents;
import io.github.roman16.wayfarer.Wayfarer;
import io.netty.buffer.ByteBuf;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public final class Talk {
    public static final String ROAD = "road";
    private static final String VOWELS = "aeiou";
    private static final String NOTICES = "notices";
    private static final String ANSWER_CHOSEN = "answer_chosen";
    private static final int TICKS_PER_SECOND = 20;
    private static final int OUTSIDE_TICKS = 20;
    private static final Map<String, Conversation> conversations = new LinkedHashMap<>();
    private static final Set<Mob> outlined = new HashSet<>();
    private static final Map<UUID, String> answering = new HashMap<>();
    private static final Map<UUID, Hearing> hearing = new HashMap<>();
    private static final Map<String, Long> AGAIN = new HashMap<>();
    private static final Map<String, Long> HEARD = new HashMap<>();
    private static final Map<UUID, Personal> personal = new HashMap<>();
    private static final Map<UUID, Vec3> outside = new HashMap<>();
    private static final Set<UUID> screens = new HashSet<>();
    private static final Set<String> unsaid = new HashSet<>();
    private static final List<Pending> pending = new ArrayList<>();
    private static final Set<String> FIGHTS = Set.of("chase", "duel", "hold", "memory", "sea", "slay", "worm");
    private static final long NEVER = -Integer.MAX_VALUE;
    private static final int NO_ENTITY = -1;
    private static long clock;
    private static long spokenUntil;
    @Nullable
    private static String speaking;
    private static String speakingIn = "";

    private Talk() {}

    private sealed interface Speech permits Said, Replay, Notice, Pause, Then, Ask {}

    private record Said(@Nullable String fire, @Nullable Vec3 at, Story.Line line, String text, boolean inSight) implements Speech {}

    private record Replay(Said said) implements Speech {}

    private record Notice(Component line) implements Speech {}

    private record Pause() implements Speech {}

    private record Then(Runnable run) implements Speech {}

    private record Ask(Offer offer) implements Speech {}

    private record Personal(List<Story.Choice> choices, Answer answer) {}

    private record Hearing(String key, long until) {}

    private record Pending(@Nullable String key, Runnable run) {}

    private static final class Conversation {
        private final String key;
        private final boolean road;
        private final Deque<Speech> queue = new ArrayDeque<>();
        private final List<Said> told = new ArrayList<>();
        private final Map<UUID, Integer> votes = new HashMap<>();
        @Nullable
        private Offer offer;
        private long offered;
        private long next;
        private long waiting;
        private long unheardSince = NEVER;
        private long movedAt;
        private long huntedAt = NEVER;
        private long heldSince = NEVER;
        private boolean warned;
        private boolean idle = true;

        private Conversation(String key, boolean road) {
            this.key = key;
            this.road = road;
            this.movedAt = clock;
        }

        private boolean busy() {
            return !queue.isEmpty() || offer != null;
        }
    }

    public record Offer(@Nullable String fire, @Nullable String speaker, List<Story.Choice> choices, @Nullable Vec3 at, Answer answer, boolean vote,
            @Nullable Runnable lapsed) {
        public Offer(@Nullable String fire, @Nullable String speaker, List<Story.Choice> choices, @Nullable Vec3 at, Answer answer) {
            this(fire, speaker, choices, at, answer, false, null);
        }

        public Offer(@Nullable String fire, @Nullable String speaker, List<Story.Choice> choices, @Nullable Vec3 at, Answer answer, boolean vote) {
            this(fire, speaker, choices, at, answer, vote, null);
        }
    }

    public interface Answer {
        void chosen(ServerPlayer player, Story.Choice choice);
    }

    public record Source(int entity, double x, double y, double z, boolean head) {
        public static final Source HEAD = new Source(NO_ENTITY, 0, 0, 0, true);
        static final StreamCodec<ByteBuf, Source> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Source::entity,
                ByteBufCodecs.DOUBLE, Source::x,
                ByteBufCodecs.DOUBLE, Source::y,
                ByteBufCodecs.DOUBLE, Source::z,
                ByteBufCodecs.BOOL, Source::head,
                Source::new);

        static Source of(Entity body) {
            return new Source(body.getId(), body.getX(), body.getEyeY(), body.getZ(), false);
        }

        static Source at(Vec3 at) {
            return new Source(NO_ENTITY, at.x, at.y, at.z, false);
        }
    }

    public record Subtitle(String by, String tone, Component text, int ticks, String clip, Source source) implements CustomPacketPayload {
        public static final Type<Subtitle> TYPE = new Type<>(Wayfarer.id("subtitle"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Subtitle> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Subtitle::by,
                ByteBufCodecs.STRING_UTF8, Subtitle::tone,
                ComponentSerialization.TRUSTED_STREAM_CODEC, Subtitle::text,
                ByteBufCodecs.VAR_INT, Subtitle::ticks,
                ByteBufCodecs.STRING_UTF8, Subtitle::clip,
                Source.STREAM_CODEC, Subtitle::source,
                Subtitle::new);

        @Override
        public Type<Subtitle> type() {
            return TYPE;
        }

        static Subtitle of(MinecraftServer server, @Nullable String person, Component text) {
            return of(server, person, text, null);
        }

        static Subtitle of(MinecraftServer server, @Nullable String person, Component text, @Nullable Vec3 at) {
            return of(server, person, person, text, at);
        }

        static Subtitle of(MinecraftServer server, @Nullable String person, @Nullable String voice, Component text, @Nullable Vec3 at) {
            String said = text.getString();
            Optional<Narration.Clip> clip = Narration.said(voice, said, voice != null && voice.equals(person) && People.renamed(server, person));
            int ticks = clip.map(Narration.Clip::ticks).orElseGet(() -> reading(said));
            String path = clip.map(Narration.Clip::path).orElse("");
            if (person == null) {
                return new Subtitle("", "", text, ticks, path, Source.HEAD);
            }
            return new Subtitle(People.label(server, person), Story.get().person(person).color().getName(), text, ticks, path, source(server, person, at));
        }

        static Subtitle answered(ServerPlayer player, Component chose, String answer) {
            Optional<Narration.Clip> clip = Narration.chosen(Narration.voice(player), answer);
            return new Subtitle("", "", chose, clip.map(Narration.Clip::ticks).orElseGet(() -> reading(answer)), clip.map(Narration.Clip::path).orElse(""),
                    Source.of(player));
        }

        private static Source source(MinecraftServer server, String person, @Nullable Vec3 at) {
            if (Story.get().person(person).voice()) {
                return Source.HEAD;
            }
            return People.find(server, person).map(Source::of).orElseGet(() -> at == null ? Source.HEAD : Source.at(at));
        }

        long waits() {
            return ticks + (clip.isEmpty() ? 0 : Balance.get().talk().beatTicks());
        }
    }

    public record Answers(List<Component> choices) implements CustomPacketPayload {
        public static final Type<Answers> TYPE = new Type<>(Wayfarer.id("answers"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Answers> STREAM_CODEC = ComponentSerialization.TRUSTED_STREAM_CODEC.apply(ByteBufCodecs.list())
                .map(Answers::new, Answers::choices);

        @Override
        public Type<Answers> type() {
            return TYPE;
        }
    }

    public record Screen(boolean open) implements CustomPacketPayload {
        public static final Type<Screen> TYPE = new Type<>(Wayfarer.id("screen"));
        public static final StreamCodec<ByteBuf, Screen> STREAM_CODEC = ByteBufCodecs.BOOL.map(Screen::new, Screen::open);

        @Override
        public Type<Screen> type() {
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

    private static String key(@Nullable String fire, @Nullable Vec3 at) {
        if (fire == null) {
            return NOTICES;
        }
        if (ROAD.equals(fire) && at != null) {
            return ROAD + "@" + (Mth.floor(at.x) >> 4) + "," + (Mth.floor(at.z) >> 4);
        }
        return fire;
    }

    private static Conversation conversation(@Nullable String fire, @Nullable Vec3 at) {
        return conversations.computeIfAbsent(key(fire, at), key -> new Conversation(key, ROAD.equals(fire)));
    }

    private static List<Conversation> ordered() {
        List<Conversation> ordered = new ArrayList<>(conversations.values().stream().filter(conversation -> !conversation.road).toList());
        ordered.addAll(conversations.values().stream().filter(conversation -> conversation.road).toList());
        return ordered;
    }

    public static void say(@Nullable String fire, @Nullable Vec3 at, List<Story.Line> lines, Placeholders placeholders) {
        say(fire, at, lines, placeholders, false);
    }

    public static void sayInSight(Vec3 at, Story.Line line) {
        say(ROAD, at, List.of(line), Placeholders.NONE, true);
    }

    private static void say(@Nullable String fire, @Nullable Vec3 at, List<Story.Line> lines, Placeholders placeholders, boolean inSight) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        Conversation conversation = conversation(fire, at);
        for (Story.Line line : lines) {
            if (line.when() != null && server != null && !Engine.holds(Progress.of(server), line.when())) {
                continue;
            }
            if (line.pause()) {
                conversation.queue.add(new Pause());
            }
            conversation.queue.add(new Said(fire, at, line, placeholders.fill(line.said(server != null && !Engine.overworld(server).isDay())), inSight));
        }
    }

    public static void say(@Nullable String fire, @Nullable Vec3 at, Story.Line line) {
        say(fire, at, List.of(line), Placeholders.NONE);
    }

    public static boolean again(MinecraftServer server, String who) {
        long now = server.overworld().getGameTime();
        Long last = AGAIN.get(who);
        if (last != null && now - last < (long) Balance.get().talk().againSeconds() * TICKS_PER_SECOND) {
            return false;
        }
        AGAIN.put(who, now);
        return true;
    }

    public static void notice(Component line) {
        conversation(null, null).queue.add(new Notice(line));
    }

    public static void then(@Nullable String fire, Runnable run) {
        then(fire, null, run);
    }

    public static void then(@Nullable String fire, @Nullable Vec3 at, Runnable run) {
        conversation(fire, at).queue.add(new Then(run));
    }

    public static void whenQuiet(Runnable run) {
        if (busy()) {
            pending.add(new Pending(null, run));
        } else {
            run.run();
        }
    }

    public static void whenQuiet(String key, Runnable run) {
        if (busy()) {
            unsaid.add(key);
            pending.add(new Pending(key, run));
        } else {
            run.run();
        }
    }

    public static boolean unsaid(String key) {
        return unsaid.contains(key);
    }

    public static void ask(Offer offer) {
        conversation(offer.fire(), offer.at()).queue.add(new Ask(offer));
    }

    public static void tell(ServerPlayer listener, Component text) {
        tell(List.of(listener), null, text);
    }

    public static long tell(List<ServerPlayer> listeners, @Nullable String by, Component text) {
        if (listeners.isEmpty()) {
            return reading(text.getString());
        }
        Subtitle subtitle = Subtitle.of(listeners.getFirst().server, by, text);
        listeners.forEach(listener -> PacketDistributor.sendToPlayer(listener, subtitle));
        return subtitle.waits();
    }

    public static Optional<String> speaker() {
        return clock < spokenUntil ? Optional.ofNullable(speaking) : Optional.empty();
    }

    public static String speakingIn() {
        return speakingIn;
    }

    public static boolean busy() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return conversations.values().stream().anyMatch(conversation -> !conversation.road && (!conversation.queue.isEmpty() || clock < conversation.next
                || conversation.offer != null && server != null && heard(server, conversation.offer.at())));
    }

    public static boolean listening(ServerPlayer player) {
        return conversations.values().stream().anyMatch(conversation -> !conversation.road && conversation.busy() && !conversation.told.isEmpty()
                && near(player, where(player.server, conversation.told.getLast()).orElse(null)));
    }

    public static boolean busy(@Nullable String fire) {
        Conversation conversation = conversations.get(key(fire, null));
        return conversation != null && (conversation.busy() || clock < conversation.next);
    }

    public static boolean free(ServerPlayer player) {
        UUID id = player.getUUID();
        Hearing heard = hearing.get(id);
        return !answering.containsKey(id) && !personal.containsKey(id) && (heard == null || heard.until() <= clock);
    }

    static void offerTo(ServerPlayer player, List<Story.Choice> choices, Answer answer) {
        personal.put(player.getUUID(), new Personal(choices, answer));
        PacketDistributor.sendToPlayer(player, new Answers(choices.stream().map(choice -> (Component) Component.literal(choice.text())).toList()));
    }

    static void withdraw(ServerPlayer player) {
        if (personal.remove(player.getUUID()) != null) {
            PacketDistributor.sendToPlayer(player, new Answers(List.of()));
        }
    }

    public static boolean answering(ServerPlayer player) {
        return personal.containsKey(player.getUUID()) || answering.containsKey(player.getUUID());
    }

    public static Optional<Offer> open(ServerPlayer player) {
        return Optional.ofNullable(answering.get(player.getUUID())).map(conversations::get).map(conversation -> conversation.offer);
    }

    public static void screen(ServerPlayer player, boolean open) {
        if (open) {
            screens.add(player.getUUID());
        } else {
            screens.remove(player.getUUID());
        }
    }

    static void drop(MinecraftServer server) {
        reset();
        answering.clear();
        personal.clear();
        server.getPlayerList().getPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, new Answers(List.of())));
    }

    public static void reset() {
        HEARD.clear();
        conversations.clear();
        unsaid.clear();
        pending.clear();
        hearing.clear();
        spokenUntil = clock;
    }

    private static void heard(MinecraftServer server, Said said) {
        Story.Line line = said.line();
        String by = line.by() == null ? "" : People.label(server, line.by());
        long day = Math.floorDiv(Engine.overworld(server).getDayTime(), Clock.DAY);
        if (said.fire() != null && !Long.valueOf(day).equals(HEARD.put(said.fire() + "/" + by + "/" + said.text(), day))) {
            Progress.of(server).hear(said.fire(), by, said.text());
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
        Steps.stage(server, line.stage());
    }

    @Nullable
    private static String voice(Story.Line line) {
        return line.voice() != null ? line.voice() : line.by();
    }

    static int reading(String text) {
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
        if (clock % OUTSIDE_TICKS == 0) {
            outside(server);
        }
        unoutline(server);
        for (Conversation conversation : ordered()) {
            advance(server, conversation);
            lapse(server, conversation);
        }
        conversations.values().removeIf(conversation -> conversation.idle && !conversation.busy() && clock >= conversation.next);
        offer(server);
        if (!pending.isEmpty() && !busy()) {
            List<Pending> due = List.copyOf(pending);
            pending.clear();
            for (Pending quiet : due) {
                if (quiet.key() != null) {
                    unsaid.remove(quiet.key());
                }
                quiet.run().run();
            }
        }
    }

    public static List<String> stuck(MinecraftServer server, long ticks) {
        return conversations.values().stream().filter(conversation -> conversation.busy() && clock - conversation.movedAt >= ticks)
                .map(conversation -> conversation.key + (conversation.offer != null ? " waits on an answer nobody near can give" : " waits on a line nobody can hear"))
                .toList();
    }

    private static void advance(MinecraftServer server, Conversation conversation) {
        long wait = (long) Balance.get().talk().waitSeconds() * TICKS_PER_SECOND;
        if (!conversation.busy() || conversation.offer != null && heard(server, conversation.offer.at())) {
            conversation.movedAt = clock;
        }
        if (conversation.queue.isEmpty() || listened(server, conversation.queue.peek())) {
            conversation.waiting = clock;
        }
        while (conversation.offer == null && !conversation.queue.isEmpty() && clock >= conversation.next
                && (listened(server, conversation.queue.peek()) || clock - conversation.waiting >= wait)) {
            Speech head = conversation.queue.peek();
            if (head instanceof Said said && said.inSight() && People.find(server, said.line().by()).filter(body -> seen(server, body)).isEmpty()) {
                conversation.queue.poll();
                continue;
            }
            if (head instanceof Said said && (held(server, conversation, said) || occupied(conversation, listeners(server, said)) || away(listeners(server, said)))
                    || head instanceof Replay replay && occupied(conversation, listeners(server, replay.said()))
                    || head instanceof Notice && (occupied(conversation, server.getPlayerList().getPlayers()) || away(server.getPlayerList().getPlayers()))) {
                break;
            }
            conversation.heldSince = NEVER;
            conversation.warned = false;
            conversation.movedAt = clock;
            switch (conversation.queue.poll()) {
                case Said said -> {
                    if (conversation.idle) {
                        conversation.told.clear();
                        conversation.idle = false;
                    }
                    conversation.told.add(said);
                    Optional<Vec3> at = where(server, said);
                    List<ServerPlayer> near = listeners(server, said);
                    near.forEach(player -> Firsts.first(player, Tasks.FIRST + Tasks.TALKED));
                    Component text = Component.literal(said.text());
                    Subtitle subtitle = Subtitle.of(server, said.line().by(), voice(said.line()), text, at.orElse(null));
                    deliver(server, subtitle, at.orElse(null), far(server, said, at, text));
                    Record.event(server, "line", null, line -> {
                        line.addProperty("quest", said.fire());
                        line.addProperty("by", said.line().by() == null ? "" : said.line().by());
                        line.addProperty("text", said.text());
                    });
                    heard(server, said);
                    conversation.next = clock + subtitle.waits();
                    spoke(conversation, said.line().by(), near);
                }
                case Replay replay -> {
                    Optional<Vec3> at = where(server, replay.said());
                    Subtitle subtitle = Subtitle.of(server, replay.said().line().by(), voice(replay.said().line()), Component.literal(replay.said().text()), at.orElse(null));
                    deliver(server, subtitle, at.orElse(null), null);
                    conversation.next = clock + subtitle.waits();
                    spoke(conversation, replay.said().line().by(), listeners(server, replay.said()));
                }
                case Notice notice -> {
                    Subtitle subtitle = Subtitle.of(server, null, notice.line());
                    deliver(server, subtitle, null, null);
                    conversation.next = clock + subtitle.waits();
                    spoke(conversation, null, server.getPlayerList().getPlayers());
                }
                case Pause pause -> conversation.next = clock + (long) Balance.get().talk().pauseSeconds() * TICKS_PER_SECOND;
                case Then then -> then.run().run();
                case Ask ask -> {
                    conversation.offer = ask.offer();
                    conversation.offered = clock;
                    conversation.unheardSince = NEVER;
                    Record.event(server, "answers", null, line -> {
                        line.addProperty("quest", ask.offer().fire());
                        line.add("answers", ask.offer().choices().stream().map(Story.Choice::text).collect(JsonArray::new, JsonArray::add, JsonArray::addAll));
                    });
                }
            }
        }
        if (conversation.queue.isEmpty() && conversation.offer == null && clock >= conversation.next) {
            conversation.idle = true;
        }
    }

    private static void spoke(Conversation conversation, @Nullable String by, List<ServerPlayer> listeners) {
        speaking = by;
        speakingIn = conversation.key;
        spokenUntil = conversation.next;
        listeners.forEach(player -> hearing.put(player.getUUID(), new Hearing(conversation.key, conversation.next)));
    }

    private static boolean away(List<ServerPlayer> listeners) {
        return listeners.stream().anyMatch(player -> player.isSleeping() || !Arrivals.there(player));
    }

    private static boolean occupied(Conversation conversation, List<ServerPlayer> listeners) {
        if (conversations.values().stream().anyMatch(other -> other != conversation && other.offer == null && other.queue.peek() instanceof Ask
                && (conversation.road || !other.road))) {
            return true;
        }
        for (ServerPlayer player : listeners) {
            Hearing heard = hearing.get(player.getUUID());
            if (heard != null && heard.until() > clock && !heard.key().equals(conversation.key)) {
                return true;
            }
            String shown = answering.get(player.getUUID());
            Conversation asking = shown == null ? null : conversations.get(shown);
            if (asking != null && asking != conversation && (conversation.road || !asking.road)) {
                return true;
            }
        }
        return false;
    }

    private static void lapse(MinecraftServer server, Conversation conversation) {
        Offer standing = conversation.offer;
        if (!conversation.road || standing == null || standing.lapsed() == null) {
            return;
        }
        if (heard(server, standing.at())) {
            conversation.unheardSince = NEVER;
            return;
        }
        if (conversation.unheardSince == NEVER) {
            conversation.unheardSince = clock;
            return;
        }
        if (clock - conversation.unheardSince >= (long) Balance.get().talk().lapseSeconds() * TICKS_PER_SECOND) {
            conversation.offer = null;
            conversation.votes.clear();
            conversation.queue.clear();
            offer(server);
            standing.lapsed().run();
        }
    }

    private static void outside(MinecraftServer server) {
        outside.clear();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Tents.outside(player).filter(door -> door.level().dimension() == Level.OVERWORLD).ifPresent(door -> outside.put(player.getUUID(), door.at()));
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        outlined.forEach(mob -> Hold.outline(mob, false));
        outlined.clear();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        reset();
        answering.clear();
        personal.clear();
        screens.clear();
        outside.clear();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        screens.remove(id);
        answering.remove(id);
        hearing.remove(id);
        outside.remove(id);
    }

    static boolean seen(MinecraftServer server, Entity body) {
        int reach = Balance.get().talk().reachBlocks();
        return Engine.players(server).stream().anyMatch(player -> player.level() == body.level() && player.distanceToSqr(body) <= (double) reach * reach
                && Sight.sees(player, body));
    }

    private static boolean held(MinecraftServer server, Conversation conversation, Said said) {
        Balance.Talk talk = Balance.get().talk();
        List<ServerPlayer> listeners = listeners(server, said);
        if (!listeners.isEmpty() && listeners.stream().noneMatch(Arrivals::there)) {
            return true;
        }
        List<ServerPlayer> hunted = fighting(server, listeners) ? List.of() : listeners.stream().filter(Talk::hunted).toList();
        if (!hunted.isEmpty()) {
            conversation.huntedAt = clock;
            outline(hunted);
            if (!conversation.warned && !conversation.idle) {
                conversation.warned = true;
                hunted.forEach(player -> hunters(player).stream().min(Comparator.comparingDouble(mob -> mob.distanceToSqr(player))).ifPresent(hunter ->
                        PacketDistributor.sendToPlayer(player, Subtitle.of(server, null, Component.translatable("wayfarer.talk.hunted", named(hunter))))));
            }
        }
        if (clock - conversation.huntedAt < (long) talk.safeSeconds() * TICKS_PER_SECOND) {
            return true;
        }
        if (listeners.stream().noneMatch(player -> screens.contains(player.getUUID()))) {
            return false;
        }
        if (conversation.heldSince == NEVER) {
            conversation.heldSince = clock;
        }
        return clock - conversation.heldSince < (long) talk.screenSeconds() * TICKS_PER_SECOND;
    }

    private static boolean fighting(MinecraftServer server, List<ServerPlayer> listeners) {
        return Engine.active(server).stream().anyMatch(active -> Steps.entered(active) && FIGHTS.contains(active.step().kind())
                && (!active.step().spec().has("at") || Progress.of(server).place(active.step().text("at")).filter(at -> listeners.stream()
                        .anyMatch(player -> player.level().dimension() == Level.OVERWORLD && player.blockPosition().distSqr(at) <= (double) Hold.REACH * Hold.REACH)).isPresent()));
    }

    private static Component named(Mob mob) {
        if (mob.getCustomName() != null) {
            return mob.getCustomName();
        }
        String kind = mob.getType().getDescription().getString().toLowerCase(Locale.ROOT);
        return Component.literal((VOWELS.indexOf(kind.charAt(0)) >= 0 ? "An " : "A ") + kind);
    }

    private static boolean hunted(ServerPlayer player) {
        return !hunters(player).isEmpty();
    }

    private static List<Mob> hunters(ServerPlayer player) {
        int reach = Balance.get().talk().huntedBlocks();
        return player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(reach),
                mob -> mob instanceof Enemy && mob.isAlive() && mob.getTarget() == player && mob.hasLineOfSight(player));
    }

    private static void outline(List<ServerPlayer> hunted) {
        hunted.stream().flatMap(player -> hunters(player).stream()).filter(mob -> !mob.hasGlowingTag()).forEach(mob -> {
            Hold.outline(mob, true);
            outlined.add(mob);
        });
    }

    private static void unoutline(MinecraftServer server) {
        long safe = (long) Balance.get().talk().safeSeconds() * TICKS_PER_SECOND;
        boolean waiting = conversations.values().stream().anyMatch(conversation -> clock - conversation.huntedAt < safe);
        outlined.removeIf(mob -> {
            boolean hunting = waiting && mob.isAlive() && mob.getTarget() instanceof ServerPlayer player && hunters(player).contains(mob);
            if (!hunting) {
                Hold.outline(mob, false);
            }
            return !hunting;
        });
    }

    private static void replay(MinecraftServer server, Conversation conversation) {
        Offer shown = conversation.offer;
        conversation.offer = null;
        conversation.votes.clear();
        offer(server);
        conversation.queue.addFirst(new Ask(shown));
        conversation.told.reversed().forEach(said -> conversation.queue.addFirst(new Replay(said)));
        Sounds.play(server, ANSWER_CHOSEN);
    }

    public static void pick(ServerPlayer player, int index) {
        MinecraftServer server = player.server;
        Personal own = personal.get(player.getUUID());
        if (own != null) {
            if (index >= 0 && index < own.choices().size()) {
                personal.remove(player.getUUID());
                PacketDistributor.sendToPlayer(player, new Answers(List.of()));
                Sounds.play(server, ANSWER_CHOSEN);
                String answer = own.choices().get(index).text();
                PacketDistributor.sendToPlayer(player, Subtitle.answered(player, Component.translatable("wayfarer.talk.chose", player.getDisplayName(), answer), answer));
                own.answer().chosen(player, own.choices().get(index));
            }
            return;
        }
        String key = answering.get(player.getUUID());
        Conversation conversation = key == null ? null : conversations.get(key);
        if (conversation == null || conversation.offer == null || index < 0) {
            return;
        }
        Offer answered = conversation.offer;
        if (index == answered.choices().size() && !conversation.told.isEmpty()) {
            replay(server, conversation);
            return;
        }
        if (index >= answered.choices().size()) {
            return;
        }
        Story.Choice picked = answered.choices().get(index);
        long offered = conversation.offered;
        Record.event(server, "chose", player, line -> {
            line.addProperty("quest", answered.fire());
            line.addProperty("answer", picked.text());
            line.addProperty("seconds", (clock - offered) / TICKS_PER_SECOND);
        });
        if (answered.vote()) {
            conversation.votes.put(player.getUUID(), index);
            long agreed = conversation.votes.values().stream().filter(vote -> vote == index).count();
            if (!Engine.most(server, agreed)) {
                Component chose = Component.translatable("wayfarer.talk.chose", player.getDisplayName(), picked.text());
                deliver(server, Subtitle.of(server, null, chose), answered.at(), null);
                Answers tally = new Answers(answers(server, conversation));
                answering.forEach((id, shown) -> {
                    ServerPlayer asked = server.getPlayerList().getPlayer(id);
                    if (asked != null && shown.equals(conversation.key)) {
                        PacketDistributor.sendToPlayer(asked, tally);
                    }
                });
                return;
            }
            conversation.votes.clear();
        }
        conversation.offer = null;
        offer(server);
        if (answered.fire() != null) {
            Progress.of(server).hear(answered.fire(), player.getDisplayName().getString(), picked.text());
        }
        Component chose = Component.translatable("wayfarer.talk.chose", player.getDisplayName(), picked.text());
        Subtitle said = Subtitle.answered(player, chose, picked.text());
        conversation.next = clock + said.waits();
        Tasks.did(player, "answer");
        Sounds.play(server, ANSWER_CHOSEN);
        deliver(server, said, answered.at(), answered.at() == null ? null : chose.copy().withStyle(ChatFormatting.DARK_GRAY));
        answered.answer().chosen(player, picked);
    }

    private static void offer(MinecraftServer server) {
        List<Conversation> asking = ordered().stream().filter(conversation -> conversation.offer != null).toList();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (personal.containsKey(player.getUUID())) {
                continue;
            }
            Optional<Conversation> shown = asking.stream().filter(conversation -> near(player, conversation.offer.at())).findFirst();
            String before = answering.get(player.getUUID());
            if (shown.isPresent() && !shown.get().key.equals(before)) {
                answering.put(player.getUUID(), shown.get().key);
                PacketDistributor.sendToPlayer(player, new Answers(answers(server, shown.get())));
            } else if (shown.isEmpty() && answering.remove(player.getUUID()) != null) {
                PacketDistributor.sendToPlayer(player, new Answers(List.of()));
            }
        }
    }

    private static List<Component> answers(MinecraftServer server, Conversation conversation) {
        Offer shown = conversation.offer;
        List<String> texts = shown.choices().stream().map(Story.Choice::text).toList();
        int online = Engine.players(server).size();
        List<Component> answers = new ArrayList<>(IntStream.range(0, texts.size())
                .mapToObj(index -> (Component) Component.literal(!shown.vote() || online < 2 ? texts.get(index)
                        : texts.get(index) + " " + conversation.votes.values().stream().filter(vote -> vote == index).count() + "/" + online))
                .toList());
        if (!conversation.told.isEmpty()) {
            answers.add(Component.translatable("wayfarer.talk.again"));
        }
        return answers;
    }

    private static boolean listened(MinecraftServer server, Speech speech) {
        if (!(speech instanceof Said said)) {
            return true;
        }
        return where(server, said).map(at -> heard(server, at)).orElse(true);
    }

    private static boolean heard(MinecraftServer server, @Nullable Vec3 at) {
        return server.getPlayerList().getPlayers().stream().anyMatch(player -> near(player, at));
    }

    private static List<ServerPlayer> listeners(MinecraftServer server, Said said) {
        Vec3 at = where(server, said).orElse(null);
        return server.getPlayerList().getPlayers().stream().filter(player -> near(player, at)).toList();
    }

    private static Optional<Vec3> where(MinecraftServer server, Said said) {
        String by = said.line().by();
        if (by == null && said.at() != null && server.getPlayerList().getPlayers().stream().noneMatch(player -> near(player, said.at()))) {
            return Optional.empty();
        }
        if (ROAD.equals(said.fire()) && said.at() != null || by == null) {
            return Optional.ofNullable(said.at());
        }
        if (Story.get().person(by).voice()) {
            return Optional.empty();
        }
        Optional<Entity> body = People.find(server, by);
        if (body.isPresent()) {
            return body.map(Entity::position);
        }
        return Optional.ofNullable(said.at());
    }

    @Nullable
    private static Component far(MinecraftServer server, Said said, Optional<Vec3> at, Component text) {
        if (at.isEmpty() || ROAD.equals(said.fire())) {
            return null;
        }
        MutableComponent line = line(server, said.line().by(), text);
        if (said.line().by() != null) {
            return line;
        }
        return server.getPlayerList().getPlayers().stream().filter(player -> near(player, at.get())).findFirst()
                .map(there -> (Component) Component.literal("[" + there.getScoreboardName() + "] ").withStyle(ChatFormatting.GRAY).append(line))
                .orElse(line);
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
        if (at == null) {
            return !(Memories.dreaming(player) && Memories.dream(player.level()));
        }
        int reach = Balance.get().talk().reachBlocks();
        Vec3 door = outside.get(player.getUUID());
        if (door != null && Tents.isTent(player.level().dimension())) {
            return at.distanceToSqr(door) <= (double) reach * reach;
        }
        if (Memories.dreaming(player) && Memories.dream(player.level())) {
            return Memories.scene(at);
        }
        return player.level().dimension() == Level.OVERWORLD && at.distanceToSqr(player.position()) <= (double) reach * reach;
    }

    public interface Placeholders {
        Placeholders NONE = text -> text;

        String fill(String text);
    }
}

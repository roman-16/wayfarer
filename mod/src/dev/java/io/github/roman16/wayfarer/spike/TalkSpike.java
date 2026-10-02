package io.github.roman16.wayfarer.spike;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.roman16.wayfarer.Wayfarer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class TalkSpike {
    private static final String TAG = Wayfarer.MODID + "_person_";
    private static final String BODY = "easy_npc:humanoid";
    private static final int GAMEMASTER = 2;
    private static final int LINE_TICKS = 20;
    private static final double REACH = 32;
    private static final JsonObject PEOPLE = JsonParser.parseString(resource("/wayfarer/spike/people.json")).getAsJsonObject();
    @Nullable
    private static Conversation active;

    private TalkSpike() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("talk")
                        .then(Commands.literal("spawn")
                                .requires(source -> source.hasPermission(GAMEMASTER))
                                .then(Commands.argument("person", StringArgumentType.word())
                                        .executes(context -> spawn(context.getSource(), StringArgumentType.getString(context, "person")))))
                        .then(Commands.literal("answer")
                                .then(Commands.argument("choice", StringArgumentType.word())
                                        .executes(context -> answer(context.getSource(), StringArgumentType.getString(context, "choice")))))));
    }

    private static int spawn(CommandSourceStack source, String person) {
        if (!PEOPLE.has(person)) {
            source.sendFailure(Component.literal("talk failed: there is no person " + person));
            return 0;
        }
        ServerLevel level = source.getLevel();
        Entity body = EntityType.byString(BODY).map(type -> type.create(level)).orElse(null);
        if (body == null) {
            source.sendFailure(Component.literal("talk failed: Easy NPC's " + BODY + " is not in this game"));
            return 0;
        }
        body.moveTo(source.getPosition().x, source.getPosition().y, source.getPosition().z, source.getRotation().y + 180, 0);
        body.setCustomName(Component.literal(PEOPLE.getAsJsonObject(person).get("name").getAsString()));
        body.addTag(TAG + person);
        if (body instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
        level.addFreshEntity(body);
        source.sendSuccess(() -> Component.literal("talk: " + person + " stands here"), false);
        return 1;
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        String person = event.getTarget().getTags().stream().filter(tag -> tag.startsWith(TAG)).map(tag -> tag.substring(TAG.length()))
                .findFirst().orElse(null);
        if (person == null || !PEOPLE.has(person)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        MinecraftServer server = player.server;
        if (active != null) {
            if (!active.person.equals(person)) {
                say(server, person, spec(person).get("busy").getAsString());
            }
            return;
        }
        active = new Conversation(person, event.getTarget(), server.getPlayerList().getPlayerCount());
        spec(person).getAsJsonArray("lines").forEach(line -> active.lines.add(line.getAsString()));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (active == null || active.lines.isEmpty() || event.getServer().getTickCount() % LINE_TICKS != 0) {
            return;
        }
        say(event.getServer(), active.person, active.lines.poll());
        if (active.lines.isEmpty() && !active.asked) {
            active.asked = true;
            offer(event.getServer());
        }
    }

    private static int answer(CommandSourceStack source, String choice) {
        ServerPlayer player = source.getPlayer();
        if (player == null || active == null || !active.asked) {
            source.sendFailure(Component.literal("Nobody is waiting for an answer."));
            return 0;
        }
        JsonObject option = option(choice);
        if (option == null) {
            source.sendFailure(Component.literal("That is not an answer here."));
            return 0;
        }
        MinecraftServer server = player.server;
        if (hard()) {
            active.votes.put(player.getUUID(), choice);
            broadcast(server, Component.literal(player.getScoreboardName() + " chose: " + option.get("text").getAsString()));
            String decided = decided();
            if (decided == null) {
                offer(server);
                return 1;
            }
            finish(server, option(decided));
            return 1;
        }
        if (player.distanceTo(active.body) > REACH) {
            source.sendFailure(Component.literal("You are too far away to answer."));
            return 0;
        }
        broadcast(server, Component.literal(player.getScoreboardName() + " chose: " + option.get("text").getAsString()));
        finish(server, option);
        return 1;
    }

    private static void finish(MinecraftServer server, JsonObject option) {
        String person = active.person;
        active = null;
        say(server, person, option.get("reply").getAsString());
    }

    @Nullable
    private static String decided() {
        int needed = (active.online * 2 + 2) / 3;
        Map<String, Long> counts = active.votes.values().stream().collect(Collectors.groupingBy(choice -> choice, Collectors.counting()));
        return counts.entrySet().stream().filter(entry -> entry.getValue() >= needed).map(Map.Entry::getKey).findFirst().orElse(null);
    }

    private static void offer(MinecraftServer server) {
        MutableComponent options = Component.literal("  ");
        for (JsonElement element : spec(active.person).getAsJsonObject("choice").getAsJsonArray("options")) {
            JsonObject option = element.getAsJsonObject();
            String text = option.get("text").getAsString();
            if (hard()) {
                long votes = active.votes.values().stream().filter(option.get("id").getAsString()::equals).count();
                text += " " + votes + "/" + active.online;
            }
            options.append(Component.literal("[" + text + "]").withStyle(style -> style.withColor(ChatFormatting.GOLD)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wayfarer talk answer " + option.get("id").getAsString())))).append("  ");
        }
        if (hard()) {
            List<String> waiting = server.getPlayerList().getPlayers().stream().filter(player -> !active.votes.containsKey(player.getUUID()))
                    .map(ServerPlayer::getScoreboardName).toList();
            String needs = "(needs %d of %d%s)".formatted((active.online * 2 + 2) / 3, active.online,
                    waiting.isEmpty() ? "" : ", waiting for " + String.join(", ", waiting));
            options.append(Component.literal(needs).withStyle(ChatFormatting.GRAY));
        }
        broadcast(server, options);
    }

    private static boolean hard() {
        return spec(active.person).getAsJsonObject("choice").get("kind").getAsString().equals("hard");
    }

    @Nullable
    private static JsonObject option(String choice) {
        for (JsonElement element : spec(active.person).getAsJsonObject("choice").getAsJsonArray("options")) {
            if (element.getAsJsonObject().get("id").getAsString().equals(choice)) {
                return element.getAsJsonObject();
            }
        }
        return null;
    }

    private static void say(MinecraftServer server, String person, String line) {
        JsonObject spec = spec(person);
        ChatFormatting color = ChatFormatting.getByName(spec.get("color").getAsString());
        broadcast(server, Component.literal("<" + spec.get("name").getAsString() + "> ").withStyle(color).append(Component.literal(line).withStyle(ChatFormatting.WHITE)));
    }

    private static void broadcast(MinecraftServer server, Component message) {
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    private static JsonObject spec(String person) {
        return PEOPLE.getAsJsonObject(person);
    }

    private static String resource(String name) {
        try (InputStream stream = TalkSpike.class.getResourceAsStream(name)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static final class Conversation {
        private final String person;
        private final Entity body;
        private final int online;
        private final Deque<String> lines = new ArrayDeque<>();
        private final Map<UUID, String> votes = new LinkedHashMap<>();
        private boolean asked;

        private Conversation(String person, Entity body, int online) {
            this.person = person;
            this.body = body;
            this.online = online;
        }
    }
}

package io.github.roman16.wayfarer.story;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class Talk {
    private static final int TICKS_PER_SECOND = 20;
    private static final Deque<Speech> queue = new ArrayDeque<>();
    private static long next;
    @Nullable
    private static Offer offer;

    private Talk() {}

    private sealed interface Speech permits Said, Then, Ask {}

    private record Said(Component line, int ticks) implements Speech {}

    private record Then(Runnable run) implements Speech {}

    private record Ask(Offer offer) implements Speech {}

    public record Offer(String quest, String step, @Nullable String speaker, List<Story.Choice> choices) {}

    public static void say(List<Story.Line> lines, Placeholders placeholders) {
        for (Story.Line line : lines) {
            queue.add(said(line, placeholders));
        }
    }

    public static void say(Story.Line line) {
        queue.add(said(line, Placeholders.NONE));
    }

    public static void notice(Component line) {
        queue.add(new Said(line, 0));
    }

    public static void then(Runnable run) {
        queue.add(new Then(run));
    }

    public static void ask(Offer offer) {
        queue.add(new Ask(offer));
    }

    public static boolean busy() {
        return !queue.isEmpty() || offer != null;
    }

    public static Optional<Offer> open() {
        return Optional.ofNullable(offer);
    }

    public static void reset() {
        queue.clear();
        offer = null;
        next = 0;
    }

    private static Said said(Story.Line line, Placeholders placeholders) {
        String text = placeholders.fill(line.text());
        return new Said(line(line.by(), Component.literal(text)), reading(text));
    }

    private static int reading(String text) {
        Balance.Talk talk = Balance.INSTANCE.talk();
        return Math.max(talk.leastSeconds(), Math.ceilDiv(text.length(), talk.charactersPerSecond())) * TICKS_PER_SECOND;
    }

    public static MutableComponent line(@Nullable String by, Component text) {
        if (by == null) {
            return text.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        }
        Story.Person person = Story.INSTANCE.person(by);
        return Component.literal("<" + person.name() + "> ").withStyle(person.color()).append(text.copy().withStyle(ChatFormatting.WHITE));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        if (server.getPlayerList().getPlayerCount() == 0) {
            return;
        }
        while (offer == null && !queue.isEmpty() && now >= next) {
            Speech speech = queue.poll();
            switch (speech) {
                case Said said -> {
                    broadcast(server, said.line());
                    next = now + said.ticks();
                }
                case Then then -> then.run().run();
                case Ask ask -> {
                    offer = ask.offer();
                    broadcast(server, choices(ask.offer()));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        reset();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("answer").then(Commands.argument("choice", StringArgumentType.word())
                        .executes(context -> answer(context.getSource(), StringArgumentType.getString(context, "choice"))))));
    }

    private static int answer(CommandSourceStack source, String choice) {
        ServerPlayer player = source.getPlayer();
        if (player == null || offer == null) {
            source.sendFailure(Component.translatable("wayfarer.talk.nobody"));
            return 0;
        }
        Story.Choice picked = offer.choices().stream().filter(option -> option.id().equals(choice)).findFirst().orElse(null);
        if (picked == null) {
            source.sendFailure(Component.translatable("wayfarer.talk.nobody"));
            return 0;
        }
        Optional<Entity> speaker = offer.speaker() == null ? Optional.empty() : People.find(player.server, offer.speaker());
        int reach = Balance.INSTANCE.talk().reachBlocks();
        if (speaker.isPresent() && (speaker.get().level() != player.level() || speaker.get().distanceToSqr(player) > (double) reach * reach)) {
            source.sendFailure(Component.translatable("wayfarer.talk.far"));
            return 0;
        }
        Offer answered = offer;
        offer = null;
        next = player.server.getTickCount() + reading(picked.text());
        Tasks.did(player, "answer");
        broadcast(player.server, Component.translatable("wayfarer.talk.chose", player.getDisplayName(), picked.text()).withStyle(ChatFormatting.DARK_GRAY));
        Engine.chosen(player.server, answered, picked);
        return 1;
    }

    private static Component choices(Offer offer) {
        MutableComponent line = Component.literal("  ");
        for (Story.Choice choice : offer.choices()) {
            line.append(Component.literal("[" + choice.text() + "]").withStyle(style -> style.withColor(ChatFormatting.GOLD)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + Wayfarer.MODID + " answer " + choice.id()))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("wayfarer.talk.click")))));
            line.append("  ");
        }
        return line;
    }

    private static void broadcast(MinecraftServer server, Component line) {
        server.getPlayerList().broadcastSystemMessage(line, false);
    }

    public interface Placeholders {
        Placeholders NONE = text -> text;

        String fill(String text);
    }
}

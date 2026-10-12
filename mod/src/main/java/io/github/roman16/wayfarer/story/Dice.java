package io.github.roman16.wayfarer.story;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Bundled;
import io.github.roman16.wayfarer.Notices;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

public final class Dice {
    static final String QUEST = "the_dice";
    static final String FOUND = "dice";
    private static final String DICER = "dicer";
    private static final String SCRATCH = "dice";
    private static final String WON = "won";
    private static final String PLAY = "play";
    private static final String NO = "no";
    private static final String THROW = "throw";
    private static final String BANK = "bank";
    private static final int TARGET = 32;
    private static final int STOP = 12;
    private static final int SIDES = 6;
    private static final int CHECK_TICKS = 40;
    private static final int UNSEEN = 24;
    private static final int FLOOR_WEIGHT = 8;
    private static final int ROOM = 8;
    private static final int ROAD_SKINS = 12;
    private static final TagKey<Structure> TAVERNS = TagKey.create(Registries.STRUCTURE, Wayfarer.id("taverns"));
    private static final Bundled<JsonObject> TEXT = Bundled.json("/wayfarer/dice.json");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<Long, BoundingBox> pending = new HashMap<>();
    private static final Map<UUID, Game> games = new HashMap<>();

    private Dice() {}

    private sealed interface Beat permits Said, Then, Ask {}

    private record Said(@Nullable String by, String text) implements Beat {}

    private record Then(Runnable run) implements Beat {}

    private record Ask(List<Story.Choice> choices, Talk.Answer answer) implements Beat {}

    private static final class Game {
        private final UUID player;
        private final Entity dicer;
        private final Deque<Beat> script = new ArrayDeque<>();
        private ItemStack stake = ItemStack.EMPTY;
        private ItemStack theirs = ItemStack.EMPTY;
        private String theirWords = "";
        private boolean playing;
        private int mine;
        private int their;
        private int pot;
        private long next;

        private Game(UUID player, Entity dicer) {
            this.player = player;
            this.dicer = dicer;
        }

        private void say(String text) {
            script.add(new Said(null, text));
        }

        private void speak(String text) {
            script.add(new Said(DICER, text));
        }
    }

    static boolean interact(ServerPlayer player, Entity body) {
        if (!body.getTags().contains(People.TAG + "_" + DICER)) {
            return false;
        }
        if (games.containsKey(body.getUUID())) {
            return true;
        }
        Game game = new Game(player.getUUID(), body);
        games.put(body.getUUID(), game);
        game.speak(fill(text("offer"), Map.of("target", TARGET)));
        boolean empty = player.getMainHandItem().isEmpty();
        game.script.add(new Ask(List.of(choice(PLAY, text(empty ? "play_empty" : PLAY)), choice(NO, text(NO))), (chooser, chosen) -> {
            if (chosen.id().equals(PLAY)) {
                start(chooser, game);
            } else {
                games.remove(body.getUUID());
            }
        }));
        return true;
    }

    private static void start(ServerPlayer player, Game game) {
        game.stake = player.getMainHandItem().copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        if (!game.stake.isEmpty()) {
            JsonObject pick = purse(player);
            game.theirs = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(pick.get("item").getAsString())), pick.get("count").getAsInt());
            game.theirWords = pick.get("words").getAsString();
        }
        game.playing = true;
        Progress progress = Progress.of(player.server);
        if (progress.place(FOUND).isEmpty()) {
            progress.place(FOUND, game.dicer.blockPosition());
            progress.setDirty();
            Engine.open(player.server);
        }
        game.speak(game.stake.isEmpty() ? text("stake_empty") : fill(text("stake"), Map.of("stake", name(game.stake), "theirs", game.theirWords)));
        throwOnce(player.server, game);
    }

    private static String name(ItemStack stack) {
        return (stack.getCount() > 1 ? stack.getCount() + " " : "") + stack.getHoverName().getString().toLowerCase(Locale.ROOT);
    }

    private static void throwOnce(MinecraftServer server, Game game) {
        int a = roll(game);
        int b = roll(game);
        if (a == 1 || b == 1) {
            game.pot = 0;
            game.say(fill(text("one"), Map.of("a", a, "b", b)));
            theirTurn(server, game);
            return;
        }
        game.pot += a + b;
        game.say(fill(text("throw"), values(game, a, b)));
        if (game.mine + game.pot >= TARGET) {
            game.mine += game.pot;
            end(server, game, true);
            return;
        }
        game.script.add(new Ask(List.of(choice(THROW, text("throw_again")), choice(BANK, text("bank_it"))), (chooser, chosen) -> {
            if (chosen.id().equals(THROW)) {
                throwOnce(server, game);
            } else {
                game.mine += game.pot;
                game.say(fill(text(BANK), Map.of("pot", game.pot, "mine", game.mine)));
                game.pot = 0;
                theirTurn(server, game);
            }
        }));
    }

    private static void theirTurn(MinecraftServer server, Game game) {
        game.pot = 0;
        while (true) {
            int a = roll(game);
            int b = roll(game);
            if (a == 1 || b == 1) {
                game.pot = 0;
                game.say(fill(text("their_one"), Map.of("a", a, "b", b)));
                break;
            }
            game.pot += a + b;
            game.say(fill(text("their_throw"), Map.of("a", a, "b", b, "pot", game.pot)));
            if (game.their + game.pot >= TARGET) {
                game.their += game.pot;
                end(server, game, false);
                return;
            }
            if (game.pot >= Math.min(STOP, TARGET - game.their)) {
                game.their += game.pot;
                game.say(fill(text("their_bank"), Map.of("pot", game.pot, "theirs", game.their)));
                break;
            }
        }
        game.pot = 0;
        game.script.add(new Then(() -> throwOnce(server, game)));
    }

    private static void end(MinecraftServer server, Game game, boolean won) {
        game.say(fill(text(won ? "win" : "lose"), Map.of("mine", game.mine, "theirs", game.their)));
        game.script.add(new Then(() -> {
            games.remove(game.dicer.getUUID());
            ServerPlayer player = server.getPlayerList().getPlayer(game.player);
            if (won && player != null) {
                give(player, game.stake);
                give(player, game.theirs);
                Progress progress = Progress.of(server);
                CompoundTag scratch = progress.scratch(SCRATCH);
                scratch.putInt(WON, scratch.getInt(WON) + 1);
                progress.setDirty();
                Journal.syncAll(server);
            }
        }));
    }

    private static void abandon(MinecraftServer server, Game game, @Nullable ServerPlayer player) {
        games.remove(game.dicer.getUUID());
        if (player != null) {
            Talk.withdraw(player);
            give(player, game.stake);
            if (game.playing) {
                tell(server, game, null, text("walked"));
            }
        }
    }

    private static void play(MinecraftServer server, Game game) {
        long now = server.getTickCount();
        while (now >= game.next && !game.script.isEmpty()) {
            Beat beat = game.script.poll();
            switch (beat) {
                case Said said -> {
                    tell(server, game, said.by(), said.text());
                    game.next = now + Talk.reading(said.text());
                }
                case Then then -> then.run().run();
                case Ask ask -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(game.player);
                    if (player != null) {
                        Talk.offerTo(player, ask.choices(), ask.answer());
                    }
                }
            }
        }
    }

    private static void tell(MinecraftServer server, Game game, @Nullable String by, String text) {
        int reach = Balance.get().talk().reachBlocks();
        Talk.tell(server.getPlayerList().getPlayers().stream()
                .filter(listener -> listener.level() == game.dicer.level() && listener.distanceToSqr(game.dicer) <= (double) reach * reach).toList(),
                by, Component.literal(text));
    }

    static int won(MinecraftServer server) {
        return Progress.of(server).scratch(SCRATCH).getInt(WON);
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        Notices.given(player, List.of(stack.copy()));
        if (!player.getInventory().add(stack.copy())) {
            player.drop(stack.copy(), false);
        }
    }

    private static JsonObject purse(ServerPlayer player) {
        List<JsonElement> purse = TEXT.get().getAsJsonArray("purse").asList();
        return purse.get(player.getRandom().nextInt(purse.size())).getAsJsonObject();
    }

    private static int roll(Game game) {
        return 1 + game.dicer.level().getRandom().nextInt(SIDES);
    }

    private static Map<String, Object> values(Game game, int a, int b) {
        return Map.of("a", a, "b", b, "pot", game.pot, "mine", game.mine, "theirs", game.their);
    }

    private static Story.Choice choice(String id, String text) {
        return new Story.Choice(id, text, "", null, null, null);
    }

    private static String text(String key) {
        return TEXT.get().get(key).getAsString();
    }

    private static String fill(String text, Map<String, ?> values) {
        String filled = text;
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            filled = filled.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return filled;
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        for (Map.Entry<Structure, StructureStart> entry : event.getChunk().getAllStarts().entrySet()) {
            StructureStart start = entry.getValue();
            if (start.isValid() && level.registryAccess().registryOrThrow(Registries.STRUCTURE).wrapAsHolder(entry.getKey()).is(TAVERNS)) {
                pending.put(start.getChunkPos().toLong(), start.getBoundingBox());
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!Engine.running(server)) {
            return;
        }
        for (Game game : List.copyOf(games.values())) {
            play(server, game);
        }
        if (server.getTickCount() % CHECK_TICKS != 0) {
            return;
        }
        for (Game game : List.copyOf(games.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(game.player);
            int reach = Balance.get().talk().reachBlocks();
            if (player == null || !game.dicer.isAlive() || player.level() != game.dicer.level() || player.distanceToSqr(game.dicer) > (double) reach * reach) {
                abandon(server, game, player);
            }
        }
        ServerLevel level = Engine.overworld(server);
        CompoundTag seated = Progress.of(server).scratch(SCRATCH);
        for (Map.Entry<Long, BoundingBox> entry : List.copyOf(pending.entrySet())) {
            String key = Long.toString(entry.getKey());
            BoundingBox box = entry.getValue();
            if (seated.contains(key)) {
                pending.remove(entry.getKey());
                continue;
            }
            if (!loaded(level, box) || server.getPlayerList().getPlayers().stream()
                    .anyMatch(player -> player.level() == level && box.inflatedBy(UNSEEN).isInside(player.blockPosition()))) {
                continue;
            }
            pending.remove(entry.getKey());
            Optional<BlockPos> seat = seat(level, box);
            if (seat.isEmpty()) {
                LOGGER.warn("the tavern at {} has no room for a dicer", box.getCenter());
            }
            seat.ifPresent(at -> {
                String skin = String.format("road_%02d", 1 + Math.floorMod(entry.getKey(), ROAD_SKINS));
                People.role(level, DICER, skin, Vec3.atBottomCenterOf(at), level.getRandom().nextFloat() * 360, "standing_weight");
                seated.putLong(key, at.asLong());
                Progress.of(server).setDirty();
                LOGGER.info("a dicer sits in the tavern at {}", at);
            });
        }
    }

    private static boolean loaded(ServerLevel level, BoundingBox box) {
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
            for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Optional<BlockPos> seat(ServerLevel level, BoundingBox box) {
        BlockPos centre = box.getCenter();
        BlockPos best = null;
        double nearest = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(box.minX() + 1, box.minY(), box.minZ() + 1, box.maxX() - 1, box.maxY() - 1, box.maxZ() - 1)) {
            double distance = Math.pow(pos.getX() - centre.getX(), 2) + Math.pow(pos.getZ() - centre.getZ(), 2) + (pos.getY() - box.minY()) * FLOOR_WEIGHT;
            if (distance < nearest && level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                    && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP) && !level.canSeeSky(pos) && indoors(level, pos)) {
                best = pos.immutable();
                nearest = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean indoors(ServerLevel level, BlockPos pos) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos.MutableBlockPos at = pos.above().mutable();
            boolean walled = false;
            for (int step = 0; step < ROOM && !walled; step++) {
                at.move(side);
                walled = level.getBlockState(at).isSolidRender(level, at);
            }
            if (!walled) {
                return false;
            }
        }
        return true;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            games.values().stream().filter(game -> game.player.equals(player.getUUID())).findFirst()
                    .ifPresent(game -> abandon(player.server, game, player));
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            games.values().stream().filter(game -> game.player.equals(player.getUUID())).findFirst()
                    .ifPresent(game -> abandon(player.server, game, player));
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        pending.clear();
        games.clear();
    }
}

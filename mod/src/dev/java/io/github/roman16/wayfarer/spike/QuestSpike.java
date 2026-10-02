package io.github.roman16.wayfarer.spike;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.roman16.wayfarer.CompassEvent;
import io.github.roman16.wayfarer.CompassPayload;
import io.github.roman16.wayfarer.Leads;
import io.github.roman16.wayfarer.Wayfarer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class QuestSpike {
    private static final String DATA = Wayfarer.MODID + "_quest_spike";
    private static final String PEOPLE = Wayfarer.MODID + "_spike_people";
    private static final int GAMEMASTER = 2;
    private static final int CHECK_TICKS = 20;
    private static final int AHEAD_BLOCKS = 1024;
    private static final int SEARCH_CHUNKS = 100;
    private static final int SPREAD_BLOCKS = 4;
    private static final int PEOPLE_REACH = 128;
    private static final long DAY_TICKS = 24000L;
    private static final int REACH = 0;
    private static final int HOLD = 1;
    private static final int CHOOSE = 2;
    private static final int DONE = 3;
    private static final JsonObject QUEST = JsonParser.parseString(resource("/wayfarer/spike/the_outpost.json")).getAsJsonObject();

    private QuestSpike() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("quest")
                        .then(Commands.literal("start")
                                .requires(source -> source.hasPermission(GAMEMASTER))
                                .executes(context -> start(context.getSource())))
                        .then(Commands.literal("choose")
                                .then(Commands.argument("choice", StringArgumentType.word())
                                        .executes(context -> choose(context.getSource(), StringArgumentType.getString(context, "choice")))))
                        .then(Commands.literal("retry").executes(context -> retry(context.getSource())))
                        .then(Commands.literal("claim")
                                .then(Commands.argument("reward", StringArgumentType.word())
                                        .executes(context -> claim(context.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(context, "reward")) ? 1 : 0)))));
    }

    private static int start(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        ServerLevel level = server.overworld();
        Vec3 at = source.getPosition();
        Vec2 rotation = source.getRotation();
        double yaw = Math.toRadians(rotation.y);
        BlockPos ahead = BlockPos.containing(at.x - Math.sin(yaw) * AHEAD_BLOCKS, at.y, at.z + Math.cos(yaw) * AHEAD_BLOCKS);
        TagKey<Structure> kind = TagKey.create(Registries.STRUCTURE, ResourceLocation.parse(step(REACH).get("goal").getAsString()));
        long searching = System.nanoTime();
        BlockPos goal = level.findNearestMapStructure(kind, ahead, SEARCH_CHUNKS, false);
        long millis = (System.nanoTime() - searching) / 1_000_000;
        if (goal == null) {
            source.sendFailure(Component.literal("there is no " + kind.location() + " within " + SEARCH_CHUNKS + " chunks ahead"));
            return 0;
        }
        Progress progress = Progress.of(server);
        progress.reset(goal);
        line(progress, REACH);
        BlockPos from = BlockPos.containing(at);
        int distance = (int) Math.sqrt(horizontal(from, goal));
        broadcast(server, Component.literal("[Quest] %s: %s %s, %d blocks".formatted(QUEST.get("title").getAsString(),
                step(REACH).get("journal").getAsString(), Leads.heading(goal.getX() - from.getX(), goal.getZ() - from.getZ()), distance)));
        sync(server);
        source.sendSuccess(() -> Component.literal("quest: the goal is at %d %d, found in %d ms".formatted(goal.getX(), goal.getZ(), millis)), false);
        return 1;
    }

    private static int choose(CommandSourceStack source, String choice) {
        MinecraftServer server = source.getServer();
        Progress progress = Progress.of(server);
        JsonObject picked = null;
        for (JsonElement option : step(CHOOSE).getAsJsonArray("choices")) {
            if (option.getAsJsonObject().get("id").getAsString().equals(choice)) {
                picked = option.getAsJsonObject();
            }
        }
        if (progress.step != CHOOSE || picked == null) {
            source.sendFailure(Component.literal("there is no choice " + choice + " to make now"));
            return 0;
        }
        progress.choices.put(choice, day(server));
        progress.codex.add(picked.get("codex").getAsString());
        progress.step = DONE;
        progress.rewards.add(QUEST.getAsJsonObject("reward").get("id").getAsString());
        progress.setDirty();
        broadcast(server, Component.literal(source.getTextName() + " chose: " + picked.get("text").getAsString()));
        broadcast(server, Component.literal("[Journal] A reward is waiting for everyone."));
        sync(server);
        return 1;
    }

    private static int retry(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        Progress progress = Progress.of(server);
        if (progress.step != HOLD || !progress.failed) {
            source.sendFailure(Component.literal("there is no failed step to try again"));
            return 0;
        }
        progress.failed = false;
        progress.peopleOut = false;
        progress.setDirty();
        broadcast(server, Component.literal("[Quest] The guards wait at the outpost again."));
        sync(server);
        return 1;
    }

    static boolean claim(ServerPlayer player, String reward) {
        Progress progress = Progress.of(player.server);
        JsonObject spec = QUEST.getAsJsonObject("reward");
        if (!progress.rewards.contains(reward) || !spec.get("id").getAsString().equals(reward)) {
            player.sendSystemMessage(Component.literal("There is no reward " + reward + " in the journal."));
            return false;
        }
        Set<UUID> claimed = progress.claims.computeIfAbsent(reward, key -> new TreeSet<>());
        if (!claimed.add(player.getUUID())) {
            player.sendSystemMessage(Component.literal("You already claimed " + reward + "."));
            return false;
        }
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(spec.get("item").getAsString())), spec.get("count").getAsInt());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        progress.setDirty();
        player.sendSystemMessage(Component.literal("[Journal] You claimed " + reward + "."));
        sync(player.server);
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_TICKS != 0) {
            return;
        }
        Progress progress = Progress.of(server);
        ServerLevel level = server.overworld();
        if (progress.step == REACH && progress.goal != null && arrived(level, progress)) {
            progress.step = HOLD;
            line(progress, HOLD);
            broadcast(server, Component.literal("[Quest] " + step(HOLD).get("journal").getAsString()));
            sync(server);
        }
        if (progress.step == HOLD && !progress.failed && !progress.peopleOut && arrived(level, progress)) {
            spawnPeople(level, progress);
            progress.setDirty();
        } else if (progress.step == HOLD && !progress.failed && settled(level, progress.goal) && people(level, progress).isEmpty()) {
            progress.step = CHOOSE;
            progress.setDirty();
            offerChoice(server);
            sync(server);
        }
        echo(server, progress);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.server;
        Progress progress = Progress.of(server);
        if (progress.step != HOLD || progress.failed) {
            return;
        }
        progress.failed = true;
        people(server.overworld(), progress).forEach(Entity::discard);
        progress.setDirty();
        broadcast(server, Component.literal("[Quest] " + step(HOLD).get("failed").getAsString() + " ").append(link("Try again", "/wayfarer quest retry")));
        sync(server);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Progress progress = Progress.of(player.server);
        long waiting = progress.rewards.stream().filter(reward -> !progress.claims.getOrDefault(reward, Set.of()).contains(player.getUUID())).count();
        if (waiting > 0) {
            player.sendSystemMessage(Component.literal("[Journal] %d reward%s waiting for you.".formatted(waiting, waiting == 1 ? " is" : "s are")));
        }
        PacketDistributor.sendToPlayer(player, new JournalView(view(progress, player)));
    }

    @SubscribeEvent
    public static void onCompass(CompassEvent event) {
        Progress progress = Progress.of(event.player().server);
        if (progress.step == REACH && progress.goal != null) {
            event.markers().add(new CompassPayload.Marker(CompassPayload.Kind.LEAD, "the outpost", progress.goal.getX(), progress.goal.getZ()));
            event.show();
        }
    }

    private static void offerChoice(MinecraftServer server) {
        broadcast(server, Component.literal(step(CHOOSE).get("prompt").getAsString()));
        MutableComponent options = Component.literal("  ");
        for (JsonElement option : step(CHOOSE).getAsJsonArray("choices")) {
            JsonObject choice = option.getAsJsonObject();
            options.append(link(choice.get("text").getAsString(), "/wayfarer quest choose " + choice.get("id").getAsString())).append("  ");
        }
        broadcast(server, options);
    }

    private static void echo(MinecraftServer server, Progress progress) {
        long today = day(server);
        for (Map.Entry<String, Long> choice : progress.choices.entrySet()) {
            if (today > choice.getValue() && progress.echoed.add(choice.getKey())) {
                broadcast(server, Component.literal(QUEST.getAsJsonObject("echo").get(choice.getKey()).getAsString()));
                progress.setDirty();
            }
        }
    }

    private static void spawnPeople(ServerLevel level, Progress progress) {
        JsonObject hold = step(HOLD);
        EntityType<?> type = EntityType.byString(hold.get("people").getAsString()).orElseThrow();
        int count = hold.get("count").getAsInt();
        for (int index = 0; index < count; index++) {
            double angle = Math.PI * 2 * index / count;
            int x = progress.goal.getX() + (int) Math.round(Math.cos(angle) * SPREAD_BLOCKS);
            int z = progress.goal.getZ() + (int) Math.round(Math.sin(angle) * SPREAD_BLOCKS);
            Entity person = type.create(level);
            if (person == null) {
                continue;
            }
            person.moveTo(x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z + 0.5, 0, 0);
            person.addTag(PEOPLE);
            if (person instanceof Mob mob) {
                mob.setPersistenceRequired();
            }
            level.addFreshEntity(person);
        }
        progress.peopleOut = true;
    }

    private static boolean arrived(ServerLevel level, Progress progress) {
        long within = step(REACH).get("within").getAsLong();
        return level.players().stream().anyMatch(player -> horizontal(player.blockPosition(), progress.goal) <= within * within);
    }

    private static List<Entity> people(ServerLevel level, Progress progress) {
        return level.getEntities((Entity) null, new AABB(progress.goal).inflate(PEOPLE_REACH),
                entity -> entity.isAlive() && entity.getTags().contains(PEOPLE));
    }

    private static boolean settled(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.areEntitiesLoaded(ChunkPos.asLong(pos));
    }

    private static void line(Progress progress, int step) {
        JsonElement codex = step(step).get("codex");
        if (codex != null) {
            progress.codex.add(codex.getAsString());
        }
        progress.setDirty();
    }

    private static MutableComponent link(String text, String command) {
        return Component.literal("[" + text + "]")
                .withStyle(style -> style.withColor(ChatFormatting.GOLD).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }

    private static void broadcast(MinecraftServer server, Component message) {
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    private static void sync(MinecraftServer server) {
        Progress progress = Progress.of(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, new JournalView(view(progress, player)));
        }
    }

    private static String view(Progress progress, ServerPlayer player) {
        JsonObject view = new JsonObject();
        JsonObject quest = new JsonObject();
        quest.addProperty("title", QUEST.get("title").getAsString());
        quest.addProperty("started", progress.goal != null);
        quest.addProperty("done", progress.step == DONE);
        quest.addProperty("failed", progress.failed);
        quest.addProperty("step", progress.step < DONE ? step(progress.step).get("journal").getAsString() : "Done.");
        view.add("quest", quest);
        JsonArray codex = new JsonArray();
        progress.codex.forEach(codex::add);
        view.add("codex", codex);
        JsonArray rewards = new JsonArray();
        JsonObject spec = QUEST.getAsJsonObject("reward");
        for (String reward : progress.rewards) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", reward);
            entry.addProperty("item", spec.get("item").getAsString());
            entry.addProperty("claimed", progress.claims.getOrDefault(reward, Set.of()).contains(player.getUUID()));
            rewards.add(entry);
        }
        view.add("rewards", rewards);
        return view.toString();
    }

    private static JsonObject step(int index) {
        return QUEST.getAsJsonArray("steps").get(index).getAsJsonObject();
    }

    private static long day(MinecraftServer server) {
        return server.overworld().getDayTime() / DAY_TICKS;
    }

    private static long horizontal(BlockPos from, BlockPos to) {
        long dx = from.getX() - to.getX();
        long dz = from.getZ() - to.getZ();
        return dx * dx + dz * dz;
    }

    private static String resource(String name) {
        try (InputStream stream = QuestSpike.class.getResourceAsStream(name)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public static final class Progress extends SavedData {
        private int step = REACH;
        @Nullable
        private BlockPos goal;
        private boolean failed;
        private boolean peopleOut;
        private final Map<String, Long> choices = new TreeMap<>();
        private final Set<String> echoed = new TreeSet<>();
        private final List<String> codex = new ArrayList<>();
        private final Map<String, Set<UUID>> claims = new TreeMap<>();
        private final List<String> rewards = new ArrayList<>();

        static Progress of(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(Progress::new, Progress::load, null), DATA);
        }

        private void reset(BlockPos target) {
            step = REACH;
            goal = target;
            failed = false;
            peopleOut = false;
            choices.clear();
            echoed.clear();
            codex.clear();
            claims.clear();
            rewards.clear();
            setDirty();
        }

        private static Progress load(CompoundTag tag, HolderLookup.Provider registries) {
            Progress progress = new Progress();
            progress.step = tag.getInt("step");
            progress.goal = tag.contains("goal") ? BlockPos.of(tag.getLong("goal")) : null;
            progress.failed = tag.getBoolean("failed");
            progress.peopleOut = tag.getBoolean("people_out");
            CompoundTag choices = tag.getCompound("choices");
            choices.getAllKeys().forEach(key -> progress.choices.put(key, choices.getLong(key)));
            tag.getList("echoed", Tag.TAG_STRING).forEach(entry -> progress.echoed.add(entry.getAsString()));
            tag.getList("codex", Tag.TAG_STRING).forEach(entry -> progress.codex.add(entry.getAsString()));
            CompoundTag claims = tag.getCompound("claims");
            claims.getAllKeys().forEach(key -> {
                Set<UUID> players = new TreeSet<>();
                claims.getList(key, Tag.TAG_INT_ARRAY).forEach(entry -> players.add(NbtUtils.loadUUID(entry)));
                progress.claims.put(key, players);
            });
            tag.getList("rewards", Tag.TAG_STRING).forEach(entry -> progress.rewards.add(entry.getAsString()));
            return progress;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putInt("step", step);
            if (goal != null) {
                tag.putLong("goal", goal.asLong());
            }
            tag.putBoolean("failed", failed);
            tag.putBoolean("people_out", peopleOut);
            CompoundTag chosen = new CompoundTag();
            choices.forEach(chosen::putLong);
            tag.put("choices", chosen);
            tag.put("echoed", strings(echoed));
            tag.put("codex", strings(codex));
            CompoundTag claimed = new CompoundTag();
            claims.forEach((reward, players) -> {
                ListTag list = new ListTag();
                players.forEach(player -> list.add(NbtUtils.createUUID(player)));
                claimed.put(reward, list);
            });
            tag.put("claims", claimed);
            tag.put("rewards", strings(rewards));
            return tag;
        }

        private static ListTag strings(Iterable<String> values) {
            ListTag list = new ListTag();
            values.forEach(value -> list.add(StringTag.valueOf(value)));
            return list;
        }
    }
}

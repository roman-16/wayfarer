package io.github.roman16.wayfarer.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.roman16.wayfarer.Wayfarer;
import io.netty.buffer.ByteBuf;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class Afterwards {
    private static final String DARK = "dark";
    private static final String SEEN = "seen";
    private static final String SIGHT = "sight";
    private static final List<People> PEOPLES = List.of(
            new People("diggers", "the_pull", "", Map.of("", "taken"), Set.of()),
            new People("drowned_city", "the_tide_folk", "the_tide_folk/the_shards", Map.of("both", "taken", "drowned", "taken", "none", "left", "theirs", "left"),
                    Set.of("none", "theirs")),
            new People("first_village", "the_bell", "the_bell/the_bell", Map.of("keep", "rung", "melt", "melted"), Set.of()),
            new People("frost_walkers", "the_frost_walkers", "the_frost_walkers/the_shard", Map.of("give", "given", "keep", "kept"), Set.of("give")),
            new People("jungle_folk", "the_locks", "", Map.of("", "emptied"), Set.of()),
            new People("sand_singers", "the_worm", "the_worm/the_worm", Map.of("killed", "killed", "spared", "spared"), Set.of("spared")),
            new People("scribes", "the_scribes", "the_scribes/the_shard", Map.of("leave", "left", "take", "taken"), Set.of("leave")),
            new People("tide_folk", "the_tide_folk", "the_tide_folk/the_shards", Map.of("both", "both", "drowned", "drowned", "none", "none", "theirs", "theirs"),
                    Set.of("drowned", "none")));

    private Afterwards() {}

    private record People(String id, String met, String decided, Map<String, String> halls, Set<String> dark) {}

    public record Open() implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(Wayfarer.id("afterwards"));
        public static final StreamCodec<ByteBuf, Open> STREAM_CODEC = StreamCodec.unit(new Open());

        @Override
        public Type<Open> type() {
            return TYPE;
        }
    }

    static JsonArray view(MinecraftServer server) {
        Progress progress = Progress.of(server);
        JsonArray pages = new JsonArray();
        After.door(progress).ifPresent(door -> {
            List<String> told = List.copyOf(progress.told.keySet());
            PEOPLES.stream().filter(people -> told.contains(people.met())).sorted(Comparator.comparingInt(people -> told.indexOf(people.met()))).forEach(people -> {
                String answer = progress.choices.getOrDefault(people.decided(), "");
                String hall = people.halls().get(answer);
                if (hall == null) {
                    return;
                }
                JsonObject page = new JsonObject();
                page.addProperty("door", door.equals(SIGHT) ? people.dark().contains(answer) ? DARK : SEEN : door);
                page.addProperty("hall", hall);
                page.addProperty("people", people.id());
                pages.add(page);
            });
        });
        return pages;
    }

    static void open(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new Open());
    }
}

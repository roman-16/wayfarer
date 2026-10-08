package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Balance;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

final class Remarks {
    private static final Map<String, Integer> said = new HashMap<>();

    private Remarks() {}

    static void answer(ServerPlayer player, Entity body) {
        Optional<String> who = People.who(body);
        if (who.isEmpty() || !body.isAlive() || Talk.busy()) {
            return;
        }
        String person = who.get();
        List<String> lines = lines(player.server, person);
        if (lines.isEmpty()) {
            return;
        }
        String key = person + "\n" + lines.getFirst();
        int next = (said.getOrDefault(key, -1) + 1) % lines.size();
        said.put(key, next);
        int reach = Balance.INSTANCE.talk().reachBlocks();
        Talk.tell(player.server.getPlayerList().getPlayers().stream()
                .filter(listener -> listener.level() == body.level() && listener.distanceToSqr(body) <= (double) reach * reach).toList(),
                People.label(player.server, person), Component.literal(lines.get(next)));
    }

    private static List<String> lines(MinecraftServer server, String person) {
        Optional<Engine.Active> tracked = Engine.main(server);
        if (tracked.isPresent()) {
            List<Story.Step> steps = tracked.get().quest().steps();
            for (int index = steps.indexOf(tracked.get().step()); index >= 0; index--) {
                List<String> found = steps.get(index).remarks().get(person);
                if (found != null) {
                    return found;
                }
            }
        }
        return Story.INSTANCE.person(person).remarks();
    }
}

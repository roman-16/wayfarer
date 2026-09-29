package io.github.roman16.wayfarer;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

public final class Teams {
    private Teams() {}

    public static UUID id(ServerPlayer player) {
        return team(player).map(Team::getId).orElse(player.getUUID());
    }

    public static Collection<ServerPlayer> onlineMembers(ServerPlayer player) {
        return team(player).<Collection<ServerPlayer>>map(Team::getOnlineMembers).orElse(List.of(player));
    }

    private static Optional<Team> team(ServerPlayer player) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return Optional.empty();
        }
        return FTBTeamsAPI.api().getManager().getTeamForPlayer(player);
    }
}

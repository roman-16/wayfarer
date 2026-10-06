package io.github.roman16.wayfarer.story;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class Places {
    private static final int CHECK_TICKS = 20;

    private Places() {}

    record Seat(Vec3 at, float yaw) {}

    static void build(MinecraftServer server, String name) {
        if (ready(server, name)) {
            return;
        }
        switch (name) {
            case Tollbridge.NAME -> Tollbridge.begin(server);
            case Diggers.NAME, Diggers.HALL -> Diggers.begin(server);
            case FirstCamp.NAME -> FirstCamp.find(server);
            default -> throw new IllegalArgumentException("the story has no way to build " + name);
        }
    }

    static boolean ready(MinecraftServer server, String name) {
        return Progress.of(server).place(name).isPresent();
    }

    static Optional<BlockPos> place(MinecraftServer server, String name) {
        return Progress.of(server).place(name);
    }

    static Optional<Seat> seat(MinecraftServer server, String name) {
        Progress progress = Progress.of(server);
        return progress.place("seat_" + name).map(pos -> new Seat(Vec3.atBottomCenterOf(pos), progress.yaws.getOrDefault("seat_" + name, 0f)));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % CHECK_TICKS == 0 && Engine.running(event.getServer())) {
            Tollbridge.tick(event.getServer());
            Caravan.tick(event.getServer());
            Diggers.tick(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        Tollbridge.reset();
        Caravan.reset();
        Diggers.reset();
    }
}

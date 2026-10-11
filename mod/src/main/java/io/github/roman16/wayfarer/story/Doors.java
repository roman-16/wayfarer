package io.github.roman16.wayfarer.story;

import java.util.List;
import net.minecraft.server.MinecraftServer;

public final class Doors {
    private static final String QUEST = "the_doors";
    private static final String STEP = "the_doors/the_doors";
    private static final String BLIND = "blind";
    private static final String SPARED = "the_worm/the_worm=spared";
    private static final List<String> PEOPLES = List.of("grass_kingdom", "tide_folk", "witches");

    private Doors() {}

    public static boolean blinding(MinecraftServer server) {
        return Engine.running(server) && BLIND.equals(Progress.of(server).choices.get(STEP)) && Progress.of(server).open(QUEST);
    }

    static int trusting(Progress progress) {
        int trusted = (int) PEOPLES.stream().filter(people -> progress.trust(people) > 0).count();
        return trusted + (Engine.chose(progress, SPARED) ? 1 : 0);
    }
}

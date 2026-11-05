package io.github.roman16.wayfarer.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.roman16.wayfarer.Narration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

public final class Epilogue {
    private static final String ALONE = "alone";
    private static final String DAT = ".dat";
    private static final String HOLLOW = "the_hollow/";
    private static final String LOST = "{lost}";
    private static final String NOT = "!";
    private static final String PIP = "pip";
    private static final String GROWN_PIP = "pip_grown";

    private Epilogue() {}

    static JsonArray view(MinecraftServer server) {
        Progress progress = Progress.of(server);
        JsonArray leaves = new JsonArray();
        if (After.door(progress).isEmpty()) {
            return leaves;
        }
        boolean alone = walkers(server).size() <= 1;
        List<String> lost = lost(progress);
        String teller = lost.contains(PIP) ? null : PIP;
        for (Story.Leaf leaf : Story.get().epilogue()) {
            if (leaf.lost() && lost.isEmpty() || !leaf.when().stream().allMatch(when -> holds(progress, alone, when))) {
                continue;
            }
            JsonObject shown = new JsonObject();
            String by = leaf.told() ? teller : leaf.by();
            if (by != null) {
                shown.addProperty("by", Story.get().person(by).name());
            }
            shown.addProperty("voice", by == null ? Narration.NARRATOR : PIP.equals(by) ? GROWN_PIP : by);
            shown.addProperty("text", leaf.text().replace(LOST, names(lost)));
            leaves.add(shown);
        }
        return leaves;
    }

    private static boolean holds(Progress progress, boolean alone, String when) {
        boolean not = when.startsWith(NOT);
        String condition = not ? when.substring(NOT.length()) : when;
        return condition.equals(ALONE) ? alone != not : Engine.holds(progress, when);
    }

    private static List<String> lost(Progress progress) {
        List<String> lost = new ArrayList<>();
        progress.choices.forEach((key, fate) -> {
            if (key.startsWith(HOLLOW) && fate.equals(Steps.LOST)) {
                lost.add(key.substring(HOLLOW.length()));
            }
        });
        lost.sort(Comparator.comparing((String person) -> !person.equals(PIP)).thenComparing(person -> Story.get().person(person).name()));
        return lost;
    }

    private static String names(List<String> people) {
        List<String> names = people.stream().map(person -> Story.get().person(person).name()).toList();
        return names.size() < 2 ? String.join("", names) : String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.getLast();
    }

    private static Set<String> walkers(MinecraftServer server) {
        Set<String> walkers = new HashSet<>();
        server.getPlayerList().getPlayers().forEach(player -> walkers.add(player.getStringUUID()));
        Path data = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        if (!Files.isDirectory(data)) {
            return walkers;
        }
        try (Stream<Path> files = Files.list(data)) {
            files.map(file -> file.getFileName().toString()).filter(name -> name.endsWith(DAT)).map(name -> name.substring(0, name.length() - DAT.length()))
                    .forEach(walkers::add);
        } catch (IOException failed) {
            throw new UncheckedIOException("epilogue failed: the world's player data at " + data + " cannot be read", failed);
        }
        return walkers;
    }
}

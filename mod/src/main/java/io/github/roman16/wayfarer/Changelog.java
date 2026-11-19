package io.github.roman16.wayfarer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;

public final class Changelog {
    private static final String FILE = "/wayfarer/CHANGELOG.md";
    private static final Pattern RELEASE = Pattern.compile("^## \\[(\\d+\\.\\d+\\.\\d+)]");
    private static final Pattern KIND = Pattern.compile("^### (\\w+)");
    private static final String FIXED = "Fixed";
    private static final String ENTRY = "- ";

    private Changelog() {}

    public static Optional<Component> since(@Nullable String last) {
        if (last == null || Release.compare(last, Release.version()) >= 0) {
            return Optional.empty();
        }
        int changes = 0;
        int fixes = 0;
        try (InputStream stream = Changelog.class.getResourceAsStream(FILE)) {
            if (stream == null) {
                return Optional.empty();
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            boolean counted = false;
            String kind = "";
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                Matcher release = RELEASE.matcher(line);
                Matcher heading = KIND.matcher(line);
                if (release.find()) {
                    String version = release.group(1);
                    counted = Release.compare(version, last) > 0 && Release.compare(version, Release.version()) <= 0;
                } else if (line.startsWith("## ")) {
                    counted = false;
                } else if (heading.find()) {
                    kind = heading.group(1);
                } else if (counted && line.startsWith(ENTRY)) {
                    if (kind.equals(FIXED)) {
                        fixes++;
                    } else {
                        changes++;
                    }
                }
            }
        } catch (IOException failure) {
            return Optional.empty();
        }
        if (changes > 0 && fixes > 0) {
            return Optional.of(Component.translatable("wayfarer.update.both", count("change", changes), count("fix", fixes)));
        }
        if (changes + fixes > 0) {
            return Optional.of(Component.translatable("wayfarer.update.one", changes > 0 ? count("change", changes) : count("fix", fixes)));
        }
        return Optional.of(Component.translatable("wayfarer.update"));
    }

    private static Component count(String what, int count) {
        return count == 1 ? Component.translatable("wayfarer.update." + what) : Component.translatable("wayfarer.update." + what + ".many", count);
    }
}

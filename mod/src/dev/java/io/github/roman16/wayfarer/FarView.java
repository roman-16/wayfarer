package io.github.roman16.wayfarer;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.types.ConfigEntry;
import com.seibel.distanthorizons.core.logging.f3.F3Screen;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class FarView {
    private static final String NUMBER = "(\\d[\\d.,'\\u00a0\\u202f]*\\d|\\d)";
    private static final Pattern POOL = Pattern.compile("^(World Gen/Import|Render Load), Tasks: " + NUMBER + ", Done: " + NUMBER + ", (?:Active|Paused): " + NUMBER + "/");
    private static final Pattern GENERATION = Pattern.compile("World Gen/Import Tasks: " + NUMBER + "/" + NUMBER + " \\(in progress " + NUMBER + "\\)");
    private static final Pattern REQUESTS = Pattern.compile("^Requests: " + NUMBER + " / " + NUMBER + " ");
    private static final Pattern STYLE = Pattern.compile("§.");

    private FarView() {
    }

    record Work(boolean generating, long left, long generated, long drawn) {
        @Override
        public String toString() {
            return "has %d generating and drawing tasks left, %d generated and %d drawn".formatted(left, generated, drawn);
        }
    }

    static Work read() {
        List<String> lines = new ArrayList<>();
        F3Screen.addStringToDisplay(lines);
        boolean generating = false;
        long left = 0;
        long generated = 0;
        long drawn = 0;
        for (String raw : lines) {
            String line = STYLE.matcher(raw).replaceAll("");
            Matcher pool = POOL.matcher(line);
            if (pool.find()) {
                left += count(pool.group(2)) + count(pool.group(4));
                if (pool.group(1).equals("Render Load")) {
                    drawn = count(pool.group(3));
                } else {
                    generated = count(pool.group(3));
                }
            }
            Matcher requests = REQUESTS.matcher(line);
            if (requests.find()) {
                generating = true;
                generated += count(requests.group(1));
                left += count(requests.group(2)) - count(requests.group(1));
            }
            Matcher generation = GENERATION.matcher(line);
            if (generation.find()) {
                generating = true;
                left += count(generation.group(1)) + count(generation.group(3));
            }
        }
        return new Work(generating, left, generated, drawn);
    }

    @SuppressWarnings("unchecked")
    static String set(String path, String value) {
        String[] parts = path.split("\\.");
        try {
            Class<?> holder = Config.class;
            for (int index = 0; index < parts.length - 1; index++) {
                holder = Class.forName(holder.getName() + "$" + parts[index]);
            }
            ConfigEntry<Object> entry = (ConfigEntry<Object>) holder.getField(parts[parts.length - 1]).get(null);
            Object current = entry.get();
            Object parsed = switch (current) {
                case Enum<?> named -> Enum.valueOf(named.getDeclaringClass().asSubclass(Enum.class), value);
                case Boolean ignored -> Boolean.valueOf(value);
                case Double ignored -> Double.valueOf(value);
                case Float ignored -> Float.valueOf(value);
                case Integer ignored -> Integer.valueOf(value);
                default -> value;
            };
            entry.set(parsed);
            return path + " = " + parsed;
        } catch (ReflectiveOperationException | ClassCastException exception) {
            return "error: dh has no setting " + path + " (" + exception + ")";
        }
    }

    private static long count(String number) {
        return Long.parseLong(number.replaceAll("\\D", ""));
    }
}

package io.github.roman16.wayfarer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

final class Problems {
    private static final int KEPT = 64;
    private static final Bundled<List<Pattern>> NOISE = Bundled.json("/wayfarer/noise.json", json -> json.getAsJsonArray("noise").asList().stream()
            .map(entry -> Pattern.compile(entry.getAsJsonObject().get("match").getAsString())).toList());
    private static final Deque<String> recent = new ArrayDeque<>();
    private static int dropped;

    private Problems() {}

    static void install() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Appender appender = new AbstractAppender(Wayfarer.MODID + "-driver-problems", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                record(event);
            }
        };
        appender.start();
        context.getConfiguration().getRootLogger().addAppender(appender, Level.WARN, null);
        context.updateLoggers();
    }

    private static synchronized void record(LogEvent event) {
        String line = "%s %s: %s".formatted(event.getLevel(), event.getLoggerName(), event.getMessage().getFormattedMessage());
        if (event.getThrown() != null) {
            line += " (" + event.getThrown() + ")";
        }
        String said = line;
        if (NOISE.get().stream().anyMatch(noise -> noise.matcher(said).find())) {
            return;
        }
        if (recent.size() == KEPT) {
            recent.removeFirst();
            dropped++;
        }
        recent.addLast(line);
    }

    static synchronized List<String> drain() {
        List<String> lines = new ArrayList<>();
        if (dropped > 0) {
            lines.add(dropped + " earlier problems were not kept");
        }
        lines.addAll(recent);
        recent.clear();
        dropped = 0;
        return lines;
    }
}

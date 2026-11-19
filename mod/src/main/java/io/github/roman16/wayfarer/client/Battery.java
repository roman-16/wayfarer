package io.github.roman16.wayfarer.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.Util;
import oshi.SystemInfo;
import oshi.hardware.PowerSource;

final class Battery {
    private static final Path LINUX_SUPPLIES = Path.of("/sys/class/power_supply");
    private static final String MAINS = "Mains";
    private static final String OFFLINE = "0";
    private static volatile boolean powering;
    private static volatile boolean readable = true;

    private Battery() {}

    static boolean powering() {
        return powering;
    }

    static void read() {
        if (readable) {
            Util.backgroundExecutor().execute(Battery::measure);
        }
    }

    private static void measure() {
        try {
            powering = Files.isDirectory(LINUX_SUPPLIES) ? mainsUnplugged() : systemBatteryAlone();
        } catch (IOException | RuntimeException | LinkageError unreadable) {
            readable = false;
            powering = false;
        }
    }

    private static boolean mainsUnplugged() throws IOException {
        List<Path> mains;
        try (Stream<Path> supplies = Files.list(LINUX_SUPPLIES)) {
            mains = supplies.filter(supply -> MAINS.equals(field(supply, "type"))).toList();
        }
        return !mains.isEmpty() && mains.stream().allMatch(supply -> OFFLINE.equals(field(supply, "online")));
    }

    private static String field(Path supply, String name) {
        try {
            return Files.readString(supply.resolve(name)).strip();
        } catch (IOException unreadable) {
            return "";
        }
    }

    private static boolean systemBatteryAlone() {
        List<PowerSource> sources = new SystemInfo().getHardware().getPowerSources();
        return !sources.isEmpty() && sources.stream().noneMatch(PowerSource::isPowerOnLine);
    }
}

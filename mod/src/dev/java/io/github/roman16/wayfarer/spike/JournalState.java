package io.github.roman16.wayfarer.spike;

public final class JournalState {
    private static volatile String view = "{}";

    private JournalState() {}

    public static String view() {
        return view;
    }

    static void show(String json) {
        view = json;
    }
}

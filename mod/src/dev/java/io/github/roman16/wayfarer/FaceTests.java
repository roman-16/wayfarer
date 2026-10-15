package io.github.roman16.wayfarer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

@GameTestHolder(Wayfarer.MODID)
@PrefixGameTestTemplate(false)
public final class FaceTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Bundled<JsonObject> FACES = Bundled.json("/wayfarer/faces.json");
    private static final String TEXTURES = "https://textures.minecraft.net/texture/";
    private static final Duration PATIENCE = Duration.ofSeconds(32);
    private static final double FOUND_SHARE = 3 / 4.0;
    private static final double WRONG_SHARE = 1 / 16.0;
    private static final int OPAQUE = 0xFF000000;
    private static final int SEEN_ALPHA = 128;
    private static final int LEGACY_HEIGHT = 32;
    private static final int HAT_RIGHT = 64;
    private static final int HEAD_RIGHT = 32;
    private static final int HEAD_BOTTOM = 16;

    private enum Outcome { RIGHT, SHORT, NONE, WRONG }

    private FaceTests() {}

    @GameTest(template = "empty")
    public static void theEyesAreFoundOnTheSetsFaces(GameTestHelper helper) {
        Map<String, Map<String, int[]>> skins = skins();
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, Map<String, int[]>> group : skins.entrySet()) {
            Map<Outcome, List<String>> outcomes = new TreeMap<>();
            for (Map.Entry<String, int[]> face : group.getValue().entrySet()) {
                JsonObject spec = FACES.get().getAsJsonObject(group.getKey()).getAsJsonObject(face.getKey());
                outcomes.computeIfAbsent(outcome(spec, face.getValue()), outcome -> new ArrayList<>()).add(face.getKey());
            }
            int total = group.getValue().size();
            int found = count(outcomes, Outcome.RIGHT) + count(outcomes, Outcome.SHORT);
            LOGGER.info("faces: {} {} - {} right, {} on the eyes but short, {} none, {} wrong {}", total, group.getKey(), count(outcomes, Outcome.RIGHT),
                    count(outcomes, Outcome.SHORT), count(outcomes, Outcome.NONE), count(outcomes, Outcome.WRONG), outcomes.getOrDefault(Outcome.WRONG, List.of()));
            if (group.getKey().equals("own") && count(outcomes, Outcome.RIGHT) < total) {
                failures.add("own faces not right: " + outcomes);
            }
            if (group.getKey().equals("community") && (count(outcomes, Outcome.WRONG) > total * WRONG_SHARE || found < total * FOUND_SHARE)) {
                failures.add("community faces under the bar: " + outcomes);
            }
        }
        if (!failures.isEmpty()) {
            helper.fail(String.join("; ", failures));
        }
        helper.succeed();
    }

    private static int count(Map<Outcome, List<String>> outcomes, Outcome outcome) {
        return outcomes.getOrDefault(outcome, List.of()).size();
    }

    private static Outcome outcome(JsonObject spec, int[] skin) {
        List<String> rows = spec.getAsJsonArray("eyes").asList().stream().map(JsonElement::getAsString).toList();
        List<Faces.Pixel> found = Faces.eyes(skin, true);
        Set<Integer> lit = new HashSet<>();
        for (Faces.Pixel pixel : found) {
            char label = rows.get(pixel.y()).charAt(pixel.x());
            if (label == '.' || label != '?' && Character.isUpperCase(label) != pixel.hat()) {
                return Outcome.WRONG;
            }
            lit.add(pixel.y() * Faces.SIDE + pixel.x());
        }
        boolean missing = false;
        for (int y = 0; y < Faces.SIDE; y++) {
            for (int x = 0; x < Faces.SIDE; x++) {
                char label = rows.get(y).charAt(x);
                missing |= label != '.' && label != '?' && !lit.contains(y * Faces.SIDE + x);
            }
        }
        return !missing ? Outcome.RIGHT : found.isEmpty() ? Outcome.NONE : Outcome.SHORT;
    }

    private static Map<String, Map<String, int[]>> skins() {
        Path cache = Path.of(System.getenv().getOrDefault("XDG_CACHE_HOME", System.getProperty("user.home") + "/.cache"), "wayfarer", "faces");
        HttpClient client = HttpClient.newBuilder().connectTimeout(PATIENCE).build();
        Map<String, Map<String, CompletableFuture<byte[]>>> pending = new TreeMap<>();
        for (Map.Entry<String, JsonElement> group : FACES.get().entrySet()) {
            for (Map.Entry<String, JsonElement> face : group.getValue().getAsJsonObject().entrySet()) {
                JsonObject spec = face.getValue().getAsJsonObject();
                pending.computeIfAbsent(group.getKey(), key -> new TreeMap<>()).put(face.getKey(), source(spec, cache, client));
            }
        }
        Map<String, Map<String, int[]>> skins = new TreeMap<>();
        pending.forEach((group, faces) -> faces.forEach((name, bytes) -> skins.computeIfAbsent(group, key -> new TreeMap<>()).put(name, pixels(bytes.join()))));
        return skins;
    }

    private static CompletableFuture<byte[]> source(JsonObject spec, Path cache, HttpClient client) {
        if (spec.has("default")) {
            return CompletableFuture.completedFuture(resource("minecraft", "assets/minecraft/textures/entity/player/wide/" + spec.get("default").getAsString() + ".png"));
        }
        if (spec.has("person")) {
            return CompletableFuture.completedFuture(resource(Wayfarer.MODID, "assets/wayfarer/textures/entity/people/" + spec.get("person").getAsString() + ".png"));
        }
        String hash = spec.get("texture").getAsString();
        Path kept = cache.resolve(hash + ".png");
        if (Files.exists(kept)) {
            return CompletableFuture.completedFuture(read(kept));
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(TEXTURES + hash)).timeout(PATIENCE).build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenApply(response -> {
            if (response.statusCode() != 200) {
                throw new IllegalStateException("face test failed: " + TEXTURES + hash + " answered " + response.statusCode());
            }
            write(kept, response.body());
            return response.body();
        });
    }

    private static byte[] resource(String mod, String name) {
        Path path = ModList.get().getModFileById(mod).getFile().findResource(name);
        if (!Files.exists(path)) {
            throw new IllegalStateException("face test failed: " + mod + " has no " + name);
        }
        return read(path);
    }

    private static byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void write(Path path, byte[] bytes) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static int[] pixels(byte[] png) {
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(png));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        int[] skin = new int[Faces.SKIN * Faces.SKIN];
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < Faces.SKIN; x++) {
                skin[y * Faces.SKIN + x] = image.getRGB(x, y);
            }
        }
        if (image.getHeight() == LEGACY_HEIGHT) {
            notchTransparency(skin);
        }
        for (int y = 0; y < HEAD_BOTTOM; y++) {
            for (int x = 0; x < HEAD_RIGHT; x++) {
                skin[y * Faces.SKIN + x] |= OPAQUE;
            }
        }
        return skin;
    }

    private static void notchTransparency(int[] skin) {
        for (int y = 0; y < LEGACY_HEIGHT; y++) {
            for (int x = HEAD_RIGHT; x < HAT_RIGHT; x++) {
                if (skin[y * Faces.SKIN + x] >>> 24 < SEEN_ALPHA) {
                    return;
                }
            }
        }
        for (int y = 0; y < LEGACY_HEIGHT; y++) {
            for (int x = HEAD_RIGHT; x < HAT_RIGHT; x++) {
                skin[y * Faces.SKIN + x] &= ~OPAQUE;
            }
        }
    }
}

package io.github.roman16.wayfarer.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

public final class Panorama {
    private static final String FOLDER = "textures/gui/title/background";
    private static final String FACE = "/panorama_";
    private static final String FIRST = FACE + "0.png";
    private static final ResourceLocation TITLE = ResourceLocation.withDefaultNamespace(FOLDER + "/panorama_0.png");

    private Panorama() {}

    public static void dress(ResourceLocation[] faces) {
        if (!faces[0].equals(TITLE)) {
            return;
        }
        List<String> sets = Minecraft.getInstance().getResourceManager().listResources(FOLDER, found -> found.getPath().endsWith(FIRST)).keySet().stream()
                .filter(found -> found.getNamespace().equals(TITLE.getNamespace()))
                .map(found -> found.getPath().substring(FOLDER.length() + 1))
                .filter(face -> face.indexOf('/') > 0 && face.indexOf('/') == face.lastIndexOf('/'))
                .map(face -> face.substring(0, face.indexOf('/')))
                .sorted()
                .toList();
        if (sets.isEmpty()) {
            return;
        }
        String set = pick(sets);
        for (int face = 0; face < faces.length; face++) {
            faces[face] = TITLE.withPath(FOLDER + "/" + set + FACE + face + ".png");
        }
    }

    static String pick(List<String> sets) {
        return sets.get(RandomSource.create().nextInt(sets.size()));
    }
}

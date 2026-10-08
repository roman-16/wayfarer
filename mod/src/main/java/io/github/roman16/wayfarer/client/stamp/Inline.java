package io.github.roman16.wayfarer.client.stamp;

import io.github.roman16.wayfarer.story.Tasks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

public final class Inline {
    private static final int KEY_GAP = 1;

    private Inline() {
    }

    public record Piece(String text, Style style, boolean key) {
        int width(Font font) {
            return key ? Stamp.keycapWidth(font, text) + KEY_GAP * 2 : font.width(FormattedText.of(text, style));
        }
    }

    public static List<List<Piece>> lines(Font font, Component text, int width) {
        List<Piece> words = new ArrayList<>();
        text.visit((style, part) -> {
            if (Tasks.KEY.equals(style.getInsertion())) {
                words.add(new Piece(part, style, true));
                return Optional.empty();
            }
            StringBuilder word = new StringBuilder();
            for (char letter : part.toCharArray()) {
                word.append(letter);
                if (letter == ' ') {
                    words.add(new Piece(word.toString(), style, false));
                    word.setLength(0);
                }
            }
            if (!word.isEmpty()) {
                words.add(new Piece(word.toString(), style, false));
            }
            return Optional.empty();
        }, Style.EMPTY);
        List<List<Piece>> lines = new ArrayList<>();
        List<Piece> line = new ArrayList<>();
        int used = 0;
        for (Piece word : words) {
            int wide = word.width(font);
            int trimmed = word.key() ? wide : font.width(FormattedText.of(word.text().stripTrailing(), word.style()));
            if (!line.isEmpty() && used + trimmed > width) {
                lines.add(line);
                line = new ArrayList<>();
                used = 0;
                if (!word.key() && word.text().isBlank()) {
                    continue;
                }
            }
            line.add(word);
            used += wide;
        }
        if (!line.isEmpty()) {
            lines.add(line);
        }
        return lines;
    }

    public static int width(Font font, List<Piece> line) {
        return line.stream().mapToInt(piece -> piece.width(font)).sum();
    }

    public static void draw(GuiGraphics graphics, Font font, List<Piece> line, int x, int y, int color, boolean shadow) {
        int cursor = x;
        for (Piece piece : line) {
            if (piece.key()) {
                Stamp.keycap(graphics, font, piece.text(), cursor + KEY_GAP, y);
            } else {
                graphics.drawString(font, Component.literal(piece.text()).withStyle(piece.style()), cursor, y, color, shadow);
            }
            cursor += piece.width(font);
        }
    }
}

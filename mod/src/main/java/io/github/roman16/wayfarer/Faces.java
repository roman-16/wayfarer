package io.github.roman16.wayfarer;

import java.util.ArrayList;
import java.util.List;

public final class Faces {
    public static final int FACE_X = 8;
    public static final int FACE_Y = 8;
    public static final int HAT_X = 40;
    public static final int SIDE = 8;
    public static final int SKIN = 64;
    private static final double ABOVE_OR_BELOW = 0.875;
    private static final int BIG = 8;
    private static final int CHAIN = 24;
    private static final int CONTINUED = 40;
    private static final int CONTRAST = 32;
    private static final int FEATURE = 64;
    private static final double FULL_CONTRAST = 128;
    private static final int GREY = 48;
    private static final double[] HALF_ROW = {0.125, 0.125, 0.25, 0.25, 0.75, 0.875, 1, 1, 1, 1, 1, 1, 0.75, 0.5, 0.25};
    private static final int PALE = 192;
    private static final double RESIZED = 0.75;
    private static final int SAME = 48;
    private static final int SHOWN_ALPHA = 26;
    private static final double SIDEWAYS = 0.5;
    private static final double SURE = 0.25;
    private static final int TALLEST = 3;
    private static final double TONED = 1.5;
    private static final int TWO_TONE = 64;
    private static final int WIDEST = 3;

    public record Pixel(int x, int y, boolean hat, boolean white) {}

    private record Box(int x, int y, int w, int h, double score, double centreX, double centreY, int mean, boolean toned) {}

    private Faces() {}

    public static List<Pixel> eyes(int[] skin, boolean hatShown) {
        Face face = new Face(skin, hatShown);
        List<Box> left = new ArrayList<>();
        List<Box> right = new ArrayList<>();
        for (int h = 1; h <= TALLEST; h++) {
            for (int w = 1; w <= WIDEST; w++) {
                for (int y = 0; y + h <= SIDE; y++) {
                    for (int x = 0; x + w <= SIDE; x++) {
                        if (x + w <= SIDE / 2 || x >= SIDE / 2) {
                            Box box = face.box(x, y, w, h);
                            if (box != null) {
                                (x < SIDE / 2 ? left : right).add(box);
                            }
                        }
                    }
                }
            }
        }
        Box bestLeft = null;
        Box bestRight = null;
        double best = SURE;
        for (Box a : left) {
            for (Box b : right) {
                double score = pair(a, b);
                if (score > best) {
                    best = score;
                    bestLeft = a;
                    bestRight = b;
                }
            }
        }
        if (bestLeft == null) {
            return List.of();
        }
        List<Pixel> pixels = new ArrayList<>();
        for (Box eye : List.of(bestLeft, bestRight)) {
            for (int y = eye.y; y < eye.y + eye.h; y++) {
                for (int x = eye.x; x < eye.x + eye.w; x++) {
                    pixels.add(new Pixel(x, y, face.hat[y * SIDE + x], eye.w * eye.h > 1 && pale(face.colour(x, y))));
                }
            }
        }
        return pixels;
    }

    private static double pair(Box a, Box b) {
        if (a.x + a.w >= b.x) {
            return 0;
        }
        double mirrorError = Math.abs(a.centreX - (SIDE - 1 - b.centreX)) + Math.abs(a.centreY - b.centreY);
        if (mirrorError > 1) {
            return 0;
        }
        double score = Math.sqrt(a.score * b.score) / (1 + mirrorError);
        score *= (1 + similarity(a.mean, b.mean)) / 2;
        score *= a.w == b.w && a.h == b.h ? 1 : RESIZED;
        score *= HALF_ROW[(int) Math.round(a.centreY + b.centreY)];
        if (a.toned && b.toned) {
            score *= TONED;
        }
        return score;
    }

    private static final class Face {
        private final int[] colours = new int[SIDE * SIDE];
        private final boolean[] hat = new boolean[SIDE * SIDE];
        private final int[] part = new int[SIDE * SIDE];
        private final int[] size = new int[SIDE * SIDE];

        private Face(int[] skin, boolean hatShown) {
            for (int y = 0; y < SIDE; y++) {
                for (int x = 0; x < SIDE; x++) {
                    int i = y * SIDE + x;
                    int base = skin[(FACE_Y + y) * SKIN + FACE_X + x];
                    int over = skin[(FACE_Y + y) * SKIN + HAT_X + x];
                    int alpha = over >>> 24;
                    hat[i] = hatShown && alpha >= SHOWN_ALPHA;
                    colours[i] = (hat[i] ? blend(over, base, alpha) : base) & 0xFFFFFF;
                    part[i] = i;
                }
            }
            for (int y = 0; y < SIDE; y++) {
                for (int x = 0; x < SIDE; x++) {
                    int i = y * SIDE + x;
                    if (x + 1 < SIDE && distance(colours[i], colours[i + 1]) <= CHAIN) {
                        join(i, i + 1);
                    }
                    if (y + 1 < SIDE && distance(colours[i], colours[i + SIDE]) <= CHAIN) {
                        join(i, i + SIDE);
                    }
                }
            }
            for (int i = 0; i < part.length; i++) {
                part[i] = root(i);
                size[part[i]]++;
            }
        }

        private int colour(int x, int y) {
            return colours[y * SIDE + x];
        }

        private boolean big(int x, int y) {
            return size[part[y * SIDE + x]] >= BIG;
        }

        private Box box(int bx, int by, int w, int h) {
            long red = 0;
            long green = 0;
            long blue = 0;
            int lightest = 0;
            int darkest = Integer.MAX_VALUE;
            boolean anyPale = false;
            for (int y = by; y < by + h; y++) {
                for (int x = bx; x < bx + w; x++) {
                    if (big(x, y) || !inside(part[y * SIDE + x], bx, by, w, h)) {
                        return null;
                    }
                    int colour = colour(x, y);
                    red += colour >> 16 & 0xFF;
                    green += colour >> 8 & 0xFF;
                    blue += colour & 0xFF;
                    lightest = Math.max(lightest, luma(colour));
                    darkest = Math.min(darkest, luma(colour));
                    anyPale |= pale(colour);
                }
                if (w == WIDEST && distance(colour(bx, y), colour(bx + w - 1, y)) > SAME) {
                    return null;
                }
            }
            int count = w * h;
            boolean toned = count > 1 && lightest - darkest >= TWO_TONE;
            if (count > 1 && !toned && anyPale) {
                return null;
            }
            List<int[]> ring = new ArrayList<>();
            for (int y = by - 1; y <= by + h; y++) {
                for (int x = bx - 1; x <= bx + w; x++) {
                    if (x >= 0 && y >= 0 && x < SIDE && y < SIDE && !(x >= bx && x < bx + w && y >= by && y < by + h)) {
                        ring.add(new int[] {x, y});
                    }
                }
            }
            int[] surround = null;
            int most = -1;
            for (int[] at : ring) {
                int alike = 0;
                for (int[] other : ring) {
                    if (distance(colour(at[0], at[1]), colour(other[0], other[1])) <= SAME) {
                        alike++;
                    }
                }
                if (alike > most) {
                    most = alike;
                    surround = at;
                }
            }
            int ground = colour(surround[0], surround[1]);
            if (h > 1 && brow(bx, by, w, h) || !toned && by + h < SIDE && browOverEye(bx, by + h - 1, w)) {
                return null;
            }
            for (int y = by; y < by + h; y++) {
                for (int side : new int[] {bx - 1, bx + w}) {
                    if (side >= 0 && side < SIDE && continues(colour(side, y), bx, y, w, ground)) {
                        return null;
                    }
                }
            }
            for (int x : new int[] {bx, bx + w - 1}) {
                if (x == 0 || x == SIDE - 1) {
                    for (int y = by; y < by + h; y++) {
                        for (int next : new int[] {by - 1, by + h}) {
                            if (next >= 0 && next < SIDE && distance(colour(x, next), colour(x, y)) <= SAME
                                    && distance(colour(x, next), ground) >= CONTRAST) {
                                return null;
                            }
                        }
                    }
                }
            }
            double neighbours = 1;
            for (int[] at : ring) {
                int x = at[0];
                int y = at[1];
                boolean sideways = (x == bx - 1 || x == bx + w) && y >= by && y < by + h;
                boolean aboveOrBelow = (y == by - 1 || y == by + h) && x >= bx && x < bx + w;
                if ((sideways || aboveOrBelow) && !big(x, y) && distance(colour(x, y), ground) >= FEATURE) {
                    neighbours *= sideways ? SIDEWAYS : ABOVE_OR_BELOW;
                }
            }
            double contrast = 0;
            for (int y = by; y < by + h; y++) {
                for (int x = bx; x < bx + w; x++) {
                    int colour = colour(x, y);
                    double apart = distance(colour, ground);
                    int px = x;
                    int py = y;
                    if (apart < CONTRAST || ring.stream().anyMatch(at -> Math.abs(at[0] - px) + Math.abs(at[1] - py) <= (count == 1 ? 2 : 1)
                            && distance(colour, colour(at[0], at[1])) <= CHAIN)) {
                        return null;
                    }
                    contrast = Math.max(contrast, Math.min(1, apart / FULL_CONTRAST));
                }
            }
            int mean = (int) (red / count) << 16 | (int) (green / count) << 8 | (int) (blue / count);
            double score = contrast * contrast * most / ring.size() * neighbours;
            return new Box(bx, by, w, h, score, bx + (w - 1) / 2.0, by + (h - 1) / 2.0, mean, toned);
        }

        private boolean brow(int bx, int by, int w, int h) {
            for (int x = bx; x < bx + w; x++) {
                if (distance(colour(x, by), colour(bx, by)) > SAME) {
                    return false;
                }
            }
            int palest = -1;
            int darkest = Integer.MAX_VALUE;
            for (int y = by + 1; y < by + h; y++) {
                for (int x = bx; x < bx + w; x++) {
                    darkest = Math.min(darkest, luma(colour(x, y)));
                    if (pale(colour(x, y))) {
                        palest = Math.max(palest, luma(colour(x, y)));
                    }
                }
            }
            return palest >= 0 && (palest - darkest >= TWO_TONE || !pale(colour(bx, by)));
        }

        private boolean browOverEye(int bx, int y, int w) {
            int palest = -1;
            int darkest = Integer.MAX_VALUE;
            for (int x = bx; x < bx + w; x++) {
                if (distance(colour(x, y), colour(bx, y)) > SAME) {
                    return false;
                }
                int below = colour(x, y + 1);
                darkest = Math.min(darkest, luma(below));
                if (pale(below)) {
                    palest = Math.max(palest, luma(below));
                }
            }
            return palest >= 0 && palest - darkest >= TWO_TONE;
        }

        private boolean continues(int beside, int bx, int y, int w, int ground) {
            if (distance(beside, ground) < CONTRAST) {
                return false;
            }
            for (int x = bx; x < bx + w; x++) {
                if (distance(colour(x, y), beside) <= CONTINUED) {
                    return true;
                }
            }
            return false;
        }

        private boolean inside(int id, int bx, int by, int w, int h) {
            for (int i = 0; i < part.length; i++) {
                if (part[i] == id && (i % SIDE < bx || i % SIDE >= bx + w || i / SIDE < by || i / SIDE >= by + h)) {
                    return false;
                }
            }
            return true;
        }

        private void join(int a, int b) {
            part[root(a)] = root(b);
        }

        private int root(int i) {
            while (part[i] != i) {
                part[i] = part[part[i]];
                i = part[i];
            }
            return i;
        }
    }

    private static int blend(int over, int base, int alpha) {
        int red = ((over >> 16 & 0xFF) * alpha + (base >> 16 & 0xFF) * (255 - alpha)) / 255;
        int green = ((over >> 8 & 0xFF) * alpha + (base >> 8 & 0xFF) * (255 - alpha)) / 255;
        int blue = ((over & 0xFF) * alpha + (base & 0xFF) * (255 - alpha)) / 255;
        return red << 16 | green << 8 | blue;
    }

    private static boolean pale(int colour) {
        int high = Math.max(colour >> 16 & 0xFF, Math.max(colour >> 8 & 0xFF, colour & 0xFF));
        int low = Math.min(colour >> 16 & 0xFF, Math.min(colour >> 8 & 0xFF, colour & 0xFF));
        return high >= PALE && high - low <= GREY;
    }

    private static int luma(int colour) {
        return (299 * (colour >> 16 & 0xFF) + 587 * (colour >> 8 & 0xFF) + 114 * (colour & 0xFF)) / 1000;
    }

    private static double similarity(int a, int b) {
        return Math.max(0, 1 - distance(a, b) / 255);
    }

    private static double distance(int a, int b) {
        int red = (a >> 16 & 0xFF) - (b >> 16 & 0xFF);
        int green = (a >> 8 & 0xFF) - (b >> 8 & 0xFF);
        int blue = (a & 0xFF) - (b & 0xFF);
        return Math.sqrt(red * red + green * green + blue * blue);
    }
}

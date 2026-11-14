package my.celium.org.betterpets.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The pet book's winter dress: snow lying on top, icicles, holly and gently falling snow. */
final class Winter {
    private static final int SNOW = 0xFFF4F8FF;
    private static final int SNOW_TOP = 0xFFFFFFFF;
    private static final int SNOW_SHADE = 0xFFC9D8EC;
    private static final int ICE = 0xD0DCEBFA;
    private static final int ICE_TIP = 0x90E8F2FF;
    private static final int LEAF = 0xFF2E6B34;
    private static final int LEAF_DARK = 0xFF1D4A24;
    private static final int BERRY = 0xFFD8262E;
    private static final int BERRY_SHINE = 0xFFFF8A8A;
    private static final int FLAKES = 70;

    private Winter() {
    }

    /** A cheap, stable 0..1 random value for {@code n}. */
    private static float rand(int n) {
        n = (n << 13) ^ n;
        n = n * (n * n * 15731 + 789221) + 1376312589;
        return (n & 0x7FFFFFFF) / (float) 0x7FFFFFFF;
    }

    /** Snow lying along a top edge at {@code y}, from {@code x0} to {@code x1}, in soft lumps. */
    static void snowCap(GuiGraphicsExtractor gfx, int x0, int x1, int y) {
        for (int x = x0; x < x1; x++) {
            double wave = Math.sin(x * 0.31) + Math.sin(x * 0.11 + 1.7) * 0.8;
            int h = 2 + (int) Math.round((wave + 1.8) * 0.7 + rand(x) * 0.8);
            if (x == x0 || x == x1 - 1) {
                h = Math.max(1, h - 2);
            }
            gfx.fill(x, y - h, x + 1, y, SNOW);
            gfx.fill(x, y - h, x + 1, y - h + 1, SNOW_TOP);
            gfx.fill(x, y, x + 1, y + 1, SNOW_SHADE);
        }
    }

    /** Icicles hanging under a bottom edge at {@code y}; none where the ribbon hangs. */
    static void icicles(GuiGraphicsExtractor gfx, int x0, int x1, int y, int ribbonX) {
        int x = x0 + 4;
        int i = 0;
        while (x < x1 - 3) {
            if (x + 2 >= ribbonX - 2 && x <= ribbonX + 7) {
                x = ribbonX + 9;
                continue;
            }
            int len = 2 + (int) (rand(i * 7 + 3) * 5);
            gfx.fill(x, y, x + 2, y + Math.max(1, len - 2), ICE);
            gfx.fill(x, y + Math.max(1, len - 2), x + 1, y + len, ICE_TIP);
            x += 6 + (int) (rand(i * 13 + 1) * 7);
            i++;
        }
    }

    /** A sprig of holly: two leaves and three berries, its top left at ({@code x}, {@code y}). */
    static void holly(GuiGraphicsExtractor gfx, int x, int y) {
        String[] leaves = {
                ".XX....XX.",
                "XXXX..XXXX",
                ".XXXXXXXX.",
                "..XX..XX..",
        };
        for (int j = 0; j < leaves.length; j++) {
            for (int i = 0; i < leaves[j].length(); i++) {
                if (leaves[j].charAt(i) == 'X') {
                    gfx.fill(x + i, y + j, x + i + 1, y + j + 1, j == 0 || i == 0 ? LEAF : (j == 3 ? LEAF_DARK : LEAF));
                }
            }
        }
        int[][] berries = {{3, 2}, {5, 1}, {5, 3}};
        for (int[] b : berries) {
            gfx.fill(x + b[0], y + b[1], x + b[0] + 2, y + b[1] + 2, BERRY);
            gfx.fill(x + b[0], y + b[1], x + b[0] + 1, y + b[1] + 1, BERRY_SHINE);
        }
    }

    /** Snow drifting down the whole screen, behind the book. */
    static void snowfall(GuiGraphicsExtractor gfx, int width, int height) {
        double t = (System.currentTimeMillis() % 3_600_000L) / 1000.0;
        for (int i = 0; i < FLAKES; i++) {
            double speed = 9 + rand(i * 3 + 1) * 16;
            double y = (rand(i * 5 + 2) * (height + 20) + t * speed) % (height + 20) - 10;
            double x = rand(i * 11 + 7) * width + Math.sin(t * (0.5 + rand(i) * 0.6) + i) * 5;
            int size = rand(i * 17 + 5) < 0.25f ? 2 : 1;
            int alpha = 120 + (int) (rand(i * 19 + 9) * 110);
            gfx.fill((int) x, (int) y, (int) x + size, (int) y + size, alpha << 24 | 0xFFFFFF);
        }
    }
}

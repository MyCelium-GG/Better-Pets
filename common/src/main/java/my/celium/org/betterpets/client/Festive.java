package my.celium.org.betterpets.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The pet menu's Christmas dressing, all pixel art: a winter night landscape that fills the whole
 * menu (sky, northern lights, moon, snowy hills, pine woods, a cabin, a decorated tree and deep
 * snow), a string of lights, snow piled on edges, holly and slowly falling flakes.
 *
 * <p>Everything that stands still is painted once through {@link PixelArt} and drawn as one image.
 * Only the few things that move (twinkling bulbs and stars, the cabin's smoke and windows, the
 * falling snow) are drawn every frame. Client only.
 */
final class Festive {
    static final int BULB_RED = 0xFFFF4D5A;
    static final int BULB_GREEN = 0xFF4BE08A;
    static final int BULB_GOLD = 0xFFFFD25A;
    static final int BULB_BLUE = 0xFF6EC8FF;
    private static final int[] BULBS = {BULB_RED, BULB_GREEN, BULB_GOLD, BULB_BLUE};
    private static final int WIRE = 0xFF2B3A30;
    private static final int SNOW = 0xFFF4F8FF;
    private static final int SNOW_SHADE = 0xFFC9D6EA;

    private static final int SKY_TOP = 0xFF070D22;
    private static final int SKY_LOW = 0xFF24406B;
    private static final int HILL_FAR = 0xFF4E6890;
    private static final int HILL_FAR_TOP = 0xFF7E98BE;
    private static final int HILL_MID = 0xFFA9BCD8;
    private static final int HILL_MID_TOP = 0xFFD6E2F2;
    private static final int PINE_FAR = 0xFF1C3A44;
    private static final int PINE = 0xFF123A2C;
    private static final int PINE_LIGHT = 0xFF1E5640;
    private static final int TRUNK = 0xFF4A3020;

    /** How far the frame, snow and holly reach outside the menu's box. */
    private static final int MARGIN = 8;
    private static final int LIGHT_SPAN = 14;

    private static final String[] HOLLY = {
        "..GG....GG..",
        ".GGGG..GGGG.",
        "GGgGGG GGgGG",
        ".GGGGRRGGGG.",
        "..GG.RRR.GG.",
        ".....RR.....",
    };

    private Festive() {
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    // ================================================================ the whole scene

    /**
     * The menu's window: frame, landscape, string of lights, snow on top and holly, with the snow
     * starting at {@code ground}. The pet stands between {@code petX0} and {@code petX1}; the tree
     * and cabin keep out of its way.
     */
    static void scene(GuiGraphicsExtractor gfx, int x0, int y0, int x1, int y1, int ground, int petX0, int petX1) {
        String key = "scene:" + x0 + "," + y0 + "," + x1 + "," + y1 + "," + ground + "," + petX0 + "," + petX1;
        PixelArt.draw(gfx, key, x0 - MARGIN, y0 - MARGIN, x1 - x0 + MARGIN * 2, y1 - y0 + MARGIN * 2, p -> {
            frame(p, x0, y0, x1, y1);
            landscape(p, x0, y0, x1, y1, ground, petX0, petX1);
            lights(p, x0 + 4, x1 - 4, y0 + 1);
            snowCap(p, x0 - 2, x1 + 2, y0 - 2, 0);
            holly(p, x0 - 5, y0 - 6);
            holly(p, x1 - 8, y1 - 4);
        });
        PixelArt.Paint live = new PixelArt.Screen(gfx);
        twinkle(live, x0, y0, x1, ground);
        lightsTwinkle(live, x0 + 4, x1 - 4, y0 + 1);
        cabinLife(live, cabinX(petX1), cabinBase(x0, petX1, ground));
        christmasTree(live, petX0 + 2, ground + 3, 40, false);
    }

    /** A see-through panel with a 1-pixel edge and snow lying on top. */
    static void panel(GuiGraphicsExtractor gfx, int x0, int y0, int x1, int y1, int fill, int edge) {
        String key = "panel:" + x0 + "," + y0 + "," + x1 + "," + y1 + "," + fill + "," + edge;
        PixelArt.draw(gfx, key, x0, y0 - 4, x1 - x0, y1 - y0 + 4, p -> {
            Ui.framed(p, x0, y0, x1, y1, fill, edge);
            snowCap(p, x0, x1, y0, 41);
        });
    }

    /** Snow lying on top of a box from {@code x0} to {@code x1} whose top edge is {@code y}. */
    static void snowCap(GuiGraphicsExtractor gfx, int x0, int x1, int y, int seed) {
        PixelArt.draw(gfx, "cap:" + x0 + "," + x1 + "," + y + "," + seed, x0, y - 4, x1 - x0, 9,
                p -> snowCap(p, x0, x1, y, seed));
    }

    private static void frame(PixelArt.Paint p, int x0, int y0, int x1, int y1) {
        p.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, Ui.FRAME);
        p.fill(x0 - 1, y0 - 1, x1 + 1, y0, Ui.FRAME_LIGHT);
        p.fill(x0 - 1, y1, x1 + 1, y1 + 1, Ui.FRAME_LIGHT);
        p.fill(x0 - 1, y0, x0, y1, Ui.FRAME_LIGHT);
        p.fill(x1, y0, x1 + 1, y1, Ui.FRAME_LIGHT);
    }

    private static void landscape(PixelArt.Paint p, int x0, int y0, int x1, int y1, int ground, int petX0, int petX1) {
        p.gradient(x0, y0, x1, ground + 4, SKY_TOP, SKY_LOW);
        aurora(p, x0, x1, y0 + 26);
        for (int i = 0; i < starCount(x0, x1); i++) {
            int sx = starX(i, x0, x1);
            int sy = starY(i, y0, ground);
            p.fill(sx, sy, sx + 1, sy + 1, 0xDDFFFFFF);
            if (i % 9 == 0) {
                p.fill(sx - 1, sy, sx, sy + 1, 0x66FFFFFF);
                p.fill(sx + 1, sy, sx + 2, sy + 1, 0x66FFFFFF);
                p.fill(sx, sy - 1, sx + 1, sy, 0x66FFFFFF);
                p.fill(sx, sy + 1, sx + 1, sy + 2, 0x66FFFFFF);
            }
        }
        moon(p, petX1 - 20, y0 + 52);

        int farBase = ground - 26;
        for (int x = x0; x < x1; x++) {
            int h = (int) Math.round(Math.sin((x - x0) / 23.0) * 5 + Math.sin((x - x0) / 9.0 + 1.3) * 2);
            int top = farBase - 6 + h;
            p.fill(x, top, x + 1, ground + 4, HILL_FAR);
            p.fill(x, top, x + 1, top + 1, HILL_FAR_TOP);
        }
        for (int i = 0; i < (x1 - x0) / 9; i++) {
            int tx = x0 + 4 + hash(i * 19 + 7) % Math.max(1, x1 - x0 - 8);
            int h = 7 + hash(i * 5 + 3) % 6;
            int base = farBase - 2 + (int) Math.round(Math.sin((tx - x0) / 23.0) * 5);
            pine(p, tx, base, h, PINE_FAR, HILL_FAR_TOP);
        }

        int midBase = ground - 10;
        for (int x = x0; x < x1; x++) {
            int h = (int) Math.round(Math.sin((x - x0) / 31.0 + 2) * 4 + Math.sin((x - x0) / 12.0) * 1.5);
            int top = midBase - 4 + h;
            p.fill(x, top, x + 1, ground + 4, HILL_MID);
            p.fill(x, top, x + 1, top + 1, HILL_MID_TOP);
        }
        int cabinX = cabinX(petX1);
        cabin(p, cabinX, cabinBase(x0, petX1, ground));
        for (int i = 0; i < (x1 - x0) / 22; i++) {
            int tx = x0 + 6 + hash(i * 41 + 2) % Math.max(1, x1 - x0 - 12);
            if (tx > cabinX - 4 && tx < cabinX + 26) {
                continue;
            }
            int h = 14 + hash(i * 11 + 9) % 9;
            int base = midBase + (int) Math.round(Math.sin((tx - x0) / 31.0 + 2) * 4);
            pine(p, tx, base, h, PINE, 0xFFEAF1FB);
        }

        christmasTree(p, petX0 + 2, ground + 3, 40, true);

        for (int x = x0; x < x1; x++) {
            double wave = Math.sin((x - x0) / 14.0) * 1.6 + Math.sin((x - x0) / 5.0) * 0.5;
            int top = ground + (int) Math.round(wave);
            p.gradient(x, top, x + 1, y1, SNOW, 0xFFDCE6F5);
            p.fill(x, top, x + 1, top + 1, 0xFFFFFFFF);
            int n = hash(x * 3 + 1);
            if (n % 11 == 0) {
                int sy = top + 4 + n % Math.max(1, y1 - top - 6);
                p.fill(x, sy, x + 1, sy + 1, SNOW_SHADE);
            }
            if (n % 37 == 0) {
                int sy = top + 2 + (n >> 4) % Math.max(1, y1 - top - 4);
                p.fill(x, sy, x + 1, sy + 1, 0xFFBFE3FF);
            }
        }
    }

    /** Green and violet curtains high in the sky. */
    private static void aurora(PixelArt.Paint p, int x0, int x1, int y) {
        for (int x = x0; x < x1; x++) {
            double wave = Math.sin((x - x0) / 26.0) * 6 + Math.sin((x - x0) / 9.0) * 2;
            int top = y + (int) Math.round(wave);
            double strength = 0.5 + 0.5 * Math.sin((x - x0) / 40.0);
            int a = (int) Math.round(18 + 34 * strength);
            p.gradient(x, top - 10, x + 1, top, 0x008A6CFF, ((a / 2) << 24) | 0x8A6CFF);
            p.gradient(x, top, x + 1, top + 14, (a << 24) | 0x3CF0A0, 0x003CF0A0);
        }
    }

    private static int starCount(int x0, int x1) {
        return Math.max(10, (x1 - x0) / 7);
    }

    private static int starX(int i, int x0, int x1) {
        return x0 + 2 + hash(i * 7 + 1) % Math.max(1, x1 - x0 - 4);
    }

    private static int starY(int i, int y0, int ground) {
        return y0 + 2 + hash(i * 13 + 5) % Math.max(1, ground - 30 - y0 - 2);
    }

    /** Dims a few stars at a time so the sky twinkles. */
    private static void twinkle(PixelArt.Paint p, int x0, int y0, int x1, int ground) {
        long t = now() / 700;
        for (int i = 0; i < starCount(x0, x1); i++) {
            if ((t + i) % 7 != 0) {
                continue;
            }
            int sx = starX(i, x0, x1);
            int sy = starY(i, y0, ground);
            float f = Math.clamp((sy - y0) / (float) Math.max(1, ground + 4 - y0), 0f, 1f);
            p.fill(sx, sy, sx + 1, sy + 1, PixelArt.lerp(SKY_TOP, SKY_LOW, f) & 0xFFFFFF | 0xAA000000);
        }
    }

    /** A full moon with a soft glow and two craters. */
    private static void moon(PixelArt.Paint p, int cx, int cy) {
        disc(p, cx, cy, 11, 0x14FFF6D8);
        disc(p, cx, cy, 8, 0x22FFF6D8);
        disc(p, cx, cy, 6, 0xFFF7F1DC);
        p.fill(cx - 3, cy - 2, cx - 1, cy, 0xFFDCD3B8);
        p.fill(cx + 1, cy + 2, cx + 3, cy + 3, 0xFFDCD3B8);
        p.fill(cx + 2, cy - 4, cx + 3, cy - 3, 0xFFE6DEC4);
    }

    private static void disc(PixelArt.Paint p, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.floor(Math.sqrt(r * r - dy * dy + 0.5));
            p.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    /** A pine in three tiers with snow on each tier's top; {@code base} is where the trunk meets the ground. */
    private static void pine(PixelArt.Paint p, int cx, int base, int h, int color, int snow) {
        p.fill(cx, base - 2, cx + 1, base, TRUNK);
        int body = h - 2;
        int tiers = 3;
        int tierH = Math.max(2, body / tiers + 1);
        for (int tier = 0; tier < tiers; tier++) {
            int tierTop = base - 2 - body + tier * (body / tiers);
            for (int r = 0; r < tierH; r++) {
                int half = (r + tier + 1) * 2 / 3;
                int y = tierTop + r;
                p.fill(cx - half, y, cx + half + 1, y + 1, r == 0 || (r == 1 && half > 0) ? snow : color);
            }
        }
    }

    /**
     * A Christmas tree with bulbs and a gold star. With {@code body} it paints the tree; without,
     * only the bulbs and star, which change colour over time.
     */
    private static void christmasTree(PixelArt.Paint p, int x, int base, int h, boolean body) {
        int cx = x + h / 3;
        int rows = h - 6;
        long t = now() / 350;
        if (body) {
            p.fill(cx - 1, base - 4, cx + 2, base, TRUNK);
        }
        int bulb = 0;
        for (int r = 0; r < rows; r++) {
            int y = base - 4 - rows + r;
            int tierRow = r % (rows / 4);
            int half = 1 + r / 3 + tierRow / 2;
            if (body) {
                p.fill(cx - half, y, cx + half + 1, y + 1, r % 2 == 0 ? PINE : PINE_LIGHT);
                if (tierRow == 0 && r > 0) {
                    p.fill(cx - half, y, cx - half + 2, y + 1, 0xFFEAF1FB);
                    p.fill(cx + half - 1, y, cx + half + 1, y + 1, 0xFFEAF1FB);
                }
            } else if (r > 3 && r % 4 == 2) {
                for (int bx = cx - half + 2; bx < cx + half - 1; bx += 4) {
                    int color = BULBS[(bulb + (int) (t % BULBS.length)) % BULBS.length];
                    int px = bx + (r / 4) % 2;
                    p.fill(px, y, px + 1, y + 1, color);
                    bulb++;
                }
            }
        }
        if (!body) {
            int sy = base - 4 - rows - 3;
            int star = (t % 6 == 0) ? 0xFFFFF2B0 : BULB_GOLD;
            p.fill(cx, sy - 1, cx + 1, sy + 4, star);
            p.fill(cx - 2, sy + 1, cx + 3, sy + 2, star);
            p.fill(cx - 1, sy, cx + 2, sy + 3, star);
        }
    }

    private static int cabinX(int petX1) {
        return petX1 - 30;
    }

    private static int cabinBase(int x0, int petX1, int ground) {
        return ground - 9 + (int) Math.round(Math.sin((cabinX(petX1) + 11 - x0) / 31.0 + 2) * 4);
    }

    /** A small log cabin with warm windows. */
    private static void cabin(PixelArt.Paint p, int x, int base) {
        int w = 22;
        int wallTop = base - 9;
        p.fill(x, wallTop, x + w, base, 0xFF5A3A26);
        for (int y = wallTop + 1; y < base; y += 2) {
            p.fill(x, y, x + w, y + 1, 0xFF4A2E1E);
        }
        p.fill(x + 13, wallTop - 9, x + 16, wallTop - 2, 0xFF6B6E78);
        p.fill(x + 13, wallTop - 10, x + 16, wallTop - 9, SNOW);
        for (int r = 0; r < 7; r++) {
            int y = wallTop - 7 + r;
            p.fill(x + w / 2 - 2 - r * 2, y, x + w / 2 + 2 + r * 2, y + 1, r < 2 || r % 2 == 0 ? SNOW : SNOW_SHADE);
        }
        p.fill(x + 3, wallTop + 2, x + 8, wallTop + 6, 0x55FFC060);
        p.fill(x + 4, wallTop + 3, x + 7, wallTop + 6, 0xFFFFCF6A);
        p.fill(x + 14, wallTop + 2, x + 19, wallTop + 6, 0x55FFC060);
        p.fill(x + 15, wallTop + 3, x + 18, wallTop + 6, 0xFFFFCF6A);
        p.fill(x + 9, wallTop + 3, x + 12, base, 0xFF3A2416);
    }

    /** The cabin's flickering windows and the smoke from its chimney. */
    private static void cabinLife(PixelArt.Paint p, int x, int base) {
        int wallTop = base - 9;
        if ((now() / 900) % 5 == 0) {
            p.fill(x + 4, wallTop + 3, x + 7, wallTop + 6, 0xFFFFB347);
            p.fill(x + 15, wallTop + 3, x + 18, wallTop + 6, 0xFFFFB347);
        }
        double t = now() / 1000.0;
        for (int i = 0; i < 4; i++) {
            double life = (t * 0.5 + i / 4.0) % 1.0;
            int px = x + 14 + (int) Math.round(Math.sin(t + i) * 2 + life * 5);
            int py = wallTop - 12 - (int) Math.round(life * 14);
            p.fill(px, py, px + 2, py + 2, Ui.fade(0xFFCFD8E6, 0.6f * (1f - (float) life)));
        }
    }

    // ================================================================ lights, snow, holly

    /** A sagging wire with coloured bulbs. */
    private static void lights(PixelArt.Paint p, int x0, int x1, int y) {
        int index = 0;
        for (int x = x0; x < x1; x += LIGHT_SPAN, index++) {
            for (int dx = 0; dx < LIGHT_SPAN && x + dx < x1; dx++) {
                int sag = (int) Math.round(Math.sin(dx / (double) LIGHT_SPAN * Math.PI) * 3);
                p.fill(x + dx, y + sag, x + dx + 1, y + sag + 1, WIRE);
            }
            if (x + LIGHT_SPAN / 2 + 2 >= x1) {
                break;
            }
            int bx = x + LIGHT_SPAN / 2 - 1;
            int by = y + 3;
            int color = BULBS[index % BULBS.length];
            p.fill(bx - 1, by, bx + 4, by + 5, Ui.fade(color, 0.35f));
            p.fill(bx + 1, by - 1, bx + 2, by, WIRE);
            p.fill(bx, by, bx + 3, by + 4, color);
            p.fill(bx, by, bx + 1, by + 1, 0xFFFFFFFF);
        }
    }

    /** Turns one bulb in five dark at a time, so the string twinkles. */
    private static void lightsTwinkle(PixelArt.Paint p, int x0, int x1, int y) {
        long t = now() / 400;
        int index = 0;
        for (int x = x0; x < x1 && x + LIGHT_SPAN / 2 + 2 < x1; x += LIGHT_SPAN, index++) {
            if ((index + t) % 5 != 0) {
                continue;
            }
            int bx = x + LIGHT_SPAN / 2 - 1;
            int by = y + 3;
            p.fill(bx, by, bx + 3, by + 4, darker(BULBS[index % BULBS.length]));
        }
    }

    /** Snow lying on top of a box: a white band with uneven drifts hanging over the edge. */
    private static void snowCap(PixelArt.Paint p, int x0, int x1, int y, int seed) {
        p.fill(x0 + 2, y - 2, x1 - 2, y, SNOW);
        p.fill(x0 + 1, y - 1, x1 - 1, y + 1, SNOW);
        for (int x = x0 + 1; x < x1 - 1; x++) {
            int h = drift(x + seed);
            if (h > 0) {
                p.fill(x, y + 1, x + 1, y + 1 + h, SNOW);
                p.fill(x, y + 1 + h, x + 1, y + 2 + h, SNOW_SHADE);
            }
            int bump = bump(x + seed);
            if (bump > 0) {
                p.fill(x, y - 2 - bump, x + 1, y - 2, SNOW);
            }
        }
    }

    /** How far snow hangs down over the edge at column {@code x}: mostly 0, sometimes 1 to 3. */
    private static int drift(int x) {
        int r = hash(x) % 13;
        return r < 6 ? 0 : r < 10 ? 1 : r < 12 ? 2 : 3;
    }

    /** Small heaps on top of the snow band. */
    private static int bump(int x) {
        int band = Math.floorMod(x, 23);
        return band < 7 ? (band == 0 || band == 6 ? 1 : 2) : 0;
    }

    /** A holly sprig: two leaves and three berries. */
    private static void holly(PixelArt.Paint p, int x, int y) {
        for (int row = 0; row < HOLLY.length; row++) {
            String line = HOLLY[row];
            for (int col = 0; col < line.length(); col++) {
                int color = switch (line.charAt(col)) {
                    case 'G' -> 0xFF2E9E5B;
                    case 'g' -> 0xFF62D08F;
                    case 'R' -> (row == 3 && col == 5) ? 0xFFFF8A8A : 0xFFE5333F;
                    default -> 0;
                };
                if (color != 0) {
                    p.fill(x + col, y + row, x + col + 1, y + row + 1, color);
                }
            }
        }
    }

    /**
     * Flakes drifting down inside a box, one rectangle each. {@code count} sets how many;
     * {@code alpha} keeps them faint. Positions come from the clock and the flake's number.
     */
    static void snowfall(GuiGraphicsExtractor gfx, int x0, int y0, int x1, int y1, int count, float alpha, int seed) {
        int w = x1 - x0;
        int h = y1 - y0;
        if (w <= 4 || h <= 4) {
            return;
        }
        double t = now() / 1000.0;
        for (int i = 0; i < count; i++) {
            int n = hash(i * 31 + seed);
            double speed = 6 + n % 9;
            double y = (n % h + t * speed) % h;
            double sway = Math.sin(t * (0.6 + (n % 5) * 0.15) + i) * 3;
            int x = x0 + (int) Math.floorMod((long) (hash(i * 17 + seed) % w + sway), (long) w);
            int py = y0 + (int) y;
            int size = n % 3 == 0 ? 2 : 1;
            int color = Ui.fade(0xFFFFFFFF, alpha * (0.5f + (n % 4) * 0.15f));
            gfx.fill(x, py, Math.min(x1, x + size), Math.min(y1, py + size), color);
        }
    }

    private static int darker(int argb) {
        int r = (argb >> 16 & 0xFF) / 2;
        int g = (argb >> 8 & 0xFF) / 2;
        int b = (argb & 0xFF) / 2;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int hash(int x) {
        int h = x * 0x45D9F3B;
        h = (h ^ (h >>> 16)) * 0x45D9F3B;
        h ^= h >>> 16;
        return h & 0x7FFFFFFF;
    }
}

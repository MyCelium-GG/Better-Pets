package my.celium.org.betterpets.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The pet menu's own look: square pixel boxes in winter-night blues with gold and Christmas red,
 * and a pixel heart. The landscape behind everything is in {@link Festive}. Nothing here comes
 * from Mycel or vanilla's widget sprites. Client only.
 */
final class Ui {
    static final int SHADE = 0x88000000;
    static final int FRAME = 0xFF0A1020;
    static final int FRAME_LIGHT = 0xFF3C5C86;
    /** The see-through panel the facts sit on, so text stays readable over the landscape. */
    static final int PANEL = 0xEC0B1424;
    static final int PANEL_EDGE = 0xFF34507A;

    static final int TEXT = 0xFFF3F6FB;
    static final int DIM = 0xFFB4C4DC;
    static final int FAINT = 0xFF7F95B6;
    static final int ACCENT = 0xFFFFD25A;
    static final int ACCENT_SOFT = 0x40FFD25A;
    static final int ACCENT_TEXT = 0xFF1C1505;
    /** Christmas red: the main button. */
    static final int PRIMARY = 0xFFC92A37;
    static final int PRIMARY_HOT = 0xFFE23A47;
    static final int PRIMARY_EDGE = 0xFF6E1219;
    static final int GOOD = 0xFF6EE7A0;
    static final int BAD = 0xFFFF7A7A;

    static final int BUTTON = 0xE8102036;
    static final int BUTTON_HOVER = 0xF01B3352;
    static final int BUTTON_EDGE = 0xFF34507A;
    static final int BUTTON_EDGE_HOT = 0xFF6F95C8;
    static final int OFF = 0xC00B1220;
    static final int OFF_TEXT = 0xFF4E5E78;

    static final int HEART = 0xFFFF5A64;
    static final int HEART_SHINE = 0xFFFF9AA0;
    static final int HEART_EMPTY = 0xFF2A3A55;

    /** Heart shape, 9 x 8, one string per row. */
    private static final String[] HEART_ROWS = {
        ".XX...XX.",
        "XXXX.XXXX",
        "XXXXXXXXX",
        "XXXXXXXXX",
        ".XXXXXXX.",
        "..XXXXX..",
        "...XXX...",
        "....X....",
    };
    static final int HEART_W = 9;
    static final int HEART_H = 8;

    private Ui() {
    }

    /** A square box with a 1-pixel edge. */
    static void framed(GuiGraphicsExtractor gfx, int x0, int y0, int x1, int y1, int fill, int edge) {
        framed(new PixelArt.Screen(gfx), x0, y0, x1, y1, fill, edge);
    }

    static void framed(PixelArt.Paint p, int x0, int y0, int x1, int y1, int fill, int edge) {
        p.fill(x0, y0, x1, y0 + 1, edge);
        p.fill(x0, y1 - 1, x1, y1, edge);
        p.fill(x0, y0 + 1, x0 + 1, y1 - 1, edge);
        p.fill(x1 - 1, y0 + 1, x1, y1 - 1, edge);
        p.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill);
    }

    /** One heart; {@code fill} is 0 (empty), 1 (half) or 2 (full). Painted once, then drawn as an image. */
    static void heart(GuiGraphicsExtractor gfx, int x, int y, int fill) {
        PixelArt.draw(gfx, "heart:" + fill, x, y, HEART_W, HEART_H, p -> heart(p, x, y, fill));
    }

    private static void heart(PixelArt.Paint gfx, int x, int y, int fill) {
        for (int row = 0; row < HEART_H; row++) {
            String line = HEART_ROWS[row];
            for (int col = 0; col < HEART_W; col++) {
                if (line.charAt(col) != 'X') {
                    continue;
                }
                boolean filled = fill == 2 || (fill == 1 && col < 5);
                int color = !filled ? HEART_EMPTY : (row == 1 && (col == 1 || col == 2)) ? HEART_SHINE : HEART;
                gfx.fill(x + col, y + row, x + col + 1, y + row + 1, color);
            }
        }
    }

    /** {@code text} cut with "..." to fit {@code width} pixels. */
    static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        String dots = "...";
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end) + dots) > width) {
            end--;
        }
        return text.substring(0, end) + dots;
    }

    /** {@code argb} with its alpha multiplied by {@code alpha} (0 to 1). */
    static int fade(int argb, float alpha) {
        int a = Math.round((argb >>> 24) * Math.clamp(alpha, 0f, 1f));
        return (a << 24) | (argb & 0xFFFFFF);
    }
}

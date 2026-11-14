package my.celium.org.betterpets.client;

import net.minecraft.client.gui.Font;

/** Small text helpers for the pet book. */
final class Ui {
    private Ui() {
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

package my.celium.org.betterpets.client;

import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * The pet book's cover. Every colour is made in game from the vanilla book page (so resource packs
 * that change the book change it too): the leather is recoloured, the red stitching becomes thread
 * in a matching colour, and the outer corners get metal caps and a small paw print. Classic leaves
 * the page as it is. The choice is the player's own and lives in {@code config/betterpets-client.properties}.
 */
public enum BookStyle {
    MIDNIGHT("Midnight", 0.62f, 0.45f, 1.05f, 0xE2B848, 0xB22228),
    FOREST("Forest", 0.35f, 0.40f, 1.0f, 0xEEE2C4, 0xD6A83A),
    LEATHER("Leather", 0.04f, 0.55f, 1.0f, 0xE2B848, 0xB22228),
    CLASSIC("Classic", -1f, 0f, 1f, 0, 0),
    /** Frosty ice-blue leather; the screen adds snow, icicles, holly and falling snow. */
    WINTER("Winter", 0.57f, 0.38f, 1.9f, 0xE6EEF6, 0xC0262E);

    static final Identifier VANILLA = Identifier.withDefaultNamespace("textures/gui/book.png");
    private static final int GOLD = 0xD6B050;
    private static final String[] PAW = {".X.X.", "X...X", "..X..", ".XXX.", ".XXX."};
    private static final Map<BookStyle, Identifier> BAKED = new EnumMap<>(BookStyle.class);
    private static BookStyle current;

    final String label;
    private final float hue;
    private final float sat;
    private final float light;
    private final int thread;
    /** The bookmark ribbon's colour, or 0 for none. */
    final int ribbon;

    BookStyle(String label, float hue, float sat, float light, int thread, int ribbon) {
        this.label = label;
        this.hue = hue;
        this.sat = sat;
        this.light = light;
        this.thread = thread;
        this.ribbon = ribbon;
    }

    /** Whether the screen dresses the book for winter. */
    boolean winter() {
        return this == WINTER;
    }

    /** The cover colour, for the swatch in the settings. */
    int swatch() {
        if (this == CLASSIC) {
            return 0xFF7F3924;
        }
        return 0xFF000000 | hls(hue, Math.min(0.62f, 0.30f * light), sat);
    }

    /** The texture to draw the pages with: the vanilla book for Classic, else the baked copy. */
    Identifier texture() {
        if (this == CLASSIC) {
            return VANILLA;
        }
        Identifier id = BAKED.get(this);
        if (id == null) {
            id = bake();
            BAKED.put(this, id);
        }
        return id;
    }

    private Identifier bake() {
        Minecraft mc = Minecraft.getInstance();
        try (InputStream in = mc.getResourceManager().getResource(VANILLA).orElseThrow().open()) {
            NativeImage image = NativeImage.read(in);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    image.setPixel(x, y, recolor(image.getPixel(x, y)));
                }
            }
            caps(image);
            paw(image);
            Identifier id = Identifier.fromNamespaceAndPath("betterpets", "pet_book/" + name().toLowerCase());
            mc.getTextureManager().register(id, new DynamicTexture(() -> "Better Pets book " + label, image));
            return id;
        } catch (Exception e) {
            LogUtils.getLogger().warn("Better Pets could not make the {} book cover, using the vanilla book", label, e);
            return VANILLA;
        }
    }

    private int recolor(int argb) {
        int a = argb >>> 24;
        if (a == 0) {
            return argb;
        }
        int r = argb >> 16 & 0xFF;
        int g = argb >> 8 & 0xFF;
        int b = argb & 0xFF;
        float lum = 0.3f * r + 0.59f * g + 0.11f * b;
        if (r > 140 && g < 60 && b < 50) {
            // The red stitching becomes thread, shaded like the original stitch.
            return a << 24 | scale(thread, lum / 120f);
        }
        if (lum >= 150) {
            return argb;
        }
        return a << 24 | hls(hue, Math.min(1f, lum / 255f * light), sat);
    }

    /** Metal caps on the page's outer corners (its right side in the texture). */
    private static void caps(NativeImage image) {
        int x0 = 165;
        int[] ys = {1, 180};
        for (int cy : ys) {
            int sy = cy == 1 ? 1 : -1;
            for (int d = 0; d < 7; d++) {
                for (int e = 0; d + e < 7; e++) {
                    int x = x0 - d;
                    int y = cy + sy * e;
                    if ((image.getPixel(x, y) >>> 24) == 0) {
                        continue;
                    }
                    float k = d + e < 3 ? 1.15f : d + e < 5 ? 0.95f : 0.75f;
                    image.setPixel(x, y, 0xFF000000 | scale(GOLD, k));
                }
            }
        }
    }

    /** A small, faint paw print in the bottom outer corner of the page. */
    private static void paw(NativeImage image) {
        int ox = 147;
        int oy = 163;
        for (int j = 0; j < PAW.length; j++) {
            for (int i = 0; i < PAW[j].length(); i++) {
                if (PAW[j].charAt(i) == 'X') {
                    int p = image.getPixel(ox + i, oy + j);
                    image.setPixel(ox + i, oy + j, 0xFF000000 | mix(p & 0xFFFFFF, 0x7A5F46, 0.35f));
                }
            }
        }
    }

    private static int scale(int rgb, float k) {
        int r = Math.clamp(Math.round((rgb >> 16 & 0xFF) * k), 0, 255);
        int g = Math.clamp(Math.round((rgb >> 8 & 0xFF) * k), 0, 255);
        int b = Math.clamp(Math.round((rgb & 0xFF) * k), 0, 255);
        return r << 16 | g << 8 | b;
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round((a >> 16 & 0xFF) * (1 - t) + (b >> 16 & 0xFF) * t);
        int g = Math.round((a >> 8 & 0xFF) * (1 - t) + (b >> 8 & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return r << 16 | g << 8 | bl;
    }

    /** HLS to 0xRRGGBB. */
    private static int hls(float h, float l, float s) {
        float q = l < 0.5f ? l * (1 + s) : l + s - l * s;
        float p = 2 * l - q;
        int r = Math.round(channel(p, q, h + 1f / 3) * 255);
        int g = Math.round(channel(p, q, h) * 255);
        int b = Math.round(channel(p, q, h - 1f / 3) * 255);
        return r << 16 | g << 8 | b;
    }

    private static float channel(float p, float q, float t) {
        t = t < 0 ? t + 1 : t > 1 ? t - 1 : t;
        if (t < 1f / 6) {
            return p + (q - p) * 6 * t;
        }
        if (t < 0.5f) {
            return q;
        }
        if (t < 2f / 3) {
            return p + (q - p) * (2f / 3 - t) * 6;
        }
        return p;
    }

    // ---------------------------------------------------------------- the player's choice

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("betterpets-client.properties");
    }

    /**
     * The player's cover. Until they pick one it follows the season: Winter from 15 November to
     * 15 January, Midnight the rest of the year.
     */
    static BookStyle current() {
        if (current == null) {
            current = seasonal();
            try (Reader reader = Files.newBufferedReader(file())) {
                Properties props = new Properties();
                props.load(reader);
                String chosen = props.getProperty("bookStyle");
                if (chosen != null) {
                    current = BookStyle.valueOf(chosen);
                }
            } catch (Exception ignored) {
                // No file yet, or an unknown name: keep the seasonal default.
            }
        }
        return current;
    }

    private static BookStyle seasonal() {
        java.time.LocalDate today = java.time.LocalDate.now();
        int month = today.getMonthValue();
        int day = today.getDayOfMonth();
        boolean winter = month == 12 || (month == 11 && day >= 15) || (month == 1 && day <= 15);
        return winter ? WINTER : MIDNIGHT;
    }

    static void choose(BookStyle style) {
        current = style;
        try {
            Files.createDirectories(file().getParent());
            try (Writer writer = Files.newBufferedWriter(file())) {
                Properties props = new Properties();
                props.setProperty("bookStyle", style.name());
                props.store(writer, "Better Pets: your own pet book settings");
            }
        } catch (Exception e) {
            LogUtils.getLogger().warn("Better Pets could not save the book colour", e);
        }
    }
}

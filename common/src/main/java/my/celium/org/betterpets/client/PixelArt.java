package my.celium.org.betterpets.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Paints pixel art once into a texture and then draws it as one image. The menu's scenery is
 * thousands of one-pixel rectangles; drawing those every frame made the menu lag, so everything
 * that does not move is painted here and cached by a key that names its size and place.
 * Client only.
 */
final class PixelArt {
    /** Something the menu's pixel art can be painted on: the screen or an image. */
    interface Paint {
        void fill(int x0, int y0, int x1, int y1, int argb);

        /** A box whose colour runs from {@code top} to {@code bottom}. */
        void gradient(int x0, int y0, int x1, int y1, int top, int bottom);
    }

    /** Paints straight to the screen, for the few things that move. */
    record Screen(GuiGraphicsExtractor gfx) implements Paint {
        @Override
        public void fill(int x0, int y0, int x1, int y1, int argb) {
            gfx.fill(x0, y0, x1, y1, argb);
        }

        @Override
        public void gradient(int x0, int y0, int x1, int y1, int top, int bottom) {
            gfx.fillGradient(x0, y0, x1, y1, top, bottom);
        }
    }

    private static final int MAX_CACHED = 40;
    private static final Map<String, Baked> CACHE = new LinkedHashMap<>(16, 0.75f, true);
    private static int next;

    private record Baked(Identifier id, DynamicTexture texture, int w, int h) {
    }

    private PixelArt() {
    }

    /**
     * Draws the art named {@code key} with its top-left corner at ({@code x}, {@code y}), painting
     * it first if it is not cached. {@code painter} paints in screen coordinates; anything outside
     * the {@code w} x {@code h} box is cut off.
     */
    static void draw(GuiGraphicsExtractor gfx, String key, int x, int y, int w, int h, Consumer<Paint> painter) {
        if (w <= 0 || h <= 0) {
            return;
        }
        Baked baked = CACHE.get(key);
        if (baked == null || baked.w != w || baked.h != h) {
            baked = bake(key, x, y, w, h, painter);
        }
        gfx.blit(RenderPipelines.GUI_TEXTURED, baked.id, x, y, 0f, 0f, w, h, w, h);
    }

    private static Baked bake(String key, int x, int y, int w, int h, Consumer<Paint> painter) {
        NativeImage image = new NativeImage(w, h, true);
        painter.accept(new Image(image, x, y));
        Identifier id = Identifier.fromNamespaceAndPath("betterpets", "menu_art/" + next++);
        DynamicTexture texture = new DynamicTexture(() -> "Better Pets menu art", image);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        Baked baked = new Baked(id, texture, w, h);
        Baked old = CACHE.put(key, baked);
        if (old != null) {
            release(old);
        }
        while (CACHE.size() > MAX_CACHED) {
            var eldest = CACHE.entrySet().iterator();
            release(eldest.next().getValue());
            eldest.remove();
        }
        return baked;
    }

    private static void release(Baked baked) {
        Minecraft.getInstance().getTextureManager().release(baked.id);
    }

    /** Paints into an image, blending see-through colours the way the screen would. */
    private record Image(NativeImage image, int ox, int oy) implements Paint {
        @Override
        public void fill(int x0, int y0, int x1, int y1, int argb) {
            int ax = Math.max(0, x0 - ox);
            int ay = Math.max(0, y0 - oy);
            int bx = Math.min(image.getWidth(), x1 - ox);
            int by = Math.min(image.getHeight(), y1 - oy);
            for (int y = ay; y < by; y++) {
                for (int x = ax; x < bx; x++) {
                    put(x, y, argb);
                }
            }
        }

        @Override
        public void gradient(int x0, int y0, int x1, int y1, int top, int bottom) {
            int rows = Math.max(1, y1 - y0);
            for (int y = y0; y < y1; y++) {
                float f = rows == 1 ? 0f : (y - y0) / (float) (rows - 1);
                fill(x0, y, x1, y + 1, lerp(top, bottom, f));
            }
        }

        private void put(int x, int y, int src) {
            int sa = src >>> 24;
            if (sa == 0) {
                return;
            }
            if (sa == 255) {
                image.setPixel(x, y, src);
                return;
            }
            int dst = image.getPixel(x, y);
            int da = dst >>> 24;
            float a = sa / 255f;
            float b = da / 255f * (1f - a);
            float out = a + b;
            int r = Math.round(((src >> 16 & 0xFF) * a + (dst >> 16 & 0xFF) * b) / out);
            int g = Math.round(((src >> 8 & 0xFF) * a + (dst >> 8 & 0xFF) * b) / out);
            int bl = Math.round(((src & 0xFF) * a + (dst & 0xFF) * b) / out);
            image.setPixel(x, y, Math.round(out * 255f) << 24 | r << 16 | g << 8 | bl);
        }
    }

    static int lerp(int from, int to, float f) {
        int a = Math.round((from >>> 24) + ((to >>> 24) - (from >>> 24)) * f);
        int r = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * f);
        int g = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * f);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * f);
        return a << 24 | r << 16 | g << 8 | b;
    }
}

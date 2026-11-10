package my.celium.org.betterpets.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * The pet menu's button: a square pixel box with snow lying on top, Christmas red when it is the
 * main action. Keyboard focus and narration work like any vanilla button. Client only.
 */
final class SnowButton extends AbstractButton {
    static final int HEIGHT = 20;

    private final Runnable action;
    private boolean primary;

    SnowButton(int x, int y, int width, Component label, Runnable action) {
        super(x, y, width, HEIGHT, label);
        this.action = action;
    }

    SnowButton primary(boolean primary) {
        this.primary = primary;
        return this;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        action.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float a) {
        int x0 = getX();
        int y0 = getY();
        int x1 = x0 + getWidth();
        int y1 = y0 + getHeight();
        boolean hot = active && isHoveredOrFocused();
        int text;
        if (!active) {
            Ui.framed(gfx, x0, y0, x1, y1, Ui.OFF, 0xFF1C2A40);
            text = Ui.OFF_TEXT;
        } else if (primary) {
            Ui.framed(gfx, x0, y0, x1, y1, hot ? Ui.PRIMARY_HOT : Ui.PRIMARY, Ui.PRIMARY_EDGE);
            gfx.fill(x0 + 1, y1 - 3, x1 - 1, y1 - 1, 0x33000000);
            text = 0xFFFFFFFF;
        } else {
            Ui.framed(gfx, x0, y0, x1, y1, hot ? Ui.BUTTON_HOVER : Ui.BUTTON, hot ? Ui.BUTTON_EDGE_HOT : Ui.BUTTON_EDGE);
            text = hot ? Ui.TEXT : 0xFFD3DDEC;
        }
        if (active) {
            Festive.snowCap(gfx, x0, x1, y0, x0 * 7);
        }
        Font font = Minecraft.getInstance().font;
        String label = Ui.fit(font, getMessage().getString(), getWidth() - 8);
        gfx.text(font, label, x0 + (getWidth() - font.width(label)) / 2, y0 + (getHeight() - 8) / 2 + 1, text, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}

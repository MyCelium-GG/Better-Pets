package my.celium.org.betterpets;

import my.celium.org.util.TextUtil;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;

/**
 * Chat building blocks for Better Pets, one voice for every message:
 * {@code [Pets] ✔ Rex came running.} / {@code [Pets] ✘ ...}. Plain text is
 * soft grey, pet names keep their own colors, values are bright, buttons wear
 * the brand color. Everything is vanilla components, so vanilla clients render it as is.
 */
public final class PetText {
    static final int BRAND = 0xFFB86B;
    static final int NAME = 0xFFFFFF;
    static final int VALUE = 0xFFE7B3;
    static final int BUTTON = BRAND;
    static final int GOOD = 0x86EFAC;
    static final int DEAD = 0xEF5350;
    static final int HEART = 0xFF6B81;
    /** Brackets, separators, list numbers. {@link TextUtil#FAINT} disappears on the chat backdrop. */
    static final int DIM = 0x8B93A1;

    private PetText() {
    }

    /** {@code [Pets] } prefix. */
    public static MutableComponent prefix() {
        return TextUtil.empty().append(dim("[")).append(TextUtil.hex("Pets", BRAND))
                .append(dim("] "));
    }

    /** Secondary text that stays readable on the chat backdrop. */
    public static MutableComponent dim(String text) {
        return TextUtil.hex(text, DIM);
    }

    /** {@code " · "} between the parts of a line. */
    public static MutableComponent sep() {
        return dim(" · ");
    }

    /** Buttons with a space between each, so they read as separate targets. */
    public static MutableComponent buttons(Component... buttons) {
        MutableComponent out = TextUtil.empty();
        for (int i = 0; i < buttons.length; i++) {
            out.append(i == 0 ? TextUtil.empty() : TextUtil.body(" ")).append(buttons[i]);
        }
        return out;
    }

    /**
     * A message from parts: strings become soft body text, components are
     * appended as they are. {@code msg(name(pet), " came running.")}
     */
    public static MutableComponent msg(Object... parts) {
        MutableComponent out = TextUtil.empty();
        for (Object part : parts) {
            out.append(part instanceof Component component ? component : TextUtil.body(String.valueOf(part)));
        }
        return out;
    }

    /** {@code [Pets] message}. */
    public static MutableComponent info(Object... parts) {
        return prefix().append(msg(parts));
    }

    /** {@code [Pets] ✔ message}. */
    public static MutableComponent ok(Object... parts) {
        return prefix().append(TextUtil.hex("✔ ", GOOD)).append(msg(parts));
    }

    /** {@code [Pets] ✘ message}. */
    public static MutableComponent fail(Object... parts) {
        return prefix().append(TextUtil.hex("✘ ", DEAD)).append(msg(parts));
    }

    /** A bright value inside a message: a number, a time, a command. */
    public static MutableComponent value(String text) {
        return TextUtil.hex(text, VALUE);
    }

    /**
     * A pet's display name with its own colors: the live custom name when the
     * pet is loaded, else the one set with {@code /pets name}, else the plain
     * name or the translated species ("Wolf"). Wrapped so text appended after
     * it never inherits the name's style.
     */
    public static MutableComponent name(PetRecord pet) {
        Entity entity = PetTracker.loaded(pet.id);
        Component styled = null;
        if (entity != null && entity.getCustomName() != null) {
            styled = entity.getCustomName();
        } else if (pet.customName() != null && pet.nameFormat != null
                && PetNameFormat.plain(pet.nameFormat).equals(pet.customName())) {
            styled = PetNameFormat.parse(pet.nameFormat);
        } else if (pet.customName() != null) {
            styled = TextUtil.hex(pet.customName(), NAME);
        }
        if (styled == null) {
            styled = species(pet).withStyle(style -> style.withColor(NAME));
        }
        return TextUtil.empty().append(styled);
    }

    /** Translated species ("Wolf", "Cat"), rendered by the client in its own language. */
    public static MutableComponent species(PetRecord pet) {
        String key = pet.speciesKey();
        return key != null ? Component.translatable(key) : Component.literal(pet.species());
    }

    /** {@code [label]} that runs {@code command} when clicked. */
    public static MutableComponent button(String label, String command, String hover) {
        return buttonText(label).withStyle(style -> style
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(TextUtil.body(hover))));
    }

    /** {@code [label]} that puts {@code command} into the chat box for the player to finish. */
    public static MutableComponent suggest(String label, String command, String hover) {
        return buttonText(label).withStyle(style -> style
                .withClickEvent(new ClickEvent.SuggestCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(TextUtil.body(hover))));
    }

    /** Styled like the {@code [Pets]} tag: dim brackets, brand label. The parent's click covers all three parts. */
    private static MutableComponent buttonText(String label) {
        return TextUtil.empty().append(dim("[")).append(TextUtil.hex(label, BUTTON)).append(dim("]"));
    }

    /** {@code text} that shows the pet's coordinates on hover and copies them when clicked. */
    public static MutableComponent place(String text, PetRecord pet) {
        String xyz = blockCoords(pet.x, pet.y, pet.z);
        return TextUtil.body(text).withStyle(style -> style
                .withClickEvent(new ClickEvent.CopyToClipboard(xyz))
                .withHoverEvent(new HoverEvent.ShowText(TextUtil.hex(xyz, VALUE)
                        .append(TextUtil.muted(" in the " + PetBrain.dimensionName(pet.dimension)))
                        .append(dim("\nClick to copy")))));
    }

    static String blockCoords(double x, double y, double z) {
        return (long) Math.floor(x) + " " + (long) Math.floor(y) + " " + (long) Math.floor(z);
    }

    /** {@code x y z} that copies itself to the clipboard when clicked. */
    public static MutableComponent coords(double x, double y, double z) {
        String text = blockCoords(x, y, z);
        return TextUtil.hex(text, VALUE).withStyle(style -> style
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.CopyToClipboard(text))
                .withHoverEvent(new HoverEvent.ShowText(TextUtil.muted("Click to copy"))));
    }
}

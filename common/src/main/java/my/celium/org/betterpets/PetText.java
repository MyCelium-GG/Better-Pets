package my.celium.org.betterpets;

import my.celium.org.util.ChatVoice;
import my.celium.org.util.TextUtil;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;

/**
 * Chat building blocks for Better Pets, on Mycel's shared message kit:
 * {@code Pets › ✔ Rex came running.} / {@code Pets › ✘ ...}. Plain text is
 * soft grey, pet names keep their own colors, values are wiki purple, buttons
 * wiki blue. Everything is vanilla components, so vanilla clients render it as is.
 */
public final class PetText {
    static final ChatVoice VOICE = TextUtil.voice("Pets");
    static final int NAME = TextUtil.BRIGHT;
    static final int VALUE = TextUtil.INFO;
    static final int BUTTON = TextUtil.ACCENT;
    static final int GOOD = TextUtil.SUCCESS;
    static final int DEAD = TextUtil.ERROR;
    static final int HEART = TextUtil.PRIMARY;
    /** Brackets, separators, list numbers. */
    static final int DIM = TextUtil.FAINT;

    private PetText() {
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

    /** {@code msg(name(pet), " came running.")}: strings become body text, components stay as they are. */
    public static MutableComponent msg(Object... parts) {
        return TextUtil.msg(parts);
    }

    /** {@code Pets › message}. */
    public static MutableComponent info(Object... parts) {
        return VOICE.note(parts);
    }

    /** {@code Pets › ✔ message}. */
    public static MutableComponent ok(Object... parts) {
        return VOICE.ok(parts);
    }

    /** {@code Pets › ✘ message}. */
    public static MutableComponent fail(Object... parts) {
        return VOICE.fail(parts);
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

    /** {@code [label]}: dim brackets, accent label. The parent's click covers all three parts. */
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

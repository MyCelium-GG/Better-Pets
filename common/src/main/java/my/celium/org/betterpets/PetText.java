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
 * {@code Pets » Rex came running.}, with rose text for errors. Plain text,
 * values and buttons are off-white, pet names keep their own colors. Everything is vanilla components, so vanilla clients render it as is.
 */
public final class PetText {
    static final ChatVoice VOICE = TextUtil.voice("Pets");
    static final int NAME = TextUtil.BRIGHT;
    static final int VALUE = TextUtil.INFO;
    static final int BUTTON = TextUtil.ACCENT;
    /** Good news is plain bright text: the red tag is the only colour in our messages. */
    static final int GOOD = TextUtil.BRIGHT;
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

    /** Buttons with two spaces between each, so they read as separate targets. */
    public static MutableComponent buttons(Component... buttons) {
        MutableComponent out = TextUtil.empty();
        for (int i = 0; i < buttons.length; i++) {
            out.append(i == 0 ? TextUtil.empty() : TextUtil.body("  ")).append(buttons[i]);
        }
        return out;
    }

    /** {@code msg(name(pet), " came running.")}: strings become body text, components stay as they are. */
    public static MutableComponent msg(Object... parts) {
        return TextUtil.msg(parts);
    }

    /** Info line: {@code Pets » message}. */
    public static MutableComponent info(Object... parts) {
        return VOICE.note(parts);
    }

    /** Success line: {@code Pets » message}. */
    public static MutableComponent ok(Object... parts) {
        return VOICE.note(parts);
    }

    /** Error line: rose text. */
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

    /** Underlined {@code label} that runs {@code command} when clicked, like Mycel's buttons. */
    public static MutableComponent button(String label, String command, String hover) {
        return TextUtil.button(label, command, hover);
    }

    /** Underlined {@code label} that puts {@code command} into the chat box for the player to finish. */
    public static MutableComponent suggest(String label, String command, String hover) {
        return TextUtil.suggest(label, command, hover);
    }

    /** Header opening a list: {@code Pets » Your pets - 5}. */
    public static MutableComponent header(String title, String detail) {
        return VOICE.header(title, detail);
    }

    /** {@code text} that shows the pet's coordinates on hover and copies them when clicked. */
    public static MutableComponent place(net.minecraft.world.entity.player.Player player, String text, PetRecord pet) {
        String xyz = blockCoords(pet.x, pet.y, pet.z);
        return TextUtil.body(text).withStyle(style -> style
                .withClickEvent(new ClickEvent.CopyToClipboard(xyz))
                .withHoverEvent(new HoverEvent.ShowText(TextUtil.hex(xyz, VALUE)
                        .append(TextUtil.muted(" " + inDimension(player, pet.dimension)))
                        .append(dim("\n" + t(player, "click_to_copy"))))));
    }

    // ---------------------------------------------------------------- translations

    /** A message in the player's language (assets/betterpets/lang). */
    public static String t(net.minecraft.world.entity.player.Player player, String key, Object... args) {
        return BetterPetsMod.LANG.get(player, key, args);
    }

    /** Like {@link #t} but keeps the style of component arguments, like a pet's coloured name. */
    public static MutableComponent c(net.minecraft.world.entity.player.Player player, String key, Object... args) {
        return BetterPetsMod.LANG.text(player, key, args);
    }

    /** "just now", "5 min ago", "3 h ago", "2 d ago" in the player's language. */
    public static String ago(net.minecraft.world.entity.player.Player player, long millis) {
        long minutes = Math.max(0, millis) / 60_000L;
        if (minutes < 1) {
            return t(player, "ago.now");
        }
        if (minutes < 60) {
            return t(player, "ago.min", minutes);
        }
        long hours = minutes / 60;
        return hours < 48 ? t(player, "ago.hours", hours) : t(player, "ago.days", hours / 24);
    }

    /** "the Nether", "the Overworld", or the dimension's path for modded ones. */
    public static String dimension(net.minecraft.world.entity.player.Player player, String dimension) {
        if (dimension == null) {
            return t(player, "dim.unknown");
        }
        return switch (dimension) {
            case "minecraft:overworld" -> t(player, "dim.overworld");
            case "minecraft:the_nether" -> t(player, "dim.nether");
            case "minecraft:the_end" -> t(player, "dim.end");
            default -> PetBrain.dimensionName(dimension);
        };
    }

    /** A horse's speed, "10.5 blocks/s", in the player's language. */
    public static String speed(net.minecraft.world.entity.player.Player player, double attribute) {
        return t(player, "horse.speed", String.format(java.util.Locale.ROOT, "%.1f", attribute * 42.16));
    }

    /** A horse's jump height, "3.4 blocks", in the player's language. */
    public static String jump(net.minecraft.world.entity.player.Player player, double attribute) {
        return t(player, "horse.jump", PetBrain.horseJump(attribute).replace(" blocks", ""));
    }

    /** A compass letter ("NE") in the player's language. */
    public static String compass(net.minecraft.world.entity.player.Player player, String english) {
        String[] from = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        String[] to = t(player, "compass").split(",");
        for (int i = 0; i < from.length && to.length == from.length; i++) {
            if (from[i].equals(english)) {
                return to[i].trim();
            }
        }
        return english;
    }

    /** "in the Nether". */
    public static String inDimension(net.minecraft.world.entity.player.Player player, String dimension) {
        return t(player, "where.in", dimension(player, dimension));
    }

    static String blockCoords(double x, double y, double z) {
        return (long) Math.floor(x) + " " + (long) Math.floor(y) + " " + (long) Math.floor(z);
    }

    /** {@code x y z} that copies itself to the clipboard when clicked. */
    public static MutableComponent coords(net.minecraft.world.entity.player.Player player, double x, double y, double z) {
        String text = blockCoords(x, y, z);
        return TextUtil.hex(text, VALUE).withStyle(style -> style
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.CopyToClipboard(text))
                .withHoverEvent(new HoverEvent.ShowText(TextUtil.muted(t(player, "click_to_copy")))));
    }
}

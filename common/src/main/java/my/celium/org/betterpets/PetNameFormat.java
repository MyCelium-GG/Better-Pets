package my.celium.org.betterpets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Turns a typed pet name into a styled component. Both popular formats work,
 * and can be mixed:
 *
 * <ul>
 *   <li><b>Legacy codes</b> with {@code &} (or {@code §}): colors {@code &0}-{@code &f},
 *       {@code &l} bold, {@code &o} italic, {@code &n} underline, {@code &m}
 *       strikethrough, {@code &k} obfuscated, {@code &r} reset; hex as
 *       {@code &#ff55aa}, {@code {#ff55aa}} or {@code &x&f&f&5&5&a&a}.
 *       {@code &&} is a literal {@code &}. A color code clears earlier legacy
 *       formats, like in vanilla.</li>
 *   <li><b>MiniMessage tags</b>: {@code <red>}, {@code <#ff55aa>},
 *       {@code <color:gold>}, {@code <bold>}/{@code <b>}, {@code <italic>}/{@code <i>},
 *       {@code <underlined>}/{@code <u>}, {@code <strikethrough>}/{@code <st>},
 *       {@code <obfuscated>}/{@code <obf>}, {@code <!bold>} to turn one off,
 *       {@code <reset>}, {@code <gradient:red:gold:#ff55aa>} and {@code <rainbow>}
 *       (also {@code <rainbow:!>} reversed). Closing tags like {@code </bold>}
 *       are optional. {@code \<} is a literal {@code <}.</li>
 * </ul>
 *
 * <p>Unknown codes and tags stay as typed. Names default to white, not
 * italic, like an anvil rename. Pure logic, no registries: unit-tested.
 */
public final class PetNameFormat {
    private static final Map<String, Integer> NAMED = new HashMap<>();
    private static final Map<Character, String> LEGACY_COLORS = new HashMap<>();
    private static final Map<String, Deco> DECO_TAGS = new HashMap<>();

    static {
        String[][] colors = {
            {"0", "black", "000000"}, {"1", "dark_blue", "0000AA"}, {"2", "dark_green", "00AA00"},
            {"3", "dark_aqua", "00AAAA"}, {"4", "dark_red", "AA0000"}, {"5", "dark_purple", "AA00AA"},
            {"6", "gold", "FFAA00"}, {"7", "gray", "AAAAAA"}, {"8", "dark_gray", "555555"},
            {"9", "blue", "5555FF"}, {"a", "green", "55FF55"}, {"b", "aqua", "55FFFF"},
            {"c", "red", "FF5555"}, {"d", "light_purple", "FF55FF"}, {"e", "yellow", "FFFF55"},
            {"f", "white", "FFFFFF"},
        };
        for (String[] c : colors) {
            NAMED.put(c[1], Integer.parseInt(c[2], 16));
            LEGACY_COLORS.put(c[0].charAt(0), c[1]);
        }
        NAMED.put("grey", NAMED.get("gray"));
        NAMED.put("dark_grey", NAMED.get("dark_gray"));
        for (Deco deco : Deco.values()) {
            for (String alias : deco.aliases) {
                DECO_TAGS.put(alias, deco);
            }
        }
    }

    enum Deco {
        BOLD('l', "bold", "b"),
        ITALIC('o', "italic", "i", "em"),
        UNDERLINED('n', "underlined", "underline", "u"),
        STRIKETHROUGH('m', "strikethrough", "st"),
        OBFUSCATED('k', "obfuscated", "obf");

        final char legacy;
        final String[] aliases;

        Deco(char legacy, String... aliases) {
            this.legacy = legacy;
            this.aliases = aliases;
        }

        Style apply(Style style, boolean on) {
            return switch (this) {
                case BOLD -> style.withBold(on);
                case ITALIC -> style.withItalic(on);
                case UNDERLINED -> style.withUnderlined(on);
                case STRIKETHROUGH -> style.withStrikethrough(on);
                case OBFUSCATED -> style.withObfuscated(on);
            };
        }

        static Deco ofLegacy(char code) {
            for (Deco deco : values()) {
                if (deco.legacy == code) {
                    return deco;
                }
            }
            return null;
        }
    }

    /** One open tag or legacy code. Exactly one of color/deco/gradient is set. */
    private static final class Frame {
        final String tag;
        final boolean legacy;
        Integer color;
        Deco deco;
        boolean decoOn;
        int[] gradient;
        boolean rainbow;
        boolean reversed;
        /** Characters a gradient or rainbow spans, and how many were colored so far. */
        int total;
        int seen;

        Frame(String tag, boolean legacy) {
            this.tag = tag;
            this.legacy = legacy;
        }
    }

    private PetNameFormat() {
    }

    /** Styled name for {@code raw}; never {@code null}. */
    public static MutableComponent parse(String raw) {
        List<Integer> chars = new ArrayList<>();
        List<Frame[]> active = new ArrayList<>();
        List<Frame> stack = new ArrayList<>();
        if (raw != null) {
            scan(raw, chars, active, stack);
        }

        // Gradients need to know how many characters they span.
        for (Frame[] frames : active) {
            for (Frame frame : frames) {
                if (frame.gradient != null || frame.rainbow) {
                    frame.total++;
                }
            }
        }

        MutableComponent root = Component.empty();
        StringBuilder run = new StringBuilder();
        Style runStyle = null;
        for (int i = 0; i < chars.size(); i++) {
            Style style = Style.EMPTY.withColor(0xFFFFFF).withItalic(false);
            for (Frame frame : active.get(i)) {
                style = apply(style, frame);
            }
            if (runStyle != null && !style.equals(runStyle)) {
                root.append(Component.literal(run.toString()).withStyle(runStyle));
                run.setLength(0);
            }
            runStyle = style;
            run.appendCodePoint(chars.get(i));
        }
        if (runStyle != null) {
            root.append(Component.literal(run.toString()).withStyle(runStyle));
        }
        return root;
    }

    /** The name without any formatting, e.g. for matching {@code /pets find}. */
    public static String plain(String raw) {
        return parse(raw).getString();
    }

    private static Style apply(Style style, Frame frame) {
        if (frame.color != null) {
            return style.withColor(TextColor.fromRgb(frame.color));
        }
        if (frame.deco != null) {
            return frame.deco.apply(style, frame.decoOn);
        }
        if (frame.gradient != null) {
            int index = frame.seen++;
            double t = frame.total <= 1 ? 0 : (double) index / (frame.total - 1);
            return style.withColor(TextColor.fromRgb(along(frame.gradient, t)));
        }
        if (frame.rainbow) {
            int index = frame.seen++;
            double t = frame.total <= 0 ? 0 : (double) index / frame.total;
            return style.withColor(TextColor.fromRgb(hue(frame.reversed ? 1 - t : t)));
        }
        return style;
    }

    private static void scan(String raw, List<Integer> chars, List<Frame[]> active, List<Frame> stack) {
        int i = 0;
        int length = raw.length();
        while (i < length) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < length && raw.charAt(i + 1) == '<') {
                emit('<', chars, active, stack);
                i += 2;
                continue;
            }
            if (c == '<') {
                int close = raw.indexOf('>', i + 1);
                if (close > i + 1 && tag(raw.substring(i + 1, close), stack)) {
                    i = close + 1;
                    continue;
                }
            }
            if (c == '{' && i + 8 < length && raw.charAt(i + 1) == '#' && raw.charAt(i + 8) == '}') {
                Integer hex = hex(raw, i + 2);
                if (hex != null) {
                    legacyColor(hex, stack);
                    i += 9;
                    continue;
                }
            }
            if ((c == '&' || c == '§') && i + 1 < length) {
                int used = legacy(raw, i, stack);
                if (used == -1) {
                    emit(raw.charAt(i + 1), chars, active, stack);
                    i += 2;
                    continue;
                }
                if (used > 0) {
                    i += used;
                    continue;
                }
            }
            int codePoint = raw.codePointAt(i);
            emit(codePoint, chars, active, stack);
            i += Character.charCount(codePoint);
        }
    }

    private static void emit(int codePoint, List<Integer> chars, List<Frame[]> active, List<Frame> stack) {
        chars.add(codePoint);
        active.add(stack.toArray(new Frame[0]));
    }

    /**
     * Handles a legacy code at {@code i}. Returns how many characters it used,
     * 0 when it is not a code, or -1 for {@code &&} (emit the second char).
     */
    private static int legacy(String raw, int i, List<Frame> stack) {
        char next = Character.toLowerCase(raw.charAt(i + 1));
        if (next == '&' && raw.charAt(i) == '&') {
            return -1;
        }
        if (next == '#') {
            Integer hex = hex(raw, i + 2);
            if (hex != null) {
                legacyColor(hex, stack);
                return 8;
            }
            return 0;
        }
        if (next == 'x') {
            StringBuilder digits = new StringBuilder();
            for (int j = 0; j < 6; j++) {
                int pos = i + 2 + j * 2;
                if (pos + 1 >= raw.length() || (raw.charAt(pos) != '&' && raw.charAt(pos) != '§')) {
                    return 0;
                }
                digits.append(raw.charAt(pos + 1));
            }
            Integer hex = hex(digits.toString(), 0);
            if (hex != null) {
                legacyColor(hex, stack);
                return 14;
            }
            return 0;
        }
        String named = LEGACY_COLORS.get(next);
        if (named != null) {
            legacyColor(NAMED.get(named), stack);
            return 2;
        }
        Deco deco = Deco.ofLegacy(next);
        if (deco != null) {
            Frame frame = new Frame("&" + next, true);
            frame.deco = deco;
            frame.decoOn = true;
            stack.add(frame);
            return 2;
        }
        if (next == 'r') {
            stack.clear();
            return 2;
        }
        return 0;
    }

    /** A legacy color replaces earlier legacy colors and formats, like vanilla. */
    private static void legacyColor(int rgb, List<Frame> stack) {
        stack.removeIf(frame -> frame.legacy);
        Frame frame = new Frame("&", true);
        frame.color = rgb;
        stack.add(frame);
    }

    /** Applies one MiniMessage tag body (between {@code <} and {@code >}); false when it is not a tag. */
    private static boolean tag(String body, List<Frame> stack) {
        String lower = body.toLowerCase(Locale.ROOT).trim();
        if (lower.startsWith("/")) {
            String name = lower.substring(1);
            for (int j = stack.size() - 1; j >= 0; j--) {
                if (!stack.get(j).legacy && (name.isEmpty() || closes(stack.get(j), name))) {
                    stack.remove(j);
                    return true;
                }
            }
            return isKnown(name);
        }
        if (lower.equals("reset")) {
            stack.clear();
            return true;
        }
        String[] parts = lower.split(":");
        String name = parts[0];
        boolean negate = name.startsWith("!");
        if (negate) {
            name = name.substring(1);
        }
        Deco deco = DECO_TAGS.get(name);
        if (deco != null) {
            Frame frame = new Frame(name, false);
            frame.deco = deco;
            // <!bold> and <bold:false> turn it off.
            frame.decoOn = !negate && !(parts.length > 1 && parts[1].equals("false"));
            stack.add(frame);
            return true;
        }
        if (negate) {
            return false;
        }
        Integer color = color(name);
        if ((name.equals("color") || name.equals("colour") || name.equals("c")) && parts.length > 1) {
            color = color(parts[1]);
        }
        if (color != null) {
            Frame frame = new Frame(name, false);
            frame.color = color;
            stack.add(frame);
            return true;
        }
        if (name.equals("gradient")) {
            List<Integer> stops = new ArrayList<>();
            for (int j = 1; j < parts.length; j++) {
                Integer stop = color(parts[j]);
                if (stop != null) {
                    stops.add(stop);
                }
            }
            if (stops.isEmpty()) {
                stops.add(0xFFFFFF);
                stops.add(0x000000);
            } else if (stops.size() == 1) {
                stops.add(stops.get(0));
            }
            Frame frame = new Frame("gradient", false);
            frame.gradient = stops.stream().mapToInt(Integer::intValue).toArray();
            stack.add(frame);
            return true;
        }
        if (name.equals("rainbow")) {
            Frame frame = new Frame("rainbow", false);
            frame.rainbow = true;
            frame.reversed = parts.length > 1 && parts[1].startsWith("!");
            stack.add(frame);
            return true;
        }
        return false;
    }

    /** Whether closing tag {@code close} ends {@code open}: same name, alias, or any color for a color. */
    private static boolean closes(Frame open, String close) {
        String base = close.split(":")[0];
        if (open.tag.equals(base)) {
            return true;
        }
        if (open.deco != null) {
            return open.deco == DECO_TAGS.get(base);
        }
        return open.color != null && (base.equals("color") || base.equals("colour") || base.equals("c")
                || color(base) != null);
    }

    private static boolean isKnown(String name) {
        String base = name.split(":")[0];
        return DECO_TAGS.containsKey(base) || color(base) != null || base.equals("gradient")
                || base.equals("rainbow") || base.equals("color") || base.equals("colour") || base.equals("c");
    }

    /** A named color or {@code #rrggbb}, or {@code null}. */
    static Integer color(String spec) {
        if (spec == null) {
            return null;
        }
        String s = spec.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("#") && s.length() == 7) {
            return hex(s, 1);
        }
        return NAMED.get(s);
    }

    private static Integer hex(String raw, int offset) {
        if (offset + 6 > raw.length()) {
            return null;
        }
        int rgb = 0;
        for (int j = 0; j < 6; j++) {
            int digit = Character.digit(raw.charAt(offset + j), 16);
            if (digit < 0) {
                return null;
            }
            rgb = (rgb << 4) | digit;
        }
        return rgb;
    }

    /** Color at {@code t} (0..1) along evenly spaced gradient stops. */
    static int along(int[] stops, double t) {
        double scaled = Math.max(0, Math.min(1, t)) * (stops.length - 1);
        int from = Math.min((int) Math.floor(scaled), stops.length - 2);
        double local = scaled - from;
        int a = stops[from];
        int b = stops[from + 1];
        int r = (int) Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * local);
        int g = (int) Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * local);
        int bl = (int) Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * local);
        return (r << 16) | (g << 8) | bl;
    }

    /** Fully saturated rainbow color at {@code t} (0..1) around the hue circle, red first. */
    static int hue(double t) {
        double h = ((t % 1.0) + 1.0) % 1.0 * 6.0;
        int sector = (int) Math.floor(h) % 6;
        int rising = (int) Math.round((h - Math.floor(h)) * 255);
        int falling = 255 - rising;
        return switch (sector) {
            case 0 -> 0xFF0000 | rising << 8;
            case 1 -> falling << 16 | 0x00FF00;
            case 2 -> 0x00FF00 | rising;
            case 3 -> falling << 8 | 0x0000FF;
            case 4 -> rising << 16 | 0x0000FF;
            default -> 0xFF0000 | falling;
        };
    }
}

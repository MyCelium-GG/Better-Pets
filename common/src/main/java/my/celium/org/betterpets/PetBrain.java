package my.celium.org.betterpets;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure helpers behind the {@code /pets} command and the find compass:
 * directions, distances, pet lookup by name or list number. No Minecraft
 * classes, so everything here is unit-tested directly.
 */
public final class PetBrain {
    /** Longest visible name {@code /pets name} accepts (same as an anvil rename). */
    public static final int MAX_NAME_LENGTH = 50;
    /** Longest typed name including color codes and tags. */
    public static final int MAX_TYPED_LENGTH = 256;

    private static final String[] COMPASS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
    // Minecraft's font only has the four straight arrows; diagonals are drawn as two of them.
    private static final String[] ARROWS = {"↑", "↑→", "→", "↓→", "↓", "↓←", "←", "↑←"};

    private PetBrain() {
    }

    /**
     * Minecraft yaw (degrees, 0 = south, 90 = west) that faces from the origin
     * towards an offset of {@code dx}/{@code dz}.
     */
    public static double yawTowards(double dx, double dz) {
        return Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** Compass direction of an offset: {@code "N"}, {@code "NE"} ... ({@code -z} is north). */
    public static String compass(double dx, double dz) {
        return COMPASS[octant(yawTowards(dx, dz))];
    }

    /**
     * Arrow pointing at the target relative to where the player looks:
     * {@code ↑} straight ahead, {@code →} to the right, {@code ↓} behind,
     * {@code ↑→} ahead and to the right.
     */
    public static String arrow(float playerYaw, double dx, double dz) {
        return ARROWS[octant(yawTowards(dx, dz) - playerYaw)];
    }

    /** One of 8 sectors (0 = the 0° direction, counting with increasing yaw). */
    static int octant(double degrees) {
        double wrapped = ((degrees % 360) + 360) % 360;
        return (int) Math.round(wrapped / 45.0) % 8;
    }

    /** {@code "42 m"} below a kilometre, {@code "1.3 km"} above. */
    public static String distance(double blocks) {
        if (blocks < 1000) {
            return Math.round(blocks) + " m";
        }
        return String.format(Locale.ROOT, "%.1f km", blocks / 1000.0);
    }

    /** {@code "just now"}, {@code "5 min ago"}, {@code "3 h ago"}, {@code "2 d ago"}. */
    public static String ago(long millis) {
        long minutes = Math.max(0, millis) / 60_000L;
        if (minutes < 1) {
            return "just now";
        }
        if (minutes < 60) {
            return minutes + " min ago";
        }
        long hours = minutes / 60;
        if (hours < 48) {
            return hours + " h ago";
        }
        return hours / 24 + " d ago";
    }

    /** {@code "overworld"} for {@code "minecraft:overworld"}, {@code "the nether"} for {@code "minecraft:the_nether"}. */
    public static String dimensionName(String dimension) {
        if (dimension == null) {
            return "unknown";
        }
        String path = dimension.substring(dimension.indexOf(':') + 1);
        return path.replace('_', ' ');
    }

    /** A pet name as a Brigadier string argument: quoted unless it is a plain word. */
    public static String commandArgument(String name) {
        if (name.matches("[0-9A-Za-z_.+\\-]+")) {
            return name;
        }
        return "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /**
     * Finds the pet a player means. Its UUID (what the menu's buttons send)
     * always works; {@code "#3"} or {@code "3"} picks the third pet of the
     * list; otherwise an exact (case-insensitive) name wins, then a
     * unique name prefix. Returns every match so the caller can report
     * ambiguity; an empty list means nothing matched.
     */
    public static List<PetRecord> resolve(List<PetRecord> pets, String query) {
        List<PetRecord> result = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return result;
        }
        String q = query.trim();
        for (PetRecord pet : pets) {
            if (pet.id != null && pet.id.toString().equalsIgnoreCase(q)) {
                result.add(pet);
                return result;
            }
        }
        String digits = q.startsWith("#") ? q.substring(1) : q;
        if (digits.matches("\\d{1,4}")) {
            int index = Integer.parseInt(digits) - 1;
            if (index >= 0 && index < pets.size()) {
                result.add(pets.get(index));
                return result;
            }
        }
        String lower = q.toLowerCase(Locale.ROOT);
        for (PetRecord pet : pets) {
            String name = pet.customName();
            if (name != null && name.toLowerCase(Locale.ROOT).equals(lower)) {
                result.add(pet);
            }
        }
        if (!result.isEmpty()) {
            return result;
        }
        for (PetRecord pet : pets) {
            String name = pet.customName();
            if (name != null && name.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(pet);
            }
        }
        return result;
    }

    /**
     * The typed name (color codes and tags still in it) with outer quotes and
     * extra spaces removed, or {@code null} when it is blank, absurdly long or
     * has control characters. The visible length is checked after formatting.
     */
    public static String cleanName(String raw) {
        if (raw == null) {
            return null;
        }
        String name = raw.strip().replaceAll("\\s+", " ");
        if (name.length() >= 2 && name.startsWith("\"") && name.endsWith("\"")) {
            name = name.substring(1, name.length() - 1).strip();
        }
        if (name.isEmpty() || name.length() > MAX_TYPED_LENGTH) {
            return null;
        }
        for (int i = 0; i < name.length(); i++) {
            if (Character.isISOControl(name.charAt(i))) {
                return null;
            }
        }
        return name;
    }

    /** Health as {@code "14/20"} with halves kept: {@code "7.5/20"}. */
    public static String health(float health, float max) {
        return number(health) + "/" + number(max);
    }

    /** How many of {@code slots} a health bar fills: any health left shows at least one. */
    public static int healthSlots(float health, float max, int slots) {
        if (health <= 0 || max <= 0) {
            return 0;
        }
        return Math.min(slots, (int) Math.ceil(health / max * slots));
    }

    /** Columns of a nine-wide row for {@code count} buttons, mirrored around the middle. */
    public static int[] centered(int count) {
        int[] columns = new int[count];
        int first = 4 - count / 2;
        for (int i = 0; i < count; i++) {
            // An even count leaves the middle column free so both sides match.
            columns[i] = first + i + (count % 2 == 0 && i >= count / 2 ? 1 : 0);
        }
        return columns;
    }

    /** Rows of a chest screen with a grid of {@code entries} above one row of buttons. */
    public static int gridRows(int entries) {
        return Math.clamp((entries + 8) / 9 + 1, 2, 6);
    }

    /** Pages needed for {@code entries}; an empty grid still has one. */
    public static int pages(int entries, int perPage) {
        return Math.max(1, (entries + perPage - 1) / perPage);
    }

    /** A horse's speed attribute as players know it: {@code "10.5 blocks/s"}. */
    public static String horseSpeed(double attribute) {
        return String.format(Locale.ROOT, "%.1f blocks/s", attribute * 42.16);
    }

    /** A horse's jump attribute as the height it clears: {@code "3.4 blocks"} (the wiki's fitted curve). */
    public static String horseJump(double attribute) {
        double height = -0.1817584952 * attribute * attribute * attribute + 3.689713992 * attribute * attribute
                + 2.128599134 * attribute - 0.343930367;
        return String.format(Locale.ROOT, "%.1f blocks", height);
    }

    /** Undoes the backslashes a dialog's text box puts before quotes and backslashes. */
    public static String unescapeDialog(String text) {
        return text.replaceAll("\\\\(.)", "$1");
    }

    private static String number(float value) {
        float rounded = Math.round(value * 2) / 2f;
        return rounded == (int) rounded ? String.valueOf((int) rounded) : String.valueOf(rounded);
    }
}

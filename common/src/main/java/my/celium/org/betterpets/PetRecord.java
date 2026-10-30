package my.celium.org.betterpets;

import java.util.UUID;

/**
 * What Better Pets remembers about one pet: enough to list and find it while
 * its chunk is unloaded. Plain mutable fields so Gson can (de)serialise it
 * directly; no Minecraft classes, so it is fully unit-testable.
 */
public final class PetRecord {
    public UUID id;
    /** Entity type id, e.g. {@code "minecraft:wolf"}. */
    public String type;
    /** Custom name as plain text, or {@code null} for an unnamed pet. */
    public String name;
    /** The colored name as typed into {@code /pets name} ({@code &a}/MiniMessage), or {@code null}. */
    public String nameFormat;
    /** Dimension id, e.g. {@code "minecraft:overworld"}. */
    public String dimension;
    public double x;
    public double y;
    public double z;
    public float health;
    public float maxHealth;
    public boolean sitting;
    public boolean leashed;
    /** Wall-clock millis when the pet first showed up, 0 for pets from before this was kept. */
    public long firstSeen;
    /** Wall-clock millis when the pet was last seen loaded. */
    public long lastSeen;
    /** Whether the pet is currently in a loaded chunk (not persisted meaningfully). */
    public transient boolean loaded;
    public boolean dead;
    /** Vanilla death message text, e.g. "Rex was slain by Zombie". */
    public String deathMessage;

    public PetRecord() {
    }

    public PetRecord(UUID id, String type) {
        this.id = id;
        this.type = type;
    }

    /** The custom name, or {@code null}. Blank names count as unnamed. */
    public String customName() {
        return name == null || name.isBlank() ? null : name;
    }

    /** Short species key: {@code "minecraft:wolf"} becomes {@code "wolf"}. */
    public String species() {
        if (type == null) {
            return "pet";
        }
        int colon = type.indexOf(':');
        return colon < 0 ? type : type.substring(colon + 1);
    }

    /** Translation key of the species name, e.g. {@code "entity.minecraft.wolf"}, or {@code null}. */
    public String speciesKey() {
        if (type == null || type.indexOf(':') < 0) {
            return null;
        }
        return "entity." + type.replace(':', '.');
    }
}

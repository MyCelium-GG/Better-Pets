package my.celium.org.betterpets;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import my.celium.org.config.ConfigValue;
import my.celium.org.stats.ModStats;

/**
 * Anonymous stats for the FastStats project {@code better-pets}, sent through Mycel under its
 * consent (the player's answer on Mycel's screen, {@code shareStats} on servers). Pet numbers come
 * from the world that is running here, so a client that only joins other servers sends none.
 */
public final class PetStats {
    /** Project token from faststats.dev (public, ships in the jar). */
    private static final String TOKEN = "8f6049b5771c7f7061e4b6daac1600be";

    private static volatile ModStats stats;
    /** Set while a world (dedicated or singleplayer) runs in this game. */
    private static volatile boolean worldRunning;

    private PetStats() {
    }

    static void start() {
        stats = ModStats.builder(BetterPetsMod.META, TOKEN)
                .number("pets_tracked", () -> worldRunning ? PetTracker.STORE.count(false) : null)
                .number("pets_dead", () -> worldRunning ? PetTracker.STORE.count(true) : null)
                .number("pet_owners", () -> worldRunning ? PetTracker.STORE.ownerCount() : null)
                .number("most_pets_one_owner", () -> worldRunning ? PetTracker.STORE.mostPetsOneOwner() : null)
                .numberMap("species", () -> worldRunning ? PetTracker.STORE.species() : null)
                .number("named_pets", () -> worldRunning ? PetTracker.STORE.named(false) : null)
                .number("colored_names", () -> worldRunning ? PetTracker.STORE.named(true) : null)
                .stringArray("settings_off", PetStats::settingsOff)
                .number("call_cooldown", () -> BetterPetsMod.callCooldownSeconds.get())
                .number("find_seconds", () -> BetterPetsMod.findSeconds.get())
                .errors("my.celium.org.betterpets.")
                .start();
    }

    /**
     * Counts one use of a feature, like {@code find} or {@code menu_screen}. Sent as the
     * {@code usage} metric; nothing happens while stats are off.
     */
    public static void count(String what) {
        ModStats current = stats;
        if (current != null) {
            current.count(what);
        }
    }

    static void worldStarted() {
        worldRunning = true;
    }

    static void worldStopped() {
        worldRunning = false;
    }

    private static String[] settingsOff() {
        List<String> off = new ArrayList<>();
        Map<String, ConfigValue<Boolean>> switches = new TreeMap<>(Map.ofEntries(
                Map.entry("enabled", BetterPetsMod.enabled),
                Map.entry("petMenu", BetterPetsMod.petMenu),
                Map.entry("protectFromOwner", BetterPetsMod.protectFromOwner),
                Map.entry("strongLeads", BetterPetsMod.strongLeads),
                Map.entry("deathAlerts", BetterPetsMod.deathAlerts),
                Map.entry("allowCall", BetterPetsMod.allowCall),
                Map.entry("allowRename", BetterPetsMod.allowRename),
                Map.entry("allowGive", BetterPetsMod.allowGive),
                Map.entry("scarves", BetterPetsMod.scarves),
                Map.entry("sleepOnBed", BetterPetsMod.sleepOnBed),
                Map.entry("snowyCoats", BetterPetsMod.snowyCoats)));
        switches.forEach((name, value) -> {
            if (!value.get()) {
                off.add(name);
            }
        });
        return off.isEmpty() ? null : off.toArray(String[]::new);
    }
}

package my.celium.org.betterpets;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import my.celium.org.data.MycelDataFile;

/**
 * Every known pet per owner, saved per world as
 * {@code <world>/data/betterpets.json}. The file is small (a few hundred
 * bytes per pet) and rewritten wholesale, atomically, when something
 * changed. A broken file is kept aside by {@link MycelDataFile} instead of
 * being overwritten.
 */
public final class PetStore {
    static final String FILE_NAME = "betterpets.json";

    /** owner UUID -> (pet UUID -> record), both in first-seen order. */
    private final Map<UUID, Map<UUID, PetRecord>> owners = new LinkedHashMap<>();
    private MycelDataFile file;
    private boolean dirty;

    private static final class Root {
        int version = 1;
        Map<UUID, List<PetRecord>> owners = new LinkedHashMap<>();
    }

    /** Replaces the contents with {@code dir/betterpets.json}; a missing or broken file starts empty. */
    public synchronized void load(Path dir) {
        owners.clear();
        dirty = false;
        file = dir == null ? null : MycelDataFile.at(BetterPetsMod.META, dir.resolve(FILE_NAME));
        if (file == null) {
            return;
        }
        Root root = file.read(Root.class, Root::new);
        if (root.owners == null) {
            return;
        }
        for (Map.Entry<UUID, List<PetRecord>> entry : root.owners.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            for (PetRecord pet : entry.getValue()) {
                if (pet != null && pet.id != null) {
                    owners.computeIfAbsent(entry.getKey(), k -> new LinkedHashMap<>()).put(pet.id, pet);
                }
            }
        }
    }

    /** Writes the file when something changed since the last save. */
    public synchronized void saveIfDirty() {
        if (!dirty || file == null) {
            return;
        }
        Root root = new Root();
        for (Map.Entry<UUID, Map<UUID, PetRecord>> entry : owners.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                root.owners.put(entry.getKey(), new ArrayList<>(entry.getValue().values()));
            }
        }
        if (file.write(root)) {
            dirty = false;
        }
    }

    /** Forgets everything without saving (server stopped). */
    public synchronized void clear() {
        owners.clear();
        file = null;
        dirty = false;
    }

    /** The owner's pets in first-seen order; this numbering is what {@code #n} refers to. */
    public synchronized List<PetRecord> pets(UUID owner) {
        Map<UUID, PetRecord> pets = owners.get(owner);
        return pets == null ? new ArrayList<>() : new ArrayList<>(pets.values());
    }

    /** The record for {@code pet} under {@code owner}, created when new. */
    public synchronized PetRecord getOrCreate(UUID owner, UUID pet, String type) {
        Map<UUID, PetRecord> pets = owners.computeIfAbsent(owner, k -> new LinkedHashMap<>());
        PetRecord record = pets.get(pet);
        if (record == null) {
            record = new PetRecord(pet, type);
            record.firstSeen = System.currentTimeMillis();
            pets.put(pet, record);
            dirty = true;
        }
        return record;
    }

    /** The owner a pet is recorded under, or {@code null}. */
    public synchronized UUID ownerOf(UUID pet) {
        for (Map.Entry<UUID, Map<UUID, PetRecord>> entry : owners.entrySet()) {
            if (entry.getValue().containsKey(pet)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** Removes a pet from an owner's list. */
    public synchronized boolean remove(UUID owner, UUID pet) {
        Map<UUID, PetRecord> pets = owners.get(owner);
        if (pets != null && pets.remove(pet) != null) {
            dirty = true;
            return true;
        }
        return false;
    }

    /** Flags unsaved changes (records are mutated in place by the tracker). */
    public synchronized void markDirty() {
        dirty = true;
    }

    synchronized boolean isDirty() {
        return dirty;
    }

    // ---------------------------------------------------------------- stats

    /** How many pets are known: living ones, or dead ones still on someone's list. */
    public synchronized int count(boolean dead) {
        int count = 0;
        for (Map<UUID, PetRecord> pets : owners.values()) {
            for (PetRecord pet : pets.values()) {
                if (pet.dead == dead) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Players with at least one living pet. */
    public synchronized int ownerCount() {
        int count = 0;
        for (Map<UUID, PetRecord> pets : owners.values()) {
            if (pets.values().stream().anyMatch(pet -> !pet.dead)) {
                count++;
            }
        }
        return count;
    }

    /** The most living pets one player has. */
    public synchronized int mostPetsOneOwner() {
        int most = 0;
        for (Map<UUID, PetRecord> pets : owners.values()) {
            most = Math.max(most, (int) pets.values().stream().filter(pet -> !pet.dead).count());
        }
        return most;
    }

    /** Living pets per species: {@code wolf} for vanilla, the full id for modded ones. */
    public synchronized Map<String, Integer> species() {
        Map<String, Integer> counts = new java.util.TreeMap<>();
        for (Map<UUID, PetRecord> pets : owners.values()) {
            for (PetRecord pet : pets.values()) {
                if (!pet.dead) {
                    String key = pet.type != null && !pet.type.startsWith("minecraft:") ? pet.type : pet.species();
                    counts.merge(key, 1, Integer::sum);
                }
            }
        }
        return counts.isEmpty() ? null : counts;
    }

    /** Living pets with a custom name; with {@code colored}, only names with colors or styles. */
    public synchronized int named(boolean colored) {
        int count = 0;
        for (Map<UUID, PetRecord> pets : owners.values()) {
            for (PetRecord pet : pets.values()) {
                if (!pet.dead && pet.customName() != null
                        && (!colored || pet.nameFormat != null && !pet.nameFormat.equals(pet.customName()))) {
                    count++;
                }
            }
        }
        return count;
    }
}

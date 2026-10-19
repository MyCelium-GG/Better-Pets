package my.celium.org.betterpets;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import my.celium.org.logging.MycelLog;

/**
 * Every known pet per owner, saved per world as
 * {@code <world>/data/betterpets.json}. The file is small (a few hundred
 * bytes per pet) and rewritten wholesale, atomically, when something
 * changed. No Minecraft classes - only Gson and files - so it is fully
 * unit-testable.
 */
public final class PetStore {
    static final String FILE_NAME = "betterpets.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** owner UUID -> (pet UUID -> record), both in first-seen order. */
    private final Map<UUID, Map<UUID, PetRecord>> owners = new LinkedHashMap<>();
    private Path file;
    private boolean dirty;

    private static final class Root {
        int version = 1;
        Map<UUID, List<PetRecord>> owners = new LinkedHashMap<>();
    }

    /** Replaces the contents with {@code dir/betterpets.json}; a missing or broken file starts empty. */
    public synchronized void load(Path dir) {
        owners.clear();
        dirty = false;
        file = dir == null ? null : dir.resolve(FILE_NAME);
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Root root = GSON.fromJson(reader, new TypeToken<Root>() {
            }.getType());
            if (root == null || root.owners == null) {
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
        } catch (Exception e) {
            MycelLog.warnMod(BetterPetsMod.ID, "Could not read {}, starting fresh: {}", file, e.toString());
            owners.clear();
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
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(FILE_NAME + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            dirty = false;
        } catch (IOException e) {
            MycelLog.warnMod(BetterPetsMod.ID, "Could not save {}: {}", file, e.toString());
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
}

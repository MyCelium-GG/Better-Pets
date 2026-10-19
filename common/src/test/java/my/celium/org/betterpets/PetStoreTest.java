package my.celium.org.betterpets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PetStoreTest {
    @Test
    void savesAndLoadsRoundTrip(@TempDir Path dir) {
        UUID owner = UUID.randomUUID();
        UUID wolf = UUID.randomUUID();
        UUID cat = UUID.randomUUID();
        PetStore store = new PetStore();
        store.load(dir);
        PetRecord rex = store.getOrCreate(owner, wolf, "minecraft:wolf");
        rex.name = "Rex";
        rex.dimension = "minecraft:overworld";
        rex.x = 12.5;
        rex.y = 64;
        rex.z = -30;
        rex.health = 18;
        rex.maxHealth = 40;
        rex.sitting = true;
        PetRecord luna = store.getOrCreate(owner, cat, "minecraft:cat");
        luna.dead = true;
        luna.deathMessage = "Cat was slain by Zombie";
        store.saveIfDirty();
        assertFalse(store.isDirty());
        assertTrue(Files.isRegularFile(dir.resolve(PetStore.FILE_NAME)));

        PetStore loaded = new PetStore();
        loaded.load(dir);
        assertEquals(2, loaded.pets(owner).size());
        PetRecord back = loaded.pets(owner).get(0);
        assertEquals(wolf, back.id);
        assertEquals("Rex", back.name);
        assertEquals(12.5, back.x);
        assertEquals(40, back.maxHealth);
        assertTrue(back.sitting);
        assertTrue(loaded.pets(owner).get(1).dead);
        assertEquals("Cat was slain by Zombie", loaded.pets(owner).get(1).deathMessage);
    }

    @Test
    void ownersAndRemoval(@TempDir Path dir) {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID pet = UUID.randomUUID();
        PetStore store = new PetStore();
        store.load(dir);
        store.getOrCreate(alice, pet, "minecraft:parrot");
        assertEquals(alice, store.ownerOf(pet));
        assertNull(store.ownerOf(UUID.randomUUID()));
        assertTrue(store.pets(bob).isEmpty());
        assertTrue(store.remove(alice, pet));
        assertFalse(store.remove(alice, pet));
        assertNull(store.ownerOf(pet));
    }

    @Test
    void brokenFileStartsEmpty(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve(PetStore.FILE_NAME), "{ not json");
        PetStore store = new PetStore();
        store.load(dir);
        assertTrue(store.pets(UUID.randomUUID()).isEmpty());
    }

    @Test
    void cleanStoreDoesNotWrite(@TempDir Path dir) {
        PetStore store = new PetStore();
        store.load(dir);
        store.saveIfDirty();
        assertFalse(Files.exists(dir.resolve(PetStore.FILE_NAME)));
    }
}

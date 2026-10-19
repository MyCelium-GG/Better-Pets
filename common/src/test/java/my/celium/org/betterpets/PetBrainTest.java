package my.celium.org.betterpets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PetBrainTest {
    private static PetRecord pet(String name) {
        PetRecord pet = new PetRecord(UUID.randomUUID(), "minecraft:wolf");
        pet.name = name;
        return pet;
    }

    @Test
    void compassUsesMinecraftAxes() {
        assertEquals("N", PetBrain.compass(0, -10));
        assertEquals("S", PetBrain.compass(0, 10));
        assertEquals("E", PetBrain.compass(10, 0));
        assertEquals("W", PetBrain.compass(-10, 0));
        assertEquals("NE", PetBrain.compass(10, -10));
        assertEquals("SW", PetBrain.compass(-10, 10));
    }

    @Test
    void arrowIsRelativeToFacing() {
        // Facing south (yaw 0): south is ahead, west is to the right, north behind.
        assertEquals("↑", PetBrain.arrow(0, 0, 10));
        assertEquals("→", PetBrain.arrow(0, -10, 0));
        assertEquals("←", PetBrain.arrow(0, 10, 0));
        assertEquals("↓", PetBrain.arrow(0, 0, -10));
        // Facing north (yaw 180 or -180): north is ahead, east to the right.
        assertEquals("↑", PetBrain.arrow(180, 0, -10));
        assertEquals("↑", PetBrain.arrow(-180, 0, -10));
        assertEquals("→", PetBrain.arrow(180, 10, 0));
        assertEquals("↑→", PetBrain.arrow(180, 10, -10));
        assertEquals("↓←", PetBrain.arrow(180, -10, 10));
    }

    @Test
    void distancesAndAges() {
        assertEquals("0 m", PetBrain.distance(0.2));
        assertEquals("42 m", PetBrain.distance(41.6));
        assertEquals("1.3 km", PetBrain.distance(1290));
        assertEquals("just now", PetBrain.ago(20_000));
        assertEquals("5 min ago", PetBrain.ago(5 * 60_000));
        assertEquals("3 h ago", PetBrain.ago(3 * 3_600_000L));
        assertEquals("2 d ago", PetBrain.ago(50 * 3_600_000L));
        assertEquals("just now", PetBrain.ago(-5));
    }

    @Test
    void dimensionNames() {
        assertEquals("overworld", PetBrain.dimensionName("minecraft:overworld"));
        assertEquals("the nether", PetBrain.dimensionName("minecraft:the_nether"));
        assertEquals("unknown", PetBrain.dimensionName(null));
    }

    @Test
    void resolveByNumberNameAndPrefix() {
        PetRecord rex = pet("Rex");
        PetRecord unnamed = pet(null);
        PetRecord biscuit = pet("Biscuit");
        PetRecord bishop = pet("Bishop");
        List<PetRecord> pets = List.of(rex, unnamed, biscuit, bishop);

        assertEquals(List.of(unnamed), PetBrain.resolve(pets, "2"));
        assertEquals(List.of(unnamed), PetBrain.resolve(pets, "#2"));
        assertEquals(List.of(rex), PetBrain.resolve(pets, "rex"));
        assertEquals(List.of(biscuit), PetBrain.resolve(pets, "Bisc"));
        assertEquals(List.of(biscuit, bishop), PetBrain.resolve(pets, "bis"));
        assertTrue(PetBrain.resolve(pets, "Luna").isEmpty());
        assertTrue(PetBrain.resolve(pets, "9").isEmpty());
        assertTrue(PetBrain.resolve(pets, " ").isEmpty());
    }

    @Test
    void exactNameBeatsPrefix() {
        PetRecord bo = pet("Bo");
        PetRecord bob = pet("Bob");
        assertEquals(List.of(bo), PetBrain.resolve(List.of(bob, bo), "bo"));
    }

    @Test
    void namesAreCleaned() {
        assertEquals("Sir Barks", PetBrain.cleanName("  Sir   Barks "));
        assertEquals("Luna", PetBrain.cleanName("\"Luna\""));
        assertEquals("&6Gold <bold>Rex", PetBrain.cleanName("&6Gold <bold>Rex"));
        assertEquals("\u00a7cRed", PetBrain.cleanName("\u00a7cRed"));
        assertNull(PetBrain.cleanName("   "));
        assertNull(PetBrain.cleanName("Bad\u0007Bell"));
        assertNull(PetBrain.cleanName("x".repeat(PetBrain.MAX_TYPED_LENGTH + 1)));
    }

    @Test
    void commandArgumentsQuoteWhenNeeded() {
        assertEquals("Rex", PetBrain.commandArgument("Rex"));
        assertEquals("\"Sir Barks\"", PetBrain.commandArgument("Sir Barks"));
        assertEquals("\"Say \\\"hi\\\"\"", PetBrain.commandArgument("Say \"hi\""));
    }

    @Test
    void healthKeepsHalves() {
        assertEquals("20/20", PetBrain.health(20, 20));
        assertEquals("7.5/20", PetBrain.health(7.5f, 20));
        assertEquals("7.5/20", PetBrain.health(7.4f, 20));
    }

    @Test
    void recordSpecies() {
        PetRecord cat = new PetRecord(UUID.randomUUID(), "minecraft:cat");
        assertEquals("cat", cat.species());
        assertEquals("entity.minecraft.cat", cat.speciesKey());
        cat.name = "  ";
        assertNull(cat.customName());
    }
}

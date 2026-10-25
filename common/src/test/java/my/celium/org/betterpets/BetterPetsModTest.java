package my.celium.org.betterpets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import my.celium.org.Mycel;
import my.celium.org.testing.MycelTesting;

/** The mod boots on Mycel with a small, well-defaulted config. */
class BetterPetsModTest {
    @BeforeAll
    static void initOnce(@TempDir Path tempDir) {
        MycelTesting.useConfigDir(tempDir);
        BetterPetsMod.init();
    }

    @Test
    void registersWithMycel() {
        assertTrue(Mycel.getRegisteredMods().contains(BetterPetsMod.META));
        assertEquals("betterpets", BetterPetsMod.META.id());
    }

    @Test
    void configDefaults() {
        assertTrue(BetterPetsMod.enabled.get());
        assertTrue(BetterPetsMod.protectFromOwner.get());
        assertTrue(BetterPetsMod.strongLeads.get());
        assertTrue(BetterPetsMod.deathAlerts.get());
        assertTrue(BetterPetsMod.allowCall.get());
        assertEquals(30, BetterPetsMod.callCooldownSeconds.get());
        assertTrue(BetterPetsMod.allowRename.get());
        assertTrue(BetterPetsMod.allowGive.get());
        assertEquals(30, BetterPetsMod.findSeconds.get());
    }

    @Test
    void enableToggleFlowsThrough() {
        assertTrue(BetterPetsMod.isModEnabled());
        Mycel.setEnabled(BetterPetsMod.ID, false);
        try {
            assertFalse(BetterPetsMod.isModEnabled());
        } finally {
            Mycel.setEnabled(BetterPetsMod.ID, true);
        }
        assertTrue(BetterPetsMod.isModEnabled());
    }

    @Test
    void tickToleratesNoServer() {
        BetterPetsMod.tickServer(null);
    }
}

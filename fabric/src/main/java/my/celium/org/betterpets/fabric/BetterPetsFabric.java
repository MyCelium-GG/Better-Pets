package my.celium.org.betterpets.fabric;

import my.celium.org.betterpets.MissingMycel;
import my.celium.org.betterpets.PetGate;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric entrypoint. Without Mycel the game still starts: Better Pets stays off and tells every
 * player who joins. Everything that needs Mycel lives in {@link FabricSetup}.
 */
public final class BetterPetsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        if (!FabricLoader.getInstance().isModLoaded("mycel")) {
            MissingMycel.logOnce();
            ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> MissingMycel.tell(handler.getPlayer()));
            return;
        }
        PetGate.open();
        FabricSetup.start();
    }
}

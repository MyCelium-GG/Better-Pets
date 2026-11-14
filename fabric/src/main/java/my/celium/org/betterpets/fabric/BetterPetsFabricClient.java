package my.celium.org.betterpets.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/** Fabric client entrypoint: the pet screen's packets (only with Mycel installed). */
public final class BetterPetsFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (FabricLoader.getInstance().isModLoaded("mycel")) {
            FabricClientSetup.start();
        }
    }
}

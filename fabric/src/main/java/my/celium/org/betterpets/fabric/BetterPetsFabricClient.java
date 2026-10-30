package my.celium.org.betterpets.fabric;

import my.celium.org.fabric.FabricNetworking;
import net.fabricmc.api.ClientModInitializer;

/** Fabric client entrypoint: the pet screen's packets. */
public final class BetterPetsFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricNetworking.flushClient();
    }
}

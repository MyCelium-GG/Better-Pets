package my.celium.org.betterpets.forge;

import my.celium.org.betterpets.MissingMycel;
import my.celium.org.betterpets.PetGate;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Forge entrypoint. Without Mycel the game still starts: Better Pets stays off and tells
 * every player who joins. Everything that needs Mycel lives in {@link ForgeSetup}.
 */
@Mod("betterpets")
public final class BetterPetsForge {
    public BetterPetsForge(@SuppressWarnings("unused") FMLJavaModLoadingContext context) {
        if (!net.minecraftforge.fml.ModList.isLoaded("mycel")) {
            MissingMycel.logOnce();
            net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent.BUS.addListener(event -> {
                if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                    MissingMycel.tell(player);
                }
            });
            return;
        }
        PetGate.open();
        ForgeSetup.start();
    }
}

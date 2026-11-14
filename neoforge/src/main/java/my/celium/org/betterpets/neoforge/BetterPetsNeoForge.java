package my.celium.org.betterpets.neoforge;

import my.celium.org.betterpets.MissingMycel;
import my.celium.org.betterpets.PetGate;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * NeoForge entrypoint. Without Mycel the game still starts: Better Pets stays off and tells
 * every player who joins. Everything that needs Mycel lives in {@link NeoForgeSetup}.
 */
@Mod("betterpets")
public final class BetterPetsNeoForge {
    public BetterPetsNeoForge(@SuppressWarnings("unused") IEventBus modBus,
            @SuppressWarnings("unused") ModContainer container) {
        if (!net.neoforged.fml.ModList.get().isLoaded("mycel")) {
            MissingMycel.logOnce();
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                    (net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) -> {
                if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                    MissingMycel.tell(player);
                }
            });
            return;
        }
        PetGate.open();
        NeoForgeSetup.start();
    }
}

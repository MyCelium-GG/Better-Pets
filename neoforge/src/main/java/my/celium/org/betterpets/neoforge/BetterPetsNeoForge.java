package my.celium.org.betterpets.neoforge;

import my.celium.org.betterpets.BetterPetsMod;
import my.celium.org.betterpets.PetsCommand;
import my.celium.org.client.ClientHooks;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * NeoForge entrypoint: forwards the server tick, commands and incoming damage
 * to the common code; on clients also offers the Mycel config screen.
 */
@Mod(BetterPetsMod.ID)
public final class BetterPetsNeoForge {
    public BetterPetsNeoForge(@SuppressWarnings("unused") IEventBus modBus,
            @SuppressWarnings("unused") ModContainer container) {
        BetterPetsMod.init();
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> BetterPetsMod.tickServer(event.getServer()));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> PetsCommand.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((LivingIncomingDamageEvent event) -> {
            if (BetterPetsMod.shouldBlockDamage(event.getEntity(), event.getSource())) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((AttackEntityEvent event) -> {
            if (BetterPetsMod.shouldBlockAttack(event.getEntity(), event.getTarget())) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteract event) -> {
            if (BetterPetsMod.onInteract(event.getEntity(), event.getTarget(), event.getHand() == InteractionHand.MAIN_HAND)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        });
        if (FMLEnvironment.getDist().isClient()) {
            ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class,
                    () -> (ModContainer ignored, Screen parent) -> ClientHooks.createConfigScreen(parent,
                            BetterPetsMod.ID));
        }
    }
}

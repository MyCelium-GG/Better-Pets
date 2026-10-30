package my.celium.org.betterpets.forge;

import my.celium.org.betterpets.BetterPetsMod;
import my.celium.org.betterpets.PetsCommand;
import my.celium.org.client.ClientHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge entrypoint: forwards the server tick, commands and incoming damage to
 * the common code; on clients also offers the Mycel config screen. Forge has no
 * working event for clicking an entity, see {@link my.celium.org.betterpets.forge.mixin.PlayerMixin}.
 */
@Mod(BetterPetsMod.ID)
public final class BetterPetsForge {
    public BetterPetsForge(@SuppressWarnings("unused") FMLJavaModLoadingContext context) {
        BetterPetsMod.init();
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> BetterPetsMod.tickServer(event.server()));
        RegisterCommandsEvent.BUS.addListener(event -> PetsCommand.register(event.getDispatcher()));
        LivingAttackEvent.BUS.addListener(
                (LivingAttackEvent event) -> BetterPetsMod.shouldBlockDamage(event.getEntity(), event.getSource()));
        AttackEntityEvent.BUS.addListener(
                (AttackEntityEvent event) -> BetterPetsMod.shouldBlockAttack(event.getEntity(), event.getTarget()));
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory(
                            (Minecraft minecraft, Screen parent) -> ClientHooks.createConfigScreen(parent,
                                    BetterPetsMod.ID)));
        }
    }
}

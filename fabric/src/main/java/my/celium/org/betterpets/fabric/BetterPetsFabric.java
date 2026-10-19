package my.celium.org.betterpets.fabric;

import my.celium.org.betterpets.BetterPetsMod;
import my.celium.org.betterpets.PetsCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.world.InteractionResult;

/** Fabric entrypoint: forwards the server tick, commands and incoming damage to the common code. */
public final class BetterPetsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        BetterPetsMod.init();
        ServerTickEvents.END_SERVER_TICK.register(BetterPetsMod::tickServer);
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> PetsCommand.register(dispatcher));
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(
                (entity, source, amount) -> !BetterPetsMod.shouldBlockDamage(entity, source));
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
                BetterPetsMod.shouldBlockAttack(player, entity) ? InteractionResult.FAIL : InteractionResult.PASS);
    }
}

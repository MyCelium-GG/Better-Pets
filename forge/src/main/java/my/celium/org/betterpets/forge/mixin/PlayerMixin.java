package my.celium.org.betterpets.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import my.celium.org.betterpets.BetterPetsMod;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Forge declares an entity-click event, but no 26.x version ever fires it. */
@Mixin(Player.class)
abstract class PlayerMixin {
    @Inject(method = "interactOn", at = @At("HEAD"), cancellable = true)
    private void betterpets$openMenu(Entity entity, InteractionHand hand, Vec3 location,
            CallbackInfoReturnable<InteractionResult> result) {
        if (BetterPetsMod.onInteract((Player) (Object) this, entity, hand == InteractionHand.MAIN_HAND)) {
            result.setReturnValue(InteractionResult.SUCCESS);
        }
    }
}

package my.celium.org.betterpets.mixin;

import my.celium.org.betterpets.client.ClientLooks;
import my.celium.org.betterpets.client.winter.PetLookState;
import my.celium.org.betterpets.net.PetLooksPacket;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Copies the server's look for this pet into its render state, every frame. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void betterpets$extractLook(LivingEntity entity, LivingEntityRenderState state, float partialTicks,
            CallbackInfo ci) {
        if (state instanceof PetLookState look) {
            PetLooksPacket.Look known = ClientLooks.get(entity.getId());
            if (known == null) {
                look.betterpets$setLook(-1, 0, false);
            } else {
                look.betterpets$setLook(known.scarf(), known.snow(), known.asleep());
            }
        }
    }
}

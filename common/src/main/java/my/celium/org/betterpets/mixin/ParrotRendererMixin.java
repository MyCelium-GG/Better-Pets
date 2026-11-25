package my.celium.org.betterpets.mixin;

import my.celium.org.betterpets.client.winter.ParrotHatLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ParrotRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the Santa hat layer to the parrot renderer. */
@Mixin(ParrotRenderer.class)
public abstract class ParrotRendererMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void betterpets$addHatLayer(EntityRendererProvider.Context context, CallbackInfo ci) {
        if (!my.celium.org.betterpets.PetGate.on()) {
            return;
        }
        ParrotRenderer self = (ParrotRenderer) (Object) this;
        ((LivingEntityRendererAccessor) self).betterpets$addLayer(new ParrotHatLayer(self));
    }
}

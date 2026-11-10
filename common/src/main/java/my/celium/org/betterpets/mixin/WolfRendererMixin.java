package my.celium.org.betterpets.mixin;

import my.celium.org.betterpets.client.winter.WolfWinterLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WolfRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the scarf and snow layer to the dog renderer. */
@Mixin(WolfRenderer.class)
public abstract class WolfRendererMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void betterpets$addWinterLayer(EntityRendererProvider.Context context, CallbackInfo ci) {
        WolfRenderer self = (WolfRenderer) (Object) this;
        ((LivingEntityRendererAccessor) self).betterpets$addLayer(new WolfWinterLayer(self));
    }
}

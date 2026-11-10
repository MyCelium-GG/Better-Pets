package my.celium.org.betterpets.mixin;

import my.celium.org.betterpets.client.winter.CatWinterLayer;
import net.minecraft.client.renderer.entity.CatRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the scarf and snow layer to the cat renderer. */
@Mixin(CatRenderer.class)
public abstract class CatRendererMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void betterpets$addWinterLayer(EntityRendererProvider.Context context, CallbackInfo ci) {
        CatRenderer self = (CatRenderer) (Object) this;
        ((LivingEntityRendererAccessor) self).betterpets$addLayer(new CatWinterLayer(self));
    }
}

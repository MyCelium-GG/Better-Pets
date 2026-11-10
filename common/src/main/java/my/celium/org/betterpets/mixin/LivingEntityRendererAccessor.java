package my.celium.org.betterpets.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the renderer mixins add a layer, which vanilla only allows its own subclasses. */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererAccessor {
    @SuppressWarnings("rawtypes")
    @Invoker("addLayer")
    boolean betterpets$addLayer(RenderLayer layer);
}

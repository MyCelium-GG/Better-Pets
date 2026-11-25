package my.celium.org.betterpets.mixin;

import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Private Display setters, for the parrots' speech bubbles. */
@Mixin(Display.class)
public interface DisplayAccessor {
    @Invoker("setBillboardConstraints")
    void betterpets$setBillboardConstraints(Display.BillboardConstraints constraints);

    @Invoker("setPosRotInterpolationDuration")
    void betterpets$setPosRotInterpolationDuration(int ticks);
}

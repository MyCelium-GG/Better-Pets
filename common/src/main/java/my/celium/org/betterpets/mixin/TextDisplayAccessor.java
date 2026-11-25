package my.celium.org.betterpets.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Private text display setters, for the parrots' speech bubbles. */
@Mixin(Display.TextDisplay.class)
public interface TextDisplayAccessor {
    @Invoker("setText")
    void betterpets$setText(Component text);

    @Invoker("setLineWidth")
    void betterpets$setLineWidth(int width);

    @Invoker("setBackgroundColor")
    void betterpets$setBackgroundColor(int argb);
}

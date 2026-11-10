package my.celium.org.betterpets.mixin;

import my.celium.org.betterpets.client.winter.PetLookState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Gives every living render state room for a pet's scarf, snow and sleep. */
@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateMixin implements PetLookState {
    @Unique
    private int betterpets$scarf = -1;
    @Unique
    private int betterpets$snow;
    @Unique
    private boolean betterpets$asleep;

    @Override
    public int betterpets$scarf() {
        return betterpets$scarf;
    }

    @Override
    public int betterpets$snow() {
        return betterpets$snow;
    }

    @Override
    public boolean betterpets$asleep() {
        return betterpets$asleep;
    }

    @Override
    public void betterpets$setLook(int scarf, int snow, boolean asleep) {
        betterpets$scarf = scarf;
        betterpets$snow = snow;
        betterpets$asleep = asleep;
    }
}

package my.celium.org.betterpets.mixin;

import my.celium.org.betterpets.client.winter.PetLookState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Gives every living render state room for a pet's scarf, snow, sleep and hat. */
@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateMixin implements PetLookState {
    @Unique
    private int betterpets$scarf = -1;
    @Unique
    private int betterpets$snow;
    @Unique
    private boolean betterpets$asleep;
    @Unique
    private boolean betterpets$hat;

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
    public boolean betterpets$hat() {
        return betterpets$hat;
    }

    @Override
    public void betterpets$setLook(int scarf, int snow, boolean asleep, boolean hat) {
        betterpets$hat = hat;
        betterpets$scarf = scarf;
        betterpets$snow = snow;
        betterpets$asleep = asleep;
    }
}

package my.celium.org.betterpets.mixin;

import my.celium.org.betterpets.talk.ParrotTalk;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Parrot speech bubbles live a few seconds and are never written to the world. */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "shouldBeSaved", at = @At("HEAD"), cancellable = true)
    private void betterpets$skipBubbles(CallbackInfoReturnable<Boolean> cir) {
        if (((Entity) (Object) this).entityTags().contains(ParrotTalk.BUBBLE_TAG)) {
            cir.setReturnValue(false);
        }
    }
}

package my.celium.org.betterpets.mixin;

import my.celium.org.betterpets.client.winter.PetLookState;
import net.minecraft.client.model.animal.wolf.WolfModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A dog asleep on (or by) its owner's bed lies down: belly on the ground, front paws stretched
 * out under its chin, hind legs tucked in, tail flat. Vanilla dogs only stand and sit. The scarf
 * and snow models are dog models too, so they lie down with it.
 */
@Mixin(WolfModel.class)
public abstract class WolfModelMixin {

    @Shadow
    @Final
    protected ModelPart head;
    @Shadow
    @Final
    protected ModelPart body;
    @Shadow
    @Final
    protected ModelPart rightHindLeg;
    @Shadow
    @Final
    protected ModelPart leftHindLeg;
    @Shadow
    @Final
    protected ModelPart rightFrontLeg;
    @Shadow
    @Final
    protected ModelPart leftFrontLeg;
    @Shadow
    @Final
    protected ModelPart tail;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/WolfRenderState;)V", at = @At("TAIL"))
    private void betterpets$lieDown(WolfRenderState state, CallbackInfo ci) {
        if (state.isSitting || !(state instanceof PetLookState look) || !look.betterpets$asleep()) {
            return;
        }
        float scale = state.ageScale;
        float drop = 6.0F * scale;
        body.y += drop;
        head.y += drop + 0.5F * scale;
        head.xRot = 0.15F;
        head.yRot = 0.0F;
        tail.y += drop;
        tail.xRot = ((float) (Math.PI / 2)) + 0.35F;
        for (ModelPart leg : new ModelPart[] {rightFrontLeg, leftFrontLeg}) {
            leg.y += drop - 0.5F * scale;
            leg.xRot = -((float) (Math.PI / 2));
        }
        rightFrontLeg.x -= 0.2F * scale;
        leftFrontLeg.x += 0.2F * scale;
        for (ModelPart leg : new ModelPart[] {rightHindLeg, leftHindLeg}) {
            leg.y += drop - 0.5F * scale;
            leg.z -= 1.0F * scale;
            leg.xRot = -((float) (Math.PI / 2)) + 0.25F;
        }
        rightHindLeg.x -= 0.4F * scale;
        leftHindLeg.x += 0.4F * scale;
        ModelPart root = ((WolfModel) (Object) this).root();
        if (root.hasChild("upper_body")) {
            ModelPart mane = root.getChild("upper_body");
            mane.y += drop;
        }
    }
}

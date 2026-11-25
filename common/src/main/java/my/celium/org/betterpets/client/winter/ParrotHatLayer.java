package my.celium.org.betterpets.client.winter;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.animal.parrot.ParrotModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.ParrotRenderState;

/** A Santa hat on a parrot. */
public final class ParrotHatLayer extends RenderLayer<ParrotRenderState, ParrotModel> {
    public ParrotHatLayer(RenderLayerParent<ParrotRenderState, ParrotModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, ParrotRenderState state,
            float yRot, float xRot) {
        if (state instanceof PetLookState look && look.betterpets$hat()) {
            coloredCutoutModelCopyLayerRender(WinterModels.parrotHat(), WinterModels.HAT, poseStack, collector,
                    lightCoords, state, -1, 2);
        }
    }
}

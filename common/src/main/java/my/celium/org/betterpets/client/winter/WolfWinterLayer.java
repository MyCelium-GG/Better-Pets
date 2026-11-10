package my.celium.org.betterpets.client.winter;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.animal.wolf.WolfModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;

/** Snow and scarf on a grown dog, drawn after vanilla's collar. */
public final class WolfWinterLayer extends RenderLayer<WolfRenderState, WolfModel> {
    public WolfWinterLayer(RenderLayerParent<WolfRenderState, WolfModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, WolfRenderState state,
            float yRot, float xRot) {
        if (state.isBaby || !(state instanceof PetLookState look)) {
            return;
        }
        int snow = look.betterpets$snow();
        if (snow > 0) {
            coloredCutoutModelCopyLayerRender(WinterModels.wolfSnow(), WinterModels.WOLF_SNOW[Math.min(snow, 3) - 1],
                    poseStack, collector, lightCoords, state, -1, 2);
        }
        int scarf = look.betterpets$scarf();
        if (scarf >= 0) {
            coloredCutoutModelCopyLayerRender(WinterModels.wolfScarf(), WinterModels.SCARF, poseStack, collector,
                    lightCoords, state, 0xFF000000 | scarf, 3);
        }
    }
}

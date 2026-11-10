package my.celium.org.betterpets.client.winter;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.animal.feline.AbstractFelineModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.CatRenderState;

/** Snow and scarf on a grown cat, drawn after vanilla's collar. */
public final class CatWinterLayer extends RenderLayer<CatRenderState, AbstractFelineModel<CatRenderState>> {
    public CatWinterLayer(RenderLayerParent<CatRenderState, AbstractFelineModel<CatRenderState>> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, CatRenderState state,
            float yRot, float xRot) {
        if (state.isBaby || !(state instanceof PetLookState look)) {
            return;
        }
        int snow = look.betterpets$snow();
        if (snow > 0) {
            coloredCutoutModelCopyLayerRender(WinterModels.catSnow(), WinterModels.CAT_SNOW[Math.min(snow, 3) - 1],
                    poseStack, collector, lightCoords, state, -1, 2);
        }
        int scarf = look.betterpets$scarf();
        if (scarf >= 0) {
            coloredCutoutModelCopyLayerRender(WinterModels.catScarf(), WinterModels.SCARF, poseStack, collector,
                    lightCoords, state, 0xFF000000 | scarf, 3);
        }
    }
}

package my.celium.org.betterpets.client.winter;

import my.celium.org.betterpets.BetterPetsMod;
import net.minecraft.client.model.animal.feline.AdultCatModel;
import net.minecraft.client.model.animal.feline.AdultFelineModel;
import net.minecraft.client.model.animal.wolf.AdultWolfModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;

/**
 * The extra models the winter layers draw on top of a dog or cat. Each one has the same parts as
 * the vanilla model, so vanilla's animation (walking, sitting, shaking) moves it along:
 *
 * <ul>
 *   <li>the scarf: a knitted band around the neck plus an end hanging down the chest, on the part
 *       that carries the neck, with every other part left empty;</li>
 *   <li>the snow: the vanilla model a hair bigger, painted white only where snow would settle
 *       (back, head, top of the tail).</li>
 * </ul>
 */
public final class WinterModels {
    public static final Identifier SCARF = texture("scarf");
    public static final Identifier[] WOLF_SNOW = {texture("wolf_snow_1"), texture("wolf_snow_2"), texture("wolf_snow_3")};
    public static final Identifier[] CAT_SNOW = {texture("cat_snow_1"), texture("cat_snow_2"), texture("cat_snow_3")};

    private static final float QUARTER = (float) (Math.PI / 2);
    private static AdultWolfModel wolfScarf;
    private static AdultWolfModel wolfSnow;
    private static AdultCatModel catScarf;
    private static AdultCatModel catSnow;

    private WinterModels() {
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(BetterPetsMod.ID, "textures/entity/winter/" + name + ".png");
    }

    public static AdultWolfModel wolfScarf() {
        if (wolfScarf == null) {
            wolfScarf = new AdultWolfModel(bake(wolfScarfMesh(), false));
        }
        return wolfScarf;
    }

    public static AdultWolfModel wolfSnow() {
        if (wolfSnow == null) {
            wolfSnow = new AdultWolfModel(bake(AdultWolfModel.createBodyLayer(new CubeDeformation(0.06F)), false));
        }
        return wolfSnow;
    }

    public static AdultCatModel catScarf() {
        if (catScarf == null) {
            catScarf = new AdultCatModel(bake(catScarfMesh(), true));
        }
        return catScarf;
    }

    public static AdultCatModel catSnow() {
        if (catSnow == null) {
            catSnow = new AdultCatModel(bake(AdultFelineModel.createBodyMesh(new CubeDeformation(0.04F)), true));
        }
        return catSnow;
    }

    private static ModelPart bake(MeshDefinition mesh, boolean cat) {
        LayerDefinition layer = LayerDefinition.create(mesh, 64, 32);
        return (cat ? layer.apply(AdultCatModel.CAT_TRANSFORMER) : layer).bakeRoot();
    }

    /** Vanilla's wolf parts and pivots, with the scarf on {@code upper_body} (the mane). */
    private static MeshDefinition wolfScarfMesh() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(-1.0F, 13.5F, -7.0F));
        head.addOrReplaceChild("real_head", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("body", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0.0F, 14.0F, 2.0F, QUARTER, 0.0F, 0.0F));
        // The mane is turned a quarter: its local -Y faces forward, local -Z faces down.
        root.addOrReplaceChild("upper_body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3.0F, -3.0F, -3.0F, 8.0F, 2.0F, 7.0F, new CubeDeformation(0.45F))
                        .texOffs(0, 10).addBox(1.5F, -4.1F, -8.0F, 2.0F, 1.0F, 5.0F, new CubeDeformation(0.12F)),
                PartPose.offsetAndRotation(-1.0F, 14.0F, -3.0F, QUARTER, 0.0F, 0.0F));
        root.addOrReplaceChild("right_hind_leg", CubeListBuilder.create(), PartPose.offset(-2.5F, 16.0F, 7.0F));
        root.addOrReplaceChild("left_hind_leg", CubeListBuilder.create(), PartPose.offset(0.5F, 16.0F, 7.0F));
        root.addOrReplaceChild("right_front_leg", CubeListBuilder.create(), PartPose.offset(-2.5F, 16.0F, -4.0F));
        root.addOrReplaceChild("left_front_leg", CubeListBuilder.create(), PartPose.offset(0.5F, 16.0F, -4.0F));
        PartDefinition tail = root.addOrReplaceChild("tail", CubeListBuilder.create(),
                PartPose.offsetAndRotation(-1.0F, 12.0F, 8.0F, (float) (Math.PI / 5), 0.0F, 0.0F));
        tail.addOrReplaceChild("real_tail", CubeListBuilder.create(), PartPose.ZERO);
        return mesh;
    }

    /** Vanilla's cat parts and pivots, with the scarf at the front end of the body. */
    private static MeshDefinition catScarfMesh() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0.0F, 15.0F, -9.0F));
        // The body is turned a quarter: its local +Y runs backwards, local -Z faces down.
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-2.0F, 3.0F, -8.0F, 4.0F, 2.0F, 6.0F, new CubeDeformation(0.4F))
                        .texOffs(0, 10).addBox(0.2F, 2.2F, -11.0F, 1.5F, 1.0F, 3.0F, new CubeDeformation(0.1F)),
                PartPose.offsetAndRotation(0.0F, 12.0F, -10.0F, QUARTER, 0.0F, 0.0F));
        root.addOrReplaceChild("tail1", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 15.0F, 8.0F, 0.9F, 0.0F, 0.0F));
        root.addOrReplaceChild("tail2", CubeListBuilder.create(), PartPose.offset(0.0F, 20.0F, 14.0F));
        root.addOrReplaceChild("left_hind_leg", CubeListBuilder.create(), PartPose.offset(1.1F, 18.0F, 5.0F));
        root.addOrReplaceChild("right_hind_leg", CubeListBuilder.create(), PartPose.offset(-1.1F, 18.0F, 5.0F));
        root.addOrReplaceChild("left_front_leg", CubeListBuilder.create(), PartPose.offset(1.2F, 14.1F, -5.0F));
        root.addOrReplaceChild("right_front_leg", CubeListBuilder.create(), PartPose.offset(-1.2F, 14.1F, -5.0F));
        return mesh;
    }
}

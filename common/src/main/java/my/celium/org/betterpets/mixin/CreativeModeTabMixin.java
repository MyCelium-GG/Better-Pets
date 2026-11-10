package my.celium.org.betterpets.mixin;

import java.util.Collection;
import java.util.Set;
import my.celium.org.betterpets.BetterPetsMod;
import my.celium.org.betterpets.winter.Scarves;
import my.celium.org.betterpets.winter.PetScarfItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the Pet Scarf to the Tools and Utilities tab, so it can be found with the creative
 * search. Done here because each loader has its own tab event.
 */
@Mixin(CreativeModeTab.class)
public abstract class CreativeModeTabMixin {
    @Shadow
    private Collection<ItemStack> displayItems;

    @Shadow
    private Set<ItemStack> displayItemsSearchTab;

    @Inject(method = "buildContents", at = @At("TAIL"))
    private void betterpets$addItems(CreativeModeTab.ItemDisplayParameters parameters, CallbackInfo ci) {
        Identifier id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey((CreativeModeTab) (Object) this);
        if (id == null || !id.getNamespace().equals("minecraft") || !id.getPath().equals("tools_and_utilities")) {
            return;
        }
        if (BetterPetsMod.petScarf == null) {
            return;
        }
        ItemStack stack = Scarves.item(PetScarfItem.DEFAULT_COLOR);
        displayItems.add(stack);
        displayItemsSearchTab.add(stack.copy());
    }
}

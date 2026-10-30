package my.celium.org.betterpets;

import java.util.ArrayList;
import java.util.List;

import my.celium.org.util.TextUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** All of a player's pets, the menu form of {@code /pets}: click one to open its screen. */
final class PetListMenu extends PetGui {
    private PetListMenu(int containerId, ServerPlayer viewer, int rows) {
        super(containerId, viewer, rows);
    }

    static void open(ServerPlayer player) {
        int rows = PetBrain.gridRows(PetTracker.STORE.pets(player.getUUID()).size());
        PetGui.open(player, TextUtil.body("Your pets"), id -> new PetListMenu(id, player, rows));
    }

    @Override
    protected void render() {
        List<PetRecord> pets = PetTracker.STORE.pets(viewer.getUUID());
        grid(pets, this::icon, pet -> PetMenu.open(viewer, pet));
        button(getContainer().getContainerSize() - 5, icon(Items.BARRIER, TextUtil.hex("Close", PetText.DEAD)),
                this::close);
    }

    private ItemStack icon(PetRecord pet) {
        List<Component> lore = new ArrayList<>();
        lore.add(PetText.species(pet).withStyle(style -> style.withColor(TextUtil.MUTED)));
        if (pet.dead) {
            lore.add(TextUtil.hex("Died " + PetBrain.ago(System.currentTimeMillis() - pet.lastSeen), PetText.DEAD));
        } else {
            lore.add(TextUtil.hex("♥ ", PetText.HEART).append(TextUtil.body(PetBrain.health(pet.health, pet.maxHealth))));
            lore.add(TextUtil.body(PetsCommand.placeText(viewer, pet)));
        }
        return named(new ItemStack(pet.dead ? Items.SKELETON_SKULL : PetMenu.egg(pet)), PetText.name(pet), lore);
    }
}

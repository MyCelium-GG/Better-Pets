package my.celium.org.betterpets;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import my.celium.org.util.TextUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

/** Picks who gets a pet: online players as heads. Giving can't be undone, so it takes two clicks. */
final class PetGiveMenu extends PetGui {
    private final PetRecord pet;
    private UUID armed;

    private PetGiveMenu(int containerId, ServerPlayer viewer, PetRecord pet, int rows) {
        super(containerId, viewer, rows);
        this.pet = pet;
    }

    static void open(ServerPlayer player, PetRecord pet) {
        int rows = PetBrain.gridRows(others(player).size());
        PetGui.open(player, TextUtil.coloredHex(PetText.c(player, "give.title", PetText.name(pet)), TextUtil.BODY),
                id -> new PetGiveMenu(id, player, pet, rows));
    }

    private static List<ServerPlayer> others(ServerPlayer player) {
        List<ServerPlayer> others = new ArrayList<>(player.level().getServer().getPlayerList().getPlayers());
        others.remove(player);
        return others;
    }

    @Override
    protected void render() {
        List<ServerPlayer> others = others(viewer);
        if (others.isEmpty()) {
            put(4, icon(Items.BARRIER, TextUtil.muted(PetText.t(viewer, "give.nobody"))));
        }
        grid(others, this::head, this::click);
        button(getContainer().getContainerSize() - 5, icon(Items.ARROW, TextUtil.hex(PetText.t(viewer, "menu.back"), PetText.BUTTON)),
                () -> PetMenu.open(viewer, pet));
    }

    private ItemStack head(ServerPlayer target) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(target.getGameProfile()));
        boolean confirm = target.getUUID().equals(armed);
        return named(head, target.getDisplayName(), List.of(confirm
                ? TextUtil.hex(PetText.t(viewer, "give.again"), PetText.DEAD)
                : TextUtil.muted(PetText.t(viewer, "give.choose"))));
    }

    private void click(ServerPlayer target) {
        if (!target.getUUID().equals(armed)) {
            armed = target.getUUID();
            return;
        }
        close();
        PetsCommand.give(viewer, pet, target);
    }
}

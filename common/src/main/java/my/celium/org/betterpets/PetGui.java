package my.celium.org.betterpets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import my.celium.org.logging.MycelLog;
import my.celium.org.util.TextUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A chest screen whose items are buttons. It is a plain vanilla chest as far
 * as the client knows, so players need nothing installed. The items only exist
 * in this menu and nothing can be picked up: every click ends in
 * {@link #clicked}, which never moves a stack.
 */
abstract class PetGui extends ChestMenu {
    private static final MenuType<?>[] TYPES = {MenuType.GENERIC_9x1, MenuType.GENERIC_9x2, MenuType.GENERIC_9x3,
            MenuType.GENERIC_9x4, MenuType.GENERIC_9x5, MenuType.GENERIC_9x6};
    /** Clicks waiting for the next tick. */
    private static final List<Runnable> PENDING = new ArrayList<>();

    protected final ServerPlayer viewer;
    private final Map<Integer, Runnable> buttons = new HashMap<>();
    private int page;

    /** Creates one of the screens, given its window id. */
    interface Factory {
        PetGui create(int containerId);
    }

    protected PetGui(int containerId, ServerPlayer viewer, int rows) {
        super(TYPES[rows - 1], containerId, viewer.getInventory(), new SimpleContainer(9 * rows), rows);
        this.viewer = viewer;
    }

    static void open(ServerPlayer player, Component title, Factory factory) {
        player.openMenu(new SimpleMenuProvider((id, inventory, ignored) -> {
            PetGui gui = factory.create(id);
            gui.refresh();
            return gui;
        }, title));
    }

    /** Draws the current state. Called when opening, once a second and after every click. */
    protected abstract void render();

    /**
     * Runs the clicks of the last tick. They wait because the vanilla click
     * handler keeps talking to the player's open menu after {@link #clicked}
     * returns, which goes wrong when a button has just opened another one.
     */
    static void runPending() {
        if (PENDING.isEmpty()) {
            return;
        }
        List<Runnable> clicks = new ArrayList<>(PENDING);
        PENDING.clear();
        for (Runnable click : clicks) {
            try {
                click.run();
            } catch (RuntimeException e) {
                MycelLog.warnMod(BetterPetsMod.ID, "A menu button failed: {}", e.toString());
            }
        }
    }

    static void clearPending() {
        PENDING.clear();
    }

    /** Redraws every open screen, once a second: health and distance change while you look. */
    static void refreshOpen(Iterable<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            if (player.containerMenu instanceof PetGui gui) {
                gui.refresh();
            }
        }
    }

    final void refresh() {
        buttons.clear();
        ItemStack filler = filler();
        for (int slot = 0; slot < getContainer().getContainerSize(); slot++) {
            getContainer().setItem(slot, filler);
        }
        render();
    }

    @Override
    public void clicked(int slot, int button, ContainerInput input, Player player) {
        Runnable action = buttons.get(slot);
        if (action == null || (input != ContainerInput.PICKUP && input != ContainerInput.QUICK_MOVE)) {
            return;
        }
        PENDING.add(() -> {
            if (viewer.containerMenu == this) {
                viewer.connection.send(new ClientboundSoundPacket(SoundEvents.UI_BUTTON_CLICK, SoundSource.MASTER,
                        viewer.getX(), viewer.getY(), viewer.getZ(), 0.4f, 1f, viewer.getRandom().nextLong()));
                action.run();
                if (viewer.containerMenu == this) {
                    refresh();
                }
            }
        });
    }

    protected final void put(int slot, ItemStack icon) {
        getContainer().setItem(slot, icon);
    }

    protected final void button(int slot, ItemStack icon, Runnable action) {
        put(slot, icon);
        buttons.put(slot, action);
    }

    protected final void close() {
        viewer.closeContainer();
    }

    /** {@code entries} in every row but the last, with arrows there when they need more than one page. */
    protected final <T> void grid(List<T> entries, Function<T, ItemStack> icon, Consumer<T> action) {
        int perPage = getContainer().getContainerSize() - 9;
        int pages = PetBrain.pages(entries.size(), perPage);
        page = Math.min(page, pages - 1);
        for (int i = 0; i < perPage && page * perPage + i < entries.size(); i++) {
            T entry = entries.get(page * perPage + i);
            button(i, icon.apply(entry), () -> action.accept(entry));
        }
        if (page > 0) {
            button(perPage, icon(Items.ARROW, TextUtil.hex("Previous page", PetText.BUTTON)), () -> page--);
        }
        if (page < pages - 1) {
            button(perPage + 8, icon(Items.ARROW, TextUtil.hex("Next page", PetText.BUTTON),
                    PetText.dim((page + 1) + " / " + pages)), () -> page++);
        }
    }

    /** An item with a name and tooltip lines, without the italics the game gives both by default. */
    protected static ItemStack icon(Item item, Component name, Component... lore) {
        ItemStack stack = new ItemStack(item);
        return named(stack, name, List.of(lore));
    }

    protected static ItemStack named(ItemStack stack, Component name, List<Component> lore) {
        stack.set(DataComponents.CUSTOM_NAME, upright(name));
        List<Component> lines = new ArrayList<>(lore.size());
        for (Component line : lore) {
            lines.add(upright(line));
        }
        stack.set(DataComponents.LORE, new ItemLore(lines));
        stack.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT
                .withHidden(DataComponents.ATTRIBUTE_MODIFIERS, true));
        return stack;
    }

    private static MutableComponent upright(Component text) {
        return TextUtil.empty().append(text).withStyle(style -> style.withItalic(false));
    }

    /** Stained glass by color name; the lines keep these items in different fields, so go by id. */
    protected static Item pane(String color) {
        return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(color + "_stained_glass_pane"));
    }

    /** A pane without a tooltip. */
    private static ItemStack filler() {
        ItemStack pane = new ItemStack(pane("black"));
        pane.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, TooltipDisplay.DEFAULT.hiddenComponents()));
        return pane;
    }
}

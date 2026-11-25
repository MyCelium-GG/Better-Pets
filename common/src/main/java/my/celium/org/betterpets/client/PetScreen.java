package my.celium.org.betterpets.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import my.celium.org.betterpets.PetBrain;
import my.celium.org.betterpets.net.PetActionPacket;
import my.celium.org.betterpets.net.PetActionPacket.Action;
import my.celium.org.betterpets.net.PetsPacket;
import my.celium.org.client.MycelScreens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

/**
 * The pet menu, as a pet book: an open book made from the game's own book page (drawn twice, the
 * left one mirrored, so the stitching meets at the spine). Every pet has its own spread: the left
 * page shows its name (click to rename), its kind and the pet itself, the right page its health,
 * whereabouts, scarf and food in ink. The first spread is a table of contents with every pet.
 * Turn pages with the arrows, the arrow keys or the mouse wheel; the actions are normal buttons
 * under the book, like the buttons under a book and quill.
 *
 * <p>All state comes from the server in {@link PetsPacket}; every click goes back as a
 * {@link PetActionPacket}.
 */
public final class PetScreen extends Screen {
    private static final Identifier HEART_EMPTY = Identifier.withDefaultNamespace("hud/heart/container");
    private static final Identifier HEART_FULL = Identifier.withDefaultNamespace("hud/heart/full");
    private static final Identifier HEART_HALF = Identifier.withDefaultNamespace("hud/heart/half");
    private static final Identifier SLOT = Identifier.withDefaultNamespace("container/slot");

    /**
     * One page of the vanilla book texture (256x256) without its cover strip on the stitched side:
     * x 26 to 166, y 1 to 181. The two pages meet at the stitching and share one spine.
     */
    private static final int PAGE_U = 26;
    private static final int PAGE_V = 1;
    private static final int PAGE_W = 140;
    private static final int SPINE_MARGIN = 11;
    private static final int PAGE_H = 180;
    private static final int BOOK_W = PAGE_W * 2;
    private static final int MARGIN = 17;
    private static final int BUTTON_H = 20;
    private static final int BUTTON_GAP = 9;
    private static final int SETTINGS_ROW = 11;
    private static final int GEAR_W = 15;
    private static final int GEAR_H = 10;
    private static final int SWATCH_W = 18;
    private static final int SWATCH_STEP = 23;
    private static final int ROW = 12;
    private static final int LABEL_W = 46;
    private static final int TILE = 18;
    private static final int CONTENTS_ROW = 18;
    private static final int CONTENTS_TOP = 34;
    private static final int KEY_ENTER = 257;
    private static final int KEY_ESCAPE = 256;
    private static final int KEY_LEFT = 263;
    private static final int KEY_RIGHT = 262;
    private static final long NOTICE_MS = 3500;

    /** Ink colours, close to the written book's. */
    private static final int INK = 0xFF2B2117;
    private static final int FADED = 0xFF7D6B55;
    private static final int RULE = 0x40553F28;
    private static final int RED = 0xFFA3282A;
    private static final int RED_HOT = 0xFFD2382F;
    private static final int HOVER = 0x22553F28;

    private PetsPacket state;
    private EditBox nameBox;
    private Button mainButton;
    private Button findButton;
    private Button callButton;
    private Button giveButton;
    private Button cancelButton;
    private Button doneButton;
    private PageButton backPage;
    private PageButton nextPage;
    private UUID shownPet;
    /** The contents page shown, or -1 while a pet's spread is open. */
    private int contentsPage = -1;
    private boolean renaming;
    private boolean giving;
    private UUID armedPlayer;
    private Runnable mainAction = () -> { };
    private String notice = "";
    private boolean settingsOpen;
    private int settingsScroll;
    private long noticeAt;

    PetScreen(PetsPacket state) {
        super(Component.literal(tr("your_pets")));
        this.state = state;
    }

    /** New data from the server. */
    void update(PetsPacket packet) {
        state = packet;
        if (!packet.notice().isEmpty()) {
            notice = packet.notice();
            noticeAt = System.currentTimeMillis();
        }
        if (nameBox != null) {
            sync();
        }
    }

    // ================================================================ layout

    private int bookX() {
        return (width - BOOK_W) / 2;
    }

    private int bookY() {
        return Math.max(GEAR_H + 2, (height - PAGE_H - BUTTON_GAP - BUTTON_H - 14) / 2 + 4);
    }

    /** Text area of the left page; the stitching is on its right, at the spine. */
    private int leftX0() {
        return bookX() + MARGIN;
    }

    private int leftX1() {
        return bookX() + PAGE_W - SPINE_MARGIN;
    }

    private int rightX0() {
        return bookX() + PAGE_W + SPINE_MARGIN;
    }

    private int rightX1() {
        return bookX() + BOOK_W - MARGIN;
    }

    private int pageTop() {
        return bookY() + 15;
    }

    private int arrowY() {
        return bookY() + PAGE_H - 25;
    }

    private int nameY() {
        return pageTop() + 2;
    }

    private int feedY() {
        return arrowY() - TILE - 6;
    }

    private int scarfY() {
        return feedY() - 15;
    }

    private int buttonY() {
        return bookY() + PAGE_H + BUTTON_GAP;
    }

    private int contentsPerPage() {
        return (arrowY() - 4 - (bookY() + CONTENTS_TOP)) / CONTENTS_ROW;
    }

    private int contentsPages() {
        int perSpread = contentsPerPage() * 2;
        return Math.max(1, (state.pets().size() + perSpread - 1) / perSpread);
    }

    private int pageCount() {
        return contentsPages() + state.pets().size();
    }

    private int currentPage() {
        if (contentsPage >= 0) {
            return contentsPage;
        }
        return contentsPages() + Math.max(0, state.pets().indexOf(selected()));
    }

    // ================================================================ widgets

    @Override
    protected void init() {
        nameBox = new EditBox(font, leftX0(), nameY(), leftX1() - leftX0(), 12, Component.literal(tr("button.name")));
        nameBox.setMaxLength(PetBrain.MAX_TYPED_LENGTH);
        nameBox.setBordered(false);
        nameBox.setTextColor(INK);
        nameBox.setHint(Component.literal(tr("book.name_hint")));
        addRenderableWidget(nameBox);

        int w = (BOOK_W - BUTTON_GAP * 4) / 5;
        int x = bookX();
        int y = buttonY();
        mainButton = addRenderableWidget(Button.builder(Component.literal(tr("action.sit")), b -> mainAction.run())
                .bounds(x, y, w, BUTTON_H).build());
        findButton = addRenderableWidget(Button.builder(Component.literal(tr("button.find")), b -> sendAndClose(Action.FIND))
                .bounds(x + (w + BUTTON_GAP), y, w, BUTTON_H).build());
        callButton = addRenderableWidget(Button.builder(Component.literal(tr("button.call")), b -> sendAndClose(Action.CALL))
                .bounds(x + (w + BUTTON_GAP) * 2, y, w, BUTTON_H).build());
        giveButton = addRenderableWidget(Button.builder(Component.literal(tr("book.give")), b -> {
            giving = true;
            armedPlayer = null;
            stopRenaming();
            sync();
        }).bounds(x + (w + BUTTON_GAP) * 3, y, w, BUTTON_H).build());
        cancelButton = addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> {
            giving = false;
            sync();
        }).bounds(x + (w + BUTTON_GAP) * 3, y, w, BUTTON_H).build());
        doneButton = addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(bookX() + BOOK_W - w, y, w, BUTTON_H).build());

        backPage = addRenderableWidget(new PageButton(bookX() + MARGIN + 4, arrowY(), false, b -> turn(-1), true));
        nextPage = addRenderableWidget(new PageButton(bookX() + BOOK_W - MARGIN - 27, arrowY(), true, b -> turn(1), true));
        shownPet = null;
        sync();
    }

    /** Makes the widgets match the state. */
    private void sync() {
        PetsPacket.Pet pet = selected();
        PetsPacket.Details d = state.details();
        if (pet != null && !pet.id().equals(shownPet)) {
            shownPet = pet.id();
            giving = false;
            stopRenaming();
        }
        boolean onPet = contentsPage < 0 && pet != null;
        boolean alive = onPet && !pet.dead();
        if (!alive) {
            stopRenaming();
        }
        nameBox.visible = renaming;

        boolean far = onPet && !d.near();
        boolean forgettable = onPet && (pet.dead() || !pet.loaded());
        if (onPet && pet.dead()) {
            setMain(tr("button.forget"), () -> send(Action.FORGET), true);
        } else if (onPet && d.canSit()) {
            setMain(tr(d.sitting() ? "book.stand" : "action.sit"), () -> send(Action.SIT), d.near());
        } else if (onPet && d.hasInventory()) {
            setMain(tr("book.gear"), () -> send(Action.INVENTORY), d.near());
        } else if (forgettable) {
            setMain(tr("button.forget"), () -> send(Action.FORGET), true);
        } else {
            setMain(tr("action.sit"), () -> { }, false);
        }
        mainButton.setTooltip(far && onPet && !pet.dead() && (d.canSit() || d.hasInventory())
                ? Tooltip.create(Component.literal(tr("book.far")))
                : null);
        findButton.active = alive;
        callButton.active = alive && state.allowCall();
        callButton.setTooltip(alive && far && state.allowCall()
                ? Tooltip.create(Component.literal(tr("book.call.tip"))) : null);
        giveButton.active = alive && state.allowGive();

        mainButton.visible = !giving;
        findButton.visible = !giving;
        callButton.visible = !giving;
        giveButton.visible = !giving;
        cancelButton.visible = giving;

        backPage.visible = currentPage() > 0;
        nextPage.visible = currentPage() < pageCount() - 1;
        if (settingsOpen) {
            for (var child : children()) {
                if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                    widget.visible = false;
                }
            }
        } else {
            doneButton.visible = true;
        }
    }

    private void setMain(String label, Runnable action, boolean active) {
        mainButton.setMessage(Component.literal(label));
        mainAction = action;
        mainButton.active = active;
    }

    private static String typedName(PetsPacket.Pet pet) {
        return pet == null ? "" : pet.format().isEmpty() ? pet.name() : pet.format();
    }

    private PetsPacket.Pet selected() {
        for (PetsPacket.Pet pet : state.pets()) {
            if (pet.id().equals(state.selected())) {
                return pet;
            }
        }
        return null;
    }

    // ================================================================ actions

    private void send(Action action) {
        ClientPets.send(new PetActionPacket(action, state.selected()));
    }

    private void sendAndClose(Action action) {
        send(action);
        onClose();
    }

    /** Opens the spread of pet {@code index}. */
    private void openPet(int index) {
        if (index < 0 || index >= state.pets().size()) {
            return;
        }
        PetsPacket.Pet pet = state.pets().get(index);
        contentsPage = -1;
        giving = false;
        if (!pet.id().equals(state.selected())) {
            ClientPets.send(new PetActionPacket(Action.SELECT, pet.id()));
        }
        sync();
    }

    /** Goes to book page {@code page}: the contents first, then one page per pet. */
    private void goTo(int page) {
        page = Math.clamp(page, 0, pageCount() - 1);
        if (page == currentPage()) {
            return;
        }
        stopRenaming();
        if (page < contentsPages()) {
            contentsPage = page;
            giving = false;
            sync();
        } else {
            openPet(page - contentsPages());
        }
    }

    private void turn(int delta) {
        goTo(currentPage() + delta);
    }

    private void startRenaming() {
        PetsPacket.Pet pet = selected();
        if (pet == null || pet.dead() || !state.allowRename()) {
            return;
        }
        renaming = true;
        nameBox.setValue(typedName(pet));
        nameBox.visible = true;
        nameBox.moveCursorToEnd(false);
        setFocused(nameBox);
        nameBox.setFocused(true);
    }

    private void stopRenaming() {
        renaming = false;
        if (nameBox != null) {
            nameBox.setFocused(false);
            nameBox.visible = false;
        }
        if (getFocused() == nameBox) {
            setFocused(null);
        }
    }

    private void saveName() {
        PetsPacket.Pet pet = selected();
        if (pet != null && !nameBox.getValue().equals(typedName(pet))) {
            ClientPets.send(new PetActionPacket(Action.RENAME, state.selected(), 0, nameBox.getValue()));
        }
        stopRenaming();
    }

    private void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
    }

    private void pageSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1f));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        MycelScreens.open(null);
    }

    @Override
    public void removed() {
        ClientPets.send(new PetActionPacket(Action.CLOSED, state.selected()));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (settingsOpen) {
            if (event.key() == KEY_ESCAPE) {
                closeSettings();
            }
            return true;
        }
        if (renaming) {
            if (event.key() == KEY_ENTER) {
                saveName();
                return true;
            }
            if (event.key() == KEY_ESCAPE) {
                stopRenaming();
                return true;
            }
            return super.keyPressed(event);
        }
        if (giving && event.key() == KEY_ESCAPE) {
            giving = false;
            sync();
            return true;
        }
        if (event.key() == KEY_LEFT && currentPage() > 0) {
            turn(-1);
            pageSound();
            return true;
        }
        if (event.key() == KEY_RIGHT && currentPage() < pageCount() - 1) {
            turn(1);
            pageSound();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        if (renaming && !nameBox.isMouseOver(mx, my)) {
            saveName();
        }
        if (settingsOpen) {
            settingsClick(mx, my, event.hasShiftDown());
            return true;
        }
        if (overGear(mx, my)) {
            openSettings();
            return true;
        }
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (contentsPage >= 0) {
            int pet = contentsAt(mx, my);
            if (pet >= 0) {
                openPet(pet);
                pageSound();
                return true;
            }
            return false;
        }
        if (giving) {
            int person = personAt(mx, my);
            if (person >= 0) {
                UUID id = state.players().get(person).id();
                if (id.equals(armedPlayer)) {
                    ClientPets.send(new PetActionPacket(Action.GIVE, state.selected(), 0, id.toString()));
                    giving = false;
                    sync();
                } else {
                    armedPlayer = id;
                }
                click();
                return true;
            }
            return false;
        }
        if (overName(mx, my)) {
            startRenaming();
            return true;
        }
        if (overTakeOff(mx, my)) {
            send(Action.SCARF_OFF);
            click();
            return true;
        }
        int food = foodAt(mx, my);
        if (food >= 0) {
            ClientPets.send(new PetActionPacket(Action.FEED, state.selected(), state.details().foods().get(food).slot(), ""));
            click();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (settingsOpen) {
            int most = Math.max(0, state.settings().size() - settingsRows());
            settingsScroll = Math.clamp(settingsScroll + (scrollY > 0 ? -1 : 1), 0, most);
            return true;
        }
        if (scrollY != 0 && !renaming) {
            int before = currentPage();
            turn(scrollY > 0 ? -1 : 1);
            if (currentPage() != before) {
                pageSound();
            }
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    // ================================================================ hit tests

    /** The contents row under the cursor, as a pet index, or -1. */
    private int contentsAt(double mx, double my) {
        int perPage = contentsPerPage();
        int top = bookY() + CONTENTS_TOP;
        if (my < top || my >= top + perPage * CONTENTS_ROW) {
            return -1;
        }
        int column;
        if (mx >= leftX0() && mx < leftX1()) {
            column = 0;
        } else if (mx >= rightX0() && mx < rightX1()) {
            column = 1;
        } else {
            return -1;
        }
        int index = contentsPage * perPage * 2 + column * perPage + (int) (my - top) / CONTENTS_ROW;
        return index < state.pets().size() ? index : -1;
    }

    private boolean overName(double mx, double my) {
        PetsPacket.Pet pet = selected();
        if (contentsPage >= 0 || pet == null || pet.dead() || renaming || giving) {
            return false;
        }
        int w = Math.min(leftX1() - leftX0(), font.width(title(pet)));
        int x = (leftX0() + leftX1() - w) / 2;
        return mx >= x && mx < x + w && my >= nameY() - 1 && my < nameY() + 10;
    }

    private int takeOffX() {
        return rightX1() - font.width(tr("book.take_off"));
    }

    private boolean overTakeOff(double mx, double my) {
        PetsPacket.Details d = state.details();
        return contentsPage < 0 && !giving && (d.scarf() >= 0 || d.hat())
                && mx >= takeOffX() && mx < rightX1() && my >= scarfY() - 1 && my < scarfY() + 10;
    }

    private int feedX() {
        return rightX0() + LABEL_W;
    }

    private int foodAt(double mx, double my) {
        PetsPacket.Pet pet = selected();
        if (contentsPage >= 0 || giving || pet == null || pet.dead()) {
            return -1;
        }
        int x0 = feedX();
        if (my < feedY() || my >= feedY() + TILE || mx < x0) {
            return -1;
        }
        int food = (int) (mx - x0) / TILE;
        return food < state.details().foods().size() && food < foodFits() ? food : -1;
    }

    private int foodFits() {
        return (rightX1() - feedX()) / TILE;
    }

    private int personTop() {
        return pageTop() + 26;
    }

    private int personAt(double mx, double my) {
        if (mx < rightX0() || mx >= rightX1() || my < personTop()) {
            return -1;
        }
        int person = (int) (my - personTop()) / ROW;
        return person < state.players().size() && personTop() + (person + 1) * ROW <= arrowY() ? person : -1;
    }

    // ================================================================ drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float a) {
        if (BookStyle.current().winter()) {
            Winter.snowfall(gfx, width, height);
        }
        drawBook(gfx);
        if (settingsOpen) {
            // Only the closed-looking book behind the settings: its text would show through the shade.
            drawGear(gfx, mouseX, mouseY);
            super.extractRenderState(gfx, mouseX, mouseY, a);
            drawSettings(gfx, mouseX, mouseY);
            drawNotice(gfx);
            return;
        }
        PetsPacket.Pet pet = selected();
        if (contentsPage >= 0 || pet == null) {
            drawContents(gfx, mouseX, mouseY);
        } else {
            drawLeftPage(gfx, pet, mouseX, mouseY);
            if (giving) {
                drawGive(gfx, pet, mouseX, mouseY);
            } else {
                drawRightPage(gfx, pet, mouseX, mouseY);
            }
        }
        String indicator = tr("book.page", currentPage() + 1, pageCount());
        gfx.text(font, indicator, rightX1() - font.width(indicator), pageTop(), FADED, false);
        drawGear(gfx, mouseX, mouseY);
        drawNotice(gfx);
        super.extractRenderState(gfx, mouseX, mouseY, a);
    }

    /** Two pages of the vanilla book; the left one mirrored so both stitchings meet at the spine. */
    private void drawBook(GuiGraphicsExtractor gfx) {
        int x = bookX();
        int y = bookY();
        float u0 = PAGE_U / 256f;
        float u1 = (PAGE_U + PAGE_W) / 256f;
        float v0 = PAGE_V / 256f;
        float v1 = (PAGE_V + PAGE_H) / 256f;
        BookStyle style = BookStyle.current();
        Identifier book = style.texture();
        drawRibbon(gfx, style, true);
        gfx.blit(book, x, y, x + PAGE_W, y + PAGE_H, u1, u0, v0, v1);
        gfx.blit(book, x + PAGE_W, y, x + BOOK_W, y + PAGE_H, u0, u1, v0, v1);
        // The pages curve down into the spine: a soft shadow on both sides of it, then the crease.
        int mid = x + PAGE_W;
        for (int i = 0; i < 12; i++) {
            float k = 1f - i / 12f;
            int alpha = Math.round(k * k * 70);
            gfx.fill(mid - 1 - i, y + 6, mid - i, y + PAGE_H - 6, alpha << 24 | 0x553F28);
            gfx.fill(mid + i, y + 6, mid + i + 1, y + PAGE_H - 6, alpha << 24 | 0x553F28);
        }
        gfx.fill(mid - 1, y + 5, mid + 1, y + PAGE_H - 4, 0xFF000000 | brighten(style.swatch() & 0xFFFFFF, 0.55f));
        drawRibbon(gfx, style, false);
        if (style.winter()) {
            Winter.icicles(gfx, x + 3, x + BOOK_W - 3, y + PAGE_H - 1, ribbonX());
            Winter.snowCap(gfx, x + 1, x + BOOK_W - 1, y + 2);
            Winter.holly(gfx, x + BOOK_W - 18, y - 3);
        }
    }

    private int ribbonX() {
        return bookX() + BOOK_W - 44;
    }

    /** The bookmark: peeking out above the right page ({@code top}) and hanging below the book. */
    private void drawRibbon(GuiGraphicsExtractor gfx, BookStyle style, boolean top) {
        if (style.ribbon == 0) {
            return;
        }
        int x = ribbonX();
        int c = 0xFF000000 | style.ribbon;
        int light = 0xFF000000 | brighten(style.ribbon, 1.25f);
        int dark = 0xFF000000 | brighten(style.ribbon, 0.7f);
        if (top) {
            int y = bookY() - 4;
            gfx.fill(x, y, x + 5, y + 8, c);
            gfx.fill(x + 1, y, x + 2, y + 8, light);
            gfx.fill(x + 4, y, x + 5, y + 8, dark);
            return;
        }
        int y = bookY() + PAGE_H - 3;
        gfx.fill(x, y, x + 5, y + 7, c);
        gfx.fill(x + 1, y, x + 2, y + 7, light);
        gfx.fill(x + 4, y, x + 5, y + 7, dark);
        // a forked end
        gfx.fill(x, y + 7, x + 2, y + 9, c);
        gfx.fill(x + 3, y + 7, x + 5, y + 9, dark);
    }

    private static int brighten(int rgb, float k) {
        int r = Math.clamp(Math.round((rgb >> 16 & 0xFF) * k), 0, 255);
        int g = Math.clamp(Math.round((rgb >> 8 & 0xFF) * k), 0, 255);
        int b = Math.clamp(Math.round((rgb & 0xFF) * k), 0, 255);
        return r << 16 | g << 8 | b;
    }

    // ================================================================ settings

    private static final String[] GEAR = {
            "X.XXX.X",
            ".XXXXX.",
            "XXX.XXX",
            "XX...XX",
            "XXX.XXX",
            ".XXXXX.",
            "X.XXX.X"};

    private int gearX() {
        return bookX() + 14;
    }

    private int gearY() {
        return bookY() - GEAR_H + 2;
    }

    private boolean overGear(double mx, double my) {
        return mx >= gearX() && mx < gearX() + GEAR_W && my >= gearY() && my < gearY() + GEAR_H;
    }

    /** A small tab on top of the cover with a gear: opens the settings. */
    private void drawGear(GuiGraphicsExtractor gfx, int mx, int my) {
        boolean hot = !settingsOpen && overGear(mx, my);
        int cover = BookStyle.current().swatch() & 0xFFFFFF;
        int x = gearX();
        int y = gearY();
        gfx.fill(x, y, x + GEAR_W, y + GEAR_H, 0xFF000000 | brighten(cover, 0.55f));
        gfx.fill(x + 1, y + 1, x + GEAR_W - 1, y + GEAR_H, 0xFF000000 | brighten(cover, hot ? 1.45f : 1.1f));
        int ink = hot ? 0xFFFFE9A8 : 0xFFE2C478;
        for (int j = 0; j < GEAR.length && j < GEAR_H - 2; j++) {
            for (int i = 0; i < GEAR[j].length(); i++) {
                if (GEAR[j].charAt(i) == 'X') {
                    gfx.fill(x + 4 + i, y + 2 + j, x + 5 + i, y + 3 + j, ink);
                }
            }
        }
        if (BookStyle.current().winter()) {
            Winter.snowCap(gfx, x, x + GEAR_W, y + 1);
        }
        if (hot) {
            gfx.setTooltipForNextFrame(font, Component.literal(tr("cmd.settings")), mx, my);
        }
    }

    /** Opens on (or switches to) the settings page; used by /betterpets settings. */
    void showSettings() {
        settingsOpen = true;
        settingsScroll = 0;
        if (nameBox != null) {
            stopRenaming();
            giving = false;
            sync();
        }
    }

    private void openSettings() {
        settingsOpen = true;
        settingsScroll = 0;
        stopRenaming();
        giving = false;
        pageSound();
        sync();
    }

    private void closeSettings() {
        settingsOpen = false;
        pageSound();
        sync();
    }

    private int panelW() {
        return PAGE_W + 6;
    }

    private int panelX() {
        return (width - panelW()) / 2;
    }

    private int panelY() {
        return bookY();
    }

    private int panelX0() {
        return panelX() + 20;
    }

    private int panelX1() {
        return panelX() + panelW() - 16;
    }

    private int swatchY() {
        return panelY() + 40;
    }

    private int rulesTop() {
        return panelY() + 76;
    }

    private int settingsRows() {
        return (panelY() + PAGE_H - 34 - rulesTop()) / SETTINGS_ROW;
    }

    private int doneY() {
        return panelY() + PAGE_H - 26;
    }

    private int swatchAt(double mx, double my) {
        if (my < swatchY() || my >= swatchY() + 12) {
            return -1;
        }
        for (int i = 0; i < BookStyle.values().length; i++) {
            int x = panelX0() + i * SWATCH_STEP;
            if (mx >= x && mx < x + SWATCH_W) {
                return i;
            }
        }
        return -1;
    }

    private int settingAt(double my) {
        if (my < rulesTop()) {
            return -1;
        }
        int row = (int) (my - rulesTop()) / SETTINGS_ROW;
        int index = settingsScroll + row;
        return row < settingsRows() && index < state.settings().size() ? index : -1;
    }

    private boolean overDone(double mx, double my) {
        int w = font.width(Component.translatable("gui.done"));
        int x = (panelX0() + panelX1() - w) / 2;
        return mx >= x - 4 && mx < x + w + 4 && my >= doneY() - 2 && my < doneY() + 10;
    }

    private void settingsClick(double mx, double my, boolean shift) {
        if (overDone(mx, my)) {
            closeSettings();
            return;
        }
        int swatch = swatchAt(mx, my);
        if (swatch >= 0) {
            BookStyle.choose(BookStyle.values()[swatch]);
            click();
            return;
        }
        int index = settingAt(my);
        if (index < 0 || !state.canEditSettings() || mx < panelX0() || mx >= panelX1()) {
            return;
        }
        PetsPacket.Setting setting = state.settings().get(index);
        String value;
        if (!setting.number()) {
            value = setting.value() == 0 ? "true" : "false";
        } else {
            int step = (setting.max() - setting.min() > 200 ? 5 : 1) * (shift ? 10 : 1);
            boolean up = mx >= panelX1() - 8;
            boolean down = mx >= panelX1() - 44 && mx < panelX1() - 36;
            if (!up && !down) {
                return;
            }
            value = String.valueOf(Math.clamp(setting.value() + (up ? step : -step), setting.min(), setting.max()));
        }
        ClientPets.send(new PetActionPacket(Action.SETTING, state.selected(), 0, setting.key() + "=" + value));
        click();
    }

    /** The settings: a single page of the same book, laid on top of it. */
    private void drawSettings(GuiGraphicsExtractor gfx, int mx, int my) {
        // A new layer, so the shade lies over the book and not just the world behind it.
        gfx.nextStratum();
        gfx.fill(0, 0, width, height, 0x88000000);
        BookStyle style = BookStyle.current();
        int x = panelX();
        int y = panelY();
        gfx.blit(style.texture(), x, y, x + panelW(), y + PAGE_H, 20 / 256f, 166 / 256f, PAGE_V / 256f,
                (PAGE_V + PAGE_H) / 256f);
        if (style.winter()) {
            Winter.snowCap(gfx, x + 1, x + panelW() - 1, y + 2);
            Winter.icicles(gfx, x + 3, x + panelW() - 3, y + PAGE_H - 1, -100);
        }
        int x0 = panelX0();
        int x1 = panelX1();
        centered(gfx, Component.literal(tr("cmd.settings")).withStyle(Style.EMPTY.withBold(true)), x0, x1, y + 14, INK);

        gfx.text(font, tr("book.cover"), x0, y + 29, FADED, false);
        String coverName = tr("cover." + style.name().toLowerCase(java.util.Locale.ROOT));
        gfx.text(font, coverName, x1 - font.width(coverName), y + 29, INK, false);
        int hovered = swatchAt(mx, my);
        BookStyle[] styles = BookStyle.values();
        for (int i = 0; i < styles.length; i++) {
            int sx = x0 + i * SWATCH_STEP;
            int sy = swatchY();
            boolean on = styles[i] == style;
            gfx.fill(sx - 1, sy - 1, sx + SWATCH_W + 1, sy + 13, on ? INK : (i == hovered ? FADED : 0x30553F28));
            gfx.fill(sx, sy, sx + SWATCH_W, sy + 12, styles[i].swatch());
            if (styles[i].ribbon != 0) {
                gfx.fill(sx + SWATCH_W - 6, sy, sx + SWATCH_W - 3, sy + 12, 0xFF000000 | styles[i].ribbon);
            }
            if (styles[i].winter()) {
                Winter.snowCap(gfx, sx, sx + SWATCH_W, sy + 1);
            }
            if (on) {
                gfx.fill(sx + 2, sy + 14, sx + SWATCH_W - 2, sy + 15, INK);
            }
            if (i == hovered) {
                gfx.setTooltipForNextFrame(font, Component.literal(tr("cover." + styles[i].name().toLowerCase(java.util.Locale.ROOT))), mx, my);
            }
        }

        gfx.fill(x0, y + 59, x1, y + 60, RULE);
        boolean canEdit = state.canEditSettings();
        gfx.text(font, tr("book.rules"), x0, y + 63, FADED, false);
        if (!canEdit) {
            String note = tr("book.ops_only");
            gfx.text(font, note, x1 - font.width(note), y + 63, RED, false);
        }
        List<PetsPacket.Setting> settings = state.settings();
        int rows = settingsRows();
        int hot = settingAt(my);
        for (int r = 0; r < rows && settingsScroll + r < settings.size(); r++) {
            int index = settingsScroll + r;
            PetsPacket.Setting setting = settings.get(index);
            int ry = rulesTop() + r * SETTINGS_ROW;
            boolean rowHot = index == hot && mx >= x0 && mx < x1;
            if (rowHot && canEdit) {
                gfx.fill(x0 - 2, ry - 2, x1 + 2, ry + SETTINGS_ROW - 2, HOVER);
            }
            int valueW;
            if (setting.number()) {
                String number = String.valueOf(setting.value());
                valueW = 44;
                int vx = x1 - valueW;
                boolean downHot = rowHot && canEdit && mx >= x1 - 44 && mx < x1 - 36;
                boolean upHot = rowHot && canEdit && mx >= x1 - 8;
                gfx.text(font, "<", vx, ry, canEdit ? (downHot ? RED_HOT : INK) : FADED, false);
                gfx.text(font, number, vx + 22 - font.width(number) / 2, ry, canEdit ? INK : FADED, false);
                gfx.text(font, ">", x1 - 5, ry, canEdit ? (upHot ? RED_HOT : INK) : FADED, false);
            } else {
                String word = (setting.value() != 0 ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF).getString();
                valueW = Math.max(font.width(CommonComponents.OPTION_ON.getString()), font.width(CommonComponents.OPTION_OFF.getString()));
                int color = !canEdit || setting.value() == 0 ? FADED : INK;
                gfx.text(font, word, x1 - font.width(word), ry, color, false);
            }
            gfx.text(font, Ui.fit(font, label(setting.key()), x1 - x0 - valueW - 4), x0, ry, canEdit ? INK : FADED, false);
            if (rowHot) {
                List<Component> tip = new ArrayList<>();
                tip.add(Component.literal(net.minecraft.locale.Language.getInstance().has("betterpets.config." + setting.key() + ".desc")
                        ? tr("config." + setting.key() + ".desc") : setting.description()));
                if (setting.number() && canEdit) {
                    tip.add(Component.literal(tr("book.number_tip"))
                            .withStyle(Style.EMPTY.withColor(0xA0A0A0)));
                }
                gfx.setComponentTooltipForNextFrame(font, tip, mx, my);
            }
        }
        if (settingsScroll > 0) {
            gfx.text(font, "^", x1 + 4, rulesTop(), FADED, false);
        }
        if (settingsScroll + rows < settings.size()) {
            gfx.text(font, "v", x1 + 4, rulesTop() + (rows - 1) * SETTINGS_ROW, FADED, false);
        }

        boolean doneHot = overDone(mx, my);
        int w = font.width(Component.translatable("gui.done"));
        int dx = (x0 + x1 - w) / 2;
        gfx.text(font, Component.translatable("gui.done"), dx, doneY(), doneHot ? RED_HOT : RED, false);
        gfx.fill(dx, doneY() + 9, dx + w, doneY() + 10, doneHot ? RED_HOT : RED);
    }

    /** A setting's name for people: known keys get a short label, others are split at capitals. */
    private static String label(String key) {
        if (net.minecraft.locale.Language.getInstance().has("betterpets.config." + key)) {
            return tr("config." + key);
        }
        return switch (key) {
            default -> {
                String words = key.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase();
                yield Character.toUpperCase(words.charAt(0)) + words.substring(1);
            }
        };
    }

    private void centered(GuiGraphicsExtractor gfx, Component text, int x0, int x1, int y, int color) {
        int w = font.width(text);
        if (w > x1 - x0) {
            String cut = Ui.fit(font, text.getString(), x1 - x0);
            gfx.text(font, cut, (x0 + x1 - font.width(cut)) / 2, y, color, false);
        } else {
            gfx.text(font, text, (x0 + x1 - w) / 2, y, color, false);
        }
    }

    private void drawContents(GuiGraphicsExtractor gfx, int mx, int my) {
        if (contentsPage <= 0) {
            centered(gfx, Component.literal(tr("book.my_pets")).withStyle(Style.EMPTY.withBold(true)), leftX0(), leftX1(), pageTop(), INK);
        } else {
            centered(gfx, Component.literal(tr("book.my_pets_more")), leftX0(), leftX1(), pageTop(), FADED);
        }
        int top = bookY() + CONTENTS_TOP;
        gfx.fill(leftX0(), top - 6, leftX1(), top - 5, RULE);
        gfx.fill(rightX0(), top - 6, rightX1(), top - 5, RULE);
        List<PetsPacket.Pet> pets = state.pets();
        if (pets.isEmpty()) {
            gfx.textWithWordWrap(font, Component.literal(tr("book.no_pets")),
                    leftX0(), top, leftX1() - leftX0(), FADED, false);
            return;
        }
        int perPage = contentsPerPage();
        int hovered = contentsAt(mx, my);
        int first = Math.max(0, contentsPage) * perPage * 2;
        for (int i = first; i < pets.size() && i < first + perPage * 2; i++) {
            PetsPacket.Pet pet = pets.get(i);
            boolean leftSide = i - first < perPage;
            int x0 = leftSide ? leftX0() : rightX0();
            int x1 = leftSide ? leftX1() : rightX1();
            int y = top + ((i - first) % perPage) * CONTENTS_ROW;
            if (i == hovered) {
                gfx.fill(x0 - 2, y - 1, x1 + 2, y + CONTENTS_ROW - 1, HOVER);
            }
            gfx.item(icon(pet), x0, y);
            if (pet.dead()) {
                gfx.fill(x0, y, x0 + 16, y + 16, 0x88E8DBB5);
            }
            String hp = pet.dead() ? tr("book.gone_short") : PetBrain.health(pet.health(), pet.maxHealth());
            int hpX = x1 - font.width(hp);
            gfx.text(font, hp, hpX, y + 4, pet.dead() ? RED : FADED, false);
            String name = Ui.fit(font, pet.displayName().getString(), hpX - (x0 + 20) - 4);
            Component shown = name.equals(pet.displayName().getString()) ? pet.displayName() : Component.literal(name);
            gfx.text(font, shown, x0 + 20, y + 4, pet.dead() ? FADED : INK, false);
            if (i == hovered) {
                gfx.setTooltipForNextFrame(font, Component.literal(tr("book.open_page", pet.displayName().getString())), mx, my);
            }
        }
    }

    /** The pet's name as the page title: bold, in its own colours if it has them. */
    private static Component title(PetsPacket.Pet pet) {
        return pet.displayName().copy().withStyle(st -> st.withBold(true));
    }

    private float lookX = Float.NaN;
    private float lookY = Float.NaN;
    private long lookTime;

    private void drawLeftPage(GuiGraphicsExtractor gfx, PetsPacket.Pet pet, int mx, int my) {
        int x0 = leftX0();
        int x1 = leftX1();
        if (!renaming) {
            boolean hot = overName(mx, my);
            centered(gfx, title(pet), x0, x1, nameY(), INK);
            if (hot) {
                int w = Math.min(x1 - x0, font.width(title(pet)));
                int x = (x0 + x1 - w) / 2;
                gfx.fill(x, nameY() + 9, x + w, nameY() + 10, INK);
                gfx.setTooltipForNextFrame(font, Component.literal(tr("book.click_rename")), mx, my);
            }
            centered(gfx, pet.species(), x0, x1, nameY() + 12, FADED);
        } else {
            gfx.fill(x0, nameY() + 10, x1, nameY() + 11, INK);
            gfx.textWithWordWrap(font, Component.literal(tr("book.enter_saves") + " &6gold, <#ff55aa>pink"),
                    x0, nameY() + 15, x1 - x0, FADED, false);
        }

        int top = nameY() + 30;
        int feet = arrowY() - 8;
        if (entity() instanceof LivingEntity living && !pet.dead()) {
            int size = previewSize(living, x1 - x0, feet - top);
            // Vanilla centres the pet in the box it is given; pick a box whose centre puts its feet on the line.
            int center = Math.round(feet - living.getBbHeight() * size / 2f);
            int boxTop = top - 40;
            int boxBottom = 2 * center - boxTop;
            // The pet watches the cursor while it is near, and otherwise looks a little to the right.
            boolean near = mx >= x0 - 6 && mx < x1 + 6 && my >= top && my < feet + 8;
            float targetX = near ? mx : (x0 + x1) / 2f + 60f;
            float targetY = near ? my : center - living.getBbHeight() * size * 0.3f;
            long now = System.currentTimeMillis();
            if (Float.isNaN(lookX) || now - lookTime > 500) {
                lookX = targetX;
                lookY = targetY;
            } else {
                float step = 1f - (float) Math.exp(-(now - lookTime) / 90.0);
                lookX += (targetX - lookX) * step;
                lookY += (targetY - lookY) * step;
            }
            lookTime = now;
            int shadow = Math.round(living.getBbWidth() * size * 0.7f);
            int cx = (x0 + x1) / 2;
            gfx.fill(cx - shadow, feet, cx + shadow, feet + 1, 0x30553F28);
            gfx.fill(cx - shadow + 3, feet + 1, cx + shadow - 3, feet + 2, 0x20553F28);
            InventoryScreen.extractEntityInInventoryFollowsMouse(gfx, x0, boxTop, x1, boxBottom, size, 0f,
                    lookX, lookY, living);
        } else {
            // Not loaded (far away) or gone: its egg or a skull, drawn big, with a note.
            int cx = (x0 + x1) / 2;
            int cy = (top + feet) / 2;
            gfx.pose().pushMatrix();
            gfx.pose().translate(cx - 24, cy - 30);
            gfx.pose().scale(3f, 3f);
            gfx.item(icon(pet), 0, 0);
            gfx.pose().popMatrix();
            centered(gfx, Component.literal(tr(pet.dead() ? "book.gone" : "status.far")), x0, x1, cy + 24, pet.dead() ? RED : FADED);
        }
    }

    private void drawRightPage(GuiGraphicsExtractor gfx, PetsPacket.Pet pet, int mx, int my) {
        PetsPacket.Details d = state.details();
        int x = rightX0();
        int w = rightX1() - x;
        int y = pageTop() + 16;
        if (pet.dead()) {
            for (var line : font.split(Component.literal(d.death()), w)) {
                gfx.text(font, line, x, y, RED, false);
                y += 9;
            }
            y += 6;
            fact(gfx, tr("fact.where"), d.where(), x, y, w);
            return;
        }
        drawHearts(gfx, pet, x, y, w);
        y += ROW + 3;
        int limit = scarfY() - 6;
        String place = pet.place().isEmpty() ? d.where() : pet.place();
        y = fact(gfx, tr("fact.doing"), d.status(), x, y, w);
        if (y + ROW <= limit) {
            y = fact(gfx, tr("fact.where"), place, x, y, w);
        }
        if (!d.speed().isEmpty() && y + ROW <= limit) {
            y = fact(gfx, tr("fact.speed"), d.speed(), x, y, w);
        }
        if (!d.jump().isEmpty() && y + ROW <= limit) {
            y = fact(gfx, tr("fact.jump"), d.jump(), x, y, w);
        }
        if (!d.since().isEmpty() && y + ROW <= limit) {
            fact(gfx, tr("fact.with_you"), d.since(), x, y, w);
        }
        gfx.fill(x, scarfY() - 6, rightX1(), scarfY() - 5, RULE);
        drawScarf(gfx, d, x, w, mx, my);
        drawFood(gfx, d, mx, my);
    }

    private void drawHearts(GuiGraphicsExtractor gfx, PetsPacket.Pet pet, int x, int y, int w) {
        float max = Math.max(1f, pet.maxHealth());
        float health = Math.clamp(pet.health(), 0f, max);
        int hearts = Math.min(10, (int) Math.ceil(max / 2f));
        float perHeart = max / hearts;
        int step = 8;
        for (int i = 0; i < hearts; i++) {
            float inThis = health - i * perHeart;
            int hx = x + i * step;
            gfx.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_EMPTY, hx, y, 9, 9);
            if (inThis >= perHeart - 0.01f) {
                gfx.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_FULL, hx, y, 9, 9);
            } else if (inThis >= perHeart / 2 - 0.01f) {
                gfx.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_HALF, hx, y, 9, 9);
            }
        }
        String number = PetBrain.health(pet.health(), pet.maxHealth());
        int numberX = x + hearts * step + 5;
        if (numberX + font.width(number) <= x + w) {
            gfx.text(font, number, numberX, y + 1, FADED, false);
        }
    }

    private int fact(GuiGraphicsExtractor gfx, String label, String value, int x, int y, int w) {
        gfx.text(font, label, x, y, FADED, false);
        int offset = LABEL_W;
        gfx.text(font, Ui.fit(font, value, w - offset), x + offset, y, INK, false);
        return y + ROW;
    }

    private void drawScarf(GuiGraphicsExtractor gfx, PetsPacket.Details d, int x, int w, int mx, int my) {
        int y = scarfY();
        gfx.text(font, tr("book.wears"), x, y, FADED, false);
        int vx = x + LABEL_W;
        if (!d.scarfFits() && !d.hatFits() && d.scarf() < 0 && !d.hat()) {
            gfx.text(font, Ui.fit(font, tr("book.scarf_no_fit"), w - LABEL_W), vx, y, FADED, false);
            return;
        }
        if (d.scarf() < 0 && !d.hat()) {
            gfx.text(font, Ui.fit(font, tr("book.scarf_none"), w - LABEL_W), vx, y, FADED, false);
            if (mx >= vx && mx < x + w && my >= y - 1 && my < y + 10) {
                List<Component> tip = new java.util.ArrayList<>();
                if (d.scarfFits()) {
                    tip.add(Component.literal(tr("book.scarf_tip.1")));
                    tip.add(Component.literal(tr("book.scarf_tip.2")));
                    tip.add(Component.literal(tr("book.scarf_tip.3")));
                }
                if (d.hatFits()) {
                    tip.add(Component.literal(tr("book.hat_tip.1")));
                    tip.add(Component.literal(tr("book.hat_tip.2")));
                }
                gfx.setComponentTooltipForNextFrame(font, tip, mx, my);
            }
            return;
        }
        if (d.scarf() >= 0) {
            gfx.fill(vx - 1, y - 1, vx + 17, y + 8, INK);
            gfx.fill(vx, y, vx + 16, y + 7, 0xFF000000 | d.scarf());
            vx += 22;
        }
        if (d.hat()) {
            drawHatIcon(gfx, vx, y - 1);
        }
        boolean hot = overTakeOff(mx, my);
        int tx = takeOffX();
        gfx.text(font, tr("book.take_off"), tx, y, hot ? RED_HOT : RED, false);
        gfx.fill(tx, y + 9, tx + font.width(tr("book.take_off")), y + 10, hot ? RED_HOT : RED);
    }

    /** A tiny Santa hat, 9 x 9 pixels, drawn in the ink style of the scarf swatch. */
    private static void drawHatIcon(GuiGraphicsExtractor gfx, int x, int y) {
        int red = 0xFFC8282C;
        int white = 0xFFF4F1EA;
        gfx.fill(x + 3, y + 1, x + 6, y + 3, red);
        gfx.fill(x + 2, y + 3, x + 7, y + 5, red);
        gfx.fill(x + 1, y + 5, x + 8, y + 6, red);
        gfx.fill(x + 6, y + 1, x + 8, y + 2, red);
        gfx.fill(x + 8, y, x + 10, y + 2, white);
        gfx.fill(x, y + 6, x + 9, y + 8, white);
        gfx.fill(x, y + 8, x + 9, y + 9, INK);
    }

    private void drawFood(GuiGraphicsExtractor gfx, PetsPacket.Details d, int mx, int my) {
        int x = rightX0();
        int y = feedY();
        gfx.text(font, tr("action.feed"), x, y + 5, FADED, false);
        int x0 = feedX();
        List<PetsPacket.Food> foods = d.foods();
        if (foods.isEmpty()) {
            String text = tr(d.entityId() < 0 ? "book.too_far" : "book.nothing_it_eats");
            gfx.text(font, Ui.fit(font, text, rightX1() - x0), x0, y + 5, FADED, false);
            return;
        }
        int hovered = foodAt(mx, my);
        List<Component> tip = new ArrayList<>();
        for (int i = 0; i < foods.size() && i < foodFits(); i++) {
            int tx = x0 + i * TILE;
            gfx.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, tx, y, TILE, TILE);
            ItemStack stack = stack(foods.get(i));
            gfx.item(stack, tx + 1, y + 1);
            gfx.itemDecorations(font, stack, tx + 1, y + 1);
            if (i == hovered) {
                gfx.fill(tx + 1, y + 1, tx + 17, y + 17, 0x80FFFFFF);
                tip.add(Component.translatable("betterpets.feed.with", stack.getHoverName()));
                tip.add(Component.literal(tr(!d.near() ? "book.far" : d.hungry() ? "feed.heals" : "feed.not_hungry")));
            }
        }
        if (!tip.isEmpty()) {
            gfx.setComponentTooltipForNextFrame(font, tip, mx, my);
        }
    }

    private static ItemStack stack(PetsPacket.Food food) {
        Identifier id = Identifier.tryParse(food.item());
        Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
        return new ItemStack(item, food.count());
    }

    private void drawGive(GuiGraphicsExtractor gfx, PetsPacket.Pet pet, int mx, int my) {
        int x = rightX0();
        int w = rightX1() - x;
        int y = pageTop() + 14;
        gfx.text(font, Ui.fit(font, tr("book.give_to", pet.displayName().getString()), w), x, y, INK, false);
        List<PetsPacket.Person> players = state.players();
        if (players.isEmpty()) {
            gfx.text(font, tr("book.nobody"), x, personTop(), FADED, false);
            return;
        }
        gfx.text(font, Ui.fit(font, tr("book.click_twice"), w), x, y + 10, FADED, false);
        int hovered = personAt(mx, my);
        for (int i = 0; i < players.size() && personTop() + (i + 1) * ROW <= arrowY(); i++) {
            PetsPacket.Person person = players.get(i);
            int ry = personTop() + i * ROW;
            boolean armed = person.id().equals(armedPlayer);
            if (i == hovered || armed) {
                gfx.fill(x - 2, ry - 2, rightX1() + 2, ry + ROW - 2, HOVER);
            }
            String label = armed ? tr("book.again_to_give", person.name()) : person.name();
            gfx.text(font, Ui.fit(font, label, w), x, ry, armed ? RED : INK, false);
        }
    }

    /** The last answer from the server, under the buttons, fading out. */
    private void drawNotice(GuiGraphicsExtractor gfx) {
        long age = System.currentTimeMillis() - noticeAt;
        if (notice.isEmpty() || age > NOTICE_MS) {
            return;
        }
        float alpha = age > NOTICE_MS - 500 ? (NOTICE_MS - age) / 500f : 1f;
        String text = Ui.fit(font, notice, BOOK_W);
        int y = Math.min(height - 10, buttonY() + BUTTON_H + 4);
        gfx.text(font, text, (width - font.width(text)) / 2, y, Ui.fade(0xFFFFFFFF, alpha), true);
    }

    private Entity entity() {
        Minecraft minecraft = Minecraft.getInstance();
        int id = state.details().entityId();
        return id < 0 || minecraft.level == null ? null : minecraft.level.getEntity(id);
    }

    /** Fits the pet in the box, tall or long. */
    private static int previewSize(LivingEntity entity, int boxW, int boxH) {
        float byHeight = (boxH - 8) / Math.max(0.4f, entity.getBbHeight());
        float byWidth = (boxW - 12) / Math.max(0.4f, entity.getBbWidth() * 1.6f);
        return Math.round(Math.clamp(Math.min(byHeight, byWidth), 10f, 60f));
    }

    private static ItemStack icon(PetsPacket.Pet pet) {
        if (pet.dead()) {
            return new ItemStack(Items.SKELETON_SKULL);
        }
        Identifier id = Identifier.tryParse(pet.type());
        return new ItemStack(BuiltInRegistries.ENTITY_TYPE.getOptional(id)
                .flatMap(SpawnEggItem::byId)
                .map(Holder::value)
                .orElse(Items.NAME_TAG));
    }

    /** A text of this mod in the game's language. */
    static String tr(String path, Object... args) {
        return net.minecraft.client.resources.language.I18n.get("betterpets." + path, args);
    }
}

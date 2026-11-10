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
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

/**
 * The pet menu: one square window filled with a winter night landscape (see {@link Festive}).
 * Your pets are tabs along the top under a string of lights; the selected pet stands in the snow
 * on the left and watches the cursor while it is near; its name, hearts, whereabouts, scarf and
 * food are on a see-through panel on the right; the buttons stand in the snow at the bottom. Click the name to rename. Scroll or use the arrow keys
 * to switch pets.
 *
 * <p>All state comes from the server in {@link PetsPacket}; every click goes back as a
 * {@link PetActionPacket}. The look is the menu's own ({@link Ui}), not Mycel's or vanilla's.
 */
public final class PetScreen extends Screen {
    private static final int HEADER_H = 38;
    private static final int ACTION_H = 34;
    private static final int TAB = 22;
    private static final int TAB_GAP = 3;
    private static final int TILE = 18;
    private static final int LINE = 11;
    private static final int GAP = 6;
    private static final int KEY_ENTER = 257;
    private static final int KEY_ESCAPE = 256;
    private static final int KEY_LEFT = 263;
    private static final int KEY_RIGHT = 262;
    private static final long NOTICE_MS = 3500;
    private static final float NAME_SCALE = 1.5f;

    private PetsPacket state;
    private EditBox nameBox;
    private SnowButton mainButton;
    private SnowButton findButton;
    private SnowButton callButton;
    private SnowButton giveButton;
    private SnowButton cancelButton;
    private UUID shownPet;
    private int firstTab;
    private boolean renaming;
    private boolean giving;
    private UUID armedPlayer;
    private Runnable mainAction = () -> { };
    private String notice = "";
    private boolean noticeOk;
    private long noticeAt;

    PetScreen(PetsPacket state) {
        super(Component.literal("Your pets"));
        this.state = state;
    }

    /** New data from the server. */
    void update(PetsPacket packet) {
        state = packet;
        if (!packet.notice().isEmpty()) {
            notice = packet.notice();
            noticeOk = packet.noticeOk();
            noticeAt = System.currentTimeMillis();
        }
        if (nameBox != null) {
            sync();
        }
    }

    // ================================================================ layout

    private int cardW() {
        return Math.clamp(width - 16, 300, 352);
    }

    private int cardH() {
        return Math.clamp(height - 28, 196, 222);
    }

    private int left() {
        return (width - cardW()) / 2;
    }

    private int top() {
        return (height - cardH()) / 2;
    }

    private int right() {
        return left() + cardW();
    }

    private int bottom() {
        return top() + cardH();
    }

    private int previewW() {
        return cardW() >= 340 ? 120 : 106;
    }

    private int previewX0() {
        return left() + 10;
    }

    private int previewX1() {
        return previewX0() + previewW();
    }

    private int bodyY0() {
        return top() + HEADER_H + 8;
    }

    private int bodyY1() {
        return bottom() - ACTION_H - 4;
    }

    private int infoX() {
        return previewX1() + 12;
    }

    private int infoW() {
        return right() - 12 - infoX();
    }

    private int nameY() {
        return bodyY0() + 1;
    }

    private int feedY() {
        return bodyY1() - TILE;
    }

    private int scarfY() {
        return feedY() - 15;
    }

    private int maxTabs() {
        return Math.max(1, (cardW() - 20 - 26) / (TAB + TAB_GAP));
    }

    private int tabX(int slot) {
        return left() + 10 + slot * (TAB + TAB_GAP);
    }

    /** Below the string of lights. */
    private int tabY() {
        return top() + 11;
    }

    private int closeX() {
        return right() - 22;
    }

    // ================================================================ widgets

    @Override
    protected void init() {
        nameBox = new EditBox(font, infoX(), nameY() + 2, infoW(), 12, Component.literal("Name"));
        nameBox.setMaxLength(PetBrain.MAX_TYPED_LENGTH);
        nameBox.setBordered(false);
        nameBox.setTextColor(Ui.TEXT);
        nameBox.setHint(Component.literal("Give it a name"));
        addRenderableWidget(nameBox);

        int y = bottom() - ACTION_H + (ACTION_H - SnowButton.HEIGHT) / 2;
        int x0 = left() + 10;
        int w = (cardW() - 20 - GAP * 3) / 4;
        mainButton = addRenderableWidget(new SnowButton(x0, y, w, Component.literal("Sit"), () -> mainAction.run()));
        findButton = addRenderableWidget(new SnowButton(x0 + (w + GAP), y, w, Component.literal("Find"),
                () -> sendAndClose(Action.FIND)));
        callButton = addRenderableWidget(new SnowButton(x0 + (w + GAP) * 2, y, w, Component.literal("Call"),
                () -> sendAndClose(Action.CALL)));
        giveButton = addRenderableWidget(new SnowButton(x0 + (w + GAP) * 3, y, w, Component.literal("Give"), () -> {
            giving = true;
            armedPlayer = null;
            stopRenaming();
            sync();
        }));
        cancelButton = addRenderableWidget(new SnowButton(x0 + (w + GAP) * 3, y, w, Component.literal("Cancel"), () -> {
            giving = false;
            sync();
        }));
        shownPet = null;
        sync();
    }

    /** Makes the widgets match the state. */
    private void sync() {
        PetsPacket.Pet pet = selected();
        PetsPacket.Details d = state.details();
        boolean alive = pet != null && !pet.dead();
        if (pet != null && !pet.id().equals(shownPet)) {
            shownPet = pet.id();
            giving = false;
            stopRenaming();
            showTab(state.pets().indexOf(pet));
        }
        if (!alive) {
            stopRenaming();
        }
        nameBox.visible = renaming;

        boolean far = pet != null && !d.near();
        boolean forgettable = pet != null && (pet.dead() || !pet.loaded());
        if (pet != null && pet.dead()) {
            setMain("Forget", () -> send(Action.FORGET), true);
        } else if (d.canSit()) {
            setMain(d.sitting() ? "Stand" : "Sit", () -> send(Action.SIT), d.near());
        } else if (d.hasInventory()) {
            setMain("Gear", () -> send(Action.INVENTORY), d.near());
        } else if (forgettable) {
            setMain("Forget", () -> send(Action.FORGET), true);
        } else {
            setMain("Sit", () -> { }, false);
        }
        mainButton.setTooltip(far && mainButton.getMessage().getString().matches("Sit|Stand|Gear")
                ? net.minecraft.client.gui.components.Tooltip.create(Component.literal("Too far away. Call it first."))
                : null);
        findButton.active = alive;
        callButton.active = alive && state.allowCall();
        callButton.primary(alive && far && state.allowCall());
        giveButton.active = alive && state.allowGive();

        mainButton.visible = !giving;
        findButton.visible = !giving;
        callButton.visible = !giving;
        giveButton.visible = !giving;
        cancelButton.visible = giving;
    }

    private void setMain(String label, Runnable action, boolean active) {
        mainButton.setMessage(Component.literal(label));
        mainAction = action;
        mainButton.active = active;
    }

    private void showTab(int index) {
        if (index < 0) {
            return;
        }
        if (index < firstTab) {
            firstTab = index;
        } else if (index >= firstTab + maxTabs()) {
            firstTab = index - maxTabs() + 1;
        }
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

    private void select(int index) {
        if (index < 0 || index >= state.pets().size()) {
            return;
        }
        PetsPacket.Pet pet = state.pets().get(index);
        if (!pet.id().equals(state.selected())) {
            ClientPets.send(new PetActionPacket(Action.SELECT, pet.id()));
            click();
        }
        showTab(index);
    }

    private void step(int delta) {
        int index = state.pets().indexOf(selected());
        int size = state.pets().size();
        if (size > 1) {
            select(Math.floorMod(index + delta, size));
        }
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
        if (event.key() == KEY_LEFT) {
            step(-1);
            return true;
        }
        if (event.key() == KEY_RIGHT) {
            step(1);
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
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        int tab = tabAt(mx, my);
        if (tab >= 0) {
            select(tab);
            return true;
        }
        if (overClose(mx, my)) {
            click();
            onClose();
            return true;
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
        if (scrollY != 0 && !renaming) {
            step(scrollY > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    // ================================================================ hit tests

    private int tabAt(double mx, double my) {
        if (my < tabY() || my >= tabY() + TAB) {
            return -1;
        }
        for (int slot = 0; slot < maxTabs(); slot++) {
            int index = firstTab + slot;
            if (index >= state.pets().size()) {
                break;
            }
            if (mx >= tabX(slot) && mx < tabX(slot) + TAB) {
                return index;
            }
        }
        return -1;
    }

    private boolean overClose(double mx, double my) {
        return mx >= closeX() && mx < closeX() + 12 && my >= tabY() + 5 && my < tabY() + 17;
    }

    private boolean overName(double mx, double my) {
        PetsPacket.Pet pet = selected();
        if (pet == null || pet.dead() || renaming) {
            return false;
        }
        int w = Math.min(infoW(), Math.round(font.width(pet.displayName()) * NAME_SCALE));
        return mx >= infoX() && mx < infoX() + w && my >= nameY() && my < nameY() + 13;
    }

    private int takeOffX() {
        return infoX() + infoW() - font.width("Take off");
    }

    private boolean overTakeOff(double mx, double my) {
        PetsPacket.Details d = state.details();
        return d.scarf() >= 0 && mx >= takeOffX() && mx < infoX() + infoW() && my >= scarfY() - 1 && my < scarfY() + 10;
    }

    private int foodAt(double mx, double my) {
        int x0 = infoX() + 30;
        if (my < feedY() || my >= feedY() + TILE || mx < x0) {
            return -1;
        }
        int food = (int) (mx - x0) / (TILE + 2);
        int fits = (infoX() + infoW() - x0 + 2) / (TILE + 2);
        return food < state.details().foods().size() && food < fits ? food : -1;
    }

    private int personTop() {
        return bodyY0() + 28;
    }

    private int personAt(double mx, double my) {
        if (mx < infoX() || mx >= infoX() + infoW() || my < personTop()) {
            return -1;
        }
        int person = (int) (my - personTop()) / 16;
        return person < state.players().size() && personTop() + (person + 1) * 16 <= bodyY1() ? person : -1;
    }

    // ================================================================ drawing

    /** Where the deep snow starts; the pet stands on it. */
    private int ground() {
        return bottom() - ACTION_H - 12;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float a) {
        gfx.fill(0, 0, width, height, Ui.SHADE);
        int x0 = left();
        int y0 = top();
        int x1 = right();
        int y1 = bottom();
        PetsPacket.Pet pet = selected();
        Festive.scene(gfx, x0, y0, x1, y1, ground(), previewX0(), previewX1());
        gfx.enableScissor(x0, y0, x1, y1);
        drawPet(gfx, pet, mouseX, mouseY);
        Festive.snowfall(gfx, x0, y0, x1, y1, 60, 0.85f, 3);
        gfx.disableScissor();

        drawTabs(gfx, mouseX, mouseY);
        drawClose(gfx, mouseX, mouseY);
        if (pet != null) {
            int px0 = infoX() - 7;
            int py0 = bodyY0() - 5;
            int px1 = right() - 6;
            int py1 = bodyY1() + 4;
            Festive.panel(gfx, px0, py0, px1, py1, Ui.PANEL, Ui.PANEL_EDGE);
            if (giving) {
                drawGive(gfx, pet, mouseX, mouseY);
            } else {
                drawInfo(gfx, pet, mouseX, mouseY);
            }
        }
        drawNotice(gfx);
        super.extractRenderState(gfx, mouseX, mouseY, a);
    }

    private void drawTabs(GuiGraphicsExtractor gfx, int mx, int my) {
        List<PetsPacket.Pet> pets = state.pets();
        int hovered = tabAt(mx, my);
        int shown = Math.min(maxTabs(), pets.size() - firstTab);
        for (int slot = 0; slot < shown; slot++) {
            int index = firstTab + slot;
            PetsPacket.Pet pet = pets.get(index);
            int x = tabX(slot);
            int y = tabY();
            boolean on = pet.id().equals(state.selected());
            if (on) {
                Ui.framed(gfx, x, y, x + TAB, y + TAB, Ui.ACCENT_SOFT, Ui.ACCENT);
            } else {
                Ui.framed(gfx, x, y, x + TAB, y + TAB, index == hovered ? Ui.BUTTON_HOVER : 0x900B1424,
                        index == hovered ? Ui.BUTTON_EDGE_HOT : 0x8034507A);
            }
            gfx.item(icon(pet), x + 3, y + 3);
            if (pet.dead()) {
                gfx.fill(x + 3, y + 3, x + 19, y + 19, 0x99121318);
            }
            if (index == hovered) {
                gfx.setTooltipForNextFrame(font, pet.displayName(), mx, my);
            }
        }
        if (firstTab > 0) {
            gfx.text(font, "<", tabX(0) - 7, tabY() + 7, Ui.FAINT, false);
        }
        if (firstTab + shown < pets.size()) {
            gfx.text(font, ">", tabX(shown) + 1, tabY() + 7, Ui.FAINT, false);
        }
    }

    private void drawClose(GuiGraphicsExtractor gfx, int mx, int my) {
        boolean hot = overClose(mx, my);
        int x = closeX();
        int y = tabY() + 5;
        if (hot) {
            Ui.framed(gfx, x - 2, y - 2, x + 14, y + 14, Ui.BUTTON_HOVER, Ui.BUTTON_EDGE_HOT);
        }
        int color = hot ? Ui.TEXT : Ui.DIM;
        for (int i = 0; i < 10; i++) {
            gfx.fill(x + 1 + i, y + 1 + i, x + 2 + i, y + 2 + i, color);
            gfx.fill(x + 10 - i, y + 1 + i, x + 11 - i, y + 2 + i, color);
        }
    }

    private float lookX = Float.NaN;
    private float lookY = Float.NaN;
    private long lookTime;

    private void drawPet(GuiGraphicsExtractor gfx, PetsPacket.Pet pet, int mx, int my) {
        int x0 = previewX0();
        int y0 = bodyY0();
        int x1 = previewX1();
        int ground = ground();
        if (entity() instanceof LivingEntity living) {
            int size = previewSize(living, x1 - x0, ground - y0 + 6);
            // Vanilla centres the pet in the box it is given; pick a box whose centre puts its feet
            // in the snow.
            int feet = ground + 4;
            int center = Math.round(feet - living.getBbHeight() * size / 2f);
            int boxTop = top();
            int boxBottom = 2 * center - boxTop;
            // The pet watches the cursor while it is near, and otherwise turns back to a fixed spot
            // up and to the right so it shows its side. It eases between the two.
            boolean near = mx >= x0 - 6 && mx < x1 + 6 && my >= y0 && my < feet + 8;
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
            // A soft shadow in the snow under its feet.
            int shadow = Math.round(living.getBbWidth() * size * 0.7f);
            int cx = (x0 + x1) / 2;
            gfx.fill(cx - shadow, feet - 1, cx + shadow, feet + 1, 0x2A1B2C4A);
            gfx.fill(cx - shadow + 2, feet + 1, cx + shadow - 2, feet + 2, 0x1A1B2C4A);
            InventoryScreen.extractEntityInInventoryFollowsMouse(gfx, x0, boxTop, x1, boxBottom, size, 0f,
                    lookX, lookY, living);
        } else if (pet != null) {
            int cx = (x0 + x1) / 2;
            int cy = ground - 22;
            gfx.item(icon(pet), cx - 8, cy - 14);
            String text = pet.dead() ? "Gone" : "Far away";
            int tw = font.width(text);
            gfx.fill(cx - tw / 2 - 3, cy + 5, cx + tw / 2 + 4, cy + 18, 0xB00B1424);
            gfx.text(font, text, cx - tw / 2, cy + 8, pet.dead() ? Ui.BAD : Ui.TEXT, false);
        }
    }

    /** Fits the pet in the box, tall or long. */
    private static int previewSize(LivingEntity entity, int boxW, int boxH) {
        float byHeight = (boxH - 34) / Math.max(0.4f, entity.getBbHeight());
        float byWidth = (boxW - 16) / Math.max(0.4f, entity.getBbWidth() * 1.6f);
        return Math.round(Math.clamp(Math.min(byHeight, byWidth), 10f, 64f));
    }

    private Entity entity() {
        Minecraft minecraft = Minecraft.getInstance();
        int id = state.details().entityId();
        return id < 0 || minecraft.level == null ? null : minecraft.level.getEntity(id);
    }

    private void drawInfo(GuiGraphicsExtractor gfx, PetsPacket.Pet pet, int mx, int my) {
        PetsPacket.Details d = state.details();
        int x = infoX();
        int w = infoW();
        int y = nameY();
        if (!renaming) {
            drawName(gfx, pet, x, y, w, overName(mx, my));
            if (overName(mx, my)) {
                gfx.setTooltipForNextFrame(font, Component.literal("Click to rename"), mx, my);
            }
        } else {
            Ui.framed(gfx, x - 4, y - 2, x + w + 2, y + 15, 0xFF070D1A, Ui.ACCENT);
        }
        y += 18;
        if (renaming) {
            gfx.text(font, Ui.fit(font, "Enter saves. Colors: &6gold, <#ff55aa>pink", w), x, y, Ui.FAINT, false);
        } else {
            String sub = pet.dead() ? pet.species().getString() : pet.species().getString() + "  ·  " + d.status();
            gfx.text(font, Ui.fit(font, sub, w), x, y, Ui.DIM, false);
        }
        y += 13;

        if (pet.dead()) {
            for (var line : font.split(Component.literal(d.death()), w)) {
                gfx.text(font, line, x, y, Ui.BAD, false);
                y += LINE;
            }
            y += 3;
            fact(gfx, "Where", d.where(), x, y, w);
            return;
        }

        drawHearts(gfx, pet, x, y, w);
        y += 14;
        int limit = scarfY() - 3;
        String place = pet.place().isEmpty() ? d.where() : pet.place();
        if (y + LINE <= limit) {
            y = fact(gfx, "Where", place, x, y, w);
        }
        if (!d.speed().isEmpty() && y + LINE <= limit) {
            y = fact(gfx, "Speed", d.speed(), x, y, w);
        }
        if (!d.jump().isEmpty() && y + LINE <= limit) {
            y = fact(gfx, "Jump", d.jump(), x, y, w);
        }
        if (!d.since().isEmpty() && y + LINE <= limit) {
            fact(gfx, "With you", d.since(), x, y, w);
        }
        drawScarf(gfx, d, x, w, mx, my);
        drawFood(gfx, d, mx, my);
    }

    /** The name, half again as big as normal text. */
    private void drawName(GuiGraphicsExtractor gfx, PetsPacket.Pet pet, int x, int y, int w, boolean hot) {
        Component name = pet.displayName();
        int room = Math.round(w / NAME_SCALE);
        gfx.pose().pushMatrix();
        gfx.pose().translate(x, y);
        gfx.pose().scale(NAME_SCALE, NAME_SCALE);
        if (font.width(name) <= room) {
            gfx.text(font, name, 0, 0, Ui.TEXT, false);
        } else {
            gfx.text(font, Ui.fit(font, name.getString(), room), 0, 0, Ui.TEXT, false);
        }
        gfx.pose().popMatrix();
        if (hot) {
            int nameW = Math.min(w, Math.round(font.width(name) * NAME_SCALE));
            gfx.fill(x, y + 13, x + nameW, y + 14, Ui.ACCENT);
        }
    }

    private void drawHearts(GuiGraphicsExtractor gfx, PetsPacket.Pet pet, int x, int y, int w) {
        float max = Math.max(1f, pet.maxHealth());
        float health = Math.clamp(pet.health(), 0f, max);
        int hearts = Math.min(10, (int) Math.ceil(max / 2f));
        float perHeart = max / hearts;
        int step = Ui.HEART_W + 1;
        for (int i = 0; i < hearts; i++) {
            float inThis = health - i * perHeart;
            int fill = inThis >= perHeart - 0.01f ? 2 : inThis >= perHeart / 2 - 0.01f ? 1 : 0;
            Ui.heart(gfx, x + i * step, y, fill);
        }
        String number = PetBrain.health(pet.health(), pet.maxHealth());
        int numberX = x + hearts * step + 4;
        if (numberX + font.width(number) <= x + w) {
            gfx.text(font, number, numberX, y, Ui.FAINT, false);
        }
    }

    private int fact(GuiGraphicsExtractor gfx, String label, String value, int x, int y, int w) {
        gfx.text(font, label, x, y, Ui.FAINT, false);
        int offset = Math.max(48, font.width(label) + 6);
        gfx.text(font, Ui.fit(font, value, w - offset), x + offset, y, Ui.TEXT, false);
        return y + LINE;
    }

    private void drawScarf(GuiGraphicsExtractor gfx, PetsPacket.Details d, int x, int w, int mx, int my) {
        if (!d.scarfFits()) {
            return;
        }
        int y = scarfY();
        gfx.text(font, "Scarf", x, y, Ui.FAINT, false);
        int vx = x + 48;
        if (d.scarf() < 0) {
            gfx.text(font, Ui.fit(font, "None", w - 48), vx, y, Ui.DIM, false);
            if (mx >= vx && mx < x + w && my >= y - 1 && my < y + 10) {
                gfx.setComponentTooltipForNextFrame(font, List.of(
                        Component.literal("Give it a Pet Scarf"),
                        Component.literal("Craft one from 3 wool and a string, dye it any colour,"),
                        Component.literal("then right-click your pet with it.")), mx, my);
            }
            return;
        }
        Ui.framed(gfx, vx, y - 1, vx + 16, y + 8, 0xFF000000 | d.scarf(), 0xFF0A1020);
        boolean hot = overTakeOff(mx, my);
        int tx = takeOffX();
        gfx.text(font, "Take off", tx, y, hot ? Ui.TEXT : Ui.ACCENT, false);
        if (hot) {
            gfx.fill(tx, y + 9, tx + font.width("Take off"), y + 10, Ui.TEXT);
        }
    }

    private void drawFood(GuiGraphicsExtractor gfx, PetsPacket.Details d, int mx, int my) {
        int x = infoX();
        int y = feedY();
        gfx.text(font, "Feed", x, y + 5, Ui.FAINT, false);
        int x0 = x + 30;
        List<PetsPacket.Food> foods = d.foods();
        if (foods.isEmpty()) {
            String text = d.entityId() < 0 ? "Too far away to feed" : "You carry nothing it eats";
            gfx.text(font, Ui.fit(font, text, infoW() - 30), x0, y + 5, Ui.DIM, false);
            return;
        }
        int hovered = foodAt(mx, my);
        List<Component> tip = new ArrayList<>();
        int fits = (infoX() + infoW() - x0 + 2) / (TILE + 2);
        for (int i = 0; i < foods.size() && i < fits; i++) {
            int tx = x0 + i * (TILE + 2);
            boolean hot = i == hovered;
            Ui.framed(gfx, tx, y, tx + TILE, y + TILE, hot ? Ui.BUTTON_HOVER : Ui.BUTTON, hot ? Ui.ACCENT : Ui.BUTTON_EDGE);
            ItemStack stack = stack(foods.get(i));
            gfx.item(stack, tx + 1, y + 1);
            gfx.itemDecorations(font, stack, tx + 1, y + 1);
            if (hot) {
                tip.add(Component.literal("Feed it ").append(stack.getHoverName()));
                tip.add(Component.literal(!d.near() ? "Too far away. Call it first."
                        : d.hungry() ? "Heals it, or gets it ready to breed" : "It is not hungry"));
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
        int x = infoX();
        int w = infoW();
        int y = nameY();
        gfx.text(font, "Give", x, y + 2, Ui.DIM, false);
        gfx.text(font, pet.displayName(), x + font.width("Give ") , y + 2, Ui.TEXT, false);
        gfx.text(font, "to who? Click a name twice.", x, y + 14, Ui.FAINT, false);
        List<PetsPacket.Person> players = state.players();
        if (players.isEmpty()) {
            gfx.text(font, "Nobody else is online.", x, personTop() + 3, Ui.DIM, false);
            return;
        }
        int hovered = personAt(mx, my);
        for (int i = 0; i < players.size() && personTop() + (i + 1) * 16 <= bodyY1(); i++) {
            PetsPacket.Person person = players.get(i);
            int ry = personTop() + i * 16;
            boolean armed = person.id().equals(armedPlayer);
            if (armed) {
                gfx.fill(x, ry, x + w, ry + 14, Ui.ACCENT);
            } else {
                Ui.framed(gfx, x, ry, x + w, ry + 14, i == hovered ? Ui.BUTTON_HOVER : Ui.BUTTON, Ui.BUTTON_EDGE);
            }
            String label = armed ? "Click again to give it to " + person.name() : person.name();
            gfx.text(font, Ui.fit(font, label, w - 10), x + 5, ry + 3, armed ? Ui.ACCENT_TEXT : Ui.TEXT, false);
        }
    }

    /** The last answer from the server, as a small box under the menu that fades out. */
    private void drawNotice(GuiGraphicsExtractor gfx) {
        long age = System.currentTimeMillis() - noticeAt;
        if (notice.isEmpty() || age > NOTICE_MS) {
            return;
        }
        float alpha = age > NOTICE_MS - 500 ? (NOTICE_MS - age) / 500f : 1f;
        String text = Ui.fit(font, notice, cardW() - 24);
        int w = font.width(text) + 16;
        int x0 = (width - w) / 2;
        int y0 = Math.min(height - 16, bottom() + 6);
        Ui.framed(gfx, x0, y0, x0 + w, y0 + 14, Ui.fade(0xF00B1424, alpha), Ui.fade(Ui.PANEL_EDGE, alpha));
        gfx.fill(x0 + 4, y0 + 5, x0 + 7, y0 + 8, Ui.fade(noticeOk ? Ui.GOOD : Ui.BAD, alpha));
        gfx.text(font, text, x0 + 11, y0 + 3, Ui.fade(Ui.TEXT, alpha), false);
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
}

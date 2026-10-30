package my.celium.org.betterpets.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import my.celium.org.betterpets.PetBrain;
import my.celium.org.betterpets.net.PetActionPacket;
import my.celium.org.betterpets.net.PetActionPacket.Action;
import my.celium.org.betterpets.net.PetsPacket;
import my.celium.org.client.MycelScreens;
import my.celium.org.client.MycelTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
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
 * The pet screen: your pets down the left, the selected one alive in the
 * middle (it watches the mouse), and on the right its name, health, stats, food
 * and what you can do with it. All state comes from the server in
 * {@link PetsPacket}; every button goes back as a {@link PetActionPacket}.
 */
public final class PetScreen extends Screen {
    private static final int TITLE_BAR = 20;
    private static final int PAD = 6;
    private static final int ROW_H = 20;
    private static final int TILE = 20;
    private static final int LINE = 10;
    private static final int BUTTON_COLS = 3;
    private static final int GAP = 3;
    private static final int KEY_ENTER = 257;
    private static final long NOTICE_MS = 4000;

    private PetsPacket state;
    private EditBox nameBox;
    private FlatButton saveButton;
    private FlatButton sitButton;
    private FlatButton inventoryButton;
    private FlatButton findButton;
    private FlatButton callButton;
    private FlatButton giveButton;
    private FlatButton forgetButton;
    private FlatButton cancelGiveButton;
    private FlatButton closeButton;
    private UUID shownPet;
    private int scroll;
    private boolean giving;
    private UUID armedPlayer;
    private String notice = "";
    private boolean noticeOk;
    private long noticeUntil;

    PetScreen(PetsPacket state) {
        super(Component.literal("Better Pets"));
        this.state = state;
    }

    /** New data from the server. */
    void update(PetsPacket packet) {
        state = packet;
        if (!packet.notice().isEmpty()) {
            notice = packet.notice();
            noticeOk = packet.noticeOk();
            noticeUntil = System.currentTimeMillis() + NOTICE_MS;
        }
        if (nameBox != null) {
            sync();
        }
    }

    // ================================================================ layout

    private int panelW() {
        return Math.clamp(width - 12, 300, 420);
    }

    private int panelH() {
        return Math.clamp(height - 12, 160, 240);
    }

    private int px() {
        return (width - panelW()) / 2;
    }

    private int py() {
        return (height - panelH()) / 2;
    }

    private int listW() {
        return panelW() >= 380 ? 112 : 88;
    }

    private int previewW() {
        return panelW() >= 380 ? 104 : 72;
    }

    private int listX() {
        return px() + 2;
    }

    private int previewX() {
        return listX() + listW() + PAD;
    }

    private int detailsX() {
        return previewX() + previewW() + PAD;
    }

    private int detailsW() {
        return px() + panelW() - PAD - detailsX();
    }

    private int bodyY() {
        return py() + TITLE_BAR + PAD;
    }

    private int bodyBottom() {
        return py() + panelH() - PAD;
    }

    /** Where the action buttons start: two rows at the bottom of the details column. */
    private int buttonsY() {
        return bodyBottom() - FlatButton.HEIGHT * 2 - GAP;
    }

    private int feedY() {
        return buttonsY() - TILE - GAP - 1;
    }

    private int feedLabelY() {
        return feedY() - LINE;
    }

    @Override
    protected void init() {
        nameBox = new EditBox(font, detailsX(), bodyY(), detailsW(), 16, Component.literal("Name"));
        nameBox.setMaxLength(PetBrain.MAX_TYPED_LENGTH);
        nameBox.setHint(Component.literal("Give it a name"));
        nameBox.setTooltip(Tooltip.create(Component.literal(
                "Press Enter to save. Colors work: &6Gold, <#ff55aa>pink, <gradient:gold:red>fire")));
        addRenderableWidget(nameBox);
        saveButton = addRenderableWidget(new FlatButton(detailsX() + detailsW() - 34, bodyY() - 1, 34,
                Component.literal("Save"), true, this::saveName));

        int w = (detailsW() - GAP * (BUTTON_COLS - 1)) / BUTTON_COLS;
        sitButton = action(0, 0, w, "Sit", "It stays right here", () -> send(Action.SIT));
        inventoryButton = action(0, 0, w, "Gear", "Saddle, armor and what it carries", () -> send(Action.INVENTORY));
        findButton = action(1, 0, w, "Find", "Make it glow and point the way", () -> sendAndClose(Action.FIND));
        callButton = action(2, 0, w, "Call", "Teleport it to you", () -> sendAndClose(Action.CALL));
        giveButton = action(0, 1, w, "Give", "Hand it to another player", () -> {
            giving = true;
            armedPlayer = null;
            sync();
        });
        forgetButton = action(1, 1, w, "Forget", "Remove it from your list", () -> send(Action.FORGET));
        cancelGiveButton = action(2, 1, w, "Cancel", "Keep it", () -> {
            giving = false;
            sync();
        });
        closeButton = action(2, 1, w, "Close", "Back to the game", this::onClose);
        shownPet = null;
        sync();
    }

    private FlatButton action(int col, int row, int w, String label, String hint, Runnable run) {
        FlatButton button = new FlatButton(detailsX() + col * (w + GAP), buttonsY() + row * (FlatButton.HEIGHT + GAP), w,
                Component.literal(label), false, run);
        button.setTooltip(Tooltip.create(Component.literal(hint)));
        return addRenderableWidget(button);
    }

    /** Makes the widgets match the state. */
    private void sync() {
        PetsPacket.Pet pet = selected();
        PetsPacket.Details d = state.details();
        boolean alive = pet != null && !pet.dead();
        if (pet != null && !pet.id().equals(shownPet)) {
            shownPet = pet.id();
            giving = false;
            nameBox.setValue(typedName(pet));
            nameBox.setCursorPosition(0);
            nameBox.setHighlightPos(0);
        } else if (pet != null && !nameBox.isFocused()) {
            nameBox.setValue(typedName(pet));
        }
        boolean renaming = alive && state.allowRename();
        nameBox.visible = alive && !giving;
        nameBox.setEditable(renaming);
        saveButton.visible = renaming && !giving && !nameBox.getValue().equals(typedName(pet));
        nameBox.setWidth(saveButton.visible ? detailsW() - 36 : detailsW());

        boolean rest = !giving;
        sitButton.visible = rest && d.canSit();
        sitButton.active = alive && d.near();
        sitButton.setMessage(Component.literal(d.sitting() ? "Stand" : "Sit"));
        inventoryButton.visible = rest && !d.canSit();
        inventoryButton.active = alive && d.near() && d.hasInventory();
        sitButton.setTooltip(tip(d.near(), d.sitting() ? "It follows you again" : "It stays right here"));
        inventoryButton.setTooltip(tip(d.near(), "Saddle, armor and what it carries"));
        findButton.visible = rest;
        findButton.active = alive;
        callButton.visible = rest;
        callButton.active = alive && state.allowCall();
        giveButton.visible = rest;
        giveButton.active = alive && state.allowGive();
        forgetButton.visible = rest;
        forgetButton.active = pet != null && (pet.dead() || !pet.loaded());
        cancelGiveButton.visible = giving;
        closeButton.visible = rest;
    }

    private static Tooltip tip(boolean near, String hint) {
        return Tooltip.create(Component.literal(near ? hint : "Too far away, call it first"));
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

    private void saveName() {
        ClientPets.send(new PetActionPacket(Action.RENAME, state.selected(), 0, nameBox.getValue()));
        nameBox.setFocused(false);
        setFocused(null);
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
        if (event.key() == KEY_ENTER && nameBox.isFocused()) {
            saveName();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        double mx = event.x();
        double my = event.y();
        int row = rowAt(mx, my);
        if (row >= 0) {
            ClientPets.send(new PetActionPacket(Action.SELECT, state.pets().get(row).id()));
            click();
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
        int food = foodAt(mx, my);
        if (food >= 0) {
            ClientPets.send(new PetActionPacket(Action.FEED, state.selected(),
                    state.details().foods().get(food).slot(), ""));
            click();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (x < listX() + listW()) {
            scroll = Math.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    private int visibleRows() {
        return (bodyBottom() - bodyY()) / ROW_H;
    }

    private int maxScroll() {
        return Math.max(0, state.pets().size() - visibleRows());
    }

    private int rowAt(double mx, double my) {
        if (mx < listX() || mx >= listX() + listW() || my < bodyY() || my >= bodyY() + visibleRows() * ROW_H) {
            return -1;
        }
        int row = (int) (my - bodyY()) / ROW_H + scroll;
        return row < state.pets().size() ? row : -1;
    }

    private int foodAt(double mx, double my) {
        if (my < feedY() || my >= feedY() + TILE || mx < detailsX()) {
            return -1;
        }
        int food = (int) (mx - detailsX()) / (TILE + 1);
        return food < state.details().foods().size() ? food : -1;
    }

    private int personAt(double mx, double my) {
        int top = bodyY() + 28;
        if (mx < detailsX() || mx >= detailsX() + detailsW() || my < top) {
            return -1;
        }
        int person = (int) (my - top) / 14;
        return person < state.players().size() && top + (person + 1) * 14 <= buttonsY() ? person : -1;
    }

    // ================================================================ render

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float a) {
        int px = px();
        int py = py();
        int pw = panelW();
        int ph = panelH();
        scroll = Math.clamp(scroll, 0, maxScroll());
        if (nameBox.isFocused()) {
            saveButton.visible = !giving && nameBox.isActive() && !nameBox.getValue().equals(typedName(selected()));
            nameBox.setWidth(saveButton.visible ? detailsW() - 36 : detailsW());
        }
        gfx.fill(0, 0, width, height, MycelTheme.SHADE);
        MycelTheme.panel(gfx, px, py, px + pw, py + ph);
        gfx.fill(px + 2, py + 2, px + pw - 2, py + TITLE_BAR, MycelTheme.ACCENT_DARK);
        gfx.fill(px + 2, py + TITLE_BAR - 1, px + pw - 2, py + TITLE_BAR, MycelTheme.HEADER_EDGE);
        gfx.text(font, title, px + 9, py + 7, MycelTheme.HEADING);
        String count = state.pets().size() == 1 ? "1 pet" : state.pets().size() + " pets";
        gfx.text(font, count, px + pw - 9 - font.width(count), py + 7, 0xFFFFCDD2);

        renderList(gfx, mouseX, mouseY);
        renderPreview(gfx, mouseX, mouseY);
        PetsPacket.Pet pet = selected();
        if (pet != null) {
            if (giving) {
                renderGive(gfx, pet, mouseX, mouseY);
            } else {
                renderDetails(gfx, pet, mouseX, mouseY);
            }
        }
        super.extractRenderState(gfx, mouseX, mouseY, a);
    }

    private void renderList(GuiGraphicsExtractor gfx, int mx, int my) {
        int x = listX();
        int w = listW();
        gfx.fill(x, py() + TITLE_BAR, x + w, py() + panelH() - 2, 0xFF1C1C1C);
        gfx.fill(x + w, py() + TITLE_BAR, x + w + 1, py() + panelH() - 2, MycelTheme.BORDER);
        int y = bodyY();
        int hovered = rowAt(mx, my);
        for (int i = scroll; i < state.pets().size() && i < scroll + visibleRows(); i++) {
            PetsPacket.Pet pet = state.pets().get(i);
            boolean on = pet.id().equals(state.selected());
            if (on) {
                gfx.fill(x, y, x + w, y + ROW_H - 1, 0xFF3A1414);
                gfx.fill(x, y, x + 2, y + ROW_H - 1, MycelTheme.ACCENT);
            } else if (i == hovered) {
                gfx.fill(x, y, x + w, y + ROW_H - 1, MycelTheme.SURFACE);
            }
            gfx.item(icon(pet), x + 3, y + 1);
            int textX = x + 21;
            int room = w - 24;
            Component name = pet.displayName();
            if (font.width(name) <= room) {
                gfx.text(font, name, textX, y + 2, MycelTheme.TEXT);
            } else {
                gfx.text(font, MycelTheme.fit(font, name.getString(), room), textX, y + 2, MycelTheme.TEXT);
            }
            if (pet.dead()) {
                gfx.text(font, "Dead", textX, y + 11, MycelTheme.BAD);
            } else {
                healthLine(gfx, textX, y + 13, room, 3, pet);
            }
            y += ROW_H;
        }
        if (maxScroll() > 0) {
            int track = visibleRows() * ROW_H;
            int thumb = Math.max(8, track * visibleRows() / state.pets().size());
            int top = bodyY() + (track - thumb) * scroll / maxScroll();
            gfx.fill(x + w - 2, top, x + w, top + thumb, 0xFF5A5A5A);
        }
    }

    private void healthLine(GuiGraphicsExtractor gfx, int x, int y, int w, int h, PetsPacket.Pet pet) {
        gfx.fill(x, y, x + w, y + h, MycelTheme.BORDER);
        int filled = pet.maxHealth() <= 0 ? 0 : Math.round(Math.clamp(pet.health() / pet.maxHealth(), 0f, 1f) * w);
        gfx.fill(x, y, x + filled, y + h, healthColor(pet));
    }

    private static int healthColor(PetsPacket.Pet pet) {
        float part = pet.maxHealth() <= 0 ? 0 : pet.health() / pet.maxHealth();
        return part >= 0.6f ? MycelTheme.GOOD : part >= 0.3f ? MycelTheme.WARN : MycelTheme.BAD;
    }

    private void renderPreview(GuiGraphicsExtractor gfx, int mx, int my) {
        int x0 = previewX();
        int y0 = bodyY();
        int x1 = x0 + previewW();
        int y1 = bodyBottom();
        gfx.fill(x0, y0, x1, y1, MycelTheme.BORDER);
        gfx.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, MycelTheme.SURFACE);
        PetsPacket.Pet pet = selected();
        Entity entity = entity();
        if (entity instanceof LivingEntity living) {
            int size = previewSize(living, x1 - x0, y1 - y0);
            InventoryScreen.extractEntityInInventoryFollowsMouse(gfx, x0 + 1, y0 + 1, x1 - 1, y1 - 1, size, 0.0625f,
                    mx, my, living);
        } else if (pet != null) {
            int cx = (x0 + x1) / 2;
            int cy = (y0 + y1) / 2;
            gfx.item(icon(pet), cx - 8, cy - 14);
            String text = pet.dead() ? "Gone" : "Far away";
            gfx.centeredText(font, text, cx, cy + 8, pet.dead() ? MycelTheme.BAD : MycelTheme.DIM);
        }
        if (pet != null) {
            previewName(gfx, pet, x0, y0, x1);
        }
    }

    /** The name with its colors, above the pet; long names are cut and lose them. */
    private void previewName(GuiGraphicsExtractor gfx, PetsPacket.Pet pet, int x0, int y0, int x1) {
        int room = x1 - x0 - 8;
        int cx = (x0 + x1) / 2;
        Component name = pet.displayName();
        if (font.width(name) <= room) {
            gfx.centeredText(font, name, cx, y0 + 5, MycelTheme.HEADING);
        } else {
            gfx.centeredText(font, MycelTheme.fit(font, name.getString(), room), cx, y0 + 5, MycelTheme.HEADING);
        }
    }

    /** Fits the pet in the box, tall or long. */
    private static int previewSize(LivingEntity entity, int boxW, int boxH) {
        float byHeight = (boxH - 34) / Math.max(0.4f, entity.getBbHeight());
        float byWidth = (boxW - 10) / Math.max(0.4f, entity.getBbWidth() * 1.6f);
        return Math.round(Math.clamp(Math.min(byHeight, byWidth), 10f, 70f));
    }

    private Entity entity() {
        Minecraft minecraft = Minecraft.getInstance();
        int id = state.details().entityId();
        return id < 0 || minecraft.level == null ? null : minecraft.level.getEntity(id);
    }

    private void renderDetails(GuiGraphicsExtractor gfx, PetsPacket.Pet pet, int mx, int my) {
        PetsPacket.Details d = state.details();
        int x = detailsX();
        int w = detailsW();
        int y = bodyY();
        if (pet.dead()) {
            gfx.text(font, pet.displayName(), x, y + 4, MycelTheme.HEADING);
            y += 18;
            for (var line : font.split(Component.literal(d.death()), w)) {
                gfx.text(font, line, x, y, MycelTheme.BAD);
                y += LINE;
            }
            y += 2;
            y = fact(gfx, "Where", d.where(), x, y, w);
            renderNotice(gfx, x, buttonsY() - LINE - 1, w);
            return;
        }
        y += 19;
        // Health bar with the number on it.
        gfx.fill(x, y, x + w, y + 11, MycelTheme.BORDER);
        gfx.fill(x + 1, y + 1, x + w - 1, y + 10, 0xFF1C1C1C);
        int inner = w - 2;
        int filled = pet.maxHealth() <= 0 ? 0 : Math.round(Math.clamp(pet.health() / pet.maxHealth(), 0f, 1f) * inner);
        gfx.fill(x + 1, y + 1, x + 1 + filled, y + 10, darken(healthColor(pet)));
        gfx.centeredText(font, "♥ " + PetBrain.health(pet.health(), pet.maxHealth()), x + w / 2, y + 2,
                MycelTheme.HEADING);
        y += 14;

        int limit = feedLabelY() - LINE;
        y = limited(gfx, d.status(), pet, x, y, w, limit);
        if (y + LINE <= limit) {
            y = fact(gfx, "At", pet.place().isEmpty() ? d.where() : pet.place(), x, y, w);
        }
        if (!d.speed().isEmpty() && y + LINE <= limit) {
            y = fact(gfx, "Speed", d.speed(), x, y, w);
        }
        if (!d.jump().isEmpty() && y + LINE <= limit) {
            y = fact(gfx, "Jump", d.jump(), x, y, w);
        }
        if (!d.since().isEmpty() && y + LINE <= limit) {
            fact(gfx, "Since", d.since(), x, y, w);
        }

        gfx.text(font, "FEED", x, feedLabelY(), MycelTheme.FAINT);
        renderNotice(gfx, x, feedLabelY() - LINE, w);
        renderFood(gfx, d, mx, my);
    }

    /** Species and what it is doing, on one line. */
    private int limited(GuiGraphicsExtractor gfx, String status, PetsPacket.Pet pet, int x, int y, int w, int limit) {
        if (y + LINE > limit) {
            return y;
        }
        String line = pet.species().getString() + "  ·  " + status;
        gfx.text(font, MycelTheme.fit(font, line, w), x, y, MycelTheme.DIM);
        return y + LINE;
    }

    private int fact(GuiGraphicsExtractor gfx, String label, String value, int x, int y, int w) {
        gfx.text(font, label, x, y, MycelTheme.FAINT);
        int offset = font.width(label) + 4;
        gfx.text(font, MycelTheme.fit(font, value, w - offset), x + offset, y, MycelTheme.VALUE);
        return y + LINE;
    }

    private static int darken(int argb) {
        int r = (argb >> 16 & 0xFF) * 6 / 10;
        int g = (argb >> 8 & 0xFF) * 6 / 10;
        int b = (argb & 0xFF) * 6 / 10;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private void renderNotice(GuiGraphicsExtractor gfx, int x, int y, int w) {
        if (System.currentTimeMillis() < noticeUntil) {
            gfx.fill(x, y - 1, x + w, y + LINE - 1, MycelTheme.BG);
            gfx.text(font, MycelTheme.fit(font, notice, w), x, y, noticeOk ? MycelTheme.GOOD : MycelTheme.WARN);
        }
    }

    private void renderFood(GuiGraphicsExtractor gfx, PetsPacket.Details d, int mx, int my) {
        int x = detailsX();
        int y = feedY();
        List<PetsPacket.Food> foods = d.foods();
        if (foods.isEmpty()) {
            gfx.fill(x, y, x + detailsW(), y + TILE, MycelTheme.SURFACE);
            String text = d.entityId() < 0 ? "Too far away to feed" : "You carry nothing it eats";
            gfx.text(font, MycelTheme.fit(font, text, detailsW() - 8), x + 4, y + 6, MycelTheme.FAINT);
            return;
        }
        int hovered = foodAt(mx, my);
        List<Component> tip = new ArrayList<>();
        for (int i = 0; i < foods.size() && x + (i + 1) * (TILE + 1) <= detailsX() + detailsW() + 1; i++) {
            PetsPacket.Food food = foods.get(i);
            int tx = x + i * (TILE + 1);
            gfx.fill(tx, y, tx + TILE, y + TILE, i == hovered ? MycelTheme.ACCENT : MycelTheme.BORDER);
            gfx.fill(tx + 1, y + 1, tx + TILE - 1, y + TILE - 1, i == hovered ? 0xFF3A1414 : MycelTheme.SURFACE);
            ItemStack stack = stack(food);
            gfx.item(stack, tx + 2, y + 2);
            gfx.itemDecorations(font, stack, tx + 2, y + 2);
            if (i == hovered) {
                tip.add(Component.literal("Feed it ").append(stack.getHoverName()));
                tip.add(Component.literal(!d.near() ? "Too far away, call it first"
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

    private void renderGive(GuiGraphicsExtractor gfx, PetsPacket.Pet pet, int mx, int my) {
        int x = detailsX();
        int w = detailsW();
        int y = bodyY();
        gfx.text(font, "Give ", x, y + 4, MycelTheme.DIM);
        gfx.text(font, pet.displayName(), x + font.width("Give "), y + 4, MycelTheme.HEADING);
        gfx.text(font, "to who? Two clicks to confirm.", x, y + 16, MycelTheme.FAINT);
        List<PetsPacket.Person> players = state.players();
        if (players.isEmpty()) {
            gfx.text(font, "Nobody else is online", x, y + 30, MycelTheme.DIM);
            return;
        }
        int hovered = personAt(mx, my);
        int top = y + 28;
        for (int i = 0; i < players.size() && top + (i + 1) * 14 <= buttonsY(); i++) {
            PetsPacket.Person person = players.get(i);
            int ry = top + i * 14;
            boolean armed = person.id().equals(armedPlayer);
            gfx.fill(x, ry, x + w, ry + 13, armed ? MycelTheme.ACCENT_DARK : i == hovered ? MycelTheme.BORDER : MycelTheme.SURFACE);
            String label = armed ? "Click again: " + person.name() : person.name();
            gfx.text(font, MycelTheme.fit(font, label, w - 8), x + 4, ry + 3, MycelTheme.TEXT);
        }
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

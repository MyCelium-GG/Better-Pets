package my.celium.org.betterpets;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;

import my.celium.org.util.TextUtil;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.ConfirmationDialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.action.CommandTemplate;
import net.minecraft.server.dialog.action.ParsedTemplate;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

/**
 * The chest version of the pet screen, for players without the mod on their
 * client (see {@link PetPanel}): what the pet is, a health bar, and buttons to
 * feed, seat, find, call, rename or give it. Renaming opens a vanilla dialog
 * with a text box.
 */
final class PetMenu extends PetGui {
    private static final int ROWS = 4;
    private static final int INFO_SLOT = 4;
    private static final int HEALTH_ROW = 9;
    private static final int HEALTH_SLOTS = 9;
    private static final int ACTION_ROW = 18;
    private static final int BACK_SLOT = 27;
    private static final int CLOSE_SLOT = 31;
    private static final String NAME_INPUT = "name";

    /** Players with the rename dialog open: {@code /pets name} takes them back to the menu. */
    private static final Set<UUID> RENAMING = new HashSet<>();

    private final UUID petId;

    private PetMenu(int containerId, ServerPlayer viewer, UUID petId) {
        super(containerId, viewer, ROWS);
        this.petId = petId;
    }

    static void open(ServerPlayer player, PetRecord pet) {
        RENAMING.remove(player.getUUID());
        PetGui.open(player, PetText.name(pet), id -> new PetMenu(id, player, pet.id));
    }

    /**
     * The shift-right-click on a pet, on the server. Items a pet reacts to
     * (food, a lead, dye, armor...) keep working while sneaking; so does
     * clicking someone else's pet. Returns whether the click was used.
     */
    static boolean onInteract(Player player, Entity target, boolean mainHand) {
        if (!(player instanceof ServerPlayer viewer) || !player.isShiftKeyDown() || !BetterPetsMod.isModEnabled()
                || !BetterPetsMod.petMenu.get() || !player.getUUID().equals(PetTracker.ownerOf(target))) {
            return false;
        }
        if (PetCare.reactsTo(target, player.getMainHandItem())) {
            return false;
        }
        // Both hands fire; only the main hand opens, but neither may fall through to vanilla.
        if (mainHand && !(player.containerMenu instanceof PetGui)) {
            PetPanel.open(viewer, PetTracker.track(viewer.getUUID(), target));
        }
        return true;
    }

    /** {@code /pets name} finished: whether it came from the dialog, which then reopens the menu. */
    static boolean fromDialog(ServerPlayer player) {
        return RENAMING.remove(player.getUUID());
    }

    static void clear() {
        RENAMING.clear();
    }

    @Override
    protected void render() {
        PetRecord pet = PetTracker.record(viewer.getUUID(), petId);
        if (pet == null) {
            close(); // given away, or forgotten
            return;
        }
        Entity entity = PetTracker.loaded(petId);
        put(INFO_SLOT, info(pet, entity));
        healthBar(pet, entity);
        List<Button> actions = pet.dead ? List.of(forgetButton(pet, PetText.t(viewer, "button.forget.tip"))) : actions(pet, entity);
        int[] columns = PetBrain.centered(actions.size());
        for (int i = 0; i < columns.length; i++) {
            button(ACTION_ROW + columns[i], actions.get(i).icon(), actions.get(i).action());
        }
        button(BACK_SLOT, icon(Items.ARROW, TextUtil.hex(PetText.t(viewer, "menu.all_pets"), PetText.BUTTON)),
                () -> PetListMenu.open(viewer));
        button(CLOSE_SLOT, icon(Items.BARRIER, TextUtil.hex(PetText.t(viewer, "menu.close"), PetText.DEAD)), this::close);
    }

    private record Button(ItemStack icon, Runnable action) {
    }

    // ---------------------------------------------------------------- what it shows

    private ItemStack info(PetRecord pet, Entity entity) {
        long now = System.currentTimeMillis();
        List<Component> lore = new ArrayList<>();
        lore.add(PetText.species(pet).withStyle(style -> style.withColor(TextUtil.MUTED)));
        if (pet.dead) {
            lore.add(TextUtil.hex(pet.deathMessage == null ? PetText.t(viewer, "died") : pet.deathMessage, PetText.DEAD));
            lore.add(TextUtil.muted(PetText.ago(viewer, now - pet.lastSeen)));
        } else {
            lore.add(TextUtil.body(status(viewer, pet, entity)));
        }
        lore.add(TextUtil.empty());
        lore.add(TextUtil.muted(PetText.t(viewer, "fact.where") + "  ").append(TextUtil.hex(PetText.blockCoords(pet.x, pet.y, pet.z), PetText.VALUE)));
        lore.add(TextUtil.muted(PetText.t(viewer, "fact.dimension") + "  ").append(TextUtil.body(PetText.dimension(viewer, pet.dimension))));
        if (entity == null && !pet.dead) {
            lore.add(TextUtil.muted(PetText.t(viewer, "fact.seen") + "  ").append(TextUtil.body(PetText.ago(viewer, now - pet.lastSeen))));
        }
        if (pet.firstSeen > 0) {
            lore.add(TextUtil.muted(PetText.t(viewer, "fact.since") + "  ").append(TextUtil.body(PetText.ago(viewer, now - pet.firstSeen))));
        }
        if (entity instanceof AbstractHorse horse) {
            lore.add(TextUtil.empty());
            lore.add(TextUtil.muted(PetText.t(viewer, "fact.speed") + "  ").append(TextUtil.hex(PetText.speed(viewer,
                    horse.getAttributeValue(Attributes.MOVEMENT_SPEED)), PetText.VALUE)));
            lore.add(TextUtil.muted(PetText.t(viewer, "fact.jump") + "  ").append(TextUtil.hex(PetText.jump(viewer,
                    horse.getAttributeValue(Attributes.JUMP_STRENGTH)), PetText.VALUE)));
        }
        return named(new ItemStack(pet.dead ? Items.SKELETON_SKULL : egg(pet)), PetText.name(pet), lore);
    }

    static String status(net.minecraft.world.entity.player.Player player, PetRecord pet, Entity entity) {
        if (entity == null) {
            return PetText.t(player, "status.far");
        }
        if (entity instanceof LivingEntity living && living.isBaby()) {
            return PetText.t(player, pet.sitting ? "status.baby_sitting" : "status.baby");
        }
        return PetText.t(player, pet.sitting ? "status.sitting" : pet.leashed ? "status.leashed" : "status.about");
    }

    /** The species' spawn egg, or a name tag for a modded pet without one. */
    static Item egg(PetRecord pet) {
        Identifier type = pet.type == null ? null : Identifier.tryParse(pet.type);
        return BuiltInRegistries.ENTITY_TYPE.getOptional(type)
                .flatMap(SpawnEggItem::byId)
                .map(Holder::value)
                .orElse(Items.NAME_TAG);
    }

    private void healthBar(PetRecord pet, Entity entity) {
        float health = entity instanceof LivingEntity living ? living.getHealth() : pet.health;
        float max = entity instanceof LivingEntity living ? living.getMaxHealth() : pet.maxHealth;
        int filled = PetBrain.healthSlots(health, max, HEALTH_SLOTS);
        Item color = pane(health >= max * 0.6f ? "lime" : health >= max * 0.3f ? "yellow" : "red");
        Component text = TextUtil.hex("♥ ", PetText.HEART).append(TextUtil.body(PetBrain.health(health, max)));
        for (int i = 0; i < HEALTH_SLOTS; i++) {
            put(HEALTH_ROW + i, icon(i < filled ? color : pane("gray"), text));
        }
    }

    // ---------------------------------------------------------------- what it does

    private List<Button> actions(PetRecord pet, Entity entity) {
        List<Button> actions = new ArrayList<>();
        actions.add(feedButton(pet, entity));
        if (entity != null && PetCare.canSit(entity)) {
            actions.add(sitButton(pet, entity));
        }
        if (entity != null && PetCare.hasInventory(entity)) {
            actions.add(new Button(icon(Items.CHEST, TextUtil.hex(PetText.t(viewer, "action.inventory"), PetText.NAME),
                    TextUtil.muted(PetText.t(viewer, "action.inventory.tip")), nearbyHint(entity)),
                    () -> {
                        if (checkNearby(pet, entity)) {
                            PetCare.openInventory(viewer, entity);
                        }
                    }));
        }
        actions.add(new Button(icon(Items.COMPASS, TextUtil.hex(PetText.t(viewer, "button.find"), PetText.NAME),
                TextUtil.muted(PetText.t(viewer, "button.find.tip"))), () -> {
                    close();
                    PetsCommand.find(viewer, pet);
                }));
        if (BetterPetsMod.allowCall.get()) {
            actions.add(new Button(icon(Items.BELL, TextUtil.hex(PetText.t(viewer, "button.call"), PetText.NAME),
                    TextUtil.muted(PetText.t(viewer, "button.call.tip"))), () -> {
                        close();
                        PetsCommand.call(viewer, pet);
                    }));
        }
        if (BetterPetsMod.allowRename.get()) {
            actions.add(new Button(icon(Items.NAME_TAG, TextUtil.hex(PetText.t(viewer, "action.rename"), PetText.NAME),
                    TextUtil.muted(PetText.t(viewer, "action.rename.tip"))), () -> openRename(pet)));
        }
        if (BetterPetsMod.allowGive.get()) {
            actions.add(new Button(icon(Items.POPPY, TextUtil.hex(PetText.t(viewer, "action.give"), PetText.NAME),
                    TextUtil.muted(PetText.t(viewer, "action.give.tip"))), () -> PetGiveMenu.open(viewer, pet)));
        }
        if (entity == null) {
            actions.add(forgetButton(pet, PetText.t(viewer, "action.forget.lost")));
        }
        return actions;
    }

    private Button forgetButton(PetRecord pet, String hint) {
        return new Button(icon(Items.LAVA_BUCKET, TextUtil.hex(PetText.t(viewer, "button.forget"), PetText.DEAD), TextUtil.muted(hint)), () -> {
            close();
            PetsCommand.forget(viewer, pet);
        });
    }

    private Button feedButton(PetRecord pet, Entity entity) {
        int slot = entity == null ? -1 : PetCare.foodSlot(viewer, entity);
        List<Component> lore = new ArrayList<>();
        ItemStack icon;
        if (slot < 0) {
            icon = new ItemStack(Items.BOWL);
            lore.add(TextUtil.muted(PetText.t(viewer, entity == null ? "feed.far" : "feed.nothing")));
        } else {
            ItemStack food = viewer.getInventory().getItem(slot);
            icon = new ItemStack(food.getItem(), food.getCount());
            lore.add(TextUtil.coloredHex(PetText.c(viewer, "feed.with", food.getHoverName().copy()
                    .withStyle(style -> style.withColor(PetText.VALUE))), TextUtil.MUTED));
            lore.add(TextUtil.muted(PetText.t(viewer, PetCare.isHungry(entity) ? "feed.heals" : "feed.not_hungry")));
            lore.add(nearbyHint(entity));
        }
        return new Button(named(icon, TextUtil.hex(PetText.t(viewer, "action.feed"), PetText.NAME), lore), () -> feed(pet));
    }

    private void feed(PetRecord pet) {
        Entity entity = PetTracker.loaded(pet.id);
        if (!checkNearby(pet, entity)) {
            return;
        }
        int slot = PetCare.foodSlot(viewer, entity);
        if (slot < 0) {
            viewer.sendSystemMessage(PetText.fail(PetText.c(viewer, "feed.nothing_for", PetText.name(pet))), true);
        } else if (!PetCare.isHungry(entity) || !PetCare.feed(viewer, entity, slot)) {
            viewer.sendSystemMessage(PetText.fail(PetText.c(viewer, "feed.not_hungry_pet", PetText.name(pet))), true);
        }
    }

    private Button sitButton(PetRecord pet, Entity entity) {
        boolean sitting = entity instanceof TamableAnimal tamable && tamable.isOrderedToSit();
        return new Button(icon(sitting ? Items.FEATHER : Items.OAK_STAIRS,
                TextUtil.hex(PetText.t(viewer, sitting ? "action.stand" : "action.sit"), PetText.NAME),
                TextUtil.muted(PetText.t(viewer, sitting ? "action.stand.tip" : "action.sit.tip")), nearbyHint(entity)),
                () -> {
                    if (checkNearby(pet, entity) && entity instanceof TamableAnimal tamable) {
                        PetCare.toggleSit(tamable);
                        pet.sitting = tamable.isOrderedToSit();
                    }
                });
    }

    private boolean isNearby(Entity entity) {
        return PetCare.isNear(viewer, entity);
    }

    private MutableComponent nearbyHint(Entity entity) {
        return isNearby(entity) ? TextUtil.empty() : TextUtil.hex(PetText.t(viewer, "far_call_first_short"), PetText.DEAD);
    }

    private boolean checkNearby(PetRecord pet, Entity entity) {
        if (isNearby(entity)) {
            return true;
        }
        viewer.sendSystemMessage(PetText.fail(PetText.c(viewer, "far_call_first", PetText.name(pet))), true);
        return false;
    }

    // ---------------------------------------------------------------- rename

    private void openRename(PetRecord pet) {
        close();
        RENAMING.add(viewer.getUUID());
        String initial = pet.nameFormat != null ? pet.nameFormat : pet.customName() != null ? pet.customName() : "";
        Input input = new Input(NAME_INPUT, new TextInput(250, TextUtil.body(PetText.t(viewer, "button.name")), true, initial,
                PetBrain.MAX_TYPED_LENGTH, Optional.empty()));
        CommonDialogData data = new CommonDialogData(TextUtil.empty().append(PetText.name(pet)), Optional.empty(), true,
                false, DialogAction.CLOSE,
                List.of(new PlainMessage(TextUtil.muted(PetText.t(viewer, "colors_work") + " &6Gold, <#ff55aa>pink, <gradient:gold:red>fire"), 250)),
                List.of(input));
        ParsedTemplate template = ParsedTemplate.CODEC.parse(JsonOps.INSTANCE,
                new JsonPrimitive("pets name " + pet.id + " $(" + NAME_INPUT + ")")).getOrThrow();
        ActionButton done = new ActionButton(new CommonButtonData(Component.translatable("gui.done"), 100),
                Optional.of(new CommandTemplate(template)));
        ActionButton cancel = new ActionButton(new CommonButtonData(Component.translatable("gui.cancel"), 100),
                Optional.of(new StaticAction(new ClickEvent.RunCommand("/pets menu " + pet.id))));
        viewer.openDialog(Holder.direct(new ConfirmationDialog(data, done, cancel)));
    }
}

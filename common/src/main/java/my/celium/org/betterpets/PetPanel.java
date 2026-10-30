package my.celium.org.betterpets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import my.celium.org.betterpets.net.PetActionPacket;
import my.celium.org.betterpets.net.PetsPacket;
import my.celium.org.network.MycelNetwork;
import my.celium.org.network.PacketDefinition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.item.ItemStack;

/**
 * The server side of the pet screen: what to show, and the buttons. Players
 * with Better Pets installed get the screen, everyone else the chest version
 * ({@link PetMenu}). On Forge {@link MycelNetwork#canSend} only tells whether
 * the client has Mycel, so a client must also answer the first packet in time.
 */
public final class PetPanel {
    public static PacketDefinition<PetsPacket> pets;
    public static PacketDefinition<PetActionPacket> action;

    private static final int ANSWER_TICKS = 40;
    private static final int MAX_FOODS = 6;

    /** Which pet each player has on screen. */
    private static final Map<UUID, UUID> OPEN = new HashMap<>();
    /** Players whose client answered: they have the mod. */
    private static final Set<UUID> CONFIRMED = new HashSet<>();
    /** Players who have not answered yet, with the tick they run out of time. */
    private static final Map<UUID, Long> AWAITING = new HashMap<>();
    private static long ticks;

    private PetPanel() {
    }

    /** Shows a pet in whichever menu this player's client can show. */
    static void open(ServerPlayer player, PetRecord pet) {
        if (!MycelNetwork.canSend(player, pets)) {
            PetMenu.open(player, pet);
            return;
        }
        OPEN.put(player.getUUID(), pet.id);
        if (!CONFIRMED.contains(player.getUUID())) {
            AWAITING.put(player.getUUID(), ticks + ANSWER_TICKS);
        }
        send(player, pet, true, "", false);
    }

    /** {@code /pets menu} without a pet: the screen starts on the first living pet, the chest on its list. */
    static void openList(ServerPlayer player) {
        List<PetRecord> all = PetTracker.STORE.pets(player.getUUID());
        if (!MycelNetwork.canSend(player, pets)) {
            PetListMenu.open(player);
            return;
        }
        open(player, all.stream().filter(pet -> !pet.dead).findFirst().orElse(all.get(0)));
    }

    static void tick(MinecraftServer server) {
        ticks++;
        if (ticks % PetTracker.SCAN_INTERVAL != 0) {
            return;
        }
        for (UUID id : new ArrayList<>(OPEN.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            PetRecord pet = player == null ? null : PetTracker.record(id, OPEN.get(id));
            if (pet == null) {
                OPEN.remove(id);
                continue;
            }
            Long deadline = AWAITING.get(id);
            if (deadline != null && ticks >= deadline) {
                AWAITING.remove(id);
                OPEN.remove(id);
                PetMenu.open(player, pet);
                continue;
            }
            send(player, pet, false, "", false);
        }
    }

    static void forgetPlayer(UUID player) {
        OPEN.remove(player);
        CONFIRMED.remove(player);
        AWAITING.remove(player);
    }

    static void clear() {
        OPEN.clear();
        CONFIRMED.clear();
        AWAITING.clear();
        ticks = 0;
    }

    // ---------------------------------------------------------------- buttons

    /** A button from the screen, on the server thread. Nothing in the packet is taken on trust. */
    public static void handle(PetActionPacket packet, ServerPlayer player) {
        UUID id = player.getUUID();
        if (packet.action() == PetActionPacket.Action.OPENED) {
            CONFIRMED.add(id);
            AWAITING.remove(id);
            return;
        }
        if (packet.action() == PetActionPacket.Action.CLOSED) {
            OPEN.remove(id);
            return;
        }
        if (!OPEN.containsKey(id) || !BetterPetsMod.isModEnabled()) {
            return;
        }
        PetRecord pet = PetTracker.record(id, packet.pet());
        if (pet == null) {
            return;
        }
        Result result = run(packet, player, pet);
        if (!OPEN.containsKey(id)) {
            return;
        }
        PetRecord shown = PetTracker.record(id, OPEN.get(id));
        if (shown == null) {
            List<PetRecord> left = PetTracker.STORE.pets(id);
            if (left.isEmpty()) {
                OPEN.remove(id);
                return;
            }
            shown = left.get(0);
            OPEN.put(id, shown.id);
        }
        send(player, shown, false, result.text(), result.ok());
    }

    private record Result(String text, boolean ok) {
        static final Result NONE = new Result("", true);

        static Result ok(String text) {
            return new Result(text, true);
        }

        static Result fail(String text) {
            return new Result(text, false);
        }
    }

    private static Result run(PetActionPacket packet, ServerPlayer player, PetRecord pet) {
        UUID id = player.getUUID();
        Entity entity = PetTracker.loaded(pet.id);
        String name = PetText.name(pet).getString();
        switch (packet.action()) {
            case SELECT -> {
                OPEN.put(id, pet.id);
                return Result.NONE;
            }
            case FEED -> {
                if (!PetCare.isNear(player, entity)) {
                    return Result.fail(name + " is too far away. Call it first.");
                }
                if (!PetCare.canFeedFrom(player, entity, packet.slot())) {
                    return Result.fail(name + " doesn't eat that.");
                }
                if (!PetCare.isHungry(entity) || !PetCare.feed(player, entity, packet.slot())) {
                    return Result.fail(name + " is not hungry.");
                }
                return Result.ok(name + " enjoyed that!");
            }
            case SIT -> {
                if (!PetCare.isNear(player, entity)) {
                    return Result.fail(name + " is too far away. Call it first.");
                }
                if (PetCare.canSit(entity) && entity instanceof TamableAnimal tamable) {
                    PetCare.toggleSit(tamable);
                    pet.sitting = tamable.isOrderedToSit();
                }
                return Result.NONE;
            }
            case INVENTORY -> {
                if (!PetCare.isNear(player, entity)) {
                    return Result.fail(name + " is too far away. Call it first.");
                }
                OPEN.remove(id);
                PetCare.openInventory(player, entity);
                return Result.NONE;
            }
            case FIND -> {
                OPEN.remove(id);
                PetsCommand.find(player, pet);
                return Result.NONE;
            }
            case CALL -> {
                OPEN.remove(id);
                if (BetterPetsMod.allowCall.get()) {
                    PetsCommand.call(player, pet);
                }
                return Result.NONE;
            }
            case RENAME -> {
                return rename(pet, packet.text());
            }
            case GIVE -> {
                return give(player, pet, packet.text());
            }
            case FORGET -> {
                if (PetsCommand.forget(player, pet) > 0) {
                    return Result.ok(name + " was removed from your list.");
                }
                return Result.fail(name + " is right here, so it stays on the list.");
            }
            default -> {
                return Result.NONE;
            }
        }
    }

    private static Result rename(PetRecord pet, String raw) {
        if (!BetterPetsMod.allowRename.get() || pet.dead) {
            return Result.NONE;
        }
        String typed = PetsCommand.validName(raw);
        if (typed == null) {
            return Result.fail("A name needs 1 to " + PetBrain.MAX_NAME_LENGTH + " letters (colors don't count).");
        }
        Component error = PetTracker.rename(pet, typed);
        return error != null ? Result.fail(error.getString()) : Result.ok("Now called " + PetNameFormat.plain(typed) + ".");
    }

    private static Result give(ServerPlayer player, PetRecord pet, String target) {
        if (!BetterPetsMod.allowGive.get() || pet.dead) {
            return Result.NONE;
        }
        ServerPlayer to = null;
        for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
            if (other != player && other.getUUID().toString().equals(target)) {
                to = other;
            }
        }
        if (to == null) {
            return Result.fail("That player has left.");
        }
        String name = PetText.name(pet).getString();
        return PetsCommand.give(player, pet, to) > 0
                ? Result.ok(name + " now belongs to " + to.getName().getString() + ".")
                : Result.fail(name + " is too far away. Go and get it first.");
    }

    // ---------------------------------------------------------------- what to show

    private static void send(ServerPlayer player, PetRecord selected, boolean open, String notice, boolean ok) {
        List<PetsPacket.Pet> rows = new ArrayList<>();
        for (PetRecord pet : PetTracker.STORE.pets(player.getUUID())) {
            rows.add(row(player, pet));
        }
        List<PetsPacket.Person> people = new ArrayList<>();
        for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
            if (other != player) {
                people.add(new PetsPacket.Person(other.getUUID(), other.getName().getString()));
            }
        }
        MycelNetwork.sendToPlayer(player, pets, new PetsPacket(open, selected.id, rows, details(player, selected),
                people, notice, ok, BetterPetsMod.allowCall.get(), BetterPetsMod.allowRename.get(),
                BetterPetsMod.allowGive.get()));
    }

    private static PetsPacket.Pet row(ServerPlayer player, PetRecord pet) {
        Entity entity = PetTracker.loaded(pet.id);
        boolean alive = entity instanceof LivingEntity;
        float health = alive ? ((LivingEntity) entity).getHealth() : pet.health;
        float max = alive ? ((LivingEntity) entity).getMaxHealth() : pet.maxHealth;
        String name = pet.customName() == null ? "" : pet.customName();
        String format = pet.nameFormat != null && PetNameFormat.plain(pet.nameFormat).equals(name) ? pet.nameFormat : "";
        return new PetsPacket.Pet(pet.id, String.valueOf(pet.type), name, format, health, max, pet.dead, entity != null,
                PetsCommand.placeText(player, pet));
    }

    private static PetsPacket.Details details(ServerPlayer player, PetRecord pet) {
        long now = System.currentTimeMillis();
        Entity entity = PetTracker.loaded(pet.id);
        boolean near = PetCare.isNear(player, entity);
        String speed = "";
        String jump = "";
        if (entity instanceof AbstractHorse horse) {
            speed = PetBrain.horseSpeed(horse.getAttributeValue(Attributes.MOVEMENT_SPEED));
            jump = PetBrain.horseJump(horse.getAttributeValue(Attributes.JUMP_STRENGTH));
        }
        boolean sitting = entity instanceof TamableAnimal tamable && tamable.isOrderedToSit();
        return new PetsPacket.Details(entity == null ? -1 : entity.getId(), near,
                entity != null && PetCare.isHungry(entity), entity != null && PetCare.canSit(entity), sitting,
                entity != null && PetCare.hasInventory(entity), PetMenu.status(pet, entity),
                PetText.blockCoords(pet.x, pet.y, pet.z) + " in the " + PetBrain.dimensionName(pet.dimension),
                pet.firstSeen > 0 ? PetBrain.ago(now - pet.firstSeen) : "",
                pet.dead ? (pet.deathMessage == null ? "Died" : pet.deathMessage) + ", " + PetBrain.ago(now - pet.lastSeen) : "",
                speed, jump, entity == null ? List.of() : foods(player, entity));
    }

    private static List<PetsPacket.Food> foods(ServerPlayer player, Entity entity) {
        Map<String, PetsPacket.Food> foods = new LinkedHashMap<>();
        for (int slot : PetCare.foodSlots(player, entity)) {
            ItemStack stack = player.getInventory().getItem(slot);
            String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            PetsPacket.Food known = foods.get(item);
            if (known != null) {
                foods.put(item, new PetsPacket.Food(known.slot(), item, known.count() + stack.getCount()));
            } else if (foods.size() < MAX_FOODS) {
                foods.put(item, new PetsPacket.Food(slot, item, stack.getCount()));
            }
        }
        return new ArrayList<>(foods.values());
    }
}

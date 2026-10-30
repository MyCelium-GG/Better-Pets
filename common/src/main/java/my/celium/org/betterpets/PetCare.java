package my.celium.org.betterpets;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.nautilus.AbstractNautilus;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Looking after a loaded pet from the menu: feeding, sitting, its inventory. */
final class PetCare {
    private static final float PARROT_HEAL = 2f;
    private static final double NEARBY = 8.0;

    private PetCare() {
    }

    /** The first slot of the player's backpack or hotbar holding food this pet eats, or -1. */
    static int foodSlot(ServerPlayer player, Entity pet) {
        List<Integer> slots = foodSlots(player, pet);
        return slots.isEmpty() ? -1 : slots.get(0);
    }

    /** Every slot of the player's backpack and hotbar holding food this pet eats. */
    static List<Integer> foodSlots(ServerPlayer player, Entity pet) {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (canFeedFrom(player, pet, slot)) {
                slots.add(slot);
            }
        }
        return slots;
    }

    /** Whether {@code slot} is a real backpack or hotbar slot with food in it for this pet. */
    static boolean canFeedFrom(ServerPlayer player, Entity pet, int slot) {
        if (slot < 0 || slot >= Inventory.INVENTORY_SIZE) {
            return false;
        }
        ItemStack stack = player.getInventory().getItem(slot);
        return !stack.isEmpty() && eats(pet, stack);
    }

    /** Feeding, seating and the inventory work this close; the vanilla inventory screen closes further away. */
    static boolean isNear(ServerPlayer player, Entity pet) {
        return pet != null && !pet.isRemoved() && pet.level() == player.level() && pet.distanceTo(player) <= NEARBY;
    }

    /** Vanilla parrots eat nothing once tamed; here they take their taming seeds, and never a cookie. */
    private static boolean eats(Entity pet, ItemStack stack) {
        if (pet instanceof Parrot) {
            return stack.is(ItemTags.PARROT_FOOD);
        }
        return pet instanceof Animal animal && animal.isFood(stack);
    }

    /** Items the pet does something with on a right-click, so a sneaking click must still reach it. */
    static boolean reactsTo(Entity pet, ItemStack held) {
        if (held.isEmpty()) {
            return false;
        }
        return eats(pet, held) || held.is(Items.LEAD) || held.is(Items.NAME_TAG) || held.is(Items.SHEARS)
                || held.has(DataComponents.DYE)
                || (pet instanceof Mob mob && (mob.isEquippableInSlot(held, EquipmentSlot.BODY)
                        || mob.isEquippableInSlot(held, EquipmentSlot.SADDLE)));
    }

    /** Whether the pet can eat at all: wounded, growing up, or ready to breed. */
    static boolean isHungry(Entity pet) {
        if (pet instanceof LivingEntity living && living.getHealth() < living.getMaxHealth()) {
            return true;
        }
        return !(pet instanceof Parrot) && pet instanceof Animal animal
                && (animal.isBaby() || (animal.getAge() == 0 && animal.canFallInLove()));
    }

    /**
     * Gives the pet one item from {@code slot}, with the same effect as
     * right-clicking it with that food in hand: the pet's own code decides what
     * the food does, so modded pets and foods work too. Check {@link #isHungry}
     * first; without something to eat, the same right-click would mount a pet.
     * Returns whether it ate.
     */
    static boolean feed(ServerPlayer player, Entity pet, int slot) {
        Inventory inventory = player.getInventory();
        boolean ate;
        if (pet instanceof Parrot parrot) {
            inventory.getItem(slot).consume(1, player);
            parrot.heal(PARROT_HEAL);
            parrot.playSound(SoundEvents.PARROT_EAT);
            ate = true;
        } else if (pet instanceof AbstractHorse horse) {
            ate = horse.fedFood(player, inventory.getItem(slot)).consumesAction();
        } else if (pet instanceof Mob mob) {
            ate = rightClickWith(player, mob, slot);
        } else {
            return false;
        }
        if (ate && pet.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HEART, pet.getX(), pet.getY() + pet.getBbHeight() + 0.3, pet.getZ(),
                    3, 0.3, 0.2, 0.3, 0);
        }
        return ate;
    }

    /** Puts the food in the player's hand for one right-click, standing, and puts everything back. */
    private static boolean rightClickWith(ServerPlayer player, Mob mob, int slot) {
        Inventory inventory = player.getInventory();
        int hand = inventory.getSelectedSlot();
        ItemStack held = inventory.getItem(hand);
        boolean sneaking = player.isShiftKeyDown();
        boolean sitting = mob instanceof TamableAnimal tamable && tamable.isOrderedToSit();
        inventory.setItem(hand, inventory.getItem(slot));
        inventory.setItem(slot, held);
        player.setShiftKeyDown(false);
        try {
            return mob.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO).consumesAction();
        } finally {
            inventory.setItem(slot, inventory.getItem(hand));
            inventory.setItem(hand, held);
            player.setShiftKeyDown(sneaking);
            if (mob instanceof TamableAnimal tamable && tamable.isOrderedToSit() != sitting) {
                tamable.setOrderedToSit(sitting);
            }
        }
    }

    /** Pets that can be told to sit. A nautilus is tamable too, but has no sitting. */
    static boolean canSit(Entity pet) {
        return pet instanceof TamableAnimal && !(pet instanceof AbstractNautilus);
    }

    static void toggleSit(TamableAnimal pet) {
        pet.setOrderedToSit(!pet.isOrderedToSit());
        pet.getNavigation().stop();
        pet.setTarget(null);
    }

    /** Horses, llamas, camels, a nautilus: the shift-click that the menu took over opened this. */
    static boolean hasInventory(Entity pet) {
        return pet instanceof HasCustomInventoryScreen && pet instanceof LivingEntity living && !living.isBaby();
    }

    static void openInventory(ServerPlayer player, Entity pet) {
        if (pet instanceof HasCustomInventoryScreen screen) {
            screen.openCustomInventoryScreen(player);
        }
    }
}

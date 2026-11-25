package my.celium.org.betterpets.winter;

import my.celium.org.betterpets.BetterPetsMod;
import my.celium.org.betterpets.PetStats;
import my.celium.org.betterpets.PetTracker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Who wears a Pet Santa Hat. Like the scarf, it is one entity tag on the pet
 * ({@code betterpets.hat}), so it is saved with the pet, rides along on a shoulder and stays on
 * when the pet changes owner. Grown dogs and cats and every parrot can wear one.
 */
public final class SantaHats {
    static final String TAG = "betterpets.hat";

    private SantaHats() {
    }

    /** Whether this animal can wear a hat at all. */
    public static boolean fits(Entity entity) {
        if (entity instanceof Parrot) {
            return true;
        }
        return (entity instanceof Wolf || entity instanceof Cat) && !((AgeableMob) entity).isBaby();
    }

    public static boolean wears(Entity entity) {
        return entity.entityTags().contains(TAG);
    }

    public static boolean isHat(ItemStack stack) {
        return BetterPetsMod.petSantaHat != null && stack.is(BetterPetsMod.petSantaHat.get());
    }

    /**
     * Right-click hook, both sides: an owner holding a hat puts it on their own dog, cat or parrot.
     * {@code true} means the click was used (vanilla must not also make the pet sit).
     */
    public static boolean onInteract(Player player, Entity target, boolean mainHand) {
        if (!BetterPetsMod.isModEnabled() || !BetterPetsMod.santaHats.get() || !fits(target)
                || !isHat(player.getMainHandItem()) || !player.getUUID().equals(PetTracker.ownerOf(target))) {
            return false;
        }
        if (mainHand && !player.level().isClientSide() && !wears(target)) {
            target.addTag(TAG);
            if (!player.getAbilities().instabuild) {
                player.getMainHandItem().shrink(1);
            }
            target.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WOOL_PLACE,
                    SoundSource.NEUTRAL, 0.8f, 1.4f);
            PetStats.count("hat_on");
            Winter.lookChanged(target);
        }
        return true;
    }

    /** Takes the hat off and hands it to {@code player}. {@code false} when it wore none. */
    public static boolean takeOff(Player player, Entity pet) {
        if (!wears(pet)) {
            return false;
        }
        pet.removeTag(TAG);
        ItemStack hat = new ItemStack(BetterPetsMod.petSantaHat.get());
        if (!player.getInventory().add(hat) && player.level() instanceof ServerLevel level) {
            player.spawnAtLocation(level, hat);
        }
        pet.level().playSound(null, pet.getX(), pet.getY(), pet.getZ(), SoundEvents.WOOL_BREAK, SoundSource.NEUTRAL,
                0.8f, 1.4f);
        Winter.lookChanged(pet);
        return true;
    }

    /** A pet died: its hat falls where it stood. */
    public static void dropOnDeath(Entity pet) {
        if (wears(pet) && pet.level() instanceof ServerLevel level) {
            pet.removeTag(TAG);
            pet.spawnAtLocation(level, new ItemStack(BetterPetsMod.petSantaHat.get()));
        }
    }
}

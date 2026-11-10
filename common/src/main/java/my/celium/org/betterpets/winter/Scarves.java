package my.celium.org.betterpets.winter;

import java.util.Locale;

import my.celium.org.betterpets.BetterPetsMod;
import my.celium.org.betterpets.PetStats;
import my.celium.org.betterpets.PetTracker;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * Who wears which scarf. The colour lives in one entity tag on the pet itself
 * ({@code betterpets.scarf.b02e26}), so it is saved with the entity and stays on when the pet
 * changes owner. Grown dogs and cats can wear one.
 */
public final class Scarves {
    private static final String TAG = "betterpets.scarf.";

    private Scarves() {
    }

    /** Whether this animal can wear a scarf at all. */
    public static boolean fits(Entity entity) {
        return (entity instanceof Wolf || entity instanceof Cat) && !((AgeableMob) entity).isBaby();
    }

    /** The scarf colour as 0xRRGGBB, or -1 without one. */
    public static int color(Entity entity) {
        for (String tag : entity.entityTags()) {
            if (tag.startsWith(TAG)) {
                try {
                    return Integer.parseInt(tag.substring(TAG.length()), 16) & 0xFFFFFF;
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }

    /**
     * Right-click hook, both sides: an owner holding a scarf dresses their own dog or cat.
     * {@code true} means the click was used (vanilla must not also make the pet sit).
     */
    public static boolean onInteract(Player player, Entity target, boolean mainHand) {
        if (!BetterPetsMod.isModEnabled() || !BetterPetsMod.scarves.get() || !fits(target)
                || !isScarf(player.getMainHandItem()) || !player.getUUID().equals(PetTracker.ownerOf(target))) {
            return false;
        }
        if (mainHand && !player.level().isClientSide()) {
            putOn(player, target, player.getMainHandItem());
            PetStats.count("scarf_on");
        }
        return true;
    }

    /** Whether {@code stack} is a Pet Scarf. */
    public static boolean isScarf(ItemStack stack) {
        return BetterPetsMod.petScarf != null && stack.is(BetterPetsMod.petScarf.get());
    }

    /**
     * The owner right-clicked their pet holding a scarf: put it on, giving back the one it wore.
     * Runs on the server.
     */
    public static void putOn(Player player, Entity pet, ItemStack held) {
        int rgb = DyedItemColor.getOrDefault(held, PetScarfItem.DEFAULT_COLOR) & 0xFFFFFF;
        int old = color(pet);
        if (old == rgb) {
            return;
        }
        setTag(pet, rgb);
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        if (old >= 0) {
            give(player, item(old));
        }
        pet.level().playSound(null, pet.getX(), pet.getY(), pet.getZ(), SoundEvents.WOOL_PLACE, SoundSource.NEUTRAL,
                0.8f, 1.2f);
        Winter.lookChanged(pet);
    }

    /** Takes the scarf off and hands it to {@code player}. {@code false} when it wore none. */
    public static boolean takeOff(Player player, Entity pet) {
        int old = color(pet);
        if (old < 0) {
            return false;
        }
        setTag(pet, -1);
        give(player, item(old));
        pet.level().playSound(null, pet.getX(), pet.getY(), pet.getZ(), SoundEvents.WOOL_BREAK, SoundSource.NEUTRAL,
                0.8f, 1.2f);
        Winter.lookChanged(pet);
        return true;
    }

    /** A pet died: its scarf falls where it stood. */
    public static void dropOnDeath(Entity pet) {
        int old = color(pet);
        if (old >= 0 && pet.level() instanceof ServerLevel level) {
            setTag(pet, -1);
            pet.spawnAtLocation(level, item(old));
        }
    }

    /** A scarf item of the given colour. */
    public static ItemStack item(int rgb) {
        ItemStack stack = new ItemStack(BetterPetsMod.petScarf.get());
        if (rgb != PetScarfItem.DEFAULT_COLOR) {
            stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb & 0xFFFFFF));
        }
        return stack;
    }

    private static void setTag(Entity pet, int rgb) {
        for (String tag : java.util.List.copyOf(pet.entityTags())) {
            if (tag.startsWith(TAG)) {
                pet.removeTag(tag);
            }
        }
        if (rgb >= 0) {
            pet.addTag(TAG + String.format(Locale.ROOT, "%06x", rgb & 0xFFFFFF));
        }
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack) && player.level() instanceof ServerLevel level) {
            player.spawnAtLocation(level, stack);
        }
    }
}

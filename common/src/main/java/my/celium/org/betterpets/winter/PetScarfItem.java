package my.celium.org.betterpets.winter;

import net.minecraft.world.item.Item;

/**
 * A knitted scarf for a dog or cat. Dye it like leather armor, then right-click your own pet
 * with it; the pet wears the same colour. What happens on the click is in {@link Scarves}.
 */
public class PetScarfItem extends Item {
    /** Red, the colour of an undyed scarf. */
    public static final int DEFAULT_COLOR = 0xB02E26;

    public PetScarfItem(Properties properties) {
        super(properties);
    }
}

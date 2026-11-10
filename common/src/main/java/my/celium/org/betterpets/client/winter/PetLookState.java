package my.celium.org.betterpets.client.winter;

/**
 * Added to every living entity's render state by a mixin, so the winter layers and the sleeping
 * pose can read the pet's look without the entity.
 */
public interface PetLookState {
    /** Scarf colour 0xRRGGBB, or -1. */
    int betterpets$scarf();

    /** 0 (clean) to 3 (white). */
    int betterpets$snow();

    boolean betterpets$asleep();

    void betterpets$setLook(int scarf, int snow, boolean asleep);
}

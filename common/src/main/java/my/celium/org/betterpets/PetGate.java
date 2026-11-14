package my.celium.org.betterpets;

/**
 * Whether Better Pets runs at all. It stays off when Mycel is missing, and every mixin asks here
 * first, so none of them loads a class that needs Mycel. Must never touch a Mycel class.
 */
public final class PetGate {
    private static volatile boolean on;

    private PetGate() {
    }

    /** Called by the loader entrypoint once it knows Mycel is installed. */
    public static void open() {
        on = true;
    }

    public static boolean on() {
        return on;
    }
}

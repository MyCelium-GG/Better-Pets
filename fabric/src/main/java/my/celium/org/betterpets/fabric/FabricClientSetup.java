package my.celium.org.betterpets.fabric;

import my.celium.org.fabric.FabricNetworking;

/** Only loaded when Mycel is installed. */
final class FabricClientSetup {
    private FabricClientSetup() {
    }

    static void start() {
        FabricNetworking.flushClient();
    }
}

package my.celium.org.betterpets.client;

import my.celium.org.betterpets.PetPanel;
import my.celium.org.betterpets.net.PetActionPacket;
import my.celium.org.betterpets.net.PetsPacket;
import my.celium.org.client.MycelScreens;
import my.celium.org.network.MycelNetwork;

/** Receives the pet screen's data, on the client. Never loaded on a dedicated server. */
public final class ClientPets {
    private ClientPets() {
    }

    public static void receive(PetsPacket packet) {
        if (MycelScreens.current() instanceof PetScreen screen) {
            screen.update(packet);
            if (packet.showSettings()) {
                screen.showSettings();
            }
        } else if (packet.open()) {
            PetScreen screen = new PetScreen(packet);
            if (packet.showSettings()) {
                screen.showSettings();
            }
            MycelScreens.open(screen);
        }
        if (packet.open()) {
            send(new PetActionPacket(PetActionPacket.Action.OPENED, packet.selected()));
        }
    }

    static void send(PetActionPacket packet) {
        MycelNetwork.sendToServer(PetPanel.action, packet);
    }
}

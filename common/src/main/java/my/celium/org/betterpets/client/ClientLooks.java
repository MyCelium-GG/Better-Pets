package my.celium.org.betterpets.client;

import java.util.HashMap;
import java.util.Map;

import my.celium.org.betterpets.net.PetLooksPacket;

/**
 * How the pets around this player look (scarf, snow, asleep), as last sent by the server.
 * Read by the renderer for every pet each frame, so it is a plain map swapped in whole.
 */
public final class ClientLooks {
    private static volatile Map<Integer, PetLooksPacket.Look> looks = Map.of();

    private ClientLooks() {
    }

    public static void receive(PetLooksPacket packet) {
        Map<Integer, PetLooksPacket.Look> next = new HashMap<>();
        for (PetLooksPacket.Look look : packet.looks()) {
            next.put(look.entityId(), look);
        }
        looks = next;
    }

    /** The look of entity {@code id}, or {@code null} when it looks like vanilla. */
    public static PetLooksPacket.Look get(int id) {
        return looks.get(id);
    }
}

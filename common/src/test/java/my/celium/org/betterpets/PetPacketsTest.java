package my.celium.org.betterpets;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;
import my.celium.org.betterpets.net.PetActionPacket;
import my.celium.org.betterpets.net.PetsPacket;
import net.minecraft.network.FriendlyByteBuf;

class PetPacketsTest {
    @Test
    void screenStateSurvivesTheWire() {
        UUID id = UUID.randomUUID();
        PetsPacket.Pet pet = new PetsPacket.Pet(id, "minecraft:wolf", "Rex", "&bRex", 14.5f, 40f, false, true, "12 m NW");
        PetsPacket.Details details = new PetsPacket.Details(42, true, true, true, false, false, "Sitting",
                "1 64 -3 in the overworld", "2 d ago", "", "9.5 blocks/s", "2.9 blocks",
                List.of(new PetsPacket.Food(9, "minecraft:cooked_beef", 16)));
        PetsPacket packet = new PetsPacket(true, id, List.of(pet), details,
                List.of(new PetsPacket.Person(UUID.randomUUID(), "Steve")), "Yum", true, true, false, true);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        PetsPacket.CODEC.encode(buf, packet);
        assertEquals(packet, PetsPacket.CODEC.decode(buf));
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void actionSurvivesTheWire() {
        PetActionPacket packet = new PetActionPacket(PetActionPacket.Action.RENAME, UUID.randomUUID(), 3, "&bRex");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        PetActionPacket.CODEC.encode(buf, packet);
        assertEquals(packet, PetActionPacket.CODEC.decode(buf));
    }
}

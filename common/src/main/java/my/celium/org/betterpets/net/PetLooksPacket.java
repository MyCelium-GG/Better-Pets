package my.celium.org.betterpets.net;

import java.util.ArrayList;
import java.util.List;

import my.celium.org.betterpets.BetterPetsMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server to client, twice a second: how the pets near this player look. Pets missing from the
 * list look like vanilla. {@code scarf} is 0xRRGGBB or -1, {@code snow} 0 (clean) to 3 (white).
 */
public record PetLooksPacket(List<Look> looks) implements CustomPacketPayload {
    public static final Type<PetLooksPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(BetterPetsMod.ID, "looks"));
    public static final StreamCodec<FriendlyByteBuf, PetLooksPacket> CODEC = StreamCodec.of(PetLooksPacket::write,
            PetLooksPacket::read);
    private static final int MAX = 512;

    public record Look(int entityId, int scarf, int snow, boolean asleep) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, PetLooksPacket packet) {
        int size = Math.min(MAX, packet.looks.size());
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            Look look = packet.looks.get(i);
            buf.writeVarInt(look.entityId);
            buf.writeInt(look.scarf);
            buf.writeByte(look.snow);
            buf.writeBoolean(look.asleep);
        }
    }

    private static PetLooksPacket read(FriendlyByteBuf buf) {
        int size = Math.min(MAX, buf.readVarInt());
        List<Look> looks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            looks.add(new Look(buf.readVarInt(), buf.readInt(), buf.readByte(), buf.readBoolean()));
        }
        return new PetLooksPacket(looks);
    }
}

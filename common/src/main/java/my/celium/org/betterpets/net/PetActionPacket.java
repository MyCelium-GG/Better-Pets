package my.celium.org.betterpets.net;

import java.util.UUID;

import my.celium.org.betterpets.BetterPetsMod;
import my.celium.org.betterpets.PetBrain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client to server: a button on the pet screen. {@code slot} is the food's
 * inventory slot for {@link Action#FEED}; {@code text} the new name for
 * {@link Action#RENAME}, the receiving player's UUID for {@link Action#GIVE}.
 * The server checks all of it again.
 */
public record PetActionPacket(Action action, UUID pet, int slot, String text) implements CustomPacketPayload {
    public static final Type<PetActionPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(BetterPetsMod.ID, "pet_action"));
    public static final StreamCodec<FriendlyByteBuf, PetActionPacket> CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeEnum(packet.action);
                buf.writeUUID(packet.pet);
                buf.writeVarInt(packet.slot);
                buf.writeUtf(packet.text, PetBrain.MAX_TYPED_LENGTH);
            },
            buf -> new PetActionPacket(buf.readEnum(Action.class), buf.readUUID(), buf.readVarInt(),
                    buf.readUtf(PetBrain.MAX_TYPED_LENGTH)));

    /** What the button does. */
    public enum Action {
        /** The screen arrived: this client has the mod. */
        OPENED,
        CLOSED,
        SELECT,
        FEED,
        SIT,
        INVENTORY,
        FIND,
        CALL,
        RENAME,
        GIVE,
        FORGET
    }

    public PetActionPacket(Action action, UUID pet) {
        this(action, pet, 0, "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

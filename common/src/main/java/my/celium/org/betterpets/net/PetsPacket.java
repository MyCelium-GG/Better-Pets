package my.celium.org.betterpets.net;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

import my.celium.org.betterpets.BetterPetsMod;
import my.celium.org.betterpets.PetNameFormat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server to client: everything the pet screen shows. {@code open} opens the
 * screen; without it the packet only refreshes one that is already open, once
 * a second and after every action.
 */
public record PetsPacket(boolean open, UUID selected, List<Pet> pets, Details details, List<Person> players,
        String notice, boolean noticeOk, boolean allowCall, boolean allowRename, boolean allowGive)
        implements CustomPacketPayload {
    public static final Type<PetsPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(BetterPetsMod.ID, "pets"));
    public static final StreamCodec<FriendlyByteBuf, PetsPacket> CODEC = StreamCodec.of(PetsPacket::write, PetsPacket::read);

    /** One row of the pet list. {@code name} and {@code format} are empty for an unnamed pet or a plain name. */
    public record Pet(UUID id, String type, String name, String format, float health, float maxHealth, boolean dead,
            boolean loaded, String place) {
        /** The name as the player knows it: colored when set that way, else the plain name, else the species. */
        public Component displayName() {
            if (!format.isEmpty()) {
                return PetNameFormat.parse(format);
            }
            return name.isEmpty() ? species() : Component.literal(name);
        }

        public Component species() {
            int colon = type.indexOf(':');
            return colon < 0 ? Component.literal(type) : Component.translatable("entity." + type.replace(':', '.'));
        }
    }

    /** One kind of food the player carries for the selected pet, with the slot to take it from. */
    public record Food(int slot, String item, int count) {
    }

    /** Another online player to give a pet to. */
    public record Person(UUID id, String name) {
    }

    /** The selected pet, in full. {@code entityId} is -1 when it is not loaded. */
    public record Details(int entityId, boolean near, boolean hungry, boolean canSit, boolean sitting,
            boolean hasInventory, String status, String where, String since, String death, String speed, String jump,
            List<Food> foods) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, PetsPacket p) {
        buf.writeBoolean(p.open);
        buf.writeUUID(p.selected);
        list(buf, p.pets, (b, pet) -> {
            b.writeUUID(pet.id);
            b.writeUtf(pet.type);
            b.writeUtf(pet.name);
            b.writeUtf(pet.format);
            b.writeFloat(pet.health);
            b.writeFloat(pet.maxHealth);
            b.writeBoolean(pet.dead);
            b.writeBoolean(pet.loaded);
            b.writeUtf(pet.place);
        });
        Details d = p.details;
        buf.writeVarInt(d.entityId);
        buf.writeBoolean(d.near);
        buf.writeBoolean(d.hungry);
        buf.writeBoolean(d.canSit);
        buf.writeBoolean(d.sitting);
        buf.writeBoolean(d.hasInventory);
        buf.writeUtf(d.status);
        buf.writeUtf(d.where);
        buf.writeUtf(d.since);
        buf.writeUtf(d.death);
        buf.writeUtf(d.speed);
        buf.writeUtf(d.jump);
        list(buf, d.foods, (b, food) -> {
            b.writeVarInt(food.slot);
            b.writeUtf(food.item);
            b.writeVarInt(food.count);
        });
        list(buf, p.players, (b, person) -> {
            b.writeUUID(person.id);
            b.writeUtf(person.name);
        });
        buf.writeUtf(p.notice);
        buf.writeBoolean(p.noticeOk);
        buf.writeBoolean(p.allowCall);
        buf.writeBoolean(p.allowRename);
        buf.writeBoolean(p.allowGive);
    }

    private static PetsPacket read(FriendlyByteBuf buf) {
        boolean open = buf.readBoolean();
        UUID selected = buf.readUUID();
        List<Pet> pets = list(buf, b -> new Pet(b.readUUID(), b.readUtf(), b.readUtf(), b.readUtf(), b.readFloat(),
                b.readFloat(), b.readBoolean(), b.readBoolean(), b.readUtf()));
        Details details = new Details(buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                buf.readBoolean(), buf.readBoolean(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                buf.readUtf(), buf.readUtf(), list(buf, b -> new Food(b.readVarInt(), b.readUtf(), b.readVarInt())));
        List<Person> players = list(buf, b -> new Person(b.readUUID(), b.readUtf()));
        return new PetsPacket(open, selected, pets, details, players, buf.readUtf(), buf.readBoolean(),
                buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    private static <T> void list(FriendlyByteBuf buf, List<T> values, BiConsumer<FriendlyByteBuf, T> writer) {
        buf.writeVarInt(values.size());
        for (T value : values) {
            writer.accept(buf, value);
        }
    }

    private static <T> List<T> list(FriendlyByteBuf buf, Function<FriendlyByteBuf, T> reader) {
        int size = buf.readVarInt();
        List<T> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(reader.apply(buf));
        }
        return values;
    }
}

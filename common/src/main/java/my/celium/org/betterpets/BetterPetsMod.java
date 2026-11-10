package my.celium.org.betterpets;

import my.celium.org.Mycel;
import my.celium.org.betterpets.client.ClientPets;
import my.celium.org.betterpets.net.PetActionPacket;
import my.celium.org.betterpets.net.PetsPacket;
import my.celium.org.config.ConfigBuilder;
import my.celium.org.config.ConfigValue;
import my.celium.org.config.MycelConfig;
import my.celium.org.event.MycelEvents;
import my.celium.org.event.ServerLifecycleEvent;
import my.celium.org.metadata.ModMetadata;
import my.celium.org.network.MycelNetwork;
import my.celium.org.network.PacketDefinition;
import my.celium.org.registry.DeferredEntry;
import my.celium.org.registry.MycelRegistry;
import my.celium.org.betterpets.client.ClientLooks;
import my.celium.org.betterpets.net.PetLooksPacket;
import my.celium.org.betterpets.winter.PetScarfItem;
import my.celium.org.betterpets.winter.Scarves;
import my.celium.org.betterpets.winter.Winter;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Better Pets: owning a dog, cat, parrot or horse, done right.
 *
 * <p>Runs on the server; vanilla clients get everything too, and clients with
 * the mod get a proper screen instead of the chest menu. The pieces:
 * {@link PetTracker} remembers every tamed pet and where it is (even in
 * unloaded chunks), watches for deaths, keeps leads from snapping and drives
 * the find compass; {@link PetsCommand} is the {@code /pets} command;
 * {@link PetPanel} is what a shift-right-click opens: the pet screen, or
 * {@link PetMenu} as the chest version;
 * {@link PetStore} saves the pet list per world. Each loader forwards its
 * server tick, command registration, entity clicks and incoming-damage event
 * here.
 */
public final class BetterPetsMod {
    public static final String ID = "betterpets";

    public static final ModMetadata META = ModMetadata.builder(ID, "Better Pets", "1.3.0")
            .author("MyCelium")
            .website("https://www.curseforge.com/minecraft/mc-mods/better-pets")
            .updateUrl("https://wiki.mycelium-gg.org/updates/betterpets.json")
            .build();

    public static ConfigValue<Boolean> enabled;
    public static ConfigValue<Boolean> petMenu;
    public static ConfigValue<Boolean> protectFromOwner;
    public static ConfigValue<Boolean> strongLeads;
    public static ConfigValue<Boolean> deathAlerts;
    public static ConfigValue<Boolean> allowCall;
    public static ConfigValue<Integer> callCooldownSeconds;
    public static ConfigValue<Boolean> allowRename;
    public static ConfigValue<Boolean> allowGive;
    public static ConfigValue<Integer> findSeconds;
    public static ConfigValue<Boolean> scarves;
    public static ConfigValue<Boolean> sleepOnBed;
    public static ConfigValue<Boolean> snowyCoats;
    public static DeferredEntry<Item> petScarf;
    public static MycelConfig config;

    private BetterPetsMod() {
    }

    /** Called once from each loader's entrypoint. */
    public static void init() {
        init(true);
    }

    /** {@code items = false} only for unit tests, which run without a game to register items into. */
    static void init(boolean items) {
        Mycel.initialize();
        Mycel.registerMod(META);

        if (items) {
            registerItems();
        }
        initRest();
    }

    private static void registerItems() {
        petScarf = MycelRegistry.items(META).registerWithKey("pet_scarf",
                key -> new PetScarfItem(new Item.Properties().setId(key).stacksTo(16)
                        .component(DataComponents.LORE, new ItemLore(List.of(
                                Component.translatable("item.betterpets.pet_scarf.hint")
                                        .withStyle(style -> style.withItalic(false)).withStyle(ChatFormatting.GRAY))))));
    }

    private static void initRest() {
        ConfigBuilder builder = Mycel.config(META);
        enabled = builder.booleanValue("enabled", true,
                "Master switch for Better Pets.");
        petMenu = builder.booleanValue("petMenu", true,
                "Shift-right-clicking your pet and /pets menu open its menu.");
        protectFromOwner = builder.booleanValue("protectFromOwner", true,
                "Your own hits, arrows and sweeps do not hurt your pets. Sneak to hit anyway.");
        strongLeads = builder.booleanValue("strongLeads", true,
                "A pet on its owner's lead is pulled along instead of the lead snapping.");
        deathAlerts = builder.booleanValue("deathAlerts", true,
                "Tell the owner where a pet died.");
        allowCall = builder.booleanValue("allowCall", true,
                "Allow /pets call to teleport a loaded pet to its owner.");
        callCooldownSeconds = builder.intValue("callCooldownSeconds", 30, 0, 3600,
                "Seconds between two /pets call uses per player.");
        allowRename = builder.booleanValue("allowRename", true,
                "Allow /pets name to rename a pet without a name tag.");
        allowGive = builder.booleanValue("allowGive", true,
                "Allow /pets give to hand a pet to another player.");
        findSeconds = builder.intValue("findSeconds", 30, 5, 300,
                "How long /pets find makes the pet glow and shows the compass.");
        scarves = builder.booleanValue("scarves", true,
                "Dogs and cats can wear a Pet Scarf: right-click your own pet with one.");
        sleepOnBed = builder.booleanValue("sleepOnBed", true,
                "When you go to bed, your dogs lie down on the end of it (dogs told to sit stay put).");
        snowyCoats = builder.booleanValue("snowyCoats", true,
                "Dogs and cats out in falling snow turn white, and shake it off under a roof.");
        config = builder.build();
        PetStats.start();

        MycelEvents.onPlayerLeave(event -> {
            PetMenu.fromDialog(event.player());
            PetPanel.forgetPlayer(event.player().getUUID());
            Winter.forgetPlayer(event.player().getUUID());
        });

        // Optional both ways: without the mod on the client the chest menu takes over.
        PetPanel.pets = PacketDefinition.toClient(PetsPacket.TYPE, PetsPacket.CODEC,
                (packet, context) -> ClientPets.receive(packet));
        PetPanel.action = PacketDefinition.toServer(PetActionPacket.TYPE, PetActionPacket.CODEC,
                (packet, context) -> PetPanel.handle(packet, context.sender()));
        MycelNetwork.register(META, PetPanel.pets);
        MycelNetwork.register(META, PetPanel.action);
        Winter.looks = PacketDefinition.toClient(PetLooksPacket.TYPE, PetLooksPacket.CODEC,
                (packet, context) -> ClientLooks.receive(packet));
        MycelNetwork.register(META, Winter.looks);

        MycelEvents.onServerLifecycle(event -> {
            if (event.phase() == ServerLifecycleEvent.Phase.STARTED) {
                PetTracker.onServerStarted(event.server());
            } else {
                PetTracker.onServerStopped();
            }
        });
    }

    public static boolean isModEnabled() {
        return Mycel.isEnabled(ID) && enabled.get();
    }

    /** Server-tick hook, called by each loader's native tick listener. */
    public static void tickServer(MinecraftServer server) {
        if (server == null) {
            return;
        }
        PetGui.runPending();
        if (isModEnabled()) {
            PetTracker.tick(server);
        }
    }

    /** Incoming-damage hook: {@code true} cancels the hit (owner hitting their own pet). */
    public static boolean shouldBlockDamage(LivingEntity target, DamageSource source) {
        try {
            return PetTracker.shouldBlockDamage(target, source);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Right-click-on-entity hook, both sides: {@code true} means the pet's menu took the click. */
    public static boolean onInteract(Player player, Entity target, boolean mainHand) {
        try {
            if (Scarves.onInteract(player, target, mainHand)) {
                return true;
            }
            return PetMenu.onInteract(player, target, mainHand);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Melee-attack hook, both sides: {@code true} cancels the swing (owner hitting their own pet). */
    public static boolean shouldBlockAttack(Player player, Entity target) {
        try {
            return PetTracker.shouldBlockAttack(player, target);
        } catch (RuntimeException e) {
            return false;
        }
    }
}

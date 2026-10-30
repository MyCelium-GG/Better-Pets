package my.celium.org.betterpets;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import my.celium.org.util.TextUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Server-side brain of Better Pets, driven by the loaders' server tick:
 *
 * <ul>
 *   <li>once a second, scans every loaded entity for tamed pets and updates
 *       their {@link PetRecord} (position, health, name, owner);</li>
 *   <li>every tick, watches the loaded pets for death, keeps pets on their
 *       owner's lead from snapping off, and drives the find compass;</li>
 *   <li>saves {@link PetStore} every few minutes and when the server stops.</li>
 * </ul>
 */
public final class PetTracker {
    static final int SCAN_INTERVAL = 20;
    static final int COMPASS_INTERVAL = 5;
    static final int SAVE_INTERVAL = 20 * 60 * 5;
    /** A pet further than this from the owner holding its lead is pulled back (vanilla snaps at 12). */
    static final double LEAD_PULL_DISTANCE = 8.0;
    /** The find compass stops once the player is this close. */
    static final double FOUND_DISTANCE = 4.0;

    public static final PetStore STORE = new PetStore();

    /** Loaded pets from the last scan, by pet UUID. */
    private static final Map<UUID, Entity> LIVE = new HashMap<>();
    /** Running {@code /pets find} sessions, by player UUID. */
    private static final Map<UUID, FindSession> FINDS = new HashMap<>();
    private static long ticks;

    private record FindSession(UUID pet, long endTick) {
    }

    private PetTracker() {
    }

    public static void onServerStarted(MinecraftServer server) {
        LIVE.clear();
        FINDS.clear();
        PetGui.clearPending();
        PetMenu.clear();
        PetPanel.clear();
        ticks = 0;
        STORE.load(server.getWorldPath(LevelResource.ROOT).resolve("data"));
    }

    public static void onServerStopped() {
        STORE.saveIfDirty();
        STORE.clear();
        LIVE.clear();
        FINDS.clear();
        PetGui.clearPending();
        PetMenu.clear();
        PetPanel.clear();
    }

    public static void tick(MinecraftServer server) {
        ticks++;
        PetPanel.tick(server);
        watchLive(server);
        if (ticks % SCAN_INTERVAL == 0) {
            scan(server);
            PetGui.refreshOpen(server.getPlayerList().getPlayers());
        }
        if (ticks % COMPASS_INTERVAL == 0) {
            tickFinds(server);
        }
        if (ticks % SAVE_INTERVAL == 0) {
            STORE.saveIfDirty();
        }
    }

    /** The owner UUID of a tamed pet, or {@code null} when {@code entity} is not someone's pet. */
    public static UUID ownerOf(Entity entity) {
        if (!(entity instanceof OwnableEntity ownable) || !(entity instanceof LivingEntity)) {
            return null;
        }
        EntityReference<LivingEntity> owner = ownable.getOwnerReference();
        return owner == null ? null : owner.getUUID();
    }

    /** The loaded entity of a pet, or {@code null} when its chunk is not loaded. */
    public static Entity loaded(UUID pet) {
        Entity entity = LIVE.get(pet);
        return entity == null || entity.isRemoved() ? null : entity;
    }

    /** The up-to-date record of a loaded pet, even one tamed since the last scan. */
    static PetRecord track(UUID owner, Entity entity) {
        LIVE.put(entity.getUUID(), entity);
        update(owner, entity);
        return record(owner, entity.getUUID());
    }

    private static void scan(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                UUID owner = ownerOf(entity);
                if (owner != null && entity.isAlive()) {
                    LIVE.put(entity.getUUID(), entity);
                    update(owner, entity);
                }
            }
        }
    }

    private static void update(UUID owner, Entity entity) {
        UUID previous = STORE.ownerOf(entity.getUUID());
        if (previous != null && !previous.equals(owner)) {
            STORE.remove(previous, entity.getUUID());
        }
        PetRecord pet = STORE.getOrCreate(owner, entity.getUUID(), EntityType.getKey(entity.getType()).toString());
        LivingEntity living = (LivingEntity) entity;
        String name = entity.hasCustomName() && entity.getCustomName() != null ? entity.getCustomName().getString() : null;
        String dimension = entity.level().dimension().identifier().toString();
        boolean sitting = entity instanceof TamableAnimal tamable && tamable.isOrderedToSit();
        boolean leashed = entity instanceof Leashable leashable && leashable.isLeashed();
        boolean moved = Math.abs(pet.x - entity.getX()) > 1 || Math.abs(pet.y - entity.getY()) > 1
                || Math.abs(pet.z - entity.getZ()) > 1 || !dimension.equals(pet.dimension);
        boolean changed = moved || !java.util.Objects.equals(name, pet.name) || pet.sitting != sitting
                || pet.leashed != leashed || pet.dead || Math.abs(pet.health - living.getHealth()) > 0.25f;
        if (!java.util.Objects.equals(name, pet.name)) {
            pet.nameFormat = null; // renamed some other way (name tag, another mod)
        }
        pet.name = name;
        pet.dimension = dimension;
        pet.x = entity.getX();
        pet.y = entity.getY();
        pet.z = entity.getZ();
        pet.health = living.getHealth();
        pet.maxHealth = living.getMaxHealth();
        pet.sitting = sitting;
        pet.leashed = leashed;
        pet.dead = false;
        pet.deathMessage = null;
        pet.lastSeen = System.currentTimeMillis();
        pet.loaded = true;
        if (changed) {
            STORE.markDirty();
        }
    }

    /** Death watch and lead pulling for the pets that are loaded right now. */
    private static void watchLive(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Entity>> it = LIVE.entrySet().iterator();
        while (it.hasNext()) {
            Entity entity = it.next().getValue();
            UUID owner = ownerOf(entity);
            if (owner == null) {
                it.remove();
                continue;
            }
            if (entity instanceof LivingEntity living && living.isDeadOrDying()) {
                it.remove();
                onDeath(server, owner, living);
                continue;
            }
            if (entity.isRemoved()) {
                it.remove();
                PetRecord pet = record(owner, entity.getUUID());
                if (pet != null) {
                    pet.loaded = false;
                }
                // DISCARDED pets are gone for good (converted, removed by a command or another mod).
                if (entity.getRemovalReason() == Entity.RemovalReason.DISCARDED) {
                    STORE.remove(owner, entity.getUUID());
                }
                continue;
            }
            if (BetterPetsMod.strongLeads.get()) {
                pullOnLead(owner, entity);
            }
        }
    }

    static PetRecord record(UUID owner, UUID pet) {
        for (PetRecord record : STORE.pets(owner)) {
            if (record.id.equals(pet)) {
                return record;
            }
        }
        return null;
    }

    private static void onDeath(MinecraftServer server, UUID owner, LivingEntity living) {
        update(owner, living);
        PetRecord pet = record(owner, living.getUUID());
        if (pet == null) {
            return;
        }
        pet.dead = true;
        pet.loaded = false;
        pet.health = 0;
        pet.deathMessage = living.getCombatTracker().getDeathMessage().getString();
        STORE.markDirty();
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player != null && BetterPetsMod.deathAlerts.get()) {
            String cause = pet.deathMessage;
            player.sendSystemMessage(PetText.info(TextUtil.hex("\u2620 ", PetText.DEAD), PetText.name(pet),
                    TextUtil.body(" died").withStyle(style -> style.withHoverEvent(
                            new net.minecraft.network.chat.HoverEvent.ShowText(TextUtil.body(cause)))),
                    PetText.sep(), PetText.coords(pet.x, pet.y, pet.z),
                    TextUtil.muted(" in the " + PetBrain.dimensionName(pet.dimension) + "  "),
                    PetText.button("pets", "/pets", "Show your pets")));
        }
    }

    /**
     * Vanilla snaps a lead at 12 blocks. A pet on its owner's lead that drifts
     * past {@link #LEAD_PULL_DISTANCE} (sprinting, flying, falling) is
     * teleported next to the owner instead, the way a following pet would be.
     */
    private static void pullOnLead(UUID owner, Entity entity) {
        if (!(entity instanceof Leashable leashable) || !leashable.isLeashed()) {
            return;
        }
        Entity holder = leashable.getLeashHolder();
        if (!(holder instanceof Player player) || !player.getUUID().equals(owner)
                || holder.level() != entity.level() || entity.isPassenger()
                || entity.distanceTo(holder) <= LEAD_PULL_DISTANCE) {
            return;
        }
        if (entity instanceof TamableAnimal tamable) {
            tamable.tryToTeleportToOwner();
        }
        if (entity.distanceTo(holder) > LEAD_PULL_DISTANCE) {
            entity.teleportTo(holder.getX(), holder.getY(), holder.getZ());
        }
        entity.resetFallDistance();
    }

    /**
     * Cancels damage a player deals to their own pet (melee, sweep, arrows,
     * tridents...). Sneaking hits still land, for when you mean it.
     */
    public static boolean shouldBlockDamage(LivingEntity target, DamageSource source) {
        if (!BetterPetsMod.isModEnabled() || !BetterPetsMod.protectFromOwner.get() || target.level().isClientSide()) {
            return false;
        }
        UUID owner = ownerOf(target);
        Entity attacker = source.getEntity();
        return owner != null && attacker instanceof Player player && player.getUUID().equals(owner)
                && !player.isShiftKeyDown();
    }

    /**
     * Stops the owner's melee swing at their own pet before it lands. Checked
     * on both sides: the client predicts crit particles and knockback on its
     * own, and would show them even though {@link #shouldBlockDamage} cancels
     * the damage on the server.
     */
    public static boolean shouldBlockAttack(Player player, Entity target) {
        if (!BetterPetsMod.isModEnabled() || !BetterPetsMod.protectFromOwner.get() || player.isShiftKeyDown()) {
            return false;
        }
        UUID owner = ownerOf(target);
        return owner != null && player.getUUID().equals(owner);
    }

    // ---------------------------------------------------------------- find

    /** Starts the find compass (and glow, when the pet is loaded) for {@code player}. */
    public static void startFind(ServerPlayer player, PetRecord pet, int seconds) {
        FINDS.put(player.getUUID(), new FindSession(pet.id, ticks + seconds * 20L));
        Entity entity = loaded(pet.id);
        if (entity instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.GLOWING, seconds * 20, 0, false, false));
        }
        sendCompass(player, pet);
    }

    private static void tickFinds(MinecraftServer server) {
        Iterator<Map.Entry<UUID, FindSession>> it = FINDS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, FindSession> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            PetRecord pet = player == null ? null : record(player.getUUID(), entry.getValue().pet());
            if (player == null || pet == null || ticks > entry.getValue().endTick()) {
                it.remove();
                continue;
            }
            if (sendCompass(player, pet)) {
                it.remove();
                player.sendSystemMessage(PetText.msg(TextUtil.hex("\u2714 ", PetText.GOOD), "Found ",
                        PetText.name(pet), "!"), true);
            }
        }
    }

    /** Shows "Rex ↑→ 42 m ▲3" in the action bar; returns whether the player has arrived. */
    private static boolean sendCompass(ServerPlayer player, PetRecord pet) {
        Entity entity = loaded(pet.id);
        double x = entity != null ? entity.getX() : pet.x;
        double y = entity != null ? entity.getY() : pet.y;
        double z = entity != null ? entity.getZ() : pet.z;
        String dimension = entity != null ? entity.level().dimension().identifier().toString() : pet.dimension;
        if (!player.level().dimension().identifier().toString().equals(dimension)) {
            player.sendSystemMessage(PetText.msg(PetText.name(pet), " is in the ",
                    PetText.value(PetBrain.dimensionName(dimension))), true);
            return false;
        }
        double dx = x - player.getX();
        double dz = z - player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz + (y - player.getY()) * (y - player.getY()));
        if (distance <= FOUND_DISTANCE) {
            return true;
        }
        int dy = (int) Math.round(y - player.getY());
        var line = PetText.msg(PetText.name(pet), PetText.dim("  \u00b7  "),
                TextUtil.hex(PetBrain.arrow(player.getYRot(), dx, dz) + " ", TextUtil.PRIMARY),
                PetText.value(PetBrain.distance(distance)));
        if (Math.abs(dy) >= 3) {
            line.append(TextUtil.muted(dy > 0 ? "  \u25b2" + dy : "  \u25bc" + (-dy)));
        }
        if (entity == null) {
            line.append(TextUtil.muted("  last seen here"));
        }
        player.sendSystemMessage(line, true);
        return false;
    }

    // ---------------------------------------------------------------- call / give

    /** Teleports a loaded pet next to the player. Returns why it could not, or {@code null} on success. */
    public static Component call(ServerPlayer player, PetRecord pet, String argument) {
        Entity entity = loaded(pet.id);
        if (entity == null) {
            return PetText.msg(PetText.name(pet), " is too far away to hear you. ",
                    PetText.button("find", "/pets find " + argument, "Point the way to it"));
        }
        if (entity.level() != player.level()) {
            return PetText.msg(PetText.name(pet), " is in the ", PetText.value(PetBrain.dimensionName(pet.dimension)),
                    " and can't hear you from here.");
        }
        if (entity instanceof Leashable leashable && leashable.isLeashed()
                && leashable.getLeashHolder() != player) {
            leashable.dropLeash();
        }
        if (entity.isPassenger()) {
            entity.stopRiding();
        }
        if (entity instanceof TamableAnimal tamable) {
            tamable.tryToTeleportToOwner();
        }
        if (entity.distanceTo(player) > 3) {
            entity.teleportTo(player.getX(), player.getY(), player.getZ());
        }
        entity.resetFallDistance();
        return null;
    }

    /** Hands a loaded pet to {@code target}. Returns why it could not, or {@code null} on success. */
    public static Component give(ServerPlayer from, PetRecord pet, ServerPlayer target) {
        Entity entity = loaded(pet.id);
        if (entity == null) {
            return PetText.msg(PetText.name(pet), " is too far away. Go and get it first, then hand it over.");
        }
        if (entity instanceof TamableAnimal tamable) {
            tamable.setOwner(target);
        } else if (entity instanceof net.minecraft.world.entity.animal.equine.AbstractHorse horse) {
            horse.setOwner(target);
        } else {
            return PetText.msg(PetText.name(pet), " can't change owner.");
        }
        STORE.remove(from.getUUID(), pet.id);
        update(target.getUUID(), entity);
        return null;
    }

    /**
     * Renames a loaded pet to {@code typed}, with its {@code &} codes and
     * MiniMessage tags applied. Returns why it could not, or {@code null}.
     */
    public static Component rename(PetRecord pet, String typed) {
        Entity entity = loaded(pet.id);
        if (entity == null) {
            return PetText.msg(PetText.name(pet), " is too far away to hear its new name. Go closer and try again.");
        }
        entity.setCustomName(PetNameFormat.parse(typed));
        if (entity instanceof net.minecraft.world.entity.Mob mob) {
            mob.setPersistenceRequired();
        }
        pet.name = PetNameFormat.plain(typed);
        pet.nameFormat = typed;
        STORE.markDirty();
        return null;
    }
}

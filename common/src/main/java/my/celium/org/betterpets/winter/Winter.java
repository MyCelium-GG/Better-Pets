package my.celium.org.betterpets.winter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import my.celium.org.betterpets.BetterPetsMod;
import my.celium.org.betterpets.PetTracker;
import my.celium.org.betterpets.net.PetLooksPacket;
import my.celium.org.network.MycelNetwork;
import my.celium.org.network.PacketDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The winter update's server side, driven by {@link PetTracker}'s tick:
 *
 * <ul>
 *   <li><b>Snowy coats</b>: a dog or cat out in falling snow slowly turns white. Under a roof it
 *       shakes the snow off, in water it washes away, in the open it melts.</li>
 *   <li><b>Sleeping on the bed</b>: when you go to bed, your dogs come and lie down on the end of
 *       it (cats already do this in vanilla). More dogs take the floor next to the bed. A dog
 *       you told to sit stays where it is.</li>
 *   <li>What every nearby player should see (scarf, snow, sleeping) goes out in a
 *       {@link PetLooksPacket} twice a second.</li>
 * </ul>
 */
public final class Winter {
    public static PacketDefinition<PetLooksPacket> looks;

    /** Seconds of snowfall until a coat is fully white. */
    static final float SECONDS_TO_WHITE = 90f;
    /** Seconds an open sky without snowfall takes to melt a white coat. */
    static final float SECONDS_TO_MELT = 60f;
    static final double SLEEP_RANGE = 24.0;
    static final double LOOK_RANGE = 64.0;
    /** A dog that cannot reach its spot in this many ticks jumps there. */
    static final int WALK_TICKS = 20 * 8;
    static final int SEND_INTERVAL = 10;

    private static final Map<UUID, Float> SNOW = new HashMap<>();
    /** Dogs on their way to bed or asleep, by pet UUID. */
    private static final Map<UUID, Sleeper> SLEEPERS = new HashMap<>();
    /** Players who were sent a non-empty look list last time. */
    private static final Set<UUID> SENT = new HashSet<>();
    private static boolean dirty;
    private static long ticks;

    private static final class Sleeper {
        final UUID owner;
        final Vec3 spot;
        final float yaw;
        final long since;
        boolean lying;

        Sleeper(UUID owner, Vec3 spot, float yaw, long since) {
            this.owner = owner;
            this.spot = spot;
            this.yaw = yaw;
            this.since = since;
        }
    }

    private Winter() {
    }

    public static void tick(MinecraftServer server) {
        ticks++;
        Collection<Entity> pets = PetTracker.livePets();
        tickSleepers(server);
        if (ticks % 20 == 0) {
            if (BetterPetsMod.snowyCoats.get()) {
                tickSnow(pets);
            } else {
                SNOW.clear();
            }
            if (BetterPetsMod.sleepOnBed.get()) {
                assignSleepers(server, pets);
            } else {
                SLEEPERS.clear();
            }
        }
        if (dirty || ticks % SEND_INTERVAL == 0) {
            dirty = false;
            sendLooks(server, pets);
        }
    }

    /** A pet's scarf changed: tell the players around it right away. */
    static void lookChanged(Entity pet) {
        dirty = true;
    }

    public static void forget(UUID pet) {
        SNOW.remove(pet);
        SLEEPERS.remove(pet);
    }

    public static void forgetPlayer(UUID player) {
        SENT.remove(player);
    }

    public static void clear() {
        SNOW.clear();
        SLEEPERS.clear();
        SENT.clear();
        dirty = false;
        ticks = 0;
    }

    /** Whether this dog is lying on (or by) its owner's bed right now. */
    public static boolean isAsleep(Entity pet) {
        Sleeper sleeper = SLEEPERS.get(pet.getUUID());
        return sleeper != null && sleeper.lying;
    }

    // ---------------------------------------------------------------- snow

    /** 0 = clean, 1 = a dusting, 2 = snowy, 3 = white. */
    static int snowLevel(float snow) {
        return snow < 0.15f ? 0 : snow < 0.45f ? 1 : snow < 0.8f ? 2 : 3;
    }

    private static void tickSnow(Collection<Entity> pets) {
        Set<UUID> seen = new HashSet<>();
        for (Entity pet : pets) {
            if (!Scarves.fits(pet) || !pet.isAlive()) {
                continue;
            }
            UUID id = pet.getUUID();
            seen.add(id);
            float snow = SNOW.getOrDefault(id, 0f);
            Level level = pet.level();
            BlockPos pos = pet.blockPosition();
            boolean openSky = level.canSeeSky(pos.above());
            if (pet.isInWater()) {
                snow = 0;
            } else if (openSky && snowsAt(level, pos)) {
                snow = Math.min(1f, snow + 1f / SECONDS_TO_WHITE);
            } else if (snow > 0 && !openSky) {
                if (snowLevel(snow) > 0) {
                    shake(pet, snow);
                }
                snow = 0;
            } else if (snow > 0) {
                snow = Math.max(0f, snow - 1f / SECONDS_TO_MELT);
            }
            if (snow > 0) {
                SNOW.put(id, snow);
            } else {
                SNOW.remove(id);
            }
        }
        SNOW.keySet().retainAll(seen);
    }

    private static boolean snowsAt(Level level, BlockPos pos) {
        return level.isRaining()
                && level.getBiome(pos).value().getPrecipitationAt(pos, level.getSeaLevel()) == Biome.Precipitation.SNOW;
    }

    /** A puff of snow, and the wet-dog shake for dogs. */
    private static void shake(Entity pet, float snow) {
        if (!(pet.level() instanceof ServerLevel level)) {
            return;
        }
        if (pet instanceof Wolf) {
            level.broadcastEntityEvent(pet, (byte) 8);
        }
        int count = 10 + Math.round(snow * 30);
        level.sendParticles(ParticleTypes.SNOWFLAKE, pet.getX(), pet.getY() + pet.getBbHeight() * 0.6, pet.getZ(),
                count, pet.getBbWidth() * 0.6, pet.getBbHeight() * 0.3, pet.getBbWidth() * 0.6, 0.05);
        level.playSound(null, pet.getX(), pet.getY(), pet.getZ(),
                pet instanceof Wolf ? SoundEvents.WOLF_SHAKE : SoundEvents.SNOW_BREAK, SoundSource.NEUTRAL, 0.7f, 1.1f);
    }

    // ---------------------------------------------------------------- sleeping

    private static void assignSleepers(MinecraftServer server, Collection<Entity> pets) {
        // Wake the dogs of owners who got up (or left).
        Iterator<Map.Entry<UUID, Sleeper>> it = SLEEPERS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Sleeper> entry = it.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getValue().owner);
            Entity pet = PetTracker.loaded(entry.getKey());
            if (owner == null || !owner.isSleeping() || pet == null || !canSleep(pet, owner)) {
                it.remove();
            }
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isSleeping() || player.getSleepingPos().isEmpty()) {
                continue;
            }
            List<Entity> dogs = new ArrayList<>();
            for (Entity pet : pets) {
                if (!SLEEPERS.containsKey(pet.getUUID()) && canSleep(pet, player)
                        && pet.distanceToSqr(player) < SLEEP_RANGE * SLEEP_RANGE) {
                    dogs.add(pet);
                }
            }
            if (dogs.isEmpty()) {
                continue;
            }
            List<Vec3> spots = freeSpots(player);
            float yaw = player.getBedOrientation() == null ? 0f : player.getBedOrientation().getOpposite().toYRot();
            for (Entity dog : dogs) {
                if (spots.isEmpty()) {
                    break;
                }
                SLEEPERS.put(dog.getUUID(), new Sleeper(player.getUUID(), spots.remove(0), yaw, ticks));
            }
        }
    }

    /** Grown, free dogs of this player, in the same world, not told to sit. */
    private static boolean canSleep(Entity pet, ServerPlayer owner) {
        return pet instanceof Wolf wolf && wolf.isAlive() && !wolf.isBaby() && wolf.isOwnedBy(owner)
                && !wolf.isOrderedToSit() && !wolf.isLeashed() && !wolf.isPassenger() && wolf.getTarget() == null
                && wolf.level() == owner.level();
    }

    /** The end of the bed first, then the floor along both sides, minus spots other dogs took. */
    private static List<Vec3> freeSpots(ServerPlayer player) {
        List<Vec3> spots = new ArrayList<>();
        BlockPos head = player.getSleepingPos().orElseThrow();
        Direction facing = player.getBedOrientation();
        Level level = player.level();
        if (facing == null) {
            return spots;
        }
        BlockPos foot = head.relative(facing.getOpposite());
        BlockState footState = level.getBlockState(foot);
        if (footState.getBlock() instanceof BedBlock) {
            spots.add(new Vec3(foot.getX() + 0.5, foot.getY() + 0.5625, foot.getZ() + 0.5));
        }
        for (BlockPos bed : new BlockPos[] {foot, head}) {
            for (Direction side : new Direction[] {facing.getClockWise(), facing.getCounterClockWise()}) {
                BlockPos floor = bed.relative(side);
                if (standable(level, floor)) {
                    spots.add(Vec3.atBottomCenterOf(floor));
                }
            }
        }
        for (Sleeper taken : SLEEPERS.values()) {
            spots.removeIf(spot -> spot.distanceToSqr(taken.spot) < 0.25);
        }
        return spots;
    }

    private static boolean standable(Level level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    /** Walks each dog to its spot, then keeps it lying still there. */
    private static void tickSleepers(MinecraftServer server) {
        for (Map.Entry<UUID, Sleeper> entry : SLEEPERS.entrySet()) {
            Sleeper sleeper = entry.getValue();
            if (!(PetTracker.loaded(entry.getKey()) instanceof Wolf wolf)) {
                continue;
            }
            Vec3 spot = sleeper.spot;
            if (!sleeper.lying) {
                if (wolf.position().distanceToSqr(spot) < 1.6 * 1.6 || ticks - sleeper.since > WALK_TICKS) {
                    sleeper.lying = true;
                    dirty = true;
                } else if ((ticks - sleeper.since) % 10 == 0) {
                    wolf.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.1);
                }
                continue;
            }
            wolf.getNavigation().stop();
            wolf.setDeltaMovement(Vec3.ZERO);
            if (wolf.position().distanceToSqr(spot) > 0.04) {
                wolf.teleportTo(spot.x, spot.y, spot.z);
            }
            wolf.setYRot(sleeper.yaw);
            wolf.setYBodyRot(sleeper.yaw);
            wolf.setYHeadRot(sleeper.yaw);
            wolf.setXRot(0);
        }
    }

    // ---------------------------------------------------------------- what players see

    private static void sendLooks(MinecraftServer server, Collection<Entity> pets) {
        if (looks == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!MycelNetwork.canSend(player, looks)) {
                continue;
            }
            List<PetLooksPacket.Look> list = new ArrayList<>();
            for (Entity pet : pets) {
                if (pet.level() != player.level() || pet.distanceToSqr(player) > LOOK_RANGE * LOOK_RANGE) {
                    continue;
                }
                int scarf = BetterPetsMod.scarves.get() && Scarves.fits(pet) ? Scarves.color(pet) : -1;
                int snow = snowLevel(SNOW.getOrDefault(pet.getUUID(), 0f));
                boolean asleep = isAsleep(pet);
                if (scarf >= 0 || snow > 0 || asleep) {
                    list.add(new PetLooksPacket.Look(pet.getId(), scarf, snow, asleep));
                }
            }
            boolean sentBefore = SENT.contains(player.getUUID());
            if (list.isEmpty() && !sentBefore) {
                continue;
            }
            if (list.isEmpty()) {
                SENT.remove(player.getUUID());
            } else {
                SENT.add(player.getUUID());
            }
            MycelNetwork.sendToPlayer(player, looks, new PetLooksPacket(list));
        }
    }
}

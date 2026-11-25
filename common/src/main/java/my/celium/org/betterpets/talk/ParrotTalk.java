package my.celium.org.betterpets.talk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import my.celium.org.betterpets.BetterPetsMod;
import my.celium.org.betterpets.PetStats;
import my.celium.org.betterpets.PetTracker;
import my.celium.org.betterpets.mixin.DisplayAccessor;
import my.celium.org.betterpets.mixin.TextDisplayAccessor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.parrot.Parrot;

/**
 * Tamed parrots listen to chat. When someone talks near one, it may say the end of it back a
 * moment later in a speech bubble above its head, and it remembers a few lines it heard to bring
 * up again later while its owner is around. The bubble is a vanilla text display, so players
 * without the mod see it too; it is never saved with the world.
 */
public final class ParrotTalk {
    /** Entity tag on every speech bubble, so it is never saved (see EntityMixin). */
    public static final String BUBBLE_TAG = "betterpets.bubble";
    static final double HEAR_RANGE = 12.0;
    static final int MAX_CHARS = 28;
    static final int MEMORY = 5;
    static final int BUBBLE_TICKS = 70;

    private record Heard(UUID speaker, String text) {
    }

    private record Pending(UUID parrot, String text, long at) {
    }

    private record Bubble(Display.TextDisplay display, long until) {
    }

    private static final Queue<Heard> HEARD = new ConcurrentLinkedQueue<>();
    private static final List<Pending> PENDING = new ArrayList<>();
    private static final Map<UUID, Deque<String>> LINES = new HashMap<>();
    private static final Map<UUID, Long> NEXT_RECALL = new HashMap<>();
    private static final Map<UUID, Bubble> BUBBLES = new HashMap<>();
    private static long ticks;

    private ParrotTalk() {
    }

    /** A player said something in chat. Can come from any thread; handled on the next tick. */
    public static void heard(ServerPlayer speaker, String text) {
        if (speaker != null && text != null && HEARD.size() < 64) {
            HEARD.add(new Heard(speaker.getUUID(), text));
        }
    }

    public static void tick(MinecraftServer server) {
        ticks++;
        boolean on = BetterPetsMod.parrotTalk.get();
        Heard heard;
        while ((heard = HEARD.poll()) != null) {
            if (on) {
                listen(server, heard);
            }
        }
        if (!on) {
            clear();
            return;
        }
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext();) {
            Pending pending = it.next();
            if (ticks >= pending.at) {
                it.remove();
                if (PetTracker.loaded(pending.parrot) instanceof Parrot parrot && parrot.isAlive()) {
                    say(parrot, pending.text);
                }
            }
        }
        if (ticks % 20 == 0) {
            recall(server);
        }
        moveBubbles();
    }

    public static void clear() {
        for (Bubble bubble : BUBBLES.values()) {
            bubble.display.discard();
        }
        BUBBLES.clear();
        PENDING.clear();
        LINES.clear();
        NEXT_RECALL.clear();
        HEARD.clear();
        ticks = 0;
    }

    /** The bit of a chat line a parrot would say: short lines whole, long ones their last words. */
    public static String phrase(String text, boolean twice) {
        String clean = text.replaceAll("\\s+", " ").trim();
        if (clean.isEmpty()) {
            return "";
        }
        String out = clean;
        if (out.length() > MAX_CHARS) {
            String[] words = out.split(" ");
            StringBuilder tail = new StringBuilder();
            for (int i = words.length - 1; i >= 0 && i >= words.length - 3; i--) {
                String next = tail.isEmpty() ? words[i] : words[i] + " " + tail;
                if (next.length() > MAX_CHARS) {
                    break;
                }
                tail.setLength(0);
                tail.append(next);
            }
            out = tail.isEmpty() ? clean.substring(clean.length() - MAX_CHARS) : tail.toString();
            out = "..." + out;
        }
        String end = out.replaceAll("[.!?,;:]+$", "");
        if (twice) {
            String[] words = end.split(" ");
            String last = words[words.length - 1].replace("...", "");
            if (!last.isEmpty() && last.length() <= 12) {
                return end + ", " + last + "!";
            }
        }
        return end + "!";
    }

    private static void listen(MinecraftServer server, Heard heard) {
        ServerPlayer speaker = server.getPlayerList().getPlayer(heard.speaker);
        if (speaker == null || heard.text.startsWith("/")) {
            return;
        }
        List<Parrot> hearers = new ArrayList<>();
        for (Entity pet : PetTracker.livePets()) {
            if (pet instanceof Parrot parrot && parrot.isTame() && parrot.isAlive() && parrot.level() == speaker.level()
                    && parrot.distanceToSqr(speaker) < HEAR_RANGE * HEAR_RANGE) {
                hearers.add(parrot);
            }
        }
        if (hearers.isEmpty()) {
            return;
        }
        RandomSource random = speaker.getRandom();
        for (Parrot parrot : hearers) {
            Deque<String> lines = LINES.computeIfAbsent(parrot.getUUID(), id -> new ArrayDeque<>());
            lines.remove(heard.text);
            lines.addLast(heard.text);
            while (lines.size() > MEMORY) {
                lines.removeFirst();
            }
            NEXT_RECALL.putIfAbsent(parrot.getUUID(), ticks + 20L * (60 + random.nextInt(120)));
        }
        // One parrot at most answers right away, so a room full of them is not a choir.
        if (random.nextInt(100) < BetterPetsMod.parrotTalkChance.get()) {
            Parrot echo = hearers.get(random.nextInt(hearers.size()));
            PENDING.add(new Pending(echo.getUUID(), phrase(heard.text, random.nextInt(3) == 0),
                    ticks + 25 + random.nextInt(50)));
        }
    }

    /** Now and then a parrot near its owner says one of the lines it remembers. */
    private static void recall(MinecraftServer server) {
        for (Map.Entry<UUID, Long> entry : NEXT_RECALL.entrySet()) {
            if (ticks < entry.getValue() || !(PetTracker.loaded(entry.getKey()) instanceof Parrot parrot)
                    || !parrot.isAlive()) {
                continue;
            }
            RandomSource random = parrot.getRandom();
            entry.setValue(ticks + 20L * (120 + random.nextInt(240)));
            UUID owner = PetTracker.ownerOf(parrot);
            ServerPlayer player = owner == null ? null : server.getPlayerList().getPlayer(owner);
            Deque<String> lines = LINES.get(parrot.getUUID());
            if (player == null || lines == null || lines.isEmpty() || player.level() != parrot.level()
                    || player.distanceToSqr(parrot) > 16 * 16 || BUBBLES.containsKey(parrot.getUUID())) {
                continue;
            }
            List<String> all = new ArrayList<>(lines);
            say(parrot, phrase(all.get(random.nextInt(all.size())), random.nextInt(3) == 0));
        }
    }

    @SuppressWarnings("unchecked")
    private static void say(Parrot parrot, String text) {
        if (text.isEmpty()) {
            return;
        }
        Bubble old = BUBBLES.remove(parrot.getUUID());
        if (old != null) {
            old.display.discard();
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("text_display"));
        Display.TextDisplay display = new Display.TextDisplay(type, parrot.level());
        TextDisplayAccessor text$ = (TextDisplayAccessor) display;
        text$.betterpets$setText(Component.literal(text));
        text$.betterpets$setLineWidth(200);
        text$.betterpets$setBackgroundColor(0xB0000000);
        DisplayAccessor display$ = (DisplayAccessor) display;
        display$.betterpets$setBillboardConstraints(Display.BillboardConstraints.CENTER);
        display$.betterpets$setPosRotInterpolationDuration(3);
        display.addTag(BUBBLE_TAG);
        display.snapTo(parrot.getX(), bubbleY(parrot), parrot.getZ());
        parrot.level().addFreshEntity(display);
        BUBBLES.put(parrot.getUUID(), new Bubble(display, ticks + BUBBLE_TICKS));
        parrot.level().playSound(null, parrot.getX(), parrot.getY(), parrot.getZ(), SoundEvents.PARROT_AMBIENT,
                SoundSource.NEUTRAL, 1.0f, Parrot.getPitch(parrot.getRandom()));
        PetStats.count("parrot_talk");
    }

    private static double bubbleY(Parrot parrot) {
        return parrot.getY() + parrot.getBbHeight() + 0.3;
    }

    private static void moveBubbles() {
        for (Iterator<Map.Entry<UUID, Bubble>> it = BUBBLES.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, Bubble> entry = it.next();
            Bubble bubble = entry.getValue();
            Entity parrot = PetTracker.loaded(entry.getKey());
            if (ticks > bubble.until || parrot == null || !parrot.isAlive() || parrot.level() != bubble.display.level()) {
                bubble.display.discard();
                it.remove();
                continue;
            }
            bubble.display.teleportTo(parrot.getX(), bubbleY((Parrot) parrot), parrot.getZ());
        }
    }
}

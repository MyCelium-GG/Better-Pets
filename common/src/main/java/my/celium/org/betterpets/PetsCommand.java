package my.celium.org.betterpets;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import my.celium.org.util.TextUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * The {@code /pets} command, open to every player:
 *
 * <ul>
 *   <li>{@code /pets} - every pet you own, where it is and what it is doing;</li>
 *   <li>{@code /pets find <pet>} - glow plus an action-bar compass to it;</li>
 *   <li>{@code /pets call <pet>} - teleports a loaded pet to you;</li>
 *   <li>{@code /pets name <pet> <name>} - renames it, no name tag needed;</li>
 *   <li>{@code /pets give <pet> <player>} - hands it to another player;</li>
 *   <li>{@code /pets forget <pet>} - removes a dead or lost pet from the list.</li>
 * </ul>
 *
 * <p>{@code <pet>} is a pet's name (quoted when it has spaces) or its number
 * from the list. Output is plain vanilla chat with clickable buttons.
 */
public final class PetsCommand {
    private static final Map<UUID, Long> LAST_CALL = new HashMap<>();

    private static final SuggestionProvider<CommandSourceStack> PETS = (context, builder) -> {
        ServerPlayer player = context.getSource().getPlayer();
        if (player != null) {
            List<PetRecord> pets = PetTracker.STORE.pets(player.getUUID());
            String remaining = builder.getRemainingLowerCase();
            for (int i = 0; i < pets.size(); i++) {
                String argument = argument(pets.get(i), i + 1);
                if (argument.toLowerCase(java.util.Locale.ROOT).startsWith(remaining)
                        || argument.toLowerCase(java.util.Locale.ROOT).startsWith("\"" + remaining)) {
                    builder.suggest(argument);
                }
            }
        }
        return builder.buildFuture();
    };

    private PetsCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pets")
                .executes(PetsCommand::list)
                .then(Commands.literal("list").executes(PetsCommand::list))
                .then(Commands.literal("find").then(pet().executes(PetsCommand::find)))
                .then(Commands.literal("call").then(pet().executes(PetsCommand::call)))
                .then(Commands.literal("name").then(pet()
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(PetsCommand::name))))
                .then(Commands.literal("give").then(pet()
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(PetsCommand::give))))
                .then(Commands.literal("forget").then(pet().executes(PetsCommand::forget))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> pet() {
        return Commands.argument("pet", StringArgumentType.string()).suggests(PETS);
    }

    /** What to type for one of {@code player}'s pets. */
    static String argument(ServerPlayer player, PetRecord pet) {
        List<PetRecord> pets = PetTracker.STORE.pets(player.getUUID());
        return argument(pet, pets.indexOf(pet) + 1);
    }

    /** What to type for a pet: its name (quoted if needed) or its list number. */
    static String argument(PetRecord pet, int number) {
        String name = pet.customName();
        return name != null ? PetBrain.commandArgument(name) : String.valueOf(number);
    }

    // ---------------------------------------------------------------- list

    private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!enabled(player)) {
            return 0;
        }
        List<PetRecord> pets = PetTracker.STORE.pets(player.getUUID());
        if (pets.isEmpty()) {
            player.sendSystemMessage(PetText.info("No pets yet. Tame a wolf, cat, parrot or horse and it shows up here."));
            return 0;
        }
        player.sendSystemMessage(PetText.info("Your pets ", PetText.dim("(" + pets.size() + ")")));
        long now = System.currentTimeMillis();
        for (int i = 0; i < pets.size(); i++) {
            player.sendSystemMessage(row(player, pets.get(i), i + 1, now));
        }
        return pets.size();
    }

    private static MutableComponent row(ServerPlayer player, PetRecord pet, int number, long now) {
        boolean loaded = PetTracker.loaded(pet.id) != null;
        String arg = argument(pet, number);
        MutableComponent line = PetText.msg(PetText.dim(number + ". "));
        if (pet.dead) {
            line.append(TextUtil.hex("☠ ", PetText.DEAD));
        }
        // Species, health and what it is doing also live in the hover, so the row stays short.
        MutableComponent hover = PetText.species(pet).withStyle(style -> style.withColor(PetText.NAME));
        if (!pet.dead) {
            hover.append(TextUtil.hex("  ♥ ", PetText.HEART))
                    .append(TextUtil.body(PetBrain.health(pet.health, pet.maxHealth)));
            if (pet.sitting) {
                hover.append(TextUtil.muted("\nSitting"));
            } else if (pet.leashed) {
                hover.append(TextUtil.muted("\nOn a lead"));
            }
        }
        line.append(PetText.name(pet).withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(hover))));

        if (pet.dead) {
            line.append(PetText.sep()).append(TextUtil.hex("died " + PetBrain.ago(now - pet.lastSeen), PetText.DEAD)
                    .withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(
                            TextUtil.body(pet.deathMessage == null ? "Died" : pet.deathMessage)))));
            line.append(PetText.sep()).append(place(player, pet)).append(TextUtil.body("  "));
            line.append(PetText.button("forget", "/pets forget " + arg, "Remove it from your list"));
            return line;
        }

        line.append(TextUtil.hex(" ♥ ", PetText.HEART))
                .append(TextUtil.body(PetBrain.health(pet.health, pet.maxHealth)));
        line.append(PetText.sep()).append(place(player, pet));
        // A pet that just unloaded is where the list says it is; only older sightings need a time.
        long unseen = now - pet.lastSeen;
        if (!loaded && unseen >= 60_000L) {
            line.append(TextUtil.muted(" (" + PetBrain.ago(unseen) + ")"));
        }

        List<Component> buttons = new java.util.ArrayList<>();
        buttons.add(PetText.button("find", "/pets find " + arg, "Make it glow and point the way"));
        if (BetterPetsMod.allowCall.get()) {
            buttons.add(PetText.button("call", "/pets call " + arg, "Teleport it to you"));
        }
        if (BetterPetsMod.allowRename.get()) {
            buttons.add(PetText.suggest("name", "/pets name " + arg + " ",
                    "Give it a new name\nColors work: &6Gold, <#ff55aa>pink, <gradient:gold:red>fire"));
        }
        line.append(TextUtil.body("  ")).append(PetText.buttons(buttons.toArray(Component[]::new)));
        return line;
    }

    /** "12 m NW", or "in the nether"; hover shows the coordinates, click copies them. */
    private static MutableComponent place(ServerPlayer player, PetRecord pet) {
        String text;
        if (player.level().dimension().identifier().toString().equals(pet.dimension)) {
            double dx = pet.x - player.getX();
            double dz = pet.z - player.getZ();
            text = PetBrain.distance(Math.sqrt(dx * dx + dz * dz)) + " " + PetBrain.compass(dx, dz);
        } else {
            text = "in the " + PetBrain.dimensionName(pet.dimension);
        }
        return PetText.place(text, pet);
    }

    // ---------------------------------------------------------------- actions

    private static int find(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PetRecord pet = resolve(player, StringArgumentType.getString(context, "pet"));
        if (pet == null) {
            return 0;
        }
        if (pet.dead) {
            player.sendSystemMessage(PetText.info(TextUtil.hex("☠ ", PetText.DEAD), PetText.name(pet),
                    " died at ", PetText.coords(pet.x, pet.y, pet.z),
                    TextUtil.muted(" in the " + PetBrain.dimensionName(pet.dimension))));
            return 0;
        }
        int seconds = BetterPetsMod.findSeconds.get();
        PetTracker.startFind(player, pet, seconds);
        if (PetTracker.loaded(pet.id) != null) {
            player.sendSystemMessage(PetText.ok(PetText.name(pet), " glows for ", PetText.value(seconds + "s"),
                    ". Follow the arrow above your hotbar."));
        } else {
            player.sendSystemMessage(PetText.ok(PetText.name(pet), " is far away. The arrow points to where it was last seen."));
        }
        return 1;
    }

    private static int call(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!BetterPetsMod.allowCall.get()) {
            player.sendSystemMessage(PetText.fail("Calling pets is turned off on this server."));
            return 0;
        }
        String query = StringArgumentType.getString(context, "pet");
        PetRecord pet = resolve(player, query);
        if (pet == null || isDead(player, pet)) {
            return 0;
        }
        long now = System.currentTimeMillis();
        long cooldown = BetterPetsMod.callCooldownSeconds.get() * 1000L;
        Long last = LAST_CALL.get(player.getUUID());
        if (last != null && now - last < cooldown) {
            player.sendSystemMessage(PetText.fail("Your pets need a breather. Call again in ",
                    PetText.value(((cooldown - (now - last)) / 1000 + 1) + "s"), "."));
            return 0;
        }
        Component error = PetTracker.call(player, pet, PetBrain.commandArgument(query));
        if (error != null) {
            player.sendSystemMessage(PetText.fail(error));
            return 0;
        }
        LAST_CALL.put(player.getUUID(), now);
        player.sendSystemMessage(PetText.ok(PetText.name(pet), " came running."));
        return 1;
    }

    private static int name(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!BetterPetsMod.allowRename.get()) {
            player.sendSystemMessage(PetText.fail("Renaming is turned off on this server. A name tag still works."));
            return 0;
        }
        PetRecord pet = resolve(player, StringArgumentType.getString(context, "pet"));
        if (pet == null || isDead(player, pet)) {
            return 0;
        }
        String typed = PetBrain.cleanName(StringArgumentType.getString(context, "name"));
        String plain = typed == null ? "" : PetNameFormat.plain(typed).strip();
        if (plain.isEmpty() || plain.length() > PetBrain.MAX_NAME_LENGTH) {
            player.sendSystemMessage(PetText.fail("A name needs 1 to ", PetText.value(String.valueOf(PetBrain.MAX_NAME_LENGTH)),
                    " letters (colors don't count)."));
            return 0;
        }
        MutableComponent before = PetText.name(pet);
        Component error = PetTracker.rename(pet, typed);
        if (error != null) {
            player.sendSystemMessage(PetText.fail(error));
            return 0;
        }
        player.sendSystemMessage(PetText.ok(before, " is now called ", PetNameFormat.parse(typed), "."));
        return 1;
    }

    private static int give(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!BetterPetsMod.allowGive.get()) {
            player.sendSystemMessage(PetText.fail("Giving pets away is turned off on this server."));
            return 0;
        }
        PetRecord pet = resolve(player, StringArgumentType.getString(context, "pet"));
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        if (pet == null || isDead(player, pet)) {
            return 0;
        }
        if (target == player) {
            player.sendSystemMessage(PetText.fail(PetText.name(pet), " is already yours."));
            return 0;
        }
        MutableComponent name = PetText.name(pet);
        Component error = PetTracker.give(player, pet, target);
        if (error != null) {
            player.sendSystemMessage(PetText.fail(error));
            return 0;
        }
        player.sendSystemMessage(PetText.ok(name, " now belongs to ", target.getDisplayName(), "."));
        target.sendSystemMessage(PetText.info(TextUtil.hex("♥ ", PetText.HEART), player.getDisplayName(),
                " gave you ", name, "! ", PetText.button("pets", "/pets", "Show your pets")));
        return 1;
    }

    private static int forget(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PetRecord pet = resolve(player, StringArgumentType.getString(context, "pet"));
        if (pet == null) {
            return 0;
        }
        if (!pet.dead && PetTracker.loaded(pet.id) != null) {
            player.sendSystemMessage(PetText.fail(PetText.name(pet), " is right here and would be back on the list in a second."));
            return 0;
        }
        PetTracker.STORE.remove(player.getUUID(), pet.id);
        player.sendSystemMessage(PetText.ok(PetText.name(pet), " was removed from your list."));
        return 1;
    }

    // ---------------------------------------------------------------- helpers

    private static boolean enabled(ServerPlayer player) {
        if (!BetterPetsMod.isModEnabled()) {
            player.sendSystemMessage(PetText.fail("Better Pets is turned off on this server."));
            return false;
        }
        return true;
    }

    private static boolean isDead(ServerPlayer player, PetRecord pet) {
        if (pet.dead) {
            player.sendSystemMessage(PetText.fail(PetText.name(pet), " died " + PetBrain.ago(System.currentTimeMillis() - pet.lastSeen) + ". ",
                    PetText.button("forget", "/pets forget " + argument(player, pet), "Remove it from your list")));
            return true;
        }
        return false;
    }

    /** The pet a player means, or {@code null} after telling them what went wrong. */
    private static PetRecord resolve(ServerPlayer player, String query) {
        if (!enabled(player)) {
            return null;
        }
        List<PetRecord> pets = PetTracker.STORE.pets(player.getUUID());
        List<PetRecord> matches = PetBrain.resolve(pets, query);
        if (matches.size() == 1) {
            return matches.get(0);
        }
        if (matches.isEmpty()) {
            player.sendSystemMessage(PetText.fail("You have no pet called ", PetText.value(query), ". ",
                    PetText.button("pets", "/pets", "Show your pets")));
        } else {
            MutableComponent names = PetText.msg();
            for (int i = 0; i < matches.size(); i++) {
                names.append(i == 0 ? PetText.msg() : TextUtil.body(", ")).append(PetText.name(matches.get(i)));
            }
            player.sendSystemMessage(PetText.fail(PetText.value(query), " could be ", names,
                    ". Type more of the name, or use its number from /pets."));
        }
        return null;
    }
}

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
 *   <li>{@code /pets menu [pet]} - the chest screen, all pets or one;</li>
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
                .then(Commands.literal("menu").executes(PetsCommand::menu)
                        .then(pet().executes(PetsCommand::menuOfPet)))
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
            player.sendSystemMessage(PetText.info(PetText.t(player, "no_pets")));
            return 0;
        }
        PetStats.count("list");
        player.sendSystemMessage(PetText.header(PetText.t(player, "your_pets"), String.valueOf(pets.size())));
        long now = System.currentTimeMillis();
        for (int i = 0; i < pets.size(); i++) {
            player.sendSystemMessage(row(player, pets.get(i), i + 1, now));
        }
        return pets.size();
    }

    private static MutableComponent row(ServerPlayer player, PetRecord pet, int number, long now) {
        boolean loaded = PetTracker.loaded(pet.id) != null;
        String arg = argument(pet, number);
        // The number is what you type for unnamed pets (/pets name 2 Luna).
        MutableComponent line = PetText.msg(PetText.dim(number + " "));
        // Species, health and what it is doing also live in the hover, so the row stays short.
        MutableComponent hover = PetText.species(pet).withStyle(style -> style.withColor(PetText.NAME));
        if (!pet.dead) {
            hover.append(TextUtil.hex("  ♥ ", PetText.HEART))
                    .append(TextUtil.body(PetBrain.health(pet.health, pet.maxHealth)));
            if (pet.sitting) {
                hover.append(TextUtil.muted("\n" + PetText.t(player, "status.sitting")));
            } else if (pet.leashed) {
                hover.append(TextUtil.muted("\n" + PetText.t(player, "status.leashed")));
            }
        }
        line.append(PetText.name(pet).withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(hover))));

        if (pet.dead) {
            line.append(PetText.sep()).append(TextUtil.hex(PetText.t(player, "died_ago", PetText.ago(player, now - pet.lastSeen)), PetText.DEAD)
                    .withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(
                            TextUtil.body(pet.deathMessage == null ? PetText.t(player, "died") : pet.deathMessage)))));
            line.append(PetText.sep()).append(place(player, pet)).append(TextUtil.body("   "));
            line.append(PetText.button(PetText.t(player, "button.forget"), "/pets forget " + arg, PetText.t(player, "button.forget.tip")));
            return TextUtil.item(line);
        }

        line.append(TextUtil.hex(" ♥ ", PetText.HEART))
                .append(TextUtil.body(PetBrain.health(pet.health, pet.maxHealth)));
        line.append(PetText.sep()).append(place(player, pet));
        // A pet that just unloaded is where the list says it is; only older sightings need a time.
        long unseen = now - pet.lastSeen;
        if (!loaded && unseen >= 60_000L) {
            line.append(TextUtil.muted(" (" + PetText.ago(player, unseen) + ")"));
        }

        List<Component> buttons = new java.util.ArrayList<>();
        if (BetterPetsMod.petMenu.get()) {
            buttons.add(PetText.button(PetText.t(player, "button.menu"), "/pets menu " + pet.id, PetText.t(player, "button.menu.tip")));
        }
        buttons.add(PetText.button(PetText.t(player, "button.find"), "/pets find " + arg, PetText.t(player, "button.find.tip")));
        if (BetterPetsMod.allowCall.get()) {
            buttons.add(PetText.button(PetText.t(player, "button.call"), "/pets call " + arg, PetText.t(player, "button.call.tip")));
        }
        if (BetterPetsMod.allowRename.get()) {
            buttons.add(PetText.suggest(PetText.t(player, "button.name"), "/pets name " + arg + " ",
                    PetText.t(player, "button.name.tip") + "\n" + PetText.t(player, "colors_work") + " &6Gold, <#ff55aa>pink, <gradient:gold:red>fire"));
        }
        line.append(TextUtil.body("   ")).append(PetText.buttons(buttons.toArray(Component[]::new)));
        return TextUtil.item(line);
    }

    /** "12 m NW", or "in the nether"; hover shows the coordinates, click copies them. */
    private static MutableComponent place(ServerPlayer player, PetRecord pet) {
        return PetText.place(player, placeText(player, pet), pet);
    }

    static String placeText(ServerPlayer player, PetRecord pet) {
        if (player.level().dimension().identifier().toString().equals(pet.dimension)) {
            double dx = pet.x - player.getX();
            double dz = pet.z - player.getZ();
            return PetBrain.distance(Math.sqrt(dx * dx + dz * dz)) + " " + PetText.compass(player, PetBrain.compass(dx, dz));
        }
        return PetText.inDimension(player, pet.dimension);
    }

    // ---------------------------------------------------------------- actions

    private static int menu(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return openMenu(context.getSource().getPlayerOrException());
    }

    /** Opens the pet book (or tells the player why not); shared with /betterpets menu. */
    static int openMenu(ServerPlayer player) {
        if (!menuEnabled(player)) {
            return 0;
        }
        if (PetTracker.STORE.pets(player.getUUID()).isEmpty()) {
            player.sendSystemMessage(PetText.info(PetText.t(player, "no_pets")));
            return 0;
        }
        PetPanel.openList(player);
        return 1;
    }

    private static int menuOfPet(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!menuEnabled(player)) {
            return 0;
        }
        PetRecord pet = resolve(player, StringArgumentType.getString(context, "pet"));
        if (pet == null) {
            return 0;
        }
        PetPanel.open(player, pet);
        return 1;
    }

    private static boolean menuEnabled(ServerPlayer player) {
        if (!enabled(player)) {
            return false;
        }
        if (!BetterPetsMod.petMenu.get()) {
            player.sendSystemMessage(PetText.fail(PetText.t(player, "off.menu")));
            return false;
        }
        return true;
    }

    private static int find(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PetRecord pet = resolve(player, StringArgumentType.getString(context, "pet"));
        return pet == null ? 0 : find(player, pet);
    }

    static int find(ServerPlayer player, PetRecord pet) {
        if (pet.dead) {
            player.sendSystemMessage(PetText.fail(PetText.c(player, "died_at", PetText.name(pet), PetText.coords(player, pet.x, pet.y, pet.z)),
                    TextUtil.muted(" " + PetText.inDimension(player, pet.dimension))));
            return 0;
        }
        int seconds = BetterPetsMod.findSeconds.get();
        PetTracker.startFind(player, pet, seconds);
        PetStats.count("find");
        if (PetTracker.loaded(pet.id) != null) {
            player.sendSystemMessage(PetText.ok(PetText.c(player, "find.glows", PetText.name(pet), PetText.value(seconds + "s"))));
        } else {
            player.sendSystemMessage(PetText.ok(PetText.c(player, "find.far", PetText.name(pet))));
        }
        return 1;
    }

    private static int call(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!BetterPetsMod.allowCall.get()) {
            player.sendSystemMessage(PetText.fail(PetText.t(player, "off.call")));
            return 0;
        }
        PetRecord pet = resolve(player, StringArgumentType.getString(context, "pet"));
        return pet == null ? 0 : call(player, pet);
    }

    static int call(ServerPlayer player, PetRecord pet) {
        if (isDead(player, pet)) {
            return 0;
        }
        long now = System.currentTimeMillis();
        long cooldown = BetterPetsMod.callCooldownSeconds.get() * 1000L;
        Long last = LAST_CALL.get(player.getUUID());
        if (last != null && now - last < cooldown) {
            player.sendSystemMessage(PetText.fail(PetText.c(player, "call.cooldown",
                    PetText.value(((cooldown - (now - last)) / 1000 + 1) + "s"))));
            return 0;
        }
        Component error = PetTracker.call(player, pet, argument(player, pet));
        if (error != null) {
            player.sendSystemMessage(PetText.fail(error));
            return 0;
        }
        LAST_CALL.put(player.getUUID(), now);
        player.sendSystemMessage(PetText.ok(PetText.c(player, "call.done", PetText.name(pet))));
        return 1;
    }

    private static int name(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean fromDialog = PetMenu.fromDialog(player);
        if (!BetterPetsMod.allowRename.get()) {
            player.sendSystemMessage(PetText.fail(PetText.t(player, "off.rename")));
            return 0;
        }
        PetRecord pet = resolve(player, StringArgumentType.getString(context, "pet"));
        if (pet == null || isDead(player, pet)) {
            return 0;
        }
        String raw = StringArgumentType.getString(context, "name");
        int result = rename(player, pet, fromDialog ? PetBrain.unescapeDialog(raw) : raw);
        if (fromDialog) {
            PetMenu.open(player, pet);
        }
        return result;
    }

    /** The name to store for what a player typed, or {@code null} when it is not a usable name. */
    static String validName(String raw) {
        String typed = PetBrain.cleanName(raw);
        String plain = typed == null ? "" : PetNameFormat.plain(typed).strip();
        return plain.isEmpty() || plain.length() > PetBrain.MAX_NAME_LENGTH ? null : typed;
    }

    private static int rename(ServerPlayer player, PetRecord pet, String raw) {
        String typed = validName(raw);
        if (typed == null) {
            player.sendSystemMessage(PetText.fail(PetText.c(player, "name.length", PetText.value(String.valueOf(PetBrain.MAX_NAME_LENGTH)))));
            return 0;
        }
        MutableComponent before = PetText.name(pet);
        Component error = PetTracker.rename(player, pet, typed);
        if (error != null) {
            player.sendSystemMessage(PetText.fail(error));
            return 0;
        }
        player.sendSystemMessage(PetText.ok(PetText.c(player, "name.done", before, PetNameFormat.parse(typed))));
        return 1;
    }

    private static int give(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!BetterPetsMod.allowGive.get()) {
            player.sendSystemMessage(PetText.fail(PetText.t(player, "off.give")));
            return 0;
        }
        PetRecord pet = resolve(player, StringArgumentType.getString(context, "pet"));
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        return pet == null ? 0 : give(player, pet, target);
    }

    static int give(ServerPlayer player, PetRecord pet, ServerPlayer target) {
        if (isDead(player, pet)) {
            return 0;
        }
        if (target == player) {
            player.sendSystemMessage(PetText.fail(PetText.c(player, "give.yours", PetText.name(pet))));
            return 0;
        }
        MutableComponent name = PetText.name(pet);
        Component error = PetTracker.give(player, pet, target);
        if (error != null) {
            player.sendSystemMessage(PetText.fail(error));
            return 0;
        }
        player.sendSystemMessage(PetText.ok(PetText.c(player, "give.done", name, target.getDisplayName())));
        target.sendSystemMessage(PetText.ok(player.getDisplayName(),
                " gave you ", name, "!   ", PetText.button("Show pets", "/pets", "Show your pets")));
        return 1;
    }

    private static int forget(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PetRecord pet = resolve(player, StringArgumentType.getString(context, "pet"));
        return pet == null ? 0 : forget(player, pet);
    }

    static int forget(ServerPlayer player, PetRecord pet) {
        if (!pet.dead && PetTracker.loaded(pet.id) != null) {
            player.sendSystemMessage(PetText.fail(PetText.c(player, "forget.here", PetText.name(pet))));
            return 0;
        }
        PetTracker.STORE.remove(player.getUUID(), pet.id);
        PetStats.count("forget");
        player.sendSystemMessage(PetText.ok(PetText.c(player, "forget.done", PetText.name(pet))));
        return 1;
    }

    // ---------------------------------------------------------------- helpers

    private static boolean enabled(ServerPlayer player) {
        if (!BetterPetsMod.isModEnabled()) {
            player.sendSystemMessage(PetText.fail(PetText.t(player, "off.all")));
            return false;
        }
        return true;
    }

    private static boolean isDead(ServerPlayer player, PetRecord pet) {
        if (pet.dead) {
            player.sendSystemMessage(PetText.fail(PetText.c(player, "dead", PetText.name(pet), PetText.ago(player, System.currentTimeMillis() - pet.lastSeen)), " ",
                    PetText.button(PetText.t(player, "button.forget"), "/pets forget " + argument(player, pet), PetText.t(player, "button.forget.tip"))));
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
            player.sendSystemMessage(PetText.fail(PetText.c(player, "no_such_pet", PetText.value(query)), " ",
                    PetText.button(PetText.t(player, "button.show"), "/pets", PetText.t(player, "button.show.tip"))));
        } else {
            MutableComponent names = PetText.msg();
            for (int i = 0; i < matches.size(); i++) {
                names.append(i == 0 ? PetText.msg() : TextUtil.body(", ")).append(PetText.name(matches.get(i)));
            }
            player.sendSystemMessage(PetText.fail(PetText.c(player, "ambiguous", PetText.value(query), names)));
        }
        return null;
    }
}

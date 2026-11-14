package my.celium.org.betterpets;

import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import my.celium.org.command.CommandHelper;
import my.celium.org.config.ConfigStrings;
import my.celium.org.config.ConfigValue;
import my.celium.org.util.ChatVoice;
import my.celium.org.util.TextUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /betterpets}, the mod's own command (looking after single pets stays with {@code /pets}):
 *
 * <ul>
 *   <li>{@code /betterpets} - the version, every setting with its value, and buttons for the pet book;
 *       operators can click a value to change it;</li>
 *   <li>{@code /betterpets menu} - opens the pet book;</li>
 *   <li>{@code /betterpets settings} - opens the pet book on its settings page (in chat for clients without the mod);</li>
 *   <li>{@code /betterpets set <setting> <value>} - operators change a setting;</li>
 *   <li>{@code /betterpets reset <setting>|all} - operators put settings back to their defaults;</li>
 *   <li>{@code /betterpets reload} - operators read the config file again after editing it by hand.</li>
 * </ul>
 */
public final class BetterPetsCommand {
    static final ChatVoice VOICE = TextUtil.voice("Better Pets");
    private static final String ROOT = "betterpets";
    private static final int OP = 2;

    private static final SuggestionProvider<CommandSourceStack> SETTINGS = (context, builder) -> {
        String typed = builder.getRemainingLowerCase();
        for (ConfigValue<?> value : BetterPetsMod.config.values()) {
            String path = ConfigStrings.pathOf(value);
            if (path.toLowerCase(Locale.ROOT).startsWith(typed)) {
                builder.suggest(path);
            }
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> VALUES = (context, builder) -> {
        ConfigValue<?> value = ConfigStrings.find(BetterPetsMod.config, StringArgumentType.getString(context, "setting"));
        if (value instanceof ConfigValue.BooleanValue) {
            builder.suggest("true").suggest("false");
        } else if (value != null) {
            builder.suggest(ConfigStrings.valueToString(value));
        }
        return builder.buildFuture();
    };

    private BetterPetsCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(ROOT)
                .executes(BetterPetsCommand::status)
                .then(Commands.literal("menu").executes(BetterPetsCommand::menu))
                .then(Commands.literal("settings").executes(BetterPetsCommand::settings))
                .then(Commands.literal("set")
                        .requires(source -> CommandHelper.permission(source, OP))
                        .then(Commands.argument("setting", StringArgumentType.word()).suggests(SETTINGS)
                                .then(Commands.argument("value", StringArgumentType.greedyString()).suggests(VALUES)
                                        .executes(BetterPetsCommand::set))))
                .then(Commands.literal("reset")
                        .requires(source -> CommandHelper.permission(source, OP))
                        .then(Commands.literal("all").executes(BetterPetsCommand::resetAll))
                        .then(Commands.argument("setting", StringArgumentType.word()).suggests(SETTINGS)
                                .executes(BetterPetsCommand::reset)))
                .then(Commands.literal("reload")
                        .requires(source -> CommandHelper.permission(source, OP))
                        .executes(BetterPetsCommand::reload)));
    }

    // ---------------------------------------------------------------- status

    private static int status(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        boolean op = CommandHelper.permission(source, OP);
        PetStats.count("command_status");
        CommandHelper.info(source, VOICE.header(BetterPetsMod.META.name() + " " + BetterPetsMod.META.version()));
        if (source.getPlayer() != null) {
            CommandHelper.info(source, TextUtil.item(TextUtil.msg(
                    TextUtil.button("Pet book", "/" + ROOT + " menu", "Open your pet book"),
                    TextUtil.body("   "),
                    TextUtil.button("Settings", "/" + ROOT + " settings", "Open the pet book on its settings page"))));
        }
        for (ConfigValue<?> value : BetterPetsMod.config.values()) {
            CommandHelper.info(source, row(value, op));
        }
        CommandHelper.info(source, TextUtil.detail(op
                ? "Click a value to change it. Hover a setting to see what it does."
                : "Hover a setting to see what it does. Operators can change them."));
        return 1;
    }

    private static MutableComponent row(ConfigValue<?> value, boolean op) {
        String path = ConfigStrings.pathOf(value);
        MutableComponent name = TextUtil.muted(path + "  ").withStyle(style -> style.withHoverEvent(
                new HoverEvent.ShowText(TextUtil.body(value.description()))));
        MutableComponent shown;
        if (value instanceof ConfigValue.BooleanValue bool) {
            boolean on = bool.get();
            shown = state(on);
            if (op) {
                shown = TextUtil.empty().append(shown).append(TextUtil.body("   ")).append(TextUtil.button(
                        on ? "Turn off" : "Turn on", "/" + ROOT + " set " + path + " " + !on, value.description()));
            }
        } else {
            shown = TextUtil.hex(ConfigStrings.valueToString(value), TextUtil.ACCENT);
            if (op) {
                shown = TextUtil.empty().append(shown).append(TextUtil.body("   ")).append(TextUtil.suggest(
                        "Change", "/" + ROOT + " set " + path + " ", ConfigStrings.describe(value)));
            }
        }
        return TextUtil.item(TextUtil.empty().append(name).append(shown));
    }

    private static MutableComponent state(boolean on) {
        return on ? TextUtil.hex("on", TextUtil.SUCCESS) : TextUtil.hex("off", TextUtil.ERROR);
    }

    // ---------------------------------------------------------------- the pet book

    private static int menu(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        PetStats.count("command_menu");
        return PetsCommand.openMenu(CommandHelper.player(context.getSource()));
    }

    private static int settings(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = CommandHelper.player(context.getSource());
        PetStats.count("command_settings");
        if (BetterPetsMod.petMenu.get() && PetPanel.openSettings(player)) {
            return 1;
        }
        // No pets to open the book on, the book is off, or the client has no Better Pets: chat it is.
        return status(context);
    }

    // ---------------------------------------------------------------- operators

    private static int set(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ConfigValue<?> value = find(source, StringArgumentType.getString(context, "setting"));
        if (value == null) {
            return 0;
        }
        String raw = StringArgumentType.getString(context, "value").trim();
        String before = ConfigStrings.valueToString(value);
        try {
            ConfigStrings.parseAndSet(value, raw);
        } catch (IllegalArgumentException e) {
            CommandHelper.failure(source, VOICE.fail("'" + raw + "' does not work for "
                    + ConfigStrings.pathOf(value) + ". Allowed: " + ConfigStrings.describe(value) + "."));
            return 0;
        }
        BetterPetsMod.config.save();
        PetStats.count("command_set");
        String after = ConfigStrings.valueToString(value);
        if (before.equals(after)) {
            CommandHelper.success(source, VOICE.note(ConfigStrings.pathOf(value) + " is already " + after + "."));
        } else {
            CommandHelper.success(source, VOICE.ok(ConfigStrings.pathOf(value) + " is now ",
                    TextUtil.hex(after, TextUtil.ACCENT), TextUtil.muted("   was " + before)));
        }
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ConfigValue<?> value = find(source, StringArgumentType.getString(context, "setting"));
        if (value == null) {
            return 0;
        }
        value.reset();
        BetterPetsMod.config.save();
        PetStats.count("command_reset");
        CommandHelper.success(source, VOICE.ok(ConfigStrings.pathOf(value) + " is back to ",
                TextUtil.hex(ConfigStrings.valueToString(value), TextUtil.ACCENT), "."));
        return 1;
    }

    private static int resetAll(CommandContext<CommandSourceStack> context) {
        BetterPetsMod.config.resetAll();
        BetterPetsMod.config.save();
        PetStats.count("command_reset");
        CommandHelper.success(context.getSource(), VOICE.ok("Every setting is back to its default."));
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        BetterPetsMod.config.reload();
        PetStats.count("command_reload");
        CommandHelper.success(context.getSource(), VOICE.ok("Read the settings again from "
                + BetterPetsMod.config.file().getFileName() + "."));
        return 1;
    }

    private static ConfigValue<?> find(CommandSourceStack source, String path) {
        ConfigValue<?> value = ConfigStrings.find(BetterPetsMod.config, path);
        if (value == null) {
            CommandHelper.failure(source, VOICE.fail("There is no setting called '" + path + "'. Type /"
                    + ROOT + " to see them all."));
        }
        return value;
    }
}

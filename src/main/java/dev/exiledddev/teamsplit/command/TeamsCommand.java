package dev.exiledddev.teamsplit.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.exiledddev.teamsplit.Msg;
import dev.exiledddev.teamsplit.Permissions;
import dev.exiledddev.teamsplit.TeamManager;
import dev.exiledddev.teamsplit.TeamSplitPlugin;
import dev.exiledddev.teamsplit.split.TeamPalette;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;
import org.jspecify.annotations.Nullable;

/**
 * {@code /teams}: split players into teams and manage them.
 */
public final class TeamsCommand {

    /** Usage line, text a click puts in the chat box, and what the command does. */
    private record HelpEntry(String usage, String suggestion, String description) {
    }

    private static final List<HelpEntry> HELP = List.of(
        new HelpEntry("/teams split <count> [names...]", "/teams split ", "shuffle everyone into teams"),
        new HelpEntry("/teams create <name> [color]", "/teams create ", "make an empty team"),
        new HelpEntry("/teams add <team> <players>", "/teams add ", "put players on a team"),
        new HelpEntry("/teams remove <players>", "/teams remove ", "take players off their team"),
        new HelpEntry("/teams exclude <players>", "/teams exclude ", "keep players out of splits"),
        new HelpEntry("/teams include <players>", "/teams include ", "let excluded players be split again"),
        new HelpEntry("/teams color <team> <color>", "/teams color ", "change a team's color"),
        new HelpEntry("/teams glow <team|all> on|off", "/teams glow ", "glow in team colors"),
        new HelpEntry("/teams list", "/teams list", "show teams and members"),
        new HelpEntry("/teams disband <team>", "/teams disband ", "delete one team"),
        new HelpEntry("/teams clear", "/teams clear", "delete all TeamSplit teams"),
        new HelpEntry("/teams reload", "/teams reload", "reload config.yml"),
        new HelpEntry("/teamrun <team|all> [--as|--sudo] [--delay <ticks>] <command>", "/teamrun ", "run a command for every member")
    );

    private final TeamSplitPlugin plugin;
    private final TeamManager manager;

    public TeamsCommand(final TeamSplitPlugin plugin, final TeamManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("teams")
            .requires(source -> source.getSender().hasPermission(Permissions.MANAGE))
            .executes(this::help)
            .then(Commands.literal("help").executes(this::help))
            .then(Commands.literal("split")
                .then(Commands.argument("count", IntegerArgumentType.integer(2, 64))
                    .executes(ctx -> this.split(ctx, ""))
                    .then(Commands.argument("names", StringArgumentType.greedyString())
                        .executes(ctx -> this.split(ctx, StringArgumentType.getString(ctx, "names"))))))
            .then(Commands.literal("create")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(ctx -> this.create(ctx, null))
                    .then(Commands.argument("color", ArgumentTypes.namedColor())
                        .executes(ctx -> this.create(ctx, ctx.getArgument("color", NamedTextColor.class))))))
            .then(Commands.literal("add")
                .then(this.teamArgument(false)
                    .then(PlayerList.argument()
                        .executes(this::add))))
            .then(Commands.literal("remove")
                .then(PlayerList.argument()
                    .executes(this::remove)))
            .then(Commands.literal("exclude")
                .then(PlayerList.argument()
                    .executes(this::exclude)))
            .then(Commands.literal("include")
                .then(Commands.argument("names", StringArgumentType.greedyString())
                    .suggests((ctx, builder) -> Suggest.matching(
                        builder.createOffset(builder.getStart() + Tokens.lastTokenStart(builder.getRemaining())),
                        this.manager.excludedNames()))
                    .executes(this::include)))
            .then(Commands.literal("color")
                .then(this.teamArgument(false)
                    .then(Commands.argument("color", ArgumentTypes.namedColor())
                        .executes(this::color))))
            .then(Commands.literal("glow")
                .then(this.teamArgument(true)
                    .then(Commands.literal("on").executes(ctx -> this.glow(ctx, true)))
                    .then(Commands.literal("off").executes(ctx -> this.glow(ctx, false)))))
            .then(Commands.literal("list").executes(this::list))
            .then(Commands.literal("disband")
                .then(this.teamArgument(false)
                    .executes(this::disband)))
            .then(Commands.literal("clear").executes(this::clear))
            .then(Commands.literal("reload").executes(this::reload))
            .build();
    }

    /** A {@code <team>} argument that suggests the current TeamSplit teams, and "all" if allowed. */
    private RequiredArgumentBuilder<CommandSourceStack, String> teamArgument(final boolean allowAll) {
        return Commands.argument("team", StringArgumentType.word())
            .suggests((ctx, builder) -> {
                final List<String> options = new ArrayList<>(this.manager.teamNames());
                if (allowAll && !options.isEmpty()) {
                    options.add(TeamPalette.ALL);
                }
                return Suggest.matching(builder, options);
            });
    }

    // ---- Subcommands ----------------------------------------------------------------------

    private int help(final CommandContext<CommandSourceStack> ctx) {
        final CommandSender sender = ctx.getSource().getSender();
        Msg.info(sender, "Commands <dark_gray>(click one to type it)</dark_gray>:");
        for (final HelpEntry entry : HELP) {
            sender.sendMessage(Component.text()
                .append(Component.text(" " + entry.usage(), NamedTextColor.GOLD)
                    .clickEvent(ClickEvent.suggestCommand(entry.suggestion()))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to type " + entry.suggestion().strip()))))
                .append(Component.text(" - " + entry.description(), NamedTextColor.GRAY)));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int split(final CommandContext<CommandSourceStack> ctx, final String namesInput) {
        final CommandSender sender = ctx.getSource().getSender();
        final int count = IntegerArgumentType.getInteger(ctx, "count");
        final List<String> names = namesInput.isBlank() ? List.of() : List.of(namesInput.strip().split("\\s+"));

        final TeamManager.SplitResult result;
        try {
            result = this.manager.split(count, names);
        } catch (final IllegalArgumentException e) {
            Msg.errorText(sender, e.getMessage());
            return 0;
        }

        Msg.success(sender, "Split <players> players into <count> teams:",
            Msg.text("players", result.eligible()), Msg.text("count", result.teams().size()));
        result.teams().forEach(team -> this.sendTeamLine(sender, team));
        if (result.teams().size() < result.requested()) {
            Msg.info(sender, "Only <players> players could be split, so you got <count> teams instead of <requested>.",
                Msg.text("players", result.eligible()), Msg.text("count", result.teams().size()), Msg.text("requested", result.requested()));
        }
        if (result.skipped() > 0) {
            Msg.info(sender, "<skipped> online player(s) were left out (excluded, exempt, spectating or vanished).",
                Msg.text("skipped", result.skipped()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int create(final CommandContext<CommandSourceStack> ctx, final @Nullable NamedTextColor color) {
        final CommandSender sender = ctx.getSource().getSender();
        final String name = StringArgumentType.getString(ctx, "name");
        final Team team;
        try {
            team = this.manager.create(name, color);
        } catch (final IllegalArgumentException e) {
            Msg.errorText(sender, e.getMessage());
            return 0;
        }
        final String addCommand = "/teams add " + team.getName() + " ";
        Msg.success(sender, "Created team <team>. Add players with <usage>.",
            Msg.team(team),
            Msg.component("usage", Component.text(addCommand + "<players>", NamedTextColor.GOLD).clickEvent(ClickEvent.suggestCommand(addCommand))));
        return Command.SINGLE_SUCCESS;
    }

    private int add(final CommandContext<CommandSourceStack> ctx) {
        final Team team = this.resolveTeam(ctx);
        if (team == null) {
            return 0;
        }
        final List<Player> players = resolvePlayers(ctx);
        if (players == null) {
            return 0;
        }
        this.manager.add(team, players);
        Msg.success(ctx.getSource().getSender(), "Added <players> to <team>.", Msg.text("players", names(players)), Msg.team(team));
        return players.size();
    }

    private int remove(final CommandContext<CommandSourceStack> ctx) {
        final CommandSender sender = ctx.getSource().getSender();
        final List<Player> players = resolvePlayers(ctx);
        if (players == null) {
            return 0;
        }
        final int removed = this.manager.remove(players);
        if (removed == 0) {
            Msg.info(sender, "<players> weren't on a TeamSplit team.", Msg.text("players", names(players)));
        } else {
            Msg.success(sender, "Took <count> player(s) off their team.", Msg.text("count", removed));
        }
        return removed;
    }

    private int exclude(final CommandContext<CommandSourceStack> ctx) {
        final CommandSender sender = ctx.getSource().getSender();
        final List<Player> players = resolvePlayers(ctx);
        if (players == null) {
            return 0;
        }
        final int removed = this.manager.exclude(players);
        Msg.success(sender, "<players> won't be put on a team by /teams split.", Msg.text("players", names(players)));
        if (removed > 0) {
            Msg.info(sender, "Took <count> of them off their current team. /teams add still works for them.", Msg.text("count", removed));
        }
        return players.size();
    }

    private int include(final CommandContext<CommandSourceStack> ctx) {
        final CommandSender sender = ctx.getSource().getSender();
        final List<String> included = new ArrayList<>();
        final List<String> notExcluded = new ArrayList<>();
        for (final String name : Tokens.split(StringArgumentType.getString(ctx, "names"))) {
            final String stored = this.manager.include(name);
            if (stored == null) {
                notExcluded.add(name);
            } else {
                included.add(stored);
            }
        }
        if (!included.isEmpty()) {
            Msg.success(sender, "<players> will be included in /teams split again.", Msg.text("players", Msg.join(included)));
        }
        if (!notExcluded.isEmpty()) {
            Msg.error(sender, "<players> weren't excluded.", Msg.text("players", Msg.join(notExcluded)));
        }
        return included.size();
    }

    private int color(final CommandContext<CommandSourceStack> ctx) {
        final Team team = this.resolveTeam(ctx);
        if (team == null) {
            return 0;
        }
        this.manager.setColor(team, ctx.getArgument("color", NamedTextColor.class));
        Msg.success(ctx.getSource().getSender(), "Changed the color of <team>.", Msg.team(team));
        return Command.SINGLE_SUCCESS;
    }

    private int glow(final CommandContext<CommandSourceStack> ctx, final boolean on) {
        final CommandSender sender = ctx.getSource().getSender();
        final String target = StringArgumentType.getString(ctx, "team");
        final List<Team> teams;
        if (target.equalsIgnoreCase(TeamPalette.ALL)) {
            teams = this.manager.teams();
            if (teams.isEmpty()) {
                Msg.error(sender, "There are no TeamSplit teams yet.");
                return 0;
            }
        } else {
            final Team team = this.resolveTeam(ctx);
            if (team == null) {
                return 0;
            }
            teams = List.of(team);
        }

        teams.forEach(team -> this.manager.setGlow(team, on));
        final Component names = Component.join(JoinConfiguration.commas(true), teams.stream().map(Msg::teamName).toList());
        if (on) {
            Msg.success(sender, "<teams> now glow in their team color. Turn it off with /teams glow <target> off before filming.",
                Msg.component("teams", names), Msg.text("target", target));
        } else {
            Msg.success(sender, "Glow is off for <teams>.", Msg.component("teams", names));
        }
        return teams.size();
    }

    private int list(final CommandContext<CommandSourceStack> ctx) {
        final CommandSender sender = ctx.getSource().getSender();
        final List<Team> teams = this.manager.teams();
        if (teams.isEmpty()) {
            Msg.info(sender, "There are no TeamSplit teams right now. Make some with /teams split \\<count> or /teams create \\<name>.");
        } else {
            Msg.info(sender, "<count> team(s):", Msg.text("count", teams.size()));
            teams.forEach(team -> this.sendTeamLine(sender, team));
        }

        final List<String> excluded = this.manager.excludedNames();
        if (!excluded.isEmpty()) {
            Msg.line(sender, " <gray>Excluded from splits:</gray> <white><names>", Msg.text("names", Msg.join(excluded)));
        }

        final List<String> unassigned = Bukkit.getOnlinePlayers().stream()
            .filter(player -> this.manager.teamOf(player) == null && !this.manager.isExcluded(player))
            .map(Player::getName)
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
        if (!teams.isEmpty() && !unassigned.isEmpty()) {
            Msg.line(sender, " <gray>Online, not on a team:</gray> <white><names>", Msg.text("names", Msg.join(unassigned)));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int disband(final CommandContext<CommandSourceStack> ctx) {
        final Team team = this.resolveTeam(ctx);
        if (team == null) {
            return 0;
        }
        // The name has to be read before the team is unregistered.
        final Component name = Msg.teamName(team);
        this.manager.disband(team);
        Msg.success(ctx.getSource().getSender(), "Deleted team <team>.", Msg.component("team", name));
        return Command.SINGLE_SUCCESS;
    }

    private int clear(final CommandContext<CommandSourceStack> ctx) {
        final CommandSender sender = ctx.getSource().getSender();
        final int count = this.manager.disbandAll();
        if (count == 0) {
            Msg.info(sender, "There were no TeamSplit teams to clear.");
            return 0;
        }
        Msg.success(sender, "Deleted <count> team(s). Everyone is off their team and TeamSplit glow is gone.", Msg.text("count", count));
        return count;
    }

    private int reload(final CommandContext<CommandSourceStack> ctx) {
        this.plugin.reloadSettings();
        Msg.success(ctx.getSource().getSender(), "Reloaded config.yml. Team defaults apply to teams created from now on.");
        return Command.SINGLE_SUCCESS;
    }

    // ---- Helpers --------------------------------------------------------------------------

    /** Looks up the {@code <team>} argument, telling the sender if it doesn't exist. */
    private @Nullable Team resolveTeam(final CommandContext<CommandSourceStack> ctx) {
        final String name = StringArgumentType.getString(ctx, "team");
        final Team team = this.manager.team(name);
        if (team == null) {
            sendUnknownTeam(ctx.getSource().getSender(), name, this.manager.teamNames());
        }
        return team;
    }

    static void sendUnknownTeam(final CommandSender sender, final String name, final List<String> teamNames) {
        if (teamNames.isEmpty()) {
            Msg.error(sender, "There's no team named <name>. There are no TeamSplit teams yet; make some with /teams split or /teams create.",
                Msg.text("name", name));
        } else {
            Msg.error(sender, "There's no team named <name>. Teams: <teams>", Msg.text("name", name), Msg.text("teams", Msg.join(teamNames)));
        }
    }

    /** Resolves the {@code <players>} argument, or tells the sender what matched nobody and returns null. */
    private static @Nullable List<Player> resolvePlayers(final CommandContext<CommandSourceStack> ctx) {
        try {
            return PlayerList.resolve(ctx);
        } catch (final IllegalArgumentException e) {
            Msg.errorText(ctx.getSource().getSender(), e.getMessage());
            return null;
        }
    }

    private static String names(final List<Player> players) {
        return Msg.join(players.stream().map(Player::getName).toList());
    }

    /** " ■ red (3/4 online, glowing): Steve, Alex, ..." with offline members grayed out. */
    private void sendTeamLine(final CommandSender sender, final Team team) {
        final List<String> entries = team.getEntries().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        final long online = entries.stream().filter(entry -> Bukkit.getPlayerExact(entry) != null).count();
        final Component members = entries.isEmpty()
            ? Component.text("nobody yet", NamedTextColor.DARK_GRAY)
            : Component.join(JoinConfiguration.commas(true), entries.stream()
                .map(entry -> Component.text(entry, Bukkit.getPlayerExact(entry) != null ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY))
                .toList());
        final String details = online + "/" + entries.size() + " online" + (this.manager.isGlowing(team) ? ", glowing" : "");

        Msg.line(sender, " <square> <team> <gray>(<details>):</gray> <members>",
            Msg.component("square", Component.text("■", Msg.colorOf(team))),
            Msg.team(team),
            Msg.text("details", details),
            Msg.component("members", members));
    }
}

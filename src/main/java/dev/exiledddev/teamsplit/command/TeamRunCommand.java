package dev.exiledddev.teamsplit.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.exiledddev.teamsplit.Msg;
import dev.exiledddev.teamsplit.Permissions;
import dev.exiledddev.teamsplit.TeamManager;
import dev.exiledddev.teamsplit.run.RunRequest;
import dev.exiledddev.teamsplit.run.TeamRunner;
import dev.exiledddev.teamsplit.split.TeamPalette;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Team;

/**
 * {@code /teamrun <team|all> [--as|--sudo] [--delay <ticks>] <command...>}: runs a command once per
 * online member of a team.
 */
public final class TeamRunCommand {

    private static final List<String> DELAY_EXAMPLES = List.of("5", "10", "20", "40", "100");

    private final Plugin plugin;
    private final TeamManager manager;
    private final TeamRunner runner;

    public TeamRunCommand(final Plugin plugin, final TeamManager manager, final TeamRunner runner) {
        this.plugin = plugin;
        this.manager = manager;
        this.runner = runner;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("teamrun")
            .requires(source -> source.getSender().hasPermission(Permissions.RUN))
            .then(Commands.argument("team", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    final List<String> options = new ArrayList<>(this.manager.teamNames());
                    if (!options.isEmpty()) {
                        options.add(TeamPalette.ALL);
                    }
                    return Suggest.matching(builder, options);
                })
                .then(Commands.argument("command", StringArgumentType.greedyString())
                    .suggests(this::suggestCommand)
                    .executes(this::run)))
            .build();
    }

    private int run(final CommandContext<CommandSourceStack> ctx) {
        final CommandSender sender = ctx.getSource().getSender();
        final String teamName = StringArgumentType.getString(ctx, "team");

        final RunRequest request;
        try {
            request = RunRequest.parse(StringArgumentType.getString(ctx, "command"));
        } catch (final IllegalArgumentException e) {
            Msg.errorText(sender, e.getMessage());
            return 0;
        }
        if (request.mode() == RunRequest.Mode.SUDO && !sender.hasPermission(Permissions.RUN_SUDO)) {
            Msg.error(sender, "You don't have permission to use --sudo.");
            return 0;
        }

        final List<Team> teams = this.resolveTeams(sender, teamName);
        if (teams.isEmpty()) {
            return 0;
        }

        final List<TeamRunner.Target> targets = new ArrayList<>();
        int offline = 0;
        for (final Team team : teams) {
            for (final String entry : team.getEntries()) {
                final Player player = TeamManager.onlinePlayer(entry);
                if (player == null) {
                    offline++;
                } else {
                    targets.add(new TeamRunner.Target(player, team.getName()));
                }
            }
        }
        if (targets.isEmpty()) {
            Msg.error(sender, "Nobody on <target> is online.", Msg.text("target", teamName));
            return 0;
        }

        if (request.autoAs()) {
            Msg.info(sender, "No {player} in the command, so it runs as each player (like --as).");
        }
        this.runner.run(sender, request, targets);

        final String skipped = offline > 0 ? " Skipped " + offline + " offline." : "";
        if (request.delayTicks() > 0) {
            final double seconds = (targets.size() - 1) * request.delayTicks() / 20.0;
            Msg.success(sender, "Running for <count> player(s), one every <delay> ticks (done in about <seconds>s).<skipped>",
                Msg.text("count", targets.size()), Msg.text("delay", request.delayTicks()),
                Msg.text("seconds", String.format(Locale.ROOT, "%.1f", seconds)), Msg.text("skipped", skipped));
        } else {
            Msg.success(sender, "Ran for <count> player(s).<skipped>", Msg.text("count", targets.size()), Msg.text("skipped", skipped));
        }
        return targets.size();
    }

    /** The teams {@code name} refers to: one team, or every team for "all". Tells the sender if there are none. */
    private List<Team> resolveTeams(final CommandSender sender, final String name) {
        if (name.equalsIgnoreCase(TeamPalette.ALL)) {
            final List<Team> teams = this.manager.teams();
            if (teams.isEmpty()) {
                Msg.error(sender, "There are no TeamSplit teams yet.");
            }
            return teams;
        }
        final Team team = this.manager.team(name);
        if (team == null) {
            TeamsCommand.sendUnknownTeam(sender, name, this.manager.teamNames());
            return List.of();
        }
        return List.of(team);
    }

    // ---- Tab completion -------------------------------------------------------------------

    /**
     * Completes flags, the {player}/{team} placeholders, and the command being run, using the server's
     * own completions for that command.
     */
    private CompletableFuture<Suggestions> suggestCommand(final CommandContext<CommandSourceStack> ctx, final SuggestionsBuilder builder) {
        try {
            return this.suggestCommandUnsafe(ctx, builder);
        } catch (final RuntimeException e) {
            // Completion is a convenience; never let another plugin's completer break typing.
            this.plugin.getLogger().log(Level.FINE, "Could not tab-complete /teamrun", e);
            return builder.buildFuture();
        }
    }

    private CompletableFuture<Suggestions> suggestCommandUnsafe(final CommandContext<CommandSourceStack> ctx, final SuggestionsBuilder builder) {
        final CommandSender sender = ctx.getSource().getSender();
        final String input = builder.getRemaining();

        // Skip over the flags that are already typed out.
        final Set<String> usedFlags = new HashSet<>();
        int pos = 0;
        while (true) {
            final int tokenEnd = input.indexOf(' ', pos);
            if (tokenEnd < 0) {
                break;
            }
            final String token = input.substring(pos, tokenEnd).toLowerCase(Locale.ROOT);
            if (!token.startsWith("--")) {
                break;
            }
            usedFlags.add(token);
            pos = tokenEnd + 1;
            if (token.equals(RunRequest.FLAG_DELAY)) {
                final int valueEnd = input.indexOf(' ', pos);
                if (valueEnd < 0) {
                    return Suggest.matching(builder.createOffset(builder.getStart() + pos), DELAY_EXAMPLES);
                }
                pos = valueEnd + 1;
            }
        }

        final String command = input.substring(pos);
        final int lastSpace = command.lastIndexOf(' ');
        final SuggestionsBuilder last = builder.createOffset(builder.getStart() + pos + lastSpace + 1);

        if (lastSpace < 0) {
            // Typing the first word: a flag or a command name.
            final List<String> options = new ArrayList<>();
            for (final String flag : RunRequest.FLAGS) {
                if (!usedFlags.contains(flag)) {
                    options.add(flag);
                }
            }
            Suggest.add(last, options);
            if (!command.startsWith("-")) {
                for (final String name : this.completions(sender, command)) {
                    // Namespaced duplicates (minecraft:give) only clutter the list unless asked for.
                    if (!name.contains(":") || command.contains(":")) {
                        last.suggest(name);
                    }
                }
            }
            return last.buildFuture();
        }

        final String lastToken = command.substring(lastSpace + 1);
        Suggest.add(last, RunRequest.PLACEHOLDERS);
        if (lastToken.startsWith("{")) {
            return last.buildFuture();
        }

        // Swap placeholders for real values so the server's completer can parse the earlier arguments.
        final String teamName = StringArgumentType.getString(ctx, "team");
        final String line = command
            .replace(RunRequest.PLAYER, this.sampleMember(sender, teamName))
            .replace(RunRequest.TEAM, teamName);
        for (final String completion : this.completions(sender, line)) {
            last.suggest(completion);
        }
        return last.buildFuture();
    }

    /** What the server would suggest for {@code line}, with the leading slash Bukkit adds for players removed. */
    private List<String> completions(final CommandSender sender, final String line) {
        final List<String> completions = Bukkit.getCommandMap().tabComplete(sender, line);
        if (completions == null) {
            return List.of();
        }
        return completions.stream().map(completion -> completion.startsWith("/") ? completion.substring(1) : completion).toList();
    }

    /** A name to stand in for {player} while completing: a member of the team if one is online. */
    private String sampleMember(final CommandSender sender, final String teamName) {
        final List<Team> teams;
        if (teamName.equalsIgnoreCase(TeamPalette.ALL)) {
            teams = this.manager.teams();
        } else {
            final Team team = this.manager.team(teamName);
            teams = team == null ? List.of() : List.of(team);
        }
        for (final Team team : teams) {
            final List<Player> members = this.manager.onlineMembers(team);
            if (!members.isEmpty()) {
                return members.getFirst().getName();
            }
        }
        return sender.getName();
    }
}

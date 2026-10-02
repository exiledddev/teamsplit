package dev.exiledddev.teamsplit.run;

import dev.exiledddev.teamsplit.Msg;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Dispatches a {@link RunRequest} once per team member.
 */
public final class TeamRunner {

    /** One member to run the command for, and the team they're on (for {team}). */
    public record Target(Player player, String team) {
    }

    private final Plugin plugin;

    public TeamRunner(final Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Runs the request for every target: immediately, or one member every {@code delayTicks} ticks.
     * Delayed runs skip members who logged off and stop if the sender logged off.
     */
    public void run(final CommandSender sender, final RunRequest request, final List<Target> targets) {
        if (request.delayTicks() == 0) {
            targets.forEach(target -> this.dispatch(sender, request, target));
            return;
        }

        for (int i = 0; i < targets.size(); i++) {
            final Target target = targets.get(i);
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                if (sender instanceof Player player && !player.isOnline()) {
                    return;
                }
                if (target.player().isOnline()) {
                    this.dispatch(sender, request, target);
                }
            }, (long) i * request.delayTicks());
        }
    }

    /**
     * What {player} becomes: the player's name, or their UUID if a name lookup wouldn't find them,
     * which happens while a nickname plugin has renamed them. Vanilla commands and selectors such
     * as {@code execute as} accept UUIDs.
     */
    static String commandName(final Player player) {
        return Bukkit.getPlayerExact(player.getName()) == player ? player.getName() : player.getUniqueId().toString();
    }

    private void dispatch(final CommandSender sender, final RunRequest request, final Target target) {
        final CommandSender executor = request.mode() == RunRequest.Mode.SUDO ? target.player() : sender;
        final String line = request.commandFor(commandName(target.player()), target.team());
        try {
            Bukkit.dispatchCommand(executor, line);
        } catch (final CommandException e) {
            this.plugin.getLogger().log(Level.WARNING, "/teamrun failed for " + target.player().getName() + ": /" + line, e);
            Msg.error(sender, "Command failed for <player>, see the console.", Msg.text("player", target.player().getName()));
        }
    }
}

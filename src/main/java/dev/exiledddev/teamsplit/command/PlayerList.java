package dev.exiledddev.teamsplit.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * A {@code <players>} argument that takes any number of names and selectors, e.g.
 * {@code Director Cast1 @a[distance=..5]}. Paper's built-in player argument only takes one.
 */
final class PlayerList {

    static final String ARGUMENT = "players";
    private static final List<String> SELECTORS = List.of("@a", "@p", "@r", "@s");

    private PlayerList() {
    }

    static RequiredArgumentBuilder<CommandSourceStack, String> argument() {
        return Commands.argument(ARGUMENT, StringArgumentType.greedyString()).suggests(PlayerList::suggest);
    }

    /**
     * Resolves every name and selector to online players, without duplicates.
     *
     * @throws IllegalArgumentException with a message for the command sender if something matches nobody
     */
    static List<Player> resolve(final CommandContext<CommandSourceStack> ctx) {
        final CommandSender sender = ctx.getSource().getSender();
        final Set<Player> players = new LinkedHashSet<>();
        for (final String token : Tokens.split(StringArgumentType.getString(ctx, ARGUMENT))) {
            final List<Entity> entities;
            try {
                entities = Bukkit.selectEntities(sender, token);
            } catch (final IllegalArgumentException e) {
                throw new IllegalArgumentException("\"" + token + "\" isn't a player name or a valid selector.");
            }
            final List<Player> matched = entities.stream().filter(Player.class::isInstance).map(Player.class::cast).toList();
            if (matched.isEmpty()) {
                throw new IllegalArgumentException("No online player matches \"" + token + "\".");
            }
            players.addAll(matched);
        }
        return new ArrayList<>(players);
    }

    private static CompletableFuture<Suggestions> suggest(final CommandContext<CommandSourceStack> ctx, final SuggestionsBuilder builder) {
        final SuggestionsBuilder last = builder.createOffset(builder.getStart() + Tokens.lastTokenStart(builder.getRemaining()));
        final List<String> options = new ArrayList<>(SELECTORS);
        Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
        return Suggest.matching(last, options);
    }
}

package dev.exiledddev.teamsplit.listener;

import dev.exiledddev.teamsplit.TeamManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Glow is stored on the player, so fix it up when they join: turn it on if their team glows, or off
 * if TeamSplit made them glow and their team was since cleared or switched glow off.
 */
public final class PlayerJoinListener implements Listener {

    private final TeamManager manager;

    public PlayerJoinListener(final TeamManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        if (this.manager.refreshGlow(event.getPlayer())) {
            this.manager.save();
        }
    }
}

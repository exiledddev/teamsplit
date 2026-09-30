package dev.exiledddev.teamsplit;

import dev.exiledddev.teamsplit.command.TeamRunCommand;
import dev.exiledddev.teamsplit.command.TeamsCommand;
import dev.exiledddev.teamsplit.listener.PlayerJoinListener;
import dev.exiledddev.teamsplit.run.TeamRunner;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.File;
import org.bukkit.plugin.java.JavaPlugin;

public final class TeamSplitPlugin extends JavaPlugin {

    private PluginSettings settings;
    private TeamManager manager;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.settings = PluginSettings.load(this.getConfig(), this.getLogger());

        final TeamData data = new TeamData(new File(this.getDataFolder(), "data.yml"), this.getLogger());
        data.load();
        this.manager = new TeamManager(data, this::settings);
        final TeamRunner runner = new TeamRunner(this);

        this.getServer().getPluginManager().registerEvents(new PlayerJoinListener(this.manager), this);

        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();
            commands.register(new TeamsCommand(this, this.manager).build(), "Split players into teams and manage them");
            commands.register(new TeamRunCommand(this, this.manager, runner).build(), "Run a command for every member of a team");
        });
    }

    @Override
    public void onDisable() {
        if (this.manager != null) {
            this.manager.save();
        }
    }

    public PluginSettings settings() {
        return this.settings;
    }

    public void reloadSettings() {
        this.reloadConfig();
        this.settings = PluginSettings.load(this.getConfig(), this.getLogger());
    }
}

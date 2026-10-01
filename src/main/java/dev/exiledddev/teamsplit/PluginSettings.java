package dev.exiledddev.teamsplit;

import java.util.Locale;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scoreboard.Team;

/**
 * A snapshot of config.yml.
 */
public record PluginSettings(
    boolean excludeSpectators,
    boolean excludeVanished,
    boolean friendlyFire,
    boolean seeFriendlyInvisibles,
    boolean color,
    Team.OptionStatus nametagVisibility,
    Team.OptionStatus collision,
    String prefix
) {

    public static PluginSettings load(final FileConfiguration config, final Logger logger) {
        return new PluginSettings(
            config.getBoolean("split.exclude-spectators", true),
            config.getBoolean("split.exclude-vanished", true),
            config.getBoolean("team-defaults.friendly-fire", true),
            config.getBoolean("team-defaults.see-friendly-invisibles", true),
            config.getBoolean("team-defaults.color", true),
            parseStatus(config.getString("team-defaults.nametag-visibility", "always"), "team-defaults.nametag-visibility", logger),
            parseStatus(config.getString("team-defaults.collision", "always"), "team-defaults.collision", logger),
            config.getString("team-defaults.prefix", "")
        );
    }

    /**
     * Accepts vanilla spellings (hideForOtherTeams, pushOwnTeam) as well as dashed or underscored ones.
     */
    private static Team.OptionStatus parseStatus(final String raw, final String key, final Logger logger) {
        final String normalized = raw.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        return switch (normalized) {
            case "always" -> Team.OptionStatus.ALWAYS;
            case "never" -> Team.OptionStatus.NEVER;
            case "hideforotherteams", "pushotherteams", "forotherteams" -> Team.OptionStatus.FOR_OTHER_TEAMS;
            case "hideforownteam", "pushownteam", "forownteam" -> Team.OptionStatus.FOR_OWN_TEAM;
            default -> {
                logger.warning("Unknown value '" + raw + "' for " + key + " in config.yml, using 'always'.");
                yield Team.OptionStatus.ALWAYS;
            }
        };
    }
}

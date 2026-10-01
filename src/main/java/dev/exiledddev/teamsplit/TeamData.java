package dev.exiledddev.teamsplit;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.Nullable;

/**
 * What TeamSplit remembers across restarts, stored in data.yml. The teams themselves are vanilla
 * scoreboard teams and are saved by the server.
 */
final class TeamData {

    private final File file;
    private final Logger logger;

    /** Names of the scoreboard teams TeamSplit made, mapped to whether their members glow. */
    final Map<String, Boolean> teams = new LinkedHashMap<>();
    /** Players left out of /teams split, mapped to their last known name. */
    final Map<UUID, String> excluded = new LinkedHashMap<>();
    /** Players whose glow TeamSplit turned on, so it only ever turns off glow it caused. */
    final Set<UUID> glowing = new HashSet<>();
    /** Each team's color, kept even while colors are hidden so /teams colors on can restore it. */
    final Map<String, NamedTextColor> colors = new HashMap<>();
    /** Set by /teams colors on|off; null means follow team-defaults.color in config.yml. */
    @Nullable Boolean colorsShown;
    /** Set by /teams nametags show|hide; null means follow team-defaults.nametag-visibility. */
    @Nullable Boolean nametagsShown;

    TeamData(final File file, final Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    void load() {
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(this.file);

        for (final Map<?, ?> entry : yaml.getMapList("teams")) {
            if (entry.get("name") instanceof String name) {
                this.teams.put(name, Boolean.TRUE.equals(entry.get("glow")));
                if (entry.get("color") instanceof String colorName && NamedTextColor.NAMES.value(colorName) != null) {
                    this.colors.put(name, NamedTextColor.NAMES.value(colorName));
                }
            }
        }

        final ConfigurationSection excludedSection = yaml.getConfigurationSection("excluded");
        if (excludedSection != null) {
            for (final String key : excludedSection.getKeys(false)) {
                final UUID uuid = parseUuid(key);
                if (uuid != null) {
                    this.excluded.put(uuid, excludedSection.getString(key, key));
                }
            }
        }

        for (final String value : yaml.getStringList("glowing")) {
            final UUID uuid = parseUuid(value);
            if (uuid != null) {
                this.glowing.add(uuid);
            }
        }

        this.colorsShown = yaml.isBoolean("colors-shown") ? yaml.getBoolean("colors-shown") : null;
        this.nametagsShown = yaml.isBoolean("nametags-shown") ? yaml.getBoolean("nametags-shown") : null;
    }

    void save() {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of("Managed by TeamSplit. Use the /teams commands instead of editing this file."));

        final List<Map<String, Object>> teamList = new ArrayList<>();
        this.teams.forEach((name, glow) -> {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", name);
            entry.put("glow", glow);
            final NamedTextColor color = this.colors.get(name);
            if (color != null) {
                entry.put("color", NamedTextColor.NAMES.key(color));
            }
            teamList.add(entry);
        });
        yaml.set("teams", teamList);

        final Map<String, String> excludedMap = new HashMap<>();
        this.excluded.forEach((uuid, name) -> excludedMap.put(uuid.toString(), name));
        yaml.createSection("excluded", excludedMap);

        yaml.set("glowing", this.glowing.stream().map(UUID::toString).sorted().toList());
        yaml.set("colors-shown", this.colorsShown);
        yaml.set("nametags-shown", this.nametagsShown);

        try {
            yaml.save(this.file);
        } catch (final IOException e) {
            this.logger.log(Level.SEVERE, "Could not save " + this.file.getName(), e);
        }
    }

    private UUID parseUuid(final String value) {
        try {
            return UUID.fromString(value);
        } catch (final IllegalArgumentException e) {
            this.logger.warning("Ignoring invalid UUID in " + this.file.getName() + ": " + value);
            return null;
        }
    }
}

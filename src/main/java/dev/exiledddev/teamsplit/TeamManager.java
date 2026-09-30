package dev.exiledddev.teamsplit;

import dev.exiledddev.teamsplit.split.SplitPlanner;
import dev.exiledddev.teamsplit.split.TeamPalette;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jspecify.annotations.Nullable;

/**
 * Owns the scoreboard teams TeamSplit creates. Teams are real vanilla teams on the main scoreboard,
 * so {@code @a[team=red]} and {@code /team modify} keep working. Scoreboard teams TeamSplit didn't
 * make are never changed or removed.
 */
public final class TeamManager {

    /** The outcome of a split, for the confirmation message. */
    public record SplitResult(List<Team> teams, int requested, int eligible, int skipped) {
    }

    private final TeamData data;
    private final Supplier<PluginSettings> settings;

    TeamManager(final TeamData data, final Supplier<PluginSettings> settings) {
        this.data = data;
        this.settings = settings;
    }

    private static Scoreboard scoreboard() {
        return Bukkit.getScoreboardManager().getMainScoreboard();
    }

    // ---- Lookups ---------------------------------------------------------------------------

    /**
     * The teams TeamSplit made that still exist, in creation order. Teams deleted some other way
     * (for example vanilla /team remove) are forgotten.
     */
    public List<Team> teams() {
        final Scoreboard scoreboard = scoreboard();
        final List<Team> teams = new ArrayList<>();
        boolean pruned = false;
        final Iterator<String> names = this.data.teams.keySet().iterator();
        while (names.hasNext()) {
            final Team team = scoreboard.getTeam(names.next());
            if (team == null) {
                names.remove();
                pruned = true;
            } else {
                teams.add(team);
            }
        }
        if (pruned) {
            this.data.save();
        }
        return teams;
    }

    public List<String> teamNames() {
        return this.teams().stream().map(Team::getName).toList();
    }

    /** A team TeamSplit made, by exact name. */
    public @Nullable Team team(final String name) {
        if (!this.data.teams.containsKey(name)) {
            return null;
        }
        final Team team = scoreboard().getTeam(name);
        if (team == null) {
            this.data.teams.remove(name);
            this.data.save();
        }
        return team;
    }

    /** The TeamSplit team the player is on, if any. */
    public @Nullable Team teamOf(final Player player) {
        final Team team = scoreboard().getEntryTeam(player.getName());
        return team != null && this.data.teams.containsKey(team.getName()) ? team : null;
    }

    public List<Player> onlineMembers(final Team team) {
        return team.getEntries().stream()
            .map(Bukkit::getPlayerExact)
            .filter(Objects::nonNull)
            .toList();
    }

    public boolean isGlowing(final Team team) {
        return this.data.teams.getOrDefault(team.getName(), false);
    }

    /** True if a scoreboard team with this name exists and TeamSplit didn't make it. */
    private boolean isForeignTeam(final String name) {
        return scoreboard().getTeam(name) != null && !this.data.teams.containsKey(name);
    }

    // ---- Splitting ------------------------------------------------------------------------

    /** Online players /teams split would use right now. */
    public List<Player> eligiblePlayers() {
        final List<Player> eligible = new ArrayList<>();
        for (final Player player : Bukkit.getOnlinePlayers()) {
            if (this.isEligible(player)) {
                eligible.add(player);
            }
        }
        return eligible;
    }

    private boolean isEligible(final Player player) {
        final PluginSettings settings = this.settings.get();
        if (this.data.excluded.containsKey(player.getUniqueId())) {
            return false;
        }
        if (player.hasPermission(Permissions.EXEMPT)) {
            return false;
        }
        if (settings.excludeSpectators() && player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        return !settings.excludeVanished() || !isVanished(player);
    }

    /**
     * Vanish plugins (SuperVanish, PremiumVanish, EssentialsX, ...) still publish their state as
     * "vanished" metadata, even though Bukkit's metadata API is deprecated.
     */
    private static boolean isVanished(final Player player) {
        return player.getMetadata("vanished").stream().anyMatch(value -> value.asBoolean());
    }

    /**
     * Removes every TeamSplit team, then deals all eligible online players into {@code requested} new
     * teams. The team count is capped at the number of eligible players so no team is empty.
     *
     * @throws IllegalArgumentException with a message for the command sender; nothing is changed
     */
    public SplitResult split(final int requested, final List<String> customNames) {
        if (requested < 2) {
            throw new IllegalArgumentException("Split into at least 2 teams.");
        }
        if (customNames.size() > requested) {
            throw new IllegalArgumentException("You gave " + customNames.size() + " names for " + requested + " teams.");
        }

        final List<Player> eligible = this.eligiblePlayers();
        final int skipped = Bukkit.getOnlinePlayers().size() - eligible.size();
        if (eligible.size() < 2) {
            throw new IllegalArgumentException("Need at least 2 players to split, but only " + eligible.size()
                + " online player(s) can be split (" + skipped + " excluded, exempt, spectating or vanished).");
        }

        final int count = Math.min(requested, eligible.size());
        final List<String> names = customNames.subList(0, Math.min(count, customNames.size()));
        // Validate everything before touching the scoreboard, so a bad name leaves the old teams alone.
        final List<TeamPalette.Entry> plan = TeamPalette.plan(count, names, this::isForeignTeam);

        this.disbandAll();

        final List<Team> teams = new ArrayList<>(count);
        for (final TeamPalette.Entry entry : plan) {
            teams.add(this.register(entry.name(), entry.color()));
        }

        final List<List<Player>> dealt = SplitPlanner.deal(eligible, count, ThreadLocalRandom.current());
        for (int i = 0; i < count; i++) {
            for (final Player player : dealt.get(i)) {
                teams.get(i).addEntry(player.getName());
            }
        }

        this.data.save();
        return new SplitResult(teams, requested, eligible.size(), skipped);
    }

    // ---- Creating and editing teams -------------------------------------------------------

    /**
     * Creates an empty team.
     *
     * @param color the team color, or null to pick the first color no TeamSplit team uses
     * @throws IllegalArgumentException with a message for the command sender
     */
    public Team create(final String name, final @Nullable NamedTextColor color) {
        final String error = TeamPalette.validateName(name);
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
        if (this.team(name) != null) {
            throw new IllegalArgumentException("TeamSplit already has a team named \"" + name + "\".");
        }
        if (this.isForeignTeam(name)) {
            throw new IllegalArgumentException("A scoreboard team named \"" + name + "\" already exists and wasn't made by TeamSplit.");
        }

        final List<Team> existing = this.teams();
        final NamedTextColor teamColor = color != null ? color : TeamPalette.nextColor(
            existing.stream().filter(Team::hasColor).map(team -> NamedTextColor.nearestTo(team.color())).toList(),
            existing.size()
        );
        final Team team = this.register(name, teamColor);
        this.data.save();
        return team;
    }

    private Team register(final String name, final NamedTextColor color) {
        final PluginSettings settings = this.settings.get();
        final Team team = scoreboard().registerNewTeam(name);
        this.data.teams.put(name, false);
        this.applyColor(team, color);
        team.setAllowFriendlyFire(settings.friendlyFire());
        team.setCanSeeFriendlyInvisibles(settings.seeFriendlyInvisibles());
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, settings.nametagVisibility());
        team.setOption(Team.Option.COLLISION_RULE, settings.collision());
        return team;
    }

    /** Sets the team color, its colored display name, and the configured prefix in that color. */
    private void applyColor(final Team team, final NamedTextColor color) {
        team.color(color);
        team.displayName(Component.text(team.getName(), color));
        final String prefix = this.settings.get().prefix();
        team.prefix(prefix.isEmpty()
            ? Component.empty()
            : Msg.MINI_MESSAGE.deserialize(prefix, Placeholder.unparsed("team", team.getName())).colorIfAbsent(color));
    }

    public void setColor(final Team team, final NamedTextColor color) {
        this.applyColor(team, color);
    }

    /**
     * Puts players on a team, moving them off any other team first. Works for excluded players too.
     */
    public void add(final Team team, final Collection<Player> players) {
        for (final Player player : players) {
            team.addEntry(player.getName());
            this.refreshGlow(player);
        }
        this.data.save();
    }

    /**
     * Takes players off their TeamSplit team.
     *
     * @return the number of players who were on one
     */
    public int remove(final Collection<Player> players) {
        int removed = 0;
        for (final Player player : players) {
            final Team team = this.teamOf(player);
            if (team != null) {
                team.removeEntry(player.getName());
                this.refreshGlow(player);
                removed++;
            }
        }
        this.data.save();
        return removed;
    }

    public void disband(final Team team) {
        this.disbandQuietly(team);
        this.data.save();
    }

    /**
     * Deletes every TeamSplit team and removes the glow TeamSplit gave their members.
     *
     * @return the number of teams deleted
     */
    public int disbandAll() {
        final List<Team> teams = this.teams();
        teams.forEach(this::disbandQuietly);
        this.data.save();
        return teams.size();
    }

    private void disbandQuietly(final Team team) {
        final List<Player> members = this.onlineMembers(team);
        this.data.teams.remove(team.getName());
        team.unregister();
        members.forEach(this::refreshGlow);
    }

    // ---- Exclusions -----------------------------------------------------------------------

    /**
     * Keeps players out of future splits and takes them off their current TeamSplit team.
     *
     * @return the number of players who were on a team
     */
    public int exclude(final Collection<Player> players) {
        for (final Player player : players) {
            this.data.excluded.put(player.getUniqueId(), player.getName());
        }
        return this.remove(players);
    }

    /**
     * Lets an excluded player be split again.
     *
     * @return the player's name as stored, or null if nobody with that name was excluded
     */
    public @Nullable String include(final String name) {
        final Iterator<Map.Entry<UUID, String>> entries = this.data.excluded.entrySet().iterator();
        while (entries.hasNext()) {
            final Map.Entry<UUID, String> entry = entries.next();
            if (entry.getValue().equalsIgnoreCase(name)) {
                entries.remove();
                this.data.save();
                return entry.getValue();
            }
        }
        return null;
    }

    /** Names of excluded players, keeping each player's current name if they're online. */
    public List<String> excludedNames() {
        final List<String> names = new ArrayList<>();
        for (final Map.Entry<UUID, String> entry : this.data.excluded.entrySet()) {
            final Player online = Bukkit.getPlayer(entry.getKey());
            if (online != null && !online.getName().equals(entry.getValue())) {
                entry.setValue(online.getName());
            }
            names.add(entry.getValue());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public boolean isExcluded(final Player player) {
        return this.data.excluded.containsKey(player.getUniqueId());
    }

    // ---- Glow -----------------------------------------------------------------------------

    public void setGlow(final Team team, final boolean glow) {
        this.data.teams.put(team.getName(), glow);
        this.onlineMembers(team).forEach(this::refreshGlow);
        this.data.save();
    }

    /**
     * Makes the player glow if their TeamSplit team has glow on, and removes glow TeamSplit gave them
     * otherwise. Glow from other sources is left alone. Call {@link #save()} afterwards if this
     * returns true.
     *
     * @return true if the saved glow state changed
     */
    public boolean refreshGlow(final Player player) {
        final Team team = this.teamOf(player);
        final UUID uuid = player.getUniqueId();
        if (team != null && this.isGlowing(team)) {
            player.setGlowing(true);
            return this.data.glowing.add(uuid);
        }
        if (this.data.glowing.remove(uuid)) {
            player.setGlowing(false);
            return true;
        }
        return false;
    }

    public void save() {
        this.data.save();
    }
}

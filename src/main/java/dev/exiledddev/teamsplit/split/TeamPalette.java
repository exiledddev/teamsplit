package dev.exiledddev.teamsplit.split;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jspecify.annotations.Nullable;

/**
 * Default team names and colors, and the rules for picking names when splitting.
 */
public final class TeamPalette {

    /** A planned team: its name and color. */
    public record Entry(String name, NamedTextColor color) {
    }

    /** Reserved word that targets every team in /teamrun and /teams glow. */
    public static final String ALL = "all";

    /** Names must stay usable unquoted in selectors such as {@code @a[team=name]}. */
    public static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_+-]{1,32}");

    /** The 16 vanilla team colors, in the order default teams use them. */
    public static final List<Entry> DEFAULTS = List.of(
        new Entry("red", NamedTextColor.RED),
        new Entry("blue", NamedTextColor.BLUE),
        new Entry("green", NamedTextColor.GREEN),
        new Entry("yellow", NamedTextColor.YELLOW),
        new Entry("aqua", NamedTextColor.AQUA),
        new Entry("pink", NamedTextColor.LIGHT_PURPLE),
        new Entry("gold", NamedTextColor.GOLD),
        new Entry("purple", NamedTextColor.DARK_PURPLE),
        new Entry("white", NamedTextColor.WHITE),
        new Entry("gray", NamedTextColor.GRAY),
        new Entry("dark_red", NamedTextColor.DARK_RED),
        new Entry("dark_blue", NamedTextColor.DARK_BLUE),
        new Entry("dark_green", NamedTextColor.DARK_GREEN),
        new Entry("dark_aqua", NamedTextColor.DARK_AQUA),
        new Entry("dark_gray", NamedTextColor.DARK_GRAY),
        new Entry("black", NamedTextColor.BLACK)
    );

    private TeamPalette() {
    }

    /**
     * Checks a user-supplied team name.
     *
     * @return an error message, or {@code null} if the name is fine
     */
    public static @Nullable String validateName(final String name) {
        if (!VALID_NAME.matcher(name).matches()) {
            return "Team names can only use letters, numbers, _ + and - (max 32 characters): " + name;
        }
        if (name.equalsIgnoreCase(ALL)) {
            return "\"" + ALL + "\" is reserved (it targets every team), pick another name.";
        }
        return null;
    }

    /**
     * Returns the first palette color not in {@code used}, or cycles through the palette by
     * {@code fallbackIndex} once all 16 colors are taken.
     */
    public static NamedTextColor nextColor(final Collection<? extends NamedTextColor> used, final int fallbackIndex) {
        for (final Entry entry : DEFAULTS) {
            if (!used.contains(entry.color())) {
                return entry.color();
            }
        }
        return DEFAULTS.get(Math.floorMod(fallbackIndex, DEFAULTS.size())).color();
    }

    /**
     * Picks names and colors for a split into {@code count} teams.
     * <ul>
     *   <li>The first teams use {@code customNames}, in order, with the first unused palette colors.</li>
     *   <li>The rest use palette names (red, blue, ...), skipping any name or color already used and any
     *       name for which {@code isTaken} returns true.</li>
     *   <li>When the palette runs out, teams are named team17, team18, ... and cycle through the colors.</li>
     * </ul>
     *
     * @param isTaken true for names that belong to a scoreboard team TeamSplit doesn't manage
     * @throws IllegalArgumentException with a message for the command sender if a custom name is unusable
     */
    public static List<Entry> plan(final int count, final List<String> customNames, final Predicate<String> isTaken) {
        if (count < 1) {
            throw new IllegalArgumentException("Need at least 1 team.");
        }
        if (customNames.size() > count) {
            throw new IllegalArgumentException("You gave " + customNames.size() + " names but only " + count + " teams will be made.");
        }

        final Set<String> usedNames = new HashSet<>();
        for (final String name : customNames) {
            final String error = validateName(name);
            if (error != null) {
                throw new IllegalArgumentException(error);
            }
            if (!usedNames.add(name.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("The name \"" + name + "\" is used twice.");
            }
            if (isTaken.test(name)) {
                throw new IllegalArgumentException("A scoreboard team named \"" + name + "\" already exists and wasn't made by TeamSplit. Pick another name or remove it with /team remove " + name + ".");
            }
        }

        final List<Entry> result = new ArrayList<>(count);
        final Set<NamedTextColor> usedColors = new HashSet<>();
        for (int i = 0; i < count; i++) {
            final Entry entry;
            if (i < customNames.size()) {
                entry = new Entry(customNames.get(i), nextColor(usedColors, i));
            } else {
                entry = pickDefault(i, usedNames, usedColors, isTaken);
                usedNames.add(entry.name().toLowerCase(Locale.ROOT));
            }
            usedColors.add(entry.color());
            result.add(entry);
        }
        return result;
    }

    private static Entry pickDefault(final int index, final Set<String> usedNames, final Set<NamedTextColor> usedColors, final Predicate<String> isTaken) {
        for (final Entry entry : DEFAULTS) {
            if (!usedColors.contains(entry.color()) && !usedNames.contains(entry.name()) && !isTaken.test(entry.name())) {
                return entry;
            }
        }
        int number = index + 1;
        String name = "team" + number;
        while (usedNames.contains(name) || isTaken.test(name)) {
            name = "team" + ++number;
        }
        return new Entry(name, DEFAULTS.get(index % DEFAULTS.size()).color());
    }
}

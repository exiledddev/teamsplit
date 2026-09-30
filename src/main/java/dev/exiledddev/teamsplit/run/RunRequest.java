package dev.exiledddev.teamsplit.run;

import java.util.List;
import java.util.Locale;

/**
 * A parsed {@code /teamrun} command: how to run it, how fast, and the command template.
 *
 * @param mode       who runs the command and from where
 * @param delayTicks ticks between members, 0 to run for everyone at once
 * @param command    the command template, without a leading slash
 * @param autoAs     true if {@link Mode#AS} was picked automatically because the command has no {@value #PLAYER}
 */
public record RunRequest(Mode mode, int delayTicks, String command, boolean autoAs) {

    public enum Mode {
        /** The /teamrun sender runs the command once per member, with {player} substituted. */
        SENDER,
        /** The sender runs {@code execute as <member> at @s run <command>}, so @s and ~ ~ ~ are the member. */
        AS,
        /** Each member runs the command themselves, with their own permissions. */
        SUDO
    }

    public static final String PLAYER = "{player}";
    public static final String TEAM = "{team}";
    public static final List<String> PLACEHOLDERS = List.of(PLAYER, TEAM);

    public static final String FLAG_AS = "--as";
    public static final String FLAG_SUDO = "--sudo";
    public static final String FLAG_DELAY = "--delay";
    public static final List<String> FLAGS = List.of(FLAG_AS, FLAG_SUDO, FLAG_DELAY);

    /** Five minutes. Long enough for any staggered effect, short enough to catch typos. */
    public static final int MAX_DELAY_TICKS = 20 * 60 * 5;

    /**
     * Parses the text after {@code /teamrun <team>}: optional flags, then the command.
     *
     * @throws IllegalArgumentException with a message for the command sender
     */
    public static RunRequest parse(final String input) {
        String rest = input.strip();
        boolean as = false;
        boolean sudo = false;
        int delay = 0;

        while (rest.startsWith("--")) {
            final String flag = firstToken(rest);
            rest = afterFirstToken(rest);

            final String lower = flag.toLowerCase(Locale.ROOT);
            if (lower.equals(FLAG_AS)) {
                as = true;
            } else if (lower.equals(FLAG_SUDO)) {
                sudo = true;
            } else if (lower.equals(FLAG_DELAY) || lower.startsWith(FLAG_DELAY + "=")) {
                final String value;
                if (lower.equals(FLAG_DELAY)) {
                    value = firstToken(rest);
                    rest = afterFirstToken(rest);
                } else {
                    value = flag.substring(FLAG_DELAY.length() + 1);
                }
                delay = parseDelay(value);
            } else {
                throw new IllegalArgumentException("Unknown flag " + flag + ". Flags are --as, --sudo and --delay <ticks>.");
            }
        }

        if (as && sudo) {
            throw new IllegalArgumentException("Use either --as or --sudo, not both.");
        }
        if (rest.startsWith("/")) {
            rest = rest.substring(1);
        }
        if (rest.isBlank()) {
            throw new IllegalArgumentException("Missing the command to run, e.g. /teamrun red give {player} diamond 5");
        }

        Mode mode = sudo ? Mode.SUDO : as ? Mode.AS : Mode.SENDER;
        boolean autoAs = false;
        if (mode == Mode.SENDER && !rest.contains(PLAYER)) {
            // Without {player} every run would be identical, so run it as each member instead.
            mode = Mode.AS;
            autoAs = true;
        }
        return new RunRequest(mode, delay, rest, autoAs);
    }

    /**
     * Builds the command line to dispatch for one member.
     */
    public String commandFor(final String playerName, final String teamName) {
        final String line = this.command.replace(PLAYER, playerName).replace(TEAM, teamName);
        if (this.mode == Mode.AS) {
            return "execute as " + playerName + " at @s run " + line;
        }
        return line;
    }

    private static int parseDelay(final String value) {
        if (value.isEmpty()) {
            throw new IllegalArgumentException("--delay needs a number of ticks (20 ticks = 1 second), e.g. --delay 20");
        }
        final int ticks;
        try {
            ticks = Integer.parseInt(value);
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException("--delay needs a whole number of ticks, got \"" + value + "\".");
        }
        if (ticks < 1 || ticks > MAX_DELAY_TICKS) {
            throw new IllegalArgumentException("--delay must be between 1 and " + MAX_DELAY_TICKS + " ticks.");
        }
        return ticks;
    }

    private static String firstToken(final String text) {
        final int space = text.indexOf(' ');
        return space < 0 ? text : text.substring(0, space);
    }

    private static String afterFirstToken(final String text) {
        final int space = text.indexOf(' ');
        return space < 0 ? "" : text.substring(space + 1).stripLeading();
    }
}

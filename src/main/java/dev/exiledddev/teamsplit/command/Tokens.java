package dev.exiledddev.teamsplit.command;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a list of player names and selectors on spaces, keeping selectors such as
 * {@code @a[distance=..10, gamemode=survival]} and quoted text in one piece.
 */
public final class Tokens {

    private Tokens() {
    }

    public static List<String> split(final String input) {
        final List<String> tokens = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        int depth = 0;
        char quote = 0;

        for (int i = 0; i < input.length(); i++) {
            final char c = input.charAt(i);
            if (quote != 0) {
                if (c == '\\' && i + 1 < input.length()) {
                    current.append(c).append(input.charAt(++i));
                    continue;
                }
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '[' || c == '{') {
                depth++;
            } else if ((c == ']' || c == '}') && depth > 0) {
                depth--;
            } else if (c == ' ' && depth == 0) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(c);
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    /** Where the token being typed starts, for tab completion. */
    public static int lastTokenStart(final String input) {
        int start = 0;
        int depth = 0;
        char quote = 0;
        for (int i = 0; i < input.length(); i++) {
            final char c = input.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '[' || c == '{') {
                depth++;
            } else if ((c == ']' || c == '}') && depth > 0) {
                depth--;
            } else if (c == ' ' && depth == 0) {
                start = i + 1;
            }
        }
        return start;
    }
}

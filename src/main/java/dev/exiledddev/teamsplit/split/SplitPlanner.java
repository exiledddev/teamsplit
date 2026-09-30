package dev.exiledddev.teamsplit.split;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Deals players into equally sized teams.
 */
public final class SplitPlanner {

    private SplitPlanner() {
    }

    /**
     * Shuffles {@code players} and deals them round-robin into {@code teamCount} teams, so team sizes
     * differ by at most one. Dealing starts at a random team, so which teams get the leftover players
     * is random too.
     *
     * @return {@code teamCount} lists, one per team, in team order
     */
    public static <T> List<List<T>> deal(final Collection<T> players, final int teamCount, final Random random) {
        if (teamCount < 1) {
            throw new IllegalArgumentException("teamCount must be at least 1, got " + teamCount);
        }

        final List<T> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled, random);

        final List<List<T>> teams = new ArrayList<>(teamCount);
        for (int i = 0; i < teamCount; i++) {
            teams.add(new ArrayList<>());
        }

        final int offset = random.nextInt(teamCount);
        for (int i = 0; i < shuffled.size(); i++) {
            teams.get((i + offset) % teamCount).add(shuffled.get(i));
        }
        return teams;
    }
}

package dev.exiledddev.teamsplit.split;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SplitPlannerTest {

    private static List<Integer> players(final int count) {
        return IntStream.range(0, count).boxed().toList();
    }

    @ParameterizedTest
    @CsvSource({"10, 2", "11, 2", "7, 3", "20, 6", "3, 3", "17, 16", "40, 17"})
    void teamSizesDifferByAtMostOne(final int playerCount, final int teamCount) {
        final List<List<Integer>> teams = SplitPlanner.deal(players(playerCount), teamCount, new Random(42));

        assertEquals(teamCount, teams.size());
        final int min = teams.stream().mapToInt(List::size).min().orElseThrow();
        final int max = teams.stream().mapToInt(List::size).max().orElseThrow();
        assertTrue(max - min <= 1, "sizes " + min + ".." + max);
    }

    @Test
    void everyPlayerIsDealtExactlyOnce() {
        final List<List<Integer>> teams = SplitPlanner.deal(players(25), 4, new Random(7));

        final List<Integer> dealt = new ArrayList<>();
        teams.forEach(dealt::addAll);
        assertEquals(25, dealt.size());
        assertEquals(new HashSet<>(players(25)), new HashSet<>(dealt));
    }

    @Test
    void oddCountGivesOneTeamTheExtraPlayer() {
        final List<List<Integer>> teams = SplitPlanner.deal(players(9), 2, new Random(1));

        final List<Integer> sizes = teams.stream().map(List::size).sorted().toList();
        assertEquals(List.of(4, 5), sizes);
    }

    @Test
    void shufflesPlayers() {
        final List<List<Integer>> first = SplitPlanner.deal(players(30), 2, new Random(1));
        final List<List<Integer>> second = SplitPlanner.deal(players(30), 2, new Random(2));

        assertTrue(!first.equals(second), "different seeds should give different teams");
    }

    @Test
    void rejectsZeroTeams() {
        assertThrows(IllegalArgumentException.class, () -> SplitPlanner.deal(players(4), 0, new Random()));
    }
}

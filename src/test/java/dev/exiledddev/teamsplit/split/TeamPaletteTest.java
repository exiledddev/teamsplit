package dev.exiledddev.teamsplit.split;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

class TeamPaletteTest {

    private static List<String> names(final List<TeamPalette.Entry> entries) {
        return entries.stream().map(TeamPalette.Entry::name).toList();
    }

    @Test
    void defaultNamesFollowThePalette() {
        final List<TeamPalette.Entry> plan = TeamPalette.plan(3, List.of(), name -> false);

        assertEquals(List.of("red", "blue", "green"), names(plan));
        assertEquals(NamedTextColor.RED, plan.get(0).color());
        assertEquals(NamedTextColor.BLUE, plan.get(1).color());
        assertEquals(NamedTextColor.GREEN, plan.get(2).color());
    }

    @Test
    void fallsBackToNumberedTeamsAfterSixteen() {
        final List<TeamPalette.Entry> plan = TeamPalette.plan(18, List.of(), name -> false);

        assertEquals(18, plan.size());
        assertEquals("black", plan.get(15).name());
        assertEquals("team17", plan.get(16).name());
        assertEquals("team18", plan.get(17).name());
        assertEquals(18, new HashSet<>(names(plan)).size());
    }

    @Test
    void customNamesComeFirstAndTakeTheFirstColors() {
        final List<TeamPalette.Entry> plan = TeamPalette.plan(3, List.of("knights", "mages", "rogues"), name -> false);

        assertEquals(List.of("knights", "mages", "rogues"), names(plan));
        assertEquals(NamedTextColor.RED, plan.get(0).color());
        assertEquals(NamedTextColor.BLUE, plan.get(1).color());
        assertEquals(NamedTextColor.GREEN, plan.get(2).color());
    }

    @Test
    void remainingTeamsAvoidColorsUsedByCustomNames() {
        final List<TeamPalette.Entry> plan = TeamPalette.plan(3, List.of("knights"), name -> false);

        assertEquals(List.of("knights", "blue", "green"), names(plan));
        assertEquals(3, plan.stream().map(TeamPalette.Entry::color).distinct().count());
    }

    @Test
    void skipsDefaultNamesTakenByOtherTeams() {
        final Set<String> taken = Set.of("red", "green");
        final List<TeamPalette.Entry> plan = TeamPalette.plan(3, List.of(), taken::contains);

        assertEquals(List.of("blue", "yellow", "aqua"), names(plan));
    }

    @Test
    void rejectsCustomNameTakenByOtherTeam() {
        assertThrows(IllegalArgumentException.class, () -> TeamPalette.plan(2, List.of("staff"), "staff"::equals));
    }

    @Test
    void rejectsDuplicateNamesIgnoringCase() {
        assertThrows(IllegalArgumentException.class, () -> TeamPalette.plan(2, List.of("Knights", "knights"), name -> false));
    }

    @Test
    void rejectsMoreNamesThanTeams() {
        assertThrows(IllegalArgumentException.class, () -> TeamPalette.plan(2, List.of("a", "b", "c"), name -> false));
    }

    @Test
    void validatesNames() {
        assertNull(TeamPalette.validateName("red"));
        assertNull(TeamPalette.validateName("team_1-a+b"));
        assertNotNull(TeamPalette.validateName("all"));
        assertNotNull(TeamPalette.validateName("ALL"));
        assertNotNull(TeamPalette.validateName("two words"));
        assertNotNull(TeamPalette.validateName("a.b"));
        assertNotNull(TeamPalette.validateName("x".repeat(33)));
        assertNotNull(TeamPalette.validateName(""));
    }

    @Test
    void nextColorPicksFirstUnused() {
        assertEquals(NamedTextColor.RED, TeamPalette.nextColor(List.of(), 0));
        assertEquals(NamedTextColor.GREEN, TeamPalette.nextColor(List.of(NamedTextColor.RED, NamedTextColor.BLUE), 2));
    }
}

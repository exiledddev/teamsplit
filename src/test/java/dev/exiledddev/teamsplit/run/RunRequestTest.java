package dev.exiledddev.teamsplit.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RunRequestTest {

    @Test
    void substitutesPlayerAndTeam() {
        final RunRequest request = RunRequest.parse("give {player} diamond 5");

        assertEquals(RunRequest.Mode.SENDER, request.mode());
        assertFalse(request.autoAs());
        assertEquals("give Steve diamond 5", request.commandFor("Steve", "red"));
        assertEquals("tellraw Alex \"You are on blue\"", RunRequest.parse("tellraw {player} \"You are on {team}\"").commandFor("Alex", "blue"));
    }

    @Test
    void asFlagWrapsInExecute() {
        final RunRequest request = RunRequest.parse("--as tp @s ~ ~5 ~");

        assertEquals(RunRequest.Mode.AS, request.mode());
        assertFalse(request.autoAs());
        assertEquals("execute as Steve at @s run tp @s ~ ~5 ~", request.commandFor("Steve", "red"));
    }

    @Test
    void commandWithoutPlayerRunsAsEachPlayer() {
        final RunRequest request = RunRequest.parse("gamemode creative");

        assertEquals(RunRequest.Mode.AS, request.mode());
        assertTrue(request.autoAs());
        assertEquals("execute as Steve at @s run gamemode creative", request.commandFor("Steve", "red"));
    }

    @Test
    void sudoRunsCommandAsIs() {
        final RunRequest request = RunRequest.parse("--sudo warp arena");

        assertEquals(RunRequest.Mode.SUDO, request.mode());
        assertFalse(request.autoAs());
        assertEquals("warp arena", request.commandFor("Steve", "red"));
    }

    @Test
    void parsesDelayInBothForms() {
        assertEquals(20, RunRequest.parse("--delay 20 say {player}").delayTicks());
        assertEquals(40, RunRequest.parse("--delay=40 say {player}").delayTicks());
        assertEquals(0, RunRequest.parse("say {player}").delayTicks());
    }

    @Test
    void combinesFlagsInAnyOrder() {
        final RunRequest request = RunRequest.parse("--delay 10 --as particle flame ~ ~1 ~");

        assertEquals(RunRequest.Mode.AS, request.mode());
        assertEquals(10, request.delayTicks());
        assertEquals("particle flame ~ ~1 ~", request.command());
    }

    @Test
    void stripsLeadingSlash() {
        assertEquals("give {player} apple", RunRequest.parse("/give {player} apple").command());
        assertEquals("give {player} apple", RunRequest.parse("--as /give {player} apple").command());
    }

    @Test
    void flagsAreCaseInsensitive() {
        assertEquals(RunRequest.Mode.AS, RunRequest.parse("--AS say hi").mode());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "   ",
        "--as",
        "--as --sudo say hi",
        "--delay",
        "--delay abc say hi",
        "--delay 0 say hi",
        "--delay 999999 say hi",
        "--fast say hi"
    })
    void rejectsBadInput(final String input) {
        assertThrows(IllegalArgumentException.class, () -> RunRequest.parse(input));
    }
}

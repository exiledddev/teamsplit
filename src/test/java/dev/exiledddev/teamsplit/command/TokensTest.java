package dev.exiledddev.teamsplit.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class TokensTest {

    @Test
    void splitsNamesOnSpaces() {
        assertEquals(List.of("Director", "Cast1", "Cast2"), Tokens.split("Director  Cast1 Cast2 "));
    }

    @Test
    void keepsSelectorsWithSpacesTogether() {
        assertEquals(List.of("Steve", "@a[distance=..10, gamemode=survival]", "@p"),
            Tokens.split("Steve @a[distance=..10, gamemode=survival] @p"));
    }

    @Test
    void keepsQuotedTextTogether() {
        assertEquals(List.of("@e[name=\"Big Bob\"]", "Alex"), Tokens.split("@e[name=\"Big Bob\"] Alex"));
        assertEquals(List.of("@a[nbt={Tags:[\"a b\"]}]"), Tokens.split("@a[nbt={Tags:[\"a b\"]}]"));
    }

    @Test
    void emptyInputHasNoTokens() {
        assertEquals(List.of(), Tokens.split("   "));
    }

    @Test
    void findsStartOfTokenBeingTyped() {
        assertEquals(0, Tokens.lastTokenStart("Ste"));
        assertEquals(6, Tokens.lastTokenStart("Steve Al"));
        assertEquals(6, Tokens.lastTokenStart("Steve "));
        assertEquals(6, Tokens.lastTokenStart("Steve @a[distance=..5, "));
    }
}

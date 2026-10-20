package com.example.bbettercalendar.ui.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class GreetingResolverTest {

    @Test
    public void profileGivesItsNameInitialsAndColour() {
        GreetingResolver.Greeting g = GreetingResolver.resolve("Laia Puig", 5, null);
        assertEquals(GreetingResolver.Kind.PROFILE, g.kind);
        assertEquals("Laia Puig", g.name);
        assertEquals("LP", g.initials);
        assertEquals(5, g.colorIndex);
    }

    @Test
    public void localNameUsesTheDefaultColour() {
        GreetingResolver.Greeting g = GreetingResolver.resolve(null, 3, "guille");
        assertEquals(GreetingResolver.Kind.LOCAL, g.kind);
        assertEquals("guille", g.name);
        assertEquals("G", g.initials);
        assertEquals(-1, g.colorIndex);
    }

    @Test
    public void nothingGivesTheGenericGreetingWithoutInitials() {
        GreetingResolver.Greeting g = GreetingResolver.resolve(null, 0, null);
        assertEquals(GreetingResolver.Kind.GENERIC, g.kind);
        assertEquals("", g.name);
        assertEquals("", g.initials);
        assertEquals(-1, g.colorIndex);
    }

    @Test
    public void profileBeatsLocalName() {
        GreetingResolver.Greeting g = GreetingResolver.resolve("Ana", 2, "Guille");
        assertEquals(GreetingResolver.Kind.PROFILE, g.kind);
        assertEquals("Ana", g.name);
    }

    @Test
    public void blankNamesCountAsMissing() {
        assertSame(GreetingResolver.resolve(null, 0, null), GreetingResolver.resolve("  ", 4, "\t"));
        assertEquals(GreetingResolver.Kind.LOCAL, GreetingResolver.resolve("", 1, " Joan ").kind);
        assertEquals("Joan", GreetingResolver.resolve("", 1, " Joan ").name);
    }

    @Test
    public void profileColourIsClampedToThePalette() {
        assertEquals(7, GreetingResolver.resolve("Ana", 99, null).colorIndex);
        assertEquals(0, GreetingResolver.resolve("Ana", -4, null).colorIndex);
    }
}

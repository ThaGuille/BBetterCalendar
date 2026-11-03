package com.example.bbettercalendar.social.invite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class InviteLinkRoutingTest {

    private static final String HOST = InviteLinkFactory.HOST;

    @Test
    public void socialOffStoresNothing() {
        assertNull(InviteLinkRouting.codeToStore(false, "https", HOST, "/i/ABCD2345"));
    }

    @Test
    public void socialOnStoresNormalizedCode() {
        String expected = InviteCodes.fromLink("/i/ABCD2345");
        assertEquals(expected, InviteLinkRouting.codeToStore(true, "https", HOST, "/i/ABCD2345"));
        assertEquals(expected, InviteLinkRouting.codeToStore(true, "https", HOST.toUpperCase(), "/i/ABCD2345"));
    }

    @Test
    public void malformedCodeStoresNothing() {
        assertNull(InviteLinkRouting.codeToStore(true, "https", HOST, "/i/!!"));
        assertNull(InviteLinkRouting.codeToStore(true, "https", HOST, "/x/ABCD2345"));
        assertNull(InviteLinkRouting.codeToStore(true, "https", HOST, null));
    }

    @Test
    public void foreignSchemeOrHostStoresNothing() {
        assertNull(InviteLinkRouting.codeToStore(true, "http", HOST, "/i/ABCD2345"));
        assertNull(InviteLinkRouting.codeToStore(true, "https", "evil.example", "/i/ABCD2345"));
        assertNull(InviteLinkRouting.codeToStore(true, "https", null, "/i/ABCD2345"));
    }
}

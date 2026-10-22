package com.example.bbettercalendar.social.invite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.net.URI;

/** F2-u6: {@link InviteLinkFactory} — el enlace https://bbetter-dbcda.web.app/i/<código> y su vuelta. */
public class InviteLinkFactoryTest {

    @Test
    public void buildsTheHostingLinkAndRoundTripsThroughFromLink() {
        String link = InviteLinkFactory.link("abcd2345");
        assertEquals("https://bbetter-dbcda.web.app/i/ABCD2345", link);
        URI uri = URI.create(link);
        assertEquals("https", uri.getScheme());
        assertEquals(InviteLinkFactory.HOST, uri.getHost());
        assertEquals("ABCD2345", InviteCodes.fromLink(uri.getPath()));
        try {
            InviteLinkFactory.link("ABCD1234");
            fail("invalid code accepted");
        } catch (IllegalArgumentException expected) {
            // 1 no es del alfabeto
        }
    }
}

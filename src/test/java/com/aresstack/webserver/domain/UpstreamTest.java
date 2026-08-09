package com.aresstack.webserver.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UpstreamTest {

    @Test
    void parsesHttpUrlWithPort() {
        Upstream upstream = Upstream.parse("http://192.168.178.30:8080");
        assertEquals("http", upstream.scheme());
        assertEquals("192.168.178.30", upstream.host());
        assertEquals(8080, upstream.port());
    }

    @Test
    void defaultsPortByScheme() {
        assertEquals(80, Upstream.parse("http://backend.local").port());
        assertEquals(443, Upstream.parse("https://backend.local").port());
    }

    @Test
    void rendersCaddyAddress() {
        assertEquals("127.0.0.1:8080", Upstream.parse("http://127.0.0.1:8080").toCaddyAddress());
        assertEquals("https://backend:8443", Upstream.parse("https://backend:8443").toCaddyAddress());
    }

    @Test
    void rejectsInvalidUrls() {
        assertThrows(IllegalArgumentException.class, () -> Upstream.parse("not a url"));
        assertThrows(IllegalArgumentException.class, () -> Upstream.parse("ftp://host:21"));
        assertThrows(IllegalArgumentException.class, () -> new Upstream("http", "host", 0));
        assertThrows(IllegalArgumentException.class, () -> new Upstream("http", "host", 70000));
        assertThrows(IllegalArgumentException.class, () -> new Upstream("http", " ", 80));
    }
}

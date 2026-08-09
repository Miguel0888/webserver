package com.aresstack.webserver.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebServerConfigurationTest {

    private static WebServerConfiguration config(List<Site> sites) {
        return new WebServerConfiguration(
                new DomainName("aresstack.de"),
                AcmeConfiguration.letsEncrypt("admin@aresstack.de"),
                Upstream.parse("http://127.0.0.1:8080"),
                sites);
    }

    @Test
    void validConfigurationPasses() {
        assertDoesNotThrow(() -> config(List.of(
                Site.of("aresstack.de"),
                Site.of("askai.aresstack.de", "http://192.168.178.30:8080")
        )).validate());
    }

    @Test
    void emptyConfigurationIsValid() {
        // 0 veröffentlichte Services ist ein legitimer Zustand.
        assertDoesNotThrow(() -> config(List.of()).validate());
    }

    @Test
    void rejectsDuplicateHosts() {
        assertThrows(IllegalArgumentException.class, () -> config(List.of(
                Site.of("aresstack.de"),
                Site.of("aresstack.de")
        )).validate());
    }

    @Test
    void anyFullyQualifiedDomainMayBePublished() {
        // Das domain-Feld ist rein informativ und beschränkt Sites nicht.
        assertDoesNotThrow(() -> config(List.of(
                Site.of("example.com")
        )).validate());
    }

    @Test
    void rejectsDuplicatePathMatchers() {
        Upstream backend = Upstream.parse("http://192.168.178.20:9000");
        Site site = new Site(new DomainName("aresstack.de"), Optional.empty(), List.of(
                new Route("/api/*", backend),
                new Route("/api/*", backend)));
        assertThrows(IllegalArgumentException.class, () -> config(List.of(site)).validate());
    }

    @Test
    void siteWithoutUpstreamUsesDefault() {
        Upstream defaultUpstream = Upstream.parse("http://127.0.0.1:8080");
        assertEquals(defaultUpstream, Site.of("aresstack.de").effectiveUpstream(defaultUpstream));
    }

    @Test
    void routeMatcherMustStartWithSlash() {
        Upstream backend = Upstream.parse("http://127.0.0.1:9000");
        assertThrows(IllegalArgumentException.class, () -> new Route("api/*", backend));
    }
}

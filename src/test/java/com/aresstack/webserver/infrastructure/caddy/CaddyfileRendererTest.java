package com.aresstack.webserver.infrastructure.caddy;

import com.aresstack.webserver.domain.AcmeConfiguration;
import com.aresstack.webserver.domain.DomainName;
import com.aresstack.webserver.domain.Route;
import com.aresstack.webserver.domain.Site;
import com.aresstack.webserver.domain.Upstream;
import com.aresstack.webserver.domain.WebServerConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaddyfileRendererTest {

    private final CaddyfileRenderer renderer = new CaddyfileRenderer();

    private static WebServerConfiguration config(List<Site> sites) {
        return new WebServerConfiguration(
                new DomainName("aresstack.de"),
                new AcmeConfiguration("admin@aresstack.de", AcmeConfiguration.LETS_ENCRYPT_PRODUCTION),
                Upstream.parse("http://127.0.0.1:8080"),
                sites);
    }

    @Test
    void rendersConceptExample() {
        String caddyfile = renderer.render(config(List.of(
                Site.of("aresstack.de"),
                Site.of("askai.aresstack.de", "http://192.168.178.30:8080"),
                Site.of("git.aresstack.de", "http://192.168.178.40:3000"))));

        assertEquals("""
                {
                    email admin@aresstack.de
                    acme_ca https://acme-v02.api.letsencrypt.org/directory
                    admin 127.0.0.1:2019
                }

                aresstack.de {
                    reverse_proxy 127.0.0.1:8080
                }

                askai.aresstack.de {
                    reverse_proxy 192.168.178.30:8080
                }

                git.aresstack.de {
                    reverse_proxy 192.168.178.40:3000
                }
                """, caddyfile);
    }

    @Test
    void rendersPathRoutesWithDefaultFallbackLast() {
        Site site = new Site(new DomainName("aresstack.de"), Optional.empty(), List.of(
                new Route("/api/*", Upstream.parse("http://192.168.178.20:9000")),
                new Route("/files/*", Upstream.parse("http://192.168.178.25:8080"))));

        String caddyfile = renderer.render(config(List.of(site)));

        assertEquals("""
                {
                    email admin@aresstack.de
                    acme_ca https://acme-v02.api.letsencrypt.org/directory
                    admin 127.0.0.1:2019
                }

                aresstack.de {
                    handle /api/* {
                        reverse_proxy 192.168.178.20:9000
                    }

                    handle /files/* {
                        reverse_proxy 192.168.178.25:8080
                    }

                    handle {
                        reverse_proxy 127.0.0.1:8080
                    }
                }
                """, caddyfile);
    }

    @Test
    void renderingIsDeterministic() {
        WebServerConfiguration configuration = config(List.of(
                Site.of("aresstack.de"),
                Site.of("git.aresstack.de", "http://192.168.178.40:3000")));
        assertEquals(renderer.render(configuration), renderer.render(configuration));
    }

    @Test
    void adminIsAlwaysLoopbackOnly() {
        String caddyfile = renderer.render(config(List.of(Site.of("aresstack.de"))));
        assertTrue(caddyfile.contains("admin 127.0.0.1:2019"));
    }
}

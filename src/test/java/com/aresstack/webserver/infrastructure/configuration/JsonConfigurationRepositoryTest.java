package com.aresstack.webserver.infrastructure.configuration;

import com.aresstack.webserver.domain.WebServerConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonConfigurationRepositoryTest {

    private static final String EXAMPLE = """
            {
              "domain": "aresstack.de",
              "defaultUpstream": "http://127.0.0.1:8080",
              "acme": {
                "email": "admin@aresstack.de",
                "ca": "https://acme-v02.api.letsencrypt.org/directory"
              },
              "sites": [
                { "host": "aresstack.de" },
                { "host": "askai.aresstack.de", "upstream": "http://192.168.178.30:8080" },
                { "host": "git.aresstack.de",
                  "upstream": "http://192.168.178.40:3000",
                  "routes": [
                    { "path": "/api/*", "upstream": "http://192.168.178.20:9000" }
                  ]
                }
              ]
            }
            """;

    @Test
    void loadsExampleConfiguration(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("webserver.json");
        Files.writeString(file, EXAMPLE);

        WebServerConfiguration configuration = new JsonConfigurationRepository(file).load();
        configuration.validate();

        assertEquals("aresstack.de", configuration.domain().value());
        assertEquals("http://127.0.0.1:8080", configuration.defaultUpstream().toString());
        assertEquals(3, configuration.sites().size());
        assertTrue(configuration.sites().get(0).upstream().isEmpty());
        assertEquals("http://192.168.178.30:8080",
                configuration.sites().get(1).upstream().orElseThrow().toString());
        assertEquals("/api/*", configuration.sites().get(2).routes().get(0).pathMatcher());
    }

    @Test
    void missingUpstreamFallsBackToLocalhost(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("webserver.json");
        Files.writeString(file, """
                {
                  "domain": "aresstack.de",
                  "acme": { "email": "admin@aresstack.de" },
                  "sites": [ { "host": "aresstack.de" } ]
                }
                """);

        WebServerConfiguration configuration = new JsonConfigurationRepository(file).load();

        assertEquals("http://127.0.0.1:8080", configuration.defaultUpstream().toString());
        assertEquals("https://acme-v02.api.letsencrypt.org/directory", configuration.acme().ca());
    }

    @Test
    void roundTripsThroughSave(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("webserver.json");
        Files.writeString(file, EXAMPLE);
        JsonConfigurationRepository repository = new JsonConfigurationRepository(file);

        WebServerConfiguration original = repository.load();
        repository.save(original);
        WebServerConfiguration reloaded = repository.load();

        assertEquals(original, reloaded);
    }

    @Test
    void customPortsAndDisabledHttpsRoundTrip(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("webserver.json");
        Files.writeString(file, """
                {
                  "domain": "aresstack.de",
                  "httpPort": 8081,
                  "httpsPort": 8444,
                  "acme": { "email": "admin@aresstack.de" },
                  "sites": [ { "host": "aresstack.de", "https": false } ]
                }
                """);
        JsonConfigurationRepository repository = new JsonConfigurationRepository(file);

        WebServerConfiguration configuration = repository.load();
        assertEquals(8081, configuration.httpPort());
        assertEquals(8444, configuration.httpsPort());
        assertEquals(false, configuration.sites().get(0).httpsEnabled());

        repository.save(configuration);
        assertEquals(configuration, repository.load());
    }

    @Test
    void rejectsMissingRequiredFields(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("webserver.json");
        Files.writeString(file, "{ \"domain\": \"aresstack.de\" }");
        assertThrows(IllegalArgumentException.class, () -> new JsonConfigurationRepository(file).load());
    }

    @Test
    void rejectsInvalidUpstreamUrl(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("webserver.json");
        Files.writeString(file, """
                {
                  "domain": "aresstack.de",
                  "acme": { "email": "admin@aresstack.de" },
                  "sites": [ { "host": "aresstack.de", "upstream": "nonsense" } ]
                }
                """);
        assertThrows(IllegalArgumentException.class, () -> new JsonConfigurationRepository(file).load());
    }

    @Test
    void missingFileFailsWithClearError(@TempDir Path dir) {
        assertThrows(UncheckedIOException.class,
                () -> new JsonConfigurationRepository(dir.resolve("missing.json")).load());
    }
}

package com.aresstack.webserver.application;

import com.aresstack.webserver.domain.AcmeConfiguration;
import com.aresstack.webserver.domain.DomainName;
import com.aresstack.webserver.domain.Site;
import com.aresstack.webserver.domain.Upstream;
import com.aresstack.webserver.domain.WebServerConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApplyConfigurationTest {

    static class RecordingWriter implements CaddyConfigurationWriter {
        final List<String> events = new ArrayList<>();
        boolean failValidation;

        @Override
        public String render(WebServerConfiguration configuration) {
            events.add("render");
            return "caddyfile";
        }

        @Override
        public void persistGenerated(String caddyfileContent) {
            events.add("persist");
        }

        @Override
        public void validateWithCaddy(String caddyfileContent) {
            events.add("validate");
            if (failValidation) {
                throw new ConfigurationApplyException("caddy validate failed");
            }
        }
    }

    static class RecordingAdmin implements CaddyAdmin {
        final List<String> events;
        boolean rejectLoad;

        RecordingAdmin(List<String> events) {
            this.events = events;
        }

        @Override
        public void loadCaddyfile(String caddyfileContent) {
            events.add("load");
            if (rejectLoad) {
                throw new ConfigurationApplyException("caddy rejected config");
            }
        }

        @Override
        public void requestShutdown() {
        }

        @Override
        public boolean isReachable() {
            return true;
        }
    }

    static class RecordingRepository implements ConfigurationRepository {
        final List<String> events;

        RecordingRepository(List<String> events) {
            this.events = events;
        }

        @Override
        public WebServerConfiguration load() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void save(WebServerConfiguration configuration) {
            events.add("save");
        }
    }

    private static WebServerConfiguration validConfig() {
        return new WebServerConfiguration(
                new DomainName("aresstack.de"),
                AcmeConfiguration.letsEncrypt("admin@aresstack.de"),
                Upstream.parse("http://127.0.0.1:8080"),
                List.of(Site.of("aresstack.de")));
    }

    @Test
    void appliesInStrictOrderAndPersistsOnlyAfterSuccessfulLoad() {
        RecordingWriter writer = new RecordingWriter();
        RecordingAdmin admin = new RecordingAdmin(writer.events);
        RecordingRepository repository = new RecordingRepository(writer.events);

        new ApplyConfiguration(writer, admin, repository).apply(validConfig());

        assertEquals(List.of("render", "validate", "load", "persist", "save"), writer.events);
    }

    @Test
    void failedCaddyValidationNeverReachesRunningServer() {
        RecordingWriter writer = new RecordingWriter();
        writer.failValidation = true;
        RecordingAdmin admin = new RecordingAdmin(writer.events);
        RecordingRepository repository = new RecordingRepository(writer.events);
        ApplyConfiguration useCase = new ApplyConfiguration(writer, admin, repository);

        assertThrows(ConfigurationApplyException.class, () -> useCase.apply(validConfig()));
        assertEquals(List.of("render", "validate"), writer.events);
    }

    @Test
    void rejectedLoadDoesNotPersist() {
        RecordingWriter writer = new RecordingWriter();
        RecordingAdmin admin = new RecordingAdmin(writer.events);
        admin.rejectLoad = true;
        RecordingRepository repository = new RecordingRepository(writer.events);
        ApplyConfiguration useCase = new ApplyConfiguration(writer, admin, repository);

        assertThrows(ConfigurationApplyException.class, () -> useCase.apply(validConfig()));
        assertEquals(List.of("render", "validate", "load"), writer.events);
    }

    @Test
    void invalidDomainConfigurationFailsBeforeRendering() {
        RecordingWriter writer = new RecordingWriter();
        ApplyConfiguration useCase = new ApplyConfiguration(
                writer, new RecordingAdmin(writer.events), new RecordingRepository(writer.events));
        WebServerConfiguration invalid = new WebServerConfiguration(
                new DomainName("aresstack.de"),
                AcmeConfiguration.letsEncrypt("admin@aresstack.de"),
                Upstream.parse("http://127.0.0.1:8080"),
                List.of(Site.of("aresstack.de"), Site.of("aresstack.de")));

        assertThrows(IllegalArgumentException.class, () -> useCase.apply(invalid));
        assertEquals(List.of(), writer.events);
    }
}

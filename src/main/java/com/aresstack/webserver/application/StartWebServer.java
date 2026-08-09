package com.aresstack.webserver.application;

import com.aresstack.webserver.domain.WebServerConfiguration;

/**
 * Startet Caddy mit der zuletzt persistierten Konfiguration und wendet sie an.
 */
public class StartWebServer {

    private final ConfigurationRepository repository;
    private final CaddyConfigurationWriter writer;
    private final CaddyRuntime runtime;

    public StartWebServer(ConfigurationRepository repository, CaddyConfigurationWriter writer,
                          CaddyRuntime runtime) {
        this.repository = repository;
        this.writer = writer;
        this.runtime = runtime;
    }

    public WebServerConfiguration start() {
        WebServerConfiguration configuration = repository.load();
        configuration.validate();
        String caddyfile = writer.render(configuration);
        writer.validateWithCaddy(caddyfile);
        writer.persistGenerated(caddyfile);
        runtime.start();
        return configuration;
    }
}

package com.aresstack.webserver.application;

import com.aresstack.webserver.domain.WebServerConfiguration;

/**
 * Prüft eine Konfiguration vollständig (Domainregeln + caddy validate),
 * ohne sie anzuwenden.
 */
public class ValidateConfiguration {

    private final CaddyConfigurationWriter writer;

    public ValidateConfiguration(CaddyConfigurationWriter writer) {
        this.writer = writer;
    }

    public void validate(WebServerConfiguration configuration) {
        configuration.validate();
        writer.validateWithCaddy(writer.render(configuration));
    }
}

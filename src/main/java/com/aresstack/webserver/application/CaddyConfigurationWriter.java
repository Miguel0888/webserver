package com.aresstack.webserver.application;

import com.aresstack.webserver.domain.WebServerConfiguration;

/**
 * Port zum Rendern und externen Validieren der generierten Caddy-Konfiguration.
 */
public interface CaddyConfigurationWriter {

    /** Rendert das Domainmodell deterministisch zu einem Caddyfile. */
    String render(WebServerConfiguration configuration);

    /** Schreibt das Caddyfile nach generated/ und gibt den Inhalt zurück. */
    void persistGenerated(String caddyfileContent);

    /** Prüft die Konfiguration mit {@code caddy validate}, wirft bei Fehler. */
    void validateWithCaddy(String caddyfileContent);
}

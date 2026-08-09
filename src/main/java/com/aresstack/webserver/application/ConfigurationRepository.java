package com.aresstack.webserver.application;

import com.aresstack.webserver.domain.WebServerConfiguration;

/**
 * Port für das Laden und Speichern der fachlichen Konfiguration
 * (config/webserver.json).
 */
public interface ConfigurationRepository {

    WebServerConfiguration load();

    void save(WebServerConfiguration configuration);
}

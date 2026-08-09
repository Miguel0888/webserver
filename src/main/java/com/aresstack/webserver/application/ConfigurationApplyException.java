package com.aresstack.webserver.application;

/**
 * Eine neue Konfiguration konnte nicht angewendet werden; die zuletzt
 * funktionierende Konfiguration bleibt aktiv.
 */
public class ConfigurationApplyException extends RuntimeException {

    public ConfigurationApplyException(String message) {
        super(message);
    }

    public ConfigurationApplyException(String message, Throwable cause) {
        super(message, cause);
    }
}

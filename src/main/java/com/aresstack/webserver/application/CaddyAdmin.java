package com.aresstack.webserver.application;

/**
 * Port zur Caddy Admin API (127.0.0.1:2019).
 */
public interface CaddyAdmin {

    /**
     * Lädt die komplette Konfiguration atomar. Schlägt der Reload fehl,
     * bleibt die alte Konfiguration aktiv und diese Methode wirft eine
     * {@link ConfigurationApplyException}.
     */
    void loadCaddyfile(String caddyfileContent);

    /** Bittet Caddy über POST /stop um einen geordneten Shutdown. */
    void requestShutdown();

    boolean isReachable();
}

package com.aresstack.webserver.application;

/**
 * Port für die Lebenszyklusverwaltung des Caddy-Prozesses.
 */
public interface CaddyRuntime {

    void start();

    void stop();

    boolean isRunning();
}

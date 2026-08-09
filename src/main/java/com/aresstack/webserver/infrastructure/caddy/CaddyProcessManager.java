package com.aresstack.webserver.infrastructure.caddy;

import com.aresstack.webserver.application.CaddyAdmin;
import com.aresstack.webserver.application.CaddyRuntime;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;

/**
 * Kapselt den Caddy-Prozess vollständig; außerhalb dieses Adapters existieren
 * keine ProcessBuilder- oder ProcessHandle-Referenzen.
 *
 * Der Serverzustand wird nie ausschließlich aus dem lokal gehaltenen
 * Process-Objekt abgeleitet: Antwortet die Admin-API auf 127.0.0.1:2019
 * bereits (z.B. Caddy aus einem früheren Anwendungslauf), wird diese Instanz
 * als laufender Server adoptiert — es wird niemals ein zweiter Caddy
 * gestartet, der dann am belegten Admin-Port stirbt.
 */
public class CaddyProcessManager implements CaddyRuntime {

    private static final Duration START_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(10);

    private final RuntimeDirectories directories;
    private final CaddyAdmin admin;

    private Process process;

    public CaddyProcessManager(RuntimeDirectories directories, CaddyAdmin admin) {
        this.directories = directories;
        this.admin = admin;
    }

    @Override
    public synchronized void start() {
        if (process != null && process.isAlive()) {
            return;
        }
        directories.ensureExist();
        if (admin.isReachable()) {
            // Adoptieren: ein Caddy läuft bereits (z.B. aus einem früheren
            // Anwendungslauf). Statt einen zweiten Prozess zu starten, wird
            // die aktuelle Konfiguration atomar in die Instanz geladen.
            adoptRunningInstance();
            return;
        }
        ProcessBuilder builder = new ProcessBuilder(
                directories.caddyBinary().toString(),
                "run",
                "--config", directories.generatedCaddyfile().toString(),
                "--adapter", "caddyfile");
        // Zertifikate, Keys und ACME-Daten landen deterministisch in data/caddy
        // statt im benutzerabhängigen Default-Verzeichnis.
        builder.environment().put("XDG_DATA_HOME", directories.caddyData().getParent().toString());
        builder.environment().put("XDG_CONFIG_HOME", directories.caddyData().getParent().toString());
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(
                directories.logs().resolve("caddy.log").toFile()));
        builder.redirectErrorStream(true);
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot start caddy: " + directories.caddyBinary(), e);
        }
        waitUntilAdminReachable();
    }

    private void adoptRunningInstance() {
        try {
            String caddyfile = Files.readString(
                    directories.generatedCaddyfile(), StandardCharsets.UTF_8);
            admin.loadCaddyfile(caddyfile);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Cannot apply configuration to running caddy: "
                            + directories.generatedCaddyfile(), e);
        }
    }

    @Override
    public synchronized void stop() {
        boolean ownedProcessAlive = process != null && process.isAlive();
        if (!ownedProcessAlive && !admin.isReachable()) {
            process = null;
            return;
        }
        // Regulärer Shutdown über die Admin API — funktioniert auch für eine
        // adoptierte Instanz ohne eigenes Process-Objekt.
        admin.requestShutdown();
        if (ownedProcessAlive) {
            ProcessHandle handle = process.toHandle();
            waitWhile(handle::isAlive, STOP_TIMEOUT);
            if (handle.isAlive()) {
                process.destroy();
                waitWhile(handle::isAlive, STOP_TIMEOUT);
            }
            if (handle.isAlive()) {
                process.destroyForcibly();
            }
        } else {
            // Eskalation per ProcessHandle gibt es nur für eigene Prozesse;
            // hier bleibt der Nachweis über die Admin-API.
            waitWhile(admin::isReachable, STOP_TIMEOUT);
        }
        process = null;
    }

    @Override
    public synchronized boolean isRunning() {
        // Eigener Prozess ODER eine erreichbare Admin-API — der Zustand darf
        // einen Anwendungsneustart überleben.
        return (process != null && process.isAlive()) || admin.isReachable();
    }

    private void waitUntilAdminReachable() {
        Instant deadline = Instant.now().plus(START_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            if (!process.isAlive()) {
                throw new IllegalStateException(
                        "Caddy exited during startup, see " + directories.logs().resolve("caddy.log"));
            }
            if (admin.isReachable()) {
                return;
            }
            sleep(200);
        }
        throw new IllegalStateException("Caddy admin API not reachable within " + START_TIMEOUT);
    }

    private static void waitWhile(java.util.function.BooleanSupplier condition, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (condition.getAsBoolean() && Instant.now().isBefore(deadline)) {
            sleep(100);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for caddy", e);
        }
    }
}

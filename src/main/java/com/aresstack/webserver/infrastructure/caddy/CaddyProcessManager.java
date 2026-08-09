package com.aresstack.webserver.infrastructure.caddy;

import com.aresstack.webserver.application.CaddyAdmin;
import com.aresstack.webserver.application.CaddyRuntime;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;

/**
 * Kapselt den Caddy-Prozess vollständig; außerhalb dieses Adapters existieren
 * keine ProcessBuilder- oder ProcessHandle-Referenzen.
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
        if (isRunning()) {
            return;
        }
        directories.ensureExist();
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

    @Override
    public synchronized void stop() {
        if (!isRunning()) {
            return;
        }
        // Regulärer Shutdown über die Admin API, Prozessabbruch nur als Fallback.
        admin.requestShutdown();
        ProcessHandle handle = process.toHandle();
        Instant deadline = Instant.now().plus(STOP_TIMEOUT);
        while (handle.isAlive() && Instant.now().isBefore(deadline)) {
            sleep(100);
        }
        if (handle.isAlive()) {
            process.destroy();
            deadline = Instant.now().plus(STOP_TIMEOUT);
            while (handle.isAlive() && Instant.now().isBefore(deadline)) {
                sleep(100);
            }
        }
        if (handle.isAlive()) {
            process.destroyForcibly();
        }
        process = null;
    }

    @Override
    public synchronized boolean isRunning() {
        return process != null && process.isAlive();
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

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for caddy", e);
        }
    }
}

package com.aresstack.webserver.infrastructure.caddy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Verzeichnislayout der Installation. {@code data/caddy} enthält Zertifikate
 * und ACME-Daten und darf bei Updates niemals gelöscht werden.
 */
public record RuntimeDirectories(Path root) {

    public Path configFile() {
        return root.resolve("config").resolve("webserver.json");
    }

    public Path generatedCaddyfile() {
        return root.resolve("generated").resolve("Caddyfile");
    }

    public Path caddyData() {
        return root.resolve("data").resolve("caddy");
    }

    public Path logs() {
        return root.resolve("logs");
    }

    public Path caddyBinary() {
        String binary = System.getProperty("os.name").toLowerCase().contains("win") ? "caddy.exe" : "caddy";
        // Entwicklungslayout (gradlew downloadCaddy) vor Release-Layout (bin/).
        Path development = root.resolve("runtime").resolve("caddy").resolve("bin").resolve(binary);
        return Files.exists(development) ? development : root.resolve("bin").resolve(binary);
    }

    public void ensureExist() {
        try {
            Files.createDirectories(configFile().getParent());
            Files.createDirectories(generatedCaddyfile().getParent());
            Files.createDirectories(caddyData());
            Files.createDirectories(logs());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create runtime directories under " + root, e);
        }
    }
}

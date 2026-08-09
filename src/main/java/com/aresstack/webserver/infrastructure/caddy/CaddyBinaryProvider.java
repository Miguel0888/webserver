package com.aresstack.webserver.infrastructure.caddy;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Stellt das Caddy-Binary bereit. Im Fat-JAR-Release liegt es als Ressource
 * im Archiv und wird beim ersten Start nach runtime/caddy/bin entpackt;
 * im Entwicklungs- und ZIP-Layout existiert es bereits auf der Platte.
 */
public final class CaddyBinaryProvider {

    private CaddyBinaryProvider() {
    }

    public static void ensureAvailable(RuntimeDirectories directories) {
        if (Files.exists(directories.caddyBinary())) {
            return;
        }
        String binaryName = directories.caddyBinary().getFileName().toString();
        try (InputStream bundled = CaddyBinaryProvider.class.getResourceAsStream("/caddy/" + binaryName)) {
            if (bundled == null) {
                // Kein gebündeltes Binary (z.B. Dev-Build) — Aufrufer meldet
                // das Fehlen mit seiner eigenen Anleitung.
                return;
            }
            Path target = directories.root()
                    .resolve("runtime").resolve("caddy").resolve("bin").resolve(binaryName);
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), "caddy", ".tmp");
            Files.copy(bundled, temp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            target.toFile().setExecutable(true);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot extract bundled caddy binary", e);
        }
    }
}

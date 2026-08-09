package com.aresstack.webserver.infrastructure.caddy;

import com.aresstack.webserver.application.CaddyConfigurationWriter;
import com.aresstack.webserver.application.ConfigurationApplyException;
import com.aresstack.webserver.domain.WebServerConfiguration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

/**
 * Implementiert Rendern, Persistieren nach generated/ und Validierung über
 * {@code caddy validate}.
 */
public class CaddyConfigurationAdapter implements CaddyConfigurationWriter {

    private final CaddyfileRenderer renderer = new CaddyfileRenderer();
    private final RuntimeDirectories directories;

    public CaddyConfigurationAdapter(RuntimeDirectories directories) {
        this.directories = directories;
    }

    @Override
    public String render(WebServerConfiguration configuration) {
        return renderer.render(configuration);
    }

    @Override
    public void persistGenerated(String caddyfileContent) {
        Path target = directories.generatedCaddyfile();
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), "Caddyfile", ".tmp");
            Files.writeString(temp, caddyfileContent, StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + target, e);
        }
    }

    @Override
    public void validateWithCaddy(String caddyfileContent) {
        try {
            Path temp = Files.createTempFile("webserver-validate", ".caddyfile");
            try {
                Files.writeString(temp, caddyfileContent, StandardCharsets.UTF_8);
                Process process = new ProcessBuilder(
                        directories.caddyBinary().toString(),
                        "validate", "--config", temp.toString(), "--adapter", "caddyfile")
                        .redirectErrorStream(true)
                        .start();
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                if (!process.waitFor(30, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    throw new ConfigurationApplyException("caddy validate timed out");
                }
                if (process.exitValue() != 0) {
                    throw new ConfigurationApplyException("caddy validate failed:\n" + output);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new ConfigurationApplyException("Cannot run caddy validate", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConfigurationApplyException("caddy validate interrupted", e);
        }
    }
}

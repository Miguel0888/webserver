package com.aresstack.webserver.infrastructure.caddy;

import com.aresstack.webserver.application.CaddyAdmin;
import com.aresstack.webserver.application.ConfigurationApplyException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * HTTP-Client für die Caddy Admin API. Ausschließlich Loopback —
 * die Admin API wird niemals ins LAN oder Internet exponiert.
 */
public class CaddyAdminClient implements CaddyAdmin {

    private final URI baseUri;
    private final HttpClient client;

    public CaddyAdminClient() {
        this(URI.create("http://" + CaddyfileRenderer.ADMIN_LISTEN));
    }

    public CaddyAdminClient(URI baseUri) {
        this.baseUri = baseUri;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    @Override
    public void loadCaddyfile(String caddyfileContent) {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/load"))
                .header("Content-Type", "text/caddyfile")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(caddyfileContent, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() != 200) {
            throw new ConfigurationApplyException(
                    "Caddy rejected configuration (HTTP " + response.statusCode() + "): " + response.body());
        }
    }

    @Override
    public void requestShutdown() {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/stop"))
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        try {
            client.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (IOException e) {
            // Verbindungsabbruch beim Stop ist erwartbar — der Prozess beendet sich.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isReachable() {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/config/"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();
        try {
            return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ConfigurationApplyException("Caddy admin API not reachable: " + request.uri(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConfigurationApplyException("Interrupted while calling admin API", e);
        }
    }
}

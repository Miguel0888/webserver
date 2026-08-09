package com.aresstack.webserver.ui;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Optionaler DynDNS-Client nach Router-Prinzip: eine Update-URL mit den
 * Platzhaltern {@code <domain> <username> <password> <ipaddr> <ip6addr>},
 * die bei Adressänderung aufgerufen wird. Ist die Funktion in den
 * Einstellungen deaktiviert, passiert hier nichts — z.B. wenn der Router
 * das Update bereits übernimmt.
 */
public final class DynDnsClient {

    public record Result(boolean success, String message) {
    }

    private static volatile String lastSentIp;
    private static volatile String lastUpdateInfo = "—";

    private DynDnsClient() {
    }

    public static String lastUpdateInfo() {
        return lastUpdateInfo;
    }

    /** Führt sofort ein Update aus (auch für "Test configuration"). */
    public static Result updateNow() {
        String template = AppPreferences.dynDnsUpdateUrl().trim();
        if (template.isEmpty()) {
            return new Result(false, "No update URL configured.");
        }
        String ip = ConnectivityCheck.publicAddress();
        if (ip == null) {
            return new Result(false, "The public IPv4 address could not be determined.");
        }
        String url = template
                .replace("<domain>", encode(AppPreferences.dynDnsDomain()))
                .replace("<username>", encode(AppPreferences.dynDnsUsername()))
                .replace("<password>", encode(AppPreferences.dynDnsPassword()))
                .replace("<ipaddr>", ip)
                .replace("<ip6addr>", "");
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET();
            String username = AppPreferences.dynDnsUsername();
            if (!username.isBlank()) {
                String credentials = username + ":" + AppPreferences.dynDnsPassword();
                request.header("Authorization", "Basic " + Base64.getEncoder()
                        .encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
            }
            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build()
                    .send(request.build(), HttpResponse.BodyHandlers.ofString());
            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
            lastUpdateInfo = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                    + " · " + ip + " · HTTP " + response.statusCode();
            if (success) {
                lastSentIp = ip;
            }
            String body = response.body() == null ? "" : response.body().trim();
            return new Result(success, "HTTP " + response.statusCode()
                    + (body.isEmpty() ? "" : " — " + body.substring(0, Math.min(body.length(), 200))));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(false, "Interrupted.");
        } catch (Exception e) {
            return new Result(false, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    /** Hintergrundlauf: nur updaten, wenn sich die öffentliche Adresse geändert hat. */
    public static void updateIfAddressChanged(UserLog log) {
        if (!AppPreferences.dynDnsEnabled()) {
            return;
        }
        String ip = ConnectivityCheck.publicAddress();
        if (ip == null || ip.equals(lastSentIp)) {
            return;
        }
        Result result = updateNow();
        log.info(result.success()
                ? "Dynamic DNS updated to " + ip
                : "Dynamic DNS update failed: " + result.message());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

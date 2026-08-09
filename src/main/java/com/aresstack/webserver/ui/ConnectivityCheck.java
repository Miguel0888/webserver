package com.aresstack.webserver.ui;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Prüft, ob DNS und Ports einer Domain zur aktuellen Serverumgebung passen.
 * Die öffentliche Adresse wird erst auf ausdrückliche Benutzeraktion über
 * einen externen Dienst ermittelt, nie automatisch beim Start.
 */
public final class ConnectivityCheck {

    private static final List<String> PUBLIC_IP_SERVICES = List.of(
            "https://api.ipify.org",
            "https://checkip.amazonaws.com",
            "https://ifconfig.me/ip");

    /**
     * {@code dnsMatches} und die public-Erreichbarkeiten sind {@code null},
     * wenn sie nicht ermittelt werden konnten (z.B. keine öffentliche
     * Adresse bestimmbar oder Router ohne NAT-Hairpinning).
     */
    public record Result(
            String host,
            List<String> dnsAddresses,
            String publicAddress,
            Boolean dnsMatches,
            boolean serverListensHttp,
            boolean serverListensHttps,
            Boolean publicHttpReachable,
            Boolean publicHttpsReachable) {
    }

    private ConnectivityCheck() {
    }

    public static Result run(String host, int httpPort, int httpsPort) {
        List<String> dnsAddresses = resolve(host);
        String publicAddress = publicAddress();

        Boolean dnsMatches = publicAddress == null || dnsAddresses.isEmpty()
                ? null
                : dnsAddresses.contains(publicAddress);

        boolean listensHttp = tcpReachable("127.0.0.1", httpPort);
        boolean listensHttps = tcpReachable("127.0.0.1", httpsPort);

        // Verbindung zur eigenen öffentlichen Adresse funktioniert nur mit
        // NAT-Hairpinning — Fehlschlag heißt deshalb "unbekannt", nicht "zu".
        Boolean publicHttp = null;
        Boolean publicHttps = null;
        if (publicAddress != null) {
            publicHttp = tcpReachable(publicAddress, httpPort) ? Boolean.TRUE : null;
            publicHttps = tcpReachable(publicAddress, httpsPort) ? Boolean.TRUE : null;
        }
        return new Result(host, dnsAddresses, publicAddress, dnsMatches,
                listensHttp, listensHttps, publicHttp, publicHttps);
    }

    /** DNS-Auflösung eines Hosts; leere Liste, wenn kein Eintrag existiert. */
    public static List<String> resolve(String host) {
        try {
            return Arrays.stream(InetAddress.getAllByName(host))
                    .map(InetAddress::getHostAddress)
                    .toList();
        } catch (UnknownHostException e) {
            return List.of();
        }
    }

    /** Öffentliche Adresse dieses Anschlusses; {@code null} wenn nicht ermittelbar. */
    public static String publicAddress() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .build();
        for (String service : PUBLIC_IP_SERVICES) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(service))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();
                HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());
                String body = response.body().trim();
                if (response.statusCode() == 200 && !body.isEmpty() && body.length() < 64) {
                    return body;
                }
            } catch (Exception e) {
                // Nächsten Dienst versuchen.
            }
        }
        return null;
    }

    private static boolean tcpReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

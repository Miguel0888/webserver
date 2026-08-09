package com.aresstack.webserver.integration;

import com.aresstack.webserver.application.ApplyConfiguration;
import com.aresstack.webserver.application.ConfigurationApplyException;
import com.aresstack.webserver.application.ConfigurationRepository;
import com.aresstack.webserver.domain.AcmeConfiguration;
import com.aresstack.webserver.domain.DomainName;
import com.aresstack.webserver.domain.Route;
import com.aresstack.webserver.domain.Site;
import com.aresstack.webserver.domain.Upstream;
import com.aresstack.webserver.domain.WebServerConfiguration;
import com.aresstack.webserver.infrastructure.caddy.CaddyAdminClient;
import com.aresstack.webserver.infrastructure.caddy.CaddyConfigurationAdapter;
import com.aresstack.webserver.infrastructure.caddy.CaddyProcessManager;
import com.aresstack.webserver.infrastructure.caddy.CaddyfileRenderer;
import com.aresstack.webserver.infrastructure.caddy.RuntimeDirectories;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Caddy-in-the-loop: startet den echten Caddy-Prozess (HTTP-only auf
 * zufälligen Loopback-Ports) gegen Fake-Backends und prüft die
 * Routing-Semantik der generierten Konfiguration.
 *
 * Benötigt das per {@code gradlew downloadCaddy} geladene Binary,
 * sonst wird die Klasse übersprungen. Kein ACME, kein DNS, keine
 * privilegierten Ports.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CaddyRoutingIntegrationTest {

    private static final String DOMAIN = "aresstack.de";

    private Path root;
    private RuntimeDirectories directories;
    private CaddyAdminClient admin;
    private CaddyProcessManager runtime;
    private CaddyConfigurationAdapter writer;
    private ApplyConfiguration applyConfiguration;

    private HttpServer backendDefault;
    private HttpServer backendApi;
    private HttpServer backendA;
    private HttpServer backendB;
    private ServerSocket wsBackend;
    private Thread wsThread;

    private int httpPort;
    private int adminPort;
    private int unusedPort;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    static class InMemoryRepository implements ConfigurationRepository {
        WebServerConfiguration saved;

        @Override
        public WebServerConfiguration load() {
            return saved;
        }

        @Override
        public void save(WebServerConfiguration configuration) {
            saved = configuration;
        }
    }

    @BeforeAll
    void setUp() throws Exception {
        Path binary = Path.of(System.getProperty("caddy.binary", ""));
        assumeTrue(Files.exists(binary), "Caddy binary missing — run: gradlew downloadCaddy");

        root = Files.createTempDirectory("webserver-it");
        directories = new RuntimeDirectories(root);
        directories.ensureExist();
        Files.createDirectories(directories.caddyBinary().getParent());
        Files.copy(binary, directories.caddyBinary());

        backendDefault = startBackend("default");
        backendApi = startBackend("api");
        backendA = startBackend("A");
        backendB = startBackend("B");
        wsBackend = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        wsThread = startWebSocketBackend(wsBackend);

        httpPort = freePort();
        adminPort = freePort();
        unusedPort = freePort();

        CaddyfileRenderer renderer =
                new CaddyfileRenderer(CaddyfileRenderer.Options.localHttp(adminPort, httpPort));
        writer = new CaddyConfigurationAdapter(directories, renderer);
        admin = new CaddyAdminClient(URI.create("http://127.0.0.1:" + adminPort));
        runtime = new CaddyProcessManager(directories, admin);
        applyConfiguration = new ApplyConfiguration(writer, admin, new InMemoryRepository());

        writer.persistGenerated(writer.render(configuration(port(backendA))));
        runtime.start();
    }

    @AfterAll
    void tearDown() {
        if (runtime != null && runtime.isRunning()) {
            runtime.stop();
        }
        // Arrays.asList statt List.of: bei übersprungenem Setup sind die
        // Backends null.
        for (HttpServer server : java.util.Arrays.asList(backendDefault, backendApi, backendA, backendB)) {
            if (server != null) {
                server.stop(0);
            }
        }
        try {
            if (wsBackend != null) {
                wsBackend.close();
            }
        } catch (IOException ignored) {
        }
    }

    private WebServerConfiguration configuration(int askaiPort) {
        return new WebServerConfiguration(
                new DomainName(DOMAIN),
                AcmeConfiguration.letsEncrypt("admin@" + DOMAIN),
                localUpstream(port(backendDefault)),
                List.of(
                        new Site(new DomainName(DOMAIN), Optional.empty(),
                                List.of(new Route("/api/*", localUpstream(port(backendApi))))),
                        Site.of("askai." + DOMAIN, "http://127.0.0.1:" + askaiPort),
                        Site.of("plain." + DOMAIN),
                        Site.of("down." + DOMAIN, "http://127.0.0.1:" + unusedPort),
                        Site.of("ws." + DOMAIN, "http://127.0.0.1:" + wsBackend.getLocalPort())));
    }

    @Test
    @Order(1)
    void rootDefaultRoutesToLocalhostBackend() throws Exception {
        assertEquals("default", get(DOMAIN, "/").body());
    }

    @Test
    @Order(2)
    void subdomainRoutesToConfiguredBackend() throws Exception {
        assertEquals("A", get("askai." + DOMAIN, "/").body());
    }

    @Test
    @Order(3)
    void subdomainWithoutUpstreamFallsBackToDefault() throws Exception {
        assertEquals("default", get("plain." + DOMAIN, "/").body());
    }

    @Test
    @Order(4)
    void pathMatcherRoutesToApiBackend() throws Exception {
        assertEquals("api", get(DOMAIN, "/api/foo").body());
    }

    @Test
    @Order(5)
    void unmatchedPathFallsBackToDefault() throws Exception {
        assertEquals("default", get(DOMAIN, "/foo").body());
    }

    @Test
    @Order(6)
    void invalidReloadKeepsOldConfigurationActive() throws Exception {
        assertThrows(ConfigurationApplyException.class,
                () -> admin.loadCaddyfile("this is {{{ not a caddyfile"));
        assertEquals("A", get("askai." + DOMAIN, "/").body());
    }

    @Test
    @Order(7)
    void unreachableBackendYieldsProxyErrorAndCaddyStaysUp() throws Exception {
        assertEquals(502, get("down." + DOMAIN, "/").statusCode());
        assertTrue(runtime.isRunning());
        assertEquals("default", get(DOMAIN, "/").body());
    }

    @Test
    @Order(8)
    void webSocketUpgradeIsProxiedThrough() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", httpPort)) {
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            out.write(("""
                    GET /ws HTTP/1.1\r
                    Host: ws.%s\r
                    Connection: Upgrade\r
                    Upgrade: websocket\r
                    Sec-WebSocket-Version: 13\r
                    Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r
                    \r
                    """.formatted(DOMAIN)).getBytes(StandardCharsets.US_ASCII));
            out.flush();

            String received = readUntil(socket.getInputStream(), "hello-ws");
            assertTrue(received.startsWith("HTTP/1.1 101"),
                    "Expected 101 Switching Protocols, got:\n" + received);
            assertTrue(received.endsWith("hello-ws"), "Tunnel payload missing:\n" + received);
        }
    }

    @Test
    @Order(9)
    void configurationSwitchTakesEffectWithoutRestart() throws Exception {
        assertEquals("A", get("askai." + DOMAIN, "/").body());

        applyConfiguration.apply(configuration(port(backendB)));

        assertEquals("B", get("askai." + DOMAIN, "/").body());
        assertEquals("default", get(DOMAIN, "/").body());
    }

    @Test
    @Order(10)
    void restartReusesPersistentCaddyStorage() throws Exception {
        Path instanceUuid = directories.caddyData().resolve("instance.uuid");
        assertTrue(Files.exists(instanceUuid),
                "Caddy storage not under data/caddy — XDG_DATA_HOME not effective");
        String uuidBeforeRestart = Files.readString(instanceUuid);

        runtime.stop();
        assertTrue(!runtime.isRunning());
        runtime.start();

        assertEquals(uuidBeforeRestart, Files.readString(instanceUuid),
                "Restart created a fresh storage instead of reusing data/caddy");
        assertEquals("B", get("askai." + DOMAIN, "/").body());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private HttpResponse<String> get(String host, String path) throws IOException, InterruptedException {
        // Anfrage geht an Caddys Loopback-Port; das Routing entscheidet
        // ausschließlich der Host-Header — wie in Produktion.
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + httpPort + path))
                .header("Host", host)
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpServer startBackend(String label) throws IOException {
        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = label.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        return server;
    }

    private static int port(HttpServer server) {
        return server.getAddress().getPort();
    }

    private static Upstream localUpstream(int port) {
        return Upstream.parse("http://127.0.0.1:" + port);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    /**
     * Minimaler WebSocket-Endpunkt: beantwortet den Upgrade-Handshake mit 101
     * und schickt danach Rohbytes durch den Tunnel.
     */
    private static Thread startWebSocketBackend(ServerSocket serverSocket) {
        Thread thread = new Thread(() -> {
            while (!serverSocket.isClosed()) {
                try (Socket socket = serverSocket.accept()) {
                    socket.setSoTimeout(5000);
                    readUntil(socket.getInputStream(), "\r\n\r\n");
                    OutputStream out = socket.getOutputStream();
                    out.write(("HTTP/1.1 101 Switching Protocols\r\n"
                            + "Upgrade: websocket\r\n"
                            + "Connection: Upgrade\r\n"
                            + "Sec-WebSocket-Accept: integration-test\r\n"
                            + "\r\n").getBytes(StandardCharsets.US_ASCII));
                    out.write("hello-ws".getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                    // Verbindung offen halten, bis der Client sie schließt.
                    socket.getInputStream().read();
                } catch (IOException e) {
                    // ServerSocket geschlossen oder Client weg — Schleife prüft isClosed().
                }
            }
        }, "ws-backend");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static String readUntil(InputStream in, String terminator) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            buffer.write(c);
            if (buffer.toString(StandardCharsets.US_ASCII).endsWith(terminator)) {
                break;
            }
        }
        return buffer.toString(StandardCharsets.US_ASCII);
    }
}

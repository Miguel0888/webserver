package com.aresstack.webserver.launcher;

import com.aresstack.webserver.application.ApplyConfiguration;
import com.aresstack.webserver.application.StartWebServer;
import com.aresstack.webserver.application.StopWebServer;
import com.aresstack.webserver.domain.WebServerConfiguration;
import com.aresstack.webserver.infrastructure.caddy.CaddyAdminClient;
import com.aresstack.webserver.infrastructure.caddy.CaddyConfigurationAdapter;
import com.aresstack.webserver.infrastructure.caddy.CaddyProcessManager;
import com.aresstack.webserver.infrastructure.caddy.RuntimeDirectories;
import com.aresstack.webserver.infrastructure.configuration.JsonConfigurationRepository;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Einstiegspunkt: verdrahtet die Adapter, startet Caddy mit der persistierten
 * Konfiguration und wendet Änderungen an webserver.json per SIGTERM-freiem
 * Reload an, solange der Prozess läuft.
 */
public final class WebServerMain {

    private WebServerMain() {
    }

    public static void main(String[] args) throws Exception {
        // Priorität: explizites Argument, dann das vom Startskript gesetzte
        // APP_HOME (-Dwebserver.root), sonst das Arbeitsverzeichnis.
        Path root = args.length > 0
                ? Path.of(args[0]).toAbsolutePath().normalize()
                : Path.of(System.getProperty("webserver.root", ".")).toAbsolutePath().normalize();
        RuntimeDirectories directories = new RuntimeDirectories(root);
        directories.ensureExist();

        if (!Files.exists(directories.configFile())) {
            System.err.println("Missing configuration: " + directories.configFile());
            System.err.println("Create it first, e.g. based on config/webserver.example.json");
            System.exit(1);
        }
        if (!Files.exists(directories.caddyBinary())) {
            System.err.println("Missing caddy binary: " + directories.caddyBinary());
            System.err.println("Run: gradlew downloadCaddy");
            System.exit(1);
        }

        JsonConfigurationRepository repository = new JsonConfigurationRepository(directories.configFile());
        CaddyConfigurationAdapter configurationAdapter = new CaddyConfigurationAdapter(directories);
        CaddyAdminClient admin = new CaddyAdminClient();
        CaddyProcessManager runtime = new CaddyProcessManager(directories, admin);

        StartWebServer startWebServer = new StartWebServer(repository, configurationAdapter, runtime);
        StopWebServer stopWebServer = new StopWebServer(runtime);
        ApplyConfiguration applyConfiguration =
                new ApplyConfiguration(configurationAdapter, admin, repository);

        WebServerConfiguration configuration = startWebServer.start();
        System.out.println("Caddy started for " + configuration.domain()
                + " with " + configuration.sites().size() + " site(s)");

        Runtime.getRuntime().addShutdownHook(new Thread(stopWebServer::stop, "caddy-shutdown"));

        watchConfiguration(directories, repository, applyConfiguration);
    }

    private static void watchConfiguration(RuntimeDirectories directories,
                                           JsonConfigurationRepository repository,
                                           ApplyConfiguration applyConfiguration) throws Exception {
        Path configDir = directories.configFile().getParent();
        Path configName = directories.configFile().getFileName();
        try (var watchService = configDir.getFileSystem().newWatchService()) {
            configDir.register(watchService,
                    java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY,
                    java.nio.file.StandardWatchEventKinds.ENTRY_CREATE);
            while (true) {
                var key = watchService.take();
                boolean changed = key.pollEvents().stream()
                        .anyMatch(event -> configName.equals(event.context()));
                key.reset();
                if (!changed) {
                    continue;
                }
                try {
                    applyConfiguration.apply(repository.load());
                    System.out.println("Configuration reloaded");
                } catch (RuntimeException e) {
                    // Alte Konfiguration bleibt aktiv — nur melden, nicht abbrechen.
                    System.err.println("Configuration rejected: " + e.getMessage());
                }
            }
        }
    }
}

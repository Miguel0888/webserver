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
import com.aresstack.webserver.ui.FriendlyErrors;
import com.aresstack.webserver.ui.MainWindow;
import com.aresstack.webserver.ui.SetupDialog;
import com.aresstack.webserver.ui.UserLog;
import com.aresstack.webserver.ui.WebServerController;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Einstiegspunkt. Standard ist die grafische Anwendung; {@code --headless}
 * startet den bisherigen Servermodus mit Konfigurations-Watcher.
 */
public final class WebServerMain {

    private WebServerMain() {
    }

    public static void main(String[] args) throws Exception {
        Path root = resolveRoot(args);
        boolean headless = Arrays.asList(args).contains("--headless")
                || GraphicsEnvironment.isHeadless();
        if (headless) {
            runHeadless(root);
        } else {
            SwingUtilities.invokeLater(() -> launchGui(root));
        }
    }

    private static Path resolveRoot(String[] args) {
        // Priorität: explizites Argument, dann das vom Startskript gesetzte
        // APP_HOME (-Dwebserver.root), sonst das Arbeitsverzeichnis.
        for (String arg : args) {
            if (!arg.startsWith("--")) {
                return Path.of(arg).toAbsolutePath().normalize();
            }
        }
        return Path.of(System.getProperty("webserver.root", ".")).toAbsolutePath().normalize();
    }

    // ------------------------------------------------------------------
    // GUI
    // ------------------------------------------------------------------

    private static void launchGui(Path root) {
        RuntimeDirectories directories = new RuntimeDirectories(root);
        directories.ensureExist();

        if (!Files.exists(directories.caddyBinary())) {
            JOptionPane.showMessageDialog(null,
                    "The webserver engine is missing:\n" + directories.caddyBinary()
                            + "\n\nReinstall the application or run: gradlew downloadCaddy",
                    "AresStack Webserver", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        JsonConfigurationRepository repository =
                new JsonConfigurationRepository(directories.configFile());
        if (!Files.exists(directories.configFile())) {
            // Erster Start: Einrichtung statt Fehlermeldung.
            WebServerConfiguration initial = SetupDialog.showSetup();
            if (initial == null) {
                System.exit(0);
            }
            repository.save(initial);
        }

        UserLog log = new UserLog();
        WebServerController controller = new WebServerController(directories, log);
        MainWindow window = new MainWindow(controller, log);
        window.setVisible(true);

        // Server automatisch starten; Fehler landen verständlich im Dialog.
        new Thread(() -> {
            try {
                controller.start();
            } catch (RuntimeException e) {
                SwingUtilities.invokeLater(() ->
                        FriendlyErrors.show(window, "Start Server", e));
            }
        }, "server-autostart").start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (controller.isRunning()) {
                controller.stop();
            }
        }, "caddy-shutdown"));
    }

    // ------------------------------------------------------------------
    // Headless (Serverbetrieb)
    // ------------------------------------------------------------------

    private static void runHeadless(Path root) throws Exception {
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

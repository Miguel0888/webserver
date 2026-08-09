package com.aresstack.webserver.ui;

import com.aresstack.webserver.application.ApplyConfiguration;
import com.aresstack.webserver.application.StartWebServer;
import com.aresstack.webserver.domain.AcmeConfiguration;
import com.aresstack.webserver.domain.DomainName;
import com.aresstack.webserver.domain.Site;
import com.aresstack.webserver.domain.WebServerConfiguration;
import com.aresstack.webserver.infrastructure.caddy.CaddyAdminClient;
import com.aresstack.webserver.infrastructure.caddy.CaddyConfigurationAdapter;
import com.aresstack.webserver.infrastructure.caddy.CaddyProcessManager;
import com.aresstack.webserver.infrastructure.caddy.RuntimeDirectories;
import com.aresstack.webserver.infrastructure.configuration.JsonConfigurationRepository;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Vermittelt zwischen der Oberfläche und den Use Cases. Alle Änderungen
 * laufen über den atomaren Apply-Pfad; eine ungültige Konfiguration erreicht
 * niemals den laufenden Server.
 */
public class WebServerController {

    private final RuntimeDirectories directories;
    private final JsonConfigurationRepository repository;
    private final CaddyConfigurationAdapter writer;
    private final CaddyProcessManager runtime;
    private final StartWebServer startWebServer;
    private final ApplyConfiguration applyConfiguration;
    private final UserLog log;
    private final List<Runnable> listeners = new ArrayList<>();

    private WebServerConfiguration current;

    public WebServerController(RuntimeDirectories directories, UserLog log) {
        this.directories = directories;
        this.log = log;
        this.repository = new JsonConfigurationRepository(directories.configFile());
        this.writer = new CaddyConfigurationAdapter(directories);
        CaddyAdminClient admin = new CaddyAdminClient();
        this.runtime = new CaddyProcessManager(directories, admin);
        this.startWebServer = new StartWebServer(repository, writer, runtime);
        this.applyConfiguration = new ApplyConfiguration(writer, admin, repository);
    }

    public RuntimeDirectories directories() {
        return directories;
    }

    public synchronized WebServerConfiguration configuration() {
        if (current == null) {
            current = repository.load();
        }
        return current;
    }

    public boolean isRunning() {
        return runtime.isRunning();
    }

    public synchronized void start() {
        current = startWebServer.start();
        log.info("Server started");
        notifyChanged();
    }

    public synchronized void stop() {
        runtime.stop();
        log.info("Server stopped");
        notifyChanged();
    }

    public synchronized void restart() {
        runtime.stop();
        current = startWebServer.start();
        log.info("Server restarted");
        notifyChanged();
    }

    public synchronized void addSite(Site site) {
        List<Site> sites = new ArrayList<>(configuration().sites());
        sites.add(site);
        applyNew(withSites(sites));
        log.info("Added " + site.host());
        if (site.httpsEnabled()) {
            log.info("Requested HTTPS certificate for " + site.host());
        }
    }

    public synchronized void updateSite(DomainName originalHost, Site updated) {
        List<Site> sites = configuration().sites().stream()
                .map(site -> site.host().equals(originalHost) ? updated : site)
                .collect(Collectors.toList());
        applyNew(withSites(sites));
        log.info("Updated " + updated.host());
    }

    public synchronized void removeSite(DomainName host) {
        List<Site> sites = configuration().sites().stream()
                .filter(site -> !site.host().equals(host))
                .collect(Collectors.toList());
        applyNew(withSites(sites));
        log.info("Removed " + host);
    }

    public synchronized void updateAcmeEmail(String email) {
        WebServerConfiguration config = configuration();
        applyNew(new WebServerConfiguration(
                config.domain(),
                new AcmeConfiguration(email, config.acme().ca()),
                config.defaultUpstream(),
                config.sites()));
        log.info("Updated Let's Encrypt account email");
    }

    private WebServerConfiguration withSites(List<Site> sites) {
        WebServerConfiguration config = configuration();
        return new WebServerConfiguration(
                config.domain(), config.acme(), config.defaultUpstream(), sites);
    }

    /**
     * Läuft der Server, wird atomar per Admin-API umgeschaltet; sonst wird
     * nur validiert, gerendert und persistiert.
     */
    private void applyNew(WebServerConfiguration next) {
        next.validate();
        if (runtime.isRunning()) {
            applyConfiguration.apply(next);
        } else {
            String caddyfile = writer.render(next);
            writer.validateWithCaddy(caddyfile);
            writer.persistGenerated(caddyfile);
            repository.save(next);
        }
        current = next;
        notifyChanged();
    }

    public synchronized void addListener(Runnable listener) {
        listeners.add(listener);
    }

    private void notifyChanged() {
        for (Runnable listener : listeners) {
            SwingUtilities.invokeLater(listener);
        }
    }
}

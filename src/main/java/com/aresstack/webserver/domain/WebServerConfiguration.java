package com.aresstack.webserver.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Fachliche Gesamtkonfiguration — die Source of Truth des Systems.
 * Kennt weder Caddy noch JSON noch Prozesse.
 */
public record WebServerConfiguration(
        DomainName domain,
        AcmeConfiguration acme,
        Upstream defaultUpstream,
        List<Site> sites,
        int httpPort,
        int httpsPort) {

    public WebServerConfiguration {
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(acme, "acme");
        Objects.requireNonNull(defaultUpstream, "defaultUpstream");
        Objects.requireNonNull(sites, "sites");
        sites = List.copyOf(sites);
        if (httpPort < 1 || httpPort > 65535 || httpsPort < 1 || httpsPort > 65535) {
            throw new IllegalArgumentException(
                    "Ports out of range: http=" + httpPort + " https=" + httpsPort);
        }
        if (httpPort == httpsPort) {
            throw new IllegalArgumentException("HTTP and HTTPS port must differ: " + httpPort);
        }
    }

    public WebServerConfiguration(DomainName domain, AcmeConfiguration acme,
                                  Upstream defaultUpstream, List<Site> sites) {
        this(domain, acme, defaultUpstream, sites, 80, 443);
    }

    /**
     * Prüft konfigurationsübergreifende Invarianten. Wird vor jedem Rendern
     * und Anwenden aufgerufen.
     */
    public void validate() {
        if (sites.isEmpty()) {
            throw new IllegalArgumentException("Configuration must contain at least one site");
        }
        Set<String> seenHosts = new HashSet<>();
        for (Site site : sites) {
            if (!seenHosts.add(site.host().value())) {
                throw new IllegalArgumentException("Duplicate site host: " + site.host());
            }
            if (!site.host().isSameOrSubdomainOf(domain)) {
                throw new IllegalArgumentException(
                        "Site host " + site.host() + " is not part of domain " + domain);
            }
            Set<String> seenPaths = new HashSet<>();
            for (Route route : site.routes()) {
                if (!seenPaths.add(route.pathMatcher())) {
                    throw new IllegalArgumentException(
                            "Duplicate path matcher " + route.pathMatcher() + " for host " + site.host());
                }
            }
        }
    }
}

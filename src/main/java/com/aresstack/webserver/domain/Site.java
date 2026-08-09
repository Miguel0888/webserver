package com.aresstack.webserver.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Ein veröffentlichter Host. Ohne eigenen Upstream greift der
 * Default-Upstream der Gesamtkonfiguration.
 */
public record Site(DomainName host, Optional<Upstream> upstream, List<Route> routes) {

    public Site {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(upstream, "upstream");
        Objects.requireNonNull(routes, "routes");
        routes = List.copyOf(routes);
    }

    public static Site of(String host) {
        return new Site(new DomainName(host), Optional.empty(), List.of());
    }

    public static Site of(String host, String upstream) {
        return new Site(new DomainName(host), Optional.of(Upstream.parse(upstream)), List.of());
    }

    public Upstream effectiveUpstream(Upstream defaultUpstream) {
        return upstream.orElse(defaultUpstream);
    }
}

package com.aresstack.webserver.domain;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

/**
 * Target eines Reverse-Proxy-Eintrags, z.B. http://192.168.178.30:8080.
 */
public record Upstream(String scheme, String host, int port) {

    public Upstream {
        Objects.requireNonNull(scheme, "scheme");
        Objects.requireNonNull(host, "host");
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("Upstream scheme must be http or https: " + scheme);
        }
        if (host.isBlank()) {
            throw new IllegalArgumentException("Upstream host must not be blank");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Upstream port out of range: " + port);
        }
    }

    public static Upstream parse(String value) {
        Objects.requireNonNull(value, "value");
        URI uri;
        try {
            uri = URI.create(value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid upstream URL: " + value, e);
        }
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalArgumentException("Invalid upstream URL, expected scheme://host[:port]: " + value);
        }
        int port = uri.getPort();
        if (port == -1) {
            port = uri.getScheme().equalsIgnoreCase("https") ? 443 : 80;
        }
        return new Upstream(uri.getScheme(), uri.getHost(), port);
    }

    /** Adresse im Caddy-Format, z.B. {@code 127.0.0.1:8080} oder {@code https://backend:8443}. */
    public String toCaddyAddress() {
        return scheme.equals("https") ? "https://" + host + ":" + port : host + ":" + port;
    }

    @Override
    public String toString() {
        return scheme + "://" + host + ":" + port;
    }
}

package com.aresstack.webserver.domain;

import java.util.Objects;

/**
 * Pfadbasierte Weiterleitung innerhalb einer Site, z.B. /api/* → backend.
 */
public record Route(String pathMatcher, Upstream upstream) {

    public Route {
        Objects.requireNonNull(pathMatcher, "pathMatcher");
        Objects.requireNonNull(upstream, "upstream");
        pathMatcher = pathMatcher.trim();
        if (!pathMatcher.startsWith("/")) {
            throw new IllegalArgumentException("Path matcher must start with '/': " + pathMatcher);
        }
        if (pathMatcher.contains(" ") || pathMatcher.contains("{") || pathMatcher.contains("}")) {
            throw new IllegalArgumentException("Path matcher contains illegal characters: " + pathMatcher);
        }
    }
}

package com.aresstack.webserver.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Ein voll qualifizierter Domainname, z.B. aresstack.de oder git.aresstack.de.
 */
public record DomainName(String value) {

    private static final Pattern VALID = Pattern.compile(
            "^(?=.{1,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$");

    public DomainName {
        Objects.requireNonNull(value, "value");
        value = value.trim().toLowerCase(Locale.ROOT);
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid domain name: " + value);
        }
    }

    public boolean isSameOrSubdomainOf(DomainName other) {
        return value.equals(other.value) || value.endsWith("." + other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}

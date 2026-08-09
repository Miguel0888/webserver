package com.aresstack.webserver.domain;

import java.util.Objects;

/**
 * ACME-Einstellungen; der CA-Endpunkt wird explizit gesetzt statt sich auf
 * Caddy-Defaults zu verlassen.
 */
public record AcmeConfiguration(String email, String ca) {

    public static final String LETS_ENCRYPT_PRODUCTION = "https://acme-v02.api.letsencrypt.org/directory";
    public static final String LETS_ENCRYPT_STAGING = "https://acme-staging-v02.api.letsencrypt.org/directory";

    public AcmeConfiguration {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(ca, "ca");
        if (email.isBlank() || !email.contains("@")) {
            throw new IllegalArgumentException("Invalid ACME email: " + email);
        }
        if (!ca.startsWith("https://")) {
            throw new IllegalArgumentException("ACME CA must be an https URL: " + ca);
        }
    }

    public static AcmeConfiguration letsEncrypt(String email) {
        return new AcmeConfiguration(email, LETS_ENCRYPT_PRODUCTION);
    }
}

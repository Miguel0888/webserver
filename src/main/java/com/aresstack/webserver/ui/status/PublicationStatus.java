package com.aresstack.webserver.ui.status;

import com.aresstack.webserver.ui.CertificateStatusChecker;

/**
 * Fachlicher Zustand einer Veröffentlichung — von der UI angezeigt, von
 * {@link PublicationStatusPresenter} ermittelt. Kein Zustand wird aus einer
 * beliebigen Exception "erraten".
 */
public record PublicationStatus(
        HttpsState https,
        Reachability destination,
        CertificateStatusChecker.CertificateInfo certificate) {

    public enum HttpsState {
        SERVER_STOPPED,
        HTTPS_OFF,
        PORT_UNAVAILABLE,
        SETTING_UP,
        SECURED,
        EXPIRING,
        EXPIRED
    }

    public enum Reachability {
        REACHABLE,
        UNREACHABLE,
        UNKNOWN
    }

    public static PublicationStatus unknown() {
        return new PublicationStatus(HttpsState.SERVER_STOPPED, Reachability.UNKNOWN, null);
    }

    /** Kurze Statuszeile für die Karte. */
    public String cardLine(boolean httpsEnabled) {
        if (destination == Reachability.UNREACHABLE) {
            return "Destination is currently unreachable";
        }
        return switch (https) {
            case SERVER_STOPPED -> "Server is stopped";
            case HTTPS_OFF -> httpsEnabled ? "" : "Served without HTTPS";
            case PORT_UNAVAILABLE -> "HTTPS port is not available";
            case SETTING_UP -> "Setting up… Requesting certificate";
            case SECURED -> "Certificate valid · Automatic renewal";
            case EXPIRING -> "Certificate expires soon · Renewal is automatic";
            case EXPIRED -> "Certificate expired";
        };
    }

    public boolean isSecured() {
        return https == HttpsState.SECURED || https == HttpsState.EXPIRING;
    }
}

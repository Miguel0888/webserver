package com.aresstack.webserver.ui.status;

import com.aresstack.webserver.ui.CertificateStatusChecker;

import java.util.List;

/**
 * Gesamtzustand einer Veröffentlichung als Zustandsmaschine.
 *
 * {@code ACTION_REQUIRED}: der Benutzer muss etwas tun — die konkrete
 * Handlung steht in {@code actionText} und erscheint direkt auf der Karte.
 * {@code SETTING_UP}: die Anwendung arbeitet selbst, keine Aktion nötig.
 * {@code LIVE}: die öffentliche Adresse ist End-to-End funktionsfähig —
 * die Erreichbarkeit des internen Backends allein ist nur ein Teilstatus.
 */
public record PublicationStatus(
        Overall overall,
        String headline,
        String actionText,
        String copyText,
        List<SubStatus> subStatuses,
        CertificateStatusChecker.CertificateInfo certificate) {

    public enum Overall {
        STOPPED,
        ACTION_REQUIRED,
        SETTING_UP,
        LIVE
    }

    public enum SubState {
        OK,
        WARN,
        PENDING,
        OFF
    }

    /** Teilzustand wie "Domain — DNS record missing" oder "Backend — reachable". */
    public record SubStatus(String label, SubState state, String detail) {
    }

    public static PublicationStatus checking() {
        return new PublicationStatus(Overall.SETTING_UP, "Checking…", null, null, List.of(), null);
    }

    public boolean isSecured() {
        return certificate != null && !certificate.isExpired();
    }
}

package com.aresstack.webserver.ui.status;

import com.aresstack.webserver.domain.Site;
import com.aresstack.webserver.domain.Upstream;
import com.aresstack.webserver.domain.WebServerConfiguration;
import com.aresstack.webserver.ui.CertificateStatusChecker;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.Date;

/**
 * Ermittelt den fachlichen Zustand einer Veröffentlichung. Unterscheidet
 * bewusst zwischen "Port zu", "Handshake schlägt fehl, Zertifikat wird noch
 * beschafft" und "Zertifikat vorhanden aber abgelaufen/ablaufend" statt alles
 * auf einen Sammelzustand abzubilden.
 */
public class PublicationStatusPresenter {

    private static final Duration EXPIRY_WARNING = Duration.ofDays(14);

    public PublicationStatus probe(WebServerConfiguration configuration, Site site,
                                   boolean serverRunning) {
        Upstream target = site.effectiveUpstream(configuration.defaultUpstream());
        PublicationStatus.Reachability destination = tcpReachable(target.host(), target.port())
                ? PublicationStatus.Reachability.REACHABLE
                : PublicationStatus.Reachability.UNREACHABLE;

        if (!serverRunning) {
            return new PublicationStatus(
                    PublicationStatus.HttpsState.SERVER_STOPPED, destination, null);
        }
        if (!site.httpsEnabled()) {
            return new PublicationStatus(
                    PublicationStatus.HttpsState.HTTPS_OFF, destination, null);
        }
        if (!tcpReachable("127.0.0.1", configuration.httpsPort())) {
            return new PublicationStatus(
                    PublicationStatus.HttpsState.PORT_UNAVAILABLE, destination, null);
        }
        try {
            CertificateStatusChecker.CertificateInfo certificate =
                    CertificateStatusChecker.fetch(site.host().value(), configuration.httpsPort());
            return new PublicationStatus(certificateState(certificate), destination, certificate);
        } catch (SSLException | ConnectException e) {
            // Port offen, aber kein Zertifikat für diesen Host — Caddy
            // beschafft es noch (oder die Domainvalidierung läuft).
            return new PublicationStatus(
                    PublicationStatus.HttpsState.SETTING_UP, destination, null);
        } catch (Exception e) {
            return new PublicationStatus(
                    PublicationStatus.HttpsState.SETTING_UP, destination, null);
        }
    }

    private static PublicationStatus.HttpsState certificateState(
            CertificateStatusChecker.CertificateInfo certificate) {
        Date now = new Date();
        if (certificate.expires().before(now)) {
            return PublicationStatus.HttpsState.EXPIRED;
        }
        Date warningThreshold = new Date(now.getTime() + EXPIRY_WARNING.toMillis());
        if (certificate.expires().before(warningThreshold)) {
            return PublicationStatus.HttpsState.EXPIRING;
        }
        return PublicationStatus.HttpsState.SECURED;
    }

    private static boolean tcpReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

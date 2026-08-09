package com.aresstack.webserver.ui.status;

import com.aresstack.webserver.domain.Site;
import com.aresstack.webserver.domain.Upstream;
import com.aresstack.webserver.domain.WebServerConfiguration;
import com.aresstack.webserver.ui.CertificateStatusChecker;
import com.aresstack.webserver.ui.ConnectivityCheck;
import com.aresstack.webserver.ui.status.PublicationStatus.Overall;
import com.aresstack.webserver.ui.status.PublicationStatus.SubState;
import com.aresstack.webserver.ui.status.PublicationStatus.SubStatus;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Führt die Veröffentlichung als Prüfkette: DNS → Backend → Ports →
 * Zertifikat. Der erste Schritt, der eine Benutzeraktion erfordert, bestimmt
 * den Gesamtzustand samt konkreter nächster Handlung; "Setting up" erscheint
 * nur, wenn die Anwendung selbst arbeitet und nichts vom Benutzer fehlt.
 */
public class PublicationStatusPresenter {

    private static final Duration EXPIRY_WARNING = Duration.ofDays(14);
    private static final long PUBLIC_IP_TTL_MILLIS = Duration.ofMinutes(10).toMillis();

    private static volatile String cachedPublicIp;
    private static volatile long publicIpFetchedAt;

    public PublicationStatus probe(WebServerConfiguration configuration, Site site,
                                   boolean serverRunning) {
        String host = site.host().value();
        Upstream target = site.effectiveUpstream(configuration.defaultUpstream());
        boolean backendOk = tcpReachable(target.host(), target.port());
        List<String> dns = ConnectivityCheck.resolve(host);
        String publicIp = publicAddressCached();

        SubStatus backendSub = new SubStatus("Backend",
                backendOk ? SubState.OK : SubState.WARN,
                target.host() + ":" + target.port() + (backendOk ? " reachable" : " not reachable"));

        if (!serverRunning) {
            List<SubStatus> subs = List.of(
                    domainSub(dns, publicIp),
                    backendSub,
                    new SubStatus("Webserver", SubState.WARN, "Stopped"),
                    new SubStatus("HTTPS", SubState.OFF, "Waiting for server"));
            return new PublicationStatus(Overall.STOPPED, "Server is stopped",
                    "Start the server to publish this service.", null, subs, null);
        }
        SubStatus webserverSub = new SubStatus("Webserver", SubState.OK, "Running");

        // 1. DNS: ohne Eintrag kann nichts davon jemals erfolgreich werden —
        // das ist eine Benutzeraktion, kein "Setting up". Subdomains werden
        // ausschließlich per CNAME auf die DynDNS-Basisdomain geführt.
        String base = baseDomain(host);
        boolean isSubdomain = !base.equals(host);
        if (dns.isEmpty()) {
            List<SubStatus> subs = List.of(
                    new SubStatus("Domain", SubState.WARN, "DNS record missing"),
                    backendSub, webserverSub,
                    new SubStatus("HTTPS", SubState.PENDING, "Waiting for DNS"));
            if (isSubdomain) {
                return new PublicationStatus(Overall.ACTION_REQUIRED, "DNS record required",
                        "Create this record at your DNS provider:",
                        new PublicationStatus.DnsRecord(firstLabel(host), base), subs, null);
            }
            return new PublicationStatus(Overall.ACTION_REQUIRED, "DNS record required",
                    host + " has no DNS entry.\n"
                            + "This is your base domain — it is kept up to date by Dynamic DNS.\n"
                            + "Check the DynDNS configuration in your router or in Settings.",
                    null, subs, null);
        }
        if (publicIp != null && !dns.contains(publicIp)) {
            List<SubStatus> subs = List.of(
                    new SubStatus("Domain", SubState.WARN, "Points to a different server"),
                    backendSub, webserverSub,
                    new SubStatus("HTTPS", SubState.PENDING, "Waiting for DNS"));
            if (isSubdomain) {
                return new PublicationStatus(Overall.ACTION_REQUIRED,
                        "DNS points to a different server",
                        host + " currently resolves to " + String.join(", ", dns) + ".\n"
                                + "It should follow " + base + " via this record:",
                        new PublicationStatus.DnsRecord(firstLabel(host), base), subs, null);
            }
            return new PublicationStatus(Overall.ACTION_REQUIRED,
                    "DNS points to a different server",
                    host + " resolves to " + String.join(", ", dns) + " instead of this\n"
                            + "connection's address. " + host + " is your Dynamic DNS address —\n"
                            + "check the DynDNS configuration in your router or in Settings.",
                    null, subs, null);
        }
        SubStatus domainSub = new SubStatus("Domain", SubState.OK, dns.get(0));

        // 2. Backend
        if (!backendOk) {
            String action = target.host() + ":" + target.port() + " did not respond.\n"
                    + "Start the service, or correct host and port via Edit.";
            List<SubStatus> subs = List.of(domainSub, backendSub, webserverSub,
                    new SubStatus("HTTPS", SubState.PENDING, "Waiting for backend"));
            return new PublicationStatus(Overall.ACTION_REQUIRED, "Backend not reachable",
                    action, null, subs, null);
        }

        // 3. HTTPS abgeschaltet: ohne ACME gibt es keinen externen Nachweis —
        // Live nur bei positiv verifizierter öffentlicher Erreichbarkeit,
        // sonst ehrlich "Verifying" statt vorschnell "Live".
        if (!site.httpsEnabled()) {
            boolean publicOk = publicIp != null
                    && tcpReachable(publicIp, configuration.httpPort());
            if (publicOk) {
                List<SubStatus> subs = List.of(domainSub, backendSub, webserverSub,
                        new SubStatus("Public endpoint", SubState.OK, "Reachable from the internet"),
                        new SubStatus("HTTPS", SubState.OFF, "Disabled for this service"));
                return new PublicationStatus(Overall.LIVE, "Served over HTTP · HTTPS disabled",
                        null, null, subs, null);
            }
            List<SubStatus> subs = List.of(domainSub, backendSub, webserverSub,
                    new SubStatus("Public endpoint", SubState.PENDING,
                            "Could not be verified from this network"),
                    new SubStatus("HTTPS", SubState.OFF, "Disabled for this service"));
            return new PublicationStatus(Overall.UNVERIFIED, "Public access not verified",
                    "Many routers cannot be reached on their own public address from inside.\n"
                            + "Check from another network (e.g. a phone on mobile data):\n"
                            + "http://" + host, null, subs, null);
        }

        // 4. HTTPS-Port
        if (!tcpReachable("127.0.0.1", configuration.httpsPort())) {
            String action = "Port " + configuration.httpsPort() + " is not accepting connections.\n"
                    + "Another application may be using it.";
            List<SubStatus> subs = List.of(domainSub, backendSub, webserverSub,
                    new SubStatus("HTTPS", SubState.WARN, "Port unavailable"));
            return new PublicationStatus(Overall.ACTION_REQUIRED, "HTTPS port not available",
                    action, null, subs, null);
        }

        // 5. Zertifikat
        try {
            CertificateStatusChecker.CertificateInfo certificate =
                    CertificateStatusChecker.fetch(host, configuration.httpsPort());
            if (certificate.isExpired()) {
                String action = "Check that ports 80 and 443 are forwarded to this computer\n"
                        + "so the certificate can be renewed.";
                List<SubStatus> subs = List.of(domainSub, backendSub, webserverSub,
                        new SubStatus("HTTPS", SubState.WARN, "Certificate expired"));
                return new PublicationStatus(Overall.ACTION_REQUIRED, "Certificate expired",
                        action, null, subs, certificate);
            }
            boolean expiringSoon = certificate.expires()
                    .before(new Date(System.currentTimeMillis() + EXPIRY_WARNING.toMillis()));
            SubStatus httpsSub = new SubStatus("Certificate", SubState.OK,
                    expiringSoon ? "Valid · expires soon, renews automatically" : "Valid · renews automatically");
            // Das Zertifikat belegt nur die Erreichbarkeit zum Ausstellungs-
            // zeitpunkt. Live gibt es ausschließlich nach positivem Test der
            // öffentlichen Adresse; sonst wird das ehrlich als unverifiziert
            // ausgewiesen (viele Router können ihre eigene öffentliche
            // Adresse von innen nicht erreichen).
            boolean publicVerified = publicIp != null
                    && tcpReachable(publicIp, configuration.httpsPort());
            if (publicVerified) {
                List<SubStatus> subs = List.of(domainSub, backendSub, webserverSub,
                        new SubStatus("Public endpoint", SubState.OK, "Reachable from the internet"),
                        httpsSub);
                return new PublicationStatus(Overall.LIVE,
                        expiringSoon
                                ? "Certificate expires soon · Renewal is automatic"
                                : "Certificate valid · Automatic renewal",
                        null, null, subs, certificate);
            }
            List<SubStatus> subs = List.of(domainSub, backendSub, webserverSub,
                    new SubStatus("Public endpoint", SubState.PENDING,
                            "Could not be verified from this network"),
                    httpsSub);
            return new PublicationStatus(Overall.UNVERIFIED, "Public access not verified",
                    "Certificate and backend are fine, but public access cannot be\n"
                            + "tested from inside this network. Check from another network\n"
                            + "(e.g. a phone on mobile data): https://" + host,
                    null, subs, certificate);
        } catch (Exception e) {
            // Alle Voraussetzungen erfüllt — jetzt arbeitet wirklich die
            // Anwendung selbst am Zertifikat.
            List<SubStatus> subs = List.of(domainSub, backendSub, webserverSub,
                    new SubStatus("HTTPS", SubState.PENDING, "Obtaining certificate"));
            return new PublicationStatus(Overall.SETTING_UP, "Obtaining HTTPS certificate…",
                    null, null, subs, null);
        }
    }

    private static SubStatus domainSub(List<String> dns, String publicIp) {
        if (dns.isEmpty()) {
            return new SubStatus("Domain", SubState.WARN, "DNS record missing");
        }
        if (publicIp != null && !dns.contains(publicIp)) {
            return new SubStatus("Domain", SubState.WARN, "Points to a different server");
        }
        return new SubStatus("Domain", SubState.OK, dns.get(0));
    }

    /** Die externe Abfrage der öffentlichen Adresse wird zwischengespeichert. */
    private static String publicAddressCached() {
        long now = System.currentTimeMillis();
        if (cachedPublicIp == null || now - publicIpFetchedAt > PUBLIC_IP_TTL_MILLIS) {
            cachedPublicIp = ConnectivityCheck.publicAddress();
            publicIpFetchedAt = now;
        }
        return cachedPublicIp;
    }

    private static String firstLabel(String host) {
        int dot = host.indexOf('.');
        return dot > 0 ? host.substring(0, dot) : host;
    }

    private static String baseDomain(String host) {
        int firstDot = host.indexOf('.');
        return firstDot > 0 && host.indexOf('.', firstDot + 1) > 0
                ? host.substring(firstDot + 1)
                : host;
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

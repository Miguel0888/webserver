package com.aresstack.webserver.infrastructure.caddy;

import com.aresstack.webserver.domain.Route;
import com.aresstack.webserver.domain.Site;
import com.aresstack.webserver.domain.Upstream;
import com.aresstack.webserver.domain.WebServerConfiguration;

/**
 * Rendert das Domainmodell deterministisch zu einem Caddyfile.
 * Reine Textgenerierung — startet keine Prozesse und schreibt keine Dateien.
 */
public class CaddyfileRenderer {

    public static final String ADMIN_LISTEN = "127.0.0.1:2019";

    /**
     * Produktion: Automatic HTTPS auf :80/:443, Admin auf 127.0.0.1:2019.
     * {@link #localHttp}: HTTP-only auf zufälligen Loopback-Ports — für
     * Integrationstests ohne Adminrechte, DNS oder ACME.
     */
    public record Options(String adminListen, Integer httpPort) {

        public static Options production() {
            return new Options(ADMIN_LISTEN, null);
        }

        public static Options localHttp(int adminPort, int httpPort) {
            return new Options("127.0.0.1:" + adminPort, httpPort);
        }

        boolean httpOnly() {
            return httpPort != null;
        }
    }

    private final Options options;

    public CaddyfileRenderer() {
        this(Options.production());
    }

    public CaddyfileRenderer(Options options) {
        this.options = options;
    }

    public String render(WebServerConfiguration configuration) {
        StringBuilder out = new StringBuilder();
        renderGlobalOptions(out, configuration);
        for (Site site : configuration.sites()) {
            out.append('\n');
            renderSite(out, site, configuration.defaultUpstream());
        }
        return out.toString();
    }

    private void renderGlobalOptions(StringBuilder out, WebServerConfiguration configuration) {
        out.append("{\n");
        out.append("    email ").append(configuration.acme().email()).append('\n');
        out.append("    acme_ca ").append(configuration.acme().ca()).append('\n');
        out.append("    admin ").append(options.adminListen()).append('\n');
        if (options.httpOnly()) {
            out.append("    default_bind 127.0.0.1\n");
            out.append("    http_port ").append(options.httpPort()).append('\n');
        }
        out.append("}\n");
    }

    private void renderSite(StringBuilder out, Site site, Upstream defaultUpstream) {
        Upstream fallback = site.effectiveUpstream(defaultUpstream);
        // http://-Scheme deaktiviert Automatic HTTPS für die Site (Testmodus).
        String address = options.httpOnly() ? "http://" + site.host() : site.host().value();
        out.append(address).append(" {\n");
        if (site.routes().isEmpty()) {
            out.append("    reverse_proxy ").append(fallback.toCaddyAddress()).append('\n');
        } else {
            for (Route route : site.routes()) {
                out.append("    handle ").append(route.pathMatcher()).append(" {\n");
                out.append("        reverse_proxy ").append(route.upstream().toCaddyAddress()).append('\n');
                out.append("    }\n\n");
            }
            // Default-Fallback: fängt alles ohne spezifischeren Matcher.
            out.append("    handle {\n");
            out.append("        reverse_proxy ").append(fallback.toCaddyAddress()).append('\n');
            out.append("    }\n");
        }
        out.append("}\n");
    }
}

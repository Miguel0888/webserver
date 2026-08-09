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
        out.append("    admin ").append(ADMIN_LISTEN).append('\n');
        out.append("}\n");
    }

    private void renderSite(StringBuilder out, Site site, Upstream defaultUpstream) {
        Upstream fallback = site.effectiveUpstream(defaultUpstream);
        out.append(site.host()).append(" {\n");
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

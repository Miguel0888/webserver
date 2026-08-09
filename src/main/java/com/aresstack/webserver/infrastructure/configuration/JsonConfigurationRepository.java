package com.aresstack.webserver.infrastructure.configuration;

import com.aresstack.webserver.application.ConfigurationRepository;
import com.aresstack.webserver.domain.AcmeConfiguration;
import com.aresstack.webserver.domain.DomainName;
import com.aresstack.webserver.domain.Route;
import com.aresstack.webserver.domain.Site;
import com.aresstack.webserver.domain.Upstream;
import com.aresstack.webserver.domain.WebServerConfiguration;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;

/**
 * Lädt und speichert config/webserver.json und übersetzt zwischen dem
 * JSON-Schema der Datei und dem Domainmodell.
 */
public class JsonConfigurationRepository implements ConfigurationRepository {

    // Dateiformat von webserver.json — bewusst getrennt vom Domainmodell.
    record FileModel(String domain, String defaultUpstream, Integer httpPort, Integer httpsPort,
                     AcmeModel acme, List<SiteModel> sites) {
    }

    record AcmeModel(String email, String ca) {
    }

    record SiteModel(String host, String upstream, Boolean https, List<RouteModel> routes) {
    }

    record RouteModel(String path, String upstream) {
    }

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    private final Path file;

    public JsonConfigurationRepository(Path file) {
        this.file = file;
    }

    @Override
    public WebServerConfiguration load() {
        FileModel model;
        try {
            model = mapper.readValue(Files.readAllBytes(file), FileModel.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read configuration " + file, e);
        }
        return toDomain(model);
    }

    @Override
    public void save(WebServerConfiguration configuration) {
        FileModel model = fromDomain(configuration);
        try {
            Files.createDirectories(file.getParent());
            Path temp = Files.createTempFile(file.getParent(), "webserver", ".json.tmp");
            mapper.writeValue(temp.toFile(), model);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write configuration " + file, e);
        }
    }

    static WebServerConfiguration toDomain(FileModel model) {
        require(model.domain() != null, "'domain' is required");
        require(model.acme() != null && model.acme().email() != null, "'acme.email' is required");
        require(model.sites() != null && !model.sites().isEmpty(), "'sites' must contain at least one entry");

        Upstream defaultUpstream = model.defaultUpstream() != null
                ? Upstream.parse(model.defaultUpstream())
                : Upstream.parse("http://127.0.0.1:8080");
        AcmeConfiguration acme = model.acme().ca() != null
                ? new AcmeConfiguration(model.acme().email(), model.acme().ca())
                : AcmeConfiguration.letsEncrypt(model.acme().email());

        List<Site> sites = model.sites().stream().map(site -> {
            require(site.host() != null, "each site requires a 'host'");
            Optional<Upstream> upstream = Optional.ofNullable(site.upstream()).map(Upstream::parse);
            List<Route> routes = site.routes() == null ? List.of() : site.routes().stream()
                    .map(route -> {
                        require(route.path() != null && route.upstream() != null,
                                "each route requires 'path' and 'upstream'");
                        return new Route(route.path(), Upstream.parse(route.upstream()));
                    })
                    .toList();
            boolean httpsEnabled = site.https() == null || site.https();
            return new Site(new DomainName(site.host()), upstream, routes, httpsEnabled);
        }).toList();

        int httpPort = model.httpPort() != null ? model.httpPort() : 80;
        int httpsPort = model.httpsPort() != null ? model.httpsPort() : 443;
        return new WebServerConfiguration(new DomainName(model.domain()), acme, defaultUpstream,
                sites, httpPort, httpsPort);
    }

    static FileModel fromDomain(WebServerConfiguration configuration) {
        List<SiteModel> sites = configuration.sites().stream().map(site -> new SiteModel(
                site.host().value(),
                site.upstream().map(Upstream::toString).orElse(null),
                site.httpsEnabled() ? null : Boolean.FALSE,
                site.routes().isEmpty() ? null : site.routes().stream()
                        .map(route -> new RouteModel(route.pathMatcher(), route.upstream().toString()))
                        .toList()
        )).toList();
        return new FileModel(
                configuration.domain().value(),
                configuration.defaultUpstream().toString(),
                configuration.httpPort() == 80 ? null : configuration.httpPort(),
                configuration.httpsPort() == 443 ? null : configuration.httpsPort(),
                new AcmeModel(configuration.acme().email(), configuration.acme().ca()),
                sites);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException("Invalid webserver.json: " + message);
        }
    }
}

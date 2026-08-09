# AresStack Webserver

AresStack Webserver ist eine grafische Desktop-Anwendung, die lokale Dienste
unter eigenen Domains **automatisch per HTTPS** veröffentlicht.

Kein Apache, kein manuell gepflegter Reverse Proxy, keine Zertifikatsdateien,
keine ACME-Konfiguration, keine Server-Konfigurationsdateien. Der Benutzer
beschreibt nur das Ergebnis:

```
askai.aresstack.de  →  localhost:8082  →  HTTPS an  →  Publish
```

Alles Weitere — Reverse Proxy, Let's-Encrypt-Zertifikat, automatische
Erneuerung, HTTP→HTTPS-Umleitung, unterbrechungsfreie Konfigurations-
änderungen — übernimmt die Anwendung. Als Engine dient intern
[Caddy](https://caddyserver.com/) (gebündelt, checksummen-verifiziert);
für den Benutzer ist das ein Implementierungsdetail.

```
                    Internet
                       │
                 :80 / :443
                       │
                       ▼
               ┌───────────────┐
               │     Caddy     │   TLS · ACME · Reverse Proxy
               └───────┬───────┘
                       │  gesteuert über lokale Admin-API
               ┌───────┴───────┐
               │  AresStack    │   Java 21 · Swing · FlatLaf
               │  Webserver    │
               └───────┬───────┘
        ┌──────────────┼──────────────┐
        ▼              ▼              ▼
   localhost:8080  192.168.x.x    nas.local
```

## Funktionen

- **Geführte Veröffentlichung:** Jede Publikation ist eine Karte mit
  Gesamtstatus — `⚠ Action required` (mit der konkreten nächsten Handlung,
  z.B. dem exakten CNAME-Record samt Copy-Buttons), `◌ Setting up`,
  `◌ Not verified` oder `● Live`. Die Prüfkette (DNS → Backend → Ports →
  Zertifikat) läuft automatisch weiter, bis alles steht.
- **Ehrliche Zustände:** `● Live` erscheint nur nach positivem Nachweis der
  öffentlichen Erreichbarkeit; ein gültiges Zertifikat ist Zertifikats-,
  nicht Netzwerkstatus.
- **HTTPS automatisch:** Let's Encrypt inklusive Erneuerung, ohne dass je
  eine IP-Adresse oder Zertifikatsdatei eingetragen werden muss.
- **Pfad-Routing** (`/api/*` → anderes Backend) und Backend-HTTPS unter
  *Advanced* — der Standardfall bleibt drei Felder.
- **DNS-Modell:** Subdomains zeigen ausschließlich per **CNAME** auf die
  DynDNS-Basisdomain. Die Basisdomain hält der Router (oder der optionale
  eingebaute DynDNS-Client) aktuell.
- **Atomare Konfiguration:** Änderungen werden validiert (`caddy validate`)
  und per `/load` atomar übernommen — eine ungültige Konfiguration erreicht
  den laufenden Server nie, Routingänderungen brauchen keinen Neustart.
- **Robuster Lebenszyklus:** Ein bei einem früheren Anwendungslauf
  gestarteter Caddy wird über die Admin-API und eine persistierte
  Prozessidentität (`data/caddy/runtime.json`) wiedererkannt und adoptiert.
  Fremde Caddy-Instanzen werden niemals verändert oder gestoppt.

## Voraussetzungen

| Was | Warum |
|---|---|
| Windows, Java 21 | Laufzeitumgebung der Anwendung |
| Eigene Domain mit DynDNS (z.B. Router/FritzBox oder eingebauter Client) | `aresstack.de` zeigt immer auf den aktuellen Anschluss |
| Portweiterleitung **80** und **443** auf diesen Rechner | Let's-Encrypt-Validierung und öffentlicher Zugriff |

## Installation

1. Aktuelles Release laden: **[Releases → snapshot](../../releases/tag/snapshot)**
   (`aresstack-webserver-…-all.jar`, selbst lauffähiges Fat JAR inkl. Caddy).
2. Starten:

   ```
   java -jar aresstack-webserver-1.0-SNAPSHOT-all.jar
   ```

   Beim ersten Start entpackt die Anwendung die gebündelte `caddy.exe` nach
   `runtime/caddy/bin` im Arbeitsverzeichnis und zeigt sofort das
   Hauptfenster — es gibt keinen Einrichtungsassistenten.

## Erster Dienst in drei Schritten

1. **+ Publish service** → öffentliche Adresse (`askai.aresstack.de`),
   Ziel (`localhost` / `8082`), HTTPS angehakt lassen → **Publish**.
2. Fehlt der DNS-Eintrag, zeigt die Karte ihn exakt so, wie ihn das
   Provider-Formular erwartet (`Type CNAME · Hostname askai · Target
   aresstack.de`, je mit Copy-Button, `?` öffnet die Anleitung).
3. Sobald DNS propagiert ist, holt die Anwendung das Zertifikat von selbst;
   die Karte wandert automatisch zu `● Live`.

## Verzeichnisse

```
config/webserver.json    fachliche Konfiguration (Source of Truth)
generated/Caddyfile      generiertes Infrastrukturartefakt
data/caddy/              Zertifikate, Schlüssel, ACME-Konto — persistent,
                         darf bei Updates niemals gelöscht werden
logs/caddy.log           technisches Log (in der App: Logs → Technical log)
runtime/caddy/bin/       gebündeltes Caddy-Binary
```

`config/`, `data/` und `logs/` sind bei Updates zu erhalten; `bin/`, `lib/`,
`generated/` und `runtime/` sind ersetzbar.

## Aus dem Quelltext bauen

```
gradlew downloadCaddy    lädt die gepinnte Caddy-Version (SHA-512-geprüft)
gradlew test             Unit- und Caddy-in-the-loop-Integrationstests
gradlew run              startet die Anwendung (Projektverzeichnis als Root)
gradlew fatJar           baut das selbst lauffähige Fat JAR
gradlew distZip          klassische ZIP-Distribution mit Startskripten
```

Jeder Push auf `master` baut über GitHub Actions ein rotierendes
Snapshot-Release (Windows, alle Tests inklusive echtem Caddy-Prozess).

## Sicherheit

- Die Caddy-Admin-API ist ausschließlich an Loopback gebunden
  (`127.0.0.1:29171`, bewusst nicht Caddys Default-Port) und wird nie ins
  LAN oder Internet exponiert.
- Das Caddy-Binary wird beim Build gegen die offiziellen Checksummen des
  GitHub-Releases verifiziert.
- Backend-Verbindungen mit HTTPS validieren Zertifikate regulär;
  `tls_insecure_skip_verify` wird niemals gesetzt.

## Lizenz

Caddy ist Apache-2.0-lizenziert; die Lizenz liegt dem Release unter
`licenses/caddy/` bzw. im Fat JAR bei.

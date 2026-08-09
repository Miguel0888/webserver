package com.aresstack.webserver.ui.publication;

import com.aresstack.webserver.domain.Site;
import com.aresstack.webserver.domain.Upstream;
import com.aresstack.webserver.domain.WebServerConfiguration;
import com.aresstack.webserver.ui.ConnectivityCheck;
import com.aresstack.webserver.ui.status.PublicationStatus;
import com.aresstack.webserver.ui.status.PublicationStatusPresenter;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.text.SimpleDateFormat;

/**
 * Statusansicht einer Veröffentlichung: Domain, Webserver, Ziel und HTTPS als
 * Ampelzeilen — Hinweise zum Beheben erscheinen nur, wenn etwas kaputt ist.
 */
public class PublicationStatusView extends JDialog {

    private static final Color OK = new Color(0x2e7d32);
    private static final Color WARN = new Color(0xed6c02);
    private static final Color MUTED = new Color(0x757575);

    private final JPanel rows = new JPanel();
    private final WebServerConfiguration configuration;
    private final Site site;
    private final boolean serverRunning;

    private PublicationStatusView(Window owner, WebServerConfiguration configuration, Site site,
                                  boolean serverRunning) {
        super(owner, site.host().value(), ModalityType.MODELESS);
        this.configuration = configuration;
        this.site = site;
        this.serverRunning = serverRunning;

        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setBorder(BorderFactory.createEmptyBorder(14, 18, 8, 18));

        JButton checkAgain = new JButton("Check again");
        checkAgain.addActionListener(e -> refresh(checkAgain));
        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttons.add(checkAgain);
        buttons.add(close);

        getContentPane().add(rows, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        setMinimumSize(new java.awt.Dimension(460, 260));
        refresh(checkAgain);
        pack();
        setLocationRelativeTo(owner);
    }

    private record Probe(ConnectivityCheck.Result connectivity, PublicationStatus status) {
    }

    private void refresh(JButton checkAgain) {
        checkAgain.setEnabled(false);
        rows.removeAll();
        rows.add(statusRow(MUTED, "Checking…", null));
        rows.revalidate();
        rows.repaint();

        new SwingWorker<Probe, Void>() {
            @Override
            protected Probe doInBackground() {
                ConnectivityCheck.Result connectivity = ConnectivityCheck.run(
                        site.host().value(), configuration.httpPort(), configuration.httpsPort());
                PublicationStatus status = new PublicationStatusPresenter()
                        .probe(configuration, site, serverRunning);
                return new Probe(connectivity, status);
            }

            @Override
            protected void done() {
                checkAgain.setEnabled(true);
                try {
                    render(get());
                } catch (Exception e) {
                    rows.removeAll();
                    rows.add(statusRow(WARN, "The check could not be completed.", null));
                    rows.revalidate();
                }
            }
        }.execute();
    }

    private void render(Probe probe) {
        ConnectivityCheck.Result connectivity = probe.connectivity();
        PublicationStatus status = probe.status();

        rows.removeAll();

        // Domain
        if (connectivity.dnsMatches() == null) {
            rows.add(statusRow(MUTED, "Domain — could not be verified", null));
        } else if (connectivity.dnsMatches()) {
            rows.add(statusRow(OK, "Domain — Correct", null));
        } else {
            rows.add(statusRow(WARN, "Domain — does not point to this server",
                    site.host() + " currently points to "
                            + String.join(", ", connectivity.dnsAddresses())
                            + " instead of " + connectivity.publicAddress() + ".\n"
                            + "Change the DNS record to: " + connectivity.publicAddress()));
        }

        // Webserver
        boolean listening = connectivity.serverListensHttp() && connectivity.serverListensHttps();
        if (listening) {
            rows.add(statusRow(OK, "Webserver — Reachable", null));
        } else if (!serverRunning) {
            rows.add(statusRow(WARN, "Webserver — Stopped", "Start the server."));
        } else {
            rows.add(statusRow(WARN, "Webserver — Not listening on port "
                            + (connectivity.serverListensHttp() ? configuration.httpsPort() : configuration.httpPort()),
                    "Another application may be using the port."));
        }

        // Destination
        Upstream target = site.effectiveUpstream(configuration.defaultUpstream());
        if (status.destination() == PublicationStatus.Reachability.REACHABLE) {
            rows.add(statusRow(OK, "Destination — Reachable", null));
        } else {
            rows.add(statusRow(WARN, "Destination — Unreachable",
                    target.host() + ":" + target.port()
                            + " did not respond. Check that the service is running."));
        }

        // HTTPS
        rows.add(httpsRow(status));

        rows.revalidate();
        rows.repaint();
        pack();
    }

    private JPanel httpsRow(PublicationStatus status) {
        return switch (status.https()) {
            case SECURED -> statusRow(OK, "HTTPS — Secured",
                    status.certificate() == null ? null
                            : "Certificate renews automatically · valid until "
                            + new SimpleDateFormat("dd.MM.yyyy").format(status.certificate().expires()));
            case EXPIRING -> statusRow(WARN, "HTTPS — Certificate expires soon",
                    "Renewal is automatic; no action needed unless this persists.");
            case EXPIRED -> statusRow(WARN, "HTTPS — Certificate expired",
                    "Check that the domain still points to this server and that\n"
                            + "ports 80 and 443 are forwarded.");
            case SETTING_UP -> statusRow(MUTED, "HTTPS — Setting up…",
                    "Requesting certificate. This usually takes under a minute.");
            case PORT_UNAVAILABLE -> statusRow(WARN, "HTTPS — Port unavailable",
                    "Port " + configuration.httpsPort() + " is not accepting connections.");
            case SERVER_STOPPED -> statusRow(MUTED, "HTTPS — Server stopped", null);
            case HTTPS_OFF -> statusRow(MUTED, "HTTPS — Disabled for this service", null);
        };
    }

    private static JPanel statusRow(Color color, String title, String detail) {
        JPanel row = new JPanel(new BorderLayout(10, 2));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        JLabel dot = new JLabel("●");
        dot.setForeground(color);
        row.add(dot, BorderLayout.WEST);
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        text.add(titleLabel);
        if (detail != null) {
            for (String line : detail.split("\n")) {
                JLabel detailLabel = new JLabel(line);
                detailLabel.setForeground(MUTED);
                text.add(detailLabel);
            }
        }
        row.add(text, BorderLayout.CENTER);
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(row, BorderLayout.WEST);
        return wrapper;
    }

    public static void open(Window owner, WebServerConfiguration configuration, Site site,
                            boolean serverRunning) {
        new PublicationStatusView(owner, configuration, site, serverRunning).setVisible(true);
    }
}

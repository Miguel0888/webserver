package com.aresstack.webserver.ui;

import com.aresstack.webserver.domain.Site;
import com.aresstack.webserver.domain.Upstream;
import com.aresstack.webserver.domain.WebServerConfiguration;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hauptfenster: Domainliste mit Zertifikatsstatus, Serverstatus und die
 * zentralen Aktionen Add Domain, Server Settings und Logs.
 */
public class MainWindow extends JFrame {

    private static final Color RUNNING = new Color(0x2e8b57);
    private static final Color STOPPED = new Color(0xb22222);

    private final WebServerController controller;
    private final UserLog log;
    private final JLabel statusDot = new JLabel("●");
    private final JLabel statusText = new JLabel();
    private final JLabel footerStatus = new JLabel();
    private final JPanel domainList = new JPanel();
    private final JButton startStopButton = new JButton();
    private final Map<String, String> certificateLabels = new ConcurrentHashMap<>();
    private final Map<String, CertificateStatusChecker.CertificateInfo> certificates = new ConcurrentHashMap<>();

    public MainWindow(WebServerController controller, UserLog log) {
        super("AresStack Webserver");
        this.controller = controller;
        this.log = log;
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JLabel title = new JLabel("AresStack Webserver");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        header.add(title, BorderLayout.WEST);
        JPanel headerStatus = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        headerStatus.add(statusDot);
        headerStatus.add(statusText);
        header.add(headerStatus, BorderLayout.EAST);

        domainList.setLayout(new BoxLayout(domainList, BoxLayout.Y_AXIS));
        JPanel listWrapper = new JPanel(new BorderLayout());
        listWrapper.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        JLabel domainsHeading = new JLabel("Domains");
        domainsHeading.setFont(domainsHeading.getFont().deriveFont(Font.BOLD));
        domainsHeading.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        listWrapper.add(domainsHeading, BorderLayout.NORTH);
        listWrapper.add(new JScrollPane(domainList), BorderLayout.CENTER);
        JButton addDomain = new JButton("+ Add Domain");
        addDomain.addActionListener(e -> addDomain());
        JPanel addPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        addPanel.add(addDomain);
        listWrapper.add(addPanel, BorderLayout.SOUTH);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        footer.add(footerStatus, BorderLayout.WEST);
        JPanel footerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        startStopButton.addActionListener(e -> toggleServer());
        JButton settings = new JButton("Server Settings");
        settings.addActionListener(e -> SettingsDialog.show(this, controller));
        JButton logs = new JButton("Logs");
        logs.addActionListener(e -> LogsDialog.show(this, log, controller.directories().logs().resolve("caddy.log")));
        footerButtons.add(startStopButton);
        footerButtons.add(settings);
        footerButtons.add(logs);
        footer.add(footerButtons, BorderLayout.EAST);

        getContentPane().add(header, BorderLayout.NORTH);
        getContentPane().add(listWrapper, BorderLayout.CENTER);
        getContentPane().add(footer, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(640, 480));
        pack();
        setLocationRelativeTo(null);

        controller.addListener(this::refresh);
        refresh();

        // Zertifikats- und Serverstatus regelmäßig im Hintergrund auffrischen.
        new Timer(15_000, e -> refreshCertificates()).start();
        refreshCertificates();
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    private void refresh() {
        boolean running = controller.isRunning();
        statusDot.setForeground(running ? RUNNING : STOPPED);
        statusText.setText(running ? "Running" : "Stopped");
        startStopButton.setText(running ? "Stop Server" : "Start Server");

        WebServerConfiguration config = controller.configuration();
        long httpsCount = config.sites().stream().filter(Site::httpsEnabled).count();
        footerStatus.setText("Server: " + (running ? "Running" : "Stopped")
                + "     HTTP: 80     HTTPS: 443     Domains: " + config.sites().size()
                + " active" + (httpsCount > 0 ? "     Certificates: " + httpsCount + " managed" : ""));

        domainList.removeAll();
        for (Site site : config.sites()) {
            domainList.add(domainRow(site, config.defaultUpstream()));
            domainList.add(Box.createVerticalStrut(4));
        }
        domainList.revalidate();
        domainList.repaint();
    }

    private JPanel domainRow(Site site, Upstream defaultUpstream) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel host = new JLabel(site.host().value());
        host.setFont(host.getFont().deriveFont(Font.BOLD));
        JLabel target = new JLabel(site.effectiveUpstream(defaultUpstream).toCaddyAddress()
                + (site.routes().isEmpty() ? "" : "   +" + site.routes().size() + " route(s)"));
        target.setForeground(Color.DARK_GRAY);
        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.add(host);
        left.add(target);
        row.add(left, BorderLayout.CENTER);

        JLabel https = new JLabel(certificateLabel(site));
        row.add(https, BorderLayout.EAST);

        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openDomain(site);
            }
        });
        return row;
    }

    private String certificateLabel(Site site) {
        if (!site.httpsEnabled()) {
            return "HTTPS disabled";
        }
        if (!controller.isRunning()) {
            return "—";
        }
        return certificateLabels.getOrDefault(site.host().value(), "Obtaining certificate…");
    }

    private void refreshCertificates() {
        if (!controller.isRunning()) {
            return;
        }
        WebServerConfiguration config = controller.configuration();
        new SwingWorker<Map<String, String>, Void>() {
            @Override
            protected Map<String, String> doInBackground() {
                Map<String, String> labels = new HashMap<>();
                for (Site site : config.sites()) {
                    if (!site.httpsEnabled()) {
                        continue;
                    }
                    String host = site.host().value();
                    try {
                        var info = CertificateStatusChecker.fetch(host, 443);
                        certificates.put(host, info);
                        labels.put(host, info.isExpired() ? "Certificate expired" : "HTTPS ✓");
                    } catch (Exception e) {
                        certificates.remove(host);
                        labels.put(host, "Obtaining certificate…");
                    }
                }
                return labels;
            }

            @Override
            protected void done() {
                try {
                    certificateLabels.putAll(get());
                    refresh();
                } catch (Exception ignored) {
                }
            }
        }.execute();
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private void addDomain() {
        Site site = DomainDialog.show(this, null, controller.configuration().defaultUpstream());
        if (site != null) {
            runAsync("Add Domain", () -> controller.addSite(site));
        }
    }

    private void openDomain(Site site) {
        Object[] options = certificateDetail(site) == null
                ? new Object[]{"Edit", "Remove", "Cancel"}
                : new Object[]{"Edit", "Remove", "Certificate…", "Cancel"};
        int choice = JOptionPane.showOptionDialog(this,
                site.host().value() + "\n"
                        + site.effectiveUpstream(controller.configuration().defaultUpstream()) + "\n"
                        + (site.httpsEnabled() ? "Public HTTPS: Enabled" : "Public HTTPS: Disabled"),
                site.host().value(),
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (choice == 0) {
            Site updated = DomainDialog.show(this, site, controller.configuration().defaultUpstream());
            if (updated != null) {
                runAsync("Edit Domain", () -> controller.updateSite(site.host(), updated));
            }
        } else if (choice == 1) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Remove " + site.host() + "?\n\nThe domain will no longer be served by this webserver.",
                    "Remove Domain", JOptionPane.OK_CANCEL_OPTION);
            if (confirm == JOptionPane.OK_OPTION) {
                runAsync("Remove Domain", () -> controller.removeSite(site.host()));
            }
        } else if (choice == 2 && options.length == 4) {
            showCertificateDetails(site);
        }
    }

    private CertificateStatusChecker.CertificateInfo certificateDetail(Site site) {
        return certificates.get(site.host().value());
    }

    private void showCertificateDetails(Site site) {
        var info = certificateDetail(site);
        if (info == null) {
            return;
        }
        SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy");
        JOptionPane.showMessageDialog(this,
                "SSL Certificate\n\n"
                        + "Status\n" + (info.isExpired() ? "Expired" : "Valid") + "\n\n"
                        + "Provider\nLet's Encrypt\n\n"
                        + "Issued\n" + format.format(info.issued()) + "\n\n"
                        + "Valid until\n" + format.format(info.expires()) + "\n\n"
                        + "Renewal\nAutomatic",
                site.host().value(), JOptionPane.INFORMATION_MESSAGE);
    }

    private void toggleServer() {
        boolean running = controller.isRunning();
        runAsync(running ? "Stop Server" : "Start Server",
                running ? controller::stop : controller::start);
    }

    /** Führt Serveroperationen außerhalb des EDT aus und meldet Fehler verständlich. */
    private void runAsync(String title, Runnable action) {
        setEnabledRecursively(getContentPane(), false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                action.run();
                return null;
            }

            @Override
            protected void done() {
                setEnabledRecursively(getContentPane(), true);
                try {
                    get();
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    FriendlyErrors.show(MainWindow.this, title, cause);
                }
                refresh();
                refreshCertificates();
            }
        }.execute();
    }

    private static void setEnabledRecursively(Component component, boolean enabled) {
        component.setEnabled(enabled);
        if (component instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) {
                setEnabledRecursively(child, enabled);
            }
        }
    }
}

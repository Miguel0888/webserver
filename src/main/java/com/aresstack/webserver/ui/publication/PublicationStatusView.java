package com.aresstack.webserver.ui.publication;

import com.aresstack.webserver.domain.Site;
import com.aresstack.webserver.domain.WebServerConfiguration;
import com.aresstack.webserver.ui.status.PublicationStatus;
import com.aresstack.webserver.ui.status.PublicationStatusPresenter;

import javax.swing.BorderFactory;
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
 * Detailansicht des Veröffentlichungszustands. Sie ist nie notwendig, um die
 * Einrichtung abzuschließen — alle Handlungsaufforderungen stehen bereits auf
 * der Karte; hier gibt es dieselben Informationen mit Zertifikatsdaten.
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
        setMinimumSize(new java.awt.Dimension(480, 280));
        refresh(checkAgain);
        pack();
        setLocationRelativeTo(owner);
    }

    private void refresh(JButton checkAgain) {
        checkAgain.setEnabled(false);
        rows.removeAll();
        rows.add(line(MUTED, "Checking…"));
        rows.revalidate();
        rows.repaint();

        new SwingWorker<PublicationStatus, Void>() {
            @Override
            protected PublicationStatus doInBackground() {
                return new PublicationStatusPresenter().probe(configuration, site, serverRunning);
            }

            @Override
            protected void done() {
                checkAgain.setEnabled(true);
                try {
                    render(get());
                } catch (Exception e) {
                    rows.removeAll();
                    rows.add(line(WARN, "The check could not be completed."));
                    rows.revalidate();
                }
            }
        }.execute();
    }

    private void render(PublicationStatus status) {
        rows.removeAll();

        JLabel headline = new JLabel(status.headline());
        headline.setFont(headline.getFont().deriveFont(Font.BOLD, headline.getFont().getSize() + 2f));
        headline.setForeground(status.overall() == PublicationStatus.Overall.ACTION_REQUIRED ? WARN
                : status.overall() == PublicationStatus.Overall.LIVE ? OK : MUTED);
        rows.add(headline);
        rows.add(javax.swing.Box.createVerticalStrut(6));

        if (status.actionText() != null) {
            for (String textLine : status.actionText().split("\n")) {
                rows.add(line(MUTED, textLine));
            }
            rows.add(javax.swing.Box.createVerticalStrut(8));
        }

        for (PublicationStatus.SubStatus sub : status.subStatuses()) {
            Color color = switch (sub.state()) {
                case OK -> OK;
                case WARN -> WARN;
                case PENDING, OFF -> MUTED;
            };
            rows.add(line(color, symbol(sub.state()) + "  " + sub.label() + " — " + sub.detail()));
        }

        if (status.certificate() != null) {
            SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy");
            rows.add(javax.swing.Box.createVerticalStrut(8));
            rows.add(line(MUTED, "Certificate issued " + format.format(status.certificate().issued())
                    + " · valid until " + format.format(status.certificate().expires())
                    + " · renews automatically"));
        }

        rows.revalidate();
        rows.repaint();
        pack();
    }

    private static String symbol(PublicationStatus.SubState state) {
        return switch (state) {
            case OK -> "✓";
            case WARN -> "⚠";
            case PENDING -> "◌";
            case OFF -> "○";
        };
    }

    private static JLabel line(Color color, String text) {
        JLabel label = new JLabel(text);
        label.setForeground(color);
        label.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        return label;
    }

    public static void open(Window owner, WebServerConfiguration configuration, Site site,
                            boolean serverRunning) {
        new PublicationStatusView(owner, configuration, site, serverRunning).setVisible(true);
    }
}

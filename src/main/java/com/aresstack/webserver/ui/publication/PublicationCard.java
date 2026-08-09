package com.aresstack.webserver.ui.publication;

import com.aresstack.webserver.domain.Site;
import com.aresstack.webserver.domain.Upstream;
import com.aresstack.webserver.ui.status.PublicationStatus;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Eine Veröffentlichung als Karte: öffentliche Adresse, Ziel, Zustand.
 * SSL erscheint hier als Status, nicht als Konfigurationsaufgabe.
 */
class PublicationCard extends JPanel {

    private static final Color ONLINE = new Color(0x2e7d32);
    private static final Color WARN = new Color(0xed6c02);
    private static final Color ERROR = new Color(0xc62828);
    private static final Color MUTED = new Color(0x757575);
    private static final Color BORDER = new Color(0xdddddd);

    PublicationCard(Site site, Upstream defaultUpstream, PublicationStatus status,
                    Runnable onEdit, Runnable onRemove, Runnable onDetails) {
        super(new BorderLayout(12, 0));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(4, 0, 4, 0),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(BORDER, 1, true),
                        BorderFactory.createEmptyBorder(12, 14, 12, 14))));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

        JLabel host = new JLabel(site.host().value());
        host.setFont(host.getFont().deriveFont(Font.BOLD, host.getFont().getSize() + 2f));
        text.add(host);
        text.add(Box.createVerticalStrut(6));

        String httpsBadge = !site.httpsEnabled() ? "HTTP"
                : status.isSecured() ? "HTTPS ✓"
                : "HTTPS";
        JLabel target = new JLabel(httpsBadge + "     →     "
                + site.effectiveUpstream(defaultUpstream).toCaddyAddress()
                + (site.routes().isEmpty() ? "" : "     · " + site.routes().size() + " path rule(s)"));
        text.add(target);

        String line = status.cardLine(site.httpsEnabled());
        if (!line.isEmpty()) {
            text.add(Box.createVerticalStrut(4));
            JLabel statusLine = new JLabel(line);
            statusLine.setForeground(statusColorFor(status));
            statusLine.setFont(statusLine.getFont().deriveFont(statusLine.getFont().getSize() - 1f));
            text.add(statusLine);
        }
        add(text, BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout());
        right.setOpaque(false);
        JLabel badge = new JLabel(badgeText(status));
        badge.setForeground(badgeColor(status));
        badge.setHorizontalAlignment(JLabel.RIGHT);
        right.add(badge, BorderLayout.NORTH);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        JButton edit = new JButton("Edit");
        edit.addActionListener(e -> onEdit.run());
        JButton more = new JButton("⋮");
        more.setMargin(new java.awt.Insets(2, 8, 2, 8));
        JPopupMenu menu = new JPopupMenu();
        JMenuItem details = new JMenuItem("Status details");
        details.addActionListener(e -> onDetails.run());
        JMenuItem remove = new JMenuItem("Remove…");
        remove.addActionListener(e -> onRemove.run());
        menu.add(details);
        menu.addSeparator();
        menu.add(remove);
        more.addActionListener(e -> menu.show(more, 0, more.getHeight()));
        buttons.add(edit);
        buttons.add(more);
        right.add(buttons, BorderLayout.SOUTH);
        add(right, BorderLayout.EAST);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onDetails.run();
            }
        });
    }

    private static String badgeText(PublicationStatus status) {
        return switch (status.destination()) {
            case REACHABLE -> "● Online";
            case UNREACHABLE -> "⚠ Offline";
            case UNKNOWN -> "● …";
        };
    }

    private static Color badgeColor(PublicationStatus status) {
        return switch (status.destination()) {
            case REACHABLE -> ONLINE;
            case UNREACHABLE -> WARN;
            case UNKNOWN -> MUTED;
        };
    }

    private static Color statusColorFor(PublicationStatus status) {
        if (status.destination() == PublicationStatus.Reachability.UNREACHABLE) {
            return WARN;
        }
        return switch (status.https()) {
            case SECURED -> MUTED;
            case EXPIRING, SETTING_UP, HTTPS_OFF, SERVER_STOPPED -> MUTED;
            case EXPIRED, PORT_UNAVAILABLE -> ERROR;
        };
    }
}

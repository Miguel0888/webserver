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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

/**
 * Eine Veröffentlichung als Karte. Der Badge zeigt den End-to-End-Zustand
 * der öffentlichen Adresse; erfordert ein Schritt eine Benutzeraktion, steht
 * die konkrete Anweisung direkt hier — nie nur in einer Detailansicht.
 */
class PublicationCard extends JPanel {

    private static final Color LIVE = new Color(0x2e7d32);
    private static final Color WARN = new Color(0xed6c02);
    private static final Color MUTED = new Color(0x757575);
    private static final Color BORDER = new Color(0xdddddd);

    interface CardActions {
        void edit();

        void remove();

        void details();

        void checkAgain();
    }

    PublicationCard(Site site, Upstream defaultUpstream, PublicationStatus status,
                    CardActions actions) {
        super(new BorderLayout(12, 0));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(4, 0, 4, 0),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(BORDER, 1, true),
                        BorderFactory.createEmptyBorder(12, 14, 12, 14))));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

        JLabel host = new JLabel(site.host().value());
        host.setFont(host.getFont().deriveFont(Font.BOLD, host.getFont().getSize() + 2f));
        text.add(host);
        text.add(Box.createVerticalStrut(5));

        String httpsBadge = !site.httpsEnabled() ? "HTTP" : status.isSecured() ? "HTTPS ✓" : "HTTPS";
        text.add(new JLabel(httpsBadge + "     →     "
                + site.effectiveUpstream(defaultUpstream).toCaddyAddress()
                + (site.routes().isEmpty() ? "" : "     · " + site.routes().size() + " path rule(s)")));

        // Kopfzeile des Zustands + ggf. konkrete nächste Handlung.
        text.add(Box.createVerticalStrut(6));
        JLabel headline = new JLabel(status.headline());
        headline.setForeground(status.overall() == PublicationStatus.Overall.ACTION_REQUIRED ? WARN : MUTED);
        headline.setFont(headline.getFont().deriveFont(Font.BOLD));
        text.add(headline);

        if (status.overall() == PublicationStatus.Overall.SETTING_UP
                && status.actionText() == null && !"Checking…".equals(status.headline())) {
            text.add(mutedLine("No action required."));
        }
        if (status.actionText() != null) {
            text.add(Box.createVerticalStrut(3));
            for (String line : status.actionText().split("\n")) {
                text.add(mutedLine(line));
            }
        }
        if (status.overall() == PublicationStatus.Overall.ACTION_REQUIRED) {
            text.add(Box.createVerticalStrut(6));
            JPanel actionButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            actionButtons.setOpaque(false);
            actionButtons.setAlignmentX(LEFT_ALIGNMENT);
            if (status.copyText() != null) {
                JButton copy = new JButton("Copy record");
                copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(status.copyText()), null));
                actionButtons.add(copy);
            }
            JButton check = new JButton("Check again");
            check.addActionListener(e -> actions.checkAgain());
            actionButtons.add(check);
            text.add(actionButtons);
        }

        // Teilstatus direkt auf der Karte, solange nicht alles läuft.
        if (status.overall() != PublicationStatus.Overall.LIVE && !status.subStatuses().isEmpty()) {
            text.add(Box.createVerticalStrut(8));
            for (PublicationStatus.SubStatus sub : status.subStatuses()) {
                JLabel row = new JLabel(subSymbol(sub.state()) + "  "
                        + String.format("%-10s", sub.label()) + "  " + sub.detail());
                row.setFont(row.getFont().deriveFont(row.getFont().getSize() - 1f));
                row.setForeground(sub.state() == PublicationStatus.SubState.WARN ? WARN : MUTED);
                text.add(row);
            }
        }
        add(text, BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout());
        right.setOpaque(false);
        JLabel badge = new JLabel(badgeText(status.overall()));
        badge.setForeground(badgeColor(status.overall()));
        badge.setHorizontalAlignment(JLabel.RIGHT);
        right.add(badge, BorderLayout.NORTH);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        JButton edit = new JButton("Edit");
        edit.addActionListener(e -> actions.edit());
        JButton more = new JButton("⋮");
        more.setMargin(new java.awt.Insets(2, 8, 2, 8));
        JPopupMenu menu = new JPopupMenu();
        JMenuItem details = new JMenuItem("Status details");
        details.addActionListener(e -> actions.details());
        JMenuItem remove = new JMenuItem("Remove…");
        remove.addActionListener(e -> actions.remove());
        menu.add(details);
        menu.addSeparator();
        menu.add(remove);
        more.addActionListener(e -> menu.show(more, 0, more.getHeight()));
        buttons.add(edit);
        buttons.add(more);
        right.add(buttons, BorderLayout.SOUTH);
        add(right, BorderLayout.EAST);

        setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height + 8));
    }

    private static JLabel mutedLine(String line) {
        JLabel label = new JLabel(line);
        label.setForeground(MUTED);
        return label;
    }

    private static String subSymbol(PublicationStatus.SubState state) {
        return switch (state) {
            case OK -> "✓";
            case WARN -> "⚠";
            case PENDING -> "◌";
            case OFF -> "○";
        };
    }

    private static String badgeText(PublicationStatus.Overall overall) {
        return switch (overall) {
            case LIVE -> "● Live";
            case SETTING_UP -> "◌ Setting up";
            case ACTION_REQUIRED -> "⚠ Action required";
            case STOPPED -> "○ Stopped";
        };
    }

    private static Color badgeColor(PublicationStatus.Overall overall) {
        return switch (overall) {
            case LIVE -> LIVE;
            case ACTION_REQUIRED -> WARN;
            case SETTING_UP, STOPPED -> MUTED;
        };
    }
}

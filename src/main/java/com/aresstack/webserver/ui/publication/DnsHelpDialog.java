package com.aresstack.webserver.ui.publication;

import com.aresstack.webserver.ui.status.PublicationStatus;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;

/**
 * Kompakte Hilfe: wo die CNAME-Werte beim DNS-Provider eingetragen werden
 * und warum der CNAME auf die DynDNS-Basisdomain zeigt. Es wird niemals
 * eine IP-Adresse verlangt.
 */
class DnsHelpDialog extends JDialog {

    private static final Color MUTED = new Color(0x757575);

    private DnsHelpDialog(Window owner, PublicationStatus.DnsRecord record) {
        super(owner, "DNS setup", ModalityType.APPLICATION_MODAL);
        String host = record.hostname() + "." + record.target();

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(14, 18, 8, 18));

        content.add(line("Open the DNS settings for " + record.target() + " at your domain", MUTED, false));
        content.add(line("provider and add a new record. Enter exactly these values:", MUTED, false));
        content.add(gap(10));
        content.add(line("Record type", MUTED, false));
        content.add(line("CNAME", null, true));
        content.add(gap(8));
        content.add(line("Hostname / Name", MUTED, false));
        content.add(line(record.hostname(), null, true));
        content.add(gap(8));
        content.add(line("Target / Points to", MUTED, false));
        content.add(line(record.target(), null, true));
        content.add(gap(8));
        content.add(line("TTL", MUTED, false));
        content.add(line("Leave the provider's default value.", null, false));
        content.add(gap(12));
        content.add(line("Why?", MUTED, false));
        content.add(line(host + " should use the same public address as", null, false));
        content.add(line(record.target() + ". That address is kept up to date automatically", null, false));
        content.add(line("by Dynamic DNS, so the subdomain follows it automatically.", null, false));
        content.add(gap(6));
        content.add(line("You do not need to enter an IP address.", null, false));

        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttons.add(close);

        getContentPane().add(content, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(close);
        pack();
        setLocationRelativeTo(owner);
    }

    private static JLabel line(String text, Color color, boolean bold) {
        JLabel label = new JLabel(text);
        if (color != null) {
            label.setForeground(color);
        }
        if (bold) {
            label.setFont(label.getFont().deriveFont(Font.BOLD));
        }
        return label;
    }

    private static java.awt.Component gap(int height) {
        return javax.swing.Box.createVerticalStrut(height);
    }

    static void open(Window owner, PublicationStatus.DnsRecord record) {
        new DnsHelpDialog(owner, record).setVisible(true);
    }
}

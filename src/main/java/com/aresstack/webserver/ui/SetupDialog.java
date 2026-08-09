package com.aresstack.webserver.ui;

import com.aresstack.webserver.domain.AcmeConfiguration;
import com.aresstack.webserver.domain.DomainName;
import com.aresstack.webserver.domain.Site;
import com.aresstack.webserver.domain.Upstream;
import com.aresstack.webserver.domain.WebServerConfiguration;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Optional;

/**
 * Einrichtungsdialog beim ersten Start: erzeugt aus Primärdomain, Standard-
 * ziel und ACME-Mail die initiale Konfiguration statt mit einem Fehler
 * abzubrechen.
 */
public class SetupDialog extends JDialog {

    private final JTextField domainField = new JTextField("aresstack.de", 24);
    private final JTextField emailField = new JTextField(24);
    private final JTextField hostField = new JTextField("localhost", 20);
    private final JTextField portField = new JTextField("8080", 6);
    private final JCheckBox httpsEnabled = new JCheckBox("Enable HTTPS automatically", true);

    private WebServerConfiguration result;

    private SetupDialog() {
        super((java.awt.Window) null, "Welcome to AresStack Webserver", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JLabel title = new JLabel("Welcome to AresStack Webserver");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 4, 12));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 12, 4, 12);
        c.anchor = GridBagConstraints.WEST;
        int row = 0;
        RouteDialog.addRow(form, c, row++, "Primary domain", domainField);
        RouteDialog.addRow(form, c, row++, "Let's Encrypt email", emailField);
        RouteDialog.addRow(form, c, row++, "Default destination host", hostField);
        RouteDialog.addRow(form, c, row++, "Port", portField);
        c.gridx = 1;
        c.gridy = row++;
        form.add(httpsEnabled, c);

        JButton start = new JButton("Start Webserver");
        start.addActionListener(e -> save());
        JPanel buttons = new JPanel();
        buttons.add(start);

        getContentPane().add(title, BorderLayout.NORTH);
        getContentPane().add(form, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(start);
        pack();
        setLocationRelativeTo(null);
    }

    private void save() {
        String email = emailField.getText().trim();
        if (email.isBlank() || !email.contains("@")) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    "Please enter your email address.\n\n"
                            + "Let's Encrypt requires it to issue HTTPS certificates\n"
                            + "(it is used for expiry warnings, not for marketing).",
                    "Email required", javax.swing.JOptionPane.WARNING_MESSAGE);
            emailField.requestFocusInWindow();
            return;
        }
        try {
            DomainName domain = new DomainName(domainField.getText());
            Upstream defaultUpstream = Upstream.parse(
                    "http://" + hostField.getText().trim() + ":" + portField.getText().trim());
            Site primarySite = new Site(domain, Optional.empty(), List.of(), httpsEnabled.isSelected());
            result = new WebServerConfiguration(
                    domain,
                    AcmeConfiguration.letsEncrypt(email),
                    defaultUpstream,
                    List.of(primarySite));
            result.validate();
            dispose();
        } catch (RuntimeException e) {
            FriendlyErrors.show(this, "Setup", e);
        }
    }

    /** Zeigt den Dialog modal; {@code null}, wenn der Benutzer abbricht. */
    public static WebServerConfiguration showSetup() {
        SetupDialog dialog = new SetupDialog();
        dialog.setVisible(true);
        return dialog.result;
    }
}

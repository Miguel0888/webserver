package com.aresstack.webserver.ui;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;

/**
 * Globale Servereinstellungen. Ports sind in Version 1 fest (80/443);
 * veränderbar ist die Let's-Encrypt-Kontaktadresse.
 */
class SettingsDialog extends JDialog {

    private SettingsDialog(Window owner, WebServerController controller) {
        super(owner, "Server Settings", ModalityType.APPLICATION_MODAL);

        JTextField emailField = new JTextField(controller.configuration().acme().email(), 24);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 12, 4, 12);
        c.anchor = GridBagConstraints.WEST;
        int row = 0;
        RouteDialog.addRow(form, c, row++, "Primary domain",
                new JLabel(controller.configuration().domain().value()));
        RouteDialog.addRow(form, c, row++, "HTTP port", new JLabel("80"));
        RouteDialog.addRow(form, c, row++, "HTTPS port", new JLabel("443"));
        RouteDialog.addRow(form, c, row++, "Default destination",
                new JLabel(controller.configuration().defaultUpstream().toString()));
        RouteDialog.addRow(form, c, row++, "Let's Encrypt account email", emailField);
        RouteDialog.addRow(form, c, row++, "Certificate management", new JLabel("Automatic"));

        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        JButton save = new JButton("Save");
        save.addActionListener(e -> {
            try {
                String email = emailField.getText().trim();
                if (!email.equals(controller.configuration().acme().email())) {
                    controller.updateAcmeEmail(email);
                }
                dispose();
            } catch (RuntimeException ex) {
                FriendlyErrors.show(this, "Server Settings", ex);
            }
        });
        JPanel buttons = new JPanel();
        buttons.add(cancel);
        buttons.add(save);

        getContentPane().add(form, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(save);
        pack();
        setLocationRelativeTo(owner);
    }

    static void show(Window owner, WebServerController controller) {
        new SettingsDialog(owner, controller).setVisible(true);
    }
}

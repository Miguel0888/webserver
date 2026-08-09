package com.aresstack.webserver.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
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
 * Globale Servereinstellungen (§13): ACME-Mail und Autostart-Optionen;
 * die Ports liegen im Advanced-Bereich, weil sie normalerweise nicht
 * verändert werden müssen.
 */
class SettingsDialog extends JDialog {

    private SettingsDialog(Window owner, WebServerController controller) {
        super(owner, "Server Settings", ModalityType.APPLICATION_MODAL);

        JTextField emailField = new JTextField(controller.configuration().acme().email(), 24);
        JCheckBox autostartServer = new JCheckBox(
                "Start automatically with application", AppPreferences.autostartServer());
        boolean windowsSupported = WindowsAutostart.isSupported(controller.directories().root());
        JCheckBox startWithWindows = new JCheckBox("Start with Windows",
                windowsSupported && WindowsAutostart.isEnabled());
        startWithWindows.setEnabled(windowsSupported);
        if (!windowsSupported) {
            startWithWindows.setToolTipText(
                    "Available in the installed application (bin\\webserver.bat).");
        }
        JTextField httpPortField = new JTextField(
                String.valueOf(controller.configuration().httpPort()), 6);
        JTextField httpsPortField = new JTextField(
                String.valueOf(controller.configuration().httpsPort()), 6);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 12, 4, 12);
        c.anchor = GridBagConstraints.WEST;
        int row = 0;
        Forms.addRow(form, c, row++, "Let's Encrypt email (optional)", emailField);
        Forms.addRow(form, c, row++, "Certificate management", new JLabel("Automatic"));
        c.gridx = 1;
        c.gridy = row++;
        form.add(autostartServer, c);
        c.gridx = 1;
        c.gridy = row++;
        form.add(startWithWindows, c);

        JPanel advanced = new JPanel(new GridBagLayout());
        advanced.setBorder(BorderFactory.createTitledBorder("Advanced"));
        GridBagConstraints a = new GridBagConstraints();
        a.insets = new Insets(4, 12, 4, 12);
        a.anchor = GridBagConstraints.WEST;
        Forms.addRow(advanced, a, 0, "HTTP port", httpPortField);
        Forms.addRow(advanced, a, 1, "HTTPS port", httpsPortField);
        a.gridx = 0;
        a.gridy = 2;
        a.gridwidth = 2;
        advanced.add(new JLabel("<html><small>Changing ports usually also requires changing"
                + " the router port forwarding.<br>Let's Encrypt requires ports 80 and 443"
                + " to be reachable from the internet.</small></html>"), a);

        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        JButton save = new JButton("Save");
        save.addActionListener(e -> {
            try {
                String email = emailField.getText().trim();
                if (!email.equals(controller.configuration().acme().email())) {
                    controller.updateAcmeEmail(email);
                }
                int httpPort = Integer.parseInt(httpPortField.getText().trim());
                int httpsPort = Integer.parseInt(httpsPortField.getText().trim());
                if (httpPort != controller.configuration().httpPort()
                        || httpsPort != controller.configuration().httpsPort()) {
                    controller.updatePorts(httpPort, httpsPort);
                }
                AppPreferences.setAutostartServer(autostartServer.isSelected());
                if (windowsSupported) {
                    if (startWithWindows.isSelected()) {
                        WindowsAutostart.enable(controller.directories().root());
                    } else {
                        WindowsAutostart.disable();
                    }
                }
                dispose();
            } catch (NumberFormatException ex) {
                FriendlyErrors.show(this, "Server Settings",
                        new IllegalArgumentException("Ports must be numbers between 1 and 65535."));
            } catch (RuntimeException ex) {
                FriendlyErrors.show(this, "Server Settings", ex);
            }
        });
        JPanel buttons = new JPanel();
        buttons.add(cancel);
        buttons.add(save);

        JPanel content = new JPanel(new BorderLayout());
        content.add(form, BorderLayout.NORTH);
        content.add(advanced, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(save);
        pack();
        setLocationRelativeTo(owner);
    }

    static void show(Window owner, WebServerController controller) {
        new SettingsDialog(owner, controller).setVisible(true);
    }
}

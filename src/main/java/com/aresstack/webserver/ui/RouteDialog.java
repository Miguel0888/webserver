package com.aresstack.webserver.ui;

import com.aresstack.webserver.domain.Route;
import com.aresstack.webserver.domain.Upstream;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;

/**
 * Dialog für eine Pfadregel: Pfad, Zielhost, Zielport, Backend-Protokoll.
 */
class RouteDialog extends JDialog {

    private final JTextField pathField = new JTextField("/", 20);
    private final JTextField hostField = new JTextField("localhost", 20);
    private final JTextField portField = new JTextField("8080", 6);
    private final JRadioButton httpButton = new JRadioButton("HTTP", true);
    private final JRadioButton httpsButton = new JRadioButton("HTTPS");

    private Route result;

    private RouteDialog(Window owner, Route existing) {
        super(owner, existing == null ? "Add Route" : "Edit Route", ModalityType.APPLICATION_MODAL);
        if (existing != null) {
            pathField.setText(existing.pathMatcher());
            hostField.setText(existing.upstream().host());
            portField.setText(String.valueOf(existing.upstream().port()));
            httpsButton.setSelected(existing.upstream().scheme().equals("https"));
            httpButton.setSelected(!httpsButton.isSelected());
        }
        ButtonGroup protocol = new ButtonGroup();
        protocol.add(httpButton);
        protocol.add(httpsButton);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 8, 4, 8);
        c.anchor = GridBagConstraints.WEST;
        int row = 0;
        addRow(form, c, row++, "Path", pathField);
        addRow(form, c, row++, "Destination host", hostField);
        addRow(form, c, row++, "Port", portField);
        JPanel protocolPanel = new JPanel();
        protocolPanel.add(httpButton);
        protocolPanel.add(httpsButton);
        addRow(form, c, row++, "Backend protocol", protocolPanel);

        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        JButton save = new JButton("Save");
        save.addActionListener(e -> save());
        JPanel buttons = new JPanel();
        buttons.add(cancel);
        buttons.add(save);

        getContentPane().add(form, "Center");
        getContentPane().add(buttons, "South");
        getRootPane().setDefaultButton(save);
        pack();
        setLocationRelativeTo(owner);
    }

    private void save() {
        try {
            String scheme = httpsButton.isSelected() ? "https" : "http";
            Upstream upstream = Upstream.parse(
                    scheme + "://" + hostField.getText().trim() + ":" + portField.getText().trim());
            result = new Route(pathField.getText().trim(), upstream);
            dispose();
        } catch (RuntimeException e) {
            FriendlyErrors.show(this, "Invalid route", e);
        }
    }

    static void addRow(JPanel panel, GridBagConstraints c, int row, String label, java.awt.Component field) {
        c.gridx = 0;
        c.gridy = row;
        panel.add(new JLabel(label), c);
        c.gridx = 1;
        panel.add(field, c);
    }

    /** Zeigt den Dialog modal; {@code null} bei Abbruch. */
    static Route show(Window owner, Route existing) {
        RouteDialog dialog = new RouteDialog(owner, existing);
        dialog.setVisible(true);
        return dialog.result;
    }
}

package com.aresstack.webserver.ui;

import com.aresstack.webserver.domain.DomainName;
import com.aresstack.webserver.domain.Route;
import com.aresstack.webserver.domain.Site;
import com.aresstack.webserver.domain.Upstream;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Dialog zum Anlegen und Bearbeiten einer veröffentlichten Domain:
 * Domainname, Ziel, Backend-Protokoll, HTTPS und Pfadrouten.
 */
class DomainDialog extends JDialog {

    private final JTextField domainField = new JTextField(24);
    private final JCheckBox useDefaultDestination = new JCheckBox("Use server default destination");
    private final JTextField hostField = new JTextField("localhost", 20);
    private final JTextField portField = new JTextField("8080", 6);
    private final JRadioButton httpButton = new JRadioButton("HTTP", true);
    private final JRadioButton httpsBackendButton = new JRadioButton("HTTPS");
    private final JCheckBox httpsEnabled = new JCheckBox("Enable HTTPS", true);
    private final DefaultListModel<Route> routes = new DefaultListModel<>();
    private final JList<Route> routeList = new JList<>(routes);

    private Site result;

    private DomainDialog(Window owner, Site existing, Upstream defaultUpstream) {
        super(owner, existing == null ? "Add Domain" : "Edit Domain", ModalityType.APPLICATION_MODAL);
        ButtonGroup protocol = new ButtonGroup();
        protocol.add(httpButton);
        protocol.add(httpsBackendButton);

        useDefaultDestination.addActionListener(e -> {
            boolean custom = !useDefaultDestination.isSelected();
            hostField.setEnabled(custom);
            portField.setEnabled(custom);
            httpButton.setEnabled(custom);
            httpsBackendButton.setEnabled(custom);
        });

        if (existing != null) {
            domainField.setText(existing.host().value());
            httpsEnabled.setSelected(existing.httpsEnabled());
            existing.routes().forEach(routes::addElement);
            if (existing.upstream().isPresent()) {
                Upstream upstream = existing.upstream().get();
                hostField.setText(upstream.host());
                portField.setText(String.valueOf(upstream.port()));
                httpsBackendButton.setSelected(upstream.scheme().equals("https"));
                httpButton.setSelected(!httpsBackendButton.isSelected());
            } else {
                useDefaultDestination.setSelected(true);
                useDefaultDestination.getActionListeners()[0].actionPerformed(null);
            }
        }

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 8, 4, 8);
        c.anchor = GridBagConstraints.WEST;
        int row = 0;
        RouteDialog.addRow(form, c, row++, "Domain", domainField);
        c.gridx = 1;
        c.gridy = row++;
        useDefaultDestination.setToolTipText("Forward to the server default: " + defaultUpstream);
        form.add(useDefaultDestination, c);
        RouteDialog.addRow(form, c, row++, "Destination host", hostField);
        RouteDialog.addRow(form, c, row++, "Port", portField);
        JPanel protocolPanel = new JPanel();
        protocolPanel.add(httpButton);
        protocolPanel.add(httpsBackendButton);
        RouteDialog.addRow(form, c, row++, "Backend protocol", protocolPanel);
        c.gridx = 1;
        c.gridy = row++;
        form.add(httpsEnabled, c);
        c.gridx = 1;
        c.gridy = row++;
        form.add(new JLabel("<html><small>Certificate: Automatic — Provider: Let's Encrypt — Renewal: Automatic</small></html>"), c);

        routeList.setCellRenderer((list, route, index, selected, focus) -> {
            JLabel label = new JLabel(route.pathMatcher() + "    →    " + route.upstream().toCaddyAddress());
            label.setOpaque(true);
            label.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            label.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            return label;
        });
        JScrollPane routeScroll = new JScrollPane(routeList);
        routeScroll.setPreferredSize(new Dimension(380, 90));
        JButton addRoute = new JButton("+ Add Route");
        addRoute.addActionListener(e -> {
            Route route = RouteDialog.show(this, null);
            if (route != null) {
                routes.addElement(route);
            }
        });
        JButton editRoute = new JButton("Edit");
        editRoute.addActionListener(e -> {
            int index = routeList.getSelectedIndex();
            if (index >= 0) {
                Route route = RouteDialog.show(this, routes.get(index));
                if (route != null) {
                    routes.set(index, route);
                }
            }
        });
        JButton removeRoute = new JButton("Remove");
        removeRoute.addActionListener(e -> {
            int index = routeList.getSelectedIndex();
            if (index >= 0) {
                routes.remove(index);
            }
        });
        JPanel routeButtons = new JPanel();
        routeButtons.add(addRoute);
        routeButtons.add(editRoute);
        routeButtons.add(removeRoute);
        JPanel routesPanel = new JPanel(new BorderLayout(4, 4));
        routesPanel.setBorder(BorderFactory.createTitledBorder("Routing (optional)"));
        routesPanel.add(routeScroll, BorderLayout.CENTER);
        routesPanel.add(routeButtons, BorderLayout.SOUTH);

        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        JButton save = new JButton("Save");
        save.addActionListener(e -> save());
        JPanel buttons = new JPanel();
        buttons.add(cancel);
        buttons.add(save);

        JPanel content = new JPanel(new BorderLayout());
        content.add(form, BorderLayout.NORTH);
        content.add(routesPanel, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(save);
        pack();
        setLocationRelativeTo(owner);
    }

    private void save() {
        try {
            Optional<Upstream> upstream;
            if (useDefaultDestination.isSelected()) {
                upstream = Optional.empty();
            } else {
                String scheme = httpsBackendButton.isSelected() ? "https" : "http";
                upstream = Optional.of(Upstream.parse(
                        scheme + "://" + hostField.getText().trim() + ":" + portField.getText().trim()));
            }
            List<Route> routeItems = new ArrayList<>();
            for (int i = 0; i < routes.size(); i++) {
                routeItems.add(routes.get(i));
            }
            result = new Site(new DomainName(domainField.getText()), upstream, routeItems,
                    httpsEnabled.isSelected());
            dispose();
        } catch (RuntimeException e) {
            FriendlyErrors.show(this, "Invalid domain", e);
        }
    }

    /** Zeigt den Dialog modal; {@code null} bei Abbruch. */
    static Site show(Window owner, Site existing, Upstream defaultUpstream) {
        DomainDialog dialog = new DomainDialog(owner, existing, defaultUpstream);
        dialog.setVisible(true);
        return dialog.result;
    }
}

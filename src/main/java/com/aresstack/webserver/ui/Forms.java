package com.aresstack.webserver.ui;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.GridBagConstraints;

/**
 * Kleine Formular-Helfer für GridBagLayout-Dialoge.
 */
public final class Forms {

    private Forms() {
    }

    public static void addRow(JPanel panel, GridBagConstraints c, int row, String label, Component field) {
        c.gridx = 0;
        c.gridy = row;
        panel.add(new JLabel(label), c);
        c.gridx = 1;
        panel.add(field, c);
    }
}

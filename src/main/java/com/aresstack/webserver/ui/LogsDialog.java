package com.aresstack.webserver.ui;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Aktivitätslog für den Benutzer plus technisches Caddy-Log für die
 * Fehleranalyse.
 */
class LogsDialog extends JDialog {

    private final JTextArea technicalArea = new JTextArea();
    private final Path technicalLog;

    private LogsDialog(java.awt.Window owner, UserLog log, Path technicalLog) {
        super(owner, "Logs", ModalityType.MODELESS);
        this.technicalLog = technicalLog;

        JTextArea activityArea = new JTextArea(String.join("\n", log.entries()));
        activityArea.setEditable(false);
        log.addListener(entry -> {
            activityArea.append((activityArea.getText().isEmpty() ? "" : "\n") + entry);
            activityArea.setCaretPosition(activityArea.getDocument().getLength());
        });

        technicalArea.setEditable(false);
        loadTechnicalLog();
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> loadTechnicalLog());
        JPanel technicalPanel = new JPanel(new BorderLayout());
        technicalPanel.add(new JScrollPane(technicalArea), BorderLayout.CENTER);
        JPanel technicalButtons = new JPanel();
        technicalButtons.add(refresh);
        technicalPanel.add(technicalButtons, BorderLayout.SOUTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Activity", new JScrollPane(activityArea));
        tabs.addTab("Technical log", technicalPanel);

        getContentPane().add(tabs, BorderLayout.CENTER);
        setPreferredSize(new Dimension(720, 420));
        pack();
        setLocationRelativeTo(owner);
    }

    private void loadTechnicalLog() {
        try {
            if (Files.exists(technicalLog)) {
                List<String> lines = Files.readAllLines(technicalLog);
                List<String> tail = lines.subList(Math.max(0, lines.size() - 500), lines.size());
                technicalArea.setText(String.join("\n", tail));
            } else {
                technicalArea.setText("No technical log yet: " + technicalLog);
            }
            technicalArea.setCaretPosition(technicalArea.getDocument().getLength());
        } catch (IOException e) {
            technicalArea.setText("Cannot read " + technicalLog + ": " + e.getMessage());
        }
    }

    static void show(java.awt.Window owner, UserLog log, Path technicalLog) {
        new LogsDialog(owner, log, technicalLog).setVisible(true);
    }
}

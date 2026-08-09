package com.aresstack.webserver.ui;

import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.Component;
import java.awt.Dimension;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Locale;

/**
 * Übersetzt technische Fehler in verständliche Meldungen; die technischen
 * Details bleiben über "Show details" erreichbar.
 */
public final class FriendlyErrors {

    private FriendlyErrors() {
    }

    public static String describe(Throwable error) {
        String text = allMessages(error).toLowerCase(Locale.ROOT);
        if (text.contains("address already in use") || text.contains("bind:")
                || text.contains("only one usage of each socket address")) {
            return "Port 80 or 443 is already being used by another application.\n"
                    + "Close the other application or change its port, then try again.";
        }
        if (text.contains("acme") || text.contains("challenge") || text.contains("letsencrypt")
                || text.contains("let's encrypt")) {
            return "HTTPS could not be enabled.\n\n"
                    + "Let's Encrypt could not verify the domain.\n"
                    + "Check that the DNS record points to this server and that\n"
                    + "ports 80 and 443 are forwarded to this computer.";
        }
        if (text.contains("connection refused") && text.contains("2019")) {
            return "The webserver engine is not reachable.\nTry restarting the server.";
        }
        if (text.contains("timed out")) {
            return "The operation timed out. Check the network connection and try again.";
        }
        String message = firstMessage(error);
        return message != null ? message : error.getClass().getSimpleName();
    }

    public static void show(Component parent, String title, Throwable error) {
        Object[] options = {"OK", "Show details"};
        int choice = JOptionPane.showOptionDialog(parent, describe(error), title,
                JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null, options, options[0]);
        if (choice == 1) {
            StringWriter details = new StringWriter();
            error.printStackTrace(new PrintWriter(details));
            JTextArea area = new JTextArea(details.toString(), 20, 80);
            area.setEditable(false);
            JScrollPane scroll = new JScrollPane(area);
            scroll.setPreferredSize(new Dimension(700, 400));
            JOptionPane.showMessageDialog(parent, scroll, title + " — details",
                    JOptionPane.PLAIN_MESSAGE);
        }
    }

    private static String allMessages(Throwable error) {
        StringBuilder all = new StringBuilder();
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t.getMessage() != null) {
                all.append(t.getMessage()).append('\n');
            }
        }
        return all.toString();
    }

    private static String firstMessage(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t.getMessage() != null && !t.getMessage().isBlank()) {
                return t.getMessage();
            }
        }
        return null;
    }
}

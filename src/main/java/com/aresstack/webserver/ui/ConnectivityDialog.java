package com.aresstack.webserver.ui;

import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;

/**
 * Zeigt die Internet-Erreichbarkeit einer Domain (§15/§16): DNS, Ports und
 * Zertifikat, mit verständlicher Anleitung bei Abweichungen.
 */
class ConnectivityDialog extends JDialog {

    private final JTextArea area = new JTextArea("Checking internet access for the domain…\n\n"
            + "Determining the public address and testing DNS and ports.");

    private ConnectivityDialog(Window owner, String host, int httpPort, int httpsPort,
                               String certificateLabel) {
        super(owner, "Internet access — " + host, ModalityType.MODELESS);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setMargin(new java.awt.Insets(10, 12, 10, 12));
        getContentPane().add(new JScrollPane(area), BorderLayout.CENTER);
        setPreferredSize(new Dimension(560, 420));
        pack();
        setLocationRelativeTo(owner);

        new SwingWorker<ConnectivityCheck.Result, Void>() {
            @Override
            protected ConnectivityCheck.Result doInBackground() {
                return ConnectivityCheck.run(host, httpPort, httpsPort);
            }

            @Override
            protected void done() {
                try {
                    area.setText(render(get(), httpPort, httpsPort, certificateLabel));
                    area.setCaretPosition(0);
                } catch (Exception e) {
                    area.setText("The check could not be completed.\n\n"
                            + FriendlyErrors.describe(e.getCause() != null ? e.getCause() : e));
                }
            }
        }.execute();
    }

    private static String render(ConnectivityCheck.Result result, int httpPort, int httpsPort,
                                 String certificateLabel) {
        StringBuilder out = new StringBuilder();
        out.append("Internet access\n\n");

        out.append("DNS\n");
        if (result.dnsAddresses().isEmpty()) {
            out.append(result.host()).append("\n→ no DNS record found\n");
        } else {
            out.append(result.host()).append("\n→ ")
                    .append(String.join(", ", result.dnsAddresses())).append('\n');
        }
        out.append("\nCurrent public address\n→ ")
                .append(result.publicAddress() != null ? result.publicAddress() : "could not be determined")
                .append("\n\nStatus\n");
        if (result.dnsMatches() == null) {
            out.append("?  Could not be verified\n");
        } else if (result.dnsMatches()) {
            out.append("✓ Correct\n");
        } else {
            out.append("✗ DNS does not point to this server.\n\n");
            out.append("Update the DNS record for ").append(result.host()).append('\n');
            out.append("so that it points to ").append(result.publicAddress()).append(".\n");
        }

        out.append("\nServer ports on this computer\n");
        out.append(line("HTTP port " + httpPort, result.serverListensHttp()));
        out.append(line("HTTPS port " + httpsPort, result.serverListensHttps()));
        if (!result.serverListensHttp() || !result.serverListensHttps()) {
            out.append("\nThe webserver is not listening on this port.\n");
            out.append("Start the server, or check whether another application uses the port.\n");
        }

        out.append("\nReachable from the internet\n");
        out.append(optionalLine("HTTP port " + httpPort, result.publicHttpReachable()));
        out.append(optionalLine("HTTPS port " + httpsPort, result.publicHttpsReachable()));
        if (result.publicHttpReachable() == null || result.publicHttpsReachable() == null) {
            out.append("\n\"?\" means the test was inconclusive: many routers do not\n");
            out.append("allow connections to their own public address from inside.\n");
            out.append("Check that ports ").append(httpPort).append(" and ").append(httpsPort)
                    .append(" are forwarded to this computer.\n");
        }

        out.append("\nCertificate\n").append(certificateLabel).append('\n');
        return out.toString();
    }

    private static String line(String label, boolean ok) {
        return (ok ? "✓ " : "✗ ") + label + '\n';
    }

    private static String optionalLine(String label, Boolean ok) {
        return (ok == null ? "?  " : ok ? "✓ " : "✗ ") + label + '\n';
    }

    static void show(Window owner, String host, int httpPort, int httpsPort, String certificateLabel) {
        new ConnectivityDialog(owner, host, httpPort, httpsPort, certificateLabel).setVisible(true);
    }
}

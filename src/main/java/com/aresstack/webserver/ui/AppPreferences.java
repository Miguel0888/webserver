package com.aresstack.webserver.ui;

import java.util.prefs.Preferences;

/**
 * Anwendungseinstellungen, die kein Teil der Serverkonfiguration sind
 * (z.B. ob der Server mit der Anwendung startet, DynDNS-Zugangsdaten).
 */
public final class AppPreferences {

    private static final Preferences NODE =
            Preferences.userRoot().node("com/aresstack/webserver");
    private static final String AUTOSTART_SERVER = "autostartServer";
    private static final String DYNDNS_ENABLED = "dynDnsEnabled";
    private static final String DYNDNS_UPDATE_URL = "dynDnsUpdateUrl";
    private static final String DYNDNS_DOMAIN = "dynDnsDomain";
    private static final String DYNDNS_USERNAME = "dynDnsUsername";
    private static final String DYNDNS_PASSWORD = "dynDnsPassword";

    private AppPreferences() {
    }

    public static boolean autostartServer() {
        return NODE.getBoolean(AUTOSTART_SERVER, true);
    }

    public static void setAutostartServer(boolean enabled) {
        NODE.putBoolean(AUTOSTART_SERVER, enabled);
    }

    public static boolean dynDnsEnabled() {
        return NODE.getBoolean(DYNDNS_ENABLED, false);
    }

    public static void setDynDnsEnabled(boolean enabled) {
        NODE.putBoolean(DYNDNS_ENABLED, enabled);
    }

    public static String dynDnsUpdateUrl() {
        return NODE.get(DYNDNS_UPDATE_URL, "");
    }

    public static void setDynDnsUpdateUrl(String url) {
        NODE.put(DYNDNS_UPDATE_URL, url);
    }

    public static String dynDnsDomain() {
        return NODE.get(DYNDNS_DOMAIN, "");
    }

    public static void setDynDnsDomain(String domain) {
        NODE.put(DYNDNS_DOMAIN, domain);
    }

    public static String dynDnsUsername() {
        return NODE.get(DYNDNS_USERNAME, "");
    }

    public static void setDynDnsUsername(String username) {
        NODE.put(DYNDNS_USERNAME, username);
    }

    public static String dynDnsPassword() {
        return NODE.get(DYNDNS_PASSWORD, "");
    }

    public static void setDynDnsPassword(String password) {
        NODE.put(DYNDNS_PASSWORD, password);
    }
}

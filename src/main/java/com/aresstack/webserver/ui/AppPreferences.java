package com.aresstack.webserver.ui;

import java.util.prefs.Preferences;

/**
 * Anwendungseinstellungen, die kein Teil der Serverkonfiguration sind
 * (z.B. ob der Server mit der Anwendung startet).
 */
public final class AppPreferences {

    private static final Preferences NODE =
            Preferences.userRoot().node("com/aresstack/webserver");
    private static final String AUTOSTART_SERVER = "autostartServer";

    private AppPreferences() {
    }

    public static boolean autostartServer() {
        return NODE.getBoolean(AUTOSTART_SERVER, true);
    }

    public static void setAutostartServer(boolean enabled) {
        NODE.putBoolean(AUTOSTART_SERVER, enabled);
    }
}

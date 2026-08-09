package com.aresstack.webserver.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * "Start with Windows" über den HKCU-Run-Schlüssel — benötigt keine
 * Adminrechte und wirkt nur für den aktuellen Benutzer.
 */
public final class WindowsAutostart {

    private static final String KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String VALUE = "AresStackWebserver";

    private WindowsAutostart() {
    }

    /** Das Startskript der installierten Anwendung, sofern vorhanden. */
    public static Path startScript(Path root) {
        return root.resolve("bin").resolve("webserver.bat");
    }

    public static boolean isSupported(Path root) {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
                && Files.exists(startScript(root));
    }

    public static boolean isEnabled() {
        return exec("reg", "query", KEY, "/v", VALUE) == 0;
    }

    public static void enable(Path root) {
        Path script = startScript(root);
        int exit = exec("reg", "add", KEY, "/v", VALUE, "/t", "REG_SZ",
                "/d", "\"" + script + "\"", "/f");
        if (exit != 0) {
            throw new IllegalStateException("Could not register autostart (reg add exit " + exit + ")");
        }
    }

    public static void disable() {
        int exit = exec("reg", "delete", KEY, "/v", VALUE, "/f");
        if (exit != 0 && isEnabled()) {
            throw new IllegalStateException("Could not remove autostart (reg delete exit " + exit + ")");
        }
    }

    private static int exec(String... command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            // Ausgabe abnehmen, damit reg.exe nicht an einem vollen Puffer hängt.
            process.getInputStream().readAllBytes();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return -1;
            }
            return process.exitValue();
        } catch (IOException e) {
            return -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

}

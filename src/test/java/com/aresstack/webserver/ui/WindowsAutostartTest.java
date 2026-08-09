package com.aresstack.webserver.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Schreibt real in HKCU\...\Run und räumt danach wieder auf. Läuft nur auf
 * Windows und nur, wenn der Benutzer den Eintrag nicht bereits selbst nutzt.
 */
@EnabledOnOs(OS.WINDOWS)
class WindowsAutostartTest {

    @Test
    void enableQueryDisableRoundTrip(@TempDir Path root) throws Exception {
        assumeFalse(WindowsAutostart.isEnabled(),
                "Autostart entry already present — not touching a real installation");

        Path bin = root.resolve("bin");
        Files.createDirectories(bin);
        Files.writeString(bin.resolve("webserver.bat"), "@echo off\r\n");
        assertTrue(WindowsAutostart.isSupported(root));

        try {
            WindowsAutostart.enable(root);
            assertTrue(WindowsAutostart.isEnabled());
        } finally {
            WindowsAutostart.disable();
        }
        assertFalse(WindowsAutostart.isEnabled());
    }

    @Test
    void notSupportedWithoutInstalledStartScript(@TempDir Path root) {
        assertFalse(WindowsAutostart.isSupported(root));
    }
}

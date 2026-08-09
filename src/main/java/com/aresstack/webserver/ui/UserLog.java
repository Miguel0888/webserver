package com.aresstack.webserver.ui;

import javax.swing.SwingUtilities;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Benutzerorientiertes Aktivitätslog ("15:02  Added askai.aresstack.de").
 * Technische Meldungen gehören nicht hierher, sondern ins Caddy-Log.
 */
public class UserLog {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final List<String> entries = new ArrayList<>();
    private final List<Consumer<String>> listeners = new ArrayList<>();

    public synchronized void info(String message) {
        String entry = LocalTime.now().format(TIME) + "  " + message;
        entries.add(entry);
        for (Consumer<String> listener : listeners) {
            SwingUtilities.invokeLater(() -> listener.accept(entry));
        }
    }

    public synchronized List<String> entries() {
        return List.copyOf(entries);
    }

    public synchronized void addListener(Consumer<String> listener) {
        listeners.add(listener);
    }
}

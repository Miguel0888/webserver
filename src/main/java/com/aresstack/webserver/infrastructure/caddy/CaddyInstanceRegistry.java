package com.aresstack.webserver.infrastructure.caddy;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

/**
 * Persistiert die Identität des von AresStack gestarteten Caddy-Prozesses
 * (PID, Prozessstartzeit, Binary-Pfad) in data/caddy/runtime.json.
 * Nur eine Instanz, deren Identität nachweislich übereinstimmt, darf nach
 * einem Anwendungsneustart adoptiert werden — eine erreichbare Admin-API
 * allein ist kein Ownership-Nachweis.
 */
public class CaddyInstanceRegistry {

    record InstanceRecord(long pid, long processStartMillis, String binaryPath) {
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final Path file;

    public CaddyInstanceRegistry(Path file) {
        this.file = file;
    }

    public void save(Process process, Path binary) {
        long startMillis = process.toHandle().info().startInstant()
                .map(Instant::toEpochMilli)
                .orElse(-1L);
        InstanceRecord record = new InstanceRecord(
                process.pid(), startMillis, canonical(binary));
        try {
            Files.createDirectories(file.getParent());
            mapper.writeValue(file.toFile(), record);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }

    public void delete() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            // Eine nicht löschbare Registry blockiert nichts Weiteres.
        }
    }

    public Optional<Long> registeredPid() {
        return load().map(InstanceRecord::pid);
    }

    /**
     * Lebt der registrierte Prozess noch und stimmt seine Identität
     * (PID, Startzeit, Binary) mit unserem ausgelieferten Caddy überein?
     */
    public boolean matchesLiveProcess(Path expectedBinary) {
        Optional<InstanceRecord> loaded = load();
        if (loaded.isEmpty()) {
            return false;
        }
        InstanceRecord record = loaded.get();
        Optional<ProcessHandle> handle = ProcessHandle.of(record.pid());
        if (handle.isEmpty() || !handle.get().isAlive()) {
            return false;
        }
        long liveStart = handle.get().info().startInstant()
                .map(Instant::toEpochMilli)
                .orElse(-1L);
        if (record.processStartMillis() >= 0 && liveStart >= 0
                && record.processStartMillis() != liveStart) {
            // PID wurde vom Betriebssystem wiederverwendet — fremder Prozess.
            return false;
        }
        return record.binaryPath().equals(canonical(expectedBinary));
    }

    private Optional<InstanceRecord> load() {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(file.toFile(), InstanceRecord.class));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static String canonical(Path binary) {
        try {
            return binary.toRealPath().toString();
        } catch (IOException e) {
            return binary.toAbsolutePath().normalize().toString();
        }
    }
}

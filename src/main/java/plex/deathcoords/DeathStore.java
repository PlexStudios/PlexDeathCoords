package plex.deathcoords;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

final class DeathStore implements AutoCloseable {
    private final Path file;
    private final Logger logger;
    private final Map<UUID, DeathLocation> deaths = new HashMap<>();
    private final Map<UUID, Set<String>> recordKeys = new HashMap<>();
    private final Map<Object, Object> root;
    private final Map<Object, Object> records;
    private final ScheduledThreadPoolExecutor writer;
    private long revision;
    private long savedRevision;
    private boolean queued;
    private boolean closing;
    private boolean failureLogged;

    DeathStore(Path file, Logger logger) throws IOException {
        this.file = file.toAbsolutePath();
        this.logger = logger;
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setCodePointLimit(Integer.MAX_VALUE);
        Object loaded = null;
        try (Reader reader = Files.newBufferedReader(this.file, StandardCharsets.UTF_8)) {
            loaded = new Yaml(new SafeConstructor(options)).load(reader);
        } catch (NoSuchFileException exception) {
            loaded = null;
        } catch (YAMLException exception) {
            throw new IOException("Invalid deaths.yml; the file has been left untouched", exception);
        }
        if (loaded != null && !(loaded instanceof Map<?, ?>)) {
            throw new IOException("deaths.yml must be a YAML mapping; the file has been left untouched");
        }
        root = loaded instanceof Map<?, ?> map ? new LinkedHashMap<>(map) : new LinkedHashMap<>();
        Object section = root.remove("deaths");
        if (section != null && !(section instanceof Map<?, ?>)) {
            throw new IOException("deaths.yml: deaths must be a YAML section; the file has been left untouched");
        }
        Map<?, ?> loadedRecords = section instanceof Map<?, ?> map ? map : Map.of();
        records = new LinkedHashMap<>(loadedRecords);
        int invalid = 0;
        for (Map.Entry<?, ?> entry : loadedRecords.entrySet()) {
            try {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("Death record keys must be UUID strings");
                }
                UUID player = DeathLocation.parseUuid(key);
                recordKeys.computeIfAbsent(player, unused -> new LinkedHashSet<>()).add(key);
                if (!(entry.getValue() instanceof Map<?, ?> record)) {
                    throw new IllegalArgumentException("Death records must be YAML sections");
                }
                DeathLocation death = DeathLocation.read(record);
                DeathLocation previous = deaths.get(player);
                if (previous == null || death.timestamp() > previous.timestamp()) {
                    deaths.put(player, death);
                }
            } catch (IllegalArgumentException exception) {
                invalid++;
            }
        }
        if (invalid > 0) {
            logger.warning("Ignored " + invalid + " invalid death records; their data will be preserved.");
        }
        writer = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "PlexDeathCoords-storage");
            thread.setDaemon(true);
            return thread;
        });
        writer.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        writer.setRemoveOnCancelPolicy(true);
    }

    synchronized DeathLocation get(UUID player) {
        return deaths.get(player);
    }

    synchronized void put(UUID player, DeathLocation death) {
        ensureOpen();
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(death, "death");
        String key = player.toString();
        Map<Object, Object> fields = new LinkedHashMap<>();
        for (String storedKey : recordKeys.getOrDefault(player, Set.of())) {
            Object previous = records.remove(storedKey);
            if (previous instanceof Map<?, ?> map) {
                fields.putAll(map);
            }
        }
        fields.put("world-uuid", death.worldId().toString());
        fields.put("world-name", death.worldName());
        fields.put("x", death.x());
        fields.put("y", death.y());
        fields.put("z", death.z());
        fields.put("timestamp", death.timestamp());
        records.put(key, fields);
        recordKeys.put(player, Set.of(key));
        deaths.put(player, death);
        revision++;
        queueSave(200);
    }

    synchronized boolean clear(UUID player) {
        ensureOpen();
        Objects.requireNonNull(player, "player");
        Set<String> keys = recordKeys.remove(player);
        if (keys == null) {
            return false;
        }
        keys.forEach(records::remove);
        deaths.remove(player);
        revision++;
        queueSave(200);
        return true;
    }

    private void ensureOpen() {
        if (closing) {
            throw new IllegalStateException("Death storage is closed");
        }
    }

    private void queueSave(long delayMillis) {
        if (!queued && !closing) {
            queued = true;
            writer.schedule(this::saveInBackground, delayMillis, TimeUnit.MILLISECONDS);
        }
    }

    private void saveInBackground() {
        boolean failed = false;
        try {
            save();
            if (failureLogged) {
                logger.info("Death storage recovered; pending records were saved.");
                failureLogged = false;
            }
        } catch (IOException | RuntimeException exception) {
            failed = true;
            if (!failureLogged) {
                logger.log(Level.SEVERE, "Could not save deaths.yml. Pending data is retained and will be retried.",
                        exception);
                failureLogged = true;
            }
        } finally {
            synchronized (this) {
                queued = false;
                if (revision != savedRevision) {
                    queueSave(failed ? 5000 : 200);
                }
            }
        }
    }

    private void save() throws IOException {
        Map<Object, Object> snapshot;
        long snapshotRevision;
        synchronized (this) {
            if (revision == savedRevision) {
                return;
            }
            snapshot = new LinkedHashMap<>(records);
            snapshotRevision = revision;
        }
        Map<Object, Object> data = new LinkedHashMap<>(root);
        data.put("deaths", snapshot);
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        byte[] bytes = new Yaml(options).dump(data).getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "deaths-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            synchronized (this) {
                savedRevision = snapshotRevision;
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (this) {
            if (closing) {
                return;
            }
            closing = true;
        }
        writer.shutdown();
        try {
            if (!writer.awaitTermination(30, TimeUnit.SECONDS)) {
                writer.shutdownNow();
                throw new IOException("Death storage did not stop within 30 seconds");
            }
        } catch (InterruptedException exception) {
            writer.shutdownNow();
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while shutting down death storage", exception);
        }
        save();
    }
}

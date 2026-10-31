package plex.deathcoords;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class DeathStoreTest {
    @TempDir
    Path directory;

    private final Logger logger = Logger.getLogger(DeathStoreTest.class.getName());

    @Test
    void persistsLatestDeathWithoutReplacingOtherPlayers() throws Exception {
        Path file = directory.resolve("deaths.yml");
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        DeathLocation oldDeath = death(1.25);
        DeathLocation newDeath = death(-45.75);
        try (DeathStore store = new DeathStore(file, logger)) {
            assertNull(store.get(first));
            store.put(first, oldDeath);
            store.put(second, oldDeath);
            store.put(first, newDeath);
            assertEquals(newDeath, store.get(first));
        }
        try (DeathStore store = new DeathStore(file, logger)) {
            assertEquals(newDeath, store.get(first));
            assertEquals(oldDeath, store.get(second));
            assertTrue(store.clear(first));
            assertFalse(store.clear(first));
        }
        try (DeathStore store = new DeathStore(file, logger)) {
            assertNull(store.get(first));
            assertEquals(oldDeath, store.get(second));
        }
    }

    @Test
    void rejectsMalformedYamlWithoutOverwritingIt() throws Exception {
        Path file = directory.resolve("deaths.yml");
        String broken = "deaths: [unfinished\n";
        Files.writeString(file, broken);
        assertThrows(IOException.class, () -> new DeathStore(file, logger));
        assertEquals(broken, Files.readString(file));
    }

    @Test
    void preservesInvalidRecordsAndUnknownFields() throws Exception {
        Path file = directory.resolve("deaths.yml");
        UUID invalid = UUID.randomUUID();
        UUID valid = UUID.randomUUID();
        Files.writeString(file, "metadata: keep\ndeaths:\n  " + invalid + ":\n    x: invalid\n  not-a-uuid: keep\n");
        try (DeathStore store = new DeathStore(file, logger)) {
            assertNull(store.get(invalid));
            store.put(valid, death(10));
        }
        YamlConfiguration data = new YamlConfiguration();
        data.load(file.toFile());
        assertEquals("keep", data.getString("metadata"));
        assertEquals("invalid", data.getString("deaths." + invalid + ".x"));
        assertEquals("keep", data.getString("deaths.not-a-uuid"));
        try (DeathStore store = new DeathStore(file, logger)) {
            assertNotNull(store.get(valid));
        }
    }

    @Test
    void preservesExtraFieldsOnAPlayerUpdate() throws Exception {
        Path file = directory.resolve("deaths.yml");
        UUID player = UUID.randomUUID();
        try (DeathStore store = new DeathStore(file, logger)) {
            store.put(player, death(10));
        }
        YamlConfiguration data = new YamlConfiguration();
        data.load(file.toFile());
        data.set("deaths." + player + ".extra", "keep");
        data.save(file.toFile());
        try (DeathStore store = new DeathStore(file, logger)) {
            store.put(player, death(20));
        }
        data.load(file.toFile());
        assertEquals("keep", data.getString("deaths." + player + ".extra"));
        assertEquals(20, data.getDouble("deaths." + player + ".x"));
    }

    @Test
    void acceptsMissingAndEmptyFilesWithoutWritingOnRead() throws Exception {
        Path file = directory.resolve("deaths.yml");
        try (DeathStore store = new DeathStore(file, logger)) {
            assertNull(store.get(UUID.randomUUID()));
        }
        assertFalse(Files.exists(file));
        Files.createFile(file);
        try (DeathStore store = new DeathStore(file, logger)) {
            assertNull(store.get(UUID.randomUUID()));
        }
        assertEquals("", Files.readString(file));
    }

    @Test
    void rejectsWrongRootShape() throws Exception {
        Path file = directory.resolve("deaths.yml");
        Files.writeString(file, "deaths: invalid\n");
        assertThrows(IOException.class, () -> new DeathStore(file, logger));
        assertEquals("deaths: invalid\n", Files.readString(file));
    }

    @Test
    void rejectsNonFiniteCoordinatesAndInvalidIdentity() {
        UUID world = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> new DeathLocation(world, "world", Double.NaN, 64, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new DeathLocation(world, "world", 0, 64, Double.POSITIVE_INFINITY, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new DeathLocation(world, " ", 0, 64, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new DeathLocation(world, "world", 0, 64, 0, -1));
    }

    @Test
    void skipsInvalidFieldsWithoutTurningThemIntoZero() throws Exception {
        UUID player = UUID.randomUUID();
        Path file = directory.resolve("deaths.yml");
        String record = "deaths:\n  " + player + ":\n    world-uuid: " + UUID.randomUUID()
                + "\n    world-name: world\n    x: 1.5\n    y: 64\n    z: 0\n    timestamp: 1\n";
        for (String invalid : new String[] {
                record.replace("x: 1.5", "x: wrong"),
                record.replace("y: 64\n", "y: null\n"),
                record.replace("z: 0", "z: .inf"),
                record.replace("timestamp: 1", "timestamp: 1.5"),
                record.replace("world-name: world", "world-name: ''")
        }) {
            Files.writeString(file, invalid);
            try (DeathStore store = new DeathStore(file, logger)) {
                assertNull(store.get(player));
            }
            assertEquals(invalid, Files.readString(file));
        }
    }

    @Test
    void writesInBackgroundAndFlushesTheNewestUpdateOnShutdown() throws Exception {
        Path file = directory.resolve("deaths.yml");
        UUID player = UUID.randomUUID();
        DeathLocation latest = death(99);
        try (DeathStore store = new DeathStore(file, logger)) {
            store.put(player, death(1));
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (!Files.exists(file) && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            assertTrue(Files.exists(file), "Background writer did not persist the death");
            store.put(player, latest);
        }
        try (DeathStore store = new DeathStore(file, logger)) {
            assertEquals(latest, store.get(player));
        }
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void retriesFailedWritesAndKeepsThePendingRecord() throws Exception {
        Path file = directory.resolve("deaths.yml");
        UUID player = UUID.randomUUID();
        try (DeathStore store = new DeathStore(file, logger)) {
            Files.createDirectory(file);
            store.put(player, death(12));
            Thread.sleep(600);
            assertEquals(12, store.get(player).x());
            Files.delete(file);
            long deadline = System.nanoTime() + 8_000_000_000L;
            while (!Files.isRegularFile(file) && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            assertTrue(Files.isRegularFile(file), "Failed write was not retried");
        }
        try (DeathStore store = new DeathStore(file, logger)) {
            assertEquals(12, store.get(player).x());
        }
    }

    @Test
    void reportsFinalSaveFailure() throws Exception {
        Path file = directory.resolve("deaths.yml");
        DeathStore store = new DeathStore(file, logger);
        Files.createDirectory(file);
        store.put(UUID.randomUUID(), death(12));
        assertThrows(IOException.class, store::close);
    }

    @Test
    void normalizesUppercasePlayerIdsBeforeUpdateAndClear() throws Exception {
        Path file = directory.resolve("deaths.yml");
        UUID player = UUID.randomUUID();
        DeathLocation latest = death(20);
        try (DeathStore store = new DeathStore(file, logger)) {
            store.put(player, death(10));
        }
        String saved = Files.readString(file);
        Files.writeString(file, saved.replace(player.toString(), player.toString().toUpperCase(Locale.ROOT)));
        try (DeathStore store = new DeathStore(file, logger)) {
            assertNotNull(store.get(player));
            assertTrue(store.clear(player));
            store.put(player, latest);
        }
        try (DeathStore store = new DeathStore(file, logger)) {
            assertEquals(latest, store.get(player));
            assertTrue(store.clear(player));
        }
        try (DeathStore store = new DeathStore(file, logger)) {
            assertNull(store.get(player));
        }
    }

    @Test
    void keepsTheNewestRecordWhenUuidCasingIsDuplicated() throws Exception {
        Path file = directory.resolve("deaths.yml");
        UUID player = UUID.randomUUID();
        DeathLocation latest = new DeathLocation(UUID.randomUUID(), "world", 20, 64, 0, 2000);
        try (DeathStore store = new DeathStore(file, logger)) {
            store.put(player, latest);
        }
        YamlConfiguration data = new YamlConfiguration();
        data.load(file.toFile());
        data.set("deaths." + player.toString().toUpperCase(Locale.ROOT),
                data.getConfigurationSection("deaths." + player).getValues(false));
        data.set("deaths." + player + ".timestamp", 1000);
        data.save(file.toFile());
        try (DeathStore store = new DeathStore(file, logger)) {
            assertEquals(latest, store.get(player));
            assertTrue(store.clear(player));
        }
        try (DeathStore store = new DeathStore(file, logger)) {
            assertNull(store.get(player));
        }
    }

    @Test
    void shutdownStopsTheWriterAndRejectsFurtherMutations() throws Exception {
        Path file = directory.resolve("deaths.yml");
        UUID player = UUID.randomUUID();
        DeathStore store = new DeathStore(file, logger);
        store.put(player, death(1));
        store.close();
        assertThrows(IllegalStateException.class, () -> store.put(player, death(2)));
        assertThrows(IllegalStateException.class, () -> store.clear(player));
        assertFalse(Thread.getAllStackTraces().keySet().stream()
                .anyMatch(thread -> thread.isAlive() && thread.getName().equals("PlexDeathCoords-storage")));
        store.close();
    }

    @Test
    void preservesNullRecordsAndNestedYamlValuesOnUnrelatedUpdates() throws Exception {
        Path file = directory.resolve("deaths.yml");
        UUID invalid = UUID.randomUUID();
        UUID valid = UUID.randomUUID();
        Files.writeString(file, "metadata:\n  optional: null\n  literal.key: keep\ndeaths:\n  "
                + invalid + ": null\n");
        try (DeathStore store = new DeathStore(file, logger)) {
            assertNull(store.get(invalid));
            store.put(valid, death(10));
        }
        Map<?, ?> data = new Yaml().load(Files.readString(file));
        Map<?, ?> metadata = (Map<?, ?>) data.get("metadata");
        assertTrue(metadata.containsKey("optional"));
        assertNull(metadata.get("optional"));
        assertEquals("keep", metadata.get("literal.key"));
        Map<?, ?> deaths = (Map<?, ?>) data.get("deaths");
        assertTrue(deaths.containsKey(invalid.toString()));
        assertNull(deaths.get(invalid.toString()));
        assertTrue(deaths.containsKey(valid.toString()));
    }

    @Test
    void preservesNullAndLiteralKeysWithinAnUpdatedPlayerRecord() throws Exception {
        Path file = directory.resolve("deaths.yml");
        UUID player = UUID.randomUUID();
        try (DeathStore store = new DeathStore(file, logger)) {
            store.put(player, death(10));
        }
        Files.writeString(file, Files.readString(file) + "    optional: null\n    literal.key: keep\n");
        try (DeathStore store = new DeathStore(file, logger)) {
            store.put(player, death(20));
        }
        Map<?, ?> data = new Yaml().load(Files.readString(file));
        Map<?, ?> records = (Map<?, ?>) data.get("deaths");
        Map<?, ?> record = (Map<?, ?>) records.get(player.toString());
        assertTrue(record.containsKey("optional"));
        assertNull(record.get("optional"));
        assertEquals("keep", record.get("literal.key"));
        assertEquals(20.0, record.get("x"));
    }

    @Test
    void rejectsDuplicateYamlKeysWithoutChangingTheFile() throws Exception {
        Path file = directory.resolve("deaths.yml");
        String yaml = "deaths: {}\ndeaths: {}\n";
        Files.writeString(file, yaml);
        assertThrows(IOException.class, () -> new DeathStore(file, logger));
        assertEquals(yaml, Files.readString(file));
    }

    @Test
    void clearingAnInvalidNullRecordPersistsTheRemoval() throws Exception {
        Path file = directory.resolve("deaths.yml");
        UUID player = UUID.randomUUID();
        Files.writeString(file, "deaths:\n  " + player + ": null\n");
        try (DeathStore store = new DeathStore(file, logger)) {
            assertNull(store.get(player));
            assertTrue(store.clear(player));
            assertFalse(store.clear(player));
        }
        Map<?, ?> data = new Yaml().load(Files.readString(file));
        assertFalse(((Map<?, ?>) data.get("deaths")).containsKey(player.toString()));
    }

    @Test
    void readsExistingStorageBeyondTheDefaultParserSizeLimit() throws Exception {
        Path file = directory.resolve("deaths.yml");
        UUID player = UUID.randomUUID();
        DeathLocation saved = death(1);
        try (DeathStore store = new DeathStore(file, logger)) {
            store.put(player, saved);
        }
        Files.writeString(file, Files.readString(file) + "metadata: " + "x".repeat(4_000_000) + "\n");
        try (DeathStore store = new DeathStore(file, logger)) {
            assertEquals(saved, store.get(player));
        }
    }

    @Test
    void retainsWorldIdentityAcrossDimensionsAndRestarts() throws Exception {
        Path file = directory.resolve("deaths.yml");
        UUID player = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        DeathLocation nether = new DeathLocation(UUID.randomUUID(), "world_nether", -5.25, 30, 70, 1000);
        DeathLocation end = new DeathLocation(UUID.randomUUID(), "world_the_end", 20.5, 65, -9, 2000);
        DeathLocation sameName = new DeathLocation(UUID.randomUUID(), "world_the_end", 100, 64, 10, 2000);
        try (DeathStore store = new DeathStore(file, logger)) {
            store.put(player, nether);
            assertEquals(nether, store.get(player));
            store.put(player, end);
            store.put(other, sameName);
        }
        try (DeathStore store = new DeathStore(file, logger)) {
            assertEquals(end, store.get(player));
            assertEquals(sameName, store.get(other));
            assertNotEquals(store.get(player).worldId(), store.get(other).worldId());
        }
    }

    @Test
    void keepsForeignSerializedDataAsPlainYamlWithoutServerAccess() throws Exception {
        Path file = directory.resolve("deaths.yml");
        Files.writeString(file, "metadata:\n  ==: org.bukkit.inventory.ItemStack\n  type: STONE\ndeaths: {}\n");
        try (DeathStore store = new DeathStore(file, logger)) {
            store.put(UUID.randomUUID(), death(10));
        }
        Map<?, ?> data = new Yaml().load(Files.readString(file));
        assertEquals(Map.of("==", "org.bukkit.inventory.ItemStack", "type", "STONE"), data.get("metadata"));
    }

    @Test
    void unrelatedUpdatesPreserveInvalidCanonicalAndValidUppercaseRecords() throws Exception {
        Path file = directory.resolve("deaths.yml");
        UUID player = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        String upper = player.toString().toUpperCase(Locale.ROOT);
        DeathLocation saved = death(10);
        try (DeathStore store = new DeathStore(file, logger)) {
            store.put(player, saved);
        }
        Map<?, ?> original = new Yaml().load(Files.readString(file));
        Object validRecord = ((Map<?, ?>) original.get("deaths")).get(player.toString());
        for (boolean invalidFirst : new boolean[] {false, true}) {
            Map<String, Object> records = new LinkedHashMap<>();
            if (invalidFirst) {
                records.put(player.toString(), null);
            }
            records.put(upper, validRecord);
            records.put(player.toString(), null);
            Files.writeString(file, new Yaml().dump(Map.of("deaths", records)));
            try (DeathStore store = new DeathStore(file, logger)) {
                assertEquals(saved, store.get(player));
                store.put(UUID.randomUUID(), death(20));
            }
            Map<?, ?> result = new Yaml().load(Files.readString(file));
            Map<?, ?> savedRecords = (Map<?, ?>) result.get("deaths");
            assertTrue(savedRecords.containsKey(player.toString()));
            assertNull(savedRecords.get(player.toString()));
            assertEquals(validRecord, savedRecords.get(upper));
            try (DeathStore store = new DeathStore(file, logger)) {
                assertTrue(store.clear(player));
            }
            try (DeathStore store = new DeathStore(file, logger)) {
                assertNull(store.get(player));
            }
        }
    }

    private DeathLocation death(double x) {
        return new DeathLocation(UUID.randomUUID(), "world_nether", x, 64.25, -10.75,
                System.currentTimeMillis());
    }
}

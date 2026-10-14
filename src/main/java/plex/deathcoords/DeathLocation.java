package plex.deathcoords;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record DeathLocation(UUID worldId, String worldName, double x, double y, double z, long timestamp) {
    public DeathLocation {
        Objects.requireNonNull(worldId, "worldId");
        if (worldName == null || worldName.isBlank()) {
            throw new IllegalArgumentException("World name must not be empty");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Coordinates must be finite numbers");
        }
        if (timestamp < 0) {
            throw new IllegalArgumentException("Timestamp must not be negative");
        }
    }

    static DeathLocation read(Map<?, ?> section) {
        if (!(section.get("world-uuid") instanceof String worldId)
                || !(section.get("world-name") instanceof String worldName)
                || !(section.get("x") instanceof Number x)
                || !(section.get("y") instanceof Number y)
                || !(section.get("z") instanceof Number z)
                || !(section.get("timestamp") instanceof Number timestamp)
                || !(timestamp instanceof Long || timestamp instanceof Integer)) {
            throw new IllegalArgumentException("Missing or invalid death fields");
        }
        return new DeathLocation(parseUuid(worldId), worldName, x.doubleValue(), y.doubleValue(),
                z.doubleValue(), timestamp.longValue());
    }

    static UUID parseUuid(String value) {
        UUID uuid = UUID.fromString(value);
        if (!uuid.toString().equalsIgnoreCase(value)) {
            throw new IllegalArgumentException("Invalid UUID");
        }
        return uuid;
    }
}

package plex.deathcoords;

import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class DeathCommandsCompassTest {
    @Test
    void compassRequiresSameNormalWorld() {
        UUID worldId = UUID.randomUUID();
        DeathLocation death = new DeathLocation(worldId, "world", 1, 2, 3, 42L);
        assertTrue(DeathCommands.canPointToDeath(worldId, World.Environment.NORMAL, death));
        assertFalse(DeathCommands.canPointToDeath(UUID.randomUUID(), World.Environment.NORMAL, death));
        assertFalse(DeathCommands.canPointToDeath(worldId, World.Environment.NETHER, death));
        assertFalse(DeathCommands.canPointToDeath(worldId, World.Environment.THE_END, death));
    }
}

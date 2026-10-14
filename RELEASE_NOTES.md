# PlexDeathCoords 1.0.0

Initial release of PlexDeathCoords by ApplexDev / Plex Studios, a small Paper
plugin for retrieving your most recent death location.

- View coordinates with `/lastdeath` or `/deathcoords`, or clear them with `clear`.
- Retain one location per player UUID across worlds and server restarts.
- Customize MiniMessage responses, precision, world display, and elapsed time.
- Reload configuration with `/plexdeathcoords reload` using the admin permission.
- Save records in YAML with background writes and normal-shutdown flushing.

**Requirements:** Paper for Minecraft 1.21 or newer. The plugin targets Java 21;
use the Java runtime required by your Paper version. No additional plugins are needed.

**Installation:** Place `PlexDeathCoords-1.0.0.jar` in `plugins/` and restart the
server. Configuration is generated in `plugins/PlexDeathCoords/config.yml`.

**Validation:** Clean Gradle build and all 45 automated tests passed. The JAR's
version, required resources, and packaged MIT license were checked.

Licensed under MIT. Live Paper/client compatibility remains unverified; see the
[audit](https://github.com/ApplexDev/PlexDeathCoords/blob/v1.0.0/AUDIT.md) for coverage
and remaining checks.

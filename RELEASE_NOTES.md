# PlexDeathCoords 1.1.0

Local release candidate; not published.

- Click the coordinate portion of `/lastdeath` or `/deathcoords` to copy the
  formatted triple. Hover text is customizable, and legacy `<x>`, `<y>`, `<z>`
  templates remain supported.
- Enable `respawn-reminder` for one customizable message after a death respawn.
  It defaults to false and requires `plexdeathcoords.use`.
- Use `/lastdeath compass` or `/deathcoords compass` with a regular compass in
  your main hand. Requires `plexdeathcoords.use` and `plexdeathcoords.compass`.
  The saved world must be loaded and match your current world. No items are given.
- Added `clickable-coordinates: true`, `respawn-reminder: false`, and
  `allow-compass: true`, plus the messages documented in the README/config.
- Existing configuration files and `deaths.yml` retain their format and values.
  New defaults are merged in memory; reload applies the new options.

**Artifact:** `build/libs/PlexDeathCoords-1.1.0.jar`.

**Validation:** `gradlew.bat clean build --console=plain --warning-mode all`
passed with Java 21 compilation, warnings treated as errors, and all 55 automated
tests passing (zero failures, errors, or skipped tests). Verified generated version
1.1.0, Java class-file version 65, expected resources/license, and no bundled runtime
dependencies.

An isolated Paper 1.21.11 build 132 server on Temurin Java 25.0.4.1 loaded and
disabled the plugin normally. Valid reload, rejection of an invalid boolean,
recovery on the next valid reload, and console restrictions were exercised.
A temporary server-side test plugin verified actual compass metadata round-trips
in the Overworld, Nether, and End, including block-position conversion,
disabled lodestone validation, and preservation of the item's custom name.
The runtime-tested JAR and final build have matching SHA-256 hashes.

**Limits:** No Minecraft client was connected. Actual clipboard interaction,
compass needle behavior, and player death/respawn dispatch remain unverified in
game. Automated tests cover command restrictions, rendering, permissions, and
synthetic respawn events. Paper 1.21 is the compile-time API target; other server
versions, including newer versions with newer Java requirements, were not run.
Bukkit, Spigot, and Folia are unsupported. Compass targets remain fixed until the
command is run again and cannot point across dimensions. Forced termination or
unavailable storage can still leave pending writes unsaved.

## Previous release: 1.0.0

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

Licensed under MIT. Live Paper/client compatibility for 1.0.0 remains unverified.

# PlexDeathCoords

PlexDeathCoords is a small Paper plugin that records each player's most recent
death location. Use `/lastdeath` or `/deathcoords` to retrieve the saved coordinates.

Maintained by [ApplexDev](https://github.com/ApplexDev) / Plex Studios.

## Features

Version 1.1.0 adds clickable chat coordinates (click to copy), optional respawn reminders,
and a `/lastdeath compass` command. Compass targets are limited to the same normal
Overworld: vanilla compasses do not reliably point to arbitrary locations in other
dimensions. All three features can be individually configured.


- One saved death per player UUID, across worlds and dimensions.
- World UUID/name, exact coordinates, and death timestamp stored in `deaths.yml`.
- Commands to view or clear a location, with permission checks and tab completion.
- Configurable MiniMessage responses, coordinate precision, world display, and elapsed time.
- Background storage writes, retries after failed saves, and a final flush on normal shutdown.
- Configuration reload without restarting the server; cancelled deaths are ignored.
- No additional plugin dependencies or bundled runtime libraries.

## Requirements

- **Server:** Paper for Minecraft 1.21 or newer. Bukkit, Spigot, and Folia are not supported.
- **Java:** The plugin targets Java 21. Use the runtime required by your server:
  Paper 1.21.x uses Java 21; Paper 26.1 and later uses Java 25. See
  [Paper's Java requirements](https://docs.papermc.io/paper/getting-started/#requirements).
- **API target:** Paper 1.21. Automated tests run against this API; live compatibility
  across individual server versions has not been tested.

## Installation

1. Build `PlexDeathCoords-1.1.0.jar` from source, or download it from [GitHub Releases](https://github.com/PlexStudios/PlexDeathCoords/releases) when a release is available.
2. Place the JAR in your Paper server's `plugins` directory.
3. Start or restart the server.
4. Edit `plugins/PlexDeathCoords/config.yml` if needed and run `/plexdeathcoords reload`.

Only deaths that occur while the plugin is enabled are recorded. Existing vanilla
death locations are not imported. Use a full server restart when replacing the JAR.

## Commands and permissions

| Command | Permission | Default |
| --- | --- | --- |
| `/lastdeath` | `plexdeathcoords.use` | Everyone |
| `/deathcoords` | `plexdeathcoords.use` | Everyone |
| `/lastdeath compass` | `plexdeathcoords.use` | Everyone |
| `/lastdeath clear` | `plexdeathcoords.use` and `plexdeathcoords.clear` | Everyone |
| `/deathcoords clear` | `plexdeathcoords.use` and `plexdeathcoords.clear` | Everyone |
| `/plexdeathcoords reload` | `plexdeathcoords.admin` | Operators |

Viewing and clearing require a player. The console can reload configuration.
Death tracking does not depend on command permissions. Clear suggestions disappear
when clearing is disabled or the player lacks permission.

## Configuration

The bundled [config.yml](src/main/resources/config.yml) documents the defaults.
This example changes display options and the main response:

```yaml
coordinate-precision: 0
show-world: true
show-time: true
allow-clear: true
clickable-coordinates: true
respawn-reminder: false
allow-compass: true

messages:
  last-death: '<gray>Last death: <white><x>, <y>, <z></white><world_part><time_part></gray>'
```

Missing options and messages use bundled defaults. Precision accepts integers from
0 to 6 and affects display only. Coordinates are rounded to the nearest value,
with ties away from zero; integer output is not Minecraft's floored block position.

The world/time toggles control `<world_part>` and `<time_part>`. Keep those
placeholders in `last-death` for the toggles to apply. `allow-clear: false` blocks
clearing even when the player has the clear permission.

Messages use [MiniMessage formatting](https://docs.papermc.io/adventure/minimessage/format/).
Close formatting tags explicitly; strict parsing rejects unclosed tags and
`<reset>`. Dynamic world names, coordinates, and command labels are inserted as
plain text. Empty message strings are allowed.

| Message | Placeholders |
| --- | --- |
| `last-death` | `<x>`, `<y>`, `<z>`, `<world>`, `<world_part>`, `<time_part>`, `<ago>` |
| `world` | `<world>`, `<x>`, `<y>`, `<z>` |
| `time` | `<ago>`, `<world>`, `<x>`, `<y>`, `<z>` |
| `seconds`, `minutes`, `hours`, `days` | `<value>` |
| `usage`, `admin-usage` | `<command>` |

Elapsed time uses the largest whole unit: seconds, minutes, hours, or days.
World display uses the name saved at death, so an unloaded world can still be shown.

Reload reads and validates configuration in the background, then applies it on
the server thread. Invalid reloads retain working settings. Duplicate YAML keys,
explicit null settings, wrong types, and invalid formatting are rejected. One
reload can run at a time; it does not register listeners again or reload death data.

## Storage

`plugins/PlexDeathCoords/deaths.yml` is created after the first recorded death.
Each player UUID maps to `world-uuid`, `world-name`, `x`, `y`, `z`, and `timestamp`
(Unix time in milliseconds). Only the latest death is retained.

Writes are coalesced and use temporary-file replacement, atomic where supported.
Read-only commands do not write to disk. Failed saves keep pending data in memory
and retry; normal shutdown flushes it. Forced termination or unavailable storage
can leave the latest changes unsaved. Keep regular backups.

Malformed storage prevents startup without overwriting the file. Invalid
individual records are ignored and preserved during unrelated updates. Unknown
fields, null values, and literal dotted keys are retained. Clearing deliberately
removes that player's record and any UUID-casing variants.

Stop the server before manually editing `deaths.yml`.

## Building from source

Install a **JDK 21** and set `JAVA_HOME` to it. No separate Gradle installation is
needed: the wrapper uses Gradle 9.1.0 with a pinned distribution checksum. The first
build needs internet access for Gradle, Paper API, and test dependencies.

Linux or macOS:

```sh
sh gradlew clean build
```

Windows PowerShell:

```powershell
.\gradlew.bat clean build
```

The output is `build/libs/PlexDeathCoords-1.0.0.jar`. The build compiles the plugin
and packages its configuration, metadata, and license. This public source copy
contains production sources and build tooling. Paper, Adventure, SnakeYAML, and
JUnit are not bundled in the plugin JAR.

## Troubleshooting

| Problem | Check |
| --- | --- |
| Plugin does not enable | Confirm the server is Paper and Java meets its requirements. Check startup logs for invalid configuration or storage. |
| No saved death is shown | The player must die while this plugin is enabled. Invalid saved records are reported in the server log and are not displayed. |
| Reload fails | Check `config.yml` for duplicate keys, incorrect types, or unclosed MiniMessage tags. Previous settings remain active. |
| Data is not saving | Check the first storage error in the server log, available disk space, and write permissions on the plugin directory. |

Back up malformed data before correcting it. Restart after fixing storage errors;
do not delete `deaths.yml` merely to silence a warning.

## Issues and contributions

Report bugs or propose changes through [GitHub Issues](https://github.com/PlexStudios/PlexDeathCoords/issues).
Include the plugin, Paper/Minecraft, and Java versions, reproduction steps, and
relevant logs with credentials and private player information removed.

When opening a pull request, keep changes
focused on death-location tracking, retrieval, and reliability.

## License

[MIT](LICENSE), copyright Plex Studios. The license is included in the repository
and release JAR. Third-party tooling and dependencies retain their own licenses;
the Gradle wrapper retains its [Apache 2.0 license](gradle/wrapper/LICENSE).

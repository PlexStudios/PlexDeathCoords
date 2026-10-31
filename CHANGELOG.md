# Changelog

## 1.1.0

### Added

- Clipboard actions on coordinate values, with customizable hover text and a `clickable-coordinates` toggle.
- Optional, customizable post-respawn reminder, disabled by default, with duplicate prevention and clickable coordinates.
- `/lastdeath compass` and `/deathcoords compass` to configure an existing main-hand compass using a lodestone target without requiring a lodestone block.
- `allow-compass` and `plexdeathcoords.compass`, both enabled by default, plus customizable success and restriction messages.

### Changed

- Added `<coordinates>` while preserving existing coordinate placeholders, saved data, and user configuration files.
- Updated command usage, tab completion, version metadata, and public configuration documentation.

### Validation

- Added regression coverage for clipboard components, legacy configuration defaults, reminder rendering/lifecycle, and compass restrictions.
- Clean Gradle build passed all 55 tests with Java 21 compilation and warnings as errors.
- Isolated Paper 1.21.11 startup, valid/invalid reload, shutdown, and real compass metadata checks passed; client interactions remain unverified.
- See `RELEASE_NOTES.md` for final build results and runtime verification limits.

## 1.0.0

Initial release.

### Added

- Most recent death tracking per player UUID, including world identity, coordinates, and timestamp.
- `/lastdeath`, `/deathcoords`, player clearing, and permission-controlled administrator reload.
- MiniMessage responses, coordinate precision, optional world/elapsed time, and a clearing toggle.
- YAML persistence with background writes, retry after failure, and shutdown flushing.
- Safe validation of configuration and saved data, preserving unrelated records and unknown fields.
- Java 21 Gradle project, wrapper, automated tests, documentation, and MIT license.

### Validation

- Clean Gradle build passed with Java 21 and Gradle 9.1.0; all 45 automated tests passed.
- Verified version 1.0.0, required YAML resources, the packaged MIT license, and no bundled runtime dependencies.
- Live Paper/client compatibility has not been tested.

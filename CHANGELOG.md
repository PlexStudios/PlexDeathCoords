# Changelog

## 1.0.0

Initial release.

### Added

- Most recent death tracking per player UUID, including world identity, coordinates, and timestamp.
- `/lastdeath`, `/deathcoords`, player clearing, and permission-controlled administrator reload.
- MiniMessage responses, coordinate precision, optional world/elapsed time, and a clearing toggle.
- YAML persistence with background writes, retry after failure, and shutdown flushing.
- Safe validation of configuration and saved data, preserving unrelated records and unknown fields.
- Java 21 Gradle project, wrapper, automated tests, documentation, and MIT license.

### Release preparation

- Clarified requirements, installation, configuration, building, and troubleshooting in the README.
- Excluded local planning files and generated binaries from Git; retained the required Gradle wrapper JAR.
- Added contribution guidelines, bug/feature issue forms, and a Java 21 build workflow for main-branch pushes and pull requests.
- Added ApplexDev / Plex Studios attribution and documented the separate third-party licenses.

### Validation

- Clean Gradle build passed with Java 21 and Gradle 9.1.0; all 45 automated tests passed.
- Verified version 1.0.0, required YAML resources, the packaged MIT license, and no bundled runtime dependencies.
- Live Paper/client compatibility has not been tested.
- GitHub Actions workflow passed local actionlint validation; remote CI results are reported by GitHub.

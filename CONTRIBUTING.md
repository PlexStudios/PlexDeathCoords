# Contributing

PlexDeathCoords is maintained by ApplexDev / Plex Studios. Contributions should
keep the plugin small and focused on reliable death-location tracking.

## Bugs and proposals

Use [GitHub Issues](https://github.com/PlexStudios/PlexDeathCoords/issues) and check
for an existing report first. For bugs, include the plugin version, exact
Paper/Minecraft and Java versions, reproduction steps, expected/actual behavior,
and relevant logs. Remove credentials and private player data before posting.

Discuss changes that alter commands, permissions, configuration, or storage
before implementing them. Explain the user problem and the smallest useful fix.

## Development

Fork the repository and create a branch for your change. Install JDK 21 and use
the included Gradle wrapper:

```sh
sh gradlew clean build
```

On Windows PowerShell:

```powershell
.\gradlew.bat clean build
```

The build runs the existing JUnit tests and produces
`build/libs/PlexDeathCoords-1.1.0.jar`. A separate Gradle installation is not needed.
For tests alone, use `sh gradlew test` or `.\gradlew.bat test`.

## Pull requests

- Keep the change focused and follow the existing Java style. Do not add `//` source comments.
- Preserve public commands, permissions, message placeholders, and stored data unless the change explicitly requires otherwise.
- Add a meaningful regression test for a bug or important behavior change. Keep real server APIs on the appropriate thread.
- Avoid new runtime dependencies, unrelated refactors, and speculative features.
- Run the full build and update relevant documentation and the changelog.
- State what was tested and any remaining manual checks. Do not claim in-game testing without using a server.
- Keep binaries, caches, IDE files, server data, and credentials out of the commit. The Gradle wrapper JAR is the required tooling exception.

Test player-facing changes on a Paper server when practical, including error
paths and restart/reload behavior. Automated tests do not replace live-server verification.

Contributions to the original project are provided under its [MIT license](LICENSE).
Retain third-party license notices when modifying or updating build tooling.

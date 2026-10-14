# Third-party notices

The [MIT license](LICENSE) applies to the original PlexDeathCoords code, tests,
configuration, and documentation. It does not replace third-party licenses.

## Included build tooling

The Gradle 9.1.0 wrapper scripts and `gradle/wrapper/gradle-wrapper.jar` are
distributed under Apache License 2.0. Their original notices are retained, and
the JAR includes its license in `META-INF/LICENSE`. A readable copy is included
at [gradle/wrapper/LICENSE](gradle/wrapper/LICENSE). See
[Gradle's license](https://github.com/gradle/gradle/blob/v9.1.0/LICENSE).

The wrapper downloads a separate Gradle distribution when needed. That
distribution contains its own license and notices for its included components;
it is not included in this repository or the plugin release JAR.

## Provided and test dependencies

These dependencies are not packaged in `PlexDeathCoords-1.0.0.jar`:

| Component | Use | License information |
| --- | --- | --- |
| Paper API 1.21 | Compile-time API and test classpath; server supplies it at runtime | [Paper's GPLv3 and upstream/component licensing](https://github.com/PaperMC/Paper/blob/main/LICENSE.md) |
| Adventure 4.17.0 | Paper-provided messaging API | [MIT upstream license](https://github.com/PaperMC/adventure/blob/main/5/license.txt) |
| SnakeYAML 2.2 | Paper-provided YAML parser | Apache License 2.0, declared in its published Maven metadata |
| JUnit 5.12.2 | Tests only | [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-v20.html), declared in its published Maven metadata |

Paper supplies additional API dependencies under their respective upstream
licenses. The plugin release contains only PlexDeathCoords classes, its YAML
resources, manifest, and MIT license. No third-party implementation was copied
into the plugin's Java sources. If dependencies or their code are redistributed
in a future change, review and preserve their licensing requirements separately.

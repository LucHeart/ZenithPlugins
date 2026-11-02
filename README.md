# ZenithPlugins

[ZenithProxy](https://github.com/rfresh2/ZenithProxy) plugins, one directory each:

| Plugin | What it does |
|---|---|
| [ZenithChat](ZenithChat/README.md) | Chat messages and DMs from a command or a REST endpoint |
| [ZenithExporter](ZenithExporter/README.md) | Prometheus metrics for the proxy, the account and the server |
| [ZenithVoice](ZenithVoice/README.md) | Simple Voice Chat through the proxy |
| [ZenithAnoying](ZenithAnoying/README.md) | Finds the nearest bell (or any block) and spam clicks it |

## Install
Download the jar matching your ZenithProxy MC version (check with the `status` command) from the plugin's release,
e.g. `ZenithChat-1.0.0+26.3.0.jar`. Put it in the `plugins/` folder next to the Zenith launcher and restart.
Requires the `java` release channel. 1.21.4 builds run on Java 21, 26.x builds need Java 25.

## Build
- `./gradlew build -Pmc=26.3.0` builds every plugin → `<Plugin>/build/libs/<Plugin>-<version>+<mc>.jar`
- `./gradlew :ZenithChat:build -Pmc=26.3.0` builds one
- `./gradlew :ZenithChat:run` starts ZenithProxy with that plugin loaded, in `<Plugin>/run/`

Without `-Pmc` it uses `mc` from the root `gradle.properties`. Supported versions are listed in `mc_versions`;
`zenith_version` picks the ZenithProxy release to compile against (`snapshot` for the latest `<mc>-SNAPSHOT`).
These are shared by all plugins. Each plugin's own `gradle.properties` has its `plugin_version`, `plugin_id` and
`maven_group`.

The shared Gradle setup (ZenithProxy dependency, Java versions, BuildConstants, jar naming) is the `zenith-plugin`
convention plugin in `buildSrc/src/main/kotlin/zenith-plugin.gradle.kts`. A plugin's `build.gradle.kts` applies it
and only adds what's its own, such as shaded dependencies.

### New plugin
1. Make a directory named like the jar, e.g. `ZenithFoo`, with a `build.gradle.kts` containing
   `plugins { id("zenith-plugin") }` and a `gradle.properties` with `plugin_version`, `plugin_id` and `maven_group`
2. Add it to `include(...)` in `settings.gradle.kts`
3. Put the `@Plugin` class in the `maven_group` package, `BuildConstants` is generated there

## CI
GitHub Actions (`.github/workflows/build.yml`) builds every plugin for every version in `mc_versions` on pushes to
`master` and pull requests; the jars are attached to the run.

Releases are per plugin: push a tag `<Plugin>/<version>`, e.g. `ZenithChat/1.0.0`. That builds only that plugin and
creates a draft GitHub release with its jars, to publish by hand. The version has to match `plugin_version` in
`<Plugin>/gradle.properties`, otherwise the build fails right away.

## License
MIT, see [LICENSE](LICENSE).

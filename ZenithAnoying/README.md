# ZenithAnoying

[ZenithProxy](https://github.com/rfresh2/ZenithProxy) plugin that finds the nearest bell (or any other block), walks to it, and spam right clicks it.

## Install
Download the jar matching your ZenithProxy MC version (check with the `status` command) from the releases,
e.g. `ZenithAnoying-1.0.0+26.3.0.jar`. Put it in the `plugins/` folder next to the Zenith launcher and restart.
Requires the `java` release channel. 1.21.4 builds run on Java 21, 26.x builds need Java 25.

## Build
Built from the root of the [ZenithPlugins](../README.md) repository: `./gradlew :ZenithAnoying:build -Pmc=26.3.0` →
`ZenithAnoying/build/libs/ZenithAnoying-<version>+<mc>.jar`. See the root README for MC versions, CI and releases.

## Commands

| Command | Description | Default |
| --- | --- | --- |
| `blockSpam on/off` | Toggle the module | `off` |
| `blockSpam block <block>` | Block to click | `bell` |
| `blockSpam interval <ticks>` | Ticks between clicks | `1` |
| `blockSpam radius <chunks>` | Search radius, in loaded chunks | `16` |
| `blockSpam pathing on/off` | Walk to the block when it's out of reach | `on` |

The module only runs while the proxy's bot is ticking, so it pauses while a player is controlling the account.

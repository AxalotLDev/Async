# Async - Minecraft Entity Multi-Threading Mod ⚙️

**Async** improves entity performance by processing entities in parallel across multiple CPU cores and threads.

<p>
  <img alt="Supported on Fabric" height="56" src="https://raw.githubusercontent.com/intergrav/devins-badges/v3/assets/cozy/supported/fabric_vector.svg">
  <img alt="Requires Fabric API" height="56" src="https://raw.githubusercontent.com/intergrav/devins-badges/v3/assets/cozy/requires/fabric-api_vector.svg">
  <img alt="Available for NeoForge" height="56" src="https://raw.githubusercontent.com/cassiancc/Cassians-Badges/refs/heads/main/cozy/NeoForge.svg">
</p>

<p>
  <a href="https://modrinth.com/mod/async"><img alt="Available on Modrinth" height="56" src="https://raw.githubusercontent.com/intergrav/devins-badges/v3/assets/cozy/available/modrinth_vector.svg"></a>
  <a href="https://github.com/AxalotLDev/Async"><img alt="View source on GitHub" height="56" src="https://raw.githubusercontent.com/intergrav/devins-badges/v3/assets/cozy/social/github-singular_vector.svg"></a>
  <a href="https://discord.com/invite/scvCQ2qKS3"><img alt="Chat with us on Discord" height="56" src="https://raw.githubusercontent.com/intergrav/devins-badges/v3/assets/cozy/social/discord-plural_vector.svg"></a>
</p>

## Important❗
**Async** is currently in alpha testing and is experimental. Its use may lead to incorrect entity behavior and crashes.

## What is Async? 🤔
Async enhances the performance of entity processing. The mod leverages multithreading, which allows multiple CPU cores to improve performance when handling a large number of entities.

### 💡 Key Benefits:
- ⚡ **Improved TPS**: Maintains stable tick times even with a large number of entities.
- 🚀 **Multithreading**: Utilizes multiple CPU cores for parallel entity processing.
- 🎲 **Async Random Ticks** (Experimental): Processes random ticks asynchronously for better performance.

### Requirements

- **Minecraft**: 1.21 or 1.21.1
- **Java**: 21 or newer
- **Loader**: Fabric, Quilt, or NeoForge
- **Dependency**: Fabric API (on Fabric/Quilt)

### 📊 Performance Comparison (9000 Villagers)
| Configuration           | TPS  | MSPT   |
| ----------------------- | ---- | ------ |
| **Lithium + Async**     | 20   | 41.8   |
| **Lithium (without Async)** | 4.4  | 225.4  |
| **Purpur**              | 5.72 | 176.18 |

### 🛠️ Test Configuration
- **Processor**: AMD Ryzen 9 7950X3D
- **RAM**: 64 GB (16 GB allocated to the server)
- **Minecraft Version**: 1.21.4
- **Number of Entities**: 9000
- **Entity Type**: Villagers

<details>
<summary>Mod List</summary>
Concurrent Chunk Management Engine, Fabric API, FerriteCore, Lithium, ScalableLux, ServerCore, StackDeobfuscator, TT20 (TPS Fixer), Tectonic, Very Many Players, Fabric Carpet.
</details>

## ⚠️ Incompatible Mods
- ❌ Moonrise - Known incompatibility
- ❌ Open Parties and Claims - Known incompatibility
- ⚠️ ...and there may be conflicts with other mods.

*If you encounter issues with other mods, please report them on our [GitHub](https://github.com/AxalotLDev/Async/issues) or [Discord](https://discord.com/invite/scvCQ2qKS3).*

## 🔧 Commands

### Configuration commands

- `/async config toggle` - Enables or disables the mod without restarting the server.
- `/async config reload` - Reloads the configuration from disk.
- `/async config setAsyncEntitySpawn [true|false]` - Enables or disables parallel mob spawning. Without an argument, shows the current value. **Warning: Not compatible with Carpet's lagFreeSpawning rule.**
- `/async config setAsyncRandomTicks [true|false]` - Enables or disables experimental async random ticks. Without an argument, shows the current value.
- `/async config synchronizedEntities` - Lists entities that are processed synchronously.
- `/async config synchronizedEntities add <entity|namespace:*>` - Adds an entity type or namespace to synchronous processing.
- `/async config synchronizedEntities remove <entity|namespace:*>` - Removes an entity type or namespace from synchronous processing.

### Statistics commands

- `/async stats` - Displays mod status, async feature states, entity count, and thread count.
- `/async stats entity` - Shows entity counts by world and how many are processed asynchronously.
- `/async stats entity <number>` - Shows the top `<number>` entity types by count, marked as `async` or `sync`.
- `/async stats entity <number> <ticks>` - Records for `<ticks>` ticks, then shows average tick time for the top `<number>` entity types.

## Configuration file

- `disabled` - Fully disables the mod.
- `maxThreads` - Worker threads to use. Defaults to `-1` for automatic sizing.
- `enableAsyncSpawn` - Enables parallel mob spawning.
- `enableAsyncRandomTicks` - Enables experimental async random ticks.
- `synchronizedEntities` - Entity types that must be ticked on the main thread.

## 📥 Download
The mod is available on [Modrinth](https://modrinth.com/mod/async)

## 🔄 Minecraft Version Support
Full support is provided only for the latest version of Minecraft. Older versions receive critical fixes only. Support for older Minecraft snapshots is not planned.

## 📭 Feedback
Our tracker for feedback and bug reports is available on GitHub:
[![Report issues on GitHub](https://img.shields.io/badge/Report%20issues%20on-GitHub-lightgrey)](https://github.com/AxalotLDev/Async/issues)

You can also chat with us on Discord:
[![Chat with us on Discord](https://img.shields.io/badge/Chat%20with%20us%20on-Discord-blue)](https://discord.com/invite/scvCQ2qKS3)

## 🙌 Acknowledgements
This mod is based on code from [MCMTFabric](https://modrinth.com/mod/mcmtfabric), which in turn was based on [JMT-MCMT](https://github.com/jediminer543/JMT-MCMT). Huge thanks to Grider and jediminer543 for their invaluable contributions!

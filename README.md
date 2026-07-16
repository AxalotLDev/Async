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

Async enhances the performance of entity processing. The mod leverages multithreading, which allows multiple CPU cores
to improve performance when handling a large number of entities.

### 💡 Key Benefits:

- ⚡ **Improved TPS**: Maintains stable tick times even with a large number of entities.
- 🚀 **Multithreading**: Utilizes multiple CPU cores for parallel entity processing.
- 🎲 **Async Random Ticks** (Experimental): Processes random ticks asynchronously for better performance.

### 📋 Requirements

- **Minecraft**: 26.1 or newer
- **Java**: 25 or newer
- **Loader**: Fabric, Quilt or NeoForge
- **Dependency**: Fabric API (on Fabric/Quilt)

### 📊 Performance Comparison (9000 Villagers)

| Configuration               | TPS  | MSPT   |
|-----------------------------|------|--------|
| **Lithium + Async**         | 20   | 41.8   |
| **Lithium (without Async)** | 4.4  | 225.4  |
| **Purpur**                  | 5.72 | 176.18 |

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

*If you encounter issues with other mods, please report them on our [GitHub](https://github.com/AxalotLDev/Async/issues)
or [Discord](https://discord.com/invite/scvCQ2qKS3).*

## 🔧 Commands

### Configuration

- `/async config toggle` — Enables or disables the mod in-game (no server restart required). Use this command to
  instantly see how Async improves your server.
- `/async config reload` — Reloads the configuration from disk.
- `/async config setAsyncEntitySpawn [true|false]` — Enables or disables parallel mob spawn processing (disabled by
  default). Without an argument, shows the current value. **Warning: Not compatible with Carpet mod lagFreeSpawning
  rule.**
- `/async config setAsyncRandomTicks [true|false]` — Enables or disables async random ticks processing (experimental
  feature, disabled by default). Without an argument, shows the current value.
- `/async config synchronizedEntities` — Lists entities that are currently processed synchronously. Defaults to
  `minecraft:tnt`, `minecraft:item` and `minecraft:experience_orb`.
- `/async config synchronizedEntities add <entity|namespace:*>` — Adds an entity type, or every entity of a namespace,
  to synchronized processing.
- `/async config synchronizedEntities remove <entity|namespace:*>` — Removes an entity type, or every entity of a
  namespace, from synchronized processing.

### Statistics

- `/async stats` — Displays mod status, async spawn and random tick states, the total entity count, and the number of
  threads in use.
- `/async stats entity` — Shows the number of entities per world, along with how many of them are processed
  asynchronously.
- `/async stats entity <number>` — Additionally shows the top `<number>` (1-100) entity types by count in descending
  order, each marked as `async` or `sync`. For example, `/async stats entity 10` displays the top 10 most numerous
  entity types.
- `/async stats entity <number> <ticks>` — Records for `<ticks>` ticks, then displays the top `<number>` entity types
  with their average mspt usage.

## ⚙️ Configuration

Async can also be configured through its config file:

- `disabled` — Fully disables the mod.
- `maxThreads` — Number of threads to use. Defaults to `-1`, meaning one less than the core count. Values are clamped to
  the core count.
- `enableAsyncSpawn` — Parallel mob spawn processing.
- `enableAsyncRandomTicks` — Async random ticks (experimental).
- `synchronizedEntities` — Entity types that must be ticked on the main thread.

## 📥 Download

The mod is available on [Modrinth](https://modrinth.com/mod/async)

## 🔄 Minecraft Version Support

Full support is provided only for the latest version of Minecraft. Support for older Minecraft snapshots is not planned.

## 📭 Feedback

Our tracker for feedback and bug reports is available on GitHub:
[![Report issues on GitHub](https://img.shields.io/badge/Report%20issues%20on-GitHub-lightgrey)](https://github.com/AxalotLDev/Async/issues)

You can also chat with us on Discord:
[![Chat with us on Discord](https://img.shields.io/badge/Chat%20with%20us%20on-Discord-blue)](https://discord.com/invite/scvCQ2qKS3)

## 🙌 Acknowledgements

This mod is based on code from [MCMTFabric](https://modrinth.com/mod/mcmtfabric), which in turn was based
on [JMT-MCMT](https://github.com/jediminer543/JMT-MCMT). Huge thanks to Grider and jediminer543 for their invaluable
contributions!
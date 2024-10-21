[![Issues](https://img.shields.io/github/issues/AxalotLDev/Async?style=for-the-badge)](https://github.com/AxalotLDev/Async/issues)
<img width="200" src="https://github.com/AxalotLDev/Async/raw/ver/1.21.1/src/main/resources/assets/async/icon.png" alt="Async icon" align="right">
<div align="left">
<h1>Async - Minecraft Entity Multi-Threading Mod</h1>
<h3>Async is a Fabric mod designed to improve the performance of entities by processing them in parallel threads.</h3>
</div>

## Important❗

**Async** is currently in alpha testing and is experimental. Using it may lead to incorrect behavior of entities and crashes.

## What is Async? 🤔

Async is a Fabric mod designed to enhance entity processing performance. The mod leverages multithreading, which allows multiple CPU cores to be used to improve performance when there are large numbers of entities.

### 💡 Key Benefits:

- ⚡ **Improved TPS**: Maintains stable tick times even with large numbers of entities.
- 🚀 **Multithreading**: Utilizes multiple CPU cores for parallel entity processing.

### 📊 Performance Comparison (3500 Villagers)

| Environment              | TPS   | MSPT             |
|--------------------------|-------|------------------|
| **Lithium + Modernfix + Async** | 20    | 40 / 50          |
| **Lithium + Modernfix**        | 4.3   | 150+             |
| **Purpur**                 | 4.61  | 150+             |

### 🛠️ Test Configuration

- **CPU**: Intel Core i7-10700
- **RAM**: 64 GB (16 GB allocated to the server)
- **Minecraft Version**: 1.21.1
- **Number of Entities**: 3500
- **Entity Type**: Villagers

## 📥 Download

The mod is available on [Modrinth](https://modrinth.com/mod/async)

## 🔄 Minecraft Version Support

Only the latest Minecraft version is fully supported. Older versions receive critical fixes. Support for old Minecraft snapshots is not planned.

## 📮 Support

Our issue tracker for feedback and bug reports is available at [GitHub Issues](https://github.com/AxalotLDev/Async/issues) or [Discord](https://discord.com/invite/scvCQ2qKS3)

## 🙌 Acknowledgements

This mod is based on the code from [MCMTFabric](https://modrinth.com/mod/mcmtfabric), which was based on [JMT-MCMT](https://github.com/jediminer543/JMT-MCMT). Huge thanks to Grider and jediminer543 for their invaluable contributions!

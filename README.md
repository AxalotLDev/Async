<div align="left">
<h1>Async Multiloader Edition!</h1>
</div>

## Important❗

I thought this would be a good place to go over what I changed against the main branch of Async.

## Mappings Changes

I changed all the mappings for the mixins from Yarn over to the official Mojang mappings. I eventually found the following as the best resource for accomplishing this: https://linkie.shedaniel.dev/mappings

## Common Module Limitations

I was unable to find a way (at least a clean way) to move the couple mixins that rely on totally obfuscated target values, to the common module. So to make it as crisp as I could, I decided that any mixin which contains anything like this will (in their entirety) exist only in platform-specific modules, and not in the common module.

## Build System Quirks

I've made an attempt to have all build values (identifiers, version numbers, etc.) cleanly contained within the gradle.properties. I've left the rest of the info here pretty much defaulted.

In the mixins.json files you may notice the "package" variable is hard-coded. This is to allow the Minecraft Dev Intellij plugin to properly detect the mixin package and provide autocomplete etc.

For some unholy reason I had to dump the Bawnorton maven repository in multiloader-common and common/build. 

## Config System

To get the config commands moved over to the common module, I made a platform event bus to allow platform-specific config code to fire when needed.

To clean up repeated hard-coded strings, I've set the config values to be key-value sets, so changes to their names can be made globally, and this will prevent typo mistakes etc.

## SynchronizePlugin

To remove the platform dependency on fabric, I decomposed the code I saw on the neoforge branch for the mixin2methodsexlude map building, and just dumped in the hard value.

I noticed that with the main release of the mod, I wasn't seeing synchronize bits being set any longer with the most recent version. I'm not sure if I'm just wrong here, but I put in a version that seems to work.

## ParallelProcessor

I added in a new "feature" behind a config value to the effect that the processor will more or less ignore ticking errors. What I have it set to try to do is recover the best it can and complete everything remaining either synchronously, or simply dump the current set of tasks if it has to. I personally feel this is preferable to hard crashing a server, and it is easily behind a config value so it poses no harm.


## pickUpItem

I added a synchro mixin for this method in MobMixin. I'm not sure why it wasn't put upstream here at the beginning, but I see it implemented in some mob mixins downstream, like for allays. I will also look around in the source code for any container entity that may call this function or something similar. There are a lot of possibilities for picking up items. I might try and look at a way of accomplishing this synchronization in the item entity itself.

## Final Thoughts

I hope there is nothing I missed, I was playing around a bit as I was building this so you may see some commits where I'm cleaning up garbage I left behind.




## 🙌 Acknowledgements

This mod is based on code from [MCMTFabric](https://modrinth.com/mod/mcmtfabric), which in turn was based on [JMT-MCMT](https://github.com/jediminer543/JMT-MCMT). Huge thanks to Grider and jediminer543 for their invaluable contributions!
You can also chat with us on Discord:
[![Chat with us on Discord](https://img.shields.io/badge/Chat%20with%20us%20on-Discord-blue)](https://discord.com/invite/scvCQ2qKS3)

# Applied Energistics 2 Supergiant

Applied Energistics 2 is a Minecraft mod about Matter, Energy and using them to conquer the world.

This repository is a Minecraft 1.12.2 Cleanroom migration of Applied Energistics 2. It adapts AE2's storage, networking, crafting, terminal, rendering, and addon API systems to the target runtime.

[Cleanroom](https://github.com/CleanroomMC/Cleanroom) is a 1.12.2 Forge fork, providing newer toolchain, new APIs and 99% compatibility.

Need Java25.

## Crafting

- **Recursive patterns**: a pattern whose output is also one of its inputs can be used in a plan as long as the whole chain produces more than it consumes, for example recycling an intermediate item back into the recipe.
- **Multiple patterns per request**: when an item has several patterns, the calculation uses them one after another until the requested amount is covered. You do not have to split the order by hand or write a separate pattern for every intermediate.
- **Force start with missing ingredients**: hold Shift and click Start to run the part of the plan that can run now. The ME Requester and the Crafting Card offer the same option.
- **Crafting tree**: open the full dependency tree from the crafting plan to see which pattern builds each step and on which machines, and Shift-click to highlight those machines in the world. You can show only the branches with missing ingredients, or save a screenshot.
- **Merge jobs**: a plan for the same final output can be merged into a running CPU job when the CPU has enough storage. Hold Ctrl to start a separate job instead.
- **Plan details**: hover an entry to see material usage, the amount actually crafted, how many times a pattern is called and the intermediate output amount. Sort the list by missing ingredients, requested amount or used percentage.
- **Recipe conflicts**: when several recipes produce the same item, choose which one to encode before writing the pattern.
- **Crafting CPU list**: search by name or current job output, sort by name, capacity, co-processors, operating time or task priority, and filter by idle state and auto-selection mode. Shift-left-click highlights the CPU structure in the world. CPUs can be renamed, and a plan can be sent to a CPU picked from the list.

## Terminals

- **Pins**: Ctrl + click an item to pin it above the terminal. The pin bar can have up to 8 rows of 9 slots. Pinned items either stay at the top of the list and scroll with it, or stay fixed at the top of the grid.
- **Freeze Display**: while enabled, items keep their position in the list instead of re-sorting when the network changes, and their amounts keep updating. In a large network or while crafting, the list no longer moves under the mouse.
- **Flow tracking**: the terminal records recent input and output rates for every item and shows them on hover. The flow view lists only items that moved. A key binding, unbound by default, highlights in the world the places where the item recently moved.
- **JEI integration**: hovering an item or fluid adds its amount in the network to the tooltip. Ctrl + middle-click pulls the item from the network, Alt + middle-click requests crafting. Recipes can be transferred into a crafting terminal. A storage cell opens its Storage Cell Contents in JEI, with stored types, bytes and bytes per type, and missing ingredients from a crafting plan can be added to the bookmark list in one click.
- **View pattern contents**: press P while hovering an encoded pattern to open its crafting grid or processing recipe, without placing it in a machine.
- **Pattern Encoding Access Terminal**: combines the pattern encoding terminal and the pattern access terminal in one screen. After writing a pattern, the providers in the network are shown and you can upload to the one you pick.
- **Storage Management Terminal**: view and edit the contents, partitions and priority of all cells and storage buses from one screen, including batch partitioning, a temporary cell staging area and a subnet overview.
- **ME Requester**: set a target amount and a batch size for an item, and the network starts crafting when the stock drops below it. The status shows idle, missing, no pattern, no crafting CPU, crafting, exporting and similar states. The Requester Terminal manages every requester in the network from one place.
- **Amount buttons**: the buttons that change an amount switch to 16, 64 or multiply and divide steps with Shift, Ctrl and Alt, and accept arithmetic expressions.

## Network Devices

- **Pattern providers**: each Pattern Expansion Card adds 9 pattern slots, and the slots are split into pages when they do not fit on one screen. Ingredients can be sent to stacked slots first, to empty slots first, or to empty slots only. Ingredients can be split across output sides by type. An encoded pattern can be uploaded to a provider in the network after a duplicate check, and one pattern can be bound to mappings on several providers.
- **Pattern Provider P2P tunnel**: distributes patterns to the machines behind several P2P outputs in turn, for recipes one machine cannot keep up with.
- **Advanced Memory Card**: lists every P2P tunnel in the network, filters by type and binding state, binds, unbinds and renames them in bulk, and highlights them in the world.
- **Multi-input P2P**: FE, EU, fluid, item and Pattern Provider P2P tunnels accept several inputs.
- **Export and storage buses**: the ME Stock Export Bus keeps a target container at a set amount. ME Precise buses treat every configured stack as a fixed amount. The ME Threshold Export Bus works only above or below a stock level. The Ore Dictionary and Mod buses filter by expression. The ME Advanced IO Bus handles input and output together and can be limited to selected resource types.
- **Interface paging**: interface stocking slots can span several pages of 18 slots each.
- **Network machines**: the Crystal Assembler works through its built-in recipes for processors, Fluix crystals, Entro Infused Ingots, upgrade cards and machines once materials are inserted, and does not need patterns. The ME Caner accepts fill and empty processing patterns. The Crystal Fixer upgrades damaged Certus Quartz and Entroized Fluix budding blocks to a higher tier so they can be harvested more often.
- **Cell restriction**: set a resource amount and type limit for a single cell in the Cell Workbench. The cell recalculates its usable bytes from that limit and stops accepting items beyond it.
- **Void cells**: accept only the items in their partition list and destroy them, optionally producing Matter Balls or Singularities. The portable version picks up and destroys the configured items from the inventory with a hotkey. The Portable Cell Workbench applies partitions and upgrades to a cell without placing it in a machine.

## Tools

- **Config Modifier**: applies an arithmetic operation to the configured amounts of a bus, including add, subtract, multiply, divide, set to a fixed value, clamp to a maximum or minimum, and clear.
- **Priority Tuner**: copies, pastes, increments or decrements priority, and applies the result to several targets in one go.
- **Pattern Modifier**: multiplies or divides the input and output amounts of a pattern, replaces an ingredient, converts an item ingredient to a fluid, clones a target pattern, or clears patterns back to blank. It works on patterns in the inventory and on patterns inside a pattern provider.
- **Wireless terminals**: the Magnet Card pulls nearby drops into the inventory or straight into the network, with whitelist and blacklist filters for pickup and for insertion. Restock, enabled with a hotkey, refills consumed items in the inventory back to their previous amount. The pattern encoding, pattern access, storage management and requester terminals all have wireless versions, and the Wireless Universal Terminal keeps the terminals installed in it and switches between them.

## Large Networks

Crafting calculations and storage synchronization use caching and incremental updates, so opening a terminal and calculating a large order take less time as the network grows.

## Addon Compatibility

This branch is not compatible with mods that depend on the 1.12.2 version of [AppliedEnergistics/Applied-Energistics-2](https://github.com/AppliedEnergistics/Applied-Energistics-2) or [AE2-UEL/Applied-Energistics-2](https://github.com/AE2-UEL/Applied-Energistics-2). For mods that only use parts of the API of those two projects, [AppliedCompatibility](https://github.com/FormlessDragon/AppliedCompatibility) provides a compatibility layer. AppliedCompatibility is the compatibility mod for this project; it restores compatibility with those mods without depending on either of the two original projects, and should usually be installed alongside this one.

## Upstream

This project is based on [AppliedEnergistics/Applied-Energistics-2](https://github.com/AppliedEnergistics/Applied-Energistics-2).

The upstream repository remains the original AE2 project and the primary reference for design, behavior, licensing, and attribution.

## Documentation

* [API.md](API.md): API notes for this branch
* [Upstream repository](https://github.com/AppliedEnergistics/Applied-Energistics-2)

## Maven

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation "com.github.FormlessDragon:Applied-Energistics-2-Supergiant:v1.1.0:dev"
}
```

## Integrated

* [Lapis256/AE2ToggleableViewCell](https://github.com/Lapis256/AE2ToggleableViewCell/tree/1.21.1): integrated the toggleable view cell feature with partial code reuse under the MIT License.
* [Mari023/AE2WirelessTerminalLibrary](https://github.com/Mari023/AE2WirelessTerminalLibrary): integrated AE2WTL wireless terminal behavior with partial code reuse under the MIT License; textures follow upstream notices.
* [NovaEngineering-Source/AE2CT-Legacy](https://github.com/NovaEngineering-Source/AE2CT-Legacy): integrated AE2CT-Legacy under the LGPL-3.0 License.
* [GlodBlock/ExtendedAE](https://github.com/GlodBlock/ExtendedAE): ported selected EAE machines, tools, recipes, GUIs, and resources into the `ae2` namespace under the LGPL-3.0 License.
* [GlodBlock/AE2 Network Analyser](https://github.com/GlodBlock/ExtendedAE/tree/analyser/1.21.1-neoforge): integrated AE2 Network Analyser under LGPL-3.0 license.
* [pedroksl/AdvancedAE](https://github.com/pedroksl/AdvancedAE): ported selected AdvancedAE parts, recipes, GUIs, and behavior into the `ae2` namespace under the LGPL-3.0 License.
* [AlmostReliable/merequester](https://github.com/AlmostReliable/merequester): integrated ME Requester under LGPL-3.0 license.
* [62832/MEGACells](https://github.com/62832/MEGACells): integrated Portable Cell Workbench assets under CC BY-NC-SA 3.0.
* [yuuki1293/AE2PatternEncodingAccessTerminal](https://github.com/yuuki1293/AE2PatternEncodingAccessTerminal): integrated AE2PatternEncodingAccessTerminal with partial code reuse and assets, all under the MIT license.
* [Aedial/DiskTerminal](https://github.com/Aedial/DiskTerminal): heavily referenced its implementation under the MIT license.

## License

* Applied Energistics 2 API
  * (c) 2013 - 2020 AlgorithmX2 et al
  * [![License](https://img.shields.io/badge/License-MIT-red.svg?style=flat-square)](http://opensource.org/licenses/MIT)
* Applied Energistics 2
  * (c) 2013 - 2020 AlgorithmX2 et al
  * [![License](https://img.shields.io/badge/License-LGPLv3-blue.svg?style=flat-square)](LICENSE)
* Textures and Models
  * (c) 2020, [Ridanisaurus Rid](https://github.com/Ridanisaurus/), © 2013 - 2020 AlgorithmX2 et al
  * [![License](https://img.shields.io/badge/License-CC%20BY--NC--SA%203.0-yellow.svg?style=flat-square)](https://creativecommons.org/licenses/by-nc-sa/3.0/)
* Text and Translations
  * [![License](https://img.shields.io/badge/License-No%20Restriction-green.svg?style=flat-square)](https://creativecommons.org/publicdomain/zero/1.0/)

## Credits

Thanks to Team Applied Energistics, AlgorithmX2, the upstream AE2 contributors, and everyone involved in the original project:
[AppliedEnergistics/Applied-Energistics-2](https://github.com/AppliedEnergistics/Applied-Energistics-2).

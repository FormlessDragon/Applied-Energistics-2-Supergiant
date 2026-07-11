---
title: Addon and Mod API
# Note that this file is automatically included into the Website and is available at https://appliedenergistics.github.io/api.html
---

## Source Layout

This branch exposes AE2's public API from `src/main/java/ae2/api`.

The `src/api/java` source set contains compatibility stubs for optional external mods. It is compiled as the
`stubApi` source set, is placed on the main source set's compile classpath, and is then removed from the final AE2 jar.
Use `src/main/java/ae2/api` as the public AE2 API surface.

## Mod Initialization

AE2 offers various extension points for your mod to hook into. The following table lists the API classes that are most
relevant during normal Forge mod initialization:

| Class                                          | Purpose                                                                                             |
|------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| `ae2.api.stacks.AEKeyTypes`                 | Addons can register custom storage types similar to `AEItemKey` and `AEFluidKey`.                   |
| `ae2.api.networking.GridServices`           | Addons can register their own grid-wide services here.                                              |
| `ae2.api.movable.BlockEntityMoveStrategies` | Allows mods to register custom strategies for moving tile entities in and out of spatial storage.   |
| `ae2.api.features.GridLinkables`            | For working with and adding items that can be linked to a grid in the security station.             |
| `ae2.api.storage.StorageCells`              | For working with and adding items that serve as storage cells for grids.                            |
| `ae2.api.features.Locatables`               | For discovering quantum network bridges and other locatable objects based on their unique keys.     |
| `ae2.api.parts.PartModels`                  | For registering JSON block models used by custom cable bus parts.                                   |
| `ae2.api.features.P2PTunnelAttunement`      | For registering new items that attune P2P tunnels to specific types when right-clicked.             |
| `ae2.api.client.StorageCellModels`          | For customizing the models of storage cells when they are inserted into drives or ME chests.        |
| `ae2.api.upgrades.Upgrades`                 | For managing upgrade cards and associating them with upgradable items, parts, or blocks.            |
| `ae2.api.upgrades.UpgradeInventories`       | For creating upgrade inventories for upgradable machines and item-backed hosts.                     |
| `ae2.api.networking.extensions.GridLogicExtensions` | Adds runtime behavior to supported AE2 grid logic instances without mixins.                  |
| `ae2.api.behaviors.GenericInternalInventoryAdapters` | Allows addons to expose AE2 generic inventories through Forge capabilities.                         |

In general, these registries are synchronized and may be used during mod loading. Finish registration before gameplay
starts using the affected systems. Changes after mod initialization can leave already-created grids, storage cells,
models, or upgrade inventories with stale assumptions.

## Item and Fluid Keys

Item and fluid types are represented by keys in AE2. The `AEKey` class is the base for all keys, whether they represent
items (`AEItemKey`) or fluids (`AEFluidKey`). Most AE2 interfaces are generic in that they accept any `AEKey`, whether
it represents a fluid, item, or addon-provided key type.

Keys do not have counts because they represent a type of resource. The amount is carried separately by storage,
crafting, transfer, and display APIs. For items, an `AEItemKey` consists of the `Item`, the 1.12.2 metadata/damage
value, the maximum stack size captured from the original stack, and optional NBT. For fluids, an `AEFluidKey` consists
of the `Fluid` and optional `FluidStack` tag data.

To represent a stack of some key, AE2 provides `GenericStack`. It consists of a key and an amount. It can be converted
from `ItemStack` or `FluidStack`, serialized to NBT, written to packets, and wrapped into an `ItemStack` for display
or filtering.

Each type of key is represented by an instance of `AEKeyType`, accessible via `AEKey.getType()`. It stores properties
common to all keys of a type, such as amount formatting, bytes per amount, operation amount, the packet reader, and
the NBT reader. New key families are registered through `AEKeyTypes.register`.

Key types that cannot be delivered as crafting CPU output should override
`AEKeyType.isCraftingCpuInsertable()` to return `false`. AE2 will then neither prefer the crafting CPU storage mount
for the type nor forward inserts to crafting CPUs. This is appropriate for resource families such as externally
produced energy that may be stored in a network but are not valid crafting results. It does not disable pattern
registration or crafting-plan calculation for the key type.

Keys can be saved to NBT using `toTagGeneric`, which also stores a reference to their type so that
`AEKey.fromTagGeneric` can restore the key without the caller knowing the exact key class. The same mechanism can be
used for packets with `AEKey.writeKey`, `AEKey.writeOptionalKey`, `AEKey.readKey`, and `AEKey.readOptionalKey`.

Use `dropSecondary()` and `getPrimaryKey()` for fuzzy matching and indexing scenarios where the primary resource type
matters more than NBT or other secondary data. For items in this branch, damage is also exposed through
`getFuzzySearchValue()` so 1.12.2 durability-based fuzzy filtering can work.

Example when your code only supports item keys:

```java
if (key instanceof AEItemKey) {
    AEItemKey itemKey = (AEItemKey) key;
    ItemStack stack = itemKey.toStack();
    // [...]
}
```

Example when your code only supports fluid keys:

```java
if (key instanceof AEFluidKey) {
    AEFluidKey fluidKey = (AEFluidKey) key;
    FluidStack stack = fluidKey.toStack(1000);
    // [...]
}
```

## Custom Key Type Machine Integration

Registering an `AEKeyType` makes AE2 able to serialize, store, display, and route that family of keys internally. It
does not automatically teach Forge item or fluid pipes, or another mod's custom capability, how to move that resource.
Addon resource types normally need the following registrations during mod initialization:

* `AEKeyTypes.register(...)` for the key family.
* `GenericSlotCapacities.register(...)` for interface and pattern provider slot limits.
* `ExternalStorageStrategy.register(...)` so AE2 storage buses and pattern providers can wrap external inventories as
  `MEStorage`.
* `GenericInternalInventoryAdapters.register(...)` so AE2's own generic inventories, such as the ME interface and
  pattern provider return inventory, can be exposed through the addon's Forge capability.
* Optional behavior strategies such as `StackImportStrategy`, `StackExportStrategy`, `PlacementStrategy`,
  `PickupStrategy`, and `ContainerItemStrategy` when the key type should work with buses, planes, or container items.

AE2 automatically applies registered `GenericInternalInventoryAdapters` to AE2-owned hosts that expose
`AECapabilities.GENERIC_INTERNAL_INV`. This includes AE2's ME interfaces, pattern providers, and other generic
inventory hosts in this branch. It does not automatically modify another mod's tile entities or parts. If your mod owns
the external machine or capability provider, your mod remains responsible for exposing its own Forge capability or AE2
storage wrapper there.

Minimal adapter shape:

```java
GenericInternalInventoryAdapters.register(MY_CAPABILITY, MyCapabilityHandler::new);

final class MyCapabilityHandler implements IMyCapability {
    private final GenericInternalInventory inv;

    MyCapabilityHandler(GenericInternalInventory inv) {
        this.inv = inv;
    }

    // Convert between your resource stack and your custom AEKey,
    // then call inv.insert(...), inv.extract(...), inv.getKey(...), and inv.getAmount(...).
}
```

## Grids and Nodes

AE2's core systems work by building grids from grid nodes that are created and owned by in-game objects such as tile
entities or parts. Grids are never created directly. They form and disband automatically by creating grid nodes, and
connecting or disconnecting them through world adjacency or explicit virtual links.

**NOTE:** Grids are purely a server-side concept. They do not exist on the client.

### Node Owners and Listeners

Every node is owned by an in-game object. An owner can be a tile entity, part, item-backed host, or another object
that needs to participate in the network. The owner does not need to implement a dedicated API interface. This makes it
possible to integrate existing game objects with AE2 without forcing all host classes into the same inheritance model.

The node uses a listener (`IGridNodeListener<T>`) to interact with its owner. Both owner and listener are passed
together to `GridHelper.createManagedNode(owner, listener)`. Keeping the listener separate allows a single listener
instance to be reused while still having type-safe access to the owner.

The listener is responsible for adapting node events back into host behavior. Typical actions include marking a tile
entity dirty when node data changes, refreshing client rendering when visible connections change, and updating block
state when power, channel, or grid boot state changes.

**Example:**

```java
class MyTileListener implements IGridNodeListener<MyTileEntity> {
    static final MyTileListener INSTANCE = new MyTileListener();

    @Override
    public void onSaveChanges(MyTileEntity nodeOwner, IGridNode node) {
        nodeOwner.markDirty();
    }

    @Override
    public void onStateChanged(MyTileEntity nodeOwner, IGridNode node, State state) {
        // For example: update block state, refresh rendering, or sync the tile to clients.
    }
}
```

```java
class MyTileEntity extends TileEntity {
    private final IManagedGridNode mainNode =
        GridHelper.createManagedNode(this, MyTileListener.INSTANCE);
}
```

### Managed Grid Nodes

`IManagedGridNode` simplifies the lifecycle of creating and destroying grid nodes, and also centralizes the node's
configuration. Managed grid nodes can be constructed on both logical sides. On the client side they will never expose a
server grid node; on the server side they become ready after `create(World, BlockPos)` succeeds.

Your game object should notify the managed node about the following events:

* When your game object loads from NBT data, call `loadFromNBT`. This has to occur before `create(World, BlockPos)`.
* Call `create(World, BlockPos)` when the owner is in-world and ready to make outgoing connections. For tile entities,
  use `GridHelper.onFirstTick` to defer creation until the tile is in a ticking chunk.
* When your game object saves to NBT data, call `saveToNBT`.
* Call `destroy` when your game object is invalidated, removed, or unloaded.

Managed nodes also configure network behavior. Use `setFlags` for grid flags, `setIdlePowerUsage` for passive power
drain, `setGridColor` to restrict adjacent color connections, `setVisualRepresentation` for UI display, and
`setOwningPlayer` or `setOwningPlayerId` for security ownership.

### In-World Nodes

The main type of grid node is the in-world grid node. It needs its `World` and `BlockPos` when created with
`IManagedGridNode.create(World, BlockPos)`. AE2 automatically attempts external connections with adjacent in-world
grid nodes.

In-world nodes can be selectively exposed on specific sides, or on all sides. The exposed sides can be changed after
node creation through `setExposedOnSides(...)`, and the change will trigger repathing. Use `setInWorldNode(false)` for
nodes that should be attached to a grid but hidden from normal world adjacency.

To expose the actual `IGridNode` to other systems, return `IManagedGridNode.getNode()` through an appropriate
capability or host interface, such as `IInWorldGridNodeHost`.

### Virtual Nodes

Virtual nodes do not automatically form connections with nearby world nodes. They allow addons to build ME network
topologies that are not represented by normal block adjacency.

Virtual links must be created explicitly with `GridHelper.createConnection(IGridNode, IGridNode)`. Removing the
connection is handled by destroying the corresponding node, which also handles unload cleanup and prevents old
connections from lingering.

### Node Services

A node owner can add services to a node through `IManagedGridNode.addService(...)`. Services are represented by
interfaces extending `IGridNodeService`. They allow nodes to opt into additional grid-managed behavior.

Node services are often consumed by grid services. For example, the ticking service looks for `IGridTickable`, the
storage service looks for `IStorageProvider`, and the crafting service looks for crafting provider node services.
This model keeps optional behavior attached to the nodes that need it.

### Grid Services

Each grid provides several services to machines connected to the grid.

AE2 provides its default services through `GridServices`. Addons can register their own grid-wide services there as
well. Services can be retrieved by calling `IGrid#getService` with the service interface. For AE2's default services,
`IGrid` also offers convenience methods such as `getStorageService()` and `getCraftingService()`.

#### Energy

**Service Interface:** `IEnergyService`  
**Convenience Getter:** `IGrid.getEnergyService()`

This service allows energy to be extracted from and injected into the grid's energy storage, including energy cells,
the grid's internal storage, and other grid-connected energy providers.

#### Ticking

**Service Interface:** `ITickManager`  
**Convenience Getter:** `IGrid.getTickManager()`

AE2 offers its grid-connected machines an advanced ticking system with the following features:

* Ticking without being a tickable tile entity
* Variable tick rates
* Putting devices to sleep if they run out of work
* Waking sleeping devices in reaction to some event, such as neighbor changes or new work becoming available

The grid's `ITickManager` service handles the per-grid aspects of this ticking system. It offers an API to manage the
sleep and wake status of grid nodes.

To participate in the ticking system, your grid node must provide the `IGridTickable` node service. The
`ITickManager` reacts to the presence of this service when your grid node joins the grid. `IGridTickable` returns a
`TickingRequest` to describe desired responsiveness, then returns `TickRateModulation` from each tick to speed up,
slow down, sleep, or keep the current rate.

#### Storage

**Service Interface:** `ae2.api.networking.storage.IStorageService`  
**Convenience Getter:** `IGrid.getStorageService()`

Storage in grids is organized as mounted `MEStorage` inventories. The storage service exposes the unified grid
inventory through `getInventory()`, provides a cached inventory snapshot through `getCachedInventory()`, and manages
`IStorageProvider` mounts from nodes and global providers.

`getCachedInventory()` is updated at most once per tick and should be preferred when slightly outdated content is
acceptable. This avoids repeatedly walking the full network inventory.

Node-backed storage should implement `IStorageProvider` as a node service. When the node joins or leaves a grid, the
storage service will mount or unmount it automatically by calling `mountInventories(...)`. Global storage providers
can be added with `IStorageService.addGlobalStorageProvider(...)` when the storage is provided by a grid service rather
than by an individual node.

When a storage provider needs to remove, add, or rebuild its mounts due to an external event or config change, call
`IStorageProvider.requestUpdate(managedNode)` for node providers, or `refreshGlobalStorageProvider(...)` for global
providers.

#### Auto-Crafting

**Service Interface:** `ICraftingService`  
**Convenience Getter:** `IGrid.getCraftingService()`

This service provides access to craftable patterns, crafting CPUs, job calculation, job simulation, job submission,
and active-request tracking. Craftable keys are queried through this service rather than being reported as ordinary
stored network contents.

#### Merged Pattern Push

Crafting providers can opt a pattern into a merged push path through
`ICraftingProvider.canMergePatternPush(IPatternDetails)`. This method only decides whether the CPU may use the special
batch path. If it returns `false`, the CPU must use the normal one-pattern dispatch attempt and must not call
`getMaxPatternPushMultiplier(...)`.

When `canMergePatternPush(...)` returns `true`, the CPU calls
`ICraftingProvider.getMaxPatternPushMultiplier(IPatternDetails, int maxMultiplier)`. Implementations must return a
value from `0` to `maxMultiplier`. Returning `0` means this provider is currently unavailable for that pattern in this
pass: the CPU will not extract inputs, will not spend energy, and will not fall back to a single-pattern push for that
provider during that pass.

Pushing `N` merged copies is equivalent to `N` consecutive successful one-pattern pushes for inputs, expected outputs,
and crafting CPU operation cost. Addon providers that cannot guarantee this equivalence should return `false` from
`canMergePatternPush(...)`.

Pattern providers that dispatch into adjacent machines may use
`ae2.api.implementations.blockentities.IPatternProviderBatchTarget` on the adjacent crafting machine to ask for a
direction-specific maximum receive count. Without that interface, AE2 falls back to the normal one-pattern crafting
machine path. For external inventories, AE2 probes the selected target group with simulated inserts and does not split
one merged dispatch across unrelated fallback directions.

#### Pattern Containers and Assembler Patterns

Pattern access terminal providers expose their pattern inventory through the public
`ae2.helpers.patternprovider.PatternContainer` type. The method `PatternContainer.isAssemblerPatternContainer()` is a
type boundary, not just a UI hint. Containers returning `true` accept and expose only assembler patterns. Containers
returning `false` accept and expose only non-assembler patterns. AE2 uses this boundary when collecting provider
inventories for the pattern access terminal, inserting patterns through the terminal, quick-moving patterns, checking
duplicate patterns, and publishing patterns to the crafting service.

Assembler patterns implement `ae2.api.crafting.IAssemblerPattern`. They are patterns that are executed internally by
assembler-style machines such as the molecular assembler instead of being pushed into an external inventory. An
assembler pattern can describe item substitution and direct fluid use through `canSubstitute()` and
`canSubstituteFluids()`. These properties belong to assembler patterns only. Non-assembler patterns are fixed-input
patterns and should not expose substitution or direct-fluid behavior.

This distinction matters for addon providers: ordinary pattern providers can assume non-assembler pattern inputs are
fixed keys and fixed key types when dispatching materials. If an addon pattern can substitute inputs, use an assembler
pattern implementation and expose it through an assembler pattern container.

### Forced Start for Missing Crafting Ingredients

Crafting calculation can now produce submit-capable plans even when some ingredients are missing. This is primarily
used by the player crafting confirmation flow, and it is also available to automation through explicit opt-in.

`ICraftingPlan.missingItems()` always reports the missing ingredient totals for the calculated plan. A plan carrying
missing items can still be submit-capable. In that case, `ICraftingPlan.simulation()` remains `false`, and the missing
items are converted into CPU `waitingFor` requests once the job starts.

Use `CalculationStrategy.REPORT_MISSING_ITEMS` when you want the full requested amount to be planned, including the
missing branch. Use `CalculationStrategy.CRAFT_LESS` when you want AE2 to reduce the requested amount to what can be
crafted immediately.

`ICraftingService` exposes an overload for explicit forced submission:

```java
ICraftingSubmitResult submitJob(
    ICraftingPlan job,
    @Nullable ICraftingRequester requestingMachine,
    @Nullable ICraftingCPU target,
    boolean prioritizePower,
    IActionSource src,
    boolean forceStart);
```

When `forceStart` is `true`, a submit-capable plan with missing ingredients may be submitted. The crafting CPU will
extract all currently available inputs, place only the remaining missing amounts into its waiting list, and continue
when those items later arrive through normal crafting or external insertion.

The older `submitJob(...)` signature remains conservative and behaves as `forceStart = false`.

Automation can opt in by implementing `ae2.api.networking.crafting.ICraftingForceStartRequester`, which is a direct
extension of `ICraftingRequester`:

```java
public interface ICraftingForceStartRequester extends ICraftingRequester {
    boolean canForceStartCrafting(ICraftingPlan plan);
}
```

`MultiCraftingTracker` checks this interface after calculation completes. If the requester returns `true`, it forwards
`forceStart = true` to `ICraftingService.submitJob(...)`. Requesters that do not implement the interface, or return
`false`, keep the conservative behavior.

Current built-in automated requesters that implement this contract:

* `ae2.helpers.InterfaceLogic`
* `ae2.parts.automation.ExportBusPart`

Both use the installed AE2 crafting card upgrade to decide whether forced start is allowed. The requester only opts in
when it has a crafting card with its force-start mode enabled. This keeps normal automation conservative while still
allowing explicit machine-driven forced crafting.

#### Pathing

**Service Interface:** `IPathingService`  
**Convenience Getter:** `IGrid.getPathingService()`

This service provides channel and controller/pathing state for the grid. Nodes can use it to inspect whether the grid
is booting and whether channel requirements are currently satisfied.

#### Spatial I/O

**Service Interface:** `ISpatialService`  
**Convenience Getter:** `IGrid.getSpatialService()`

This service provides information about the currently defined spatial region, including bounds, validity, world, and
required power.

## Adding New Upgrades or Making Upgradable Machines

Relevant APIs:

* `ae2.api.upgrades.Upgrades` for managing upgrade cards and associating them with machines
* `ae2.api.upgrades.UpgradeInventories` for creating upgrade inventories for use in upgradable machines or items
* `ae2.api.upgrades.IUpgradeInventory` for querying and iterating installed upgrades

### Custom Upgrade Cards

Each upgrade is uniquely identified by a registered item, called the upgrade card. To create a custom upgrade card
that behaves like AE2's own upgrade cards, use `Upgrades.createUpgradeCardItem()` to create an item for your card.
Register that item normally and provide its model and translation key.

AE2 handles the supported-machine tooltip and right-click insertion behavior for upgrade card items created this way.
Use `Upgrades.isUpgradeCardItem(...)` when you need to identify upgrade cards.

### Associating Upgrade Cards with Machines

For both cases where your addon adds a custom machine or a custom upgrade card, associate possible upgrades with
potential machines. `Upgrades.add(upgradeCard, upgradableObject, maxSupported)` links an upgrade card item with an
upgradable item, part item, or block item.

If several machines are treated equally for tooltip display, pass a translation key to the `tooltipGroup` parameter
through `Upgrades.add(upgradeCard, upgradableObject, maxSupported, tooltipGroup)`. When displaying the tooltip for an
upgrade card, all supported machines with the same `tooltipGroup` are merged into a single translated line. AE2 uses
this for related block/part forms and related item/fluid variants.

### Physical Upgrade Slots

The maximum number of copies of a card accepted by `Upgrades.add(...)` is independent from the number of physical
slots on a machine. A machine may deliberately expose fewer slots than the sum of all supported card maxima, requiring
the player to choose between compatible upgrades.

Addons can contribute physical slots to an existing machine with `Upgrades.addUpgradeSlots(...)`:

```java
ResourceLocation id = new ResourceLocation("examplemod", "high_voltage_slot");

Upgrades.add(HIGH_VOLTAGE_CARD, MY_MACHINE_ITEM, 1);
Upgrades.addUpgradeSlots(MY_MACHINE_ITEM, id, 1);
```

The contribution id is unique per machine item. Reusing an id, supplying a non-positive slot count, or registering
after the machine's upgrade-slot count has first been resolved throws an exception. Distinct addon ids are additive.
This makes a feature that adds a card and a slot explicit while retaining the machine author's base capacity and
upgrade trade-offs.

`UpgradeInventories.forMachine(machine, baseSlots, callback)` automatically includes registered contributions.
Custom machine inventories should call `UpgradeInventories.getMachineUpgradeSlots(machine, baseSlots)` exactly once
when constructing their inventory. Both methods freeze further slot contributions for that machine, so register them
during mod initialization before any instance of the machine can be created.

### Making Custom Machines or Items Upgradable

Use `UpgradeInventories` to create inventories for storing upgrade cards. These inventories use the provided machine
or item identity to decide which upgrade cards are accepted, and they prevent incompatible cards from being inserted.

They also offer convenience methods through `IUpgradeInventory` to quickly check if an upgrade is present, count how
many upgrades of a type are installed, and iterate over installed cards.

For the machine version created by `UpgradeInventories.forMachine`, save the inventory from the change callback. For
the item version created by `UpgradeInventories.forItem`, the upgrade inventory writes changes directly into the
provided `ItemStack` NBT. The item variant also accepts an optional change callback.

## Extending Built-In Grid Logic

`GridLogicExtensions` lets addons attach independent runtime behavior to supported AE2 grid logic without mixins,
access transformers, or references to private fields. The current built-in integration points are ME interfaces and
pattern providers, for both their block and part forms.

Register a factory for each machine item during mod initialization:

```java
ResourceLocation id = new ResourceLocation("examplemod", "network_monitor");
GridLogicExtensions.register(MY_MACHINE_ITEM, id, MyLogicExtension::new);
```

Registration ids are unique per machine item. Registrations for a machine are frozen when its first logic instance is
created; duplicate or late registrations throw an exception so a world cannot contain machines with different
extension sets.

The factory receives a `GridLogicContext`, which exposes the machine item, owning block entity or part, host tile,
managed grid node, action source, upgrade inventory, and a current snapshot of target sides. The host tile is the
containing `TileEntity`; for a part, the owner returned by `getOwner()` is the part while the host tile is normally the
cable bus tile.

Implement `GridLogicExtension` for lifecycle callbacks:

```java
final class MyLogicExtension implements GridLogicExtension {
    private final GridLogicContext context;

    MyLogicExtension(GridLogicContext context) {
        this.context = context;
    }

    @Override
    public void initialize(GridLogicContext context) {
        context.getManagedNode().addService(IMyNodeService.class, new MyNodeService(context));
    }

    @Override
    public void onUpgradesChanged() {
        // Reconfigure behavior after AE2 has processed the upgrade change.
    }

    @Override
    public void onNeighborChanged(EnumFacing side) {
        // Invalidate state associated with this adjacent side.
    }
}
```

Factories create all extensions first. AE2 then calls `initialize(...)` after the complete extension list is attached
to the owning logic, so initialization can safely cause follow-up logic activity. `onUpgradesChanged()` is dispatched
after AE2's native upgrade handling. Neighbor callbacks are dispatched server-side for immediately adjacent block
changes; an extension should use `context.getTargetSides()` when it only handles current output sides, because a
pattern provider's selected side can change at runtime.

## Wireless Terminals

Wireless terminals are registered through `AddWirelessTerminalEvent`. Register your handler during mod loading, before
AE2 finishes wireless terminal registration. Registration is frozen after AE2 runs the event; duplicate ids, missing
fields, invalid upgrade slot counts, and registrations after the event fail with an exception.

| Class                                                             | Purpose                                                                 |
|-------------------------------------------------------------------|-------------------------------------------------------------------------|
| `ae2.api.implementations.items.AddWirelessTerminalEvent`       | Registers terminal definitions during AE2 initialization.                |
| `ae2.api.implementations.items.WirelessTerminalDefinition`     | Read-only terminal definition used by hotkeys, universal terminals, and GUIs. |
| `ae2.api.implementations.items.WirelessTerminalDefinitionBuilder` | Builder for registering a wireless terminal definition.                  |
| `ae2.api.implementations.items.WirelessTerminalApi`            | Lookup helpers and universal terminal helpers.                           |
| `ae2.api.implementations.items.WirelessTerminalUpgradeHelper`  | Registers upgrade cards against all registered wireless terminals.       |

Definitions contain a unique id, the terminal item, an icon factory, a GUI opener, a host factory, a hotkey name, and
the number of upgrade slots supported by that terminal. Terminal items should extend `WirelessTerminalItem`.

Example registration:

```java
AddWirelessTerminalEvent.register(event -> event.builder(
        "example",
        MY_WIRELESS_TERMINAL,
        (definition, player, locator, stack, returningFromSubmenu) -> {
            // Open your GUI here. Return true when it was opened.
            return true;
        },
        (stackItem, terminalItem, player, locator, returnToMainContainer) -> {
            // Return your WirelessTerminalGuiHost implementation.
            return new MyWirelessTerminalGuiHost(stackItem, terminalItem, player, locator, returnToMainContainer);
        },
        terminal -> new ItemStack(terminal))
    .hotkeyName("wireless_example_terminal")
    .upgradeSlots(2)
    .addTerminal());
```

Use `noUpgrades()` for terminals that do not accept upgrade cards. `upgradeCount(int)` is an alias for
`upgradeSlots(int)`.

`WirelessTerminalApi.wirelessTerminals()`, `ofId(...)`, `ofItem(...)`, and `ofStack(...)` expose the registered
definitions. `makeUniversalTerminal(...)`, `mergeUniversalTerminal(...)`, and `selectTerminal(...)` are helper methods
for working with the wireless universal terminal. Missing or unregistered terminal definitions are ignored by the
universal terminal selection UI and hotkey lookup.

To make an upgrade card available to every registered wireless terminal, call
`WirelessTerminalUpgradeHelper.addUpgradeToAllTerminals(upgradeCard, maxSupported)`. Pass `0` as `maxSupported` to use
each terminal definition's own upgrade slot count. The wireless universal terminal receives the combined supported
count for registered terminals.

### Pattern Access Terminal Quick Move Targets

The pattern access terminal can quick-move crafting patterns into compatible pattern providers.

For crafting patterns, compatibility is declared through
`ae2.api.implementations.items.ICraftingPatternQuickMoveHost`.

Implement this marker interface on either:

* an item, if the pattern provider group icon is a normal item
* a block, if the pattern provider group icon is represented by an `ItemBlock`

When AE2 evaluates a pattern provider group for crafting-pattern quick move, it first checks whether the group's icon
item implements `ICraftingPatternQuickMoveHost`. If not, and the icon item is an `ItemBlock`, it checks whether the
backing block implements the same interface.

This allows addons to opt custom machines into the pattern access terminal's crafting-pattern quick-move behavior
without hard-coding specific AE2 block ids.

## Porting from Older AE2 APIs

This branch uses the key-based storage and crafting API. Older addons built around `IAEStack`, `IAEItemStack`,
`IAEFluidStack`, and channel-specific inventories need to move to the current model.

`IAEStack`, `IAEItemStack`, and `IAEFluidStack` have been replaced by an API that separates the "what" from the "how
much". `AEKey` identifies what is being transferred or stored, while a separate method argument or `GenericStack`
stores the amount.

The mapping is roughly as follows:

| Older Class or Idea        | Current API                                  |
|----------------------------|----------------------------------------------|
| `IAEStack`                 | `GenericStack`, `AEKey`                      |
| `IAEItemStack`             | `GenericStack`, `AEItemKey`                  |
| `IAEFluidStack`            | `GenericStack`, `AEFluidKey`                 |
| `IStorageChannel`          | `AEKeyType`                                  |
| `StorageChannels`          | `AEKeyTypes`                                 |
| `StorageChannels.items()`  | `AEKeyType.items()`                          |
| `StorageChannels.fluids()` | `AEKeyType.fluids()`                         |
| `IMEInventory`             | `MEStorage`                                  |
| `IMEMonitorable`           | `IStorageService.getInventory()` or storage watchers |
| `ICraftingMedium`          | `ICraftingMachine`                           |
| `ICellProvider`            | `IStorageProvider`                           |
| `getUnitsPerByte`          | `getAmountPerByte`                           |
| `transferFactor`           | `getAmountPerOperation`                      |

The network inventory is no longer channel-specific. It contains items, fluids, and addon-provided key types at the
same time. Use `AEKeyFilter.none()` as the no-op filter when you want all key types, `AEItemKey.filter()` for items,
and `AEFluidKey.filter()` for fluids.

Stack watching now sends keys for which the stored amount has changed. Treat the key and amount as separate values in
your addon code.

Craftable items are provided by `grid.getCraftingService().getCraftables()` and related crafting APIs. They are
modeled by the crafting service instead of ordinary network storage.

The crafting service also tracks exact encoded pattern definitions provided by the network. Use
`ICraftingService.isKnownPattern(AEItemKey patternDefinition)` to check whether an encoded pattern item is already
known to the grid.

Mounting storage into the network storage has changed. Since storage is unified across key types, the storage service
calls `mountInventories` on `IStorageProvider` services provided by grid nodes and allows each provider to mount
storage into the network. When the node wants to remove or add storage due to an external event or config change, it
can request the mounting process again by calling `IStorageProvider.requestUpdate(managedNode)`. This replaces sending
cell-array refresh events directly.

For item-opened GUIs, this branch uses `IGuiItem` and `ItemGuiHost`.

## Internal APIs

The following internal-facing changes may still be useful to addons that depend on AE2 implementation details.

Items that open AE GUIs are represented by `IGuiItem`. `ItemGuiHost` can be used as a convenient host for terminals
and other item-opened screens.

The priority and crafting-confirm flows use `ISubGuiHost` so hosts can return to the previous screen after a nested
screen closes.

Custom storage cells are based on the unified key-storage model. Addon cells should expose `MEStorage` through the
appropriate cell or capability APIs and should use key filters to restrict accepted key types when needed. Item and
fluid storage math can still differ, so prefer the public cell APIs in `ae2.api.storage.cells` over depending on
AE2 implementation classes.

## Crank

The crank uses `ICrankable` to inject energy into the block it is attached to when the player turns the crank.

On Forge 1.12.2, expose `AECapabilities.CRANKABLE` on your tile entity. You can limit which sides of your block a
crank is allowed on by only returning a non-null `ICrankable` for the allowed sides. AE2 also provides
`ICrankable.get(World, BlockPos, EnumFacing)` as a lookup helper.

Example:

```java
@Nullable
private ICrankable getCrankable(EnumFacing side) {
    if (side == EnumFacing.UP || side == EnumFacing.DOWN) {
        return new ICrankable() {
            @Override
            public boolean canTurn() {
                return getStoredPower() < getMaxStoredPower();
            }

            @Override
            public void applyTurn() {
                injectExternalPower(...);
            }
        };
    }

    return null;
}
```

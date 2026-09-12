---
title: 附属与模组 API
---

## 源码布局

本分支的公开 API 位于 `src/main/java/ae2/api`。HEI/JEI 等可选集成由构建脚本提供的编译期依赖接入，而不是通过内置的桩源码。

AE2 自身内容的稳定引用以常量形式提供，位于 `ae2.api.ids`（`AEItemIds`、`AEBlockIds`、`AEPartIds`、`AECreativeTabIds`），共享常量位于 `AEConstants`。请优先使用这些常量，而不是手动构造 `ResourceLocation`。

## 模组初始化

AE2 为你的模组提供了多种扩展点。下表列出了在常规 Forge 模组初始化期间最相关的 API 类：

| 类                                             | 用途                                                                                               |
|------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| `ae2.api.stacks.AEKeyTypes`                 | 附加模组可以注册自定义存储类型，类似 `AEItemKey` 与 `AEFluidKey`。                                   |
| `ae2.api.networking.GridServices`           | 附加模组可以在此注册自己的网络级服务。                                                               |
| `ae2.api.movable.BlockEntityMoveStrategies` | 允许模组注册自定义策略，用于在空间存储的移入移出过程中移动方块实体。                                 |
| `ae2.api.features.GridLinkables`            | 用于处理以及添加可绑定到特定网络的物品，例如在ME无线访问点处绑定无线终端。                           |
| `ae2.api.features.ChargeableItems`          | 用于注册由 AE2 充能器处理的外部物品能量适配器。                                                      |
| `ae2.api.storage.StorageCells`              | 用于处理以及添加可作为网络存储元件的物品。                                                           |
| `ae2.api.features.Locatables`               | 用于依据唯一键发现量子桥及其他可定位对象。                                                       |
| `ae2.api.parts.PartModels`                  | 用于注册自定义线缆总线部件所需的 JSON 方块模型。                                                     |
| `ae2.api.features.P2PTunnelAttunement`      | 用于注册新的物品，使 P2P 隧道在右键时调谐到特定类型。                                                |
| `ae2.api.client.StorageCellModels`          | 用于自定义存储元件插入ME驱动器或ME箱子时的模型。                                                   |
| `ae2.api.upgrades.Upgrades`                 | 用于管理升级卡，并将其与可升级的物品、部件或方块关联。                                               |
| `ae2.api.upgrades.UpgradeInventories`       | 用于为可升级机器与物品宿主创建升级物品栏。                                                           |
| `ae2.api.networking.extensions.GridLogicExtensions` | 无需 Mixin 即可为受支持的 AE2 网络逻辑附加运行时行为。                                       |
| `ae2.api.behaviors.GenericInternalInventoryAdapters` | 允许附加模组通过 Forge Capability 暴露 AE2 通用物品栏。                                      |
| `ae2.api.crafting.cpu.ICraftingUnitRegistry` | 用于添加复用 AE2 集群逻辑的自定义合成 CPU 单元方块。                                                 |
| `ae2.api.client.AEKeyRendering`             | 用于注册自定义键类型的客户端 GUI 渲染。                                                              |
| `ae2.api.cellterminal.CellTerminalApi`      | 用于注册存储管理终端的扫描器与实时目标解析器。                                                       |

一般来说，这些注册表都是同步（线程安全）的，可以在模组加载期间使用。请在受影响系统开始参与游戏之前完成注册。模组初始化之后的更改，可能使已经创建的网络、存储元件、模型、升级物品栏或充能器物品栏保留过时的假设。

## 物品与流体键

在 AE2 中，物品与流体类型由键（key）表示。`AEKey` 是所有键的基类，无论是表示物品（`AEItemKey`）还是流体（`AEFluidKey`）。AE2 的大多数接口都是通用的：它们接受任意 `AEKey`，无论它表示流体、物品还是附加模组提供的键类型。

键本身不包含数量，因为键只表示一种资源类型。数量由存储、合成、传输和显示 API 单独携带。对本分支中的物品而言，`AEItemKey` 由 `Item`、1.12.2 的元数据/损伤值、从原始堆栈捕获的最大堆叠数以及可选的 NBT 组成；对流体而言，`AEFluidKey` 由 `Fluid` 和可选的 `FluidStack` 标签数据组成。

要表示某个键的一组资源，AE2 提供 `GenericStack`，它由一个键和一个数量组成。它可以从 `ItemStack` 或 `FluidStack` 转换而来，可序列化为 NBT、写入数据包，也可以包装成 `ItemStack` 用于显示或过滤。

每类键都由一个 `AEKeyType` 实例表示，可通过 `AEKey.getType()` 访问。它保存该类型所有键共有的属性，例如数量格式化、每字节资源量、每次操作资源量、数据包读取器和 NBT 读取器。新的键家族通过 `AEKeyTypes.register` 注册。

不能作为合成 CPU 产物交付的键类型，应重写 `AEKeyType.isCraftingCpuInsertable()` 并返回 `false`。此时 AE2 既不会优先把合成 CPU 的存储挂载点用于该类型，也不会向合成 CPU 转发插入操作。这适用于诸如外部产生的能量之类的资源家族——它们可以存入网络，但不是有效的合成产物。该设置不会禁用该键类型的样板注册或合成计划计算。

键可以使用 `toTagGeneric` 保存到 NBT，它同时会保存自身类型的引用，因此 `AEKey.fromTagGeneric` 可以在调用方不知道具体键类的情况下恢复键。同样的机制也适用于数据包：`AEKey.writeKey`、`AEKey.writeOptionalKey`、`AEKey.readKey` 与 `AEKey.readOptionalKey`。

在模糊匹配与索引这类主要关注主资源类型而非 NBT 或其他次要数据的场景中，使用 `dropSecondary()` 和 `getPrimaryKey()`。对本分支中的物品，损伤值还会通过 `getFuzzySearchValue()` 暴露，使基于 1.12.2 耐久的模糊过滤可以生效。

当你的代码只支持物品键时的示例：

```java
if (key instanceof AEItemKey itemKey) {
    ItemStack stack = itemKey.toStack();
    // [...]
}
```

当你的代码只支持流体键时的示例：

```java
if (key instanceof AEFluidKey fluidKey) {
    FluidStack stack = fluidKey.toStack(1000);
    // [...]
}
```

## 自定义键类型的机器集成

注册 `AEKeyType` 只是让 AE2 能够在内部序列化、存储、显示和路由这一键家族。它不会自动让 Forge 的物品或流体管道，或其他模组的自定义 Capability 学会传输这种资源。附加模组的资源类型通常需要在模组初始化期间完成以下注册：

* `AEKeyTypes.register(...)` 注册键家族。
* `GenericSlotCapacities.register(...)` 设置接口与样板供应器的槽位上限。
* `ExternalStorageStrategy.register(...)` 让 AE2 的存储总线和样板供应器能把外部物品栏包装为 `MEStorage`。
* `GenericInternalInventoryAdapters.register(...)` 让 AE2 自己的通用物品栏（例如 ME接口与样板供应器的回收物品栏）能通过附加模组的 Forge Capability 暴露。
* 可选的行为策略，例如 `StackImportStrategy`、`StackExportStrategy`、`PlacementStrategy`、`PickupStrategy` 与 `ContainerItemStrategy`，用于让该键类型支持各类总线、成型面板或容器物品。

AE2 会自动将已注册的 `GenericInternalInventoryAdapters` 应用于暴露 `AECapabilities.GENERIC_INTERNAL_INV` 的 AE2 自有宿主，包括本分支中 AE2 的 ME接口、样板供应器以及其他通用物品栏宿主。它不会自动修改其他模组的方块实体或部件。如果你的模组拥有外部机器或 Capability 提供方，则由你的模组负责在其上暴露自己的 Forge Capability 或 AE2 存储包装器。

最小适配器形态：

```java
GenericInternalInventoryAdapters.register(MY_CAPABILITY, MyCapabilityHandler::new);

final class MyCapabilityHandler implements IMyCapability {
    private final GenericInternalInventory inv;

    MyCapabilityHandler(GenericInternalInventory inv) {
        this.inv = inv;
    }

    // 在你的资源堆栈与自定义 AEKey 之间进行转换，
    // 然后调用 inv.insert(...)、inv.extract(...)、inv.getKey(...) 与 inv.getAmount(...)。
}
```

## 自定义键渲染

注册 `AEKeyType` 只是教会了 AE2 如何存储、序列化和路由你的键。每个键家族还需要一个客户端渲染处理器，否则 AE2 的终端与元件查看器等 GUI 无法显示它。

在模组初始化期间于客户端为每种键类型注册一个处理器：

```java
AEKeyRendering.register(MY_KEY_TYPE, MyKey.class, new MyKeyRenderHandler());
```

`AEKeyRenderHandler<T>` 需要实现以下方法：

* `drawInGui(Minecraft, x, y, key)` 把键绘制到 16x16 的 GUI 区域中。实现必须平衡矩阵操作，并恢复超出 AE2 GUI 基线的 GL 状态更改：混合启用、深度与光照禁用、颜色为白色。调用方不会修复通过原生 OpenGL 调用改变的状态。
* `drawOnBlockFace(key, scale, combinedLight, world)` 把键绘制到方块面上，供成型面板预览等场景使用。
* `getDisplayName(key)` 返回终端与悬浮提示使用的翻译后显示名称。
* `getTooltip(key)` 为可选方法，默认返回显示名称加上所属模组 id。

同一键类型的重复注册会抛出异常。AE2 内置注册了自己的物品与流体处理器；附加模组只需为自己的键类型注册处理器。

## 自定义合成单元

合成 CPU 集群逻辑可以通过 `ae2.api.crafting.cpu` 中的自定义合成单元定义进行扩展。单元定义（`ICraftingUnitDefinition`）描述一种参与合成集群的 CPU 方块类型：

| 方法                        | 用途                                                                 |
|-----------------------------|----------------------------------------------------------------------|
| `id()`                      | 用于注册、模型查找与持久化的稳定标识。                               |
| `storageBytes()`            | 该类型一个方块为其集群贡献的存储字节数。                             |
| `acceleratorThreads()`      | 该类型一个方块为其集群贡献的并行处理单元数。                         |
| `getItemRepresentation()`   | 用作方块物品表示的物品。                                             |
| `getVisualDefinition()`     | 客户端渲染契约：未成形/已成形模型、环与光照贴图。                    |
| `getFamilyId()`             | 用于限定不同实现之间集群兼容性的家族标识。                           |

在模组初始化期间、模型烘焙之前注册定义与升级规则。定义可以在加载期间的任意时点注册；重复 id 会以异常拒绝：

```java
CraftingUnitRegistry.getInstance().register(MY_UNIT_DEFINITION);
CraftingUnitTransformationRegistry.getInstance()
    .register(MY_UNIT_BLOCK, MY_UPGRADED_BLOCK, MY_UPGRADE_ITEM);
```

转换注册表驱动把基础单元方块右键升级为升级形态的规则，以及拆除升级时返还的物品。`getFamilyId()` 不同的单元不会共同组成一个合成集群，因此附加模组的单元可以彼此自由混用，但除非刻意共享家族 id，否则与 AE2 自身的单元相互独立。

客户端的已成形模型通过 `ae2.api.client.crafting.ICraftingUnitClientRegistry` 在客户端初始化阶段提供：

```java
CraftingUnitClientRegistry.getInstance().registerModelProvider(MY_UNIT_ID, MY_MODEL_PROVIDER);
```

注册表单例目前位于 `ae2.core.registries`（`CraftingUnitRegistry`、`CraftingUnitTransformationRegistry`、`CraftingUnitClientRegistry`）；`ae2.api.crafting.cpu` 与 `ae2.api.client.crafting` 中的接口是稳定契约。

## 网络与节点

AE2 核心系统的工作方式是：把由方块实体或部件等游戏内对象创建并持有的网络节点组装成网络。网络永远不会被直接创建；它们随着网络节点的创建，以及通过世界相邻关系或显式虚拟连接进行的连接与断开而自动形成和解散。

**注意：** 网络纯粹是服务端概念，客户端上不存在网络。

### 节点所有者与监听器

每个节点都由一个游戏内对象拥有。所有者可以是方块实体、部件、物品宿主，或其他需要参与网络的对象。所有者不需要实现专用的 API 接口，这使得把既有游戏对象接入 AE2 成为可能，而不必把所有宿主类强行纳入同一个继承模型。

节点通过监听器（`IGridNodeListener<T>`）与其所有者交互。所有者与监听器一起传给 `GridHelper.createManagedNode(owner, listener)`。把监听器独立出来，既允许单个监听器实例被复用，又能对所有者保持类型安全访问。

监听器负责把节点事件适配回宿主行为。典型动作包括：节点数据变化时把方块实体标记为脏、可见连接变化时刷新客户端渲染，以及能量、频道或网络引导状态变化时更新方块状态。

**示例：**

```java
class MyTileListener implements IGridNodeListener<MyTileEntity> {
    static final MyTileListener INSTANCE = new MyTileListener();

    @Override
    public void onSaveChanges(MyTileEntity nodeOwner, IGridNode node) {
        nodeOwner.markDirty();
    }

    @Override
    public void onStateChanged(MyTileEntity nodeOwner, IGridNode node, State state) {
        // 例如：更新方块状态、刷新渲染，或将方块实体同步到客户端。
    }
}
```

```java
class MyTileEntity extends TileEntity {
    private final IManagedGridNode mainNode =
        GridHelper.createManagedNode(this, MyTileListener.INSTANCE);
}
```

### 托管网络节点

`IManagedGridNode` 简化了网络节点的创建与销毁生命周期，并集中管理节点配置。托管网络节点可以在两个逻辑侧构造：客户端侧永远不会暴露服务端网络节点；服务端侧在 `create(World, BlockPos)` 成功后变为就绪。

你的游戏对象应在以下事件时通知托管节点：

* 当游戏对象从 NBT 数据加载时调用 `loadFromNBT`。这必须发生在 `create(World, BlockPos)` 之前。
* 当所有者已进入世界且可以建立对外连接时调用 `create(World, BlockPos)`。对方块实体，使用 `GridHelper.onFirstTick` 把创建推迟到方块实体位于正在 tick 的区块中。
* 当游戏对象保存到 NBT 数据时调用 `saveToNBT`。
* 当游戏对象失效、被移除或卸载时调用 `destroy`。

托管节点还负责配置网络行为：用 `setFlags` 设置网络标志，用 `setIdlePowerUsage` 设置被动能耗，用 `setGridColor` 限制相邻同色连接，用 `setVisualRepresentation` 设置 UI 展示，用 `setOwningPlayer` 或 `setOwningPlayerId` 设置安全所有权。

### 世界内节点

最主要的网络节点类型是世界内网络节点。它在通过 `IManagedGridNode.create(World, BlockPos)` 创建时需要 `World` 与 `BlockPos`。AE2 会自动尝试与相邻的世界内网络节点建立外部连接。

世界内节点可以有选择地暴露在特定面或所有面上。暴露面可以在节点创建后通过 `setExposedOnSides(...)` 更改，更改会触发重新寻路。对于需要挂到网络上、但对常规世界邻接隐藏的节点，使用 `setInWorldNode(false)`。

要把实际的 `IGridNode` 暴露给其他系统，可通过合适的 Capability 或宿主接口（例如 `IInWorldGridNodeHost`）返回 `IManagedGridNode.getNode()`。

### 虚拟节点

虚拟节点不会自动与附近的世界节点建立连接。它允许附加模组构建不由常规方块邻接表示的 ME 网络拓扑。

虚拟连接必须通过 `GridHelper.createConnection(IGridNode, IGridNode)` 显式创建。单条连接可以通过销毁对应节点移除；附加模组一次移除多条连接时，应使用 `GridHelper.destroyConnections(Collection<? extends IGridConnection>)`。批量入口会在修改前校验参数，对重复连接按身份去重，在回调前解绑两端，并对最终连通分量各处理一次。该操作同步执行，必须在服务端线程调用。

### 节点服务

节点所有者可以通过 `IManagedGridNode.addService(...)` 向节点添加服务。服务以继承 `IGridNodeService` 的接口表示，让节点可以选择加入额外的网络托管行为。

节点服务通常由网络服务消费。例如：刻调度服务寻找 `IGridTickable`，存储服务寻找 `IStorageProvider`，合成服务寻找合成提供方节点服务。这种模型把可选行为附着在需要它的节点上。

### 网络服务

每个网络为连接到它的机器提供若干服务。

AE2 通过 `GridServices` 提供默认服务，附加模组也可以在那里注册自己的网络级服务。服务可通过 `IGrid#getService` 传入服务接口获取。对 AE2 的默认服务，`IGrid` 还提供 `getStorageService()`、`getCraftingService()` 等便捷方法。

#### 能量

**服务接口：** `IEnergyService`  
**便捷获取：** `IGrid.getEnergyService()`

该服务允许从网络的能量存储中取出能量或向其中注入能量，包括能量元件、网络内部存储以及其他接入网络的能源提供方。

能源提供方实现 `IAEPowerStorage`。`injectAEPower(...)` 必须返回有限、非负且不大于请求量的剩余量；模拟操作不得修改存储或发出事件。若实现可以直接计算容量或传输限制，应覆盖 `getExtractableAEPower(double)` 与 `getReceivableAEPower(double)`；默认实现使用模拟操作。这两个查询必须无副作用，并返回不超过参数的有限非负值。能源服务操作之外的数值变化必须发送 `GridPowerStorageChanged(ChangeType.VALUES_CHANGED)`；路由或容量变化使用 `ChangeType.ROUTING_CHANGED`。这些回调必须在所属网络的服务端线程同步执行。

能源服务按已注册提供方缓存原始数值贡献，数值变化事件只刷新事件指出的来源。某个提供方返回非法值时，仅该来源在本次刷新中被隔离并按零贡献处理，不得使其他能源提供方失效；后续有效变化事件可以使其恢复。已删除的 `PowerStorageSnapshotBuilder` API 不应由附加模组实现。

#### 刻调度

**服务接口：** `ITickManager`  
**便捷获取：** `IGrid.getTickManager()`

AE2 为接入网络的机器提供了先进的刻调度系统，具备以下特性：

* 无需是可 tick 的方块实体即可被调度
* 可变 tick 频率
* 设备没有工作时休眠
* 在某些事件（如邻块变化或有新工作可用）时唤醒休眠设备

网络的 `ITickManager` 服务处理该调度系统的网络侧部分，提供管理网络节点休眠与唤醒状态的 API。

`ae2.api.networking.ticking.TickSnapshot` 暴露刻管理使用的可变计时汇总。节点移除或最大耗时降低后，`cpuMax()` 会延迟重算，因此读取它可能遍历当前节点计时条目；`reset()` 会清除所有节点及分类计时数据。

要参与刻调度系统，你的网络节点必须提供 `IGridTickable` 节点服务。`ITickManager` 在你的节点加入网络时对该服务的存在做出反应。`IGridTickable` 返回 `TickingRequest` 描述期望的响应速度，然后每次 tick 返回 `TickRateModulation` 来加速、减速、休眠或保持当前频率。

#### 存储

**服务接口：** `ae2.api.networking.storage.IStorageService`  
**便捷获取：** `IGrid.getStorageService()`

网络中的存储以挂载的 `MEStorageMonitor` 物品栏形式组织。存储服务通过 `getInventory()` 把统一的网络物品栏暴露为 `MEStorageMonitor`，通过 `getCachedInventory()` 提供其共享的聚合 `KeyCounter`，并管理来自节点与全局提供方的 `IStorageProvider` 挂载。

聚合缓存在首次访问时通过枚举全部挂载构建。之后的内容变化通过 `MEStorageChangeListener.onStackChange(...)` 的带符号增量同步更新。只有在挂载结构变化或某个监视器调用 `onListUpdate()` 之后，才会重新进行一次完整枚举。返回的 `KeyCounter` 是共享的，调用方不得修改。

`getInventory()` 的使用方可以注册自己的 `MEStorageChangeListener`，先完整枚举一次，然后应用收到的同步带符号增量。正增量表示某个键的数量增加，负增量表示减少。`onListUpdate()` 表示无法用精确增量描述的结构性变化，会请求一次新的完整枚举；对普通内容变化而言，它不能替代 `onStackChange(...)`。

存储监听节点还可以覆盖 `IStorageWatcherNode.onStackChange(AEKey, long, long)`，同时接收变化后的绝对网络数量与变化前的绝对网络数量。该重载默认调用原有回调，因此既有监听器仍保持源码兼容。两个数量均为非负值，并描述同一次同步更新。

对于稳定且较大的内部物品栏，可以额外实现 `ae2.api.inventories.VersionedInternalInventory`。`getContentsVersion()` 必须是单调递增、无副作用的版本值；可见槽内容、数量、NBT 或槽位可见性每次真实变化（包括通过其他 facade 的变化）都必须递增，模拟操作不得递增。消费者可以在物品栏对象、大小和版本均未变化时跳过槽位枚举。该接口只规定生命周期顺序，不使物品栏访问变为线程安全。

由节点提供的存储应以节点服务的形式实现 `IStorageProvider`。节点加入或离开网络时，存储服务会自动调用 `mountInventories(...)` 挂载或卸载它。当存储由某个网络服务而非单个节点提供时，可以通过 `IStorageService.addGlobalStorageProvider(...)` 添加全局存储提供方。

`IStorageMounts.mount(...)` 只接受 `MEStorageMonitor`。每个挂载到网络上的物品栏，都必须在注册的服务端线程上，把每次可见内容变化作为带符号增量恰好同步上报一次。只实现 `MEStorage` 的网络挂载不受支持。如果某个挂载无法把结构性变化表示为精确增量，必须调用 `onListUpdate()`，让网络重新进行一次权威枚举。

当存储提供方因外部事件或配置更改而需要移除、添加或重建挂载时，节点提供方调用 `IStorageProvider.requestUpdate(managedNode)`，全局提供方调用 `refreshGlobalStorageProvider(...)`。

`StorageCell` 继承自 `MEStorageMonitor`。因此由 `ICellHandler` 返回的自定义元件与任何其他网络挂载一样，承担同步上报带符号增量的义务。一次 `MODULATE` 插入或抽取必须把实际可见的增加或减少量恰好上报一次；`SIMULATE` 操作不得上报变化。移除监听器后必须停止后续所有回调，监视器在分发回调前必须校验注册令牌。

外部物品或流体处理器可以额外实现 `ExternalStorageMonitor`。存储总线会在挂载时枚举该处理器一次，之后改为消费精确的带符号增量而不是轮询它。处理器在监听器注册时会收到处于激活状态的 `StorageFilter`，之后必须在注册的服务端线程上同步上报每次内容变化。无法用增量表达的结构性变化使用 `onListUpdate()` 并触发一次新的枚举。这只是针对相邻 Forge 物品或流体处理器的可选优化；未实现该接口的处理器继续使用存储总线的自适应周期扫描。

展示整个网络物品栏的网络终端宿主可以重写 `ITerminalHost.getGridStorageService()`。终端容器使用返回的服务标识订阅共享的网络监视器，而不是每个刻独立枚举同一网络。便携元件、ME箱子等本地终端必须保持默认的 `null`；可重连的宿主在断开时应返回 `null`，重连后返回当前服务。

#### 自动合成

**服务接口：** `ICraftingService`  
**便捷获取：** `IGrid.getCraftingService()`

该服务提供对可合成样板、合成 CPU、任务计算、任务模拟、任务提交与活动请求跟踪的访问。可合成键通过该服务查询，而不是作为普通的网络存储内容上报。

`getCraftablesVersion()` 返回一个单调递增的版本号，每当可合成资源集合可能发生变化时它都会改变。使用方可以记录上次观察到的版本号，在版本号不变期间避免再次调用 `getCraftables(...)`。该值只描述可合成提供方的结构，不能替代存储监视器的变化通知。

##### 在合成状态列表中分组 CPU

合成 CPU 可以声明自己属于某个分组，从而在合成状态 CPU 列表中与同组成员保持相邻。它面向那些由同一个物理多方块结构承载多台 CPU 的机器，使这些 CPU 在视觉上聚在一起，而不是散落在列表各处。

在 CPU 上覆盖 `getCpuListGroup()` 并返回 `CraftingCpuGroup`：

```java
@Override
public CraftingCpuGroup getCpuListGroup() {
    return new CraftingCpuGroup(this.groupId, this.memberOrdinal);
}
```

`groupId` 必须按分组实例而非机器类型保持唯一，这样同种机器的两个独立多方块结构才会形成两个独立分组。`memberOrdinal` 决定组内成员的顺序；序号相同的成员保持当前排序模式产生的相对顺序。

分组在服务端与客户端都是在排序之后应用的，因此与玩家选择的排序模式相互正交：整个分组占据其中排序最靠前的那个成员的位置，除了让各分组连续之外，分组不会改变列表的其他顺序。返回默认值 `null` 表示该 CPU 不参与分组。

也可以通过覆盖 `getUnfocusedCpuListBackgroundIcon()` 与 `getFocusedCpuListBackgroundIcon()`，返回经 `Icon.register(...)` 注册的图标，从视觉上区分分组成员。行背景图标必须已注册且尺寸恰好为 67x22 像素。

#### 合并样板推送

合成提供方可以通过 `ICraftingProvider.canMergePatternPush(IPatternDetails)` 将某个样板加入合并推送路径。该方法只决定 CPU 是否可以使用特殊的批处理路径；若返回 `false`，CPU 必须使用常规的单样板分发尝试，且不得调用 `getMaxPatternPushMultiplier(...)`。

当 `canMergePatternPush(...)` 返回 `true` 时，CPU 会调用 `ICraftingProvider.getMaxPatternPushMultiplier(IPatternDetails, int maxMultiplier)`。实现必须返回 `0` 到 `maxMultiplier` 之间的值。返回 `0` 表示该提供方在本轮对该样板当前不可用：CPU 不会提取输入、不会消耗能量，也不会在该轮为该提供方回退到单样板推送。

合并推送 `N` 份，在输入、预期产物和合成 CPU 操作能耗上等价于 `N` 次连续成功的单样板推送。无法保证这一等价性的附加模组提供方应从 `canMergePatternPush(...)` 返回 `false`。

向相邻机器分发的样板供应器，可以在相邻合成机器上实现 `ae2.api.implementations.blockentities.IPatternProviderBatchTarget`，以询问针对特定方向的最大接收数量。没有该接口时，AE2 回退到常规的单样板合成机器路径。对于外部物品栏，AE2 会用模拟插入探测选定的目标组，并且不会把一次合并分发拆散到不相关的回退方向上。

#### 样板容器与组装样板

样板管理终端的提供方通过公开的 `ae2.helpers.patternprovider.PatternContainer` 类型暴露其样板物品栏。`PatternContainer.isAssemblerPatternContainer()` 是类型边界，而不只是 UI 提示。返回 `true` 的容器只接受并只展示组装样板；返回 `false` 的容器只接受并只展示非组装样板。AE2 在为样板管理终端收集提供方物品栏、通过终端插入样板、快速移动样板、检查重复样板以及向合成服务发布样板时，都会使用这一边界。

组装样板实现 `ae2.api.crafting.IAssemblerPattern`。它们是由分子装配室等组装类机器内部执行的样板，而不是被推入外部物品栏的样板。组装样板可以通过 `canSubstitute()` 与 `canSubstituteFluids()` 描述物品替换与直接使用流体。这些属性只属于组装样板。非组装样板是固定输入样板，不应暴露替换或直接流体行为。

这一区分对附加模组提供方很重要：普通样板供应器可以假设非组装样板的输入是固定的键和固定的键类型。如果附加模组的样板需要替换输入，应使用组装样板实现，并通过组装样板容器暴露。

### 缺失合成原料时的强制启动

合成计算现在可以产出即使缺少部分原料也具备提交条件的计划。这主要被玩家的合成确认流程使用，同时也可通过显式选择加入向自动化开放。

`ICraftingPlan.missingItems()` 始终报告计划缺失原料的总量。带有缺失物品的计划仍可具备提交条件；此时 `ICraftingPlan.simulation()` 保持 `false`，任务开始后缺失物品会被转换为 CPU 的 `waitingFor` 请求。

希望按完整的请求量进行规划（包括缺失分支）时，使用 `CalculationStrategy.REPORT_MISSING_ITEMS`；希望 AE2 把请求量缩减为当前可立即合成的量时，使用 `CalculationStrategy.CRAFT_LESS`。

`ICraftingService` 为显式强制提交提供了重载：

```java
ICraftingSubmitResult submitJob(
    ICraftingPlan job,
    @Nullable ICraftingRequester requestingMachine,
    @Nullable ICraftingCPU target,
    boolean prioritizePower,
    IActionSource src,
    boolean forceStart);
```

`forceStart` 为 `true` 时，允许提交带有缺失原料但具备提交条件的计划。合成 CPU 会提取当前所有可用的输入，把剩余缺失量放入等待列表，并在这些物品稍后通过正常合成或外部插入到达时继续任务。

旧的 `submitJob(...)` 签名保持保守，行为等同 `forceStart = false`。

自动化可以通过实现 `ae2.api.networking.crafting.ICraftingForceStartRequester`（`ICraftingRequester` 的直接扩展）选择加入：

```java
public interface ICraftingForceStartRequester extends ICraftingRequester {
    boolean canForceStartCrafting(ICraftingPlan plan);
}
```

`MultiCraftingTracker` 在计算完成后检查该接口。如果请求方返回 `true`，它会向 `ICraftingService.submitJob(...)` 传递 `forceStart = true`。未实现该接口或返回 `false` 的请求方保持保守行为。

当前实现该契约的内置自动化请求方：

* `ae2.helpers.InterfaceLogic`
* `ae2.parts.automation.ExportBusPart`

两者都使用已安装的 AE2 合成卡升级来决定是否允许强制启动。只有当请求方装有启用了强制启动模式的合成卡时才会选择加入。这让常规自动化保持保守，同时仍允许显式的机器驱动强制合成。

#### 寻路

**服务接口：** `IPathingService`  
**便捷获取：** `IGrid.getPathingService()`

该服务提供网络的频道与控制器/寻路状态。节点可以用它检查网络是否正在引导，以及频道需求当前是否满足。

当前频道分配模式通过 `IPathingService.channelMode()` 获取。

#### 空间 I/O

**服务接口：** `ISpatialService`  
**便捷获取：** `IGrid.getSpatialService()`

该服务提供当前定义的空间区域的信息，包括边界、有效性、世界和所需能量。

## 新增升级卡或让机器可升级

相关 API：

* `ae2.api.upgrades.Upgrades` 用于管理升级卡，并将其与机器关联
* `ae2.api.upgrades.UpgradeInventories` 用于创建升级物品栏，供可升级机器或物品使用
* `ae2.api.upgrades.IUpgradeInventory` 用于查询与遍历已安装的升级

### 自定义升级卡

每个升级由一个已注册物品唯一标识，称为升级卡。要创建行为类似 AE2 自带升级卡的自定义升级卡，使用 `Upgrades.createUpgradeCardItem()` 为你的卡创建物品。随后正常注册该物品，并提供它的模型与翻译键。

对于以这种方式创建的升级卡物品，AE2 会处理"受支持机器"的悬浮提示与右键插入行为。需要识别升级卡时，使用 `Upgrades.isUpgradeCardItem(...)`。

### 将升级卡与机器关联

无论你的附加模组添加的是自定义机器还是自定义升级卡，都需要把可能的升级与潜在的机器关联起来。`Upgrades.add(upgradeCard, upgradableObject, maxSupported)` 将升级卡物品与可升级的物品、部件物品或方块物品关联。

如果若干机器在悬浮提示中应被同等对待，通过 `Upgrades.add(upgradeCard, upgradableObject, maxSupported, tooltipGroup)` 传入翻译键作为 `tooltipGroup` 参数。显示升级卡的悬浮提示时，所有具有相同 `tooltipGroup` 的受支持机器会合并为一行翻译文本。AE2 对相关联的方块/部件形态以及相关联的物品/流体变体就使用了这一机制。

### 物理升级槽位

`Upgrades.add(...)` 接受的某张卡的最大数量，与机器上物理槽位的数量相互独立。机器可以故意暴露比所有受支持卡片上限之和更少的槽位，迫使玩家在兼容升级之间做取舍。

附加模组可以通过 `Upgrades.addUpgradeSlots(...)` 为现有机器贡献物理槽位：

```java
ResourceLocation id = new ResourceLocation("examplemod", "high_voltage_slot");

Upgrades.add(HIGH_VOLTAGE_CARD, MY_MACHINE_ITEM, 1);
Upgrades.addUpgradeSlots(MY_MACHINE_ITEM, id, 1);
```

贡献 id 对每个机器物品唯一。复用 id、提供非正的槽位数，或在机器的升级槽数量首次被解析之后再注册，都会抛出异常。不同附加模组的贡献 id 之间是累加的。这让"添加一张卡并附带一个槽位"的功能变得显式，同时保留机器作者的基础容量与升级取舍。

`UpgradeInventories.forMachine(machine, baseSlots, callback)` 会自动包含已注册的贡献。自定义机器物品栏应在构建物品栏时恰好调用一次 `UpgradeInventories.getMachineUpgradeSlots(machine, baseSlots)`。这两个方法都会冻结该机器后续的槽位贡献，因此请在模组初始化期间、任何机器实例被创建之前完成注册。

### 让自定义机器或物品可升级

使用 `UpgradeInventories` 创建存放升级卡的物品栏。这些物品栏使用提供的机器或物品标识决定接受哪些升级卡，并阻止不兼容的卡被插入。

它们还通过 `IUpgradeInventory` 提供便捷方法，用于快速检查某升级是否存在、统计某类升级的安装数量，以及遍历已安装的卡。

对于 `UpgradeInventories.forMachine` 创建的机器版本，请在变更回调中保存物品栏。对于 `UpgradeInventories.forItem` 创建的物品版本，升级物品栏会把更改直接写入提供的 `ItemStack` NBT。物品版本还接受可选的变更回调。

## 扩展内置网络逻辑

`GridLogicExtensions` 让附加模组在不使用 Mixin、Access Transformer 或私有字段引用的情况下，为受支持的 AE2 网络逻辑附加独立的运行时行为。当前的内置集成点是 ME接口与样板供应器的方块和部件形态。

在模组初始化期间为每个机器物品注册一个工厂：

```java
ResourceLocation id = new ResourceLocation("examplemod", "network_monitor");
GridLogicExtensions.register(MY_MACHINE_ITEM, id, MyLogicExtension::new);
```

注册 id 对每个机器物品唯一。某台机器的注册会在它的第一个逻辑实例被创建时冻结；重复或迟到的注册会抛出异常，以避免同一世界里存在扩展集不同的机器。

工厂会收到一个 `GridLogicContext`，它暴露机器物品、拥有的方块实体或部件、宿主方块、托管网络节点、动作来源、升级物品栏，以及当前目标面的快照。宿主方块是包含它的 `TileEntity`；对于部件，`getOwner()` 返回的所有者是部件本身，而宿主方块通常是线缆总线的方块实体。AE2 构建放置预览时宿主方块可能为 `null`，因此扩展不得在工厂构造或 `initialize(...)` 中依赖它。

实现 `GridLogicExtension` 以获得生命周期回调：

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
        // 在 AE2 完成升级处理后重新配置行为。
    }

    @Override
    public void onNeighborChanged(EnumFacing side) {
        // 使与该相邻面关联的状态失效。
    }
}
```

工厂先创建全部扩展；AE2 随后在完整的扩展列表挂载到所属逻辑之后调用 `initialize(...)`，因此初始化可以安全地引发后续逻辑活动。`onUpgradesChanged()` 在 AE2 原生的升级处理之后派发。邻居回调在服务端为紧邻方块的更改派发；如果扩展只处理当前输出面，应使用 `context.getTargetSides()`，因为样板供应器选中的面可能在运行时改变。

## 机器设置

机器相关的可切换设置由 `ae2.api.config.Settings` 中的 `Setting<T>` 常量建模，每个可配置行为对应一个常量（`REDSTONE_CONTROLLED`、`SORT_DIRECTION`、`PATTERN_AUTO_FILL` 等）。`RedstoneMode`、`YesNo` 等枚举形式的选项列表同样位于 `ae2.api.config`。

宿主机器通过 `IConfigManager` 暴露设置，任何实现 `ae2.api.util.IConfigurableObject` 的对象都可以获取它：

```java
public class MyMachine implements IConfigurableObject {
    private final IConfigManager configManager = IConfigManager.builder(this::onSettingChanged)
        .registerSetting(Settings.REDSTONE_CONTROLLED, RedstoneMode.HIGH_SIGNAL)
        .build();

    @Override
    public IConfigManager getConfigManager() {
        return this.configManager;
    }

    private void onSettingChanged(IConfigManager manager, Setting<?> setting) {
        // 响应设置变化：标记机器为脏、刷新 GUI 等。
    }
}
```

`getSetting(...)` 与 `putSetting(...)` 读取和写入单个值，`getSettings()` 与 `hasSetting(...)` 枚举机器支持的设置，`writeToNBT(...)`/`readFromNBT(...)` 持久化当前值。向管理器注册未注册的设置会抛出 `UnsupportedSettingException`。便携终端与无线终端等物品宿主使用 `IConfigManager.builder(ItemStack)` 构建管理器，设置会自动持久化到物品的 NBT 中。

## 无线终端

无线终端通过 `AddWirelessTerminalEvent` 注册。请在模组加载期间、AE2 完成无线终端注册之前注册你的处理器。AE2 运行完该事件后注册即被冻结；重复 id、缺少必填字段、无效的升级槽数量以及事件之后的注册都会以异常失败。

| 类                                                              | 用途                                                            |
|-------------------------------------------------------------------|-----------------------------------------------------------------|
| `ae2.api.implementations.items.AddWirelessTerminalEvent`       | 在 AE2 初始化期间注册终端定义。                                 |
| `ae2.api.implementations.items.WirelessTerminalDefinition`     | 供热键、通用终端与 GUI 使用的只读终端定义。                     |
| `ae2.api.implementations.items.WirelessTerminalDefinitionBuilder` | 注册无线终端定义的构建器。                                    |
| `ae2.api.implementations.items.WirelessTerminalApi`            | 查找辅助方法与通用终端辅助方法。                                 |
| `ae2.api.implementations.items.WirelessTerminalUpgradeHelper`  | 向所有已注册无线终端注册升级卡。                                 |

定义包含唯一 id、终端物品、图标工厂、GUI 打开器、宿主工厂、容器工厂、界面工厂、热键名称，以及该终端支持的升级槽位数。终端物品应继承 `WirelessTerminalItem`。容器工厂创建终端的服务端容器，界面工厂创建客户端界面；这使外部无线终端不必保留或依赖 AE2 的 `GuiKey`。省略 GUI 打开器的构建器重载会回退到 AE2 标准的基于物品的打开器。

注册示例：

```java
AddWirelessTerminalEvent.register(event -> event.builder(
        "example",
        MY_WIRELESS_TERMINAL,
        (stackItem, terminalItem, player, locator, returnToMainContainer) -> {
            // 返回你的 WirelessTerminalGuiHost 实现。
            return new MyWirelessTerminalGuiHost(stackItem, terminalItem, player, locator, returnToMainContainer);
        },
        (definition, inventory, host) -> new MyWirelessTerminalContainer(definition, inventory, host),
        (definition, container, inventory) -> new MyWirelessTerminalScreen(container, inventory),
        terminal -> new ItemStack(terminal))
    .hotkeyName("wireless_example_terminal")
    .upgradeSlots(2)
    .addTerminal());
```

不接受升级卡的终端使用 `noUpgrades()`。`upgradeCount(int)` 是 `upgradeSlots(int)` 的别名。

`WirelessTerminalApi.wirelessTerminals()`、`ofId(...)`、`ofItem(...)` 与 `ofStack(...)` 暴露已注册的定义。`makeUniversalTerminal(...)`、`mergeUniversalTerminal(...)` 与 `selectTerminal(...)` 是处理无线通用终端的辅助方法。缺失或未注册的终端定义会被通用终端选择界面与热键查找忽略。

要让一张升级卡对所有已注册的无线终端可用，调用 `WirelessTerminalUpgradeHelper.addUpgradeToAllTerminals(upgradeCard, maxSupported)`。`maxSupported` 传 `0` 表示使用各终端定义自身的升级槽位数。无线通用终端获得各已注册终端受支持数量的合计值。

### 样板管理终端快速移动目标

样板管理终端可以把合成样板快速移动到兼容的样板供应器。

对合成样板而言，兼容性通过 `ae2.api.implementations.items.ICraftingPatternQuickMoveHost` 声明。

在以下两者之一上实现该标记接口：

* 物品——当样板供应器组的图标是普通物品时
* 方块——当样板供应器组的图标由 `ItemBlock` 表示时

AE2 在评估某个样板供应器组能否进行合成样板快速移动时，先检查该组图标物品是否实现 `ICraftingPatternQuickMoveHost`；若未实现且图标物品是 `ItemBlock`，再检查其背后的方块是否实现同一接口。

这让附加模组可以把自定义机器选择加入样板管理终端的合成样板快速移动行为，而无需硬编码具体的 AE2 方块 id。

## 客户端集成 API

以下扩展点把附加模组的功能集成进 AE2 终端 GUI。它们仅限客户端：请从客户端初始化阶段注册，并且绝不要在专用服务器上调用。

### 样板导入优先级

从 HEI/JEI 向样板编码终端导入配方时，每个原料槽可能包含多个候选变体。`ae2.api.client.PatternImportPriorities.register(priority)` 注册的 `PatternImportPriority` 实现决定把哪个候选写入终端。

实现需要提供稳定 id、显示名称、可选的悬浮提示行，以及 `matches(context, candidate)` 判定。优先级按玩家配置的顺序求值，第一个命中的实现选中该候选。`PatternImportPriorityContext` 暴露活动终端容器、客户端 repo 视图、HEI 书签快照，以及 `isBookmarked(...)`、`isCraftable(...)`、`isStored(...)` 等便捷判定。AE2 内置了 HEI 书签、可合成与已存储三个优先级；玩家可以在样板导入优先级设置界面对所有优先级重新排序。

### 终端设置页面

`ae2.api.client.terminalsettings.TerminalSettingsPages.register(provider)` 在不触碰宿主工具栏的情况下，为终端设置 GUI 添加页面。提供方提供稳定 id、工具栏图标、本地化标题和 `isVisible(context)` 判定，并为每个 GUI 实例创建一个 `TerminalSettingsPage`。页面接收生命周期回调（`init`、`update`、`drawBackground`、`drawForeground`、`keyTyped`、`onClosed`），并通过上下文构建控件；上下文暴露复选框、文本框、按钮与标签工厂。布局、工具栏按钮与页面选择由宿主负责。

### 样板供应器工具栏事件

`ae2.api.client.PatternProviderGuiInitEvent` 在样板供应器 GUI 创建完内置工具栏按钮后发布到 Forge 事件总线。附加模组通过 `event.addToLeftToolbar(button)` 追加自己的控件，并可通过 `event.getHost()` 读取 `PatternProviderLogicHost`。

### 合成任务状态事件

当客户端收到本地玩家发起的合成任务已开始、已取消或已完成的通知时，`ae2.api.client.CraftingJobStateEvent` 会发布到 Forge 事件总线。它提供任务 id、合成目标键、请求数量与剩余数量，以及一个 `CraftingJobState`。

```java
@SubscribeEvent
public void onCraftingJobState(CraftingJobStateEvent event) {
    if (event.getState() == CraftingJobState.FINISHED) {
        // 对完成的任务作出响应。
    }
}
```

该事件在 AE2 执行自身处理（例如完成提示 toast）之前发布，因此无论玩家是否携带无线终端，监听方都能收到通知。由于服务端只会把这些更新发送给发起请求的玩家，事件仅上报本地玩家所拥有的任务，并且永远不会在专用服务器上触发。监听方抛出的异常会被记录并吞掉，以免有缺陷的附加模组破坏任务跟踪。

## 存储管理终端集成

存储管理终端通过可插拔的扫描器发现存储目标、存储总线和子网。附加模组在通用初始化阶段通过 `ae2.api.cellterminal.CellTerminalApi` 注册自己的扫描器与目标解析器：

```java
CellTerminalApi.registerStorageScanner(MY_STORAGE_SCANNER);
CellTerminalApi.registerStorageTargetResolver(MY_STORAGE_TARGET_RESOLVER);
```

扫描器实现 `CellTerminalScanner.Storage`、`.Bus` 或 `.Subnet` 变体之一，并从 `scan(grid)` 返回对应的 `CellTerminalTarget` 子类型。目标携带 `CellTerminalTargetLocator`，以便服务端动作稍后重新解析当前存活的世界对象。任何暴露可写目标的扫描器都必须注册对应种类的解析器，否则服务端 GUI 动作会快速失败。

`CellTerminalCapability` 枚举了目标的可选能力（内容预览、元件槽写入、分区与文本分区写入、自动分区等），调用方据此决定提供哪些页面与动作，而无需依赖 AE2 实现类。终端宿主实现 `CellTerminalContainerHost`，为存储管理终端容器提供网络节点、临时元件存储、子网元数据账本与连接状态。

## 可选模组集成

### 世界内部件悬浮提示

`ae2.api.integrations.igtooltip.PartTooltips` 为 AE2 为其部件渲染的世界内悬浮提示添加正文行与服务端数据，由内置的提示集成共享：

```java
PartTooltips.addBody(MyPart.class, (part, context, tooltip) -> tooltip.addLine("..."));
PartTooltips.addServerData(MyPart.class, (player, part, serverData) -> serverData.setString("state", "..."));
```

两个方法都接受可选的优先级参数，用于控制顺序。该 API 仍处于实验阶段，可能发生变化。

### HEI 材料转换器

安装 HEI 后，`ae2.api.integrations.hei.IngredientConverters.register(converter)` 教会 AE2 的幽灵槽拖放如何把第三方 HEI 材料类型与 AE2 `GenericStack` 相互转换。转换器声明其 `IIngredientType`，每种材料类型只接受一个转换器：AE2 内置的物品与流体转换器先注册，之后针对已被覆盖类型的注册会被忽略。

## 从旧版 AE2 API 迁移

本分支使用基于键的存储与合成 API。围绕 `IAEStack`、`IAEItemStack`、`IAEFluidStack` 与分频道物品栏构建的旧版附加模组需要迁移到当前模型。

`IAEStack`、`IAEItemStack` 与 `IAEFluidStack` 已被"是什么"与"有多少"相分离的 API 取代。`AEKey` 标识被传输或存储的是什么，而单独的方法参数或 `GenericStack` 表示数量。

对应关系大致如下：

| 旧类或概念                  | 当前 API                                              |
|----------------------------|----------------------------------------------|
| `IAEStack`                 | `GenericStack`、`AEKey`                      |
| `IAEItemStack`             | `GenericStack`、`AEItemKey`                  |
| `IAEFluidStack`            | `GenericStack`、`AEFluidKey`                 |
| `IStorageChannel`          | `AEKeyType`                                  |
| `StorageChannels`          | `AEKeyTypes`                                 |
| `StorageChannels.items()`  | `AEKeyType.items()`                          |
| `StorageChannels.fluids()` | `AEKeyType.fluids()`                         |
| `IMEInventory`             | `MEStorage`                                  |
| `IMEMonitorable`           | `IStorageService.getInventory()` 或存储监视器 |
| `ICraftingMedium`          | `ICraftingMachine`                           |
| `ICellProvider`            | `IStorageProvider`                           |
| `getUnitsPerByte`          | `getAmountPerByte`                           |
| `transferFactor`           | `getAmountPerOperation`                      |

网络物品栏不再按频道划分。物品、流体与附加模组提供的键类型同时存在于其中。想要所有键类型时，使用 `AEKeyFilter.none()` 作为无操作过滤器；物品使用 `AEItemKey.filter()`，流体使用 `AEFluidKey.filter()`。

堆栈监视现在发送的是存储数量发生变化的键。请在附加模组代码中把键与数量作为两个独立的值处理。

可合成物品由 `grid.getCraftingService().getCraftables()` 及相关合成 API 提供。它们由合成服务建模，而不是作为普通网络存储。

合成服务还跟踪网络提供的精确编码样板定义。使用 `ICraftingService.isKnownPattern(AEItemKey patternDefinition)` 检查某个编码样板物品是否已被网络知晓。

向网络存储挂载存储的方式发生了变化。由于存储跨键类型统一，存储服务会对网络节点提供的 `IStorageProvider` 服务调用 `mountInventories`，并允许每个提供方向网络挂载 `MEStorageMonitor` 物品栏。`IStorageMounts.mount(...)` 不再接受普通 `MEStorage`。当节点因外部事件或配置更改需要移除或添加存储时，可以调用 `IStorageProvider.requestUpdate(managedNode)` 再次请求挂载过程。这取代了直接发送元件数组刷新事件的做法。

对于由物品打开的 GUI，本分支使用 `IGuiItem` 与 `ItemGuiHost`。

## 内部 API

以下面向内部的变更，对依赖 AE2 实现细节的附加模组可能仍然有用。

打开 AE GUI 的物品由 `IGuiItem` 表示。`ItemGuiHost` 可以作为终端及其他由物品打开的界面的便捷宿主。

优先级与合成确认流程使用 `ISubGuiHost`，使宿主能在嵌套界面关闭后返回上一个界面。

自定义存储元件基于统一的键存储模型。附加模组元件必须通过相应的元件 API 暴露 `StorageCell`（它继承 `MEStorageMonitor`），并且必须同步上报带符号的内容增量。需要时应使用键过滤器限制接受的键类型。物品与流体的存储计算仍可能不同，因此请优先使用 `ae2.api.storage.cells` 中的公开元件 API，而不是依赖 AE2 实现类。

`ae2.api.networking.events.statistics` 中的网络统计事件仅用于统计上报，明确可能变化，尚不是稳定的 API 面。

`ae2.api.networking.crafting.ICraftingCpuProvider` 允许一个网络节点的所有者一次性提供多台合成 CPU，这是 `ICraftingCPUTileEntity` 无法表达的——后者把「一个方块实体」建模为「恰好一台 CPU」。它面向那些以共享存储池同时运行多个任务的机器。

```java
public class MyMachineTile extends AENetworkedTile implements ICraftingCpuProvider {
    @Override
    public Collection<? extends CraftingCPUCluster> getProvidedCraftingCpus() {
        return this.myCluster.getMyCpus(); // 你自己的 CPU，每个都继承 CraftingCPUCluster
    }
}
```

每当返回的集合将要发生变化时，都必须在网络上发布 `GridCraftingCpuChange`；合成服务只有在该事件把 CPU 列表标记为脏之后才会重新读取提供方，并不会每 tick 轮询。`getProvidedCraftingCpus()` 必须是纯查询，因为它会在服务重建列表的过程中被调用。对于每个方块都拥有独立节点的多方块结构，这些节点可以全部返回同一组 CPU：服务会按对象标识去重。

这是一个面向内部的扩展点。它的契约类型是实现类 `CraftingCPUCluster` 而非 `ICraftingCPU` 接口，因为合成服务依赖了 `ICraftingCPU` 未暴露的行为，所以提供的 CPU 必须继承 `CraftingCPUCluster`。如果将来该内部类型被泛化，本签名预期会随之变化。

## 曲柄

玩家转动曲柄时，曲柄通过 `ICrankable` 向其连接的方块注入能量。

在 Forge 1.12.2 上，在你的方块实体上暴露 `AECapabilities.CRANKABLE`。你可以只为允许的一侧返回非空 `ICrankable`，从而限制曲柄可以安装在你方块的哪些面上。AE2 还提供 `ICrankable.get(World, BlockPos, EnumFacing)` 作为查找辅助方法。

示例：

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

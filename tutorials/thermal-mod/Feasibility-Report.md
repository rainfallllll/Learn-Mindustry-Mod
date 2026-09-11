# 《热力学系统设计手册》Mod 可行性评估报告

> **评估目标**：逐条核验设计方案中每个机制在 Mindustry v159.7 源码层面的可实现性
> **版本声明**：Mindustry v159.7 (HEAD b3317f3) · Arc 引擎
> **评估日期**：2026-09-11

---

## 1. 概述

### 1.1 方案总览

《热力学系统设计手册》提出了一套以**热量 Q 为唯一状态变量、温度 T=Q/C 为表观结果**的模块化热交换系统。核心设计包括：

- **双缓冲结算**：Phase 1 只写 `pendingDeltaQ`，Phase 2 统一结算，消除遍历顺序依赖
- **惰性温度**：`tempDirty` 缓存避免每 tick 重复除法
- **建筑间独立热阻 R**：`R_AB = R_A + R_B`（串联），与环境传热系数 airU 完全独立
- **双轨环境温度表**：T_ground（每 tile）+ T_air（全局），进图预计算
- **地块效率系数**：`groundEfficiency` 查表 + `maxHeatRate` 上限
- **五类建筑**：产热锅炉 / 恒温耗热精炼炉 / 温差发电机 / 档位热泵 / 一体化管道

### 1.2 评估方法

对设计方案中列出的 10 个核心机制，逐条在 Mindustry v159.7 源码中查找对应的基础设施，输出：

- **判定**：原生支持 / 需适配 / 难点
- **源码依据**：具体类名:行号
- **模组实现建议**：用什么类、覆写什么方法、数据结构选型
- **风险/注意事项**：多线程、存档兼容、网络同步等

### 1.3 总体判定

**方案整体可行。** 所有核心机制均有 Mindustry 原生基础设施支撑，无需修改游戏源码。主要适配点集中在：环境温度需自建存储（Floor 类无温度字段）、每 tick 驱动需通过 Events 系统注册、建筑列表需手动维护增删。

---

## 2. 逐条机制评估

### 2.1 核心双缓冲循环（Phase1 只读温度写 pendingDeltaQ / Phase2 结算）

- **描述**：每 tick 分两阶段——Phase 1 遍历建筑邻居计算接触热流写入双方 pendingDeltaQ；Phase 2 统一结算 + 环境换热 + 建筑逻辑。
- **判定**：✅ 原生支持
- **源码依据**：
  - `BuildingComp.update()` 每 tick 调用：`core/src/mindustry/entities/comp/BuildingComp.java:2270`
  - `Trigger.update` 枚举值存在：`core/src/mindustry/game/EventType.java:47`
  - `Events.run(Trigger, Runnable)` 注册方式：`Arc/arc-core/src/arc/Events.java:19`
  - `Events.on(Class, Cons)` 注册方式：`Arc/arc-core/src/arc/Events.java:14`
- **模组实现建议**：
  - 在 `ThermalModMain.init()` 中调用 `Events.run(EventType.Trigger.update, () -> thermalSystem.update(1f))` 驱动每 tick 更新
  - `ThermalSystem` 内部维护 `Seq<ThermalBuilding>` 列表，Phase 1 和 Phase 2 各遍历一次
  - 注意 `Trigger.update` 是全局每帧触发（60fps），而非每游戏 tick——Mindustry 的游戏 tick 也是 60fps，所以 dt=1f 对应一 tick
- **风险/注意事项**：
  - `Trigger.update` 在客户端和服务端都会触发，多人模式下需确认热力计算只在服务端运行
  - 遍历顺序虽被双缓冲消除，但 `Seq<ThermalBuilding>` 的增删需在 `placed()`/`onRemoved()` 中维护，遍历时不能修改集合

---

### 2.2 防过冲 flow=min(flow, storedHeat)

- **描述**：接触热流计算后，限制传热量不超过自身存量，杜绝温度穿越与负值。
- **判定**：✅ 原生支持
- **源码依据**：纯数学运算，无需游戏源码支撑。`Math.min(float, float)` 为 Java 标准库。
- **模组实现建议**：
  - 在 `ThermalSystem.update()` Phase 1 中直接使用 `flow = Math.min(flow, myNode.storedHeat)`
  - `ThermalComponent.addHeat()` 中已有 `if (storedHeat < 0f) storedHeat = 0f` 防负兜底
- **风险/注意事项**：无特殊风险。注意 `pendingDeltaQ` 可能为负值（表示流出），Phase 2 结算时 `addHeat(pendingDeltaQ)` 已包含正负处理。

---

### 2.3 惰性温度 T=Q/C（tempDirty 缓存）

- **描述**：温度不实时计算，只在 `getTemperatureK()` 被调用时根据 storedHeat/heatCapacity 求值，并用 tempDirty 标志缓存结果。
- **判定**：✅ 原生支持
- **源码依据**：纯 Java 逻辑，无需游戏 API。
- **模组实现建议**：
  - `ThermalComponent` 内部维护 `cachedTempK` + `tempDirty`
  - `addHeat()` 标记 `tempDirty = true`
  - `getTemperatureK()` 检查 dirty 标志，最多每 tick 一次除法
- **风险/注意事项**：
  - Phase 1 中所有建筑先调用 `getTemperatureK()` 计算温度，此时 tempDirty 会被清除
  - Phase 2 中 `addHeat(pendingDeltaQ)` 又会标记 dirty——这是正确的，因为 Phase 2 的环境换热和建筑逻辑需要重新求值
  - 注意同一 tick 内多次调用 `getTemperatureK()` 不会重复除法，这是设计预期

---

### 2.4 建筑间独立热阻 R（contactResistance，串联 R_AB=R_A+R_B）

- **描述**：建筑间传热使用独立热阻 R，与环境传热系数 airU 无关。两端建筑接触热阻串联相加。
- **判定**：✅ 原生支持
- **源码依据**：
  - `BuildingComp.proximity` 邻居数组：`BuildingComp.java:69` — `transient Seq<Building> proximity = new Seq<>(true, 6, Building.class)`
  - `onProximityUpdate()` 邻居更新回调：`BuildingComp.java:1159`
  - 原版已有热传导基础设施参考：`mindustry/world/blocks/heat/HeatConductor.java`（使用 proximity 邻居传热）
- **模组实现建议**：
  - `ThermalComponent` 持有 `contactResistance` 字段
  - Phase 1 遍历 `building.proximity`，检查邻居是否 `instanceof ThermalBuilding`
  - `flow = dT / (myR + neighborR) * dt`，即 `R_AB = contactResistance_A + contactResistance_B`
  - 只向低温传热：`if (myTemp > neighborTemp)` 计算一次即可，避免双向重复
- **风险/注意事项**：
  - `proximity` 数组是 `transient`，反序列化后会重新填充——不影响我们的设计，因为我们每 tick 都重新遍历
  - 大块建筑（size>1）的 proximity 可能包含多个邻居，每个邻居都会独立计算热流——这与设计方案中"接触面积沿用原版 contactPoints"一致
  - 注意 `proximity` 只包含相邻建筑，对角线建筑不在其中——这是 Mindustry 的标准行为

---

### 2.5 环境查表（双轨温度表 T_ground/T_air，进图预计算）

- **描述**：进图时遍历所有 tile，根据 Floor 类型预计算每个 tile 的地温；全局气温单独存储。运行时建筑查表获得 ΔT。
- **判定**：⚠️ 需适配
- **源码依据**：
  - **Floor 类无温度字段**：`core/src/mindustry/world/blocks/environment/Floor.java:28` 起，仅有 `speedMultiplier`/`dragMultiplier`/`damageTaken`/`drownTime`/`walkEffect` 等，无温度相关字段
  - `WorldLoadEvent` 事件：`EventType.java:107` — `public static class WorldLoadEvent{}`
  - `Tile.pos()` 返回打包坐标：`core/src/mindustry/world/Tile.java:73` — `return Point2.pack(x, y)`
  - `Tile.array()` 返回线性索引：`Tile.java:78` — `return x + y * world.tiles.width`
  - `Tile.floor()` 获取 Floor 类型：`Tile.java:187`
  - `Tiles.eachTile(Cons<Tile>)` 遍历所有 tile：`core/src/mindustry/world/Tiles.java:122`
  - `Vars.world.tiles` 访问：`core/src/mindustry/core/World.java:33`
  - `Vars.world.width()` / `height()`：`World.java:90/94`
- **模组实现建议**：
  - `EnvironmentTemperature` 类使用 `FloatArray groundTemps`，大小为 `world.width() * world.height()`，按 `tile.array()` 索引存储
  - 材质温度表用 `ObjectMap<String, Float>` 或直接用 `Floor.name` 匹配
  - 在 `Events.on(WorldLoadEvent.class, e -> EnvironmentTemperature.precompute())` 中遍历所有 tile 预计算
  - 全局气温 `airTemperatureK` 为静态 float，默认 293.15K（20°C）
  - **注意**：设计文档说"按 tile.pos() 索引"，但 `pos()` 返回的是 `Point2.pack(x,y)` 打包坐标，与 `array()` 的 `x + y*width` 不同。实际实现中应使用 `tile.array()` 作为 FloatArray 索引
- **风险/注意事项**：
  - `WorldLoadEvent` 注释说"Entities are not yet loaded at this stage"——此时建筑还没加载，但 tile 已经就绪，可以安全遍历 floor
  - 高斯-赛德尔迭代在进图时执行一次，运行时零开销——设计合理
  - 存档兼容：地温是预计算的派生数据，不需要序列化，下次进图重新计算即可
  - 多地图切换：需在 `WorldLoadEvent` 时重新分配 `FloatArray` 大小

---

### 2.6 地块效率系数 groundEfficiency + maxHeatRate 上限

- **描述**：每个建筑声明面对不同地块的效率系数（基准空气=1.0），地块本身声明最大热交换速率。实际换热 = min(airU × groundEff × ΔT, maxHeatRate)。
- **判定**：⚠️ 需适配
- **源码依据**：
  - `Block.attributes` 字段：`core/src/mindustry/world/Block.java:186` — `public Attributes attributes = new Attributes()`
  - `Attribute.add(String)` 可自定义属性：`core/src/mindustry/world/meta/Attribute.java:62`
  - 但 Attribute 系统主要用于环境属性（heat/water/oil 等），不适合直接存 maxHeatRate
- **模组实现建议**：
  - `groundEfficiency` 直接在 `ThermalComponent` 中用 `ObjectFloatMap<String>` 存储，key 为 floor 名称
  - `maxHeatRate` 在 `EnvironmentTemperature` 中维护一个 `ObjectFloatMap<String>`，按 floor 名称查询
  - 运行时：`idealFlow = airU × groundEff × ΔT × dt`，`actualFlow = min(idealFlow, maxHeatRate × dt)`
  - 不需要扩展 Attribute 系统，自建查表更简单
- **风险/注意事项**：
  - Floor 名称匹配：`tile.floor().name` 是 Block 的 name 字段（如 "slag"/"sand"/"water"），需确认与设计方案中的 HJSON 配置一致
  - "air" 不是 Floor，是虚拟条目——用 `groundEfficiency.get("air", 1f)` 查询，未列出的默认 1.0

---

### 2.7 isFloating 架空只与空气换热

- **描述**：`isFloating=true` 的建筑只与空气换热，不与地面换热。`isFloating=false` 的建筑同时与空气和地面换热。
- **判定**：✅ 原生支持
- **源码依据**：纯布尔标志判断，无需游戏 API 支撑。
- **模组实现建议**：
  - `ThermalComponent.isFloating` 布尔字段
  - Phase 2 环境换热时：始终计算空气换热；仅 `!isFloating` 时计算地面换热
  - 与 Mindustry 原版的 `floating` 概念无关（原版 floating 指不需要地面支撑的建筑），这是我们模组自己的逻辑标志
- **风险/注意事项**：
  - 不要与 `Block.floating` 字段混淆——那是原版的"可悬空放置"标志
  - 架空管道放在任何地块上都不与地面换热，这是设计预期

---

### 2.8 五类建筑（产热锅炉/恒温耗热精炼炉/纯温驱温差发电机/档位热泵/一体化管道）

- **描述**：五种不同行为模式的热力建筑，每种有独立的 updateThermalLogic 实现。
- **判定**：✅ 原生支持（热泵双端需适配）
- **源码依据**：
  - `Block(String name)` 构造函数：`Block.java:444`
  - 内部 Build 类模式参考：`HeatConductor.java:54` — `public class HeatConductorBuild extends Building`
  - `Building` 基类（由 BuildingComp 生成）：`BuildingComp.java:54` — `@Component(base = true, genInterface = false)`
  - `updateTile()` 覆写：`BuildingComp.java:2017`
  - `placed()` 覆写：`BuildingComp.java:1361`
  - `onRemoved()` 覆写：`BuildingComp.java:1398`
- **模组实现建议**：
  - 每个建筑继承 `Block`（或 `Wall`/`RotatedBlock` 等子类），内部 Build 类继承 `Building` 并实现 `ThermalBuilding` 接口
  - 锅炉：`updateThermalLogic()` 中温度达 maxTempK 停机，回差重启
  - 精炼炉：温度 >= operatingMinK 且有输入时耗热生产，否则空载耗 idleLoss
  - 导热管：纯导热，无自身逻辑
  - 散热塔：高 airU，纯散热
  - 温差发电机/热泵：本轮只给骨架（见范围声明）
- **风险/注意事项**：
  - Build 类是内部类（非 static），需要持有外部 Block 实例引用——这是 Mindustry 标准模式
  - 注册时：`Block` 构造函数自动调用 `Vars.content.handleContent(this)` 注册（`Content.java:20-23`）
  - 每个建筑需要对应的贴图资源（PNG），最小 mod 中可以先用原版贴图占位

---

### 2.9 面板显示 display(Table)

- **描述**：在建筑信息面板中显示温度、热量、热容、运行状态等。
- **判定**：✅ 原生支持
- **源码依据**：
  - `display(Table table)` 方法：`BuildingComp.java:1561`
  - `displayBars(Table table)` 方法：`BuildingComp.java:1667`
  - Arc UI 库 `Table` / `Label` / `Image`：`arc.scene.ui.*`
- **模组实现建议**：
  - Build 类覆写 `display(Table table)`，先调用 `super.display(table)` 保留原版显示
  - 然后在 table 中添加热力信息行：温度（°C）、存储热量、热容、空气换热系数、运行状态
  - 也可覆写 `displayBars()` 添加温度进度条
  - 温度显示：`(thermal.getTemperatureK() - 273.15)` 转摄氏度
- **风险/注意事项**：
  - `display(Table)` 只在同队建筑信息面板中显示（`team == player.team()` 判断在 `BuildingComp.java:1573`）
  - 面板更新频率较高，避免在 display 中做重计算——`getTemperatureK()` 已有缓存
  - 使用 `table.label(() -> ...)`  lambda 形式可自动刷新显示

---

### 2.10 性能（每 tick 遍历所有热力建筑 × 邻居）

- **描述**：每 tick 需遍历所有热力建筑及其邻居，计算接触热流。
- **判定**：✅ 原生支持（性能可控）
- **源码依据**：
  - `BuildingComp.update()` 每 tick 由游戏引擎调用：`BuildingComp.java:2270`
  - 但我们的 ThermalSystem 是通过 `Events.run(Trigger.update, ...)` 驱动的全局遍历
- **模组实现建议**：
  - `Seq<ThermalBuilding>` 维护活跃热力建筑列表
  - Phase 1：N 个建筑 × 平均 4 个邻居 = 4N 次热流计算
  - Phase 2：N 个建筑 × 环境换热 + 自身逻辑 = N 次计算
  - 总计每 tick 约 5N 次浮点运算
- **风险/注意事项**：
  - 60 tick/s，若 N=200 个热力建筑，每 tick 约 1000 次浮点运算——远低于一帧的计算预算
  - 大型工厂（500+ 热力建筑）时需关注，可考虑只更新活跃区域的建筑
  - `Trigger.update` 在渲染线程触发，不要在其中做阻塞操作
  - 存档/读档时 `Seq<ThermalBuilding>` 会被重建，需在 `placed()`/`onRemoved()` 中正确维护

---

## 3. 五类建筑可行性详评

### 3.1 工业锅炉（持续产热）

| 维度 | 评估 |
|:----|:----|
| 实现难度 | ★☆☆☆☆ 极低 |
| 核心逻辑 | 每 tick addHeat(ratedPower)，温度达 maxTempK 停机，回差重启 |
| 源码依赖 | `Building.updateTile()` → 调用 `updateThermalLogic(dt)` |
| 关键注意 | 停机状态用 boolean 标志，回差 hysteresis 防止震荡 |
| 建议 | 直接实现，可作为第一个验证建筑 |

### 3.2 精炼炉（恒温耗热）

| 维度 | 评估 |
|:----|:----|
| 实现难度 | ★★☆☆☆ 低 |
| 核心逻辑 | 温度 >= operatingMinK 且有输入时耗热生产，否则空载耗 idleLoss |
| 源码依赖 | 可参考 `GenericCrafter` 的 `consume()`/`progress` 模式 |
| 关键注意 | 本轮最小 mod 中可简化：只做温度判断，不实际产出物品 |
| 建议 | 骨架实现，生产逻辑后续扩展 |

### 3.3 温差发电机（纯温驱）

| 维度 | 评估 |
|:----|:----|
| 实现难度 | ★★★☆☆ 中 |
| 核心逻辑 | 温差 → efficiency 查表 → 热流转电力 |
| 源码依赖 | 需对接 `PowerNode` / `PowerGraph` 系统（`BuildingComp.java:1163`） |
| 关键注意 | 热端/冷端概念在本设计中合并为单一节点（与环境温差发电），比双端热泵简单 |
| 建议 | 本轮给骨架：定义类结构和参数，不实际接入电力系统 |

### 3.4 档位热泵（双端热量搬运）

| 维度 | 评估 |
|:----|:----|
| 实现难度 | ★★★★☆ 高 |
| 核心逻辑 | 冷端 -Q，热端 +(Q+W)，两个独立 ThermalComponent |
| 源码依赖 | 需要两个 ThermalComponent，需对接电力系统 |
| 关键注意 | 双端接口意味着一个建筑有两个热力节点，与现有 ThermalBuilding 单节点接口不兼容 |
| 建议 | 本轮只给类骨架和参数定义，不实现完整双端逻辑 |

### 3.5 一体化管道（导热传输）

| 维度 | 评估 |
|:----|:----|
| 实现难度 | ★☆☆☆☆ 极低 |
| 核心逻辑 | 无自身逻辑，纯导热——靠 ThermalSystem 的 Phase 1 自动传热 |
| 源码依赖 | 参考 `HeatConductor` 的 Build 类结构 |
| 关键注意 | isFloating=true，低 airU，小 contactResistance |
| 建议 | 直接实现，作为热网连接的基本单元 |

---

## 4. 性能评估

### 4.1 每 tick 计算量估算

```
设 N = 热力建筑数量，K = 平均邻居数（约 4）

Phase 1（接触热流）：
  - N 次 getTemperatureK()（惰性，约 N 次除法）
  - N × K 次邻居遍历
  - N × K/2 次热流计算（只向低温传热，约一半）
  - 每次热流：1 次除法 + 2 次乘法 + 2 次加法 ≈ 5 FLOPs

Phase 2（结算 + 环境换热 + 自身逻辑）：
  - N 次 addHeat(pendingDeltaQ) + 清零
  - N 次 getTemperatureK()（重新求值）
  - N 次空气换热：2 次乘法 + 1 次减法 ≈ 3 FLOPs
  - N × 0.8 次地面换热（20% 架空）：3 次乘法 + 1 次 min ≈ 4 FLOPs
  - N 次 updateThermalLogic(dt)：约 2~10 FLOPs（视建筑类型）

总计每 tick：约 20N FLOPs + 2NK 次比较
```

### 4.2 建筑数量上限建议

| 场景 | N（热力建筑） | 每 tick FLOPs | 评估 |
|:----|:------------:|:------------:|:----|
| 小型基地 | 50 | ~1,000 | 完全无压力 |
| 中型工厂 | 200 | ~4,000 | 流畅运行 |
| 大型热网 | 500 | ~10,000 | 可接受 |
| 超大规模 | 1000+ | ~20,000 | 需考虑优化 |

**建议**：当前实现可支撑 **500 个热力建筑** 流畅运行。超过后可考虑：
1. 只对活跃区域（玩家附近）的建筑执行完整热力计算
2. 远距离建筑降低更新频率（每 N tick 更新一次）
3. 使用 BitSet 优化建筑列表遍历

---

## 5. 存档与网络

### 5.1 序列化需求

| 数据 | 是否需要序列化 | 说明 |
|:----|:------------:|:----|
| storedHeat | ✅ 需要 | 核心状态，读档后需恢复 |
| pendingDeltaQ | ❌ 不需要 | 每 tick 清零，临时缓冲 |
| heatCapacity / airU 等配置参数 | ❌ 不需要 | 来自 Block 定义，运行时不变 |
| groundTemps（环境温度） | ❌ 不需要 | 进图预计算，重进图自动重建 |
| airTemperature | ❌ 不需要 | 全局常量 |
| isFloating / contactResistance | ❌ 不需要 | 来自 Block 定义 |

**实现方式**：在 Build 类的 `write(Writable write)` / `read(Readable read)` 方法中序列化 `storedHeat`。Mindustry 的 Building 自动通过 `BuildingComp` 的序列化机制处理。

### 5.2 多人同步

- **问题**：热力计算在服务端运行，客户端需要看到建筑温度（用于面板和视觉效果）
- **方案**：
  1. 存储 `storedHeat` 到 Building 的序列化字段（原版自动同步）
  2. 客户端通过 `getTemperatureK()` 本地计算显示温度
  3. 不需要额外的网络包——`storedHeat` 随建筑状态自动同步
- **注意**：`pendingDeltaQ` 不需要同步（每 tick 清零）
- **注意**：`placed()` 中有 `if(net.client()) return;` 判断（`BuildingComp.java:1362`）——在客户端不执行 placed 逻辑，所以建筑列表的添加也应该在服务端完成。但 `Events.run(Trigger.update, ...)` 在两端都触发，需要用 `!net.client()` 守卫

---

## 6. 总体结论

### 6.1 可行性判定

**方案完全可行。** 所有核心机制在 Mindustry v159.7 中均有对应的基础设施：

- 双缓冲循环 → `Trigger.update` + `Events.run()` 驱动
- 邻居遍历 → `Building.proximity` 原生邻居数组
- 面板显示 → `display(Table)` 可覆写
- 建筑生命周期 → `placed()` / `onRemoved()` 可覆写
- 世界加载 → `WorldLoadEvent` 可监听
- 内容注册 → `Block` 构造函数自动注册

### 6.2 推荐的最小实现路径

```
Step 1: ThermalComponent（纯 Java 类，无依赖）
Step 2: ThermalBuilding（接口，无依赖）
Step 3: EnvironmentTemperature（WorldLoadEvent 预计算）
Step 4: ThermalSystem（双缓冲管理器，依赖以上三者）
Step 5: HeatConduit（最简单的导热管，验证热传导）
Step 6: IndustrialBoiler（产热，验证温度控制）
Step 7: CoolingTower（散热，验证环境换热）
Step 8: RefineryFurnace（耗热，验证恒温逻辑）
Step 9: display(Table) 面板覆写
Step 10: mod.json + build.gradle 打包
```

### 6.3 后续扩展建议

1. **温差发电机**：接入 `PowerGraph` 系统，将热流转电力
2. **热泵双端**：扩展 ThermalBuilding 接口支持双 ThermalComponent
3. **视觉效果**：根据换热速率触发粒子特效（蒸汽/冒烟/融化）
4. **高斯-赛德尔迭代**：进图时迭代求解地表温度场（当前版本直接用材质温度，不做迭代）
5. **物品级热力**：管道中传输的物品携带热量（设计方案明确本轮不做）
6. **科技树**：通过技术解锁不同档位的管道和热泵

---

## 7. 范围边界声明

本轮交付物的明确范围：

| 项目 | 本轮是否实现 | 说明 |
|:----|:----------:|:----|
| ThermalComponent 核心组件 | ✅ 完整 | 含惰性温度、防负热量、dirty 缓存 |
| ThermalSystem 双缓冲管理器 | ✅ 完整 | Phase1 + Phase2 完整实现 |
| EnvironmentTemperature 环境温度 | ✅ 简化版 | 材质温度表 + FloatArray 存储，不做高斯-赛德尔迭代 |
| IndustrialBoiler 工业锅炉 | ✅ 完整 | 产热 + 停机 + 回差重启 |
| CoolingTower 散热塔 | ✅ 完整 | 高 airU + 地面换热 |
| HeatConduit 导热管 | ✅ 完整 | 架空 + 低 airU + 小 R |
| RefineryFurnace 精炼炉 | ✅ 骨架 | 温度判断 + 空载耗热，不实际生产物品 |
| 温差发电机 | ⏸️ 骨架 | 类结构定义，不接入电力系统 |
| 档位热泵 | ⏸️ 骨架 | 类结构定义，不实现双端逻辑 |
| 物品级热力 | ❌ 不做 | 设计方案明确标注本轮不做 |
| 视觉特效 | ❌ 不做 | 纯计算 mod，无粒子/动画 |
| 高斯-赛德尔温度场迭代 | ❌ 简化 | 直接用材质温度，不做扩散迭代 |
| 序列化/存档 | ⚠️ 基础 | storedHeat 字段预留，完整存档系统后续扩展 |

---

> **报告版本**：v1.0 · 2026-09-11
> **源码核验**：Mindustry v159.7 (HEAD b3317f3) · Arc 引擎
> **评估机制数**：10 项 · ✅ 原生支持 7 项 · ⚠️ 需适配 2 项 · 🔴 难点 0 项

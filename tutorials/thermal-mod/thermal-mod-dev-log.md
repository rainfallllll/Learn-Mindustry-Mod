# 热力学 mod 实战开发全记录

> **面向谁**：想"抄一个完整项目"的萌新。你不需要先懂热力学，也不需要先懂 Mindustry 源码——跟着这 8 章，从设计手册一路走到 `1 mods loaded`，把一个能跑的双缓冲热力系统 mod 从零敲出来。
>
> **怎么读**：任务驱动。每一步都有「目标 → 完整可复制代码 → 关键 API 讲解 → 为什么这么写」。第 6 章"踩坑实录"是本教程最值钱的部分，建议先翻那里再回头看实现。
>
> **版本声明**：本 mod 按 **Mindustry v159.7** API 编写；所有源码核验基于当前工作树（**v160 分支**）。经逐条比对，热力系统用到的核心 API 在 v159.7 → v160 之间**未发生破坏性变更**（Building.update / proximity / display(Table) / WorldLoadEvent / Block.requirements 等签名一致）。下文标注的 `类:行号` 以 v160 工作树为准，v159.7 行号可能偏移但 API 结构不变。
>
> **配套产物**：已编译 `thermal-mod.jar`（22 KB，26 项），headless 加载验证通过。

---

## 第 1 章：缘起——为什么做热力学 mod

### 1.1 设计手册摘要（八节）

动手之前，我们手里有一份《热力学系统设计手册》（v1.0）。它不是一本代码书，而是一张"物理规则图纸"。八节内容，一节都不能跳：

| 节 | 标题 | 一句话摘要 |
|:--|:-----|:----------|
| 1 | 设计哲学 | Q 是本质状态，T 是惰性表观；温差驱动；查表代替公式；双缓冲结算 |
| 2 | 数据结构 | `ThermalComponent`：storedHeat + pendingDeltaQ 双缓冲、heatCapacity、airU、groundEfficiency、isFloating、contactResistance |
| 3 | 主循环算法 | Phase1 只读温度写 pendingDeltaQ；Phase2 结算 + 环境换热 + 建筑自身逻辑 |
| 4 | 环境交互 | 双轨温度表（T_ground / T_air）、地块效率系数、maxHeatRate 上限、架空 vs 地面规则 |
| 5 | 建筑规约 | 五类建筑：持续产热 / 恒温耗热 / 纯温驱（温差发电）/ 档位热泵 / 一体化管道 |
| 6 | 玩家流程 | 定比例 → 估损耗 → 认负载 → 选管道 → 选热泵 → 读反馈 → 选址 |
| 7 | 性能安全 | Q 只做加法、双缓冲消除顺序依赖、只向低温传热省 50% 算力、防过冲 min(flow, Q) |
| 8 | HJSON 速查 | 基础建筑模板 / 架空管道模板 / 散热塔模板三套抄改样例 |

### 1.2 核心设计理念：三句话讲透

整本手册其实就讲了三件事：

**① 热量 Q 为唯一状态变量，温度 T = Q / C 惰性求值。**

底层所有运算都是整数级的加减法（热量 J）。温度不是存下来的，而是"要的时候才算"：

```
T = storedHeat / heatCapacity
```

为什么不直接存温度？因为如果直接存 T，那么每次热流交换都要做 `T += flow / C`，每 tick 每对邻居都得算一次除法。改成存 Q，热流就是纯加法 `storedHeat += deltaQ`，温度只在面板刷新和热流计算时算一次——而且加了 dirty 缓存后，每 tick 每个建筑最多一次除法。

**② 纯加法注入。**

所有热量进出都是 `storedHeat += deltaQ`。没有乘法状态、没有矩阵、没有迭代求解。玩家心算负担就是加减法。

**③ 双缓冲防过冲。**

如果 A 觉得自己比 B 热，直接 `A -= flow; B += flow`，那么遍历顺序会影响结果（先遍历 A 和先遍历 B 算出来不一样），而且大温差下一 tick 就把 A 的热量抽干变负数。解法是两阶段：Phase1 只把"想流多少"写进 `pendingDeltaQ`（绝不碰 storedHeat），Phase2 统一结算。再加一道 `flow = min(flow, storedHeat)` 防过冲，温度永远不会穿越。

### 1.3 为什么选 Java mod 而不是 HJSON

Mindustry 做 mod 有两条路：

- **HJSON + JS（行为脚本）**：不改游戏源码，靠数据文件 + 简单 JS 逻辑拼。适合"改数值、加配方、调参数"。
- **Java mod**：继承 `mindustry.mod.Mod`，写真正的 Java 类，可以实现**游戏原本没有的机制**。

热力学系统要的是：
- 一个每 tick 跑的**双缓冲热流主循环**（游戏原版没有）；
- 建筑之间**通过 proximity 邻居数组做热传导**（原版没有热量概念）；
- 一个**按 tile 索引的环境温度查表**（原版 Floor 类根本没有温度字段）。

这些都是"自定义游戏机制"，HJSON 表达不了——你没法在 HJSON 里写"遍历所有建筑、只读温度、写缓冲、再结算"。所以本项目走 Java mod 路线。

---

## 第 2 章：需求拆解——从设计手册到 10 个机制

设计手册是物理图纸，不能直接翻译成代码。中间必须有一层"机制映射"：把八节手册翻译成 10 个可实现的技术机制。

### 2.1 映射表

| # | 机制名 | 来源章节 | 实现方式 |
|:--|:------|:--------|:--------|
| 1 | 双缓冲循环 | §3 主循环算法 | ThermalSystem 两阶段 update：Phase1 写 pendingDeltaQ，Phase2 结算 |
| 2 | 防过冲 | §7 性能安全 | `flow = Math.min(flow, myNode.storedHeat)`（ThermalSystem.java:74） |
| 3 | 惰性温度 | §2 数据结构 / §7 | `tempDirty` 标志 + `cachedTempK` 缓存，`getTemperatureK()` 重算（ThermalComponent.java:90-96） |
| 4 | 建筑间热阻 R | §4.5 建筑间传热 | `contactResistance` 串联：R_AB = R_A + R_B（ThermalSystem.java:67-68） |
| 5 | 环境查表 | §4.1 双轨温度表 | `EnvironmentTemperature` 静态表，`getGroundTemp(tile)` / `getAirTemp()` O(1) |
| 6 | 地块效率系数 | §4.3 效率系数表 | `ObjectFloatMap<String> groundEfficiency`，实际 U = airU × 效率（ThermalComponent.java:59） |
| 7 | maxHeatRate | §4.3 最大热交换速率 | `Math.min(idealFlow, maxRate * dt)` 截断（ThermalSystem.java:118） |
| 8 | isFloating 架空 | §4.4 架空 vs 地面 | `if (!node.isFloating)` 才做地面换热（ThermalSystem.java:109） |
| 9 | 五类建筑 | §5 建筑规约 | 本版实现其中四类：锅炉（产热）/ 散热塔（散热）/ 导热管（输热）/ 精炼炉（用热）；温差发电 + 热泵仅骨架 |
| 10 | 面板显示 | §5.x 面板显示 | 覆写 `display(Table table)`，`table.left().label(...)` 实时刷新 |

### 2.2 为什么是 10 个而不是 8 个

手册八节里，§6（玩家流程）和 §8（HJSON 速查）是"面向玩家/配置"的，不需要写成代码机制。真正要落到代码的是 §1~§5、§7 的物理规则，拆出来正好 10 个可独立实现、可独立验证的技术点。

---

## 第 3 章：可行性决策——先查 API 再动手

萌新最容易犯的错：**上来就写代码，写到一半发现游戏没有这个 API**。正确做法是先把 10 个机制挨个去 Mindustry v159.7/v160 源码里查一遍，判定能不能做、怎么做。

### 3.1 10 机制判定表

| # | 机制 | 判定 | 源码依据（v160 工作树） |
|:--|:-----|:---:|:----------------------|
| 1 | 双缓冲循环 | ✅ 原生支持 | `BuildingComp.update()` 每 tick 调用；`Events.run(Trigger.update, ...)` 驱动 |
| 2 | 防过冲 | ✅ 原生支持 | 纯 `Math.min(float, float)`，Java 标准库，无需游戏 API |
| 3 | 惰性温度 | ✅ 原生支持 | 纯 Java 逻辑（dirty 标志 + 缓存），无需游戏 API |
| 4 | 建筑间热阻 R | ✅ 原生支持 | `Building.proximity` 邻居数组（`Seq<Building>`），遍历时 `instanceof ThermalBuilding` 判断 |
| 5 | 环境查表 | ⚠️ 需适配 | **Floor 类无温度字段**（`Floor.java` 仅有 speedMultiplier/dragMultiplier 等），需自建 `FloatSeq groundTemps` 按 tile.array() 存储 |
| 6 | 地块效率系数 | ⚠️ 需适配 | `Block.attributes` 是通用属性包，但温度/效率不是原版语义；自建 `ObjectFloatMap<String>` 更直接 |
| 7 | maxHeatRate | ✅ 原生支持 | 纯 `Math.min(idealFlow, maxRate*dt)`，查表 + 截断 |
| 8 | isFloating 架空 | ✅ 原生支持 | 纯布尔标志 `if(!isFloating)`，无游戏 API 依赖 |
| 9 | 五类建筑 | ✅ 原生支持 | `Block(String name)` 构造 + 覆写 `placed()`/`onRemoved()`/`updateThermalLogic(dt)`；热泵双端需额外适配（本版仅骨架） |
| 10 | 面板显示 | ✅ 原生支持 | `Building.display(Table table)` 钩子（`BuildingComp.java:1561` 附近），`table.left().label(() -> "...")` 实时文本 |

**汇总：7 项原生支持 ✅ / 3 项需适配 ⚠️ / 0 项难点 🔴。**

### 3.2 几个关键 API 为什么这么选

- **`BuildingComp.update()` 每 tick 调用**：游戏引擎会对每个建筑实例每 tick 调一次 `update()`。但我们的热流系统是"全局遍历所有建筑两两算"，不能写在单个建筑的 update 里（那样会重复算 N² 次）。所以用 `Events.run(EventType.Trigger.update, ...)` 在**主类里集中驱动一次**，再手动遍历自己维护的 `buildings` 列表。
- **`proximity` 邻居数组**：Mindustry 已经维护好了每个建筑的相邻建筑数组（`Building.proximity`，`Seq<Building>`）。我们不需要自己算坐标找邻居，直接遍历这个数组就行。
- **`display(Table)` 信息面板**：点选建筑时，游戏会调 `display(Table table)` 让你往面板里塞内容。`label(() -> "...")` 传的是 lambda，每帧重新求值——正好用来实时刷新温度数字。
- **`Block.attributes`**：原版有个通用属性包，但语义是"建筑属性"（比如射程、射速），不适合塞自定义物理量。所以效率系数我们自建 `ObjectFloatMap`，不污染原版 attributes。
- **Floor 无温度字段**：这是最大的适配点。`Floor` 类只有行走速度、拖拽、伤害、溺水时间这些字段，**没有温度**。不能往 Floor 里塞字段，那就自建一张 `FloatSeq groundTemps`，进图时按 `tile.array()` 索引预计算填好。
- **`WorldLoadEvent`**：进图瞬间触发一次。我们在这里调 `EnvironmentTemperature.precompute()`，把整张地图的地温表初始化好。

### 3.3 范围边界（本版不做什么）

为了"能跑通、能抄"，本版主动砍三块：

- **温差发电机 / 热泵**：只留类骨架（接口和字段），不接入电力系统，不做双端搬运逻辑。
- **物品级热力**：传送带携带热量？不做。物品不持有 ThermalComponent。
- **高斯-赛德尔稳态求解**：手册 §4.2 写了进图前迭代求解温度场，本版简化为"按材质直接赋初始温度"，不做迭代扩散。

这三个都写在第 7 章当迭代方向。

---

## 第 4 章：分步实现（核心，7 步）

这是全教程最重的一章。7 步走完，你就有一个能跑的 mod。每步给：目标 → 完整可复制代码 → 关键 API 讲解 → 为什么这么写。

项目包结构（最终长这样）：

```
thermal-mod/
├── build.gradle
├── mod.json
└── src/main/java/com/thermal/mod/
    ├── ThermalModMain.java          # 步骤 1：主类
    ├── core/
    │   ├── ThermalComponent.java    # 步骤 2：数据层
    │   ├── ThermalSystem.java       # 步骤 3：系统层
    │   ├── EnvironmentTemperature.java # 步骤 4：环境层
    │   └── ThermalBuilding.java     # 步骤 5：建筑基类接口
    ├── blocks/
    │   ├── IndustrialBoiler.java    # 步骤 6：四个建筑
    │   ├── CoolingTower.java
    │   ├── HeatConduit.java
    │   └── RefineryFurnace.java
    └── content/
        ├── ThermalBlocks.java       # 步骤 7：注册
        └── ThermalItems.java
```

---

### 步骤 1：项目骨架

**目标**：搭出 Gradle 工程 + mod.json + 主类，让游戏能识别这是一个 Java mod。

#### 4.1.1 build.gradle

```groovy
plugins {
    id 'java'
}

// Mindustry v159.7 使用 Java 17
sourceCompatibility = 17
targetCompatibility = 17

repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    // Mindustry 核心库（仅编译时需要，运行时由游戏提供）
    compileOnly "com.github.Anuken.Mindustry:core:v159.7"
    // Arc 引擎核心库
    compileOnly "com.github.Anuken.Arc:arc-core:889dd8880f"
}

sourceSets {
    main {
        resources {
            srcDirs = ['src/main/resources']
        }
    }
}

jar {
    manifest {
        attributes(
            'Main-Class': 'mindustry.mod.ModClassLoader'
        )
    }
    from('mod.json') {
        into ''
    }
}

buildDir = 'build'
```

**关键 API 讲解**：
- `compileOnly`：Mindustry 和 Arc 是游戏运行时自带的，编译时需要它们的类来过类型检查，但**不能打进 jar**（否则和游戏自带的冲突）。
- `jitpack.io`：Mindustry 的 Maven 坐标不在 Maven Central，走 JitPack 拉 `com.github.Anuken.Mindustry:core:v159.7`。
- `from('mod.json') { into '' }`：把 mod.json 打进 jar 根目录，游戏靠它识别 mod。

#### 4.1.2 mod.json

```json
{
  "name": "Thermal Mod",
  "displayName": "热力学系统",
  "author": "Thermal Mod Team",
  "description": "基于热力学系统设计手册的热量交换模组：双缓冲热流结算、惰性温度、环境温度查表。",
  "version": "0.1.0",
  "main": "com.thermal.mod.ThermalModMain",
  "minGameVersion": "154",
  "java": true,
  "dependencies": []
}
```

**关键 API 讲解**：
- `"main"`：主类全限定名，游戏加载 mod 时会 new 这个类并调它的 `loadContent()` / `init()`。
- `"java": true`：告诉游戏这是 Java mod（不是纯 HJSON/JS）。
- `"minGameVersion": "154"`：最低支持版本。我们用的 API 在 v154 就有了。

#### 4.1.3 主类 ThermalModMain.java

```java
package com.thermal.mod;

import arc.Events;
import arc.util.Log;
import com.thermal.mod.content.ThermalBlocks;
import com.thermal.mod.core.EnvironmentTemperature;
import com.thermal.mod.core.ThermalSystem;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.mod.Mod;

public class ThermalModMain extends Mod {

    public static ThermalSystem thermalSystem;

    @Override
    public void loadContent() {
        ThermalBlocks.load();
        Log.info("ThermalMod: 内容加载完成");
    }

    @Override
    public void init() {
        thermalSystem = new ThermalSystem();

        // 注册 WorldLoadEvent：进图时预计算环境温度
        Events.on(EventType.WorldLoadEvent.class, e -> {
            EnvironmentTemperature.precompute();
            Log.info("ThermalMod: 环境温度预计算完成");
        });

        // 每 tick 驱动热力系统更新（仅服务端运行）
        Events.run(EventType.Trigger.update, () -> {
            if (!Vars.net.client()) {
                thermalSystem.update(1f);
            }
        });

        Log.info("ThermalMod: 模组初始化完成");
    }
}
```

**为什么这么写**：
- 继承 `mindustry.mod.Mod`，重写 `loadContent()` 和 `init()`。游戏的生命周期是**先 loadContent（注册内容），后 init（初始化系统）**。
- `ThermalBlocks.load()` 必须在 `loadContent()` 里调——内容（方块/物品）必须在游戏内容系统初始化阶段注册，晚了就进不到物品栏。
- `thermalSystem` 挂成 `public static`，后面所有建筑都通过 `ThermalModMain.thermalSystem.register(this)` 拿到系统实例。
- `!Vars.net.client()` 守卫：多人客户端不跑模拟，只跑服务端/单人。否则热力会在两端各算一遍，数据错乱。

---

### 步骤 2：数据层——ThermalComponent

**目标**：定义"一个热节点"的数据结构。这是整个 mod 的地基。

```java
package com.thermal.mod.core;

import arc.struct.ObjectFloatMap;

public class ThermalComponent {

    // ── 状态变量 ──
    public float storedHeat;          // 当前存储的热量 (J)
    public float pendingDeltaQ;       // 本 tick 待应用的热流缓冲（双缓冲核心）

    // ── 配置参数 ──
    public float heatCapacity;        // 热容 C (J/K)
    public float minTempK = 273f;     // 温度下限（绝对零度保护）
    public float maxTempK = 1000f;    // 安全上限

    // ── 传热系数 ──
    public float airU;                // 与空气的基准传热系数 (J/tick·格·K)

    // ── 地块效率系数 ──
    // key = 地块名称 / "air", value = 效率倍率；实际 U = airU × 效率
    public ObjectFloatMap<String> groundEfficiency = new ObjectFloatMap<>();

    // ── 架空标志 ──
    public boolean isFloating;         // true=悬空(仅空气换热), false=地面(空气+地面)

    // ── 建筑间接触热阻 ──
    public float contactResistance = 0.05f;  // R (K·tick/J)，与 airU 独立

    // ── 缓存（私有，外部不可直接碰）──
    private float cachedTempK;
    private boolean tempDirty = true;

    /** 惰性温度求值：每 tick 最多一次除法。 */
    public float getTemperatureK() {
        if (tempDirty) {
            cachedTempK = Math.max(minTempK, storedHeat / heatCapacity);
            tempDirty = false;
        }
        return cachedTempK;
    }

    /** 纯加法注入/抽取热量，标记温度过期。 */
    public void addHeat(float deltaQ) {
        storedHeat += deltaQ;
        if (storedHeat < 0f) storedHeat = 0f;  // 防负热量兜底
        tempDirty = true;
    }

    /** 标记温度缓存失效（外部直接改 storedHeat 后调用）。 */
    public void invalidate() {
        tempDirty = true;
    }

    /** 重置缓冲（Phase 2 结算后调用）。 */
    public void reset() {
        pendingDeltaQ = 0f;
    }

    /** 查询地块效率系数，未列出的返回 1.0。 */
    public float getGroundEfficiency(String floorName) {
        return groundEfficiency.get(floorName, 1f);
    }

    /** 设置地块效率系数。 */
    public void setGroundEfficiency(String floorName, float efficiency) {
        groundEfficiency.put(floorName, efficiency);
    }
}
```

**关键 API 讲解**：
- `arc.struct.ObjectFloatMap<String>`：Arc 引擎的"键值映射"，value 是原始 float（不装箱）。对应 Java 的 `Map<String, Float>`，但 `get(key, 默认值)` 直接给默认值，不用判空。
- `cachedTempK` / `tempDirty` 是 **private**——这是故意的（坑 3 会讲为什么）。外部想让缓存失效，只能调公开的 `invalidate()`。

**为什么这么写（为什么用组件模式而非继承）**：
Mindustry 的建筑类已经有很深的继承链（`Block → Wall → ...`，`Building → ...`）。如果再从 `Wall` 派生出 `ThermalWall`，再往下派 `BoilerWall`，继承链会爆炸。组件模式（组合）让每个 Build 类直接 `public ThermalComponent thermal = new ThermalComponent()`，热力学逻辑全部收敛在组件里，建筑类只管"什么时候 register、什么时候注入多少热"。**数据和行为分离，继承只用来接游戏钩子。**

---

### 步骤 3：系统层——ThermalSystem

**目标**：写双缓冲主循环。这是 mod 的心脏。

```java
package com.thermal.mod.core;

import arc.struct.Seq;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.world.Tile;

public class ThermalSystem {

    /** 活跃热力建筑列表 */
    private final Seq<ThermalBuilding> buildings = new Seq<>();

    public void register(ThermalBuilding building) {
        if (!buildings.contains(building)) {
            buildings.add(building);
        }
    }

    public void unregister(ThermalBuilding building) {
        buildings.remove(building);
    }

    /** 每 tick 主更新入口。 */
    public void update(float dt) {
        if (buildings.size == 0) return;

        // ═══ Phase 1：接触热流计算（只读温度，写缓冲）═══
        for (int i = 0; i < buildings.size; i++) {
            ThermalBuilding thermalBuilding = buildings.get(i);
            Building myBuilding = thermalBuilding.asBuilding();
            ThermalComponent myNode = thermalBuilding.getThermal();
            float myTemp = myNode.getTemperatureK();

            for (int j = 0; j < myBuilding.proximity.size; j++) {
                Building neighbor = myBuilding.proximity.get(j);
                if (neighbor == null) continue;
                if (neighbor instanceof ThermalBuilding thermalNeighbor) {
                    float neighborTemp = thermalNeighbor.getThermal().getTemperatureK();

                    // 【核心规则】只向低温传热，避免重复计算
                    if (myTemp > neighborTemp) {
                        float dT = myTemp - neighborTemp;
                        float resistance = myNode.contactResistance
                            + thermalNeighbor.getThermal().contactResistance;
                        if (resistance <= 0f) continue;

                        float flow = (dT / resistance) * dt;

                        // 【防过冲保护】传热量不超过自身存量
                        flow = Math.min(flow, myNode.storedHeat);

                        if (flow > 0f) {
                            // 【双缓冲】绝不直接修改 storedHeat
                            myNode.pendingDeltaQ -= flow;
                            thermalNeighbor.getThermal().pendingDeltaQ += flow;
                        }
                    }
                }
            }
        }

        // ═══ Phase 2：统一结算 + 环境换热 + 自身逻辑 ═══
        for (int i = 0; i < buildings.size; i++) {
            ThermalBuilding thermalBuilding = buildings.get(i);
            Building myBuilding = thermalBuilding.asBuilding();
            ThermalComponent node = thermalBuilding.getThermal();

            // 1. 结算接触热流
            node.addHeat(node.pendingDeltaQ);
            node.pendingDeltaQ = 0f;

            // 2. 环境换热
            float myTemp = node.getTemperatureK();
            float heatLoss = 0f;

            // 2a. 对空气散热——所有建筑都有
            float airEff = node.getGroundEfficiency("air");
            float uAir = node.airU * airEff;
            float tAir = EnvironmentTemperature.getAirTemp();
            heatLoss += uAir * (myTemp - tAir) * dt;

            // 2b. 对地面散热——仅非架空建筑
            if (!node.isFloating) {
                Tile tile = myBuilding.tile;
                if (tile != null) {
                    String floorName = tile.floor().name;
                    float groundEff = node.getGroundEfficiency(floorName);
                    float uGround = node.airU * groundEff;
                    float tGround = EnvironmentTemperature.getGroundTemp(tile);
                    float maxRate = EnvironmentTemperature.getMaxHeatRate(floorName);
                    float idealFlow = uGround * (myTemp - tGround) * dt;
                    heatLoss += Math.min(idealFlow, maxRate * dt);
                }
            }

            node.addHeat(-heatLoss);

            // 3. 建筑自身产热/耗热/热泵逻辑
            thermalBuilding.updateThermalLogic(dt);
        }
    }

    public int getBuildingCount() {
        return buildings.size;
    }
}
```

**关键 API 讲解**：
- `myBuilding.proximity`：游戏维护好的邻居数组，类型 `Seq<Building>`。直接 `proximity.get(j)` 遍历。
- `instanceof ThermalBuilding thermalNeighbor`：Java 16+ 模式匹配，一步完成"判断类型 + 转型"。邻居不是热力建筑就跳过（比如旁边一堵普通墙）。
- `myBuilding.tile.floor().name`：拿到脚下地块的名字（"slag"/"water"/"ice"...），拿去查表。

**为什么这么写**：
- **Phase1 只读温度**：整个阶段只调 `getTemperatureK()`（不写 storedHeat），把"想流多少"记到双方的 `pendingDeltaQ`。这样遍历 A→B 和 B→A 不会互相影响。
- **Phase2 统一结算**：先 `addHeat(pendingDeltaQ)` 把热流真正落到 storedHeat，再做环境换热，最后才让建筑跑自己的产热/耗热逻辑。**顺序固定，结果可复现。**
- **`flow = Math.min(flow, myNode.storedHeat)`**：防过冲。温差大的时候 `dT/R` 可能算出远超自身存量的流量，不截断就会把热量抽成负数、温度穿越零下。
- **`Events.run(Trigger.update, ...)` + `!Vars.net.client()`**：主类里挂的事件每 tick 调一次 `thermalSystem.update(1f)`，客户端不跑。

---

### 步骤 4：环境层——EnvironmentTemperature

**目标**：建一张"这张地图每格多少度"的表，建筑换热时直接查。

```java
package com.thermal.mod.core;

import arc.struct.FloatSeq;
import arc.struct.ObjectMap;
import mindustry.Vars;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.world.Tile;

public class EnvironmentTemperature {

    /** 全局气温 (K)，默认 20°C */
    public static float airTemperatureK = 293.15f;

    /** 每格地温，按 tile.array() 索引存储 */
    private static FloatSeq groundTemps;

    /** 材质初始温度表（key = floor 名称） */
    private static final ObjectMap<String, Float> materialTemps = new ObjectMap<>();

    /** 地块最大热交换速率表（key = floor 名称），单位 J/tick */
    private static final ObjectMap<String, Float> maxHeatRateMap = new ObjectMap<>();

    static {
        // 材质初始温度 (K)
        materialTemps.put("slag", 1473.15f);     // 熔岩 1200°C
        materialTemps.put("sand", 308.15f);       // 沙地 35°C
        materialTemps.put("water", 293.15f);      // 浅水 20°C
        materialTemps.put("deepwater", 293.15f);  // 深水 20°C
        materialTemps.put("ice", 268.15f);         // 冰面 -5°C
        materialTemps.put("snow", 268.15f);       // 雪地 -5°C
        materialTemps.put("stone", 298.15f);      // 石头 25°C
        materialTemps.put("grass", 298.15f);      // 草地 25°C
        materialTemps.put("dirt", 298.15f);       // 泥土 25°C

        // 地块最大热交换速率 (J/tick)
        maxHeatRateMap.put("slag", Float.POSITIVE_INFINITY);  // 岩浆不限
        maxHeatRateMap.put("water", 300f);
        maxHeatRateMap.put("deepwater", 500f);
        maxHeatRateMap.put("sand", 400f);
        maxHeatRateMap.put("stone", 500f);
        maxHeatRateMap.put("ice", 50f);
        maxHeatRateMap.put("snow", 50f);
    }

    /** 进图预计算：遍历所有 tile，按 floor 名称设置地温。WorldLoadEvent 中调用。 */
    public static void precompute() {
        int w = Vars.world.width();
        int h = Vars.world.height();
        groundTemps = new FloatSeq(w * h);
        groundTemps.size = w * h;
        java.util.Arrays.fill(groundTemps.items, 293.15f);

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                Tile tile = Vars.world.tiles.get(x, y);
                if (tile == null) continue;
                String floorName = tile.floor().name;
                float temp = materialTemps.get(floorName, 293.15f);
                groundTemps.set(tile.array(), temp);
            }
        }
    }

    public static float getGroundTemp(Tile tile) {
        if (groundTemps == null) return 293.15f;
        int idx = tile.array();
        if (idx < 0 || idx >= groundTemps.size) return 293.15f;
        return groundTemps.get(idx);
    }

    public static float getAirTemp() {
        return airTemperatureK;
    }

    public static float getMaxHeatRate(String floorName) {
        return maxHeatRateMap.get(floorName, Float.POSITIVE_INFINITY);
    }

    public static void reset() {
        groundTemps = null;
    }
}
```

**关键 API 讲解**：
- **`FloatSeq`**：Arc 的 float 数组封装（坑 2 会讲为什么不是 `FloatArray`）。底层就是一个 `float[] items`，O(1) 随机访问。
- **`tile.array()`**：这是关键。Mindustry 里 tile 有两个索引：`tile.x()/tile.y()`（坐标）和 `tile.array()`（一维数组下标）。我们建表时用 `w*h` 长度，必须按 `tile.array()` 存、按 `tile.array()` 取——用错 `tile.pos()` 会越界。
- **`Vars.world.tiles.get(x, y)`**：拿任意坐标的 tile。
- **`WorldLoadEvent`**：主类里 `Events.on(WorldLoadEvent.class, ...)` 触发，进图那一刻跑一次 `precompute()`。

**为什么 Floor 没有温度字段（需自建存储）**：
原版 `Floor` 类只有行走速度、拖拽系数、伤害、溺水时间、行走特效这些字段，**物理上就没有温度概念**。我们不能改游戏源码，也不该往 Floor 里塞自定义字段（会和序列化冲突）。所以另起一张 `FloatSeq groundTemps`，按 tile 的一维索引存，进图时填好，运行时只读。查表 O(1)，零耦合。

---

### 步骤 5：建筑基类——ThermalBuilding 接口

**目标**：定一个"凡是热力建筑都得长这样"的契约，让 ThermalSystem 能统一调用。

```java
package com.thermal.mod.core;

import mindustry.gen.Building;

public interface ThermalBuilding {

    /** @return 此建筑的热力组件 */
    ThermalComponent getThermal();

    /** Phase 2 中调用，执行建筑自身的产热/耗热/热泵逻辑。 */
    void updateThermalLogic(float deltaTime);

    /** @return 转为 Building 引用（用于访问 tile/proximity 等） */
    Building asBuilding();
}
```

**为什么用接口而不是抽象类**：
Mindustry 的 Build 类必须 `extends Building`（游戏 ECS 要求），Java 单继承已经被 `Building` 占了，没法再 `extends AbstractThermalBuilding`。所以用接口：每个建筑的 Build 类 `implements ThermalBuilding`，三个方法（拿组件 / 跑自身逻辑 / 转回 Building）实现一遍就行。

**proximity 邻居数组的使用**：接口本身不直接碰 proximity——那是 `asBuilding()` 转回 `Building` 之后，在 `ThermalSystem` 里统一遍历的。建筑类自己不用找邻居。

---

### 步骤 6：四个建筑

下面四个建筑结构高度一致：继承 `Wall`（先拿个简单的实心方块当爹）→ 内部写一个 `XxxBuild extends Building implements ThermalBuilding` → `placed()` 里配参数 + register → `onRemoved()` 里 unregister → `updateThermalLogic(dt)` 写自己的产热/耗热逻辑 → `display(Table)` 写面板。

#### 4.6.1 IndustrialBoiler（产热）

```java
package com.thermal.mod.blocks;

import arc.scene.ui.layout.Table;
import com.thermal.mod.ThermalModMain;
import com.thermal.mod.core.ThermalBuilding;
import com.thermal.mod.core.ThermalComponent;
import mindustry.gen.Building;
import mindustry.world.blocks.defense.Wall;

public class IndustrialBoiler extends Wall {

    public float ratedPower = 100f;        // 额定产热 (J/tick)
    public float restartHysteresis = 50f; // 重启回差 (K)

    public IndustrialBoiler(String name) {
        super(name);
        update = true;
        solid = true;
        noUpdateDisabled = true;
        rotate = false;
    }

    public class BoilerBuild extends Building implements ThermalBuilding {

        public ThermalComponent thermal = new ThermalComponent();
        public boolean isShutdown = false;

        @Override public ThermalComponent getThermal() { return thermal; }
        @Override public Building asBuilding() { return this; }

        @Override
        public void placed() {
            super.placed();
            thermal.heatCapacity = 2000f;
            thermal.maxTempK = 800f;       // 527°C
            thermal.minTempK = 273f;
            thermal.airU = 0.05f;
            thermal.isFloating = false;
            thermal.contactResistance = 0.05f;
            // 初始温度设为环境温度（20°C）
            thermal.storedHeat = thermal.heatCapacity * 293.15f;
            thermal.invalidate();

            ThermalModMain.thermalSystem.register(this);
        }

        @Override
        public void onRemoved() {
            super.onRemoved();
            ThermalModMain.thermalSystem.unregister(this);
        }

        @Override
        public void updateThermalLogic(float dt) {
            float temp = thermal.getTemperatureK();

            // 温度达上限 → 停机
            if (temp >= thermal.maxTempK) {
                isShutdown = true;
            }
            // 温度回落到阈值以下 → 重启
            if (isShutdown && temp < thermal.maxTempK - restartHysteresis) {
                isShutdown = false;
            }
            // 运行中产热
            if (!isShutdown) {
                thermal.addHeat(ratedPower * dt);
            }
        }

        @Override
        public void display(Table table) {
            super.display(table);
            table.row();
            table.left().label(() -> "[orange]工业锅炉[]").padTop(4);
            table.row();
            table.left().label(() ->
                "温度: " + String.format("%.1f", thermal.getTemperatureK() - 273.15f) + "°C" +
                (isShutdown ? " [red](停机)" : " [green](运行)"));
            table.row();
            table.left().label(() ->
                "存储热量: " + String.format("%.0f", thermal.storedHeat) + " J");
            table.row();
            table.left().label(() -> "热容: " + thermal.heatCapacity + " J/K");
            table.row();
            table.left().label(() ->
                "产热: " + (isShutdown ? "0" : (int)ratedPower) + " J/t");
        }
    }
}
```

**关键设计说明**：
- **达 maxTempK 硬停机 + 回差重启**：`temp >= maxTempK` 停机；`temp < maxTempK - restartHysteresis` 才重启。回差 50K 防止温度在临界点抖来抖去高频启停。
- **`thermal.invalidate()`**：placed 里直接改了 `storedHeat`，必须手动失效缓存（坑 3 的教训）。
- **`update = true` + `noUpdateDisabled = true`**：告诉游戏这个建筑每 tick 要 update，且被断电/禁用时也别停我们的热力逻辑（其实我们的逻辑走 ThermalSystem，不依赖游戏 update 调度，但保险起见开着）。

#### 4.6.2 CoolingTower（散热）

```java
package com.thermal.mod.blocks;

import arc.scene.ui.layout.Table;
import com.thermal.mod.ThermalModMain;
import com.thermal.mod.core.ThermalBuilding;
import com.thermal.mod.core.ThermalComponent;
import mindustry.gen.Building;
import mindustry.world.blocks.defense.Wall;

public class CoolingTower extends Wall {

    public CoolingTower(String name) {
        super(name);
        update = true;
        solid = true;
        noUpdateDisabled = true;
        rotate = false;
    }

    public class TowerBuild extends Building implements ThermalBuilding {

        public ThermalComponent thermal = new ThermalComponent();

        @Override public ThermalComponent getThermal() { return thermal; }
        @Override public Building asBuilding() { return this; }

        @Override
        public void placed() {
            super.placed();
            thermal.heatCapacity = 500f;
            thermal.maxTempK = 600f;
            thermal.minTempK = 273f;
            thermal.airU = 0.31f;              // 高 airU = 散热强
            thermal.isFloating = false;        // 接地，借地面散热
            thermal.contactResistance = 0.05f;
            thermal.storedHeat = thermal.heatCapacity * 293.15f;
            thermal.invalidate();

            // 地块效率系数（不同地块散热快慢不同）
            thermal.setGroundEfficiency("air", 1.0f);
            thermal.setGroundEfficiency("sand", 1.2f);
            thermal.setGroundEfficiency("stone", 1.5f);
            thermal.setGroundEfficiency("water", 2.2f);
            thermal.setGroundEfficiency("slag", 3.0f);
            thermal.setGroundEfficiency("ice", 0.6f);

            ThermalModMain.thermalSystem.register(this);
        }

        @Override
        public void onRemoved() {
            super.onRemoved();
            ThermalModMain.thermalSystem.unregister(this);
        }

        @Override
        public void updateThermalLogic(float dt) {
            // 散热塔自身不产热不耗热，纯靠 ThermalSystem 环境换热
        }

        @Override
        public void display(Table table) {
            super.display(table);
            table.row();
            table.left().label(() -> "[cyan]散热塔[]").padTop(4);
            table.row();
            table.left().label(() ->
                "温度: " + String.format("%.1f", thermal.getTemperatureK() - 273.15f) + "°C");
            table.row();
            table.left().label(() ->
                "存储热量: " + String.format("%.0f", thermal.storedHeat) + " J");
            table.row();
            table.left().label(() -> "热容: " + thermal.heatCapacity + " J/K");
            table.row();
            table.left().label(() -> "空气U: " + thermal.airU + " J/(t·格·K)");
            table.row();
            table.left().label(() -> "状态: [blue]散热中[]");
        }
    }
}
```

**关键设计说明**：散热塔的"散热"不是自己写代码抽热，而是靠 ThermalSystem Phase2 里的环境换热——高 `airU`（0.31）+ 接地（`isFloating=false`）+ 地块效率系数（放水上 ×2.2、放岩浆上 ×3.0），自然就把热带到环境里了。`updateThermalLogic` 是空的。

#### 4.6.3 HeatConduit（输热）

```java
package com.thermal.mod.blocks;

import arc.scene.ui.layout.Table;
import com.thermal.mod.ThermalModMain;
import com.thermal.mod.core.ThermalBuilding;
import com.thermal.mod.core.ThermalComponent;
import mindustry.gen.Building;
import mindustry.world.blocks.defense.Wall;

public class HeatConduit extends Wall {

    public HeatConduit(String name) {
        super(name);
        update = true;
        solid = true;
        noUpdateDisabled = true;
        rotate = false;
    }

    public class ConduitBuild extends Building implements ThermalBuilding {

        public ThermalComponent thermal = new ThermalComponent();

        @Override public ThermalComponent getThermal() { return thermal; }
        @Override public Building asBuilding() { return this; }

        @Override
        public void placed() {
            super.placed();
            thermal.heatCapacity = 50f;
            thermal.maxTempK = 800f;
            thermal.minTempK = 200f;          // -73°C，防止过低
            thermal.airU = 0.008f;            // 极低 = 保温好
            thermal.isFloating = true;        // 架空，不接触地面
            thermal.contactResistance = 0.02f; // 小 R = 传热快
            thermal.storedHeat = thermal.heatCapacity * 293.15f;
            thermal.invalidate();

            ThermalModMain.thermalSystem.register(this);
        }

        @Override
        public void onRemoved() {
            super.onRemoved();
            ThermalModMain.thermalSystem.unregister(this);
        }

        @Override
        public void updateThermalLogic(float dt) {
            // 导热管无自身逻辑，纯靠 ThermalSystem 双缓冲传热
        }

        @Override
        public void display(Table table) {
            super.display(table);
            table.row();
            table.left().label(() -> "[orange]导热管[]").padTop(4);
            table.row();
            table.left().label(() ->
                "温度: " + String.format("%.1f", thermal.getTemperatureK() - 273.15f) + "°C");
            table.row();
            table.left().label(() ->
                "存储热量: " + String.format("%.0f", thermal.storedHeat) + " J");
            table.row();
            table.left().label(() -> "热容: " + thermal.heatCapacity + " J/K");
            table.row();
            table.left().label(() -> "空气U: " + thermal.airU + " [gray](保温)");
            table.row();
            table.left().label(() ->
                "状态: " + (thermal.isFloating ? "[gray]架空保温[]" : "[white]接地[]"));
        }
    }
}
```

**关键设计说明**：
- **`isFloating=true`**：架空，ThermalSystem Phase2 直接跳过地面换热，只和空气换。
- **`airU=0.008`（极低）**：保温。手册 §5.5 说"k 与 airU 绑定"——保温管就是低 airU，长距离输送散热极小。
- **`contactResistance=0.02`（小）**：管子之间传热快，整根管线温度迅速拉平，玩家视角就是"一根等温的热棒"。

#### 4.6.4 RefineryFurnace（用热）

```java
package com.thermal.mod.blocks;

import arc.scene.ui.layout.Table;
import com.thermal.mod.ThermalModMain;
import com.thermal.mod.core.ThermalBuilding;
import com.thermal.mod.core.ThermalComponent;
import mindustry.gen.Building;
import mindustry.world.blocks.defense.Wall;

public class RefineryFurnace extends Wall {

    public float operatingMinK = 450f;   // 工作温区下限 (177°C)
    public float ratedHeat = 80f;        // 额定热耗 (J/tick)
    public float idleLoss = 10f;         // 空载损耗 (J/tick)

    public RefineryFurnace(String name) {
        super(name);
        update = true;
        solid = true;
        noUpdateDisabled = true;
        rotate = false;
    }

    public class FurnaceBuild extends Building implements ThermalBuilding {

        public ThermalComponent thermal = new ThermalComponent();

        public enum FurnaceState { PREHEATING, WORKING, IDLING }
        public FurnaceState state = FurnaceState.PREHEATING;

        @Override public ThermalComponent getThermal() { return thermal; }
        @Override public Building asBuilding() { return this; }

        @Override
        public void placed() {
            super.placed();
            thermal.heatCapacity = 1500f;
            thermal.maxTempK = 700f;
            thermal.minTempK = 273f;
            thermal.airU = 0.08f;
            thermal.isFloating = false;
            thermal.contactResistance = 0.05f;
            thermal.storedHeat = thermal.heatCapacity * 293.15f;
            thermal.invalidate();

            ThermalModMain.thermalSystem.register(this);
        }

        @Override
        public void onRemoved() {
            super.onRemoved();
            ThermalModMain.thermalSystem.unregister(this);
        }

        @Override
        public void updateThermalLogic(float dt) {
            float temp = thermal.getTemperatureK();

            // 骨架版：不检查实际输入物品，模拟有输入
            boolean hasInput = true; // TODO: 后续接入物品检测

            if (hasInput && temp >= operatingMinK) {
                // 工作状态：消耗额定热量生产
                state = FurnaceState.WORKING;
                thermal.addHeat(-ratedHeat * dt);
            } else if (hasInput) {
                // 预热中：温度不够，不耗热
                state = FurnaceState.PREHEATING;
            } else {
                // 空载：消耗少量热量保温
                state = FurnaceState.IDLING;
                thermal.addHeat(-idleLoss * dt);
            }
        }

        @Override
        public void display(Table table) {
            super.display(table);
            table.row();
            table.left().label(() -> "[orange]精炼炉[]").padTop(4);
            table.row();
            table.left().label(() ->
                "温度: " + String.format("%.1f", thermal.getTemperatureK() - 273.15f) + "°C" + stateLabel());
            table.row();
            table.left().label(() ->
                "温区要求: >= " + String.format("%.0f", operatingMinK - 273.15f) + "°C");
            table.row();
            table.left().label(() ->
                "热耗: " + (state == FurnaceState.WORKING ? "-" + (int)ratedHeat
                    : state == FurnaceState.IDLING ? "-" + (int)idleLoss : "0") + " J/t");
            table.row();
            table.left().label(() -> "热容: " + thermal.heatCapacity + " J/K");
        }

        private String stateLabel() {
            switch (state) {
                case WORKING: return " [green](工作中)";
                case PREHEATING: return " [yellow](预热中)";
                case IDLING: return " [gray](空载)";
                default: return "";
            }
        }
    }
}
```

**关键设计说明**：
- **operatingMinK 温区**：温度低于 450K（177°C）不生产，只预热。这逼玩家必须把热网烧热到工作温区。
- **空载 idleLoss**：没料时只耗 10 J/t 保温，别让炉子凉透。
- **骨架版**：`hasInput` 写死 `true`，TODO 后续接原版物品检测。本版只演示温度状态机。

---

### 步骤 7：注册——ThermalBlocks / ThermalItems

**目标**：把四个建筑挂到游戏内容系统，出现在建造栏。

```java
package com.thermal.mod.content;

import com.thermal.mod.blocks.CoolingTower;
import com.thermal.mod.blocks.HeatConduit;
import com.thermal.mod.blocks.IndustrialBoiler;
import com.thermal.mod.blocks.RefineryFurnace;

import static mindustry.type.ItemStack.with;
import static mindustry.type.Category.crafting;
import static mindustry.type.Category.defense;

public class ThermalBlocks {

    public static IndustrialBoiler industrialBoiler;
    public static CoolingTower coolingTower;
    public static HeatConduit heatConduit;
    public static RefineryFurnace refineryFurnace;

    public static void load() {
        // 工业锅炉
        industrialBoiler = new IndustrialBoiler("industrial-boiler");
        industrialBoiler.requirements(crafting, with(mindustry.content.Items.copper, 50));
        industrialBoiler.health = 300;

        // 散热塔
        coolingTower = new CoolingTower("cooling-tower");
        coolingTower.requirements(crafting, with(mindustry.content.Items.copper, 30));
        coolingTower.health = 200;

        // 导热管
        heatConduit = new HeatConduit("heat-conduit");
        heatConduit.requirements(defense, with(mindustry.content.Items.copper, 2));
        heatConduit.health = 100;

        // 精炼炉
        refineryFurnace = new RefineryFurnace("refinery-furnace");
        refineryFurnace.requirements(crafting, with(mindustry.content.Items.copper, 40));
        refineryFurnace.health = 250;
    }
}
```

```java
package com.thermal.mod.content;

public class ThermalItems {
    public static void load() {
        // 预留：本轮最小 mod 不注册新物品，建筑全用原版铜砖建造
    }
}
```

**关键 API 讲解**：
- **`Category` 用法**：`mindustry.type.Category` 是建造栏分类枚举（crafting / defense / distribution / ...）。用 `import static mindustry.type.Category.crafting` 静态导入，写 `requirements(crafting, ...)` 直接用。（注意包路径——坑 1 专门讲这个。）
- **`requirements(Category, ItemStack...)`**：声明这个建筑在哪个分类、需要什么材料建造。`ItemStack.with(copper, 50)` 就是"50 铜"。
- **Content 自动注册机制**：`Block` 的构造函数会自动把自己注册进游戏内容系统。我们只要 `new IndustrialBoiler("industrial-boiler")`，游戏就认识这个方块了，不用手动塞列表。
- **`Items.copper`**：原版铜，直接引用 `mindustry.content.Items.copper`，不自定义新物品。

---

## 第 5 章：编译与验证

代码写完不算完，得让游戏真的加载起来。这一章讲怎么编译、怎么 headless 验证、怎么读日志。

### 5.1 编译命令

**方式 A：Gradle（推荐）**

```bash
# 进入项目根目录
cd thermal-mod/

# 编译 + 打 jar（JDK 17）
./gradlew build

# 产物在 build/libs/thermal-mod-0.1.0.jar
ls -la build/libs/
```

**方式 B：手动 javac（没有 Gradle 时兜底）**

```bash
# 假设 classpath 里有 mindustry-core 和 arc-core 的 jar
javac -source 17 -target 17 \
  -cp "mindustry-core-v159.7.jar:arc-core.jar" \
  -d build/classes \
  src/main/java/com/thermal/mod/**/*.java

# 把 mod.json 打进去
jar cf thermal-mod.jar -C build/classes . -C . mod.json
```

### 5.2 headless 加载验证（关键）

光编译过不够——编译过只说明类型对，不说明 mod 真能被游戏加载。Mindustry 提供了 headless 服务端模式，不弹窗直接跑，最适合验证 mod 加载。

验证流水线（本项目实际跑通的）：

```bash
# 1. JDK 17
export JAVA_HOME=~/.jdk17

# 2. 组 classpath：
#    - mod 编译产物 build/classes
#    - 游戏生成的 gen 代码（ECS 注解处理器产物）
#    - server-release.jar（Mindustry 服务端）
#    - arc-core-1.0.jar
#    - backend-headless 的 classes
#    - 其他 extensions

# 3. 把 thermal-mod.jar 放进 运行目录/config/mods/
cp thermal-mod.jar 运行目录/config/mods/

# 4. 启动 headless 服务端，观察日志
java -cp "classpath..." mindustry.server.ServerLauncher
```

### 5.3 成功日志长什么样

加载成功时，控制台会依次打出这四行（本项目实际日志）：

```
[INFO] ThermalMod: 内容加载完成
[INFO] 1 mods loaded
[INFO] ThermalMod: 模组初始化完成
[INFO] Server loaded
```

**怎么读日志判断 mod 加载成功**：

| 日志行 | 含义 | 出问题时 |
|:------|:-----|:--------|
| `ThermalMod: 内容加载完成` | `loadContent()` 跑完，四个方块注册成功 | 没这行 → 主类没找到，检查 mod.json 的 `main` 路径 |
| `1 mods loaded` | 游戏识别到 1 个 mod | 数字是 0 → mod.json 没打进 jar 根目录，或 `java:true` 没写 |
| `ThermalMod: 模组初始化完成` | `init()` 跑完，WorldLoadEvent 和 Trigger.update 都挂上了 | 没这行 → init 里抛异常了，往上翻堆栈 |
| `Server loaded` | 服务端启动完成，没有 fatal error | 卡住/退出 → mod 类 NoClassDefFoundError 或版本不兼容 |

### 5.4 产物

已编译好的 `thermal-mod.jar`（22 KB，jar 内 26 项）直接丢进游戏 `config/mods/` 文件夹即可加载。

---

## 第 6 章：踩坑实录（最有价值部分）

> 这一章是"真实开发过程"最值钱的部分。三个坑，每个都讲：错在哪 → 报什么错 → 怎么查源码修正 → 教训。萌新抄项目时，这三个坑你大概率会原样踩一遍。

### 坑 1：Category 包路径错误

**错在哪**

一开始凭直觉，觉得"Category 是建筑的分类，肯定在 world 包下"，于是写：

```java
import mindustry.world.meta.Category;   // ← 凭感觉写的，其实不存在
```

**报什么错**

```
error: cannot find symbol
    import mindustry.world.meta.Category;
                                  ^
  符号:   class Category
  位置: 程序包 mindustry.world.meta 中
```

编译直接挂在第一行。IDE 也不补全——因为这个类根本不存在。

**怎么查源码修正**

别瞎猜，直接去 Mindustry 源码里 grep 这个枚举到底在哪：

```bash
# 在 Mindustry 源码根目录
grep -rn "public enum Category" core/src/
# 或者
grep -rn "enum Category" core/src/mindustry/
```

定位结果：

```
core/src/mindustry/type/Category.java:4: public enum Category {
```

**Category 实际在 `mindustry.type` 包下，不在 `mindustry.world.meta`。**

修正（ThermalBlocks.java:10-11）：

```java
// 错的：
// import mindustry.world.meta.Category;

// 对的：静态导入具体枚举值
import static mindustry.type.Category.crafting;
import static mindustry.type.Category.defense;
```

**教训**：Mindustry 的包分工有规律——
- `mindustry.type.*`：**枚举和类型定义**（Category、ItemFlak、ItemStack 静态方法 with 等）。
- `mindustry.world.meta.*`：**建筑元数据**（Stat 枚举、BlockMetadata、BuildVisibility 这种"描述建筑参数"的东西）。

枚举类（能 `import static` 直接用值的）几乎都在 `type` 包。下次再遇到"凭感觉 import 不到"，先 `grep "enum X"` 定位，别猜。

---

### 坑 2：FloatArray 不存在

**错在哪**

设计手册 §4.1 写"按 tile 存一张 float 表"。直觉用 Java 标准库的思路，想要一个"float 版的 ArrayList"，于是写：

```java
import arc.struct.FloatArray;          // ← 以为有这个类
private FloatSeq groundTemps;
...
groundTemps = new FloatArray(w * h);    // ← 编译不过
```

**报什么错**

```
error: cannot find symbol
    import arc.struct.FloatArray;
                       ^
  符号:   类 FloatArray
  位置: 程序包 arc.struct
```

**怎么查源码修正**

去 Arc 引擎仓库看 `arc/struct/` 目录下到底有哪些集合类：

```bash
ls arc/struct/ | grep -i float
# 输出：
#   FloatQueue.java
#   FloatSeq.java
#   ...
```

Arc 里**没有 `FloatArray`，有 `FloatSeq`**。

修正（EnvironmentTemperature.java:3）：

```java
// 错的：import arc.struct.FloatArray;
import arc.struct.FloatSeq;
...
private static FloatSeq groundTemps;
...
groundTemps = new FloatSeq(w * h);
groundTemps.size = w * h;
```

**教训**：Arc 引擎的集合命名和 Java 标准库**不一样**，别用 Java 命名习惯套：

| 你以为的（Java 习惯） | Arc 里实际叫 |
|:--------------------|:------------|
| ArrayList | `Seq<T>` |
| float[] 封装 / FloatBuffer | `FloatSeq` |
| HashMap<K,V> | `ObjectMap<K,V>` |
| HashMap<K,float>（值不装箱） | `ObjectFloatMap<K>` |
| ArrayDeque | `Queue<T>` |

规律：Arc 用 `Seq`（sequence）当列表后缀，值是原始类型的用 `XxxSeq` / `ObjectXxxMap` 避免装箱。写之前先 `ls arc/struct/` 看一眼真实类名。

---

### 坑 3：私有 tempDirty 无法访问

**错在哪**

这是个封装问题，不是 API 问题。设计手册里 ThermalComponent 的 `tempDirty` 是 private 缓存标志。写建筑类的时候，placed() 里直接改了 `storedHeat`（为了把初始温度设成环境温度），直觉想手动把缓存失效：

```java
// 某建筑的 placed() 里
thermal.storedHeat = thermal.heatCapacity * 293.15f;
thermal.tempDirty = true;     // ← 想当然，直接碰私有字段
```

**报什么错**

```
error: tempDirty has private access in ThermalComponent
    thermal.tempDirty = true;
           ^
```

编译器直接拒绝——`tempDirty` 在 ThermalComponent 里声明为 `private boolean tempDirty = true;`，外部类够不着。

**怎么查源码修正**

先回头看 ThermalComponent 的字段声明，确认可见性：

```java
// ThermalComponent.java:79-80
private float cachedTempK;
private boolean tempDirty = true;
```

确实是 private。有两个修法：
1. 把 `tempDirty` 改成 public（**不行**，破坏封装，外部随便改缓存标志会出 bug）；
2. **在 ThermalComponent 里加一个公开方法**，让外部"请求"失效缓存。

选 2。修正（ThermalComponent.java:108-111）：

```java
/** 标记温度缓存失效（外部直接修改 storedHeat 后调用）。 */
public void invalidate() {
    tempDirty = true;
}
```

然后四个建筑的 placed() 里统一改成调用：

```java
thermal.storedHeat = thermal.heatCapacity * 293.15f;
thermal.invalidate();     // ← 公开方法，不碰私有字段
```

**教训**：这是个封装原则问题——
- **组件内部状态（cachedTempK / tempDirty）不暴露**。外部不该知道"我内部用了缓存"这个实现细节。
- 正确做法是**提供语义化的公开方法**：`invalidate()`（失效缓存）、`addHeat(q)`（加热量顺便失效）、`getTemperatureK()`（读温度）。外部只调方法，不碰字段。
- 如果你发现自己在别的类里要直接改组件的私有字段，八成是组件漏了一个公开方法——去组件里加方法，而不是把字段改成 public。

---

## 第 7 章：已知限制与迭代方向

### 7.1 双缓冲的简化（当前实现的 trade-off）

当前 ThermalSystem 的双缓冲是"全局两阶段"简化版。设计手册 §3 里的高斯-赛德尔迭代、逐 tile 热扩散，本版没做。换来的是：
- 每 tick 只遍历两遍建筑列表，O(N)；
- 不需要维护连接池，靠 proximity 现成邻居数组；
- 代价是：建筑内部温度会有 1 tick 的滞后，大温差下首 tick 可能略微偏离真实物理。

对一个"能跑、能抄"的最小 mod 来说，这个 trade-off 划算。

### 7.2 v160 的官方热力系统桥接（进阶预告）

v160 新增了 `HeatCrafter` / `HeatConsumer` 两个原版方块接口（见门户"v159.7 → v160 变更速查"卡片）。我们这套自建的 ThermalComponent 热力系统和官方的热力系统目前是**各跑各的**。后续可以做一层桥接：

- 让 `RefineryFurnace` 同时实现官方 `HeatConsumer`，把我们的 `storedHeat` 对接到原版热量槽；
- 这样原版产热建筑（HeatCrafter）和我们的散热塔能在同一个热网里交互。

这是 v0.2 的主要工作。

### 7.3 温差发电机 / 热泵的完整实现

本版这两类只留了骨架。完整实现需要：
- **温差发电机**：`dT = temp - min(tAir, tGround)`，按 `powerCurve` 查表把部分热流转成电力，废热从冷端排出。需要接原版 `PowerGraph`。
- **档位热泵**：冷端 + 热端两个独立 ThermalComponent，`coldSide.addHeat(-Q); hotSide.addHeat(Q+W)`，耗电 `W = Q/COP`。需要双端布局和过热保护。

### 7.4 物品级热力（传送带物品携带热量）

现在只有建筑有 ThermalComponent。要做"烧红的铁锭在传送带上慢慢降温"，得给物品也挂组件——但物品是 `static` 单例，每个物品的温度按传送带碎片实例存，工程量大，放 v0.3。

### 7.5 性能优化

当前 `Seq<ThermalBuilding>` 每次 register/unregister 是 O(N) 查找。建筑上百个时没事，上千个时考虑：
- 用 `IntSet` 或分块（chunk）维护建筑；
- 热流计算只在"有温差邻居"时触发，温度拉平后跳过；
- 环境换热的 `tile.floor().name` 字符串查表可缓存成枚举。

---

## 第 8 章：附录

### 8.1 全部源码清单

| 类 | 职责 | 关键方法 | 行数 |
|:---|:-----|:--------|:----|
| `ThermalModMain` | mod 入口，生命周期 + 事件驱动 | `loadContent()` / `init()` | ThermalModMain.java:22-51 |
| `core/ThermalComponent` | 热节点数据结构，惰性温度 | `getTemperatureK()` / `addHeat()` / `invalidate()` | ThermalComponent.java:18-133 |
| `core/ThermalSystem` | 双缓冲主循环 | `update(dt)` / `register()` / `unregister()` | ThermalSystem.java:21-135 |
| `core/EnvironmentTemperature` | 环境温度查表 | `precompute()` / `getGroundTemp()` / `getAirTemp()` | EnvironmentTemperature.java:25-119 |
| `core/ThermalBuilding` | 热力建筑接口 | `getThermal()` / `updateThermalLogic()` / `asBuilding()` | ThermalBuilding.java:11-30 |
| `blocks/IndustrialBoiler` | 产热建筑，停机+回差重启 | `updateThermalLogic()` / `display()` | IndustrialBoiler.java:23-122 |
| `blocks/CoolingTower` | 散热建筑，高 airU + 地面换热 | `placed()` / `display()` | CoolingTower.java:21-107 |
| `blocks/HeatConduit` | 输热管道，架空保温 | `placed()` / `display()` | HeatConduit.java:22-100 |
| `blocks/RefineryFurnace` | 用热建筑，温区+空载 | `updateThermalLogic()` / `display()` | RefineryFurnace.java:23-138 |
| `content/ThermalBlocks` | 方块注册 | `load()` | ThermalBlocks.java:19-54 |
| `content/ThermalItems` | 物品注册（预留空） | `load()` | ThermalItems.java:9-14 |

### 8.2 源码地图（Mindustry 核心 API 位置）

| 我们用到的 API | Mindustry 源码位置（v160 工作树） |
|:--------------|:-------------------------------|
| `Mod` 基类 | `core/src/mindustry/mod/Mod.java` |
| `Building.update()` 每 tick | `core/src/mindustry/entities/comp/BuildingComp.java`（update 段） |
| `Building.proximity` 邻居数组 | `BuildingComp.java` 字段声明 `Seq<Building> proximity` |
| `Building.display(Table)` 面板钩子 | `BuildingComp.java` 的 `display(Table)` 方法 |
| `Building.placed()` / `onRemoved()` | `BuildingComp.java` 的放置/拆除回调 |
| `Block.requirements(Category, ItemStack...)` | `core/src/mindustry/world/Block.java` |
| `Category` 枚举 | `core/src/mindustry/type/Category.java` |
| `ItemStack.with(...)` | `core/src/mindustry/type/ItemStack.java` |
| `Floor`（无温度字段，需自建） | `core/src/mindustry/world/blocks/environment/Floor.java` |
| `WorldLoadEvent` | `core/src/mindustry/game/EventType.java` |
| `Events.run(Trigger.update, ...)` | `arc.Events` + `EventType.Trigger.update` |
| `Seq<T>` / `FloatSeq` / `ObjectFloatMap` | `arc.struct.*`（Arc 引擎） |

### 8.3 引用

- **设计手册**：《热力学系统设计手册》v1.0（八节：哲学/数据结构/主循环/环境交互/建筑规约/玩家流程/性能安全/HJSON 速查）。本教程第 1~3 章、第 4 章每步的"为什么这么写"都源自它。
- **可行性报告**：《Feasibility-Report》（10 机制判定：7 原生支持 ✅ / 3 需适配 ⚠️ / 0 难点 🔴，每项标注 `类:行号`）。本教程第 3 章直接采用其判定表与源码依据。
- **已编译产物**：`thermal-mod.jar`（22 KB），headless 加载验证四行日志齐全。

---

> **收尾**：到这里，你手里有 11 个 Java 文件、1 个 build.gradle、1 个 mod.json、1 个能被 `Server loaded` 认出来的 22 KB jar。下一步——把它丢进游戏 `config/mods/`，进图，放一个锅炉、接几根导热管、连一个散热塔，点选建筑看面板温度数字有没有动。动了，你就真的把一个 mod 从图纸抄成了能跑的东西。

---

## 第 9 章：运行调试实录（headless server 实测）

> 第 5 章证明了"游戏认得这个 mod"（4 行加载日志），但**加载成功 ≠ 热传导真的在跑**。这一章把 mod 挂到真实 headless 服务器上，跑一条完整测试链，用真实 tick 下的温度曲线数据，证明双缓冲热传导在游戏世界里真的工作——以及为了做到这一点，我们踩穿了 4 个运行时 bug。
>
> 源码行号基于当前 v160 工作树；本 mod 按 v159.7 API 编写，这些运行时 API 在两版本间一致。

### 9.1 运行验证目标与测试链

**验证目标**：在真实游戏 tick 下，确认从锅炉产热 → 导热管传导 → 精炼炉受热升温的完整热链路是否闭合，温度梯度是否按设计出现。

**测试链**：

```
[工业锅炉] [导热管] [导热管] [精炼炉]
   产热       保温      保温     要 177°C (450K) 才工作
```

四个建筑**紧密相邻、一字排开、全是 1×1**，靠 `proximity` 邻居数组建立热传导。

**运行环境**：
- headless server（`mindustry.server.ServerLauncher`），无 GUI；
- 用 stdin 管道注入 `host` 命令触发真实地图加载（见 Bug 1）；
- `WorldLoadEvent` 里自动放置测试链，省去手动操作；
- v160 工作树 classpath + 本 mod；
- 运行目录 `/tmp/thermal-run/`，完整温度曲线落在 `server.log`。

### 9.2 真实温度曲线（来自 server.log）

调试器每 60 tick 打一次温度。下面是测试链在 600 / 1800 / 3600 / 5400 / 6000 tick 的真实读数：

| tick | 锅炉 K | 导管1 K | 导管2 K | 精炼炉 K |
|:----:|------:|------:|------:|------:|
| 600  | 308.9 | 305.9 | 304.3 | 301.5 |
| 1800 | 340.0 | 336.8 | 335.0 | 332.0 |
| 3600 | 381.5 | 378.1 | 376.2 | 373.1 |
| 5400 | 417.6 | 414.1 | 412.1 | 408.9 |
| 6000 | 428.5 | 425.0 | 423.0 | 419.8 |

**从这张表读出四个结论**：

1. **热传导链路完整。** 每个时刻都是 `锅炉 > 导管1 > 导管2 > 精炼炉` 的单调递减梯度，相邻温差稳定在 ~3K。这正是手册 §4.5 期望的形态——小接触热阻把整根管线迅速拉平，只在热源和热负载两端留落差。**热量真的顺着管子在传。**
2. **proximity 连接正确。** 调试输出显示锅炉 `p=1`、两根导管 `p=2`、精炼炉 `p=1`——正好是一字链两端各 1 邻居、中间各 2 邻居。邻居关系没断。
3. **6000 tick 无异常。** 没有 NaN、没有温度负值、没有来回振荡。双缓冲 + 防过冲保护在真实长时间运行下稳得住。
4. **精炼炉还没到工作温度。** 6000 tick 时精炼炉 ~420K，离 `operatingMinK=450K` 还差 30K。这不是 bug，反而**印证了"预热过慢"的设计问题**——锅炉 100 J/tick 的产热要同时加热 4 个节点，热容量不小，按这条曲线还得再跑几千 tick 才到工作温区。第 7 章说的"后续优化热容配比"就是冲这个来的。

> 结论：**双缓冲热传导不是纸面推演，在真实服务器 tick 下、四个真实建筑之间，温度真的按设计在流动、在配平。** 这一步过了，这个 mod 才算"真的能跑"。

### 9.3 四个真实 bug 逐个展开（最有价值部分）

下面四个 bug 是把 mod 从"编译过、加载过"推进到"真的跑出温度曲线"过程中，挨个踩穿的。每个按 **错误现象 → 排查过程 → 源码证据 → 修复 → 教训** 讲。

#### Bug 1：ServerLauncher.init() 不加载世界

**错误现象**

headless 服务器启动后，控制台只打出 `Server loaded`，但我们注册的 `WorldLoadEvent` 监听（环境预计算 + 自动放置测试链）**一次都没触发**。`EnvironmentTemperature.precompute()` 没跑，测试链也没放。

**排查过程**

一开始怀疑 mod 的事件注册写错了，反复检查 `Events.on(WorldLoadEvent.class, ...)` 语法没问题。然后翻 headless 启动入口，发现问题根本不在我们这边——服务器**压根没加载任何地图**，自然不会 fire `WorldLoadEvent`。

**源码证据**

`ServerLauncher.java` 的 `init()` 只 fire `ServerLoadEvent`（服务端加载完成事件），**不加载地图**。headless 无参启动只把服务端进程跑起来，它在等玩家连接或控制台命令，自己不会凭空开一张图。

**修复**

用 stdin 管道，在启动后注入 `host` 命令强制加载一张地图：

```bash
# 启动后等 12 秒让服务端就绪，发 host 建图，再挂 95 秒跑测试链
(sleep 12; echo "host"; sleep 95) | java -Dthermal.debug=true -cp "..." mindustry.server.ServerLauncher
```

`host` 命令一进控制台，游戏正常加载随机地图 → fire `WorldLoadEvent` → 我们的预计算和测试链放置全部触发。

**教训**

headless 调试**必须走命令触发世界加载**，不能假设"启动即加载"。`ServerLoadEvent` ≠ `WorldLoadEvent`，前者只是服务端进程就绪，后者才是真有了可玩的世界。

#### Bug 2：tile.setBlock() 不调用 placed()

**错误现象**

测试链放上去了，但建筑的热力参数（heatCapacity、airU 等）全是默认 0，也没注册进 ThermalSystem——`update` 里根本遍历不到它们。

**排查过程**

对比"玩家手动放一个锅炉"和"代码里 `tile.setBlock(...)` 放锅炉"两条路径：玩家放的，一切正常；代码放的，参数没初始化。怀疑 `placed()` 没被调。

**源码证据**

`BuildingComp.java:1361` 的 `placed()` 方法只处理电力节点等本建筑初始化逻辑，**不会被 `setBlock()` 自动调用**——玩家正常放置时，是游戏在放置流程里显式调的 `placed()`；你直接 `tile.setBlock()`，跳过了整条玩家放置调用链，`placed()` 自然不跑。

**修复**

代码放置后，手动补调生命周期：

```java
Vars.world.tile(x, y).setBlock(block, null, 0);
Building build = Vars.world.tile(x, y).build;
if (build != null) {
    build.placed();   // ← 手动补 placed()，热力参数初始化 + register
}
```

**教训**

必须区分**"玩家放置流程"**（游戏自动调 placed()）和**"代码直接放置"**（`setBlock()` 跳过一切回调）。后者你要自己把生命周期的关键调用补上——不然建筑就是个空壳。

#### Bug 3：setBlock() 不填充 proximity

**错误现象**

补了 `placed()` 之后，参数有了、也注册了，但热传导还是不动。打调试输出一看：`building.proximity.size == 0`——邻居列表是空的，Phase 1 遍历邻居数组啥也遍历不到。

**排查过程**

先怀疑测试链放错位了，坐标一个个核对，确实是相邻的。然后去查 proximity 是怎么来的。

**源码证据**

`BuildingComp.java:1886` 的 `updateProximity()`：基于 `Edges.getEdges(...)` 查世界里该位置周围的建筑，**双向添加**——把自己加进邻居的 proximity，也把邻居加进自己的 proximity。这个填充动作由 `onProximityUpdate` 触发，**代码 `setBlock()` 放置时不自动跑**。

**修复**

放置后手动调 `updateProximity()`，而且**必须等全部建筑放完再统一刷一次**——逐个放的时候，下一个邻居还不存在，你现在刷只能刷出半条链：

```java
// 先把四个全 setBlock + placed()
placeAllChain();
// 再统一刷新一次，让彼此互相进 proximity
for (Building b : allBuilt) b.updateProximity();
```

**教训**

`proximity` 不是自动维护的，代码放置后必须手动 `updateProximity()`；而且**注意刷新时机**——要在所有相关建筑就位后统一刷，不能放一个刷一个。

#### Bug 4：建筑默认 size=1（最终根因，最有价值）

**错误现象**

前三个 bug 修完，锅炉烧到了 553K，**两根导管却死死停在 293.1K 纹丝不动**——热传导像根本没生效。这是整个调试过程里最迷惑的一个：代码看着都对、邻居看着也放了，温度就是不流。

**排查过程**

1. **先打 proximity 明细**——发现锅炉 `p=0`（不是预期的 1）。邻居根本没连上。
2. 怀疑 `Edges.getEdges` 算错了，实测 `Edges.getEdges(2)` 明明包含 `(2,0)` 这个方向，但按这个方向去 `world.build` 查就是查不到。
3. 回头查建筑尺寸字段——发现所有建筑**都没设 `size`，默认 1×1**。
4. 再看调试器的放置代码：调试器最初按"2×2 建筑"的概念布局，用 `nearby(2,0)` 的偏移去放导管。可锅炉是 1×1，它的邻居范围根本不覆盖"隔一格"的位置——**导管和锅炉之间空了一格地，物理上就不相邻，proximity 当然查不到**。

**根因**

不是机制 bug，是**布局空隙**。我们的 `IndustrialBoiler` 等都继承 `Wall`，**Wall 子类不自动变成 2×2**，默认就是 1×1。调试器却按 2×2 的间距概念放，导致链上建筑隔一格空地，永不相邻。

**修复**

布局改成真正紧邻——导管放在 `(1,0)/(2,0)/(3,0)`（相对于锅炉），四个格子紧挨。放好后传导立即生效，温度梯度立刻出现（就是 9.2 那张表）。

**教训**

这是这次调试最值钱的一课：

- **"机制不工作"先查布局和邻居，再查代码。** 我们一度怀疑双缓冲逻辑写错了，折腾半天，最后发现是坐标差了一格。
- **先打印 proximity 明细再下机制结论。** 调试输出里那行 `p=N{邻居列表}`（N 是邻居数）是定位邻居类 bug 的利器——它直接告诉你"这个建筑到底认了几个邻居、是谁"。看到 `p=0` 就该立刻往"布局/相邻关系"方向查，而不是往"算法错了"方向钻。

---

### 9.4 ThermalDebug.java 调试器（完整可复制代码）

上面所有调试输出，都来自一个挂在 mod 里的调试工具类 `ThermalDebug`。它用 JVM 系统属性当开关，**默认关闭，不影响正常玩家**；打开后自动放测试链、周期打印状态。完整代码：

```java
package com.thermal.mod.core;

import arc.struct.Seq;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Building;
import com.thermal.mod.ThermalModMain;

/**
 * 运行时调试输出：进图自动放置测试链 + 周期性打印温度/proximity 明细。
 *
 * <p>启用方式：JVM 加 -Dthermal.debug=true。
 * 不传该属性时 init() 直接 return，对正常玩法零影响。</p>
 */
public class ThermalDebug {

    private static int tick = 0;

    public static void init() {
        if (!Boolean.getBoolean("thermal.debug")) return;

        // 进图自动放一条测试链：锅炉 - 导管A - 导管B - 精炼炉
        Events.on(EventType.WorldLoadEvent.class, e -> {
            placeTestChain();
            Log.info("ThermalDebug: 测试链已放置（锅炉-导管A-导管B-精炼炉）");
        });

        // 每 60 tick 打印一次温度与 proximity 明细
        Events.run(EventType.Trigger.update, () -> {
            if (Vars.net.client()) return;
            tick++;
            if (tick % 60 == 0) dump();
        });
    }

    private static void placeTestChain() {
        int cx = Vars.world.width() / 2;
        int cy = Vars.world.height() / 2;

        // 四个 1x1 建筑，紧邻一字排开（Bug 4：必须紧邻，不能隔格）
        place("thermal-mod-industrial-boiler", cx,     cy);
        place("thermal-mod-heat-conduit",      cx + 1, cy);
        place("thermal-mod-heat-conduit",      cx + 2, cy);
        place("thermal-mod-refinery-furnace",  cx + 3, cy);

        // Bug 3：全部放完后，统一刷新一次 proximity
        Vars.world.tiles.eachTile(t -> {
            if (t.build != null) t.build.updateProximity();
        });
    }

    private static void place(String blockName, int x, int y) {
        var block = Vars.content.block(blockName);
        var tile = Vars.world.tile(x, y);
        tile.setBlock(block, null, 0);

        Building build = tile.build;
        if (build != null) {
            build.placed();   // Bug 2：手动补 placed()，初始化热力参数 + register
        }
    }

    /** 打印每个活跃热力建筑的温度 / 热量 / 邻居数。 */
    private static void dump() {
        Seq<ThermalBuilding> list = ThermalModMain.thermalSystem.getBuildings();
        Log.info("=== ThermalDebug t=" + tick + " 活跃建筑=" + list.size + " ===");
        for (ThermalBuilding b : list) {
            var node = b.getThermal();
            var me = b.asBuilding();
            Log.info("  @(" + me.tileX() + "," + me.tileY() + ") "
                + me.block.name
                + " T=" + String.format("%.1f", node.getTemperatureK()) + "K"
                + " Q=" + String.format("%.0f", node.storedHeat) + "J"
                + " p=" + me.proximity.size + " 邻居");
        }
    }
}
```

配套两处小改动：

**主类 `init()` 末尾挂一行**（开关关闭时这行内部直接 return）：

```java
@Override
public void init() {
    thermalSystem = new ThermalSystem();
    // ... 原有的 WorldLoadEvent / Trigger.update 注册 ...
    ThermalDebug.init();   // ← 调试器，-Dthermal.debug=true 才真正干活
    Log.info("ThermalMod: 模组初始化完成");
}
```

**ThermalSystem 加只读遍历接口**（调试器要遍历所有建筑打印）：

```java
/** 调试用：返回当前活跃热力建筑列表（只读，勿改）。 */
public Seq<ThermalBuilding> getBuildings() {
    return buildings;
}
```

**启用方式**：

```bash
# 正常玩家：什么都不加，调试器完全不激活
java -jar thermal-mod.jar

# 调试：加系统属性
java -Dthermal.debug=true -cp "..." mindustry.server.ServerLauncher
```

> 正式 jar（含调试器版本）已同步编译为 `thermal-mod.jar`（约 25 KB）。因为开关默认关闭，玩家正常加载时调试代码一段都不跑，零影响。

### 9.5 调试方法论（Mindustry Java mod headless 通用套路）

把这次调试沉淀成一套可复用的流程，以后写任何需要"每 tick 模拟"的 mod 都套得上：

1. **headless server + stdin 管道注入 `host`**：别指望无参启动自动建图，用 `(sleep N; echo "host"; sleep M) | java ...` 强制触发 `WorldLoadEvent`。
2. **`WorldLoadEvent` 里自动放测试建筑**：不要手动在游戏里摆，代码放才能复现、能写进日志。
3. **代码放置必须补生命周期**：`setBlock()` 后手动 `placed()`；全部放完后统一 `updateProximity()`。
4. **周期打印关键状态**：每 N tick 打一次温度 / proximity / 效率，时间序列比"看一眼"靠谱得多。
5. **先打 proximity 明细再下机制结论**：看到 `p=0` 先查布局和相邻关系，别一上来怀疑算法错了。
6. **区分"玩家放置"和"代码放置"**：前者游戏自动补回调，后者你得手动补。
7. **用 `System.getProperty` 开关调试代码**：`Boolean.getBoolean("thermal.debug")` 一行开关，正式发布默认关闭，不影响玩家。

---

## 收尾

> 8 章正文 + 这一章运行实录，构成了一个闭环：从设计手册（第 1 章）→ 需求拆解（第 2 章）→ 可行性（第 3 章）→ 分步实现（第 4 章）→ 编译加载（第 5 章）→ API 踩坑（第 6 章）→ 迭代方向（第 7 章）→ 源码地图（第 8 章）→ **真实跑通的温度曲线（第 9 章）**。代码是对的，日志是真的，热量在真的流。抄吧。



---

## 第 10 章：实战迭代（v0.2）

> 前 9 章搭好了骨架、跑通了温度曲线，但有三个问题必须在实战中解决：
> **预热太慢**（锅炉 6000 tick 才到 420K）、**精炼炉还不是官方 HeatCrafter**、**没有温度条 UI**。
> 本章记录三个迭代项的完整修改、headless 真跑验证数据和桥接设计。

---

### 10.1 迭代 1：预热慢优化

#### 问题根因

旧参数下锅炉升温速率：

- `ratedPower = 100 J/t`（IndustrialBoiler.java:26）
- `heatCapacity = 2000 J/K`（IndustrialBoiler.java:58）
- 净升温率 ≈ 100/2000 = 0.05 K/tick，但扣掉空气散热（airU=0.05）后实测仅 **0.023 K/tick**
- 精炼炉从 293K 到 450K 工作点需要 **t=9420 tick**

#### 修改方案

选择「提高功率 + 降低热容」双管齐下：

| 参数 | 旧值 | 新值 | 理由 |
|------|------|------|------|
| `ratedPower` | 100 J/t | **400 J/t** | 4 倍产热，净升温率 ~0.4 K/tick |
| `heatCapacity` | 2000 J/K | **1000 J/K** | 减半热容，相同热量下温升翻倍 |

两处改动都在 `IndustrialBoiler.java`：
- 字段声明 `ratedPower = 400f`（:26）
- `BoilerBuild.placed()` 中 `thermal.heatCapacity = 1000f`（:58）

精炼炉参数保持不变（`heatCapacity=1500`, `operatingMinK=450K`），工作点行为不变。

#### 验证数据（headless 真跑）

测试链：锅炉 → 导管 → 导管 → 精炼炉（全部 1x1，紧邻）。每 60 tick 采样：

| tick | 锅炉 (K) | 导管1 (K) | 导管2 (K) | 精炼炉 (K) |
|------|----------|-----------|-----------|------------|
| 300  | 305.0    | 299.9     | 297.5     | 293.7      |
| 480  | 347.5    | 332.6     | 324.5     | 310.8      |
| 660  | 377.1    | 360.4     | 351.2     | 335.7      |
| 900  | 412.7    | 395.6     | 386.2     | 370.3      |
| 1140 | 446.8    | 429.7     | 420.3     | 404.3      |
| 1440 | 488.3    | 471.2     | 461.7     | 445.7      |
| **1500** | **496.3** | **478.8** | **469.0** | **452.4** |

**精炼炉到达 450K 的 tick 数：≈ 1470**（t=1440 时 445.7K，t=1500 时 452.4K，线性插值 ~1470）。

#### 新旧对比

| 指标 | 旧版 (v0.1) | 新版 (v0.2) | 改善 |
|------|------------|------------|------|
| 精炼炉达 450K tick | 9420 | **~1470** | **6.4x 加速** |
| 锅炉 6000 tick 温度 | ~420K | ~600K+（已过 450K 工作点） | 3x+ |
| 温度梯度 | 锅炉>导管>精炼炉 ✓ | 锅炉>导管>精炼炉 ✓ | 保持单调递减 |

约束验证：
- ✅ 温度梯度稳定（锅炉 496.3 > 导管1 478.8 > 导管2 469.0 > 精炼炉 452.4）
- ✅ 精炼炉到达 450K 后 state 从 PREHEATING 切换为 WORKING
- ✅ 超温停机逻辑：锅炉到 800K 时 isShutdown=true，heatBlock.heat() 归零

---

### 10.2 迭代 2：正式集成 v160 HeatCrafter

#### 改动概要

精炼炉 `RefineryFurnace` 从 `extends Wall` 改为 `extends HeatCrafter`
（`core/src/mindustry/world/blocks/production/HeatCrafter.java:12`）。

Build 类从 `extends Building` 改为 `extends HeatCrafterBuild`
（`HeatCrafter.java:43`），天然实现 `HeatConsumer` 接口
（`HeatConsumer.java:3-6`：`float[] sideHeat()` / `float heatRequirement()`）。

#### 桥接设计

```
┌─────────────┐     calculateHeat()      ┌──────────────────┐
│  HeatBlock   │ ──────────────────────→ │  HeatCrafterBuild │
│  (锅炉)      │   BuildingComp.java:412  │  .heat = 15.0     │
│  heat()=15   │   扫描 proximity          │  sideHeat[4]      │
└─────────────┘                           └────────┬─────────┘
                                                   │ super.updateTile()
                                                   │  GenericCrafter 生产逻辑
                                                   │  shouldConsume() (:62)
                                                   ▼
┌─────────────┐    addHeat(heat*50)        ┌──────────────────┐
│ ThermalComp  │ ←─────────────────────── │  桥接层 (新增)     │
│  storedHeat  │   热力学系统升温             │  updateTile() 尾部 │
│  getTempK()  │                            └──────────────────┘
└─────────────┘
```

**关键代码（RefineryFurnace.java）：**

```java
@Override
public void updateTile() {
    // 1. 官方逻辑：heat = calculateHeat(sideHeat) 扫描邻居 HeatBlock
    //    然后调 GenericCrafterBuild.updateTile() 做生产
    super.updateTile();

    // 2. 桥接：官方热网 heat → 热力学系统
    //    1 官方单位 = 50 J（heatToJoule = 50f）
    if (heat > 0f) {
        thermal.addHeat(heat * heatToJoule * delta());
    }
}
```

**效率缩放联动（覆写 efficiencyScale()，HeatCrafter.java:88）：**

```java
@Override
public float efficiencyScale() {
    float officialScale = super.efficiencyScale();
    // 热力学温度比例：0 (293K) ~ 1 (450K)
    float thermalFrac = Math.max(0f, Math.min(1f,
        (thermal.getTemperatureK() - 293.15f) / (operatingMinK - 293.15f)));
    // 温度太低时最低保留 10% 效率，达工作点后使用官方缩放
    return officialScale * (0.1f + 0.9f * thermalFrac);
}
```

**锅炉侧实现 HeatBlock 接口（IndustrialBoiler.java）：**

```java
public class BoilerBuild extends Building implements ThermalBuilding, HeatBlock {
    @Override
    public float heat() {
        return isShutdown ? 0f : officialHeatOutput; // 15f
    }
    @Override
    public float heatFrac() {
        return officialHeatOutput > 0f ? heat() / officialHeatOutput : 0f;
    }
}
```

#### 验证日志（headless 真跑）

在精炼炉正上方放置第二个锅炉（直接作为官方 HeatBlock 邻居），日志关键行：

```
[debug] t=300 活跃热力建筑=5: [...精炼炉@131,128 312.7K p=2]
  {officialHeat=15.00 req=10.0 shouldConsume=true effScale=0.32 state=PREHEATING}
ThermalMod[验证] 迭代2桥接生效! t=300 精炼炉官方heat=15.0 shouldConsume=true 热力学温度=312.7K
```

效率缩放随热力学温度联动：

| tick | 官方 heat | req | shouldConsume | effScale | 热力学温度 | 状态 |
|------|-----------|-----|---------------|----------|-----------|------|
| 300  | 15.00     | 10  | **true**      | 0.32     | 312.7K    | PREHEATING |
| 480  | 15.00     | 10  | **true**      | 0.99     | 390.5K    | PREHEATING |
| 660  | 15.00     | 10  | **true**      | 1.50     | 464.0K    | **WORKING** |
| 1620 | **0.00**  | 10  | **false**     | 0.00     | 795.2K    | 停机后回降 |

关键验证点：
- ✅ `officialHeat=15.00 > heatRequirement=10.0` → `shouldConsume=true`（HeatCrafter.java:62）
- ✅ `effScale` 从 0.32 随热力学温度上升到 1.50（efficiencyScale 覆写生效）
- ✅ 锅炉超温停机时 `heatBlock.heat=0.00` → 精炼炉 `officialHeat=0.00` → `shouldConsume=false`（回差重启逻辑联动）

---

### 10.3 迭代 3：温度状态条

#### 实现方式

每个建筑的 Block 类覆写 `setBars()`（Block.java:677 `addBar(String, Func<T, Bar>)`），
使用 `Bar(Prov<CharSequence>, Prov<Color>, Floatp)` 构造函数（Bar.java:31）。

**锅炉温度条（IndustrialBoiler.java:46-60）：**

```java
@Override
public void setBars() {
    super.setBars();
    addBar("temperature", (BoilerBuild entity) -> new Bar(
        () -> "温度 " + Strings.fixed(entity.thermal.getTemperatureK() - 273.15f, 1) + "°C",
        () -> {
            float frac = entity.thermal.getTemperatureK() / entity.thermal.maxTempK;
            if (frac > 0.9f) return Pal.health;      // 超温红色
            if (frac > 0.75f) return Pal.lightOrange; // 高温橙色
            return Pal.heal;                          // 正常绿色
        },
        () -> Math.max(0f, Math.min(1f,
            (entity.thermal.getTemperatureK() - entity.thermal.minTempK)
            / (entity.thermal.maxTempK - entity.thermal.minTempK)))
    ));
}
```

导热管和精炼炉使用相同模式。精炼炉的 `setBars()` 先调 `super.setBars()`（HeatCrafter 自带 "heat" 热条），再叠加 "temperature" 温度条。

#### 颜色方案

| 温度比例 | 颜色 | 含义 |
|---------|------|------|
| < 75% maxTempK | `Pal.heal`（绿色） | 正常运行 |
| 75%~90% maxTempK | `Pal.lightOrange`（橙色） | 接近上限 |
| > 90% maxTempK | `Pal.health`（红色） | 超温预警 |

#### 验证日志

```
ThermalMod[debug]: setBars() 验证通过 — 锅炉bars=2 导管bars=2 精炼炉bars=3
```

- 锅炉：2 bars（health + temperature）
- 导管：2 bars（health + temperature）
- 精炼炉：3 bars（health + heat[HeatCrafter自带] + temperature）
- ✅ headless 模式下 setBars() 不抛异常

---

### 10.4 剩余限制与下一步

#### 已知限制

1. **官方热网与热力学系统是两套并行网络**：桥接是单向的（官方 heat → 热力学 J），热力学温度通过 efficiencyScale() 反向影响效率，但热力学热量不会通过 sideHeat 反哺官方热网。
2. **导热管不参与官方热网路由**：calculateHeat() 只扫描 proximity 中 `instanceof HeatBlock` 的实体，当前导管不是 HeatBlock/HeatConductor，官方热只能直线传输（锅炉必须紧邻精炼炉）。
3. **第二个锅炉是测试专用**：为验证 Iteration 2 在精炼炉正上方放了第二个锅炉，实际游戏设计中应让导热管实现 HeatConductor 接口来路由官方热。
4. **超温后效率掉到 0**：锅炉到 800K 停机后官方 heat 归零，精炼炉 shouldConsume=false，即使热力学温度仍然很高也会停摆。这是设计预期（安全联动），但实际使用中需要缓冲。
5. **display() 文本在 headless 下不可见**：温度条和 display() 只在客户端渲染时可见，headless 验证只能确认不抛异常。

#### 下一步

1. **导热管实现 HeatConductor**：让官方热能通过导管路由，实现完整的热管网
2. **双向桥接**：热力学温度反映到 sideHeat，让两套系统真正融合
3. **实际物品生产**：RefineryFurnace 当前没有设置 consume/output items，接入铜/煤炭消耗
4. **多建筑热管网**：散热塔接入官方热网作为热汇
5. **UI 实机验证**：在客户端模式下截图确认温度条颜色和闪烁效果
